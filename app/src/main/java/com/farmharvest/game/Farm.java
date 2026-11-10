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
    /** Eggs keep up to EGG_CAP. Feed keeps up to FEED_CAP_LEVELS levels of the herd's need (at least MIN_FEED_CAP). Above that is sold at OVERFLOW_PRICE each. */
    static final int EGG_CAP = 60, MIN_FEED_CAP = 10, OVERFLOW_PRICE = 2;
    static final float FEED_CAP_LEVELS = 2.5f;

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
    /** What each animal eats per level. Water is shared by every animal. */
    static final int CHICKEN_SEED = 2, CHICKEN_WATER = 2, COW_HAY = 4, COW_WATER = 3;
    /**
     * Fed streak: a level with the whole herd fed adds 1, a level with any hungry animal takes 2 off.
     * Every streak level adds STREAK_BONUS_PCT to what the animals pay, up to STREAK_MAX levels.
     */
    static final int STREAK_MAX = 10, STREAK_BONUS_PCT = 10, STREAK_LOSS = 2;

    static int streakPct(int streak) {
        return Math.min(streak, STREAK_MAX) * STREAK_BONUS_PCT;
    }

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
        /** Fed streak after this level, and the payout bonus it earned this level. */
        int streak, bonusPct, bonusCoins;
    }

    /** The animals and what they eat. */
    static final class Herd {
        final int[] stock = new int[STOCKS];
        final int[] house = new int[HOUSES];
        /** Chicks hatched in the last level, and chicks that have grown for one level. */
        final int[] chicks = new int[CHICK_GROW_LEVELS];
        int chickens, cows;
        /** Levels in a row the whole herd was fed (see STREAK_LOSS). */
        int streak;

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

        private int needFor(int stockKind, int hens) {
            switch (stockKind) {
                case SEED:
                    return hens * CHICKEN_SEED;
                case HAY:
                    return cows * COW_HAY;
                case WATER:
                    return hens * CHICKEN_WATER + cows * COW_WATER;
                default:
                    return 0;
            }
        }

        /** Stock of each kind the animals need to be fully fed after the next level. */
        int need(int stockKind) {
            return needFor(stockKind, chickens);
        }

        /** The most of a stock the farm keeps. Chicks count as hens, since they will be eating soon. */
        int cap(int stockKind) {
            if (stockKind == EGG) return EGG_CAP;
            return Math.max(MIN_FEED_CAP, (int) Math.ceil(FEED_CAP_LEVELS * needFor(stockKind, chickens + chickCount())));
        }

        /** Eggs worth banking: two chicks' worth while there is, or soon will be, room in the coop. */
        private int eggWant() {
            if (house[COOP] == 0) return 2 * EGGS_PER_CHICK;
            return EGGS_PER_CHICK * Math.max(0, Math.min(chickenRoom(), 2));
        }

        /** How much of a stock the farm wants to have after the next level: feed for the herd, or eggs to hatch. */
        int want(int stockKind) {
            return stockKind == EGG ? eggWant() : need(stockKind);
        }

        /** How many more of a stock the farm still needs, given what is banked and what a level has hauled so far. */
        int shortfall(int stockKind, int hauled) {
            return Math.max(0, want(stockKind) - stock[stockKind] - hauled);
        }

        /** 0 (empty) to 1 (as full as the farm wants) for a stock. */
        float fill(int stockKind) {
            if (stockKind == EGG) {
                int w = eggWant();
                return w == 0 ? 1f : Math.min(1f, stock[EGG] / (float) w);
            }
            return Math.min(1f, stock[stockKind] / (float) cap(stockKind));
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
                int cap = cap(s);
                if (stock[s] > cap) {
                    rp.soldOverflow += (stock[s] - cap) * OVERFLOW_PRICE;
                    stock[s] = cap;
                }
            }
            rp.coins += rp.soldOverflow;

            // cows come first: they pay more for the shared water
            rp.cowsFed = Math.min(cows, Math.min(stock[HAY] / COW_HAY, stock[WATER] / COW_WATER));
            stock[HAY] -= rp.cowsFed * COW_HAY;
            stock[WATER] -= rp.cowsFed * COW_WATER;
            rp.chickensFed = Math.min(chickens, Math.min(stock[SEED] / CHICKEN_SEED, stock[WATER] / CHICKEN_WATER));
            stock[SEED] -= rp.chickensFed * CHICKEN_SEED;
            stock[WATER] -= rp.chickensFed * CHICKEN_WATER;
            rp.hungry = cows - rp.cowsFed + chickens - rp.chickensFed;
            int base = rp.cowsFed * COW_COINS + rp.chickensFed * CHICKEN_COINS;

            if (chickens + cows > 0) streak = rp.hungry == 0 ? Math.min(streak + 1, STREAK_MAX) : Math.max(0, streak - STREAK_LOSS);
            rp.streak = streak;
            rp.bonusPct = streakPct(streak);
            rp.bonusCoins = Math.round(base * rp.bonusPct / 100f);
            rp.coins += base + rp.bonusCoins;

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
