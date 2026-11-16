package com.flexi.worldclock;

import android.content.ClipData;
import android.content.ClipDescription;
import android.view.DragEvent;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.view.ViewGroup;

import java.util.List;

/**
 * Drag-and-drop reordering for the vertical list of timezone rows.
 *
 * <p>The framework's own drag-and-drop is used rather than an AndroidX RecyclerView, so the widget
 * pack stays dependency free. Reordering happens live: as soon as the pointer enters another row,
 * that row is swapped, so releasing the finger simply ends the gesture.
 */
final class DragReorder {

    private static final String LABEL = "flexi-zone-row";

    private DragReorder() {
    }

    /** Notified whenever the order changes during a drag. */
    interface OnReordered {
        void onReordered();
    }

    /** Registers {@code handle} as the drag source of the row rendered at {@code index}. */
    static void markHandle(View handle, int index) {
        handle.setOnLongClickListener(v -> {
            ClipData data = ClipData.newPlainText(LABEL, String.valueOf(index));
            v.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                v.startDragAndDrop(data, new View.DragShadowBuilder(v), Integer.valueOf(index), 0);
            return true;
        });
    }

    /**
     * Attaches the drop logic to the row container. {@code order} is the mutable list mirrored by
     * the container's children, so every swap keeps the model and the views in sync.
     */
    static void attach(ViewGroup container, List<?> order, OnReordered callback) {
        container.setOnDragListener((view, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return isOurs(event);
                case DragEvent.ACTION_DRAG_LOCATION:
                    handleLocation(container, order, event, callback);
                    return true;
                case DragEvent.ACTION_DRAG_ENDED:
                    if (callback != null) {
                        callback.onReordered();
                    }
                    return true;
                default:
                    return true;
            }
        });
    }

    /** Swaps the dragged row with the row currently under the pointer. */
    private static void handleLocation(ViewGroup container, List<?> order, DragEvent event,
            OnReordered callback) {
        int from = draggedIndex(container, event);
        if (from < 0) {
            return;
        }
            int to = slotFor(container, event, from);
        if (to < 0 || from == to
                || from >= container.getChildCount() || to >= container.getChildCount()
                || from >= order.size() || to >= order.size()) {
            return;
        }
        View row = container.getChildAt(from);
        container.removeViewAt(from);
        container.addView(row, to);
        reorder(order, from, to);
        container.setTag(R.id.drag_from_index, to);
        refreshRowIndexes(container);
        if (callback != null) {
            callback.onReordered();
        }
    }

    /**
         * Row index that should hold the dragged row.
         *
         * <p>{@link DragEvent#getY()} is in window coordinates, while row bounds are relative to the
         * container, so both are converted into the container's own coordinate space first.
     */
        static int slotFor(ViewGroup container, DragEvent event, int skipIndex) {
        int count = container.getChildCount();
        if (count == 0) {
            return -1;
        }
            int[] location = new int[2];
            container.getLocationInWindow(location);
            float y = event.getY() - location[1] + container.getScrollY();

        if (y < container.getChildAt(0).getTop()) {
            return 0;
        }
        for (int i = 0; i < count; i++) {
                if (i == skipIndex) {
                    continue;
                }
            View row = container.getChildAt(i);
            if (y < row.getBottom()) {
                return i;
            }
        }
        return count - 1;
    }

    /** Pure list reorder, split out so it can be tested without a view hierarchy. */
    static <T> void reorder(List<T> order, int from, int to) {
        if (from == to || from < 0 || to < 0 || from >= order.size() || to >= order.size()) {
            return;
        }
        T value = order.remove(from);
        order.add(to, value);
    }

    private static boolean isOurs(DragEvent event) {
        ClipDescription description = event.getClipDescription();
        return description != null && LABEL.contentEquals(description.getLabel());
    }

    /** Index of the row being dragged, remembered on the container between drag events. */
    private static int draggedIndex(ViewGroup container, DragEvent event) {
        Object tag = container.getTag(R.id.drag_from_index);
        if (tag instanceof Integer) {
            return (Integer) tag;
        }
        Object local = event.getLocalState();
        if (local instanceof Integer) {
            int index = (Integer) local;
            container.setTag(R.id.drag_from_index, index);
            return index;
        }
        return -1;
    }

    /** Keeps the per-handle tags aligned with the new child order. */
    private static void refreshRowIndexes(ViewGroup container) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View row = container.getChildAt(i);
            View handle = row.findViewById(R.id.zone_handle);
            if (handle != null) {
                markHandle(handle, i);
            }
        }
    }
}