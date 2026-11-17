// icon builds the launcher icon files: the strawberry jam jar standing on a kitchen counter, in front of a peach
// wall with bunting, in the same cartoon style as the game.
// Run after tools/dishes (which writes jam.png here):
//
//	(cd tools/icon && go run . ../../app/src/main)
package main

import (
	"image"
	"image/color"
	"image/png"
	"math"
	"os"
	"path/filepath"
)

// ---------------------------------------------------------------- drawing

type shape func(x, y float64) bool

// paint blends a shape (in unit coordinates, 0..1) into im, antialiased with 3x3 samples per pixel.
func paint(im *image.NRGBA, s shape, c color.NRGBA, opacity float64, x0, y0, x1, y1 float64) {
	n := float64(im.Bounds().Dx())
	for py := max(0, int(y0*n)-1); py < min(im.Bounds().Dy(), int(y1*n)+2); py++ {
		for px := max(0, int(x0*n)-1); px < min(im.Bounds().Dx(), int(x1*n)+2); px++ {
			in := 0
			for sy := 0; sy < 3; sy++ {
				for sx := 0; sx < 3; sx++ {
					if s((float64(px)+(float64(sx)+0.5)/3)/n, (float64(py)+(float64(sy)+0.5)/3)/n) {
						in++
					}
				}
			}
			if in == 0 {
				continue
			}
			a := opacity * float64(in) / 9
			d := im.NRGBAAt(px, py)
			mix := func(top, bot uint8) uint8 { return uint8(math.Round(float64(top)*a + float64(bot)*(1-a))) }
			da := float64(d.A) / 255
			oa := a + da*(1-a)
			im.SetNRGBA(px, py, color.NRGBA{mix(c.R, d.R), mix(c.G, d.G), mix(c.B, d.B), uint8(math.Round(oa * 255))})
		}
	}
}

func rrect(l, t, r, b, rad float64) shape {
	return func(x, y float64) bool {
		if x < l || x > r || y < t || y > b {
			return false
		}
		cx, cy := math.Max(l+rad, math.Min(x, r-rad)), math.Max(t+rad, math.Min(y, b-rad))
		return (x-cx)*(x-cx)+(y-cy)*(y-cy) <= rad*rad
	}
}

func ellipse(cx, cy, rx, ry float64) shape {
	return func(x, y float64) bool {
		dx, dy := (x-cx)/rx, (y-cy)/ry
		return dx*dx+dy*dy <= 1
	}
}

func triangle(ax, ay, bx, by, cx, cy float64) shape {
	return func(x, y float64) bool {
		d1 := (x-bx)*(ay-by) - (ax-bx)*(y-by)
		d2 := (x-cx)*(by-cy) - (bx-cx)*(y-cy)
		d3 := (x-ax)*(cy-ay) - (cx-ax)*(y-ay)
		neg := d1 < 0 || d2 < 0 || d3 < 0
		pos := d1 > 0 || d2 > 0 || d3 > 0
		return !(neg && pos)
	}
}

func hex(v uint32) color.NRGBA { return color.NRGBA{uint8(v >> 16), uint8(v >> 8), uint8(v), 255} }

var outline = hex(0xB08C60)

