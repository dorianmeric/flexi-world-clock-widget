package com.flexi.worldclock

import android.content.ClipData
import android.content.ClipDescription
import android.view.DragEvent
import android.view.HapticFeedbackConstants
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator

/**
 * Drag-and-drop reordering for the vertical list of timezone rows.
 *
 * The framework's own drag-and-drop is used rather than an AndroidX RecyclerView, so the widget
 * pack needs no extra UI dependency. Reordering happens live: as soon as the pointer enters another row,
 * that row is swapped, so releasing the finger simply ends the gesture. The swap slides rather than
 * jumps, so the grip on the row is never lost from one frame to the next.
 */
internal object DragReorder {

    private const val LABEL = "flexi-zone-row"

    /** How long a row takes to slide to the slot it was swapped into. Short, so the list keeps up. */
    private const val SLIDE_MS = 140L

    private val slideInterpolator = DecelerateInterpolator()

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
                DragEvent.ACTION_DRAG_STARTED -> {
                    val ours = isOurs(event)
                    // The row the handle belongs to is carried as the drag's local state, so the
                    // outline goes on the right row even before the pointer moves.
                    if (ours) (event.localState as? Int)?.let { highlight(container, it) }
                    ours
                }

                DragEvent.ACTION_DRAG_LOCATION -> {
                    handleLocation(container, order, event, onReordered)
                    true
                }

                DragEvent.ACTION_DRAG_ENDED -> {
                    highlight(container, -1)
                    onReordered()
                    true
                }

                else -> true
            }
        }
    }

    /**
     * Puts the dotted outline of `bg_zone_row` on the row being dragged, and takes it off every
     * other row. The row's background is a state list keyed on `state_activated`, so setting the
     * flag is all the "in hand" look needs; -1 clears it when the drag ends.
     */
    fun highlight(container: ViewGroup, index: Int) {
        for (i in 0 until container.childCount) {
            container.getChildAt(i).isActivated = i == index
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
        // Where every row is drawn right now, so each can slide from there into its new slot
        // instead of jumping. Read before the children move and including any slide still in
        // flight, so a swap that interrupts one carries on from where the row actually is.
        val startTops = visualTops(container)
        container.removeViewAt(from)
        container.addView(row, to)
        reorder(order, from, to)
        container.setTag(R.id.drag_from_index, to)
        refreshRowIndexes(container)
        slideIntoPlace(container, startTops)
        onReordered()
    }

    /** Top of every child as drawn: its laid-out top plus whatever slide is still in flight. */
    private fun visualTops(container: ViewGroup): Map<View, Float> {
        val tops = HashMap<View, Float>(container.childCount)
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            tops[child] = child.top + child.translationY
        }
        return tops
    }

    /**
     * Slides every row from where it was drawn to where the reorder has just put it.
     *
     * The new positions are only known once the container has laid the children out again, so the
     * offsets are applied from a pre-draw pass; measuring any earlier would find the old tops and
     * animate nothing.
     */
    private fun slideIntoPlace(container: ViewGroup, startTops: Map<View, Float>) {
        // A fast drag can swap again before the previous slide was set up; drop that pending one
        // so only the newest offsets are applied against the newest layout.
        (container.getTag(R.id.drag_slide_listener) as? ViewTreeObserver.OnPreDrawListener)?.let {
            if (container.viewTreeObserver.isAlive) {
                container.viewTreeObserver.removeOnPreDrawListener(it)
            }
        }

        val observer = container.viewTreeObserver
        val listener = object : ViewTreeObserver.OnPreDrawListener {
            override fun onPreDraw(): Boolean {
                if (observer.isAlive) {
                    observer.removeOnPreDrawListener(this)
                }
                container.setTag(R.id.drag_slide_listener, null)
                applySlide(container, startTops)
                return true
            }
        }
        container.setTag(R.id.drag_slide_listener, listener)
        observer.addOnPreDrawListener(listener)
    }

    /** Puts each moved child back at its old drawn position and animates it home. */
    private fun applySlide(container: ViewGroup, startTops: Map<View, Float>) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val start = startTops[child] ?: continue
            val offset = start - child.top
            if (offset == 0f && child.translationY == 0f) {
                continue
            }
            child.translationY = offset
            child.animate()
                .translationY(0f)
                .setDuration(SLIDE_MS)
                .setInterpolator(slideInterpolator)
                .start()
        }
    }

    /**
     * Row index that should hold the dragged row.
     *
     * [DragEvent.getY] arrives in the coordinate space of the receiving view — the framework offsets
     * it down the hierarchy before the listener sees it — so it is already relative to the container,
     * as are the row bounds. Only the container's own scroll has to be added back.
     */
    fun slotFor(container: ViewGroup, event: DragEvent, skipIndex: Int): Int {
        val count = container.childCount
        if (count == 0) {
            return -1
        }
        val y = event.y + container.scrollY

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
