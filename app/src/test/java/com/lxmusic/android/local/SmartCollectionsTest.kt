package com.lxmusic.android.local

import com.lxmusic.android.data.model.MusicInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SmartCollectionsTest {

    private val now = 1_700_000_000_000L
    private val day = 24L * 60L * 60L * 1000L

    private fun track(
        id: String,
        name: String = id,
        added: Long = 0L,
    ) = MusicInfo(
        id = id,
        source = MusicInfo.SOURCE_LOCAL,
        songmid = id,
        name = name,
        singer = "someone",
        dateAdded = added,
    )

    private fun play(count: Int, at: Long) = SmartCollections.PlayStat(count, at)

    // ---------------- 最近添加 ----------------

    @Test
    fun onlyTracksAddedInsideTheWindowAreRecent() {
        val tracks = listOf(
            track("inside", added = now - day),
            track("edge", added = now - SmartCollections.RECENT_DAYS * day),
            track("outside", added = now - 40 * day),
        )
        val shelf = SmartCollections.recentlyAdded(tracks, now)
        assertEquals(listOf("inside"), shelf.tracks.map { it.id })
    }

    @Test
    fun anUnknownDateAddedNeverCountsAsNew() {
        // 0 是"不知道"，不是 1970 年。整库升级上来时每一首都是 0，把它当"很新"
        // 会让整库进架，这一架就不再是"最近添加"。
        val tracks = listOf(track("unknown", added = 0L), track("new", added = now - day))
        val shelf = SmartCollections.recentlyAdded(tracks, now)
        assertEquals(listOf("new"), shelf.tracks.map { it.id })
    }

    @Test
    fun aFutureDateIsNotNewEither() {
        val tracks = listOf(track("future", added = now + day), track("new", added = now - day))
        val shelf = SmartCollections.recentlyAdded(tracks, now)
        assertEquals(listOf("new"), shelf.tracks.map { it.id })
    }

    @Test
    fun recentIsNewestFirst() {
        val tracks = listOf(
            track("older", added = now - 5 * day),
            track("newest", added = now - day),
            track("middle", added = now - 3 * day),
        )
        assertEquals(
            listOf("newest", "middle", "older"),
            SmartCollections.recentlyAdded(tracks, now).tracks.map { it.id },
        )
    }

    @Test
    fun theShelfIsCapped() {
        val tracks = (1..40).map { track("t$it", added = now - it * 1000L) }
        assertEquals(
            SmartCollections.SHELF_SIZE,
            SmartCollections.recentlyAdded(tracks, now).count,
        )
    }

    // ---------------- 最常播放 ----------------

    @Test
    fun mostPlayedSortsByCountThenByRecency() {
        val tracks = listOf(track("a"), track("b"), track("c"))
        val played = mapOf(
            "a" to play(3, now - 10 * day),
            "b" to play(3, now - day),
            "c" to play(9, now - 20 * day),
        )
        assertEquals(
            listOf("c", "b", "a"),
            SmartCollections.mostPlayed(tracks, played).tracks.map { it.id },
        )
    }

    @Test
    fun aTrackWithZeroPlaysIsNotMostPlayed() {
        val tracks = listOf(track("a"), track("b"))
        val played = mapOf("a" to play(0, now), "b" to play(1, now))
        assertEquals(listOf("b"), SmartCollections.mostPlayed(tracks, played).tracks.map { it.id })
    }

    @Test
    fun aPlayedTrackMissingFromTheLibraryIsIgnored() {
        val tracks = listOf(track("a"))
        val played = mapOf("a" to play(1, now), "gone" to play(99, now))
        assertEquals(listOf("a"), SmartCollections.mostPlayed(tracks, played).tracks.map { it.id })
    }

    // ---------------- 从未播放 ----------------

    @Test
    fun nothingHasBeenPlayedMeansNoUnplayedShelf() {
        // 一首都还没播过时，"从未播放"等于整个库，什么都不说明。
        val tracks = listOf(track("a"), track("b"))
        assertTrue(SmartCollections.neverPlayed(tracks, emptyMap()).isEmpty)
    }

    @Test
    fun unplayedIsEverythingThePlayLogHasNeverMentioned() {
        val tracks = listOf(track("played"), track("silent"), track("quiet"))
        val played = mapOf("played" to play(1, now))
        assertEquals(
            listOf("silent", "quiet"),
            SmartCollections.neverPlayed(tracks, played).tracks.map { it.id },
        )
    }

    // ---------------- 很久没听 ----------------

    @Test
    fun longForgottenIsOlderThanTheStaleWindow() {
        val tracks = listOf(track("recent"), track("stale"), track("ancient"))
        val played = mapOf(
            "recent" to play(1, now - day),
            "stale" to play(1, now - 120 * day),
            "ancient" to play(1, now - 400 * day),
        )
        assertEquals(
            listOf("ancient", "stale"),
            SmartCollections.longForgotten(tracks, played, now).tracks.map { it.id },
        )
    }

    @Test
    fun aZeroTimestampNeverLooksForgotten() {
        // lastPlayedAt=0 表示不知道，排进"很久没听"就是凭空的断言。
        val tracks = listOf(track("unknown"))
        val played = mapOf("unknown" to play(1, 0L))
        assertTrue(SmartCollections.longForgotten(tracks, played, now).isEmpty)
    }

    // ---------------- build ----------------

    @Test
    fun buildKeepsAFixedOrderAndDropsEmptyShelves() {
        val tracks = listOf(
            track("new", added = now - day),
            track("known", added = now - 400 * day),
        )
        val played = mapOf("known" to play(2, now - 200 * day))
        val shelves = SmartCollections.build(tracks, played, now)
        // 四架都在：new 是最近添加且从未播放，known 播过但很久没听。
        assertEquals(listOf("recent", "top", "unplayed", "stale"), shelves.map { it.key })
    }

    @Test
    fun buildOnAnEmptyLibraryReturnsNothing() {
        assertTrue(SmartCollections.build(emptyList(), emptyMap(), now).isEmpty())
    }

    @Test
    fun aShelfCarriesItsOwnHeadline() {
        val tracks = listOf(track("a", added = now - day))
        val shelf = SmartCollections.recentlyAdded(tracks, now)
        assertEquals("最近添加", shelf.title)
        assertEquals(1, shelf.count)
        assertTrue("30" in shelf.subtitle)
    }

    @Test
    fun anEmptyShelfSaysSo() {
        val shelf = SmartCollections.recentlyAdded(emptyList(), now)
        assertTrue(shelf.isEmpty)
        assertEquals(0, shelf.count)
    }

    @Test
    fun aTrackCanBeOnSeveralShelvesAtOnce() {
        // 同一首歌既可能是"最近添加"也可能是"最常播放"——四个架子是四种看法，
        // 不是四个互斥的抽屉。这里只锁住"没有被去重"这个事实。
        val tracks = listOf(track("both", added = now - day))
        val played = mapOf("both" to play(5, now - day))
        val shelves = SmartCollections.build(tracks, played, now)
        assertEquals(2, shelves.count { shelf -> shelf.tracks.any { it.id == "both" } })
    }
}
