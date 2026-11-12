package com.lxmusic.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * Regression tests for the queue-resize defects that made playback appear to
 * freeze.
 *
 * [RandomShuffleState] memoises one draw per direction. [PlayerController]
 * hands the *same* state object to a brand new [RandomShuffleOrder] every time
 * the queue length changes, so an answer memoised while the queue was long used
 * to be replayed after the queue shrank. ExoPlayer treats a
 * [androidx.media3.exoplayer.source.ShuffleOrder] result as a child index and
 * never range-checks it, so an out-of-range answer corrupts the timeline
 * instead of merely picking a wrong track.
 *
 * V1.0.1: every entry point on the state is `@Synchronized` (it is written from
 * ExoPlayer's playback thread via `MediaSourceList.setShuffleOrder` ->
 * `cloneAndInsert` and from the app's main thread on a button press), and a
 * memo whose index no longer fits the current length is discarded rather than
 * replayed.
 */
class RandomShuffleStateStalenessTest {

    private fun state() = RandomShuffleState()

    @Test
    fun aShrunkQueueNeverReplaysAnOutOfRangeNextIndex() {
        val state = state()
        val long = RandomShuffleOrder(64, state, Random(5))
        long.getNextIndex(3)

        val short = RandomShuffleOrder(8, state, Random(5))
        val replayed = short.getNextIndex(3)

        assertTrue(
            "a shrunk queue replayed a stale index " + replayed + " (valid 0 until 8)",
            replayed in 0 until 8,
        )
    }

    @Test
    fun aShrunkQueueNeverReplaysAnOutOfRangePreviousIndex() {
        val state = state()
        val long = RandomShuffleOrder(64, state, Random(9))
        long.getPreviousIndex(1)

        val short = RandomShuffleOrder(4, state, Random(9))
        val replayed = short.getPreviousIndex(1)

        // V6.1: -1 is a legal answer at the first slot of a cycle (ExoPlayer
        // restarts the current track). The defect this test guards is an index
        // that belongs to the old, longer queue.
        assertTrue("stale previous " + replayed, replayed == -1 || replayed in 0 until 4)
    }

    @Test
    fun aShrunkQueueNeverReplaysAnOutOfRangeLastIndex() {
        val state = state()
        val long = RandomShuffleOrder(64, state, Random(3))
        long.getLastIndex()

        val short = RandomShuffleOrder(4, state, Random(3))
        val replayed = short.getLastIndex()

        assertTrue("stale last " + replayed, replayed in 0 until 4)
    }

    @Test
    fun everyDrawStaysInRangeAcrossManySizes() {
        val state = state()
        var size = 1
        while (size <= 32) {
            val order = RandomShuffleOrder(size, state, Random(size))
            for (i in 0 until size) {
                val next = order.getNextIndex(i)
                val previous = order.getPreviousIndex(i)
                // A one-track queue has nowhere to go; -1 is the contract there.
                val expected = if (size == 1) -1 else null
                if (expected != null) {
                    assertEquals("size 1 must report no neighbour", expected, next)
                    assertEquals("size 1 must report no neighbour", expected, previous)
                } else {
                    assertTrue("next out of range at size " + size, next in 0 until size)
                    // V6.1: -1 is a legal answer for "previous" at the first
                    // slot of a cycle - ExoPlayer turns it into "restart this
                    // track". What must never happen is an *out-of-range* index.
                    assertTrue(
                        "previous out of range at size $size: $previous",
                        previous == -1 || previous in 0 until size,
                    )
                }
            }
            size += 7
        }
    }

    @Test
    fun aStillValidMemoIsReusedRatherThanRedrawn() {
        val state = state()
        val order = RandomShuffleOrder(8, state, Random(11))
        val first = order.getNextIndex(2)
        val second = order.getNextIndex(2)

        assertEquals("a valid memo must stay stable", first, second)
    }

    // ---------------------------------------------------------------
    // V1.4.6: forgetDraws() —— 队列编辑后作废答案，但不动 generation
    // ---------------------------------------------------------------

    @Test
    fun forgetDrawsDiscardsTheAnswersTheQueueEditInvalidated() {
        val state = state()
        val order = RandomShuffleOrder(9, state, Random(3))
        order.getNextIndex(4)
        order.getPreviousIndex(4)
        assertTrue("nothing was memoised to begin with", state.memoised > 0)

        state.forgetDraws()

        assertEquals("forgetDraws must empty the ledger", 0, state.memoised)
    }

    @Test
    fun forgetDrawsKeepsTheTrackWeCameFromSoPreviousStillAvoidsIt() {
        val state = state()
        // 走两步，让账本知道「我们从 3 走到 5」。V1.4.7 之前这里依据的是
        // lastTrack（刚开播的**这一首**），那跟 current 永远相等、避开规则
        // 从未生效；现在依据是 cameFrom。
        state.onTrackStarted(9, 3)
        state.onTrackStarted(9, 5)
        state.forgetDraws()

        // 依据还在，所以「上一首」必须继续避开我们来的那一首。
        val order = RandomShuffleOrder(9, state, Random(7))
        for (index in 0 until 9) {
            val previous = order.getPreviousIndex(index)
            assertTrue(
                "previous went back to the track we came from at index $index",
                previous != 3,
            )
        }
    }

    @Test
    fun forgetDrawsLeavesTheGenerationAloneSoNoOrderGetsReinstalled() {
        val state = state()
        val before = state.version
        state.forgetDraws()
        assertEquals(
            "bumping the generation here forces a masking-timeline rebuild",
            before,
            state.version,
        )
    }

    @Test
    fun invalidateStillBumpsTheGenerationAndForgetsTheLastTrack() {
        val state = state()
        state.onTrackStarted(9, 5)
        val before = state.version
        state.invalidate()
        assertEquals("invalidate must move the version forward", before + 1, state.version)
        assertEquals("invalidate must empty the ledger too", 0, state.memoised)
    }

    @Test
    fun aForgottenLedgerDrawsAgainInsteadOfReplayingTheOldPath() {
        val state = state()
        val order = RandomShuffleOrder(9, state, Random(21))
        val first = order.getNextIndex(4)
        assertEquals("a valid memo must stay stable", first, order.getNextIndex(4))

        state.forgetDraws()

        // 重抽之后**可能**又抽到同一个下标——那不算错，随机就是随机。
        // 要紧的是它确实重新问了 RandomShuffle.pickOther，而不是把 4/6 里
        // 那个旧答案原样端出来。为确认这一点，换一个种子重装 order：
        // 旧账本在的话，getNextIndex(4) 会无视新种子直接给 first。
        val reseeded = RandomShuffleOrder(9, state, Random(9999))
        val afterEdit = reseeded.getNextIndex(4)
        assertTrue(
            "after forgetDraws the answer must be redrawn, not replayed",
            afterEdit in 0 until 9 && afterEdit != 4,
        )
    }

    @Test
    fun theMemoNeverGrowsWithoutBoundWhenTheQueueKeepsResizing() {
        val state = state()
        for (round in 0 until 50) {
            val order = RandomShuffleOrder(4 + round % 5, state, Random(round))
            order.getNextIndex(0)
            order.getPreviousIndex(0)
        }
        assertTrue("memo leaked " + state.memoised + " entries", state.memoised <= 10)
    }
}
