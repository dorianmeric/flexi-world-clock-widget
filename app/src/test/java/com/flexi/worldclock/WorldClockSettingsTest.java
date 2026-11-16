package com.flexi.worldclock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class WorldClockSettingsTest {

    @Test
    public void defaultsIncludeZonesWithinLimit() {
        WidgetPrefs.WorldClockSettings defaults = WidgetPrefs.WorldClockSettings.defaults();
        assertFalse(defaults.zones.isEmpty());
        assertTrue(defaults.zones.size() <= WidgetPrefs.WorldClockSettings.MAX_ZONES);
        for (WidgetPrefs.ZoneEntry zone : defaults.zones) {
            assertTrue(ClockFormats.isValidZone(zone.zoneId));
        }
    }

    @Test
    public void jsonRoundTripPreservesFormatting() {
        WidgetPrefs.WorldClockSettings settings = WidgetPrefs.WorldClockSettings.defaults();
        settings.city.family = "serif";
        settings.city.sizeSp = 15f;
        settings.time.family = "monospace";
        settings.time.sizeSp = 24f;
        settings.offset.sizeSp = 11f;
        settings.showOffset = true;
        settings.spacingDp = 14f;
        settings.widthCells = 5;
        settings.zones.clear();
        settings.zones.add(new WidgetPrefs.ZoneEntry("Delhi", "Asia/Kolkata"));
        settings.zones.add(new WidgetPrefs.ZoneEntry("Honolulu", "Pacific/Honolulu"));

        WidgetPrefs.WorldClockSettings restored =
                WidgetPrefs.WorldClockSettings.fromJson(settings.toJson());

        assertEquals("serif", restored.city.family);
        assertEquals(15f, restored.city.sizeSp, 0.001f);
        assertEquals("monospace", restored.time.family);
        assertEquals(24f, restored.time.sizeSp, 0.001f);
        assertEquals(11f, restored.offset.sizeSp, 0.001f);
        assertTrue(restored.showOffset);
        assertEquals(14f, restored.spacingDp, 0.001f);
        assertEquals(5, restored.widthCells);
        assertEquals(2, restored.zones.size());
        assertEquals("Delhi", restored.zones.get(0).label);
        assertEquals("Asia/Kolkata", restored.zones.get(0).zoneId);
    }

    @Test
    public void zoneCountIsCappedAtTheLimit() {
        WidgetPrefs.WorldClockSettings settings = WidgetPrefs.WorldClockSettings.defaults();
        settings.zones.clear();
        for (int i = 0; i < WidgetPrefs.WorldClockSettings.MAX_ZONES + 5; i++) {
            settings.zones.add(new WidgetPrefs.ZoneEntry("Tokyo", "Asia/Tokyo"));
        }

        WidgetPrefs.WorldClockSettings restored =
                WidgetPrefs.WorldClockSettings.fromJson(settings.toJson());

        assertEquals(WidgetPrefs.WorldClockSettings.MAX_ZONES, restored.zones.size());
    }

    @Test
    public void invalidStoredValuesFallBackToDefaults() {
        WidgetPrefs.WorldClockSettings settings = WidgetPrefs.WorldClockSettings.defaults();
        settings.widthCells = 99;
        settings.spacingDp = -20f;
        settings.time.sizeSp = 400f;
        settings.zones.clear();
        settings.zones.add(new WidgetPrefs.ZoneEntry("Nowhere", "Mars/Olympus"));

        WidgetPrefs.WorldClockSettings restored =
                WidgetPrefs.WorldClockSettings.fromJson(settings.toJson());

        assertEquals(WidgetPrefs.WorldClockSettings.MAX_WIDTH_CELLS, restored.widthCells);
        assertEquals(0f, restored.spacingDp, 0.001f);
        assertEquals(96f, restored.time.sizeSp, 0.001f);
        // The only stored zone was invalid, so the defaults are restored.
        assertEquals(WidgetPrefs.WorldClockSettings.defaults().zones.size(),
                restored.zones.size());
    }

    @Test
    public void clockSettingsRoundTrip() {
        WidgetPrefs.ClockSettings settings = WidgetPrefs.ClockSettings.defaults();
        settings.time.family = "sans-serif-light";
        settings.time.sizeSp = 42f;
        settings.date.family = "serif";
        settings.date.sizeSp = 18f;
        settings.leftPaddingDp = 30f;

        WidgetPrefs.ClockSettings restored =
                WidgetPrefs.ClockSettings.fromJson(settings.toJson());

        assertEquals("sans-serif-light", restored.time.family);
        assertEquals(42f, restored.time.sizeSp, 0.001f);
        assertEquals("serif", restored.date.family);
        assertEquals(18f, restored.date.sizeSp, 0.001f);
        assertEquals(30f, restored.leftPaddingDp, 0.001f);
    }

    @Test
    public void unknownFontFamilyFallsBackToDefault() {
        WidgetPrefs.ClockSettings settings = WidgetPrefs.ClockSettings.defaults();
        settings.time.family = "not-a-font";

        WidgetPrefs.ClockSettings restored =
                WidgetPrefs.ClockSettings.fromJson(settings.toJson());

        assertEquals(Fonts.DEFAULT_FAMILY, restored.time.family);
    }

    @Test
    public void configuredWidthIsDerivedFromTheCellCount() {
        WidgetPrefs.WorldClockSettings settings = WidgetPrefs.WorldClockSettings.defaults();
        settings.widthCells = 3;
        assertEquals(3 * WorldClockWidgetViews.CELL_DP,
                WorldClockWidgetViews.configuredWidthDp(settings), 0.01f);
    }
}
