package com.farmharvest.game;

import android.content.Context;
import android.content.SharedPreferences;

/** Everything that survives between sessions: levels, coins, the barn, upgrades, boosters and tutorial cards seen. */
final class Progress {
    int unlocked, totalCoins;
    boolean soundOn;
    final int[] bestStars = new int[Level.COUNT];
    final boolean[] tipSeen = new boolean[Art.COUNT];
    final int[] barn = new int[Art.CROPS];
    final int[] building = new int[Farm.UPGRADES];
    final int[] boosters = new int[Farm.BOOSTERS];
    private final SharedPreferences prefs;

    Progress(Context ctx) {
        prefs = ctx.getSharedPreferences("farm", Context.MODE_PRIVATE);
        unlocked = Math.max(1, prefs.getInt("unlocked", 1));
        totalCoins = prefs.getInt("coins", 0);
        soundOn = prefs.getBoolean("sound", true);
        for (int t = Art.CROPS; t < Art.COUNT; t++) tipSeen[t] = prefs.getBoolean("tip_" + t, false);
        for (int i = 0; i < Level.COUNT; i++) bestStars[i] = prefs.getInt("stars_" + i, 0);
        for (int i = 0; i < barn.length; i++) barn[i] = prefs.getInt("barn_" + i, 0);
        for (int i = 0; i < building.length; i++) building[i] = prefs.getInt("building_" + i, 0);
        for (int i = 0; i < boosters.length; i++) boosters[i] = prefs.getInt("booster_" + i, 0);
    }

    private void saveFarm() {
        SharedPreferences.Editor e = prefs.edit().putInt("coins", totalCoins);
        for (int i = 0; i < barn.length; i++) e.putInt("barn_" + i, barn[i]);
        for (int i = 0; i < building.length; i++) e.putInt("building_" + i, building[i]);
        for (int i = 0; i < boosters.length; i++) e.putInt("booster_" + i, boosters[i]);
        e.apply();
    }

    /** Records a finished level. stars is 0 for a loss. */
    void levelFinished(int levelIndex, int stars, int coinsEarned) {
        if (stars > 0) {
            bestStars[levelIndex] = Math.max(bestStars[levelIndex], stars);
            unlocked = Math.max(unlocked, Math.min(Level.COUNT, levelIndex + 2));
        }
        totalCoins += coinsEarned;
        prefs.edit().putInt("coins", totalCoins).putInt("unlocked", unlocked)
                .putInt("stars_" + levelIndex, bestStars[levelIndex]).apply();
    }

    void setSound(boolean on) {
        soundOn = on;
        prefs.edit().putBoolean("sound", on).apply();
    }

    void tipShown(int type) {
        tipSeen[type] = true;
        prefs.edit().putBoolean("tip_" + type, true).apply();
    }

    // ---- barn and market

    int barnTotal() {
        int n = 0;
        for (int v : barn) n += v;
        return n;
    }

    int capacity() {
        return Farm.capacity(building[Farm.BARN]);
    }

    /**
     * Stores a level's harvest. Crops that do not fit are sold straight away at half the day's price.
     * Returns {crops stored, coins from overflow}.
     */
    int[] addHarvest(int[] counts, long day) {
        int stored = 0, overflowCoins = 0, total = barnTotal(), cap = capacity();
        for (int type = 0; type < Art.CROPS; type++) {
            for (int i = 0; i < counts[type]; i++) {
                if (total < cap) {
                    barn[type]++;
                    total++;
                    stored++;
                } else {
                    overflowCoins += Math.max(1, Farm.price(type, day, building[Farm.WELL]) / 2);
                }
            }
        }
        totalCoins += overflowCoins;
        saveFarm();
        return new int[]{stored, overflowCoins};
    }

    int sellValue(int type, long day) {
        return barn[type] * Farm.price(type, day, building[Farm.WELL]);
    }

    int sellValueAll(long day) {
        int v = 0;
        for (int t = 0; t < barn.length; t++) v += sellValue(t, day);
        return v;
    }

    /** Sells every unit of one crop. Returns the coins earned. */
    int sell(int type, long day) {
        int v = sellValue(type, day);
        barn[type] = 0;
        totalCoins += v;
        saveFarm();
        return v;
    }

    int sellAll(long day) {
        int v = sellValueAll(day);
        java.util.Arrays.fill(barn, 0);
        totalCoins += v;
        saveFarm();
        return v;
    }

    // ---- shop

    boolean buyUpgrade(int b) {
        int cost = Farm.nextCost(b, building[b]);
        if (cost < 0 || totalCoins < cost) return false;
        totalCoins -= cost;
        building[b]++;
        saveFarm();
        return true;
    }

    boolean buyBooster(int k) {
        if (totalCoins < Farm.BOOSTER_PRICE[k]) return false;
        totalCoins -= Farm.BOOSTER_PRICE[k];
        boosters[k]++;
        saveFarm();
        return true;
    }

    /** Uses one booster, buying it on the spot if none are owned. Returns false when it cannot be afforded. */
    boolean takeBooster(int k) {
        if (boosters[k] > 0) {
            boosters[k]--;
        } else if (totalCoins >= Farm.BOOSTER_PRICE[k]) {
            totalCoins -= Farm.BOOSTER_PRICE[k];
        } else {
            return false;
        }
        saveFarm();
        return true;
    }
}
