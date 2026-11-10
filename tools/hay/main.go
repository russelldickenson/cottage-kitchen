// hay draws the hay bale tile (crop_hay.png): a smiling bale in the same cartoon style as the other crops.
// Run: cd tools/hay && go run . ../../app/src/main/res/drawable-nodpi/crop_hay.png
package main

import (
	"image"
	"image/color"
	"image/png"
	"math"
	"os"
)

const size, ss = 192, 4

type rgb struct{ r, g, b float64 }

func hex(c uint32) rgb { return rgb{float64(c >> 16 & 255), float64(c >> 8 & 255), float64(c & 255)} }

func mix(a, b rgb, t float64) rgb {
	return rgb{a.r + (b.r-a.r)*t, a.g + (b.g-a.g)*t, a.b + (b.b-a.b)*t}
}

func rrect(px, py, x0, y0, x1, y1, rad float64) float64 {
	cx, cy := (x0+x1)/2, (y0+y1)/2
	qx := math.Abs(px-cx) - ((x1-x0)/2 - rad)
	qy := math.Abs(py-cy) - ((y1-y0)/2 - rad)
	return math.Hypot(math.Max(qx, 0), math.Max(qy, 0)) + math.Min(math.Max(qx, qy), 0) - rad
}

func capsule(px, py, x0, y0, x1, y1, rad float64) float64 {
	dx, dy := x1-x0, y1-y0
	t := math.Max(0, math.Min(1, ((px-x0)*dx+(py-y0)*dy)/(dx*dx+dy*dy)))
	return math.Hypot(px-(x0+t*dx), py-(y0+t*dy)) - rad
}

func circle(px, py, cx, cy, r float64) float64 { return math.Hypot(px-cx, py-cy) - r }

var (
	outline = hex(0x7A4A12)
	gold1   = hex(0xF7D766)
	gold2   = hex(0xE3A92C)
	straw   = hex(0xC98A1B)
	twine   = hex(0xA0561A)
)

type sample struct {
	c rgb
	a float64
}

func shade(px, py float64) sample {
	var out sample
	put := func(c rgb) { out = sample{c, 1} }

	// loose straw sticking out around the edge
	wisps := [][4]float64{{40, 58, 28, 46}, {64, 38, 58, 22}, {96, 32, 98, 14}, {130, 38, 138, 22}, {154, 58, 166, 46},
		{30, 120, 16, 124}, {162, 120, 176, 124}, {48, 150, 40, 166}, {146, 150, 154, 166}}
	for _, w := range wisps {
		if capsule(px, py, w[0], w[1], w[2], w[3], 5.5) < 0 {
			put(outline)
		}
	}
	for _, w := range wisps {
		if capsule(px, py, w[0], w[1], w[2], w[3], 2.6) < 0 {
			put(gold1)
		}
	}

	const cx, cy, R = 96.0, 98.0, 68.0
	if circle(px, py, cx, cy, R+4.5) < 0 {
		put(outline)
	}
	if circle(px, py, cx, cy, R) < 0 {
		put(mix(gold1, gold2, (py-(cy-R))/(2*R)))
		// spiral rings of straw
		d := math.Hypot(px-cx, py-cy)
		ang := math.Atan2(py-cy, px-cx)
		ring := math.Mod(d-ang*2.2+400, 13)
		if ring < 2.4 && d > 14 {
			put(straw)
		}
		// highlight
		if circle(px, py, cx-30, cy-34, 9) < 0 {
			put(mix(gold1, rgb{255, 255, 255}, 0.6))
		}
		// twine band across the bale
		if math.Abs(py-(cy+50)) < 4.5 {
			put(outline)
		}
		if math.Abs(py-(cy+50)) < 2.6 {
			put(twine)
		}
	}

	// face
	for _, ex := range []float64{84, 110} {
		if circle(px, py, ex, 100, 12) < 0 {
			put(outline)
		}
		if circle(px, py, ex, 100, 9.6) < 0 {
			put(rgb{255, 255, 255})
		}
		if circle(px, py, ex+1, 101, 5.2) < 0 {
			put(rgb{35, 25, 20})
		}
		if circle(px, py, ex+3, 98.5, 1.8) < 0 {
			put(rgb{255, 255, 255})
		}
	}
	// smile: lower arc
	d := math.Hypot(px-97, py-112)
	if py > 118 && math.Abs(d-12) < 2.6 && px > 82 && px < 112 {
		put(outline)
	}
	return out
}

func main() {
	dst := "app/src/main/res/drawable-nodpi/crop_hay.png"
	if len(os.Args) > 1 {
		dst = os.Args[1]
	}
	im := image.NewNRGBA(image.Rect(0, 0, size, size))
	for y := 0; y < size; y++ {
		for x := 0; x < size; x++ {
			var r, g, b, a float64
			for sy := 0; sy < ss; sy++ {
				for sx := 0; sx < ss; sx++ {
					s := shade(float64(x)+(float64(sx)+0.5)/ss, float64(y)+(float64(sy)+0.5)/ss)
					r += s.c.r * s.a
					g += s.c.g * s.a
					b += s.c.b * s.a
					a += s.a
				}
			}
			if a == 0 {
				continue
			}
			n := float64(ss * ss)
			im.SetNRGBA(x, y, color.NRGBA{uint8(r / a), uint8(g / a), uint8(b / a), uint8(255 * a / n)})
		}
	}
	f, err := os.Create(dst)
	if err != nil {
		panic(err)
	}
	defer f.Close()
	if err := png.Encode(f, im); err != nil {
		panic(err)
	}
}
