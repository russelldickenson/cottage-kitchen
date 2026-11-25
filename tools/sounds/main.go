// sounds synthesises the game's sound effects as small mono 16-bit WAV files.
// Run from the repo root: (cd tools/sounds && go run . ../../app/src/main/res/raw)
package main

import (
	"encoding/binary"
	"math"
	"math/rand"
	"os"
	"path/filepath"
)

const rate = 22050

type buf []float64

func newBuf(sec float64) buf { return make(buf, int(sec*rate)) }

func (b buf) at(t float64) int { return int(t * rate) }

// add mixes src into b starting at sec.
func (b buf) add(src buf, sec, gain float64) {
	o := b.at(sec)
	for i, v := range src {
		if o+i >= len(b) {
			break
		}
		b[o+i] += v * gain
	}
}

// tone renders a sine (with optional harmonics) that sweeps f0->f1 with an exponential decay.
func tone(sec, f0, f1, decay float64, harm ...float64) buf {
	b := newBuf(sec)
	phase := 0.0
	for i := range b {
		t := float64(i) / rate
		f := f0 + (f1-f0)*t/sec
		phase += 2 * math.Pi * f / rate
		v := math.Sin(phase)
		for h, g := range harm {
			v += g * math.Sin(phase*float64(h+2))
		}
		env := math.Exp(-decay * t)
		if t < 0.004 {
			env *= t / 0.004
		}
		b[i] = v * env
	}
	return b
}

func noise(sec, decay, smooth float64, rng *rand.Rand) buf {
	b := newBuf(sec)
	last := 0.0
	for i := range b {
		t := float64(i) / rate
		last += (rng.Float64()*2 - 1 - last) * smooth
		b[i] = last * math.Exp(-decay*t)
	}
	return b
}

func normalise(b buf, peak float64) {
	m := 0.0
	for _, v := range b {
		m = math.Max(m, math.Abs(v))
	}
	if m == 0 {
		return
	}
	for i := range b {
		b[i] *= peak / m
	}
	// short fade-out so nothing clicks
	n := rate / 200
	for i := 0; i < n && i < len(b); i++ {
		b[len(b)-1-i] *= float64(i) / float64(n)
	}
}

func write(dir, name string, b buf) {
	normalise(b, 0.8)
	f, err := os.Create(filepath.Join(dir, name+".wav"))
	if err != nil {
		panic(err)
	}
	defer f.Close()
	data := make([]int16, len(b))
	for i, v := range b {
		data[i] = int16(v * 32767)
	}
	le := binary.LittleEndian
	f.Write([]byte("RIFF"))
	binary.Write(f, le, uint32(36+len(data)*2))
	f.Write([]byte("WAVEfmt "))
	binary.Write(f, le, uint32(16))
	binary.Write(f, le, uint16(1))
	binary.Write(f, le, uint16(1))
	binary.Write(f, le, uint32(rate))
	binary.Write(f, le, uint32(rate*2))
	binary.Write(f, le, uint16(2))
	binary.Write(f, le, uint16(16))
	f.Write([]byte("data"))
	binary.Write(f, le, uint32(len(data)*2))
	binary.Write(f, le, data)
}

func note(n int) float64 { return 440 * math.Pow(2, float64(n-9)/12) } // semitones above middle C

func main() {
	dir := os.Args[1]
	rng := rand.New(rand.NewSource(7))

	// swap: soft tick
	b := newBuf(0.07)
	b.add(tone(0.07, 420, 260, 45), 0, 1)
	b.add(noise(0.07, 60, 0.3, rng), 0, 0.25)
	write(dir, "sfx_swap", b)

	// bad swap: low bonk
	b = newBuf(0.18)
	b.add(tone(0.18, 190, 110, 16, 0.4), 0, 1)
	write(dir, "sfx_bad", b)

	// pop: bubbly rising blip (pitched up by combo at play time)
	b = newBuf(0.13)
	b.add(tone(0.13, 480, 980, 22, 0.3), 0, 1)
	write(dir, "sfx_pop", b)

	// coin: two-note ting
	b = newBuf(0.3)
	b.add(tone(0.3, 1568, 1568, 14, 0.35, 0.15), 0, 0.8)
	b.add(tone(0.25, 2093, 2093, 16, 0.3), 0.07, 1)
	write(dir, "sfx_coin", b)

	// win: fanfare
	b = newBuf(1.5)
	for i, n := range []int{0, 4, 7, 12} {
		b.add(tone(0.35, note(n+12), note(n+12), 6, 0.5, 0.25), float64(i)*0.13, 0.7)
	}
	for _, n := range []int{12, 16, 19, 24} {
		b.add(tone(0.9, note(n), note(n), 3, 0.4, 0.2), 0.55, 0.5)
	}
	write(dir, "sfx_win", b)

	// lose: sad descending wah-wah
	b = newBuf(1.0)
	for i, n := range []int{7, 4, 0, -5} {
		d := 0.25
		if i == 3 {
			d = 0.4
		}
		b.add(tone(d+0.1, note(n), note(n)*0.97, 5, 0.3), float64(i)*0.22, 0.8)
	}
	write(dir, "sfx_lose", b)

	// click: tiny tick
	b = newBuf(0.04)
	b.add(tone(0.04, 900, 600, 80), 0, 1)
	write(dir, "sfx_click", b)
}
