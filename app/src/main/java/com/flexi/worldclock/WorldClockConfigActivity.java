package com.flexi.worldclock;

import android.app.Activity;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

/** Configuration screen of the flexible world clock widget. */
public class WorldClockConfigActivity extends Activity {

    private static final int MIN_CITY_SP = 6;
    private static final int MAX_CITY_SP = 30;
    private static final int MIN_TIME_SP = 8;
    private static final int MAX_TIME_SP = 48;
    private static final int MIN_OFFSET_SP = 6;
    private static final int MAX_OFFSET_SP = 20;
    private static final int MAX_SPACING_DP = 48;

    private int appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private boolean confirmed;
    private WidgetPrefs.WorldClockSettings settings;

    private FrameLayout previewHost;
    private LinearLayout zoneList;
    private TextView zonesHeader;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setResult(RESULT_CANCELED);

        appWidgetId = readAppWidgetId(getIntent());
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        setContentView(R.layout.activity_world_clock_config);
        setTitle(R.string.world_config_title);
        settings = WidgetPrefs.loadWorld(this, appWidgetId);

        previewHost = findViewById(R.id.preview_host);
        previewHost.setPadding(8, 8, 8, 8);
        zoneList = findViewById(R.id.zone_list);
        zonesHeader = findViewById(R.id.zones_header);

        bindFont(R.id.city_font, settings.city.family,
                family -> settings.city.family = family);
        bindFont(R.id.time_font, settings.time.family,
                family -> settings.time.family = family);

        Ui.Slider.sp(this, findViewById(R.id.city_size), findViewById(R.id.city_size_value),
                MIN_CITY_SP, MAX_CITY_SP, Math.round(settings.city.sizeSp), value -> {
                    settings.city.sizeSp = value;
                    renderPreview();
                });
        Ui.Slider.sp(this, findViewById(R.id.time_size), findViewById(R.id.time_size_value),
                MIN_TIME_SP, MAX_TIME_SP, Math.round(settings.time.sizeSp), value -> {
                    settings.time.sizeSp = value;
                    renderPreview();
                });
        Ui.Slider.sp(this, findViewById(R.id.offset_size),
                findViewById(R.id.offset_size_value), MIN_OFFSET_SP, MAX_OFFSET_SP,
                Math.round(settings.offset.sizeSp), value -> {
                    settings.offset.sizeSp = value;
                    renderPreview();
                });
        Ui.Slider.dp(this, findViewById(R.id.spacing), findViewById(R.id.spacing_value),
                0, MAX_SPACING_DP, Math.round(settings.spacingDp), value -> {
                    settings.spacingDp = value;
                    renderPreview();
                });
        Ui.Slider.cells(this, findViewById(R.id.width), findViewById(R.id.width_value),
                WidgetPrefs.WorldClockSettings.MIN_WIDTH_CELLS,
                WidgetPrefs.WorldClockSettings.MAX_WIDTH_CELLS, settings.widthCells, value -> {
                    settings.widthCells = value;
                    renderPreview();
                });

        CheckBox showOffset = findViewById(R.id.show_offset);
        showOffset.setChecked(settings.showOffset);
        showOffset.setOnCheckedChangeListener((buttonView, isChecked) -> {
            settings.showOffset = isChecked;
            findViewById(R.id.offset_size).setEnabled(isChecked);
            renderPreview();
        });
        findViewById(R.id.offset_size).setEnabled(settings.showOffset);

        findViewById(R.id.add_zone).setOnClickListener(v -> showPicker(null));
        findViewById(R.id.save).setOnClickListener(v -> save());
        findViewById(R.id.cancel).setOnClickListener(v -> finish());

        renderZoneList();
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

    private void renderZoneList() {
        zoneList.removeAllViews();
        if (settings.zones.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText(R.string.no_zones);
            zoneList.addView(empty);
        }

        long now = System.currentTimeMillis();
        for (int i = 0; i < settings.zones.size(); i++) {
            WidgetPrefs.ZoneEntry zone = settings.zones.get(i);
            View row = LayoutInflater.from(this)
                    .inflate(R.layout.item_zone_row, zoneList, false);

            Button pick = row.findViewById(R.id.zone_pick);
            pick.setText(zoneLabel(zone, now));
            int index = i;
            pick.setOnClickListener(v -> showPicker(index));

            row.findViewById(R.id.zone_remove).setOnClickListener(v -> {
                settings.zones.remove(index);
                renderZoneList();
                renderPreview();
            });

            zoneList.addView(row);
        }

        zonesHeader.setText(getString(R.string.zones, settings.zones.size(),
                WidgetPrefs.WorldClockSettings.MAX_ZONES));
        findViewById(R.id.add_zone).setEnabled(
                settings.zones.size() < WidgetPrefs.WorldClockSettings.MAX_ZONES);
    }

