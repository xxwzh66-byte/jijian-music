package com.lxmusic.android.ui

/**
 * Aliases kept so the UI layer's existing imports resolve after the state
 * classes moved into [com.lxmusic.android.ui.state] with the store split.
 *
 * They forward, they do not duplicate: a screen importing
 * `com.lxmusic.android.ui.UpdateStatus` gets the same enum the stores use.
 */
typealias LocalUiState = com.lxmusic.android.ui.state.LocalUiState
typealias LibraryUiState = com.lxmusic.android.ui.state.LibraryUiState
typealias StatsUiState = com.lxmusic.android.ui.state.StatsUiState
typealias UpdateCheckState = com.lxmusic.android.ui.state.UpdateCheckState
typealias UpdateStatus = com.lxmusic.android.ui.state.UpdateStatus
