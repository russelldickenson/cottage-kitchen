package com.farmharvest.game;

import static com.farmharvest.game.Gfx.BLUE;
import static com.farmharvest.game.Gfx.CREAM;
import static com.farmharvest.game.Gfx.GREEN;
import static com.farmharvest.game.Gfx.ORANGE;
import static com.farmharvest.game.Gfx.WOOD;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

import java.util.ArrayList;
import java.util.List;

/** Draws the farm screen (scene, tabs, upgrade cards, level result). State is in FarmHub. */
final class FarmRenderer {
    private static final int OUT = 0xFF3E2008, INK = 0xFF6B3F17, PRICE_BAD = 0xFFFF9A8A;

    private static final int RED = 0xFFD9453A;

    // Positions inside the scene: x and y as fractions of its width and height, and the item size as a fraction of its width.
    /** Where each species' house stands: x, y as fractions of the scene's width and height, and the size as a fraction of its width. */
    private static final float[][] HOUSE_POS = {
            {0.79f, 0.55f, 0.28f},    // coop
            {0.37f, 0.52f, 0.34f},    // barn
            {0.09f, 0.56f, 0.20f},    // pig sty
            {0.64f, 0.83f, 0.17f},    // sheep pen
            {0.585f, 0.34f, 0.15f},   // beehive
    };
    private static final float[][] DECOR_POS = {
            {0.50f, 0.96f, 0.98f},   // fence (x is the centre, size is its width)
            {0.29f, 0.88f, 0.20f},   // flowers
            {0.83f, 0.82f, 0.30f},   // pond
            {0.91f, 0.34f, 0.18f},   // oak tree
            {0.09f, 0.40f, 0.20f},   // windmill
            {0.76f, 0.13f, 0.17f},   // hot air balloon
    };
    private static final int[] PETALS = {0xFFE5604A, 0xFFFFD43B, 0xFFE91E63, 0xFF9B59B6, 0xFFFF9B26};

    private final GameView v;
    private final Renderer r;
    private final Gfx gfx;
    private final RectF stripScene = new RectF();
    /** Crops flying from the board to the house that uses them, and eggs hatching in the coop. */
    private final List<Hop> hops = new ArrayList<>();
    private final List<Hatch> hatches = new ArrayList<>();
    private final float[] bump = new float[Farm.SPECIES];
    private final int[] nextHouse = new int[Farm.STOCKS];
    private int eggsIn;

    FarmRenderer(GameView v, Renderer r, Gfx gfx) {
        this.v = v;
        this.r = r;
        this.gfx = gfx;
    }

    // ---------------------------------------------------------------- shared

    private void header(Canvas c) {
        float m = 3 * v.u, hh = 13 * v.u, top = v.topInset + m;
        float right = v.W - m;
        r.drawCoinPill(c, m + hh + m * 0.8f + hh * 0.4f, top, right, top + hh, Math.round(v.dispCoins));
    }

    /** Sparkles and floating text for screens without a board. */
    private void effects(Canvas c) {
        for (Effects.Particle p : v.fx.particles) {
            float a = p.life / p.max;
            gfx.star(c, p.x, p.y, p.size * (0.5f + a), Gfx.alpha(p.color, a), 0);
        }
        for (Effects.Pop p : v.fx.pops) {
            float a = Math.min(1f, p.life / (p.max * 0.4f));
            float s = 1f + 0.25f * (float) Math.sin(Math.min(1f, (p.max - p.life) * 6f) * Math.PI / 2);
            gfx.textC(c, p.text, p.x, p.y, p.size * s, Gfx.alpha(p.color, a), Gfx.alpha(0xFF4A2808, a), Paint.Align.CENTER);
        }
    }

    private void priceChip(Canvas c, float cx, float cy, int price, boolean affordable, float size) {
        String s = String.valueOf(price);
        float tw = gfx.width(s, size), cr = size * 0.55f, total = cr * 2 + size * 0.25f + tw;
        float x = cx - total / 2;
        gfx.coin(c, x + cr, cy, cr);
        gfx.textC(c, s, x + cr * 2 + size * 0.25f, cy, size, affordable ? Color.WHITE : PRICE_BAD, 0xFF3A1F08, Paint.Align.LEFT);
    }

    // ---------------------------------------------------------------- farm screen

    void drawFarm(Canvas c) {
        FarmHub h = v.hub;
        header(c);
        if (h.compact()) drawBanner(c);
        else gfx.textC(c, "My Farm", v.W / 2, h.titleY, 7.5f * v.u, Color.WHITE, 0xFF1F5F10, Paint.Align.CENTER);
        drawScene(c, v.hub.scene, true);
        if (!h.compact()) {
            drawStockStrip(c);
            drawTabs(c);
            for (int i = 0; i < h.cardCount(); i++) drawCard(c, i);
        }
        effects(c);
        if (h.tipOpen) {
            drawFarmTip(c);
            return;
        }
        r.drawButtons(c);
        if (h.compact() && h.tendBtn != null && h.somethingToBuy()) {
            // a badge on Tend Farm: there is something affordable to build or buy
            RectF b = h.tendBtn.r;
            float rad = 3.2f * v.u, bx = b.right - 4 * v.u, by = b.top + 1 * v.u;
            gfx.circle(c, bx, by, rad, RED);
            gfx.circleLine(c, bx, by, rad, 0.5f * v.u, Color.WHITE);
            gfx.textC(c, "!", bx, by, rad * 1.5f, Color.WHITE, 0, Paint.Align.CENTER);
        }
    }

    /** The first-run explanation of the farm: what to collect, what it does, and the streak. */
    private void drawFarmTip(Canvas c) {
        c.drawColor(Color.argb(150, 0, 0, 0));
        RectF o = v.tipBox;
        float u = v.u, ph = o.height();
        gfx.rrect(c, o.left, o.top + 1.5f * u, o.right, o.bottom + 1.5f * u, 6 * u, Gfx.darken(WOOD, 0.6f));
        gfx.rrect(c, o.left, o.top, o.right, o.bottom, 6 * u, WOOD);
        gfx.rrect(c, o.left + u, o.top + u, o.right - u, o.bottom - u, 5 * u, CREAM);
        gfx.rrect(c, o.left - u, o.top - 3 * u, o.right + u, o.top + ph * 0.14f, 5 * u, ORANGE);
        gfx.textC(c, "Your farm", o.centerX(), o.top + ph * 0.045f, 7 * u, Color.WHITE, 0xFF7A3E0B, Paint.Align.CENTER);
        float iy = o.top + ph * 0.24f, step = o.width() / (Farm.STOCKS + 0.4f);
        for (int k = 0; k < Farm.STOCKS; k++) r.drawIcon(c, Farm.STOCK_TILE[k], o.left + step * (k + 0.7f), iy, step * 0.78f);
        String[] lines = {
                "Clear these tiles in a level to fill your farm stock.",
                "Eggs hatch into hens by themselves. Seed, hay, water and potatoes feed your animals.",
                "Feed every animal each level to grow a streak, and earn up to double."};
        float size = 4.2f * u, maxW = o.width() - 12 * u, ly = o.top + ph * 0.38f;
        for (String t : lines) {
            for (String line : r.wrap(t, size, maxW)) {
                gfx.textC(c, line, o.centerX(), ly, size, 0xFF6B3F17, 0, Paint.Align.CENTER);
                ly += size * 1.3f;
            }
            ly += size * 0.55f;
        }
        r.drawButtons(c);
    }

