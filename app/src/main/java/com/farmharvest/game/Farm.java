package com.farmharvest.game;

/**
 * Farm economy: crop prices, the livestock (five species, each with its own house, feed and payout) and decorations.
 */
final class Farm {
    /**
     * What one harvested crop sells for at the end of a level, indexed by crop id.
     * Eggs, potatoes, hay, sunflower seed and water are 0: they go into the farm stock instead (see STOCK_TILE).
     */
    static final int[] BASE_PRICE = {24, 30, 24, 36, 24, 48, 0, 0, 54, 0, 0, 60, 0};


    // ---- farm stock: tiles cleared on the board that are kept for the animals
    static final int EGG = 0, SEED = 1, HAY = 2, WATER = 3, POTATO = 4, STOCKS = 5;
    /** The tile type (Art id) that fills each stock. */
    static final int[] STOCK_TILE = {6, 10, 9, 12, 7};
    static final String[] STOCK_NAME = {"Eggs", "Seed", "Hay", "Water", "Potatoes"};
    /** Eggs keep up to EGG_CAP. Feed keeps up to FEED_CAP_LEVELS levels of the herd's need (at least MIN_FEED_CAP). Above that is sold at OVERFLOW_PRICE each. */
    static final int EGG_CAP = 60, MIN_FEED_CAP = 10, OVERFLOW_PRICE = 2;
    static final float FEED_CAP_LEVELS = 2.25f;

    // ---- species. Each has one house of the same index.
    static final int CHICKEN = 0, COW = 1, PIG = 2, SHEEP = 3, BEE = 4, SPECIES = 5;
    static final String[] HOUSE_NAME = {"Coop", "Barn", "Pig Sty", "Sheep Pen", "Beehive"};
    static final String[] ANIMAL_NAME = {"Hens", "Cows", "Pigs", "Sheep", "Bees"};
    /** A house can only be built once this level has been reached. */
    static final int[] HOUSE_UNLOCK_LEVEL = {1, 4, 7, 10, 13};
    /** Animals each house holds at level 1, 2, 3. */
    static final int[][] HOUSE_CAPACITY = {{4, 8, 12}, {2, 4, 6}, {2, 4, 6}, {3, 6, 9}, {3, 6, 9}};
    static final int MAX_HOUSE_LEVEL = 3;
    /** Coins a fed animal pays out after each level, before the streak bonus. */
    static final int[] COINS = {15, 80, 60, 50, 35};
    /** What each animal eats per level, per stock (EGG is never eaten). Water is shared by every animal. */
    static final int[][] FEED = {
            {0, 1, 0, 1, 0},   // hen: seed, water
            {0, 0, 2, 2, 0},   // cow: hay, water
            {0, 0, 0, 2, 3},   // pig: potatoes, water
            {0, 0, 1, 1, 0},   // sheep: hay, water
            {0, 1, 0, 1, 0},   // bee: seed, water
    };
    /** Pricing is by payback: an animal costs ANIMAL_PAYBACK levels of its payout, and a house level HOUSE_PAYBACK levels of the extra payout it unlocks. */
    static final int ANIMAL_PAYBACK = 8, HOUSE_PAYBACK = 10;
    static final int EGGS_PER_CHICK = 5, CHICK_GROW_LEVELS = 1;
    /** Feeding order: the best payers get the shared water first. */
    private static final int[] FEED_ORDER = {COW, PIG, SHEEP, BEE, CHICKEN};

    /**
     * Fed streak: a level with the whole herd fed adds 1, a level with any hungry animal takes 2 off.
     * Every streak level adds STREAK_BONUS_PCT to what the animals pay, up to STREAK_MAX levels.
     */
    static final int STREAK_MAX = 10, STREAK_BONUS_PCT = 10, STREAK_LOSS = 2;

    // purely cosmetic
    static final int FENCE = 0, FLOWERS = 1, POND = 2, OAK = 3, WINDMILL = 4, BALLOON = 5, DECORS = 6;
    static final String[] DECOR_NAME = {"Fence", "Flowers", "Pond", "Oak Tree", "Windmill", "Balloon"};
    static final int[] DECOR_COST = {500, 900, 1500, 2300, 3300, 5000};

    static int streakPct(int streak) {
        return Math.min(streak, STREAK_MAX) * STREAK_BONUS_PCT;
    }

    /** Stock index a cleared tile type goes to, or -1 when the crop is sold instead. */
    static int stockOf(int tileType) {
        for (int s = 0; s < STOCKS; s++) if (STOCK_TILE[s] == tileType) return s;
        return -1;
    }

    static int capacity(int species, int level) {
        return level <= 0 ? 0 : HOUSE_CAPACITY[species][Math.min(level, MAX_HOUSE_LEVEL) - 1];
    }

    /** Price of building a house (level 0 to 1) or upgrading it, or -1 when it is fully upgraded. */
    static int houseCost(int species, int level) {
        if (level >= MAX_HOUSE_LEVEL) return -1;
        int extra = capacity(species, level + 1) - capacity(species, level);
        return Math.round(HOUSE_PAYBACK * extra * COINS[species] / 50f) * 50;
    }

