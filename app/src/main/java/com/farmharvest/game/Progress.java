package com.farmharvest.game;

import android.content.Context;
import android.content.SharedPreferences;

/** Everything that survives between sessions: levels, coins, farm upgrades, decorations and tutorial cards seen. */
final class Progress {
    int unlocked, totalCoins;
    boolean soundOn, farmTipSeen;
    final int[] bestStars = new int[Level.COUNT];
    final boolean[] tipSeen = new boolean[Art.COUNT];
    final Farm.Herd herd = new Farm.Herd();
    final boolean[] decor = new boolean[Farm.DECORS];
    private final SharedPreferences prefs;

    Progress(Context ctx) {
        prefs = ctx.getSharedPreferences("farm", Context.MODE_PRIVATE);
        unlocked = Math.max(1, prefs.getInt("unlocked", 1));
        totalCoins = prefs.getInt("coins", 0);
        soundOn = prefs.getBoolean("sound", true);
        farmTipSeen = prefs.getBoolean("tip_farm", false);
        for (int t = Art.CROPS; t < Art.COUNT; t++) tipSeen[t] = prefs.getBoolean("tip_" + t, false);
        for (int i = 0; i < Level.COUNT; i++) bestStars[i] = prefs.getInt("stars_" + i, 0);
        for (int i = 0; i < decor.length; i++) decor[i] = prefs.getBoolean("decor_" + i, false);
        boolean freshInstall = !prefs.contains("unlocked") && !prefs.contains("coins") && !prefs.contains("house_0");
        loadHerd();
        refundOldUpgrades();
        if (freshInstall) {
            // a new farm starts with a coop and two hens, so the first level already has animals to feed
            herd.house[Farm.CHICKEN] = 1;
            herd.count[Farm.CHICKEN] = 2;
            saveShop();
        }
    }

    private void loadHerd() {
        for (int i = 0; i < herd.stock.length; i++) herd.stock[i] = prefs.getInt("stock_" + i, 0);
        for (int i = 0; i < herd.house.length; i++) herd.house[i] = prefs.getInt("house_" + i, 0);
        for (int i = 0; i < herd.chicks.length; i++) herd.chicks[i] = prefs.getInt("chicks_" + i, 0);
        // saves from before the other animals: hens and cows lived under their own keys, and chicks took two levels to grow
        herd.count[Farm.CHICKEN] = prefs.getInt("count_0", prefs.getInt("chickens", 0));
        herd.count[Farm.COW] = prefs.getInt("count_1", prefs.getInt("cows", 0));
        for (int i = Farm.COW + 1; i < herd.count.length; i++) herd.count[i] = prefs.getInt("count_" + i, 0);
        herd.chicks[0] += prefs.getInt("chicks_1", 0);
        herd.streak = prefs.getInt("streak", 0);
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
        for (int i = 0; i < decor.length; i++) e.putBoolean("decor_" + i, decor[i]);
        for (int i = 0; i < herd.stock.length; i++) e.putInt("stock_" + i, herd.stock[i]);
        for (int i = 0; i < herd.house.length; i++) e.putInt("house_" + i, herd.house[i]);
        for (int i = 0; i < herd.chicks.length; i++) e.putInt("chicks_" + i, herd.chicks[i]);
        for (int i = 0; i < herd.count.length; i++) e.putInt("count_" + i, herd.count[i]);
        e.remove("chicks_1").putInt("streak", herd.streak);
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

    void farmTipShown() {
        farmTipSeen = true;
        prefs.edit().putBoolean("tip_farm", true).apply();
    }

    void tipShown(int type) {
        tipSeen[type] = true;
        prefs.edit().putBoolean("tip_" + type, true).apply();
    }

    // ---- farm

    /** What tapping a species' card does next, in the order a farm grows. */
    static final int ACT_LOCKED = 0, ACT_BUILD = 1, ACT_HATCHING = 2, ACT_BUY = 4, ACT_UPGRADE = 5, ACT_FULL = 6;

    boolean houseUnlocked(int sp) {
        return unlocked >= Farm.HOUSE_UNLOCK_LEVEL[sp];
    }

    int nextAction(int sp) {
        if (!houseUnlocked(sp)) return ACT_LOCKED;
        if (herd.house[sp] == 0) return ACT_BUILD;
        if (herd.room(sp) > 0) return sp == Farm.CHICKEN ? ACT_HATCHING : ACT_BUY;
        return herd.house[sp] < Farm.MAX_HOUSE_LEVEL ? ACT_UPGRADE : ACT_FULL;
    }

    /** Coins the next action costs, or 0 when it is free or not available. */
    int actionPrice(int sp) {
        switch (nextAction(sp)) {
            case ACT_BUILD:
            case ACT_UPGRADE:
                return Farm.houseCost(sp, herd.house[sp]);
            case ACT_BUY:
                return Farm.animalPrice(sp);
            default:
                return 0;
        }
    }

    /** Does the next action for a species. Returns null on success, otherwise why it could not be done. */
    String tend(int sp) {
        int act = nextAction(sp);
        switch (act) {
            case ACT_LOCKED:
                return "Beat level " + (Farm.HOUSE_UNLOCK_LEVEL[sp] - 1) + " first";
            case ACT_FULL:
                return "Fully upgraded";
            case ACT_HATCHING:
                return "Eggs hatch on their own";
            default: {
                int cost = actionPrice(sp);
                if (totalCoins < cost) return "Not enough coins";
                totalCoins -= cost;
                if (act == ACT_BUY) herd.count[sp]++;
                else herd.house[sp]++;
            }
        }
        herd.autoHatch();
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
}