    private void drawBanner(Canvas c) {
        RectF b = v.hub.banner;
        float u = v.u, h = b.height();
        int col = v.won ? GREEN : 0xFFE5604A;
        gfx.rrect(c, b.left, b.top + 1f * u, b.right, b.bottom + 1f * u, 5 * u, Gfx.darken(WOOD, 0.6f));
        gfx.rrect(c, b.left, b.top, b.right, b.bottom, 5 * u, WOOD);
        gfx.rrect(c, b.left + 0.7f * u, b.top + 0.7f * u, b.right - 0.7f * u, b.bottom - 0.7f * u, 4.4f * u, CREAM);
        gfx.rrect(c, b.left + 0.7f * u, b.top + 0.7f * u, b.right - 0.7f * u, b.top + 9.5f * u, 4.4f * u, col);
        gfx.rrect(c, b.left + 0.7f * u, b.top + 5f * u, b.right - 0.7f * u, b.top + 9.5f * u, 0, col);
        gfx.textC(c, v.won ? "Level Complete!" : "Out of Moves!", b.centerX(), b.top + 5.2f * u, 5.6f * u, Color.WHITE, Gfx.darken(col, 0.45f), Paint.Align.CENTER);

        String amount = "+" + v.coins;
        float fs = 7.4f * u, tw = gfx.width(amount, fs), total = 10 * u + tw, rowY = b.top + 14.6f * u;
        float sx = v.won ? b.left + 7 * u : b.centerX() - total / 2;
        gfx.coinPile(c, sx + 4.5f * u, rowY, 4.6f * u);
        gfx.textC(c, amount, sx + 10 * u, rowY, fs, 0xFFFFC928, 0xFF7A3E0B, Paint.Align.LEFT);
        if (v.won) {
            for (int i = 0; i < 3; i++) {
                boolean on = i < v.stars;
                gfx.star(c, b.right - 25 * u + i * 8.6f * u, rowY - (i == 1 ? 1.2f * u : 0), 3.7f * u,
                        on ? 0xFFFFD43B : 0xFFD8CBA8, on ? 0xFFC97700 : 0xFFB5A67E);
            }
        }
        String detail = "Market " + v.saleCoins + (v.animalCoins > 0 ? "  +  Animals " + v.animalCoins : "");
        gfx.textC(c, detail, b.centerX(), b.top + h - 2.7f * u, 3.3f * u, 0xFF8B5A2B, 0, Paint.Align.CENTER);

        // what went into the farm stock, and how the animals got on
        StringBuilder haul = new StringBuilder();
        for (int s = 0; s < Farm.STOCKS; s++) {
            int n = v.report.hauled[s];
            if (n > 0) haul.append(haul.length() > 0 ? "  " : "").append('+').append(n).append(' ').append(Farm.STOCK_NAME[s].toLowerCase());
        }
        if (v.report.hatched > 0) haul.append(haul.length() > 0 ? "  ·  " : "").append(v.report.hatched).append(v.report.hatched == 1 ? " chick hatched" : " chicks hatched");
        if (haul.length() == 0) haul.append("No farm stock this time");
        float haulSize = Math.min(3.6f * u, 3.6f * u * (b.width() - 6 * u) / Math.max(1f, gfx.width(haul.toString(), 3.6f * u)));
        gfx.textC(c, haul.toString(), b.centerX(), b.top + h - 12.5f * u, haulSize, 0xFF3E7A22, 0, Paint.Align.CENTER);
        Farm.Herd hd = v.progress.herd;
        if (v.report.hungry > 0) {
            String hungry = v.report.hungry + (v.report.hungry == 1 ? " animal went hungry" : " animals went hungry") + "  (streak " + v.report.streak + ")";
            gfx.textC(c, hungry, b.centerX(), b.top + h - 7.8f * u, 3.4f * u, RED, 0, Paint.Align.CENTER);
        } else if (v.report.streak > 0) {
            gfx.textC(c, "All fed! Streak " + v.report.streak + ": +" + v.report.bonusPct + "% animal coins", b.centerX(), b.top + h - 7.8f * u, 3.4f * u, 0xFF3E9E22, 0, Paint.Align.CENTER);
        } else if (!anyHouse(hd)) {
            gfx.textC(c, "Tap Tend Farm to build a coop for your eggs!", b.centerX(), b.top + h - 7.8f * u, 3.4f * u, 0xFF8B5A2B, 0, Paint.Align.CENTER);
        }
    }

    private void drawTabs(Canvas c) {
        FarmHub h = v.hub;
        float u = v.u;
        String[] names = {"ANIMALS", "DECOR"};
        for (int t = 0; t < 2; t++) {
            RectF b = h.tabRect[t];
            boolean on = h.tab == t;
            boolean pressed = h.tabBtn[t] != null && h.tabBtn[t].pressed;
            int base = on ? GREEN : WOOD;
            float off = pressed ? 0.6f * u : 0f, hgt = b.height(), rad = hgt * 0.4f;
            gfx.rrect(c, b.left, b.top + 0.8f * u, b.right, b.bottom + 0.8f * u, rad, Gfx.darken(base, 0.62f));
            gfx.rrect(c, b.left, b.top + off, b.right, b.bottom + off, rad, base);
            gfx.textC(c, names[t], b.centerX(), b.centerY() + off, 4.4f * u, Color.WHITE, Gfx.darken(base, 0.45f), Paint.Align.CENTER);
        }
    }

    private void drawCard(Canvas c, int i) {
        FarmHub h = v.hub;
        Progress p = v.progress;
        boolean decor = h.tab == FarmHub.TAB_DECOR;
        RectF cd = h.card[i];
        boolean pressed = h.cardBtn[i] != null && h.cardBtn[i].pressed;
        float u = v.u, off = pressed ? 0.6f * u : 0f, hgt = cd.height();
        gfx.rrect(c, cd.left, cd.top + 1f * u + off, cd.right, cd.bottom + 1f * u + off, 3.5f * u, Gfx.darken(WOOD, 0.6f));
        gfx.rrect(c, cd.left, cd.top + off, cd.right, cd.bottom + off, 3.5f * u, WOOD);
        gfx.rrect(c, cd.left + 0.6f * u, cd.top + 0.6f * u + off, cd.right - 0.6f * u, cd.bottom - 0.6f * u + off, 3f * u, CREAM);
        if (!decor && i >= FarmHub.ANIMAL_ACTIONS) {
            drawStreakCard(c, cd);
            return;
        }

        float ix = cd.left + 10 * u, iy = cd.top + hgt * 0.5f + off, box = Math.min(15 * u, hgt * 0.68f);
        drawIcon(c, decor, i, ix, iy, box);

        float tx = cd.left + 19.5f * u;
        gfx.textC(c, decor ? Farm.DECOR_NAME[i] : Farm.ANIMAL_NAME[i], tx, cd.top + hgt * 0.24f + off, 4.4f * u, INK, 0, Paint.Align.LEFT);
        float subY = cd.top + hgt * 0.47f + off, py = cd.top + hgt * 0.73f + off;
        if (decor) {
            gfx.textC(c, "Decoration", tx, subY, 3.2f * u, 0xFF8B5A2B, 0, Paint.Align.LEFT);
            if (p.decor[i]) {
                gfx.check(c, tx + 2.2f * u, py, 2.4f * u);
                gfx.textC(c, "Owned", tx + 6 * u, py, 4f * u, 0xFF3E9E22, 0, Paint.Align.LEFT);
            } else {
                priceChip(c, tx + 10 * u, py, Farm.DECOR_COST[i], p.totalCoins >= Farm.DECOR_COST[i], 4.4f * u);
            }
        } else {
            speciesCard(c, i, cd, tx, subY, py);
        }
    }

    /** One species: house level pips top right, a status line, and what tapping the card does next. */
    private void speciesCard(Canvas c, int sp, RectF cd, float tx, float subY, float py) {
        Progress p = v.progress;
        Farm.Herd hd = p.herd;
        float u = v.u, pipY = subY - (subY - cd.top) * 0.5f + 0.4f * u;
        int act = p.nextAction(sp), lvl = hd.house[sp];
        for (int k = 0; k < Farm.MAX_HOUSE_LEVEL; k++) {
            float px = cd.right - 4f * u - (Farm.MAX_HOUSE_LEVEL - 1 - k) * 3.1f * u;
            boolean on = k < lvl;
            gfx.circle(c, px, pipY, 1.15f * u, on ? 0xFFFFC928 : 0x33000000);
            gfx.circleLine(c, px, pipY, 1.15f * u, 0.32f * u, on ? 0xFFC97700 : 0xFF8B5A2B);
        }
        int cap = Farm.capacity(sp, lvl);
        int have = hd.count[sp] + (sp == Farm.CHICKEN ? hd.chickCount() : 0);
        int brown = 0xFF8B5A2B;
        String sub;
        switch (act) {
            case Progress.ACT_LOCKED:
                sub = "Locked";
                break;
            case Progress.ACT_BUILD:
                sub = "Build · holds " + Farm.capacity(sp, 1);
                break;
            case Progress.ACT_UPGRADE:
                sub = "Full · +" + (Farm.capacity(sp, lvl + 1) - cap) + " room";
                break;
            case Progress.ACT_FULL:
                sub = have + "/" + cap + " max";
                break;
            default:
                sub = have + "/" + cap + "  earns " + Farm.COINS[sp];
        }
        gfx.textC(c, sub, tx, subY, 3.2f * u, brown, 0, Paint.Align.LEFT);
        switch (act) {
            case Progress.ACT_LOCKED:
                gfx.lock(c, tx + 2.2f * u, py, 2.4f * u);
                gfx.textC(c, "Beat level " + (Farm.HOUSE_UNLOCK_LEVEL[sp] - 1), tx + 6 * u, py, 3.6f * u, brown, 0, Paint.Align.LEFT);
                break;
            case Progress.ACT_HATCHING:
                gfx.textC(c, "Eggs " + hd.stock[Farm.EGG] + "/" + Farm.EGGS_PER_CHICK, tx, py, 4f * u, 0xFF3E9E22, 0, Paint.Align.LEFT);
                break;
            case Progress.ACT_FULL:
                gfx.check(c, tx + 2.2f * u, py, 2.4f * u);
                break;
            default: {
                int price = p.actionPrice(sp);
                priceChip(c, tx + 10 * u, py, price, p.totalCoins >= price, 4.4f * u);
            }
        }
    }

