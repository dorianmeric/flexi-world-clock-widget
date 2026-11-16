package com.flexi.worldclock;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.CheckBox;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

/** Shared listener/slider plumbing for both configuration screens. */
final class Ui {

    private Ui() {
    }

    /** Spinner selection callback (ignores the initial selection event). */
    abstract static class OnItemSelected implements AdapterView.OnItemSelectedListener {
        private boolean armed;

        abstract void onSelected(int position);

        @Override
        public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
            if (!armed) {
                armed = true;
                return;
            }
            onSelected(position);
        }

        @Override
        public void onNothingSelected(AdapterView<?> parent) {
        }
    }

    /** Binds a SeekBar to an integer value range and keeps its value label in sync. */
    static final class Slider {
        private final Context context;
        private final TextView label;
        private final int min;
        private final int unitRes;
        private int value;

        private Slider(Context context, SeekBar bar, TextView label, int min, int max,
                int initial, int unitRes, IntSink sink) {
            this.context = context;
            this.label = label;
            this.min = min;
            this.unitRes = unitRes;
            this.value = initial;

            bar.setMax(max - min);
            bar.setProgress(initial - min);
            updateLabel();

            bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    value = min + progress;
                    updateLabel();
                    sink.onValue(value);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });
        }

        static Slider sp(Context context, SeekBar bar, TextView label, int min, int max,
                int initial, IntSink sink) {
            return new Slider(context, bar, label, min, max, initial, R.string.value_sp, sink);
        }

        static Slider dp(Context context, SeekBar bar, TextView label, int min, int max,
                int initial, IntSink sink) {
            return new Slider(context, bar, label, min, max, initial, R.string.value_dp, sink);
        }

                static Slider cells(Context context, SeekBar bar, TextView label, int min, int max,
                        int initial, IntSink sink) {
                    return new Slider(context, bar, label, min, max, initial, R.string.value_cells, sink);
                }

        private void updateLabel() {
            label.setText(context.getString(unitRes, String.valueOf(value)));
        }

        int value() {
            return value;
        }
    }

    /** Receives an integer slider value. */
    interface IntSink {
        void onValue(int value);
    }

    /** Receives a font family selected in a spinner. */
    interface FamilySink {
        void onValue(String family);
    }

        /** Receives the current background settings after any change. */
        interface BackgroundSink {
            void onValue(WidgetBackground.Settings settings);
        }

        /** Height of the status bar in px, taken from the window metrics. */
        static int statusBarHeight(Context context) {
            android.view.WindowManager windowManager =
                    (android.view.WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
            if (windowManager == null) {
                return 0;
            }
            android.view.WindowMetrics metrics = windowManager.getCurrentWindowMetrics();
            android.graphics.Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                    android.view.WindowInsets.Type.systemBars());
            return insets.top;
        }

        /** Pads {@code root} so an in-layout header clears the status bar. */
        static void applyStatusBarInset(View root) {
            if (root == null) {
                return;
            }
            int top = statusBarHeight(root.getContext());
            if (top > 0) {
                root.setPadding(root.getPaddingLeft(), root.getPaddingTop() + top,
                        root.getPaddingRight(), root.getPaddingBottom());
            }
        }

        /**
         * Binds the shared background panel (swatch strip, opacity slider, "no background" toggle) to
         * {@code settings}. Every change is written straight into the model and pushed to the preview.
         */
        static void bindBackground(View root, WidgetBackground.Settings settings,
                BackgroundSink sink) {
            Context context = root.getContext();
            LinearLayout swatches = root.findViewById(R.id.background_swatches);
            View controls = root.findViewById(R.id.background_controls);
            CheckBox none = root.findViewById(R.id.background_none);
            SeekBar opacity = root.findViewById(R.id.background_opacity);
            TextView opacityValue = root.findViewById(R.id.background_opacity_value);

            swatches.removeAllViews();
            LayoutInflater inflater = LayoutInflater.from(context);
            for (int i = 0; i < WidgetBackground.PRESET_COLORS.length; i++) {
                int color = WidgetBackground.PRESET_COLORS[i];
                View swatch = inflater.inflate(R.layout.item_swatch, swatches, false);
                View fill = swatch.findViewById(R.id.swatch_fill);
                ((GradientDrawable) fill.getBackground().mutate()).setColor(0xFF000000 | color);
                fill.setContentDescription(context.getString(R.string.background_swatch, i + 1));
                swatch.setOnClickListener(v -> {
                    settings.color = color;
                    settings.enabled = true;
                    none.setChecked(false);
                    syncBackgroundUi(swatches, controls, opacity, settings);
                    sink.onValue(settings);
                });
                swatches.addView(swatch);
            }

            opacity.setMax(100 - 5);
            opacity.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                @Override
                public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                    settings.opacity = 5 + progress;
                    opacityValue.setText(context.getString(R.string.value_percent,
                            String.valueOf(settings.opacity)));
                    if (!settings.enabled) {
                        settings.enabled = true;
                        none.setChecked(false);
                        syncBackgroundUi(swatches, controls, opacity, settings);
                    }
                    sink.onValue(settings);
                }

                @Override
                public void onStartTrackingTouch(SeekBar seekBar) {
                }

                @Override
                public void onStopTrackingTouch(SeekBar seekBar) {
                }
            });

            none.setOnCheckedChangeListener((buttonView, checked) -> {
                settings.enabled = !checked;
                syncBackgroundUi(swatches, controls, opacity, settings);
                sink.onValue(settings);
            });

            none.setChecked(!settings.enabled);
            syncBackgroundUi(swatches, controls, opacity, settings);
        }

        /** Reflects {@code settings} in the panel without firing the listeners again. */
        private static void syncBackgroundUi(LinearLayout swatches, View controls, SeekBar opacity,
                WidgetBackground.Settings settings) {
            controls.setAlpha(settings.enabled ? 1f : 0.35f);
            controls.setEnabled(settings.enabled);
            opacity.setProgress(Math.max(0, settings.opacity - 5));
            for (int i = 0; i < swatches.getChildCount(); i++) {
                View swatch = swatches.getChildAt(i);
                boolean selected = settings.enabled
                        && WidgetBackground.PRESET_COLORS[i] == (settings.color & 0x00FFFFFF);
                swatch.setSelected(selected);
            }
        }
}