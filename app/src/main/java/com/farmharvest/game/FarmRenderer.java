package com.farmharvest.game;

import static com.farmharvest.game.Gfx.BLUE;
import static com.farmharvest.game.Gfx.CREAM;
import static com.farmharvest.game.Gfx.GOLD;
import static com.farmharvest.game.Gfx.GREEN;
import static com.farmharvest.game.Gfx.ORANGE;
import static com.farmharvest.game.Gfx.WOOD;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;

/** Draws the farm hub, the market and the general store. State and buttons live in FarmHub. */
final class FarmRenderer {
    private static final int OUT = 0xFF3E2008, INK = 0xFF6B3F17, PRICE_BAD = 0xFFFF9A8A;
    static final int[] BOOSTER_COLOR = {ORANGE, BLUE, GREEN};

    private static final String[] PLATE = {"Barn", "Well", "Scarecrow Plot", "Market", "General Store", "Orders"};
    private static final int[] SKIN = {0xFFF2C9A0, 0xFFF5D7B7, 0xFFC68642, 0xFFF2C9A0, 0xFFE0A878, 0xFFF5D7B7, 0xFFE0A878, 0xFFC68642};
    private static final int[] CLOTHES = {0xFF5BC236, 0xFFE5604A, 0xFFFFD43B, 0xFFF08A24, 0xFFE91E63, 0xFF3FA9F5, 0xFF9B59B6, 0xFF2C3E50};

    private final GameView v;
    private final Renderer r;
    private final Gfx gfx;

    FarmRenderer(GameView v, Renderer r, Gfx gfx) {
        this.v = v;
        this.r = r;
        this.gfx = gfx;
    }

    private void header(Canvas c, String title) {
        float m = 3 * v.u, hh = 13 * v.u, top = v.topInset + m;
        r.drawCoinPill(c, m + hh + m * 0.8f + hh * 0.4f, top, v.W - m, top + hh, Math.round(v.dispCoins));
        gfx.textC(c, title, v.W / 2, v.hub.titleY, 7.5f * v.u, Color.WHITE, 0xFF1F5F10, Paint.Align.CENTER);
    }

    /** Sparkles and floating text, shared by the screens that have no board. */
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

    // ---------------------------------------------------------------- farm hub

    void drawFarm(Canvas c) {
        header(c, "My Farm");
        FarmHub h = v.hub;
        for (int i = 0; i < FarmHub.BUILDINGS; i++) drawBuilding(c, i);
        effects(c);
        if (h.card >= 0) drawCard(c);
        r.drawButtons(c);
        if (h.card >= 0) effects(c);
    }

    private void art(Canvas c, int id, float cx, float cy, float s) {
        switch (id) {
            case FarmHub.BARN:
                barn(c, cx, cy, s);
                break;
            case FarmHub.WELL:
                well(c, cx, cy, s);
                break;
            case FarmHub.PLOT:
                plot(c, cx, cy, s);
                break;
            case FarmHub.ORDERS:
                notice(c, cx, cy, s);
                break;
            case FarmHub.MARKET:
                market(c, cx, cy, s);
                break;
            default:
                store(c, cx, cy, s);
        }
    }