    /** The read-only card on the animals tab: the fed streak and what it pays. */
    private void drawStreakCard(Canvas c, RectF cd) {
        Farm.Herd hd = v.progress.herd;
        float u = v.u, hgt = cd.height(), lx = cd.left + 4 * u;
        gfx.textC(c, "Fed streak", lx, cd.top + hgt * 0.22f, 3.8f * u, INK, 0, Paint.Align.LEFT);
        if (hd.animals() == 0) {
            gfx.textC(c, "Feed every animal each", lx, cd.top + hgt * 0.5f, 3.2f * u, 0xFF8B5A2B, 0, Paint.Align.LEFT);
            gfx.textC(c, "level to build a streak.", lx, cd.top + hgt * 0.74f, 3.2f * u, 0xFF8B5A2B, 0, Paint.Align.LEFT);
            return;
        }
        int pct = Farm.streakPct(hd.streak);
        gfx.textC(c, String.valueOf(hd.streak), lx + 3 * u, cd.top + hgt * 0.62f, 9f * u, hd.streak > 0 ? 0xFF3E9E22 : 0xFF8B5A2B, 0xFFFFFFFF, Paint.Align.LEFT);
        gfx.textC(c, "+" + pct + "% coins", lx + 16 * u, cd.top + hgt * 0.5f, 3.8f * u, hd.streak > 0 ? 0xFF3E9E22 : 0xFF8B5A2B, 0, Paint.Align.LEFT);
        gfx.textC(c, "Any hungry: -" + Farm.STREAK_LOSS, lx + 16 * u, cd.top + hgt * 0.76f, 3.2f * u, 0xFF8B5A2B, 0, Paint.Align.LEFT);
    }

    /** What is in the farm stock: have, over what the herd needs for the next level when it needs any. Amber when short. */
    private void drawStockStrip(Canvas c) {
        RectF s = v.hub.stockStrip;
        Farm.Herd hd = v.progress.herd;
        float u = v.u, w = s.width() / Farm.STOCKS;
        gfx.rrect(c, s.left, s.top + 0.8f * u, s.right, s.bottom + 0.8f * u, 3 * u, Gfx.darken(WOOD, 0.6f));
        gfx.rrect(c, s.left, s.top, s.right, s.bottom, 3 * u, WOOD);
        gfx.rrect(c, s.left + 0.6f * u, s.top + 0.6f * u, s.right - 0.6f * u, s.bottom - 0.6f * u, 2.6f * u, CREAM);
        for (int k = 0; k < Farm.STOCKS; k++) {
            float x = s.left + k * w;
            r.drawIcon(c, Farm.STOCK_TILE[k], x + 3.8f * u, s.centerY(), 6.2f * u);
            int need = hd.need(k), have = hd.stock[k];
            boolean low = need > have;
            gfx.textC(c, String.valueOf(have), x + 7.6f * u, s.centerY() - 2.2f * u, 4.2f * u, low ? 0xFFD9822B : INK, 0, Paint.Align.LEFT);
            String second = k == Farm.EGG ? "per 5" : need > 0 ? "of " + need : "";
            gfx.textC(c, second, x + 7.6f * u, s.centerY() + 2.9f * u, 2.7f * u, low ? 0xFFD9822B : 0xFF8B5A2B, 0, Paint.Align.LEFT);
        }
    }

    /** The scene art drawn small, centred in a box of side box. */
    private void drawIcon(Canvas c, boolean decor, int i, float cx, float cy, float box) {
        if (!decor) {
            switch (i) {
                case Farm.CHICKEN:
                    coop(c, cx, cy - 0.06f * box, box * 0.95f, 1);
                    break;
                case Farm.COW:
                    barn(c, cx, cy, box * 0.95f, 1);
                    break;
                case Farm.PIG:
                    sty(c, cx, cy, box * 0.95f, 1);
                    break;
                case Farm.SHEEP:
                    pen(c, cx, cy, box * 0.95f, 1);
                    break;
                default:
                    hive(c, cx, cy, box * 1.05f, 1);
            }
            return;
        }
        switch (i) {
            case Farm.FENCE:
                fence(c, cx - 0.5f * box, cx + 0.5f * box, cy + 0.22f * box, 0.5f * box);
                break;
            case Farm.FLOWERS:
                flowers(c, cx - 0.05f * box, cy, box * 1.12f);
                break;
            case Farm.POND:
                pond(c, cx, cy, box * 1.0f);
                break;
            case Farm.OAK:
                oak(c, cx, cy - 0.05f * box, box * 0.9f);
                break;
            case Farm.WINDMILL:
                windmill(c, cx, cy + 0.05f * box, box * 0.95f);
                break;
            default:
                balloon(c, cx, cy - 0.05f * box, box * 0.95f);
        }
    }

    /** Centre of an item in the scene, in pixels, for sparkles when it is bought. */
    float[] itemCenter(boolean decor, int i) {
        RectF s = v.hub.scene;
        float[] p = decor ? DECOR_POS[i] : HOUSE_POS[i];
        return new float[]{s.left + p[0] * s.width(), s.top + p[1] * s.height()};
    }

    // ---------------------------------------------------------------- the scene

