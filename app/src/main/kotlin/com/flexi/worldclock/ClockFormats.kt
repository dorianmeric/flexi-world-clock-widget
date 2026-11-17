package com.flexi.worldclock

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.GregorianCalendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs
import kotlin.math.roundToInt

/** Formatting helpers shared by the widgets, the previews and the unit tests. */
internal object ClockFormats {

    /** 24-hour clock with seconds, renders as `23:30-55`. */
    const val TIME = "HH:mm'-'ss"

    /** Weekday, day and short month, renders as `Monday 16-Nov`. */
    const val DATE = "EEEE d'-'MMM"

    /** Hour and minute used by the world clock columns, renders as `23:45`. */
    const val ZONE_TIME = "HH:mm"

    /**
     * Returns the "UTC+8" style label for [timeZoneId] at [nowMillis], taking the zone's current
     * offset (including daylight saving) into account.
     */
    fun utcLabel(timeZoneId: String?, nowMillis: Long): String =
        if (timeZoneId == null) {
            utcLabel(0)
        } else {
            utcLabel(TimeZone.getTimeZone(timeZoneId).getOffset(nowMillis))
        }

    /** Formats a raw offset in milliseconds as `UTC+5:30` / `UTC-3`. */
    fun utcLabel(rawOffsetMillis: Int): String {
        val totalMinutes = (rawOffsetMillis / 60000f).roundToInt()
        val negative = totalMinutes < 0
        val absolute = abs(totalMinutes)
        val hours = absolute / 60
        val minutes = absolute % 60

        return buildString(8) {
            append("UTC").append(if (negative) '-' else '+').append(hours)
            if (minutes != 0) {
                append(':')
                if (minutes < 10) {
                    append('0')
                }
                append(minutes)
            }
        }
    }

    /** True when [timeZoneId] resolves to a real (or UTC) zone id. */
    fun isValidZone(timeZoneId: String?): Boolean =
        !timeZoneId.isNullOrEmpty() &&
            (timeZoneId == "UTC" || timeZoneId in TimeZone.getAvailableIDs())

    /** Current local zone id, e.g. `Europe/Berlin`. */
    fun deviceTimeZoneId(): String = TimeZone.getDefault().id

    /** Renders [pattern] in [timeZoneId] at [millis]; tests pass a fixed [locale] to stay stable. */
    fun format(
        pattern: String,
        timeZoneId: String,
        millis: Long,
        locale: Locale = Locale.getDefault(),
    ): String {
        val moment = calendar(timeZoneId, millis)
        val formatter = SimpleDateFormat(pattern, locale)
        formatter.calendar = moment
        return formatter.format(moment.time)
    }

    private fun calendar(timeZoneId: String, millis: Long): Calendar =
        GregorianCalendar(TimeZone.getTimeZone(timeZoneId)).apply { timeInMillis = millis }
}
