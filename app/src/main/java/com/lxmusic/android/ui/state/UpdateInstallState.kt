package com.lxmusic.android.ui.state

/**
 * Where an in-app update install currently is.
 *
 * The app downloads the release APK itself rather than handing the user off to
 * a browser: the only thing a GitHub page can do that this cannot is make the
 * user find the right asset among two.
 */
enum class InstallStatus {
    /** Nothing downloading. */
    IDLE,
    /** Bytes are arriving; [UpdateInstallState.progress] is meaningful. */
    DOWNLOADING,
    /** Download finished, checking the file is the one the release published. */
    VERIFYING,
    /** APK on disk and verified; the system installer should be launched. */
    READY,
    /** Failed; [UpdateInstallState.message] says why. */
    FAILED,
}

/**
 * State of one in-app update attempt.
 *
 * [filePath] is a plain path rather than a Uri because this object is produced
 * off the main thread and never touches Android types; the UI builds the
 * content:// Uri when it launches the installer.
 *
 * [seq] increments on every state change that the UI may want to react to
 * (a fresh download starting, or READY being reached), so a `LaunchedEffect`
 * keyed on it fires exactly once per event rather than once per recomposition.
 */
data class UpdateInstallState(
    val status: InstallStatus = InstallStatus.IDLE,
    val written: Long = 0L,
    val total: Long = 0L,
    val filePath: String? = null,
    val message: String? = null,
    val seq: Int = 0,
) {
    /** 0f..1f; 0f when the server sent no Content-Length. */
    val progress: Float
        get() = if (total <= 0L) 0f else (written.toDouble() / total.toDouble()).toFloat().coerceIn(0f, 1f)

    /** True when the total is unknown and the UI should show an indeterminate bar. */
    val isIndeterminate: Boolean
        get() = total <= 0L && status == InstallStatus.DOWNLOADING

    /** "4.3 / 15.9 MB", or "4.3 MB" when the total is unknown. */
    val sizeText: String
        get() = if (total <= 0L) mb(written) else "${mb(written)} / ${mb(total)}"

    private fun mb(bytes: Long): String = "%.1f MB".format(bytes / 1_000_000.0)
}
