package com.flexi.worldclock;

import android.content.Context;
import android.view.View;
import android.widget.AdapterView;
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
}