package com.flexi.worldclock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Fixed catalogue of well known timezones with a friendly city label. */
public final class ZoneCatalog {

    /** A timezone choice: a city label plus the Olson zone id. */
    public static final class Zone {
        public final String label;
        public final String id;

        public Zone(String label, String id) {
            this.label = label;
            this.id = id;
        }

        @Override
        public String toString() {
            return label + " (" + id + ")";
        }
    }

    private static final String[][] ENTRIES = {
            {"Amsterdam", "Europe/Amsterdam"},
            {"Anchorage", "America/Anchorage"},
            {"Athens", "Europe/Athens"},
            {"Auckland", "Pacific/Auckland"},
            {"Bangkok", "Asia/Bangkok"},
            {"Beijing", "Asia/Shanghai"},
            {"Berlin", "Europe/Berlin"},
            {"Bogotá", "America/Bogota"},
            {"Brisbane", "Australia/Brisbane"},
            {"Bucharest", "Europe/Bucharest"},
            {"Budapest", "Europe/Budapest"},
            {"Buenos Aires", "America/Argentina/Buenos_Aires"},
            {"Cairo", "Africa/Cairo"},
            {"Cape Town", "Africa/Johannesburg"},
            {"Caracas", "America/Caracas"},
            {"Chicago", "America/Chicago"},
            {"Copenhagen", "Europe/Copenhagen"},
                        {"Dhaka", "Asia/Dhaka"},
            {"Delhi", "Asia/Kolkata"},
            {"Denver", "America/Denver"},
            {"Dubai", "Asia/Dubai"},
            {"Dublin", "Europe/Dublin"},
            {"Havana", "America/Havana"},
            {"Helsinki", "Europe/Helsinki"},
            {"Hong Kong", "Asia/Hong_Kong"},
            {"Honolulu", "Pacific/Honolulu"},
            {"Istanbul", "Europe/Istanbul"},
            {"Jakarta", "Asia/Jakarta"},
            {"Jerusalem", "Asia/Jerusalem"},
            {"Johannesburg", "Africa/Johannesburg"},
            {"Karachi", "Asia/Karachi"},
            {"Kathmandu", "Asia/Kathmandu"},
            {"Kuala Lumpur", "Asia/Kuala_Lumpur"},
            {"Lagos", "Africa/Lagos"},
            {"Lima", "America/Lima"},
            {"Lisbon", "Europe/Lisbon"},
            {"London", "Europe/London"},
            {"Los Angeles", "America/Los_Angeles"},
            {"Madrid", "Europe/Madrid"},
            {"Manila", "Asia/Manila"},
            {"Melbourne", "Australia/Melbourne"},
            {"Mexico City", "America/Mexico_City"},
            {"Moscow", "Europe/Moscow"},
            {"Mumbai", "Asia/Kolkata"},
            {"Nairobi", "Africa/Nairobi"},
            {"New York", "America/New_York"},
            {"Oslo", "Europe/Oslo"},
            {"Paris", "Europe/Paris"},
            {"Perth", "Australia/Perth"},
            {"Prague", "Europe/Prague"},
            {"Reykjavík", "Atlantic/Reykjavik"},
            {"Rio de Janeiro", "America/Sao_Paulo"},
            {"Riyadh", "Asia/Riyadh"},
            {"Rome", "Europe/Rome"},
            {"San Francisco", "America/Los_Angeles"},
            {"Santiago", "America/Santiago"},
            {"São Paulo", "America/Sao_Paulo"},
            {"Seattle", "America/Los_Angeles"},
            {"Seoul", "Asia/Seoul"},
            {"Shanghai", "Asia/Shanghai"},
            {"Singapore", "Asia/Singapore"},
            {"Sofia", "Europe/Sofia"},
            {"Stockholm", "Europe/Stockholm"},
            {"Sydney", "Australia/Sydney"},
            {"Taipei", "Asia/Taipei"},
            {"Tallinn", "Europe/Tallinn"},
            {"Tehran", "Asia/Tehran"},
            {"Tel Aviv", "Asia/Jerusalem"},
            {"Tokyo", "Asia/Tokyo"},
            {"Toronto", "America/Toronto"},
            {"Vancouver", "America/Vancouver"},
                        {"Vientiane", "Asia/Vientiane"},
            {"Vienna", "Europe/Vienna"},
            {"Warsaw", "Europe/Warsaw"},
            {"Washington", "America/New_York"},
            {"Zürich", "Europe/Zurich"},
            {"UTC", "UTC"},
    };

    private ZoneCatalog() {
    }

    public static List<Zone> all() {
        List<Zone> zones = new ArrayList<>(ENTRIES.length);
        for (String[] entry : ENTRIES) {
            zones.add(new Zone(entry[0], entry[1]));
        }
        return zones;
    }

    /** Best-effort label for an arbitrary zone id (catalogue match first, then the id tail). */
    public static String labelFor(String timeZoneId) {
        for (Zone zone : all()) {
            if (zone.id.equals(timeZoneId)) {
                return zone.label;
            }
        }
        int slash = timeZoneId.lastIndexOf('/');
        String tail = slash >= 0 ? timeZoneId.substring(slash + 1) : timeZoneId;
        return tail.replace('_', ' ');
    }

    /** Case-insensitive search across city labels and zone ids. */
    public static List<Zone> search(String query) {
        if (query == null || query.trim().isEmpty()) {
            return all();
        }
        String needle = query.trim().toLowerCase(Locale.ROOT);
        List<Zone> matches = new ArrayList<>();
        for (Zone zone : all()) {
            if (zone.label.toLowerCase(Locale.ROOT).contains(needle)
                    || zone.id.toLowerCase(Locale.ROOT).contains(needle)) {
                matches.add(zone);
            }
        }
        return matches;
    }

    /** Catalogue entries completed with the device's own zone when it is missing. */
    public static List<Zone> withDeviceZone(Zone deviceZone) {
        List<Zone> zones = all();
        List<Zone> result = new ArrayList<>(zones);
        result.add(deviceZone);
        Collections.sort(result, (a, b) -> a.label.compareToIgnoreCase(b.label));
        return result;
    }
}