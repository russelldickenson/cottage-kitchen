package com.zigzag.cottagekitchen;

import static com.zigzag.cottagekitchen.GameView.COLS;
import static com.zigzag.cottagekitchen.GameView.ROWS;
import static com.zigzag.cottagekitchen.Gfx.CREAM;
import static com.zigzag.cottagekitchen.Gfx.GREEN;
import static com.zigzag.cottagekitchen.Gfx.ORANGE;
import static com.zigzag.cottagekitchen.Gfx.PANEL;
import static com.zigzag.cottagekitchen.Gfx.WOOD;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;

import com.zigzag.cottagekitchen.GameView.Button;
import com.zigzag.cottagekitchen.GameView.Phase;
import com.zigzag.cottagekitchen.GameView.Screen;

import java.util.ArrayList;
import java.util.List;

/** Draws every screen from the state held by GameView. Owns the artwork bitmaps and paints. */
final class Renderer {
    private final GameView v;
    private final Gfx gfx;
    private final Effects fx;
    private final Bitmap[] art = new Bitmap[Art.COUNT];
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private Bitmap backdrop;
    final CookRenderer cook;
    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    Renderer(Context ctx, GameView v, Gfx gfx, Effects fx) {
        this.v = v;
        this.gfx = gfx;
        this.fx = fx;
        this.cook = new CookRenderer(ctx, v, this, gfx);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        for (int i = 0; i < Art.COUNT; i++) art[i] = BitmapFactory.decodeResource(ctx.getResources(), Art.RES[i], o);
    }

    /** Redraws the kitchen backdrop for the new screen size. */
    void onSize(float W, float H) {
        if (backdrop != null) backdrop.recycle();
        backdrop = new Backdrop(gfx).build((int) W, (int) H);
    }

    void draw(Canvas c) {
        drawBackground(c);
        if (v.screen == Screen.TITLE) drawTitle(c);
        else if (v.screen == Screen.RESULT) cook.drawResult(c);
        else if (v.screen == Screen.BOOK) cook.drawBook(c);
        else drawPlay(c);
    }

    private void drawBackground(Canvas c) {
        if (backdrop != null) c.drawBitmap(backdrop, 0, 0, null);
    }

    void drawIcon(Canvas c, int type, float cx, float cy, float size, float alpha) {
        bmp.setAlpha((int) (255 * alpha));
        drawIcon(c, type, cx, cy, size);
        bmp.setAlpha(255);
    }

    void drawIcon(Canvas c, int type, float cx, float cy, float size) {
        Bitmap b = art[type];
        src.set(0, 0, b.getWidth(), b.getHeight());
        dst.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        c.drawBitmap(b, src, dst, bmp);
    }

    /** A wooden pill with a short label in it. */
    void drawPill(Canvas c, float l, float t, float r, float b, String text) {
        drawPill(c, l, t, r, b, text, -1);
    }

    /** A wooden pill with a short label and, when icon is a tile type, its picture at the left. */
    void drawPill(Canvas c, float l, float t, float r, float b, String text, int icon) {
        drawPill(c, l, t, r, b, text, icon, false);
    }

    /** compact trims the padding after the text, for a row of several pills. */
    void drawPill(Canvas c, float l, float t, float r, float b, String text, int icon, boolean compact) {
        float h = b - t;
        gfx.rrect(c, l, t + h * 0.07f, r, b + h * 0.07f, h / 2, Gfx.darken(PANEL, 0.6f));
        gfx.rrect(c, l, t, r, b, h / 2, PANEL);
        gfx.rrectLine(c, l + h * 0.03f, t + h * 0.03f, r - h * 0.03f, b - h * 0.03f, h / 2, h * 0.06f, 0xFFC98A4B);
        float left = l;
        if (icon >= 0) {
            drawIcon(c, icon, l + h * 0.62f, (t + b) / 2, h * 0.9f);
            left = l + h * 0.75f;
        }
        float size = h * 0.5f, tw = gfx.width(text, size), maxW = r - left - h * (compact ? 0.25f : 0.6f);
        gfx.textC(c, text, (left + r) / 2, (t + b) / 2, tw > maxW ? size * maxW / tw : size, Color.WHITE, 0xFF3A1F08, Paint.Align.CENTER);
    }

