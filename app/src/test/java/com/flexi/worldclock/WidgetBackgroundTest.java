package com.flexi.worldclock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WidgetBackgroundTest {

    @Test
    public void defaultsAreAnOpaqueEnoughGraphite() {
        WidgetBackground.Settings settings = WidgetBackground.Settings.defaults();
        assertTrue(settings.enabled);
        assertEquals(0xFF12161C, settings.color);
        assertEquals(80, settings.opacity);
    }

    @Test
    public void resolvedColorCarriesTheConfiguredOpacity() {
        WidgetBackground.Settings settings = WidgetBackground.Settings.defaults();
        settings.color = 0xFF3B82F6;
        settings.opacity = 50;

        int resolved = settings.resolvedColor();
                assertEquals(0x3B82F6, resolved & 0x00FFFFFF);
        // 50% of 255 is 128 (127.5 rounded).
        assertEquals(128, (resolved >>> 24) & 0xFF);
    }

    @Test
    public void disabledBackgroundIsFullyTransparent() {
        WidgetBackground.Settings settings = WidgetBackground.Settings.none();
        assertFalse(settings.enabled);
        assertEquals(0, settings.resolvedColor() >>> 24);
    }

    @Test
    public void opacityIsClampedToTheUsableRange() {
        WidgetBackground.Settings settings = WidgetBackground.Settings.defaults();
        settings.opacity = 400;
        settings.clamp();
        assertEquals(100, settings.opacity);

        settings.opacity = -10;
        settings.clamp();
        assertEquals(5, settings.opacity);
    }

    @Test
    public void jsonRoundTripPreservesColourAndOpacity() {
        WidgetBackground.Settings settings = new WidgetBackground.Settings();
        settings.color = 0xFF14342B;
        settings.opacity = 35;
        settings.enabled = false;

        WidgetBackground.Settings restored =
                WidgetBackground.Settings.fromJson(settings.toJson());

        assertEquals(0xFF14342B, restored.color);
        assertEquals(35, restored.opacity);
        assertFalse(restored.enabled);
    }

    @Test
    public void corruptColourFallsBackToTheDefault() {
        org.json.JSONObject json = new org.json.JSONObject();
        try {
            json.put("on", true);
            json.put("color", "not-a-colour");
            json.put("opacity", 60);
        } catch (org.json.JSONException e) {
            throw new AssertionError(e);
        }

        WidgetBackground.Settings restored = WidgetBackground.Settings.fromJson(json);
        assertEquals(0xFF12161C, restored.color);
        assertEquals(60, restored.opacity);
    }

    @Test
    public void presetPaletteHasNoDuplicates() {
        int[] presets = WidgetBackground.PRESET_COLORS;
        assertTrue(presets.length >= 8);
        for (int i = 0; i < presets.length; i++) {
            // Every preset must be a usable RGB colour.
            assertEquals(0xFF000000, presets[i] & 0xFF000000);
            for (int j = i + 1; j < presets.length; j++) {
                assertTrue("duplicate preset colour at " + i + " and " + j,
                        presets[i] != presets[j]);
            }
        }
    }

    @Test
    public void clockSettingsRoundTripCarriesTheBackground() {
        WidgetPrefs.ClockSettings settings = WidgetPrefs.ClockSettings.defaults();
        settings.background = WidgetBackground.Settings.none();
        settings.background.opacity = 20;

        WidgetPrefs.ClockSettings restored =
                WidgetPrefs.ClockSettings.fromJson(settings.toJson());

        assertFalse(restored.background.enabled);
        assertEquals(20, restored.background.opacity);
    }

    @Test
    public void worldClockSettingsRoundTripCarriesTheBackground() {
        WidgetPrefs.WorldClockSettings settings = WidgetPrefs.WorldClockSettings.defaults();
        settings.background.color = 0xFF1B2A41;
        settings.background.opacity = 55;

        WidgetPrefs.WorldClockSettings restored =
                WidgetPrefs.WorldClockSettings.fromJson(settings.toJson());

        assertTrue(restored.background.enabled);
        assertEquals(0xFF1B2A41, restored.background.color);
        assertEquals(55, restored.background.opacity);
    }

    @Test
    public void settingsWithoutABackgroundKeyUseTheDefault() {
        WidgetPrefs.ClockSettings restored =
                WidgetPrefs.ClockSettings.fromJson(new org.json.JSONObject());
        assertTrue(restored.background.enabled);
        assertEquals(0xFF12161C, restored.background.color);
    }
}