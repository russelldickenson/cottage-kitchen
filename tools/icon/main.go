// icon builds the launcher icon files from the pumpkin sprite.
// Run from the repo root: (cd tools/icon && go run . ../../app/src/main/res)
package main

import (
	"image"
	"image/color"
	"image/png"
	"math"
	"os"
	"path/filepath"
)

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

// sample reads src bilinearly in premultiplied space at fractional coordinates.
func sample(src *image.NRGBA, x, y float64) (r, g, b, a float64) {
	w, h := src.Bounds().Dx(), src.Bounds().Dy()
	x -= 0.5
	y -= 0.5
	x0, y0 := int(math.Floor(x)), int(math.Floor(y))
	fx, fy := x-float64(x0), y-float64(y0)
	for j := 0; j < 2; j++ {
		for i := 0; i < 2; i++ {
			px, py := x0+i, y0+j
			if px < 0 || py < 0 || px >= w || py >= h {
				continue
			}
			wt := (1 - math.Abs(float64(i)-fx)) * (1 - math.Abs(float64(j)-fy))
			c := src.NRGBAAt(px, py)
			al := float64(c.A) / 255
			r += float64(c.R) * al * wt
			g += float64(c.G) * al * wt
			b += float64(c.B) * al * wt
			a += al * wt
		}
	}
	return
}

// drawScaled composites src scaled to box x box with its centre at (cx, cy) onto dst.
func drawScaled(dst *image.NRGBA, src *image.NRGBA, cx, cy, box float64) {
	sw := float64(src.Bounds().Dx())
	scale := box / sw
	x0, y0 := int(cx-box/2)-1, int(cy-box/2)-1
	for y := y0; y < y0+int(box)+3; y++ {
		for x := x0; x < x0+int(box)+3; x++ {
			if x < 0 || y < 0 || x >= dst.Bounds().Dx() || y >= dst.Bounds().Dy() {
				continue
			}
			r, g, b, a := sample(src, (float64(x)+0.5-(cx-box/2))/scale, (float64(y)+0.5-(cy-box/2))/scale)
			if a <= 0 {
				continue
			}
			d := dst.NRGBAAt(x, y)
			da := float64(d.A) / 255
			oa := a + da*(1-a)
			or := (r + float64(d.R)*da*(1-a)) / oa
			og := (g + float64(d.G)*da*(1-a)) / oa
			ob := (b + float64(d.B)*da*(1-a)) / oa
			dst.SetNRGBA(x, y, color.NRGBA{uint8(or + 0.5), uint8(og + 0.5), uint8(ob + 0.5), uint8(oa*255 + 0.5)})
		}
	}
}

func lerp(a, b uint8, t float64) uint8 { return uint8(float64(a) + (float64(b)-float64(a))*t) }

// background paints the sky, sun and hills used behind the pumpkin.
func background(n int, round bool) *image.NRGBA {
	im := image.NewNRGBA(image.Rect(0, 0, n, n))
	fn := float64(n)
	for y := 0; y < n; y++ {
		t := float64(y) / fn
		sky := color.NRGBA{lerp(0x6E, 0xE4, t), lerp(0xC8, 0xF6, t), lerp(0xF5, 0xFF, t), 255}
		for x := 0; x < n; x++ {
			c := sky
			fx := float64(x) / fn
			// sun
			if math.Hypot(fx-0.8, t-0.22) < 0.09 {
				c = color.NRGBA{0xFF, 0xE0, 0x66, 255}
			}
			// hills
			if t > 0.72+0.06*math.Sin(fx*math.Pi*1.6+0.4) {
				c = color.NRGBA{0x8B, 0xD4, 0x50, 255}
			}
			if t > 0.86+0.04*math.Sin(fx*math.Pi*2+2.2) {
				c = color.NRGBA{0x68, 0xB8, 0x3A, 255}
			}
			if round {
				// rounded-square mask, 22% corner radius
				rad := 0.22 * fn
				dx := math.Max(math.Max(rad-float64(x)-0.5, float64(x)+0.5-(fn-rad)), 0)
				dy := math.Max(math.Max(rad-float64(y)-0.5, float64(y)+0.5-(fn-rad)), 0)
				d := math.Hypot(dx, dy) - rad
				if d > 0.5 {
					continue
				}
				if d > -0.5 {
					c.A = uint8(255 * (0.5 - d))
				}
			}
			im.SetNRGBA(x, y, c)
		}
	}
	return im
}

func main() {
	res := os.Args[1]
	pumpkin := load(filepath.Join(res, "drawable-nodpi", "crop_pumpkin.png"))

	// adaptive foreground: 108dp canvas at xxxhdpi, art kept inside the 66dp safe zone
	fg := image.NewNRGBA(image.Rect(0, 0, 432, 432))
	drawScaled(fg, pumpkin, 216, 224, 280)
	save(filepath.Join(res, "drawable-xxxhdpi", "ic_launcher_foreground.png"), fg)

	// legacy icons for Android 7.x (adaptive icons need 8.0)
	for dir, n := range map[string]int{"mdpi": 48, "hdpi": 72, "xhdpi": 96, "xxhdpi": 144, "xxxhdpi": 192} {
		bg := background(n, true)
		drawScaled(bg, pumpkin, float64(n)/2, float64(n)*0.53, float64(n)*0.74)
		save(filepath.Join(res, "mipmap-"+dir, "ic_launcher.png"), bg)
	}
}