    /** The farm scene in s. The hub frames it and labels it; the play screen draws it bare and pulses a house when a crop lands. */
    private void drawScene(Canvas c, RectF s, boolean hub) {
        Progress p = v.progress;
        Farm.Herd hd = p.herd;
        float u = v.u, ws = s.width(), hs = s.height();
        c.save();
        if (hub) gfx.clipRRect(c, s, 4 * u);
        gfx.rrect(c, s.left, s.top, s.right, s.bottom, 0, 0xFF9DDCF9);
        gfx.circle(c, s.left + ws * 0.90f, s.top + hs * 0.10f, ws * 0.05f, 0xFFFFE066);
        gfx.ellipse(c, s.left + ws * 0.25f, s.top + hs * 0.10f, ws * 0.10f, hs * 0.03f, 0xCCFFFFFF);
        gfx.ellipse(c, s.left + ws * 0.32f, s.top + hs * 0.085f, ws * 0.07f, hs * 0.03f, 0xCCFFFFFF);
        gfx.ellipse(c, s.left + ws * 0.20f, s.top + hs * 0.38f, ws * 0.46f, hs * 0.13f, 0xFFB5E58A);
        gfx.ellipse(c, s.left + ws * 0.88f, s.top + hs * 0.37f, ws * 0.40f, hs * 0.12f, 0xFFA6DC72);
        gfx.rrect(c, s.left, s.top + hs * 0.36f, s.right, s.bottom, 0, 0xFF8BD450);
        gfx.rrect(c, s.left, s.top + hs * 0.88f, s.right, s.bottom, 0, 0xFF74C043);

        // back to front; houses that are not built yet show as faded outlines once their level is reached
        if (p.decor[Farm.WINDMILL]) decor(c, s, Farm.WINDMILL);
        if (p.decor[Farm.BALLOON]) decor(c, s, Farm.BALLOON);
        if (p.decor[Farm.OAK]) decor(c, s, Farm.OAK);
        for (int sp : new int[]{Farm.BEE, Farm.COW, Farm.CHICKEN, Farm.PIG, Farm.SHEEP}) {
            boolean built = hd.house[sp] > 0;
            if (!built && !p.houseUnlocked(sp)) continue;
            if (!built) c.saveLayerAlpha(s.left, s.top, s.right, s.bottom, 85);
            float[] pos = HOUSE_POS[sp];
            float x = s.left + pos[0] * ws, y = s.top + pos[1] * hs, sz = pos[2] * ws;
            c.save();
            float pulse = hub ? 1f : 1f + 0.12f * bump[sp] * (float) Math.sin(bump[sp] * Math.PI);
            c.scale(pulse, pulse, x, y + 0.4f * sz);
            switch (sp) {
                case Farm.BEE:
                    hive(c, x, y, sz, hd.house[sp]);
                    break;
                case Farm.COW:
                    barn(c, x, y, sz, hd.house[sp]);
                    break;
                case Farm.CHICKEN:
                    coop(c, x, y, sz, hd.house[sp]);
                    break;
                case Farm.PIG:
                    sty(c, x, y, sz, hd.house[sp]);
                    break;
                default:
                    pen(c, x, y, sz, hd.house[sp]);
            }
            c.restore();
            if (!built) c.restore();
        }
        if (p.decor[Farm.FLOWERS]) decor(c, s, Farm.FLOWERS);
        if (p.decor[Farm.POND]) decor(c, s, Farm.POND);
        drawAnimals(c, s);
        if (p.decor[Farm.FENCE]) decor(c, s, Farm.FENCE);
        c.restore();
        if (!hub) return;
        gfx.rrectLine(c, s.left, s.top, s.right, s.bottom, 4 * u, 1f * u, WOOD);

        int animals = hd.animals();
        String label = !anyHouse(hd) ? "Build a coop to start" : animals == 0 ? "Hatch eggs, buy animals" : animals + (animals == 1 ? " animal" : " animals");
        float tw = gfx.width(label, 3.6f * u), pl = s.left + 2.5f * u, pt = s.top + 2.5f * u;
        gfx.rrect(c, pl, pt, pl + tw + 5 * u, pt + 6 * u, 3 * u, 0xCC6B3F17);
        gfx.textC(c, label, pl + 2.5f * u + tw / 2, pt + 3 * u, 3.6f * u, animals > 0 ? 0xFFFFE066 : Color.WHITE, 0, Paint.Align.CENTER);
        if (hd.streak > 0 && animals > 0) {
            String st = "Streak " + hd.streak + "  +" + Farm.streakPct(hd.streak) + "%";
            float sw = gfx.width(st, 3.4f * u), sl = s.right - 2.5f * u - sw - 5 * u;
            gfx.rrect(c, sl, pt, sl + sw + 5 * u, pt + 6 * u, 3 * u, 0xCC2F7A1A);
            gfx.textC(c, st, sl + 2.5f * u + sw / 2, pt + 3 * u, 3.4f * u, Color.WHITE, 0, Paint.Align.CENTER);
        }
    }

    // ---------------------------------------------------------------- the play-screen strip

    private static final float HOP_TIME = 0.55f, HATCH_TIME = 1.6f;

    /** One crop in flight from the board to a house in the strip. */
    private static final class Hop {
        int tile, species;
        float sx, sy, delay, t;
        boolean egg;
    }

    /** An egg cracking open in the coop doorway and a chick hopping out. */
    private static final class Hatch {
        float t;
    }

    /** Forget the last level's flights. */
    void resetStripEvents() {
        hops.clear();
        hatches.clear();
        java.util.Arrays.fill(bump, 0f);
        eggsIn = 0;
    }

    /** A farm-stock tile was cleared at (x, y) on the board: send it to a house that wants it. No house for it, no flight. */
    void launch(int tileType, float x, float y, float delay) {
        int stock = Farm.stockOf(tileType);
        if (stock < 0 || v.farmStrip.height() <= 0) return;
        Farm.Herd hd = v.progress.herd;
        int sp = -1;
        if (stock == Farm.EGG) {
            if (hd.house[Farm.CHICKEN] > 0) sp = Farm.CHICKEN;
        } else {
            for (int k = 0; k < Farm.SPECIES && sp < 0; k++) {
                int cand = (nextHouse[stock] + k) % Farm.SPECIES;
                if (hd.house[cand] > 0 && Farm.FEED[cand][stock] > 0) sp = cand;
            }
            if (sp >= 0) nextHouse[stock] = sp + 1;
        }
        if (sp < 0) return;
        Hop h = new Hop();
        h.tile = tileType;
        h.species = sp;
        h.sx = x;
        h.sy = y;
        h.delay = delay;
        h.egg = stock == Farm.EGG;
        hops.add(h);
    }

    void update(float dt) {
        for (int i = 0; i < bump.length; i++) bump[i] = Math.max(0f, bump[i] - dt * 4f);
        for (int i = hops.size() - 1; i >= 0; i--) {
            Hop h = hops.get(i);
            if (h.delay > 0) {
                h.delay -= dt;
                continue;
            }
            h.t += dt / HOP_TIME;
            if (h.t < 1f) continue;
            hops.remove(i);
            float[] d = houseSpot(h.species);
            bump[h.species] = 1f;
            v.fx.sparkle(d[0], d[1], v.u, v.cell);
            if (h.egg) {
                eggsIn++;
                if ((v.progress.herd.stock[Farm.EGG] + eggsIn) % Farm.EGGS_PER_CHICK == 0) hatches.add(new Hatch());
            }
        }
        for (int i = hatches.size() - 1; i >= 0; i--) {
            hatches.get(i).t += dt;
            if (hatches.get(i).t > HATCH_TIME) hatches.remove(i);
        }
    }

    /** The door of a house in the strip, in pixels: where crops land. */
    float[] houseSpot(int sp) {
        layoutStrip(v.farmStrip);
        RectF s = stripScene;
        float[] pos = HOUSE_POS[sp];
        return new float[]{s.left + pos[0] * s.width(), s.top + pos[1] * s.height()};
    }

    /**
     * The hub's scene, fitted below the goal bar: full width when the strip is tall enough, else scaled down by its height and
     * centred (the sides are filled with sky and grass). It sits on the strip's bottom edge.
     */
    private void layoutStrip(RectF full) {
        float top = full.top + 12 * v.u, h = full.bottom - top, w = full.width();
        float vw = Math.min(w, h / 0.62f), vh = Math.min(h, 0.9f * vw);
        stripScene.set(full.centerX() - vw / 2, full.bottom - vh, full.centerX() + vw / 2, full.bottom);
    }

    /** Crops in flight, drawn over the board and the strip. */
    void drawStripFlights(Canvas c) {
        for (Hop h : hops) {
            if (h.delay > 0) continue;
            float[] d = houseSpot(h.species);
            float t = h.t * h.t * (3 - 2 * h.t);
            float cpx = (h.sx + d[0]) / 2, cpy = Math.min(h.sy, d[1]) - 10 * v.u;
            float x = (1 - t) * (1 - t) * h.sx + 2 * (1 - t) * t * cpx + t * t * d[0];
            float y = (1 - t) * (1 - t) * h.sy + 2 * (1 - t) * t * cpy + t * t * d[1];
            r.drawIcon(c, h.tile, x, y, v.cell * (0.75f - 0.35f * t));
        }
    }

    /** How far above a house's centre its roof peak is, as a fraction of its size: where the hunger tag sits. */
    private static final float[] TAG_UP = {0.5f, 0.46f, 0.4f, 0.2f, 0.4f};

    /** How many of a species the stock and this level's harvest would feed right now, above its house. Green when all, amber some, red none. */
    private void hungerTag(Canvas c, RectF s, int sp, int fed) {
        int animals = v.progress.herd.count[sp];
        if (animals == 0 || v.progress.herd.house[sp] == 0) return;
        float[] pos = HOUSE_POS[sp];
        float u = v.u, size = 3.1f * u, x = s.left + pos[0] * s.width(), y = s.top + pos[1] * s.height() - TAG_UP[sp] * pos[2] * s.width();
        String label = fed + "/" + animals;
        float w = gfx.width(label, size) + 4 * u, h = 5 * u;
        int col = fed >= animals ? 0xDD2F7A1A : fed > 0 ? 0xDDB8741A : 0xDD9E2A24;
        gfx.rrect(c, x - w / 2, y - h / 2, x + w / 2, y + h / 2, h / 2, col);
        gfx.textC(c, label, x, y, size, Color.WHITE, 0, Paint.Align.CENTER);
    }

