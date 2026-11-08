package com.lxmusic.android.data.backup

import com.lxmusic.android.data.model.MusicInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

/**
 * Ids are `content://` URIs, so a backup taken on one device cannot rely on
 * them. Everything here is about the fallback identity and about a file that
 * survives a round trip through a zip the user may have renamed.
 */
class BackupCodecTest {

    private fun track(
        id: String,
        name: String = "Song",
        singer: String = "Singer",
        album: String = "Album",
        ms: Long = 210_000L,
    ) = MusicInfo(
        id = id,
        source = MusicInfo.SOURCE_LOCAL,
        songmid = id,
        name = name,
        singer = singer,
        albumName = album,
        intervalSeconds = (ms / 1000L).toInt(),
        localDurationMs = ms,
    )

    // ---------------- fingerprint ----------------

    @Test
    fun fingerprintIgnoresCaseAndSurroundingSpace() {
        val a = track("a", name = "Blue In Green", singer = "Miles Davis")
        val b = track("b", name = "  blue in green ", singer = "MILES DAVIS")
        assertEquals(TrackMatcher.fingerprint(a), TrackMatcher.fingerprint(b))
    }

    @Test
    fun fingerprintTreatsSubSecondDifferenceAsTheSameSong() {
        val a = track("a", ms = 210_000L)
        val b = track("b", ms = 210_400L)
        assertEquals(TrackMatcher.fingerprint(a), TrackMatcher.fingerprint(b))
    }

    @Test
    fun fingerprintSeparatesDifferentDurations() {
        val a = track("a", ms = 210_000L)
        val b = track("b", ms = 240_000L)
        assertNotEquals(TrackMatcher.fingerprint(a), TrackMatcher.fingerprint(b))
    }

    @Test
    fun fingerprintFallsBackToIntervalSecondsWhenMsIsMissing() {
        val withMs = track("a", ms = 210_000L)
        val withoutMs = track("b", ms = 0L).copy(intervalSeconds = 210, interval = "03:30")
        assertEquals(TrackMatcher.fingerprint(withMs), TrackMatcher.fingerprint(withoutMs))
    }

    // ---------------- resolve ----------------

    @Test
    fun exactIdWins() {
        val backup = listOf(track("keep"))
        val local = listOf(track("keep"), track("other", name = "Different"))
        val resolved = TrackMatcher.resolve(backup, local)
        assertEquals("keep", resolved["keep"])
    }

    @Test
    fun differentIdFallsBackToTheFingerprint() {
        val backup = listOf(track("old-uri"))
        val local = listOf(track("new-uri"))
        val resolved = TrackMatcher.resolve(backup, local)
        assertEquals("new-uri", resolved["old-uri"])
    }

    @Test
    fun missingTrackIsAbsentFromTheMap() {
        val backup = listOf(track("gone", name = "Lost Song"))
        val local = listOf(track("here", name = "Some Other Song"))
        assertTrue(TrackMatcher.resolve(backup, local).isEmpty())
    }

    @Test
    fun duplicateFingerprintTakesTheFirstLocalTrack() {
        val backup = listOf(track("old"))
        val local = listOf(track("first"), track("second"))
        assertEquals("first", TrackMatcher.resolve(backup, local)["old"])
    }

    @Test
    fun reportSeparatesExactFromMatchedFromMissing() {
        val backup = listOf(
            track("same", name = "A"),
            track("moved", name = "B"),
            track("gone", name = "C"),
        )
        val local = listOf(track("same", name = "A"), track("fresh", name = "B"))
        val resolved = TrackMatcher.resolve(backup, local)

        val report = TrackMatcher.report(backup, resolved)

        assertEquals(1, report.exact)
        assertEquals(1, report.matched)
        assertEquals(1, report.missing)
        assertEquals(3, report.total)
    }

    // ---------------- JSON ----------------

    @Test
    fun jsonRoundTripKeepsEverySection() {
        val file = BackupFile(
            exportedAt = 1_700_000_000_000L,
            appVersion = "1.1.6",
            tracks = listOf(track("a")),
            favorites = listOf("a"),
            playlists = listOf(BackupPlaylist("Late night", 1L, null, listOf("a"))),
            history = listOf(BackupHistoryRow("a", 5L)),
            events = listOf(BackupEventRow("a", 6L)),
            settings = mapOf("themeMode" to "dark"),
        )

        val back = BackupFile.fromJson(BackupFile.toJson(file))!!

        assertEquals(1, back.tracks.size)
        assertEquals(listOf("a"), back.favorites)
        assertEquals("Late night", back.playlists.single().name)
        assertEquals(5L, back.history.single().playedAt)
        assertEquals(6L, back.events.single().playedAt)
        assertEquals("dark", back.settings["themeMode"])
        assertEquals("1.1.6", back.appVersion)
    }

    @Test
    fun unknownFutureVersionIsRejected() {
        val json = """{"version": 99, "tracks": []}"""
        assertNull(BackupFile.fromJson(json))
    }

    @Test
    fun garbageIsRejected() {
        assertNull(BackupFile.fromJson("not a backup"))
    }

    // ---------------- archive ----------------

    @Test
    fun archiveRoundTripThroughAZip() {
        val file = BackupFile(
            exportedAt = 42L,
            tracks = listOf(track("a")),
            favorites = listOf("a"),
        )
        val bytes = ByteArrayOutputStream().also { BackupArchive.write(it, file) }.toByteArray()

        // The entry name is part of the format, so assert on the raw bytes.
        assertTrue(String(bytes, Charsets.ISO_8859_1).contains(BackupArchive.ENTRY_JSON))

        val read = BackupArchive.read(ByteArrayInputStream(bytes))
        assertEquals(42L, read?.exportedAt)
        assertEquals(1, read?.tracks?.size)
    }

    @Test
    fun plainJsonIsAcceptedForAnUnzippedFile() {
        val file = BackupFile(exportedAt = 7L, favorites = listOf("x"))
        val bytes = BackupFile.toJson(file).toByteArray(Charsets.UTF_8)

        val read = BackupArchive.read(ByteArrayInputStream(bytes))

        assertEquals(7L, read?.exportedAt)
        assertEquals(listOf("x"), read?.favorites)
    }

    @Test
    fun emptyStreamIsRejected() {
        assertNull(BackupArchive.read(ByteArrayInputStream(ByteArray(0))))
    }
}
