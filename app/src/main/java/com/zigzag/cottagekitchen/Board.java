package com.zigzag.cottagekitchen;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/** Pure match-3 grid logic. No drawing or timing in here. */
final class Board {
    static final class Run {
        final int type;
        final boolean horizontal;
        final List<Tile> tiles = new ArrayList<>();

        Run(int type, boolean horizontal) {
            this.type = type;
            this.horizontal = horizontal;
        }
    }

    final int cols, rows;
    final Tile[][] g;
    private final int[] palette;
    private final Random rnd;

    Board(int cols, int rows, int[] palette, Random rnd) {
        this.cols = cols;
        this.rows = rows;
        this.palette = palette;
        this.rnd = rnd;
        this.g = new Tile[rows][cols];
        fill();
    }

    private int pick() {
        return palette[rnd.nextInt(palette.length)];
    }

    int typeAt(int r, int c) {
        if (r < 0 || c < 0 || r >= rows || c >= cols) return -1;
        Tile t = g[r][c];
        return t == null || Art.isSpecial(t.type) || Art.isCrate(t.type) ? -1 : t.type;
    }

    private boolean makes3(int r, int c, int t) {
        return (c >= 2 && typeAt(r, c - 1) == t && typeAt(r, c - 2) == t)
                || (r >= 2 && typeAt(r - 1, c) == t && typeAt(r - 2, c) == t);
    }

    /** Fills the whole grid with fresh tiles, guaranteeing at least one legal move and no ready matches. */
    void fill() {
        do {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int t;
                    do {
                        t = pick();
                    } while (makes3(r, c, t));
                    g[r][c] = new Tile(t, r, c);
                }
            }
        } while (findMove() == null);
    }

    /** Turns n tiles in the lower part of the board into crates (spread over different columns), keeping a legal move. */
    void addCrates(int n, Random rnd) {
        if (n <= 0) return;
        int top = rows / 3;
        while (true) {
            List<Integer> order = new ArrayList<>();
            for (int c = 0; c < cols; c++) order.add(c);
            java.util.Collections.shuffle(order, rnd);
            List<Tile> made = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                int c = order.get(i % order.size()), r = top + rnd.nextInt(rows - top);
                Tile t = g[r][c];
                if (Art.isCrate(t.type)) continue;
                t.type = Art.CRATE;
                t.hp = Art.CRATE_HP;
                made.add(t);
            }
            if (findMove() != null) return;
            for (Tile t : made) {
                t.type = pick();
                t.hp = 0;
            }
        }
    }

    /** Re-rolls the crop types of existing tiles in place. */
    void reshuffle() {
        do {
            for (int r = 0; r < rows; r++) {
                for (int c = 0; c < cols; c++) {
                    int t;
                    do {
                        t = pick();
                    } while (makes3(r, c, t));
                    if (!Art.isSpecial(g[r][c].type) && !Art.isCrate(g[r][c].type)) g[r][c].type = t;
                }
            }
        } while (findMove() == null);
    }

    void swap(int r1, int c1, int r2, int c2) {
        Tile a = g[r1][c1], b = g[r2][c2];
        g[r1][c1] = b;
        g[r2][c2] = a;
        a.r = r2;
        a.c = c2;
        b.r = r1;
        b.c = c1;
    }

    List<Run> findRuns() {
        List<Run> out = new ArrayList<>();
        for (int r = 0; r < rows; r++) {
            int c = 0;
            while (c < cols) {
                int t = typeAt(r, c);
                int e = c + 1;
                if (t >= 0) {
                    while (e < cols && typeAt(r, e) == t) e++;
                    if (e - c >= 3) {
                        Run run = new Run(t, true);
                        for (int k = c; k < e; k++) run.tiles.add(g[r][k]);
                        out.add(run);
                    }
                }
                c = e;
            }
        }
        for (int c = 0; c < cols; c++) {
            int r = 0;
            while (r < rows) {
                int t = typeAt(r, c);
                int e = r + 1;
                if (t >= 0) {
                    while (e < rows && typeAt(e, c) == t) e++;
                    if (e - r >= 3) {
                        Run run = new Run(t, false);
                        for (int k = r; k < e; k++) run.tiles.add(g[k][c]);
                        out.add(run);
                    }
                }
                r = e;
            }
        }
        return out;
    }

    /** First legal swap as {r1,c1,r2,c2}, or null if the board is dead. */
    int[] findMove() {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                if (c + 1 < cols && swapMatches(r, c, r, c + 1)) return new int[]{r, c, r, c + 1};
                if (r + 1 < rows && swapMatches(r, c, r + 1, c)) return new int[]{r, c, r + 1, c};
            }
        }
        return null;
    }

    private boolean swapMatches(int r1, int c1, int r2, int c2) {
        if (Art.isCrate(g[r1][c1].type) || Art.isCrate(g[r2][c2].type)) return false;
        if (Art.isSpecial(g[r1][c1].type) || Art.isSpecial(g[r2][c2].type)) return true;
        swap(r1, c1, r2, c2);
        boolean ok = !findRuns().isEmpty();
        swap(r1, c1, r2, c2);
        return ok;
    }

    /** Drops tiles down and spawns new ones above the board. Null cells are the holes. */
    void collapse() {
        for (int c = 0; c < cols; c++) {
            int w = rows - 1;
            for (int r = rows - 1; r >= 0; r--) {
                Tile t = g[r][c];
                if (t != null) {
                    if (r != w) {
                        g[w][c] = t;
                        g[r][c] = null;
                        t.r = w;
                    }
                    w--;
                }
            }
            int n = w + 1;
            for (int r = w; r >= 0; r--) {
                Tile t = new Tile(pick(), r, c);
                t.y = r - n;
                g[r][c] = t;
            }
        }
    }
}
