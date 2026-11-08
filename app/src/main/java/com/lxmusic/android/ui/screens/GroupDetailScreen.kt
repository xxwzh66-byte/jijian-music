package com.lxmusic.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.lxmusic.android.local.LocalGroup
import com.lxmusic.android.local.MusicGroup
import com.lxmusic.android.ui.MainViewModel
import com.lxmusic.android.ui.components.Cover
import com.lxmusic.android.ui.components.TrackRow
import com.lxmusic.android.ui.neon.AuroraBackdrop
import com.lxmusic.android.ui.neon.NeonGlassAction
import com.lxmusic.android.ui.neon.NeonHairline
import com.lxmusic.android.ui.neon.NeonTokens
import com.lxmusic.android.ui.neon.rememberMotionEnabled
import com.lxmusic.android.ui.neon.rememberNeonSurface
import com.lxmusic.android.ui.shell.Route
import com.lxmusic.android.ui.shell.topGroupRoute
import com.lxmusic.android.ui.theme.OledTokens

/**
 * Host for the shared album / artist curtain.
 *
 * One curtain serves every group page: it reads the topmost [Route.Group] off
 * the stack and resolves it against the live library, so switching from an
 * album to its artist reuses the same composition instead of restarting the
 * entry animation. Resolving by key (not by captured object) also means the
 * page survives the library being rescanned underneath it.
 */
@Composable
fun GroupDetailHost(viewModel: MainViewModel, routes: List<Route>, onBack: () -> Unit) {
    // The curtain stays composed during its 260 ms exit, by which time the route
    // is already gone from the stack. Hold the last route so the page does not
    // blank out mid-animation.
    var last by remember { mutableStateOf<Route.Group?>(null) }
    val top = topGroupRoute(routes)
    if (top != null) last = top
    val group = top ?: last ?: return
    val resolved = viewModel.groupByKey(group)
    // The album vanished - a rescan after files moved, or a stale stack entry
    // after a delete. Rather than show a blank page, close it.
    LaunchedEffect(resolved == null) { if (resolved == null) onBack() }
    if (resolved == null) return
    GroupDetailScreen(
        group = resolved,
        onBack = onBack,
        onPlayAll = { viewModel.playGroup(resolved) },
        onPlayFrom = { index -> viewModel.playGroup(resolved, index) },
        onShuffleAll = { viewModel.playGroupShuffled(resolved) },
    )
}

/**
 * The album / artist page: a cover, the facts that belong on a record sleeve,
 * and the track list.
 *
 * Deliberately not a discovery surface. There is no "related albums", no
 * "you might also like" - the point of this screen is the one thing the grouped
 * list could not show: what is actually on this record, in order.
 */
@Composable
fun GroupDetailScreen(
    group: MusicGroup,
    onBack: () -> Unit,
    onPlayAll: () -> Unit,
    onPlayFrom: (Int) -> Unit,
    onShuffleAll: () -> Unit,
) {
    val surface = rememberNeonSurface(
        dark = MaterialTheme.colorScheme.surface.luminance() <= 0.5f,
        themeColor = "default",
    )
    val motion = rememberMotionEnabled()
    val isArtist = group.key.startsWith("artist:")

    Box(Modifier.fillMaxSize()) {
        AuroraBackdrop(
            colors = surface.auroraColors,
            animate = motion,
            dim = surface.dim,
            baseColor = surface.baseColor,
            scrims = false,
        )
        Column(Modifier.fillMaxSize()) {
            GroupTopBar(
                title = if (isArtist) "歌手" else "专辑",
                onBack = onBack,
                onShuffle = onShuffleAll,
            )
            LazyColumn(
                contentPadding = PaddingValues(bottom = 96.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item { GroupHeader(group, isArtist) }
                item { GroupActions(count = group.count, onPlayAll = onPlayAll) }
                itemsIndexed(group.tracks, key = { _, m -> m.id }) { i, music ->
                    TrackRow(
                        music = music,
                        onClick = { onPlayFrom(i) },
                        trailing = {
                            Text(
                                "${i + 1}".padStart(2, '0'),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 12.sp,
                            )
                        },
                    )
                    NeonHairline()
                }
            }
        }
    }
}

@Composable
private fun GroupTopBar(title: String, onBack: () -> Unit, onShuffle: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NeonGlassAction(Icons.AutoMirrored.Filled.ArrowBack, "返回", "返回上一页") { onBack() }
        Spacer(Modifier.width(12.dp))
        Box(
            Modifier
                .width(3.dp)
                .height(18.dp)
                .background(
                    Brush.verticalGradient(NeonTokens.brandGradient("default")),
                    RoundedCornerShape(2.dp),
                ),
        )
        Spacer(Modifier.width(9.dp))
        Text(title, fontSize = 19.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        NeonGlassAction(Icons.Default.Shuffle, "随机播放", hint = "打乱这张的顺序") { onShuffle() }
    }
}

@Composable
private fun GroupHeader(group: MusicGroup, isArtist: Boolean) {
    Column(Modifier.fillMaxWidth().padding(horizontal = OledTokens.screenMargin)) {
        Row(verticalAlignment = Alignment.Bottom) {
            Cover(group.coverUrl, 132.dp)
            Spacer(Modifier.width(OledTokens.spaceLg))
            Column(Modifier.weight(1f)) {
                Text(
                    group.title,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(OledTokens.spaceXs))
                Text(
                    // An album's subtitle is its artist, which is the one fact a
                    // record sleeve leads with. An artist's own subtitle would
                    // just repeat the track count shown on the next line.
                    if (isArtist) "" else group.subtitle,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(OledTokens.spaceXs))
                Text(
                    "${group.count} 首 · ${group.totalDuration}",
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun GroupActions(count: Int, onPlayAll: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = OledTokens.screenMargin, vertical = OledTokens.spaceMd),
        horizontalArrangement = Arrangement.spacedBy(OledTokens.spaceSm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            Modifier
                .weight(1f)
                .height(44.dp)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(OledTokens.radiusMd))
                .clickable(enabled = count > 0, onClick = onPlayAll),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Default.PlayArrow, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text(
                "播放全部",
                color = MaterialTheme.colorScheme.onPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}
