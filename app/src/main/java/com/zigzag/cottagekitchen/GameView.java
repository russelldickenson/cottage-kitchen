package com.zigzag.cottagekitchen;

import static com.zigzag.cottagekitchen.Gfx.BLUE;
import static com.zigzag.cottagekitchen.Gfx.GREEN;
import static com.zigzag.cottagekitchen.Gfx.ORANGE;

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
    enum Screen {TITLE, BOOK, RECIPE, PLAY, RESULT}

    /** Room for future recipes with more ingredient levels than any dish has today. */
    static final int MAX_STEPS = 12;

    static final int ICON_NONE = 0, ICON_CHEF = 1, ICON_NEXT = 2, ICON_REPLAY = 3;
    /** tipType value for the one-time "how to play" card, before the crate tip. */
    static final int TIP_INTRO = -1;

    enum Phase {IDLE, SWAP, SWAPBACK, CLEAR, FALL, DONE, TIP}

    static final int COLS = Rules.COLS, ROWS = Rules.ROWS;
    private static final float SWAP_SPEED = 9f, GRAVITY = 45f, CLEAR_TIME = 0.28f;

    static final class Button {
        final RectF r = new RectF();
        String text = "";
        int color = GREEN;
        boolean circle, locked, pressed, back, speaker, muted;
        /** A small picture beside the label: one of the ICON_ values. */
        int icon;
        /** Level index when this is a card on the recipe book, else -1. */
        int card = -1;
        /** Step number when this is an ingredient node on the recipe screen, else -1. */
        int recipeStep = -1;
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
    float doneT, tipT;
    int pendingTip = -1, tipType;
    boolean pendingIntro;

    // layout
    float W, H, topInset, botInset, u;
    float bx, by, cell;
    /** Extra top offset on the recipe screen, so the dish and its caption clear the bunting. */
    float recipeShift;
    /** The order card under the board: the dish and what it still needs. */
    final RectF kitchen = new RectF();
    final RectF boardPanel = new RectF(), tipBox = new RectF(), confirmBox = new RectF(), resultBox = new RectF();
    /** One card per dish on the recipe book screen. */
    final RectF[] bookCard = new RectF[Recipes.ALL.length];
    int bookPressed = -1;
    /** The recipe screen: the dish hero and one node per ingredient level. */
    final RectF recipeDish = new RectF();
    final RectF[] recipeNode = new RectF[MAX_STEPS];
    int recipeStepCount, recipe, recipePressed = -1;
    /** "Order up!" plays on the recipe screen when a dish's last ingredient is collected. */
    boolean recipeCelebrate, showCelebration;
    float celebrateT;

    // play state
    Screen screen = Screen.TITLE;
    Phase phase = Phase.IDLE;
    Level level;
    int levelIndex;
    Board board;
    int[] need = new int[Art.COUNT];
    int movesLeft, combo, stars, score;
    /** The first five-in-a-row or L/T clear in a level refunds one move. */
    boolean batchBonusUsed;
    boolean recordScore;
    boolean won;
    float idleTime, clearTimer, time;
    /** Seconds since the level began: the "collect this" banner shows at the start. */
    float introT;
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
        for (int i = 0; i < recipeNode.length; i++) recipeNode[i] = new RectF();
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
        layoutRecipe();
    }

    /** Lays out the recipe screen: a dish hero and the dish's ingredient steps in wrapped rows. */
    private void layoutRecipe() {
        float m = 3 * u, hh = 13 * u;
        // nudge the dish, its name and the ingredient count below the bunting across the top
        recipeShift = 10 * u;
        float panelT = topInset + m + hh + 3 * u + recipeShift;
        float panelB = H - botInset - m - 16 * u;
        float availW = W - 2 * m, ph = Math.max(10 * u, panelB - panelT);
        float dishSize = Math.min(availW * 0.42f, ph * 0.34f);
        recipeDish.set(W / 2 - dishSize / 2, panelT, W / 2 + dishSize / 2, panelT + dishSize);

        recipeStepCount = Recipes.ALL[recipe].ingredients.length;
        float stepsTop = panelT + dishSize + 4 * u;
        float stepsH = Math.max(4 * u, panelB - stepsTop);
        int perRow = Math.min(recipeStepCount, 5);
        int rows = (recipeStepCount + perRow - 1) / perRow;
        float gap = 2 * u;
        float cellW = (availW - (perRow - 1) * gap) / perRow;
        float cellH = Math.min((stepsH - (rows - 1) * gap) / rows, cellW * 1.3f);
        float rowsH = rows * cellH + (rows - 1) * gap;
        float gridTop = stepsTop + Math.max(0, (stepsH - rowsH) * 0.25f);
        for (RectF r : recipeNode) r.set(0, 0, 0, 0);
        for (int i = 0; i < recipeStepCount; i++) {
            int row = i / perRow, col = i % perRow;
            int inRow = Math.min(perRow, recipeStepCount - row * perRow);
            float rowW = inRow * cellW + (inRow - 1) * gap;
            float l = W / 2 - rowW / 2 + col * (cellW + gap);
            float t = gridTop + row * (cellH + gap);
            recipeNode[i].set(l, t, l + cellW, t + cellH);
        }
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
            addWide(W / 2, H * 0.66f, W * 0.6f, 17 * u, "Let's cook", GREEN, this::goBook).icon = ICON_CHEF;
        } else if (screen == Screen.RESULT) {
            addBack(m, topInset + m, hh, this::goBook);
            buildResultButtons();
        } else if (screen == Screen.BOOK) {
            addBack(m, topInset + m, hh, this::goTitle);
            addSpeaker(W - m - hh, topInset + m, hh);
            float areaTop = topInset + m + hh + 4 * u, gap = 2.5f * u;
            float areaH = H - botInset - m - areaTop;
            int rows = (Recipes.ALL.length + 2) / 3;
            float cw = (W - 2 * m - 2 * gap) / 3f, ch = Math.min(areaH / 5f, (areaH - (rows - 1) * gap) / rows);
            for (int i = 0; i < Recipes.ALL.length; i++) {
                final int dish = i;
                float x = m + (i % 3) * (cw + gap), y = areaTop + (i / 3) * (ch + gap);
                bookCard[i].set(x, y, x + cw, y + ch);
                if (progress.recipeUnlocked(i)) {
                    Button b = addHit(bookCard[i], () -> openRecipe(dish));
                    b.card = dish;
                }
            }
        } else if (screen == Screen.RECIPE) {
            addBack(m, topInset + m, hh, this::goBook);
            addSpeaker(W - m - hh, topInset + m, hh);
            int next = progress.nextStep(recipe);
            for (int i = 0; i < recipeStepCount; i++) {
                if (i > next) continue;
                final int step = i;
                Button b = addHit(recipeNode[i], () -> startLevel(Recipes.firstLevel(recipe) + step));
                b.recipeStep = step;
            }
            boolean cooked = progress.recipeDone(recipe);
            String label = cooked ? "COOK AGAIN" : next > 0 ? "Next" : "START COOKING";
            Button b = addWide(W / 2, H - botInset - m - 8 * u, W * 0.6f, 13 * u, label, GREEN,
                    () -> startLevel(Recipes.firstLevel(recipe) + (cooked ? 0 : next)));
            b.icon = cooked ? ICON_REPLAY : next > 0 ? ICON_NEXT : ICON_CHEF;
        } else if (phase == Phase.TIP) {
            addWide(tipBox.centerX(), tipBox.top + tipBox.height() * 0.9f, tipBox.width() * 0.6f, tipBox.height() * 0.11f, "GOT IT!", GREEN, () -> {
                if (tipType == TIP_INTRO) progress.introShown();
                else progress.tipShown(tipType);
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
        if (screen == Screen.RECIPE) {
            goBook();
            return true;
        }
        if (screen == Screen.RESULT) {
            goRecipe();
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
            goRecipe();
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
            boolean last = level.lastStep();
            boolean hasNext = levelIndex + 1 < Level.COUNT;
            float usable = total - 2 * gap, wP = usable * 0.30f, wR = usable * 0.34f, wN = usable * 0.36f;
            addWide(m + wP / 2, cy, wP, h, "Recipe", BLUE, this::goRecipe);
            addWide(m + wP + gap + wR / 2, cy, wR, h, "Replay", ORANGE, () -> startLevel(levelIndex)).icon = ICON_REPLAY;
            Button next = addWide(m + wP + gap + wR + gap + wN / 2, cy, wN, h,
                    last ? (hasNext ? "Next recipe" : "DONE") : "Next", GREEN,
                    last ? () -> {
                        if (hasNext) openRecipe(Recipes.dishOf(levelIndex + 1));
                        else goBook();
                    } : () -> startLevel(levelIndex + 1));
            if (!last || hasNext) next.icon = ICON_NEXT;
        } else {
            float usable = total - 2 * gap, wB = usable * 0.28f, wT = usable * 0.42f, wP = usable * 0.30f;
            addWide(m + wB / 2, cy, wB, h, "Book", BLUE, this::goBook);
            addWide(m + wB + gap + wT / 2, cy, wT, h, "TRY AGAIN", GREEN, () -> startLevel(levelIndex)).icon = ICON_REPLAY;
            addWide(W - m - wP / 2, cy, wP, h, "Recipe", ORANGE, this::goRecipe);
        }
    }

    void goTitle() {
        screen = Screen.TITLE;
        buildButtons();
    }

    void goBook() {
        confirmQuit = false;
        showCelebration = false;
        screen = Screen.BOOK;
        phase = Phase.IDLE;
        buildButtons();
    }

    /** Opens a dish from the book: its ingredient levels are the group inside it. */
    void openRecipe(int dish) {
        recipe = dish;
        showCelebration = false;
        screen = Screen.RECIPE;
        phase = Phase.IDLE;
        layoutRecipe();
        buildButtons();
    }

    /** Returns to the dish just played, showing the celebration once when it was completed. */
    void goRecipe() {
        recipe = level.dish;
        showCelebration = recipeCelebrate;
        recipeCelebrate = false;
        celebrateT = 0;
        screen = Screen.RECIPE;
        phase = Phase.IDLE;
        layoutRecipe();
        buildButtons();
    }

    void startLevel(int idx) {
        levelIndex = idx;
        confirmQuit = false;
        showCelebration = false;
        recipeCelebrate = false;
        level = Level.get(idx);
        recipe = level.dish;
        board = new Board(COLS, ROWS, level.kinds, rnd);
        board.addCrates(level.crates(), rnd);
        need = new int[Art.COUNT];
        for (int i = 0; i < level.goalKinds.length; i++) need[level.goalKinds[i]] = level.goalCounts[i];
        movesLeft = level.moves;
        combo = 0;
        score = 0;
        batchBonusUsed = false;
        recordScore = false;
        introT = 0;
        selected = null;
        hint = null;
        fx.clear();
        renderer.cook.clear();
        dying.clear();
        pendingTip = level.crates() > 0 && !progress.tipSeen[Art.CRATE] ? Art.CRATE : -1;
        pendingIntro = idx == 0 && progress.unlocked == 1 && !progress.introSeen;
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
        recipePressed = -1;
        for (Button b : buttons) {
            if (b != downButton) b.pressed = false;
            else if (b.pressed && b.card >= 0) bookPressed = b.card;
            else if (b.pressed && b.recipeStep >= 0) recipePressed = b.recipeStep;
        }
        if (showCelebration) {
            celebrateT += dt;
            if (celebrateT > 2.4f) showCelebration = false;
        }
        if (screen != Screen.PLAY || confirmQuit) return;
        introT += dt;

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
                    if (runs.isEmpty()) {
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
                if (clearTimer >= CLEAR_TIME) finishClear();
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
        dying.clear();
        sfx.match(combo);
        if (!batchBonusUsed && Rules.bigMatch(runs)) {
            batchBonusUsed = true;
            movesLeft++;
            sfx.coin();
            addPop("Batch cooked! +1 move", W / 2, by + cell * 2.4f, cell * 0.8f, 1.2f, 0xFFFFD43B);
        }
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
                    t.dying = true;
                    t.dyDelay = 0;
                    dying.add(t);
                }
            }
            if (first && combo > 1) addPop("Combo x" + combo + "!", W / 2, by + cell * 1.2f, cell * 0.8f, 1.1f, 0xFFFFD43B);
            first = false;
        }
        score += seen.size() * 10 * combo;
        breakCrates(seen);
    }

    /** Clears next to a crate (or catching it) hit it: it cracks, then breaks and counts toward the level goal. */
    private void breakCrates(java.util.Collection<Tile> cleared) {
        List<Tile> damaged = new ArrayList<>();
        for (Tile t : Rules.hitCrates(board, cleared, damaged)) {
            t.dyDelay = 0;
            t.dying = true;
            dying.add(t);
            score += 50;
            if (need[Art.CRATE] > 0) need[Art.CRATE]--;
        }
        for (Tile t : damaged) {
            t.squash = 1f;
            fx.sparkle(cx(t), cy(t), u, cell);
        }
    }

    private void addPop(String text, float x, float y, float size, float life, int color) {
        fx.pop(text, x, y, size, life, color, W * 0.88f);
    }

    private void finishClear() {
        for (Tile t : dying) board.g[t.r][t.c] = null;
        dying.clear();
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
        if (pendingIntro) {
            pendingIntro = false;
            tipType = TIP_INTRO;
            tipT = 0;
            phase = Phase.TIP;
            buildButtons();
        } else if (pendingTip >= 0) {
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
        recordScore = score > progress.bestScore[levelIndex];
        progress.levelFinished(levelIndex, stars, score);
        recipeCelebrate = win && level.lastStep();
        if (win) {
            sfx.win();
            phase = Phase.DONE;
            doneT = 0;
            addPop(Recipes.INGREDIENT[level.ingredient()] + " collected!", W / 2, by + cell * 3f, cell * 0.8f, 1.4f, 0xFFFFD43B);
            buildButtons();
        } else {
            sfx.lose();
            goResult();
        }
    }

    private void attemptSwap(Tile a, Tile b) {
        if (phase != Phase.IDLE || Math.abs(a.r - b.r) + Math.abs(a.c - b.c) != 1) return;
        if (Art.isCrate(a.type) || Art.isCrate(b.type)) {
            selected = null;
            sfx.badSwap();
            return;
        }
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
        if (Art.isCrate(t.type)) {
            selected = null;
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
