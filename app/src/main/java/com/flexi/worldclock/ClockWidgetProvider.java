package com.flexi.worldclock;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/** Home-screen provider for the 2x1 clock widget. */
public class ClockWidgetProvider extends AppWidgetProvider {

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
                // The options carry the width/height the launcher will grant, which sizes the
                // generated background bitmap.
            appWidgetManager.updateAppWidget(appWidgetId,
                        ClockWidgetViews.build(context, WidgetPrefs.loadClock(context, appWidgetId),
                                appWidgetId, appWidgetManager.getAppWidgetOptions(appWidgetId)));
        }
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager appWidgetManager,
            int appWidgetId, Bundle newOptions) {
        appWidgetManager.updateAppWidget(appWidgetId,
                    ClockWidgetViews.build(context, WidgetPrefs.loadClock(context, appWidgetId),
                            appWidgetId, newOptions));
    }

    @Override
    public void onDeleted(Context context, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            WidgetPrefs.delete(context, appWidgetId);
        }
    }
}