package com.flexi.worldclock

import java.util.Locale

/** Fixed catalogue of well known timezones with a friendly city label. */
internal object ZoneCatalog {

    /** A timezone choice: a city label plus the Olson zone id. */
    data class Zone(val label: String, val id: String)

    private val ENTRIES: List<Zone> = listOf(
        Zone("Amsterdam", "Europe/Amsterdam"),
        Zone("Anchorage", "America/Anchorage"),
        Zone("Athens", "Europe/Athens"),
        Zone("Auckland", "Pacific/Auckland"),
        Zone("Bangkok", "Asia/Bangkok"),
        Zone("Beijing", "Asia/Shanghai"),
        Zone("Berlin", "Europe/Berlin"),
        Zone("Bogotá", "America/Bogota"),
        Zone("Brisbane", "Australia/Brisbane"),
        Zone("Bucharest", "Europe/Bucharest"),
        Zone("Budapest", "Europe/Budapest"),
        Zone("Buenos Aires", "America/Argentina/Buenos_Aires"),
        Zone("Cairo", "Africa/Cairo"),
        Zone("Cape Town", "Africa/Johannesburg"),
        Zone("Caracas", "America/Caracas"),
        Zone("Chicago", "America/Chicago"),
        Zone("Copenhagen", "Europe/Copenhagen"),
        Zone("Dhaka", "Asia/Dhaka"),
        Zone("Delhi", "Asia/Kolkata"),
        Zone("Denver", "America/Denver"),
        Zone("Dubai", "Asia/Dubai"),
        Zone("Dublin", "Europe/Dublin"),
        Zone("Havana", "America/Havana"),
        Zone("Helsinki", "Europe/Helsinki"),
        Zone("Hong Kong", "Asia/Hong_Kong"),
        Zone("Honolulu", "Pacific/Honolulu"),
        Zone("Istanbul", "Europe/Istanbul"),
        Zone("Jakarta", "Asia/Jakarta"),
        Zone("Jerusalem", "Asia/Jerusalem"),
        Zone("Johannesburg", "Africa/Johannesburg"),
        Zone("Karachi", "Asia/Karachi"),
        Zone("Kathmandu", "Asia/Kathmandu"),
        Zone("Kuala Lumpur", "Asia/Kuala_Lumpur"),
        Zone("Lagos", "Africa/Lagos"),
        Zone("Lima", "America/Lima"),
        Zone("Lisbon", "Europe/Lisbon"),
        Zone("London", "Europe/London"),
        Zone("Los Angeles", "America/Los_Angeles"),
        Zone("Madrid", "Europe/Madrid"),
        Zone("Manila", "Asia/Manila"),
        Zone("Melbourne", "Australia/Melbourne"),
        Zone("Mexico City", "America/Mexico_City"),
        Zone("Moscow", "Europe/Moscow"),
        Zone("Mumbai", "Asia/Kolkata"),
        Zone("Nairobi", "Africa/Nairobi"),
        Zone("New York", "America/New_York"),
        Zone("Oslo", "Europe/Oslo"),
        Zone("Paris", "Europe/Paris"),
        Zone("Perth", "Australia/Perth"),
        Zone("Prague", "Europe/Prague"),
        Zone("Reykjavík", "Atlantic/Reykjavik"),
        Zone("Rio de Janeiro", "America/Sao_Paulo"),
        Zone("Riyadh", "Asia/Riyadh"),
        Zone("Rome", "Europe/Rome"),
        Zone("San Francisco", "America/Los_Angeles"),
        Zone("Santiago", "America/Santiago"),
        Zone("São Paulo", "America/Sao_Paulo"),
        Zone("Seattle", "America/Los_Angeles"),
        Zone("Seoul", "Asia/Seoul"),
        Zone("Shanghai", "Asia/Shanghai"),
        Zone("Singapore", "Asia/Singapore"),
        Zone("Sofia", "Europe/Sofia"),
        Zone("Stockholm", "Europe/Stockholm"),
        Zone("Sydney", "Australia/Sydney"),
        Zone("Taipei", "Asia/Taipei"),
        Zone("Tallinn", "Europe/Tallinn"),
        Zone("Tehran", "Asia/Tehran"),
        Zone("Tel Aviv", "Asia/Jerusalem"),
        Zone("Tokyo", "Asia/Tokyo"),
        Zone("Toronto", "America/Toronto"),
        Zone("Vancouver", "America/Vancouver"),
        Zone("Vientiane", "Asia/Vientiane"),
        Zone("Vienna", "Europe/Vienna"),
        Zone("Warsaw", "Europe/Warsaw"),
        Zone("Washington", "America/New_York"),
        Zone("Zürich", "Europe/Zurich"),
        Zone("UTC", "UTC"),
    )

    fun all(): List<Zone> = ENTRIES

    /** Best-effort label for an arbitrary zone id (catalogue match first, then the id tail). */
    fun labelFor(timeZoneId: String): String {
        ENTRIES.firstOrNull { it.id == timeZoneId }?.let { return it.label }
        val slash = timeZoneId.lastIndexOf('/')
        val tail = if (slash >= 0) timeZoneId.substring(slash + 1) else timeZoneId
        return tail.replace('_', ' ')
    }

    /** Case-insensitive search across city labels and zone ids. */
    fun search(query: String?): List<Zone> {
        val needle = query?.trim().orEmpty().lowercase(Locale.ROOT)
        if (needle.isEmpty()) {
            return all()
        }
        return ENTRIES.filter {
            it.label.lowercase(Locale.ROOT).contains(needle) ||
                it.id.lowercase(Locale.ROOT).contains(needle)
        }
    }

    /** Catalogue entries completed with the device's own zone. */
    fun withDeviceZone(deviceZone: Zone): List<Zone> =
        (ENTRIES + deviceZone).sortedWith { a, b -> a.label.compareTo(b.label, ignoreCase = true) }
}
