package com.flexi.worldclock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** Home-screen provider for the 2x1 clock widget. */
class ClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            // The options carry the width/height the launcher will grant, which sizes the
            // generated background bitmap.
            appWidgetManager.updateAppWidget(
                appWidgetId,
                ClockWidgetViews.build(
                    context,
                    WidgetPrefs.loadClock(context, appWidgetId),
                    appWidgetId,
                    appWidgetManager.getAppWidgetOptions(appWidgetId),
                ),
            )
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle?,
    ) {
        appWidgetManager.updateAppWidget(
            appWidgetId,
            ClockWidgetViews.build(
                context,
                WidgetPrefs.loadClock(context, appWidgetId),
                appWidgetId,
                newOptions,
            ),
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            WidgetPrefs.delete(context, appWidgetId)
        }
    }
}
