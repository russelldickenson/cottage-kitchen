package com.zigzag.cottagekitchen;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Pure match-3 rules shared by the game and the level simulator. No Android types in here. */
final class Rules {
    static final int COLS = 7, ROWS = 8;

    /** A batch-worthy clear: five or more in a row, or a horizontal and vertical run meeting in an L/T. */
    static boolean bigMatch(List<Board.Run> runs) {
        for (Board.Run run : runs) if (run.tiles.size() >= 5) return true;
        for (Board.Run h : runs) {
            if (!h.horizontal) continue;
            for (Board.Run v : runs) {
                if (v.horizontal || v.type != h.type) continue;
                for (Tile t : h.tiles) if (v.tiles.contains(t)) return true;
            }
        }
        return false;
    }

    /**
     * Crates next to a cleared tile, or caught in the clear themselves, lose one hit each (once per clear).
     * Returns the crates that broke; those that only cracked go into damaged.
     */
    static List<Tile> hitCrates(Board board, java.util.Collection<Tile> cleared, List<Tile> damaged) {
        Set<Tile> hit = new LinkedHashSet<>();
        int[][] dirs = {{-1, 0}, {1, 0}, {0, -1}, {0, 1}};
        for (Tile t : cleared) {
            if (Art.isCrate(t.type)) hit.add(t);
            else for (int[] d : dirs) {
                int nr = t.r + d[0], nc = t.c + d[1];
                if (nr < 0 || nc < 0 || nr >= board.rows || nc >= board.cols) continue;
                Tile n = board.g[nr][nc];
                if (n != null && Art.isCrate(n.type)) hit.add(n);
            }
        }
        List<Tile> broken = new ArrayList<>();
        for (Tile c : hit) {
            if (--c.hp <= 0) broken.add(c);
            else damaged.add(c);
        }
        return broken;
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
