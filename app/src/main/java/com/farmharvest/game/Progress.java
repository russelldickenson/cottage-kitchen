package com.farmharvest.game;

import android.content.Context;
import android.content.SharedPreferences;

/** Everything that survives between sessions: how far the player has got, the best stars per dish, sound and tutorial cards seen. */
final class Progress {
    int unlocked;
    boolean soundOn;
    final int[] bestStars = new int[Level.COUNT];
    final boolean[] tipSeen = new boolean[Art.COUNT];
    private final SharedPreferences prefs;

    Progress(Context ctx) {
        prefs = ctx.getSharedPreferences("farm", Context.MODE_PRIVATE);
        unlocked = Math.max(1, Math.min(Level.COUNT, prefs.getInt("unlocked", 1)));
        soundOn = prefs.getBoolean("sound", true);
        for (int t = Art.CROPS; t < Art.COUNT; t++) tipSeen[t] = prefs.getBoolean("tip_" + t, false);
        for (int i = 0; i < Level.COUNT; i++) bestStars[i] = prefs.getInt("stars_" + i, 0);
    }

    /** A dish is open once the one before it has been cooked (all its batches). */
    boolean recipeUnlocked(int recipe) {
        return unlocked > recipe * Level.STAGES;
    }

    boolean recipeDone(int recipe) {
        return bestStars[recipe * Level.STAGES + Level.STAGES - 1] > 0;
    }

    /** The batch to cook next: the first one not yet cleared, or the last again once the order is done. */
    int nextBatch(int recipe) {
        for (int s = 0; s < Level.STAGES; s++) if (bestStars[recipe * Level.STAGES + s] == 0) return s;
        return Level.STAGES - 1;
    }

    /** Records a finished level. stars is 0 for a loss. */
    void levelFinished(int levelIndex, int stars) {
        if (stars == 0) return;
        bestStars[levelIndex] = Math.max(bestStars[levelIndex], stars);
        unlocked = Math.max(unlocked, Math.min(Level.COUNT, levelIndex + 2));
        prefs.edit().putInt("unlocked", unlocked).putInt("stars_" + levelIndex, bestStars[levelIndex]).apply();
    }

    void setSound(boolean on) {
        soundOn = on;
        prefs.edit().putBoolean("sound", on).apply();
    }

    void tipShown(int type) {
        tipSeen[type] = true;
        prefs.edit().putBoolean("tip_" + type, true).apply();
    }
}
