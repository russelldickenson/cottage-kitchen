package com.farmharvest.game;

/** Level definition: which crops appear, what must be harvested, and the move budget. */
final class Level {
    static final int COUNT = 15;

    /** Power-up tiles (Golden Egg, Tractor, TNT, Scarecrow) only appear from this level number. */
    static final int POWER_UP_LEVEL = 8;

    /**
     * Per-level tuning, produced by tools/sim/run.sh (tune): the base goal count per crop and the move budget.
     * Budgets ramp from ~14 to ~25 moves while goals grow to fit what a skilled player can harvest in that time.
     */
    private static final int[] SCALE = {10, 11, 13, 14, 15, 17, 18, 19, 20, 22, 23, 24, 26, 27, 28};
    private static final int[] MOVES = {13, 13, 15, 16, 20, 20, 21, 22, 23, 25, 25, 25, 26, 26, 27};

    final int number;
    final int[] kinds;
    final int[] goalKinds;
    final int[] goalCounts;
    final int moves;

    Level(int number, int[] kinds, int[] goalKinds, int[] goalCounts, int moves) {
        this.number = number;
        this.kinds = kinds;
        this.goalKinds = goalKinds;
        this.goalCounts = goalCounts;
        this.moves = moves;
    }

    boolean powerUps() {
        return number >= POWER_UP_LEVEL;
    }

    static Level get(int index) {
        return get(index, MOVES[index], SCALE[index]);
    }

    /** Crops that are only sold: everything that is not an animal stock tile. */
    private static final int[] PLAIN = {0, 1, 2, 3, 4, 5, 7, 8, 11};

    static Level get(int index, int moves, int scale) {
        // Two rotating cash crops plus three of the four farm stock tiles, so the animals can always be fed.
        // Hay is held back for the first two levels (no barn yet), then each stock takes a turn sitting out.
        int[] kinds = new int[5];
        kinds[0] = PLAIN[(2 * index) % PLAIN.length];
        kinds[1] = PLAIN[(2 * index + 1) % PLAIN.length];
        int skip = index < 2 ? Farm.HAY : (index - 2) % Farm.STOCKS;
        int n = 2;
        for (int s = 0; s < Farm.STOCKS; s++) if (s != skip) kinds[n++] = Farm.STOCK_TILE[s];

        // goals alternate between a cash crop and a farm stock tile
        int[] order = {0, 2, 1, 3};
        int ng = Math.min(2 + index / 4, 4);
        int[] gk = new int[ng];
        int[] gc = new int[ng];
        for (int j = 0; j < ng; j++) {
            gk[j] = kinds[order[j]];
            gc[j] = scale + 2 * j;
        }
        return new Level(index + 1, kinds, gk, gc, moves);
    }
}
