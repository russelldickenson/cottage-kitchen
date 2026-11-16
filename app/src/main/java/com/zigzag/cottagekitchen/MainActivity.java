package com.zigzag.cottagekitchen;

import android.app.Activity;
import android.os.Build;
import android.os.Bundle;
import android.window.OnBackInvokedDispatcher;

public class MainActivity extends Activity {
    private GameView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new GameView(this);
        setContentView(view);
        // Apps targeting Android 16 no longer get onBackPressed(); use the platform callback instead.
        if (Build.VERSION.SDK_INT >= 33) {
            getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, this::handleBack);
        }
    }

    private void handleBack() {
        if (!view.onBack()) finish();
    }

    @Override
    protected void onDestroy() {
        view.release();
        super.onDestroy();
    }

    /** Android 12 and below. */
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        if (!view.onBack()) super.onBackPressed();
    }
}
