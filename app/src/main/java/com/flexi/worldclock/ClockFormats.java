package com.flexi.worldclock;

import java.util.Arrays;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.Locale;
import java.util.TimeZone;

/** Formatting helpers shared by the widgets, the previews and the unit tests. */
public final class ClockFormats {

    /** 24-hour clock with seconds, renders as {@code 23:30-55}. */
    public static final String TIME = "HH:mm'-'ss";

    /** Weekday, day and short month, renders as {@code Monday 16-Nov}. */
    public static final String DATE = "EEEE d'-'MMM";

    /** Hour and minute used by the world clock columns, renders as {@code 23:45}. */
    public static final String ZONE_TIME = "HH:mm";

    private ClockFormats() {
    }

    /**
     * Returns the "UTC+8" style label for {@code timeZoneId} at {@code nowMillis}, taking the
     * zone's current offset (including daylight saving) into account.
     */
    public static String utcLabel(String timeZoneId, long nowMillis) {
        if (timeZoneId == null) {
            return utcLabel(0);
        }
        return utcLabel(TimeZone.getTimeZone(timeZoneId).getOffset(nowMillis));
    }

    /** Formats a raw offset in milliseconds as {@code UTC+5:30} / {@code UTC-3}. */
    public static String utcLabel(int rawOffsetMillis) {
        int totalMinutes = Math.round(rawOffsetMillis / 60000f);
        boolean negative = totalMinutes < 0;
        int abs = Math.abs(totalMinutes);
        int hours = abs / 60;
        int minutes = abs % 60;

        StringBuilder label = new StringBuilder(8);
        label.append("UTC").append(negative ? '-' : '+').append(hours);
        if (minutes != 0) {
            label.append(':');
            if (minutes < 10) {
                label.append('0');
            }
            label.append(minutes);
        }
        return label.toString();
    }

    /** True when {@code timeZoneId} resolves to a real (or UTC) zone id. */
    public static boolean isValidZone(String timeZoneId) {
        if (timeZoneId == null || timeZoneId.isEmpty()) {
            return false;
        }
        return "UTC".equals(timeZoneId) || Arrays.asList(TimeZone.getAvailableIDs()).contains(timeZoneId);
    }

    /** Current local zone id, e.g. {@code Europe/Berlin}. */
    public static String deviceTimeZoneId() {
        return TimeZone.getDefault().getID();
    }

    /** Renders {@code pattern} in {@code timeZoneId} at {@code millis}; used by tests. */
    public static String format(String pattern, String timeZoneId, long millis) {
            return format(pattern, timeZoneId, millis, Locale.getDefault());
        }

        /** Renders {@code pattern} with an explicit locale; used by tests to stay deterministic. */
        public static String format(String pattern, String timeZoneId, long millis, Locale locale) {
            java.text.SimpleDateFormat formatter = new java.text.SimpleDateFormat(pattern, locale);
        formatter.setCalendar(calendar(timeZoneId, millis));
        return formatter.format(calendar(timeZoneId, millis).getTime());
    }

    private static Calendar calendar(String timeZoneId, long millis) {
        Calendar calendar = new GregorianCalendar(TimeZone.getTimeZone(timeZoneId));
        calendar.setTimeInMillis(millis);
        return calendar;
    }
}