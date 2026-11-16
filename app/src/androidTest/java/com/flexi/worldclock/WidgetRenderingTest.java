package com.flexi.worldclock;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.os.Parcel;
import android.text.Spanned;
import android.text.style.TypefaceSpan;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

/** Instrumented checks for the parts that need real Android classes. */
@RunWith(AndroidJUnit4.class)
public class WidgetRenderingTest {

    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    @Test
    public void clockFormatKeepsSpanOverTheWholePattern() {
        CharSequence format = Fonts.clockFormat(ClockFormats.TIME, "serif");
        assertTrue(format instanceof Spanned);

        Spanned spanned = (Spanned) format;
        TypefaceSpan[] spans = spanned.getSpans(0, spanned.length(), TypefaceSpan.class);
        assertEquals(1, spans.length);
        assertEquals("serif", spans[0].getFamily());
        assertEquals(0, spanned.getSpanStart(spans[0]));
        assertEquals(spanned.length(), spanned.getSpanEnd(spans[0]));
        assertTrue(format.toString().length() > ClockFormats.TIME.length());
    }

    @Test
    public void customFormatSurvivesAsAFormattedSpannedString() {
            // Mirrors exactly what TextClock does on every tick.
            java.util.Calendar calendar = java.util.Calendar.getInstance();
            calendar.setTimeInMillis(1700177455000L);

        CharSequence format = Fonts.clockFormat(ClockFormats.TIME, "monospace");
            CharSequence rendered = android.text.format.DateFormat.format(format, calendar);

            assertTrue("DateFormat should return a Spanned: " + rendered,
                    rendered instanceof Spanned);
        Spanned spanned = (Spanned) rendered;
        TypefaceSpan[] spans = spanned.getSpans(0, spanned.length(), TypefaceSpan.class);
        assertEquals(1, spans.length);
        assertEquals("monospace", spans[0].getFamily());
            // The rendered text carries the font even though the format is re-parsed each tick.
            // The trailing zero-width guard is invisible, so the visible text is the plain time.
            assertTrue(rendered.toString(),
                    rendered.toString().matches("\\d{2}:\\d{2}-\\d{2}\u200b"));
    }

