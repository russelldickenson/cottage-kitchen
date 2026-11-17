// dishes turns artwork (flat-background JPEGs or PNGs in src/) into transparent square PNGs for the game:
// dish_*.jpeg become 384px dish pictures (bottom-aligned), crop_*.jpeg/png become 192px board tiles (centred).
// Vector sources sit beside their raster: crop_sugar.png was rendered from crop_sugar.svg (macOS: qlmanage -t -s 800 -o . crop_sugar.svg).
// Usage (from the repo root): go run ./tools/dishes   (or: cd tools/dishes && go run . )
package main

import (
	"fmt"
	"image"
	"image/color"
	_ "image/jpeg"
	"image/png"
	"math"
	"os"
	"path/filepath"
	"strings"
)

const (
	dishSize = 384
	tileSize = 192
)

// holes lists pictures with background showing through enclosed gaps (a bow, a handle), which are cleared too.
var holes = map[string]bool{"strawberry_jam": true}

// tight lists pictures whose dish has light grey parts (a plate) that a normal tolerance would eat.
var tight = map[string]float64{"pumpkin_soup": 4.5}

// shrink scales pictures that read too big next to the other tiles (a flat square fills its whole canvas).
var shrink = map[string]float64{"sugar": 0.75}

// crops restricts a picture to the dish itself when it holds extra props.
var crops = map[string]image.Rectangle{
	"honey_cake": image.Rect(330, 90, 1080, 690),
}

func main() {
	root := "."
	if _, err := os.Stat("src"); err != nil {
		root = "tools/dishes"
	}
	dst := filepath.Join(root, "../../app/src/main/res/drawable-nodpi")
	var files []string
	for _, pat := range []string{"dish_*.jpeg", "dish_*.png", "crop_*.jpeg", "crop_*.png"} {
		m, _ := filepath.Glob(filepath.Join(root, "src", pat))
		files = append(files, m...)
	}
	for _, f := range files {
		name := strings.TrimSuffix(strings.TrimSuffix(filepath.Base(f), ".jpeg"), ".png")
		im := load(f)
		key := strings.TrimPrefix(strings.TrimPrefix(name, "dish_"), "crop_")
		if r, ok := crops[key]; ok {
			im = sub(im, r)
		}
		tol := 16.0
		if t, ok := tight[key]; ok {
			tol = t
		}
		keyOut(im, tol, holes[key])
		im = largest(im)
		var res *image.NRGBA
		if strings.HasPrefix(name, "crop_") {
			sc := 1.0
			if v, ok := shrink[key]; ok {
				sc = v
			}
			res = fit(im, tileSize, 6, false, sc)
		} else {
			res = fit(im, dishSize, 8, true, 1)
		}
		o, _ := os.Create(filepath.Join(dst, name+".png"))
		png.Encode(o, res)
		o.Close()
		if name == "dish_strawberry_jam" {
			// the launcher icon is built from a large copy of the jar (see tools/icon)
			big, _ := os.Create(filepath.Join(root, "../icon/jam.png"))
			png.Encode(big, fit(im, 1024, 16, true, 1))
			big.Close()
		}
		fmt.Println("wrote", name)
	}
}

func load(p string) *image.NRGBA {
	f, err := os.Open(p)
	if err != nil {
		panic(err)
	}
	defer f.Close()
	src, _, err := image.Decode(f)
	if err != nil {
		panic(err)
	}
	b := src.Bounds()
	im := image.NewNRGBA(image.Rect(0, 0, b.Dx(), b.Dy()))
	for y := 0; y < b.Dy(); y++ {
		for x := 0; x < b.Dx(); x++ {
			im.Set(x, y, src.At(b.Min.X+x, b.Min.Y+y))
		}
	}
	return im
}

