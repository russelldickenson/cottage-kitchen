package com.zigzag.cottagekitchen;

/**
 * Level definition: one ingredient of one dish to collect, which tiles are on the board, and the move budget.
 * A dish is finished once all of its ingredient levels have been cleared.
 */
final class Level {
    static final int COUNT = Recipes.LEVELS;

    /** Power-up tiles (Golden Egg, Tractor, TNT, Scarecrow) only appear from this level number. */
    static final int POWER_UP_LEVEL = 12;

    /** Move budgets, produced by tools/sim/run.sh (tune) from how fast an expert bot collects each order. */
    private static final int[] MOVES = {
            11, 11, 11, 12, 12, 11, 12, 12, 12, 12, 12, 11, 11, 12, 12,
            13, 12, 12, 12, 13, 13, 13, 14, 13, 14, 14, 14, 15, 15, 15,
            15, 16, 15, 17, 15, 17, 16, 18, 17, 17, 18, 17, 19, 17, 18,
            18, 18, 18, 19};

    /** Position in the book, then the ingredient within the dish. */
    final int number, dish, step;
    final Recipes.Recipe recipe;
    final int[] kinds;
    final int[] goalKinds;
    final int[] goalCounts;
    final int moves;

    Level(int number, int dish, int step, int[] kinds, int goalKind, int goalCount, int moves) {
        this.number = number;
        this.dish = dish;
        this.step = step;
        this.recipe = Recipes.ALL[dish];
        this.kinds = kinds;
        this.goalKinds = new int[]{goalKind};
        this.goalCounts = new int[]{goalCount};
        this.moves = moves;
    }

    boolean powerUps() {
        return number >= POWER_UP_LEVEL;
    }

    int ingredient() {
        return goalKinds[0];
    }

    boolean lastStep() {
        return step == recipe.ingredients.length - 1;
    }

    int totalNeeded() {
        return goalCounts[0];
    }

    /** How many of the ingredient to collect: it grows steadily through the book. */
    static int scaleFor(int index) {
        return Math.round(10 + 0.45f * index);
    }

    static Level get(int index) {
        return get(index, MOVES[index], scaleFor(index));
    }

    /** The ingredient on a board of five kinds: the dish's own ingredients first, then others to fill it up. */
    static Level get(int index, int moves, int count) {
        int dish = Recipes.dishOf(index), step = index - Recipes.firstLevel(dish);
        Recipes.Recipe rc = Recipes.ALL[dish];
        int n = rc.ingredients.length;
        int[] kinds = new int[Math.max(5, n)];
        System.arraycopy(rc.ingredients, 0, kinds, 0, n);
        int k = n, filler = (7 * index + 3) % Art.CROPS;
        while (k < kinds.length) {
            boolean used = false;
            for (int i = 0; i < k; i++) used |= kinds[i] == filler;
            if (!used) kinds[k++] = filler;
            filler = (filler + 1) % Art.CROPS;
        }
        return new Level(index + 1, dish, step, kinds, rc.ingredients[step], count, moves);
    }
}
