package com.lxmusic.android.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File

/** What happened when we tried to hand a downloaded APK to the system installer. */
enum class InstallOutcome {
    /** The system installer came up; the user decides the rest. */
    LAUNCHED,

    /** This app is not allowed to install packages yet; send the user to grant it. */
    NEEDS_PERMISSION,

    /** The file vanished or no activity would take the intent. */
    FAILED,
}

/**
 * Hands a downloaded APK to the package installer.
 *
 * On Android 8+ `REQUEST_INSTALL_PACKAGES` is a per-app permission the user
 * grants in Settings, and there is no runtime prompt for it - the only way in
 * is `ACTION_MANAGE_UNKNOWN_APP_SOURCES`. So the first attempt usually returns
 * [InstallOutcome.NEEDS_PERMISSION], the UI offers to open that screen, and the
 * user taps install again once they are back.
 */
object UpdateInstaller {

    /** Authority declared in AndroidManifest; `${applicationId}` differs for debug. */
    private fun authority(context: Context) = "${context.packageName}.fileprovider"

    fun canInstall(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }

    /** Opens the OS screen where this app is allowed to install packages. */
    fun requestInstallPermission(context: Context): Boolean = runCatching {
        context.startActivity(
            Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}"),
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        true
    }.getOrDefault(false)

    /**
     * Launches the installer for [path].
     *
     * The APK is exposed through a [FileProvider] because `file://` URIs have
     * thrown FileUriExposedException since Android 7 - the cache directory this
     * app downloads into is private to it.
     */
    fun install(context: Context, path: String?): InstallOutcome {
        val file = path?.let(::File) ?: return InstallOutcome.FAILED
        if (!file.isFile || file.length() <= 0L) return InstallOutcome.FAILED
        if (!canInstall(context)) return InstallOutcome.NEEDS_PERMISSION

        val uri = runCatching {
            FileProvider.getUriForFile(context, authority(context), file)
        }.getOrNull() ?: return InstallOutcome.FAILED

        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        return runCatching {
            context.startActivity(intent)
            InstallOutcome.LAUNCHED
        }.getOrElse { InstallOutcome.FAILED }
    }
}
