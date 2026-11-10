package com.farmharvest.game;

import android.content.Context;
import android.content.SharedPreferences;

/** Everything that survives between sessions: levels, coins, farm upgrades, decorations, boosters and tutorial cards seen. */
final class Progress {
    int unlocked, totalCoins;
    boolean soundOn;
    final int[] bestStars = new int[Level.COUNT];
    final boolean[] tipSeen = new boolean[Art.COUNT];
    final int[] boosters = new int[Farm.BOOSTERS];
    final Farm.Herd herd = new Farm.Herd();
    final boolean[] decor = new boolean[Farm.DECORS];
    private final SharedPreferences prefs;

    Progress(Context ctx) {
        prefs = ctx.getSharedPreferences("farm", Context.MODE_PRIVATE);
        unlocked = Math.max(1, prefs.getInt("unlocked", 1));
        totalCoins = prefs.getInt("coins", 0);
        soundOn = prefs.getBoolean("sound", true);
        for (int t = Art.CROPS; t < Art.COUNT; t++) tipSeen[t] = prefs.getBoolean("tip_" + t, false);
        for (int i = 0; i < Level.COUNT; i++) bestStars[i] = prefs.getInt("stars_" + i, 0);
        for (int i = 0; i < boosters.length; i++) boosters[i] = prefs.getInt("booster_" + i, 0);
        for (int i = 0; i < decor.length; i++) decor[i] = prefs.getBoolean("decor_" + i, false);
        loadHerd();
        refundOldUpgrades();
    }

    private void loadHerd() {
        for (int i = 0; i < herd.stock.length; i++) herd.stock[i] = prefs.getInt("stock_" + i, 0);
        for (int i = 0; i < herd.house.length; i++) herd.house[i] = prefs.getInt("house_" + i, 0);
        for (int i = 0; i < herd.chicks.length; i++) herd.chicks[i] = prefs.getInt("chicks_" + i, 0);
        herd.chickens = prefs.getInt("chickens", 0);
        herd.cows = prefs.getInt("cows", 0);
    }

    /** The old flat-bonus output upgrades were replaced by animals: pay back what was spent on them, once. */
    private void refundOldUpgrades() {
        if (prefs.getBoolean("outputs_refunded", false)) return;
        final int[][] oldCost = {{600, 1800, 4200}, {1000, 2600, 6000}, {1500, 3600, 7800}, {2200, 5400, 10800}};
        SharedPreferences.Editor e = prefs.edit();
        for (int k = 0; k < oldCost.length; k++) {
            int lvl = Math.min(prefs.getInt("output_" + k, 0), 3);
            for (int l = 0; l < lvl; l++) totalCoins += oldCost[k][l];
            e.remove("output_" + k);
        }
        e.putInt("coins", totalCoins).putBoolean("outputs_refunded", true).apply();
    }

    private void saveShop() {
        SharedPreferences.Editor e = prefs.edit().putInt("coins", totalCoins);
        for (int i = 0; i < boosters.length; i++) e.putInt("booster_" + i, boosters[i]);
        for (int i = 0; i < decor.length; i++) e.putBoolean("decor_" + i, decor[i]);
        for (int i = 0; i < herd.stock.length; i++) e.putInt("stock_" + i, herd.stock[i]);
        for (int i = 0; i < herd.house.length; i++) e.putInt("house_" + i, herd.house[i]);
        for (int i = 0; i < herd.chicks.length; i++) e.putInt("chicks_" + i, herd.chicks[i]);
        e.putInt("chickens", herd.chickens).putInt("cows", herd.cows);
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

    // ---- farm

    /** Cost of building or upgrading a house, or -1 when it is fully upgraded. */
    int houseCost(int h) {
        return herd.house[h] >= Farm.MAX_HOUSE_LEVEL ? -1 : Farm.HOUSE_COST[h][herd.house[h]];
    }

    boolean houseUnlocked(int h) {
        return unlocked >= Farm.HOUSE_UNLOCK_LEVEL[h];
    }

    /** Builds or upgrades a house. Returns null on success, otherwise why it could not be done. */
    String buildHouse(int h) {
        int cost = houseCost(h);
        if (cost < 0) return "Fully upgraded";
        if (!houseUnlocked(h)) return "Beat level " + (Farm.HOUSE_UNLOCK_LEVEL[h] - 1) + " first";
        if (totalCoins < cost) return "Not enough coins";
        totalCoins -= cost;
        herd.house[h]++;
        saveShop();
        return null;
    }

    /** Turns 5 eggs into a chick. Returns null on success, otherwise why not. */
    String hatchChick() {
        if (herd.house[Farm.COOP] == 0) return "Build a coop first";
        if (herd.stock[Farm.EGG] < Farm.EGGS_PER_CHICK) return "Need " + Farm.EGGS_PER_CHICK + " eggs";
        if (herd.chickenRoom() <= 0) return "Coop is full";
        herd.hatch();
        saveShop();
        return null;
    }

    String buyCow() {
        if (herd.house[Farm.BARN] == 0) return "Build a barn first";
        if (herd.cowRoom() <= 0) return "Barn is full";
        if (totalCoins < Farm.COW_PRICE) return "Not enough coins";
        totalCoins -= Farm.COW_PRICE;
        herd.cows++;
        saveShop();
        return null;
    }

    /** Banks a level's harvest into the farm stock, feeds the animals and returns what happened. The coins are paid by the caller. */
    Farm.Report farmRound(int[] harvest) {
        Farm.Report rp = herd.endLevel(harvest);
        saveShop();
        return rp;
    }

    boolean buyDecor(int k) {
        if (decor[k] || totalCoins < Farm.DECOR_COST[k]) return false;
        totalCoins -= Farm.DECOR_COST[k];
        decor[k] = true;
        saveShop();
        return true;
    }

    // ---- boosters

    boolean buyBooster(int k) {
        if (totalCoins < Farm.BOOSTER_PRICE[k]) return false;
        totalCoins -= Farm.BOOSTER_PRICE[k];
        boosters[k]++;
        saveShop();
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
        saveShop();
        return true;
    }
}
