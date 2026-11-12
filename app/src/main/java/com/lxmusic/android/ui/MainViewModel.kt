package com.lxmusic.android.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lxmusic.android.BuildConfig
import com.lxmusic.android.LxApp
import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.data.repo.MusicRepository
import com.lxmusic.android.data.repo.PlaylistWithTracks
import com.lxmusic.android.data.settings.AppSettings
import com.lxmusic.android.data.settings.ListStyle
import com.lxmusic.android.data.settings.SettingsStore
import com.lxmusic.android.data.settings.ThemeMode
import com.lxmusic.android.data.settings.recordPolicy
import com.lxmusic.android.data.stats.AnnualReport
import com.lxmusic.android.data.stats.PlayStats
import com.lxmusic.android.data.stats.PlayTally
import com.lxmusic.android.data.stats.StatsRange
import com.lxmusic.android.local.LocalFileDeleter
import com.lxmusic.android.local.LocalGroup
import com.lxmusic.android.local.LocalSort
import com.lxmusic.android.local.MusicGroup
import com.lxmusic.android.net.UpdateChecker
import com.lxmusic.android.player.EqualizerState
import com.lxmusic.android.player.PlayerState
import com.lxmusic.android.ui.state.BackupUiState
import com.lxmusic.android.ui.state.BackupStore
import com.lxmusic.android.ui.state.LibraryUiState
import com.lxmusic.android.ui.state.LocalUiState
import com.lxmusic.android.ui.state.MediaLibraryStore
import com.lxmusic.android.ui.state.PlayerUiStore
import com.lxmusic.android.ui.state.StatsStore
import com.lxmusic.android.ui.state.StatsUiState
import com.lxmusic.android.ui.state.UpdateCheckState
import com.lxmusic.android.ui.state.UpdateStatus
import com.lxmusic.android.ui.state.UpdateStore
import com.lxmusic.android.ui.shell.Route
import com.lxmusic.android.ui.shell.RouteStack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Kept so existing imports in the UI layer keep resolving. */
typealias LocalUiStateAlias = LocalUiState

/** Top-level tabs. */
enum class HomeTab(val label: String) {
    /** V1.1.0: the default tab is "continue listening", not a raw track list. */
    LOCAL("正在听"),
    LIBRARY("音乐库"),
}

/**
 * The screen-facing facade.
 *
 * Work is delegated to four stores - [MediaLibraryStore] (scan + library),
 * [PlayerUiStore] (transport + equalizer), [StatsStore] and [UpdateStore] -
 * and everything below this class is just forwarding. The public surface is
 * unchanged, so the screens did not have to move with the split.
 */
