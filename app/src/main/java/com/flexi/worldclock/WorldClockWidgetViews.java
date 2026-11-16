package com.flexi.worldclock;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.widget.RemoteViews;

import java.util.List;

/**
 * Builds the {@link RemoteViews} of the world clock widget: one column per timezone, laid out
 * side by side and distributed across the widget width, with a customisable gap between columns.
 */
final class WorldClockWidgetViews {

    /** Approximate width of one launcher grid cell, used to interpret the configured width. */
    static final float CELL_DP = 74f;

    private static final float ROW_HORIZONTAL_PADDING_DP = 8f;
    private static final float ROW_VERTICAL_PADDING_DP = 6f;

    private WorldClockWidgetViews() {
    }

    static RemoteViews build(Context context, int appWidgetId, Bundle options) {
        return build(context, WidgetPrefs.loadWorld(context, appWidgetId), appWidgetId, options);
    }

    static RemoteViews build(Context context, WidgetPrefs.WorldClockSettings settings,
            int appWidgetId, Bundle options) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.world_clock_widget);

        List<WidgetPrefs.ZoneEntry> zones = settings.zones;
        if (zones.isEmpty()) {
            zones = WidgetPrefs.WorldClockSettings.defaults().zones;
        }

        float density = context.getResources().getDisplayMetrics().density;
        int horizontal = Math.round(ROW_HORIZONTAL_PADDING_DP * density);
        int vertical = Math.round(ROW_VERTICAL_PADDING_DP * density);
        int spacingPx = Math.round(settings.spacingDp * density);
        int count = Math.min(zones.size(), WidgetPrefs.WorldClockSettings.MAX_ZONES);

        // Every column is a weighted child of the row, so the columns plus the gaps between
        // them always fill the width the launcher granted, at whatever size the widget is.
        views.removeAllViews(R.id.world_row);
        long now = System.currentTimeMillis();
        for (int i = 0; i < count; i++) {
            RemoteViews column = buildColumn(context, zones.get(i), settings, now);
            if (i < count - 1 && spacingPx > 0) {
                column.setViewLayoutMargin(R.id.zone_column, RemoteViews.MARGIN_END,
                        spacingPx, TypedValue.COMPLEX_UNIT_PX);
            }
            views.addView(R.id.world_row, column);
        }

        views.setViewPadding(R.id.world_row, horizontal, vertical, horizontal, vertical);

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            views.setOnClickPendingIntent(R.id.world_root, configIntent(context, appWidgetId));
        }
        return views;
    }

    private static RemoteViews buildColumn(Context context, WidgetPrefs.ZoneEntry zone,
            WidgetPrefs.WorldClockSettings settings, long now) {
        RemoteViews column =
                new RemoteViews(context.getPackageName(), R.layout.world_clock_column);

        column.setTextViewText(R.id.zone_city,
                Fonts.spanned(zone.label, settings.city.family));
        column.setTextViewTextSize(R.id.zone_city, TypedValue.COMPLEX_UNIT_SP,
                settings.city.sizeSp);

        CharSequence format = Fonts.clockFormat(ClockFormats.ZONE_TIME, settings.time.family);
        column.setCharSequence(R.id.zone_time, "setFormat12Hour", format);
        column.setCharSequence(R.id.zone_time, "setFormat24Hour", format);
        column.setString(R.id.zone_time, "setTimeZone", zone.zoneId);
        column.setTextViewTextSize(R.id.zone_time, TypedValue.COMPLEX_UNIT_SP,
                settings.time.sizeSp);

        if (settings.showOffset) {
            column.setViewVisibility(R.id.zone_offset, View.VISIBLE);
            column.setTextViewText(R.id.zone_offset,
                    Fonts.spanned(ClockFormats.utcLabel(zone.zoneId, now),
                            settings.offset.family));
            column.setTextViewTextSize(R.id.zone_offset, TypedValue.COMPLEX_UNIT_SP,
                    settings.offset.sizeSp);
        } else {
            column.setViewVisibility(R.id.zone_offset, View.GONE);
        }
        return column;
    }

    /** Width requested in the configuration screen, in dp. */
    static float configuredWidthDp(WidgetPrefs.WorldClockSettings settings) {
        return settings.widthCells * CELL_DP;
    }

    static PendingIntent configIntent(Context context, int appWidgetId) {
        Intent intent = new Intent(context, WorldClockConfigActivity.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
        intent.setData(Uri.parse("flexi://worldclock/" + appWidgetId));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        return PendingIntent.getActivity(context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void updateNow(Context context, int appWidgetId) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        manager.updateAppWidget(appWidgetId,
                build(context, appWidgetId, manager.getAppWidgetOptions(appWidgetId)));
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(
                new ComponentName(context, WorldClockWidgetProvider.class));
        for (int id : ids) {
            manager.updateAppWidget(id, build(context, id, manager.getAppWidgetOptions(id)));
        }
    }
}