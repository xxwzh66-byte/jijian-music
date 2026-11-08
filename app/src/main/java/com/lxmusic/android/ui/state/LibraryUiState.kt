package com.lxmusic.android.ui.state

import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.data.repo.PlaylistWithTracks

data class LibraryUiState(
    val favorites: List<MusicInfo> = emptyList(),
    val recent: List<MusicInfo> = emptyList(),
    val playlists: List<PlaylistWithTracks> = emptyList(),
    val favoriteIds: Set<String> = emptySet(),
    val playingPlaylistId: Long? = null,
)