class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = MusicRepository(app)
    private val settingsStore: SettingsStore = LxApp.instance.settingsStore

    val libraryStore = MediaLibraryStore(app, repo, settingsStore, viewModelScope)
    val playerUi = PlayerUiStore(app, settingsStore, viewModelScope)
    val statsStore = StatsStore(repo, viewModelScope)
    // Downloaded APKs go in the cache: they are worthless once installed, and
    // the system may reclaim them at any time without losing anything.
    val updateStore = UpdateStore(
        settingsStore = settingsStore,
        scope = viewModelScope,
        apkDir = { java.io.File(app.cacheDir, "updates") },
    )
    val backupStore = BackupStore(app, settingsStore, viewModelScope) {
        libraryStore.local.value.allTracks
    }

    private val _tab = kotlinx.coroutines.flow.MutableStateFlow(HomeTab.LOCAL)
    val tab: StateFlow<HomeTab> = _tab

    // ---------------- forwarded state ----------------

    val local: StateFlow<LocalUiState> get() = libraryStore.local
    val library: StateFlow<LibraryUiState> get() = libraryStore.library
    val scanning: StateFlow<Boolean> get() = libraryStore.scanning
    val lastScan: StateFlow<ScanReport> get() = libraryStore.lastScan

    /** The auto-assembled shelves on the home screen. */
    val collections: StateFlow<List<com.lxmusic.android.local.SmartCollection>> get() =
        libraryStore.collections

    /** The unified search page's query plus everything it found. */
    val search: StateFlow<com.lxmusic.android.local.SearchResults> get() = libraryStore.search
    val player: StateFlow<PlayerState> get() = playerUi.player
    val equalizer: StateFlow<EqualizerState> get() = playerUi.equalizer
    val stats: StateFlow<StatsUiState> get() = statsStore.stats

    /** This calendar year, summarised. Independent of the range chips. */
    val annual: StateFlow<AnnualReport?> get() = statsStore.annual
    val updateCheck: StateFlow<UpdateCheckState> get() = updateStore.updateCheck
    val updateInstall: StateFlow<UpdateInstallState> get() = updateStore.install
    val backup: StateFlow<BackupUiState> get() = backupStore.state

    val playerController get() = playerUi.controller
    val openNowPlaying get() = playerUi.openNowPlaying

    val settings: StateFlow<AppSettings> = settingsStore.state
    val versionName: String get() = BuildConfig.VERSION_NAME

    // ---------------- navigation (curtain stack) ----------------

    /** Which full-screen pages are raised over the stage, oldest first. */
    val routes = RouteStack()

    // ---------------- backup ----------------

    /** Writes a backup to the location the user picked. */
    fun exportBackup(uri: Uri) = backupStore.export(uri, versionName)

    /** Restores from the location the user picked, then refreshes the library. */
    fun importBackup(uri: Uri) {
        backupStore.import(uri)
        // The import writes favourites / history straight to the database; the
        // in-memory library state has to be re-read or the lists stay stale
        // until the next app start.
        libraryStore.refreshLibrary()
    }

    fun acknowledgeBackup() = backupStore.acknowledge()

    fun openSettings() = routes.open(Route.Settings)
    fun closeSettings() = routes.close(Route.Settings)
    fun openFolderManager() = routes.open(Route.Folders)
    fun closeFolderManager() = routes.close(Route.Folders)

    fun openStats() {
        routes.open(Route.Stats)
        statsStore.refresh()
    }

    fun closeStats() = routes.close(Route.Stats)

    fun openQueue() = routes.open(Route.Queue)
    fun closeQueue() = routes.close(Route.Queue)

    /** Opens the unified search page. The query survives closing and reopening. */
    fun openSearch() = routes.open(Route.Search)
    fun closeSearch() = routes.close(Route.Search)
    fun setSearchQuery(q: String) = libraryStore.setSearchQuery(q)
    fun clearSearch() = libraryStore.clearSearch()

    /** Plays a search result inside its own section, not the whole library. */
    fun playSearchTracks(startIndex: Int = 0) {
        val list = search.value.tracks
        if (list.isNotEmpty()) playerUi.playAndOpen(list, startIndex)
    }
    fun openEqualizer() = routes.open(Route.Equalizer)
    fun closeEqualizer() = routes.close(Route.Equalizer)
    fun openNowPlayingCurtain() = routes.open(Route.NowPlaying)
    fun closeNowPlayingCurtain() = routes.close(Route.NowPlaying)

    /**
     * Opens the album or artist page for [group].
     *
     * Only the key is remembered - see [Route.Group] for why the object itself
     * cannot be handed over.
     */
    fun openGroup(group: MusicGroup) = routes.open(Route.Group(group.key))

    /**
     * Opens a group page from a bare key, as the search results hand it over.
     *
     * The key already says which kind it is (`artist:` prefix), so the caller
     * does not have to carry a group object around just to open its page.
     */
    fun openGroupByKey(key: String) = routes.open(Route.Group(key))


    /**
     * Closes the topmost group page.
     *
     * A group page can be opened on top of another one (an album from a search,
     * then that artist), so this closes the newest rather than clearing them all.
     */
    fun closeGroup() {
        routes.stack.value.lastOrNull { it is Route.Group }?.let(routes::close)
    }

    /** Resolves a group route against the current library. */
    fun groupByKey(route: Route.Group): MusicGroup? =
        libraryStore.groupByKey(route.groupKey, route.kind)

    /** Plays a whole group starting from its first track. */
    fun playGroupShuffled(group: MusicGroup) {
        if (group.tracks.isEmpty()) return
        val shuffled = group.tracks.shuffled()
        playerUi.play(shuffled, 0)
        routes.open(Route.NowPlaying)
    }

    // ---------------- derived ----------------

    /**
     * The track the user listened to last, resolved against the local library
     * so the home screen can offer a one-tap "continue listening" card.
     */
    val lastPlayed: StateFlow<MusicInfo?> = combine(settingsStore.state, local) { st, lib ->
        st.lastPlayedId?.let { id -> lib.allTracks.firstOrNull { it.id == id } }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /**
     * The most recently played local tracks, for the horizontal cover rail on
     * 「正在听」. Empty is not an error - it is empty after the first scan too.
     */
    val recentLocal: StateFlow<List<MusicInfo>> = library
        .map { lib -> lib.recent.filter { it.isLocal }.distinctBy { it.id }.take(12) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Resume the card track inside the whole local queue. */
    fun resumeLastPlayed() {
        val track = lastPlayed.value ?: return
        val list = local.value.allTracks
        val at = list.indexOfFirst { it.id == track.id }
        if (at >= 0) playerUi.playAndOpen(list, at) else playerUi.playAndOpen(listOf(track), 0)
    }

    init {
        playerUi.controller.connect()
        // Persist mode flips the moment the user makes them.
        playerUi.controller.onModeChanged = { mode, shuf ->
            settingsStore.setRepeatMode(mode.name)
            settingsStore.setShuffle(shuf)
        }
        // Throttled last-played persistence.
        playerUi.controller.onProgress = { id, pos -> settingsStore.setLastPlayed(id, pos) }

        viewModelScope.launch {
            settingsStore.loaded.first()
            val st = settingsStore.state.value
            playerUi.applyPersistedModes(st)
            restoreLastPlayed(st)
            if (st.autoScanOnStart) libraryStore.scanLocal()
        }

        // A collapsed queue is repaired at press time, not only at restore time,
        // so "next" never silently does nothing.
        playerUi.controller.onNeedBackfill = { currentId -> libraryContextFor(currentId) }
        playerUi.controller.onTrackStarted = { music ->
            viewModelScope.launch {
                // The two histories are independent switches: 最近播放 is
                // opt-outable privacy, the play counts are their own toggle.
                val policy = settingsStore.state.value.recordPolicy()
                if (policy.history) {
                    repo.markPlayed(music)
                    libraryStore.refreshLibrary()
                }
                if (policy.stats) repo.logPlay(music)
            }
        }
        libraryStore.onTrackGone = { id -> playerUi.controller.forgetTrack(id) }

        playerUi.startMirrors()
        viewModelScope.launch { settingsStore.state.collect { playerUi.applyPreferences(it) } }
        libraryStore.refreshLibrary()
    }

    // ---------------- update checking ----------------

    fun checkForUpdates() = updateStore.check()
    fun checkForUpdatesOnStartup() = updateStore.checkOnStartup()

    /** Pull the discovered release APK into the cache, verified. */
    fun downloadUpdate() = updateStore.downloadUpdate()
    fun cancelUpdateDownload() = updateStore.cancelInstall()
    fun acknowledgeUpdateInstall() = updateStore.acknowledgeInstall()

    // ---------------- playback ----------------

    fun togglePlayPause() = playerUi.togglePlayPause()
    fun pause() = playerUi.pause()
    fun seekTo(ms: Long) = playerUi.seekTo(ms)
    fun next() = playerUi.next()
    fun prev() = playerUi.prev()
    fun cycleRepeat() = playerUi.cycleRepeat()
    fun toggleShuffle() = playerUi.toggleShuffle()

    fun playList(list: List<MusicInfo>, startIndex: Int = 0) {
        if (list.isNotEmpty()) playerUi.play(list, startIndex)
    }

    // ---------------- queue ----------------

    fun queueJumpTo(index: Int) = playerUi.queueJumpTo(index)
    fun queueMove(from: Int, to: Int) = playerUi.queueMove(from, to)
    fun queueRemove(index: Int) = playerUi.queueRemove(index)
    fun queueClear() = playerUi.clearQueue()
    fun queueUndoRemove() = playerUi.queueUndoRemove()
    val lastQueueRemoval: Pair<MusicInfo, Int>? get() = playerUi.lastQueueRemoval
    fun addToQueueNext(music: MusicInfo) = playerUi.swapNext(listOf(music))
    fun addToQueueLast(music: MusicInfo) = playerUi.append(listOf(music))

    // ---------------- library / local ----------------

    fun setTab(t: HomeTab) {
        _tab.value = t
        if (t == HomeTab.LIBRARY) libraryStore.refreshLibrary()
    }

    fun scanLocal() = libraryStore.scanLocal()
    fun setLocalGroup(g: LocalGroup) = libraryStore.setLocalGroup(g)
    fun setLocalQuery(q: String) = libraryStore.setLocalQuery(q)
    fun setLocalSort(sort: LocalSort) = libraryStore.setLocalSort(sort)
    fun refreshLibrary() = libraryStore.refreshLibrary()

    fun playLocal(index: Int) {
        val list = local.value.songs
        if (index in list.indices) playerUi.playAndOpen(list, index)
    }

    fun playGroup(group: MusicGroup, startIndex: Int = 0) {
        if (group.tracks.isEmpty()) return
        playerUi.play(group.tracks, startIndex)
        routes.open(Route.NowPlaying)
    }

    /**
     * Plays a shelf from the start.
     *
     * The queue is the shelf's own tracks, not the library: "play 最常播放" means
     * those thirty songs, and letting the queue continue into the rest of the
     * library would make the shelf meaningless one track later.
     */
    fun playCollection(collection: com.lxmusic.android.local.SmartCollection, startIndex: Int = 0) {
        if (collection.tracks.isEmpty()) return
        playerUi.playAndOpen(collection.tracks, startIndex)
    }

    suspend fun isFavorite(musicId: String): Boolean = libraryStore.isFavorite(musicId)
    fun toggleFavorite(music: MusicInfo) = libraryStore.toggleFavorite(music)
    fun createPlaylist(name: String, onDone: (Long) -> Unit = {}) = libraryStore.createPlaylist(name, onDone)
    fun renamePlaylist(id: Long, name: String) = libraryStore.renamePlaylist(id, name)
    fun deletePlaylist(id: Long) = libraryStore.deletePlaylist(id)
    fun addToPlaylist(playlistId: Long, music: MusicInfo) = libraryStore.addToPlaylist(playlistId, music)
    fun removeFromPlaylist(playlistId: Long, musicId: String) = libraryStore.removeFromPlaylist(playlistId, musicId)
    fun clearHistory() = libraryStore.clearHistory()

    fun playPlaylist(playlist: PlaylistWithTracks, startIndex: Int = 0) {
        if (playlist.tracks.isNotEmpty()) playerUi.playAndOpen(playlist.tracks, startIndex)
    }

    fun playFavorites(startIndex: Int = 0) {
        val favs = library.value.favorites
        if (favs.isNotEmpty()) playerUi.playAndOpen(favs, startIndex)
    }

    /**
     * 播放搜索页「收藏」那一段里的某一首。
     *
     * 队列用这一段匹配出来的子集，不是全部收藏 —— 用户是带着那个关键词点下去的，
     * 队列里冒出无关的歌就等于关键词失效了。
     */
    fun playSearchFavorites(startIndex: Int = 0) {
        val list = search.value.favorites
        if (list.isNotEmpty()) playerUi.playAndOpen(list, startIndex)
    }

    fun playRecent(startIndex: Int = 0) {
        val recent = library.value.recent
        if (recent.isNotEmpty()) playerUi.playAndOpen(recent, startIndex)
    }

    // ---------------- statistics ----------------

    fun setStatsRange(range: StatsRange) = statsStore.setRange(range)
    fun refreshStats() = statsStore.refresh()
    fun clearPlayStats() = statsStore.clear()

    fun playStatsTrack(tally: PlayTally) {
        val list = stats.value.tallies.map { it.music }
        val at = list.indexOfFirst { it.id == tally.music.id }.coerceAtLeast(0)
        if (list.isNotEmpty()) playerUi.playAndOpen(list, at)
    }

    fun annualReport(year: Int = PlayStats.currentYear(), onDone: (AnnualReport) -> Unit) =
        statsStore.annualReport(year, onDone)

    // ---------------- equalizer ----------------

    fun eqSetEnabled(enabled: Boolean) = playerUi.eqSetEnabled(enabled)
    fun eqSetBand(bandIndex: Int, levelMb: Short) = playerUi.eqSetBand(bandIndex, levelMb)
    fun eqApplyPreset(preset: com.lxmusic.android.player.EqPreset) = playerUi.eqApplyPreset(preset)
    fun eqSetBass(strength: Int) = playerUi.eqSetBass(strength)
    fun eqSetVirtualizer(strength: Int) = playerUi.eqSetVirtualizer(strength)
    fun eqSetLoudness(gainMb: Int) = playerUi.eqSetLoudness(gainMb)
    fun eqReset() = playerUi.eqReset()

    // ---------------- settings ----------------

    fun setThemeMode(mode: ThemeMode) = settingsStore.setThemeMode(mode)
    fun setThemeColor(id: String) = settingsStore.setThemeColor(id)
    fun setListStyle(style: ListStyle) = settingsStore.setListStyle(style)
    fun setAutoScan(on: Boolean) = settingsStore.setAutoScan(on)
    fun setReadEmbeddedCover(on: Boolean) = settingsStore.setReadEmbeddedCover(on)
    fun setRecordHistory(on: Boolean) = settingsStore.setRecordHistory(on)
    fun setRecordStats(on: Boolean) = settingsStore.setRecordStats(on)
    fun setPauseOnHeadsetUnplug(on: Boolean) = settingsStore.setPauseOnHeadsetUnplug(on)
    fun setLyricFontScale(v: Float) = settingsStore.setLyricFontScale(v)
    fun setLyricOffset(ms: Int) = settingsStore.setLyricOffset(ms)
    fun setPreferLrcFile(on: Boolean) = settingsStore.setPreferLrcFile(on)
    fun setPlayerSkin(skin: String) = settingsStore.setPlayerSkin(skin)
    fun setShowPlayerMeta(on: Boolean) = settingsStore.setShowPlayerMeta(on)

    /** V1.4.1: 首页「最近在听」这一排的显示开关。 */
    fun setShowRecentRail(on: Boolean) = settingsStore.setShowRecentRail(on)

    /** Registers a persisted SAF tree permission and rescans. */
    fun addScanFolder(treeUri: String) {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.takePersistableUriPermission(
                Uri.parse(treeUri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        settingsStore.addScanFolder(treeUri)
        libraryStore.scanLocal()
    }

    fun removeScanFolder(treeUri: String) {
        val ctx = getApplication<Application>()
        runCatching {
            ctx.contentResolver.releasePersistableUriPermission(
                Uri.parse(treeUri),
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
        settingsStore.removeScanFolder(treeUri)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.removeLocalTracks(
                    repo.cachedLocalTracks().filter { it.uri.startsWith(treeUri) }.map { it.id }
                )
            }
            libraryStore.scanLocal()
        }
    }

    /** Registers a persisted permission for a single picked audio file. */
    fun addManualFiles(uris: List<String>) {
        val ctx = getApplication<Application>()
        uris.forEach { u ->
            runCatching {
                ctx.contentResolver.takePersistableUriPermission(
                    Uri.parse(u),
                    android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
            settingsStore.addManualUri(u)
        }
        libraryStore.scanLocal()
    }

    fun removeManualFile(uri: String) {
        settingsStore.removeManualUri(uri)
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                repo.removeLocalTracks(repo.cachedLocalTracks().filter { it.uri == uri }.map { it.id })
            }
            libraryStore.scanLocal()
        }
    }

    // ---------------- deletion ----------------

    /** A MediaStore row that needs the system confirm dialog. MainActivity collects this. */
    val deleteRequest = kotlinx.coroutines.flow.MutableSharedFlow<android.content.IntentSender>(extraBufferCapacity = 1)

    fun deleteLocalFile(music: MusicInfo, onResult: (String) -> Unit = {}) {
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) { LocalFileDeleter.delete(getApplication(), music) }
            when (result) {
                is com.lxmusic.android.local.DeleteResult.Done -> {
                    libraryStore.dropCachedCover(music)
                    libraryStore.onDeleteConfirmed(music)
                }
                is com.lxmusic.android.local.DeleteResult.NeedsConfirmation -> {
                    libraryStore.markPendingDelete(music.id)
                    deleteRequest.emit(result.sender)
                }
                is com.lxmusic.android.local.DeleteResult.Failed -> onResult(result.reason)
            }
        }
    }

    fun onDeleteConfirmed(music: MusicInfo) = libraryStore.onDeleteConfirmed(music)
    fun onDeleteCancelled(music: MusicInfo) = libraryStore.onDeleteCancelled(music)
    fun removeFromLibrary(music: MusicInfo) = libraryStore.removeFromLibrary(music)
    fun reconcilePendingDeletes(tracks: List<MusicInfo>) = libraryStore.reconcilePendingDeletes(tracks)

    /** Clears favourites, playlists, history and every preference. */
    fun clearAllData() {
        viewModelScope.launch {
            repo.clearAllUserData()
            settingsStore.clearAll()
            libraryStore.refreshLibrary()
        }
    }

    // ---------------- restore ----------------

    /**
     * Silently reload the last played track at its saved position.
     * Unknown ids are cleared so a dead marker never blocks the next save.
     */
    private fun restoreLastPlayed(st: AppSettings) {
        val id = st.lastPlayedId
        if (id.isNullOrBlank()) return
        viewModelScope.launch {
            val track = local.value.allTracks.firstOrNull { it.id == id }
                ?: repo.recent().firstOrNull { it.id == id && it.isLocal }
            if (track == null) {
                settingsStore.setLastPlayed(null, 0L)
                playerUi.controller.clearSavedProgress()
                return@launch
            }
            playerUi.controller.prepareRestored(track, st.lastPlayedPosMs)
            // Restore yields a single-track queue which breaks shuffle; backfill
            // the full context once the scan settles.
            viewModelScope.launch {
                val ordered = libraryContextFor(track.id)
                if (ordered.size > 1) playerUi.controller.backfillQueue(ordered, track.id)
            }
        }
    }

    /**
     * The ordered queue context that should surround [currentId].
     *
     * Waits for the first finished scan, then prefers the in-memory library and
     * falls back to the persisted recent list. Returns an empty list when the
     * track is unknown, which callers read as "nothing to backfill".
     */
    private suspend fun libraryContextFor(currentId: String): List<MusicInfo> {
        local.first { !it.loading }
        val all = local.value.allTracks
        val track = all.firstOrNull { it.id == currentId }
            ?: repo.recent().firstOrNull { it.id == currentId && it.isLocal }
            ?: return emptyList()
        return buildRestoreContext(all, track)
    }

    /** Queue context for a restored track: the whole library, track mid-list. */
    private fun buildRestoreContext(all: List<MusicInfo>, track: MusicInfo): List<MusicInfo> {
        val rest = all.filter { it.id != track.id }
        val at = (all.size / 2).coerceIn(0, rest.size)
        return rest.subList(0, at) + track + rest.subList(at, rest.size)
    }

    override fun onCleared() {
        playerUi.release()
        super.onCleared()
    }
}
