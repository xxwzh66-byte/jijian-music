package com.lxmusic.android.data.backup

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import com.lxmusic.android.data.model.MusicInfo

/** One play-history row: which track, when. */
data class BackupHistoryRow(val trackId: String, val playedAt: Long)

/** One play-statistics event. */
data class BackupEventRow(val trackId: String, val playedAt: Long)

/** A playlist as stored in a backup: names and track ids, no track copies. */
data class BackupPlaylist(
    val name: String,
    val createdAt: Long,
    val coverUrl: String? = null,
    val trackIds: List<String> = emptyList(),
)

/**
 * The whole user-owned state, flattened for transport.
 *
 * Tracks are stored once in [tracks] and everything else refers to them by id.
 * A year of play events referencing duplicated `MusicInfo` blobs would be tens
 * of megabytes; this way the event list is two numbers per row.
 */
data class BackupFile(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long = 0L,
    val appVersion: String = "",
    val tracks: List<MusicInfo> = emptyList(),
    val favorites: List<String> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val history: List<BackupHistoryRow> = emptyList(),
    val events: List<BackupEventRow> = emptyList(),
    /** Settings that describe taste rather than device: theme, EQ, lyric prefs. */
    val settings: Map<String, String> = emptyMap(),
) {
    companion object {
        /** Bumped only when the shape changes in a way old readers cannot ignore. */
        const val CURRENT_VERSION = 1

        private val gson: Gson = GsonBuilder().serializeNulls().create()

        fun toJson(file: BackupFile): String = gson.toJson(file)

        /** Returns null for anything that is not a backup this build understands. */
        fun fromJson(json: String): BackupFile? = runCatching {
            gson.fromJson(json, BackupFile::class.java)
        }.getOrNull()?.takeIf { it.version in 1..CURRENT_VERSION }
    }
}

/** Why a restore could not bring a track back. */
enum class RestoreOutcome {
    /** Same id, straight hit. */
    EXACT,

    /** Different id but the same title / artist / album / duration. */
    MATCHED,

    /** No counterpart on this device - the file is gone. */
    MISSING,
}

/** What a restore did, per track. */
data class RestoreReport(
    val exact: Int,
    val matched: Int,
    val missing: Int,
) {
    val total: Int get() = exact + matched + missing
}

/**
 * Resolves backup track ids to ids that exist on this device.
 *
 * Ids are `content://` URIs for MediaStore tracks and SAF document URIs for
 * folder-scanned ones, so they change across devices and even across a factory
 * reset. The fallback identity is the four things a listener would check by
 * hand: title, artist, album, duration.
 *
 * Deliberately pure - no `android.net.Uri`, no ContentResolver - so the matching
 * rules can be unit-tested on the JVM.
 */
object TrackMatcher {

    /** Separator that cannot appear in a tag field. */
    private const val SEP = '\u0001'

    /**
     * Identity used when the id cannot be matched.
     *
     * Duration is normalised to milliseconds and rounded to the second: two
     * rips of the same song routinely differ by a few milliseconds, and a strict
     * comparison would call them different songs.
     */
    fun fingerprint(music: MusicInfo): String {
        val durationMs = if (music.localDurationMs > 0) {
            music.localDurationMs
        } else {
            music.intervalSeconds.toLong() * 1000L
        }
        val seconds = (durationMs + 500L) / 1000L
        return listOf(
            music.name.trim().lowercase(),
            music.singer.trim().lowercase(),
            music.albumName.trim().lowercase(),
            seconds.toString(),
        ).joinToString(SEP.toString())
    }

    /**
     * Maps every id in [backupTracks] to the id of its local counterpart.
     *
     * Unmatched tracks are absent from the result. When several local tracks
     * share a fingerprint, the first one wins - the user is not asked to pick
     * between two identical rips.
     */
    fun resolve(
        backupTracks: List<MusicInfo>,
        localTracks: List<MusicInfo>,
    ): Map<String, String> {
        val localById = localTracks.associateBy { it.id }
        val localByPrint = HashMap<String, String>(localTracks.size)
        for (track in localTracks) {
            localByPrint.putIfAbsent(fingerprint(track), track.id)
        }
        val out = LinkedHashMap<String, String>(backupTracks.size)
        for (backup in backupTracks) {
            val hit = when {
                localById.containsKey(backup.id) -> backup.id
                else -> localByPrint[fingerprint(backup)]
            }
            if (hit != null) out[backup.id] = hit
        }
        return out
    }

    /**
     * Classifies [backupTracks] against the resolved ids, for the report the
     * user sees after a restore ("恢复 120 首，缺失 3 首").
     */
    fun report(backupTracks: List<MusicInfo>, resolved: Map<String, String>): RestoreReport {
        var exact = 0
        var matched = 0
        for (backup in backupTracks) {
            val hit = resolved[backup.id] ?: continue
            if (hit == backup.id) exact++ else matched++
        }
        return RestoreReport(
            exact = exact,
            matched = matched,
            missing = backupTracks.size - exact - matched,
        )
    }
}
