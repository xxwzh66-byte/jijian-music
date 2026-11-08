package com.lxmusic.android.ui.state

import com.lxmusic.android.net.UpdateInfo

/** 「检查更新」的四种结果。 */
enum class UpdateStatus { IDLE, CHECKING, UP_TO_DATE, AVAILABLE, FAILED }

/**
 * 设置页「检查更新」的一次结果。
 *
 * [checkSeq] 每次检查完成后递增，界面用它触发「更新内容公告」对话框——
 * 用序号而不是状态值，是因为连续两次检查都得到 AVAILABLE 也应当各弹一次。
 */
data class UpdateCheckState(
    val status: UpdateStatus = UpdateStatus.IDLE,
    val currentVersion: String = "",
    val latest: UpdateInfo? = null,
    val message: String? = null,
    val checkSeq: Int = 0,
)
