package com.farmharvest.game;

import static com.farmharvest.game.Gfx.BLUE;
import static com.farmharvest.game.Gfx.GREEN;
import static com.farmharvest.game.Gfx.ORANGE;

import android.graphics.Color;
import android.graphics.RectF;

import com.farmharvest.game.GameView.Button;
import com.farmharvest.game.GameView.Screen;

/** Layout, state and buttons for the farm screen (shown after every level). */
final class FarmHub {
    static final int TAB_ANIMALS = 0, TAB_DECOR = 1;
    /** Animals tab: one card per species (coop, barn, sty, pen, hive), then a read-only streak card. */
    static final int ANIMAL_ACTIONS = Farm.SPECIES, CARD_STREAK = Farm.SPECIES;

    private final GameView v;

    /** True when the farm is showing the result of a level that was just played. */
    boolean result;
    /** After a level the result is a compact view; Tend Farm opens the full farm screen on top of it. */
    boolean tend;
    /** The first-run explanation of the farm is showing. */
    boolean tipOpen;
    int tab;

    final RectF banner = new RectF(), scene = new RectF(), stockStrip = new RectF(), tendRect = new RectF();
    final RectF[] tabRect = {new RectF(), new RectF()};
    final RectF[] card = new RectF[Farm.DECORS];
    final Button[] cardBtn = new Button[Farm.DECORS];
    final Button[] tabBtn = new Button[2];
    Button tendBtn;
    float titleY;


    FarmHub(GameView v) {
        this.v = v;
        for (int i = 0; i < card.length; i++) card[i] = new RectF();
    }

    /** Called when the farm is opened from the title or level menu. */
    void open() {
        result = false;
        tend = false;
        tab = TAB_ANIMALS;
        tipOpen = !v.progress.farmTipSeen;
        layout();
    }

    void showResult() {
        result = true;
        tend = false;
        tab = TAB_ANIMALS;
        tipOpen = !v.progress.farmTipSeen;
        layout();
    }

    void dismissTip() {
        tipOpen = false;
        v.progress.farmTipShown();
        v.buildButtons();
    }

    /** The level result with a banner and a Tend Farm button, as opposed to the full farm screen. */
    boolean compact() {
        return result && !tend;
    }

    void setTend(boolean on) {
        tend = on;
        layout();
        v.buildButtons();
    }

    /** True when a coin purchase for any species is affordable right now, to put a badge on Tend Farm. */
    boolean somethingToBuy() {
        Progress p = v.progress;
        for (int sp = 0; sp < Farm.SPECIES; sp++) {
            int act = p.nextAction(sp);
            if ((act == Progress.ACT_BUILD || act == Progress.ACT_BUY || act == Progress.ACT_UPGRADE) && p.totalCoins >= p.actionPrice(sp)) return true;
        }
        return false;
    }

    void layout() {
        float u = v.u, m = 3 * u, hh = 13 * u, gap = 2.5f * u, tabH = 8.5f * u, btnH = 13 * u, stripH = 12f * u;
        boolean compact = compact();
        float y = v.topInset + m + hh + gap;
        titleY = y + 3.5f * u;
        if (compact) {
            banner.set(m, y, v.W - m, y + 34 * u);
            y = banner.bottom + gap;
        } else {
            y += 9 * u;
        }

        float bottom = v.H - v.botInset - m;
        float tw = (v.W - 2 * m - gap) / 2f;
        if (compact) {
            // banner, then as much scene as fits, a Tend Farm button and the level buttons
            tendRect.set(m, bottom - 2 * btnH - gap, v.W - m, bottom - btnH - gap);
            float sceneH = Math.min(0.9f * (v.W - 2 * m), tendRect.top - gap - y);
            scene.set(m, y, v.W - m, y + sceneH);
        } else {
            float avail = bottom - y;
            float sceneWant = 0.62f * (v.W - 2 * m);
            float cardH = Math.max(15 * u, Math.min(25 * u, (avail - tabH - stripH - 5 * gap - sceneWant) / 3f));
            float sceneH = Math.min(0.9f * (v.W - 2 * m), avail - tabH - stripH - 5 * gap - 3 * cardH);
            scene.set(m, y, v.W - m, y + sceneH);
            stockStrip.set(m, scene.bottom + gap, v.W - m, scene.bottom + gap + stripH);

            float tabTop = stockStrip.bottom + gap;
            tabRect[0].set(m, tabTop, m + tw, tabTop + tabH);
            tabRect[1].set(m + tw + gap, tabTop, v.W - m, tabTop + tabH);

            float cardsTop = tabTop + tabH + gap;
            for (int i = 0; i < card.length; i++) {
                float x = m + (i % 2) * (tw + gap), cy = cardsTop + (i / 2) * (cardH + gap);
                card[i].set(x, cy, x + tw, cy + cardH);
            }
        }
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
        if (s == Screen.FARM && tipOpen) {
            float w = v.tipBox.width() * 0.6f, h = v.tipBox.height() * 0.11f;
            v.addWide(v.tipBox.centerX(), v.tipBox.top + v.tipBox.height() * 0.9f, w, h, "GOT IT!", GREEN, this::dismissTip);
        } else if (s == Screen.FARM && compact()) {
            v.addBack(m, v.topInset + m, hh, v::goLevels);
            tendBtn = v.addWide(tendRect.centerX(), tendRect.centerY(), tendRect.width(), tendRect.height(), "TEND FARM", ORANGE, () -> setTend(true));
            buildResultButtons();
        } else if (s == Screen.FARM) {
            v.addBack(m, v.topInset + m, hh, result ? () -> setTend(false) : v::goTitle);
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
            int act = p.nextAction(i);
            if (act == Progress.ACT_HATCHING) {
                v.fx.pop("Eggs hatch on their own!", c.centerX(), c.centerY(), 5f * v.u, 1f, Color.WHITE, v.W * 0.8f);
                return;
            }
            done = act == Progress.ACT_BUILD ? "Built!" : act == Progress.ACT_UPGRADE ? "Upgraded!" : "Welcome!";
            err = p.tend(i);
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
}
