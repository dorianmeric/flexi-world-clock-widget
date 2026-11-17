package com.flexi.worldclock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the on-disk JSON contract of [WidgetPrefs]. Settings are written per widget instance, so a
 * renamed key, a changed value type or a reformatted number here would silently reset every
 * existing user's widget on upgrade.
 *
 * Key *order* is deliberately not asserted: it is decided by `org.json` (a hash map on the JVM, a
 * linked map on Android), not by the writer, and it differs between the two.
 */
class PersistedFormatTest {

    @Test
    fun clockSettingsKeepTheirStoredKeys() {
        val json = WidgetPrefs.ClockSettings.defaults().toJson()

        assertEquals(
            setOf("time", "date", "leftPadding", "background", "verticalPadding"),
            json.keys().asSequence().toSet(),
        )
        assertEquals(
            setOf("family", "size"),
            json.getJSONObject("time").keys().asSequence().toSet(),
        )
        assertEquals(
            setOf("family", "size"),
            json.getJSONObject("date").keys().asSequence().toSet(),
        )
        assertEquals(
            setOf("on", "color", "opacity"),
            json.getJSONObject("background").keys().asSequence().toSet(),
        )
        assertEquals(12, json.getInt("leftPadding"))
        assertEquals(4, json.getInt("verticalPadding"))
        assertEquals("sans-serif", json.getJSONObject("time").getString("family"))
        assertEquals(34.0, json.getJSONObject("time").getDouble("size"), 0.0)
        assertEquals(16.0, json.getJSONObject("date").getDouble("size"), 0.0)
        assertEquals("#12161C", json.getJSONObject("background").getString("color"))
        assertTrue(json.getJSONObject("background").getBoolean("on"))
        assertEquals(80, json.getJSONObject("background").getInt("opacity"))
    }

    @Test
    fun worldClockSettingsKeepTheirStoredKeys() {
        val json = WidgetPrefs.WorldClockSettings.defaults().toJson()

        assertEquals(
            setOf(
                "city",
                "time",
                "offset",
                "showOffset",
                "spacing",
                "widthCells",
                "background",
                "zones",
            ),
            json.keys().asSequence().toSet(),
        )
        assertEquals(12.0, json.getJSONObject("city").getDouble("size"), 0.0)
        assertEquals(20.0, json.getJSONObject("time").getDouble("size"), 0.0)
        assertEquals(10.0, json.getJSONObject("offset").getDouble("size"), 0.0)
        assertFalse(json.getBoolean("showOffset"))
        assertEquals(8.0, json.getDouble("spacing"), 0.0)
        assertEquals(4, json.getInt("widthCells"))

        val zones = json.getJSONArray("zones")
        assertEquals(4, zones.length())
        val expected = listOf(
            "London" to "Europe/London",
            "New York" to "America/New_York",
            "Tokyo" to "Asia/Tokyo",
            "Sydney" to "Australia/Sydney",
        )
        for ((index, pair) in expected.withIndex()) {
            val entry = zones.getJSONObject(index)
            assertEquals(setOf("label", "zone"), entry.keys().asSequence().toSet())
            assertEquals(pair.first, entry.getString("label"))
            assertEquals(pair.second, entry.getString("zone"))
        }
    }

    @Test
    fun fractionalSizesUseTheSameNumberEncodingAsJava() {
        // Java wrote floats through JSONObject.put(String, double), which widens the float; Kotlin
        // has no implicit widening, so the writer applies toDouble() itself. Without it a float
        // such as 0.1f would be boxed and serialised as "0.1" instead of Java's double form,
        // rewriting every stored size on the next save.
        val settings = WidgetPrefs.ClockSettings.defaults()
        settings.time.sizeSp = 0.1f

        assertTrue(
            settings.time.toJson().toString(),
            settings.time.toJson().toString().contains("\"size\":0.10000000149011612"),
        )
    }

    @Test
    fun wholeSizesDoNotGainADecimalPoint() {
        // The sliders write whole sp values, so the persisted documents stay decimal-free.
        val raw = WidgetPrefs.ClockSettings.defaults().toJson().toString()

        assertFalse("whole sizes must not gain a trailing .0: $raw", raw.contains(".0"))
        assertTrue(raw, raw.contains("\"size\":34"))
    }
}
