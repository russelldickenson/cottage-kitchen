package com.farmharvest.game;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowInsets;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Renders every screen (title, level select, play) and drives the match-3 state machine. */
public class GameView extends View {
    private enum Screen {TITLE, LEVELS, PLAY}

    private enum Phase {IDLE, SWAP, SWAPBACK, CLEAR, FALL, BAG, END}

    private static final int COLS = 7, ROWS = 8;
    private static final float SWAP_SPEED = 9f, GRAVITY = 45f, CLEAR_TIME = 0.28f;

    private static final int GREEN = 0xFF5BC236, ORANGE = 0xFFFF9B26, BLUE = 0xFF3FA9F5, PANEL = 0xFF6B3F17;
    private static final int CREAM = 0xFFFFF4D2, WOOD = 0xFF8B5A2B;

    private static final class Button {
        final RectF r = new RectF();
        String text = "";
        int color = GREEN;
        boolean circle, locked, pressed, back;
        int stars = -1;
        Runnable act;
    }

    private static final class Particle {
        float x, y, vx, vy, life, max, size;
        int color;
    }

    private static final class Flyer {
        float sx, sy, delay, t;
        int value;
    }

    private static final class Pop {
        float x, y, life, max, size;
        String text;
        int color;
    }

    private final Gfx gfx = new Gfx();
    private final Random rnd = new Random();
    private final Bitmap[] art = new Bitmap[Art.COUNT];
    private final SharedPreferences prefs;
    private final Paint bmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint sky = new Paint();
    private final Paint plain = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path hill1 = new Path(), hill2 = new Path();
    private final Rect src = new Rect();
    private final RectF dst = new RectF();

    private final List<Button> buttons = new ArrayList<>();
    private final List<Particle> particles = new ArrayList<>();
    private final List<Flyer> flyers = new ArrayList<>();
    private final List<Pop> pops = new ArrayList<>();
    private final List<Tile> dying = new ArrayList<>();
    private final List<Flyer> bagFlyers = new ArrayList<>();
    private static final float BAG_PAUSE = 1f, BAG_POP = 0.35f, BAG_FLY = 0.5f;
    private float bagT, bagBump, bagEndT;
    private int bagTotal;

    // layout
    private float W, H, topInset, botInset, u;
    private float bx, by, cell, coinX, coinY, coinR;
    private final RectF boardPanel = new RectF(), goalPanel = new RectF(), overlay = new RectF();

    // persistent progress
    private int unlocked, totalCoins;
    private final int[] bestStars = new int[Level.COUNT];

    // play state
    private Screen screen = Screen.TITLE;
    private Phase phase = Phase.IDLE;
    private Level level;
    private int levelIndex;
    private Board board;
    private int[] need = new int[Art.COUNT];
    private int movesLeft, coins, landed, combo, stars, bonus;
    private boolean won;
    private float dispCoins, bump, idleTime, clearTimer, overlayT, time;
    private Tile selected, swapA, swapB, downTile;
    private int[] hint;
    private Button downButton;
    private float downX, downY;
    private boolean dragged;
    private long last;

    public GameView(Context ctx) {
        super(ctx);
        prefs = ctx.getSharedPreferences("farm", Context.MODE_PRIVATE);
        BitmapFactory.Options o = new BitmapFactory.Options();
        o.inScaled = false;
        for (int i = 0; i < Art.COUNT; i++) art[i] = BitmapFactory.decodeResource(getResources(), Art.RES[i], o);
        unlocked = Math.max(1, prefs.getInt("unlocked", 1));
        totalCoins = prefs.getInt("coins", 0);
        for (int i = 0; i < Level.COUNT; i++) bestStars[i] = prefs.getInt("stars_" + i, 0);
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
        sky.setShader(new LinearGradient(0, 0, 0, H, 0xFF6EC8F5, 0xFFE4F6FF, Shader.TileMode.CLAMP));
        hill1.reset();
        hill1.moveTo(0, H * 0.80f);
        hill1.quadTo(W * 0.25f, H * 0.68f, W * 0.55f, H * 0.78f);
        hill1.quadTo(W * 0.82f, H * 0.87f, W, H * 0.74f);
        hill1.lineTo(W, H);
        hill1.lineTo(0, H);
        hill1.close();
        hill2.reset();
        hill2.moveTo(0, H * 0.90f);
        hill2.quadTo(W * 0.3f, H * 0.80f, W * 0.65f, H * 0.90f);
        hill2.quadTo(W * 0.85f, H * 0.96f, W, H * 0.87f);
        hill2.lineTo(W, H);
        hill2.lineTo(0, H);
        hill2.close();
        layout();
        buildButtons();
    }

