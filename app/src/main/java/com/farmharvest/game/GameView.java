package com.farmharvest.game;

import static com.farmharvest.game.Gfx.BLUE;
import static com.farmharvest.game.Gfx.GREEN;
import static com.farmharvest.game.Gfx.ORANGE;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Owns the game state, runs the match-3 state machine and handles touch input. Drawing is in Renderer. */
public class GameView extends View {
    enum Screen {TITLE, LEVELS, PLAY, FARM, MARKET, SHOP}

    enum Phase {IDLE, SWAP, SWAPBACK, CLEAR, FALL, BAG, TIP, END}

    static final int COLS = Rules.COLS, ROWS = Rules.ROWS;
    private static final float SWAP_SPEED = 9f, GRAVITY = 45f, CLEAR_TIME = 0.28f;

    static final class Button {
        final RectF r = new RectF();
        String text = "";
        int color = GREEN;
        boolean circle, locked, pressed, back, speaker, muted;
        /** Hit area only: the screen draws it itself. */
        boolean hidden;
        /** Booster glyph (Farm.MOVES etc.) for the in-level booster buttons, or -1. */
        int icon = -1;
        int stars = -1;
        Runnable act;
    }

    final Gfx gfx = new Gfx();
    final Random rnd = new Random();
    final Effects fx = new Effects(rnd, gfx);
    final FarmHub hub = new FarmHub(this);
    final Renderer renderer;
    final Progress progress;
    final Sfx sfx;
    boolean confirmQuit;

    final List<Button> buttons = new ArrayList<>();
    final List<Tile> dying = new ArrayList<>();
    Rules.Spawns spawns = new Rules.Spawns();
    Tile fxTile;
    float clearDuration = CLEAR_TIME;
    static final float BAG_PAUSE = 1f, BAG_POP = 0.35f;
    float bagT, bagEndT, tipT;
    int pendingTip = -1, tipType;
    int bagTotal;
    int[] harvest = new int[Art.CROPS];
    int harvestStored, harvestOverflow;
    boolean hammerMode;
    final RectF[] boosterRect = {new RectF(), new RectF(), new RectF()};

    // layout
    float W, H, topInset, botInset, u;
    float bx, by, cell, coinX, coinY, coinR;
    final RectF boardPanel = new RectF(), goalPanel = new RectF(), overlay = new RectF(), tipBox = new RectF(), confirmBox = new RectF();

    // play state
    Screen screen = Screen.TITLE;
    Phase phase = Phase.IDLE;
    Level level;
    int levelIndex;
    Board board;
    int[] need = new int[Art.COUNT];
    int movesLeft, coins, landed, combo, stars, bonus;
    boolean won;
    float dispCoins, idleTime, clearTimer, overlayT, time;
    Tile selected, swapA, swapB, downTile;
    int[] hint;
    Button downButton;
    float downX, downY;
    boolean dragged;
    long last;

    public GameView(Context ctx) {
        super(ctx);
        progress = new Progress(ctx);
        sfx = new Sfx(ctx);
        sfx.enabled = progress.soundOn;
        renderer = new Renderer(ctx, this, gfx, fx);
        setOnApplyWindowInsetsListener((v, insets) -> {
            topInset = insets.getSystemWindowInsetTop();
            botInset = insets.getSystemWindowInsetBottom();
            if (W > 0) {
                layout();
                buildButtons();
            }
            return insets;
        });
    }

