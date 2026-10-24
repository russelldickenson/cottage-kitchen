package com.farmharvest.game;

import android.app.Activity;
import android.os.Bundle;

public class MainActivity extends Activity {
    private GameView view;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        view = new GameView(this);
        setContentView(view);
    }

    @Override
    public void onBackPressed() {
        if (!view.onBack()) super.onBackPressed();
    }
}
