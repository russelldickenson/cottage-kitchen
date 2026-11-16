package com.farmharvest.game;

import static com.farmharvest.game.GameView.COLS;
import static com.farmharvest.game.GameView.ROWS;
import static com.farmharvest.game.Gfx.CREAM;
import static com.farmharvest.game.Gfx.GREEN;
import static com.farmharvest.game.Gfx.ORANGE;
import static com.farmharvest.game.Gfx.PANEL;
import static com.farmharvest.game.Gfx.WOOD;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import com.farmharvest.game.GameView.Button;
import com.farmharvest.game.GameView.Phase;
import com.farmharvest.game.GameView.Screen;

import java.util.ArrayList;
import java.util.List;

/** Draws every screen from the state held by GameView. Owns the artwork bitmaps and paints. */
final class Renderer {
    private final GameView v;
    private final Gfx gfx;
    private final Effects fx;
    private final Bitmap[] art = new Bitmap[Art.COUNT];
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint sky = new Paint();
    private final Paint plain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path hill1 = new Path(), hill2 = new Path();
    final FarmRenderer farm;
    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    Renderer(Context ctx, GameView v, Gfx gfx, Effects fx) {
        this.v = v;
        this.gfx = gfx;
        this.fx = fx;
        this.farm = new FarmRenderer(v, this, gfx);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        for (int i = 0; i < Art.COUNT; i++) art[i] = BitmapFactory.decodeResource(ctx.getResources(), Art.RES[i], o);
    }

    /** Rebuilds the size-dependent background shapes. */
    void onSize(float W, float H) {
        sky.setShader(new LinearGradient(0, 0, 0, H, 0xFF6EC8F5, 0xFFE4F6FF, Shader.TileMode.CLAMP));
        hill1.reset();
        hill1.moveTo(0, H * 0.80f);
        hill1.quadTo(W * 0.25f, H * 0.68f, W * 0.55f, H * 0.78f);
        hill1.quadTo(W * 0.82f, H * 0.87f, W, H * 0.74f);
        hill1.lineTo(W, H);
        hill1.lineTo(0, H);
        hill1.close();
        hill2.reset();
        hill2.moveTo(0, H * 0.90f);
        hill2.quadTo(W * 0.3f, H * 0.80f, W * 0.65f, H * 0.90f);
        hill2.quadTo(W * 0.85f, H * 0.96f, W, H * 0.87f);
        hill2.lineTo(W, H);
        hill2.lineTo(0, H);
        hill2.close();
    }

    void draw(Canvas c) {
        drawBackground(c);
        if (v.screen == Screen.TITLE) drawTitle(c);
        else if (v.screen == Screen.FARM) farm.drawFarm(c);
        else if (v.screen == Screen.LEVELS) drawLevels(c);
        else drawPlay(c);
    }

    private void drawBackground(Canvas c) {
        c.drawRect(0, 0, v.W, v.H, sky);
        gfx.circle(c, v.W * 0.85f, v.H * 0.12f + v.topInset * 0.3f, 11 * v.u, 0x55FFF3A0);
        gfx.circle(c, v.W * 0.85f, v.H * 0.12f + v.topInset * 0.3f, 7 * v.u, 0xFFFFE066);
        for (int i = 0; i < 3; i++) {
            float x = ((v.time * (3 + i * 1.5f) * v.u + i * v.W * 0.45f) % (v.W * 1.5f)) - v.W * 0.25f;
            float y = v.H * (0.13f + i * 0.09f);
            float s = v.u * (5 + i);
            gfx.ellipse(c, x, y, s * 2.6f, s * 1.1f, 0xCCFFFFFF);
            gfx.circle(c, x - s * 0.8f, y - s * 0.6f, s * 1.2f, 0xCCFFFFFF);
            gfx.circle(c, x + s * 0.7f, y - s * 0.8f, s * 1.5f, 0xCCFFFFFF);
        }
        plain.setColor(0xFF8BD450);
        c.drawPath(hill1, plain);
        plain.setColor(0xFF68B83A);
        c.drawPath(hill2, plain);
    }