    private void drawBuilding(Canvas c, int id) {
        RectF t = v.hub.tile[id];
        float u = v.u, cx = t.centerX();
        boolean pressed = v.hub.bld[id] != null && v.hub.bld[id].pressed;
        float off = pressed ? 0.8f * u : 0f;
        float s = Math.min(t.width(), t.height() * 0.52f);
        float artCy = t.top + s * 0.5f + off;
        gfx.ellipse(c, cx, artCy + s * 0.44f, s * 0.52f, s * 0.08f, 0x30000000);
        art(c, id, cx, artCy, s);
        if (id == FarmHub.ORDERS) {
            int n = v.progress.fillableOrders() + (v.progress.giftReady(Farm.today()) ? 1 : 0);
            if (n > 0) {
                float bx = cx + s * 0.42f, by = artCy - s * 0.4f, br = s * 0.13f;
                gfx.circle(c, bx, by, br, 0xFFE5304A);
                gfx.circleLine(c, bx, by, br, s * 0.025f, Color.WHITE);
                gfx.textC(c, String.valueOf(n), bx, by, br * 1.35f, Color.WHITE, 0, Paint.Align.CENTER);
            }
        }

        float plateTop = t.top + s * 1.04f, plateH = 7 * u, pw = t.width() * 0.92f;
        gfx.rrect(c, cx - pw / 2, plateTop + 0.7f * u, cx + pw / 2, plateTop + plateH + 0.7f * u, 2 * u, Gfx.darken(WOOD, 0.6f));
        gfx.rrect(c, cx - pw / 2, plateTop, cx + pw / 2, plateTop + plateH, 2 * u, WOOD);
        gfx.textC(c, PLATE[id],
                cx, plateTop + plateH / 2, 4.6f * u, Color.WHITE, 0xFF3A1F08, Paint.Align.CENTER);

        float y1 = plateTop + plateH + 4.2f * u, y2 = y1 + 4.6f * u, y3 = y2 + 5.4f * u;
        Progress p = v.progress;
        if (id <= FarmHub.PLOT) {
            int lvl = p.building[id];
            String line = id == FarmHub.BARN ? p.barnTotal() + " / " + p.capacity() + " crops" : Farm.effect(id, lvl);
            gfx.textC(c, line, cx, y1, 3.7f * u, INK, 0, Paint.Align.CENTER);
            int max = Farm.maxLevel(id);
            float gap = 3.4f * u, x0 = cx - gap * (max - 1) / 2;
            for (int i = 0; i < max; i++) {
                boolean on = i < lvl;
                gfx.circle(c, x0 + i * gap, y2, 1.25f * u, on ? GOLD : 0x55FFFFFF);
                gfx.circleLine(c, x0 + i * gap, y2, 1.25f * u, 0.4f * u, on ? 0xFFC97700 : 0xFF8B5A2B);
            }
            int cost = Farm.nextCost(id, lvl);
            if (cost < 0) gfx.textC(c, "MAX", cx, y3, 4.4f * u, 0xFF4CBB2C, 0xFFFFFFFF, Paint.Align.CENTER);
            else priceChip(c, cx, y3, cost, p.totalCoins >= cost, 4.4f * u);
        } else if (id == FarmHub.MARKET) {
            gfx.textC(c, "Sell your crops", cx, y1, 3.7f * u, INK, 0, Paint.Align.CENTER);
            int worth = p.sellValueAll(Farm.today());
            if (worth > 0) priceChip(c, cx, y2 + 1.4f * u, worth, true, 4.4f * u);
            else gfx.textC(c, "Barn is empty", cx, y2 + 1.4f * u, 3.7f * u, 0xFF8B5A2B, 0, Paint.Align.CENTER);
        } else if (id == FarmHub.ORDERS) {
            int open = p.openOrders(), ready = p.fillableOrders();
            gfx.textC(c, open + (open == 1 ? " open order" : " open orders"), cx, y1, 3.7f * u, INK, 0, Paint.Align.CENTER);
            if (ready > 0) gfx.textC(c, ready + " ready to fill!", cx, y2 + 1.4f * u, 4.2f * u, 0xFF3E9E22, 0xFFFFFFFF, Paint.Align.CENTER);
            else if (p.giftReady(Farm.today())) gfx.textC(c, "Daily gift waiting!", cx, y2 + 1.4f * u, 4.2f * u, 0xFFD06A00, 0xFFFFFFFF, Paint.Align.CENTER);
            else gfx.textC(c, "Check back soon", cx, y2 + 1.4f * u, 3.7f * u, 0xFF8B5A2B, 0, Paint.Align.CENTER);
        } else {
            gfx.textC(c, "Boosters", cx, y1, 3.7f * u, INK, 0, Paint.Align.CENTER);
            int owned = p.boosters[0] + p.boosters[1] + p.boosters[2];
            gfx.textC(c, owned + " in stock", cx, y2 + 1.4f * u, 3.7f * u, 0xFF8B5A2B, 0, Paint.Align.CENTER);
        }
    }

    // ---- building art, each drawn inside a square of side s centred on (cx, cy)

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

