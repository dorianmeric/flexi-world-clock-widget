package com.flexi.worldclock

import android.app.Activity
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.widget.Toast

/** Asks the launcher to pin a widget (fallback: the user adds it from the widget picker). */
internal object PinWidgets {

    fun isSupported(activity: Activity): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            activity.packageManager.hasSystemFeature(PackageManager.FEATURE_APP_WIDGETS)

    fun requestPin(activity: Activity, providerClass: Class<*>) {
        if (!isSupported(activity)) {
            showUnavailable(activity)
            return
        }

        val manager = AppWidgetManager.getInstance(activity)
        if (!manager.isRequestPinAppWidgetSupported) {
            showUnavailable(activity)
            return
        }

        val provider = ComponentName(activity, providerClass)
        val installed = manager.installedProviders.any { provider == it.provider }
        if (!installed) {
            showUnavailable(activity)
            return
        }

        val successCallback = PendingIntent.getActivity(
            activity,
            providerClass.hashCode(),
            Intent(activity, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        manager.requestPinAppWidget(provider, null, successCallback)
    }

    private fun showUnavailable(activity: Activity) {
        Toast.makeText(activity, R.string.pin_unavailable, Toast.LENGTH_LONG).show()
    }
}