    void drawIcon(Canvas c, int type, float cx, float cy, float size) {
        Bitmap b = art[type];
        src.set(0, 0, b.getWidth(), b.getHeight());
        dst.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        c.drawBitmap(b, src, dst, bmp);
    }

    void drawCoinPill(Canvas c, float l, float t, float r, float b, int value) {
        float h = b - t;
        gfx.rrect(c, l, t + h * 0.07f, r, b + h * 0.07f, h / 2, Gfx.darken(PANEL, 0.6f));
        gfx.rrect(c, l, t, r, b, h / 2, PANEL);
        gfx.rrectLine(c, l + h * 0.03f, t + h * 0.03f, r - h * 0.03f, b - h * 0.03f, h / 2, h * 0.06f, 0xFFC98A4B);
        float s = 1f + 0.25f * fx.bump;
        c.save();
        c.scale(s, s, l + h * 0.2f, (t + b) / 2);
        gfx.coinPile(c, l + h * 0.2f, (t + b) / 2, h * 0.5f);
        c.restore();
        gfx.textC(c, String.valueOf(value), l + h * 0.9f, (t + b) / 2, h * 0.55f, Color.WHITE, 0xFF3A1F08, Paint.Align.LEFT);
    }

    private void drawTitle(Canvas c) {
        c.save();
        c.rotate(-4, v.W / 2, v.H * 0.27f);
        gfx.text(c, "Farm", v.W / 2, v.H * 0.27f, v.W * 0.24f, 0xFFFFD43B, 0xFF7A3E0B, Paint.Align.CENTER);
        gfx.text(c, "Harvest", v.W / 2, v.H * 0.27f + v.W * 0.2f, v.W * 0.2f, 0xFF7ED957, 0xFF1F5F10, Paint.Align.CENTER);
        c.restore();
        int[] show = {1, 0, 5, 4, 2};
        for (int i = 0; i < show.length; i++) {
            float x = v.W * (0.14f + i * 0.18f);
            float y = v.H * 0.5f - Math.abs((float) Math.sin(v.time * 3 + i * 0.9f)) * 4 * v.u;
            gfx.ellipse(c, x, v.H * 0.5f + v.W * 0.085f, v.W * 0.06f, v.W * 0.012f, 0x33000000);
            drawIcon(c, show[i], x, y, v.W * 0.18f);
        }
        float m = 3 * v.u, hh = 11 * v.u;
        drawCoinPill(c, v.W * 0.52f, v.topInset + m, v.W - m, v.topInset + m + hh, Math.round(v.dispCoins));
        drawButtons(c);
    }

    private void drawLevels(Canvas c) {
        float m = 3 * v.u;
        gfx.textC(c, "Choose a Level", v.W / 2 + 3 * v.u, v.topInset + m + 6.5f * v.u, 7 * v.u, Color.WHITE, 0xFF1F5F10, Paint.Align.CENTER);
        drawButtons(c);
    }

