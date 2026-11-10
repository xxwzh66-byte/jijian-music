package com.lxmusic.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lxmusic.android.data.repo.PlaylistWithTracks
import com.lxmusic.android.local.MusicGroup
import com.lxmusic.android.local.SearchResults
import com.lxmusic.android.ui.MainViewModel
import com.lxmusic.android.ui.components.Cover
import com.lxmusic.android.ui.components.describedAs
import com.lxmusic.android.ui.components.TrackDescription
import com.lxmusic.android.ui.components.EmptyState
import com.lxmusic.android.ui.components.SearchBar
import com.lxmusic.android.ui.components.SectionHeader
import com.lxmusic.android.ui.components.TrackRow
import com.lxmusic.android.ui.neon.AuroraBackdrop
import com.lxmusic.android.ui.neon.NeonGlassAction
import com.lxmusic.android.ui.neon.NeonHairline
import com.lxmusic.android.ui.neon.NeonTokens
import com.lxmusic.android.ui.neon.rememberMotionEnabled
import com.lxmusic.android.ui.neon.rememberNeonSurface
import com.lxmusic.android.ui.theme.OledTokens

/**
 * 统一搜索页。
 *
 * 与首页列表里那个只过滤歌曲的输入框不同，这一页一次回答五个问题：这首歌、
 * 我收藏过没有、它属于哪张专辑、谁唱的、在哪个歌单里。五段按"越具体越靠前"
 * 排列——歌曲永远第一，因为绝大多数搜索都是一首具体的歌。
 *
 * 每段只出现非空的结果；全部为空时才显示一个空状态，避免没有歌单的人看到
 * 四个"暂无"。
 */
@Composable
fun SearchScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val results by viewModel.search.collectAsState()
    val themeColor = viewModel.settings.collectAsState().value.themeColor
    val surface = rememberNeonSurface(
        dark = MaterialTheme.colorScheme.surface.luminance() <= 0.5f,
        themeColor = themeColor,
    )
    val motion = rememberMotionEnabled()

    Box(Modifier.fillMaxSize()) {
        AuroraBackdrop(
            colors = surface.auroraColors,
            animate = motion,
            dim = surface.dim,
            baseColor = surface.baseColor,
            scrims = false,
        )
        Column(Modifier.fillMaxSize().navigationBarsPadding()) {
            SearchTopBar(
                query = results.query,
                onQueryChange = { viewModel.setSearchQuery(it) },
                onBack = onBack,
            )
            if (results.isEmpty) {
                SearchEmpty(query = results.query)
            } else {
                SearchBody(
                    results = results,
                    onPlayTrack = { index -> viewModel.playSearchTracks(index) },
                    onPlayFavorite = { index -> viewModel.playSearchFavorites(index) },
                    onOpenGroup = { key -> viewModel.openGroupByKey(key) },
                    onPlayPlaylist = { playlist -> viewModel.playPlaylist(playlist) },
                )
            }
        }
    }
}

@Composable
private fun SearchTopBar(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeonGlassAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", "返回上一页") { onBack() }
        Spacer(Modifier.width(10.dp))
        SearchBar(
            value = query,
            onValueChange = onQueryChange,
            placeholder = "歌曲、专辑、歌手、歌单",
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun SearchBody(
    results: SearchResults,
    onPlayTrack: (Int) -> Unit,
    onPlayFavorite: (Int) -> Unit,
    onOpenGroup: (String) -> Unit,
    onPlayPlaylist: (PlaylistWithTracks) -> Unit,
) {
    LazyColumn(
        contentPadding = PaddingValues(bottom = 96.dp),
        modifier = Modifier.fillMaxSize(),
    ) {
        if (results.tracks.isNotEmpty()) {
            item { SectionHeader("歌曲", count = results.tracks.size) }
            itemsIndexed(results.tracks, key = { _, m -> "t:" + m.id }) { i, music ->
                TrackRow(music = music, onClick = { onPlayTrack(i) })
                NeonHairline()
            }
        }
        if (results.favorites.isNotEmpty()) {
            item { SectionHeader("收藏", count = results.favorites.size) }
            itemsIndexed(results.favorites, key = { _, m -> "f:" + m.id }) { i, music ->
                TrackRow(music = music, onClick = { onPlayFavorite(i) })
                NeonHairline()
            }
        }
        if (results.albums.isNotEmpty()) {
            item { SectionHeader("专辑", count = results.albums.size) }
            items(results.albums, key = { "a:" + it.key }) { group ->
                ResultGroupRow(group, isArtist = false) { onOpenGroup(group.key) }
                NeonHairline()
            }
        }
        if (results.artists.isNotEmpty()) {
            item { SectionHeader("歌手", count = results.artists.size) }
            items(results.artists, key = { "r:" + it.key }) { group ->
                ResultGroupRow(group, isArtist = true) { onOpenGroup(group.key) }
                NeonHairline()
            }
        }
        if (results.playlists.isNotEmpty()) {
            item { SectionHeader("歌单", count = results.playlists.size) }
            items(results.playlists, key = { "p:" + it.id }) { playlist ->
                ResultPlaylistRow(playlist) { onPlayPlaylist(playlist) }
                NeonHairline()
            }
        }
    }
}

@Composable
private fun ResultGroupRow(group: MusicGroup, isArtist: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .describedAs(TrackDescription.open(group.title, group.count))
            .padding(horizontal = OledTokens.screenMargin, vertical = OledTokens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(group.coverUrl, if (isArtist) 44.dp else 48.dp)
        Spacer(Modifier.width(OledTokens.spaceMd))
        Column(Modifier.weight(1f)) {
            Text(
                group.title,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                if (isArtist) "${group.count} 首" else "${group.subtitle} · ${group.count} 首",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Icon(
            Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun ResultPlaylistRow(playlist: PlaylistWithTracks, onPlay: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onPlay)
            .describedAs(TrackDescription.play(playlist.name, playlist.tracks.size))
            .padding(horizontal = OledTokens.screenMargin, vertical = OledTokens.spaceMd),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Cover(playlist.coverUrl, 48.dp)
        Spacer(Modifier.width(OledTokens.spaceMd))
        Column(Modifier.weight(1f)) {
            Text(
                playlist.name,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                "${playlist.tracks.size} 首",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun SearchEmpty(query: String) {
    if (query.isBlank()) {
        EmptyState(
            text = "搜索歌曲、专辑、歌手与歌单",
            icon = Icons.Default.Search,
        )
    } else {
        EmptyState(
            text = "没有找到「$query」",
            icon = Icons.Default.Search,
        )
    }
}