    private void well(Canvas c, float cx, float cy, float s) {
        gfx.rrect(c, cx - 0.26f * s, cy + 0.02f * s, cx + 0.26f * s, cy + 0.42f * s, 0.08f * s, 0xFFBDBDBD);
        gfx.rrectLine(c, cx - 0.26f * s, cy + 0.02f * s, cx + 0.26f * s, cy + 0.42f * s, 0.08f * s, 0.025f * s, OUT);
        for (int i = 0; i < 2; i++) gfx.lineSeg(c, cx - 0.24f * s, cy + (0.16f + i * 0.13f) * s, cx + 0.24f * s, cy + (0.16f + i * 0.13f) * s, 0.015f * s, 0xFF8A8A8A);
        gfx.ellipse(c, cx, cy + 0.03f * s, 0.26f * s, 0.07f * s, 0xFF5A5A5A);
        gfx.ellipse(c, cx, cy + 0.04f * s, 0.22f * s, 0.05f * s, 0xFF3FA9F5);
        gfx.rrect(c, cx - 0.27f * s, cy - 0.3f * s, cx - 0.2f * s, cy + 0.05f * s, 0.01f * s, 0xFF8B5A2B);
        gfx.rrect(c, cx + 0.2f * s, cy - 0.3f * s, cx + 0.27f * s, cy + 0.05f * s, 0.01f * s, 0xFF8B5A2B);
        gfx.polygon(c, 0xFFC0622B, OUT, 0.03f * s, cx - 0.37f * s, cy - 0.27f * s, cx, cy - 0.46f * s, cx + 0.37f * s, cy - 0.27f * s);
        gfx.lineSeg(c, cx, cy - 0.28f * s, cx, cy - 0.1f * s, 0.02f * s, INK);
        gfx.rrect(c, cx - 0.05f * s, cy - 0.1f * s, cx + 0.05f * s, cy, 0.015f * s, 0xFFA9702F);
        gfx.rrectLine(c, cx - 0.05f * s, cy - 0.1f * s, cx + 0.05f * s, cy, 0.015f * s, 0.015f * s, OUT);
    }

    private void plot(Canvas c, float cx, float cy, float s) {
        gfx.rrect(c, cx - 0.44f * s, cy + 0.14f * s, cx + 0.44f * s, cy + 0.42f * s, 0.07f * s, 0xFF9A6A3A);
        gfx.rrectLine(c, cx - 0.44f * s, cy + 0.14f * s, cx + 0.44f * s, cy + 0.42f * s, 0.07f * s, 0.025f * s, OUT);
        for (int i = 0; i < 3; i++) gfx.lineSeg(c, cx - 0.36f * s, cy + (0.23f + i * 0.08f) * s, cx + 0.36f * s, cy + (0.23f + i * 0.08f) * s, 0.02f * s, 0xFF7A4E26);
        r.drawIcon(c, Art.SCARECROW, cx, cy - 0.05f * s, 0.74f * s);
    }

    private void market(Canvas c, float cx, float cy, float s) {
        float hw = 0.42f * s;
        gfx.rrect(c, cx - hw, cy - 0.26f * s, cx - hw + 0.05f * s, cy + 0.42f * s, 0.01f * s, 0xFF8B5A2B);
        gfx.rrect(c, cx + hw - 0.05f * s, cy - 0.26f * s, cx + hw, cy + 0.42f * s, 0.01f * s, 0xFF8B5A2B);
        r.drawIcon(c, 0, cx - 0.25f * s, cy + 0.1f * s, 0.3f * s);
        r.drawIcon(c, 5, cx, cy + 0.1f * s, 0.3f * s);
        r.drawIcon(c, 4, cx + 0.25f * s, cy + 0.1f * s, 0.3f * s);
        gfx.rrect(c, cx - hw, cy + 0.2f * s, cx + hw, cy + 0.42f * s, 0.03f * s, 0xFFB5793B);
        gfx.rrectLine(c, cx - hw, cy + 0.2f * s, cx + hw, cy + 0.42f * s, 0.03f * s, 0.025f * s, OUT);
        int n = 6;
        float aw = 2 * hw + 0.1f * s, x0 = cx - aw / 2, sw = aw / n, top = cy - 0.36f * s, bot = cy - 0.14f * s;
        for (int i = 0; i < n; i++) {
            int col = i % 2 == 0 ? 0xFFD9453A : 0xFFFFF4D2;
            gfx.polygon(c, col, OUT, 0.012f * s, x0 + i * sw, top, x0 + (i + 1) * sw, top, x0 + (i + 1) * sw, bot, x0 + i * sw, bot);
            gfx.circle(c, x0 + (i + 0.5f) * sw, bot, sw / 2, col);
            gfx.circleLine(c, x0 + (i + 0.5f) * sw, bot, sw / 2, 0.012f * s, OUT);
        }
    }

