package com.flexi.worldclock

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Parcel
import android.text.Spanned
import android.text.format.DateFormat
import android.text.style.TypefaceSpan
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.RemoteViews
import android.widget.TextClock
import android.widget.TextView
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.xmlpull.v1.XmlPullParser
import java.util.Calendar
import kotlin.math.roundToInt

/** Instrumented checks for the parts that need real Android classes. */
@RunWith(AndroidJUnit4::class)
class WidgetRenderingTest {

    @Test
    fun clockFormatKeepsSpanOverTheWholePattern() {
        val format = Fonts.clockFormat(ClockFormats.TIME, "serif")
        assertTrue(format is Spanned)

        val spanned = format as Spanned
        val spans = spanned.getSpans(0, spanned.length, TypefaceSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals("serif", spans[0].family)
        assertEquals(0, spanned.getSpanStart(spans[0]))
        assertEquals(spanned.length, spanned.getSpanEnd(spans[0]))
        assertTrue(format.toString().length > ClockFormats.TIME.length)
    }

    @Test
    fun customFormatSurvivesAsAFormattedSpannedString() {
        // Mirrors exactly what TextClock does on every tick.
        val calendar = Calendar.getInstance()
        calendar.timeInMillis = 1700177455000L

        val format = Fonts.clockFormat(ClockFormats.TIME, "monospace")
        val rendered = DateFormat.format(format, calendar)

        assertTrue("DateFormat should return a Spanned: $rendered", rendered is Spanned)
        val spanned = rendered as Spanned
        val spans = spanned.getSpans(0, spanned.length, TypefaceSpan::class.java)
        assertEquals(1, spans.size)
        assertEquals("monospace", spans[0].family)
        // The rendered text carries the font even though the format is re-parsed each tick.
        // The trailing zero-width guard is invisible, so the visible text is the plain time.
        assertTrue(
            rendered.toString(),
            rendered.toString().matches("\\d{2}:\\d{2}-\\d{2}\u200b".toRegex()),
        )
    }

    @Test
    fun clockWidgetAppliesSettingsToItsViews() {
        val settings = WidgetPrefs.ClockSettings.defaults()
        settings.time.sizeSp = 40f
        settings.date.sizeSp = 20f
        settings.leftPaddingDp = 24f

        val views = ClockWidgetViews.build(context(), settings, AppWidgetManager.INVALID_APPWIDGET_ID)
        val root = views.apply(context(), FrameLayout(context()))

        val time = root.findViewById<TextClock>(R.id.clock_time)
        val date = root.findViewById<TextClock>(R.id.clock_date)
        assertEquals(40f, time.textSize / density(), 0.6f)
        assertEquals(20f, date.textSize / density(), 0.6f)
        assertTrue(date.text.toString().matches(".+ \\d{1,2}-\\w{3}.*".toRegex()))
        assertEquals(
            (24 * density()).roundToInt(),
            root.findViewById<View>(R.id.clock_content).paddingLeft,
        )
    }

    /**
     * The launcher receives the widget as a Parcel, so the custom font has to survive that
     * round-trip. This is the check that proves the span-based font approach actually works across
     * processes.
     */
    @Test
    fun customFontSurvivesTheRemoteViewsParcel() {
        val settings = WidgetPrefs.ClockSettings.defaults()
        settings.time.family = "serif"
        settings.time.sizeSp = 36f

        val parcel = Parcel.obtain()
        try {
            ClockWidgetViews.build(context(), settings, AppWidgetManager.INVALID_APPWIDGET_ID)
                .writeToParcel(parcel, 0)
            parcel.setDataPosition(0)
            val restored = RemoteViews.CREATOR.createFromParcel(parcel)

            val root = restored.apply(context(), FrameLayout(context()))
            val time = root.findViewById<TextClock>(R.id.clock_time)

            // The rendered clock text is what the user sees; the font must be on it.
            val rendered = time.text
            assertTrue("clock text should stay spanned: $rendered", rendered is Spanned)
            val spanned = rendered as Spanned
            val spans = spanned.getSpans(0, spanned.length, TypefaceSpan::class.java)
            assertTrue("expected a TypefaceSpan on $rendered", spans.size >= 1)
            assertEquals("serif", spans[0].family)
        } finally {
            parcel.recycle()
        }
    }

    @Test
    fun worldClockWidgetProducesOneColumnPerZone() {
        val settings = WidgetPrefs.WorldClockSettings.defaults()
        settings.zones.clear()
        settings.zones.add(WidgetPrefs.ZoneEntry("Berlin", "Europe/Berlin"))
        settings.zones.add(WidgetPrefs.ZoneEntry("Tokyo", "Asia/Tokyo"))
        settings.zones.add(WidgetPrefs.ZoneEntry("New York", "America/New_York"))
        settings.showOffset = true

        val views = WorldClockWidgetViews.build(
            context(),
            settings,
            AppWidgetManager.INVALID_APPWIDGET_ID,
            PreviewRenderer.options(300f),
        )
        val root = views.apply(context(), FrameLayout(context()))

        val row = root.findViewById<ViewGroup>(R.id.world_row)
        assertEquals(3, row.childCount)
        for (i in 0 until row.childCount) {
            val column = row.getChildAt(i)
            val city = column.findViewById<TextView>(R.id.zone_city)
            val offset = column.findViewById<TextView>(R.id.zone_offset)
            assertTrue(city.text.length > 0)
            assertTrue(offset.text.toString().startsWith("UTC"))
        }
    }

    /** The columns plus the gaps between them must span the whole widget width. */
    @Test
    fun columnWidthsFillTheWidgetWidth() {
        val settings = WidgetPrefs.WorldClockSettings.defaults()
        settings.zones.clear()
        settings.spacingDp = 10f
        for (id in listOf("Europe/London", "America/New_York", "Asia/Tokyo", "Australia/Sydney")) {
            settings.zones.add(WidgetPrefs.ZoneEntry(ZoneCatalog.labelFor(id), id))
        }

        val host = FrameLayout(context())
        val widthPx = (320 * density()).roundToInt()
        val views = WorldClockWidgetViews.build(
            context(),
            settings,
            AppWidgetManager.INVALID_APPWIDGET_ID,
            PreviewRenderer.options(320f),
        )
        val root = views.apply(context(), host)

        val widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY)
        val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        // RemoteViews.apply() inflates against the host but does not attach the result, so the
        // returned root has to be measured and laid out explicitly.
        root.measure(widthSpec, heightSpec)
        root.layout(0, 0, widthPx, root.measuredHeight)

        val row = root.findViewById<ViewGroup>(R.id.world_row)
        var columnsWidth = 0
        var gapsWidth = 0
        for (i in 0 until row.childCount) {
            val column = row.getChildAt(i)
            columnsWidth += column.width
            val params = column.layoutParams as ViewGroup.MarginLayoutParams
            gapsWidth += params.leftMargin + params.rightMargin
        }
        val padded = row.paddingLeft + row.paddingRight

        assertEquals(
            "columns + gaps + padding should fill the widget",
            widthPx.toDouble(),
            (columnsWidth + gapsWidth + padded).toDouble(),
            2.0,
        )
    }

