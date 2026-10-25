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
    final int[] output = new int[Farm.OUTPUTS];
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
        for (int i = 0; i < output.length; i++) output[i] = prefs.getInt("output_" + i, 0);
        for (int i = 0; i < decor.length; i++) decor[i] = prefs.getBoolean("decor_" + i, false);
    }

    private void saveShop() {
        SharedPreferences.Editor e = prefs.edit().putInt("coins", totalCoins);
        for (int i = 0; i < boosters.length; i++) e.putInt("booster_" + i, boosters[i]);
        for (int i = 0; i < output.length; i++) e.putInt("output_" + i, output[i]);
        for (int i = 0; i < decor.length; i++) e.putBoolean("decor_" + i, decor[i]);
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

    int bonusPct() {
        return Farm.bonusPct(output);
    }

    /** Cost of the next level of an output upgrade, or -1 when it is fully upgraded. */
    int outputCost(int k) {
        return output[k] >= Farm.MAX_OUTPUT_LEVEL ? -1 : Farm.OUTPUT_COST[k][output[k]];
    }

    boolean buyOutput(int k) {
        int cost = outputCost(k);
        if (cost < 0 || totalCoins < cost) return false;
        totalCoins -= cost;
        output[k]++;
        saveShop();
        return true;
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