    private void drawPlay(Canvas c) {
        float m = 3 * v.u, hh = 13 * v.u, hudTop = v.topInset + m;
        // coin counter + moves
        float mw = 27 * v.u;
        drawCoinPill(c, m + hh + m * 0.8f + hh * 0.4f, hudTop, v.W - m - mw - m * 0.8f, hudTop + hh, Math.round(v.dispCoins));
        float ml = v.W - m - mw, mr = v.W - m;
        gfx.rrect(c, ml, hudTop + hh * 0.07f, mr, hudTop + hh * 1.07f, hh / 2, Gfx.darken(PANEL, 0.6f));
        gfx.rrect(c, ml, hudTop, mr, hudTop + hh, hh / 2, PANEL);
        gfx.rrectLine(c, ml + hh * 0.03f, hudTop + hh * 0.03f, mr - hh * 0.03f, hudTop + hh * 0.97f, hh / 2, hh * 0.06f, 0xFFC98A4B);
        gfx.textC(c, "MOVES", (ml + mr) / 2, hudTop + hh * 0.26f, hh * 0.22f, 0xFFFFE8B0, 0xFF3A1F08, Paint.Align.CENTER);
        gfx.textC(c, String.valueOf(v.movesLeft), (ml + mr) / 2, hudTop + hh * 0.66f, hh * 0.52f,
                v.movesLeft <= 3 ? 0xFFFF7A66 : Color.WHITE, 0xFF3A1F08, Paint.Align.CENTER);


        // board
        gfx.rrect(c, v.boardPanel.left, v.boardPanel.top + 1.2f * v.u, v.boardPanel.right, v.boardPanel.bottom + 1.2f * v.u, 4 * v.u, Gfx.darken(WOOD, 0.6f));
        gfx.rrect(c, v.boardPanel.left, v.boardPanel.top, v.boardPanel.right, v.boardPanel.bottom, 4 * v.u, WOOD);
        gfx.rrect(c, v.boardPanel.left + v.u * 0.7f, v.boardPanel.top + v.u * 0.7f, v.boardPanel.right - v.u * 0.7f, v.boardPanel.bottom - v.u * 0.7f, 3.4f * v.u, 0xFFE9CF93);
        for (int r = 0; r < ROWS; r++) {
            for (int col = 0; col < COLS; col++) {
                float x = v.bx + col * v.cell, y = v.by + r * v.cell, g = v.cell * 0.04f;
                gfx.rrect(c, x + g, y + g, x + v.cell - g, y + v.cell - g, v.cell * 0.2f, (r + col) % 2 == 0 ? 0xFFF8E7B4 : 0xFFF1DB9C);
            }
        }
        if (v.board != null) {
            c.save();
            c.clipRect(v.bx, v.by, v.bx + v.cell * COLS, v.by + v.cell * ROWS);
            if (v.selected != null) {
                float x = v.bx + v.selected.c * v.cell, y = v.by + v.selected.r * v.cell, g = v.cell * 0.03f;
                gfx.rrect(c, x + g, y + g, x + v.cell - g, y + v.cell - g, v.cell * 0.2f, 0x66FFFFFF);
                gfx.rrectLine(c, x + g, y + g, x + v.cell - g, y + v.cell - g, v.cell * 0.2f, v.cell * 0.06f, 0xFFFFFFFF);
            }
            for (int r = 0; r < ROWS; r++) {
                for (int col = 0; col < COLS; col++) {
                    Tile t = v.board.g[r][col];
                    if (t == null) continue;
                    float extra = 1f;
                    if (t == v.selected) extra = 1f + 0.07f * (float) Math.sin(v.time * 12);
                    else if (v.hint != null && ((t.r == v.hint[0] && t.c == v.hint[1]) || (t.r == v.hint[2] && t.c == v.hint[3])))
                        extra = 1f + 0.1f * (float) Math.sin(v.time * 8);
                    if (t != v.fxTile) drawTile(c, t, extra);
                }
            }
            if (v.fxTile != null && v.phase == Phase.CLEAR) {
                drawFx(c);
                drawTile(c, v.fxTile, 1f);
            }
            c.restore();
        }

        if (v.farmStrip.height() > 0) {
            farm.drawStrip(c, v.farmStrip);
            drawGoals(c);
            farm.drawStripFlights(c);
        }

        for (Effects.Particle p : fx.particles) {
            float a = p.life / p.max;
            gfx.star(c, p.x, p.y, p.size * (0.5f + a), Gfx.alpha(p.color, a), 0);
        }
        for (Effects.Flyer f : fx.flyers) {
            if (f.delay > 0) continue;
            float t = f.t * f.t * (3 - 2 * f.t);
            float cpx = (f.sx + v.coinX) / 2 + (f.sx < v.W / 2 ? -1 : 1) * 10 * v.u;
            float cpy = Math.min(f.sy, v.coinY) - 12 * v.u;
            float x = (1 - t) * (1 - t) * f.sx + 2 * (1 - t) * t * cpx + t * t * v.coinX;
            float y = (1 - t) * (1 - t) * f.sy + 2 * (1 - t) * t * cpy + t * t * v.coinY;
            gfx.coin(c, x, y, v.cell * (0.3f - 0.12f * t));
        }
        for (Effects.Pop p : fx.pops) {
            float a = Math.min(1f, p.life / (p.max * 0.4f));
            float s = 1f + 0.25f * (float) Math.sin(Math.min(1f, (p.max - p.life) * 6f) * Math.PI / 2);
            gfx.textC(c, p.text, p.x, p.y, p.size * s, Gfx.alpha(p.color, a), Gfx.alpha(0xFF4A2808, a), Paint.Align.CENTER);
        }

        if (v.confirmQuit) {
            drawConfirm(c);
        } else if (v.phase == Phase.TIP) drawTip(c);
        else drawButtons(c);
    }

