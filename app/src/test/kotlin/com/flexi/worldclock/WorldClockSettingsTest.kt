package com.flexi.worldclock

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorldClockSettingsTest {

    @Test
    fun defaultsIncludeZonesWithinLimit() {
        val defaults = WidgetPrefs.WorldClockSettings.defaults()
        assertFalse(defaults.zones.isEmpty())
        assertTrue(defaults.zones.size <= WidgetPrefs.WorldClockSettings.MAX_ZONES)
        for (zone in defaults.zones) {
            assertTrue(ClockFormats.isValidZone(zone.zoneId))
        }
    }

    @Test
    fun jsonRoundTripPreservesFormatting() {
        val settings = WidgetPrefs.WorldClockSettings.defaults()
        settings.city.family = "serif"
        settings.city.sizeSp = 15f
        settings.time.family = "monospace"
        settings.time.sizeSp = 24f
        settings.offset.sizeSp = 11f
        settings.showOffset = true
        settings.spacingDp = 14f
        settings.widthCells = 5
        settings.zones.clear()
        settings.zones.add(WidgetPrefs.ZoneEntry("Delhi", "Asia/Kolkata"))
        settings.zones.add(WidgetPrefs.ZoneEntry("Honolulu", "Pacific/Honolulu"))

        val restored = WidgetPrefs.WorldClockSettings.fromJson(settings.toJson())

        assertEquals("serif", restored.city.family)
        assertEquals(15f, restored.city.sizeSp, 0.001f)
        assertEquals("monospace", restored.time.family)
        assertEquals(24f, restored.time.sizeSp, 0.001f)
        assertEquals(11f, restored.offset.sizeSp, 0.001f)
        assertTrue(restored.showOffset)
        assertEquals(14f, restored.spacingDp, 0.001f)
        assertEquals(5, restored.widthCells)
        assertEquals(2, restored.zones.size)
        assertEquals("Delhi", restored.zones[0].label)
        assertEquals("Asia/Kolkata", restored.zones[0].zoneId)
    }

    @Test
    fun zoneCountIsCappedAtTheLimit() {
        val settings = WidgetPrefs.WorldClockSettings.defaults()
        settings.zones.clear()
        repeat(WidgetPrefs.WorldClockSettings.MAX_ZONES + 5) {
            settings.zones.add(WidgetPrefs.ZoneEntry("Tokyo", "Asia/Tokyo"))
        }

        val restored = WidgetPrefs.WorldClockSettings.fromJson(settings.toJson())

        assertEquals(WidgetPrefs.WorldClockSettings.MAX_ZONES, restored.zones.size)
    }

    @Test
    fun invalidStoredValuesFallBackToDefaults() {
        val settings = WidgetPrefs.WorldClockSettings.defaults()
        settings.widthCells = 99
        settings.spacingDp = -20f
        settings.time.sizeSp = 400f
        settings.zones.clear()
        settings.zones.add(WidgetPrefs.ZoneEntry("Nowhere", "Mars/Olympus"))

        val restored = WidgetPrefs.WorldClockSettings.fromJson(settings.toJson())

        assertEquals(WidgetPrefs.WorldClockSettings.MAX_WIDTH_CELLS, restored.widthCells)
        assertEquals(0f, restored.spacingDp, 0.001f)
        assertEquals(96f, restored.time.sizeSp, 0.001f)
        // The only stored zone was invalid, so the defaults are restored.
        assertEquals(WidgetPrefs.WorldClockSettings.defaults().zones.size, restored.zones.size)
    }

    @Test
    fun clockSettingsRoundTrip() {
        val settings = WidgetPrefs.ClockSettings.defaults()
        settings.time.family = "sans-serif-light"
        settings.time.sizeSp = 42f
        settings.date.family = "serif"
        settings.date.sizeSp = 18f
        settings.leftPaddingDp = 30f

        val restored = WidgetPrefs.ClockSettings.fromJson(settings.toJson())

        assertEquals("sans-serif-light", restored.time.family)
        assertEquals(42f, restored.time.sizeSp, 0.001f)
        assertEquals("serif", restored.date.family)
        assertEquals(18f, restored.date.sizeSp, 0.001f)
        assertEquals(30f, restored.leftPaddingDp, 0.001f)
    }

    @Test
    fun unknownFontFamilyFallsBackToDefault() {
        val settings = WidgetPrefs.ClockSettings.defaults()
        settings.time.family = "not-a-font"

        val restored = WidgetPrefs.ClockSettings.fromJson(settings.toJson())

        assertEquals(Fonts.DEFAULT_FAMILY, restored.time.family)
    }

    @Test
    fun configuredWidthIsDerivedFromTheCellCount() {
        val settings = WidgetPrefs.WorldClockSettings.defaults()
        settings.widthCells = 3
        assertEquals(
            3 * WorldClockWidgetViews.CELL_DP,
            WorldClockWidgetViews.configuredWidthDp(settings),
            0.01f,
        )
    }

    @Test
    fun reorderMovesTheEntryAndKeepsTheRestInOrder() {
        val order = mutableListOf("London", "New York", "Tokyo", "Sydney")

        DragReorder.reorder(order, 3, 0)
        assertEquals(listOf("Sydney", "London", "New York", "Tokyo"), order)

        DragReorder.reorder(order, 0, 2)
        assertEquals(listOf("London", "New York", "Sydney", "Tokyo"), order)
    }

    @Test
    fun reorderIgnoresOutOfRangeMoves() {
        val order = mutableListOf("London", "Tokyo")
        DragReorder.reorder(order, -1, 1)
        DragReorder.reorder(order, 0, 5)
        DragReorder.reorder(order, 1, 1)
        assertEquals(listOf("London", "Tokyo"), order)
    }

    @Test
    fun reorderOfTwoZonesSwapsThem() {
        val order = mutableListOf("Vientiane", "Dhaka")
        DragReorder.reorder(order, 0, 1)
        assertEquals(listOf("Dhaka", "Vientiane"), order)
    }

    @Test
    fun newCitiesAreInTheCatalogue() {
        assertTrue(ClockFormats.isValidZone("Asia/Vientiane"))
        assertTrue(ClockFormats.isValidZone("Asia/Dhaka"))
        assertEquals("Vientiane", ZoneCatalog.labelFor("Asia/Vientiane"))
        assertEquals("Dhaka", ZoneCatalog.labelFor("Asia/Dhaka"))
        assertFalse(ZoneCatalog.search("vientiane").isEmpty())
        assertFalse(ZoneCatalog.search("dhaka").isEmpty())
    }
}
