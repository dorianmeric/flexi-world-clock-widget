package com.flexi.worldclock

import org.junit.Assert.assertEquals
import org.junit.Test

/** The slot maths behind a drag: which slot a row drawn at a given centre belongs in. */
class DragReorderTest {

    /** Three flush 48px rows, so the slot centres sit 48px apart. */
    private val evenSlots = floatArrayOf(24f, 72f, 120f)

    @Test
    fun slotIsTheOneTheRowIsDrawnOver() {
        assertEquals(0, DragReorder.nearestSlot(evenSlots, 24f))
        assertEquals(1, DragReorder.nearestSlot(evenSlots, 72f))
        assertEquals(2, DragReorder.nearestSlot(evenSlots, 120f))
    }

    @Test
    fun slotChangesAtTheNeighbouringCentre() {
        // Dragging up onto the row above: the slots change over once the centre of the row in hand
        // has passed the centre of the slot it is heading for.
        assertEquals(1, DragReorder.nearestSlot(evenSlots, 49f))
        assertEquals(0, DragReorder.nearestSlot(evenSlots, 47f))
        assertEquals(2, DragReorder.nearestSlot(evenSlots, 97f))
        assertEquals(1, DragReorder.nearestSlot(evenSlots, 95f))
    }

    @Test
    fun slotsOfDifferentHeightsAreFollowed() {
        val uneven = floatArrayOf(30f, 100f, 230f)
        assertEquals(1, DragReorder.nearestSlot(uneven, 90f))
        assertEquals(2, DragReorder.nearestSlot(uneven, 200f))
    }

    @Test
    fun draggingPastTheEndsStopsAtTheFirstAndLastSlot() {
        assertEquals(0, DragReorder.nearestSlot(evenSlots, -500f))
        assertEquals(2, DragReorder.nearestSlot(evenSlots, 900f))
    }
}
