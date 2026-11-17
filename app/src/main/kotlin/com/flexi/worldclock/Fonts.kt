package com.flexi.worldclock

import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.TypefaceSpan

/**
 * Catalogue of Android system font families that can be applied through a [TypefaceSpan]. A family
 * name is the only typeface description that survives the RemoteViews parcel to the launcher
 * process (a `TypefaceSpan(Typeface)` relies on process-local storage), so custom fonts are
 * expressed as system font families.
 */
internal object Fonts {

    /**
     * Invisible zero-width character appended (quoted) to a clock format so the pattern never ends
     * on the token covered by the span boundary; this keeps the [TypefaceSpan] attached to every
     * character the framework formats on each tick.
     */
    private const val GUARD = '\u200b'

    /** family id to display label. */
    private val FAMILIES: List<Pair<String, String>> = listOf(
        "sans-serif" to "Sans Serif (default)",
        "sans-serif-light" to "Sans Serif Light",
        "sans-serif-thin" to "Sans Serif Thin",
        "sans-serif-medium" to "Sans Serif Medium",
        "sans-serif-black" to "Sans Serif Black",
        "sans-serif-condensed" to "Sans Serif Condensed",
        "sans-serif-condensed-light" to "Sans Serif Condensed Light",
        "sans-serif-condensed-medium" to "Sans Serif Condensed Medium",
        "sans-serif-smallcaps" to "Sans Serif Small Caps",
        "serif" to "Serif",
        "serif-monospace" to "Serif Monospace",
        "monospace" to "Monospace",
        "casual" to "Casual",
        "cursive" to "Cursive",
    )

    private val DISPLAY_NAMES: List<String> = FAMILIES.map { it.second }
    private val FAMILY_IDS: List<String> = FAMILIES.map { it.first }

    const val DEFAULT_FAMILY = "sans-serif"

    fun displayNames(): List<String> = DISPLAY_NAMES

    fun families(): List<String> = FAMILY_IDS

    /** Index of [family] in the catalogue, or 0 when unknown. */
    fun indexOf(family: String): Int = FAMILY_IDS.indexOf(family).coerceAtLeast(0)

    fun familyAt(index: Int): String = FAMILY_IDS.getOrElse(index) { DEFAULT_FAMILY }

    /** Returns [text] with a family [TypefaceSpan] covering the whole string. */
    fun spanned(text: CharSequence, family: String?): CharSequence =
        SpannableStringBuilder(text).withFamilySpan(family)

    /**
     * Builds a TextClock format string for [pattern] whose rendered output uses [family]. The
     * format is passed to the clock view through `setFormat24Hour`/`setFormat12Hour`; the
     * framework's `DateFormat` re-formats the pattern on every tick and preserves the span, so the
     * font survives updates.
     */
    fun clockFormat(pattern: String, family: String?): CharSequence =
        SpannableStringBuilder(pattern).apply { append('\'').append(GUARD).append('\'') }
            .withFamilySpan(family)

    private fun SpannableStringBuilder.withFamilySpan(family: String?): SpannableStringBuilder =
        apply {
            if (!family.isNullOrEmpty()) {
                setSpan(TypefaceSpan(family), 0, length, Spanned.SPAN_INCLUSIVE_INCLUSIVE)
            }
        }
}
