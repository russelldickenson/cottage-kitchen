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

/** Draws the farm screen (scene, tabs, upgrade cards, level result) and the booster store. State is in FarmHub. */
final class FarmRenderer {
    private static final int OUT = 0xFF3E2008, INK = 0xFF6B3F17, PRICE_BAD = 0xFFFF9A8A;
    static final int[] BOOSTER_COLOR = {ORANGE, BLUE, GREEN};

    // crop art used as the icon of each output upgrade: egg, bee, apple, pumpkin
    private static final int[] OUTPUT_CROP = {6, 11, 4, 5};

    // Positions inside the scene: x and y as fractions of its width and height, and the item size as a fraction of its width.
    private static final float[] BARN_POS = {0.37f, 0.52f, 0.34f};
    private static final float[][] OUTPUT_POS = {
            {0.79f, 0.55f, 0.28f},   // coop
            {0.585f, 0.34f, 0.15f},   // beehive
            {0.12f, 0.74f, 0.20f},   // orchard
            {0.50f, 0.80f, 0.22f},   // pumpkin patch
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

    FarmRenderer(GameView v, Renderer r, Gfx gfx) {
        this.v = v;
        this.r = r;
        this.gfx = gfx;
    }

    // ---------------------------------------------------------------- shared

    private void header(Canvas c, boolean withShop) {
        float m = 3 * v.u, hh = 13 * v.u, top = v.topInset + m;
        float right = withShop ? v.hub.shopRect.left - 2.5f * v.u : v.W - m;
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
        header(c, true);
        if (h.result) drawBanner(c);
        else gfx.textC(c, "My Farm", v.W / 2, h.titleY, 7.5f * v.u, Color.WHITE, 0xFF1F5F10, Paint.Align.CENTER);
        drawScene(c);
        drawTabs(c);
        for (int i = 0; i < h.cardCount(); i++) drawCard(c, i);
        if (h.tab == FarmHub.TAB_UPGRADES) {
            RectF row = h.card[4];
            float u = v.u;
            gfx.textC(c, "Every upgrade level adds +" + Farm.BONUS_PCT_PER_LEVEL + "% to what you", v.W / 2, row.top + 5 * u, 3.9f * u, Color.WHITE, 0xFF1F5F10, Paint.Align.CENTER);
            gfx.textC(c, "earn after every level.", v.W / 2, row.top + 10.5f * u, 3.9f * u, Color.WHITE, 0xFF1F5F10, Paint.Align.CENTER);
        }
        drawShopButton(c);
        effects(c);
        r.drawButtons(c);
    }

    private void drawShopButton(Canvas c) {
        RectF b = v.hub.shopRect;
        boolean pressed = v.hub.shopBtn != null && v.hub.shopBtn.pressed;
        float w = b.width(), depth = w * 0.09f, off = pressed ? depth * 0.8f : 0f;
        float cx = b.centerX(), cy = b.centerY();
        gfx.circle(c, cx, cy + depth, w / 2, Gfx.darken(BLUE, 0.62f));
        gfx.circle(c, cx, cy + off, w / 2, BLUE);
        gfx.circle(c, cx, cy + off, w * 0.43f, Gfx.lighten(BLUE, 0.18f));
        gfx.textC(c, "SHOP", cx, cy + off, w * 0.27f, Color.WHITE, Gfx.darken(BLUE, 0.45f), Paint.Align.CENTER);
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
        String detail = "Play " + v.playCoins + "  +  Harvest " + v.saleCoins + (v.farmPct > 0 ? "  +  Farm " + v.farmPct + "%" : "");
        gfx.textC(c, detail, b.centerX(), b.top + h - 2.7f * u, 3.3f * u, 0xFF8B5A2B, 0, Paint.Align.CENTER);
    }

    private void drawTabs(Canvas c) {
        FarmHub h = v.hub;
        float u = v.u;
        String[] names = {"UPGRADES", "DECOR"};
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

        float ix = cd.left + 10 * u, iy = cd.top + hgt * 0.5f + off, box = Math.min(15 * u, hgt * 0.68f);
        drawIcon(c, decor, i, ix, iy, box);

        float tx = cd.left + 19.5f * u;
        String name = decor ? Farm.DECOR_NAME[i] : Farm.OUTPUT_NAME[i];
        gfx.textC(c, name, tx, cd.top + hgt * 0.24f + off, 4.4f * u, INK, 0, Paint.Align.LEFT);
        float py = cd.top + hgt * 0.73f + off;
        if (decor) {
            gfx.textC(c, "Decoration", tx, cd.top + hgt * 0.47f + off, 3.2f * u, 0xFF8B5A2B, 0, Paint.Align.LEFT);
            if (p.decor[i]) {
                gfx.check(c, tx + 2.2f * u, py, 2.4f * u);
                gfx.textC(c, "Owned", tx + 6 * u, py, 4f * u, 0xFF3E9E22, 0, Paint.Align.LEFT);
            } else {
                priceChip(c, tx + 10 * u, py, Farm.DECOR_COST[i], p.totalCoins >= Farm.DECOR_COST[i], 4.4f * u);
            }
        } else {
            int lvl = p.output[i];
            for (int k = 0; k < Farm.MAX_OUTPUT_LEVEL; k++) {
                float px = tx + 1.6f * u + k * 4.2f * u, py2 = cd.top + hgt * 0.47f + off;
                gfx.circle(c, px, py2, 1.5f * u, k < lvl ? 0xFFFFC928 : 0x33000000);
                gfx.circleLine(c, px, py2, 1.5f * u, 0.4f * u, k < lvl ? 0xFFC97700 : 0xFF8B5A2B);
            }
            int cost = p.outputCost(i);
            if (cost < 0) gfx.textC(c, "MAX", tx + 4 * u, py, 4.6f * u, 0xFF3E9E22, 0xFFFFFFFF, Paint.Align.LEFT);
            else priceChip(c, tx + 10 * u, py, cost, p.totalCoins >= cost, 4.4f * u);
        }
    }

    /** The scene art drawn small, centred in a box of side box. */
    private void drawIcon(Canvas c, boolean decor, int i, float cx, float cy, float box) {
        if (!decor) {
            switch (i) {
                case Farm.COOP:
                    coop(c, cx, cy - 0.06f * box, box * 0.95f, 1);
                    break;
                case Farm.HIVE:
                    hive(c, cx, cy, box * 1.05f, 1);
                    break;
                case Farm.ORCHARD:
                    orchard(c, cx, cy, box * 0.85f, 1);
                    break;
                default:
                    patch(c, cx, cy, box * 0.95f, 1);
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
        float[] p = decor ? DECOR_POS[i] : OUTPUT_POS[i];
        return new float[]{s.left + p[0] * s.width(), s.top + p[1] * s.height()};
    }

    // ---------------------------------------------------------------- the scene

    private void drawScene(Canvas c) {
        RectF s = v.hub.scene;
        Progress p = v.progress;
        float u = v.u, ws = s.width(), hs = s.height();
        c.save();
        gfx.clipRRect(c, s, 4 * u);
        gfx.rrect(c, s.left, s.top, s.right, s.bottom, 0, 0xFF9DDCF9);
        gfx.circle(c, s.left + ws * 0.90f, s.top + hs * 0.10f, ws * 0.05f, 0xFFFFE066);
        gfx.ellipse(c, s.left + ws * 0.25f, s.top + hs * 0.10f, ws * 0.10f, hs * 0.03f, 0xCCFFFFFF);
        gfx.ellipse(c, s.left + ws * 0.32f, s.top + hs * 0.085f, ws * 0.07f, hs * 0.03f, 0xCCFFFFFF);
        gfx.ellipse(c, s.left + ws * 0.20f, s.top + hs * 0.38f, ws * 0.46f, hs * 0.13f, 0xFFB5E58A);
        gfx.ellipse(c, s.left + ws * 0.88f, s.top + hs * 0.37f, ws * 0.40f, hs * 0.12f, 0xFFA6DC72);
        gfx.rrect(c, s.left, s.top + hs * 0.36f, s.right, s.bottom, 0, 0xFF8BD450);
        gfx.rrect(c, s.left, s.top + hs * 0.88f, s.right, s.bottom, 0, 0xFF74C043);

        // back to front
        if (p.decor[Farm.WINDMILL]) place(c, s, DECOR_POS[Farm.WINDMILL], 0, Farm.WINDMILL, true, 0);
        if (p.decor[Farm.BALLOON]) place(c, s, DECOR_POS[Farm.BALLOON], 0, Farm.BALLOON, true, 0);
        if (p.decor[Farm.OAK]) place(c, s, DECOR_POS[Farm.OAK], 0, Farm.OAK, true, 0);
        barn(c, s.left + BARN_POS[0] * ws, s.top + BARN_POS[1] * hs, BARN_POS[2] * ws);
        for (int k : new int[]{Farm.HIVE, Farm.COOP, Farm.ORCHARD, Farm.PATCH}) {
            int lvl = p.output[k];
            if (lvl == 0) c.saveLayerAlpha(s.left, s.top, s.right, s.bottom, 85);
            place(c, s, OUTPUT_POS[k], lvl, k, false, lvl);
            if (lvl == 0) c.restore();
        }
        if (p.decor[Farm.FLOWERS]) place(c, s, DECOR_POS[Farm.FLOWERS], 0, Farm.FLOWERS, true, 0);
        if (p.decor[Farm.POND]) place(c, s, DECOR_POS[Farm.POND], 0, Farm.POND, true, 0);
        if (p.decor[Farm.FENCE]) place(c, s, DECOR_POS[Farm.FENCE], 0, Farm.FENCE, true, 0);
        c.restore();
        gfx.rrectLine(c, s.left, s.top, s.right, s.bottom, 4 * u, 1f * u, WOOD);

        int pct = p.bonusPct();
        String label = pct > 0 ? "+" + pct + "% coins" : "Buy upgrades to earn more";
        float tw = gfx.width(label, 3.6f * u), pl = s.left + 2.5f * u, pt = s.top + 2.5f * u;
        gfx.rrect(c, pl, pt, pl + tw + 5 * u, pt + 6 * u, 3 * u, 0xCC6B3F17);
        gfx.textC(c, label, pl + 2.5f * u + tw / 2, pt + 3 * u, 3.6f * u, pct > 0 ? 0xFFFFE066 : Color.WHITE, 0, Paint.Align.CENTER);
    }

    private void place(Canvas c, RectF s, float[] pos, int lvl, int item, boolean decor, int extra) {
        float ws = s.width(), cx = s.left + pos[0] * ws, cy = s.top + pos[1] * s.height(), sz = pos[2] * ws;
        if (decor) {
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
        } else {
            switch (item) {
                case Farm.COOP:
                    coop(c, cx, cy, sz, extra);
                    break;
                case Farm.HIVE:
                    hive(c, cx, cy, sz, extra);
                    break;
                case Farm.ORCHARD:
                    orchard(c, cx, cy, sz, extra);
                    break;
                default:
                    patch(c, cx, cy, sz, extra);
            }
        }
    }

    // ---- items, each centred on (cx, cy) and roughly s wide and tall

    private void barn(Canvas c, float cx, float cy, float s) {
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
    }

    private void coop(Canvas c, float cx, float cy, float s, int lvl) {
        gfx.rrect(c, cx - 0.38f * s, cy - 0.04f * s, cx + 0.38f * s, cy + 0.32f * s, 0.03f * s, 0xFFF0C987);
        gfx.rrectLine(c, cx - 0.38f * s, cy - 0.04f * s, cx + 0.38f * s, cy + 0.32f * s, 0.03f * s, 0.025f * s, OUT);
        gfx.polygon(c, 0xFFD9453A, OUT, 0.03f * s, cx - 0.46f * s, cy - 0.02f * s, cx, cy - 0.42f * s, cx + 0.46f * s, cy - 0.02f * s);
        gfx.circle(c, cx, cy - 0.17f * s, 0.07f * s, CREAM);
        gfx.circleLine(c, cx, cy - 0.17f * s, 0.07f * s, 0.02f * s, OUT);
        gfx.rrect(c, cx - 0.1f * s, cy + 0.08f * s, cx + 0.1f * s, cy + 0.32f * s, 0.03f * s, 0xFF8B5A2B);
        gfx.rrectLine(c, cx - 0.1f * s, cy + 0.08f * s, cx + 0.1f * s, cy + 0.32f * s, 0.03f * s, 0.02f * s, OUT);
        int n = Math.max(1, Math.min(lvl, 3));
        for (int k = 0; k < n; k++) {
            boolean gold = lvl >= 3 && k == n - 1;
            r.drawIcon(c, gold ? Art.GOLDEN_EGG : 6, cx + (k - (n - 1) / 2f) * 0.3f * s, cy + 0.44f * s, 0.3f * s);
        }
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
        int n = Math.max(1, Math.min(lvl, 3));
        for (int k = 0; k < n; k++) {
            float a = v.time * 2 + k * 2.1f;
            r.drawIcon(c, 11, cx + (float) Math.cos(a) * 0.42f * s, cy - 0.12f * s + (float) Math.sin(a * 1.5f) * 0.1f * s, 0.26f * s);
        }
    }

    private void tree(Canvas c, float x, float y, float ts) {
        gfx.rrect(c, x - 0.06f * ts, y + 0.02f * ts, x + 0.06f * ts, y + 0.42f * ts, 0.02f * ts, 0xFF8B5A2B);
        gfx.rrectLine(c, x - 0.06f * ts, y + 0.02f * ts, x + 0.06f * ts, y + 0.42f * ts, 0.02f * ts, 0.02f * ts, OUT);
        gfx.circle(c, x - 0.22f * ts, y + 0.04f * ts, 0.2f * ts, 0xFF3E9E3E);
        gfx.circle(c, x + 0.22f * ts, y + 0.04f * ts, 0.2f * ts, 0xFF3E9E3E);
        gfx.circle(c, x, y - 0.14f * ts, 0.3f * ts, 0xFF4CAF50);
        gfx.circle(c, x - 0.08f * ts, y - 0.24f * ts, 0.1f * ts, 0xFF6BC46B);
        float[][] apples = {{-0.14f, -0.12f}, {0.12f, -0.2f}, {0.2f, 0.02f}, {-0.24f, 0.06f}};
        for (float[] a : apples) gfx.circle(c, x + a[0] * ts, y + a[1] * ts, 0.05f * ts, 0xFFE5304A);
    }

    private void orchard(Canvas c, float cx, float cy, float s, int lvl) {
        int n = Math.max(1, Math.min(lvl, 3));
        float ts = n == 1 ? s : s * 0.78f;
        for (int k = 0; k < n; k++) tree(c, cx + (k - (n - 1) / 2f) * 0.62f * s, cy - 0.04f * s + (k % 2) * 0.04f * s, ts);
    }

    private void patch(Canvas c, float cx, float cy, float s, int lvl) {
        gfx.rrect(c, cx - 0.48f * s, cy + 0.0f * s, cx + 0.48f * s, cy + 0.3f * s, 0.07f * s, 0xFF9A6A3A);
        gfx.rrectLine(c, cx - 0.48f * s, cy + 0.0f * s, cx + 0.48f * s, cy + 0.3f * s, 0.07f * s, 0.025f * s, OUT);
        for (int k = 0; k < 2; k++) gfx.lineSeg(c, cx - 0.4f * s, cy + (0.1f + k * 0.1f) * s, cx + 0.4f * s, cy + (0.1f + k * 0.1f) * s, 0.02f * s, 0xFF7A4E26);
        int n = Math.min(1 + lvl, 4);
        for (int k = 0; k < n; k++) r.drawIcon(c, 5, cx + (k - (n - 1) / 2f) * 0.27f * s, cy - 0.07f * s + (k % 2) * 0.05f * s, 0.36f * s);
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

    // ---------------------------------------------------------------- booster store

    void drawShop(Canvas c) {
        header(c, false);
        FarmHub h = v.hub;
        Progress p = v.progress;
        float u = v.u;
        gfx.textC(c, "General Store", v.W / 2, h.shopTitleY, 7.5f * u, Color.WHITE, 0xFF1F5F10, Paint.Align.CENTER);
        for (int k = 0; k < Farm.BOOSTERS; k++) {
            RectF rr = h.shopRow[k];
            gfx.rrect(c, rr.left, rr.top + 1f * u, rr.right, rr.bottom + 1f * u, 4 * u, Gfx.darken(WOOD, 0.6f));
            gfx.rrect(c, rr.left, rr.top, rr.right, rr.bottom, 4 * u, WOOD);
            gfx.rrect(c, rr.left + 0.7f * u, rr.top + 0.7f * u, rr.right - 0.7f * u, rr.bottom - 0.7f * u, 3.4f * u, CREAM);

            float ix = rr.left + 11 * u, iy = rr.centerY(), ir = 8 * u;
            int col = BOOSTER_COLOR[k];
            gfx.circle(c, ix, iy + 1f * u, ir, Gfx.darken(col, 0.62f));
            gfx.circle(c, ix, iy, ir, col);
            gfx.circle(c, ix, iy, ir * 0.86f, Gfx.lighten(col, 0.18f));
            gfx.boosterIcon(c, k, ix, iy, ir * 0.62f);

            gfx.textC(c, Farm.BOOSTER_NAME[k], rr.left + 22 * u, iy - 6.5f * u, 5.4f * u, INK, 0, Paint.Align.LEFT);
            gfx.textC(c, Farm.BOOSTER_INFO[k], rr.left + 22 * u, iy, 3.5f * u, 0xFF8B5A2B, 0, Paint.Align.LEFT);
            gfx.textC(c, "Owned: " + p.boosters[k], rr.left + 22 * u, iy + 6.5f * u, 4.2f * u, 0xFF3E9E22, 0, Paint.Align.LEFT);

            RectF chip = h.buyChip(k);
            boolean ok = p.totalCoins >= Farm.BOOSTER_PRICE[k];
            boolean pressed = h.shopBuy[k] != null && h.shopBuy[k].pressed;
            float off = pressed ? 0.6f * u : 0f, ch = chip.height();
            int base = ok ? GREEN : 0xFFA8A8A8;
            gfx.rrect(c, chip.left, chip.top + 0.9f * u, chip.right, chip.bottom + 0.9f * u, ch * 0.4f, Gfx.darken(base, 0.62f));
            gfx.rrect(c, chip.left, chip.top + off, chip.right, chip.bottom + off, ch * 0.4f, base);
            gfx.textC(c, "BUY", chip.centerX(), chip.top + ch * 0.27f + off, 3.8f * u, Color.WHITE, Gfx.darken(base, 0.45f), Paint.Align.CENTER);
            priceChip(c, chip.centerX(), chip.top + ch * 0.68f + off, Farm.BOOSTER_PRICE[k], ok, 3.9f * u);
        }
        float ty = h.shopRow[2].bottom + 8 * u;
        gfx.textC(c, "Boosters also sit under the board", v.W / 2, ty, 4f * u, Color.WHITE, 0xFF1F5F10, Paint.Align.CENTER);
        gfx.textC(c, "during a level. Tap one to use it!", v.W / 2, ty + 5.5f * u, 4f * u, Color.WHITE, 0xFF1F5F10, Paint.Align.CENTER);
        effects(c);
        r.drawButtons(c);
    }
}
