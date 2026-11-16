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
