package com.farmharvest.game;

/** Level definition: which crops appear, what must be harvested, and the move budget. */
final class Level {
    static final int COUNT = 15;

    final int number;
    final int[] kinds;
    final int[] goalKinds;
    final int[] goalCounts;
    final int moves;

    private Level(int number, int[] kinds, int[] goalKinds, int[] goalCounts, int moves) {
        this.number = number;
        this.kinds = kinds;
        this.goalKinds = goalKinds;
        this.goalCounts = goalCounts;
        this.moves = moves;
    }

    static Level get(int index) {
        int nk = 4 + Math.min(index / 4, 2);
        int offset = (index * 3) % Art.COUNT;
        int[] kinds = new int[nk];
        for (int j = 0; j < nk; j++) kinds[j] = (offset + j) % Art.COUNT;

        int ng = Math.min(2 + index / 5, 4);
        int[] gk = new int[ng];
        int[] gc = new int[ng];
        int total = 0;
        for (int j = 0; j < ng; j++) {
            gk[j] = kinds[j];
            gc[j] = Math.min(8 + 2 * index + 2 * j, 30);
            total += gc[j];
        }
        int moves = Math.round(total / 3f * 1.9f) + 5;
        return new Level(index + 1, kinds, gk, gc, moves);
    }
}
