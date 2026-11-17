package com.flexi.worldclock

import android.appwidget.AppWidgetManager
import android.content.Context
import android.graphics.Color
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.RemoteViews
import kotlin.math.roundToInt

/** Renders a [RemoteViews] hierarchy inside a configuration activity for live preview. */
internal object PreviewRenderer {

    /**
     * Applies [remoteViews] into [host]. When [widthDp] is positive the preview is pinned to that
     * width (mirroring the widget's requested width); otherwise it fills the available space.
     */
    fun show(host: FrameLayout, remoteViews: RemoteViews, widthDp: Float) {
        val context = host.context
        host.removeAllViews()
        host.setBackgroundColor(Color.TRANSPARENT)

        val view = remoteViews.apply(context, host)
        val params = if (widthDp > 0f) {
            val widthPx = TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP,
                widthDp,
                context.resources.displayMetrics,
            ).roundToInt()
            FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT)
        } else {
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
            )
        }
        view.layoutParams = params
        host.addView(view)
    }

    /** Widget options matching a preview so the builder can size columns as the launcher would. */
    fun options(widthDp: Float): Bundle = Bundle().apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, widthDp.roundToInt())
    }

    /** Widget options matching a preview at a given width and height, in dp. */
    fun options(widthDp: Float, heightDp: Float): Bundle = options(widthDp).apply {
        putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, heightDp.roundToInt())
    }
}