// background is the kitchen: a peach striped wall, bunting, and the worktop with its cabinet.
func background(n int) *image.NRGBA {
	im := image.NewNRGBA(image.Rect(0, 0, n, n))
	top, bot := hex(0xFCE9D0), hex(0xF4DAB8)
	for y := 0; y < n; y++ {
		t := float64(y) / float64(n)
		c := color.NRGBA{uint8(float64(top.R) + (float64(bot.R)-float64(top.R))*t), uint8(float64(top.G) + (float64(bot.G)-float64(top.G))*t), uint8(float64(top.B) + (float64(bot.B)-float64(top.B))*t), 255}
		for x := 0; x < n; x++ {
			im.SetNRGBA(x, y, c)
		}
	}
	// wallpaper stripes
	for sx := -0.02; sx < 1; sx += 0.16 {
		l, r := sx, sx+0.08
		paint(im, func(x, y float64) bool { return x >= l && x <= r }, hex(0xE8B77A), 0.1, l, 0, r, 1)
	}
	// bunting along a sagging line
	flags := []uint32{0xF29C94, 0xFFF3DC, 0x9FCF98, 0xFFD36B}
	line := func(x float64) float64 { return 0.11 + 0.09*4*x*(1-x) }
	for i := 0; i < 9; i++ {
		x := 0.07 + float64(i)*0.1125
		y := line(x)
		w, h := 0.09, 0.1
		tri := triangle(x-w/2, y, x+w/2, y, x, y+h)
		big := triangle(x-w/2-0.012, y-0.008, x+w/2+0.012, y-0.008, x, y+h+0.02)
		paint(im, big, outline, 1, x-w, y-0.02, x+w, y+h+0.04)
		paint(im, tri, hex(flags[i%len(flags)]), 1, x-w, y, x+w, y+h+0.02)
	}
	for x := 0.0; x < 1; x += 0.004 {
		y := line(x)
		paint(im, ellipse(x, y, 0.004, 0.004), outline, 1, x-0.01, y-0.01, x+0.01, y+0.01)
	}
	// worktop and cabinet
	const counter = 0.745
	paint(im, rrect(-0.1, counter, 1.1, 1.2, 0), hex(0xC4DCC6), 1, 0, counter, 1, 1)
	paint(im, rrect(0.08, counter+0.1, 0.46, 1.1, 0.03), hex(0xCFE5D1), 1, 0.05, counter+0.08, 0.5, 1)
	paint(im, rrect(0.54, counter+0.1, 0.92, 1.1, 0.03), hex(0xCFE5D1), 1, 0.5, counter+0.08, 0.95, 1)
	paint(im, rrect(-0.1, counter-0.012, 1.1, counter+0.07, 0), outline, 1, 0, counter-0.02, 1, counter+0.09)
	paint(im, rrect(-0.1, counter, 1.1, counter+0.058, 0), hex(0xEBC692), 1, 0, counter, 1, counter+0.07)
	paint(im, rrect(-0.1, counter, 1.1, counter+0.02, 0), hex(0xF5DDB4), 1, 0, counter, 1, counter+0.03)
	return im
}

// jar is the strawberry jam jar with a soft shadow, sized to height h and standing on y = base.
func jar(n int, src *image.NRGBA, h, base float64) *image.NRGBA {
	im := image.NewNRGBA(image.Rect(0, 0, n, n))
	b := src.Bounds()
	minX, minY, maxX, maxY := b.Max.X, b.Max.Y, 0, 0
	for y := b.Min.Y; y < b.Max.Y; y++ {
		for x := b.Min.X; x < b.Max.X; x++ {
			if src.NRGBAAt(x, y).A > 8 {
				minX, maxX = min(minX, x), max(maxX, x)
				minY, maxY = min(minY, y), max(maxY, y)
			}
		}
	}
	cw, ch := float64(maxX-minX+1), float64(maxY-minY+1)
	scale := h * float64(n) / ch // output pixels per source pixel
	ow := cw * scale
	// shadow
	paint(im, ellipse(0.5, base+0.004, ow/float64(n)*0.46, 0.022), color.NRGBA{0x5A, 0x34, 0x10, 255}, 0.22, 0.1, base-0.03, 0.9, base+0.05)
	x0, y0 := float64(n)/2-ow/2, base*float64(n)-h*float64(n)
	for y := int(y0) - 1; y < int(y0+h*float64(n))+2; y++ {
		for x := int(x0) - 1; x < int(x0+ow)+2; x++ {
			if x < 0 || y < 0 || x >= n || y >= n {
				continue
			}
			// area-average the source over this pixel (premultiplied)
			sx0, sx1 := float64(minX)+(float64(x)-x0)/scale, float64(minX)+(float64(x+1)-x0)/scale
			sy0, sy1 := float64(minY)+(float64(y)-y0)/scale, float64(minY)+(float64(y+1)-y0)/scale
			var r, g, bl, a, cnt float64
			for sy := int(math.Floor(sy0)); float64(sy) < sy1; sy++ {
				for sx := int(math.Floor(sx0)); float64(sx) < sx1; sx++ {
					if sx < minX || sx > maxX || sy < minY || sy > maxY {
						cnt++
						continue
					}
					c := src.NRGBAAt(sx, sy)
					al := float64(c.A)
					r += float64(c.R) * al
					g += float64(c.G) * al
					bl += float64(c.B) * al
					a += al
					cnt++
				}
			}
			if cnt == 0 || a == 0 {
				continue
			}
			sa := a / cnt / 255
			s := color.NRGBA{uint8(r / a), uint8(g / a), uint8(bl / a), 255}
			d := im.NRGBAAt(x, y)
			da := float64(d.A) / 255
			oa := sa + da*(1-sa)
			mix := func(top, bot uint8) uint8 { return uint8(math.Round((float64(top)*sa + float64(bot)*da*(1-sa)) / oa)) }
			im.SetNRGBA(x, y, color.NRGBA{mix(s.R, d.R), mix(s.G, d.G), mix(s.B, d.B), uint8(math.Round(oa * 255))})
		}
	}
	return im
}

