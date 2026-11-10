# 极简music (JiJianMusic) for Android

`applicationId = com.lxmusic.android` · 当前版本 **1.4.1** (`versionCode = 17`)

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

日常还有两个额外任务：

- `:app:versionedApk` —— 另出一份 `build/apk/jijian-music-<versionName>-vc<versionCode>.apk`。
  这个改名是必要的：QQ 等 IM 按**文件名 + 大小**去重，连续两次 `app-debug.apk`
  字节长度常常一样，新包会被静默匹配成聊天里那个旧包。
- `:app:versionedDebugApk` —— 同上，debug 版。**与上一个任务是两个独立任务**，
  跑 `versionedApk assembleDebug` 不会产出带版本号的 debug 名。

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

**412 项单元测试 / 41 个类 / 0 失败。**

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

## V1.4.1 — 首页减掉两排 (`versionCode = 17`)

**「为你挑的」收起。** V1.3 铺在首页的四张自动合集（最近添加 / 最常播放 /
从未播放 / 很久没听）不再显示。代码没删：`SmartCollections`（纯计算）、
`CollectionRail`（组件）与 17 个测试都在，把 `LocalScreen.SHOW_SMART_COLLECTIONS`
改回 `true` 即恢复——一个开关，不动别的地方。

**「最近在听」可以关掉。** 那排封面右侧多了一个 X，点了就收走。它和下面的架子
不同，不是用户自己排的东西，所以关掉是**偏好**而不是一次性的 UI 状态：落在
`AppSettings.showRecentRail`，设置页「音乐库」里能重新打开。默认开——默认关等于
新装用户永远看不到回头路。

两条改的是同一个判断：首页该放用户自己挑的东西。自动算出来的架子在库还很小的时候
有意义，库养起来之后它们只是把「我点的歌」往下推。

**411 → 412 项**：新增 1 个 `SettingsRoundTripTest` 用例（`showRecentRail` 的默认值
与写入往返），其余不变。

---

## 许可

本项目不附带也不托管任何音乐内容，只播放设备上已有的音频文件。请自行遵守所在
司法辖区的版权法律。
