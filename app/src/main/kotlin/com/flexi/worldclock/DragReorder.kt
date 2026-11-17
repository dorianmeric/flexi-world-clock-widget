package com.flexi.worldclock

import android.annotation.SuppressLint
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.animation.DecelerateInterpolator
import kotlin.math.abs

/**
 * Touch-driven reordering of the vertical list of timezone rows.
 *
 * The framework's own drag-and-drop is deliberately not used: it moves a drag *shadow* while the
 * real row stays put in the list, and the location events it delivers are too sparse to preview an
 * order with. Here the row itself is what follows the finger. A long press on the grip lifts it, the
 * row is then translated under the finger for the rest of the gesture, and every row the finger
 * passes slides one slot out of the way — so the order the list would end up in is on screen before
 * the finger comes up.
 *
 * The row views only change places when the finger lifts, which keeps the maths to the fixed slots
 * measured when the row was lifted. The model, by contrast, takes the new order as soon as the
 * finger reaches a slot, so the widget preview follows the drag as well. The two are brought back
 * into agreement by [drop]; while a drag is in flight the model is therefore one step ahead of the
 * rows, and only [onPreview] — which must not read the rows — may be called in that state.
 */
internal class DragReorder(
    private val container: ViewGroup,
    private val order: MutableList<*>,
    private val onPreview: () -> Unit,
    private val onReordered: () -> Unit,
) {
    /** The grip under the finger, and the row it belongs to, until the press is resolved. */
    private var grip: View? = null
    private var pressedRow: View? = null

    /** The row in hand, following the finger, or null when nothing is being dragged. */
    private var lifted: View? = null

    /** Slot the lifted row's view still occupies, and the slot the model has it in now. */
    private var fromIndex = -1
    private var heldIndex = -1

    /** Slot the finger is over: where the row would land if the finger came up now. */
    private var targetIndex = -1

    /** Centre of each slot, read once when the row is lifted and fixed for the gesture. */
    private var slotCenters = FloatArray(0)

    private var downRawY = 0f
    private var lastRawY = 0f

    /** Pointer that owns the gesture, so a second finger cannot take the row over from it. */
    private var activePointer = MotionEvent.INVALID_POINTER_ID

    private val touchSlop = ViewConfiguration.get(container.context).scaledTouchSlop
    private val slideInterpolator = DecelerateInterpolator()
    private val armDrag = Runnable { arm() }

    /**
     * Makes a long press on [row]'s grip pick that row up. The whole row is tracked rather than just
     * the grip, so the row keeps following the finger once the finger wanders off the handle.
     */
    @SuppressLint("ClickableViewAccessibility")
    fun markHandle(row: View) {
        row.findViewById<View>(R.id.zone_handle)?.setOnTouchListener { gripView, event ->
            onTouch(gripView, row, event)
        }
    }

    private fun onTouch(gripView: View, row: View, event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // A drag needs somewhere to go, and the row has to stand for a zone in the list.
                val slot = container.indexOfChild(row)
                if (slot < 0 || slot >= order.size || container.childCount < 2) {
                    return false
                }
                grip = gripView
                pressedRow = row
                gripView.isPressed = true
                activePointer = event.getPointerId(0)
                downRawY = event.rawY
                lastRawY = event.rawY
                container.postDelayed(armDrag, ViewConfiguration.getLongPressTimeout().toLong())
            }

            MotionEvent.ACTION_MOVE -> {
                lastRawY = event.rawY
                if (lifted == null) {
                    // The finger is on its way somewhere else — usually scrolling the page — so the
                    // press that would have lifted the row is called off.
                    if (abs(event.rawY - downRawY) > touchSlop) {
                        endPress()
                    }
                } else {
                    followFinger(event.rawY)
                }
            }

            MotionEvent.ACTION_UP -> if (lifted == null) endPress() else drop()

            MotionEvent.ACTION_CANCEL -> if (lifted == null) endPress() else abandon()

            // Another finger landing is ignored: rawY is the first pointer's, which is the one the
            // press started with. That pointer leaving is the end of the gesture, even though the
            // finger holding it was not the one lifted last.
            MotionEvent.ACTION_POINTER_UP -> {
                if (event.getPointerId(event.actionIndex) == activePointer) {
                    if (lifted == null) endPress() else drop()
                }
            }
        }
        return lifted != null || pressedRow != null
    }

    /**
     * Ends a drag that is in flight, putting the rows and the model back the way they were. Callers
     * that rebuild the rows have to do this first: a drag holds on to the very views they replace.
     */
    fun cancelDrag() {
        if (lifted == null) {
            endPress()
        } else {
            abandon()
        }
    }

    /** Calls off a press that never grew into a drag. */
    private fun endPress() {
        container.removeCallbacks(armDrag)
        grip?.isPressed = false
        grip = null
        pressedRow = null
        activePointer = MotionEvent.INVALID_POINTER_ID
    }

    /** Lifts the pressed row so it becomes the one that follows the finger. */
    private fun arm() {
        val row = pressedRow ?: return
        val slot = container.indexOfChild(row)
        if (slot < 0 || slot >= order.size) {
            return
        }

        val count = container.childCount
        slotCenters = FloatArray(count)
        for (i in 0 until count) {
            val child = container.getChildAt(i)
            slotCenters[i] = child.top + child.height / 2f
        }

        fromIndex = slot
        heldIndex = slot
        targetIndex = slot
        lifted = row
        highlight(container, slot)
        // Floating above the list is also what draws the row over the rows it is pushed past.
        row.elevation = LIFT_DP * container.resources.displayMetrics.density
        // The page must not scroll out from under a row that is in hand.
        container.requestDisallowInterceptTouchEvent(true)
        row.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        followFinger(lastRawY)
    }

    /**
     * Keeps the lifted row under the finger, moving the rest of the list on as the finger reaches a
     * new slot. The lifted row's own view stays where it was laid out, so its translation is the
     * whole of its movement and the slot bounds measured at lift time stay valid.
     */
    private fun followFinger(rawY: Float) {
        val row = lifted ?: return
        row.translationY = rawY - downRawY

        val center = slotCenters[fromIndex] + row.translationY
        val slot = nearestSlot(slotCenters, center)
        if (slot == targetIndex) {
            return
        }

        targetIndex = slot
        // The model takes the order at once so the widget preview follows the finger; the rows are
        // only moved on release, which is why the labels on screen never scramble mid-drag.
        reorder(order, heldIndex, targetIndex)
        heldIndex = targetIndex
        shiftRows()
        onPreview()
    }

    /** Slides every other row one slot out of the way of the lifted one. */
    private fun shiftRows() {
        val row = lifted ?: return
        val shift = slotHeight(row)
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            if (child === row) {
                continue
            }
            val offset = when {
                fromIndex < targetIndex && i in (fromIndex + 1)..targetIndex -> -shift
                fromIndex > targetIndex && i in targetIndex until fromIndex -> shift
                else -> 0f
            }
            if (child.translationY == offset) {
                continue
            }
            child.animate()
                .translationY(offset)
                .setDuration(SLIDE_MS)
                .setInterpolator(slideInterpolator)
                .start()
        }
    }

    /**
     * One slot is the lifted row plus any margin it carries, since the rows are stacked flush. Rows
     * of unequal height would need each slot measured instead, but the city and meta labels are
     * capped at one line, so every row in the list is the same height.
     */
    private fun slotHeight(row: View): Float {
        val params = row.layoutParams as? ViewGroup.MarginLayoutParams
        val margins = (params?.topMargin ?: 0) + (params?.bottomMargin ?: 0)
        return (row.height + margins).toFloat()
    }

    /** Finger up: the rows take the order that has been on screen all along. */
    private fun drop() {
        val row = lifted ?: return
        val drawnTops = drawnTops()
        val from = fromIndex
        val to = targetIndex
        endGesture()
        if (from != to) {
            container.removeViewAt(from)
            container.addView(row, to)
        }
        settle(drawnTops, row)
        onReordered()
    }

    /** The gesture was taken away: nothing was committed, so every row slides back where it was. */
    private fun abandon() {
        val row = lifted ?: return
        val drawnTops = drawnTops()
        // Put the model back the way the rows still are.
        reorder(order, heldIndex, fromIndex)
        endGesture()
        settle(drawnTops, row)
        onPreview()
    }

    private fun endGesture() {
        endPress()
        highlight(container, -1)
        lifted = null
        fromIndex = -1
        heldIndex = -1
        targetIndex = -1
        slotCenters = FloatArray(0)
        container.requestDisallowInterceptTouchEvent(false)
    }

    /** Where every row is drawn right now: its laid-out top plus whatever slide is still in flight. */
    private fun drawnTops(): Map<View, Float> {
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
     * animate nothing. The row in hand is part of that: it stops being drawn under the finger and
     * comes to rest in its slot, which is the only movement it makes that the finger did not ask for.
     */
    private fun settle(drawnTops: Map<View, Float>, liftedRow: View) {
        // A second gesture can start before the first has settled; drop that pending pass so only
        // the newest offsets are applied against the newest layout.
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
                applySettle(drawnTops, liftedRow)
                return true
            }
        }
        container.setTag(R.id.drag_slide_listener, listener)
        observer.addOnPreDrawListener(listener)
        // The pass is only reached through a traversal, and a drag that ends in the slot it started
        // in changes nothing else, so ask for one explicitly.
        container.requestLayout()
    }

    /** Puts each moved child back at its old drawn position and animates it home. */
    private fun applySettle(drawnTops: Map<View, Float>, liftedRow: View) {
        for (i in 0 until container.childCount) {
            val child = container.getChildAt(i)
            val drawnTop = drawnTops[child] ?: continue
            val offset = drawnTop - child.top
            if (offset == 0f && child.translationY == 0f) {
                if (child === liftedRow) {
                    child.elevation = 0f
                }
                continue
            }
            child.translationY = offset
            val animation = child.animate()
                .translationY(0f)
                .setDuration(SLIDE_MS)
                .setInterpolator(slideInterpolator)
            if (child === liftedRow) {
                animation.withEndAction { child.elevation = 0f }
            }
            animation.start()
        }
    }

    companion object {
        /** How long a row takes to slide one slot out of the way, or to settle after a drop. */
        private const val SLIDE_MS = 140L

        /** How far the row in hand floats, in dp, so it is drawn over the rows it passes. */
        private const val LIFT_DP = 6f

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

        /** Row index that should hold a row drawn with its centre at [y]. Pure, so it is testable. */
        fun nearestSlot(centers: FloatArray, y: Float): Int {
            var best = 0
            var bestDistance = abs(centers[0] - y)
            for (i in 1 until centers.size) {
                val distance = abs(centers[i] - y)
                if (distance < bestDistance) {
                    best = i
                    bestDistance = distance
                }
            }
            return best
        }

        /** Pure list reorder, split out so it can be tested without a view hierarchy. */
        fun <T> reorder(order: MutableList<T>, from: Int, to: Int) {
            if (from == to || from < 0 || to < 0 || from >= order.size || to >= order.size) {
                return
            }
            order.add(to, order.removeAt(from))
        }
    }
}
