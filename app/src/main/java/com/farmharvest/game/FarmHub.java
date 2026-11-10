package com.farmharvest.game;

import static com.farmharvest.game.Gfx.BLUE;
import static com.farmharvest.game.Gfx.GREEN;
import static com.farmharvest.game.Gfx.ORANGE;

import android.graphics.Color;
import android.graphics.RectF;

import com.farmharvest.game.GameView.Button;
import com.farmharvest.game.GameView.Screen;

/** Layout, state and buttons for the farm screen (shown after every level) and the booster store. */
final class FarmHub {
    static final int TAB_ANIMALS = 0, TAB_DECOR = 1;
    /** Animals tab buttons: coop, barn, hatch, cow. */
    static final int ANIMAL_ACTIONS = 4, CARD_COOP = 0, CARD_BARN = 1, CARD_HATCH = 2, CARD_COW = 3;

    private final GameView v;

    /** True when the farm is showing the result of a level that was just played. */
    boolean result;
    int tab;

    final RectF banner = new RectF(), scene = new RectF(), shopRect = new RectF();
    final RectF[] tabRect = {new RectF(), new RectF()};
    final RectF[] card = new RectF[Farm.DECORS];
    final Button[] cardBtn = new Button[Farm.DECORS];
    final Button[] tabBtn = new Button[2];
    Button shopBtn;
    float titleY;

    // booster store
    final RectF[] shopRow = new RectF[Farm.BOOSTERS];
    final Button[] shopBuy = new Button[Farm.BOOSTERS];
    float shopTitleY;

    FarmHub(GameView v) {
        this.v = v;
        for (int i = 0; i < card.length; i++) card[i] = new RectF();
        for (int i = 0; i < shopRow.length; i++) shopRow[i] = new RectF();
    }

    /** Called when the farm is opened from the title or level menu. */
    void open() {
        result = false;
        tab = TAB_ANIMALS;
        layout();
    }

    void showResult() {
        result = true;
        tab = TAB_ANIMALS;
        layout();
    }

    void layout() {
        float u = v.u, m = 3 * u, hh = 13 * u, gap = 2.5f * u, tabH = 8.5f * u, btnH = 13 * u;
        float y = v.topInset + m + hh + gap;
        titleY = y + 3.5f * u;
        if (result) {
            banner.set(m, y, v.W - m, y + 34 * u);
            y = banner.bottom + gap;
        } else {
            y += 9 * u;
        }
        shopRect.set(v.W - m - hh, v.topInset + m, v.W - m, v.topInset + m + hh);

        float bottom = v.H - v.botInset - m;
        float cardsBottom = result ? bottom - btnH - gap : bottom;
        float avail = cardsBottom - y;
        float sceneWant = 0.62f * (v.W - 2 * m);
        float cardH = Math.max(15 * u, Math.min(25 * u, (avail - tabH - 4 * gap - sceneWant) / 3f));
        float sceneH = Math.min(0.9f * (v.W - 2 * m), avail - tabH - 4 * gap - 3 * cardH);
        scene.set(m, y, v.W - m, y + sceneH);

        float tabTop = scene.bottom + gap, tw = (v.W - 2 * m - gap) / 2f;
        tabRect[0].set(m, tabTop, m + tw, tabTop + tabH);
        tabRect[1].set(m + tw + gap, tabTop, v.W - m, tabTop + tabH);

        float cardsTop = tabTop + tabH + gap;
        for (int i = 0; i < card.length; i++) {
            float x = m + (i % 2) * (tw + gap), cy = cardsTop + (i / 2) * (cardH + gap);
            card[i].set(x, cy, x + tw, cy + cardH);
        }

        shopTitleY = v.topInset + m + hh + 6 * u;
        float sy = shopTitleY + 9 * u;
        for (int k = 0; k < shopRow.length; k++) shopRow[k].set(m, sy + k * 30 * u, v.W - m, sy + k * 30 * u + 27 * u);
    }

    RectF buyChip(int k) {
        RectF r = shopRow[k];
        float u = v.u;
        return new RectF(r.right - 31 * u, r.centerY() - 6 * u, r.right - 3 * u, r.centerY() + 6 * u);
    }

    /** Cards shown on the current tab. */
    int cardCount() {
        return Farm.DECORS;
    }

