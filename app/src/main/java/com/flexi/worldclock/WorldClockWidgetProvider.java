package com.flexi.worldclock;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/** Home-screen provider for the flexible world clock widget. */
public class WorldClockWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
                // Read the options back from the manager: onUpdate is also the first render after a
                // bind, where these options already carry the width the launcher will grant.
            appWidgetManager.updateAppWidget(appWidgetId, WorldClockWidgetViews.build(
                    context, appWidgetId, appWidgetManager.getAppWidgetOptions(appWidgetId)));
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId, Bundle newOptions) {
        // The launcher resized the widget: redistribute the columns over the new width.
        appWidgetManager.updateAppWidget(appWidgetId, WorldClockWidgetViews.build(
                context, appWidgetId, newOptions));
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            WidgetPrefs.delete(context, appWidgetId);
        }
    }
}