    private void layout() {
        float m = 3 * u, hh = 13 * u;
        float hudTop = topInset + m;
        float goalTop = hudTop + hh + m * 0.8f;
        goalPanel.set(m, goalTop, W - m, goalTop + 26 * u);
        float top = goalPanel.bottom + m;
        float bottom = H - botInset - m;
        float pad = 2 * u;
        float availH = bottom - top;
        cell = Math.min((W - 2 * m - 2 * pad) / COLS, (availH - 2 * pad) / ROWS);
        float bw = cell * COLS, bh = cell * ROWS;
        bx = (W - bw) / 2;
        by = top + Math.min((availH - bh) / 2, 2 * u);
        boardPanel.set(bx - pad, by - pad, bx + bw + pad, by + bh + pad);
        overlay.set(W * 0.08f, H * 0.2f, W * 0.92f, H * 0.8f);
        coinR = hh * 0.5f;
        coinX = m + hh + m * 0.8f + hh * 0.6f;
        coinY = hudTop + hh / 2;
    }

    private void buildButtons() {
        buttons.clear();
        float m = 3 * u, hh = 13 * u;
        if (screen == Screen.TITLE) {
            Button b = new Button();
            b.r.set(W * 0.2f, H * 0.66f, W * 0.8f, H * 0.66f + 17 * u);
            b.text = "PLAY";
            b.act = this::goLevels;
            buttons.add(b);
        } else if (screen == Screen.LEVELS) {
            addBack(m, topInset + m, hh, this::goTitle);
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
                b.locked = i >= unlocked;
                b.color = i == unlocked - 1 ? ORANGE : GREEN;
                b.stars = b.locked ? -1 : bestStars[i];
                if (!b.locked) b.act = () -> startLevel(idx);
                buttons.add(b);
            }
        } else if (phase == Phase.END) {
            float pw = overlay.width(), ph = overlay.height(), bh = ph * 0.11f;
            float cx = overlay.centerX();
            if (won) {
                final boolean hasNext = levelIndex + 1 < Level.COUNT;
                addWide(cx, overlay.top + ph * 0.64f, pw * 0.7f, bh, hasNext ? "NEXT LEVEL" : "ALL DONE!", GREEN,
                        hasNext ? () -> startLevel(levelIndex + 1) : this::goLevels);
                addWide(cx - pw * 0.19f, overlay.top + ph * 0.80f, pw * 0.34f, bh, "REPLAY", ORANGE, () -> startLevel(levelIndex));
                addWide(cx + pw * 0.19f, overlay.top + ph * 0.80f, pw * 0.34f, bh, "MENU", BLUE, this::goLevels);
            } else {
                addWide(cx, overlay.top + ph * 0.64f, pw * 0.7f, bh, "TRY AGAIN", GREEN, () -> startLevel(levelIndex));
                addWide(cx, overlay.top + ph * 0.80f, pw * 0.5f, bh, "MENU", BLUE, this::goLevels);
            }
        } else {
            addBack(m, topInset + m, hh, this::goLevels);
        }
    }

    private void addBack(float x, float y, float d, Runnable act) {
        Button b = new Button();
        b.r.set(x, y, x + d, y + d);
        b.circle = true;
        b.back = true;
        b.color = ORANGE;
        b.act = act;
        buttons.add(b);
    }

    private void addWide(float cx, float cy, float w, float h, String text, int color, Runnable act) {
        Button b = new Button();
        b.r.set(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2);
        b.text = text;
        b.color = color;
        b.act = act;
        buttons.add(b);
    }

    // ---------------------------------------------------------------- navigation

    boolean onBack() {
        if (screen == Screen.PLAY) {
            goLevels();
            return true;
        }
        if (screen == Screen.LEVELS) {
            goTitle();
            return true;
        }
        return false;
    }

    private void goTitle() {
        screen = Screen.TITLE;
        buildButtons();
    }

    private void goLevels() {
        screen = Screen.LEVELS;
        phase = Phase.IDLE;
        buildButtons();
    }

    private void startLevel(int idx) {
        levelIndex = idx;
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
        particles.clear();
        flyers.clear();
        pops.clear();
        dying.clear();
        bagFlyers.clear();
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
        bump = Math.max(0, bump - dt * 4);
        float target = screen == Screen.PLAY ? landed : totalCoins;
        if (Math.abs(target - dispCoins) < 1) dispCoins = target;
        else dispCoins += (target - dispCoins) * Math.min(1f, dt * 9f) + Math.signum(target - dispCoins) * dt * 20;

        for (int i = particles.size() - 1; i >= 0; i--) {
            Particle p = particles.get(i);
            p.life -= dt;
            p.vy += 60 * u * dt;
            p.x += p.vx * dt;
            p.y += p.vy * dt;
            if (p.life <= 0) particles.remove(i);
        }
        for (int i = pops.size() - 1; i >= 0; i--) {
            Pop p = pops.get(i);
            p.life -= dt;
            p.y -= 8 * u * dt;
            if (p.life <= 0) pops.remove(i);
        }
        for (int i = flyers.size() - 1; i >= 0; i--) {
            Flyer f = flyers.get(i);
            if (f.delay > 0) {
                f.delay -= dt;
                continue;
            }
            f.t += dt / 0.65f;
            if (f.t >= 1) {
                landed += f.value;
                bump = 1;
                flyers.remove(i);
            }
        }
        bagBump = Math.max(0, bagBump - dt * 5);
        for (int i = bagFlyers.size() - 1; i >= 0; i--) {
            Flyer f = bagFlyers.get(i);
            if (f.delay > 0) {
                f.delay -= dt;
                continue;
            }
            f.t += dt / BAG_FLY;
            if (f.t >= 1) {
                landed -= f.value;
                bagBump = 1;
                bagFlyers.remove(i);
            }
        }
        for (Button b : buttons) if (b != downButton) b.pressed = false;
        if (screen != Screen.PLAY) return;

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
                float p = Math.min(1f, clearTimer / CLEAR_TIME);
                float s = p < 0.3f ? 1f + 0.3f * (p / 0.3f) : 1.3f * (1f - (p - 0.3f) / 0.7f);
                for (Tile t : dying) t.scale = Math.max(0f, s);
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
            case BAG:
                bagT += dt;
                if (bagT >= BAG_PAUSE + BAG_POP && bagFlyers.isEmpty()) {
                    if (bagEndT < 0) bagEndT = bagT;
                    if (bagT - bagEndT > 0.6f) {
                        phase = Phase.END;
                        overlayT = 0;
                        buildButtons();
                    }
                }
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
        dying.clear();
        boolean first = true;
        for (Board.Run run : runs) {
            int n = run.tiles.size();
            int gain = (n * 10 + Math.max(0, n - 3) * 15) * combo;
            coins += gain;
            float ax = 0, ay = 0;
            int share = gain / n;
            for (int i = 0; i < n; i++) {
                Tile t = run.tiles.get(i);
                ax += cx(t);
                ay += cy(t);
                Flyer f = new Flyer();
                f.sx = cx(t);
                f.sy = cy(t);
                f.delay = rnd.nextFloat() * 0.12f;
                f.value = i == n - 1 ? gain - share * (n - 1) : share;
                flyers.add(f);
                if (!t.dying) {
                    t.dying = true;
                    dying.add(t);
                    if (need[t.type] > 0) need[t.type]--;
                    sparkle(cx(t), cy(t));
                }
            }
            Pop pop = new Pop();
            pop.x = ax / n;
            pop.y = ay / n;
            pop.text = "+" + gain;
            pop.size = cell * 0.55f;
            pop.life = pop.max = 0.9f;
            pop.color = Color.WHITE;
            pops.add(pop);
            if (first && combo > 1) {
                Pop cp = new Pop();
                cp.x = W / 2;
                cp.y = by + cell * 1.2f;
                cp.text = "Combo x" + combo + "!";
                cp.size = cell * 0.8f;
                cp.life = cp.max = 1.1f;
                cp.color = 0xFFFFD43B;
                pops.add(cp);
            }
            first = false;
        }
    }

    private void finishClear() {
        for (Tile t : dying) board.g[t.r][t.c] = null;
        dying.clear();
        board.collapse();
        phase = Phase.FALL;
    }

    private void afterSettle() {
        boolean allDone = true;
        for (int n : need) if (n > 0) allDone = false;
        if (allDone) {
            endLevel(true);
        } else if (movesLeft <= 0) {
            endLevel(false);
        } else {
            if (board.findMove() == null) {
                board.reshuffle();
                for (Tile[] row : board.g) for (Tile t : row) t.scale = 0f;
                Pop p = new Pop();
                p.x = W / 2;
                p.y = by + cell * 3.5f;
                p.text = "Shuffle!";
                p.size = cell * 0.9f;
                p.life = p.max = 1.2f;
                p.color = 0xFF9BE15D;
                pops.add(p);
            }
            phase = Phase.IDLE;
            idleTime = 0;
            hint = null;
        }
    }

    private void startBag() {
        phase = Phase.BAG;
        bagT = 0;
        bagEndT = -1;
        bagTotal = Math.max(1, coins);
        flyers.clear();
        landed = coins;
        bagFlyers.clear();
        int n = Math.min(26, Math.max(8, coins / 20));
        int share = coins / n;
        for (int i = 0; i < n; i++) {
            Flyer f = new Flyer();
            f.delay = BAG_PAUSE + BAG_POP + i * 0.035f;
            f.value = i == n - 1 ? coins - share * (n - 1) : share;
            bagFlyers.add(f);
        }
    }

    private void endLevel(boolean win) {
        phase = Phase.END;
        overlayT = 0;
        won = win;
        bonus = 0;
        stars = 0;
        if (win) {
            bonus = movesLeft * 30;
            coins += bonus;
            landed += bonus;
            float ratio = movesLeft / (float) level.moves;
            stars = ratio >= 0.4f ? 3 : ratio >= 0.15f ? 2 : 1;
            bestStars[levelIndex] = Math.max(bestStars[levelIndex], stars);
            unlocked = Math.max(unlocked, Math.min(Level.COUNT, levelIndex + 2));
        }
        totalCoins += coins;
        prefs.edit().putInt("coins", totalCoins).putInt("unlocked", unlocked)
                .putInt("stars_" + levelIndex, bestStars[levelIndex]).apply();
        if (win) startBag();
        buildButtons();
    }

    private void attemptSwap(Tile a, Tile b) {
        if (phase != Phase.IDLE || Math.abs(a.r - b.r) + Math.abs(a.c - b.c) != 1) return;
        board.swap(a.r, a.c, b.r, b.c);
        swapA = a;
        swapB = b;
        selected = null;
        hint = null;
        phase = Phase.SWAP;
    }

    private float cx(Tile t) {
        return bx + (t.x + 0.5f) * cell;
    }

    private float cy(Tile t) {
        return by + (t.y + 0.5f) * cell;
    }

    private void sparkle(float x, float y) {
        for (int i = 0; i < 7; i++) {
            Particle p = new Particle();
            double a = rnd.nextDouble() * Math.PI * 2;
            float sp = (15 + rnd.nextFloat() * 30) * u;
            p.x = x;
            p.y = y;
            p.vx = (float) Math.cos(a) * sp;
            p.vy = (float) Math.sin(a) * sp - 15 * u;
            p.life = p.max = 0.5f + rnd.nextFloat() * 0.3f;
            p.size = cell * (0.08f + rnd.nextFloat() * 0.1f);
            p.color = i % 2 == 0 ? 0xFFFFE066 : Color.WHITE;
            particles.add(p);
        }
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
                    if (b.r.contains(x, y) && b.act != null) b.act.run();
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
            drawBackground(c);
            if (screen == Screen.TITLE) drawTitle(c);
            else if (screen == Screen.LEVELS) drawLevels(c);
            else drawPlay(c);
        }
        postInvalidateOnAnimation();
    }

    private void drawBackground(Canvas c) {
        c.drawRect(0, 0, W, H, sky);
        gfx.circle(c, W * 0.85f, H * 0.12f + topInset * 0.3f, 11 * u, 0x55FFF3A0);
        gfx.circle(c, W * 0.85f, H * 0.12f + topInset * 0.3f, 7 * u, 0xFFFFE066);
        for (int i = 0; i < 3; i++) {
            float x = ((time * (3 + i * 1.5f) * u + i * W * 0.45f) % (W * 1.5f)) - W * 0.25f;
            float y = H * (0.13f + i * 0.09f);
            float s = u * (5 + i);
            gfx.ellipse(c, x, y, s * 2.6f, s * 1.1f, 0xCCFFFFFF);
            gfx.circle(c, x - s * 0.8f, y - s * 0.6f, s * 1.2f, 0xCCFFFFFF);
            gfx.circle(c, x + s * 0.7f, y - s * 0.8f, s * 1.5f, 0xCCFFFFFF);
        }
        plain.setColor(0xFF8BD450);
        c.drawPath(hill1, plain);
        plain.setColor(0xFF68B83A);
        c.drawPath(hill2, plain);
    }

    private void drawIcon(Canvas c, int type, float cx, float cy, float size) {
        Bitmap b = art[type];
        src.set(0, 0, b.getWidth(), b.getHeight());
        dst.set(cx - size / 2, cy - size / 2, cx + size / 2, cy + size / 2);
        c.drawBitmap(b, src, dst, bmp);
    }

    private void drawCoinPill(Canvas c, float l, float t, float r, float b, int value) {
        float h = b - t;
        gfx.rrect(c, l, t + h * 0.07f, r, b + h * 0.07f, h / 2, Gfx.darken(PANEL, 0.6f));
        gfx.rrect(c, l, t, r, b, h / 2, PANEL);
        gfx.rrectLine(c, l + h * 0.03f, t + h * 0.03f, r - h * 0.03f, b - h * 0.03f, h / 2, h * 0.06f, 0xFFC98A4B);
        float s = 1f + 0.25f * bump;
        c.save();
        c.scale(s, s, l + h * 0.2f, (t + b) / 2);
        gfx.coinPile(c, l + h * 0.2f, (t + b) / 2, h * 0.5f);
        c.restore();
        gfx.textC(c, String.valueOf(value), l + h * 0.9f, (t + b) / 2, h * 0.55f, Color.WHITE, 0xFF3A1F08, Paint.Align.LEFT);
    }

    private void drawTitle(Canvas c) {
        c.save();
        c.rotate(-4, W / 2, H * 0.2f);
        gfx.text(c, "Farm", W / 2, H * 0.2f, W * 0.24f, 0xFFFFD43B, 0xFF7A3E0B, Paint.Align.CENTER);
        gfx.text(c, "Harvest", W / 2, H * 0.2f + W * 0.2f, W * 0.2f, 0xFF7ED957, 0xFF1F5F10, Paint.Align.CENTER);
        c.restore();
        int[] show = {1, 0, 5, 4, 2};
        for (int i = 0; i < show.length; i++) {
            float x = W * (0.14f + i * 0.18f);
            float y = H * 0.5f - Math.abs((float) Math.sin(time * 3 + i * 0.9f)) * 4 * u;
            gfx.ellipse(c, x, H * 0.5f + W * 0.085f, W * 0.06f, W * 0.012f, 0x33000000);
            drawIcon(c, show[i], x, y, W * 0.18f);
        }
        float m = 3 * u, hh = 11 * u;
        drawCoinPill(c, W * 0.52f, topInset + m, W - m, topInset + m + hh, Math.round(dispCoins));
        drawButtons(c);
    }

    private void drawLevels(Canvas c) {
        float m = 3 * u;
        gfx.textC(c, "Choose a Level", W / 2 + 3 * u, topInset + m + 6.5f * u, 7 * u, Color.WHITE, 0xFF1F5F10, Paint.Align.CENTER);
        drawButtons(c);
    }

    private void drawPlay(Canvas c) {
        float m = 3 * u, hh = 13 * u, hudTop = topInset + m;
        // coin counter + moves
        float mw = 27 * u;
        drawCoinPill(c, m + hh + m * 0.8f + hh * 0.4f, hudTop, W - m - mw - m * 0.8f, hudTop + hh, Math.round(dispCoins));
        float ml = W - m - mw, mr = W - m;
        gfx.rrect(c, ml, hudTop + hh * 0.07f, mr, hudTop + hh * 1.07f, hh / 2, Gfx.darken(PANEL, 0.6f));
        gfx.rrect(c, ml, hudTop, mr, hudTop + hh, hh / 2, PANEL);
        gfx.rrectLine(c, ml + hh * 0.03f, hudTop + hh * 0.03f, mr - hh * 0.03f, hudTop + hh * 0.97f, hh / 2, hh * 0.06f, 0xFFC98A4B);
        gfx.textC(c, "MOVES", (ml + mr) / 2, hudTop + hh * 0.26f, hh * 0.22f, 0xFFFFE8B0, 0xFF3A1F08, Paint.Align.CENTER);
        gfx.textC(c, String.valueOf(movesLeft), (ml + mr) / 2, hudTop + hh * 0.66f, hh * 0.52f,
                movesLeft <= 3 ? 0xFFFF7A66 : Color.WHITE, 0xFF3A1F08, Paint.Align.CENTER);

        drawGoals(c);

        // board
        gfx.rrect(c, boardPanel.left, boardPanel.top + 1.2f * u, boardPanel.right, boardPanel.bottom + 1.2f * u, 4 * u, Gfx.darken(WOOD, 0.6f));
        gfx.rrect(c, boardPanel.left, boardPanel.top, boardPanel.right, boardPanel.bottom, 4 * u, WOOD);
        gfx.rrect(c, boardPanel.left + u * 0.7f, boardPanel.top + u * 0.7f, boardPanel.right - u * 0.7f, boardPanel.bottom - u * 0.7f, 3.4f * u, 0xFFE9CF93);
        for (int r = 0; r < ROWS; r++) {
            for (int col = 0; col < COLS; col++) {
                float x = bx + col * cell, y = by + r * cell, g = cell * 0.04f;
                gfx.rrect(c, x + g, y + g, x + cell - g, y + cell - g, cell * 0.2f, (r + col) % 2 == 0 ? 0xFFF8E7B4 : 0xFFF1DB9C);
            }
        }
        if (board != null) {
            c.save();
            c.clipRect(bx, by, bx + cell * COLS, by + cell * ROWS);
            if (selected != null) {
                float x = bx + selected.c * cell, y = by + selected.r * cell, g = cell * 0.03f;
                gfx.rrect(c, x + g, y + g, x + cell - g, y + cell - g, cell * 0.2f, 0x66FFFFFF);
                gfx.rrectLine(c, x + g, y + g, x + cell - g, y + cell - g, cell * 0.2f, cell * 0.06f, 0xFFFFFFFF);
            }
            for (int r = 0; r < ROWS; r++) {
                for (int col = 0; col < COLS; col++) {
                    Tile t = board.g[r][col];
                    if (t == null) continue;
                    float extra = 1f;
                    if (t == selected) extra = 1f + 0.07f * (float) Math.sin(time * 12);
                    else if (hint != null && ((t.r == hint[0] && t.c == hint[1]) || (t.r == hint[2] && t.c == hint[3])))
                        extra = 1f + 0.1f * (float) Math.sin(time * 8);
                    drawTile(c, t, extra);
                }
            }
            c.restore();
        }

        for (Particle p : particles) {
            float a = p.life / p.max;
            gfx.star(c, p.x, p.y, p.size * (0.5f + a), Gfx.alpha(p.color, a), 0);
        }
        for (Flyer f : flyers) {
            if (f.delay > 0) continue;
            float t = f.t * f.t * (3 - 2 * f.t);
            float cpx = (f.sx + coinX) / 2 + (f.sx < W / 2 ? -1 : 1) * 10 * u;
            float cpy = Math.min(f.sy, coinY) - 12 * u;
            float x = (1 - t) * (1 - t) * f.sx + 2 * (1 - t) * t * cpx + t * t * coinX;
            float y = (1 - t) * (1 - t) * f.sy + 2 * (1 - t) * t * cpy + t * t * coinY;
            gfx.coin(c, x, y, cell * (0.3f - 0.12f * t));
        }
        for (Pop p : pops) {
            float a = Math.min(1f, p.life / (p.max * 0.4f));
            float s = 1f + 0.25f * (float) Math.sin(Math.min(1f, (p.max - p.life) * 6f) * Math.PI / 2);
            gfx.textC(c, p.text, p.x, p.y, p.size * s, Gfx.alpha(p.color, a), Gfx.alpha(0xFF4A2808, a), Paint.Align.CENTER);
        }

        if (phase == Phase.BAG) {
            drawBag(c);
            drawButtons(c);
        } else if (phase == Phase.END) drawEnd(c);
        else drawButtons(c);
    }

    private void drawBag(Canvas c) {
        float hh = 13 * u, hudTop = topInset + 3 * u;
        float k = Math.max(0f, Math.min(1f, (bagT - BAG_PAUSE) / BAG_POP));
        c.drawColor(Color.argb((int) (140 * k), 0, 0, 0));
        // keep the counter bright above the dim layer
        drawCoinPill(c, 3 * u + hh + 3 * u * 0.8f + hh * 0.4f, hudTop, W - 3 * u - 27 * u - 3 * u * 0.8f, hudTop + hh, Math.max(0, Math.round(dispCoins)));
        if (k <= 0f) return;
        float s = W * 0.55f, bagX = W / 2, bagY = H * 0.52f;
        float ease = 1f + 2.70158f * (float) Math.pow(k - 1, 3) + 1.70158f * (float) Math.pow(k - 1, 2);
        float sc = ease * (1f + 0.1f * bagBump);
        float mouthY = bagY - 0.5f * s;
        for (Flyer f : bagFlyers) {
            if (f.delay > 0) continue;
            float t = f.t * f.t;
            float cpx = (coinX + bagX) / 2, cpy = Math.min(coinY, mouthY) - 14 * u;
            float x = (1 - t) * (1 - t) * coinX + 2 * (1 - t) * t * cpx + t * t * bagX;
            float y = (1 - t) * (1 - t) * coinY + 2 * (1 - t) * t * cpy + t * t * mouthY;
            gfx.coin(c, x, y, cell * (0.34f - 0.1f * t));
        }
        float fillFrac = 1f - Math.max(0, landed) / (float) bagTotal;
        c.save();
        c.scale(sc, sc, bagX, bagY + 0.55f * s);
        gfx.moneyBag(c, bagX, bagY, s, fillFrac);
        c.restore();
    }

    private void drawGoals(Canvas c) {
        RectF p = goalPanel;
        gfx.rrect(c, p.left, p.top + u, p.right, p.bottom + u, 4 * u, Gfx.darken(WOOD, 0.7f));
        gfx.rrect(c, p.left, p.top, p.right, p.bottom, 4 * u, WOOD);
        gfx.rrect(c, p.left + u * 0.8f, p.top + u * 0.8f, p.right - u * 0.8f, p.bottom - u * 0.8f, 3.4f * u, CREAM);
        if (level == null) return;
        gfx.textC(c, "LEVEL " + level.number + " - HARVEST GOALS", p.centerX(), p.top + p.height() * 0.15f, 3.6f * u, 0xFF8B5A2B, 0, Paint.Align.CENTER);
        int n = level.goalKinds.length;
        float slot = (p.width() - 4 * u) / n;
        for (int i = 0; i < n; i++) {
            int kind = level.goalKinds[i];
            float x = p.left + 2 * u + slot * (i + 0.5f);
            float iy = p.top + p.height() * 0.5f;
            float size = Math.min(slot * 0.8f, p.height() * 0.38f);
            drawIcon(c, kind, x, iy, size);
            if (need[kind] == 0) gfx.check(c, x + size * 0.38f, iy + size * 0.32f, size * 0.26f);
            gfx.textC(c, need[kind] == 0 ? "Done!" : String.valueOf(need[kind]), x, p.top + p.height() * 0.84f, 5.4f * u,
                    need[kind] == 0 ? 0xFF4CBB2C : Color.WHITE, need[kind] == 0 ? 0xFFFFFFFF : 0xFF8B5A2B, Paint.Align.CENTER);
        }
    }

    private void drawTile(Canvas c, Tile t, float extra) {
        float x = cx(t), y = cy(t);
        float size = cell * 0.92f * t.scale * extra;
        float w = size * (1f + 0.12f * t.squash), h = size * (1f - 0.18f * t.squash);
        float bottom = y + size / 2;
        gfx.ellipse(c, x, y + cell * 0.38f, cell * 0.3f * t.scale, cell * 0.06f * t.scale, 0x26000000);
        Bitmap b = art[t.type];
        src.set(0, 0, b.getWidth(), b.getHeight());
        dst.set(x - w / 2, bottom - h, x + w / 2, bottom);
        c.drawBitmap(b, src, dst, bmp);
    }

    private void drawEnd(Canvas c) {
        float fade = Math.min(1f, overlayT * 4f);
        c.drawColor(Color.argb((int) (150 * fade), 0, 0, 0));
        float k = Math.min(1f, overlayT * 2.5f);
        float ease = 1f + 2.70158f * (float) Math.pow(k - 1, 3) + 1.70158f * (float) Math.pow(k - 1, 2);
        float dy = (1f - ease) * -H * 0.25f * (k < 1 ? 1 : 0);
        c.save();
        c.translate(0, dy);
        RectF o = overlay;
        float ph = o.height();
        gfx.rrect(c, o.left, o.top + 1.5f * u, o.right, o.bottom + 1.5f * u, 6 * u, Gfx.darken(WOOD, 0.6f));
        gfx.rrect(c, o.left, o.top, o.right, o.bottom, 6 * u, WOOD);
        gfx.rrect(c, o.left + u, o.top + u, o.right - u, o.bottom - u, 5 * u, CREAM);
        gfx.rrect(c, o.left - u, o.top - 4 * u, o.right + u, o.top + ph * 0.17f, 5 * u, won ? GREEN : 0xFFE5604A);
        gfx.textC(c, won ? "Level Complete!" : "Out of Moves!", o.centerX(), o.top + ph * 0.06f, 7.2f * u, Color.WHITE, 0xFF2C4A14, Paint.Align.CENTER);

        if (won) {
            for (int i = 0; i < 3; i++) {
                float sk = Math.max(0f, Math.min(1f, overlayT * 3f - 0.4f - i * 0.35f));
                float x = o.centerX() + (i - 1) * 20 * u;
                float y = o.top + ph * 0.3f - (i == 1 ? 3 * u : 0);
                boolean on = i < stars;
                float s = on ? 0.4f + 0.6f * sk : 1f;
                gfx.star(c, x, y, 9.5f * u * s, on ? 0xFFFFD43B : 0xFFD8CBA8, on ? 0xFFC97700 : 0xFFB5A67E);
            }
        } else {
            gfx.textC(c, "Give it another go, farmer!", o.centerX(), o.top + ph * 0.3f, 4.6f * u, 0xFF8B5A2B, 0, Paint.Align.CENTER);
        }
        String amount = "+" + coins;
        float fs = 8 * u, tw = gfx.width(amount, fs);
        float total = 10 * u + tw;
        float sx = o.centerX() - total / 2;
        gfx.coinPile(c, sx + 4.5f * u, o.top + ph * 0.46f, 5 * u);
        gfx.textC(c, amount, sx + 10 * u, o.top + ph * 0.46f, fs, 0xFFFFC928, 0xFF7A3E0B, Paint.Align.LEFT);
        if (won && bonus > 0) {
            gfx.textC(c, "Moves bonus: " + bonus, o.centerX(), o.top + ph * 0.545f, 4.4f * u, 0xFF8B5A2B, 0, Paint.Align.CENTER);
        }
        drawButtons(c);
        c.restore();
    }

    private void drawButtons(Canvas c) {
        for (Button b : buttons) drawButton(c, b);
    }

    private void drawButton(Canvas c, Button b) {
        float w = b.r.width(), h = b.r.height();
        float depth = h * 0.09f;
        float off = b.pressed ? depth * 0.8f : 0f;
        int base = b.locked ? 0xFFA8A8A8 : b.color;
        float cx = b.r.centerX(), cy = b.r.centerY();
        if (b.circle) {
            float rad = w / 2;
            gfx.circle(c, cx, cy + depth, rad, Gfx.darken(base, 0.62f));
            gfx.circle(c, cx, cy + off, rad, base);
            gfx.circle(c, cx, cy + off, rad * 0.86f, Gfx.lighten(base, 0.18f));
            gfx.ellipse(c, cx, cy + off - rad * 0.45f, rad * 0.55f, rad * 0.28f, 0x55FFFFFF);
        } else {
            float rad = h * 0.32f;
            gfx.rrect(c, b.r.left, b.r.top + depth, b.r.right, b.r.bottom + depth, rad, Gfx.darken(base, 0.62f));
            gfx.rrect(c, b.r.left, b.r.top + off, b.r.right, b.r.bottom + off, rad, base);
            gfx.rrect(c, b.r.left + h * 0.08f, b.r.top + off + h * 0.07f, b.r.right - h * 0.08f, b.r.top + off + h * 0.45f, rad * 0.7f, 0x44FFFFFF);
        }
        if (b.back) gfx.backArrow(c, cx, cy + off, w * 0.5f);
        else if (b.locked) gfx.lock(c, cx, cy + off, w * 0.26f);
        else if (b.circle) {
            boolean hasStars = b.stars >= 0;
            gfx.textC(c, b.text, cx, cy + off - (hasStars ? w * 0.06f : 0), w * 0.42f, Color.WHITE, Gfx.darken(base, 0.45f), Paint.Align.CENTER);
            if (hasStars) {
                for (int i = 0; i < 3; i++) {
                    boolean on = i < b.stars;
                    gfx.star(c, cx + (i - 1) * w * 0.22f, cy + off + w * 0.31f - (i == 1 ? 0 : w * 0.03f), w * 0.1f,
                            on ? 0xFFFFD43B : 0xFF7A8F60, on ? 0xFFC97700 : 0xFF4E5E3C);
                }
            }
        } else {
            gfx.textC(c, b.text, cx, cy + off, h * 0.42f, Color.WHITE, Gfx.darken(base, 0.45f), Paint.Align.CENTER);
        }
    }
}
