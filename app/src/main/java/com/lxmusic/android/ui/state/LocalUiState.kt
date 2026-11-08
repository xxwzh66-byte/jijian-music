package com.lxmusic.android.ui.state

import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.local.LocalGroup
import com.lxmusic.android.local.LocalSort
import com.lxmusic.android.local.MusicGroup

data class LocalUiState(
    val loading: Boolean = false,
    val allTracks: List<MusicInfo> = emptyList(),
    val group: LocalGroup = LocalGroup.SONGS,
    val sort: LocalSort = LocalSort.TITLE_ASC,
    val query: String = "",
    /** Current visible songs (respecting search + group). */
    val songs: List<MusicInfo> = emptyList(),
    /** Album/artist groups (empty when grouping by songs). */
    val groups: List<MusicGroup> = emptyList(),
    val error: String? = null,
)
