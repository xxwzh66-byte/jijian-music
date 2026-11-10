package com.lxmusic.android.local

import com.lxmusic.android.data.model.MusicInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for the pre-lowered search index.
 *
 * The failure mode this guards against is a *behaviour* change: the index must
 * answer exactly what [LocalLibrary.search] answered, only cheaper. Every test
 * below therefore pins a case where a naive "just lowercase the haystack" or
 * "just lowercase the needle" implementation quietly differs.
 */
class LocalIndexTest {

    private fun track(
        id: String,
        name: String,
        singer: String = "未知歌手",
        album: String = "",
        seconds: Int = 180,
    ) = MusicInfo(
        id = id,
        source = MusicInfo.SOURCE_LOCAL,
        songmid = id,
        name = name,
        singer = singer,
        albumName = album,
        intervalSeconds = seconds,
    )

    private val library = listOf(
        track("1", "Blue Train", "John Coltrane", "Blue Train", 500),
        track("2", "so what", "Miles Davis", "Kind of Blue", 545),
        track("3", "夜曲", "周杰伦", "十一月的萧邦", 227),
        track("4", "Autumn Leaves", "Bill Evans", "Portrait", 400),
    )

    @Test
    fun aBlankQueryReturnsEverythingInSortOrder() {
        val index = LocalIndex.of(library, LocalSort.TITLE_ASC)
        assertEquals(4, index.search("").size)
        assertEquals(4, index.search("   ").size)
        // TITLE_ASC lowercases before comparing, so "Autumn" < "Blue" < "so" < "夜曲".
        assertEquals(listOf("4", "1", "2", "3"), index.search("").map { it.id })
    }

    @Test
    fun theQueryIsMatchedCaseInsensitively() {
        val index = LocalIndex.of(library, LocalSort.TITLE_ASC)
        assertEquals(listOf("2"), index.search("SO WHAT").map { it.id })
        assertEquals(listOf("2"), index.search("So WhAt").map { it.id })
    }

    @Test
    fun theTrackNameArtistAndAlbumAreAllSearchable() {
        val index = LocalIndex.of(library, LocalSort.TITLE_ASC)
        assertEquals(listOf("3"), index.search("夜曲").map { it.id })
        assertEquals(listOf("3"), index.search("周杰伦").map { it.id })
        assertEquals(listOf("3"), index.search("萧邦").map { it.id })
    }

    @Test
    fun theIndexAgreesWithThePlainFilterForEveryQuery() {
        // The whole point: same answers as before, just pre-lowered.
        val index = LocalIndex.of(library, LocalSort.TITLE_ASC)
        val queries = listOf("", "b", "blue", "BLUE", "miles", "夜", "zzz", "  jazz  ")
        for (q in queries) {
            assertEquals(
                "query=$q",
                LocalLibrary.search(LocalLibrary.sort(library, LocalSort.TITLE_ASC), q).map { it.id },
                index.search(q).map { it.id },
            )
        }
    }

    @Test
    fun oneArtistInTwoFieldsIsNotDoubleCounted() {
        // `search` returns tracks, not field hits, so a track cannot appear twice.
        val albums = listOf(track("9", "Blue in Green", "Miles Davis", "Blue"))
        val index = LocalIndex.of(albums, LocalSort.TITLE_ASC)
        assertEquals(1, index.search("blue").size)
    }

    @Test
    fun theIndexKeepsTheRequestedSortOrder() {
        val asc = LocalIndex.of(library, LocalSort.DURATION_ASC)
        assertEquals(listOf("3", "4", "1", "2"), asc.search("").map { it.id })
        val desc = LocalIndex.of(library, LocalSort.DURATION_DESC)
        assertEquals(listOf("2", "1", "4", "3"), desc.search("").map { it.id })
    }

    @Test
    fun aFilteredResultPreservesIndexOrder() {
        val index = LocalIndex.of(library, LocalSort.DURATION_ASC)
        // "Blue" appears in two albums; DURATION_ASC must survive the filter.
        val hits = index.search("blue").map { it.id }
        assertEquals(listOf("1", "2"), hits)
    }

    @Test
    fun theTrackListIsTheSortedList() {
        val index = LocalIndex.of(library, LocalSort.TITLE_ASC)
        assertSame(index.tracks, index.search(""))
        assertEquals(index.tracks.size, index.size)
    }

    @Test
    fun anEmptyLibraryAnswersEmpty() {
        val index = LocalIndex.of(emptyList(), LocalSort.TITLE_ASC)
        assertEquals(0, index.size)
        assertTrue(index.search("anything").isEmpty())
    }

    @Test
    fun groupingRunsOffTheIndexedSubset() {
        val index = LocalIndex.of(library, LocalSort.TITLE_ASC)
        val jazz = index.search("blue")
        val albums = index.albums(jazz)
        assertEquals(2, albums.size)
        assertEquals(setOf("album:Blue Train", "album:Kind of Blue"), albums.map { it.key }.toSet())
    }
}