    /** The level's goals as a bar along the top of the farm strip. While farming for the herd it shows what the herd is still short of, with Bank beside it. */
    private void drawGoals(Canvas c) {
        RectF p = v.goalPanel;
        if (v.level == null) return;
        gfx.rrect(c, p.left, p.top, p.right, p.bottom, 3.4f * v.u, Gfx.alpha(CREAM, 0.92f));
        int n = v.level.goalKinds.length;
        // while farming for the herd the Bank button takes the right end of the bar
        float usable = p.width() - 2 * v.u - (v.farming ? v.bankRect.width() : 0);
        float slot = usable / n, size = Math.min(p.height() * 0.62f, slot * 0.4f);
        for (int i = 0; i < n; i++) {
            int kind = v.level.goalKinds[i];
            float x = p.left + v.u + slot * i, tx = x + size * 1.3f + 1.6f * v.u, tw = slot - size * 1.3f - 2.6f * v.u;
            boolean done = v.need[kind] == 0;
            // covered before the level began, by stock carried over from earlier levels: not something the player just did
            boolean stocked = done && v.level.goalCounts[i] == 0;
            float iy = p.centerY();
            drawIcon(c, kind, x + size * 0.6f, iy, size);
            if (done) gfx.check(c, x + size * 0.95f, iy + size * 0.3f, size * 0.26f);
            float top = Math.min(5f * v.u, p.height() * 0.42f);
            if (stocked) gfx.textC(c, "Stocked", tx, p.top + p.height() * 0.36f, top * 0.8f, 0xFF4F7A2A, 0, Paint.Align.LEFT);
            else gfx.textC(c, done ? "Done!" : String.valueOf(v.need[kind]), tx, p.top + p.height() * 0.36f, top,
                    done ? 0xFF4CBB2C : Color.WHITE, done ? 0xFFFFFFFF : 0xFF8B5A2B, Paint.Align.LEFT);
            float ls = 2.7f * v.u;
            String label = goalLabel(kind, ls, tw);
            int onHand = Farm.stockOf(kind) >= 0 ? v.progress.herd.stock[Farm.stockOf(kind)] : 0;
            if (onHand > 0) {
                // how much was already in stock, when it fits
                String with = label + " · " + onHand + " in stock", shorter = label + " · " + onHand;
                label = gfx.width(with, ls) <= tw ? with : gfx.width(shorter, ls) <= tw ? shorter : label;
            }
            float lw = gfx.width(label, ls);
            gfx.textC(c, label, tx, p.top + p.height() * 0.76f, lw > tw ? ls * tw / lw : ls, 0xFF8B5A2B, 0, Paint.Align.LEFT);
        }
    }

    /** Who a goal is for: the animals that eat a stock, chicks for eggs, the market for everything else. */
    private String goalLabel(int tile, float size, float maxW) {
        int stock = Farm.stockOf(tile);
        if (stock < 0) return "to sell";
        if (stock == Farm.EGG) return "for chicks";
        Farm.Herd hd = v.progress.herd;
        List<String> who = new ArrayList<>();
        for (int sp = 0; sp < Farm.SPECIES; sp++) {
            int animals = hd.count[sp] + (sp == Farm.CHICKEN ? hd.chickCount() : 0);
            if (animals > 0 && Farm.FEED[sp][stock] > 0) who.add(Farm.ANIMAL_NAME[sp].toLowerCase());
        }
        if (who.isEmpty()) return "for the herd";
        // the full list if it fits, then the first species and a count, so the text never has to shrink to unreadable
        String full = "for " + String.join(", ", who);
        if (who.size() == 1 || gfx.width(full, size) <= maxW) return full;
        return "for " + who.get(0) + " +" + (who.size() - 1);
    }