func sub(im *image.NRGBA, r image.Rectangle) *image.NRGBA {
	res := image.NewNRGBA(image.Rect(0, 0, r.Dx(), r.Dy()))
	for y := 0; y < r.Dy(); y++ {
		for x := 0; x < r.Dx(); x++ {
			res.SetNRGBA(x, y, im.NRGBAAt(r.Min.X+x, r.Min.Y+y))
		}
	}
	return res
}

// near reports whether c looks like the background or a shadow on it: the background colour, scaled darker or lighter.
func near(c, bg color.NRGBA, tol float64) bool {
	r, g, b := float64(c.R), float64(c.G), float64(c.B)
	br, bgg, bb := float64(bg.R), float64(bg.G), float64(bg.B)
	k := (r*br + g*bgg + b*bb) / (br*br + bgg*bgg + bb*bb)
	if k < 0.55 || k > 1.12 {
		return false
	}
	dr, dg, db := r-k*br, g-k*bgg, b-k*bb
	return math.Sqrt(dr*dr+dg*dg+db*db) < tol
}

// keyOut makes the background transparent: a flood fill from the border through background-like pixels, then two
// passes that trim the light fringe left around the outline.
func keyOut(im *image.NRGBA, tol float64, holes bool) {
	w, h := im.Bounds().Dx(), im.Bounds().Dy()
	bg := im.NRGBAAt(3, 3)
	isBg := make([]bool, w*h)
	var q [][2]int
	push := func(x, y int, tol float64) {
		if x < 0 || y < 0 || x >= w || y >= h || isBg[y*w+x] || !near(im.NRGBAAt(x, y), bg, tol) {
			return
		}
		isBg[y*w+x] = true
		q = append(q, [2]int{x, y})
	}
	for x := 0; x < w; x++ {
		push(x, 0, tol)
		push(x, h-1, tol)
	}
	for y := 0; y < h; y++ {
		push(0, y, tol)
		push(w-1, y, tol)
	}
	for len(q) > 0 {
		p := q[len(q)-1]
		q = q[:len(q)-1]
		for _, d := range [][2]int{{1, 0}, {-1, 0}, {0, 1}, {0, -1}} {
			push(p[0]+d[0], p[1]+d[1], tol)
		}
	}
	for pass := 0; pass < 2; pass++ {
		var edge []int
		for y := 1; y < h-1; y++ {
			for x := 1; x < w-1; x++ {
				i := y*w + x
				if !isBg[i] && (isBg[i-1] || isBg[i+1] || isBg[i-w] || isBg[i+w]) && near(im.NRGBAAt(x, y), bg, 2*tol+2) {
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
	if holes {
		for y := 0; y < h; y++ {
			for x := 0; x < w; x++ {
				c := im.NRGBAAt(x, y)
				dr, dg, db := float64(c.R)-float64(bg.R), float64(c.G)-float64(bg.G), float64(c.B)-float64(bg.B)
				if dr*dr+dg*dg+db*db < 14*14 {
					im.Pix[(y*w+x)*4+3] = 0
				}
			}
		}
	}
}

// largest drops stray pieces: anything much smaller than the main blob.
func largest(im *image.NRGBA) *image.NRGBA {
	w, h := im.Bounds().Dx(), im.Bounds().Dy()
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
	mx := 0
	for _, s := range sizes {
		mx = max(mx, s)
	}
	for i, c := range comp {
		if c != 0 && sizes[c-1] < mx/10 {
			im.Pix[i*4+3] = 0
		}
	}
	return im
}

// fit trims to the content and scales it (area-averaged) to fill a square canvas, bottom-aligned or centred.
func fit(im *image.NRGBA, out, pad int, bottom bool, extra float64) *image.NRGBA {
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
	scale := extra * float64(out-2*pad) / float64(max(cw, ch))
	nw, nh := int(float64(cw)*scale+0.5), int(float64(ch)*scale+0.5)
	res := image.NewNRGBA(image.Rect(0, 0, out, out))
	ox, oy := (out-nw)/2, (out-nh)/2
	if bottom {
		oy = out - pad - nh
	}
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
