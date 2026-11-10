package com.farmharvest.game;

/** Farm economy: crop prices, the livestock herd (eggs, feed, coop, barn, chickens, cows), decorations and boosters. No Android types. */
final class Farm {
    /**
     * What one harvested crop sells for at the end of a level, indexed by crop id.
     * Eggs, hay, sunflower seed and water are 0: they go into the farm stock instead (see STOCK_TILE).
     */
    static final int[] BASE_PRICE = {8, 10, 8, 12, 8, 16, 0, 6, 18, 0, 0, 20, 0};

    // boosters
    static final int MOVES = 0, SHUFFLE = 1, HAMMER = 2, BOOSTERS = 3;
    static final String[] BOOSTER_NAME = {"Extra Moves", "Shuffle", "Hammer"};
    static final String[] BOOSTER_INFO = {"Adds 5 moves.", "Reshuffles the board.", "Smashes any one tile."};
    static final int[] BOOSTER_PRICE = {400, 250, 350};
    static final int EXTRA_MOVES = 5;

    // ---- farm stock: tiles cleared on the board that are kept for the animals
    static final int EGG = 0, SEED = 1, HAY = 2, WATER = 3, STOCKS = 4;
    /** The tile type (Art id) that fills each stock. */
    static final int[] STOCK_TILE = {6, 10, 9, 12};
    static final String[] STOCK_NAME = {"Eggs", "Seed", "Hay", "Water"};
    /** The most of each stock the farm keeps. Anything above is sold at OVERFLOW_PRICE each. */
    static final int STOCK_CAP = 60, OVERFLOW_PRICE = 2;

    // ---- housing
    static final int COOP = 0, BARN = 1, HOUSES = 2;
    static final String[] HOUSE_NAME = {"Coop", "Barn"};
    /** Price of building each house (level 1) and upgrading it (levels 2 and 3). */
    static final int[][] HOUSE_COST = {{300, 800, 1800}, {800, 1800, 3600}};
    /** Animals each house holds at level 1, 2, 3. */
    static final int[][] HOUSE_CAPACITY = {{4, 8, 12}, {2, 4, 6}};
    /** A house can only be built once this level has been reached. */
    static final int[] HOUSE_UNLOCK_LEVEL = {1, 4};
    static final int MAX_HOUSE_LEVEL = 3;

    // ---- animals
    static final int EGGS_PER_CHICK = 5, CHICK_GROW_LEVELS = 2, COW_PRICE = 500;
    /** Coins an animal that was fed (1 feed + 1 water) pays out after each level. */
    static final int CHICKEN_COINS = 15, COW_COINS = 45;

    // purely cosmetic
    static final int FENCE = 0, FLOWERS = 1, POND = 2, OAK = 3, WINDMILL = 4, BALLOON = 5, DECORS = 6;
    static final String[] DECOR_NAME = {"Fence", "Flowers", "Pond", "Oak Tree", "Windmill", "Balloon"};
    static final int[] DECOR_COST = {500, 900, 1500, 2300, 3300, 5000};

    /** Stock index a cleared tile type goes to, or -1 when the crop is sold instead. */
    static int stockOf(int tileType) {
        for (int s = 0; s < STOCKS; s++) if (STOCK_TILE[s] == tileType) return s;
        return -1;
    }

    static int capacity(int house, int level) {
        return level <= 0 ? 0 : HOUSE_CAPACITY[house][Math.min(level, MAX_HOUSE_LEVEL) - 1];
    }

    /** Coins from selling the crops harvested in a level (stock tiles are not sold). */
    static int saleValue(int[] harvest) {
        int v = 0;
        for (int t = 0; t < harvest.length; t++) v += harvest[t] * BASE_PRICE[t];
        return v;
    }

    /** Level payout: play coins, crop sales and what the animals earned. */
    static int payout(int playCoins, int saleCoins, int animalCoins) {
        return playCoins + saleCoins + animalCoins;
    }

    /** What happened on the farm when a level ended. */
    static final class Report {
        /** Tiles brought in from the board, per stock (before the cap). */
        final int[] hauled = new int[STOCKS];
        int chickensFed, cowsFed, hungry, grown, coins, soldOverflow;
    }

    /** The animals and what they eat. */
    static final class Herd {
        final int[] stock = new int[STOCKS];
        final int[] house = new int[HOUSES];
        /** Chicks hatched in the last level, and chicks that have grown for one level. */
        final int[] chicks = new int[CHICK_GROW_LEVELS];
        int chickens, cows;

        int chickCount() {
            int n = 0;
            for (int c : chicks) n += c;
            return n;
        }

        int chickenRoom() {
            return capacity(COOP, house[COOP]) - chickens - chickCount();
        }

        int cowRoom() {
            return capacity(BARN, house[BARN]) - cows;
        }

        int hungryCows() {
            return Math.max(0, cows - Math.min(stock[HAY], stock[WATER]));
        }

        /** Stock of each kind the animals need to be fully fed after the next level. */
        int need(int stockKind) {
            switch (stockKind) {
                case SEED:
                    return chickens;
                case HAY:
                    return cows;
                case WATER:
                    return chickens + cows;
                default:
                    return 0;
            }
        }

        /** True when 5 eggs and a free spot in the coop are available. */
        boolean canHatch() {
            return stock[EGG] >= EGGS_PER_CHICK && chickenRoom() > 0;
        }

        void hatch() {
            stock[EGG] -= EGGS_PER_CHICK;
            chicks[0]++;
        }

        /** The end-of-level farm step: bank the harvest, feed and milk the herd, grow the chicks. */
        Report endLevel(int[] harvest) {
            Report rp = new Report();
            for (int t = 0; t < harvest.length; t++) {
                int s = stockOf(t);
                if (s < 0 || harvest[t] == 0) continue;
                rp.hauled[s] += harvest[t];
                stock[s] += harvest[t];
            }
            for (int s = 0; s < STOCKS; s++) {
                if (stock[s] > STOCK_CAP) {
                    rp.soldOverflow += (stock[s] - STOCK_CAP) * OVERFLOW_PRICE;
                    stock[s] = STOCK_CAP;
                }
            }
            rp.coins += rp.soldOverflow;

            // cows come first: they pay more for the shared water
            rp.cowsFed = Math.min(cows, Math.min(stock[HAY], stock[WATER]));
            stock[HAY] -= rp.cowsFed;
            stock[WATER] -= rp.cowsFed;
            rp.chickensFed = Math.min(chickens, Math.min(stock[SEED], stock[WATER]));
            stock[SEED] -= rp.chickensFed;
            stock[WATER] -= rp.chickensFed;
            rp.hungry = cows - rp.cowsFed + chickens - rp.chickensFed;
            rp.coins += rp.cowsFed * COW_COINS + rp.chickensFed * CHICKEN_COINS;

            rp.grown = chicks[chicks.length - 1];
            chickens += rp.grown;
            for (int i = chicks.length - 1; i > 0; i--) chicks[i] = chicks[i - 1];
            chicks[0] = 0;
            return rp;
        }
    }

    private Farm() {
    }
}
