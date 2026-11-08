package com.lxmusic.android.ui.state

import android.app.Application
import android.net.Uri
import com.lxmusic.android.data.backup.BackupArchive
import com.lxmusic.android.data.backup.BackupRepository
import com.lxmusic.android.data.backup.BackupReport
import com.lxmusic.android.data.settings.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Where a backup operation stands, for the settings row's subtitle. */
enum class BackupStatus { IDLE, EXPORTING, IMPORTING, DONE, FAILED }

data class BackupUiState(
    val status: BackupStatus = BackupStatus.IDLE,
    val message: String? = null,
    val lastReport: BackupReport? = null,
)

/**
 * Drives export / import from the settings screen.
 *
 * The SAF plumbing lives here rather than in the composable so that the picker
 * callbacks stay one line at the call site and the IO happens off the main
 * thread. [BackupRepository] does the actual merging.
 */
class BackupStore(
    private val app: Application,
    settingsStore: SettingsStore,
    private val scope: CoroutineScope,
    private val localTracks: () -> List<com.lxmusic.android.data.model.MusicInfo>,
) {

    private val repo = BackupRepository(app, settingsStore)

    private val _state = MutableStateFlow(BackupUiState())
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    /** Writes a backup to [uri] (already created by the system file picker). */
    fun export(uri: Uri, appVersion: String) {
        if (_state.value.status == BackupStatus.EXPORTING) return
        _state.value = BackupUiState(status = BackupStatus.EXPORTING)
        scope.launch {
            val result = runCatching {
                val file = repo.export(appVersion)
                withContext(Dispatchers.IO) {
                    val stream = app.contentResolver.openOutputStream(uri)
                        ?: error("无法写入所选位置")
                    stream.use { BackupArchive.write(it, file) }
                }
                file
            }
            _state.value = if (result.isSuccess) {
                val file = result.getOrThrow()
                BackupUiState(
                    status = BackupStatus.DONE,
                    message = "已导出 ${file.tracks.size} 首曲目信息、${file.playlists.size} 个歌单",
                )
            } else {
                BackupUiState(status = BackupStatus.FAILED, message = result.exceptionOrNull()?.message ?: "导出失败")
            }
        }
    }

    /** Reads a backup from [uri] and merges it in. */
    fun import(uri: Uri) {
        if (_state.value.status == BackupStatus.IMPORTING) return
        _state.value = BackupUiState(status = BackupStatus.IMPORTING)
        scope.launch {
            val result = runCatching {
                val file = withContext(Dispatchers.IO) {
                    val stream = app.contentResolver.openInputStream(uri)
                        ?: error("无法读取所选文件")
                    stream.use { BackupArchive.read(it) }
                } ?: error("这不是有效的备份文件")
                repo.import(file, localTracks())
            }
            _state.value = if (result.isSuccess) {
                val report = result.getOrThrow()
                BackupUiState(
                    status = BackupStatus.DONE,
                    message = report.summary,
                    lastReport = report,
                )
            } else {
                BackupUiState(status = BackupStatus.FAILED, message = result.exceptionOrNull()?.message ?: "导入失败")
            }
        }
    }

    /** Clears the result line once the user has read it. */
    fun acknowledge() {
        if (_state.value.status == BackupStatus.DONE || _state.value.status == BackupStatus.FAILED) {
            _state.value = BackupUiState()
        }
    }
}
