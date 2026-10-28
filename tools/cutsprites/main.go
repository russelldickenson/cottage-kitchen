// cutsprites crops individual sprites out of the supplied artwork sheets,
// removes the flat grey background and writes square PNGs.
package main

import (
	"image"
	"image/color"
	"image/png"
	"math"
	"os"
	"path/filepath"
)

type spec struct {
	name, sheet string
	r           image.Rectangle
}

const out = 192

// Sprites that read too big or small at the default fit, as a multiple of the usual size.
var scales = map[string]float64{"blueberry": 0.9}

var specs = []spec{
	{"corn", "sheet1", image.Rect(0, 20, 190, 238)},
	{"strawberry", "sheet1", image.Rect(190, 20, 372, 238)},
	{"orange", "sheet1", image.Rect(372, 20, 570, 238)},
	{"blueberry", "blueberry", image.Rect(0, 0, 1024, 559)},
	{"apple", "sheet1", image.Rect(795, 20, 1024, 238)},
	{"bee", "sheet1", image.Rect(0, 295, 235, 515)},
	{"egg", "sheet1", image.Rect(235, 295, 450, 515)},
	{"potato", "sheet1", image.Rect(455, 295, 640, 515)},
	{"sunflower", "sheet1", image.Rect(645, 270, 830, 515)},
	{"water", "sheet1", image.Rect(840, 295, 1024, 515)},
	{"pumpkin", "sheet2", image.Rect(20, 50, 235, 260)},
	{"milk", "sheet2", image.Rect(640, 40, 800, 270)},
	{"watermelon", "sheet2", image.Rect(600, 300, 830, 510)},
	{"scarecrow", "specials", image.Rect(10, 95, 290, 475)},
	{"tnt", "specials", image.Rect(295, 110, 480, 445)},
	{"egg_gold", "specials", image.Rect(485, 160, 700, 450)},
	{"tractor", "specials", image.Rect(700, 150, 1015, 465)},
}

func load(p string) image.Image {
	f, err := os.Open(p)
	if err != nil {
		panic(err)
	}
	defer f.Close()
	im, err := png.Decode(f)
	if err != nil {
		panic(err)
	}
	return im
}

func dist(a, b color.NRGBA) float64 {
	dr, dg, db := float64(a.R)-float64(b.R), float64(a.G)-float64(b.G), float64(a.B)-float64(b.B)
	return math.Sqrt(dr*dr + dg*dg + db*db)
}

func main() {
	sheets := map[string]image.Image{
		"sheet1":    load("tools/sprites/sheet1.png"),
		"sheet2":    load("tools/sprites/sheet2.png"),
		"blueberry": load("tools/sprites/blueberry.png"),
		"specials":  load("tools/sprites/specials.png"),
	}
	dst := "app/src/main/res/drawable-nodpi"
	for _, s := range specs {
		sp := crop(sheets[s.sheet], s.r)
		sp = largest(sp)
		sp = fit(sp, scaleOf(s.name))
		f, _ := os.Create(filepath.Join(dst, "crop_"+s.name+".png"))
		png.Encode(f, sp)
		f.Close()
	}
}

func crop(src image.Image, r image.Rectangle) *image.NRGBA {
	w, h := r.Dx(), r.Dy()
	im := image.NewNRGBA(image.Rect(0, 0, w, h))
	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			im.Set(x, y, color.NRGBAModel.Convert(src.At(r.Min.X+x, r.Min.Y+y)))
		}
	}
	bg := im.NRGBAAt(2, 2)
	// flood fill background from every border pixel
	isBg := make([]bool, w*h)
	var q [][2]int
	push := func(x, y int) {
		if x < 0 || y < 0 || x >= w || y >= h || isBg[y*w+x] {
			return
		}
		if dist(im.NRGBAAt(x, y), bg) < 22 {
			isBg[y*w+x] = true
			q = append(q, [2]int{x, y})
		}
	}
	for x := 0; x < w; x++ {
		push(x, 0)
		push(x, h-1)
	}
	for y := 0; y < h; y++ {
		push(0, y)
		push(w-1, y)
	}
	for len(q) > 0 {
		p := q[len(q)-1]
		q = q[:len(q)-1]
		push(p[0]+1, p[1])
		push(p[0]-1, p[1])
		push(p[0], p[1]+1)
		push(p[0], p[1]-1)
	}
	// soften fringe: grey-ish pixels touching bg become partially transparent
	for pass := 0; pass < 2; pass++ {
		var edge []int
		for y := 1; y < h-1; y++ {
			for x := 1; x < w-1; x++ {
				i := y*w + x
				if isBg[i] {
					continue
				}
				if (isBg[i-1] || isBg[i+1] || isBg[i-w] || isBg[i+w]) && dist(im.NRGBAAt(x, y), bg) < 70 {
					edge = append(edge, i)
				}
			}
		}
		for _, i := range edge {
			isBg[i] = true
		}
	}
	for i, b := range isBg {
		if b {
			im.Pix[i*4+3] = 0
		}
	}
	return im
}

