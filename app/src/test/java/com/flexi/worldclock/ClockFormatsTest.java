package com.flexi.worldclock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.Locale;

public class ClockFormatsTest {

    /** 2023-11-16 23:30:55 UTC, a Thursday. */
    private static final long THURSDAY_2330 = 1700177455000L;

    @Test
    public void timePatternMatchesRequestedLayout() {
        // 23:30-55 => hours, minutes and seconds separated by colons and a dash.
        String rendered = ClockFormats.format(ClockFormats.TIME, "UTC", THURSDAY_2330);
        assertEquals("23:30-55", rendered);
    }

    @Test
    public void datePatternMatchesRequestedLayout() {
        String rendered =
                ClockFormats.format(ClockFormats.DATE, "UTC", THURSDAY_2330, Locale.ENGLISH);
        assertEquals("Thursday 16-Nov", rendered);
    }

    @Test
    public void zoneTimePatternUsesHoursAndMinutes() {
        assertEquals("23:30", ClockFormats.format(ClockFormats.ZONE_TIME, "UTC", THURSDAY_2330));
    }

    @Test
    public void timePatternHonoursTheZone() {
        assertEquals("23:30-55", ClockFormats.format(ClockFormats.TIME, "UTC", THURSDAY_2330));
        assertEquals("00:30-55",
                ClockFormats.format(ClockFormats.TIME, "Europe/Berlin", THURSDAY_2330));
        assertEquals("08:30-55",
                ClockFormats.format(ClockFormats.TIME, "Asia/Tokyo", THURSDAY_2330));
    }

    @Test
    public void utcLabelFormatsWholeHours() {
        assertEquals("UTC+8", ClockFormats.utcLabel(8 * 3600_000));
        assertEquals("UTC-3", ClockFormats.utcLabel(-3 * 3600_000));
        assertEquals("UTC+0", ClockFormats.utcLabel(0));
    }

    @Test
    public void utcLabelFormatsPartialHours() {
        assertEquals("UTC+5:30", ClockFormats.utcLabel((5 * 60 + 30) * 60_000));
        assertEquals("UTC-3:30", ClockFormats.utcLabel(-(3 * 60 + 30) * 60_000));
        assertEquals("UTC+5:45", ClockFormats.utcLabel((5 * 60 + 45) * 60_000));
    }

    @Test
    public void utcLabelAccountsForDaylightSaving() {
        // Berlin is UTC+1 in January and UTC+2 in July.
        assertEquals("UTC+1", ClockFormats.utcLabel("Europe/Berlin", 1704067200000L));
        assertEquals("UTC+2", ClockFormats.utcLabel("Europe/Berlin", 1719792000000L));
    }

    @Test
    public void zoneValidation() {
        assertTrue(ClockFormats.isValidZone("Europe/Berlin"));
        assertTrue(ClockFormats.isValidZone("UTC"));
        assertFalse(ClockFormats.isValidZone("Mars/Olympus"));
        assertFalse(ClockFormats.isValidZone(""));
        assertFalse(ClockFormats.isValidZone(null));
    }
}