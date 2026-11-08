package com.lxmusic.android.data.backup

import android.content.Context
import com.google.gson.Gson
import com.lxmusic.android.data.db.AppDatabase
import com.lxmusic.android.data.db.FavoriteEntity
import com.lxmusic.android.data.db.PlayEventEntity
import com.lxmusic.android.data.db.PlayHistoryEntity
import com.lxmusic.android.data.db.PlaylistEntity
import com.lxmusic.android.data.db.PlaylistTrackEntity
import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.data.settings.AppSettings
import com.lxmusic.android.data.settings.EqProfile
import com.lxmusic.android.data.settings.ListStyle
import com.lxmusic.android.data.settings.SettingsStore
import com.lxmusic.android.data.settings.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Reads and writes the `.jjmbackup` container.
 *
 * The container is a plain zip with one entry, `backup.json`. Zip rather than a
 * bare JSON file for one reason: it leaves room to add cover art later without
 * a format break, and every file manager on Android can already handle it.
 */
object BackupArchive {

    /** The single entry inside the archive. */
    const val ENTRY_JSON = "backup.json"

    /** File extension the system file picker suggests. */
    const val EXTENSION = "jjmbackup"

    fun write(out: OutputStream, file: BackupFile) {
        ZipOutputStream(out).use { zip ->
            zip.putNextEntry(ZipEntry(ENTRY_JSON))
            zip.write(BackupFile.toJson(file).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    /**
     * Reads a backup from [input].
     *
     * Tolerates a bare JSON file too - a user who renamed the entry, or an older
     * build that wrote JSON directly, should still be able to restore.
     */
    fun read(input: InputStream): BackupFile? {
        val bytes = input.readBytes()
        if (bytes.isEmpty()) return null
        if (!isZip(bytes)) return BackupFile.fromJson(String(bytes, Charsets.UTF_8))
        ZipInputStream(bytes.inputStream()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (entry.name == ENTRY_JSON) {
                    val buffer = ByteArrayOutputStream()
                    zip.copyTo(buffer)
                    return BackupFile.fromJson(buffer.toString("UTF-8"))
                }
                entry = zip.nextEntry
            }
        }
        return null
    }

    /** Zip files start with "PK\u0003\u0004" (or an empty-archive signature). */
    private fun isZip(bytes: ByteArray): Boolean =
        bytes.size >= 4 && bytes[0] == 'P'.code.toByte() && bytes[1] == 'K'.code.toByte()
}

/**
 * Exports and imports everything the user has accumulated.
 *
 * Scope is deliberate: this covers what cannot be re-derived from the device -
 * favourites, playlists, play history and statistics, and the taste-shaped
 * settings. Scan folders are left out because the tree URIs carry device-specific
 * permission grants that are dead on arrival after a restore.
 */
class BackupRepository(
    private val context: Context,
    private val settingsStore: SettingsStore,
) {

    private val db = AppDatabase.get(context)
    private val gson = Gson()

    // ---------------- export ----------------

    /** Snapshots the database and the portable part of the settings. */
    suspend fun export(appVersion: String): BackupFile = withContext(Dispatchers.IO) {
        val favorites = db.favoriteDao().all()
        val playlists = db.playlistDao().all()
        val history = db.historyDao().recent(EXPORT_HISTORY_LIMIT)
        val events = db.playEventDao().since(0L, Long.MAX_VALUE)

        // One copy of each track, however many lists it appears in.
        val tracks = LinkedHashMap<String, MusicInfo>()
        fun remember(json: String) {
            runCatching { gson.fromJson(json, MusicInfo::class.java) }
                .getOrNull()?.let { tracks.putIfAbsent(it.id, it) }
        }
        favorites.forEach { remember(it.musicJson) }
        history.forEach { remember(it.musicJson) }
        events.forEach { remember(it.musicJson) }

        val backupPlaylists = playlists.map { p ->
            val rows = db.playlistDao().tracks(p.id)
            rows.forEach { remember(it.musicJson) }
            BackupPlaylist(
                name = p.name,
                createdAt = p.createdAt,
                coverUrl = p.coverUrl,
                trackIds = rows.map { it.musicId },
            )
        }

        BackupFile(
            exportedAt = System.currentTimeMillis(),
            appVersion = appVersion,
            tracks = tracks.values.toList(),
            favorites = favorites.map { it.musicId },
            playlists = backupPlaylists,
            history = history.map { BackupHistoryRow(it.musicId, it.playedAt) },
            events = events.map { BackupEventRow(it.musicId, it.playedAt) },
            settings = portableSettings(settingsStore.state.value),
        )
    }

    // ---------------- import ----------------

    /**
     * Merges [file] into the current library.
     *
     * Merge, never replace: a user restoring a backup onto a phone that already
     * has music must not lose what it found. Duplicates are absorbed by the
     * entity primary keys (favourites, history) and by name matching for
     * playlists.
     *
     * [localTracks] is what the scanners currently see; it is what backup ids are
     * resolved against. Tracks that cannot be resolved are skipped rather than
     * imported as dangling references - a favourites list pointing at files that
     * do not exist is worse than a shorter list.
     */
    suspend fun import(
        file: BackupFile,
        localTracks: List<MusicInfo>,
    ): BackupReport = withContext(Dispatchers.IO) {
        val resolved = TrackMatcher.resolve(file.tracks, localTracks)
        val byId = file.tracks.associateBy { it.id }

        /** Local track for a backup id, or null when the file is gone. */
        fun local(id: String): MusicInfo? = resolved[id]?.let { newId ->
            localTracks.firstOrNull { it.id == newId }
        }

        var favorites = 0
        for (id in file.favorites) {
            val track = local(id) ?: continue
            db.favoriteDao().add(FavoriteEntity(track.id, gson.toJson(track)))
            favorites++
        }

        val existing = db.playlistDao().all().associateBy { it.name }
        var playlists = 0
        var playlistTracks = 0
        for (backupList in file.playlists) {
            val name = backupList.name.ifBlank { "恢复的歌单" }
            val target = existing[name] ?: run {
                val id = db.playlistDao().insert(
                    PlaylistEntity(
                        name = name,
                        coverUrl = backupList.coverUrl,
                        createdAt = backupList.createdAt,
                        updatedAt = System.currentTimeMillis(),
                    ),
                )
                PlaylistEntity(id, name, backupList.coverUrl, backupList.createdAt, System.currentTimeMillis())
            }
            var position = db.playlistDao().maxPosition(target.id)?.plus(1) ?: 0
            for (backupTrackId in backupList.trackIds) {
                val track = local(backupTrackId) ?: continue
                db.playlistDao().insertTrack(
                    PlaylistTrackEntity(
                        playlistId = target.id,
                        musicId = track.id,
                        musicJson = gson.toJson(track),
                        position = position++,
                    ),
                )
                playlistTracks++
            }
            playlists++
        }

        var history = 0
        for (row in file.history) {
            val track = local(row.trackId) ?: continue
            db.historyDao().touch(PlayHistoryEntity(track.id, gson.toJson(track), row.playedAt))
            history++
        }

        var events = 0
        for (row in file.events) {
            val track = local(row.trackId) ?: continue
            db.playEventDao().log(
                PlayEventEntity(musicId = track.id, musicJson = gson.toJson(track), playedAt = row.playedAt),
            )
            events++
        }

        applyPortableSettings(file.settings)

        BackupReport(
            tracks = TrackMatcher.report(file.tracks, resolved),
            favorites = favorites,
            playlists = playlists,
            playlistTracks = playlistTracks,
            history = history,
            events = events,
        )
    }

    // ---------------- settings ----------------

    /**
     * The settings worth carrying across devices.
     *
     * Excluded on purpose: `lastPlayedId` / `lastPlayedPosMs` (point at a track
     * that may not exist), `lastUpdateCheckAt` (a throttle, meaningless after a
     * restore), `pendingDeletes` (a pending intent to delete files that are not
     * here), and everything device-shaped such as scan folders and EQ gain.
     *
     * EQ is included - a hand-tuned curve is work the user did, not a device fact.
     */
    private fun portableSettings(s: AppSettings): Map<String, String> = buildMap {
        put("themeMode", s.themeMode.name)
        put("themeColor", s.themeColor)
        put("listStyle", s.listStyle.name)
        put("lyricFontScale", s.lyricFontScale.toString())
        put("lyricOffsetMs", s.lyricOffsetMs.toString())
        put("preferLrcFile", s.preferLrcFile.toString())
        put("playerSkin", s.playerSkin)
        put("showPlayerMeta", s.showPlayerMeta.toString())
        put("recordHistory", s.recordHistory.toString())
        put("recordStats", s.recordStats.toString())
        put("eqEnabled", s.eq.enabled.toString())
        s.eq.preset?.let { put("eqPreset", it) }
        put("eqBands", EqProfile.encodeBands(s.eq.bands))
        put("eqBass", s.eq.bass.toString())
        put("eqVirtualizer", s.eq.virtualizer.toString())
        put("eqLoudness", s.eq.loudnessMb.toString())
    }

    /** Applies only the keys this build understands; unknown keys are ignored. */
    private fun applyPortableSettings(raw: Map<String, String>) {
        if (raw.isEmpty()) return
        val current = settingsStore.state.value
        settingsStore.update { s ->
            s.copy(
                themeMode = raw["themeMode"]?.let { name ->
                    runCatching { ThemeMode.valueOf(name) }.getOrNull()
                } ?: s.themeMode,
                themeColor = raw["themeColor"] ?: s.themeColor,
                listStyle = raw["listStyle"]?.let { name ->
                    runCatching { ListStyle.valueOf(name) }.getOrNull()
                } ?: s.listStyle,
                lyricFontScale = raw["lyricFontScale"]?.toFloatOrNull() ?: s.lyricFontScale,
                lyricOffsetMs = raw["lyricOffsetMs"]?.toIntOrNull() ?: s.lyricOffsetMs,
                preferLrcFile = raw["preferLrcFile"]?.toBooleanStrictOrNull() ?: s.preferLrcFile,
                playerSkin = raw["playerSkin"] ?: s.playerSkin,
                showPlayerMeta = raw["showPlayerMeta"]?.toBooleanStrictOrNull() ?: s.showPlayerMeta,
                recordHistory = raw["recordHistory"]?.toBooleanStrictOrNull() ?: s.recordHistory,
                recordStats = raw["recordStats"]?.toBooleanStrictOrNull() ?: s.recordStats,
            )
        }
        // EQ goes through its own setter: it has more fields than a copy() call
        // should carry, and writing it via copy() would need the whole profile.
        runCatching {
            settingsStore.setEq(
                EqProfile(
                    enabled = raw["eqEnabled"]?.toBooleanStrictOrNull() ?: current.eq.enabled,
                    preset = if (raw.containsKey("eqPreset")) raw["eqPreset"] else current.eq.preset,
                    bands = raw["eqBands"]?.let { EqProfile.decodeBands(it) } ?: current.eq.bands,
                    bass = raw["eqBass"]?.toIntOrNull() ?: current.eq.bass,
                    virtualizer = raw["eqVirtualizer"]?.toIntOrNull() ?: current.eq.virtualizer,
                    loudnessMb = raw["eqLoudness"]?.toIntOrNull() ?: current.eq.loudnessMb,
                ),
            )
        }
    }

    private companion object {
        /** Play history is capped the same way the UI caps its list. */
        const val EXPORT_HISTORY_LIMIT = 500
    }
}

/** What a restore actually did. */
data class BackupReport(
    val tracks: RestoreReport,
    val favorites: Int,
    val playlists: Int,
    val playlistTracks: Int,
    val history: Int,
    val events: Int,
) {
    /** One line for the toast / dialog after the restore finishes. */
    val summary: String
        get() = buildString {
            append("恢复 ${tracks.exact + tracks.matched} 首")
            if (tracks.matched > 0) append("（其中 ${tracks.matched} 首按标题匹配）")
            if (tracks.missing > 0) append("，缺失 ${tracks.missing} 首")
            if (favorites > 0) append("，收藏 $favorites")
            if (playlists > 0) append("，歌单 $playlists")
            if (history > 0) append("，播放记录 $history")
        }
}