    private void drawTitle(Canvas c) {
        c.save();
        c.rotate(-4, v.W / 2, v.H * 0.27f);
        gfx.text(c, "Cottage", v.W / 2, v.H * 0.27f, v.W * 0.2f, 0xFFFFD43B, 0xFF7A3E0B, Paint.Align.CENTER);
        gfx.text(c, "Kitchen", v.W / 2, v.H * 0.27f + v.W * 0.2f, v.W * 0.2f, 0xFF7ED957, 0xFF1F5F10, Paint.Align.CENTER);
        c.restore();
        int[] show = {6, 12, 9, 1, 4};
        for (int i = 0; i < show.length; i++) {
            float x = v.W * (0.14f + i * 0.18f);
            float y = v.H * 0.5f - Math.abs((float) Math.sin(v.time * 3 + i * 0.9f)) * 4 * v.u;
            gfx.ellipse(c, x, v.H * 0.5f + v.W * 0.085f, v.W * 0.06f, v.W * 0.012f, 0x33000000);
            drawIcon(c, show[i], x, y, v.W * 0.18f);
        }
        drawButtons(c);
    }

    private void drawPlay(Canvas c) {
        float m = 3 * v.u, hh = 13 * v.u, hudTop = v.topInset + m;
        // level + moves
        float mw = 27 * v.u;
        float gl = m + hh + m * 0.8f, gr = v.W - m - mw - m * 0.8f;
        int goals = v.level.goalKinds.length;
        if (goals == 1) {
            drawPill(c, gl, hudTop, gr, hudTop + hh, (v.level.goalCounts[0] - v.need[v.level.ingredient()]) + "/" + v.level.goalCounts[0], v.level.ingredient());
        } else {
            // several goals share the space, each showing what is still to collect
            float gap = m * 0.5f, pw = (gr - gl - gap * (goals - 1)) / goals;
            for (int i = 0; i < goals; i++) {
                int kind = v.level.goalKinds[i];
                float pl = gl + i * (pw + gap);
                drawPill(c, pl, hudTop, pl + pw, hudTop + hh, "x" + v.need[kind], kind, true);
            }
        }
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

        if (v.kitchen.height() > 0) {
            cook.drawKitchen(c, v.kitchen);
            cook.drawFlights(c);
        }

        for (Effects.Particle p : fx.particles) {
            float a = p.life / p.max;
            gfx.star(c, p.x, p.y, p.size * (0.5f + a), Gfx.alpha(p.color, a), 0);
        }
        for (Effects.Pop p : fx.pops) {
            float a = Math.min(1f, p.life / (p.max * 0.4f));
            float s = 1f + 0.25f * (float) Math.sin(Math.min(1f, (p.max - p.life) * 6f) * Math.PI / 2);
            gfx.textC(c, p.text, p.x, p.y, p.size * s, Gfx.alpha(p.color, a), Gfx.alpha(0xFF4A2808, a), Paint.Align.CENTER);
        }

        drawIntro(c);

        if (v.confirmQuit) {
            drawConfirm(c);
        } else if (v.phase == Phase.TIP) drawTip(c);
        else drawButtons(c);
    }