    /** The farm under the board: the goal bar sits over its sky, the hub's scene fills the rest, with a hunger tag on each house. */
    void drawStrip(Canvas c, RectF full) {
        layoutStrip(full);
        RectF s = stripScene;
        Farm.Herd hd = v.progress.herd;
        float u = v.u, ws = s.width(), hs = s.height();
        gfx.rrect(c, full.left, full.top + 1f * u, full.right, full.bottom + 1f * u, 4 * u, Gfx.darken(WOOD, 0.6f));
        c.save();
        gfx.clipRRect(c, full, 4 * u);
        gfx.rrect(c, full.left, full.top, full.right, full.bottom, 0, 0xFF9DDCF9);
        gfx.rrect(c, full.left, s.top + hs * 0.36f, full.right, full.bottom, 0, 0xFF8BD450);
        gfx.rrect(c, full.left, s.top + hs * 0.88f, full.right, full.bottom, 0, 0xFF74C043);
        drawScene(c, s, false);

        int[] fedNow = hd.provisionalFed(v.harvest);
        for (int sp = 0; sp < Farm.SPECIES; sp++) hungerTag(c, s, sp, fedNow[sp]);
        float[] cp = HOUSE_POS[Farm.CHICKEN];
        float cx = s.left + cp[0] * ws, base = s.top + cp[1] * hs + 0.34f * cp[2] * ws, size = cp[2] * ws;
        for (Hatch h : hatches) {
            if (h.t < 0.6f) {
                c.save();
                c.rotate((float) Math.sin(h.t * 40) * 14f, cx, base);
                r.drawIcon(c, Farm.STOCK_TILE[Farm.EGG], cx, base - 0.1f * size, 0.22f * size);
                c.restore();
            } else {
                float k = (h.t - 0.6f) / (HATCH_TIME - 0.6f), hop = (float) Math.abs(Math.sin(k * Math.PI * 3)) * 0.18f * size * (1 - k);
                chick(c, cx + k * 0.4f * size, base + 0.06f * size - hop, 0.17f * size * Math.min(1f, k * 6f), 1f, h.t * 4f);
            }
        }
        boolean any = false;
        for (int h : hd.house) any |= h > 0;
        if (!any) {
            String msg = "Build a coop in My Farm to fill this field";
            float tsz = 3.6f * u, tw = gfx.width(msg, tsz), pl = full.centerX() - tw / 2 - 2.5f * u, pt = full.top + 15 * u;
            gfx.rrect(c, pl, pt, pl + tw + 5 * u, pt + 6 * u, 3 * u, 0xCC6B3F17);
            gfx.textC(c, msg, full.centerX(), pt + 3 * u, tsz, Color.WHITE, 0, Paint.Align.CENTER);
        }
        c.restore();
        gfx.rrectLine(c, full.left, full.top, full.right, full.bottom, 4 * u, 1f * u, WOOD);
    }

    private static boolean anyHouse(Farm.Herd hd) {
        for (int h : hd.house) if (h > 0) return true;
        return false;
    }

    private void decor(Canvas c, RectF s, int item) {
        float[] pos = DECOR_POS[item];
        float ws = s.width(), cx = s.left + pos[0] * ws, cy = s.top + pos[1] * s.height(), sz = pos[2] * ws;
        switch (item) {
            case Farm.FENCE:
                fence(c, s.left + 0.01f * ws, s.right - 0.01f * ws, cy, 0.09f * ws);
                break;
            case Farm.FLOWERS:
                flowers(c, cx, cy, sz);
                break;
            case Farm.POND:
                pond(c, cx, cy, sz);
                break;
            case Farm.OAK:
                oak(c, cx, cy, sz);
                break;
            case Farm.WINDMILL:
                windmill(c, cx, cy, sz);
                break;
            default:
                balloon(c, cx, cy, sz);
        }
    }

    // ---- items, each centred on (cx, cy) and roughly s wide and tall

    private void barn(Canvas c, float cx, float cy, float s, int lvl) {
        if (lvl >= 2) silo(c, cx - 0.53f * s, cy + 0.42f * s, s);
        float w = 0.36f * s;
        gfx.rrect(c, cx - w, cy - 0.02f * s, cx + w, cy + 0.42f * s, 0.03f * s, 0xFFD9453A);
        gfx.rrectLine(c, cx - w, cy - 0.02f * s, cx + w, cy + 0.42f * s, 0.03f * s, 0.025f * s, OUT);
        gfx.polygon(c, 0xFF9E2A24, OUT, 0.03f * s, cx - 0.44f * s, cy, cx - 0.36f * s, cy - 0.24f * s, cx, cy - 0.4f * s,
                cx + 0.36f * s, cy - 0.24f * s, cx + 0.44f * s, cy);
        gfx.rrect(c, cx - 0.07f * s, cy - 0.2f * s, cx + 0.07f * s, cy - 0.06f * s, 0.015f * s, CREAM);
        gfx.rrectLine(c, cx - 0.07f * s, cy - 0.2f * s, cx + 0.07f * s, cy - 0.06f * s, 0.015f * s, 0.02f * s, OUT);
        gfx.rrect(c, cx - 0.16f * s, cy + 0.1f * s, cx + 0.16f * s, cy + 0.42f * s, 0.02f * s, CREAM);
        gfx.lineSeg(c, cx - 0.16f * s, cy + 0.1f * s, cx + 0.16f * s, cy + 0.42f * s, 0.03f * s, 0xFFD9453A);
        gfx.lineSeg(c, cx + 0.16f * s, cy + 0.1f * s, cx - 0.16f * s, cy + 0.42f * s, 0.03f * s, 0xFFD9453A);
        gfx.rrectLine(c, cx - 0.16f * s, cy + 0.1f * s, cx + 0.16f * s, cy + 0.42f * s, 0.02f * s, 0.025f * s, OUT);
        if (lvl >= 3) {
            // a stack of hay bales beside the barn
            r.drawIcon(c, Farm.STOCK_TILE[Farm.HAY], cx + 0.5f * s, cy + 0.32f * s, 0.26f * s);
            r.drawIcon(c, Farm.STOCK_TILE[Farm.HAY], cx + 0.44f * s, cy + 0.14f * s, 0.2f * s);
        }
    }

    private void silo(Canvas c, float x, float yBase, float s) {
        float w = 0.1f * s, top = yBase - 0.66f * s;
        gfx.rrect(c, x - w, top, x + w, yBase, 0.02f * s, 0xFFC9D6E3);
        gfx.lineSeg(c, x - w, top + 0.18f * s, x + w, top + 0.18f * s, 0.015f * s, 0xFF8FA3B8);
        gfx.lineSeg(c, x - w, top + 0.34f * s, x + w, top + 0.34f * s, 0.015f * s, 0xFF8FA3B8);
        gfx.rrectLine(c, x - w, top, x + w, yBase, 0.02f * s, 0.02f * s, OUT);
        gfx.ellipse(c, x, top, w * 1.1f, 0.09f * s, OUT);
        gfx.ellipse(c, x, top, w, 0.075f * s, 0xFFD9453A);
    }

    private void coop(Canvas c, float cx, float cy, float s, int lvl) {
        gfx.rrect(c, cx - 0.38f * s, cy - 0.04f * s, cx + 0.38f * s, cy + 0.32f * s, 0.03f * s, 0xFFF0C987);
        gfx.rrectLine(c, cx - 0.38f * s, cy - 0.04f * s, cx + 0.38f * s, cy + 0.32f * s, 0.03f * s, 0.025f * s, OUT);
        gfx.polygon(c, 0xFFD9453A, OUT, 0.03f * s, cx - 0.46f * s, cy - 0.02f * s, cx, cy - 0.42f * s, cx + 0.46f * s, cy - 0.02f * s);
        gfx.circle(c, cx, cy - 0.17f * s, 0.07f * s, CREAM);
        gfx.circleLine(c, cx, cy - 0.17f * s, 0.07f * s, 0.02f * s, OUT);
        gfx.rrect(c, cx - 0.1f * s, cy + 0.08f * s, cx + 0.1f * s, cy + 0.32f * s, 0.03f * s, 0xFF8B5A2B);
        gfx.rrectLine(c, cx - 0.1f * s, cy + 0.08f * s, cx + 0.1f * s, cy + 0.32f * s, 0.03f * s, 0.02f * s, OUT);
        if (lvl >= 2) fence(c, cx - 0.52f * s, cx + 0.52f * s, cy + 0.5f * s, 0.2f * s);
        if (lvl >= 3) r.drawIcon(c, Art.GOLDEN_EGG, cx, cy - 0.5f * s, 0.3f * s);
    }

