// icon builds the launcher icon files from the source artwork _aa_cottage_kitchen.png
// (a cottage on a blue rounded square, drawn in the same cartoon style as the game).
// Run from this directory after replacing the artwork:
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

const source = "../../_aa_cottage_kitchen.png"

// load decodes a PNG into an NRGBA image.
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

// scaleDown averages src down to n x n (area/box filter).
func scaleDown(src *image.NRGBA, n int) *image.NRGBA {
	s := src.Bounds().Dx()
	out := image.NewNRGBA(image.Rect(0, 0, n, n))
	k := float64(s) / float64(n)
	for y := 0; y < n; y++ {
		for x := 0; x < n; x++ {
			var r, g, b, a, cnt float64
			for sy := int(math.Floor(float64(y) * k)); float64(sy) < math.Min(float64(y+1)*k, float64(s)); sy++ {
				for sx := int(math.Floor(float64(x) * k)); float64(sx) < math.Min(float64(x+1)*k, float64(s)); sx++ {
					c := src.NRGBAAt(sx, sy)
					al := float64(c.A) / 255
					r += float64(c.R) * al
					g += float64(c.G) * al
					b += float64(c.B) * al
					a += float64(c.A)
					cnt++
				}
			}
			if cnt == 0 || a == 0 {
				continue
			}
			out.SetNRGBA(x, y, color.NRGBA{
				uint8(math.Round(r * 255 / a)),
				uint8(math.Round(g * 255 / a)),
				uint8(math.Round(b * 255 / a)),
				uint8(math.Round(a / cnt)),
			})
		}
	}
	return out
}

// roundedMask makes the corners outside a rounded square (radius frac*n) transparent,
// antialiased, so the adaptive foreground sits cleanly inside any system mask shape.
func roundedMask(im *image.NRGBA, frac float64) *image.NRGBA {
	n := im.Bounds().Dx()
	rad := frac * float64(n)
	out := image.NewNRGBA(im.Bounds())
	for y := 0; y < n; y++ {
		for x := 0; x < n; x++ {
			dx := math.Max(math.Max(rad-float64(x)-0.5, float64(x)+0.5-(float64(n)-rad)), 0)
			dy := math.Max(math.Max(rad-float64(y)-0.5, float64(y)+0.5-(float64(n)-rad)), 0)
			d := math.Hypot(dx, dy) - rad
			c := im.NRGBAAt(x, y)
			if d > 0.5 {
				continue // fully outside: transparent
			}
			if d > -0.5 {
				c.A = uint8(float64(c.A) * (0.5 - d))
			}
			out.SetNRGBA(x, y, c)
		}
	}
	return out
}

// average returns the mean colour of a unit-square region of src.
func average(src *image.NRGBA, fx0, fy0, fx1, fy1 float64) color.NRGBA {
	n := src.Bounds().Dx()
	var r, g, b, cnt float64
	for y := int(fy0 * float64(n)); y < int(fy1*float64(n)); y++ {
		for x := int(fx0 * float64(n)); x < int(fx1*float64(n)); x++ {
			c := src.NRGBAAt(x, y)
			r += float64(c.R)
			g += float64(c.G)
			b += float64(c.B)
			cnt++
		}
	}
	return color.NRGBA{uint8(math.Round(r / cnt)), uint8(math.Round(g / cnt)), uint8(math.Round(b / cnt)), 255}
}

func solid(n int, c color.NRGBA) *image.NRGBA {
	im := image.NewNRGBA(image.Rect(0, 0, n, n))
	for y := 0; y < n; y++ {
		for x := 0; x < n; x++ {
			im.SetNRGBA(x, y, c)
		}
	}
	return im
}

func save(p string, im image.Image) {
	if err := os.MkdirAll(filepath.Dir(p), 0o755); err != nil {
		panic(err)
	}
	f, err := os.Create(p)
	if err != nil {
		panic(err)
	}
	defer f.Close()
	if err := png.Encode(f, im); err != nil {
		panic(err)
	}
}

func main() {
	main := os.Args[1] // app/src/main
	res := filepath.Join(main, "res")
	src := load(source)

	// Legacy launcher icons (Android 7.x and older) and the Play Store icon.
	for dir, n := range map[string]int{"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192} {
		save(filepath.Join(res, "mipmap-"+dir, "ic_launcher.png"), scaleDown(src, n))
	}
	save(filepath.Join(main, "ic_launcher-playstore.png"), scaleDown(src, 512))

	// Adaptive icon (API 26+): the artwork is the foreground, sampled sky blue the background.
	save(filepath.Join(res, "drawable-xxxhdpi", "ic_launcher_foreground.png"), roundedMask(scaleDown(src, 432), 0.22))
	bg := average(src, 0.30, 0.01, 0.60, 0.05)
	save(filepath.Join(res, "drawable-xxxhdpi", "ic_launcher_background.png"), solid(432, bg))
}