    /** At the start of a level: what to collect, with its picture, over the board. */
    private void drawIntro(Canvas c) {
        final float life = 2.4f;
        float t = v.introT;
        if (t >= life || v.level == null || v.phase == Phase.TIP || v.confirmQuit) return;
        float a = Math.min(1f, t * 5f) * Math.min(1f, (life - t) * 3f);
        float pop = 1f + 0.12f * (float) Math.sin(Math.min(1f, t * 3f) * Math.PI);
        int kind = v.level.ingredient();
        String count = "Collect " + v.level.goalCounts[0], name = Recipes.INGREDIENT[kind];
        float cx = v.W / 2, cy = v.by + v.cell * ROWS * 0.4f, w = v.W * 0.84f * pop, h = v.cell * 3.3f * pop;
        float l = cx - w / 2, rt = cx + w / 2;
        gfx.rrect(c, l, cy - h / 2 + v.u, rt, cy + h / 2 + v.u, 6 * v.u, Gfx.alpha(0xFF2B1606, 0.5f * a));
        gfx.rrect(c, l, cy - h / 2, rt, cy + h / 2, 6 * v.u, Gfx.alpha(PANEL, 0.96f * a));
        gfx.rrectLine(c, l + v.u * 0.4f, cy - h / 2 + v.u * 0.4f, rt - v.u * 0.4f, cy + h / 2 - v.u * 0.4f, 5.6f * v.u, v.u * 0.8f, Gfx.alpha(0xFFFF9B26, a));
        if (v.level.multi()) {
            // one row per goal: its picture, then how many to collect
            int n = v.level.goalKinds.length;
            float rowH = h / (n + 0.4f), rsz = rowH * 0.92f;
            for (int i = 0; i < n; i++) {
                int gk = v.level.goalKinds[i];
                float ry = cy - h / 2 + h * 0.2f / (n + 0.4f) + rowH * (i + 0.5f);
                gfx.circle(c, l + rowH * 0.9f, ry, rsz * 0.55f, Gfx.alpha(0xFFFFF4D2, a));
                drawIcon(c, gk, l + rowH * 0.9f, ry, rsz, a);
                String line = gk == Art.CRATE ? "and break " + v.level.goalCounts[i] + (v.level.goalCounts[i] == 1 ? " crate" : " crates")
                        : (i == 0 ? "Collect " : "and ") + v.level.goalCounts[i] + " " + Level.goalName(gk);
                float lx = l + rowH * 1.7f, lmax = rt - lx - 3 * v.u, ls = rowH * 0.5f, lw = gfx.width(line, ls);
                if (lw > lmax) ls *= lmax / lw;
                gfx.textC(c, line, lx, ry, ls, Gfx.alpha(Color.WHITE, a), Gfx.alpha(0xFF3A1F08, a), Paint.Align.LEFT);
            }
            return;
        }
        float isz = h * 0.82f;
        gfx.circle(c, l + h * 0.62f, cy, isz * 0.55f, Gfx.alpha(0xFFFFF4D2, a));
        drawIcon(c, kind, l + h * 0.62f, cy, isz, a);
        float tx = l + h * 1.3f, maxW = rt - tx - 3 * v.u;
        float s1 = h * 0.27f, s2 = h * 0.38f;
        float w1 = gfx.width(count, s1), w2 = gfx.width(name, s2);
        if (w1 > maxW) s1 *= maxW / w1;
        if (w2 > maxW) s2 *= maxW / w2;
        gfx.textC(c, count, tx, cy - h * 0.2f, s1, Gfx.alpha(0xFFFFE8B0, a), Gfx.alpha(0xFF3A1F08, a), Paint.Align.LEFT);
        gfx.textC(c, name, tx, cy + h * 0.16f, s2, Gfx.alpha(Color.WHITE, a), Gfx.alpha(0xFF3A1F08, a), Paint.Align.LEFT);
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
        Bitmap b = art[t.type == Art.CRATE && t.hp < Art.CRATE_HP ? Art.CRATE_CRACKED : t.type];
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

    private static final String[] TIP_NAME = {"Scarecrow", "TNT", "Golden Egg", "Rainbow Tractor", "Crate"};
    private static final String[] TIP_USE = {
            "Swap it with a neighbour and it spins, sweeping away the 3x3 area around it.",
            "Swap it with a neighbour to blow up the 3x3 area around it.",
            "Swap it with any crop to harvest every one of that crop on the board.",
            "Swap it with any crop and it drives across the board, harvesting every one of that crop.",
            "Crates can not be swapped. Match crops right beside one to crack it, and again to break it.",
    };
    private static final String[] TIP_MAKE = {
            "Make one by matching a T or L shape.",
            "Make one by matching a T or L shape.",
            "Make one by matching 5 in a row.",
            "Make one by matching 5 in a row.",
            "Power-ups beside a crate hit it too.",
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
        gfx.textC(c, (v.tipType == Art.CRATE ? "New Obstacle!" : "New Power-Up!"), o.centerX(), o.top + ph * 0.045f, 7 * v.u, Color.WHITE, 0xFF7A3E0B, Paint.Align.CENTER);

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

    private final Paint face = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF faceRect = new RectF();

    private void drawButton(Canvas c, Button b) {
        if (b.hidden) return;
        float w = b.r.width(), h = b.r.height();
        float depth = h * 0.1f, o = Math.max(1.5f, h * 0.05f);
        float off = b.pressed ? depth * 0.8f : 0f;
        // back and sound are quiet cream buttons; the rest are coloured
        boolean quiet = b.back || b.speaker;
        int base = b.locked ? 0xFFB9B2A4 : quiet ? 0xFFFFF1CC : b.color;
        int top = Gfx.lighten(base, 0.3f), bot = Gfx.darken(base, 0.9f);
        float cx = b.r.centerX(), cy = b.r.centerY();
        if (b.circle) {
            float rad = w / 2;
            gfx.circle(c, cx, cy + depth, rad + o, 0x66000000 | (Gfx.INK & 0xFFFFFF));
            gfx.circle(c, cx, cy + off, rad + o, Gfx.INK);
            face.setShader(new LinearGradient(0, cy + off - rad, 0, cy + off + rad, top, bot, Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy + off, rad, face);
            gfx.ellipse(c, cx - rad * 0.22f, cy + off - rad * 0.58f, rad * 0.38f, rad * 0.13f, 0x77FFFFFF);
        } else {
            float rad = h * 0.34f;
            gfx.rrect(c, b.r.left - o, b.r.top + depth - o, b.r.right + o, b.r.bottom + depth + o, rad + o, 0x66000000 | (Gfx.INK & 0xFFFFFF));
            gfx.rrect(c, b.r.left - o, b.r.top + off - o, b.r.right + o, b.r.bottom + off + o, rad + o, Gfx.INK);
            faceRect.set(b.r.left, b.r.top + off, b.r.right, b.r.bottom + off);
            face.setShader(new LinearGradient(0, faceRect.top, 0, faceRect.bottom, top, bot, Shader.TileMode.CLAMP));
            c.drawRoundRect(faceRect, rad, rad, face);
            gfx.rrect(c, b.r.left + h * 0.2f, b.r.top + off + h * 0.07f, b.r.right - h * 0.2f, b.r.top + off + h * 0.17f, h * 0.05f, 0x66FFFFFF);
        }
        face.setShader(null);
        if (b.back) gfx.backArrow(c, cx - w * 0.02f, cy + off, w * 0.5f, 0xFF6B3F17);
        else if (b.speaker) gfx.speaker(c, cx, cy + off, w * 0.42f, b.muted, 0xFF6B3F17);
        else if (b.locked) gfx.lock(c, cx, cy + off, w * 0.26f);
        else {
            float size = h * 0.42f;
            int outline = Gfx.darken(base, 0.4f);
            if (b.icon != GameView.ICON_NONE) {
                // a small picture beside the label: before it, or after it for the forward arrow
                float tw = gfx.width(b.text, size), icon = (b.icon == GameView.ICON_CHEF ? 0.62f : b.icon == GameView.ICON_REPLAY ? 0.6f : 0.5f) * h, gap = h * 0.18f;
                float x0 = cx - (icon + gap + tw) / 2, textX = b.icon == GameView.ICON_NEXT ? x0 : x0 + icon + gap;
                float iconX = b.icon == GameView.ICON_NEXT ? x0 + tw + gap + icon / 2 : x0 + icon / 2;
                if (b.icon == GameView.ICON_CHEF) gfx.chefHat(c, iconX, cy + off - h * 0.06f, icon);
                else if (b.icon == GameView.ICON_NEXT) gfx.nextArrow(c, iconX, cy + off, icon, outline);
                else gfx.replayArrow(c, iconX, cy + off, icon, outline);
                gfx.textC(c, b.text, textX, cy + off, size, Color.WHITE, outline, Paint.Align.LEFT);
            } else {
                gfx.textC(c, b.text, cx, cy + off, size, Color.WHITE, outline, Paint.Align.CENTER);
            }
        }
    }
}
