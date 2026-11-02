package com.lxmusic.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QueueLogicTest {

    private val q = listOf("A", "B", "C", "D")

    @Test fun moveKeepsSameTrackSelected() {
        val r = QueueLogic.move(q, index = 1, from = 0, to = 3)
        assertEquals(listOf("B", "C", "D", "A"), r.list)
        assertEquals(0, r.index)
    }

    @Test fun moveTrackItself() {
        val r = QueueLogic.move(q, index = 1, from = 1, to = 3)
        assertEquals(listOf("A", "C", "D", "B"), r.list)
        assertEquals(3, r.index)
    }

    @Test fun moveBeforeSelectedShiftsIndex() {
        val r = QueueLogic.move(q, index = 2, from = 3, to = 0)
        assertEquals(listOf("D", "A", "B", "C"), r.list)
        assertEquals(3, r.index)
    }

    @Test fun moveInvalidIsNoop() {
        val r = QueueLogic.move(q, index = 1, from = 9, to = 0)
        assertEquals(q, r.list)
        assertEquals(1, r.index)
    }

    @Test fun removeBeforeSelectedShiftsIndex() {
        val r = QueueLogic.remove(q, index = 2, removeAt = 0)
        assertEquals(listOf("B", "C", "D"), r.list)
        assertEquals(1, r.index)
        assertFalse(r.shouldPlay)
    }

    @Test fun removeSelectedAsksToPlayNext() {
        val r = QueueLogic.remove(q, index = 1, removeAt = 1)
        assertEquals(listOf("A", "C", "D"), r.list)
        assertEquals(1, r.index)
        assertTrue(r.shouldPlay)
    }

    @Test fun removeSelectedLastClampsIndex() {
        val r = QueueLogic.remove(q, index = 3, removeAt = 3)
        assertEquals(listOf("A", "B", "C"), r.list)
        assertEquals(2, r.index)
        assertTrue(r.shouldPlay)
    }

    @Test fun removeLastItemClearsQueue() {
        val r = QueueLogic.remove(listOf("A"), index = 0, removeAt = 0)
        assertTrue(r.list.isEmpty())
        assertEquals(-1, r.index)
        assertFalse(r.shouldPlay)
    }

    // ---------------- removeAll (V1.1.5: deleting a file clears its slots) ----------------

    @Test fun removeAllDropsEveryMatchingSlot() {
        val dup = listOf("A", "B", "A", "C", "A")
        val r = QueueLogic.removeAll(dup, index = 1) { it == "A" }
        assertEquals(listOf("B", "C"), r.list)
        assertTrue(r.changed)
    }

    @Test fun removeAllKeepsThePlayingTrackWhenItSurvives() {
        val r = QueueLogic.removeAll(listOf("A", "B", "C", "D"), index = 2) { it == "A" }
        assertEquals(listOf("B", "C", "D"), r.list)
        assertEquals("C", r.list[r.index])
        assertFalse(r.playingRemoved)
    }

    @Test fun removeAllShiftsTheIndexForMatchesBeforeIt() {
        val r = QueueLogic.removeAll(listOf("X", "X", "Y", "X", "Z"), index = 2) { it == "X" }
        assertEquals(listOf("Y", "Z"), r.list)
        assertEquals(0, r.index)
        assertEquals("Y", r.list[r.index])
        assertFalse(r.playingRemoved)
    }

    @Test fun removeAllReportsWhenThePlayingTrackItselfIsGone() {
        val r = QueueLogic.removeAll(listOf("A", "B", "C"), index = 1) { it == "B" }
        assertEquals(listOf("A", "C"), r.list)
        assertEquals(1, r.index)
        assertEquals("C", r.list[r.index])
        assertTrue(r.playingRemoved)
    }

    @Test fun removeAllClampsWhenThePlayingTrackWasLast() {
        val r = QueueLogic.removeAll(listOf("A", "B", "C"), index = 2) { it == "C" }
        assertEquals(listOf("A", "B"), r.list)
        assertEquals(1, r.index)
        assertTrue(r.playingRemoved)
    }

    @Test fun removeAllClearingEverythingResetsTheIndex() {
        val r = QueueLogic.removeAll(listOf("A", "A"), index = 0) { it == "A" }
        assertTrue(r.list.isEmpty())
        assertEquals(-1, r.index)
        assertTrue(r.playingRemoved)
        assertTrue(r.changed)
    }

    @Test fun removeAllWithNoMatchIsANoOp() {
        val q = listOf("A", "B")
        val r = QueueLogic.removeAll(q, index = 1) { it == "Z" }
        assertFalse("nothing matched, so callers can skip their follow-up", r.changed)
        assertEquals(q, r.list)
        assertEquals(1, r.index)
        assertFalse(r.playingRemoved)
    }

    @Test fun removeAllToleratesAnOutOfRangeIndex() {
        val r = QueueLogic.removeAll(listOf("A", "B"), index = 7) { it == "A" }
        assertEquals(listOf("B"), r.list)
        assertEquals(0, r.index)
        assertFalse(r.playingRemoved)
    }
}
