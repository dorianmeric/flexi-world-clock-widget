package com.flexi.worldclock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.os.Bundle

/** Home-screen provider for the flexible world clock widget. */
class WorldClockWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        for (appWidgetId in appWidgetIds) {
            // Read the options back from the manager: onUpdate is also the first render after a
            // bind, where these options already carry the width the launcher will grant.
            appWidgetManager.updateAppWidget(
                appWidgetId,
                WorldClockWidgetViews.build(
                    context,
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
        // The launcher resized the widget: redistribute the columns over the new width.
        appWidgetManager.updateAppWidget(
            appWidgetId,
            WorldClockWidgetViews.build(context, appWidgetId, newOptions),
        )
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        for (appWidgetId in appWidgetIds) {
            WidgetPrefs.delete(context, appWidgetId)
        }
    }
}