    private void store(Canvas c, float cx, float cy, float s) {
        gfx.rrect(c, cx - 0.38f * s, cy - 0.12f * s, cx + 0.38f * s, cy + 0.42f * s, 0.03f * s, 0xFFF0C987);
        gfx.rrectLine(c, cx - 0.38f * s, cy - 0.12f * s, cx + 0.38f * s, cy + 0.42f * s, 0.03f * s, 0.025f * s, OUT);
        gfx.polygon(c, 0xFF3FA9F5, OUT, 0.03f * s, cx - 0.47f * s, cy - 0.08f * s, cx - 0.36f * s, cy - 0.34f * s, cx + 0.36f * s, cy - 0.34f * s, cx + 0.47f * s, cy - 0.08f * s);
        gfx.rrect(c, cx - 0.2f * s, cy - 0.48f * s, cx + 0.2f * s, cy - 0.34f * s, 0.02f * s, WOOD);
        gfx.rrectLine(c, cx - 0.2f * s, cy - 0.48f * s, cx + 0.2f * s, cy - 0.34f * s, 0.02f * s, 0.018f * s, OUT);
        gfx.textC(c, "SHOP", cx, cy - 0.41f * s, 0.1f * s, Color.WHITE, 0xFF3A1F08, Paint.Align.CENTER);
        gfx.rrect(c, cx - 0.3f * s, cy + 0.02f * s, cx + 0.3f * s, cy + 0.34f * s, 0.03f * s, 0xFF5A3410);
        for (int k = 0; k < 3; k++) {
            float x = cx + (k - 1) * 0.2f * s;
            gfx.circle(c, x, cy + 0.18f * s, 0.085f * s, BOOSTER_COLOR[k]);
            gfx.boosterIcon(c, k, x, cy + 0.18f * s, 0.05f * s);
        }
    }

    // ---------------------------------------------------------------- upgrade card

    private void drawCard(Canvas c) {
        FarmHub h = v.hub;
        int id = h.card, lvl = v.progress.building[id], max = Farm.maxLevel(id);
        c.drawColor(Color.argb(160, 0, 0, 0));
        RectF o = h.cardBox;
        float u = v.u, ph = o.height();
        gfx.rrect(c, o.left, o.top + 1.5f * u, o.right, o.bottom + 1.5f * u, 6 * u, Gfx.darken(WOOD, 0.6f));
        gfx.rrect(c, o.left, o.top, o.right, o.bottom, 6 * u, WOOD);
        gfx.rrect(c, o.left + u, o.top + u, o.right - u, o.bottom - u, 5 * u, CREAM);
        gfx.rrect(c, o.left - u, o.top - 3 * u, o.right + u, o.top + ph * 0.14f, 5 * u, ORANGE);
        gfx.textC(c, Farm.BUILDING_NAME[id], o.centerX(), o.top + ph * 0.045f, 7 * u, Color.WHITE, 0xFF7A3E0B, Paint.Align.CENTER);

        art(c, id, o.centerX(), o.top + ph * 0.29f, 34 * u);
        gfx.textC(c, "Level " + lvl + " of " + max, o.centerX(), o.top + ph * 0.465f, 4.6f * u, 0xFF8B5A2B, 0, Paint.Align.CENTER);
        gfx.textC(c, "Now:  " + Farm.effect(id, lvl), o.centerX(), o.top + ph * 0.535f, 4.6f * u, INK, 0, Paint.Align.CENTER);
        if (lvl < max) gfx.textC(c, "Next:  " + Farm.effect(id, lvl + 1), o.centerX(), o.top + ph * 0.605f, 4.6f * u, 0xFF3E9E22, 0, Paint.Align.CENTER);
        else gfx.textC(c, "Fully upgraded!", o.centerX(), o.top + ph * 0.605f, 4.6f * u, 0xFF3E9E22, 0, Paint.Align.CENTER);
        if (lvl < max) gfx.textC(c, "You have " + v.progress.totalCoins + " coins", o.centerX(), o.top + ph * 0.665f, 3.8f * u, 0xFF8B5A2B, 0, Paint.Align.CENTER);
    }

