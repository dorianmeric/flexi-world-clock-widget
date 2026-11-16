package com.flexi.worldclock;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.TypedValue;
import android.widget.RemoteViews;

/** Builds the {@link RemoteViews} of the 2x1 clock widget. */
final class ClockWidgetViews {

    private ClockWidgetViews() {
    }

    static RemoteViews build(Context context, int appWidgetId) {
            return build(context, WidgetPrefs.loadClock(context, appWidgetId), appWidgetId, null);
    }

    static RemoteViews build(Context context, WidgetPrefs.ClockSettings settings, int appWidgetId) {
            return build(context, settings, appWidgetId, null);
        }

        static RemoteViews build(Context context, WidgetPrefs.ClockSettings settings, int appWidgetId,
                Bundle options) {
        String pkg = context.getPackageName();
        RemoteViews views = new RemoteViews(pkg, R.layout.clock_widget);

        applyClockStyle(views, R.id.clock_time, ClockFormats.TIME, settings.time);
        applyClockStyle(views, R.id.clock_date, ClockFormats.DATE, settings.date);

        int left = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, settings.leftPaddingDp,
                context.getResources().getDisplayMetrics()));
        int vertical = Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, settings.verticalPaddingDp,
                context.getResources().getDisplayMetrics()));
        views.setViewPadding(R.id.clock_content, left, vertical, 0, vertical);

                WidgetBackground.apply(views, R.id.widget_background, settings.background,
                        widgetWidthPx(context, options), widgetHeightPx(context, options),
                        context.getResources().getDisplayMetrics().density);

        if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            views.setOnClickPendingIntent(R.id.clock_root, configIntent(context, appWidgetId));
        }
        return views;
    }

    private static void applyClockStyle(RemoteViews views, int viewId, String pattern,
            WidgetPrefs.TextStyle style) {
        CharSequence format = Fonts.clockFormat(pattern, style.family);
        // Both formats are set to the same pattern so the user's 12/24-hour setting cannot
        // switch the widget to a different layout.
        views.setCharSequence(viewId, "setFormat12Hour", format);
        views.setCharSequence(viewId, "setFormat24Hour", format);
        views.setTextViewTextSize(viewId, TypedValue.COMPLEX_UNIT_SP, style.sizeSp);
    }

    /**
     * Width of the 2x1 widget in px. The launcher's reported width wins; the configured 2-cell
     * width is the fallback used for in-app previews.
     */
    static int widgetWidthPx(Context context, Bundle options) {
        float density = context.getResources().getDisplayMetrics().density;
        int reported = options == null ? 0
                : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0);
        float widthDp = reported > 0 ? reported : 2 * WorldClockWidgetViews.CELL_DP;
        return Math.max(1, Math.round(widthDp * density));
    }

    /** Height of the single-row widget in px, from the launcher's options. */
    static int widgetHeightPx(Context context, Bundle options) {
        float density = context.getResources().getDisplayMetrics().density;
        int reported = options == null ? 0
                : options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0);
        float heightDp = reported > 0 ? reported : 56f;
        return Math.max(1, Math.round(heightDp * density));
    }

    static PendingIntent configIntent(Context context, int appWidgetId) {
        Intent intent = new Intent(context, ClockConfigActivity.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE);
        intent.setData(Uri.parse("flexi://clock/" + appWidgetId));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId);
        return PendingIntent.getActivity(context, appWidgetId, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
    }

    static void updateNow(Context context, int appWidgetId) {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            manager.updateAppWidget(appWidgetId, build(context,
                    WidgetPrefs.loadClock(context, appWidgetId), appWidgetId,
                    manager.getAppWidgetOptions(appWidgetId)));
    }

    static void updateAll(Context context) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, ClockWidgetProvider.class));
        for (int id : ids) {
                manager.updateAppWidget(id, build(context, WidgetPrefs.loadClock(context, id), id,
                        manager.getAppWidgetOptions(id)));
        }
    }
}