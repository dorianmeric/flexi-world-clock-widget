package com.flexi.worldclock

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WidgetBackgroundTest {

    @Test
    fun defaultsAreAnOpaqueEnoughGraphite() {
        val settings = WidgetBackground.Settings.defaults()
        assertTrue(settings.enabled)
        assertEquals(0xFF12161C.toInt(), settings.color)
        assertEquals(80, settings.opacity)
    }

    @Test
    fun resolvedColorCarriesTheConfiguredOpacity() {
        val settings = WidgetBackground.Settings.defaults()
        settings.color = 0xFF3B82F6.toInt()
        settings.opacity = 50

        val resolved = settings.resolvedColor()
        assertEquals(0x3B82F6, resolved and 0x00FFFFFF)
        // 50% of 255 is 128 (127.5 rounded).
        assertEquals(128, (resolved ushr 24) and 0xFF)
    }

    @Test
    fun disabledBackgroundIsFullyTransparent() {
        val settings = WidgetBackground.Settings.none()
        assertFalse(settings.enabled)
        assertEquals(0, settings.resolvedColor() ushr 24)
    }

    @Test
    fun opacityIsClampedToTheUsableRange() {
        val settings = WidgetBackground.Settings.defaults()
        settings.opacity = 400
        settings.clamp()
        assertEquals(100, settings.opacity)

        settings.opacity = -10
        settings.clamp()
        assertEquals(5, settings.opacity)
    }

    @Test
    fun jsonRoundTripPreservesColourAndOpacity() {
        val settings = WidgetBackground.Settings()
        settings.color = 0xFF14342B.toInt()
        settings.opacity = 35
        settings.enabled = false

        val restored = WidgetBackground.Settings.fromJson(settings.toJson())

        assertEquals(0xFF14342B.toInt(), restored.color)
        assertEquals(35, restored.opacity)
        assertFalse(restored.enabled)
    }

    @Test
    fun corruptColourFallsBackToTheDefault() {
        val json = JSONObject().apply {
            put("on", true)
            put("color", "not-a-colour")
            put("opacity", 60)
        }

        val restored = WidgetBackground.Settings.fromJson(json)
        assertEquals(0xFF12161C.toInt(), restored.color)
        assertEquals(60, restored.opacity)
    }

    @Test
    fun presetPaletteHasNoDuplicates() {
        val presets = WidgetBackground.PRESET_COLORS
        assertTrue(presets.size >= 8)
        for (i in presets.indices) {
            // Every preset must be a usable RGB colour.
            assertEquals(0xFF000000.toInt(), presets[i] and 0xFF000000.toInt())
            for (j in i + 1 until presets.size) {
                assertTrue("duplicate preset colour at $i and $j", presets[i] != presets[j])
            }
        }
    }

    @Test
    fun clockSettingsRoundTripCarriesTheBackground() {
        val settings = WidgetPrefs.ClockSettings.defaults()
        settings.background = WidgetBackground.Settings.none()
        settings.background.opacity = 20

        val restored = WidgetPrefs.ClockSettings.fromJson(settings.toJson())

        assertFalse(restored.background.enabled)
        assertEquals(20, restored.background.opacity)
    }

    @Test
    fun worldClockSettingsRoundTripCarriesTheBackground() {
        val settings = WidgetPrefs.WorldClockSettings.defaults()
        settings.background.color = 0xFF1B2A41.toInt()
        settings.background.opacity = 55

        val restored = WidgetPrefs.WorldClockSettings.fromJson(settings.toJson())

        assertTrue(restored.background.enabled)
        assertEquals(0xFF1B2A41.toInt(), restored.background.color)
        assertEquals(55, restored.background.opacity)
    }

    @Test
    fun settingsWithoutABackgroundKeyUseTheDefault() {
        val restored = WidgetPrefs.ClockSettings.fromJson(JSONObject())
        assertTrue(restored.background.enabled)
        assertEquals(0xFF12161C.toInt(), restored.background.color)
    }
}
