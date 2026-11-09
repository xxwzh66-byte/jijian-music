package com.lxmusic.android.ui.screens

import androidx.compose.foundation.background
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import com.lxmusic.android.data.settings.ThemeMode
import com.lxmusic.android.ui.MainViewModel
import com.lxmusic.android.ui.UpdateCheckState
import com.lxmusic.android.ui.UpdateStatus
import com.lxmusic.android.ui.neon.NeonHairline
import com.lxmusic.android.ui.neon.NeonScreen
import com.lxmusic.android.ui.neon.NeonTokens
import com.lxmusic.android.ui.theme.AppPalettes
import com.lxmusic.android.ui.components.toast
import com.lxmusic.android.ui.state.BackupStatus
import com.lxmusic.android.ui.state.BackupUiState
import com.lxmusic.android.ui.state.InstallStatus
import com.lxmusic.android.ui.state.UpdateInstallState
import com.lxmusic.android.update.InstallOutcome
import com.lxmusic.android.update.UpdateInstaller
import androidx.compose.material3.LinearProgressIndicator

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(viewModel: MainViewModel, onBack: () -> Unit) {
    val s by viewModel.settings.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    var showClearDialog by remember { mutableStateOf(false) }

    // ---------------- backup (V1.1.6) ----------------
    val backup by viewModel.backup.collectAsState()
    // 导出用 CreateDocument：系统让用户挑位置和文件名，app 不去猜相册还是下载。
    // MIME 写成 application/octet-stream —— zip 容器没有公认的 audio/media 类型，
    // 用 "application/zip" 会让部分文件管理器把它归到压缩包、改名时连带改后缀。
    val exportPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream"),
    ) { uri: Uri? -> if (uri != null) viewModel.exportBackup(uri) }
    // 导入用 OpenDocument 并限定 octet-stream + zip：不限定的话用户能选任何文件，
    // 选错就只能在解析阶段报「不是有效的备份文件」。
    val importPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? -> if (uri != null) viewModel.importBackup(uri) }

    // ---------------- update (V1.0.2) ----------------
    val up by viewModel.updateCheck.collectAsState()
    val install by viewModel.updateInstall.collectAsState()
    var showUpdateDialog by remember { mutableStateOf(false) }
    // V1.1.4: 折叠状态。外观默认展开，其余收起。
    // 用「以换行分隔的组名」这个字符串存，而不是 Map——rememberSaveable 的
    // 默认 saver 只认 Bundle 能装的类型，Map 进去会直接抛异常。
    var expandedKeys by rememberSaveable { mutableStateOf("外观") }
    val isExpanded: (String) -> Boolean = { key ->
        key in expandedKeys.split('\n')
    }
    val toggle: (String) -> Unit = { key ->
        val keys = expandedKeys.split('\n').filter { it.isNotEmpty() }.toMutableSet()
        if (!keys.add(key)) keys.remove(key)
        expandedKeys = keys.joinToString("\n")
    }
    // 每次检查完成都弹一次「更新内容公告」；用 checkSeq 而不是 status，
    // 因为连续两次都查到 AVAILABLE 也应各弹一次。
    androidx.compose.runtime.LaunchedEffect(up.checkSeq) {
        if (up.checkSeq > 0 && up.status != UpdateStatus.CHECKING) showUpdateDialog = true
    }

    NeonScreen(
        title = "设置",
        onBack = onBack,
        themeColor = s.themeColor,
    ) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 32.dp),
        ) {
            // V1.1.4: 分组可折叠。外观默认展开（最常改），其余收起——
            // 一屏能看完全部四组标题，想调什么再点开，不再靠滚动找。
            item { SectionHeader("外观", expanded = isExpanded("外观")) { toggle("外观") } }
            if (isExpanded("外观")) {
                item {
                    ChoiceRow(
                        title = "主题模式",
                        options = listOf(
                            ThemeMode.SYSTEM to "跟随系统",
                            ThemeMode.LIGHT to "亮色",
                            ThemeMode.DARK to "暗色",
                        ),
                        selected = s.themeMode,
                        onSelect = viewModel::setThemeMode,
                    )
                }

                item {
                    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                        Text("主题色", style = MaterialTheme.typography.bodyLarge)
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            AppPalettes.forEach { p ->
                                val selected = p.id == s.themeColor
                                Box(
                                    Modifier.size(36.dp).clip(CircleShape)
                                        .background(Brush.linearGradient(p.gradient))
                                        .border(
                                            width = if (selected) 3.dp else 0.dp,
                                            color = if (selected) MaterialTheme.colorScheme.onSurface
                                            else MaterialTheme.colorScheme.surface,
                                            shape = CircleShape,
                                        )
                                        .clickable {
                                            toast(context, "切换主题色：" + p.label)
                                            viewModel.setThemeColor(p.id)
                                        },
                                )
                            }
                        }
                    }
                }

                item {
                    SwitchRow(
                        title = "播放页显示歌名信息",
                        checked = s.showPlayerMeta,
                        onCheckedChange = viewModel::setShowPlayerMeta,
                    )
                }
            }

            // ---------------- playback (lyrics folded in) ----------------
            item { SectionHeader("播放与歌词", expanded = isExpanded("播放与歌词")) { toggle("播放与歌词") } }
            if (isExpanded("播放与歌词")) {
                item {
                    SwitchRow(
                        title = "播放时记录历史",
                        checked = s.recordHistory,
                        onCheckedChange = viewModel::setRecordHistory,
                    )
                }
                item {
                    SwitchRow(
                        title = "记录播放统计",
                        checked = s.recordStats,
                        onCheckedChange = viewModel::setRecordStats,
                    )
                }
                item {
                    SwitchRow(
                        title = "耳机拔出时暂停",
                        checked = s.pauseOnHeadsetUnplug,
                        onCheckedChange = viewModel::setPauseOnHeadsetUnplug,
                    )
                }
                item {
                    SliderRow(
                        title = "歌词字体大小",
                        value = s.lyricFontScale,
                        valueRange = 0.7f..1.6f,
                        valueText = "%.2f×".format(s.lyricFontScale),
                        onValueChange = viewModel::setLyricFontScale,
                    )
                }
                item {
                    SliderRow(
                        title = "歌词偏移校准",
                        value = s.lyricOffsetMs.toFloat(),
                        valueRange = -500f..500f,
                        valueText = "${s.lyricOffsetMs} ms",
                        onValueChange = { viewModel.setLyricOffset(it.toInt()) },
                    )
                }
                item {
                    SwitchRow(
                        title = "优先读取同目录 .lrc",
                        checked = s.preferLrcFile,
                        onCheckedChange = viewModel::setPreferLrcFile,
                    )
                }
            }

            // ---------------- music library ----------------
            item { SectionHeader("音乐库", expanded = isExpanded("音乐库")) { toggle("音乐库") } }
            if (isExpanded("音乐库")) {
                item {
                    InfoRow(
                        title = "管理扫描文件夹",
                        subtitle = "已授权 ${s.scanFolders.size} 个文件夹 · 已手动添加 ${s.manualUris.size} 首",
                        onClick = viewModel::openFolderManager,
                    )
                }
                item {
                    SwitchRow(
                        title = "启动时自动扫描",
                        checked = s.autoScanOnStart,
                        onCheckedChange = viewModel::setAutoScan,
                    )
                }
                item {
                    SwitchRow(
                        title = "扫描时读取内嵌封面",
                        checked = s.readEmbeddedCover,
                        onCheckedChange = viewModel::setReadEmbeddedCover,
                    )
                }
            }

            // ---------------- backup ----------------
            item { SectionHeader("备份", expanded = isExpanded("备份")) { toggle("备份") } }
            if (isExpanded("备份")) {
                item {
                    InfoRow(
                        title = "导出备份",
                        subtitle = backupSubtitle(backup),
                        onClick = {
                            viewModel.acknowledgeBackup()
                            exportPicker.launch(backupFileName())
                        },
                    )
                }
                item {
                    InfoRow(
                        title = "从备份恢复",
                        onClick = {
                            viewModel.acknowledgeBackup()
                            importPicker.launch(arrayOf("application/octet-stream", "application/zip"))
                        },
                    )
                }
            }

            // ---------------- update ----------------
            item { SectionHeader("更新", expanded = isExpanded("更新")) { toggle("更新") } }
            if (isExpanded("更新")) {
                item {
                    SwitchRow(
                        title = "启动时自动检查更新",
                        checked = s.autoCheckUpdates,
                        onCheckedChange = viewModel::setAutoCheckUpdates,
                    )
                }
                item {
                    InfoRow(
                        title = "立即检查",
                        subtitle = updateSubtitle(up, viewModel.versionName),
                        onClick = viewModel::checkForUpdates,
                    )
                }
                item {
                    InfoRow(
                        title = "版本",
                        subtitle = viewModel.versionName,
                        onClick = {
                            val cm = context.getSystemService(
                                android.content.Context.CLIPBOARD_SERVICE,
                            ) as android.content.ClipboardManager
                            cm.setPrimaryClip(
                                android.content.ClipData.newPlainText("version", viewModel.versionName),
                            )
                            toast(context, "已复制版本号：" + viewModel.versionName)
                        },
                    )
                }
            }

            item {
                InfoRow(
                    title = "清空数据",
                    danger = true,
                    onClick = { showClearDialog = true },
                )
            }
        }
    }

    if (showClearDialog) {
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            title = { Text("清空数据") },
            text = { Text("将删除所有收藏、自建歌单、播放历史与设置，不可恢复。是否继续？") },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllData(); showClearDialog = false }) { Text("确定") }
            },
            dismissButton = { TextButton(onClick = { showClearDialog = false }) { Text("取消") } },
        )
    }

    if (showUpdateDialog) {
        UpdateDialog(
            state = up,
            install = install,
            onDownload = viewModel::downloadUpdate,
            onCancelDownload = viewModel::cancelUpdateDownload,
            onInstalled = {
                viewModel.acknowledgeUpdateInstall()
                showUpdateDialog = false
            },
            onDismiss = {
                viewModel.acknowledgeUpdateInstall()
                showUpdateDialog = false
            },
        )
    }
}

