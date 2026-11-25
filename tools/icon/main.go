// icon builds the launcher icon files from the source artwork
// _aa_cottage_with_hat.jpeg (a cottage in the same cartoon style as the game).
//
// The artwork is drawn centred on a plain background sampled from its own edges,
// leaving a border all round. Launchers on API 26+ apply their own shape mask and
// crop the outer part of the icon; keeping the cottage inside a plain margin means
// a reshaped icon never cuts into the drawing.
//
// Run from this directory after replacing the artwork:
//
//	(cd tools/icon && go run . ../../app/src/main)
package main

import (
	"image"
	"image/color"
	"image/png"
	_ "image/jpeg"
	"math"
	"os"
	"path/filepath"
)

const source = "../../_aa_cottage_with_hat.jpeg"

// Fraction of the canvas the artwork fills. The rest is the plain border.
// Adaptive icons keep the cottage inside the central safe zone so any launcher
// mask shape (circle, squircle, ...) leaves the drawing intact.
const (
	legacyFill    = 0.80
	adaptiveFill  = 0.66
	playstoreFill = 0.80
)

// load decodes a PNG or JPEG into an NRGBA image.
func load(p string) *image.NRGBA {
	f, err := os.Open(p)
	if err != nil {
		panic(err)
	}
	defer f.Close()
	im, _, err := image.Decode(f)
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

// cornerColour samples the four corners of the artwork, whose background the border
// copies, so the cottage sits on a plain field of its own colour.
func cornerColour(src *image.NRGBA) color.NRGBA {
	patches := [][4]float64{
		{0.00, 0.00, 0.10, 0.10},
		{0.90, 0.00, 1.00, 0.10},
		{0.00, 0.90, 0.10, 1.00},
		{0.90, 0.90, 1.00, 1.00},
	}
	var r, g, b float64
	for _, p := range patches {
		c := average(src, p[0], p[1], p[2], p[3])
		r += float64(c.R)
		g += float64(c.G)
		b += float64(c.B)
	}
	return color.NRGBA{uint8(math.Round(r / 4)), uint8(math.Round(g / 4)), uint8(math.Round(b / 4)), 255}
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

// compose returns an n x n canvas filled with bg, with src scaled to a centred
// square of side frac*n (a plain border is left all round when bg is opaque).
func compose(src *image.NRGBA, n int, frac float64, bg color.NRGBA) *image.NRGBA {
	out := solid(n, bg)
	side := int(math.Round(frac * float64(n)))
	scaled := scaleDown(src, side)
	off := (n - side) / 2
	for y := 0; y < side; y++ {
		for x := 0; x < side; x++ {
			out.SetNRGBA(off+x, off+y, scaled.NRGBAAt(x, y))
		}
	}
	return out
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
	bg := cornerColour(src)
	println("border colour:", colorToCSS(bg))

	// Legacy launcher icons (Android 7.x and older) and the Play Store icon:
	// the cottage centred on the plain background.
	for dir, n := range map[string]int{"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192} {
		save(filepath.Join(res, "mipmap-"+dir, "ic_launcher.png"), compose(src, n, legacyFill, bg))
	}
	save(filepath.Join(main, "ic_launcher-playstore.png"), compose(src, 512, playstoreFill, bg))

	// Adaptive icon (API 26+): a transparent foreground with the cottage in the safe
	// zone, over a plain background of the sampled colour.
	transparent := color.NRGBA{0, 0, 0, 0}
	save(filepath.Join(res, "drawable-xxxhdpi", "ic_launcher_foreground.png"), compose(src, 432, adaptiveFill, transparent))
	save(filepath.Join(res, "drawable-xxxhdpi", "ic_launcher_background.png"), solid(432, bg))
}

func colorToCSS(c color.NRGBA) string {
	const hex = "0123456789abcdef"
	b := []byte{'#', 0, 0, 0, 0, 0, 0}
	for i, v := range []uint8{c.R, c.G, c.B} {
		b[1+i*2] = hex[v>>4]
		b[2+i*2] = hex[v&0x0f]
	}
	return string(b)
}