// largest keeps the main connected blob (plus sizeable pieces) and drops stray neighbours.
func largest(im *image.NRGBA) *image.NRGBA {
	b := im.Bounds()
	w, h := b.Dx(), b.Dy()
	comp := make([]int, w*h)
	var sizes []int
	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			if comp[y*w+x] != 0 || im.Pix[(y*w+x)*4+3] == 0 {
				continue
			}
			id := len(sizes) + 1
			sizes = append(sizes, 0)
			st := [][2]int{{x, y}}
			comp[y*w+x] = id
			for len(st) > 0 {
				p := st[len(st)-1]
				st = st[:len(st)-1]
				sizes[id-1]++
				for _, d := range [][2]int{{1, 0}, {-1, 0}, {0, 1}, {0, -1}} {
					nx, ny := p[0]+d[0], p[1]+d[1]
					if nx < 0 || ny < 0 || nx >= w || ny >= h || comp[ny*w+nx] != 0 || im.Pix[(ny*w+nx)*4+3] == 0 {
						continue
					}
					comp[ny*w+nx] = id
					st = append(st, [2]int{nx, ny})
				}
			}
		}
	}
	max := 0
	for _, s := range sizes {
		if s > max {
			max = s
		}
	}
	for i, c := range comp {
		if c != 0 && sizes[c-1] < max/8 {
			im.Pix[i*4+3] = 0
		}
	}
	return im
}

// fit trims to content, scales to fit a square canvas (area-averaged, premultiplied) and centres.
func scaleOf(name string) float64 {
	if s, ok := scales[name]; ok {
		return s
	}
	return 1
}

func fit(im *image.NRGBA, extra float64) *image.NRGBA {
	b := im.Bounds()
	minX, minY, maxX, maxY := b.Max.X, b.Max.Y, 0, 0
	for y := b.Min.Y; y < b.Max.Y; y++ {
		for x := b.Min.X; x < b.Max.X; x++ {
			if im.NRGBAAt(x, y).A > 0 {
				minX, maxX = min(minX, x), max(maxX, x)
				minY, maxY = min(minY, y), max(maxY, y)
			}
		}
	}
	cw, ch := maxX-minX+1, maxY-minY+1
	const pad = 6
	scale := extra * float64(out-2*pad) / float64(max(cw, ch))
	nw, nh := int(float64(cw)*scale+0.5), int(float64(ch)*scale+0.5)
	res := image.NewNRGBA(image.Rect(0, 0, out, out))
	ox, oy := (out-nw)/2, (out-nh)/2
	for y := 0; y < nh; y++ {
		for x := 0; x < nw; x++ {
			x0, x1 := float64(x)/scale, float64(x+1)/scale
			y0, y1 := float64(y)/scale, float64(y+1)/scale
			var r, g, bl, a, n float64
			for sy := int(y0); float64(sy) < y1 && minY+sy <= maxY; sy++ {
				for sx := int(x0); float64(sx) < x1 && minX+sx <= maxX; sx++ {
					c := im.NRGBAAt(minX+sx, minY+sy)
					al := float64(c.A)
					r += float64(c.R) * al
					g += float64(c.G) * al
					bl += float64(c.B) * al
					a += al
					n++
				}
			}
			if n == 0 || a == 0 {
				continue
			}
			res.SetNRGBA(ox+x, oy+y, color.NRGBA{uint8(r / a), uint8(g / a), uint8(bl / a), uint8(a / n)})
		}
	}
	return res
}