    private void sty(Canvas c, float cx, float cy, float s, int lvl) {
        // a low brown shed with a pitched roof and a mud patch in front
        gfx.ellipse(c, cx, cy + 0.36f * s, 0.46f * s, 0.1f * s, 0xFF9A7B4F);
        gfx.rrect(c, cx - 0.36f * s, cy - 0.02f * s, cx + 0.36f * s, cy + 0.34f * s, 0.03f * s, 0xFFC98A5A);
        gfx.rrectLine(c, cx - 0.36f * s, cy - 0.02f * s, cx + 0.36f * s, cy + 0.34f * s, 0.03f * s, 0.025f * s, OUT);
        gfx.polygon(c, 0xFF8B5A2B, OUT, 0.03f * s, cx - 0.44f * s, cy, cx, cy - 0.34f * s, cx + 0.44f * s, cy);
        gfx.rrect(c, cx - 0.12f * s, cy + 0.1f * s, cx + 0.12f * s, cy + 0.34f * s, 0.03f * s, 0xFF5B3A1E);
        gfx.rrectLine(c, cx - 0.12f * s, cy + 0.1f * s, cx + 0.12f * s, cy + 0.34f * s, 0.03f * s, 0.02f * s, OUT);
        if (lvl >= 2) fence(c, cx - 0.52f * s, cx + 0.52f * s, cy + 0.5f * s, 0.2f * s);
        if (lvl >= 3) gfx.circle(c, cx + 0.42f * s, cy - 0.3f * s, 0.07f * s, 0xFFFFB3B8);
    }

    private void pen(Canvas c, float cx, float cy, float s, int lvl) {
        // a fenced paddock with a feed trough
        gfx.rrect(c, cx - 0.5f * s, cy - 0.1f * s, cx + 0.5f * s, cy + 0.34f * s, 0.05f * s, 0xFF7FC444);
        fence(c, cx - 0.5f * s, cx + 0.5f * s, cy - 0.1f * s + 0.12f * s, 0.26f * s);
        fence(c, cx - 0.5f * s, cx + 0.5f * s, cy + 0.34f * s, 0.26f * s);
        gfx.rrect(c, cx - 0.16f * s, cy + 0.1f * s, cx + 0.16f * s, cy + 0.2f * s, 0.02f * s, 0xFFB5793B);
        gfx.rrectLine(c, cx - 0.16f * s, cy + 0.1f * s, cx + 0.16f * s, cy + 0.2f * s, 0.02f * s, 0.018f * s, OUT);
        gfx.rrect(c, cx - 0.13f * s, cy + 0.11f * s, cx + 0.13f * s, cy + 0.14f * s, 0.01f * s, 0xFFF2D06B);
        if (lvl >= 3) gfx.circle(c, cx + 0.4f * s, cy - 0.16f * s, 0.05f * s, 0xFFFFFFFF);
    }

    private void hive(Canvas c, float cx, float cy, float s, int lvl) {
        for (int k = 0; k < 3; k++) {
            float y = cy + 0.26f * s - k * 0.2f * s, w = (0.3f - 0.04f * k) * s;
            gfx.rrect(c, cx - w, y - 0.1f * s, cx + w, y + 0.1f * s, 0.1f * s, k % 2 == 0 ? 0xFFFFC928 : 0xFFFFB300);
            gfx.rrectLine(c, cx - w, y - 0.1f * s, cx + w, y + 0.1f * s, 0.1f * s, 0.02f * s, OUT);
        }
        gfx.rrect(c, cx - 0.2f * s, cy - 0.3f * s, cx + 0.2f * s, cy - 0.15f * s, 0.06f * s, 0xFFB5793B);
        gfx.rrectLine(c, cx - 0.2f * s, cy - 0.3f * s, cx + 0.2f * s, cy - 0.15f * s, 0.06f * s, 0.02f * s, OUT);
        gfx.circle(c, cx, cy + 0.28f * s, 0.05f * s, OUT);
        if (lvl >= 2) gfx.rrect(c, cx + 0.4f * s, cy + 0.12f * s, cx + 0.62f * s, cy + 0.36f * s, 0.03f * s, 0xFFE9B04A);
    }

    // ---- animals, each centred on (x, y) with s the body length; dir is 1 facing right, -1 facing left

    private void chicken(Canvas c, float x, float y, float s, float dir, float phase) {
        float peck = Math.max(0f, (float) Math.sin(phase * 2.2f)) * 0.1f * s;
        for (float lx : new float[]{-0.06f, 0.1f}) {
            gfx.lineSeg(c, x + dir * lx * s, y + 0.22f * s, x + dir * lx * s, y + 0.4f * s, 0.04f * s, 0xFFFF9B26);
        }
        gfx.polygon(c, Color.WHITE, OUT, 0.02f * s, x - dir * 0.26f * s, y - 0.02f * s, x - dir * 0.46f * s, y - 0.22f * s, x - dir * 0.4f * s, y + 0.08f * s);
        gfx.ellipse(c, x, y + 0.06f * s, 0.36f * s, 0.28f * s, OUT);
        gfx.ellipse(c, x, y + 0.06f * s, 0.33f * s, 0.25f * s, Color.WHITE);
        gfx.ellipse(c, x - dir * 0.04f * s, y + 0.1f * s, 0.17f * s, 0.12f * s, 0xFFEADFC9);
        float hx = x + dir * 0.26f * s, hy = y - 0.2f * s + peck;
        gfx.circle(c, hx, hy - 0.16f * s, 0.085f * s, OUT);
        gfx.circle(c, hx, hy - 0.16f * s, 0.06f * s, RED);
        gfx.circle(c, hx, hy, 0.19f * s, OUT);
        gfx.circle(c, hx, hy, 0.165f * s, Color.WHITE);
        gfx.polygon(c, 0xFFFFB300, OUT, 0.015f * s, hx + dir * 0.14f * s, hy - 0.04f * s, hx + dir * 0.3f * s, hy + 0.01f * s, hx + dir * 0.14f * s, hy + 0.06f * s);
        gfx.circle(c, hx + dir * 0.05f * s, hy - 0.03f * s, 0.03f * s, OUT);
        gfx.circle(c, hx + dir * 0.02f * s, hy + 0.11f * s, 0.04f * s, RED);
    }

    private void chick(Canvas c, float x, float y, float s, float dir, float phase) {
        y -= Math.abs((float) Math.sin(phase * 3f)) * 0.08f * s;
        gfx.lineSeg(c, x - 0.07f * s, y + 0.28f * s, x - 0.07f * s, y + 0.4f * s, 0.035f * s, 0xFFFF9B26);
        gfx.lineSeg(c, x + 0.07f * s, y + 0.28f * s, x + 0.07f * s, y + 0.4f * s, 0.035f * s, 0xFFFF9B26);
        gfx.circle(c, x, y, 0.36f * s, OUT);
        gfx.circle(c, x, y, 0.32f * s, 0xFFFFE066);
        gfx.polygon(c, 0xFFFF9B26, OUT, 0.015f * s, x + dir * 0.28f * s, y - 0.04f * s, x + dir * 0.44f * s, y + 0.02f * s, x + dir * 0.28f * s, y + 0.08f * s);
        gfx.circle(c, x + dir * 0.13f * s, y - 0.08f * s, 0.04f * s, OUT);
    }

