package com.flexi.worldclock;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/** Per-widget-instance configuration, persisted as JSON keyed by app widget id. */
public final class WidgetPrefs {

    private static final String FILE = "widget_settings";
    private static final String KEY_CLOCK = "clock_";
    private static final String KEY_WORLD = "world_";

    private WidgetPrefs() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    // ---------------------------------------------------------------- clock

    public static ClockSettings loadClock(Context context, int appWidgetId) {
        String raw = prefs(context).getString(KEY_CLOCK + appWidgetId, null);
        if (raw == null) {
            return ClockSettings.defaults();
        }
        try {
            return ClockSettings.fromJson(new JSONObject(raw));
        } catch (JSONException e) {
            return ClockSettings.defaults();
        }
    }

    public static void saveClock(Context context, int appWidgetId, ClockSettings settings) {
        prefs(context).edit()
                .putString(KEY_CLOCK + appWidgetId, settings.toJson().toString())
                .apply();
    }

    // ---------------------------------------------------------- world clock

    public static WorldClockSettings loadWorld(Context context, int appWidgetId) {
        String raw = prefs(context).getString(KEY_WORLD + appWidgetId, null);
        if (raw == null) {
            return WorldClockSettings.defaults();
        }
        try {
            return WorldClockSettings.fromJson(new JSONObject(raw));
        } catch (JSONException e) {
            return WorldClockSettings.defaults();
        }
    }

    public static void saveWorld(Context context, int appWidgetId, WorldClockSettings settings) {
        prefs(context).edit()
                .putString(KEY_WORLD + appWidgetId, settings.toJson().toString())
                .apply();
    }

    public static void delete(Context context, int appWidgetId) {
        prefs(context).edit()
                .remove(KEY_CLOCK + appWidgetId)
                .remove(KEY_WORLD + appWidgetId)
                .apply();
    }

    // --------------------------------------------------------------- models

    /** Typography used by one text line. */
    public static final class TextStyle {
        public String family = Fonts.DEFAULT_FAMILY;
        public float sizeSp = 14f;

        public TextStyle() {
        }

        public TextStyle(String family, float sizeSp) {
            this.family = family;
            this.sizeSp = sizeSp;
        }

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("family", family);
            json.put("size", sizeSp);
            return json;
        }

