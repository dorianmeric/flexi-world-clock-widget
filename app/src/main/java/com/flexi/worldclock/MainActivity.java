package com.flexi.worldclock;

import android.app.Activity;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

/** Small landing screen that explains the pack and offers to pin the widgets. */
public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        setTitle(R.string.app_name);

        Button pinClock = findViewById(R.id.pin_clock);
        pinClock.setOnClickListener(v -> PinWidgets.requestPin(this, ClockWidgetProvider.class));

        Button pinWorld = findViewById(R.id.pin_world);
        pinWorld.setOnClickListener(v ->
                PinWidgets.requestPin(this, WorldClockWidgetProvider.class));
    }

    @Override
    protected void onResume() {
        super.onResume();
        TextView hint = findViewById(R.id.main_hint);
        if (!PinWidgets.isSupported(this)) {
            hint.setText(R.string.pin_unavailable);
        }
    }
}