/** V1.0.2: 「检查更新」一行的副标题，随状态换文案。 */
private fun updateSubtitle(state: UpdateCheckState, current: String): String? = when (state.status) {
    UpdateStatus.IDLE -> null
    UpdateStatus.CHECKING -> "正在检查…"
    UpdateStatus.UP_TO_DATE -> "已是最新版本（$current）"
    UpdateStatus.AVAILABLE -> "有新版本：${state.latest?.tagName ?: "?"}，点按看更新内容"
    UpdateStatus.FAILED -> state.message ?: "检查失败，点按重试"
}

/**
 * 「更新内容公告」对话框，同时也是更新入口。
 *
 * V1.1.7 起不再把用户送去 GitHub：点了「下载并安装」就地把 release 的 APK
 * 拉进 cache，校验过 SHA-256 之后交给系统安装器。进度、失败原因都留在这一屏。
 */
@Composable
internal fun UpdateDialog(
    state: UpdateCheckState,
    install: UpdateInstallState,
    onDownload: () -> Unit,
    onCancelDownload: () -> Unit,
    onInstalled: () -> Unit,
    onDismiss: () -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val info = state.latest
    val canInstall = state.status == UpdateStatus.AVAILABLE && !info?.apkUrl.isNullOrBlank()
    val busy = install.status == InstallStatus.DOWNLOADING || install.status == InstallStatus.VERIFYING
    val title = when (state.status) {
        UpdateStatus.AVAILABLE -> "发现新版本 ${info?.tagName ?: ""}"
        UpdateStatus.UP_TO_DATE -> "已是最新版本"
        else -> "检查更新"
    }

    // 下载完成就交给系统安装器，只交一次：seq 每次状态变化才递增，
    // 重组不会重复触发。没有「安装未知应用」权限时先去申请，回来再点一次。
    androidx.compose.runtime.LaunchedEffect(install.seq) {
        if (install.status != InstallStatus.READY) return@LaunchedEffect
        when (UpdateInstaller.install(context, install.filePath)) {
            InstallOutcome.LAUNCHED -> onInstalled()
            InstallOutcome.NEEDS_PERMISSION -> {
                toast(context, "请允许安装未知应用后重试")
                UpdateInstaller.requestInstallPermission(context)
            }
            InstallOutcome.FAILED -> toast(context, "无法启动安装程序")
        }
    }

    // 被「安装未知应用」挡回来之后的重试：文件早已下好并校验过，再点一次是
    // 去授权，不该重下 16 MB。seq 没有变，所以上面那个 LaunchedEffect 不会自己再跑。
    val retryInstall: () -> Unit = {
        when (UpdateInstaller.install(context, install.filePath)) {
            InstallOutcome.LAUNCHED -> onInstalled()
            InstallOutcome.NEEDS_PERMISSION -> UpdateInstaller.requestInstallPermission(context)
            InstallOutcome.FAILED -> Unit
        }
    }

    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        title = { Text(title) },
        text = {
            Column(
                Modifier.fillMaxWidth()
                    .verticalScroll(androidx.compose.foundation.rememberScrollState()),
            ) {
                if (state.status == UpdateStatus.FAILED) {
                    Text(state.message ?: "检查更新失败")
                    return@Column
                }
                Text("本机版本 ${state.currentVersion}")
                if (info != null) {
                    Text("最新版本 ${info.tagName}")
                    if (info.apkName != null && info.apkSize > 0) {
                        Text("安装包 ${info.apkName} · ${"%.1f".format(info.apkSize / 1_000_000.0)} MB")
                    }
                    Spacer(Modifier.height(12.dp))
                    if (info.notes.isNotBlank()) {
                        Text("更新内容", style = MaterialTheme.typography.titleSmall)
                        Spacer(Modifier.height(4.dp))
                        Text(info.notes, style = MaterialTheme.typography.bodyMedium)
                    } else {
                        Text("（这个版本没有写更新内容）", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }

                when (install.status) {
                    InstallStatus.DOWNLOADING -> {
                        Spacer(Modifier.height(16.dp))
                        if (install.isIndeterminate) {
                            LinearProgressIndicator(Modifier.fillMaxWidth())
                        } else {
                            LinearProgressIndicator(
                                progress = { install.progress },
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "正在下载 ${install.sizeText}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    InstallStatus.VERIFYING -> {
                        Spacer(Modifier.height(16.dp))
                        LinearProgressIndicator(Modifier.fillMaxWidth())
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "正在校验安装包…",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    InstallStatus.FAILED -> {
                        Spacer(Modifier.height(12.dp))
                        Text(
                            install.message ?: "下载失败",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                    else -> Unit
                }
            }
        },
        confirmButton = {
            when {
                busy -> TextButton(onClick = onCancelDownload) { Text("取消下载") }
                install.status == InstallStatus.READY -> TextButton(onClick = retryInstall) {
                    Text("去安装")
                }
                canInstall -> TextButton(onClick = onDownload) {
                    Text(if (install.status == InstallStatus.FAILED) "重试下载" else "下载并安装")
                }
                else -> TextButton(onClick = onDismiss) { Text("知道了") }
            }
        },
        dismissButton = {
            if (!busy) TextButton(onClick = onDismiss) { Text("稍后") }
        },
    )
}

@Composable
private fun SectionHeader(title: String, expanded: Boolean, onToggle: () -> Unit) {
    Column {
        Spacer(Modifier.height(8.dp))
        // V6.0：分组标题从"换色小字"升级为"品牌竖条 + 标题"，
        // 与主页 NeonSectionHeader、二级页顶栏共用同一个章节锚点。
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onToggle)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .height(14.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(Brush.verticalGradient(NeonTokens.brandGradient())),
            )
            Spacer(Modifier.width(8.dp))
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "收起$title" else "展开$title",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp),
            )
        }
        NeonHairline()
    }
}

@Composable
private fun <T> ChoiceRow(title: String, options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { (value, label) ->
                val isSel = value == selected
                Text(
                    label,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSel) MaterialTheme.colorScheme.onPrimary
                    else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(MaterialTheme.shapes.small)
                        .background(
                            if (isSel) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.surfaceVariant
                        )
                        .clickable { onSelect(value) }
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                )
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun SliderRow(
    title: String,
    value: Float,
    valueRange: ClosedFloatingPointRange<Float>,
    valueText: String,
    onValueChange: (Float) -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(valueText, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Slider(value = value, onValueChange = onValueChange, valueRange = valueRange)
    }
}

@Composable
private fun InfoRow(
    title: String,
    onClick: () -> Unit,
    subtitle: String? = null,
    danger: Boolean = false,
) {
    Column(
        Modifier.fillMaxWidth().clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Text(
            title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (subtitle != null) {
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Stable-ish file name: the date is what a user can tell apart in a picker. */
private fun backupFileName(): String {
    val stamp = java.text.SimpleDateFormat("yyyyMMdd-HHmm", java.util.Locale.US)
        .format(java.util.Date())
    return "jijian-music-$stamp.jjmbackup"
}

/** Status line under the export row; null when there is nothing to report. */
private fun backupSubtitle(state: BackupUiState): String? = when (state.status) {
    BackupStatus.IDLE -> null
    BackupStatus.EXPORTING -> "正在导出……"
    BackupStatus.IMPORTING -> "正在恢复……"
    BackupStatus.DONE -> state.message ?: "完成"
    BackupStatus.FAILED -> state.message ?: "失败"
}
