package com.lxmusic.android.player

import com.lxmusic.android.data.model.MusicInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Tests for the queue screen header numbers. */
class QueueSummaryTest {

    private fun track(id: String, seconds: Int) = MusicInfo(
        id = id,
        source = MusicInfo.SOURCE_LOCAL,
        songmid = id,
        name = "track $id",
        singer = "singer",
        intervalSeconds = seconds,
    )

    // 60, 120, 180 seconds.
    private val queue = listOf(track("a", 60), track("b", 120), track("c", 180))

    @Test
    fun anEmptyQueueReportsNothing() {
        val s = QueueSummary.of(emptyList(), -1)
        assertTrue(s.isEmpty)
        assertEquals("", s.headerText())
        assertEquals(0, s.position)
    }

    @Test
    fun thePositionIsOneBasedAndTheFirstTrackHasTwoLeft() {
        val s = QueueSummary.of(queue, index = 0)
        assertEquals(1, s.position)
        assertEquals(2, s.remaining)
        // 时长含当前曲的剩余，所以是整队 60+120+180。
        assertEquals(360, s.remainingSeconds)
    }

    @Test
    fun theLastTrackHasNothingRemaining() {
        val s = QueueSummary.of(queue, index = 2)
        assertEquals(3, s.position)
        assertEquals(0, s.remaining)
        assertTrue(s.headerText().endsWith("最后一首"))
    }

    @Test
    fun elapsedTimeIsSubtractedFromTheCurrentTrack() {
        // 30 s into a 60 s track: 30 + 120 + 180.
        val s = QueueSummary.of(queue, index = 0, positionMs = 30_000)
        assertEquals(330, s.remainingSeconds)
    }

    @Test
    fun aStalePositionCannotEatIntoTheNextTrack() {
        // 999 s of position on a 60 s track clamps to the track's own length.
        val s = QueueSummary.of(queue, index = 0, positionMs = 999_000)
        assertEquals(300, s.remainingSeconds)
    }

    @Test
    fun aNegativePositionIsTreatedAsZero() {
        val s = QueueSummary.of(queue, index = 0, positionMs = -5_000)
        assertEquals(360, s.remainingSeconds)
    }

    @Test
    fun anIndexOfMinusOneWithANonEmptyQueueCountsTheWholeQueue() {
        // Nothing playing (e.g. right after clear) but the list is still there.
        val s = QueueSummary.of(queue, index = -1)
        assertEquals(0, s.position)
        assertEquals(360, s.remainingSeconds)
        assertEquals(3, s.total)
    }

    @Test
    fun theHeaderNamesPositionAndRemainingTime() {
        // index 1 未播放：120+180 = 300 s = 5 分。
        val s = QueueSummary.of(queue, index = 1, positionMs = 0)
        assertEquals("2/3 · 剩 5 分", s.headerText())
    }

    @Test
    fun theHeaderDoesNotPutACountNextToADuration() {
        // 首数不含当前曲、时长含当前曲剩余，并排会自相矛盾，所以只留时长。
        val s = QueueSummary.of(queue, index = 1, positionMs = 60_000)
        assertFalse(s.headerText().contains("首"))
    }

    @Test
    fun durationsAreRoundedToTheNearestMinute() {
        assertEquals("1 分", QueueSummary.durationText(45))
        assertEquals("2 分", QueueSummary.durationText(100))
        assertEquals("0 分", QueueSummary.durationText(0))
        assertEquals("0 分", QueueSummary.durationText(-10))
    }

    @Test
    fun durationsCrossIntoHours() {
        // 3570 s = 59.5 分，四舍五入正好跨进一小时。
        assertEquals("1 小时", QueueSummary.durationText(3570))
        assertEquals("1 小时", QueueSummary.durationText(3600))
        assertEquals("1 小时 12 分", QueueSummary.durationText(4320))
    }

    @Test
    fun aSingleTrackQueueSaysItIsTheLastOne() {
        val s = QueueSummary.of(listOf(track("only", 200)), index = 0)
        assertFalse(s.isEmpty)
        assertEquals("1/1 · 最后一首", s.headerText())
    }
}