        static TextStyle fromJson(JSONObject json, String fallbackFamily, float fallbackSize) {
                    if (json == null) {
                        return new TextStyle(fallbackFamily, fallbackSize);
                    }
            TextStyle style = new TextStyle();
            style.family = json.optString("family", fallbackFamily);
            style.sizeSp = (float) json.optDouble("size", fallbackSize);
            if (!Fonts.families().contains(style.family)) {
                style.family = fallbackFamily;
            }
            style.sizeSp = clamp(style.sizeSp, 6f, 96f);
            return style;
        }
    }

    /** Settings for the 2x1 clock widget. */
    public static final class ClockSettings {
        public TextStyle time = new TextStyle(Fonts.DEFAULT_FAMILY, 34f);
        public TextStyle date = new TextStyle(Fonts.DEFAULT_FAMILY, 16f);
        public float leftPaddingDp = 12f;
        public float verticalPaddingDp = 4f;

        public static ClockSettings defaults() {
            return new ClockSettings();
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("time", time.toJson());
                json.put("date", date.toJson());
                json.put("leftPadding", leftPaddingDp);
                json.put("verticalPadding", verticalPaddingDp);
            } catch (JSONException ignored) {
                // Values are primitives, encoding cannot fail.
            }
            return json;
        }

        static ClockSettings fromJson(JSONObject json) {
            ClockSettings settings = defaults();
            settings.time = TextStyle.fromJson(json.optJSONObject("time"),
                    Fonts.DEFAULT_FAMILY, 34f);
            settings.date = TextStyle.fromJson(json.optJSONObject("date"),
                    Fonts.DEFAULT_FAMILY, 16f);
            settings.leftPaddingDp = clamp((float) json.optDouble("leftPadding", 12f), 0f, 96f);
            settings.verticalPaddingDp =
                    clamp((float) json.optDouble("verticalPadding", 4f), 0f, 48f);
            return settings;
        }
    }

    /** One column of the world clock widget. */
    public static final class ZoneEntry {
        public String label;
        public String zoneId;

        public ZoneEntry(String label, String zoneId) {
            this.label = label;
            this.zoneId = zoneId;
        }

        JSONObject toJson() throws JSONException {
            JSONObject json = new JSONObject();
            json.put("label", label);
            json.put("zone", zoneId);
            return json;
        }

        static ZoneEntry fromJson(JSONObject json) {
            String zoneId = json.optString("zone", "");
            String label = json.optString("label", ZoneCatalog.labelFor(zoneId));
            return new ZoneEntry(label, zoneId);
        }
    }

    /** Settings for the flexible world clock widget. */
    public static final class WorldClockSettings {
        public static final int MAX_ZONES = 10;
        public static final int MIN_WIDTH_CELLS = 2;
        public static final int MAX_WIDTH_CELLS = 6;

        public TextStyle city = new TextStyle(Fonts.DEFAULT_FAMILY, 12f);
        public TextStyle time = new TextStyle(Fonts.DEFAULT_FAMILY, 20f);
        public TextStyle offset = new TextStyle(Fonts.DEFAULT_FAMILY, 10f);
        public boolean showOffset = false;
        public float spacingDp = 8f;
        public int widthCells = 4;
        public List<ZoneEntry> zones = new ArrayList<>();

        public static WorldClockSettings defaults() {
            WorldClockSettings settings = new WorldClockSettings();
            settings.zones.add(new ZoneEntry("London", "Europe/London"));
            settings.zones.add(new ZoneEntry("New York", "America/New_York"));
            settings.zones.add(new ZoneEntry("Tokyo", "Asia/Tokyo"));
            settings.zones.add(new ZoneEntry("Sydney", "Australia/Sydney"));
            return settings;
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("city", city.toJson());
                json.put("time", time.toJson());
                json.put("offset", offset.toJson());
                json.put("showOffset", showOffset);
                json.put("spacing", spacingDp);
                json.put("widthCells", widthCells);
                JSONArray array = new JSONArray();
                for (ZoneEntry zone : zones) {
                    array.put(zone.toJson());
                }
                json.put("zones", array);
            } catch (JSONException ignored) {
                // Values are primitives, encoding cannot fail.
            }
            return json;
        }

        static WorldClockSettings fromJson(JSONObject json) {
            WorldClockSettings settings = defaults();
            settings.city = TextStyle.fromJson(json.optJSONObject("city"),
                    Fonts.DEFAULT_FAMILY, 12f);
            settings.time = TextStyle.fromJson(json.optJSONObject("time"),
                    Fonts.DEFAULT_FAMILY, 20f);
            settings.offset = TextStyle.fromJson(json.optJSONObject("offset"),
                    Fonts.DEFAULT_FAMILY, 10f);
            settings.showOffset = json.optBoolean("showOffset", false);
            settings.spacingDp = clamp((float) json.optDouble("spacing", 8f), 0f, 64f);
            settings.widthCells = (int) clamp(json.optInt("widthCells", 4),
                    MIN_WIDTH_CELLS, MAX_WIDTH_CELLS);

            List<ZoneEntry> zones = new ArrayList<>();
            JSONArray array = json.optJSONArray("zones");
            if (array != null) {
                for (int i = 0; i < array.length() && zones.size() < MAX_ZONES; i++) {
                    JSONObject item = array.optJSONObject(i);
                    if (item == null) {
                        continue;
                    }
                    ZoneEntry zone = ZoneEntry.fromJson(item);
                    if (ClockFormats.isValidZone(zone.zoneId)) {
                        zones.add(zone);
                    }
                }
            }
            if (zones.isEmpty()) {
                zones = defaults().zones;
            }
            settings.zones = zones;
            return settings;
        }
    }

    static float clamp(float value, float min, float max) {
        return value < min ? min : (value > max ? max : value);
    }

    static int clamp(int value, int min, int max) {
        return value < min ? min : (value > max ? max : value);
    }
}