package com.flexi.worldclock

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.FrameLayout
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView
import kotlin.math.roundToInt

/** Configuration screen of the 2x1 clock widget. */
class ClockConfigActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var confirmed = false
    private lateinit var settings: WidgetPrefs.ClockSettings
    private var previewHost: FrameLayout? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = readAppWidgetId(intent)
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        setContentView(R.layout.activity_clock_config)
        Ui.applyStatusBarInset(findViewById(R.id.screen_root))
        setTitle(R.string.clock_config_title)
        settings = WidgetPrefs.loadClock(this, appWidgetId)
        previewHost = findViewById(R.id.preview_host)

        bindFont(R.id.time_font, settings.time.family) { family -> settings.time.family = family }
        bindFont(R.id.date_font, settings.date.family) { family -> settings.date.family = family }

        Ui.Slider.sp(
            this,
            findViewById<SeekBar>(R.id.time_size),
            findViewById<TextView>(R.id.time_size_value),
            MIN_TIME_SP,
            MAX_TIME_SP,
            settings.time.sizeSp.roundToInt(),
        ) { value ->
            settings.time.sizeSp = value.toFloat()
            renderPreview()
        }
        Ui.Slider.sp(
            this,
            findViewById<SeekBar>(R.id.date_size),
            findViewById<TextView>(R.id.date_size_value),
            MIN_DATE_SP,
            MAX_DATE_SP,
            settings.date.sizeSp.roundToInt(),
        ) { value ->
            settings.date.sizeSp = value.toFloat()
            renderPreview()
        }
        Ui.Slider.dp(
            this,
            findViewById<SeekBar>(R.id.left_padding),
            findViewById<TextView>(R.id.left_padding_value),
            0,
            MAX_LEFT_PADDING_DP,
            settings.leftPaddingDp.roundToInt(),
        ) { value ->
            settings.leftPaddingDp = value.toFloat()
            renderPreview()
        }

        findViewById<View>(R.id.save).setOnClickListener { save() }
        findViewById<View>(R.id.cancel).setOnClickListener { finish() }

        Ui.bindBackground(window.decorView, settings.background) { background ->
            settings.background = background
            renderPreview()
        }
        renderPreview()
    }

    private fun bindFont(spinnerId: Int, current: String, onFamily: (String) -> Unit) {
        val spinner = findViewById<Spinner>(spinnerId)
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, Fonts.displayNames())
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinner.adapter = adapter
        spinner.setSelection(Fonts.indexOf(current))
        spinner.onItemSelectedListener = object : Ui.OnItemSelected() {
            override fun onSelected(position: Int) {
                onFamily(Fonts.familyAt(position))
                renderPreview()
            }
        }
    }

    private fun renderPreview() {
        val host = previewHost ?: return
        val widthDp = WorldClockWidgetViews.CELL_DP * PREVIEW_WIDTH_CELLS
        PreviewRenderer.show(
            host,
            ClockWidgetViews.build(
                this,
                settings,
                AppWidgetManager.INVALID_APPWIDGET_ID,
                PreviewRenderer.options(widthDp, CLOCK_PREVIEW_HEIGHT_DP),
            ),
            widthDp,
        )
    }

    private fun save() {
        WidgetPrefs.saveClock(this, appWidgetId, settings)
        ClockWidgetViews.updateNow(this, appWidgetId)
        confirmed = true
        setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        finish()
    }

    override fun finish() {
        if (!confirmed) {
            setResult(RESULT_CANCELED)
        }
        super.finish()
    }

    companion object {
        private const val MIN_TIME_SP = 12
        private const val MAX_TIME_SP = 80
        private const val MIN_DATE_SP = 8
        private const val MAX_DATE_SP = 44
        private const val MAX_LEFT_PADDING_DP = 64

        /** Width of the 2x1 widget in launcher cells, used for the preview. */
        private const val PREVIEW_WIDTH_CELLS = 2f

        /** Height the 2x1 widget occupies on a launcher grid, used for the preview. */
        private const val CLOCK_PREVIEW_HEIGHT_DP = 56f

        fun readAppWidgetId(intent: Intent?): Int = intent?.getIntExtra(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID
    }
}
