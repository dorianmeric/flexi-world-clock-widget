package com.flexi.worldclock

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.View
import android.widget.RemoteViews
import org.json.JSONException
import org.json.JSONObject
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Configurable widget background: a rounded rectangle filled with a chosen colour at a chosen
 * opacity, or nothing at all.
 *
 * The background is drawn into a bitmap and pushed through `ImageView.setImageViewBitmap`, which is
 * a `@RemotableViewMethod`. A static shape drawable cannot be used because its colour cannot be
 * changed from [RemoteViews].
 */
internal object WidgetBackground {

    /** Corner radius, matching the drawable the launcher shows before the first update. */
    const val RADIUS_DP = 14f

    /** Bound on the generated bitmap so a huge widget cannot allocate an unreasonable buffer. */
    private const val MAX_DIMENSION_PX = 1600

    private const val DEFAULT_COLOR = 0xFF12161C.toInt()
    private const val DEFAULT_OPACITY = 80
    private const val MIN_OPACITY = 5
    private const val MAX_OPACITY = 100

    /** Swatches offered in the settings screens, as opaque RGB values. */
    val PRESET_COLORS = intArrayOf(
        0xFF12161C.toInt(), // graphite (default)
        0xFF1B2A41.toInt(), // navy
        0xFF14342B.toInt(), // forest
        0xFF3A1E3D.toInt(), // plum
        0xFF432818.toInt(), // espresso
        0xFF0E3B43.toInt(), // teal
        0xFF3C3A38.toInt(), // stone
        0xFF5A5F6A.toInt(), // slate
        0xFFF2F4F7.toInt(), // paper
        0xFF000000.toInt(), // black
    )

    /** Persisted background choice of one widget instance. */
    class Settings {
        var enabled = true
        var color = DEFAULT_COLOR
        var opacity = DEFAULT_OPACITY

        fun clamp() {
            // Only the RGB channels are configurable; opacity carries the alpha channel.
            color = 0xFF000000.toInt() or (color and 0x00FFFFFF)
            opacity = opacity.coerceIn(MIN_OPACITY, MAX_OPACITY)
        }

        /** Colour including the configured opacity, ready to fill a canvas with. */
        fun resolvedColor(): Int {
            if (!enabled) {
                return Color.TRANSPARENT
            }
            val alpha = (opacity * 255f / 100f).roundToInt()
            return (alpha shl 24) or (color and 0x00FFFFFF)
        }

        fun toJson(): JSONObject {
            val json = JSONObject()
            try {
                json.put("on", enabled)
                json.put("color", "#%06X".format(color and 0x00FFFFFF))
                json.put("opacity", opacity)
            } catch (ignored: JSONException) {
                // Primitives only, encoding cannot fail.
            }
            return json
        }

        companion object {
            fun defaults(): Settings = Settings()

            /**
             * Fully transparent background. Used when the user picks "no background" so that the
             * launcher's wallpaper shows through unchanged.
             */
            fun none(): Settings = defaults().apply { enabled = false }

            fun fromJson(json: JSONObject?): Settings = defaults().apply {
                if (json == null) {
                    return@apply
                }
                enabled = json.optBoolean("on", true)
                color = parseColor(json.optString("color", ""), DEFAULT_COLOR)
                opacity = WidgetPrefs.clamp(
                    json.optInt("opacity", DEFAULT_OPACITY),
                    MIN_OPACITY,
                    MAX_OPACITY,
                )
                clamp()
            }

            /**
             * Parses `#RRGGBB` or `#AARRGGBB`. Implemented without `android.graphics.Color` so the
             * behaviour is identical on the JVM tests, where the framework classes are stubs.
             */
            private fun parseColor(value: String?, fallback: Int): Int {
                val text = value?.trim().orEmpty()
                val hex = text.removePrefix("#")
                if (hex.length != 6 && hex.length != 8) {
                    return fallback
                }
                val parsed = hex.toLongOrNull(radix = 16) ?: return fallback
                return if (hex.length == 6) {
                    (0xFF000000L or parsed).toInt()
                } else {
                    parsed.toInt()
                }
            }
        }
    }

    /** Applies the background to [backgroundViewId] of a widget hierarchy. */
    fun apply(
        views: RemoteViews,
        backgroundViewId: Int,
        settings: Settings,
        widthPx: Int,
        heightPx: Int,
        density: Float,
    ) {
        val bitmap = if (settings.enabled) render(settings, widthPx, heightPx, density) else null
        if (bitmap == null) {
            views.setViewVisibility(backgroundViewId, View.GONE)
            return
        }
        views.setViewVisibility(backgroundViewId, View.VISIBLE)
        views.setImageViewBitmap(backgroundViewId, bitmap)
    }

    /** Renders the rounded rectangle, or `null` when the widget has no usable size. */
    fun render(settings: Settings, widthPx: Int, heightPx: Int, density: Float): Bitmap? {
        if (widthPx <= 0 || heightPx <= 0) {
            return null
        }
        val width = widthPx.coerceIn(1, MAX_DIMENSION_PX)
        val height = heightPx.coerceIn(1, MAX_DIMENSION_PX)
        // A radius that never exceeds half the shortest side keeps the shape sane on tiny sizes.
        val radius = min(RADIUS_DP * density, min(width, height) / 2f)

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = settings.resolvedColor()
            style = Paint.Style.FILL
        }
        Canvas(bitmap).drawRoundRect(
            RectF(0f, 0f, width.toFloat(), height.toFloat()),
            radius,
            radius,
            paint,
        )
        return bitmap
    }
}
