package com.lxmusic.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Regression tests for the ExoPlayer clone protocol.
 *
 * `MediaSourceList.setShuffleOrder` builds *candidate* orders:
 *
 *   if (shuffleOrder.getLength() != holderCount) {
 *       shuffleOrder = shuffleOrder.cloneAndClear().cloneAndInsert(0, holderCount);
 *   }
 *   this.shuffleOrder = shuffleOrder;
 *
 * and the result can still be rejected by the length assertion in
 * `ExoPlayerImpl.setShuffleOrder`. The order a clone came from therefore stays
 * installed and keeps answering the notification, lockscreen and bluetooth
 * buttons. A clone that invalidated the shared state used to wipe the live
 * order's memo as a side effect of building a candidate: the very next
 * `getNextIndex` on the installed order returned a different track, and the
 * speculative path could also shift the answer the app had already shown the
 * user.
 *
 * V1.4.3: the clone and its parent share one memo now. A snapshot was worse in
 * the other direction - the clone ExoPlayer kept never heard `onTrackStarted`,
 * so it kept handing back the answer it had drawn before the last track change.
 */
class RandomShuffleCloneTest {

    private fun state() = RandomShuffleState()

    @Test
    fun aCloneMustNotBlindTheOrderItWasClonedFrom() {
        val state = state()
        val installed = RandomShuffleOrder(8, state, Random(4))
        val before = installed.getNextIndex(3)

        installed.cloneAndInsert(0, 1)

        assertEquals(
            "building a candidate order must not rewrite the live order",
            before,
            installed.getNextIndex(3),
        )
    }

    @Test
    fun cloneAndRemoveMustNotBlindTheOrderItWasClonedFrom() {
        val state = state()
        val installed = RandomShuffleOrder(8, state, Random(6))
        val before = installed.getPreviousIndex(2)

        installed.cloneAndRemove(0, 1)

        assertEquals(
            "a speculative remove must not rewrite the live order",
            before,
            installed.getPreviousIndex(2),
        )
    }

    @Test
    fun cloneAndClearMustNotBlindTheOrderItWasClonedFrom() {
        val state = state()
        val installed = RandomShuffleOrder(8, state, Random(5))
        val before = installed.getNextIndex(1)

        installed.cloneAndClear()

        assertEquals(
            "clearing a candidate must not rewrite the live order",
            before,
            installed.getNextIndex(1),
        )
    }

    @Test
    fun aCloneAtTheSameLengthInheritsItsParentsPlaylist() {
        // A clone is a snapshot: at equal length it walks the same order as its
        // parent, at a different length it draws its own (the old playlist no
        // longer describes the queue) - but either way it stays usable.
        val state = state()
        val parent = RandomShuffleOrder(6, state, Random(8))
        val parentAnswer = parent.getNextIndex(1)

        val clone = parent.cloneAndInsert(0, 0)

        assertEquals("the playlist must survive a clone at equal length", parentAnswer, clone.getNextIndex(1))
        assertEquals(6, clone.getLength())
        assertNotEquals(parent.getLastIndex(), clone.getFirstIndex())
    }

    @Test
    fun theLengthRepairPathYieldsAUsableOrder() {
        // The speculative path MediaSourceList uses when the installed order
        // disagrees with the holder count: the repaired order must be a valid
        // playlist for the *new* size, never -1 while a track remains.
        val state = state()
        val wrong = RandomShuffleOrder(3, state, Random(2))

        val repaired = wrong.cloneAndClear().cloneAndInsert(0, 8)

        assertEquals("repair must reach the holder count", 8, repaired.getLength())
        assertTrue(repaired.getFirstIndex() in 0 until 8)
        assertTrue(repaired.getLastIndex() in 0 until 8)
        for (i in 0 until 8) {
            assertTrue("repaired order out of range at " + i, repaired.getNextIndex(i) in 0 until 8)
            assertNotEquals(i, repaired.getNextIndex(i))
        }
    }

    @Test
    fun aRepairedOrderWalksItsWholeQueue() {
        // V1.0.1: the walk is a fresh draw per step, so the property is no longer
        // "covers every track once" - it is "never stalls and can reach every
        // track". A repaired order that covered only part of the queue would be
        // the old "随机播放只在几首之间跳" bug under a new name.
        val state = state()
        val repaired = RandomShuffleOrder(3, state, Random(5)).cloneAndClear().cloneAndInsert(0, 8)
        assertEquals("repair must reach the holder count", 8, repaired.getLength())
        val reached = HashSet<Int>()
        var cursor = repaired.getFirstIndex()
        repeat(400) {
            val next = repaired.getNextIndex(cursor)
            assertTrue("a repaired order must always have a successor", next in 0 until 8)
            assertNotEquals("the successor must change the track", cursor, next)
            reached.add(next)
            cursor = next
            state.onTrackStarted(8, cursor)
        }
        assertEquals("the repaired order must be able to reach every track", 8, reached.size)
    }

    @Test
    fun aCloneHearsTheTrackStartedToo() {
        // V1.4.3: the clone that ExoPlayer keeps is the one answering the
        // notification. It must go stale at the same moment the app does,
        // otherwise the next press replays the answer drawn for the previous
        // track - which reads as "shuffle is on, the next track is always the
        // same one".
        val state = state()
        val installed = RandomShuffleOrder(8, state, Random(3))
        val clone = installed.cloneAndInsert(0, 0)

        val before = clone.getNextIndex(0)
        state.onTrackStarted(8, before)

        val after = clone.getNextIndex(before)
        assertTrue("a clone must draw again after a track starts", after in 0 until 8)
        assertNotEquals("the next track must not replay the old answer", before, after)
    }

    @Test
    fun everyQuestionGetsItsOwnSlot() {
        // media3 asks with different `current` indices inside one decision:
        // the notification wants the current track's successor while the
        // MediaPeriodQueue previews the ones after it. A single memo slot let
        // the second question overwrite the first answer.
        val state = state()
        val order = RandomShuffleOrder(8, state, Random(11))

        val fromZero = order.getNextIndex(0)
        val fromThree = order.getNextIndex(3)

        assertEquals("the first question must stay answered", fromZero, order.getNextIndex(0))
        assertEquals("the second question must stay answered", fromThree, order.getNextIndex(3))
        assertTrue(state.memoised() >= 2)
    }

    @Test
    fun invalidateBlindsTheCloneAsWell() {
        val state = state()
        val installed = RandomShuffleOrder(8, state, Random(9))
        val clone = installed.cloneAndInsert(0, 0)

        clone.getNextIndex(2)
        state.invalidate()

        assertEquals("invalidate must reach the shared memo", 0, state.memoised())
    }

    /** The memo of the live state, which the clones must never touch. */
    private fun RandomShuffleState.memoised(): Int = memoised
}
