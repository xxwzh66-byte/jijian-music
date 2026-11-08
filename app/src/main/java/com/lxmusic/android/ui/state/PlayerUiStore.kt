package com.lxmusic.android.ui.state

import android.app.Application
import com.lxmusic.android.data.model.MusicInfo
import com.lxmusic.android.data.settings.AppSettings
import com.lxmusic.android.data.settings.SettingsStore
import com.lxmusic.android.player.EqPreset
import com.lxmusic.android.player.EqualizerState
import com.lxmusic.android.player.PlayerController
import com.lxmusic.android.player.PlayerState
import com.lxmusic.android.player.RepeatMode
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * The UI-facing half of playback.
 *
 * [PlayerController] owns the ExoPlayer and the queue; this class mirrors its
 * state into Compose-friendly flows, wires the persisted repeat/shuffle modes,
 * and owns the debounced equalizer snapshot.
 */
class PlayerUiStore(
    app: Application,
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
) {

    val controller = PlayerController(app)

    private val _player = MutableStateFlow(PlayerState())
    val player: StateFlow<PlayerState> = _player.asStateFlow()

    private val _equalizer = MutableStateFlow(EqualizerState())
    val equalizer: StateFlow<EqualizerState> = _equalizer.asStateFlow()

    /** Fired when a user tap should open the full-screen player. */
    val openNowPlaying = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    /** Start playback AND open the full-screen player. */
    fun playAndOpen(list: List<MusicInfo>, index: Int) {
        if (list.isEmpty() || index !in list.indices) return
        controller.setQueue(list, index)
        openNowPlaying.tryEmit(Unit)
    }

    /** Start playback without taking over the screen. */
    fun play(list: List<MusicInfo>, index: Int) {
        if (list.isEmpty() || index !in list.indices) return
        controller.setQueue(list, index)
    }

    /** Replays the persisted repeat / shuffle modes and the EQ curve. */
    fun applyPersistedModes(st: AppSettings) {
        controller.applyPersistedModes(
            when (st.repeatMode) {
                "ALL" -> RepeatMode.ALL
                "ONE" -> RepeatMode.ONE
                else -> RepeatMode.OFF
            },
            st.shuffle,
        )
        controller.equalizer.setProfile(st.eq)
    }

    /** Mirrors lyric / watchdog / eq preferences onto the player. */
    fun applyPreferences(s: AppSettings) {
        controller.lyricController.offsetMs = s.lyricOffsetMs
        controller.lyricController.preferLrcFile = s.preferLrcFile
        controller.lyricController.readEmbedded = true
        controller.watchdog.stallMs = s.watchdogStallMs
        controller.watchdog.bufferingMs = s.watchdogBufferingMs
    }

    fun startMirrors() {
        scope.launch { controller.state.collect { _player.value = it } }
        controller.equalizer.onStateChanged = { _equalizer.value = it }
        _equalizer.value = controller.equalizer.state
    }

    // ---------------- transport ----------------

    fun togglePlayPause() = controller.togglePlayPause()
    fun pause() {
        if (controller.state.value.isPlaying) controller.togglePlayPause()
    }
    fun seekTo(ms: Long) = controller.seekTo(ms)
    fun next() { scope.launch { controller.next() } }
    fun prev() { scope.launch { controller.prev() } }
    fun cycleRepeat() = controller.cycleRepeat()
    fun toggleShuffle() = controller.toggleShuffle()

    // ---------------- queue ----------------

    fun queueJumpTo(index: Int) = controller.playAt(index)
    fun queueMove(from: Int, to: Int) = controller.moveInQueue(from, to)
    fun queueRemove(index: Int) = controller.removeFromQueue(index)
    fun swapNext(list: List<MusicInfo>) = controller.playNext(list)
    fun append(list: List<MusicInfo>) = controller.enqueue(list)
    fun clearQueue() = controller.clearQueue()

    /** The track the last [queueRemove] took out (for the undo snackbar). */
    val lastQueueRemoval: Pair<MusicInfo, Int>? get() = controller.lastRemoved

    fun queueUndoRemove() = controller.undoQueueRemoval()

    // ---------------- equalizer ----------------

    // Every change writes a snapshot back: the effects are bound to the audio
    // session and reset whenever the player is rebuilt, so mutating them without
    // persisting means the curve is gone on the next launch.
    fun eqSetEnabled(enabled: Boolean) = controller.equalizer.setEnabled(enabled).also { persistEq() }
    fun eqSetBand(bandIndex: Int, levelMb: Short) =
        controller.equalizer.setBandLevel(bandIndex, levelMb).also { persistEq() }
    fun eqApplyPreset(preset: EqPreset) = controller.equalizer.applyPreset(preset).also { persistEq() }
    fun eqSetBass(strength: Int) = controller.equalizer.setBassStrength(strength).also { persistEq() }
    fun eqSetVirtualizer(strength: Int) =
        controller.equalizer.setVirtualizerStrength(strength).also { persistEq() }
    fun eqSetLoudness(gainMb: Int) = controller.equalizer.setLoudnessGainMb(gainMb).also { persistEq() }
    fun eqReset() = controller.equalizer.reset().also { persistEq() }

    private var eqPersistJob: Job? = null

    /** A change is pending; [release] uses it to decide whether to flush. */
    @Volatile private var eqDirty = false

    private fun persistEq() {
        // Slider onValueChange fires per frame; writing DataStore on every one of
        // them is pointless. Collapse to "one write 400ms after the finger stops".
        eqDirty = true
        eqPersistJob?.cancel()
        eqPersistJob = scope.launch {
            delay(EQ_PERSIST_DEBOUNCE_MS)
            writeEqNow()
        }
    }

    /**
     * Flush the curve immediately.
     *
     * If the screen closes inside the debounce window the pending job dies with
     * the scope: the change is already audible but never saved, and the next
     * launch returns to the previous curve.
     */
    fun writeEqNow() {
        eqDirty = false
        settingsStore.setEq(controller.equalizer.snapshot())
    }

    fun release() {
        if (eqDirty) {
            eqPersistJob?.cancel()
            writeEqNow()
        }
        controller.release()
    }

    private companion object {
        const val EQ_PERSIST_DEBOUNCE_MS = 400L
    }
}
