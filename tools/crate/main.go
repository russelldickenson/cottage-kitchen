// crate draws the placeholder crate tiles (whole and cracked) straight into the game's drawables.
// Run it from the repo root or from tools/crate. Replace the PNGs with real artwork when it exists.
package main

import (
	"image"
	"image/color"
	"image/png"
	"math"
	"os"
	"path/filepath"
)

const size, ss = 192, 4

var (
	outline = color.NRGBA{0x5A, 0x34, 0x10, 255}
	wood    = color.NRGBA{0xE3, 0xAE, 0x62, 255}
	panel   = color.NRGBA{0xF0, 0xC4, 0x82, 255}
	slat    = color.NRGBA{0xC9, 0x8B, 0x3E, 255}
	nail    = color.NRGBA{0x8A, 0x5A, 0x25, 255}
	cheek   = color.NRGBA{0xF4, 0x9A, 0x9A, 255}
	white   = color.NRGBA{255, 255, 255, 255}
)

// shape reports whether a point is inside it, in 192-pixel space.
type shape func(x, y float64) bool

func rrect(x0, y0, x1, y1, r float64) shape {
	return func(x, y float64) bool {
		if x < x0 || x > x1 || y < y0 || y > y1 {
			return false
		}
		dx := math.Max(math.Max(x0+r-x, x-(x1-r)), 0)
		dy := math.Max(math.Max(y0+r-y, y-(y1-r)), 0)
		return dx*dx+dy*dy <= r*r
	}
}

func circle(cx, cy, r float64) shape {
	return func(x, y float64) bool { return (x-cx)*(x-cx)+(y-cy)*(y-cy) <= r*r }
}

func seg(x0, y0, x1, y1, w float64) shape {
	return func(x, y float64) bool {
		dx, dy := x1-x0, y1-y0
		t := math.Max(0, math.Min(1, ((x-x0)*dx+(y-y0)*dy)/(dx*dx+dy*dy)))
		px, py := x0+t*dx-x, y0+t*dy-y
		return px*px+py*py <= w*w/4
	}
}

// smile is an arc of the circle (cx,cy,r) between two angles in degrees, w wide.
func smile(cx, cy, r, a0, a1, w float64) shape {
	return func(x, y float64) bool {
		d := math.Hypot(x-cx, y-cy)
		a := math.Atan2(y-cy, x-cx) * 180 / math.Pi
		return math.Abs(d-r) <= w/2 && a >= a0 && a <= a1
	}
}

func ring(cx, cy, r, w float64) shape {
	return func(x, y float64) bool { return math.Abs(math.Hypot(x-cx, y-cy)-r) <= w/2 }
}

type layer struct {
	s shape
	c color.NRGBA
}

func inset(x0, y0, x1, y1, r, by float64) shape { return rrect(x0+by, y0+by, x1-by, y1-by, math.Max(1, r-by)) }

func crate(cracked bool) []layer {
	ls := []layer{
		{rrect(20, 26, 172, 178, 16), outline},
		{inset(20, 26, 172, 178, 16, 6), wood},
		{rrect(36, 42, 156, 162, 6), outline},
		{inset(36, 42, 156, 162, 6, 4), panel},
		{seg(40, 78, 152, 78, 9), outline},
		{seg(40, 78, 152, 78, 4), slat},
		{seg(40, 144, 152, 144, 9), outline},
		{seg(40, 144, 152, 144, 4), slat},
	}
	for _, p := range [][2]float64{{30, 36}, {162, 36}, {30, 168}, {162, 168}} {
		ls = append(ls, layer{circle(p[0], p[1], 5), outline}, layer{circle(p[0], p[1], 3), nail})
	}
	ls = append(ls,
		layer{circle(78, 108, 9), outline}, layer{circle(78, 108, 6.5), outline}, layer{circle(76, 105, 2.5), white},
		layer{circle(114, 108, 9), outline}, layer{circle(114, 108, 6.5), outline}, layer{circle(112, 105, 2.5), white},
		layer{circle(64, 124, 7), cheek}, layer{circle(128, 124, 7), cheek},
	)
	if cracked {
		ls = append(ls,
			layer{ring(96, 124, 6, 3.5), outline},
			layer{seg(20, 58, 50, 70, 5), outline}, layer{seg(50, 70, 42, 94, 5), outline}, layer{seg(42, 94, 56, 110, 5), outline},
			layer{seg(172, 150, 140, 138, 5), outline}, layer{seg(140, 138, 148, 116, 5), outline},
			layer{seg(96, 26, 88, 46, 5), outline}, layer{seg(88, 46, 104, 60, 5), outline},
		)
	} else {
		ls = append(ls, layer{smile(96, 114, 10, 20, 160, 3.5), outline})
	}
	return ls
}

func render(ls []layer) *image.NRGBA {
	img := image.NewNRGBA(image.Rect(0, 0, size, size))
	for py := 0; py < size; py++ {
		for px := 0; px < size; px++ {
			var r, g, b, a float64
			for sy := 0; sy < ss; sy++ {
				for sx := 0; sx < ss; sx++ {
					x, y := float64(px)+(float64(sx)+0.5)/ss, float64(py)+(float64(sy)+0.5)/ss
					for i := len(ls) - 1; i >= 0; i-- {
						if ls[i].s(x, y) {
							c := ls[i].c
							r, g, b, a = r+float64(c.R), g+float64(c.G), b+float64(c.B), a+1
							break
						}
					}
				}
			}
			if a > 0 {
				img.SetNRGBA(px, py, color.NRGBA{uint8(r / a), uint8(g / a), uint8(b / a), uint8(255 * a / (ss * ss))})
			}
		}
	}
	return img
}

func main() {
	dst := "../../app/src/main/res/drawable-nodpi"
	if _, err := os.Stat("tools/crate"); err == nil {
		dst = "app/src/main/res/drawable-nodpi"
	}
	for name, cracked := range map[string]bool{"crop_crate.png": false, "crop_crate_cracked.png": true} {
		f, err := os.Create(filepath.Join(dst, name))
		if err != nil {
			panic(err)
		}
		png.Encode(f, render(crate(cracked)))
		f.Close()
	}
}
