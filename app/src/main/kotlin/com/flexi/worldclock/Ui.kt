package com.flexi.worldclock

import android.content.Context
import android.graphics.Insets
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.AdapterView
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView

/** Shared listener/slider plumbing for both configuration screens. */
internal object Ui {

    /** Spinner selection callback (ignores the initial selection event). */
    abstract class OnItemSelected : AdapterView.OnItemSelectedListener {
        private var armed = false

        abstract fun onSelected(position: Int)

        override fun onItemSelected(
            parent: AdapterView<*>?,
            view: View?,
            position: Int,
            id: Long,
        ) {
            if (!armed) {
                armed = true
                return
            }
            onSelected(position)
        }

        override fun onNothingSelected(parent: AdapterView<*>?) = Unit
    }

    /** Binds a SeekBar to an integer value range and keeps its value label in sync. */
    class Slider private constructor(
        private val context: Context,
        bar: SeekBar,
        private val label: TextView,
        private val min: Int,
        max: Int,
        initial: Int,
        private val unitRes: Int,
        sink: (Int) -> Unit,
    ) {
        private var value = initial

        init {
            bar.max = max - min
            bar.progress = initial - min
            updateLabel()

            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                    value = min + progress
                    updateLabel()
                    sink(value)
                }

                override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

                override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
            })
        }

        private fun updateLabel() {
            label.text = context.getString(unitRes, value.toString())
        }

        companion object {
            fun sp(
                context: Context,
                bar: SeekBar,
                label: TextView,
                min: Int,
                max: Int,
                initial: Int,
                sink: (Int) -> Unit,
            ): Slider = Slider(context, bar, label, min, max, initial, R.string.value_sp, sink)

            fun dp(
                context: Context,
                bar: SeekBar,
                label: TextView,
                min: Int,
                max: Int,
                initial: Int,
                sink: (Int) -> Unit,
            ): Slider = Slider(context, bar, label, min, max, initial, R.string.value_dp, sink)

            fun cells(
                context: Context,
                bar: SeekBar,
                label: TextView,
                min: Int,
                max: Int,
                initial: Int,
                sink: (Int) -> Unit,
            ): Slider = Slider(context, bar, label, min, max, initial, R.string.value_cells, sink)
        }
    }

    /** Height of the status bar in px, taken from the window metrics. */
    private fun statusBarHeight(context: Context): Int {
        val windowManager =
            context.getSystemService(Context.WINDOW_SERVICE) as? WindowManager ?: return 0
        val insets: Insets = windowManager.currentWindowMetrics.windowInsets
            .getInsetsIgnoringVisibility(WindowInsets.Type.systemBars())
        return insets.top
    }

    /** Pads [root] so an in-layout header clears the status bar. */
    fun applyStatusBarInset(root: View?) {
        if (root == null) {
            return
        }
        val top = statusBarHeight(root.context)
        if (top > 0) {
            root.setPadding(
                root.paddingLeft,
                root.paddingTop + top,
                root.paddingRight,
                root.paddingBottom,
            )
        }
    }

    /**
     * Binds the shared background panel (swatch strip, opacity slider, "no background" toggle) to
     * [settings]. Every change is written straight into the model and pushed to the preview.
     */
    fun bindBackground(
        root: View,
        settings: WidgetBackground.Settings,
        onChanged: (WidgetBackground.Settings) -> Unit,
    ) {
        val context = root.context
        val swatches = root.findViewById<LinearLayout>(R.id.background_swatches)
        val controls = root.findViewById<View>(R.id.background_controls)
        val none = root.findViewById<CheckBox>(R.id.background_none)
        val opacity = root.findViewById<SeekBar>(R.id.background_opacity)
        val opacityValue = root.findViewById<TextView>(R.id.background_opacity_value)

        swatches.removeAllViews()
        val inflater = LayoutInflater.from(context)
        WidgetBackground.PRESET_COLORS.forEachIndexed { index, color ->
            val swatch = inflater.inflate(R.layout.item_swatch, swatches, false)
            val fill = swatch.findViewById<View>(R.id.swatch_fill)
            (fill.background.mutate() as GradientDrawable).setColor(0xFF000000.toInt() or color)
            fill.contentDescription = context.getString(R.string.background_swatch, index + 1)
            swatch.setOnClickListener {
                settings.color = color
                settings.enabled = true
                none.isChecked = false
                syncBackgroundUi(swatches, controls, opacity, settings)
                onChanged(settings)
            }
            swatches.addView(swatch)
        }

        opacity.max = 100 - 5
        opacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar, progress: Int, fromUser: Boolean) {
                settings.opacity = 5 + progress
                opacityValue.text =
                    context.getString(R.string.value_percent, settings.opacity.toString())
                if (!settings.enabled) {
                    settings.enabled = true
                    none.isChecked = false
                    syncBackgroundUi(swatches, controls, opacity, settings)
                }
                onChanged(settings)
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) = Unit

            override fun onStopTrackingTouch(seekBar: SeekBar) = Unit
        })

        none.setOnCheckedChangeListener { _, checked ->
            settings.enabled = !checked
            syncBackgroundUi(swatches, controls, opacity, settings)
            onChanged(settings)
        }

        none.isChecked = !settings.enabled
        syncBackgroundUi(swatches, controls, opacity, settings)
    }

    /** Reflects [settings] in the panel. */
    private fun syncBackgroundUi(
        swatches: LinearLayout,
        controls: View,
        opacity: SeekBar,
        settings: WidgetBackground.Settings,
    ) {
        controls.alpha = if (settings.enabled) 1f else 0.35f
        controls.isEnabled = settings.enabled
        opacity.progress = maxOf(0, settings.opacity - 5)
        for (i in 0 until swatches.childCount) {
            val selected = settings.enabled &&
                WidgetBackground.PRESET_COLORS[i] == (settings.color and 0x00FFFFFF)
            swatches.getChildAt(i).isSelected = selected
        }
    }
}
