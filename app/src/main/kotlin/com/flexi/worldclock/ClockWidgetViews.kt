package com.flexi.worldclock

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.widget.RemoteViews
import kotlin.math.roundToInt

/** Builds the [RemoteViews] of the 2x1 clock widget. */
internal object ClockWidgetViews {

    fun build(context: Context, appWidgetId: Int): RemoteViews =
        build(context, WidgetPrefs.loadClock(context, appWidgetId), appWidgetId, null)

    fun build(
        context: Context,
        settings: WidgetPrefs.ClockSettings,
        appWidgetId: Int,
        options: Bundle? = null,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.clock_widget)
        val metrics = context.resources.displayMetrics

        applyClockStyle(views, R.id.clock_time, ClockFormats.TIME, settings.time)
        applyClockStyle(views, R.id.clock_date, ClockFormats.DATE, settings.date)

        val left = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            settings.leftPaddingDp,
            metrics,
        ).roundToInt()
        val vertical = TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            settings.verticalPaddingDp,
            metrics,
        ).roundToInt()
        views.setViewPadding(R.id.clock_content, left, vertical, 0, vertical)

        WidgetBackground.apply(
            views,
            R.id.widget_background,
            settings.background,
            widgetWidthPx(context, options),
            widgetHeightPx(context, options),
            metrics.density,
        )

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            views.setOnClickPendingIntent(R.id.clock_root, configIntent(context, appWidgetId))
        }
        return views
    }

    private fun applyClockStyle(
        views: RemoteViews,
        viewId: Int,
        pattern: String,
        style: WidgetPrefs.TextStyle,
    ) {
        val format = Fonts.clockFormat(pattern, style.family)
        // Both formats are set to the same pattern so the user's 12/24-hour setting cannot
        // switch the widget to a different layout.
        views.setCharSequence(viewId, "setFormat12Hour", format)
        views.setCharSequence(viewId, "setFormat24Hour", format)
        views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, style.sizeSp)
    }

    /**
     * Width of the 2x1 widget in px. The launcher's reported width wins; the configured 2-cell
     * width is the fallback used for in-app previews.
     */
    fun widgetWidthPx(context: Context, options: Bundle?): Int {
        val density = context.resources.displayMetrics.density
        val reported = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0) ?: 0
        val widthDp = if (reported > 0) reported.toFloat() else 2 * WorldClockWidgetViews.CELL_DP
        return maxOf(1, (widthDp * density).roundToInt())
    }

    /** Height of the single-row widget in px, from the launcher's options. */
    fun widgetHeightPx(context: Context, options: Bundle?): Int {
        val density = context.resources.displayMetrics.density
        val reported = options?.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0) ?: 0
        val heightDp = if (reported > 0) reported.toFloat() else 56f
        return maxOf(1, (heightDp * density).roundToInt())
    }

    fun configIntent(context: Context, appWidgetId: Int): PendingIntent {
        val intent = Intent(context, ClockConfigActivity::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
            data = Uri.parse("flexi://clock/$appWidgetId")
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
            build(
                context,
                WidgetPrefs.loadClock(context, appWidgetId),
                appWidgetId,
                manager.getAppWidgetOptions(appWidgetId),
            ),
        )
    }

    fun updateAll(context: Context) {
        val manager = AppWidgetManager.getInstance(context)
        val ids = manager.getAppWidgetIds(ComponentName(context, ClockWidgetProvider::class.java))
        for (id in ids) {
            manager.updateAppWidget(
                id,
                build(
                    context,
                    WidgetPrefs.loadClock(context, id),
                    id,
                    manager.getAppWidgetOptions(id),
                ),
            )
        }
    }
}
