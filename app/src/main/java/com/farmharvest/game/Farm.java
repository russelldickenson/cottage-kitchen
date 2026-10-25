package com.farmharvest.game;

import java.util.Random;

/** Farm economy: crop prices and the daily market, barn capacity, upgrades and boosters. No Android types. */
final class Farm {
    static final String[] NAMES = {"Corn", "Strawberry", "Orange", "Blueberry", "Apple", "Pumpkin", "Egg", "Potato",
            "Watermelon", "Milk", "Sunflower", "Honey", "Water"};
    static final int[] BASE_PRICE = {8, 10, 8, 12, 8, 16, 10, 6, 18, 14, 12, 20, 4};

    // boosters
    static final int MOVES = 0, SHUFFLE = 1, HAMMER = 2, BOOSTERS = 3;
    static final String[] BOOSTER_NAME = {"Extra Moves", "Shuffle", "Hammer"};
    static final String[] BOOSTER_INFO = {"Adds 5 moves.", "Reshuffles the board.", "Smashes any one tile."};
    static final int[] BOOSTER_PRICE = {400, 250, 350};
    static final int EXTRA_MOVES = 5;

    // upgradable buildings
    static final int BARN = 0, WELL = 1, PLOT = 2, UPGRADES = 3;
    static final String[] BUILDING_NAME = {"Barn", "Well", "Scarecrow Plot"};
    static final int[][] UPGRADE_COST = {
            {1200, 2500, 5000, 7000},
            {1500, 3000, 5000, 7500, 10000},
            {3000, 6500, 11000},
    };
    static final int BASE_CAPACITY = 200, CAPACITY_STEP = 100;
    static final float SELL_BONUS_STEP = 0.10f;

    static int maxLevel(int building) {
        return UPGRADE_COST[building].length;
    }

    /** Cost of the next level of a building, or -1 when it is fully upgraded. */
    static int nextCost(int building, int level) {
        return level >= maxLevel(building) ? -1 : UPGRADE_COST[building][level];
    }

    static int capacity(int barnLevel) {
        return BASE_CAPACITY + CAPACITY_STEP * barnLevel;
    }

    static int startingPowerUps(int plotLevel) {
        return plotLevel;
    }

    /** What a building does at the given level, in one line. */
    static String effect(int building, int level) {
        switch (building) {
            case BARN:
                return "Holds " + capacity(level) + " crops";
            case WELL:
                return level == 0 ? "Normal sell prices" : "+" + Math.round(SELL_BONUS_STEP * 100 * level) + "% sell prices";
            default:
                return level == 0 ? "No head start" : level + " power-up" + (level > 1 ? "s" : "") + " (Lv " + Level.POWER_UP_LEVEL + "+)";
        }
    }

    // market

    static long today() {
        return System.currentTimeMillis() / 86400000L;
    }

    static int specialCrop(long day) {
        return new Random(day * 7919L + 11).nextInt(Art.CROPS);
    }

    /** Today's price swing for a crop: roughly 0.75x to 1.35x, and 1.8x for the crop of the day. */
    static float marketMultiplier(int type, long day) {
        if (type == specialCrop(day)) return 1.8f;
        return 0.75f + 0.6f * new Random(day * 1009L + type * 7919L).nextFloat();
    }

    /** +1 when the crop is selling well today, -1 when poorly, 0 otherwise. */
    static int trend(int type, long day) {
        float m = marketMultiplier(type, day);
        return m >= 1.12f ? 1 : m <= 0.88f ? -1 : 0;
    }

    static int price(int type, long day, int wellLevel) {
        return Math.max(1, Math.round(BASE_PRICE[type] * marketMultiplier(type, day) * (1f + SELL_BONUS_STEP * wellLevel)));
    }

    private Farm() {
    }
}
