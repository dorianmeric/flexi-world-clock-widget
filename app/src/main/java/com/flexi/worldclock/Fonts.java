package com.flexi.worldclock;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.TypefaceSpan;

import java.util.ArrayList;
import java.util.List;

/**
 * Catalogue of Android system font families that can be applied through a
 * {@link TypefaceSpan}. A family name is the only typeface description that survives the
 * RemoteViews parcel to the launcher process (a {@code TypefaceSpan(Typeface)} relies on
 * process-local storage), so custom fonts are expressed as system font families.
 */
public final class Fonts {

    /**
     * Invisible zero-width character appended (quoted) to a clock format so the pattern never
     * ends on the token covered by the span boundary; this keeps the {@link TypefaceSpan}
     * attached to every character the framework formats on each tick.
     */
    private static final char GUARD = '\u200b';

    /** family id, display label */
    private static final String[][] FAMILIES = {
            {"sans-serif", "Sans Serif (default)"},
            {"sans-serif-light", "Sans Serif Light"},
            {"sans-serif-thin", "Sans Serif Thin"},
            {"sans-serif-medium", "Sans Serif Medium"},
            {"sans-serif-black", "Sans Serif Black"},
            {"sans-serif-condensed", "Sans Serif Condensed"},
            {"sans-serif-condensed-light", "Sans Serif Condensed Light"},
            {"sans-serif-condensed-medium", "Sans Serif Condensed Medium"},
            {"sans-serif-smallcaps", "Sans Serif Small Caps"},
            {"serif", "Serif"},
            {"serif-monospace", "Serif Monospace"},
            {"monospace", "Monospace"},
            {"casual", "Casual"},
            {"cursive", "Cursive"},
    };

    public static final String DEFAULT_FAMILY = "sans-serif";

    private Fonts() {
    }

    public static List<String> displayNames() {
        List<String> names = new ArrayList<>(FAMILIES.length);
        for (String[] entry : FAMILIES) {
            names.add(entry[1]);
        }
        return names;
    }

    public static List<String> families() {
        List<String> ids = new ArrayList<>(FAMILIES.length);
        for (String[] entry : FAMILIES) {
            ids.add(entry[0]);
        }
        return ids;
    }

    /** Index of {@code family} in the catalogue, or 0 when unknown. */
    public static int indexOf(String family) {
        int index = families().indexOf(family);
        return index < 0 ? 0 : index;
    }

    public static String familyAt(int index) {
        List<String> families = families();
        if (index < 0 || index >= families.size()) {
            return DEFAULT_FAMILY;
        }
        return families.get(index);
    }

    /** Returns {@code text} with a family {@link TypefaceSpan} covering the whole string. */
    public static CharSequence spanned(CharSequence text, String family) {
        SpannableStringBuilder builder = new SpannableStringBuilder(text);
        if (family != null && !family.isEmpty()) {
            builder.setSpan(new TypefaceSpan(family), 0, builder.length(),
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }
        return builder;
    }

    /**
     * Builds a TextClock format string for {@code pattern} whose rendered output uses
     * {@code family}. The format is passed to the clock view through
     * {@code setFormat24Hour}/{@code setFormat12Hour}; the framework's {@code DateFormat}
     * re-formats the pattern on every tick and preserves the span, so the font survives updates.
     */
    public static CharSequence clockFormat(String pattern, String family) {
        SpannableStringBuilder builder = new SpannableStringBuilder(pattern);
        builder.append('\'').append(GUARD).append('\'');
        if (family != null && !family.isEmpty()) {
            builder.setSpan(new TypefaceSpan(family), 0, builder.length(),
                    Spanned.SPAN_INCLUSIVE_INCLUSIVE);
        }
        return builder;
    }
}