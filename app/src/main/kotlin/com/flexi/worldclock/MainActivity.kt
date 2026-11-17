package com.flexi.worldclock

import android.app.Activity
import android.os.Bundle
import android.widget.Button
import android.widget.TextView

/** Small landing screen that explains the pack and offers to pin the widgets. */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Ui.applyStatusBarInset(findViewById(R.id.screen_root))

        findViewById<Button>(R.id.pin_clock).setOnClickListener {
            PinWidgets.requestPin(this, ClockWidgetProvider::class.java)
        }

        findViewById<Button>(R.id.pin_world).setOnClickListener {
            PinWidgets.requestPin(this, WorldClockWidgetProvider::class.java)
        }
    }

    override fun onResume() {
        super.onResume()
        val hint = findViewById<TextView>(R.id.main_hint)
        if (!PinWidgets.isSupported(this)) {
            hint.setText(R.string.pin_unavailable)
        }
    }
}