    private void drawTile(Canvas c, Tile t, float extra) {
        float x = v.cx(t), y = v.cy(t);
        boolean fx = t == v.fxTile && v.phase == Phase.CLEAR;
        float rot = 0, mult = 1f, ox = 0, oy = 0;
        if (fx) {
            float k = v.clearTimer;
            switch (t.type) {
                case Art.SCARECROW:
                    rot = k * 1000f;
                    mult = 1.5f;
                    break;
                case Art.TNT:
                    mult = 1f + 0.6f * Math.min(1f, k * 4f);
                    ox = (float) Math.sin(k * 70) * v.cell * 0.03f;
                    break;
                case Art.GOLDEN_EGG:
                    mult = 1.4f + 0.1f * (float) Math.sin(k * 20);
                    gfx.circle(c, x, y, v.cell * (0.7f + 0.2f * (float) Math.sin(k * 18)), 0x66FFE066);
                    break;
                case Art.TRACTOR:
                    if (k > 0.15f) return; // the tractor itself is driving across the v.board
                    mult = 1f + k * 2f;
                    break;
            }
        }
        float size = v.cell * 0.92f * t.scale * extra * mult;
        float w = size * (1f + 0.12f * t.squash), h = size * (1f - 0.18f * t.squash);
        float bottom = y + oy + size / 2;
        if (!fx) gfx.ellipse(c, x, y + v.cell * 0.38f, v.cell * 0.3f * t.scale, v.cell * 0.06f * t.scale, 0x26000000);
        Bitmap b = art[t.type];
        src.set(0, 0, b.getWidth(), b.getHeight());
        dst.set(x + ox - w / 2, bottom - h, x + ox + w / 2, bottom);
        c.save();
        if (rot != 0) c.rotate(rot, x + ox, y + oy);
        c.drawBitmap(b, src, dst, bmp);
        c.restore();
    }

    /** Effects around the power-up that is currently being played. */
    private void drawFx(Canvas c) {
        float x = v.cx(v.fxTile), y = v.cy(v.fxTile), k = v.clearTimer;
        switch (v.fxTile.type) {
            case Art.GOLDEN_EGG:
                for (Tile t : v.dying) {
                    if (t == v.fxTile) continue;
                    float a = 1f - (k - t.dyDelay) / 0.3f;
                    if (k >= t.dyDelay - 0.05f && a > 0f)
                        gfx.beam(c, x, y, v.cx(t), v.cy(t), v.cell * 0.14f * Math.min(1f, a), Gfx.alpha(0xFFFFD43B, Math.min(1f, a)));
                }
                gfx.circle(c, x, y, v.cell * (0.6f + k * 3f), Gfx.alpha(0xFFFFF3B0, Math.max(0f, 0.45f - k)));
                break;
            case Art.TNT: {
                float a = Math.max(0f, 1f - k * 2.5f);
                float rad = v.cell * (0.4f + k * 9f);
                gfx.circle(c, x, y, rad * 0.85f, Gfx.alpha(0xFFFFE08A, a * 0.55f));
                gfx.circleLine(c, x, y, rad, v.cell * 0.22f * a, Gfx.alpha(0xFFFF7A00, a));
                break;
            }
            case Art.SCARECROW: {
                float a = Math.max(0f, 1f - k * 2f);
                for (int i = 0; i < 10; i++) {
                    double ang = k * 14 + i * Math.PI / 5;
                    float rr = v.cell * (0.5f + k * 5f);
                    gfx.star(c, x + (float) Math.cos(ang) * rr, y + (float) Math.sin(ang) * rr, v.cell * 0.14f, Gfx.alpha(0xFFFFD25A, a), 0);
                }
                break;
            }
            case Art.TRACTOR: {
                float tx = v.bx - v.cell * 0.5f + (k / 0.08f) * v.cell;
                if (tx < v.bx + v.cell * (COLS + 1)) {
                    float size = v.cell * 1.5f;
                    Bitmap b = art[Art.TRACTOR];
                    src.set(0, 0, b.getWidth(), b.getHeight());
                    float bob = (float) Math.sin(k * 40) * v.cell * 0.02f;
                    dst.set(tx - size / 2, y + v.cell * 0.45f - size + bob, tx + size / 2, y + v.cell * 0.45f + bob);
                    gfx.ellipse(c, tx, y + v.cell * 0.45f, size * 0.4f, v.cell * 0.07f, 0x30000000);
                    c.drawBitmap(b, src, dst, bmp);
                }
                break;
            }
        }
    }

