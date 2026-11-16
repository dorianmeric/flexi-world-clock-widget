package com.flexi.worldclock;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.RemoteViews;

/** Renders a {@link RemoteViews} hierarchy inside a configuration activity for live preview. */
final class PreviewRenderer {

    private PreviewRenderer() {
    }

    /**
     * Applies {@code remoteViews} into {@code host}. When {@code widthDp} is positive the preview
     * is pinned to that width (mirroring the widget's requested width); otherwise it fills the
     * available space.
     */
    static void show(FrameLayout host, RemoteViews remoteViews, float widthDp) {
        Context context = host.getContext();
        host.removeAllViews();
        host.setBackgroundColor(Color.TRANSPARENT);

        View view = remoteViews.apply(context, host);
        ViewGroup.LayoutParams params;
        if (widthDp > 0f) {
            int widthPx = Math.round(TypedValue.applyDimension(
                    TypedValue.COMPLEX_UNIT_DIP, widthDp,
                    context.getResources().getDisplayMetrics()));
            params = new FrameLayout.LayoutParams(widthPx, ViewGroup.LayoutParams.WRAP_CONTENT);
        } else {
            params = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
        }
        view.setLayoutParams(params);
        host.addView(view);
    }

    /** Widget options matching a preview so the builder can size columns as the launcher would. */
    static Bundle options(float widthDp) {
        Bundle options = new Bundle();
        options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, Math.round(widthDp));
        return options;
    }

        /** Widget options matching a preview at a given width and height, in dp. */
        static Bundle options(float widthDp, float heightDp) {
            Bundle options = options(widthDp);
            options.putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, Math.round(heightDp));
            return options;
        }
}