# 极简music (JiJianMusic) for Android

`applicationId = com.lxmusic.android` · 当前版本 **1.4.7** (`versionCode = 22`)

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

**426 项单元测试 / 42 个类 / 0 失败。**

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

## V1.4.7 — 那条避开规则从 V1.0.1 起就是死的 (`versionCode = 22`)

### 一个恒为假的条件

「上一首」不是把「下一首」倒过来放 —— 用户要的是**也随机**。为了不让它随机
到刚刚才听过的那一首（那就等于把刚才按的那一步撤销了），`previousIndex` 里有
一条避开规则，注释从 V1.0.1 起就写着「避开刚播完的那首」。

它一次都没生效过。

```kotlin
// 修之前
val justPlayed = memo.lastTrack
val avoid = if (size > 2 && justPlayed in 0 until size && justPlayed != current) {
    intArrayOf(current, justPlayed)
} else {
    intArrayOf(current)
}
```

`lastTrack` 由 `started(size, current)` 写入 —— 写进去的正是**刚开播的这一
首**，也就是调用方马上要传进来的 `current`。第三个条件恒为假，整个分支永远走
`else`，`avoid` 里永远只有 `current` 自己。避开规则是一段**不可达代码**。

用户看到的是两件事：

- 播放 3 → 按「下一首」到 5 → 按「上一首」，有 1/7 的概率原样退回 3。看起来
  像按键没生效，只好再按一次。
- 连按两次「上一首」可以在同一对曲子之间来回弹。

### `cameFrom` 与两个必须的细节

`ShuffleMemo` 新增 `cameFrom`：**我们从哪一首走到现在这一首的**。

```kotlin
fun started(size: Int, current: Int) {
    if (current != lastTrack && lastTrack in 0 until size) {
        cameFrom = lastTrack
    }
    lastTrack = current
    next.clear()
    previous.clear()
}
```

1. **在覆盖 `lastTrack` 之前转存**。晚一行，旧值就没了。
2. **重复上报同一首不覆盖 `cameFrom`**。换歌有两条路径都会上报 —— media3 的
   `onMediaItemTransition`（通知栏、蓝牙、自动续播）和 `PlayerController` 的
   `playIndex`（app 内按键）。第二条如果不判等就写，`cameFrom` 会变成当前首，
   避开规则又白写了。

`previousIndex` 的避开依据从 `lastTrack` 换成 `cameFrom`，其余不变：仍然
`size > 2` 才启用（两首队列没有第三个位置可去，排除两个等于无解），仍然均匀
抽。`clear()`（换队列、开关随机）连 `cameFrom` 一起清。

### 一次「测试没在断言」的事故

新增 `RandomShuffleBacktrackTest` 的第一版，最关键那条写的是：

```kotlin
assertNotEquals("previous undid the next press", 50, undone)
```

50 个种子里「一次都没退回」的概率是 (6/7)^50 ≈ 0.0005，改成 `assertEquals(0, undone)`
才是真的在断言。第一版无论改不改实现都会通过 —— 它跑绿了，但没有证明任何事
情。严格化之后它立刻在 50 个种子里抓到了若干次退回，改完实现才转绿。

同批把 V1.4.6 留下的 `forgetDrawsKeepsTheTrackThatJustPlayedSoPreviousStillAvoidsIt`
改成 `forgetDrawsKeepsTheTrackWeCameFromSoPreviousStillAvoidsIt`：它断言的正是
那个错误语义（`lastTrack` 作依据），继续留着会把 bug 锁死在测试里。

### 422 → 426 项

新增 4 个用例：不能撤销刚按的那一步、连按两次不能两点弹、避开的是「来的那一
首」而不是「正在播的这首」、重复上报不擦掉「来的那一首」。

本机仍然没有可用模拟器或真机（WHPX 初始化后 qemu 静默退出），**改动后的运行
时行为没有被端到端验证过**，验证只到「JVM 单测 + aapt2/apksigner 静态核验」。

## 许可

本项目不附带也不托管任何音乐内容，只播放设备上已有的音频文件。请自行遵守所在
司法辖区的版权法律。
