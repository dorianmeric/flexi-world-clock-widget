package com.flexi.worldclock;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.widget.RemoteViews;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Configurable widget background: a rounded rectangle filled with a chosen colour at a chosen
 * opacity, or nothing at all.
 *
 * <p>The background is drawn into a bitmap and pushed through
 * {@code ImageView.setImageViewBitmap}, which is a {@code @RemotableViewMethod}. A static shape
 * drawable cannot be used because its colour cannot be changed from {@link RemoteViews}.
 */
final class WidgetBackground {

    /** Corner radius, matching the drawable the launcher shows before the first update. */
    static final float RADIUS_DP = 14f;

    /** Bound on the generated bitmap so a huge widget cannot allocate an unreasonable buffer. */
    private static final int MAX_DIMENSION_PX = 1600;

    /** Swatches offered in the settings screens, as opaque RGB values. */
    static final int[] PRESET_COLORS = {
            0xFF12161C, // graphite (default)
            0xFF1B2A41, // navy
            0xFF14342B, // forest
            0xFF3A1E3D, // plum
            0xFF432818, // espresso
            0xFF0E3B43, // teal
            0xFF3C3A38, // stone
            0xFF5A5F6A, // slate
            0xFFF2F4F7, // paper
            0xFF000000, // black
    };

    private static final int DEFAULT_COLOR = 0xFF12161C;
    private static final int DEFAULT_OPACITY = 80;
    private static final int MIN_OPACITY = 5;
    private static final int MAX_OPACITY = 100;

    private WidgetBackground() {
    }

    /** Persisted background choice of one widget instance. */
    static final class Settings {
        public boolean enabled = true;
        public int color = DEFAULT_COLOR;
        public int opacity = DEFAULT_OPACITY;

        static Settings defaults() {
            return new Settings();
        }

        /**
         * Fully transparent background. Used when the user picks "no background" so that the
         * launcher's wallpaper shows through unchanged.
         */
        static Settings none() {
            Settings settings = defaults();
            settings.enabled = false;
            return settings;
        }

        void clamp() {
            // Only the RGB channels are configurable; opacity carries the alpha channel.
            color = 0xFF000000 | (color & 0x00FFFFFF);
            opacity = Math.max(MIN_OPACITY, Math.min(MAX_OPACITY, opacity));
        }

        /** Colour including the configured opacity, ready to fill a canvas with. */
        int resolvedColor() {
            if (!enabled) {
                return Color.TRANSPARENT;
            }
            int alpha = Math.round(opacity * 255f / 100f);
            return (alpha << 24) | (color & 0x00FFFFFF);
        }

        JSONObject toJson() {
            JSONObject json = new JSONObject();
            try {
                json.put("on", enabled);
                json.put("color", String.format("#%06X", color & 0x00FFFFFF));
                json.put("opacity", opacity);
            } catch (JSONException ignored) {
                // Primitives only, encoding cannot fail.
            }
            return json;
        }

        static Settings fromJson(JSONObject json) {
            Settings settings = defaults();
            if (json == null) {
                return settings;
            }
            settings.enabled = json.optBoolean("on", true);
            settings.color = parseColor(json.optString("color", ""), DEFAULT_COLOR);
            settings.opacity = (int) WidgetPrefs.clamp(
                    json.optInt("opacity", DEFAULT_OPACITY), MIN_OPACITY, MAX_OPACITY);
            settings.clamp();
            return settings;
        }

        /**
                 * Parses {@code #RRGGBB} or {@code #AARRGGBB}. Implemented without
                 * {@code android.graphics.Color} so the behaviour is identical on the JVM tests, where the
                 * framework classes are stubs.
                 */
                private static int parseColor(String value, int fallback) {
                    if (value == null) {
                return fallback;
            }
                    String hex = value.trim();
                    if (hex.startsWith("#")) {
                        hex = hex.substring(1);
                    }
                    if (hex.length() != 6 && hex.length() != 8) {
                        return fallback;
                    }
            try {
                        long parsed = Long.parseLong(hex, 16);
                        return hex.length() == 6 ? (int) (0xFF000000L | parsed) : (int) parsed;
                    } catch (NumberFormatException e) {
                return fallback;
            }
        }
    }

    /** Applies the background to {@code root} of a widget hierarchy. */
    static void apply(RemoteViews views, int backgroundViewId, Settings settings,
            int widthPx, int heightPx, float density) {
        if (!settings.enabled) {
            views.setViewVisibility(backgroundViewId, android.view.View.GONE);
            return;
        }
        Bitmap bitmap = render(settings, widthPx, heightPx, density);
        if (bitmap == null) {
            views.setViewVisibility(backgroundViewId, android.view.View.GONE);
            return;
        }
        views.setViewVisibility(backgroundViewId, android.view.View.VISIBLE);
        views.setImageViewBitmap(backgroundViewId, bitmap);
    }

    /** Renders the rounded rectangle, or {@code null} when the widget has no usable size. */
    static Bitmap render(Settings settings, int widthPx, int heightPx, float density) {
        int width = Math.max(1, Math.min(MAX_DIMENSION_PX, widthPx));
        int height = Math.max(1, Math.min(MAX_DIMENSION_PX, heightPx));
        if (widthPx <= 0 || heightPx <= 0) {
            return null;
        }
        // A radius that never exceeds half the shortest side keeps the shape sane on tiny sizes.
        float radius = Math.min(RADIUS_DP * density, Math.min(width, height) / 2f);

        Bitmap bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(settings.resolvedColor());
        paint.setStyle(Paint.Style.FILL);
        canvas.drawRoundRect(new RectF(0, 0, width, height), radius, radius, paint);
        return bitmap;
    }
}