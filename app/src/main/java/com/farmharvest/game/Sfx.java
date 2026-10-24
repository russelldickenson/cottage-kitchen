package com.farmharvest.game;

import android.content.Context;
import android.media.AudioAttributes;
import android.media.SoundPool;
import android.os.Build;
import android.os.VibrationEffect;
import android.os.Vibrator;

/** Sound effects and haptics. Both follow the single sound on/off switch. */
final class Sfx {
    boolean enabled = true;

    private final SoundPool pool;
    private final Vibrator vibrator;
    private final int swap, bad, pop, coin, special, boom, magic, tractor, win, lose, click;
    private long lastCoinMs;

    @SuppressWarnings("deprecation")
    Sfx(Context ctx) {
        pool = new SoundPool.Builder()
                .setMaxStreams(8)
                .setAudioAttributes(new AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build())
                .build();
        swap = pool.load(ctx, R.raw.sfx_swap, 1);
        bad = pool.load(ctx, R.raw.sfx_bad, 1);
        pop = pool.load(ctx, R.raw.sfx_pop, 1);
        coin = pool.load(ctx, R.raw.sfx_coin, 1);
        special = pool.load(ctx, R.raw.sfx_special, 1);
        boom = pool.load(ctx, R.raw.sfx_boom, 1);
        magic = pool.load(ctx, R.raw.sfx_magic, 1);
        tractor = pool.load(ctx, R.raw.sfx_tractor, 1);
        win = pool.load(ctx, R.raw.sfx_win, 1);
        lose = pool.load(ctx, R.raw.sfx_lose, 1);
        click = pool.load(ctx, R.raw.sfx_click, 1);
        Vibrator v = (Vibrator) ctx.getSystemService(Context.VIBRATOR_SERVICE);
        vibrator = v != null && v.hasVibrator() ? v : null;
    }

    private void play(int id, float vol, float rate) {
        if (enabled) pool.play(id, vol, vol, 1, 0, Math.max(0.5f, Math.min(2f, rate)));
    }

    @SuppressWarnings("deprecation")
    void haptic(int ms, int amplitude) {
        if (!enabled || vibrator == null) return;
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createOneShot(ms, amplitude));
        else vibrator.vibrate(ms);
    }

    void swap() {
        play(swap, 0.6f, 1f);
    }

    void badSwap() {
        play(bad, 0.7f, 1f);
        haptic(18, 90);
    }

    /** A cleared match; each combo step is a little higher. */
    void match(int combo) {
        play(pop, 0.85f, 1f + 0.12f * Math.max(0, combo - 1));
        haptic(10, 70);
    }

    /** A coin reaching the counter. Rate-limited so a burst does not become noise. */
    void coin() {
        long now = System.currentTimeMillis();
        if (now - lastCoinMs < 55) return;
        lastCoinMs = now;
        play(coin, 0.5f, 1f);
    }

    /** A coin dropping into the bag; fill (0..1) raises the pitch as the bag fills. */
    void bagCoin(float fill) {
        long now = System.currentTimeMillis();
        if (now - lastCoinMs < 40) return;
        lastCoinMs = now;
        play(coin, 0.55f, 0.85f + 0.7f * fill);
    }

    void powerUpMade() {
        play(special, 0.8f, 1f);
        haptic(25, 120);
    }

    void powerUpPlayed(int type) {
        switch (type) {
            case Art.GOLDEN_EGG:
                play(magic, 0.9f, 1f);
                haptic(35, 150);
                break;
            case Art.TRACTOR:
                play(tractor, 0.9f, 1f);
                haptic(60, 120);
                break;
            default:
                play(boom, 1f, 1f);
                haptic(45, 230);
                break;
        }
    }

    void win() {
        play(win, 0.9f, 1f);
        haptic(40, 160);
    }

    void lose() {
        play(lose, 0.8f, 1f);
        haptic(60, 100);
    }

    void click() {
        play(click, 0.6f, 1f);
    }

    void release() {
        pool.release();
    }
}
