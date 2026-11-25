package com.zigzag.cottagekitchen;

import static com.zigzag.cottagekitchen.Gfx.CREAM;
import static com.zigzag.cottagekitchen.Gfx.INK;
import static com.zigzag.cottagekitchen.Gfx.WOOD;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Draws the dishes: the order card under the board, the recipe book and the result screen. */
final class CookRenderer {
    private static final float FLIGHT = 0.5f;
    private static final int GREY = 0xFFB9AF9C;

    /** An ingredient flying from the board to its place on the order card. */
    private static final class Fly {
        int type;
        float sx, sy, delay, t;
    }

    private final GameView v;
    private final Renderer r;
    private final Gfx gfx;
    private final List<Fly> flies = new ArrayList<>();
    /** Per ingredient: 1 right after one lands on the card. */
    private final float[] bump = new float[Art.CROPS];
    private final RectF rf = new RectF();
    /** Dish artwork by recipe; a dish with no picture yet is drawn in code. */
    private final Map<Recipes.Recipe, Bitmap> art = new HashMap<>();
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint ghost = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);

    // order card layout, from layoutCard()
    private float dishX, dishY, dishSize, rowsLeft, rowsRight, rowsTop, rowH, titleY;

    CookRenderer(Context ctx, GameView v, Renderer r, Gfx gfx) {
        this.v = v;
        // not-yet-cooked dishes show as a pale grey silhouette
        ColorMatrix grey = new ColorMatrix();
        grey.setSaturation(0f);
        grey.postConcat(new ColorMatrix(new float[]{0.5f, 0, 0, 0, 105, 0, 0.5f, 0, 0, 100, 0, 0, 0.5f, 0, 92, 0, 0, 0, 1, 0}));
        ghost.setColorFilter(new ColorMatrixColorFilter(grey));
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        for (Recipes.Recipe rc : Recipes.ALL) {
            int id = ctx.getResources().getIdentifier("dish_" + rc.key, "drawable", ctx.getPackageName());
            if (id != 0) art.put(rc, BitmapFactory.decodeResource(ctx.getResources(), id, o));
        }
        this.r = r;
        this.gfx = gfx;
    }

    // ---------------------------------------------------------------- order card

    void clear() {
        flies.clear();
        java.util.Arrays.fill(bump, 0f);
    }

    void launch(int type, float x, float y, float delay) {
        Fly f = new Fly();
        f.type = type;
        f.sx = x;
        f.sy = y;
        f.delay = delay;
        flies.add(f);
    }

    /** Advances the flights. Returns how many ingredients reached the card. */
    int update(float dt) {
        int landed = 0;
        for (int i = 0; i < bump.length; i++) bump[i] = Math.max(0, bump[i] - dt * 4f);
        for (int i = flies.size() - 1; i >= 0; i--) {
            Fly f = flies.get(i);
            if (f.delay > 0) {
                f.delay -= dt;
                continue;
            }
            f.t += dt / FLIGHT;
            if (f.t >= 1) {
                bump[f.type] = 1;
                landed++;
                flies.remove(i);
            }
        }
        return landed;
    }

    private void layoutCard(RectF s) {
        float p = 3 * v.u;
        dishSize = Math.min(s.width() * 0.34f, s.height() - 2 * p);
        dishX = s.left + p + dishSize / 2;
        dishY = s.centerY();
        rowsLeft = dishX + dishSize / 2 + 2 * v.u;
        rowsRight = s.right - p;
        titleY = s.top + p + 3 * v.u;
        float area0 = s.top + p + 7.5f * v.u, area = s.bottom - p - area0;
        int n = v.level == null ? 1 : v.level.recipe.ingredients.length;
        rowH = Math.min(11 * v.u, area / n);
        rowsTop = area0 + (area - rowH * n) / 2;
    }

    /** Where the ingredient being collected sits on the order card. */
    private float[] iconAt(int type) {
        layoutCard(v.kitchen);
        int row = v.level.step;
        for (int j = 0; j < v.level.recipe.ingredients.length; j++) if (v.level.recipe.ingredients[j] == type) row = j;
        return new float[]{rowsLeft + rowH * 0.5f, rowsTop + rowH * (row + 0.5f), rowH};
    }

    /** Which of the level's goals is this ingredient, or -1. */
    private static int goalIndex(Level lv, int kind) {
        for (int i = 0; i < lv.goalKinds.length; i++) if (lv.goalKinds[i] == kind) return i;
        return -1;
    }

    /** How much of the whole dish is done: the ingredients already collected, plus this level's progress. */
    private float dishFraction(Level lv) {
        int n = lv.recipe.ingredients.length;
        float have = 0;
        for (int j = 0; j < n; j++) {
            if (j == lv.step) have += (lv.goalCounts[0] - v.need[lv.ingredient()]) / (float) lv.goalCounts[0];
            else if (v.progress.ingredientDone(lv.dish, j)) have += 1;
        }
        return have / n;
    }

    /** The order under the board: the dish filling up as ingredients arrive, and a row for each ingredient it needs. */
    void drawKitchen(Canvas c, RectF s) {
        Level lv = v.level;
        if (lv == null) return;
        gfx.rrect(c, s.left, s.top + 0.8f * v.u, s.right, s.bottom + 0.8f * v.u, 4 * v.u, Gfx.darken(WOOD, 0.6f));
        gfx.rrect(c, s.left, s.top, s.right, s.bottom, 4 * v.u, WOOD);
        gfx.rrect(c, s.left + 0.7f * v.u, s.top + 0.7f * v.u, s.right - 0.7f * v.u, s.bottom - 0.7f * v.u, 3.4f * v.u, CREAM);
        layoutCard(s);

        float pulse = v.phase == GameView.Phase.DONE ? 1f + 0.12f * (float) Math.sin(v.time * 10) : 1f;
        gfx.circle(c, dishX, dishY, dishSize * 0.52f * pulse, 0x33FFD43B);
        drawDishFilling(c, lv.recipe, dishX, dishY, dishSize * pulse, v.phase == GameView.Phase.DONE && lv.lastStep() ? 1f : dishFraction(lv));

        gfx.text(c, lv.recipe.name, rowsLeft, titleY + 1.6f * v.u, fitSize(lv.recipe.name, 5.2f * v.u, rowsRight - rowsLeft), 0xFF6B3F17, 0, Paint.Align.LEFT);
        for (int j = 0; j < lv.recipe.ingredients.length; j++) {
            int kind = lv.recipe.ingredients[j];
            int gi = goalIndex(lv, kind);
            boolean now = gi >= 0, done = !now && v.progress.ingredientDone(lv.dish, j);
            float cy = rowsTop + rowH * (j + 0.5f);
            if (now) {
                // the ingredient this level is about stands out from the rest
                gfx.rrect(c, rowsLeft - 1.2f * v.u, cy - rowH * 0.56f, rowsRight + 1.2f * v.u, cy + rowH * 0.56f, rowH * 0.3f, 0x33FF9B26);
                gfx.rrectLine(c, rowsLeft - 1.2f * v.u, cy - rowH * 0.56f, rowsRight + 1.2f * v.u, cy + rowH * 0.56f, rowH * 0.3f, v.u * 0.5f, 0xFFFF9B26);
            }
            float isz = rowH * 0.9f * (now ? 1f + 0.25f * bump[kind] : 1f);
            r.drawIcon(c, kind, rowsLeft + rowH * 0.5f, cy, isz, now || done ? 1f : 0.4f);
            float bl = rowsLeft + rowH * 1.15f, br = rowsRight - 11 * v.u, bh = rowH * 0.34f;
            gfx.rrect(c, bl, cy - bh / 2, br, cy + bh / 2, bh / 2, 0xFFE3CE9B);
            if (now) {
                int goal = lv.goalCounts[gi], got = goal - v.need[kind];
                if (got > 0) gfx.rrect(c, bl, cy - bh / 2, bl + Math.max(bh, (br - bl) * got / goal), cy + bh / 2, bh / 2, got >= goal ? Gfx.CHECK_GREEN : 0xFFFFB02E);
                // the count lives in the pill at the top; the card only shows the bar and a tick when it is full
                if (got >= goal) gfx.check(c, rowsRight - 4.5f * v.u, cy, rowH * 0.32f);
            } else if (done) {
                gfx.rrect(c, bl, cy - bh / 2, br, cy + bh / 2, bh / 2, Gfx.CHECK_GREEN);
                gfx.check(c, rowsRight - 4.5f * v.u, cy, rowH * 0.32f);
            } else {
                gfx.textC(c, "Next", rowsRight, cy, rowH * 0.4f, 0xFFB09A6C, 0, Paint.Align.RIGHT);
            }
        }
    }

    void drawFlights(Canvas c) {
        for (Fly f : flies) {
            if (f.delay > 0) continue;
            float[] to = iconAt(f.type);
            float t = f.t * f.t * (3 - 2 * f.t);
            float cpx = (f.sx + to[0]) / 2, cpy = Math.min(f.sy, to[1]) - 8 * v.u;
            float x = (1 - t) * (1 - t) * f.sx + 2 * (1 - t) * t * cpx + t * t * to[0];
            float y = (1 - t) * (1 - t) * f.sy + 2 * (1 - t) * t * cpy + t * t * to[1];
            r.drawIcon(c, f.type, x, y, v.cell * (0.8f - 0.3f * t));
        }
    }

    private float fitSize(String s, float size, float maxW) {
        float w = gfx.width(s, size);
        return w > maxW ? size * maxW / w : size;
    }

    // ---------------------------------------------------------------- recipe book (level select)

    void drawBook(Canvas c) {
        float m = 3 * v.u;
        gfx.textC(c, "Recipe Book", v.W / 2, v.topInset + m + 6.5f * v.u, 7.5f * v.u, Color.WHITE, 0xFF1F5F10, Paint.Align.CENTER);
        for (int i = 0; i < Recipes.ALL.length; i++) {
            RectF k = v.bookCard[i];
            boolean locked = !v.progress.recipeUnlocked(i), current = Recipes.dishOf(v.progress.unlocked - 1) == i;
            boolean cooked = v.progress.recipeDone(i);
            float pressed = v.bookPressed == i ? 0.6f * v.u : 0;
            gfx.rrect(c, k.left, k.top + 0.8f * v.u, k.right, k.bottom + 0.8f * v.u, 3 * v.u, Gfx.darken(WOOD, 0.6f));
            gfx.rrect(c, k.left, k.top + pressed, k.right, k.bottom + pressed, 3 * v.u, current ? 0xFFFF9B26 : WOOD);
            gfx.rrect(c, k.left + 0.6f * v.u, k.top + pressed + 0.6f * v.u, k.right - 0.6f * v.u, k.bottom + pressed - 0.6f * v.u, 2.6f * v.u, locked ? 0xFFE3DDD0 : CREAM);
            float cx = k.centerX(), w = k.width(), h = k.height();
            Recipes.Recipe rc = Recipes.ALL[i];
            if (locked) gfx.lock(c, cx, k.centerY(), w * 0.2f);
            else drawDish(c, rc, cx, k.top + pressed + h * 0.36f, Math.min(w * 0.7f, h * 0.48f), !cooked);
            gfx.circle(c, k.left + w * 0.15f, k.top + h * 0.15f, w * 0.1f, locked ? 0xFFA8A8A8 : WOOD);
            gfx.textC(c, String.valueOf(i + 1), k.left + w * 0.15f, k.top + h * 0.15f, w * 0.12f, Color.WHITE, 0, Paint.Align.CENTER);
            if (!locked) {
                String name = rc.name;
                gfx.textC(c, name, cx, k.top + pressed + h * 0.67f, fitSize(name, w * 0.15f, w * 0.9f), 0xFF6B3F17, 0, Paint.Align.CENTER);
                // the dish's ingredients, bright once collected
                int n = rc.ingredients.length;
                float isz = Math.min(w * 0.2f, w * 0.88f / n), y = k.top + pressed + h * 0.86f;
                for (int j = 0; j < n; j++) {
                    float x = cx + (j - (n - 1) / 2f) * isz * 1.08f;
                    boolean done = v.progress.ingredientDone(i, j);
                    r.drawIcon(c, rc.ingredients[j], x, y, isz, done ? 1f : 0.35f);
                    if (done) gfx.check(c, x + isz * 0.34f, y + isz * 0.3f, isz * 0.2f);
                }
            }
        }
        v.renderer.drawButtons(c);
    }

    // ---------------------------------------------------------------- recipe (the levels of one dish)

    /** Each dish is a group of ingredient levels; this screen is the group's menu. */
    void drawRecipe(Canvas c) {
        Recipes.Recipe rc = Recipes.ALL[v.recipe];
        float m = 3 * v.u, u = v.u;
        boolean cooked = v.progress.recipeDone(v.recipe);
        gfx.textC(c, rc.name, v.W / 2, v.topInset + m + 6.5f * u + v.recipeShift,
                fitSize(rc.name, 7.5f * u, v.W - 40 * u), Color.WHITE, 0xFF1F5F10, Paint.Align.CENTER);

        RectF hero = v.recipeDish;
        gfx.circle(c, hero.centerX(), hero.centerY() + hero.height() * 0.1f, hero.width() * 0.5f, 0x33FFD43B);
        drawDish(c, rc, hero.centerX(), hero.centerY(), hero.height(), !cooked);
        int have = v.progress.ingredientsDone(v.recipe), n = rc.ingredients.length;
        gfx.textC(c, have + " of " + n + " ingredients", v.W / 2, hero.bottom + 2.2f * u,
                4.2f * u, 0xFF8B5A2B, 0, Paint.Align.CENTER);

        int next = v.progress.nextStep(v.recipe);
        for (int i = 0; i < v.recipeStepCount; i++) {
            RectF k = v.recipeNode[i];
            boolean done = v.progress.ingredientDone(v.recipe, i);
            boolean isNext = i == next, locked = i > next;
            float press = v.recipePressed == i ? 0.5f * u : 0;
            gfx.rrect(c, k.left, k.top + 0.8f * u, k.right, k.bottom + 0.8f * u, 2.6f * u, Gfx.darken(WOOD, 0.6f));
            gfx.rrect(c, k.left, k.top + press, k.right, k.bottom + press, 2.6f * u, isNext ? 0xFFFF9B26 : WOOD);
            gfx.rrect(c, k.left + 0.5f * u, k.top + press + 0.5f * u, k.right - 0.5f * u,
                    k.bottom + press - 0.5f * u, 2.1f * u, locked ? 0xFFE3DDD0 : CREAM);
            float cx = k.centerX(), iy = k.top + k.height() * 0.36f;
            float isz = Math.min(k.width() * 0.5f, k.height() * 0.32f);
            if (locked) gfx.lock(c, cx, iy, isz * 0.5f);
            else {
                r.drawIcon(c, rc.ingredients[i], cx, iy, isz, done ? 1f : 0.65f);
                if (done) gfx.check(c, cx + isz * 0.34f, iy + isz * 0.28f, isz * 0.22f);
            }
            String name = Recipes.INGREDIENT[rc.ingredients[i]];
            gfx.textC(c, name, cx, k.top + k.height() * 0.68f,
                    fitSize(name, Math.min(k.height() * 0.13f, k.width() * 0.15f), k.width() * 0.88f),
                    locked ? 0xFFA8A8A8 : 0xFF6B3F17, 0, Paint.Align.CENTER);
            int s = v.progress.bestStars[Recipes.firstLevel(v.recipe) + i];
            float sy = k.top + k.height() * 0.9f, ss = Math.min(k.height() * 0.075f, k.width() * 0.09f);
            for (int j = 0; j < 3; j++) gfx.star(c, cx + (j - 1) * ss * 1.8f, sy, ss,
                    j < s ? 0xFFFFD43B : 0xFFCDBFA0, j < s ? 0xFFC97700 : 0xFFA89878);
        }
        v.renderer.drawButtons(c);
        if (v.showCelebration) drawCelebration(c);
    }

    /** A short "Order up!" moment on the recipe screen when its last ingredient is collected. */
    private void drawCelebration(Canvas c) {
        float t = v.celebrateT;
        float a = t < 0.25f ? t / 0.25f : t > 2f ? (2.4f - t) / 0.4f : 1f;
        a = Math.max(0f, Math.min(1f, a));
        RectF hero = v.recipeDish;
        float ds = hero.height() * 0.6f;
        for (int i = 0; i < 8; i++) {
            double ang = t * 1.2 + i * Math.PI / 4;
            gfx.star(c, hero.centerX() + (float) Math.cos(ang) * ds * 0.62f,
                    hero.centerY() + (float) Math.sin(ang) * ds * 0.42f, ds * 0.06f,
                    Gfx.alpha(0xFFFFD43B, 0.9f * a), 0);
        }
        String txt = "Order up!";
        gfx.textC(c, txt, hero.centerX(), hero.centerY(), fitSize(txt, 8 * v.u, hero.width() * 0.9f),
                Gfx.alpha(0xFFFFD43B, a), Gfx.alpha(0xFF7A3E0B, a), Paint.Align.CENTER);
    }

    // ---------------------------------------------------------------- result

    void drawResult(Canvas c) {
        Level lv = v.level;
        RectF o = v.resultBox;
        float pw = o.width(), ph = o.height();
        int n = lv.recipe.ingredients.length;
        gfx.rrect(c, o.left, o.top + 1.5f * v.u, o.right, o.bottom + 1.5f * v.u, 6 * v.u, Gfx.darken(WOOD, 0.6f));
        gfx.rrect(c, o.left, o.top, o.right, o.bottom, 6 * v.u, WOOD);
        gfx.rrect(c, o.left + v.u, o.top + v.u, o.right - v.u, o.bottom - v.u, 5 * v.u, CREAM);
        gfx.rrect(c, o.left - v.u, o.top - 3 * v.u, o.right + v.u, o.top + ph * 0.12f, 5 * v.u, v.won ? Gfx.GREEN : 0xFFFF7A66);
        String title = !v.won ? "Out of moves" : Recipes.INGREDIENT[lv.ingredient()] + " collected!";
        gfx.textC(c, title, o.centerX(), o.top + ph * 0.04f, fitSize(title, 7.5f * v.u, pw - 8 * v.u), Color.WHITE, v.won ? 0xFF1F5F10 : 0xFF8A2A1A, Paint.Align.CENTER);

        // the dish fill is the only progress cue here; the recipe screen now owns the step-by-step detail
        int have = v.progress.ingredientsDone(lv.dish);
        float ds = Math.min(pw * 0.5f, ph * 0.28f);
        float dishY = o.top + ph * (v.won ? 0.72f : 0.32f);
        drawDishFilling(c, lv.recipe, o.centerX(), dishY, ds, have / (float) n);
        gfx.textC(c, lv.recipe.name, o.centerX(), o.top + ph * (v.won ? 0.9f : 0.48f),
                fitSize(lv.recipe.name, 7f * v.u, pw - 10 * v.u), 0xFF6B3F17, 0, Paint.Align.CENTER);

        if (v.won) {
            for (int s = 0; s < 3; s++) {
                boolean on = s < v.stars;
                gfx.star(c, o.centerX() + (s - 1) * pw * 0.22f, o.top + ph * 0.20f - (s == 1 ? 0 : pw * 0.02f), pw * 0.09f,
                        on ? 0xFFFFD43B : 0xFFCDBFA0, on ? 0xFFC97700 : 0xFFA89878);
            }
            if (v.recordScore) gfx.textC(c, "New best!  " + v.score, o.centerX(), o.top + ph * 0.55f,
                    4.6f * v.u, 0xFFD06A00, 0, Paint.Align.CENTER);
        } else {
            gfx.textC(c, "Still missing", o.centerX(), o.top + ph * 0.60f, 4.6f * v.u, 0xFF8B5A2B, 0, Paint.Align.CENTER);
            if (v.recordScore) gfx.textC(c, "New best!  " + v.score, o.centerX(), o.top + ph * 0.69f,
                    4.6f * v.u, 0xFFD06A00, 0, Paint.Align.CENTER);
            // every goal that fell short
            int missing = 0;
            for (int k : lv.goalKinds) if (v.need[k] > 0) missing++;
            int slot2 = 0;
            float isz = pw * 0.08f, y = o.top + ph * 0.78f;
            for (int k : lv.goalKinds) {
                if (v.need[k] <= 0) continue;
                float x = o.centerX() + (slot2++ - (missing - 1) / 2f) * isz * 2.2f;
                r.drawIcon(c, k, x, y - isz * 0.15f, isz * 1.3f);
                gfx.textC(c, "x" + v.need[k], x, y + isz * 0.8f, isz * 0.5f, 0xFFD2452B, 0, Paint.Align.CENTER);
            }
        }
        r.drawButtons(c);
    }

    // ---------------------------------------------------------------- dishes

    /** The dish as a grey outline that fills with colour from the bottom as the order is completed. */
    private void drawDishFilling(Canvas c, Recipes.Recipe rc, float cx, float cy, float size, float frac) {
        if (frac >= 1f) {
            drawDish(c, rc, cx, cy, size, false);
            return;
        }
        drawDish(c, rc, cx, cy, size, true);
        if (frac <= 0f) return;
        float top = cy + size * 0.55f - size * 1.1f * frac;
        c.save();
        c.clipRect(cx - size, top, cx + size, cy + size);
        drawDish(c, rc, cx, cy, size, false);
        c.restore();
    }

    private int tone(int color, boolean grey) {
        if (!grey) return color;
        int rr = (Color.red(color) + 3 * Color.red(GREY)) / 4, g = (Color.green(color) + 3 * Color.green(GREY)) / 4, b = (Color.blue(color) + 3 * Color.blue(GREY)) / 4;
        return Color.rgb(rr, g, b);
    }

    /** A filled, outlined rounded rectangle. */
    private void box(Canvas c, float l, float t, float rt, float b, float rad, int color, float e) {
        gfx.rrect(c, l - e, t - e, rt + e, b + e, rad + e, INK);
        gfx.rrect(c, l, t, rt, b, rad, color);
    }

    private void blob(Canvas c, float cx, float cy, float rx, float ry, int color, float e) {
        gfx.ellipse(c, cx, cy, rx + e, ry + e, INK);
        gfx.ellipse(c, cx, cy, rx, ry, color);
    }

    /** One dish, centred at (cx, cy) in a square of the given size. */
    void drawDish(Canvas c, Recipes.Recipe rc, float cx, float cy, float size, boolean grey) {
        Bitmap pic = art.get(rc);
        if (pic != null) {
            float d = size * 0.96f, bottom = cy + size * 0.5f;
            gfx.ellipse(c, cx, bottom - d * 0.02f, d * 0.34f, d * 0.04f, 0x1F000000);
            rf.set(cx - d / 2, bottom - d, cx + d / 2, bottom);
            c.drawBitmap(pic, null, rf, grey ? ghost : bmp);
            return;
        }
        int shape = rc.shape, accent = rc.color;
        float s = size, e = s * 0.022f;
        int ac = tone(accent, grey), light = tone(Gfx.lighten(accent, 0.55f), grey), dark = tone(Gfx.darken(accent, 0.7f), grey);
        int crust = tone(0xFFE0A24F, grey), cream = tone(0xFFFFF4E0, grey), plate = tone(0xFFF4F4F4, grey);
        gfx.ellipse(c, cx, cy + 0.4f * s, 0.42f * s, 0.06f * s, 0x26000000);
        switch (shape) {
            case Recipes.JAR:
                box(c, cx - 0.3f * s, cy - 0.2f * s, cx + 0.3f * s, cy + 0.4f * s, 0.12f * s, ac, e);
                gfx.rrect(c, cx - 0.22f * s, cy - 0.1f * s, cx - 0.16f * s, cy + 0.26f * s, 0.03f * s, 0x66FFFFFF);
                box(c, cx - 0.2f * s, cy + 0.06f * s, cx + 0.2f * s, cy + 0.28f * s, 0.05f * s, cream, e * 0.7f);
                gfx.circle(c, cx, cy + 0.17f * s, 0.07f * s, dark);
                box(c, cx - 0.32f * s, cy - 0.34f * s, cx + 0.32f * s, cy - 0.16f * s, 0.05f * s, tone(0xFFFFFFFF, grey), e);
                gfx.rrect(c, cx - 0.32f * s, cy - 0.27f * s, cx + 0.32f * s, cy - 0.2f * s, 0, dark);
                break;
            case Recipes.CAKE:
                blob(c, cx, cy + 0.34f * s, 0.46f * s, 0.09f * s, plate, e);
                box(c, cx - 0.4f * s, cy + 0.04f * s, cx + 0.4f * s, cy + 0.34f * s, 0.07f * s, ac, e);
                box(c, cx - 0.3f * s, cy - 0.2f * s, cx + 0.3f * s, cy + 0.06f * s, 0.07f * s, ac, e);
                gfx.rrect(c, cx - 0.4f * s, cy + 0.04f * s, cx + 0.4f * s, cy + 0.12f * s, 0.04f * s, cream);
                gfx.rrect(c, cx - 0.3f * s, cy - 0.2f * s, cx + 0.3f * s, cy - 0.13f * s, 0.035f * s, cream);
                for (int i = -1; i <= 1; i++) gfx.circle(c, cx + i * 0.2f * s, cy + 0.2f * s, 0.03f * s, light);
                box(c, cx - 0.02f * s, cy - 0.36f * s, cx + 0.02f * s, cy - 0.2f * s, 0.01f * s, tone(0xFFFFE066, grey), e * 0.6f);
                gfx.ellipse(c, cx, cy - 0.4f * s, 0.03f * s, 0.045f * s, tone(0xFFFF9B26, grey));
                break;
            case Recipes.PIE:
                blob(c, cx, cy + 0.32f * s, 0.46f * s, 0.1f * s, plate, e);
                gfx.polygon(c, tone(0xFFC9A04A, grey), INK, e * 2, cx - 0.42f * s, cy + 0.1f * s, cx + 0.42f * s, cy + 0.1f * s, cx + 0.34f * s, cy + 0.34f * s, cx - 0.34f * s, cy + 0.34f * s);
                blob(c, cx, cy + 0.1f * s, 0.42f * s, 0.14f * s, crust, e);
                blob(c, cx, cy + 0.09f * s, 0.32f * s, 0.1f * s, ac, e * 0.6f);
                for (int i = -1; i <= 1; i++) gfx.lineSeg(c, cx + i * 0.16f * s - 0.1f * s, cy + 0.02f * s, cx + i * 0.16f * s + 0.1f * s, cy + 0.16f * s, 0.045f * s, crust);
                break;
            case Recipes.BOWL:
                box(c, cx - 0.42f * s, cy - 0.04f * s, cx + 0.42f * s, cy + 0.38f * s, 0.2f * s, tone(0xFFF8F0E0, grey), e);
                gfx.rrect(c, cx - 0.36f * s, cy + 0.14f * s, cx + 0.36f * s, cy + 0.2f * s, 0.03f * s, dark);
                blob(c, cx, cy - 0.04f * s, 0.42f * s, 0.1f * s, tone(0xFFE9DCC2, grey), e);
                gfx.ellipse(c, cx, cy - 0.04f * s, 0.34f * s, 0.075f * s, ac);
                gfx.ellipse(c, cx - 0.1f * s, cy - 0.06f * s, 0.1f * s, 0.025f * s, light);
                for (int i = -1; i <= 1; i += 2) gfx.lineSeg(c, cx + i * 0.12f * s, cy - 0.16f * s, cx + i * 0.12f * s + 0.04f * s, cy - 0.3f * s, 0.035f * s, 0x99FFFFFF);
                break;
            case Recipes.STACK:
                blob(c, cx, cy + 0.34f * s, 0.46f * s, 0.09f * s, plate, e);
                for (int i = 0; i < 3; i++) box(c, cx - 0.38f * s, cy + 0.14f * s - i * 0.15f * s, cx + 0.38f * s, cy + 0.3f * s - i * 0.15f * s, 0.08f * s, i % 2 == 0 ? crust : tone(0xFFF0B85F, grey), e);
                box(c, cx - 0.4f * s, cy - 0.2f * s, cx + 0.4f * s, cy - 0.06f * s, 0.07f * s, tone(0xFF8B4A1A, grey), e);
                gfx.polygon(c, tone(0xFF8B4A1A, grey), 0, 0, cx - 0.3f * s, cy - 0.08f * s, cx - 0.22f * s, cy + 0.12f * s, cx - 0.14f * s, cy - 0.08f * s);
                box(c, cx - 0.1f * s, cy - 0.34f * s, cx + 0.1f * s, cy - 0.2f * s, 0.03f * s, tone(0xFFFFE066, grey), e * 0.7f);
                break;
            case Recipes.MUFFIN:
                gfx.polygon(c, tone(0xFFE8C9A0, grey), INK, e * 2, cx - 0.32f * s, cy + 0.02f * s, cx + 0.32f * s, cy + 0.02f * s, cx + 0.24f * s, cy + 0.38f * s, cx - 0.24f * s, cy + 0.38f * s);
                for (int i = -1; i <= 1; i++) gfx.lineSeg(c, cx + i * 0.14f * s, cy + 0.06f * s, cx + i * 0.11f * s, cy + 0.34f * s, 0.03f * s, tone(0xFFC9A072, grey));
                blob(c, cx, cy - 0.08f * s, 0.4f * s, 0.3f * s, ac, e);
                gfx.ellipse(c, cx - 0.14f * s, cy - 0.17f * s, 0.12f * s, 0.07f * s, light);
                for (int i = 0; i < 4; i++) gfx.circle(c, cx + (i - 1.5f) * 0.16f * s, cy - 0.04f * s - (i % 2) * 0.1f * s, 0.035f * s, dark);
                break;
            case Recipes.LOAF:
                box(c, cx - 0.44f * s, cy - 0.06f * s, cx + 0.44f * s, cy + 0.36f * s, 0.2f * s, tone(0xFFC98B3E, grey), e);
                blob(c, cx, cy - 0.04f * s, 0.4f * s, 0.14f * s, tone(0xFFDDA45A, grey), e);
                for (int i = -1; i <= 1; i++) gfx.lineSeg(c, cx + i * 0.2f * s - 0.05f * s, cy - 0.1f * s, cx + i * 0.2f * s + 0.05f * s, cy + 0.0f * s, 0.045f * s, tone(0xFFF3CF8E, grey));
                for (int i = 0; i < 5; i++) gfx.circle(c, cx + (i - 2) * 0.16f * s, cy + 0.2f * s + (i % 2) * 0.06f * s, 0.025f * s, dark);
                break;
            case Recipes.BARS:
                blob(c, cx, cy + 0.34f * s, 0.46f * s, 0.09f * s, plate, e);
                box(c, cx - 0.4f * s, cy + 0.1f * s, cx + 0.4f * s, cy + 0.32f * s, 0.06f * s, ac, e);
                box(c, cx - 0.34f * s, cy - 0.16f * s, cx + 0.34f * s, cy + 0.08f * s, 0.06f * s, tone(0xFFDDA45A, grey), e);
                for (int i = 0; i < 6; i++) gfx.ellipse(c, cx + (i - 2.5f) * 0.12f * s, cy - 0.04f * s + (i % 2) * 0.05f * s - 0.02f * s, 0.03f * s, 0.017f * s, dark);
                for (int i = 0; i < 6; i++) gfx.ellipse(c, cx + (i - 2.5f) * 0.13f * s, cy + 0.2f * s + (i % 2) * 0.04f * s - 0.02f * s, 0.032f * s, 0.018f * s, light);
                gfx.lineSeg(c, cx + 0.12f * s, cy - 0.16f * s, cx + 0.12f * s, cy + 0.08f * s, 0.02f * s, dark);
                break;
            default: // FEAST: a platter of the harvest
                blob(c, cx, cy + 0.3f * s, 0.46f * s, 0.12f * s, tone(0xFFD8DCE0, grey), e);
                blob(c, cx - 0.2f * s, cy + 0.14f * s, 0.2f * s, 0.17f * s, tone(0xFFF08A24, grey), e);
                blob(c, cx + 0.2f * s, cy + 0.18f * s, 0.18f * s, 0.16f * s, tone(0xFFE5483B, grey), e);
                blob(c, cx + 0.02f * s, cy - 0.04f * s, 0.12f * s, 0.2f * s, tone(0xFFF2C94C, grey), e);
                blob(c, cx + 0.2f * s, cy + 0.02f * s, 0.05f * s, 0.03f * s, tone(0xFF5BA02C, grey), 0);
                blob(c, cx - 0.2f * s, cy - 0.02f * s, 0.05f * s, 0.03f * s, tone(0xFF5BA02C, grey), 0);
                break;
        }
    }
}
