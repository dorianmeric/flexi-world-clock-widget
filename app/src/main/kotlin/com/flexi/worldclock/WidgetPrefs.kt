package com.flexi.worldclock

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject

/** Per-widget-instance configuration, persisted as JSON keyed by app widget id. */
internal object WidgetPrefs {

    private const val FILE = "widget_settings"
    private const val KEY_CLOCK = "clock_"
    private const val KEY_WORLD = "world_"

    private fun prefs(context: Context): SharedPreferences =
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE)

    // ---------------------------------------------------------------- clock

    fun loadClock(context: Context, appWidgetId: Int): ClockSettings {
        val raw = prefs(context).getString(KEY_CLOCK + appWidgetId, null)
            ?: return ClockSettings.defaults()
        return try {
            ClockSettings.fromJson(JSONObject(raw))
        } catch (e: JSONException) {
            ClockSettings.defaults()
        }
    }

    fun saveClock(context: Context, appWidgetId: Int, settings: ClockSettings) {
        prefs(context).edit()
            .putString(KEY_CLOCK + appWidgetId, settings.toJson().toString())
            .apply()
    }

    // ---------------------------------------------------------- world clock

    fun loadWorld(context: Context, appWidgetId: Int): WorldClockSettings {
        val raw = prefs(context).getString(KEY_WORLD + appWidgetId, null)
            ?: return WorldClockSettings.defaults()
        return try {
            WorldClockSettings.fromJson(JSONObject(raw))
        } catch (e: JSONException) {
            WorldClockSettings.defaults()
        }
    }

    fun saveWorld(context: Context, appWidgetId: Int, settings: WorldClockSettings) {
        prefs(context).edit()
            .putString(KEY_WORLD + appWidgetId, settings.toJson().toString())
            .apply()
    }

    fun delete(context: Context, appWidgetId: Int) {
        prefs(context).edit()
            .remove(KEY_CLOCK + appWidgetId)
            .remove(KEY_WORLD + appWidgetId)
            .apply()
    }

    // --------------------------------------------------------------- models

    /** Typography used by one text line. */
    class TextStyle(
        var family: String = Fonts.DEFAULT_FAMILY,
        var sizeSp: Float = 14f,
    ) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("family", family)
            // toDouble() keeps Java's put(String, double) overload rather than boxing the Float,
            // so the persisted number is serialised exactly as the previous release wrote it.
            put("size", sizeSp.toDouble())
        }

        companion object {
            fun fromJson(json: JSONObject?, fallbackFamily: String, fallbackSize: Float): TextStyle {
                if (json == null) {
                    return TextStyle(fallbackFamily, fallbackSize)
                }
                val style = TextStyle()
                style.family = json.optString("family", fallbackFamily)
                style.sizeSp = json.optDouble("size", fallbackSize.toDouble()).toFloat()
                if (style.family !in Fonts.families()) {
                    style.family = fallbackFamily
                }
                style.sizeSp = clamp(style.sizeSp, 6f, 96f)
                return style
            }
        }
    }

    /** Settings for the 2x1 clock widget. */
    class ClockSettings {
        var time: TextStyle = TextStyle(Fonts.DEFAULT_FAMILY, 34f)
        var date: TextStyle = TextStyle(Fonts.DEFAULT_FAMILY, 16f)
        var leftPaddingDp = 12f
        var verticalPaddingDp = 4f
        var background: WidgetBackground.Settings = WidgetBackground.Settings.defaults()

        fun toJson(): JSONObject {
            val json = JSONObject()
            try {
                json.put("time", time.toJson())
                json.put("date", date.toJson())
                json.put("leftPadding", leftPaddingDp.toDouble())
                json.put("background", background.toJson())
                json.put("verticalPadding", verticalPaddingDp.toDouble())
            } catch (ignored: JSONException) {
                // Values are primitives, encoding cannot fail.
            }
            return json
        }

        companion object {
            fun defaults(): ClockSettings = ClockSettings()

            fun fromJson(json: JSONObject): ClockSettings = defaults().apply {
                time = TextStyle.fromJson(json.optJSONObject("time"), Fonts.DEFAULT_FAMILY, 34f)
                date = TextStyle.fromJson(json.optJSONObject("date"), Fonts.DEFAULT_FAMILY, 16f)
                leftPaddingDp = clamp(
                    json.optDouble("leftPadding", 12.0).toFloat(),
                    0f,
                    96f,
                )
                verticalPaddingDp = clamp(
                    json.optDouble("verticalPadding", 4.0).toFloat(),
                    0f,
                    48f,
                )
                background = WidgetBackground.Settings.fromJson(
                    json.optJSONObject("background"),
                )
            }
        }
    }

    /** One column of the world clock widget. */
    data class ZoneEntry(var label: String, var zoneId: String) {
        fun toJson(): JSONObject = JSONObject().apply {
            put("label", label)
            put("zone", zoneId)
        }

        companion object {
            fun fromJson(json: JSONObject): ZoneEntry {
                val zoneId = json.optString("zone", "")
                return ZoneEntry(json.optString("label", ZoneCatalog.labelFor(zoneId)), zoneId)
            }
        }
    }

    /** Settings for the flexible world clock widget. */
    class WorldClockSettings {
        var city: TextStyle = TextStyle(Fonts.DEFAULT_FAMILY, 12f)
        var time: TextStyle = TextStyle(Fonts.DEFAULT_FAMILY, 20f)
        var offset: TextStyle = TextStyle(Fonts.DEFAULT_FAMILY, 10f)
        var showOffset = false
        var spacingDp = 8f
        var widthCells = 4
        var zones: MutableList<ZoneEntry> = mutableListOf()
        var background: WidgetBackground.Settings = WidgetBackground.Settings.defaults()

        fun toJson(): JSONObject {
            val json = JSONObject()
            try {
                json.put("city", city.toJson())
                json.put("time", time.toJson())
                json.put("offset", offset.toJson())
                json.put("showOffset", showOffset)
                json.put("spacing", spacingDp.toDouble())
                json.put("widthCells", widthCells)
                json.put("background", background.toJson())
                json.put("zones", JSONArray().apply { zones.forEach { put(it.toJson()) } })
            } catch (ignored: JSONException) {
                // Values are primitives, encoding cannot fail.
            }
            return json
        }

        companion object {
            const val MAX_ZONES = 10
            const val MIN_WIDTH_CELLS = 2
            const val MAX_WIDTH_CELLS = 6

            fun defaults(): WorldClockSettings = WorldClockSettings().apply {
                zones.add(ZoneEntry("London", "Europe/London"))
                zones.add(ZoneEntry("New York", "America/New_York"))
                zones.add(ZoneEntry("Tokyo", "Asia/Tokyo"))
                zones.add(ZoneEntry("Sydney", "Australia/Sydney"))
            }

            fun fromJson(json: JSONObject): WorldClockSettings = defaults().apply {
                city = TextStyle.fromJson(json.optJSONObject("city"), Fonts.DEFAULT_FAMILY, 12f)
                time = TextStyle.fromJson(json.optJSONObject("time"), Fonts.DEFAULT_FAMILY, 20f)
                offset = TextStyle.fromJson(
                    json.optJSONObject("offset"),
                    Fonts.DEFAULT_FAMILY,
                    10f,
                )
                showOffset = json.optBoolean("showOffset", false)
                spacingDp = clamp(json.optDouble("spacing", 8.0).toFloat(), 0f, 64f)
                widthCells = clamp(
                    json.optInt("widthCells", 4),
                    MIN_WIDTH_CELLS,
                    MAX_WIDTH_CELLS,
                )

                val parsed = mutableListOf<ZoneEntry>()
                val array = json.optJSONArray("zones")
                if (array != null) {
                    var index = 0
                    while (index < array.length() && parsed.size < MAX_ZONES) {
                        array.optJSONObject(index)?.let { item ->
                            val zone = ZoneEntry.fromJson(item)
                            if (ClockFormats.isValidZone(zone.zoneId)) {
                                parsed.add(zone)
                            }
                        }
                        index++
                    }
                }
                zones = if (parsed.isEmpty()) defaults().zones else parsed
                background = WidgetBackground.Settings.fromJson(
                    json.optJSONObject("background"),
                )
            }
        }
    }

    fun clamp(value: Float, min: Float, max: Float): Float =
        value.coerceIn(min, max)

    fun clamp(value: Int, min: Int, max: Int): Int =
        value.coerceIn(min, max)
}