    private String zoneLabel(WidgetPrefs.ZoneEntry zone, long now) {
        return zone.label + "  ·  " + ClockFormats.utcLabel(zone.zoneId, now);
    }

    /** {@code replaceIndex} of {@code null} appends a new zone, otherwise replaces that zone. */
    private void showPicker(Integer replaceIndex) {
        if (replaceIndex == null
                && settings.zones.size() >= WidgetPrefs.WorldClockSettings.MAX_ZONES) {
            Toast.makeText(this, getString(R.string.zone_limit_reached,
                    WidgetPrefs.WorldClockSettings.MAX_ZONES), Toast.LENGTH_SHORT).show();
            return;
        }

        View content = LayoutInflater.from(this).inflate(R.layout.dialog_zone_picker, null);
        EditText search = content.findViewById(R.id.zone_search);
        ListView results = content.findViewById(R.id.zone_results);

        ZoneAdapter adapter = new ZoneAdapter(this, zoneChoices());
        results.setAdapter(adapter);

        AlertDialog dialog = new AlertDialog.Builder(this)
                .setTitle(R.string.add_timezone_title)
                .setView(content)
                .setNegativeButton(R.string.cancel, null)
                .create();

        search.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.update(ZoneCatalog.search(s == null ? "" : s.toString()));
            }

            @Override
            public void afterTextChanged(Editable s) {
            }
        });

        results.setOnItemClickListener((parent, view, position, id) -> {
            ZoneCatalog.Zone zone = adapter.getItem(position);
            if (zone == null) {
                return;
            }
            WidgetPrefs.ZoneEntry entry = new WidgetPrefs.ZoneEntry(zone.label, zone.id);
            if (replaceIndex == null) {
                settings.zones.add(entry);
            } else {
                settings.zones.set(replaceIndex, entry);
            }
            renderZoneList();
            renderPreview();
            dialog.dismiss();
        });

        dialog.show();
    }

    /** Catalogue entries plus the device's own zone when it is not part of the catalogue. */
    private List<ZoneCatalog.Zone> zoneChoices() {
        List<ZoneCatalog.Zone> zones = new ArrayList<>(ZoneCatalog.all());
        String deviceZone = ClockFormats.deviceTimeZoneId();
        for (ZoneCatalog.Zone zone : zones) {
            if (zone.id.equals(deviceZone)) {
                return zones;
            }
        }
        if (ClockFormats.isValidZone(deviceZone)) {
            zones.add(0, new ZoneCatalog.Zone("Local", deviceZone));
        }
        return zones;
    }

    private void renderPreview() {
        if (previewHost == null) {
            return;
        }
        float widthDp = settings.widthCells * WorldClockWidgetViews.CELL_DP;
        PreviewRenderer.show(previewHost, WorldClockWidgetViews.build(this, settings,
                        AppWidgetManager.INVALID_APPWIDGET_ID, PreviewRenderer.options(widthDp)),
                widthDp);
    }

    private void save() {
        WidgetPrefs.saveWorld(this, appWidgetId, settings);
        WorldClockWidgetViews.updateNow(this, appWidgetId);
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

    /** Timezone picker list: city label on top, zone id and UTC offset underneath. */
    private static final class ZoneAdapter extends BaseAdapter {
        private final Context context;
        private List<ZoneCatalog.Zone> zones;
        private final long now = System.currentTimeMillis();

        ZoneAdapter(Context context, List<ZoneCatalog.Zone> zones) {
            this.context = context;
            this.zones = new ArrayList<>(zones);
        }

        void update(List<ZoneCatalog.Zone> updated) {
            this.zones = new ArrayList<>(updated);
            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return zones.size();
        }

        @Override
        public ZoneCatalog.Zone getItem(int position) {
            return position >= 0 && position < zones.size() ? zones.get(position) : null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View row = convertView != null ? convertView
                    : LayoutInflater.from(context)
                    .inflate(android.R.layout.simple_list_item_2, parent, false);
            ZoneCatalog.Zone zone = getItem(position);
            TextView title = row.findViewById(android.R.id.text1);
            TextView subtitle = row.findViewById(android.R.id.text2);
            title.setText(zone.label);
            subtitle.setText(zone.id + "  ·  "
                    + ClockFormats.utcLabel(zone.id, now));
            return row;
        }
    }
}