    /**
     * The custom font must actually change how the clock is rendered, not just ride along in the
     * format string. A monospace face has distinctly wider digits than the default sans face, so
     * measuring the laid-out text width proves the span reaches the rendering stage.
     */
    @Test
    fun customFontChangesTheRenderedTextWidth() {
        val settings = WidgetPrefs.ClockSettings.defaults()
        settings.time.sizeSp = 30f

        val sansWidth = measureTimeWidth(settings, "sans-serif")
        val monoWidth = measureTimeWidth(settings, "monospace")

        assertTrue(
            "monospace should lay out wider than sans-serif: mono=$monoWidth sans=$sansWidth",
            monoWidth > sansWidth,
        )
    }

    private fun measureTimeWidth(settings: WidgetPrefs.ClockSettings, family: String): Float {
        settings.time.family = family
        val root = ClockWidgetViews.build(context(), settings, AppWidgetManager.INVALID_APPWIDGET_ID)
            .apply(context(), FrameLayout(context()))
        val time = root.findViewById<TextClock>(R.id.clock_time)
        root.measure(
            View.MeasureSpec.makeMeasureSpec(
                (600 * density()).roundToInt(),
                View.MeasureSpec.AT_MOST,
            ),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        root.layout(0, 0, root.measuredWidth, root.measuredHeight)
        return time.layout.getLineWidth(0)
    }

    /** The world clock's optional "UTC+8" label is shown only when enabled. */
    @Test
    fun utcOffsetLabelFollowsTheSetting() {
        val settings = WidgetPrefs.WorldClockSettings.defaults()
        settings.zones.clear()
        settings.zones.add(WidgetPrefs.ZoneEntry("Berlin", "Europe/Berlin"))

        settings.showOffset = false
        val hidden = WorldClockWidgetViews.build(
            context(),
            settings,
            AppWidgetManager.INVALID_APPWIDGET_ID,
            PreviewRenderer.options(300f),
        ).apply(context(), FrameLayout(context()))
        assertEquals(View.GONE, hidden.findViewById<View>(R.id.zone_offset).visibility)

        settings.showOffset = true
        val shown = WorldClockWidgetViews.build(
            context(),
            settings,
            AppWidgetManager.INVALID_APPWIDGET_ID,
            PreviewRenderer.options(300f),
        ).apply(context(), FrameLayout(context()))
        val offset = shown.findViewById<View>(R.id.zone_offset)
        assertEquals(View.VISIBLE, offset.visibility)
        assertTrue((offset as TextView).text.toString().startsWith("UTC"))
    }

    /** The 2x1 clock keeps its left padding configurable. */
    @Test
    fun leftPaddingIsAppliedToTheClockContent() {
        val settings = WidgetPrefs.ClockSettings.defaults()
        settings.leftPaddingDp = 40f

        val root = ClockWidgetViews.build(context(), settings, AppWidgetManager.INVALID_APPWIDGET_ID)
            .apply(context(), FrameLayout(context()))

        assertEquals(
            (40 * density()).roundToInt(),
            root.findViewById<View>(R.id.clock_content).paddingLeft,
        )
    }

    /** The generated background honours colour, opacity and the "no background" choice. */
    @Test
    fun generatedBackgroundHonoursColourAndOpacity() {
        val settings = WidgetBackground.Settings()
        settings.color = 0xFFFF0000.toInt()
        settings.opacity = 50

        val bitmap = WidgetBackground.render(settings, 40, 40, 1f)
        assertNotNull(bitmap)
        // Centre pixel is the fill colour with the configured alpha.
        val pixel = bitmap!!.getPixel(20, 20)
        assertEquals(0xFF0000, pixel and 0x00FFFFFF)
        assertEquals(128, (pixel ushr 24) and 0xFF)
        // A corner is transparent thanks to the rounded corners.
        assertEquals(0, (bitmap.getPixel(0, 0) ushr 24) and 0xFF)
    }

    @Test
    fun disabledBackgroundIsHiddenInTheWidget() {
        val settings = WidgetPrefs.ClockSettings.defaults()
        settings.background = WidgetBackground.Settings.none()

        val root = ClockWidgetViews.build(
            context(),
            settings,
            AppWidgetManager.INVALID_APPWIDGET_ID,
            PreviewRenderer.options(148f, 56f),
        ).apply(context(), FrameLayout(context()))

        assertEquals(View.GONE, root.findViewById<View>(R.id.widget_background).visibility)
    }

    @Test
    fun enabledBackgroundIsVisibleAndOpaque() {
        val settings = WidgetPrefs.WorldClockSettings.defaults()
        settings.background.color = 0xFF1B2A41.toInt()
        settings.background.opacity = 90

        val root = WorldClockWidgetViews.build(
            context(),
            settings,
            AppWidgetManager.INVALID_APPWIDGET_ID,
            PreviewRenderer.options(300f, 56f),
        ).apply(context(), FrameLayout(context()))

        val background = root.findViewById<View>(R.id.widget_background)
        assertEquals(View.VISIBLE, background.visibility)
        assertNotNull((background as ImageView).drawable)
    }

    /** Dragging a zone row onto another must reorder the model the widget renders from. */
    @Test
    fun draggedZoneRowReordersTheModel() {
        val settings = WidgetPrefs.WorldClockSettings.defaults()
        settings.zones.clear()
        settings.zones.add(WidgetPrefs.ZoneEntry("London", "Europe/London"))
        settings.zones.add(WidgetPrefs.ZoneEntry("Dhaka", "Asia/Dhaka"))
        settings.zones.add(WidgetPrefs.ZoneEntry("Vientiane", "Asia/Vientiane"))

        val root = WorldClockWidgetViews.build(
            context(),
            settings,
            AppWidgetManager.INVALID_APPWIDGET_ID,
            PreviewRenderer.options(300f, 56f),
        ).apply(context(), FrameLayout(context()))
        val row = root.findViewById<ViewGroup>(R.id.world_row)
        assertEquals(3, row.childCount)

        // Move Vientiane (index 2) to the front, as a drag onto the first row would.
        DragReorder.reorder(settings.zones, 2, 0)
        assertEquals("Vientiane", settings.zones[0].label)

        val reordered = WorldClockWidgetViews.build(
            context(),
            settings,
            AppWidgetManager.INVALID_APPWIDGET_ID,
            PreviewRenderer.options(300f, 56f),
        ).apply(context(), FrameLayout(context()))
        val reorderedRow = reordered.findViewById<ViewGroup>(R.id.world_row)
        val firstCity = reorderedRow.getChildAt(0).findViewById<TextView>(R.id.zone_city)
        assertEquals("Vientiane", firstCity.text.toString())
    }

    /**
     * The dotted outline keys on the row's activated flag, so only the row being dragged may carry
     * it — and it has to come off again when the drag ends.
     */
    @Test
    fun onlyTheDraggedRowIsHighlighted() {
        val container = LinearLayout(context())
        repeat(3) { container.addView(View(context())) }

        DragReorder.highlight(container, 1)
        assertFalse(container.getChildAt(0).isActivated)
        assertTrue(container.getChildAt(1).isActivated)
        assertFalse(container.getChildAt(2).isActivated)

        DragReorder.highlight(container, -1)
        for (i in 0 until container.childCount) {
            assertFalse(container.getChildAt(i).isActivated)
        }
    }

    /**
     * The activated flag has to reach a visibly dotted outline, or the drag cue never shows. The
     * row's background is rendered to a bitmap and its top edge walked: some accent pixels prove the
     * outline is drawn, and their number being short of the width proves it is dashed, not solid.
     */
    @Test
    fun activatedRowDrawsADottedOutline() {
        val row = LayoutInflater.from(context())
            .inflate(R.layout.item_zone_row, LinearLayout(context()), false)
        val background = row.background
        assertTrue("row background should be a state list", background is StateListDrawable)

        row.isActivated = false
        val idle = background.current
        row.isActivated = true
        val active = background.current
        assertTrue("activated state should pick a different drawable", active !== idle)

        val accent = context().getColor(R.color.accent)
        val size = 160
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        (active as GradientDrawable).setBounds(0, 0, size, size)
        active.draw(Canvas(bitmap))

        // The 2dp stroke straddles the edge, so the first pixel row inside it carries the outline.
        var accentPixels = 0
        for (x in 0 until size) {
            val pixel = bitmap.getPixel(x, 1)
            val nearAccent = channel(pixel, 16, accent) &&
                channel(pixel, 8, accent) &&
                channel(pixel, 0, accent) &&
                (pixel ushr 24) > 120
            if (nearAccent) accentPixels++
        }
        assertTrue("expected an accent outline, found $accentPixels", accentPixels > 0)
        assertTrue("outline should be dotted, not solid: $accentPixels of $size", accentPixels < size)
    }

    /** Whether [pixel]'s channel at [shift] is close enough to [reference] to count as the same colour. */
    private fun channel(pixel: Int, shift: Int, reference: Int): Boolean =
        kotlin.math.abs(((pixel shr shift) and 0xFF) - ((reference shr shift) and 0xFF)) < 48

    /**
     * The long-press menu offers "Edit widget" only when the provider declares a configure activity
     * plus the reconfigurable feature, so both are asserted from the provider XML.
     */
    @Test
    fun providersDeclareThemselvesReconfigurable() {
        assertProviderXml(R.xml.clock_widget_info, ".ClockConfigActivity")
        assertProviderXml(R.xml.world_clock_widget_info, ".WorldClockConfigActivity")
    }

    /**
     * End-to-end check of the same declarations through the framework: the parsed
     * [AppWidgetProviderInfo] the launcher reads must expose the configure component and the
     * reconfigurable flag.
     */
    @Test
    fun parsedProviderInfoExposesTheConfigureComponent() {
        val manager = AppWidgetManager.getInstance(context())
        var checked = 0
        for (info in manager.installedProviders) {
            if (info.provider.packageName != context().packageName) {
                continue
            }
            // The launcher shows "Edit" when configure is set and reconfiguration is allowed.
            assertNotNull("configure missing for ${info.provider}", info.configure)
            assertTrue(
                "reconfigurable flag missing for ${info.provider}",
                (info.widgetFeatures and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE) != 0,
            )
            // The configure activity must actually resolve.
            assertNotNull(
                "configure activity unresolvable for ${info.provider}",
                context().packageManager.resolveActivity(Intent().setComponent(info.configure), 0),
            )
            checked++
        }
        assertEquals("expected both providers to be installed", 2, checked)
    }

    private fun assertProviderXml(xmlRes: Int, expectedConfigureSuffix: String) {
        val parser = context().resources.getXml(xmlRes)
        var configure: String? = null
        var features: String? = null
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (event == XmlPullParser.START_TAG) {
                configure = parser.getAttributeValue(ANDROID_NS, "configure")
                features = parser.getAttributeValue(ANDROID_NS, "widgetFeatures")
            }
            event = parser.next()
        }

        assertNotNull("configure attribute missing in $xmlRes", configure)
        assertTrue(
            "configure should point at $expectedConfigureSuffix but was $configure",
            configure!!.endsWith(expectedConfigureSuffix),
        )
        assertNotNull("widgetFeatures missing in $xmlRes", features)
        // Resources are compiled, so the flag list resolves to its integer value (0x1).
        val flags = parseFlags(features)
        assertTrue(
            "WIDGET_FEATURE_RECONFIGURABLE missing in $xmlRes: $features",
            (flags and AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE.toLong()) != 0L,
        )
    }

    /** Reads a compiled flag attribute, accepting both the raw and the symbolic form. */
    private fun parseFlags(value: String?): Long {
        val text = value?.trim() ?: return 0
        if (text.startsWith("0x") || text.startsWith("0X")) {
            return text.substring(2).toLong(16)
        }
        return text.toLongOrNull() ?: run {
            // Symbolic form, e.g. "reconfigurable".
            var flags = 0L
            for (part in text.split("|")) {
                if (part.trim() == "reconfigurable") {
                    flags = flags or
                        AppWidgetProviderInfo.WIDGET_FEATURE_RECONFIGURABLE.toLong()
                }
            }
            flags
        }
    }

    private fun context(): Context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun density(): Float = context().resources.displayMetrics.density

    private companion object {
        const val ANDROID_NS = "http://schemas.android.com/apk/res/android"
    }
}