    private void cow(Canvas c, float x, float y, float s, float dir, float phase) {
        final int spot = 0xFF4A3A33, pink = 0xFFFFB3B8;
        float bob = (float) Math.sin(phase * 1.2f) * 0.012f * s;
        for (float lx : new float[]{-0.28f, -0.14f, 0.16f, 0.3f}) {
            float px = x + dir * lx * s;
            gfx.rrect(c, px - 0.05f * s, y + 0.1f * s, px + 0.05f * s, y + 0.34f * s, 0.02f * s, OUT);
            gfx.rrect(c, px - 0.035f * s, y + 0.1f * s, px + 0.035f * s, y + 0.3f * s, 0.015f * s, Color.WHITE);
            gfx.rrect(c, px - 0.04f * s, y + 0.29f * s, px + 0.04f * s, y + 0.34f * s, 0.01f * s, spot);
        }
        gfx.lineSeg(c, x - dir * 0.4f * s, y - 0.08f * s, x - dir * 0.48f * s, y + 0.14f * s, 0.03f * s, OUT);
        gfx.circle(c, x - dir * 0.48f * s, y + 0.16f * s, 0.04f * s, spot);
        gfx.ellipse(c, x, y, 0.42f * s, 0.25f * s, OUT);
        gfx.ellipse(c, x, y, 0.395f * s, 0.225f * s, Color.WHITE);
        gfx.circle(c, x - dir * 0.16f * s, y - 0.06f * s, 0.09f * s, spot);
        gfx.circle(c, x + dir * 0.06f * s, y + 0.08f * s, 0.07f * s, spot);
        gfx.circle(c, x - dir * 0.3f * s, y + 0.08f * s, 0.06f * s, spot);
        gfx.ellipse(c, x - dir * 0.1f * s, y + 0.22f * s, 0.06f * s, 0.035f * s, pink);
        float hx = x + dir * 0.42f * s, hy = y - 0.1f * s + bob;
        gfx.polygon(c, CREAM, OUT, 0.012f * s, hx - dir * 0.03f * s, hy - 0.11f * s, hx - dir * 0.01f * s, hy - 0.2f * s, hx + dir * 0.04f * s, hy - 0.11f * s);
        gfx.ellipse(c, hx - dir * 0.1f * s, hy - 0.06f * s, 0.065f * s, 0.035f * s, OUT);
        gfx.ellipse(c, hx, hy, 0.16f * s, 0.14f * s, OUT);
        gfx.ellipse(c, hx, hy, 0.145f * s, 0.125f * s, Color.WHITE);
        gfx.ellipse(c, hx + dir * 0.08f * s, hy + 0.05f * s, 0.09f * s, 0.07f * s, pink);
        gfx.circle(c, hx + dir * 0.1f * s, hy + 0.05f * s, 0.012f * s, OUT);
        gfx.circle(c, hx + dir * 0.02f * s, hy - 0.04f * s, 0.022f * s, OUT);
    }

    private void pig(Canvas c, float x, float y, float s, float dir, float phase) {
        final int pink = 0xFFFFB3C1, dark = 0xFFE8899C;
        float bob = (float) Math.sin(phase * 2.4f) * 0.015f * s;
        for (float lx : new float[]{-0.22f, -0.1f, 0.12f, 0.24f}) {
            float px = x + dir * lx * s;
            gfx.rrect(c, px - 0.045f * s, y + 0.12f * s, px + 0.045f * s, y + 0.3f * s, 0.015f * s, OUT);
            gfx.rrect(c, px - 0.03f * s, y + 0.12f * s, px + 0.03f * s, y + 0.28f * s, 0.012f * s, dark);
        }
        gfx.lineSeg(c, x - dir * 0.36f * s, y - 0.04f * s, x - dir * 0.44f * s, y - 0.12f * s, 0.03f * s, OUT);
        gfx.circle(c, x - dir * 0.45f * s, y - 0.13f * s, 0.035f * s, dark);
        gfx.ellipse(c, x, y, 0.38f * s, 0.26f * s, OUT);
        gfx.ellipse(c, x, y, 0.35f * s, 0.235f * s, pink);
        gfx.circle(c, x - dir * 0.12f * s, y - 0.04f * s, 0.07f * s, dark);
        float hx = x + dir * 0.36f * s, hy = y - 0.06f * s + bob;
        gfx.polygon(c, dark, OUT, 0.012f * s, hx - dir * 0.06f * s, hy - 0.1f * s, hx - dir * 0.02f * s, hy - 0.22f * s, hx + dir * 0.05f * s, hy - 0.1f * s);
        gfx.circle(c, hx, hy, 0.17f * s, OUT);
        gfx.circle(c, hx, hy, 0.15f * s, pink);
        gfx.ellipse(c, hx + dir * 0.1f * s, hy + 0.04f * s, 0.085f * s, 0.065f * s, dark);
        gfx.circle(c, hx + dir * 0.09f * s, hy + 0.04f * s, 0.012f * s, OUT);
        gfx.circle(c, hx + dir * 0.13f * s, hy + 0.04f * s, 0.012f * s, OUT);
        gfx.circle(c, hx + dir * 0.02f * s, hy - 0.05f * s, 0.022f * s, OUT);
    }

    private void sheep(Canvas c, float x, float y, float s, float dir, float phase) {
        final int wool = 0xFFFFFFFF, face = 0xFF4A3A33;
        float bob = (float) Math.sin(phase * 1.6f) * 0.012f * s;
        for (float lx : new float[]{-0.18f, -0.06f, 0.1f, 0.2f}) {
            float px = x + dir * lx * s;
            gfx.rrect(c, px - 0.035f * s, y + 0.14f * s, px + 0.035f * s, y + 0.32f * s, 0.012f * s, face);
        }
        // fluffy body: a cluster of circles
        float[][] puffs = {{0, 0, 0.26f}, {-0.2f, 0.02f, 0.2f}, {0.2f, 0.02f, 0.2f}, {-0.08f, -0.14f, 0.18f}, {0.1f, -0.14f, 0.18f}, {0, 0.12f, 0.2f}};
        for (float[] pf : puffs) gfx.circle(c, x + dir * pf[0] * s, y + pf[1] * s, (pf[2] + 0.025f) * s, OUT);
        for (float[] pf : puffs) gfx.circle(c, x + dir * pf[0] * s, y + pf[1] * s, pf[2] * s, wool);
        float hx = x + dir * 0.32f * s, hy = y - 0.04f * s + bob;
        gfx.ellipse(c, hx, hy, 0.13f * s, 0.15f * s, OUT);
        gfx.ellipse(c, hx, hy, 0.11f * s, 0.13f * s, face);
        gfx.circle(c, hx + dir * 0.03f * s, hy - 0.04f * s, 0.02f * s, Color.WHITE);
        gfx.ellipse(c, hx - dir * 0.09f * s, hy - 0.06f * s, 0.06f * s, 0.03f * s, face);
    }

    /** The herd, wandering in front of its houses. Drawn at most: 12 hens and chicks, 6 cows, 6 pigs, 9 sheep, 9 bees. */
    private void drawAnimals(Canvas c, RectF s) {
        Farm.Herd hd = v.progress.herd;
        float ws = s.width(), hs = s.height(), t = v.time;
        for (int k = 0; k < Math.min(hd.count[Farm.COW], 6); k++) {
            float ph = t * 0.45f + k * 1.9f;
            float x = 0.24f + 0.135f * (k % 3) + 0.015f * (float) Math.sin(ph), y = 0.74f + 0.085f * (k / 3);
            cow(c, s.left + x * ws, s.top + y * hs, 0.15f * ws, Math.cos(ph) >= 0 ? 1 : -1, t + k);
        }
        for (int k = 0; k < Math.min(hd.count[Farm.PIG], 6); k++) {
            float ph = t * 0.6f + k * 2.1f;
            float x = 0.05f + 0.065f * (k % 3) + 0.008f * (float) Math.sin(ph), y = 0.68f + 0.075f * (k / 3);
            pig(c, s.left + x * ws, s.top + y * hs, 0.075f * ws, Math.cos(ph) >= 0 ? 1 : -1, t + k);
        }
        for (int k = 0; k < Math.min(hd.count[Farm.SHEEP], 9); k++) {
            float ph = t * 0.5f + k * 1.7f;
            float x = 0.59f + 0.05f * (k % 3) + 0.006f * (float) Math.sin(ph), y = 0.82f + 0.045f * (k / 3);
            sheep(c, s.left + x * ws, s.top + y * hs, 0.06f * ws, Math.cos(ph) >= 0 ? 1 : -1, t + k);
        }
        int total = Math.min(12, hd.count[Farm.CHICKEN] + hd.chickCount());
        for (int k = 0; k < total; k++) {
            float ph = t * 1.3f + k * 2.3f;
            float x = 0.67f + 0.075f * (k % 4) + 0.015f * (float) Math.sin(ph * 0.5f), y = 0.65f + 0.06f * (k / 4);
            float dir = Math.cos(ph * 0.5f) >= 0 ? 1 : -1;
            if (k < hd.count[Farm.CHICKEN]) chicken(c, s.left + x * ws, s.top + y * hs, 0.075f * ws, dir, ph);
            else chick(c, s.left + x * ws, s.top + y * hs + 0.01f * hs, 0.05f * ws, dir, ph);
        }
        float[] hp = HOUSE_POS[Farm.BEE];
        for (int k = 0; k < Math.min(hd.count[Farm.BEE], 9); k++) {
            float a = t * (1.6f + 0.1f * (k % 3)) + k * 0.7f, rad = (0.07f + 0.012f * (k % 4)) * ws;
            r.drawIcon(c, 11, s.left + hp[0] * ws + (float) Math.cos(a) * rad, s.top + hp[1] * hs - 0.02f * hs + (float) Math.sin(a * 1.4f) * rad * 0.6f, 0.04f * ws);
        }
    }