    private static final String[] TIP_NAME = {"Scarecrow", "TNT", "Golden Egg", "Rainbow Tractor"};
    private static final String[] TIP_USE = {
            "Swap it with a neighbour and it spins, sweeping away the 3x3 area around it.",
            "Swap it with a neighbour to blow up the 3x3 area around it.",
            "Swap it with any crop to harvest every one of that crop on the board.",
            "Swap it with any crop and it drives across the board, harvesting every one of that crop.",
    };
    private static final String[] TIP_MAKE = {
            "Make one by matching a T or L shape.",
            "Make one by matching a T or L shape.",
            "Make one by matching 5 in a row.",
            "Make one by matching 5 in a row.",
    };

    List<String> wrap(String text, float size, float maxW) {
        List<String> lines = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ")) {
            String next = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && gfx.width(next, size) > maxW) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(next);
            }
        }
        if (cur.length() > 0) lines.add(cur.toString());
        return lines;
    }

    private void drawTip(Canvas c) {
        c.drawColor(Color.argb((int) (150 * Math.min(1f, v.tipT * 4f)), 0, 0, 0));
        float k = Math.min(1f, v.tipT * 2.5f);
        float ease = 1f + 2.70158f * (float) Math.pow(k - 1, 3) + 1.70158f * (float) Math.pow(k - 1, 2);
        c.save();
        c.translate(0, (1f - ease) * -v.H * 0.25f * (k < 1 ? 1 : 0));
        RectF o = v.tipBox;
        float ph = o.height(), idx = v.tipType - Art.CROPS;
        gfx.rrect(c, o.left, o.top + 1.5f * v.u, o.right, o.bottom + 1.5f * v.u, 6 * v.u, Gfx.darken(WOOD, 0.6f));
        gfx.rrect(c, o.left, o.top, o.right, o.bottom, 6 * v.u, WOOD);
        gfx.rrect(c, o.left + v.u, o.top + v.u, o.right - v.u, o.bottom - v.u, 5 * v.u, CREAM);
        gfx.rrect(c, o.left - v.u, o.top - 3 * v.u, o.right + v.u, o.top + ph * 0.14f, 5 * v.u, ORANGE);
        gfx.textC(c, "New Power-Up!", o.centerX(), o.top + ph * 0.045f, 7 * v.u, Color.WHITE, 0xFF7A3E0B, Paint.Align.CENTER);

        float iy = o.top + ph * 0.30f, isz = v.W * 0.3f + 2 * v.u * (float) Math.sin(v.time * 4);
        gfx.circle(c, o.centerX(), iy, isz * 0.62f, 0x55FFE066);
        drawIcon(c, v.tipType, o.centerX(), iy, isz);
        gfx.textC(c, TIP_NAME[(int) idx], o.centerX(), o.top + ph * 0.485f, 6.6f * v.u, 0xFFFFC928, 0xFF7A3E0B, Paint.Align.CENTER);

        float maxW = o.width() - 12 * v.u, size = 4.4f * v.u, ly = o.top + ph * 0.56f;
        for (String line : wrap(TIP_USE[(int) idx], size, maxW)) {
            gfx.textC(c, line, o.centerX(), ly, size, 0xFF6B3F17, 0, Paint.Align.CENTER);
            ly += size * 1.35f;
        }
        ly += size * 0.5f;
        for (String line : wrap(TIP_MAKE[(int) idx], size, maxW)) {
            gfx.textC(c, line, o.centerX(), ly, size, 0xFFD06A00, 0, Paint.Align.CENTER);
            ly += size * 1.35f;
        }
        drawButtons(c);
        c.restore();
    }

    private void drawConfirm(Canvas c) {
        c.drawColor(Color.argb(150, 0, 0, 0));
        RectF o = v.confirmBox;
        float ph = o.height();
        gfx.rrect(c, o.left, o.top + 1.5f * v.u, o.right, o.bottom + 1.5f * v.u, 6 * v.u, Gfx.darken(WOOD, 0.6f));
        gfx.rrect(c, o.left, o.top, o.right, o.bottom, 6 * v.u, WOOD);
        gfx.rrect(c, o.left + v.u, o.top + v.u, o.right - v.u, o.bottom - v.u, 5 * v.u, CREAM);
        gfx.textC(c, "Quit this level?", o.centerX(), o.top + ph * 0.12f, 7 * v.u, 0xFF6B3F17, 0, Paint.Align.CENTER);
        gfx.textC(c, "Your progress in it will be lost.", o.centerX(), o.top + ph * 0.28f, 4.2f * v.u, 0xFF8B5A2B, 0, Paint.Align.CENTER);
        drawButtons(c);
    }


    void drawButtons(Canvas c) {
        for (Button b : v.buttons) drawButton(c, b);
    }

    private void drawButton(Canvas c, Button b) {
        if (b.hidden) return;
        float w = b.r.width(), h = b.r.height();
        float depth = h * 0.09f;
        float off = b.pressed ? depth * 0.8f : 0f;
        int base = b.locked ? 0xFFA8A8A8 : b.color;
        float cx = b.r.centerX(), cy = b.r.centerY();
        if (b.circle) {
            float rad = w / 2;
            gfx.circle(c, cx, cy + depth, rad, Gfx.darken(base, 0.62f));
            gfx.circle(c, cx, cy + off, rad, base);
            gfx.circle(c, cx, cy + off, rad * 0.86f, Gfx.lighten(base, 0.18f));
            gfx.ellipse(c, cx, cy + off - rad * 0.45f, rad * 0.55f, rad * 0.28f, 0x55FFFFFF);
        } else {
            float rad = h * 0.32f;
            gfx.rrect(c, b.r.left, b.r.top + depth, b.r.right, b.r.bottom + depth, rad, Gfx.darken(base, 0.62f));
            gfx.rrect(c, b.r.left, b.r.top + off, b.r.right, b.r.bottom + off, rad, base);
            gfx.rrect(c, b.r.left + h * 0.08f, b.r.top + off + h * 0.07f, b.r.right - h * 0.08f, b.r.top + off + h * 0.45f, rad * 0.7f, 0x44FFFFFF);
        }
        if (b.back) gfx.backArrow(c, cx, cy + off, w * 0.5f);
        else if (b.speaker) gfx.speaker(c, cx, cy + off, w * 0.42f, b.muted);
        else if (b.locked) gfx.lock(c, cx, cy + off, w * 0.26f);
        else if (b.circle) {
            boolean hasStars = b.stars >= 0;
            gfx.textC(c, b.text, cx, cy + off - (hasStars ? w * 0.06f : 0), w * 0.42f, Color.WHITE, Gfx.darken(base, 0.45f), Paint.Align.CENTER);
            if (hasStars) {
                for (int i = 0; i < 3; i++) {
                    boolean on = i < b.stars;
                    gfx.star(c, cx + (i - 1) * w * 0.22f, cy + off + w * 0.31f - (i == 1 ? 0 : w * 0.03f), w * 0.1f,
                            on ? 0xFFFFD43B : 0xFF7A8F60, on ? 0xFFC97700 : 0xFF4E5E3C);
                }
            }
        } else {
            gfx.textC(c, b.text, cx, cy + off, h * 0.42f, Color.WHITE, Gfx.darken(base, 0.45f), Paint.Align.CENTER);
        }
    }
}
