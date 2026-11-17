package com.flexi.worldclock

import android.content.ClipData
import android.content.ClipDescription
import android.view.DragEvent
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup

/**
 * Drag-and-drop reordering for the vertical list of timezone rows.
 *
 * The framework's own drag-and-drop is used rather than an AndroidX RecyclerView, so the widget
 * pack needs no extra UI dependency. Reordering happens live: as soon as the pointer enters another row,
 * that row is swapped, so releasing the finger simply ends the gesture.
 */
internal object DragReorder {

    private const val LABEL = "flexi-zone-row"

    /** Registers [handle] as the drag source of the row rendered at [index]. */
    fun markHandle(handle: View, index: Int) {
        handle.setOnLongClickListener { view ->
            val data = ClipData.newPlainText(LABEL, index.toString())
            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            view.startDragAndDrop(data, View.DragShadowBuilder(view), index, 0)
            true
        }
    }

    /**
     * Attaches the drop logic to the row container. [order] is the mutable list mirrored by the
     * container's children, so every swap keeps the model and the views in sync. [onReordered] is
     * notified on every change, including once when the drag ends.
     */
    fun attach(container: ViewGroup, order: MutableList<*>, onReordered: () -> Unit) {
        container.setOnDragListener { _, event ->
            when (event.action) {
                DragEvent.ACTION_DRAG_STARTED -> isOurs(event)
                DragEvent.ACTION_DRAG_LOCATION -> {
                    handleLocation(container, order, event, onReordered)
                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    onReordered()
                    true
                }

                else -> true
            }
        }
    }

    /** Swaps the dragged row with the row currently under the pointer. */
    private fun handleLocation(
        container: ViewGroup,
        order: MutableList<*>,
        event: DragEvent,
        onReordered: () -> Unit,
    ) {
        val from = draggedIndex(container, event)
        if (from < 0) {
            return
        }
        val to = slotFor(container, event, from)
        if (to < 0 || from == to ||
            from >= container.childCount || to >= container.childCount ||
            from >= order.size || to >= order.size
        ) {
            return
        }
        val row = container.getChildAt(from)
        container.removeViewAt(from)
        container.addView(row, to)
        reorder(order, from, to)
        container.setTag(R.id.drag_from_index, to)
        refreshRowIndexes(container)
        onReordered()
    }

    /**
     * Row index that should hold the dragged row.
     *
     * [DragEvent.getY] is in window coordinates, while row bounds are relative to the container, so
     * both are converted into the container's own coordinate space first.
     */
    fun slotFor(container: ViewGroup, event: DragEvent, skipIndex: Int): Int {
        val count = container.childCount
        if (count == 0) {
            return -1
        }
        val location = IntArray(2)
        container.getLocationInWindow(location)
        val y = event.y - location[1] + container.scrollY

        if (y < container.getChildAt(0).top) {
            return 0
        }
        for (i in 0 until count) {
            if (i == skipIndex) {
                continue
            }
            if (y < container.getChildAt(i).bottom) {
                return i
            }
        }
        return count - 1
    }

    /** Pure list reorder, split out so it can be tested without a view hierarchy. */
    fun <T> reorder(order: MutableList<T>, from: Int, to: Int) {
        if (from == to || from < 0 || to < 0 || from >= order.size || to >= order.size) {
            return
        }
        order.add(to, order.removeAt(from))
    }

    private fun isOurs(event: DragEvent): Boolean {
        val description = event.clipDescription ?: return false
        return LABEL.contentEquals(description.label)
    }

    /** Index of the row being dragged, remembered on the container between drag events. */
    private fun draggedIndex(container: ViewGroup, event: DragEvent): Int {
        (container.getTag(R.id.drag_from_index) as? Int)?.let { return it }
        val local = event.localState
        if (local is Int) {
            container.setTag(R.id.drag_from_index, local)
            return local
        }
        return -1
    }

    /** Keeps the per-handle tags aligned with the new child order. */
    private fun refreshRowIndexes(container: ViewGroup) {
        for (i in 0 until container.childCount) {
            container.getChildAt(i).findViewById<View>(R.id.zone_handle)?.let { markHandle(it, i) }
        }
    }
}
