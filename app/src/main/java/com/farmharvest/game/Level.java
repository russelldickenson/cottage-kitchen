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
    private static final int[] PLAIN = {0, 1, 2, 3, 4, 5, 8, 11};

    /** How many of the farm's stock tiles share the board with the cash crops. */
    private static final int STOCKS_ON_BOARD = 3;

    /** The level for this stage of the farm. */
    static Level get(int index, Farm.Herd herd) {
        return get(index, MOVES[index], SCALE[index], herd);
    }

    static Level get(int index, int moves, int scale) {
        return get(index, moves, scale, null);
    }

    /**
     * Cash crops plus the stock tiles the herd cares about. The stock the herd is shortest of becomes the level's stock
     * goal, sized to the shortfall; the other stock tiles on the board are the next most needed, so a hungry herd can
     * always be fixed on the board. With no herd (the simulator's tuning runs) it behaves like a new farm.
     */
    static Level get(int index, int moves, int scale, Farm.Herd herd) {
        if (herd == null) herd = new Farm.Herd();
        // a species' feed is worth carrying once its house is built or about to unlock
        boolean[] relevant = new boolean[Farm.STOCKS];
        relevant[Farm.EGG] = true;
        for (int sp = 0; sp < Farm.SPECIES; sp++) {
            if (herd.house[sp] == 0 && index + 2 < Farm.HOUSE_UNLOCK_LEVEL[sp]) continue;
            for (int s = 0; s < Farm.STOCKS; s++) if (Farm.FEED[sp][s] > 0) relevant[s] = true;
        }
        // priority: how much of its want is still missing, then how empty it is
        float[] score = new float[Farm.STOCKS], key = new float[Farm.STOCKS];
        for (int s = 0; s < Farm.STOCKS; s++) {
            int want = herd.want(s);
            score[s] = want == 0 ? 0 : herd.shortfall(s, 0) / (float) want;
            key[s] = score[s] + 0.01f * (1f - herd.fill(s));
        }
        int[] chosen = new int[STOCKS_ON_BOARD];
        int n = 0;
        boolean[] taken = new boolean[Farm.STOCKS];
        while (n < STOCKS_ON_BOARD) {
            int best = -1;
            for (int k = 0; k < Farm.STOCKS; k++) {
                int s = (index + k) % Farm.STOCKS;
                if (relevant[s] && !taken[s] && (best < 0 || key[s] > key[best])) best = s;
            }
            if (best < 0) break;
            taken[best] = true;
            chosen[n++] = best;
        }
        int target = chosen[0], herdGoal = score[target] > 0 ? target : -1;
        int stockGoal = scale + 2;
        if (herdGoal >= 0) stockGoal = Math.max((int) Math.ceil(0.75 * scale), Math.min(scale + 2, herd.shortfall(target, 0)));

        // kinds: a cash crop, the stock goal, the other stocks, then more cash crops to make five
        int[] kinds = new int[5];
        kinds[0] = PLAIN[(2 * index) % PLAIN.length];
        for (int i = 0; i < n; i++) kinds[1 + i] = Farm.STOCK_TILE[chosen[i]];
        for (int i = 1 + n; i < 5; i++) kinds[i] = PLAIN[(2 * index + i - n) % PLAIN.length];

        int ng = Math.min(2 + index / 4, 4);
        int[] gk = new int[ng];
        int[] gc = new int[ng];
        for (int j = 0; j < ng; j++) {
            gk[j] = kinds[j];
            gc[j] = j == 1 ? stockGoal : scale + 2 * j;
        }
        Level lv = new Level(index + 1, kinds, gk, gc, moves);
        lv.herdGoal = herdGoal;
        return lv;
    }
}
