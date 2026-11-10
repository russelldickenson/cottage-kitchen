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
    /** Stock (Farm.EGG, SEED, HAY, WATER) this level's stock goal was set for because the herd is short of it, or -1. */
    int herdGoal = -1;

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

    /** The level for this stage of the farm. */
    static Level get(int index, Farm.Herd herd) {
        return get(index, MOVES[index], SCALE[index], herd);
    }

    static Level get(int index, int moves, int scale) {
        return get(index, moves, scale, null);
    }

    /**
     * Two rotating cash crops plus three of the four farm stock tiles. With no herd (the simulator's tuning runs) the
     * stock that sits out rotates. With a herd, the stock the herd is shortest of becomes the level's stock goal, sized
     * to the shortfall, and the stock that is fullest sits out, so a hungry herd can always be fixed on the board.
     */
    static Level get(int index, int moves, int scale, Farm.Herd herd) {
        int[] kinds = new int[5];
        kinds[0] = PLAIN[(2 * index) % PLAIN.length];
        kinds[1] = PLAIN[(2 * index + 1) % PLAIN.length];
        int stockGoal = scale + 2, herdGoal = -1;
        int target, skip;
        if (herd == null) {
            skip = index < 2 ? Farm.HAY : (index - 2) % Farm.STOCKS;
            target = (skip + 1) % Farm.STOCKS;
        } else {
            boolean noBarnYet = herd.house[Farm.BARN] == 0 && index < 3;
            float best = 0;
            target = -1;
            for (int k = 0; k < Farm.STOCKS; k++) {
                int s = (index + k) % Farm.STOCKS;
                if (s == Farm.HAY && noBarnYet) continue;
                int want = herd.want(s);
                float score = want == 0 ? 0 : herd.shortfall(s, 0) / (float) want;
                if (score > best) {
                    best = score;
                    target = s;
                }
            }
            if (target >= 0) {
                herdGoal = target;
                stockGoal = Math.max((int) Math.ceil(0.75 * scale), Math.min(scale + 2, herd.shortfall(target, 0)));
            } else {
                // nothing is short: top up whichever stock is emptiest
                float low = 2;
                for (int k = 0; k < Farm.STOCKS; k++) {
                    int s = (index + k) % Farm.STOCKS;
                    float f = s == Farm.HAY && noBarnYet ? 1f : herd.fill(s);
                    if (f < low) {
                        low = f;
                        target = s;
                    }
                }
            }
            skip = -1;
            float high = -1;
            for (int k = 0; k < Farm.STOCKS; k++) {
                int s = (index + k) % Farm.STOCKS;
                if (s == target) continue;
                float f = s == Farm.HAY && noBarnYet ? 2f : herd.fill(s);
                if (f > high) {
                    high = f;
                    skip = s;
                }
            }
        }
        kinds[2] = Farm.STOCK_TILE[target];
        int n = 3;
        for (int s = 0; s < Farm.STOCKS; s++) if (s != skip && s != target) kinds[n++] = Farm.STOCK_TILE[s];

        // goals alternate between a cash crop and the stock goal
        int[] order = {0, 2, 1, 3};
        int ng = Math.min(2 + index / 4, 4);
        int[] gk = new int[ng];
        int[] gc = new int[ng];
        for (int j = 0; j < ng; j++) {
            gk[j] = kinds[order[j]];
            gc[j] = j == 1 ? stockGoal : scale + 2 * j;
        }
        Level lv = new Level(index + 1, kinds, gk, gc, moves);
        lv.herdGoal = herdGoal;
        return lv;
    }
}
