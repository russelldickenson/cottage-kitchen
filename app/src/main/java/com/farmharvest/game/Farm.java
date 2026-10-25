package com.farmharvest.game;

/** Farm economy: what crops sell for, output upgrades, decorations and boosters. No Android types. */
final class Farm {
    /** What one harvested crop sells for at the end of a level, indexed by crop id. */
    static final int[] BASE_PRICE = {8, 10, 8, 12, 8, 16, 10, 6, 18, 14, 12, 20, 4};

    // boosters
    static final int MOVES = 0, SHUFFLE = 1, HAMMER = 2, BOOSTERS = 3;
    static final String[] BOOSTER_NAME = {"Extra Moves", "Shuffle", "Hammer"};
    static final String[] BOOSTER_INFO = {"Adds 5 moves.", "Reshuffles the board.", "Smashes any one tile."};
    static final int[] BOOSTER_PRICE = {400, 250, 350};
    static final int EXTRA_MOVES = 5;

    // output upgrades: each level adds a share of extra coins to every level payout
    static final int COOP = 0, HIVE = 1, ORCHARD = 2, PATCH = 3, OUTPUTS = 4;
    static final String[] OUTPUT_NAME = {"Coop", "Beehive", "Orchard", "Pumpkins"};
    static final int[][] OUTPUT_COST = {
            {600, 1800, 4200},
            {1000, 2600, 6000},
            {1500, 3600, 7800},
            {2200, 5400, 10800},
    };
    static final int MAX_OUTPUT_LEVEL = 3, BONUS_PCT_PER_LEVEL = 5;

    // purely cosmetic
    static final int FENCE = 0, FLOWERS = 1, POND = 2, OAK = 3, WINDMILL = 4, BALLOON = 5, DECORS = 6;
    static final String[] DECOR_NAME = {"Fence", "Flowers", "Pond", "Oak Tree", "Windmill", "Balloon"};
    static final int[] DECOR_COST = {500, 900, 1500, 2300, 3300, 5000};

    /** Total extra-coin share from all output upgrades, in percent. */
    static int bonusPct(int[] outputLevel) {
        int pct = 0;
        for (int l : outputLevel) pct += l * BONUS_PCT_PER_LEVEL;
        return pct;
    }

    /** Coins from selling everything harvested in a level. */
    static int saleValue(int[] harvest) {
        int v = 0;
        for (int t = 0; t < harvest.length; t++) v += harvest[t] * BASE_PRICE[t];
        return v;
    }

    /** Level payout: play coins plus crop sales, plus the farm bonus on top. */
    static int payout(int playCoins, int saleCoins, int bonusPct) {
        return Math.round((playCoins + saleCoins) * (100 + bonusPct) / 100f);
    }

    private Farm() {
    }
}
