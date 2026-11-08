package com.lxmusic.android.data.stats

import com.lxmusic.android.data.model.MusicInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

/**
 * The yearly wrap-up is one more pure function over the event table, so it is
 * pinned to a fixed year and zone like the rest of the statistics.
 */
class AnnualReportTest {

    private val tz: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    private fun at(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long {
        val c = Calendar.getInstance(tz)
        c.clear()
        c.set(y, mo - 1, d, h, mi, 0)
        return c.timeInMillis
    }

    private fun track(id: String, singer: String = "Singer", seconds: Int = 200) =
        MusicInfo(
            id = id,
            source = MusicInfo.SOURCE_LOCAL,
            songmid = id,
            name = "Track $id",
            singer = singer,
            intervalSeconds = seconds,
        )

    private fun record(id: String, whenMs: Long, singer: String = "Singer", seconds: Int = 200) =
        PlayRecord(track(id, singer, seconds), whenMs)

    @Test
    fun yearStartIsMidnightOnTheFirstOfJanuary() {
        assertEquals(at(2026, 1, 1, 0, 0), PlayStats.yearStart(2026, tz))
    }

    @Test
    fun currentYearFollowsTheClock() {
        assertEquals(2026, PlayStats.currentYear(at(2026, 10, 14, 15, 30), tz))
    }

    @Test
    fun emptyYearReportsNothingRatherThanFailing() {
        val report = PlayStats.annualReport(emptyList(), 2026, tz)

        assertEquals(2026, report.year)
        assertTrue(report.isEmpty)
        assertEquals(0, report.plays)
        assertEquals("0 分钟", report.durationText)
        assertNull(report.topArtist)
        assertNull(report.topTrack)
    }

    @Test
    fun eventsFromOtherYearsAreIgnored() {
        val records = listOf(
            record("a", at(2025, 6, 1, 12, 0)),
            record("b", at(2026, 3, 1, 12, 0)),
            record("c", at(2027, 1, 2, 12, 0)),
        )

        val report = PlayStats.annualReport(records, 2026, tz)

        assertEquals(1, report.plays)
        assertEquals(1, report.distinctTracks)
    }

    @Test
    fun durationSumsEveryPlayNotJustDistinctTracks() {
        // Three plays of a 200 s track: the year is 10 minutes long, not 3:20.
        val records = listOf(
            record("a", at(2026, 2, 1, 9, 0), seconds = 200),
            record("a", at(2026, 2, 2, 9, 0), seconds = 200),
            record("a", at(2026, 2, 3, 9, 0), seconds = 200),
        )

        val report = PlayStats.annualReport(records, 2026, tz)

        assertEquals(3, report.plays)
        assertEquals(600L, report.seconds)
        assertEquals(1, report.distinctTracks)
        assertEquals("10 分钟", report.durationText)
    }

    @Test
    fun durationTextShowsHoursOnlyWhenThereAreAny() {
        val short = PlayStats.annualReport(listOf(record("a", at(2026, 2, 1, 9, 0), seconds = 90)), 2026, tz)
        assertEquals("1 分钟", short.durationText)

        val long = PlayStats.annualReport(
            List(100) { record("a", at(2026, 2, 1, 9, 0), seconds = 400) },
            2026,
            tz,
        )
        assertEquals("11 小时 6 分", long.durationText)
    }

    @Test
    fun topArtistIsTheMostPlayedOne() {
        val records = listOf(
            record("a", at(2026, 2, 1, 9, 0), singer = "Miles"),
            record("b", at(2026, 2, 2, 9, 0), singer = "Miles"),
            record("c", at(2026, 2, 3, 9, 0), singer = "Coltrane"),
        )

        val report = PlayStats.annualReport(records, 2026, tz)

        assertEquals("Miles", report.topArtist)
        assertEquals(2, report.topArtistPlays)
    }

    @Test
    fun blankSingerCollapsesIntoUnknown() {
        val records = listOf(
            record("a", at(2026, 2, 1, 9, 0), singer = ""),
            record("b", at(2026, 2, 2, 9, 0), singer = ""),
        )

        assertEquals("未知歌手", PlayStats.annualReport(records, 2026, tz).topArtist)
    }

    @Test
    fun topTrackIsTheMostRepeatedOne() {
        val records = listOf(
            record("a", at(2026, 2, 1, 9, 0)),
            record("a", at(2026, 2, 2, 9, 0)),
            record("b", at(2026, 2, 3, 9, 0)),
        )

        val report = PlayStats.annualReport(records, 2026, tz)

        assertEquals("a", report.topTrack?.id)
        assertEquals(2, report.topTrackPlays)
    }

    @Test
    fun latestPlayAtTracksTheLastEventOfTheYear() {
        val last = at(2026, 12, 31, 23, 50)
        val records = listOf(
            record("a", at(2026, 1, 1, 0, 5)),
            record("b", last),
            record("c", at(2026, 6, 1, 12, 0)),
        )

        assertEquals(last, PlayStats.annualReport(records, 2026, tz).latestPlayAt)
    }
}
