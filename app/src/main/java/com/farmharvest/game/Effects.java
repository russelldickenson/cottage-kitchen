package com.farmharvest.game;

import android.graphics.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Short-lived visual effects: sparkles, coins flying to the counter or the bag, and floating text. */
final class Effects {
    static final class Particle {
        float x, y, vx, vy, life, max, size;
        int color;
    }

    /** A coin on its way somewhere. Start position is only used for coins heading to the counter. */
    static final class Flyer {
        float sx, sy, delay, t;
        int value;
    }

    static final class Pop {
        float x, y, life, max, size;
        String text;
        int color;
    }

    private static final float COUNTER_FLIGHT = 0.65f, BAG_FLIGHT = 0.5f;

    final List<Particle> particles = new ArrayList<>();
    final List<Flyer> flyers = new ArrayList<>();
    final List<Flyer> bagFlyers = new ArrayList<>();
    final List<Pop> pops = new ArrayList<>();
    /** Counter / bag "bounce" strength, 1 right after a coin lands. */
    float bump, bagBump;

    private int landed, bagLanded;
    private final Random rnd;
    private final Gfx gfx;

    Effects(Random rnd, Gfx gfx) {
        this.rnd = rnd;
        this.gfx = gfx;
    }

    void clear() {
        particles.clear();
        flyers.clear();
        bagFlyers.clear();
        pops.clear();
    }

    void update(float dt, float u) {
        bump = Math.max(0, bump - dt * 4);
        bagBump = Math.max(0, bagBump - dt * 5);
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
        for (int i = flyers.size() - 1; i >= 0; i--) {
            Flyer f = flyers.get(i);
            if (f.delay > 0) {
                f.delay -= dt;
                continue;
            }
            f.t += dt / COUNTER_FLIGHT;
            if (f.t >= 1) {
                landed += f.value;
                bump = 1;
                flyers.remove(i);
            }
        }
        for (int i = bagFlyers.size() - 1; i >= 0; i--) {
            Flyer f = bagFlyers.get(i);
            if (f.delay > 0) {
                f.delay -= dt;
                continue;
            }
            f.t += dt / BAG_FLIGHT;
            if (f.t >= 1) {
                bagLanded += f.value;
                bagBump = 1;
                bagFlyers.remove(i);
            }
        }
    }

    /** Coins that reached the counter since the last call. */
    int takeLanded() {
        int v = landed;
        landed = 0;
        return v;
    }

    /** Coins that reached the bag since the last call. */
    int takeBagLanded() {
        int v = bagLanded;
        bagLanded = 0;
        return v;
    }

    void coin(float fromX, float fromY, float delay, int value) {
        Flyer f = new Flyer();
        f.sx = fromX;
        f.sy = fromY;
        f.delay = delay;
        f.value = value;
        flyers.add(f);
    }

    void bagCoin(float delay, int value) {
        Flyer f = new Flyer();
        f.delay = delay;
        f.value = value;
        bagFlyers.add(f);
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
        p.size = tw > maxWidth ? size * maxWidth / tw : size;
        p.life = p.max = life;
        p.color = color;
        pops.add(p);
    }
}