    @Test
    public void clockWidgetAppliesSettingsToItsViews() {
        WidgetPrefs.ClockSettings settings = WidgetPrefs.ClockSettings.defaults();
        settings.time.sizeSp = 40f;
        settings.date.sizeSp = 20f;
        settings.leftPaddingDp = 24f;

        FrameLayout parent = new FrameLayout(context());
        android.widget.RemoteViews views = ClockWidgetViews.build(context(), settings,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        View root = views.apply(context(), parent);

        android.widget.TextClock time = root.findViewById(R.id.clock_time);
        android.widget.TextClock date = root.findViewById(R.id.clock_date);
        assertEquals(40f, time.getTextSize() / density(), 0.6f);
        assertEquals(20f, date.getTextSize() / density(), 0.6f);
        assertTrue(date.getText().toString().matches(".+ \\d{1,2}-\\w{3}.*"));
        assertEquals(Math.round(24 * density()), root.findViewById(R.id.clock_content)
                .getPaddingLeft());
    }

        /**
         * The launcher receives the widget as a Parcel, so the custom font has to survive that
         * round-trip. This is the check that proves the span-based font approach actually works
         * across processes.
         */
        @Test
        public void customFontSurvivesTheRemoteViewsParcel() {
            WidgetPrefs.ClockSettings settings = WidgetPrefs.ClockSettings.defaults();
            settings.time.family = "serif";
            settings.time.sizeSp = 36f;

            Parcel parcel = Parcel.obtain();
            try {
                ClockWidgetViews.build(context(), settings, AppWidgetManager.INVALID_APPWIDGET_ID)
                        .writeToParcel(parcel, 0);
                parcel.setDataPosition(0);
                android.widget.RemoteViews restored = android.widget.RemoteViews.CREATOR
                        .createFromParcel(parcel);

                View root = restored.apply(context(), new FrameLayout(context()));
                android.widget.TextClock time = root.findViewById(R.id.clock_time);

                // The rendered clock text is what the user sees; the font must be on it.
                CharSequence rendered = time.getText();
                assertTrue("clock text should stay spanned: " + rendered,
                        rendered instanceof Spanned);
                Spanned spanned = (Spanned) rendered;
                TypefaceSpan[] spans = spanned.getSpans(0, spanned.length(), TypefaceSpan.class);
                assertTrue("expected a TypefaceSpan on " + rendered, spans.length >= 1);
                assertEquals("serif", spans[0].getFamily());
            } finally {
                parcel.recycle();
            }
        }

    @Test
    public void worldClockWidgetProducesOneColumnPerZone() {
        WidgetPrefs.WorldClockSettings settings = WidgetPrefs.WorldClockSettings.defaults();
        settings.zones.clear();
        settings.zones.add(new WidgetPrefs.ZoneEntry("Berlin", "Europe/Berlin"));
        settings.zones.add(new WidgetPrefs.ZoneEntry("Tokyo", "Asia/Tokyo"));
        settings.zones.add(new WidgetPrefs.ZoneEntry("New York", "America/New_York"));
        settings.showOffset = true;

        FrameLayout parent = new FrameLayout(context());
        android.widget.RemoteViews views = WorldClockWidgetViews.build(context(), settings,
                AppWidgetManager.INVALID_APPWIDGET_ID, PreviewRenderer.options(300f));
        View root = views.apply(context(), parent);

        android.view.ViewGroup row = root.findViewById(R.id.world_row);
        assertEquals(3, row.getChildCount());
        for (int i = 0; i < row.getChildCount(); i++) {
            View column = row.getChildAt(i);
            TextView city = column.findViewById(R.id.zone_city);
            TextView offset = column.findViewById(R.id.zone_offset);
            assertTrue(city.getText().length() > 0);
            assertTrue(offset.getText().toString().startsWith("UTC"));
        }
    }

        /** The columns plus the gaps between them must span the whole widget width. */
        @Test
        public void columnWidthsFillTheWidgetWidth() {
            WidgetPrefs.WorldClockSettings settings = WidgetPrefs.WorldClockSettings.defaults();
            settings.zones.clear();
            settings.spacingDp = 10f;
            for (String id : new String[]{"Europe/London", "America/New_York", "Asia/Tokyo",
                    "Australia/Sydney"}) {
                settings.zones.add(new WidgetPrefs.ZoneEntry(ZoneCatalog.labelFor(id), id));
            }

            FrameLayout host = new FrameLayout(context());
            int widthPx = Math.round(320 * density());
            android.widget.RemoteViews views = WorldClockWidgetViews.build(context(), settings,
                    AppWidgetManager.INVALID_APPWIDGET_ID, PreviewRenderer.options(320f));
            View root = views.apply(context(), host);

            int widthSpec = View.MeasureSpec.makeMeasureSpec(widthPx, View.MeasureSpec.EXACTLY);
            int heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
            // RemoteViews.apply() inflates against the host but does not attach the result, so the
            // returned root has to be measured and laid out explicitly.
            root.measure(widthSpec, heightSpec);
            root.layout(0, 0, widthPx, root.getMeasuredHeight());

            android.view.ViewGroup row = root.findViewById(R.id.world_row);
            int columnsWidth = 0;
            int gapsWidth = 0;
            for (int i = 0; i < row.getChildCount(); i++) {
                View column = row.getChildAt(i);
                columnsWidth += column.getWidth();
                android.view.ViewGroup.MarginLayoutParams lp =
                        (android.view.ViewGroup.MarginLayoutParams) column.getLayoutParams();
                gapsWidth += lp.leftMargin + lp.rightMargin;
            }
            int padded = row.getPaddingLeft() + row.getPaddingRight();

            assertEquals("columns + gaps + padding should fill the widget",
                    widthPx, columnsWidth + gapsWidth + padded, 2);
        }

    /**
         * The custom font must actually change how the clock is rendered, not just ride along in the
         * format string. A monospace face has distinctly wider digits than the default sans face, so
         * measuring the laid-out text width proves the span reaches the rendering stage.
         */
    @Test
        public void customFontChangesTheRenderedTextWidth() {
        WidgetPrefs.ClockSettings settings = WidgetPrefs.ClockSettings.defaults();
            settings.time.sizeSp = 30f;

            float sansWidth = measureTimeWidth(settings, "sans-serif");
            float monoWidth = measureTimeWidth(settings, "monospace");

            assertTrue("monospace should lay out wider than sans-serif: mono=" + monoWidth
                    + " sans=" + sansWidth, monoWidth > sansWidth);
        }

        private float measureTimeWidth(WidgetPrefs.ClockSettings settings, String family) {
            settings.time.family = family;
        View root = ClockWidgetViews.build(context(), settings,
                AppWidgetManager.INVALID_APPWIDGET_ID).apply(context(), new FrameLayout(context()));
        android.widget.TextClock time = root.findViewById(R.id.clock_time);
            root.measure(
                    View.MeasureSpec.makeMeasureSpec(Math.round(600 * density()),
                            View.MeasureSpec.AT_MOST),
                    View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
            root.layout(0, 0, root.getMeasuredWidth(), root.getMeasuredHeight());
            return time.getLayout().getLineWidth(0);
    }

    /** The world clock's optional "UTC+8" label is shown only when enabled. */
    @Test
    public void utcOffsetLabelFollowsTheSetting() {
        WidgetPrefs.WorldClockSettings settings = WidgetPrefs.WorldClockSettings.defaults();
        settings.zones.clear();
        settings.zones.add(new WidgetPrefs.ZoneEntry("Berlin", "Europe/Berlin"));

        settings.showOffset = false;
        View hidden = WorldClockWidgetViews.build(context(), settings,
                        AppWidgetManager.INVALID_APPWIDGET_ID, PreviewRenderer.options(300f))
                .apply(context(), new FrameLayout(context()));
        assertEquals(View.GONE, hidden.findViewById(R.id.zone_offset).getVisibility());

        settings.showOffset = true;
        View shown = WorldClockWidgetViews.build(context(), settings,
                        AppWidgetManager.INVALID_APPWIDGET_ID, PreviewRenderer.options(300f))
                .apply(context(), new FrameLayout(context()));
        View offset = shown.findViewById(R.id.zone_offset);
        assertEquals(View.VISIBLE, offset.getVisibility());
        assertTrue(((TextView) offset).getText().toString().startsWith("UTC"));
    }

    /** The 2x1 clock keeps its left padding configurable. */
    @Test
    public void leftPaddingIsAppliedToTheClockContent() {
        WidgetPrefs.ClockSettings settings = WidgetPrefs.ClockSettings.defaults();
        settings.leftPaddingDp = 40f;

        View root = ClockWidgetViews.build(context(), settings,
                AppWidgetManager.INVALID_APPWIDGET_ID).apply(context(), new FrameLayout(context()));

        assertEquals(Math.round(40 * density()),
                root.findViewById(R.id.clock_content).getPaddingLeft());
    }

    private float density() {
        return context().getResources().getDisplayMetrics().density;
    }
}