    private void fence(Canvas c, float x0, float x1, float yBase, float h) {
        float pw = h * 0.42f, step = h * 0.78f;
        gfx.lineSeg(c, x0, yBase - h * 0.3f, x1, yBase - h * 0.3f, h * 0.12f, 0xFFB5793B);
        gfx.lineSeg(c, x0, yBase - h * 0.65f, x1, yBase - h * 0.65f, h * 0.12f, 0xFFB5793B);
        for (float x = x0 + pw / 2; x < x1; x += step) {
            gfx.polygon(c, CREAM, OUT, h * 0.06f, x - pw / 2, yBase, x + pw / 2, yBase, x + pw / 2, yBase - h * 0.85f, x, yBase - h, x - pw / 2, yBase - h * 0.85f);
        }
    }

    private void flowers(Canvas c, float cx, float cy, float s) {
        float[][] f = {{-0.34f, 0.0f}, {-0.16f, 0.1f}, {0.02f, -0.06f}, {0.2f, 0.08f}, {0.36f, -0.02f}};
        for (int k = 0; k < f.length; k++) {
            float x = cx + f[k][0] * s, y = cy + f[k][1] * s;
            gfx.lineSeg(c, x, y + 0.25f * s, x, y, 0.025f * s, 0xFF3E9E3E);
            gfx.circle(c, x, y, 0.1f * s, PETALS[k % PETALS.length]);
            gfx.circleLine(c, x, y, 0.1f * s, 0.02f * s, Gfx.darken(PETALS[k % PETALS.length], 0.6f));
            gfx.circle(c, x, y, 0.04f * s, 0xFFFFF3B0);
        }
    }

    private void pond(Canvas c, float cx, float cy, float s) {
        gfx.ellipse(c, cx, cy, 0.5f * s, 0.23f * s, 0xFF3A8AC8);
        gfx.ellipse(c, cx, cy, 0.46f * s, 0.19f * s, 0xFF63C2F7);
        gfx.ellipse(c, cx - 0.1f * s, cy - 0.08f * s, 0.2f * s, 0.04f * s, 0x66FFFFFF);
        gfx.ellipse(c, cx - 0.24f * s, cy + 0.05f * s, 0.09f * s, 0.04f * s, 0xFF4CAF50);
        gfx.circle(c, cx - 0.22f * s, cy + 0.03f * s, 0.025f * s, 0xFFFF8AAE);
        float bob = (float) Math.sin(v.time * 2) * 0.012f * s, dx = cx + 0.14f * s, dy = cy - 0.01f * s + bob;
        gfx.ellipse(c, dx, dy, 0.1f * s, 0.065f * s, 0xFFFFD43B);
        gfx.circle(c, dx + 0.08f * s, dy - 0.07f * s, 0.05f * s, 0xFFFFD43B);
        gfx.polygon(c, 0xFFFF9B26, 0, 0, dx + 0.12f * s, dy - 0.08f * s, dx + 0.19f * s, dy - 0.06f * s, dx + 0.12f * s, dy - 0.04f * s);
        gfx.circle(c, dx + 0.09f * s, dy - 0.085f * s, 0.01f * s, OUT);
    }

    private void oak(Canvas c, float cx, float cy, float s) {
        gfx.rrect(c, cx - 0.07f * s, cy + 0.1f * s, cx + 0.07f * s, cy + 0.5f * s, 0.02f * s, 0xFF8B5A2B);
        gfx.rrectLine(c, cx - 0.07f * s, cy + 0.1f * s, cx + 0.07f * s, cy + 0.5f * s, 0.02f * s, 0.02f * s, OUT);
        gfx.circle(c, cx - 0.22f * s, cy - 0.0f * s, 0.24f * s, 0xFF378F37);
        gfx.circle(c, cx + 0.22f * s, cy - 0.0f * s, 0.24f * s, 0xFF378F37);
        gfx.circle(c, cx, cy - 0.2f * s, 0.32f * s, 0xFF3E9E3E);
        gfx.circle(c, cx - 0.1f * s, cy - 0.3f * s, 0.11f * s, 0xFF6BC46B);
    }

    private void windmill(Canvas c, float cx, float cy, float s) {
        gfx.polygon(c, CREAM, OUT, 0.025f * s, cx - 0.16f * s, cy + 0.45f * s, cx + 0.16f * s, cy + 0.45f * s, cx + 0.09f * s, cy - 0.18f * s, cx - 0.09f * s, cy - 0.18f * s);
        gfx.polygon(c, 0xFFD9453A, OUT, 0.025f * s, cx - 0.12f * s, cy - 0.18f * s, cx, cy - 0.34f * s, cx + 0.12f * s, cy - 0.18f * s);
        gfx.rrect(c, cx - 0.045f * s, cy + 0.3f * s, cx + 0.045f * s, cy + 0.45f * s, 0.02f * s, 0xFF8B5A2B);
        float hx = cx, hy = cy - 0.2f * s, spin = v.time * 45f;
        for (int k = 0; k < 4; k++) {
            c.save();
            c.rotate(spin + k * 90, hx, hy);
            gfx.rrect(c, hx - 0.012f * s, hy - 0.4f * s, hx + 0.012f * s, hy, 0.01f * s, 0xFF8B5A2B);
            gfx.rrect(c, hx + 0.012f * s, hy - 0.38f * s, hx + 0.11f * s, hy - 0.1f * s, 0.01f * s, 0xFFFFE9A8);
            gfx.rrectLine(c, hx + 0.012f * s, hy - 0.38f * s, hx + 0.11f * s, hy - 0.1f * s, 0.01f * s, 0.012f * s, OUT);
            c.restore();
        }
        gfx.circle(c, hx, hy, 0.035f * s, 0xFF8B5A2B);
    }

    private void balloon(Canvas c, float cx, float cy, float s) {
        cy += (float) Math.sin(v.time * 1.4f) * 0.03f * s;
        gfx.lineSeg(c, cx - 0.18f * s, cy + 0.2f * s, cx - 0.08f * s, cy + 0.38f * s, 0.015f * s, OUT);
        gfx.lineSeg(c, cx + 0.18f * s, cy + 0.2f * s, cx + 0.08f * s, cy + 0.38f * s, 0.015f * s, OUT);
        gfx.rrect(c, cx - 0.1f * s, cy + 0.38f * s, cx + 0.1f * s, cy + 0.5f * s, 0.02f * s, 0xFFB5793B);
        gfx.rrectLine(c, cx - 0.1f * s, cy + 0.38f * s, cx + 0.1f * s, cy + 0.5f * s, 0.02f * s, 0.02f * s, OUT);
        gfx.circle(c, cx, cy - 0.1f * s, 0.3f * s, 0xFFE5604A);
        gfx.ellipse(c, cx, cy - 0.1f * s, 0.12f * s, 0.3f * s, 0xFFFFD43B);
        gfx.ellipse(c, cx, cy - 0.1f * s, 0.04f * s, 0.3f * s, CREAM);
        gfx.circleLine(c, cx, cy - 0.1f * s, 0.3f * s, 0.025f * s, OUT);
    }
}
