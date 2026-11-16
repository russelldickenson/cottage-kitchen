package com.zigzag.cottagekitchen;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Short-lived visual effects: sparkles and floating text. */
final class Effects {
    static final class Particle {
        float x, y, vx, vy, life, max, size;
        int color;
    }

    static final class Pop {
        float x, y, life, max, size;
        String text;
        int color;
    }

    final List<Particle> particles = new ArrayList<>();
    final List<Pop> pops = new ArrayList<>();

    private final Random rnd;
    private final Gfx gfx;

    Effects(Random rnd, Gfx gfx) {
        this.rnd = rnd;
        this.gfx = gfx;
    }

    void clear() {
        particles.clear();
        pops.clear();
    }

    void update(float dt, float u) {
        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            p.vy += 60 * u * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.life <= 0) particles.remove(i);
        }
        for (int i = pops.size() - 1; i >= 0; i--) {
            Pop p = pops.get(i);
            p.life -= dt;
            p.y -= 8 * u * dt;
            if (p.life <= 0) pops.remove(i);
        }
    }

    void sparkle(float x, float y, float u, float cell) {
        for (int i = 0; i < 7; i++) {
            Particle p = new Particle();
            double a = rnd.nextDouble() * Math.PI * 2;
            float sp = (15 + rnd.nextFloat() * 30) * u;
            p.x = x;
            p.y = y;
            p.vx = (float) Math.cos(a) * sp;
            p.vy = (float) Math.sin(a) * sp - 15 * u;
            p.life = p.max = 0.5f + rnd.nextFloat() * 0.3f;
            p.size = cell * (0.08f + rnd.nextFloat() * 0.1f);
            p.color = i % 2 == 0 ? 0xFFFFE066 : Color.WHITE;
            particles.add(p);
        }
    }

    /** Floating text that shrinks to fit maxWidth. */
    void pop(String text, float x, float y, float size, float life, int color, float maxWidth) {
        Pop p = new Pop();
        p.x = x;
        p.y = y;
        p.text = text;
        float tw = gfx.width(text, size);
        float fit = maxWidth / 1.3f; // pops swell by up to 1.25x when they appear
        p.size = tw > fit ? size * fit / tw : size;
        p.life = p.max = life;
        p.color = color;
        pops.add(p);
    }
}
