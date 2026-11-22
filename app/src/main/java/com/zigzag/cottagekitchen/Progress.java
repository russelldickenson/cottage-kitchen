package com.zigzag.cottagekitchen;

import android.content.Context;
import android.content.SharedPreferences;

/** Everything that survives between sessions: how far the player has got, best stars and score per level, sound and tutorial cards seen. */
final class Progress {
    int unlocked;
    boolean soundOn;
    boolean introSeen;
    final int[] bestStars = new int[Level.COUNT];
    final int[] bestScore = new int[Level.COUNT];
    final boolean[] tipSeen = new boolean[Art.COUNT];
    private final SharedPreferences prefs;

    Progress(Context ctx) {
        prefs = ctx.getSharedPreferences("cottagekitchen", Context.MODE_PRIVATE);
        unlocked = Math.max(1, Math.min(Level.COUNT, prefs.getInt("unlocked", 1)));
        soundOn = prefs.getBoolean("sound", true);
        introSeen = prefs.getBoolean("intro", false);
        for (int t = Art.CROPS; t < Art.COUNT; t++) tipSeen[t] = prefs.getBoolean("tip_" + t, false);
        for (int i = 0; i < Level.COUNT; i++) {
            bestStars[i] = prefs.getInt("stars_" + i, 0);
            bestScore[i] = prefs.getInt("score_" + i, 0);
        }
    }

    /** A dish is open once the one before it has been cooked (all its ingredients collected). */
    boolean recipeUnlocked(int dish) {
        return unlocked > Recipes.firstLevel(dish);
    }

    boolean recipeDone(int dish) {
        return bestStars[Recipes.lastLevel(dish)] > 0;
    }

    boolean ingredientDone(int dish, int step) {
        return bestStars[Recipes.firstLevel(dish) + step] > 0;
    }

    /** How many of a dish's ingredients have been collected. */
    int ingredientsDone(int dish) {
        int n = 0;
        for (int s = 0; s < Recipes.ALL[dish].ingredients.length; s++) if (ingredientDone(dish, s)) n++;
        return n;
    }

    /** The ingredient to collect next: the first one not yet done, or the last again once the dish is finished. */
    int nextStep(int dish) {
        int n = Recipes.ALL[dish].ingredients.length;
        for (int s = 0; s < n; s++) if (!ingredientDone(dish, s)) return s;
        return n - 1;
    }

    /** Records a finished level. stars is 0 for a loss, but the score still counts. */
    void levelFinished(int levelIndex, int stars, int score) {
        bestScore[levelIndex] = Math.max(bestScore[levelIndex], score);
        SharedPreferences.Editor edit = prefs.edit().putInt("score_" + levelIndex, bestScore[levelIndex]);
        if (stars == 0) {
            edit.apply();
            return;
        }
        bestStars[levelIndex] = Math.max(bestStars[levelIndex], stars);
        unlocked = Math.max(unlocked, Math.min(Level.COUNT, levelIndex + 2));
        edit.putInt("unlocked", unlocked).putInt("stars_" + levelIndex, bestStars[levelIndex]).apply();
    }

    void setSound(boolean on) {
        soundOn = on;
        prefs.edit().putBoolean("sound", on).apply();
    }

    void introShown() {
        introSeen = true;
        prefs.edit().putBoolean("intro", true).apply();
    }

    void tipShown(int type) {
        tipSeen[type] = true;
        prefs.edit().putBoolean("tip_" + type, true).apply();
    }
}
