package com.lxmusic.android.ui.state

import android.app.Application
import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.data.repo.MusicRepository
import com.lxmusic.android.data.repo.PlaylistWithTracks
import com.lxmusic.android.data.settings.SettingsStore
import com.lxmusic.android.local.LocalFileDeleter
import com.lxmusic.android.local.LocalGroup
import com.lxmusic.android.local.LocalIndex
import com.lxmusic.android.local.LocalLibrary
import com.lxmusic.android.local.LocalSort
import com.lxmusic.android.local.LocalMusicScanner
import com.lxmusic.android.local.LocalTrackMapper
import com.lxmusic.android.local.SafMusicScanner
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.net.Uri

/**
 * Owns the on-device music library: the scan, the visible song list, and the
 * favourite / playlist / history slices of the database.
 *
 * Split out of `MainViewModel` so the screen state does not have to share a
 * class with update checking or playback.
 */
class MediaLibraryStore(
    private val app: Application,
    private val repo: MusicRepository,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
) {

    private val localScanner = LocalMusicScanner(app)
    private val safScanner = SafMusicScanner(app)

    private val _local = MutableStateFlow(LocalUiState())
    val local: StateFlow<LocalUiState> = _local.asStateFlow()

    private val _library = MutableStateFlow(LibraryUiState())
    val library: StateFlow<LibraryUiState> = _library.asStateFlow()

    /** True while a local scan is running (drives the progress indicator). */
    private val _scanning = MutableStateFlow(false)
    val scanning: StateFlow<Boolean> = _scanning.asStateFlow()

    /** What the last finished scan did, for the "本次扫描" line. */
    private val _lastScan = MutableStateFlow(ScanReport())
    val lastScan: StateFlow<ScanReport> = _lastScan.asStateFlow()

    /**
     * Search/sort cache, rebuilt only when the track list or the sort order
     * changes.
     *
     * Without it every keystroke lowercased three fields of every track; the
     * index does that once and a query is a `contains` per field. Guarded by
     * the two values it was built from, so a stale index is impossible rather
     * than merely unlikely.
     */
    private var index: LocalIndex? = null
    private var indexTracks: List<MusicInfo>? = null
    private var indexSort: LocalSort? = null

    /** Set by [com.lxmusic.android.ui.MainViewModel] so a delete can drop the queue slot. */
    var onTrackGone: ((String) -> Unit)? = null

    // ---------------- local music ----------------

    /**
     * Loads the cached library first, then rescans in the background.
     *
     * With no authorised folders the legacy MediaStore scan is used, so existing
     * users keep their library until they pick a folder.
     */
    fun scanLocal() {
        _local.value = _local.value.copy(loading = true, error = null)
        scope.launch {
            try {
                // 1. instant paint from the Room cache
                val cached = withContext(Dispatchers.IO) {
                    repo.cachedLocalTracks().map { LocalTrackMapper.toMusic(it) }
                }
                if (cached.isNotEmpty()) {
                    _local.value = _local.value.copy(loading = false, allTracks = cached)
                    recomputeLocal()
                    reconcilePendingDeletes(cached)
                }
                // 2. background refresh. The cached rows go in so unchanged
                // files skip their tag read entirely — see IncrementalScan.
                val folders = settingsStore.state.value.scanFolders.toList()
                val manual = settingsStore.state.value.manualUris.toList()
                val cacheById = cached.associateBy { it.id }
                var reused = 0
                val started = System.currentTimeMillis()
                val fresh = if (folders.isEmpty()) {
                    localScanner.scan()
                } else {
                    safScanner.scanFolders(
                        treeUris = folders,
                        readEmbeddedCover = settingsStore.state.value.readEmbeddedCover,
                        cached = cacheById,
                        onProgress = { count -> _scanning.value = count > 0 },
                        onReused = { n -> reused = n },
                    ) + manual.mapNotNull { u ->
                        runCatching { safScanner.readSingle(Uri.parse(u), settingsStore.state.value.readEmbeddedCover) }.getOrNull()
                    }
                }
                _scanning.value = false
                val kept = LocalLibrary.prune(cached, fresh)
                val merged = LocalLibrary.merge(fresh, kept)
                _local.value = _local.value.copy(loading = false, allTracks = merged)
                recomputeLocal()
                withContext(Dispatchers.IO) {
                    repo.clearFolderCache()
                    repo.upsertLocalTracks(merged.map { LocalTrackMapper.toEntity(it, it.extra["origin"] ?: "folder") })
                }
                _lastScan.value = ScanReport(
                    total = merged.size,
                    reused = reused,
                    read = (merged.size - reused).coerceAtLeast(0),
                    elapsedMs = System.currentTimeMillis() - started,
                )
            } catch (e: Exception) {
                _scanning.value = false
                _local.value = _local.value.copy(loading = false, error = e.message)
            }
        }
    }

    fun setLocalGroup(g: LocalGroup) {
        _local.value = _local.value.copy(group = g)
        recomputeLocal()
    }

    fun setLocalQuery(q: String) {
        _local.value = _local.value.copy(query = q)
        recomputeLocal()
    }

    fun setLocalSort(sort: LocalSort) {
        _local.value = _local.value.copy(sort = sort)
        recomputeLocal()
    }

    private fun recomputeLocal() {
        val s = _local.value
        val filtered = indexFor(s.allTracks, s.sort).search(s.query)
        when (s.group) {
            LocalGroup.SONGS ->
                _local.value = s.copy(songs = filtered, groups = emptyList())
            LocalGroup.ALBUMS ->
                _local.value = s.copy(songs = emptyList(), groups = LocalLibrary.byAlbum(filtered))
            LocalGroup.ARTISTS ->
                _local.value = s.copy(songs = emptyList(), groups = LocalLibrary.byArtist(filtered))
        }
    }

    /** The index for this exact (tracks, sort) pair, rebuilt only when it changed. */
    private fun indexFor(tracks: List<MusicInfo>, sort: LocalSort): LocalIndex {
        val cached = index
        // Reference equality is the right test here: the store replaces the
        // list wholesale (`copy(allTracks = ...)`), so a new object means new
        // contents and an equal-but-different instance means nothing to redo.
        if (cached != null && indexTracks === tracks && indexSort == sort) return cached
        val fresh = LocalIndex.of(tracks, sort)
        index = fresh
        indexTracks = tracks
        indexSort = sort
        return fresh
    }

    /** The track that currently holds `id`, recomputed from the full list. */
    fun trackById(id: String): MusicInfo? = _local.value.allTracks.firstOrNull { it.id == id }

    /**
     * The album/artist group for [key], recomputed from the full library.
     *
     * A detail screen must not hold a `MusicGroup` instance: [recomputeLocal]
     * rebuilds them whenever the search box changes, so a captured object goes
     * stale the moment the user types.
     */
    fun groupByKey(key: String, group: LocalGroup): com.lxmusic.android.local.MusicGroup? {
        val all = _local.value.allTracks
        val groups = when (group) {
            LocalGroup.ALBUMS -> LocalLibrary.byAlbum(all)
            LocalGroup.ARTISTS -> LocalLibrary.byArtist(all)
            LocalGroup.SONGS -> emptyList()
        }
        return groups.firstOrNull { it.key == key }
    }

    // ---------------- library ----------------

    fun refreshLibrary() {
        scope.launch {
            val favs = repo.favorites()
            val recent = repo.recent()
            val lists = repo.playlists()
            _library.value = _library.value.copy(
                favorites = favs,
                recent = recent,
                playlists = lists,
                favoriteIds = favs.map { it.id }.toSet(),
            )
        }
    }

    suspend fun isFavorite(musicId: String): Boolean = repo.isFavorite(musicId)

    fun toggleFavorite(music: MusicInfo) {
        scope.launch {
            repo.toggleFavorite(music)
            val favs = repo.favorites()
            _library.value = _library.value.copy(
                favorites = favs,
                favoriteIds = favs.map { it.id }.toSet(),
            )
        }
    }

    fun createPlaylist(name: String, onDone: (Long) -> Unit = {}) {
        scope.launch {
            val id = repo.createPlaylist(name.ifBlank { "新建歌单" })
            refreshLibrary()
            onDone(id)
        }
    }

    fun renamePlaylist(id: Long, name: String) {
        scope.launch { repo.renamePlaylist(id, name); refreshLibrary() }
    }

    fun deletePlaylist(id: Long) {
        scope.launch { repo.deletePlaylist(id); refreshLibrary() }
    }

    fun addToPlaylist(playlistId: Long, music: MusicInfo) {
        scope.launch { repo.addToPlaylist(playlistId, music); refreshLibrary() }
    }

    fun removeFromPlaylist(playlistId: Long, musicId: String) {
        scope.launch { repo.removeFromPlaylist(playlistId, musicId); refreshLibrary() }
    }

    fun clearHistory() {
        scope.launch { repo.clearHistory(); refreshLibrary() }
    }

    fun playlists(): List<PlaylistWithTracks> = _library.value.playlists

    // ---------------- deletion ----------------

    /** Removes a track from the library without touching the file. */
    fun removeFromLibrary(music: MusicInfo) {
        scope.launch {
            withContext(Dispatchers.IO) { repo.removeLocalTrack(music.id) }
            dropFromVisibleLists(music.id)
        }
    }

    /** Called after a file is really gone (or after the system dialog returns OK). */
    fun onDeleteConfirmed(music: MusicInfo) {
        scope.launch {
            repo.removeLocalTrack(music.id)
            settingsStore.setPendingDeletes(settingsStore.state.value.pendingDeletes - music.id)
            onTrackGone?.invoke(music.id)
            dropFromVisibleLists(music.id)
        }
    }

    fun onDeleteCancelled(music: MusicInfo) {
        scope.launch {
            settingsStore.setPendingDeletes(settingsStore.state.value.pendingDeletes - music.id)
        }
    }

    fun markPendingDelete(id: String) {
        settingsStore.setPendingDeletes(settingsStore.state.value.pendingDeletes + id)
    }

    fun reconcilePendingDeletes(tracks: List<MusicInfo>) {
        val pending = settingsStore.state.value.pendingDeletes
        if (pending.isEmpty()) return
        scope.launch {
            val byId = tracks.associateBy { it.id }
            val gone = pending.filter { id -> byId[id]?.let { !trackExists(it) } ?: true }
            if (gone.isEmpty()) {
                settingsStore.setPendingDeletes(settingsStore.state.value.pendingDeletes - pending)
                return@launch
            }
            withContext(Dispatchers.IO) { repo.removeLocalTracks(gone.toList()) }
            gone.forEach { dropFromVisibleLists(it) }
            settingsStore.setPendingDeletes(settingsStore.state.value.pendingDeletes - gone)
            recomputeLocal()
        }
    }

    suspend fun dropFromVisibleLists(id: String) {
        _local.value = _local.value.copy(allTracks = _local.value.allTracks.filterNot { it.id == id })
        recomputeLocal()
        refreshLibrary()
    }

    fun dropCachedCover(music: MusicInfo) {
        scope.launch {
            withContext(Dispatchers.IO) { LocalFileDeleter.dropCachedCover(app, music) }
        }
    }

    private fun trackExists(m: MusicInfo): Boolean {
        val raw = m.localPath ?: m.extra["uri"] ?: return false
        return runCatching {
            if (raw.startsWith("content://")) {
                app.contentResolver.openInputStream(Uri.parse(raw))?.use { true } ?: false
            } else java.io.File(raw.replaceFirst("file://", "")).exists()
        }.getOrDefault(false)
    }
}
