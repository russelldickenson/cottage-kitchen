package com.farmharvest.game;

/**
 * Level definition: the dish to cook, which ingredients are on the board, how many of each it needs, and the move budget.
 * Each dish is an order of STAGES batches: every batch is a bigger order on a busier board, so one picture serves several levels.
 */
final class Level {
    static final int STAGES = 3;
    static final int COUNT = Recipes.ALL.length * STAGES;

    /** Power-up tiles (Golden Egg, Tractor, TNT, Scarecrow) only appear from this level number. */
    static final int POWER_UP_LEVEL = 10;

    /** Move budgets, produced by tools/sim/run.sh (tune) from how fast an expert bot clears each order. */
    private static final int[] MOVES = {
            11, 13, 16, 11, 14, 16, 13, 15, 16, 12, 13, 15, 11, 13, 14,
            14, 15, 17, 15, 16, 17, 15, 16, 18, 13, 15, 15, 16, 18, 19,
            15, 16, 15, 18, 19, 19, 18, 19, 21};

    final int number, stage;
    final Recipes.Recipe recipe;
    final int[] kinds;
    final int[] goalKinds;
    final int[] goalCounts;
    final int moves;

    Level(int number, int stage, Recipes.Recipe recipe, int[] kinds, int[] goalKinds, int[] goalCounts, int moves) {
        this.number = number;
        this.stage = stage;
        this.recipe = recipe;
        this.kinds = kinds;
        this.goalKinds = goalKinds;
        this.goalCounts = goalCounts;
        this.moves = moves;
    }

    boolean powerUps() {
        return number >= POWER_UP_LEVEL;
    }

    boolean lastStage() {
        return stage == STAGES - 1;
    }

    int totalNeeded() {
        int t = 0;
        for (int c : goalCounts) t += c;
        return t;
    }

    /** Base size of the order: it grows with the dish and with every batch of it. */
    static int scaleFor(int index) {
        return Math.round(10 + 1.3f * (index / STAGES) + 4.5f * (index % STAGES));
    }

    static Level get(int index) {
        return get(index, MOVES[index], scaleFor(index));
    }

    /**
     * Ingredients the dish needs, plus other tiles to fill the board: five kinds, or one per ingredient if there are
     * more. Later batches ask for more of each ingredient. A dish with more ingredients is a bigger order.
     */
    static Level get(int index, int moves, int scale) {
        Recipes.Recipe rc = Recipes.ALL[index / STAGES];
        int stage = index % STAGES;
        int n = rc.ingredients.length, partSum = 0;
        for (int p : rc.parts) partSum += p;
        int total = Math.round(scale * (1f + 0.5f * (n - 1)));
        int[] counts = new int[n];
        for (int i = 0; i < n; i++) counts[i] = Math.max(3, Math.round(total * rc.parts[i] / (float) partSum));

        int[] kinds = new int[Math.min(Art.CROPS, Math.max(5, n))];
        System.arraycopy(rc.ingredients, 0, kinds, 0, n);
        int k = n;
        for (int step = 0; k < kinds.length; step++) {
            int filler = (3 * index + 5 * step + 2) % Art.CROPS;
            boolean used = false;
            for (int i = 0; i < k; i++) used |= kinds[i] == filler;
            if (!used) kinds[k++] = filler;
        }
        return new Level(index + 1, stage, rc, kinds, rc.ingredients.clone(), counts, moves);
    }
}