    // ---------------------------------------------------------------- market

    void drawMarket(Canvas c) {
        header(c, "Market");
        FarmHub h = v.hub;
        Progress p = v.progress;
        float u = v.u, m = 3 * u;
        long day = Farm.today();
        int star = Farm.specialCrop(day);

        float bt = h.bannerTop, bb = bt + h.bannerH;
        gfx.rrect(c, m, bt + 0.8f * u, v.W - m, bb + 0.8f * u, 3 * u, Gfx.darken(WOOD, 0.6f));
        gfx.rrect(c, m, bt, v.W - m, bb, 3 * u, WOOD);
        gfx.rrect(c, m + 0.6f * u, bt + 0.6f * u, v.W - m - 0.6f * u, bb - 0.6f * u, 2.5f * u, 0xFFFFE9A8);
        gfx.star(c, m + 6 * u, bt + h.bannerH / 2, 3.6f * u, 0xFFFFD43B, 0xFFC97700);
        r.drawIcon(c, star, m + 15 * u, bt + h.bannerH / 2, 8 * u);
        gfx.textC(c, "Star crop: " + Farm.NAMES[star], m + 21 * u, bt + h.bannerH * 0.32f, 4.4f * u, INK, 0, Paint.Align.LEFT);
        gfx.textC(c, "Selling at x1.8 today!", m + 21 * u, bt + h.bannerH * 0.72f, 3.6f * u, 0xFFD06A00, 0, Paint.Align.LEFT);
        gfx.textC(c, "Barn " + p.barnTotal() + "/" + p.capacity(), v.W - m - 3 * u, bt + h.bannerH / 2, 4f * u, INK, 0, Paint.Align.RIGHT);

        if (h.rowType.isEmpty()) {
            gfx.textC(c, "Your barn is empty.", v.W / 2, v.H * 0.42f, 6f * u, Color.WHITE, 0xFF1F5F10, Paint.Align.CENTER);
            gfx.textC(c, "Harvest some crops first!", v.W / 2, v.H * 0.42f + 8 * u, 4.6f * u, Color.WHITE, 0xFF1F5F10, Paint.Align.CENTER);
        }
        for (int i = 0; i < h.rowType.size(); i++) drawMarketRow(c, i, day);
        effects(c);
        r.drawButtons(c);
    }

    private void drawMarketRow(Canvas c, int i, long day) {
        FarmHub h = v.hub;
        RectF rr = h.marketRow(i);
        int type = h.rowType.get(i);
        boolean pressed = h.rowBtn.get(i).pressed;
        float u = v.u, off = pressed ? 0.6f * u : 0f, cy = rr.centerY() + off, rad = 2.8f * u;
        int unit = Farm.price(type, day, v.progress.building[Farm.WELL]);
        gfx.rrect(c, rr.left, rr.top + 0.8f * u + off, rr.right, rr.bottom + 0.8f * u + off, rad, Gfx.darken(WOOD, 0.6f));
        gfx.rrect(c, rr.left, rr.top + off, rr.right, rr.bottom + off, rad, WOOD);
        gfx.rrect(c, rr.left + 0.5f * u, rr.top + 0.5f * u + off, rr.right - 0.5f * u, rr.bottom - 0.5f * u + off, rad * 0.85f, CREAM);
        r.drawIcon(c, type, rr.left + 6.5f * u, cy, 8.8f * u);
        gfx.textC(c, Farm.NAMES[type], rr.left + 13 * u, cy - 2.2f * u, 4.3f * u, INK, 0, Paint.Align.LEFT);
        gfx.textC(c, "x" + v.progress.barn[type], rr.left + 13 * u, cy + 2.6f * u, 3.8f * u, 0xFF8B5A2B, 0, Paint.Align.LEFT);

        // price per crop, with a trend arrow
        float px = rr.left + 47 * u;
        gfx.coin(c, px, cy, 2.1f * u);
        gfx.textC(c, String.valueOf(unit), px + 3.3f * u, cy, 4.6f * u, INK, 0, Paint.Align.LEFT);
        int trend = Farm.trend(type, day);
        float ax = px + 12.5f * u;
        if (trend > 0) gfx.polygon(c, 0xFF4CBB2C, 0xFF2C7F14, 0.35f * u, ax, cy - 2.2f * u, ax + 2.2f * u, cy + 1.6f * u, ax - 2.2f * u, cy + 1.6f * u);
        else if (trend < 0) gfx.polygon(c, 0xFFE5604A, 0xFF9E2A24, 0.35f * u, ax, cy + 2.2f * u, ax + 2.2f * u, cy - 1.6f * u, ax - 2.2f * u, cy - 1.6f * u);

        // sell chip
        float cl = rr.right - 27 * u, cr = rr.right - 2 * u, chh = rr.height() * 0.68f;
        gfx.rrect(c, cl, cy - chh / 2 + 0.7f * u, cr, cy + chh / 2 + 0.7f * u, chh / 2, Gfx.darken(ORANGE, 0.62f));
        gfx.rrect(c, cl, cy - chh / 2, cr, cy + chh / 2, chh / 2, ORANGE);
        priceChip(c, (cl + cr) / 2, cy, v.progress.sellValue(type, day), true, 4.4f * u);
    }

