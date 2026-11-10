package com.lxmusic.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for drag-to-reorder arithmetic. */
class QueueDragTest {

    private val row = 100f

    @Test
    fun aDragShorterThanHalfARowDoesNotMoveAnything() {
        val d = QueueDrag.resolve(from = 2, draggedPx = 40f, rowHeightPx = row, size = 10)
        assertEquals(2, d.to)
        assertFalse(d.moved)
    }

    @Test
    fun halfARowIsEnoughToClaimTheNextSlot() {
        assertEquals(3, QueueDrag.resolve(2, 50f, row, 10).to)
        assertEquals(1, QueueDrag.resolve(2, -50f, row, 10).to)
    }

    @Test
    fun theTargetIsClampedToTheQueue() {
        assertEquals(9, QueueDrag.resolve(8, 900f, row, 10).to)
        assertEquals(0, QueueDrag.resolve(1, -900f, row, 10).to)
    }

    @Test
    fun theOffsetKeepsTheWholeTravelledDistance() {
        // The row has to stay under the finger, even past the clamped target.
        val d = QueueDrag.resolve(8, 900f, row, 10)
        assertEquals(900f, d.offsetPx, 0.01f)
        assertEquals(9, d.to)
    }

    @Test
    fun anOutOfRangeStartIsInert() {
        val d = QueueDrag.resolve(from = -1, draggedPx = 500f, rowHeightPx = row, size = 10)
        assertEquals(-1, d.to)
        assertEquals(0f, d.offsetPx, 0.01f)
        assertFalse(d.moved)
    }

    @Test
    fun aZeroRowHeightDoesNotDivideByZero() {
        val d = QueueDrag.resolve(3, 250f, rowHeightPx = 0f, size = 10)
        assertEquals(3, d.to)
        assertEquals(0f, d.offsetPx, 0.01f)
    }

    @Test
    fun theDraggedRowFollowsTheFinger() {
        val d = QueueDrag.resolve(2, 260f, row, 10)
        assertEquals(260f, QueueDrag.displacementFor(2, d, row), 0.01f)
    }

    @Test
    fun rowsTheDragPassedSlideTheOtherWay() {
        // 240 px = 2.4 行 → 落点 4，把 3、4 各顶上去一行。
        val d = QueueDrag.resolve(2, 240f, row, 10)
        assertEquals(4, d.to)
        assertEquals(-row, QueueDrag.displacementFor(3, d, row), 0.01f)
        assertEquals(-row, QueueDrag.displacementFor(4, d, row), 0.01f)
        assertEquals(0f, QueueDrag.displacementFor(5, d, row), 0.01f)
    }

    @Test
    fun aLongDragCountsEveryRowItPasses() {
        // 260 px = 2.6 行，四舍五入到 3 格：2 → 5。
        val d = QueueDrag.resolve(2, 260f, row, 10)
        assertEquals(5, d.to)
        assertEquals(-row, QueueDrag.displacementFor(5, d, row), 0.01f)
    }

    @Test
    fun draggingUpwardsMovesThePassedRowsDown() {
        val d = QueueDrag.resolve(5, -260f, row, 10)
        assertEquals(2, d.to)
        // 落点 2 到起点 5 之间（含 2、3、4）全部下移一格。
        assertEquals(row, QueueDrag.displacementFor(2, d, row), 0.01f)
        assertEquals(row, QueueDrag.displacementFor(3, d, row), 0.01f)
        assertEquals(row, QueueDrag.displacementFor(4, d, row), 0.01f)
        assertEquals(0f, QueueDrag.displacementFor(5, d, row) - d.offsetPx, 0.01f)
    }

    @Test
    fun bothDirectionsNeedTheSameHalfRowToCommit() {
        // roundToInt 对 -0.5 取 0、对 +0.5 取 1，直接套会让向上拖慢半行。
        assertEquals(3, QueueDrag.resolve(2, 50f, row, 10).to)
        assertEquals(1, QueueDrag.resolve(2, -50f, row, 10).to)
    }

    @Test
    fun aDragThatHasNotClaimedASlotStillMovesTheRow() {
        val d = QueueDrag.resolve(2, 30f, row, 10)
        assertFalse(d.moved)
        assertEquals(30f, QueueDrag.displacementFor(2, d, row), 0.01f)
        assertEquals(0f, QueueDrag.displacementFor(3, d, row), 0.01f)
    }

    @Test
    fun aPositiveDragLeavesLaterRowsAlone() {
        val d = QueueDrag.resolve(2, 260f, row, 10)
        assertTrue(QueueDrag.displacementFor(1, d, row) == 0f)
        assertTrue(QueueDrag.displacementFor(0, d, row) == 0f)
    }
}
