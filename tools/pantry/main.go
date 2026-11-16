// pantry draws the milk bottle tile in the same cartoon style as the other crops.
// (Flour, honey, egg and sugar are real artwork: see tools/dishes.)
// Usage: go run . <res/drawable-nodpi dir>
package main

import (
	"image"
	"image/color"
	"image/png"
	"math"
	"os"
	"path/filepath"
)

const (
	size = 192
	ss   = 4 // supersampling
	big  = size * ss
	grow = 1.1
)

var outline = color.NRGBA{0x6B, 0x3F, 0x17, 255}

type canvas struct{ pix []color.NRGBA }

func newCanvas() *canvas { return &canvas{make([]color.NRGBA, big*big)} }

type shape func(x, y float64) bool

func rrect(x0, y0, x1, y1, r float64) shape {
	return func(x, y float64) bool {
		if x < x0 || x > x1 || y < y0 || y > y1 {
			return false
		}
		cx := math.Max(x0+r, math.Min(x, x1-r))
		cy := math.Max(y0+r, math.Min(y, y1-r))
		return (x-cx)*(x-cx)+(y-cy)*(y-cy) <= r*r
	}
}

func ellipse(cx, cy, rx, ry float64) shape {
	return func(x, y float64) bool {
		dx, dy := (x-cx)/rx, (y-cy)/ry
		return dx*dx+dy*dy <= 1
	}
}

func capsule(x1, y1, x2, y2, w float64) shape {
	return func(x, y float64) bool {
		dx, dy := x2-x1, y2-y1
		t := math.Max(0, math.Min(1, ((x-x1)*dx+(y-y1)*dy)/(dx*dx+dy*dy)))
		px, py := x1+t*dx-x, y1+t*dy-y
		return px*px+py*py <= w*w/4
	}
}

func poly(pts ...float64) shape {
	return func(x, y float64) bool {
		in := false
		n := len(pts) / 2
		for i, j := 0, n-1; i < n; j, i = i, i+1 {
			xi, yi, xj, yj := pts[2*i], pts[2*i+1], pts[2*j], pts[2*j+1]
			if (yi > y) != (yj > y) && x < (xj-xi)*(y-yi)/(yj-yi)+xi {
				in = !in
			}
		}
		return in
	}
}

func union(s ...shape) shape {
	return func(x, y float64) bool {
		for _, f := range s {
			if f(x, y) {
				return true
			}
		}
		return false
	}
}

func (c *canvas) fill(s shape, col color.NRGBA) {
	for py := 0; py < big; py++ {
		for px := 0; px < big; px++ {
			if s(float64(px)/ss, float64(py)/ss) {
				c.pix[py*big+px] = col
			}
		}
	}
}

// body draws a shape with a dark outline of width w (in output pixels).
func (c *canvas) body(s shape, edge float64, col color.NRGBA) {
	c.fill(func(x, y float64) bool {
		for a := 0.0; a < 2*math.Pi; a += math.Pi / 8 {
			if s(x+math.Cos(a)*edge, y+math.Sin(a)*edge) {
				return true
			}
		}
		return s(x, y)
	}, outline)
	c.fill(s, col)
}

func (c *canvas) face(cx, cy, gap float64) { c.faceK(cx, cy, gap, 1) }

// faceK draws the face scaled by k.
func (c *canvas) faceK(cx, cy, gap, k float64) {
	white, dark := color.NRGBA{255, 255, 255, 255}, color.NRGBA{0x2A, 0x1A, 0x10, 255}
	for _, dx := range []float64{-gap, gap} {
		c.fill(ellipse(cx+dx, cy, 11*k, 12*k), dark)
		c.fill(ellipse(cx+dx, cy, 9*k, 10*k), white)
		c.fill(ellipse(cx+dx+1*k, cy+1*k, 5*k, 5.5*k), dark)
		c.fill(ellipse(cx+dx+2.5*k, cy-1*k, 1.8*k, 1.8*k), white)
	}
	// smile
	var prev [2]float64
	for i := 0; i <= 12; i++ {
		a := math.Pi * (0.15 + 0.7*float64(i)/12)
		x, y := cx+math.Cos(a)*14*k, cy+(16+math.Sin(a)*9)*k
		if i > 0 {
			c.fill(capsule(prev[0], prev[1], x, y, 3.4*k), dark)
		}
		prev = [2]float64{x, y}
	}
}

func (c *canvas) save(path string) {
	img := image.NewNRGBA(image.Rect(0, 0, size, size))
	for y := 0; y < size; y++ {
		for x := 0; x < size; x++ {
			var r, g, b, a, n int
			for sy := 0; sy < ss; sy++ {
				for sx := 0; sx < ss; sx++ {
					// the drawing is scaled up a little about the base so it fills the tile like the other crops
					px := int(96*ss + (float64(x*ss+sx)-96*ss)/grow)
					py := int(184*ss + (float64(y*ss+sy)-184*ss)/grow)
					if px < 0 || py < 0 || px >= big || py >= big {
						n++
						continue
					}
					p := c.pix[py*big+px]
					r += int(p.R) * int(p.A)
					g += int(p.G) * int(p.A)
					b += int(p.B) * int(p.A)
					a += int(p.A)
					n++
				}
			}
			if a > 0 {
				img.SetNRGBA(x, y, color.NRGBA{uint8(r / a), uint8(g / a), uint8(b / a), uint8(a / n)})
			}
		}
	}
	f, err := os.Create(path)
	if err != nil {
		panic(err)
	}
	defer f.Close()
	if err := png.Encode(f, img); err != nil {
		panic(err)
	}
}

func milk() *canvas {
	c := newCanvas()
	white, blue := color.NRGBA{0xF7, 0xFB, 0xFF, 255}, color.NRGBA{0x4F, 0xA8, 0xE8, 255}
	c.fill(ellipse(96, 176, 46, 7), color.NRGBA{0, 0, 0, 40})
	c.body(union(rrect(70, 34, 122, 62, 6), rrect(62, 56, 130, 100, 26)), 4, white)
	c.body(rrect(50, 76, 142, 172, 24), 4, white)
	c.fill(rrect(54, 92, 138, 100, 3), color.NRGBA{0xDC, 0xEE, 0xFB, 255})
	c.body(rrect(66, 22, 126, 44, 8), 4, blue)
	c.fill(rrect(72, 27, 120, 31, 2), color.NRGBA{0x8D, 0xCE, 0xFA, 255})
	c.fill(rrect(54, 150, 138, 168, 10), color.NRGBA{0xDC, 0xEE, 0xFB, 255})
	c.face(96, 122, 19)
	c.fill(ellipse(64, 112, 4, 14), color.NRGBA{255, 255, 255, 255})
	return c
}

func main() {
	dir := "."
	if len(os.Args) > 1 {
		dir = os.Args[1]
	}
	milk().save(filepath.Join(dir, "crop_milk.png"))
}