    // ---------------------------------------------------------------- general store

    void drawShop(Canvas c) {
        header(c, "General Store");
        FarmHub h = v.hub;
        Progress p = v.progress;
        float u = v.u;
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

    // ---------------------------------------------------------------- orders

    private void notice(Canvas c, float cx, float cy, float s) {
        gfx.rrect(c, cx - 0.3f * s, cy - 0.05f * s, cx - 0.24f * s, cy + 0.42f * s, 0.01f * s, 0xFF8B5A2B);
        gfx.rrect(c, cx + 0.24f * s, cy - 0.05f * s, cx + 0.3f * s, cy + 0.42f * s, 0.01f * s, 0xFF8B5A2B);
        gfx.rrect(c, cx - 0.44f * s, cy - 0.4f * s, cx + 0.44f * s, cy + 0.16f * s, 0.04f * s, 0xFFB5793B);
        gfx.rrectLine(c, cx - 0.44f * s, cy - 0.4f * s, cx + 0.44f * s, cy + 0.16f * s, 0.04f * s, 0.03f * s, OUT);
        int[] paper = {0xFFFFF4D2, 0xFFFFE9A8, 0xFFFFD9D2};
        float[] px = {-0.25f, 0f, 0.26f}, rot = {-6, 4, -3};
        for (int k = 0; k < 3; k++) {
            float x = cx + px[k] * s, y = cy - 0.12f * s;
            c.save();
            c.rotate(rot[k], x, y);
            gfx.rrect(c, x - 0.1f * s, y - 0.15f * s, x + 0.1f * s, y + 0.15f * s, 0.015f * s, paper[k]);
            gfx.rrectLine(c, x - 0.1f * s, y - 0.15f * s, x + 0.1f * s, y + 0.15f * s, 0.015f * s, 0.012f * s, 0xFF8B5A2B);
            for (int l = 0; l < 3; l++) gfx.lineSeg(c, x - 0.065f * s, y - 0.05f * s + l * 0.06f * s, x + 0.065f * s, y - 0.05f * s + l * 0.06f * s, 0.012f * s, 0xFF8A7A5A);
            gfx.circle(c, x, y - 0.145f * s, 0.024f * s, 0xFFE5304A);
            c.restore();
        }
    }

    /** A customer's face, drawn in code: a bust with either a hat or hair. */
    private void customer(Canvas c, int id, float cx, float cy, float r) {
        id = Math.floorMod(id, SKIN.length);
        c.save();
        gfx.clipCircle(c, cx, cy, r);
        gfx.circle(c, cx, cy, r, 0xFFCFEBA8);
        gfx.ellipse(c, cx, cy + r * 0.82f, r * 0.78f, r * 0.42f, CLOTHES[id]);
        gfx.circle(c, cx, cy + r * 0.02f, r * 0.52f, SKIN[id]);
        if (id % 2 == 0) {
            int hc = CLOTHES[(id + 3) % CLOTHES.length];
            gfx.ellipse(c, cx, cy - r * 0.3f, r * 0.46f, r * 0.32f, hc);
            gfx.rrect(c, cx - r * 0.72f, cy - r * 0.34f, cx + r * 0.72f, cy - r * 0.22f, r * 0.06f, Gfx.darken(hc, 0.85f));
        } else {
            gfx.ellipse(c, cx, cy - r * 0.27f, r * 0.55f, r * 0.34f, id == 1 ? 0xFFDADADA : 0xFF7A4E26);
        }
        gfx.circle(c, cx - r * 0.19f, cy + r * 0.02f, r * 0.07f, OUT);
        gfx.circle(c, cx + r * 0.19f, cy + r * 0.02f, r * 0.07f, OUT);
        gfx.circle(c, cx - r * 0.31f, cy + r * 0.2f, r * 0.1f, 0x66FF7A7A);
        gfx.circle(c, cx + r * 0.31f, cy + r * 0.2f, r * 0.1f, 0x66FF7A7A);
        gfx.ellipse(c, cx, cy + r * 0.26f, r * 0.16f, r * 0.08f, 0xFF8A3A2A);
        c.restore();
        gfx.circleLine(c, cx, cy, r, r * 0.07f, 0xFF8B5A2B);
    }

    void drawOrders(Canvas c) {
        header(c, "Orders");
        FarmHub h = v.hub;
        Progress p = v.progress;
        float u = v.u, m = 3 * u;
        long day = Farm.today();

        // daily gift banner
        float gt = h.giftTop, gb = gt + h.giftH, gy = (gt + gb) / 2;
        gfx.rrect(c, m, gt + 0.8f * u, v.W - m, gb + 0.8f * u, 3 * u, Gfx.darken(WOOD, 0.6f));
        gfx.rrect(c, m, gt, v.W - m, gb, 3 * u, WOOD);
        gfx.rrect(c, m + 0.6f * u, gt + 0.6f * u, v.W - m - 0.6f * u, gb - 0.6f * u, 2.5f * u, 0xFFFFE9A8);
        float gx = m + 7 * u;
        gfx.rrect(c, gx - 3.4f * u, gy - 2f * u, gx + 3.4f * u, gy + 3.8f * u, 0.6f * u, 0xFFE5604A);
        gfx.rrect(c, gx - 4.2f * u, gy - 4f * u, gx + 4.2f * u, gy - 1.4f * u, 0.6f * u, 0xFFD9453A);
        gfx.rrect(c, gx - 0.8f * u, gy - 4f * u, gx + 0.8f * u, gy + 3.8f * u, 0.3f * u, 0xFFFFD43B);
        boolean ready = p.giftReady(day);
        gfx.textC(c, ready ? "Daily gift is ready!" : "Daily gift claimed", m + 14 * u, gy - 2.2f * u, 4.6f * u, INK, 0, Paint.Align.LEFT);
        gfx.textC(c, ready ? "+" + Farm.GIFT_COINS + " coins, bonus every 3rd day" : "Come back tomorrow!", m + 14 * u, gy + 2.6f * u, 3.0f * u, 0xFF8B5A2B, 0, Paint.Align.LEFT);
        if (ready) {
            RectF chip = h.giftChip();
            boolean pressed = h.giftBtn != null && h.giftBtn.pressed;
            float off = pressed ? 0.6f * u : 0f;
            gfx.rrect(c, chip.left, chip.top + 0.8f * u, chip.right, chip.bottom + 0.8f * u, chip.height() * 0.4f, Gfx.darken(GREEN, 0.62f));
            gfx.rrect(c, chip.left, chip.top + off, chip.right, chip.bottom + off, chip.height() * 0.4f, GREEN);
            gfx.textC(c, "CLAIM", chip.centerX(), chip.centerY() + off, 4.6f * u, Color.WHITE, Gfx.darken(GREEN, 0.45f), Paint.Align.CENTER);
        }

        for (int slot = 0; slot < Orders.SLOTS; slot++) drawOrderCard(c, slot, day);
        effects(c);
        r.drawButtons(c);
    }

    private void drawOrderCard(Canvas c, int slot, long day) {
        FarmHub h = v.hub;
        Progress p = v.progress;
        Orders.Order o = p.orders[slot];
        RectF cd = h.orderCard(slot);
        float u = v.u;
        gfx.rrect(c, cd.left, cd.top + 1f * u, cd.right, cd.bottom + 1f * u, 4 * u, Gfx.darken(WOOD, 0.6f));
        gfx.rrect(c, cd.left, cd.top, cd.right, cd.bottom, 4 * u, WOOD);
        gfx.rrect(c, cd.left + 0.7f * u, cd.top + 0.7f * u, cd.right - 0.7f * u, cd.bottom - 0.7f * u, 3.4f * u, CREAM);
        if (!o.has) {
            gfx.textC(c, "No order right now", cd.centerX(), cd.centerY() - 3 * u, 5.4f * u, INK, 0, Paint.Align.CENTER);
            gfx.textC(c, "A new customer arrives tomorrow.", cd.centerX(), cd.centerY() + 3.5f * u, 3.8f * u, 0xFF8B5A2B, 0, Paint.Align.CENTER);
            return;
        }
        customer(c, o.customer, cd.left + 10.5f * u, cd.top + 10 * u, 7.5f * u);
        gfx.textC(c, Orders.CUSTOMER[o.customer], cd.left + 20 * u, cd.top + 7 * u, 4.8f * u, INK, 0, Paint.Align.LEFT);
        int dl = Orders.daysLeft(o, day);
        gfx.textC(c, dl <= 1 ? "Last day!" : dl + " days left", cd.right - 3.5f * u, cd.top + 7 * u, 3.8f * u, dl <= 1 ? 0xFFD9453A : 0xFF8B5A2B, 0, Paint.Align.RIGHT);
        gfx.textC(c, "wants:", cd.left + 20 * u, cd.top + 12.5f * u, 3.4f * u, 0xFF8B5A2B, 0, Paint.Align.LEFT);

        for (int k = 0; k < o.n; k++) {
            float ix = cd.left + 4 * u + k * 22 * u + 10.5f * u;
            int have = p.barn[o.type[k]];
            r.drawIcon(c, o.type[k], ix, cd.top + 22.5f * u, 10 * u);
            boolean enough = have >= o.qty[k];
            gfx.textC(c, Math.min(have, 999) + "/" + o.qty[k], ix, cd.top + 30.2f * u, 4f * u, enough ? 0xFF3E9E22 : 0xFFD9453A, 0, Paint.Align.CENTER);
        }

        float ry = cd.top + 39 * u;
        gfx.coin(c, cd.left + 6 * u, ry, 2.7f * u);
        String reward = "+" + o.reward;
        gfx.textC(c, reward, cd.left + 10 * u, ry, 5.6f * u, 0xFFFFC928, 0xFF7A3E0B, Paint.Align.LEFT);
        if (o.gift >= 0) {
            float bx = cd.left + 10 * u + gfx.width(reward, 5.6f * u) + 6 * u;
            gfx.circle(c, bx, ry, 3.3f * u, BOOSTER_COLOR[o.gift]);
            gfx.circleLine(c, bx, ry, 3.3f * u, 0.4f * u, Gfx.darken(BOOSTER_COLOR[o.gift], 0.6f));
            gfx.boosterIcon(c, o.gift, bx, ry, 2.1f * u);
            gfx.textC(c, "+1", bx + 5.2f * u, ry, 4f * u, INK, 0, Paint.Align.LEFT);
        }

        RectF f = h.fillRect(slot);
        boolean ok = p.canFill(slot);
        boolean pressed = h.fillBtn[slot] != null && h.fillBtn[slot].pressed;
        float off = pressed ? 0.6f * u : 0f, fh = f.height();
        int base = ok ? GREEN : 0xFFA8A8A8;
        gfx.rrect(c, f.left, f.top + 0.9f * u, f.right, f.bottom + 0.9f * u, fh * 0.4f, Gfx.darken(base, 0.62f));
        gfx.rrect(c, f.left, f.top + off, f.right, f.bottom + off, fh * 0.4f, base);
        gfx.textC(c, "FILL", f.centerX(), f.centerY() + off, 5f * u, Color.WHITE, Gfx.darken(base, 0.45f), Paint.Align.CENTER);
    }
}
