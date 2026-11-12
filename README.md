# 极简music (JiJianMusic) for Android

`applicationId = com.lxmusic.android` · 当前版本 **1.4.5** (`versionCode = 20`)

> **V1.4.5 起只编译、只发布 debug 版**（`com.lxmusic.android.debug`）。应用内的「下载并安装」装的就是同一个 debug 签名，能覆盖升级；正式包如果还留着旧版本，需要先卸载一次。

一个**纯本地**的极简音乐播放器。起手参考
[lyswhut/lx-music-desktop](https://github.com/lyswhut/lx-music-desktop) 的音源架构，
在线搜索与自定义音源在 V1.1.0 已整个删除，现在没有任何网络播放路径。

Kotlin + Jetpack Compose（Material 3），播放走 Media3 ExoPlayer。

```
┌──────────────────────────── Android app (Kotlin) ────────────────────────────┐
│  Compose UI  ─▶  MainViewModel  ─▶  PlayerController ─▶ Media3 ExoPlayer     │
│                       │  facade over 5 stores                                 │
│                       │                    │                                  │
│                       ▼                    ▼                                  │
│              SettingsStore (DataStore)   QueueMirror (one playlist)          │
│              Room DB v6                  RandomShuffleOrder (draws)          │
└───────────────────────────────────────────────────────────────────────────────┘
```

`ui/shell/LxShell.kt` 持有舞台：全屏页面都是注册进
`CurtainSpec(key, visible, onDismiss, content)` 的幕布，所以没有任何页面自己写
`BackHandler`。进一步的设计记录在 [HANDOVER.md](HANDOVER.md)。

---

## 音源

只有本地文件，两条路：

- **SAF 目录扫描** —— 系统选择器里挑任意目录，应用记住 tree URI，随时重扫。
- **MediaStore 兜底** —— 选择器够不到的东西走这里。

识别哪些扩展名与 MIME 见 `local/SafMusicScanner.kt`。播放是 ExoPlayer over
`content://` / `file://`。

---

## 构建

```bash
# JDK 21 + Android SDK（platform 34 / build-tools 34.0.0）。JDK 17 也可；AGP 8.5 不支持 JDK 25。
export JAVA_HOME=/path/to/jdk21
export ANDROID_SDK_ROOT=/path/to/android-sdk
./gradlew :app:assembleDebug
```

发布只出一个包：

```bash
./gradlew :app:versionedDebugApk
# → build/apk/jijian-music-<versionName>-vc<versionCode>-debug.apk
```

**V1.4.5 起不再构建 release 包**（`versionedApk` 任务还在，但发版流程不跑它）。
原因是应用内的「下载并安装」必须和用户手里那份同一个签名：release 包用
`jijian.keystore`、debug 包用 debug key，装上去必然签名冲突。既然自动安装是主要
更新路径，就统一发 debug 包——它同时也让崩溃栈带行号。

`:app:versionedDebugApk` 的改名是必要的：QQ 等 IM 按**文件名 + 大小**去重，
连续两次 `app-debug.apk` 字节长度常常一样，新包会被静默匹配成聊天里那个旧包。

Windows 上如果路径含中文，会因 `sun.jnu.encoding=GBK` 读错 Gradle 的 UTF-8
argfile，让单测全部 `ClassNotFoundException`；用 `subst` 把父目录映射成 ASCII
盘符再构建。详见 [HANDOVER.md](HANDOVER.md) 第二节。

---

## 目录

```
app/src/main/java/com/lxmusic/android/
├── LxApp.kt / MainActivity.kt / CrashLogger.kt
├── data/
│   ├── db/AppDatabase.kt        # Room v6：歌单、收藏、历史、local_track、play_event
│   ├── model/MusicInfo.kt
│   ├── repo/MusicRepository.kt  # 唯一 DB 访问点
│   ├── settings/                # SettingsStore（DataStore，31 键）+ EqProfile
│   ├── stats/PlayStats.kt       # 纯播放次数聚合（日 / 周 / 全部 / 年度）
│   └── backup/                  # .jjmbackup 容器 + 导入合并
├── local/
│   ├── SafMusicScanner.kt       # SAF 扫描
│   ├── LocalMusicScanner.kt     # MediaStore 兜底
│   ├── LocalLibrary.kt          # 分组 / 排序 / 搜索 / 合并
│   ├── LocalIndex.kt            # 预小写搜索索引
│   ├── IncrementalScan.kt       # (mtime, size) 复用策略
│   ├── SmartCollections.kt      # 自动合集（V1.4.1 起不在首页显示）
│   ├── UnifiedSearch.kt         # 跨曲目/收藏/专辑/歌手/歌单的一次查询
│   └── LocalFileDeleter.kt / LocalTrackMapper.kt
├── lyric/                       # LRC 解析、编码探测、内嵌标签读取
├── net/                         # HttpBridge / ApkDownloader / UpdateChecker
├── update/UpdateInstaller.kt    # FileProvider → 系统安装器
├── player/
│   ├── PlaybackService.kt       # MediaSessionService + ExoPlayer
│   ├── PlayerController.kt      # 播放状态、队列、抽签策略
│   ├── RandomShuffle*.kt        # 每次换歌抽一次签（见下）
│   ├── QueueMirror.kt           # 唯一镜像队列的地方
│   ├── QueueLogic.kt / QueueDrag.kt / QueueIndex.kt
│   ├── StallWatchdog.kt         # 静默停顿检测（纯 Kotlin）
│   └── EqualizerController.kt
└── ui/
    ├── LxApp.kt / HomeChrome.kt # 外壳接线 / 顶栏·公告条·迷你播放器·舞台
    ├── shell/                   # LxShell、Route 栈、WidePane（平板右栏归属）
    ├── state/                   # MediaLibraryStore / PlayerUiStore / StatsStore /
    │                            #   UpdateStore / BackupStore（MainViewModel 只是门面）
    ├── MainViewModel.kt
    ├── components/              # TrackRow、LyricView、RotatingArtwork、CoverTier、
    │                            #   TrackDescription、SearchBar、Hints
    ├── neon/                    # Neon Vinyl 设计系统
    ├── screens/                 # Local、Library、GroupDetail、NowPlaying、Queue、
    │                            #   Equalizer、Stats、FolderManager、Settings、Search
    └── theme/                   # OledTokens / ColorPalettes / Theme
```

---

## 功能

**本地播放** —— MediaStore + SAF 双通道扫描；ExoPlayer 直接播 `file://` /
`content://`；内嵌或同目录 `.lrc` 歌词；无网络播放路径。

**歌词** —— 一行多时间戳、1–3 位小数、重复时间戳自动识别成翻译轨；`Lyric.indexAt`
二分查找；滚动视图高亮居中、翻译显示在下方；**V1.4 起点击任意一行就跳到那句**
（此前 `onSeekTo` 参数存在了七八个版本却从未被调用），播放页菜单里还能就地调
字号与偏移。

**播放语义（V1.0.1 起）** —— 随机播放是**每次换歌抽一次签**，在除当前曲以外的
所有槽位上均匀抽，结果缓存到下一首真正开始为止。「上一首」用同一规则，额外避开
刚播完那首，所以双向都随机、连按两次不会在两首之间弹跳。**没有 cycle、没有播放
历史栈。** 硬件「上一首」永不重播当前曲（`setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)`）。
`RandomShuffleOrder` 把同一份状态交给 media3，通知栏 / 锁屏 / 蓝牙 / 应用内按钮
答案一致。完整历史写在 `player/RandomShuffle.kt` 的 KDoc 里。

**静默停顿看门狗** —— 位置在「仍在播放」状态下冻结 8 s 就请求重试，同一点再冻
一次则从冻结位置重建媒体源；缓冲单独计时（20 s）。

**均衡器** —— Android 每次新音频会话都把效果器重建为空，所以曲线存进
`AppSettings.eq`，每次 `attach()` 之后重放。

**Neon Vinyl 视觉** —— 极光背景三团放射光在椭圆轨道漂移（11 s / 17 s / 23 s，
互质所以永不对齐），播放页配色从当前封面提取；玻璃面板是半透明白 + 1 dp 描边 +
顶部高光，**禁用 `RenderEffect` 模糊**（曾导致 API 31+ 崩溃）；进度条手写
`awaitEachGesture`（Material 的 `Slider` 会抢播放按钮的点击）；76 dp 呼吸播放按钮
跟随系统「移除动画」；双主题通用。

**浏览** —— 专辑 / 歌手有独立页面（大封面、N 首、总时长、播放全部 / 随机播放）；
统一搜索一次返回歌曲 / 收藏 / 专辑 / 歌手 / 歌单；平板（≥840 dp）右栏可并排专辑页
或播放页。

**统计** —— 今日 / 本周 / 全部 + 年度回顾，数据来自独立的 `play_event` 表（一次
播放一行），一年以前的事件在每次写入时清理。「记录播放统计」与「播放时记录历史」
是两个独立开关。

**备份** —— `.jjmbackup`（zip 内含 `backup.json`），按指纹合并导入，不删现有数据。

**更新** —— 检查 GitHub Releases 后在应用内流式下载 APK（支持 `Range` 续传、
发布 digest 校验），再交给系统安装器。

**可访问性** —— 列表整行合并成一句话，动词随动作变（分组行说「打开」绝不说
「播放」，迷你播放器说「打开播放页」）；玻璃按钮 48 dp 触摸区；歌词行、队列行、
合集架都有读屏能念出来的名字。

---

## 测试

```bash
./gradlew :app:testDebugUnitTest
```

**417 项单元测试 / 41 个类 / 0 失败。**

纯逻辑优先：抽签、队列、歌词解析、设置键往返、备份指纹、扫描复用、搜索索引、
可访问性文案、平板右栏归属都是不需要 Android 的纯函数。`RandomShuffle*` 五个类
加上 `ShuffleStallProbe` 编码了这个项目遇到过的每一个随机播放 bug——动那段代码
之前先读它们。`NeonTokensTest` 则对八套配色重算 WCAG 对比度（取极光最亮合成值），
防止「再炫一点」把正文压到 AA 以下。

已不存在的文件：`data/Changelog.kt`、`ui/screens/AnnouncementScreen.kt`、
`player/ShuffleBag.kt`、`player/BagShuffleOrder.kt`、`player/PlayHistory.kt`、
`jsengine/`、`source/`、`ui/screens/OnlineScreen.kt`、`FeatureFlags.kt`。
`RandomShuffle.kt` 是唯一的随机实现，播放只可能是本地文件。

---

## V1.4.5 — 只发 debug，启动就查，切样式不再叠两次 (`versionCode = 20`)

**四件产品决定。**

「启动时自动检查更新」这个开关连它背后的 24 小时节流一起删了。开关加节流的
实际效果是「用户以为在查，其实没查」——两个都想省网络请求的机制叠在一起，变成
一个只在第一次启动有效的功能。现在每次进应用都静默查一次，失败和「已是最新」
仍然退回 IDLE，不往设置页塞一条用户没要的状态。

「更新」分区里加了**项目地址**，点了直接开
`https://github.com/xxwzh66-byte/jijian-music`。

发版只发 debug 包（见上面「构建」）。`UpdateChecker.parseRelease` 的选包逻辑随之
反转：从「优先 release 包」改成「优先 debug 包」，只在老 release 没有 debug 资产时
才退回任意 apk。

**两个真 bug。**

「随机播放第一次启动直接卡死」是三个东西叠出来的：`QueueMirror.resync` 每次队列
变更都回写 `shuffleModeEnabled`，而 `onQueueMutated` 本身又会触发一次安装——两条路
各自把 ExoPlayer 的 timeline 重建一遍；`resetShuffle` 还会 `invalidate()` 随机账本，
generation 一变 memo 守卫立刻失效，于是又必然多装一次；而装一次 order 就是造一条
新的 masking timeline，跨进程的 `MediaController` 再把每一轮放大成主线程停顿。
冷启动时 `backfillQueue` 正好把这些串在同一帧里发生。

三处改动：

```kotlin
// QueueMirror：只回写 repeatMode，shuffle 交给它的主人
private fun resync(t: MirrorTarget) {
    t.repeatMode = repeatFlag()
    onQueueMutated()
}

// PlayerController：队列变更不再作废随机账本
private fun resetShuffle() {
    forgetInstalledOrder()
    installShuffleOrder()
}

// PlayerController：装 order 的熔断（1 秒 4 次）
if (++installWindowCount > MAX_INSTALLS_PER_WINDOW) return false
```

队列变短时账本里的旧下标已经被 `ShuffleMemo.slot` 的 `cached in 0 until size` 挡掉，
不需要整本作废；真正该「重新随机」的只有用户自己按开关那一下
（`toggleShuffle` 仍然 `invalidate()`）。熔断是兜底：正常一秒最多进两三次，边缘
路径成环时宁可这一轮不装，也不能让主线程一直转。

「切换样式一次性加载多次样式」的根因是 `Crossfade`：过渡期它同时组合**两套舞台**，
唱片（凹槽 Canvas 30–50 圈 `drawCircle` + 封面 + 唱臂）和歌词列表（LazyColumn +
每行两个动画）一起在跑，连 `pointerInput(sk)` 都各自重建一次。改成「先淡出、换、
再淡入」之后，任一时刻只有一套舞台存在：

```kotlin
stageAlpha.animateTo(0f, tween(FADE_OUT_MS, LinearEasing))
shown = skin
stageAlpha.animateTo(1f, tween(FADE_IN_MS, LinearEasing))
```

顺带把滑动切歌的 `pointerInput` 从 `pointerInput(sk)` 改成 `pointerInput(Unit)`——
它是舞台容器的手势，和里面画什么无关，没有理由随样式重建。

**418 → 417 项**：删掉 `autoCheckUpdates` 的往返用例（那个偏好已经不存在），
`QueueMirrorTest.everyEditResetsBothModes` 改名为
`everyEditResetsRepeatModeButLeavesShuffleToItsOwner`（契约变了，测试跟着改），
`UpdateCheckerTest` 两条选包用例按 debug 优先重写。

---

## 许可

本项目不附带也不托管任何音乐内容，只播放设备上已有的音频文件。请自行遵守所在
司法辖区的版权法律。
