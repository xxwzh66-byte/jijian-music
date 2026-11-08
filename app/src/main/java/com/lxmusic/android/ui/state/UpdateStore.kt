package com.lxmusic.android.ui.state

import com.lxmusic.android.BuildConfig
import com.lxmusic.android.data.settings.SettingsStore
import com.lxmusic.android.net.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Owns the "check for updates" flow.
 *
 * Fetches `releases/latest` from GitHub and compares the tag against
 * [BuildConfig.VERSION_NAME]. All network work happens on IO; the result lands
 * in [updateCheck] and the UI decides whether to surface it.
 */
class UpdateStore(
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
    private val checker: () -> UpdateChecker = { UpdateChecker() },
) {

    private val _update = MutableStateFlow(UpdateCheckState(currentVersion = BuildConfig.VERSION_NAME))
    val updateCheck: StateFlow<UpdateCheckState> = _update.asStateFlow()

    private var seq = 0

    fun check() = run(silent = false)

    /**
     * Startup check, throttled to once per [UPDATE_CHECK_INTERVAL_MS].
     *
     * Shares the result flow with [check] but only interrupts the user when a
     * newer version really exists: a silent failure or "already latest" falls
     * back to IDLE, because this app may be offline for its whole life and the
     * settings subtitle should not report a check the user never asked for.
     */
    fun maybeCheckOnStartup() {
        if (_update.value.status != UpdateStatus.IDLE) return
        if (!settingsStore.state.value.autoCheckUpdates) return
        val now = System.currentTimeMillis()
        if (now - settingsStore.state.value.lastUpdateCheckAt < UPDATE_CHECK_INTERVAL_MS) return
        settingsStore.setLastUpdateCheckAt(now)
        run(silent = true)
    }

    fun setAutoCheckUpdates(on: Boolean) = settingsStore.setAutoCheckUpdates(on)

    private fun run(silent: Boolean) {
        if (_update.value.status == UpdateStatus.CHECKING) return
        val current = BuildConfig.VERSION_NAME
        _update.value = UpdateCheckState(status = UpdateStatus.CHECKING, currentVersion = current)
        scope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { checker().fetchLatest().getOrThrow() }
            }
            seq += 1
            _update.value = result.fold(
                onSuccess = { info ->
                    when {
                        UpdateChecker.isNewer(info.tagName, current) ->
                            UpdateCheckState(UpdateStatus.AVAILABLE, current, info, checkSeq = seq)
                        silent -> UpdateCheckState(currentVersion = current)
                        else ->
                            UpdateCheckState(UpdateStatus.UP_TO_DATE, current, info, checkSeq = seq)
                    }
                },
                onFailure = { e ->
                    when {
                        silent -> UpdateCheckState(currentVersion = current)
                        else -> UpdateCheckState(
                            status = UpdateStatus.FAILED,
                            currentVersion = current,
                            message = e.message ?: "检查更新失败",
                            checkSeq = seq,
                        )
                    }
                },
            )
        }
    }

    private companion object {
        /** Startup silent-check throttle (24h). */
        const val UPDATE_CHECK_INTERVAL_MS = 24 * 60 * 60 * 1000L
    }
}
