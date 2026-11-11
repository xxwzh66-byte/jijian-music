package com.lxmusic.android.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * V1.0.1: the random draw itself.
 *
 * The order of the playlist is no longer shuffled up front - each decision asks
 * [RandomShuffle.pickOther] for one index. Two properties are load-bearing:
 *
 * - the answer is always a real index in `0 until size`, never -1 while the
 *   queue has two or more tracks, because media3 reads -1 as "no next item" and
 *   stops playback for good (the v5.3.1 stall);
 * - the draw is uniform and never returns an excluded index, so "next" changes
 *   the track instead of replaying it.
 */
class RandomShuffleTest {

    @Test
    fun pickOtherAlwaysLandsInRange() {
        for (size in 1..24) {
            for (seed in 0 until 20) {
                val pick = RandomShuffle.pickOther(size, intArrayOf(), Random(seed))
                assertTrue("size=$size seed=$seed gave $pick", pick in 0 until size)
            }
        }
    }

    @Test
    fun pickOtherNeverReturnsAnExcludedIndex() {
        for (seed in 0 until 200) {
            val pick = RandomShuffle.pickOther(6, intArrayOf(2), Random(seed))
            assertNotEquals("excluded index came back with seed $seed", 2, pick)
        }
        for (seed in 0 until 200) {
            val pick = RandomShuffle.pickOther(6, intArrayOf(1, 4), Random(seed))
            assertTrue("excluded index came back: $pick (seed $seed)", pick !in intArrayOf(1, 4))
        }
    }

    @Test
    fun pickOtherReachesEveryAllowedIndex() {
        // Uniformity is the whole point of the version: a draw that only ever
        // lands on two of the four candidates is not random playback.
        val size = 5
        val hits = IntArray(size)
        for (seed in 0 until 1_000) hits[RandomShuffle.pickOther(size, intArrayOf(3), Random(seed))]++
        val expected = 250
        for (index in 0 until size) {
            if (index == 3) {
                assertEquals("the excluded index must never be drawn", 0, hits[index])
            } else {
                assertTrue("index $index off the uniform band: ${hits.toList()}", hits[index] in (expected - 70)..(expected + 70))
            }
        }
    }

    @Test
    fun anImpossibleExclusionStillReturnsAnIndexInsteadOfLooping() {
        // size == exclude.size: every candidate is banned. Looping until a free
        // one appears would hang the playback thread, so the draw must degrade
        // to "one of them" rather than spin.
        val pick = RandomShuffle.pickOther(1, intArrayOf(0), Random(3))
        assertTrue("a fully excluded draw must still answer", pick == 0)
        val two = RandomShuffle.pickOther(2, intArrayOf(0, 1), Random(9))
        assertTrue("a fully excluded draw must still answer", two in 0 until 2)
    }

    @Test
    fun degenerateSizesAnswerMinusOne() {
        assertEquals(-1, RandomShuffle.pickOther(0, intArrayOf(), Random(1)))
        assertEquals(-1, RandomShuffle.pickOther(-3, intArrayOf(), Random(1)))
    }

    @Test
    fun aTwoTrackQueueAlwaysDrawsTheOtherTrack() {
        for (seed in 0 until 100) {
            assertEquals("with two tracks the draw is forced", 1, RandomShuffle.pickOther(2, intArrayOf(0), Random(seed)))
            assertEquals(0, RandomShuffle.pickOther(2, intArrayOf(1), Random(seed)))
        }
    }

    /**
     * V1.4.2: the exclusion array used to be counted, not deduplicated.
     *
     * `previousIndex` passes `intArrayOf(current, lastTrack)`. When the queue is
     * being edited, `lastTrack` can equal `current`, so the same index arrives
     * twice. The old candidate size was `size - exclude.count { ... }`, which on
     * a two-track queue became `2 - 2 = 0` and on a stale queue went negative -
     * `IntArray(negative)` throws `NegativeArraySizeException` on the playback
     * thread. Deduplicating first is the fix; this test pins it.
     */
    @Test
    fun duplicateExclusionsDoNotBlowUpTheCandidateSize() {
        for (seed in 0 until 200) {
            assertEquals(
                "the same index twice must not turn into a negative array size",
                1,
                RandomShuffle.pickOther(2, intArrayOf(0, 0), Random(seed)),
            )
            assertEquals(0, RandomShuffle.pickOther(2, intArrayOf(1, 1), Random(seed)))
            assertTrue(
                "a duplicate that is also out of range must be ignored",
                RandomShuffle.pickOther(2, intArrayOf(0, 0, 99), Random(seed)) == 1,
            )
        }
    }

    /**
     * V1.4.2 explicit decision: there is no "recently played" window.
     *
     * Only the current track (and, for "previous", the track that just finished)
     * is excluded. Every other index stays reachable no matter how recently it
     * played - the user asked for a random mode that allows the same song to come
     * back within a few presses. A window would silently override that.
     */
    @Test
    fun anIndexThatJustPlayedIsStillReachable() {
        val size = 4
        val hits = IntArray(size)
        for (seed in 0 until 2_000) {
            hits[RandomShuffle.pickOther(size, intArrayOf(0), Random(seed))]++
        }
        assertTrue("index 1 must be drawable immediately after playing", hits[1] > 0)
        assertTrue("index 2 must be drawable immediately after playing", hits[2] > 0)
        assertTrue("index 3 must be drawable immediately after playing", hits[3] > 0)
        assertEquals("the current track stays excluded", 0, hits[0])
    }

    @Test
    fun outOfRangeExclusionsAreIgnoredRatherThanReserved() {
        // A stale index from a longer queue must not eat a slot: `size` is 3 and
        // index 7 does not exist, so the draw stays uniform over 0..2.
        val hits = IntArray(3)
        for (seed in 0 until 2_000) {
            hits[RandomShuffle.pickOther(3, intArrayOf(7), Random(seed))]++
        }
        for (index in 0..2) {
            assertTrue("index $index must remain reachable", hits[index] > 0)
        }
    }
}
