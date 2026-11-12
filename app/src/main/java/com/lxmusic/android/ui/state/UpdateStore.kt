package com.lxmusic.android.ui.state

import com.lxmusic.android.BuildConfig
import com.lxmusic.android.data.settings.SettingsStore
import com.lxmusic.android.net.ApkDownloader
import com.lxmusic.android.net.UpdateChecker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Owns both halves of "is there a newer version, and can I get it".
 *
 * [updateCheck] answers the first question (GitHub `releases/latest`, compared
 * against [BuildConfig.VERSION_NAME]). [install] answers the second by pulling
 * the APK itself, so updating never leaves the app.
 */
class UpdateStore(
    private val settingsStore: SettingsStore,
    private val scope: CoroutineScope,
    /** Where finished APKs live. Cache-dir based; see MainViewModel. */
    private val apkDir: () -> File,
    private val downloader: () -> ApkDownloader = { ApkDownloader() },
    private val checker: () -> UpdateChecker = { UpdateChecker() },
) {

    private val _update = MutableStateFlow(UpdateCheckState(currentVersion = BuildConfig.VERSION_NAME))
    val updateCheck: StateFlow<UpdateCheckState> = _update.asStateFlow()

    private val _install = MutableStateFlow(UpdateInstallState())
    val install: StateFlow<UpdateInstallState> = _install.asStateFlow()

    private var seq = 0
    private var installSeq = 0

    /** The running download, so [cancelInstall] can actually stop it. */
    private var installJob: Job? = null

    fun check() = run(silent = false)

    /**
     * Every launch checks, and only a newer version interrupts the user.
     *
     * V1.4.5 删掉了「启动时自动检查更新」这个开关，也删掉了 24 小时节流：开关和节流
     * 加起来的效果是「用户以为在查，其实没查」。现在每次进应用都查一次，但仍然静默 ——
     * 失败与「已是最新」都退回 IDLE，不往设置页塞一条用户没要的状态。
     */
    fun checkOnStartup() {
        if (_update.value.status != UpdateStatus.IDLE) return
        settingsStore.setLastUpdateCheckAt(System.currentTimeMillis())
        run(silent = true)
    }

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

    // ---------------- in-app install ----------------

    /**
     * Downloads the APK that [updateCheck] found and leaves it verified on disk.
     *
     * The download URL comes from the release metadata, not from a string the
     * user typed, and the file is checked against the digest GitHub published;
     * a mismatch deletes the file rather than offering it to the installer.
     */
    fun downloadUpdate() {
        val info = _update.value.latest ?: return
        if (_update.value.status != UpdateStatus.AVAILABLE) return
        val url = info.apkUrl
        if (url.isNullOrBlank()) {
            failInstall("这个版本没有提供安装包，请到发布页下载")
            return
        }
        if (_install.value.status == InstallStatus.DOWNLOADING) return

        installSeq += 1
        _install.value = UpdateInstallState(
            status = InstallStatus.DOWNLOADING,
            total = info.apkSize,
            seq = installSeq,
        )

        installJob?.cancel()
        installJob = scope.launch {
            val stored = withContext(Dispatchers.IO) {
                val keep = ApkDownloader.fileNameFor(info.apkName)
                // Remove installers belonging to other versions - a stale APK of
                // an older release sitting in the cache is one more thing that
                // can be run by mistake - while keeping this asset's own
                // `<name>.part`, which is where a dropped transfer resumes from.
                purgeApkDir(keepName = keep, keepPart = true)
                val dest = File(apkDir(), keep)

                val download = downloader().download(url, dest) { written, total ->
                    _install.value = _install.value.copy(
                        status = InstallStatus.DOWNLOADING,
                        written = written,
                        total = if (total > 0L) total else _install.value.total,
                    )
                }
                val ok = download.getOrNull()
                if (ok == null) {
                    Result.failure(download.exceptionOrNull() ?: IllegalStateException("下载失败"))
                } else {
                    _install.value = _install.value.copy(status = InstallStatus.VERIFYING)
                    val actual = ApkDownloader.sha256(ok)
                    if (ApkDownloader.digestMatches(info.apkDigest, actual)) {
                        Result.success(ok)
                    } else {
                        ok.delete()
                        Result.failure(IllegalStateException("安装包校验失败，已丢弃"))
                    }
                }
            }

            if (!isActive) return@launch
            installJob = null
            installSeq += 1
            _install.value = stored.fold(
                onSuccess = { file ->
                    UpdateInstallState(
                        status = InstallStatus.READY,
                        written = file.length(),
                        total = file.length(),
                        filePath = file.absolutePath,
                        seq = installSeq,
                    )
                },
                onFailure = { e ->
                    UpdateInstallState(
                        status = InstallStatus.FAILED,
                        message = e.message ?: "下载失败",
                        seq = installSeq,
                    )
                },
            )
        }
    }

    /**
     * Stops a running download.
     *
     * Cancelling the job rather than only resetting the flow matters: without it
     * the coroutine keeps streaming 16 MB in the background and then overwrites
     * the state with READY, offering to install a file the user just refused.
     */
    fun cancelInstall() {
        if (_install.value.status == InstallStatus.IDLE) return
        installJob?.cancel()
        installJob = null
        installSeq += 1
        // Deliberately leaves the `.part` file alone: it is the resume point if
        // the user changes their mind. A cancelled download costing a few MB of
        // cache is cheaper than a cancelled download costing all fifteen.
        _install.value = UpdateInstallState(seq = installSeq)
    }

    /** Called once the installer has been launched or declined. */
    fun acknowledgeInstall() {
        installSeq += 1
        _install.value = UpdateInstallState(seq = installSeq)
    }

    /** Deletes every file this store ever put in [apkDir]; safe to call twice. */
    private fun purgeApkDir(keepName: String? = null, keepPart: Boolean = false) {
        runCatching {
            val dir = apkDir()
            dir.mkdirs()
            dir.listFiles()?.forEach { f ->
                if (!f.isFile) return@forEach
                if (keepName != null) {
                    // The finished package and its `.part` sibling are the
                    // in-progress download for the version we are about to
                    // fetch; deleting them would turn every retry into a full
                    // re-download, which is the whole point of V1.1.8.
                    if (f.name == keepName) return@forEach
                    if (keepPart && f.name == "$keepName.part") return@forEach
                }
                f.delete()
            }
        }
    }

    private fun failInstall(message: String) {
        installSeq += 1
        _install.value = UpdateInstallState(InstallStatus.FAILED, message = message, seq = installSeq)
    }

}
