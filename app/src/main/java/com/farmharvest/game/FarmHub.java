package com.farmharvest.game;

import static com.farmharvest.game.Gfx.BLUE;
import static com.farmharvest.game.Gfx.GREEN;
import static com.farmharvest.game.Gfx.ORANGE;

import android.graphics.Color;
import android.graphics.RectF;

import com.farmharvest.game.GameView.Button;
import com.farmharvest.game.GameView.Screen;

import java.util.ArrayList;
import java.util.List;

/** Layout, state and buttons for the farm, market and general store screens. Drawing is in FarmRenderer. */
final class FarmHub {
    static final int BARN = 0, WELL = 1, PLOT = 2, MARKET = 3, STORE = 4, BUILDINGS = 5;

    private final GameView v;

    final RectF[] tile = new RectF[BUILDINGS];
    final Button[] bld = new Button[BUILDINGS];
    final RectF cardBox = new RectF();
    /** Building whose upgrade card is open, or -1. */
    int card = -1;

    // market
    final List<Integer> rowType = new ArrayList<>();
    final List<Button> rowBtn = new ArrayList<>();
    float titleY, bannerTop, bannerH, rowTop0, rowH, rowGap;
    Button sellAllBtn;

    // store
    final RectF[] shopRow = new RectF[Farm.BOOSTERS];
    final Button[] shopBuy = new Button[Farm.BOOSTERS];

    FarmHub(GameView v) {
        this.v = v;
        for (int i = 0; i < BUILDINGS; i++) tile[i] = new RectF();
        for (int i = 0; i < shopRow.length; i++) shopRow[i] = new RectF();
    }

    void layout() {
        float u = v.u, m = 3 * u, hh = 13 * u;
        titleY = v.topInset + m + hh + 6 * u;

        float ay = v.topInset + m + hh + 12 * u;
        float rowH3 = (v.H - v.botInset - m - ay) / 3f;
        float tw = 44 * u, th = rowH3 - 2 * u;
        float lx = v.W / 2 - 24 * u, rx = v.W / 2 + 24 * u, mx = v.W / 2;
        place(BARN, lx, ay, tw, th);
        place(MARKET, rx, ay, tw, th);
        place(WELL, lx, ay + rowH3, tw, th);
        place(PLOT, rx, ay + rowH3, tw, th);
        place(STORE, mx, ay + 2 * rowH3, tw, th);

        cardBox.set(v.W * 0.08f, v.H * 0.2f, v.W * 0.92f, v.H * 0.8f);

        bannerTop = titleY + 7 * u;
        bannerH = 11 * u;
        rowTop0 = bannerTop + bannerH + 3 * u;
        rowH = 10.5f * u;
        rowGap = 1.5f * u;

        float sy = titleY + 9 * u;
        for (int k = 0; k < shopRow.length; k++) shopRow[k].set(m, sy + k * 30 * u, v.W - m, sy + k * 30 * u + 27 * u);
    }

    private void place(int i, float cx, float top, float w, float h) {
        tile[i].set(cx - w / 2, top, cx + w / 2, top + h);
    }

    RectF marketRow(int i) {
        float m = 3 * v.u, top = rowTop0 + i * (rowH + rowGap);
        return new RectF(m, top, v.W - m, top + rowH);
    }

    RectF buyChip(int k) {
        RectF r = shopRow[k];
        float u = v.u;
        return new RectF(r.right - 31 * u, r.centerY() - 6 * u, r.right - 3 * u, r.centerY() + 6 * u);
    }

    void open() {
        card = -1;
    }

    void build(Screen s) {
        float m = 3 * v.u, hh = 13 * v.u;
        if (s == Screen.FARM) {
            if (card >= 0) {
                buildCard();
                return;
            }
            v.addBack(m, v.topInset + m, hh, v::goTitle);
            for (int i = 0; i < BUILDINGS; i++) {
                final int id = i;
                bld[i] = v.addHit(tile[i], () -> tapBuilding(id));
            }
        } else if (s == Screen.MARKET) {
            v.addBack(m, v.topInset + m, hh, () -> v.goScreen(Screen.FARM));
            buildMarket();
        } else {
            v.addBack(m, v.topInset + m, hh, () -> v.goScreen(Screen.FARM));
            for (int k = 0; k < Farm.BOOSTERS; k++) {
                final int kind = k;
                shopBuy[k] = v.addHit(buyChip(k), () -> buyBooster(kind));
            }
        }
    }

