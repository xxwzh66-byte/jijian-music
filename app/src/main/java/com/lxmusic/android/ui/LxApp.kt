package com.lxmusic.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.ui.screens.UpdateDialog
import com.lxmusic.android.ui.components.ArtworkColors
import com.lxmusic.android.ui.components.TrackActionsSheet
import com.lxmusic.android.ui.neon.AuroraBackdrop
import com.lxmusic.android.ui.neon.rememberMotionEnabled
import com.lxmusic.android.ui.neon.rememberNeonSurface
import com.lxmusic.android.ui.screens.EqualizerScreen
import com.lxmusic.android.ui.components.PlayerSkin
import com.lxmusic.android.ui.components.toast
import com.lxmusic.android.ui.screens.NowPlayingScreen
import com.lxmusic.android.ui.screens.QueueScreen
import com.lxmusic.android.ui.screens.SettingsScreen
import com.lxmusic.android.ui.screens.StatsScreen
import com.lxmusic.android.ui.screens.FolderManagerScreen
import com.lxmusic.android.ui.shell.CurtainSpec
import com.lxmusic.android.ui.shell.LxShell
import com.lxmusic.android.ui.shell.Route
import com.lxmusic.android.ui.shell.isGroupOpen
import com.lxmusic.android.ui.shell.routeGroupId
import com.lxmusic.android.ui.screens.GroupDetailHost

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LxApp(viewModel: MainViewModel) {
    // V1.1.0: 平板/横屏（>= 840dp）双栏——左列表右播放页，播放不再是"全屏接管"。
    val wide = androidx.compose.ui.platform.LocalConfiguration.current.screenWidthDp >= 840
    val tab by viewModel.tab.collectAsState()
    val playerState by viewModel.player.collectAsState()
    val libraryState by viewModel.library.collectAsState()
    // V6: 主题色要在主壳与播放页之间共享，所以提到最外层只收一次。
    val settings by viewModel.settings.collectAsState()
    val themeColor = settings.themeColor
    val context = LocalContext.current

    var showNowPlaying by remember { mutableStateOf(false) }
    // V5.1-1: any playback entry tap opens the full-screen player.
    LaunchedEffect(viewModel) {
        viewModel.openNowPlaying.collect { viewModel.openNowPlayingCurtain() }
    }
    // V1.0.3: 启动静默检查更新（每 24h 一次，见 MainViewModel.maybeCheckForUpdatesOnStartup）。
    LaunchedEffect(viewModel) { viewModel.maybeCheckForUpdatesOnStartup() }
    val updateState by viewModel.updateCheck.collectAsState()
    val updateInstall by viewModel.updateInstall.collectAsState()
    // V1.1.6: 五块幕布的开关从五个布尔值收成一条 Route 栈。这样详情页这类
    // 带参数的页面才装得进来（"打开 key 为 X 的专辑"没法用一个 Boolean 表达）。
    val routeStack by viewModel.routes.stack.collectAsState()
    fun isOpen(route: Route): Boolean = routeStack.any { it.id == route.id }
    // V1.0.3: 首页公告条点开后的「更新内容」对话框。
    var showUpdateDialog by remember { mutableStateOf(false) }
    var actionTarget by remember { mutableStateOf<MusicInfo?>(null) }
    var actionRemoveContext by remember { mutableStateOf<Long?>(null) } // playlistId if removing

    // V1.1.0: 极光跟随当前曲目封面主色。提色在 IO 线程做一次并 remember，
    // 封面不变就不重算；提不出来回落调色板。放在这里而不是舞台里，因为
    // 幕布拉起时极光仍要在背后继续，色不能跟着换。
    val artworkTint by produceState<androidx.compose.ui.graphics.Color?>(
        initialValue = null,
        key1 = playerState.current?.id,
    ) {
        value = ArtworkColors.schemeFor(context, playerState.current?.img).primary
    }
    val surface = rememberNeonSurface(
        dark = MaterialTheme.colorScheme.surface.luminance() <= 0.5f,
        themeColor = themeColor,
        artworkTint = artworkTint,
    )

    // 播放页在窄屏是一块幕布、在宽屏是右栏。两处内容一致，所以只写一遍。
    val nowPlaying: @Composable () -> Unit = {
        NowPlayingScreen(
            state = playerState,
            lyricFontScale = settings.lyricFontScale,
            skin = PlayerSkin.from(settings.playerSkin),
            onSkinSelected = { sk -> viewModel.setPlayerSkin(sk.name) },
            showMeta = settings.showPlayerMeta,
            themeColor = settings.themeColor,
            isFavorite = playerState.current?.id?.let { it in libraryState.favoriteIds } ?: false,
            onBack = { viewModel.closeNowPlayingCurtain() },
            onTogglePlay = viewModel::togglePlayPause,
            onNext = viewModel::next,
            onPrev = viewModel::prev,
            onSeek = viewModel::seekTo,
            onToggleRepeat = { viewModel.cycleRepeat() },
            onToggleShuffle = { viewModel.toggleShuffle() },
            onToggleFavorite = { playerState.current?.let { viewModel.toggleFavorite(it) } },
            onOpenQueue = { viewModel.openQueue() },
            onOpenEqualizer = { viewModel.openEqualizer() },
        )
    }

    val playing = playerState.current != null

    // 越靠前越在上层。顺序沿用 V1.1.2 那串 early-return 的判断次序，所以层级不变。
    // 可见性现在由 Route 栈决定，但绘制顺序仍然是这份声明顺序 —— 幕布不会因为
    // 谁的打开时间晚就盖到别人上面。
    val curtains = buildList {
        add(
            CurtainSpec(Route.Settings.id, isOpen(Route.Settings), { viewModel.closeSettings() }) {
                SettingsScreen(viewModel = viewModel, onBack = { viewModel.closeSettings() })
            },
        )
        add(
            CurtainSpec(Route.Folders.id, isOpen(Route.Folders), { viewModel.closeFolderManager() }) {
                FolderManagerScreen(viewModel = viewModel, onBack = { viewModel.closeFolderManager() })
            },
        )
        add(CurtainSpec(Route.Stats.id, isOpen(Route.Stats), { viewModel.closeStats() }) {
            StatsScreen(viewModel = viewModel, onBack = { viewModel.closeStats() })
        })
        add(
            CurtainSpec(routeGroupId(routeStack), isGroupOpen(routeStack), { viewModel.closeGroup() }) {
                GroupDetailHost(viewModel = viewModel, routes = routeStack, onBack = { viewModel.closeGroup() })
            },
        )
        add(CurtainSpec(Route.Queue.id, isOpen(Route.Queue), { viewModel.closeQueue() }) {
            QueueScreen(viewModel = viewModel, onBack = { viewModel.closeQueue() })
        })
        add(CurtainSpec(Route.Equalizer.id, isOpen(Route.Equalizer), { viewModel.closeEqualizer() }) {
            EqualizerScreen(viewModel = viewModel, onBack = { viewModel.closeEqualizer() })
        })
        // 宽屏时播放页在右栏，不再占一块幕布。
        if (!wide) {
            add(
                CurtainSpec(Route.NowPlaying.id, isOpen(Route.NowPlaying) && playing, {
                    viewModel.closeNowPlayingCurtain()
                }) { nowPlaying() },
            )
        }
    }

    LxShell(
        backdrop = {
            AuroraBackdrop(
                colors = surface.auroraColors,
                animate = rememberMotionEnabled(),
                dim = surface.dim,
                baseColor = surface.baseColor,
                scrims = false,
            )
        },
        tabs = {
            StageContent(
                viewModel = viewModel,
                tab = tab,
                playerState = playerState,
                settings = settings,
                updateState = updateState,
                themeColor = themeColor,
                onOpenQueue = { viewModel.openQueue() },
                onShowEqualizer = { viewModel.openEqualizer() },
                onOpenUpdateDialog = { showUpdateDialog = true },
                onActionTarget = { music -> actionTarget = music; actionRemoveContext = null },
                onActionLibraryTarget = { music, playlistId ->
                    actionTarget = music; actionRemoveContext = playlistId
                },
            )
        },
        dock = {
            MiniPlayer(
                state = playerState,
                themeColor = themeColor,
                surface = surface,
                onClick = { viewModel.openNowPlayingCurtain() },
                onToggle = viewModel::togglePlayPause,
                onNext = viewModel::next,
            )
        },
        curtains = curtains,
        spotlight = if (wide && isOpen(Route.NowPlaying) && playing) {
            { Box(Modifier.fillMaxSize()) { nowPlaying() } }
        } else {
            null
        },
    )

    if (showUpdateDialog) {
        UpdateDialog(
            state = updateState,
            install = updateInstall,
            onDownload = viewModel::downloadUpdate,
            onCancelDownload = viewModel::cancelUpdateDownload,
            onInstalled = {
                viewModel.acknowledgeUpdateInstall()
                showUpdateDialog = false
            },
            onDismiss = {
                viewModel.acknowledgeUpdateInstall()
                showUpdateDialog = false
            },
        )
    }

    actionTarget?.let { music ->
        val isLocal = music.isLocal
        TrackActionsSheet(
            music = music,
            isFavorite = music.id in libraryState.favoriteIds,
            playlists = libraryState.playlists,
            onDismiss = { actionTarget = null; actionRemoveContext = null },
            onToggleFavorite = { viewModel.toggleFavorite(music) },
            onAddToPlaylist = { viewModel.addToPlaylist(it, music) },
            onCreatePlaylist = { name -> viewModel.createPlaylist(name) { id -> viewModel.addToPlaylist(id, music) } },
            onRemoveFromPlaylist = actionRemoveContext?.let { pid -> { viewModel.removeFromPlaylist(pid, music.id) } },
            onRemoveFromLibrary = if (isLocal) ({ viewModel.removeFromLibrary(music) }) else null,
            onDeleteFile = if (isLocal) ({
                viewModel.deleteLocalFile(music) { msg -> toast(context, msg) }
            }) else null,
        )
    }
}
