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
    enum Screen {TITLE, BOOK, PLAY, RESULT}

    enum Phase {IDLE, SWAP, SWAPBACK, CLEAR, FALL, DONE, TIP}

    static final int COLS = Rules.COLS, ROWS = Rules.ROWS;
    private static final float SWAP_SPEED = 9f, GRAVITY = 45f, CLEAR_TIME = 0.28f;

    static final class Button {
        final RectF r = new RectF();
        String text = "";
        int color = GREEN;
        boolean circle, locked, pressed, back, speaker, muted;
        /** Level index when this is a card on the recipe book, else -1. */
        int card = -1;
        /** Hit area only: the screen draws it itself. */
        boolean hidden;
        Runnable act;
    }

    final Gfx gfx = new Gfx();
    final Random rnd = new Random();
    final Effects fx = new Effects(rnd, gfx);
    final Renderer renderer;
    final Progress progress;
    final Sfx sfx;
    boolean confirmQuit;

    final List<Button> buttons = new ArrayList<>();
    final List<Tile> dying = new ArrayList<>();
    Rules.Spawns spawns = new Rules.Spawns();
    Tile fxTile;
    float clearDuration = CLEAR_TIME;
    float doneT, tipT;
    int pendingTip = -1, tipType;

    // layout
    float W, H, topInset, botInset, u;
    float bx, by, cell;
    /** The order card under the board: the dish and what it still needs. */
    final RectF kitchen = new RectF();
    final RectF boardPanel = new RectF(), tipBox = new RectF(), confirmBox = new RectF(), resultBox = new RectF();
    /** One card per dish on the recipe book screen, which is also the level menu. */
    final RectF[] bookCard = new RectF[Level.COUNT];
    int bookPressed = -1;

    // play state
    Screen screen = Screen.TITLE;
    Phase phase = Phase.IDLE;
    Level level;
    int levelIndex;
    Board board;
    int[] need = new int[Art.COUNT];
    int movesLeft, combo, stars;
    boolean won, firstCook;
    float idleTime, clearTimer, time;
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
        for (int i = 0; i < bookCard.length; i++) bookCard[i] = new RectF();
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
        float top = hudTop + hh + m * 0.8f;
        float pad = 2 * u, minStrip = 36 * u, maxStrip = 44 * u;
        float bottom = H - botInset - m;
        float availH = bottom - top - minStrip - 3 * u;
        cell = Math.min((W - 2 * m - 2 * pad) / COLS, (availH - 2 * pad) / ROWS);
        float bw = cell * COLS, bh = cell * ROWS;
        bx = (W - bw) / 2;
        by = top + pad;
        boardPanel.set(bx - pad, by - pad, bx + bw + pad, by + bh + pad);
        // the order card takes everything under the board
        float stripTop = boardPanel.bottom + 3 * u;
        kitchen.set(m, stripTop, W - m, Math.min(bottom, stripTop + maxStrip));
        resultBox.set(W * 0.07f, topInset + m + hh + 8 * u, W * 0.93f, H - botInset - m - 13 * u - 4 * u);
        tipBox.set(W * 0.1f, H * 0.22f, W * 0.9f, H * 0.78f);
        confirmBox.set(W * 0.1f, H * 0.32f, W * 0.9f, H * 0.68f);
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
                goBook();
            });
        } else if (screen == Screen.TITLE) {
            addSpeaker(m, topInset + m, hh);
            addWide(W / 2, H * 0.66f, W * 0.6f, 17 * u, "COOK", GREEN, this::goBook);
        } else if (screen == Screen.RESULT) {
            addBack(m, topInset + m, hh, this::goBook);
            buildResultButtons();
        } else if (screen == Screen.BOOK) {
            addBack(m, topInset + m, hh, this::goTitle);
            addSpeaker(W - m - hh, topInset + m, hh);
            float areaTop = topInset + m + hh + 4 * u, gap = 2.5f * u;
            float areaH = H - botInset - m - areaTop;
            int rows = (Level.COUNT + 2) / 3;
            float cw = (W - 2 * m - 2 * gap) / 3f, ch = Math.min(areaH / 5f, (areaH - (rows - 1) * gap) / rows);
            for (int i = 0; i < Level.COUNT; i++) {
                final int idx = i;
                float x = m + (i % 3) * (cw + gap), y = areaTop + (i / 3) * (ch + gap);
                bookCard[i].set(x, y, x + cw, y + ch);
                if (i < progress.unlocked) {
                    Button b = addHit(bookCard[i], () -> startLevel(idx));
                    b.card = idx;
                }
            }
        } else if (phase == Phase.TIP) {
            addWide(tipBox.centerX(), tipBox.top + tipBox.height() * 0.9f, tipBox.width() * 0.6f, tipBox.height() * 0.11f, "GOT IT!", GREEN, () -> {
                progress.tipShown(tipType);
                phase = Phase.IDLE;
                idleTime = 0;
                buildButtons();
            });
        } else {
            addBack(m, topInset + m, hh, this::askQuit);
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
        if (screen == Screen.BOOK) {
            goTitle();
            return true;
        }
        if (screen == Screen.RESULT) {
            goBook();
            return true;
        }
        return false;
    }

    /** Back arrow / system back during a level: ask first, unless the level is already decided. */
    private void askQuit() {
        if (screen == Screen.PLAY && phase != Phase.DONE) {
            confirmQuit = true;
            buildButtons();
        } else {
            goBook();
        }
    }

    void release() {
        sfx.release();
    }

    /** The result of the level that just ended: the finished dish, or what was still missing. */
    private void goResult() {
        screen = Screen.RESULT;
        phase = Phase.IDLE;
        buildButtons();
    }

    private void buildResultButtons() {
        float m = 3 * u, gap = 2.5f * u, h = 13 * u;
        float total = W - 2 * m, cy = H - botInset - m - h / 2;
        if (won) {
            boolean hasNext = levelIndex + 1 < Level.COUNT;
            float usable = total - 2 * gap, wR = usable * 0.30f, wN = usable * 0.40f, wM = usable * 0.30f;
            addWide(m + wR / 2, cy, wR, h, "REPLAY", ORANGE, () -> startLevel(levelIndex));
            addWide(m + wR + gap + wN / 2, cy, wN, h, hasNext ? "NEXT" : "DONE", GREEN,
                    hasNext ? () -> startLevel(levelIndex + 1) : this::goBook);
            addWide(W - m - wM / 2, cy, wM, h, "BOOK", BLUE, this::goBook);
        } else {
            float usable = total - gap, wT = usable * 0.6f, wM = usable * 0.4f;
            addWide(m + wT / 2, cy, wT, h, "TRY AGAIN", GREEN, () -> startLevel(levelIndex));
            addWide(W - m - wM / 2, cy, wM, h, "BOOK", BLUE, this::goBook);
        }
    }

    void goTitle() {
        screen = Screen.TITLE;
        buildButtons();
    }

    void goBook() {
        confirmQuit = false;
        screen = Screen.BOOK;
        phase = Phase.IDLE;
        buildButtons();
    }

    void startLevel(int idx) {
        levelIndex = idx;
        confirmQuit = false;
        level = Level.get(idx);
        board = new Board(COLS, ROWS, level.kinds, rnd);
        need = new int[Art.COUNT];
        for (int i = 0; i < level.goalKinds.length; i++) need[level.goalKinds[i]] = level.goalCounts[i];
        movesLeft = level.moves;
        combo = 0;
        selected = null;
        hint = null;
        fx.clear();
        renderer.cook.clear();
        dying.clear();
        pendingTip = -1;
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
        fx.update(dt, u);
        if (renderer.cook.update(dt) > 0) sfx.coin();
        bookPressed = -1;
        for (Button b : buttons) {
            if (b != downButton) b.pressed = false;
            else if (b.pressed && b.card >= 0) bookPressed = b.card;
        }
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
            case DONE:
                // the dish is ready: a moment to enjoy it, then the result
                doneT += dt;
                if (((int) (doneT * 14)) != ((int) ((doneT - dt) * 14))) {
                    fx.sparkle(kitchen.left + kitchen.width() * 0.2f + rnd.nextFloat() * kitchen.width() * 0.6f, kitchen.centerY() + (rnd.nextFloat() - 0.5f) * kitchen.height() * 0.6f, u, cell);
                }
                if (doneT > 1.5f) goResult();
                break;
            case TIP:
                tipT += dt;
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
            for (int i = 0; i < n; i++) {
                Tile t = run.tiles.get(i);
                if (seen.add(t)) {
                    if (need[t.type] > 0) {
                        need[t.type]--;
                        renderer.cook.launch(t.type, cx(t), cy(t), rnd.nextFloat() * 0.12f);
                    }
                    if (!spawns.tiles.contains(t)) {
                        t.dying = true;
                        t.dyDelay = 0;
                        dying.add(t);
                    }
                }
            }
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
        float maxDelay = 0;
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
                if (need[t.type] > 0) {
                    need[t.type]--;
                    renderer.cook.launch(t.type, cx(t), cy(t), d);
                }
            }
            maxDelay = Math.max(maxDelay, d);
        }
        act.dyDelay = maxDelay;
        sfx.powerUpPlayed(banner == null ? act.type : Art.TNT);
        clearDuration = CLEAR_TIME + maxDelay;
        String name = banner != null ? banner : act.type == Art.GOLDEN_EGG ? "Golden Harvest!" : act.type == Art.TRACTOR ? "Rainbow Roundup!"
                : act.type == Art.TNT ? "Boom!" : "Scarecrow Sweep!";
        addPop(name, W / 2, by + cell * 1.2f, cell * 0.85f, 1.3f, 0xFFFFD43B);
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
        if (Rules.goalsDone(need)) endLevel(true);
        else if (movesLeft <= 0) endLevel(false);
        else keepPlaying();
    }

    private void keepPlaying() {
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

    private void endLevel(boolean win) {
        won = win;
        stars = win ? Rules.stars(movesLeft, level.moves) : 0;
        firstCook = win && progress.bestStars[levelIndex] == 0;
        progress.levelFinished(levelIndex, stars);
        if (win) {
            sfx.win();
            phase = Phase.DONE;
            doneT = 0;
            addPop(level.recipe.name + " is ready!", W / 2, by + cell * 3f, cell * 0.8f, 1.4f, 0xFFFFD43B);
            buildButtons();
        } else {
            sfx.lose();
            goResult();
        }
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
                } else if (downTile != null && !dragged && phase == Phase.IDLE) {
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
