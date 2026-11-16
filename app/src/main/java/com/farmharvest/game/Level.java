package com.farmharvest.game;

/** Level definition: the dish to cook, which ingredients are on the board, how many of each it needs, and the move budget. */
final class Level {
    static final int COUNT = Recipes.ALL.length;

    /** Power-up tiles (Golden Egg, Tractor, TNT, Scarecrow) only appear from this level number. */
    static final int POWER_UP_LEVEL = 8;

    /**
     * Per-level tuning, produced by tools/sim/run.sh (tune): the base size of the order and the move budget.
     * More ingredients make a bigger order (see total), and the budget ramps with it.
     */
    private static final int[] SCALE = {10, 11, 13, 14, 15, 17, 18, 19, 20, 22, 23, 24, 26};
    private static final int[] MOVES = {11, 12, 13, 13, 14, 15, 16, 16, 16, 16, 17, 17, 18};

    final int number;
    final Recipes.Recipe recipe;
    final int[] kinds;
    final int[] goalKinds;
    final int[] goalCounts;
    final int moves;

    Level(int number, Recipes.Recipe recipe, int[] kinds, int[] goalKinds, int[] goalCounts, int moves) {
        this.number = number;
        this.recipe = recipe;
        this.kinds = kinds;
        this.goalKinds = goalKinds;
        this.goalCounts = goalCounts;
        this.moves = moves;
    }

    boolean powerUps() {
        return number >= POWER_UP_LEVEL;
    }

    int totalNeeded() {
        int t = 0;
        for (int c : goalCounts) t += c;
        return t;
    }

    static Level get(int index) {
        return get(index, MOVES[index], SCALE[index]);
    }

    /** Ingredients the dish needs, plus enough other tiles to make five kinds. A dish with more ingredients is a bigger order. */
    static Level get(int index, int moves, int scale) {
        Recipes.Recipe rc = Recipes.ALL[index];
        int n = rc.ingredients.length, partSum = 0;
        for (int p : rc.parts) partSum += p;
        int total = Math.round(scale * (1f + 0.5f * (n - 1)));
        int[] counts = new int[n];
        for (int i = 0; i < n; i++) counts[i] = Math.max(3, Math.round(total * rc.parts[i] / (float) partSum));

        int[] kinds = new int[Math.max(5, n)];
        System.arraycopy(rc.ingredients, 0, kinds, 0, n);
        int k = n;
        for (int step = 0; k < kinds.length; step++) {
            int filler = (3 * index + 5 * step + 2) % Art.CROPS;
            boolean used = false;
            for (int i = 0; i < k; i++) used |= kinds[i] == filler;
            if (!used) kinds[k++] = filler;
        }
        return new Level(index + 1, rc, kinds, rc.ingredients.clone(), counts, moves);
    }
}
