package com.flexi.worldclock;

import android.app.Activity;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;

/** Asks the launcher to pin a widget (fallback: the user adds it from the widget picker). */
final class PinWidgets {

    private PinWidgets() {
    }

    static boolean isSupported(Activity activity) {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                && activity.getPackageManager()
                .hasSystemFeature(android.content.pm.PackageManager.FEATURE_APP_WIDGETS);
    }

    static void requestPin(Activity activity, Class<?> providerClass) {
        if (!isSupported(activity)) {
            Toast.makeText(activity, R.string.pin_unavailable, Toast.LENGTH_LONG).show();
            return;
        }

        AppWidgetManager manager = AppWidgetManager.getInstance(activity);
        if (!manager.isRequestPinAppWidgetSupported()) {
            Toast.makeText(activity, R.string.pin_unavailable, Toast.LENGTH_LONG).show();
            return;
        }

        ComponentName provider = new ComponentName(activity, providerClass);
                boolean installed = false;
                for (AppWidgetProviderInfo info : manager.getInstalledProviders()) {
                    if (provider.equals(info.provider)) {
                        installed = true;
                        break;
                    }
                }
                if (!installed) {
            Toast.makeText(activity, R.string.pin_unavailable, Toast.LENGTH_LONG).show();
            return;
        }

        PendingIntent successCallback = PendingIntent.getActivity(activity, providerClass.hashCode(),
                new Intent(activity, MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        manager.requestPinAppWidget(provider, null, successCallback);
    }
}