    /** Price of one animal bought with coins. Hens are hatched from eggs and cost none. */
    static int animalPrice(int species) {
        return species == CHICKEN ? 0 : ANIMAL_PAYBACK * COINS[species];
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
        final int[] fed = new int[SPECIES];
        int hungry, grown, hatched, coins, soldOverflow;
        /** Fed streak after this level, and the payout bonus it earned this level. */
        int streak, bonusPct, bonusCoins;
    }

    /** The animals and what they eat. */
    static final class Herd {
        final int[] stock = new int[STOCKS];
        final int[] house = new int[SPECIES];
        final int[] count = new int[SPECIES];
        /** Chicks hatched in the last level, waiting to grow into hens (CHICK_GROW_LEVELS levels). */
        final int[] chicks = new int[CHICK_GROW_LEVELS];
        /** Levels in a row the whole herd was fed (see STREAK_LOSS). */
        int streak;

        int chickCount() {
            int n = 0;
            for (int c : chicks) n += c;
            return n;
        }

        int animals() {
            int n = chickCount();
            for (int c : count) n += c;
            return n;
        }

        /** Free places in a species' house. Chicks take a hen's place. */
        int room(int species) {
            return capacity(species, house[species]) - count[species] - (species == CHICKEN ? chickCount() : 0);
        }

        private int needFor(int stockKind, boolean withChicks) {
            int n = 0;
            for (int sp = 0; sp < SPECIES; sp++) n += (count[sp] + (withChicks && sp == CHICKEN ? chickCount() : 0)) * FEED[sp][stockKind];
            return n;
        }

        /** Stock of each kind the animals need to be fully fed after the next level. */
        int need(int stockKind) {
            return needFor(stockKind, false);
        }

        /** The most of a stock the farm keeps. Chicks count as hens, since they will be eating soon. */
        int cap(int stockKind) {
            if (stockKind == EGG) return EGG_CAP;
            return Math.max(MIN_FEED_CAP, (int) Math.ceil(FEED_CAP_LEVELS * needFor(stockKind, true)));
        }

        /** Eggs worth banking: two chicks' worth while there is, or soon will be, room in the coop. */
        private int eggWant() {
            if (house[CHICKEN] == 0) return 2 * EGGS_PER_CHICK;
            return EGGS_PER_CHICK * Math.max(0, Math.min(room(CHICKEN), 2));
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
            return stock[EGG] >= EGGS_PER_CHICK && room(CHICKEN) > 0;
        }

        void hatch() {
            stock[EGG] -= EGGS_PER_CHICK;
            chicks[0]++;
        }

        /** Eggs hatch by themselves whenever there are 5 and room in the coop. Returns how many chicks hatched. */
        int autoHatch() {
            int n = 0;
            while (canHatch()) {
                hatch();
                n++;
            }
            return n;
        }

        /** How many animals of each species the stock plus what this level has harvested so far would feed right now. Mirrors endLevel's feeding. */
        int[] provisionalFed(int[] harvest) {
            int[] avail = new int[STOCKS];
            System.arraycopy(stock, 0, avail, 0, STOCKS);
            for (int t = 0; t < harvest.length; t++) {
                int s = stockOf(t);
                if (s >= 0) avail[s] += harvest[t];
            }
            for (int s = 0; s < STOCKS; s++) avail[s] = Math.min(avail[s], cap(s));
            int[] fed = new int[SPECIES];
            for (int sp : FEED_ORDER) {
                int f = count[sp];
                for (int s = 0; s < STOCKS; s++) if (FEED[sp][s] > 0) f = Math.min(f, avail[s] / FEED[sp][s]);
                for (int s = 0; s < STOCKS; s++) avail[s] -= f * FEED[sp][s];
                fed[sp] = f;
            }
            return fed;
        }

        /** The end-of-level farm step: bank the harvest, feed and pay the herd, grow the chicks. */
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

            int base = 0, animals = 0;
            for (int sp : FEED_ORDER) {
                int fed = count[sp];
                for (int s = 0; s < STOCKS; s++) if (FEED[sp][s] > 0) fed = Math.min(fed, stock[s] / FEED[sp][s]);
                for (int s = 0; s < STOCKS; s++) stock[s] -= fed * FEED[sp][s];
                rp.fed[sp] = fed;
                rp.hungry += count[sp] - fed;
                animals += count[sp];
                base += fed * COINS[sp];
            }

            if (animals > 0) streak = rp.hungry == 0 ? Math.min(streak + 1, STREAK_MAX) : Math.max(0, streak - STREAK_LOSS);
            rp.streak = streak;
            rp.bonusPct = streakPct(streak);
            rp.bonusCoins = Math.round(base * rp.bonusPct / 100f);
            rp.coins += base + rp.bonusCoins;

            rp.grown = chicks[chicks.length - 1];
            count[CHICKEN] += rp.grown;
            for (int i = chicks.length - 1; i > 0; i--) chicks[i] = chicks[i - 1];
            chicks[0] = 0;
            rp.hatched = autoHatch();
            return rp;
        }
    }

    private Farm() {
    }
}
