package com.flexi.worldclock;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.FrameLayout;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;

import java.util.List;

/** Configuration screen of the 2x1 clock widget. */
public class ClockConfigActivity extends Activity {

    private static final int MIN_TIME_SP = 12;
    private static final int MAX_TIME_SP = 80;
    private static final int MIN_DATE_SP = 8;
    private static final int MAX_DATE_SP = 44;
    private static final int MAX_LEFT_PADDING_DP = 64;

    /** Height the 2x1 widget occupies on a launcher grid, used for the preview. */
    private static final float CLOCK_PREVIEW_HEIGHT_DP = 56f;

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean confirmed;
    private WidgetPrefs.ClockSettings settings;
    private FrameLayout previewHost;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);

        appWidgetId = readAppWidgetId(getIntent());
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        setContentView(R.layout.activity_clock_config);
        Ui.applyStatusBarInset(findViewById(R.id.screen_root));
        setTitle(R.string.clock_config_title);
        settings = WidgetPrefs.loadClock(this, appWidgetId);
        previewHost = findViewById(R.id.preview_host);

        bindFont(R.id.time_font, settings.time.family,
                family -> settings.time.family = family);
        bindFont(R.id.date_font, settings.date.family,
                family -> settings.date.family = family);

        Ui.Slider.sp(this, findViewById(R.id.time_size), findViewById(R.id.time_size_value),
                        MIN_TIME_SP, MAX_TIME_SP, Math.round(settings.time.sizeSp), value -> {
                    settings.time.sizeSp = value;
                    renderPreview();
                });
                Ui.Slider.sp(this, findViewById(R.id.date_size), findViewById(R.id.date_size_value),
                        MIN_DATE_SP, MAX_DATE_SP, Math.round(settings.date.sizeSp), value -> {
                    settings.date.sizeSp = value;
                    renderPreview();
                });
                Ui.Slider.dp(this, findViewById(R.id.left_padding),
                findViewById(R.id.left_padding_value), 0, MAX_LEFT_PADDING_DP,
                Math.round(settings.leftPaddingDp), value -> {
                    settings.leftPaddingDp = value;
                    renderPreview();
                });

        findViewById(R.id.save).setOnClickListener(v -> save());
        findViewById(R.id.cancel).setOnClickListener(v -> finish());

                Ui.bindBackground(getWindow().getDecorView(), settings.background, background -> {
                            settings.background = background;
                            renderPreview();
                        });
        renderPreview();
    }

    private void bindFont(int spinnerId, String current, Ui.FamilySink sink) {
        Spinner spinner = findViewById(spinnerId);
        List<String> names = Fonts.displayNames();
        ArrayAdapter<String> adapter =
                new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        spinner.setAdapter(adapter);
        spinner.setSelection(Fonts.indexOf(current));
        spinner.setOnItemSelectedListener(new Ui.OnItemSelected() {
            @Override
            void onSelected(int position) {
                sink.onValue(Fonts.familyAt(position));
                renderPreview();
            }
        });
    }

    private void renderPreview() {
        if (previewHost == null) {
            return;
        }
        PreviewRenderer.show(previewHost,
                        ClockWidgetViews.build(this, settings, AppWidgetManager.INVALID_APPWIDGET_ID,
                                PreviewRenderer.options(
                                        WorldClockWidgetViews.CELL_DP * 2f,
                                        CLOCK_PREVIEW_HEIGHT_DP)),
                        WorldClockWidgetViews.CELL_DP * 2f);
    }

    private void save() {
        WidgetPrefs.saveClock(this, appWidgetId, settings);
        ClockWidgetViews.updateNow(this, appWidgetId);
        confirmed = true;
        setResult(RESULT_OK,
                new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId));
        finish();
    }

    @Override
    public void finish() {
        if (!confirmed) {
            setResult(RESULT_CANCELED);
        }
        super.finish();
    }

    static int readAppWidgetId(Intent intent) {
        if (intent == null) {
            return AppWidgetManager.INVALID_APPWIDGET_ID;
        }
        return intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
    }
}