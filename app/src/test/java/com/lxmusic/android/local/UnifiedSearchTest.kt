package com.lxmusic.android.local

import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.data.repo.PlaylistWithTracks
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UnifiedSearchTest {

    private fun track(
        id: String,
        name: String = id,
        singer: String = "someone",
        album: String = "somewhere",
    ) = MusicInfo(
        id = id,
        source = MusicInfo.SOURCE_LOCAL,
        songmid = id,
        name = name,
        singer = singer,
        albumName = album,
    )

    private fun playlist(id: Long, name: String, tracks: List<MusicInfo>) =
        PlaylistWithTracks(id = id, name = name, coverUrl = null, tracks = tracks, createdAt = 0L)

    // ---------------- 空查询 ----------------

    @Test
    fun aBlankQueryFindsNothingButKeepsTheQueryText() {
        val results = UnifiedSearch.run("   ", listOf(track("a")), emptyList(), emptyList())
        assertTrue(results.isEmpty)
        assertEquals("   ", results.query)
        assertEquals(0, results.totalCount)
    }

    // ---------------- 歌曲 ----------------

    @Test
    fun theTrackSectionIsWhateverTheCallerFiltered() {
        // 这一层不自己判断"什么算命中"：库索引负责匹配与排序，这里只负责
        // 把它拿到的列表原样放进结果。
        val hit = track("1", name = "夜空中最亮的星")
        val results = UnifiedSearch.run("星空", listOf(hit), emptyList(), emptyList())
        assertEquals(listOf("1"), results.tracks.map { it.id })
        assertEquals("星空", results.query)
    }

    @Test
    fun theQueryIsTrimmedButTheFieldKeepsWhatWasTyped() {
        val hit = track("1", name = "abc")
        val results = UnifiedSearch.run("  abc  ", listOf(hit), emptyList(), emptyList())
        assertEquals(1, results.tracks.size)
        // query 留着原文：输入框要显示用户打的字，不是被裁过的版本
        assertEquals("  abc  ", results.query)
    }

    @Test
    fun matchingIgnoresCase() {
        val track = track("1", name = "Let It Be")
        val fav = track("2", name = "LET IT DOWN")
        val results = UnifiedSearch.run("LET IT", emptyList(), listOf(track, fav), emptyList())
        assertEquals(listOf("1", "2"), results.favorites.map { it.id })
    }

    // ---------------- 收藏 ----------------

    @Test
    fun aFavoriteAlreadyInTheTrackHitsIsNotRepeated() {
        // 同一首歌在一页上出现两次，读起来像程序出错。
        val both = track("1", name = "x")
        val results = UnifiedSearch.run("x", listOf(both), listOf(both), emptyList())
        assertEquals(1, results.tracks.size)
        assertTrue(results.favorites.isEmpty())
    }

    @Test
    fun aFavoriteOutsideTheTrackHitsIsListedOnItsOwn() {
        val local = track("1", name = "hit")
        val fav = track("2", name = "hit too")
        val results = UnifiedSearch.run("hit", listOf(local), listOf(fav), emptyList())
        assertEquals(listOf("2"), results.favorites.map { it.id })
    }

    // ---------------- 专辑 / 歌手 ----------------

    @Test
    fun albumsComeFromTheTracksTheCallerAlreadyFiltered() {
        // 这一层不再自己判断"什么算命中"：入参是什么，分组就从什么里分。
        // 已经过滤好的两首 Red 交给它，蓝色那张就不该出现在结果里。
        val red1 = track("1", album = "Red")
        val red2 = track("2", album = "Red")
        val matched = listOf(red1, red2)
        val results = UnifiedSearch.run("red", matched, emptyList(), emptyList())
        assertEquals(1, results.albums.size)
        assertEquals("Red", results.albums.first().title)
        assertEquals(2, results.albums.first().count)
    }

    @Test
    fun albumsAndArtistsAreCappedAtSix() {
        val tracks = (1..10).map { track("t$it", album = "A$it", singer = "S$it") }
        val results = UnifiedSearch.run("t", tracks, emptyList(), emptyList())
        assertTrue(results.albums.size <= UnifiedSearch.MAX_GROUPS)
        assertTrue(results.artists.size <= UnifiedSearch.MAX_GROUPS)
    }

    @Test
    fun anAlbumIsFoundThroughTheArtistNameToo() {
        val tracks = listOf(track("1", name = "歌", singer = "周杰伦", album = "叶惠美"))
        val results = UnifiedSearch.run("周杰伦", tracks, emptyList(), emptyList())
        assertEquals(1, results.tracks.size)
        assertEquals("叶惠美", results.albums.firstOrNull()?.title)
        assertEquals("周杰伦", results.artists.firstOrNull()?.title)
    }

    // ---------------- 歌单 ----------------

    @Test
    fun aPlaylistMatchesByName() {
        val tracks = listOf(track("1", name = "a"))
        val lists = listOf(playlist(1L, "深夜循环", emptyList()), playlist(2L, "通勤", emptyList()))
        val results = UnifiedSearch.run("深夜", tracks, emptyList(), lists)
        assertEquals(listOf("深夜循环"), results.playlists.map { it.name })
    }

    @Test
    fun aPlaylistMatchesWhenItContainsAMatchingTrack() {
        val hit = track("1", name = "晴天", singer = "周杰伦")
        val lists = listOf(playlist(1L, "我的歌单", listOf(hit)))
        // 曲目本身不在本地库里（没授权那个文件夹），但歌单里有
        val results = UnifiedSearch.run("晴天", emptyList(), emptyList(), lists)
        assertEquals(listOf("我的歌单"), results.playlists.map { it.name })
    }

    @Test
    fun playlistsAreCappedAtSix() {
        val lists = (1..10).map { playlist(it.toLong(), "同款$it", emptyList()) }
        val results = UnifiedSearch.run("同款", emptyList(), emptyList(), lists)
        assertEquals(UnifiedSearch.MAX_PLAYLISTS, results.playlists.size)
    }

    // ---------------- isEmpty / totalCount ----------------

    @Test
    fun aQueryThatMatchesNothingIsEmpty() {
        val results = UnifiedSearch.run("zzz", emptyList(), emptyList(), emptyList())
        assertTrue(results.isEmpty)
        assertEquals(0, results.totalCount)
    }

    @Test
    fun aQueryWithOnlyPlaylistHitsIsNotEmpty() {
        val lists = listOf(playlist(1L, "zzz", emptyList()))
        val results = UnifiedSearch.run("zzz", emptyList(), emptyList(), lists)
        assertFalse(results.isEmpty)
        assertEquals(1, results.totalCount)
    }

    @Test
    fun totalCountAddsUpEverySection() {
        val both = track("1", name = "hit", album = "A", singer = "S")
        val fav = track("2", name = "hit fav", album = "A", singer = "S")
        val results = UnifiedSearch.run(
            "hit",
            listOf(both),
            listOf(fav),
            listOf(playlist(1L, "hit list", emptyList())),
        )
        // 1 首歌 + 1 个收藏 + 1 张专辑 + 1 位歌手 + 1 个歌单
        assertEquals(5, results.totalCount)
    }
}
