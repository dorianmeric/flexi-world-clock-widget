package com.flexi.worldclock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlin.math.roundToInt

/**
 * Builds the [RemoteViews] of the world clock widget: one column per timezone, laid out side by
 * side and distributed across the widget width, with a customisable gap between columns.
 */
internal object WorldClockWidgetViews {

    /** Approximate width of one launcher grid cell, used to interpret the configured width. */
    const val CELL_DP = 74f

    private const val ROW_HORIZONTAL_PADDING_DP = 8f
    private const val ROW_VERTICAL_PADDING_DP = 6f
    private const val FALLBACK_HEIGHT_DP = 56f

    fun build(context: Context, appWidgetId: Int, options: Bundle?): RemoteViews =
        build(context, WidgetPrefs.loadWorld(context, appWidgetId), appWidgetId, options)

    fun build(
        context: Context,
        settings: WidgetPrefs.WorldClockSettings,
        appWidgetId: Int,
        options: Bundle?,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.world_clock_widget)

        val zones = settings.zones.ifEmpty { WidgetPrefs.WorldClockSettings.defaults().zones }

        val density = context.resources.displayMetrics.density
        val horizontal = (ROW_HORIZONTAL_PADDING_DP * density).roundToInt()
        val vertical = (ROW_VERTICAL_PADDING_DP * density).roundToInt()
        val spacingPx = (settings.spacingDp * density).roundToInt()
        val count = minOf(zones.size, WidgetPrefs.WorldClockSettings.MAX_ZONES)

        // Every column is a weighted child of the row, so the columns plus the gaps between
        // them always fill the width the launcher granted, at whatever size the widget is.
        views.removeAllViews(R.id.world_row)
        val now = System.currentTimeMillis()
        for (i in 0 until count) {
            val column = buildColumn(context, zones[i], settings, now)
            if (i < count - 1 && spacingPx > 0) {
                column.setViewLayoutMargin(
                    R.id.zone_column,
                    RemoteViews.MARGIN_END,
                    spacingPx.toFloat(),
                    TypedValue.COMPLEX_UNIT_PX,
                )
            }
            views.addView(R.id.world_row, column)
        }

        views.setViewPadding(R.id.world_row, horizontal, vertical, horizontal, vertical)

        WidgetBackground.apply(
            views,
            R.id.widget_background,
            settings.background,
            ClockWidgetViews.widgetWidthPx(context, options),
            widgetHeightPx(context, options),
            density,
        )

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            views.setOnClickPendingIntent(R.id.world_root, configIntent(context, appWidgetId))
        }
        return views
    }

    private fun buildColumn(
        context: Context,
        zone: WidgetPrefs.ZoneEntry,
        settings: WidgetPrefs.WorldClockSettings,
        now: Long,
    ): RemoteViews {
        val column = RemoteViews(context.packageName, R.layout.world_clock_column)

        column.setTextViewText(R.id.zone_city, Fonts.spanned(zone.label, settings.city.family))
        column.setTextViewTextSize(
            R.id.zone_city,
            TypedValue.COMPLEX_UNIT_SP,
            settings.city.sizeSp,
        )

        val format = Fonts.clockFormat(ClockFormats.ZONE_TIME, settings.time.family)
        column.setCharSequence(R.id.zone_time, "setFormat12Hour", format)
        column.setCharSequence(R.id.zone_time, "setFormat24Hour", format)
        column.setString(R.id.zone_time, "setTimeZone", zone.zoneId)
        column.setTextViewTextSize(
            R.id.zone_time,
            TypedValue.COMPLEX_UNIT_SP,
            settings.time.sizeSp,
        )

        if (settings.showOffset) {
            column.setViewVisibility(R.id.zone_offset, View.VISIBLE)
            column.setTextViewText(
                R.id.zone_offset,
                Fonts.spanned(ClockFormats.utcLabel(zone.zoneId, now), settings.offset.family),
            )
            column.setTextViewTextSize(
                R.id.zone_offset,
                TypedValue.COMPLEX_UNIT_SP,
                settings.offset.sizeSp,
            )
        } else {
            column.setViewVisibility(R.id.zone_offset, View.GONE)
        }
        return column
    }

    /** Width requested in the configuration screen, in dp. */
    fun configuredWidthDp(settings: WidgetPrefs.WorldClockSettings): Float =
        settings.widthCells * CELL_DP

    /** Height of the widget in px, preferring the launcher's reported height. */
    private fun widgetHeightPx(context: Context, options: Bundle?): Int {
        val density = context.resources.displayMetrics.density
        val reported = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
        val heightDp = if (reported > 0) reported.toFloat() else FALLBACK_HEIGHT_DP
        return maxOf(1, (heightDp * density).roundToInt())
    }

    fun configIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, WorldClockConfigActivity::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
            data = Uri.parse("flexi://worldclock/$appWidgetId")
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
        }
        return PendingIntent.getActivity(
            context,
            appWidgetId,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    fun updateNow(context: Context, appWidgetId: Int) {
        val manager = AppWidgetManager.getInstance(context)
        manager.updateAppWidget(
            appWidgetId,
            build(context, appWidgetId, manager.getAppWidgetOptions(appWidgetId)),
        )
    }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(
            ComponentName(context, WorldClockWidgetProvider::class.java),
        )
        for (id in ids) {
            manager.updateAppWidget(id, build(context, id, manager.getAppWidgetOptions(id)))
        }
    }
}
