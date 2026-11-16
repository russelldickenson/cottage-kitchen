package com.farmharvest.game;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

/** Pure match-3 rules shared by the game and the level simulator. No Android types in here. */
final class Rules {
    static final int COLS = 7, ROWS = 8;
    // Matching pays nothing by itself any more: coins come from fed animals and from selling the harvest.
    static final int TILE_COINS = 0, LONG_RUN_BONUS = 0, SPECIAL_TILE_COINS = 0, MOVE_BONUS = 0;

    /** Power-up tiles a clear should leave behind. */
    static final class Spawns {
        final List<Tile> tiles = new ArrayList<>();
        final List<Integer> types = new ArrayList<>();

        void add(Tile t, int type) {
            if (tiles.contains(t)) return;
            tiles.add(t);
            types.add(type);
        }
    }

    /**
     * 5+ in a row makes a colour-clearer (placed on the swapped tile when it is part of the run);
     * a horizontal and vertical run of one crop sharing a tile (a T or L) makes a bomb on that tile.
     */
    static Spawns findSpawns(List<Board.Run> runs, Tile swapA, Tile swapB, Random rnd) {
        Spawns s = new Spawns();
        for (Board.Run run : runs) {
            if (run.tiles.size() < 5) continue;
            Tile at = run.tiles.contains(swapA) ? swapA : run.tiles.contains(swapB) ? swapB : run.tiles.get(run.tiles.size() / 2);
            s.add(at, rnd.nextBoolean() ? Art.GOLDEN_EGG : Art.TRACTOR);
        }
        for (Board.Run h : runs) {
            if (!h.horizontal) continue;
            for (Board.Run v : runs) {
                if (v.horizontal || v.type != h.type) continue;
                for (Tile t : h.tiles) {
                    if (v.tiles.contains(t)) {
                        s.add(t, rnd.nextBoolean() ? Art.TNT : Art.SCARECROW);
                        break;
                    }
                }
            }
        }
        return s;
    }

    /** Coins for one matched run. */
    static int runCoins(int length, int combo) {
        return (length * TILE_COINS + Math.max(0, length - 3) * LONG_RUN_BONUS) * combo;
    }

    /** Coins for a power-up clear of the given number of tiles. */
    static int specialCoins(int tiles, int combo) {
        return tiles * SPECIAL_TILE_COINS * combo;
    }

    /** Which of the two swapped tiles is the power-up that plays (colour-clearers outrank bombs). */
    static Tile activator(Tile a, Tile b) {
        if (Art.isColor(a.type)) return a;
        if (Art.isColor(b.type)) return b;
        return Art.isBomb(a.type) ? a : b;
    }

    /** Everything a swapped power-up takes with it. At least one of a and b must be a power-up. */
    static Set<Tile> specialClearSet(Board board, Tile a, Tile b) {
        boolean ac = Art.isColor(a.type), bc = Art.isColor(b.type);
        boolean as = Art.isSpecial(a.type), bs = Art.isSpecial(b.type);
        Set<Tile> set = new LinkedHashSet<>();
        if ((ac || bc) && as && bs) {
            for (Tile[] row : board.g) for (Tile t : row) if (t != null) set.add(t);
        } else if (ac || bc) {
            Tile other = ac ? b : a;
            for (Tile[] row : board.g) for (Tile t : row) if (t != null && t.type == other.type) set.add(t);
            set.add(ac ? a : b);
        } else {
            ArrayDeque<Tile> q = new ArrayDeque<>();
            for (Tile t : new Tile[]{a, b}) {
                if (Art.isBomb(t.type) && set.add(t)) q.add(t);
            }
            while (!q.isEmpty()) {
                Tile t = q.poll();
                for (int dr = -1; dr <= 1; dr++) {
                    for (int dc = -1; dc <= 1; dc++) {
                        int nr = t.r + dr, nc = t.c + dc;
                        if (nr < 0 || nc < 0 || nr >= board.rows || nc >= board.cols) continue;
                        Tile n = board.g[nr][nc];
                        if (n != null && set.add(n) && Art.isBomb(n.type)) q.add(n);
                    }
                }
            }
        }
        return set;
    }

    static boolean goalsDone(int[] need) {
        for (int n : need) if (n > 0) return false;
        return true;
    }

    static int stars(int movesLeft, int totalMoves) {
        float ratio = movesLeft / (float) totalMoves;
        return ratio >= 0.4f ? 3 : ratio >= 0.15f ? 2 : 1;
    }

    private Rules() {
    }
}