// over composites top onto bottom, both n x n.
func over(bottom, top *image.NRGBA) *image.NRGBA {
	out := image.NewNRGBA(bottom.Bounds())
	copy(out.Pix, bottom.Pix)
	for i := 0; i < len(top.Pix); i += 4 {
		a := float64(top.Pix[i+3]) / 255
		if a == 0 {
			continue
		}
		for k := 0; k < 3; k++ {
			out.Pix[i+k] = uint8(math.Round(float64(top.Pix[i+k])*a + float64(out.Pix[i+k])*(1-a)))
		}
		out.Pix[i+3] = 255
	}
	return out
}

// rounded cuts the corners of a full-bleed square for the legacy launcher icons.
func rounded(im *image.NRGBA) *image.NRGBA {
	n := im.Bounds().Dx()
	fn := float64(n)
	out := image.NewNRGBA(im.Bounds())
	rad := 0.22 * fn
	for y := 0; y < n; y++ {
		for x := 0; x < n; x++ {
			dx := math.Max(math.Max(rad-float64(x)-0.5, float64(x)+0.5-(fn-rad)), 0)
			dy := math.Max(math.Max(rad-float64(y)-0.5, float64(y)+0.5-(fn-rad)), 0)
			d := math.Hypot(dx, dy) - rad
			if d > 0.5 {
				continue
			}
			c := im.NRGBAAt(x, y)
			if d > -0.5 {
				c.A = uint8(float64(c.A) * (0.5 - d))
			}
			out.SetNRGBA(x, y, c)
		}
	}
	return out
}

// scaleDown averages im down to n x n.
func scaleDown(im *image.NRGBA, n int) *image.NRGBA {
	s := im.Bounds().Dx()
	out := image.NewNRGBA(image.Rect(0, 0, n, n))
	k := float64(s) / float64(n)
	for y := 0; y < n; y++ {
		for x := 0; x < n; x++ {
			var r, g, b, a, cnt float64
			for sy := int(float64(y) * k); float64(sy) < float64(y+1)*k && sy < s; sy++ {
				for sx := int(float64(x) * k); float64(sx) < float64(x+1)*k && sx < s; sx++ {
					c := im.NRGBAAt(sx, sy)
					al := float64(c.A)
					r += float64(c.R) * al
					g += float64(c.G) * al
					b += float64(c.B) * al
					a += al
					cnt++
				}
			}
			if a == 0 {
				continue
			}
			out.SetNRGBA(x, y, color.NRGBA{uint8(r / a), uint8(g / a), uint8(b / a), uint8(a / cnt)})
		}
	}
	return out
}

func load(p string) *image.NRGBA {
	f, err := os.Open(p)
	if err != nil {
		panic(err)
	}
	defer f.Close()
	im, err := png.Decode(f)
	if err != nil {
		panic(err)
	}
	b := im.Bounds()
	out := image.NewNRGBA(b)
	for y := b.Min.Y; y < b.Max.Y; y++ {
		for x := b.Min.X; x < b.Max.X; x++ {
			out.Set(x, y, im.At(x, y))
		}
	}
	return out
}

func save(p string, im image.Image) {
	os.MkdirAll(filepath.Dir(p), 0o755)
	f, err := os.Create(p)
	if err != nil {
		panic(err)
	}
	defer f.Close()
	png.Encode(f, im)
}

func main() {
	main := os.Args[1] // app/src/main
	res := filepath.Join(main, "res")
	jam := load("jam.png")

	// the adaptive icon is 108dp: art stays inside the central 66dp. Drawn large, then averaged down.
	const big = 864
	bg := background(big)
	fg := jar(big, jam, 0.46, 0.752)

	save(filepath.Join(res, "drawable-xxxhdpi", "ic_launcher_background.png"), scaleDown(bg, 432))
	save(filepath.Join(res, "drawable-xxxhdpi", "ic_launcher_foreground.png"), scaleDown(fg, 432))

	// legacy icons (Android 7.x and older) and the Play Store icon are the full picture
	full := over(bg, fg)
	for dir, n := range map[string]int{"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192} {
		save(filepath.Join(res, "mipmap-"+dir, "ic_launcher.png"), rounded(scaleDown(full, n)))
	}
	save(filepath.Join(main, "ic_launcher-playstore.png"), scaleDown(full, 512))
}