    /** Cards that can be tapped: on the animals tab the last two (herd and stock) are just readouts. */
    int buttonCount() {
        return tab == TAB_ANIMALS ? ANIMAL_ACTIONS : Farm.DECORS;
    }

    void build(Screen s) {
        float m = 3 * v.u, hh = 13 * v.u;
        if (s == Screen.FARM) {
            v.addBack(m, v.topInset + m, hh, result ? v::goLevels : v::goTitle);
            shopBtn = v.addHit(shopRect, () -> v.goScreen(Screen.SHOP));
            for (int t = 0; t < 2; t++) {
                final int which = t;
                tabBtn[t] = v.addHit(tabRect[t], () -> {
                    tab = which;
                    v.buildButtons();
                });
            }
            for (int i = 0; i < cardBtn.length; i++) cardBtn[i] = null;
            for (int i = 0; i < buttonCount(); i++) {
                final int idx = i;
                cardBtn[i] = v.addHit(card[i], () -> buy(idx));
            }
            if (result) buildResultButtons();
        } else {
            v.addBack(m, v.topInset + m, hh, () -> v.goScreen(Screen.FARM));
            for (int k = 0; k < Farm.BOOSTERS; k++) {
                final int kind = k;
                shopBuy[k] = v.addHit(buyChip(k), () -> buyBooster(kind));
            }
        }
    }

    private void buildResultButtons() {
        float u = v.u, m = 3 * u, gap = 2.5f * u, h = 13 * u;
        float total = v.W - 2 * m, cy = v.H - v.botInset - m - h / 2;
        if (v.won) {
            boolean hasNext = v.levelIndex + 1 < Level.COUNT;
            float usable = total - 2 * gap, wR = usable * 0.30f, wN = usable * 0.40f, wM = usable * 0.30f;
            v.addWide(m + wR / 2, cy, wR, h, "REPLAY", ORANGE, () -> v.startLevel(v.levelIndex));
            v.addWide(m + wR + gap + wN / 2, cy, wN, h, hasNext ? "NEXT" : "DONE", GREEN,
                    hasNext ? () -> v.startLevel(v.levelIndex + 1) : v::goLevels);
            v.addWide(v.W - m - wM / 2, cy, wM, h, "MENU", BLUE, v::goLevels);
        } else {
            float usable = total - gap, wT = usable * 0.6f, wM = usable * 0.4f;
            v.addWide(m + wT / 2, cy, wT, h, "TRY AGAIN", GREEN, () -> v.startLevel(v.levelIndex));
            v.addWide(v.W - m - wM / 2, cy, wM, h, "MENU", BLUE, v::goLevels);
        }
    }

    // ---------------------------------------------------------------- buying

    private void buy(int i) {
        Progress p = v.progress;
        RectF c = card[i];
        String err;
        String done;
        if (tab == TAB_ANIMALS) {
            switch (i) {
                case CARD_COOP:
                case CARD_BARN: {
                    int h = i == CARD_COOP ? Farm.COOP : Farm.BARN;
                    done = p.herd.house[h] == 0 ? "Built!" : "Upgraded!";
                    err = p.buildHouse(h);
                    break;
                }
                case CARD_HATCH:
                    err = p.hatchChick();
                    done = "Chick!";
                    break;
                default:
                    err = p.buyCow();
                    done = "Moo!";
            }
        } else {
            if (p.decor[i]) return;
            err = p.buyDecor(i) ? null : "Not enough coins";
            done = "New!";
        }
        if (err == null) {
            v.sfx.powerUpMade();
            v.sfx.coin();
            float[] at = v.renderer.farm.itemCenter(tab == TAB_DECOR, i);
            v.fx.sparkle(at[0], at[1], v.u, v.cell);
            v.fx.sparkle(at[0] - 8 * v.u, at[1] + 4 * v.u, v.u, v.cell);
            v.fx.sparkle(at[0] + 8 * v.u, at[1] + 4 * v.u, v.u, v.cell);
            v.fx.pop(done, Math.max(0.25f * v.W, Math.min(0.75f * v.W, at[0])), at[1] - 6 * v.u, 8 * v.u, 1.2f, 0xFFFFD43B, v.W * 0.8f);
        } else {
            v.sfx.badSwap();
            v.fx.pop(err, c.centerX(), c.centerY(), 5f * v.u, 1f, 0xFFFF7A66, v.W * 0.8f);
        }
    }

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