    private void tapBuilding(int id) {
        if (id == MARKET) {
            v.goScreen(Screen.MARKET);
        } else if (id == STORE) {
            v.goScreen(Screen.SHOP);
        } else {
            card = id;
            v.buildButtons();
        }
    }

    // ---------------------------------------------------------------- upgrade card

    private void buildCard() {
        final int b = card;
        int cost = Farm.nextCost(b, v.progress.building[b]);
        float pw = cardBox.width(), ph = cardBox.height(), cx = cardBox.centerX();
        if (cost >= 0) {
            boolean ok = v.progress.totalCoins >= cost;
            v.addWide(cx, cardBox.top + ph * 0.79f, pw * 0.74f, ph * 0.11f, "UPGRADE  " + cost, ok ? GREEN : 0xFFA8A8A8, () -> buyUpgrade(b));
        }
        v.addWide(cx, cardBox.top + ph * (cost >= 0 ? 0.935f : 0.84f), pw * 0.5f, ph * 0.09f, "CLOSE", BLUE, () -> {
            card = -1;
            v.buildButtons();
        });
    }

    private void buyUpgrade(int b) {
        if (v.progress.buyUpgrade(b)) {
            v.sfx.powerUpMade();
            v.fx.sparkle(cardBox.centerX(), cardBox.top + cardBox.height() * 0.3f, v.u, v.cell);
            v.fx.sparkle(cardBox.centerX() - 12 * v.u, cardBox.top + cardBox.height() * 0.33f, v.u, v.cell);
            v.fx.sparkle(cardBox.centerX() + 12 * v.u, cardBox.top + cardBox.height() * 0.33f, v.u, v.cell);
            v.fx.pop("Upgraded!", cardBox.centerX(), cardBox.top + cardBox.height() * 0.40f, 8 * v.u, 1.2f, 0xFFFFD43B, v.W * 0.8f);
            v.buildButtons();
        } else {
            v.sfx.badSwap();
            v.fx.pop("Not enough coins", cardBox.centerX(), cardBox.top + cardBox.height() * 0.68f, 5.5f * v.u, 1f, 0xFFFF7A66, v.W * 0.8f);
        }
    }

    // ---------------------------------------------------------------- market

    private void buildMarket() {
        final long day = Farm.today();
        rowType.clear();
        rowBtn.clear();
        sellAllBtn = null;
        for (int t = 0; t < Art.CROPS; t++) if (v.progress.barn[t] > 0) rowType.add(t);
        rowType.sort((a, b) -> Integer.compare(v.progress.sellValue(b, day), v.progress.sellValue(a, day)));
        for (int i = 0; i < rowType.size(); i++) {
            final int type = rowType.get(i);
            rowBtn.add(v.addHit(marketRow(i), () -> sellRow(type)));
        }
        if (!rowType.isEmpty()) {
            float u = v.u, bh = 13 * u;
            float cy = v.H - v.botInset - 3 * u - bh / 2;
            sellAllBtn = v.addWide(v.W / 2, cy, v.W * 0.7f, bh, "SELL ALL  +" + v.progress.sellValueAll(day), GREEN, this::sellAll);
        }
    }

    private void sellRow(int type) {
        int row = rowType.indexOf(type);
        RectF r = marketRow(Math.max(0, row));
        int value = v.progress.sell(type, Farm.today());
        celebrate(r.centerX(), r.centerY(), value);
        v.buildButtons();
    }

    private void sellAll() {
        int value = v.progress.sellAll(Farm.today());
        celebrate(v.W / 2, rowTop0 + 3 * rowH, value);
        v.buildButtons();
    }

    private void celebrate(float x, float y, int value) {
        v.sfx.coin();
        v.sfx.haptic(15, 90);
        v.fx.pop("+" + value, x, y, 7 * v.u, 1.1f, 0xFFFFD43B, v.W * 0.8f);
        for (int i = 0; i < 3; i++) v.fx.sparkle(x + (i - 1) * 14 * v.u, y, v.u, v.cell);
    }

    // ---------------------------------------------------------------- store

    private void buyBooster(int k) {
        RectF r = shopRow[k];
        if (v.progress.buyBooster(k)) {
            v.sfx.coin();
            v.fx.sparkle(r.left + 12 * v.u, r.centerY(), v.u, v.cell);
            v.fx.pop("Bought!", r.centerX(), r.centerY(), 6.5f * v.u, 0.9f, Color.WHITE, v.W * 0.8f);
        } else {
            v.sfx.badSwap();
            v.fx.pop("Not enough coins", r.centerX(), r.centerY(), 5.5f * v.u, 1f, 0xFFFF7A66, v.W * 0.8f);
        }
    }
}