    // ---------------------------------------------------------------- layout

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        W = w;
        H = h;
        u = W / 100f;
        renderer.onSize(W, H);
        layout();
        buildButtons();
    }

    private void layout() {
        float m = 3 * u, hh = 13 * u;
        float hudTop = topInset + m;
        float goalTop = hudTop + hh + m * 0.8f;
        goalPanel.set(m, goalTop, W - m, goalTop + 26 * u);
        float top = goalPanel.bottom + m;
        float barH = 15 * u;
        float bottom = H - botInset - m - barH - 8 * u;
        float pad = 2 * u;
        float availH = bottom - top;
        cell = Math.min((W - 2 * m - 2 * pad) / COLS, (availH - 2 * pad) / ROWS);
        float bw = cell * COLS, bh = cell * ROWS;
        bx = (W - bw) / 2;
        by = top + Math.min((availH - bh) / 2, 2 * u);
        boardPanel.set(bx - pad, by - pad, bx + bw + pad, by + bh + pad);
        float barTop = Math.min(boardPanel.bottom + 7 * u, H - botInset - m - barH - 5 * u);
        for (int k = 0; k < 3; k++) {
            float x = W / 2 + (k - 1) * 27 * u - barH / 2;
            boosterRect[k].set(x, barTop, x + barH, barTop + barH);
        }
        hub.layout();
        overlay.set(W * 0.08f, H * 0.2f, W * 0.92f, H * 0.8f);
        tipBox.set(W * 0.1f, H * 0.22f, W * 0.9f, H * 0.78f);
        confirmBox.set(W * 0.1f, H * 0.32f, W * 0.9f, H * 0.68f);
        coinR = hh * 0.5f;
        coinX = m + hh + m * 0.8f + hh * 0.6f;
        coinY = hudTop + hh / 2;
    }

    void buildButtons() {
        buttons.clear();
        float m = 3 * u, hh = 13 * u;
        if (screen == Screen.PLAY && confirmQuit) {
            float pw = confirmBox.width(), ph = confirmBox.height(), bh = ph * 0.17f;
            addWide(confirmBox.centerX(), confirmBox.top + ph * 0.52f, pw * 0.7f, bh, "KEEP PLAYING", GREEN, () -> {
                confirmQuit = false;
                buildButtons();
            });
            addWide(confirmBox.centerX(), confirmBox.top + ph * 0.79f, pw * 0.5f, bh, "QUIT", ORANGE, () -> {
                confirmQuit = false;
                goLevels();
            });
        } else if (screen == Screen.TITLE) {
            addSpeaker(m, topInset + m, hh);
            addWide(W / 2, H * 0.62f + 8.5f * u, W * 0.6f, 17 * u, "PLAY", GREEN, this::goLevels);
            addWide(W / 2, H * 0.62f + 17 * u + 4 * u + 7.5f * u, W * 0.6f, 15 * u, "MY FARM", ORANGE, () -> goScreen(Screen.FARM));
        } else if (screen == Screen.FARM || screen == Screen.MARKET || screen == Screen.SHOP) {
            hub.build(screen);
        } else if (screen == Screen.LEVELS) {
            addBack(m, topInset + m, hh, this::goTitle);
            addSpeaker(W - m - hh, topInset + m, hh);
            float areaTop = topInset + m + hh + 8 * u;
            float areaH = H - botInset - m - areaTop;
            float d = Math.min(W * 0.24f, areaH / 5f * 0.82f);
            float gx = (W - 3 * d) / 4f, gy = (areaH - 5 * d) / 5f;
            for (int i = 0; i < Level.COUNT; i++) {
                final int idx = i;
                Button b = new Button();
                float x = gx + (i % 3) * (d + gx);
                float y = areaTop + gy / 2 + (i / 3) * (d + gy);
                b.r.set(x, y, x + d, y + d);
                b.circle = true;
                b.text = String.valueOf(i + 1);
                b.locked = i >= progress.unlocked;
                b.color = i == progress.unlocked - 1 ? ORANGE : GREEN;
                b.stars = b.locked ? -1 : progress.bestStars[i];
                if (!b.locked) b.act = () -> startLevel(idx);
                buttons.add(b);
            }
        } else if (phase == Phase.TIP) {
            addWide(tipBox.centerX(), tipBox.top + tipBox.height() * 0.9f, tipBox.width() * 0.6f, tipBox.height() * 0.11f, "GOT IT!", GREEN, () -> {
                progress.tipShown(tipType);
                phase = Phase.IDLE;
                idleTime = 0;
                buildButtons();
            });
        } else if (phase == Phase.END) {
            float pw = overlay.width(), ph = overlay.height(), bh = ph * 0.11f;
            float cx = overlay.centerX();
            if (won) {
                final boolean hasNext = levelIndex + 1 < Level.COUNT;
                addWide(cx, overlay.top + ph * 0.72f, pw * 0.7f, bh, hasNext ? "NEXT LEVEL" : "ALL DONE!", GREEN,
                        hasNext ? () -> startLevel(levelIndex + 1) : this::goLevels);
                addWide(cx - pw * 0.19f, overlay.top + ph * 0.87f, pw * 0.34f, bh, "REPLAY", ORANGE, () -> startLevel(levelIndex));
                addWide(cx + pw * 0.19f, overlay.top + ph * 0.87f, pw * 0.34f, bh, "MENU", BLUE, this::goLevels);
            } else {
                addWide(cx, overlay.top + ph * 0.72f, pw * 0.7f, bh, "TRY AGAIN", GREEN, () -> startLevel(levelIndex));
                addWide(cx, overlay.top + ph * 0.87f, pw * 0.5f, bh, "MENU", BLUE, this::goLevels);
            }
        } else {
            addBack(m, topInset + m, hh, this::askQuit);
            if (screen == Screen.PLAY && phase != Phase.BAG) {
                for (int k = 0; k < Farm.BOOSTERS; k++) {
                    final int kind = k;
                    Button b = new Button();
                    b.r.set(boosterRect[k]);
                    b.circle = true;
                    b.icon = k;
                    b.color = FarmRenderer.BOOSTER_COLOR[k];
                    b.act = () -> useBooster(kind);
                    buttons.add(b);
                }
            }
        }
    }

    private void addSpeaker(float x, float y, float d) {
        Button b = new Button();
        b.r.set(x, y, x + d, y + d);
        b.circle = true;
        b.speaker = true;
        b.muted = !progress.soundOn;
        b.color = BLUE;
        b.act = () -> {
            progress.setSound(!progress.soundOn);
            sfx.enabled = progress.soundOn;
            sfx.click();
            buildButtons();
        };
        buttons.add(b);
    }

    void addBack(float x, float y, float d, Runnable act) {
        Button b = new Button();
        b.r.set(x, y, x + d, y + d);
        b.circle = true;
        b.back = true;
        b.color = ORANGE;
        b.act = act;
        buttons.add(b);
    }

    Button addWide(float cx, float cy, float w, float h, String text, int color, Runnable act) {
        Button b = new Button();
        b.r.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        b.text = text;
        b.color = color;
        b.act = act;
        buttons.add(b);
        return b;
    }

    /** An invisible tap target; the screen draws whatever it covers. */
    Button addHit(RectF area, Runnable act) {
        Button b = new Button();
        b.r.set(area);
        b.hidden = true;
        b.act = act;
        buttons.add(b);
        return b;
    }

    // ---------------------------------------------------------------- navigation

    boolean onBack() {
        if (screen == Screen.PLAY) {
            if (confirmQuit) {
                confirmQuit = false;
                buildButtons();
            } else {
                askQuit();
            }
            return true;
        }
        if (screen == Screen.LEVELS) {
            goTitle();
            return true;
        }
        if (screen == Screen.FARM) {
            if (hub.card >= 0) {
                hub.card = -1;
                buildButtons();
            } else {
                goTitle();
            }
            return true;
        }
        if (screen == Screen.MARKET || screen == Screen.SHOP) {
            goScreen(Screen.FARM);
            return true;
        }
        return false;
    }

    /** Back arrow / system back during a level: ask first, unless the level is already decided. */
    private void askQuit() {
        if (screen == Screen.PLAY && phase != Phase.END && phase != Phase.BAG) {
            confirmQuit = true;
            buildButtons();
        } else {
            goLevels();
        }
    }

    void release() {
        sfx.release();
    }

    /** Switches to a screen that has no board (farm, market, store). */
    void goScreen(Screen s) {
        screen = s;
        phase = Phase.IDLE;
        confirmQuit = false;
        hammerMode = false;
        hub.open();
        buildButtons();
    }

    void goTitle() {
        screen = Screen.TITLE;
        buildButtons();
    }

    void goLevels() {
        confirmQuit = false;
        hammerMode = false;
        screen = Screen.LEVELS;
        phase = Phase.IDLE;
        buildButtons();
    }

    private void startLevel(int idx) {
        levelIndex = idx;
        confirmQuit = false;
        level = Level.get(idx);
        board = new Board(COLS, ROWS, level.kinds, rnd);
        need = new int[Art.COUNT];
        for (int i = 0; i < level.goalKinds.length; i++) need[level.goalKinds[i]] = level.goalCounts[i];
        movesLeft = level.moves;
        coins = 0;
        landed = 0;
        dispCoins = 0;
        combo = 0;
        selected = null;
        hint = null;
        fx.clear();
        dying.clear();
        pendingTip = -1;
        harvest = new int[Art.CROPS];
        harvestStored = harvestOverflow = 0;
        hammerMode = false;
        placeStartingPowerUps();
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) board.g[r][c].y = r - ROWS - rnd.nextFloat() * 1.5f - (COLS - c) * 0.1f;
        }
        screen = Screen.PLAY;
        phase = Phase.FALL;
        idleTime = 0;
        buildButtons();
    }

    // ---------------------------------------------------------------- game logic

    private void update(float dt) {
        time += dt;
        float target = screen == Screen.PLAY ? landed : progress.totalCoins;
        if (Math.abs(target - dispCoins) < 1) dispCoins = target;
        else dispCoins += (target - dispCoins) * Math.min(1f, dt * 9f) + Math.signum(target - dispCoins) * dt * 20;

        fx.update(dt, u);
        int coinsIn = fx.takeLanded(), bagIn = fx.takeBagLanded();
        landed += coinsIn - bagIn;
        if (coinsIn > 0) sfx.coin();
        if (bagIn > 0) sfx.bagCoin(1f - Math.max(0, landed) / (float) bagTotal);
        for (Button b : buttons) if (b != downButton) b.pressed = false;
        if (screen != Screen.PLAY || confirmQuit) return;

        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Tile t = board.g[r][c];
                if (t == null) continue;
                t.squash = Math.max(0, t.squash - dt * 5f);
                if (!t.dying && t.scale < 1f) t.scale = Math.min(1f, t.scale + dt * 4f);
            }
        }

        switch (phase) {
            case IDLE:
                idleTime += dt;
                if (idleTime > 6f && hint == null) hint = board.findMove();
                break;
            case SWAP:
                if (moveTiles(dt, false)) {
                    List<Board.Run> runs = board.findRuns();
                    if (Art.isSpecial(swapA.type) || Art.isSpecial(swapB.type)) {
                        movesLeft--;
                        combo = 1;
                        activateSpecial(swapA, swapB);
                    } else if (runs.isEmpty()) {
                        board.swap(swapA.r, swapA.c, swapB.r, swapB.c);
                        sfx.badSwap();
                        phase = Phase.SWAPBACK;
                    } else {
                        movesLeft--;
                        combo = 1;
                        startClear(runs);
                    }
                }
                break;
            case SWAPBACK:
                if (moveTiles(dt, false)) {
                    phase = Phase.IDLE;
                    idleTime = 0;
                }
                break;
            case CLEAR:
                clearTimer += dt;
                for (Tile t : dying) {
                    float p = (clearTimer - t.dyDelay) / CLEAR_TIME;
                    if (p < 0) continue;
                    if (!t.popped) {
                        t.popped = true;
                        fx.sparkle(cx(t), cy(t), u, cell);
                    }
                    p = Math.min(1f, p);
                    float s = p < 0.3f ? 1f + 0.3f * (p / 0.3f) : 1.3f * (1f - (p - 0.3f) / 0.7f);
                    t.scale = Math.max(0f, s);
                }
                if (clearTimer >= clearDuration) finishClear();
                break;
            case FALL:
                if (moveTiles(dt, true)) {
                    List<Board.Run> runs = board.findRuns();
                    if (!runs.isEmpty()) {
                        combo++;
                        startClear(runs);
                    } else {
                        afterSettle();
                    }
                }
                break;
            case BAG:
                bagT += dt;
                if (bagT >= BAG_PAUSE + BAG_POP && fx.bagFlyers.isEmpty()) {
                    if (bagEndT < 0) bagEndT = bagT;
                    if (bagT - bagEndT > 0.6f) {
                        phase = Phase.END;
                        sfx.win();
                        overlayT = 0;
                        buildButtons();
                    }
                }
                break;
            case TIP:
                tipT += dt;
                break;
            case END:
                overlayT += dt;
                break;
        }
    }

    /** Moves tiles toward their grid cells. Returns true when everything has arrived. */
    private boolean moveTiles(float dt, boolean gravity) {
        boolean settled = true;
        float step = SWAP_SPEED * dt;
        for (int r = 0; r < ROWS; r++) {
            for (int c = 0; c < COLS; c++) {
                Tile t = board.g[r][c];
                if (t == null) continue;
                float dx = t.c - t.x;
                if (Math.abs(dx) <= step) t.x = t.c;
                else {
                    t.x += Math.signum(dx) * step;
                    settled = false;
                }
                if (gravity && t.y < t.r) {
                    t.vy += GRAVITY * dt;
                    t.y += t.vy * dt;
                    if (t.y >= t.r) {
                        t.y = t.r;
                        if (t.vy > 6f) t.squash = 1f;
                        t.vy = 0;
                    } else settled = false;
                } else {
                    float dy = t.r - t.y;
                    if (Math.abs(dy) <= step) t.y = t.r;
                    else {
                        t.y += Math.signum(dy) * step;
                        settled = false;
                    }
                }
            }
        }
        return settled;
    }

    private void startClear(List<Board.Run> runs) {
        phase = Phase.CLEAR;
        clearTimer = 0;
        clearDuration = CLEAR_TIME;
        fxTile = null;
        dying.clear();
        spawns = level.powerUps() ? Rules.findSpawns(runs, swapA, swapB, rnd) : new Rules.Spawns();
        sfx.match(combo);
        swapA = swapB = null;

        Set<Tile> seen = new HashSet<>();
        boolean first = true;
        for (Board.Run run : runs) {
            int n = run.tiles.size();
            int gain = Rules.runCoins(n, combo);
            coins += gain;
            float ax = 0, ay = 0;
            int share = gain / n;
            for (int i = 0; i < n; i++) {
                Tile t = run.tiles.get(i);
                ax += cx(t);
                ay += cy(t);
                fx.coin(cx(t), cy(t), rnd.nextFloat() * 0.12f, i == n - 1 ? gain - share * (n - 1) : share);
                if (seen.add(t)) {
                    harvest[t.type]++;
                    if (need[t.type] > 0) need[t.type]--;
                    if (!spawns.tiles.contains(t)) {
                        t.dying = true;
                        t.dyDelay = 0;
                        dying.add(t);
                    }
                }
            }
            addPop("+" + gain, ax / n, ay / n, cell * 0.55f, 0.9f, Color.WHITE);
            if (first && combo > 1) addPop("Combo x" + combo + "!", W / 2, by + cell * 1.2f, cell * 0.8f, 1.1f, 0xFFFFD43B);
            first = false;
        }
        if (!spawns.tiles.isEmpty()) addPop(spawns.types.get(0) == Art.GOLDEN_EGG || spawns.types.get(0) == Art.TRACTOR ? "Super Harvest!" : "Big Bang!",
                W / 2, by + cell * 2.4f, cell * 0.85f, 1.2f, 0xFFFF9B26);
    }

    private void addPop(String text, float x, float y, float size, float life, int color) {
        fx.pop(text, x, y, size, life, color, W * 0.88f);
    }

    /** One of the swapped tiles is a power-up: work out what it takes with it. */
    private void activateSpecial(Tile a, Tile b) {
        beginSpecialClear(Rules.specialClearSet(board, a, b), Rules.activator(a, b), null);
    }

    private void beginSpecialClear(Set<Tile> set, final Tile act, String banner) {
        phase = Phase.CLEAR;
        clearTimer = 0;
        dying.clear();
        spawns = new Rules.Spawns();
        fxTile = act;
        swapA = swapB = null;
        List<Tile> list = new ArrayList<>(set);
        list.sort((x, y) -> Integer.compare(Math.abs(x.r - act.r) + Math.abs(x.c - act.c), Math.abs(y.r - act.r) + Math.abs(y.c - act.c)));
        int gain = Rules.specialCoins(list.size(), combo);
        coins += gain;
        int share = gain / list.size();
        float maxDelay = 0;
        float ax = 0, ay = 0;
        for (int i = 0; i < list.size(); i++) {
            Tile t = list.get(i);
            float d;
            if (act.type == Art.GOLDEN_EGG) d = 0.03f * i;
            else if (act.type == Art.TRACTOR) d = 0.1f + 0.08f * t.c;
            else d = 0.07f * Math.max(Math.abs(t.r - act.r), Math.abs(t.c - act.c));
            t.dyDelay = d;
            t.dying = true;
            dying.add(t);
            if (t.type < Art.CROPS) {
                harvest[t.type]++;
                if (need[t.type] > 0) need[t.type]--;
            }
            maxDelay = Math.max(maxDelay, d);
            ax += cx(t);
            ay += cy(t);
            fx.coin(cx(t), cy(t), d + rnd.nextFloat() * 0.1f, i == list.size() - 1 ? gain - share * (list.size() - 1) : share);
        }
        act.dyDelay = maxDelay;
        sfx.powerUpPlayed(banner == null ? act.type : Art.TNT);
        clearDuration = CLEAR_TIME + maxDelay;
        String name = banner != null ? banner : act.type == Art.GOLDEN_EGG ? "Golden Harvest!" : act.type == Art.TRACTOR ? "Rainbow Roundup!"
                : act.type == Art.TNT ? "Boom!" : "Scarecrow Sweep!";
        addPop(name, W / 2, by + cell * 1.2f, cell * 0.85f, 1.3f, 0xFFFFD43B);
        addPop("+" + gain, ax / list.size(), ay / list.size(), cell * 0.65f, 1.0f, Color.WHITE);
    }

    private void finishClear() {
        for (Tile t : dying) board.g[t.r][t.c] = null;
        dying.clear();
        fxTile = null;
        for (int i = 0; i < spawns.tiles.size(); i++) {
            Tile t = spawns.tiles.get(i);
            t.type = spawns.types.get(i);
            if (!progress.tipSeen[t.type] && pendingTip < 0) pendingTip = t.type;
            sfx.powerUpMade();
            t.scale = 0f;
            fx.sparkle(cx(t), cy(t), u, cell);
            fx.sparkle(cx(t), cy(t), u, cell);
        }
        spawns = new Rules.Spawns();
        board.collapse();
        phase = Phase.FALL;
    }

    private void afterSettle() {
        if (Rules.goalsDone(need)) {
            endLevel(true);
        } else if (movesLeft <= 0) {
            endLevel(false);
        } else {
            if (board.findMove() == null) {
                board.reshuffle();
                for (Tile[] row : board.g) for (Tile t : row) t.scale = 0f;
                sfx.swap();
                addPop("Shuffle!", W / 2, by + cell * 3.5f, cell * 0.9f, 1.2f, 0xFF9BE15D);
            }
            phase = Phase.IDLE;
            idleTime = 0;
            hint = null;
            if (pendingTip >= 0) {
                tipType = pendingTip;
                pendingTip = -1;
                tipT = 0;
                phase = Phase.TIP;
                buildButtons();
            }
        }
    }

    private void startBag() {
        phase = Phase.BAG;
        bagT = 0;
        bagEndT = -1;
        bagTotal = Math.max(1, coins);
        fx.flyers.clear();
        landed = coins;
        fx.bagFlyers.clear();
        int n = Math.min(26, Math.max(8, coins / 20));
        int share = coins / n;
        for (int i = 0; i < n; i++) {
            fx.bagCoin(BAG_PAUSE + BAG_POP + i * 0.035f, i == n - 1 ? coins - share * (n - 1) : share);
        }
    }

    private void endLevel(boolean win) {
        phase = Phase.END;
        overlayT = 0;
        won = win;
        if (!win) sfx.lose();
        bonus = 0;
        stars = 0;
        if (win) {
            bonus = movesLeft * Rules.MOVE_BONUS;
            coins += bonus;
            landed += bonus;
            stars = Rules.stars(movesLeft, level.moves);
        }
        progress.levelFinished(levelIndex, stars, coins);
        int[] banked = progress.addHarvest(harvest, Farm.today());
        harvestStored = banked[0];
        harvestOverflow = banked[1];
        if (win) startBag();
        buildButtons();
    }

    /** The Scarecrow Plot upgrade scatters power-ups on the board at the start of a level. */
    private void placeStartingPowerUps() {
        if (!level.powerUps()) return;
        int n = Farm.startingPowerUps(progress.building[Farm.PLOT]);
        boolean[][] used = new boolean[ROWS][COLS];
        while (n > 0) {
            int r = rnd.nextInt(ROWS), c = rnd.nextInt(COLS);
            if (used[r][c]) continue;
            used[r][c] = true;
            int type = Art.CROPS + rnd.nextInt(Art.COUNT - Art.CROPS);
            board.g[r][c].type = type;
            if (!progress.tipSeen[type] && pendingTip < 0) pendingTip = type;
            n--;
        }
    }

    private void boosterPop(String text, int color) {
        addPop(text, W / 2, by + cell * 3.5f, cell * 0.8f, 1.3f, color);
    }

    /** An in-level booster button. Boosters are bought on the spot with coins when none are owned. */
    private void useBooster(int k) {
        if (phase != Phase.IDLE || confirmQuit) return;
        if (k == Farm.HAMMER && hammerMode) {
            hammerMode = false;
            return;
        }
        hammerMode = false;
        if (progress.boosters[k] == 0 && progress.totalCoins < Farm.BOOSTER_PRICE[k]) {
            sfx.badSwap();
            boosterPop("Need " + Farm.BOOSTER_PRICE[k] + " coins", 0xFFFF7A66);
            return;
        }
        selected = null;
        hint = null;
        idleTime = 0;
        if (k == Farm.HAMMER) {
            hammerMode = true;
            boosterPop("Tap a tile to smash it!", 0xFFFFD43B);
            return;
        }
        progress.takeBooster(k);
        sfx.powerUpMade();
        if (k == Farm.MOVES) {
            movesLeft += Farm.EXTRA_MOVES;
            boosterPop("+" + Farm.EXTRA_MOVES + " moves!", 0xFF9BE15D);
        } else {
            board.reshuffle();
            for (Tile[] row : board.g) for (Tile t : row) t.scale = 0f;
            boosterPop("Shuffle!", 0xFF9BE15D);
        }
    }

    private void smash(Tile t) {
        hammerMode = false;
        if (!progress.takeBooster(Farm.HAMMER)) {
            sfx.badSwap();
            boosterPop("Need " + Farm.BOOSTER_PRICE[Farm.HAMMER] + " coins", 0xFFFF7A66);
            return;
        }
        selected = null;
        hint = null;
        combo = 1;
        Set<Tile> one = new java.util.LinkedHashSet<>();
        one.add(t);
        beginSpecialClear(one, t, "Smash!");
    }

    private void attemptSwap(Tile a, Tile b) {
        if (phase != Phase.IDLE || Math.abs(a.r - b.r) + Math.abs(a.c - b.c) != 1) return;
        board.swap(a.r, a.c, b.r, b.c);
        sfx.swap();
        swapA = a;
        swapB = b;
        selected = null;
        hint = null;
        phase = Phase.SWAP;
    }

    float cx(Tile t) {
        return bx + (t.x + 0.5f) * cell;
    }

    float cy(Tile t) {
        return by + (t.y + 0.5f) * cell;
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        float x = e.getX(), y = e.getY();
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                downButton = hitButton(x, y);
                downTile = null;
                dragged = false;
                downX = x;
                downY = y;
                if (downButton != null) {
                    downButton.pressed = true;
                } else if (screen == Screen.PLAY && phase == Phase.IDLE) {
                    downTile = tileAt(x, y);
                    idleTime = 0;
                    hint = null;
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                if (downButton != null) {
                    downButton.pressed = downButton.r.contains(x, y);
                } else if (downTile != null && !dragged && !hammerMode && phase == Phase.IDLE) {
                    float dx = x - downX, dy = y - downY;
                    if (Math.max(Math.abs(dx), Math.abs(dy)) > cell * 0.4f) {
                        int r = downTile.r, c = downTile.c;
                        if (Math.abs(dx) > Math.abs(dy)) c += dx > 0 ? 1 : -1;
                        else r += dy > 0 ? 1 : -1;
                        dragged = true;
                        if (r >= 0 && r < ROWS && c >= 0 && c < COLS) attemptSwap(downTile, board.g[r][c]);
                    }
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (downButton != null) {
                    Button b = downButton;
                    downButton = null;
                    b.pressed = false;
                    if (b.r.contains(x, y) && b.act != null) {
                        if (!b.speaker) sfx.click();
                        b.act.run();
                    }
                } else if (downTile != null && !dragged && phase == Phase.IDLE && tileAt(x, y) == downTile) {
                    tap(downTile);
                } else if (hammerMode && downTile == null && phase == Phase.IDLE) {
                    hammerMode = false;
                }
                downTile = null;
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (downButton != null) downButton.pressed = false;
                downButton = null;
                downTile = null;
                return true;
        }
        return super.onTouchEvent(e);
    }

    private void tap(Tile t) {
        if (hammerMode) {
            smash(t);
            return;
        }
        if (selected == null) selected = t;
        else if (selected == t) selected = null;
        else if (Math.abs(selected.r - t.r) + Math.abs(selected.c - t.c) == 1) attemptSwap(selected, t);
        else selected = t;
    }

    private Button hitButton(float x, float y) {
        for (int i = buttons.size() - 1; i >= 0; i--) {
            Button b = buttons.get(i);
            if (b.r.contains(x, y)) return b;
        }
        return null;
    }

    private Tile tileAt(float x, float y) {
        int c = (int) Math.floor((x - bx) / cell), r = (int) Math.floor((y - by) / cell);
        if (r < 0 || c < 0 || r >= ROWS || c >= COLS) return null;
        return board.g[r][c];
    }

    // ---------------------------------------------------------------- drawing

    @Override
    protected void onDraw(Canvas c) {
        long now = System.nanoTime();
        float dt = last == 0 ? 0.016f : Math.min(0.05f, (now - last) / 1e9f);
        last = now;
        if (W > 0) {
            update(dt);
            renderer.draw(c);
        }
        postInvalidateOnAnimation();
    }

}
