# 极简music (JiJianMusic) — 项目交接报告

> 交接版本：**versionCode 22 / versionName "1.4.7"**
> 报告版本：**V1.4.7**（按代码实况更新；V1.0 之前的逐版本历史已从本报告移除）
> 代码规模：`app/src/main/java` 94 个 .kt；`app/src/test/java` 42 个 .kt / **426 项测试 0 失败**
> 产物：`build/apk/jijian-music-1.4.7-vc22-debug.apk`（debug 签名，**23,137,110 B**；
> SHA-256 `e19396a33d86dcdff347d67d1adc20abcbe6c82f920a717350f25e23f7d07f5e`）
>
> **V1.4.5 起只编译、只发布 debug 包。** 应用内的「下载并安装」必须与用户手里那份
> 同一个签名，release 包（`jijian.keystore`）装上去必然冲突，所以发版流程不再跑
> `versionedApk`。

---

## 一、三十秒概览

这是一个 Android 本地音乐播放器。起手参考了
[lx-music-desktop](https://github.com/lyswhut/lx-music-desktop) 的音源架构，
但**在线搜索与自定义音源在 V1.1.0 已整个删除**（V1.1.3 清掉最后一批残留：
`user_api` 表、`examples/example-source.js`、音质设置），现在没有任何网络播放路径。

- **语言/UI**：Kotlin + Jetpack Compose（Material 3）
- **视觉系统**：**"Neon Vinyl"**——Aurora UI 极光背景 + 深色 OLED + Glassmorphism 玻璃，
  双主题（深浅）通用，见第四节「Neon Vinyl 视觉系统」
- **V1.0.1 播放语义（当前）**：随机播放 = **每次换歌抽一次签**（在除当前曲外的所有
  槽位上均匀抽，结果缓存到下一首真正开始为止）；「上一首」用**同一规则**，额外避开
  刚播完的那首，所以双向都是随机的、且连按两次不会在两首之间弹跳。
  **没有 cycle、没有播放历史栈**——`PlayHistory.kt` 与 `advanceCycle` 都已删除。
  静默停顿由 `StallWatchdog` 兜底；均衡器曲线持久化在 `AppSettings.eq`。
  见第五、九节
- **V1.1.6 结构与产品**：全屏页面从六个布尔值换成带类型的 `Route` 栈
  （`ui/shell/Route.kt`，`Group(key)` 是第一个带参数的页面）；`MainViewModel`
  887 → 508 行，降级成五个 store 的门面（`ui/state/`）；专辑/歌手有了自己的页面；
  新增 `.jjmbackup` 备份恢复与「这一年」年度回顾。见第十四节
- **V1.2 性能**：四件事都只做「已经算过的东西别重算」——扫描按 `(mtime, size)`
  双标复用缓存、搜索对全库只 lowercase 一次、封面按显示尺寸分级解码、队列头部
  给出「3/12 · 剩 34 分」并支持长按拖动排序。见第十七节
- **V1.3 找得到**：四张没人维护的架子（最近添加 / 最常播放 / 从未播放 / 很久没听）
  从曲库加播放记录现算，尺寸各三十首、顺序固定、空了就自己消失；一次搜索返回五种
  答案（歌曲 / 收藏 / 专辑 / 歌手 / 歌单），匹配只在 `LocalIndex` 一处实现。见第十八节
- **V1.4 沉浸**：歌词每一行都成了可点的跳转目标（`onSeekTo` 参数从 V5 起就存在、
  却一直没被调用），播放页菜单里能就地调字号与偏移；平板上右栏不再固定 45%——
  专辑页占一半、播放页占 45%、没有可显示的东西时把宽度还给列表；列表每行合并成
  一句话，动词随动作变。见第十九节
- **V1.4.1 首页减负**：「为你挑的」四张自动合集收起（`SHOW_SMART_COLLECTIONS`
  一个开关，代码与 17 个测试都在），「最近在听」右侧加 X 可关掉并落进
  `AppSettings.showRecentRail`。见第二十节
- **V1.4.2 随机播放去崩溃**：`pickOther` 把「候选数组预分配 + 数量计算」换成一个
  `banned()` 判定——越界下标不算禁用、重复下标无害。同时把「不做近期去重窗口」写成
  显式决定（随机播放允许同一首歌在几步之内再次出现）。见第二十一节
- **V1.4.5 只发 debug + 启动就查更新 + 两个真 bug**：`UpdateChecker` 反过来优先挑
  `-debug.apk`，发版不再跑 `assembleRelease`（应用内安装必须与手上那份同签名）；
  「启动时自动检查更新」开关与 24h 节流一起删掉，每次启动都查一次；随机播放的
  冷启动卡死来自 `QueueMirror.resync` 回写 `shuffleModeEnabled` 与 `resetShuffle`
  的 `invalidate()` 叠成环，一并加装 order 熔断（1 秒 4 次）；播放页换样式从
  `Crossfade`（两套舞台同时存在）改成先淡出再换、再淡入。见第二十三节
- **V1.4.6 随机播放的「装 order」想错了一层**：装进 ExoPlayer 的
  `RandomShuffleOrder` 每问都转发到同一个 `RandomShuffleState`，账本 `ShuffleMemo`
  在 `copy()` 里也是共享的 —— 它是**活视图**不是快照，所以重装恒等于零收益，而
  重装正是那个环（`setShuffleOrder → createMaskingTimeline() → PlaylistTimeline
  无 equals → onTimelineChanged → 又回来`）。守卫改成只比长度，环从根上消失。
  另补两个真缺口：队列编辑后按旧下标记下的答案会被当成本次决策的答案
  （`ShuffleMemo.forgetDraws()`：扔答案、留 `lastTrack`、不动 generation）；
  熔断那条 return 以前连 `shuffleModeEnabled` 都不交代，app 与通知栏会各随机各的
  （`syncShuffleMode()` 统一三条退出路径）。422 项测试。
- **V1.4.7「上一首」的避开规则从未生效**：它依据 `ShuffleMemo.lastTrack`，而
  `started()` 写进去的就是刚开播的**这一首**，跟 `current` 永远相等 ——
  `justPlayed != current` 恒假，避开分支是不可达代码（从 V1.0.1 起就是）。表现为
  按「下一首」再按「上一首」有概率原样退回、连按两次能在两点之间弹。新增
  `cameFrom`（覆盖前转存旧 `lastTrack`，重复上报不覆盖），`previousIndex` 改用它
  作避开依据。见第二十五节
- **V1.4.3 随机播放不再卡同一首 + 播放页切样式不卡**：单槽位备忘录取代成按
  `current` 分槽的 `ShuffleMemo`，且 `copy()` 改为**共享**同一本账——装进 ExoPlayer
  的那份克隆体终于也能被 `onTrackStarted` 弄脏。切样式三处卡顿全部是「在
  composition 里读动画值」，改走 `graphicsLayer` / 传 lambda / 200ms Crossfade。见第二十二节
- **V1.0.1 耳机按键修复**：media3 的 `seekToPrevious()` 有两条路会变成「重播当前曲」——
  位置超过 `getMaxSeekToPreviousPosition()`（默认 3s），或 `getPreviousIndex()` 返回
  `-1`。V1.0 只关了前者；V1.0.1 让抽签**永不返回 -1**，把后者也堵掉。
- **V1.0.1 两个 seam**：`player/ShuffleSink.kt`（装/卸 order）与
  `player/QueueMirror.kt`（镜像 playlist 的五步收在一处），替代 `PlayerController`
  里八处复制粘贴。见第九节「V1.0.1 变更」
- **播放内核**：Media3 ExoPlayer 1.4.1 + `MediaSessionService`
- **持久化**：Room 2.6.1（**v6**；V1.1.6 删了 `song_list`/`list_item` 两张死表）+ DataStore Preferences（31 KEY）
- **构建**：Gradle 8.7 + AGP 8.5.2 + Kotlin 2.0.20，Groovy DSL
- **minSdk 24 / targetSdk 34 / compileSdk 34**
- **包名**：`com.lxmusic.android`（debug 变体加后缀 `.debug`）

---

## 二、环境搭建（从零接手的第一步）

这台机器上环境**已经就绪**，但都不是系统默认的，必须显式指定：

| 组件 | 位置 | 备注 |
|---|---|---|
| JDK 21 | `C:\Program Files (x86)\Android\openjdk\jdk-21.0.8` | **AGP 8.5 不兼容 JDK 25**（系统默认只有 25），必须显式指定这个 |
| Android SDK | `D:\demo\toolchains\android-sdk`（junction → `D:\demo\02_kaifa\android-sdk`） | `platforms/android-34`+`36`、`build-tools/34.0.0`+`35`+`36`、`platform-tools`。**路径里没有「开发工具链」这个名字了**，旧文档写的 `02_开发工具链` 已不存在 |
| Gradle | wrapper 自动下载 | `gradle/wrapper/gradle-wrapper.properties` 指向**华为云镜像**，官方 CDN 极慢 |

**V1.1.6 起路径与临时目录是两个非显然的坑**，都已经在仓库里绕过：

1. 项目现在位于 `D:\demo\01_项目代码\lxmusic-android`，路径含中文。AGP 8.5.2 会直接拒绝
   （`Your project path contains non-ASCII characters`），所以 `gradle.properties` 里有一行
   **`android.overridePathCheck=true`**。这不是编码问题，是 AGP 的路径检查。
2. 中文路径下 Kotlin 编译守护进程会崩：`java.nio.file.AccessDeniedException: ...\kotlin-daemon-client-<n>.tmp`
   + `Daemon compilation failed: Could not connect to Kotlin compile daemon`，之后所有源文件都
   报「找不到」（非 ASCII 被转义成 `\uXXXX` 字面量）。**唯一的解是
   `-Pkotlin.compiler.execution.strategy=in-process`**，把编译器放进 Gradle 进程里跑。
   改 `org.gradle.jvmargs` 的 `java.io.tmpdir` 无效，改 `TEMP/TMP` 也无效。
3. 本机用 `subst X: "D:\demo\01_项目代码"` 把项目盘映射成 ASCII 盘符，`X:\lxmusic-android`
   就是干净路径。`subst` 在重启后失效，需要时重新执行（脚本是 `D:\demo\_mk_subst2.py`，
   用 `encoding="gbk"` 写 `.bat` 再 `cmd /c`，否则 .bat 里的中文会被解析成 `D:\demo\x01_...`）。
   **`mklink /J` 建 junction 不管用**：Gradle 仍会把含中文的真实路径写进 `gradle-worker-classpath*.txt`。
4. 为什么非要 ASCII 盘符：JVM 的 `sun.jnu.encoding` / `native.encoding` 都是 **GBK**
   （`chcp 65001` 与 `-Dsun.jnu.encoding=UTF-8` 都改不动，`-Dfile.encoding=UTF-8` 只影响输出），
   Java 读 UTF-8 的 `@argfile` 时按 GBK 解码，含中文的项目路径全变乱码 → 30 个测试类一起
   `initializationError: ClassNotFoundException`。实测同一份 argfile 存成 GBK 立刻正常，存成
   UTF-8 就挂——所以问题在路径编码，不在 classpath 本身。

### 标准构建命令（bash，仓库外的 `D:\demo\_bx4.sh`）

```bash
cd /x/lxmusic-android || exit 1
# 不要覆盖 JAVA_HOME：系统已给 jdk17。覆盖成 jdk21 会报
#   "Value ... given for org.gradle.java.home is invalid"
export ANDROID_SDK_ROOT="${ANDROID_SDK_ROOT:-D:\\demo\\toolchains\\android-sdk}"
./gradlew.bat "$@" --no-daemon --console=plain \
  "-Dorg.gradle.jvmargs=-Xmx2048m -Dfile.encoding=UTF-8 -Djava.io.tmpdir=D:\\demo\\_tmp -Dkotlin.daemon.client.alive.path=D:\\demo\\_tmp\\kotlin-client.alive" \
  -Pkotlin.compiler.execution.strategy=in-process
```

> **`_bx4.sh` 会丢**：它在仓库外的 `D:\demo` 下，已被清理过至少两次。丢了的症状是
> `bash: /d/demo/_bx4.sh: No such file or directory`。照上面代码块重建即可，三分钟的事，
> 不要试图换别的构建方式 —— 每一代 `_bxN.sh` 都踩过不同的坑（见下）。

调用：`bash /d/demo/_bx4.sh :app:compileDebugKotlin` / `bash /d/demo/_bx4.sh :app:testDebugUnitTest`。

> `_bx.sh` / `_bx2.sh` / `_bx3.sh` 都已作废：前两个用中文路径，全部测试报
> `ClassNotFoundException`；第三个丢了 `-Dfile.encoding=UTF-8`，仍失败。**只用 `_bx4.sh`**。
> 两个 `-D` 缺一不可：没有 `-Djava.io.tmpdir=D:\demo\_tmp` 时，Gradle 的 JVM 在
> `C:\Users\...\Local\Temp` 撞 `AccessDeniedException`（同一目录用 python 或裸 java 创建
> 同名文件都成功，是 Gradle 进程特有的限制）。

- `:app:assembleDebug` → `app/build/outputs/apk/debug/app-debug.apk`
- `:app:versionedApk` → `build/apk/jijian-music-<版本>-vc<号>.apk`（**给用户的就是这个**，见第六节）
- `:app:versionedDebugApk` → 同目录的 `-debug` 变体
- `:app:testDebugUnitTest` → 426 项单元测试；统计用 `python -X utf8 _cnt.py`（另有 `_per_class.py`）解析
  `app/build/test-results/testDebugUnitTest/*.xml` 的 `tests=` / `failures=` / `errors=` 属性
  （不要用 PowerShell 的 `[xml]`，会把中文测试名弄乱）

### 这台机器上没有的东西（重要）

- **没有模拟器，也没有真机**。合并前的记录里写着 AVD `neon33f` 可用，那是过时的：
  这一代会话连续试过三次（旧 AVD、新建 AVD `jjm33`、`-verbose` 直跑 qemu-headless），
  **全部在打印 `WHPX on Windows 10.0.26200 detected.` / `Windows Hypervisor Platform
  accelerator is operational` 之后进程静默消失**（exit code 21），`adb devices` 始终为空。
  `-gpu swiftshader_indirect` / `-gpu off` / `-accel off` / `-wipe-data` / 换端口 /
  `Start-Process` 游离 / 删 `*.lock` 全试过，全部一样。
  **结论：实机验证一律退化为「JVM 单测 + aapt2 / apksigner 静态核验」，不要再花时间
  试模拟器。** 需要真机行为时，请让用户 `adb install -r -d` 手动验证并抓 logcat。
- 项目**已是 git 仓库**，远端 `https://github.com/xxwzh66-byte/jijian-music`（公开）。
  V1.0 起的历史可用 `git log` / `git show` 追溯；`D:\demo\backups\` 里的三个
  v5.3–v5.5 源码 zip 是 V1.0 之前唯一的留档。
- **`git push` 不通**（见第七节）：发版靠 GitHub API 直接写 Git Data，不走 `git push`。

---

## 三、架构：三层，与它为什么被砍到三层

`lx-music-desktop` 是 Electron + Vue3，能力分两块：内置搜索模块（酷我/酷狗/QQ/网易/
咪咕）住在 `src/renderer/utils/musicSdk/*`，自定义源协议由
`src/main/modules/userApi/renderer/preload.js` 注入 `window.lx`。

Android 端曾经把这两块都用 headless WebView 复刻出来（两个 WebView + 一个
`SharedNativeBridge` 原生桥 + `SourceManager` 门面），那是 `jsengine/` 与 `source/`
两个包的全部内容。**V1.1.0 把它们连同 `assets/jsengine/`、`OnlineScreen`、
`FeatureFlags`、`user_api` 表一起删了**，理由很直接：播放器的主线是「打开就听自己的歌」，
而这条支线要养两个 WebView 进程（30–80 MB RAM）、一套 esbuild 打包链、一个只在
桌面端成立的脚本协议。

现在的三层：

| 层 | 文件 | 职责 |
|---|---|---|
| 壳 | `ui/LxApp.kt`、`ui/shell/LxShell.kt`、`ui/HomeChrome.kt` | 舞台四槽位 + 幕布；不碰业务状态 |
| 状态 | `ui/MainViewModel.kt` | 唯一状态持有者，把 Settings/DB/播放器缝在一起 |
| 播放 | `player/PlayerController.kt` + `player/QueueMirror.kt` | 队列、抽签、镜像到 ExoPlayer |

`PlayerController` 不接收任何解析器：每首曲子都是本地文件，`playIndex` 只会产出
`file://` 或 `content://` URI。

---

## 四、代码地图（按「我要改 X，该动哪个文件」组织）

所有路径相对 `app/src/main/java/com/lxmusic/android/`。

### 入口与全局

| 文件 | 职责 |
|---|---|
| `LxApp.kt` | `Application`；持有 `db` / `settingsStore` 两个单例；**最先安装 `CrashLogger`** |
| `MainActivity.kt` | 唯一的 Activity；Compose 入口；权限请求、耳机拔出广播、MediaStore 删除的 IntentSender 流程 |
| `CrashLogger.kt` | 未捕获异常落盘到 `filesDir/last_crash.txt`（**排障第一入口**） |
| `ui/CrashReportDialog.kt` | 把上面的日志展示出来供用户复制 |

### 播放（最容易出 bug 的地方）

| 文件 | 职责 |
|---|---|
| `player/PlayerController.kt`（28 KB，最大的文件） | **播放状态机**。队列、索引、随机、循环、进度回写、歌词加载、队列镜像到 ExoPlayer |
| `player/PlaybackService.kt` | `MediaSessionService`；通知渠道 `jijian_playback`、通知 id 1001、`setSessionActivity` 让点通知回到 App |
| `player/PlayerHolder.kt` | 一个 `@Volatile var player: ExoPlayer?`——因为 `MediaController` 拿不到 `audioSessionId`、也设不了 `ShuffleOrder` |
| `player/RandomShuffle.kt` | 纯逻辑：真随机抽样策略 + 带 memo 的状态 |
| `player/RandomShuffleOrder.kt` | 把上面的策略适配成 ExoPlayer 的 `ShuffleOrder` 接口 |
| `player/EqualizerController.kt` | 均衡器（5 段 + BassBoost + Virtualizer + LoudnessEnhancer） |
| `player/QueueLogic.kt` | 队列 `move` / `remove` 纯函数（`nextIndex`/`prevIndex` V1.1.3 已删，生产从未调用） |
| `player/ShuffleSink.kt`（V1.0.1） | 装/卸 shuffle order 的 seam：`install(): Boolean` / `dropInstalled()`。生产实现桥到 `PlayerHolder` 的 ExoPlayer，测试实现是内存 sink |
| `player/QueueMirror.kt`（V1.0.1） | 镜像 playlist 的唯一起点：`replace` / `ensure` / `insert` / `move` / `remove` / `clear` / `dropToSingleItem`，五步顺序固定（drop → flag → select → setMediaItems → resync）。取代 `playlistMode` 字段与八处复制粘贴 |
| `player/QueueIndex.kt` | `queueIndexOf(mediaId, queue, playerIndex)`：先按 **mediaId** 查队列槽位，查不到回退播放器报的 index，再不行返 -1 让调用方忽略回调。修 `playIndex(3)` 在单曲模式被报成 0 |
| `player/StallWatchdog.kt` | 纯 Kotlin 的静默停顿看门狗（无 Android 类型，假时钟可测）：8s 不动 → RETRY，再 8s → REBUFFER，缓冲态单独 20s |

### 数据

| 文件 | 职责 |
|---|---|
| `data/db/AppDatabase.kt` | Room **v5**。表：`song_list` / `playlist` / `favorite` / `play_history` / `play_event` / `local_track`（`user_api` v4→v5 迁移里 drop） |
| `data/model/MusicInfo.kt` | 核心模型 `MusicInfo` / `QualityInfo` / `SongList` / `ListItem` |
| `data/repo/MusicRepository.kt` | 所有 DAO 的门面；`logPlay` / `playRecords` 是统计功能的数据源 |
| `data/settings/SettingsStore.kt` | DataStore；`AppSettings` 是**唯一设置真相**，30 个 KEY 常量（含 `record_stats`、6 个 `eq_*`、`last_update_check_at`、V1.1.4 的 `auto_check_updates`、V1.1.5 的 `pending_deletes`；V1.1.4 删掉了 `update_token`） |
| `data/settings/EqProfile.kt` | 用户音调曲线的可持久化快照：enabled/preset/bands/bass/virtualizer/loudness；`encodeBands`/`decodeBands` 坏数据丢弃不抛 |
| `data/stats/PlayStats.kt` | 播放统计纯逻辑：日/周/全部三档、直方图分桶、排行；V1.1.6 加 `yearStart`/`currentYear`/`annualReport` |
| `data/backup/BackupCodec.kt`（V1.1.6） | **纯 JVM、可单测**：`BackupFile`（JSON 形状）、`TrackMatcher`（指纹匹配）、`BackupArchive`（zip 容器读写） |
| `data/backup/BackupRepository.kt`（V1.1.6） | 导出/导入的实际 DB 操作：`export(appVersion)` 收齐五张表，`import(file, localTracks)` 合并 |

### 本地音乐

`local/LocalMusicScanner.kt`（MediaStore 扫描）、`local/SafMusicScanner.kt`（SAF 目录扫描，
含 `DocumentFile.fromSingleUri(uri).parentFile.listFiles()` 找同名 .lrc）、
`local/LocalLibrary.kt`（分组+搜索+排序）、`local/LocalFileDeleter.kt`、`local/LocalTrackMapper.kt`

### 歌词

`lyric/Lyric.kt`（`LrcParser` + `Lyric.indexAt` 二分查找）、`lyric/LyricLoader.kt`、
`lyric/EmbeddedLyrics.kt`（ID3v2 USLT/ULT/TXXX、FLAC VorbisComment、Ogg Vorbis/Opus、MP4 `©lyr`）、
`lyric/LyricEncoding.kt`（严格 UTF-8 → 严格 GBK → ISO-8859-1）

### 网络

| 文件 | 职责 |
|---|---|
| `net/HttpBridge.kt` | OkHttp 封装：`execute(url, opts)` 返回 `HttpResult(statusCode/statusMessage/headersJson/raw)`，Gson 序列化 header，`followRedirects(true)`，默认 15s 超时 |
| `net/UpdateChecker.kt`（V1.0.2） | 一次 HTTP 拉 `repos/{repo}/releases/latest`，解析成 `UpdateInfo(tagName/title/notes/url/apkName/apkSize)`；`compareVersions`/`isNewer` 处理 `v` 前缀、数字分段、预发布后缀排序。仓库是公开的，匿名请求即可（V1.1.4 删掉了访问令牌参数） |

### UI

| 文件 | 职责 |
|---|---|
| `ui/LxApp.kt` | V1.1.3 起只做壳的接线（200 行）；全屏页由 `ui/shell/LxShell.kt` 的 `CurtainSpec` 接管，不再 early-return |
| `ui/HomeChrome.kt` | V1.1.3：顶栏、更新公告条、迷你播放条、舞台正文（`StageContent`） |
| `ui/shell/LxShell.kt` | V1.1.3：舞台四槽位 `backdrop`/`tabs`/`dock`/`spotlight` + 幕布栈。V1.1.6 起 `visible` 由 `RouteStack` 决定，但**绘制顺序仍是 `LxApp` 里写死的那串**，所以层级可预测 |
| `ui/MainViewModel.kt`（508 行） | **V1.1.6 起是门面**：把 `MediaLibraryStore`/`PlayerUiStore`/`StatsStore`/`UpdateStore`/`BackupStore` 的 Flow 与方法转发出去。改状态逻辑要去 `ui/state/`，不要往这里加逻辑 |
| `ui/state/MediaLibraryStore.kt`（V1.1.6） | 扫描、缓存合并、分组、收藏、歌单、删除记账（`reconcilePendingDeletes` 等）。`groupByKey(key, kind)` **按 key 重算**，详情页必须走它 |
| `ui/state/PlayerUiStore.kt`（V1.1.6） | 包住 `PlayerController`；传输控制、队列操作、均衡器持久化（400ms 防抖） |
| `ui/state/StatsStore.kt` / `UpdateStore.kt` / `BackupStore.kt`（V1.1.6） | 统计窗口 + 年度；更新检查 + 24h 节流；备份导出导入 |
| `ui/StateAliases.kt`（V1.1.6） | `typealias` 把 `ui.state` 的状态类回接到 `ui` 包，屏幕与组件不用改 import |
| `ui/screens/GroupDetailScreen.kt`（V1.1.6） | 专辑/歌手详情：大封面 + 曲目表 + 播放全部/随机播放。`GroupDetailHost` 在幕布 260ms 退场期间保留最后一页，避免白屏 |
| `ui/shell/Route.kt`（V1.1.6） | `sealed interface Route`（含 `Group(key)`）+ `RouteStack`（`StateFlow<List<Route>>`）+ `topGroupRoute`/`isGroupOpen`/`routeGroupId` 三个 helper |
| `ui/screens/NowPlayingScreen.kt` | 播放页（霓虹舞台）；`PlayerSkin.VINYL`（唱片）/ `LYRICS`（全屏歌词）两种皮肤 |
| `ui/screens/StatsScreen.kt` | 播放统计页（玻璃卡片 + 渐变直方图） |
| `ui/screens/QueueScreen.kt` | 播放队列（自定义顶栏、当前行发光、进页自动定位） |
| `ui/components/RotatingArtwork.kt` | 黑胶唱片（累积角度动画、沟槽、唱臂） |
| `ui/components/LyricView.kt` | 卡拉OK 滚动歌词；`activeColor` 参数让高亮行带霓虹色 |
| `ui/components/Common.kt` / `CommonComponents.kt` | `TrackRow`（含 `active` 当前播放标记）/ `ConfirmSheet` 等共享组件 |
| `ui/theme/OledTokens.kt` | **所有间距/圆角/颜色都从这里取**，不要写字面量 |
| `ui/theme/ColorPalettes.kt` | 8 套配色（blue/purple/green/orange/pink/red/cyan/indigo） |
| `ui/neon/`（9 个文件） | 霓虹设计系统，见下方「Neon Vinyl 视觉系统」 |

### Neon Vinyl 视觉系统

风格来自 `ui-ux-pro-max` 技能：**Aurora UI**（music platform 首选，流动渐变 8-12s，
`cost:low`）+ **Dark Mode (OLED)** + **Glassmorphism**（rgba 0.06-0.13 填充 + 0.14 描边）。

| 文件 | 内容 |
|---|---|
| `ui/neon/NeonTokens.kt` | 色板 + 动效时长。`ink/inkDeep/inkSoft/surface` 底色；`neonA/neonB/neonC/accent` 霓虹；`glass/glassStrong/glassHi/stroke/rail` 玻璃；`brandGradient(themeColor)`、`auroraFrom(primary, secondary, themeColor)`、`glow/lighten/darken` |
| `ui/neon/NeonMotion.kt` | `rememberMotionEnabled()`（读系统"移除动画"开关）、`rememberLoopPhase(ms)`、`rememberBreath(ms)`——**都返回 `State<Float>` 而不是 `Float`**，让 Canvas 只重跑 draw 不重组 |
| `ui/neon/AuroraBackdrop.kt` | 三团 radialGradient 极光走椭圆轨道（11s/17s/23s 互质，避免同步）；`dim` 压暗、`baseColor` 兜底、`scrims` 上下压黑 |
| `ui/neon/GlassSurface.kt` | `Modifier.neonGlass(shape, fillTop, fillBottom, borderColor, borderWidth)`、`Modifier.neonChip(shape, active, accent)`（`NeonPanel` V1.1.3 已删，零调用） |
| `ui/neon/NeonProgress.kt` | 自实现进度条（不用 Material `Slider`：48dp 触摸区会盖住下方播放键）。`awaitEachGesture` 手写按下/拖动/抬手 |
| `ui/neon/NeonControls.kt` | `NeonPlayButton`（三层辉光渐变球）、`NeonIconButton`、`NeonStatusLabel` |
| `ui/neon/NeonShell.kt` | `NeonTab`/`NeonTabRow`（胶囊选中，形状+颜色双重编码）、`NeonHairline`（渐隐分隔线）、`NeonDot`、`NeonSectionHeader`、`isLightSurface`（`NeonHalo` V1.1.3 已删，零调用） |
| `ui/neon/NeonSurface.kt` | **双主题材质规格** `NeonSurfaceSpec` + `rememberNeonSurface(dark, themeColor)`。深色=极光+亮色玻璃；浅色=极光压到 ~7%+白色磨砂玻璃 |
| `ui/neon/NeonScaffold.kt` | **二级页面骨架** `NeonScreen(title, onBack, actions) { }`：极光背景 + 56dp 玻璃顶栏 + `navigationBarsPadding()` 内容槽；`NeonGlassAction` 38dp 玻璃圆钮。设置/均衡器/扫描文件夹/播放统计四屏共用 |

**四条不可违背的约束**（违反会崩或掉对比度）：

1. **禁止 `RenderEffect` 模糊。** 早期一次崩溃排查已把 API31+ 全屏 RenderEffect 从播放页删除
   （`NowPlayingScreen.kt` 有历史注释）。玻璃必须靠「半透明白 + 1dp 描边 + 顶部高光」画出来。
2. **文字色走 `OledTokens`（播放页）/ `MaterialTheme.colorScheme`（主界面外壳与列表）。**
   `OledTokensTest` 锁了 4.5:1；霓虹 token 只负责渐变、描边、活动态，不承担正文。
   外壳硬写亮色会让浅色主题下的文字消失。
3. **霓虹只上「正在发生的事」**：进度、正在播放的封面/行、随机/循环开启态、当前歌词行。
   静态装饰一律中性玻璃——整屏发光等于没有重点。
4. **极光是双主题的**（`NeonSurface.kt`）。浅色主题下铺深色极光会让 `onSurface`（近黑）
   正文压深底，直接不可读。

---

## 五、随机播放：语义、与 media3 的合同、踩过的坑（务必先读）

这是本项目历史最曲折的部分，从 v5.3 到 v1.0.1 迭代了十一个版本。
**当前语义（V1.0.1，`RandomShuffle.kt` 的 KDoc 是权威定义）**：

- **每次换歌抽一次（draw）**：`nextIndex` 在「除当前曲以外的所有槽位」上均匀抽一个，
  结果记在 `nextPick` 里，**在下一首歌真正开始之前反复问都给同一个答案**。
  这条「记住」是为了不重蹈两个坑：v5.3.6 之前是每次查询独立抽样，media3 一次决策里
  会问好几遍，答案互不相同 → 通知栏与按钮打架、播放头被来回拉（卡死）；
  v6.1 的 cycle 又矫枉过正，顺序被定死。
- **两个方向都是抽**：`previousIndex` 与 `nextIndex` 同规则，唯一区别是它额外避开
  **刚播完的那首**（`lastTrack`），所以连按两次「上一首」不会在两首之间弹跳。
  它**不是**「播放历史回退」，也不是「cycle 的前一位」。
- **`PlayHistory` 已删除**（V1.0.1）。以前 `prev()` 弹栈，历史为空时返回 -1，
  media3 把 -1 读成「没有上一首」→ `seekToCurrentItem(0)` = 当前曲从头重播，
  这就是「耳机第一次按上一首会重放本曲」。现在抽签永不返回 -1，那条分支进不去。
- **没有 cycle 了**：`cycleFrom` / `advanceCycle` / `cyclesDrawn` / `generation`
  全部删除。`generation` 当初只为「发现序被重画」而存在，抽签语义下没有序可画。
- **`firstIndex` / `lastIndex` 不是随机结果**：`firstIndex` 恒为 0、`lastIndex` 恒为
  `size-1`。media3 在建 `MediaPeriodQueue` 和 `repeatMode == ALL` 回绕时问它们，
  答案必须稳定（问两遍不同会让它重排缓冲）。「下一首」不走这两个函数。
- **按钮与系统走同一份 state**：App 按钮调 `nextIndex` / `previousIndex`，
  通知栏/锁屏/蓝牙走已安装的 `RandomShuffleOrder` → 同一个 `RandomShuffleState`。
- **没有「近期不重复」窗口（V1.4.2 明确）**：`pickOther` 的 `exclude` 只装当前曲
  （上一首方向再加刚播完那首）。同一首歌在几步之内再次出现是允许的——用户要的
  随机本来就允许重复，窗口会把随机换成「轮转的近亲」，而且是一条界面上看不见的规则。

**决策理由**见 [docs/adr/0001](docs/adr/0001-shuffle-cycle-and-play-history.md)。
注意：该 ADR 写于 V6.1，描述的是「一条 cycle 播完才重洗 + prev 走历史」那套已经被
V1.0.1 推翻的语义。它保留下来是因为下面表格里那些根因的推导过程仍有价值，**结论部分过期**。

V1.0.1 之前踩过的坑（**结论全部已被抽签语义取代，留作教训**——同一个错误形状还会再来）：

| # | 坑 | 教训（与具体算法无关的那部分） |
|---|---|---|
| 1 | memo 只按源位置建键、不记 size（v5.3.4） | ExoPlayer 把 `getNextIndex()` 的返回值当子索引**直接用，全程无范围校验**。任何缓存都必须带尺寸校验，越界值会直接损坏 timeline。 |
| 2 | KDoc 声称线程安全，代码里零 `@Synchronized`（v5.3.4） | 裸 `HashMap` 并发 `put` 撞 resize 可让桶链表成环 → 主线程遍历永不终止 = **ANR（无崩溃、无日志、就是卡死）**。当年 CrashLogger 抓不到东西就是这个原因。 |
| 3 | 三个 clone 都调 `invalidate()`（v5.3.5） | `MediaSourceList` 会构造**候选** order 试探长度，候选的副作用抹掉了在役 order 的状态。克隆必须走快照（`copy()`），不能共享可变状态。 |
| 4 | 对每个源位置各抽一次（v5.3.6） | `ShuffleOrder` 在 media3 契约里是一条**全序**；四个查询必须互相自洽，否则 `hasNextWindow` 说没有、`getNextWindowIndex` 又给出索引。 |
| 5 | `MediaControllerImplBase.seekToNextMediaItem()` 是**双动作**（v5.3.6） | 它先让 session 侧前进、再从 controller 镜像的 `PlayerInfo` 算一次 seek。两次答案不一致就把播放头拉回来。**只要一条自洽的序才能让两边同答**——这是当年「通知栏按下一首没反应、App 内按钮正常」这个不对称的根因。 |
| 6 | 每次换歌 `invalidate()`（v6.0 及以前） | 「每次查询独立抽样」在 20 首队列里前 10 首出现重复的概率约 89%。随机性由**什么时候重抽**决定，不由怎么抽决定——这就是 V1.0.1 的整个设计。 |
| 7 | `prev()` 抽 cycle 上一位（v6.0 及以前） | 那是本轮**还没播过**的一首，听起来像往前跳。V6.1 用 `PlayHistory` 弹栈修，V1.0.1 又改成抽签——中间那一版的复杂度（`expected` id 认领）全是为了兜「回退是异步的」。 |
| 8 | `installShuffleOrder()` 每次都装新 order（v6.0 及以前） | `setShuffleOrder` 内部 `createMaskingTimeline()` 造新 `PlaylistTimeline`（**无 equals**）→ `updatePlaybackInfo` 判 timeline 变了 → 派发 `onTimelineChanged` → app 又装一次。一次开关 = 一串 playlist rebuild，跨 binder 放大成卡顿。**判重的 guard 必须存在**。 |
| 9 | 耳机「上一首」重播当前曲 | 两层原因，都在 media3 默认行为里：① `seekToPrevious()` 比对 `getCurrentPosition() <= getMaxSeekToPreviousPosition()`（默认 3s），超过就 `seekToCurrentItem(0)`；② `hasPreviousMediaItem()` 问 `getPreviousIndex()`，答 `-1` 时同样落到重播分支。**两个都要堵**（V1.0 只堵了①）。 |

**改这块之前先读这几个测试文件**：`RandomShuffleTest`、`RandomShuffleStateTest`、
`RandomShuffleOrderTest`、`RandomShuffleCloneTest`、`RandomShuffleStateStalenessTest`、
`ShuffleStallProbe`、`QueueMirrorTest`——它们编码了上表每一行。

---

## 六、发布流程（含一个非显然的坑）

### 版本号

`app/build.gradle` 顶部是**单一真相来源**，`defaultConfig` 与 `versionedApk` 都读它：

```groovy
ext.appVersionCode = 3
ext.appVersionName = "1.1.5"
```

改版本只改这两行，**不要**去动 `defaultConfig`。

### 为什么需要 `versionedApk` 任务

QQ（及多数 IM 文件传输）**按「文件名 + 大小」去重**。此前每个版本都叫
`app-debug.apk`，且相邻版本字节数相同，
所以发新包给用户，QQ 认为「这个文件我已经有了」，**不认是新安装包**。

`versionedApk` 把产物复制成 `jijian-music-1.1.5-vc10.apk`——文件名唯一，问题消失。

### GitHub Actions（V1.0.3 起）

`.github/workflows/ci.yml`：push 到 main 跑全量单测（含 `_cnt.py` 汇总步）。

> **这份工作流在当前网络下跑不到**：这个工作流要求的路径是 `git push`，而 `git push`
> 在本机不通（第七节）。另外 `build.yml` 里**任何 `secrets.*` 表达式都会让工作流在解析期
> `startup_failure`**（p3–p7 二分确证：workflow 级 env / job 级 env / 内联 `run:` /
> 运行时 `$GITHUB_ENV` 全试过），所以 release job 已整个删掉。
>
> **实际发版流程是本地构建 + GitHub API 上传**（第七节）。App 内的检查更新读的是
> Release 的 tag / 标题 / 正文，与用哪条路上传无关。

### 签名

debug 签名，证书在 `C:\Users\32978\.android\debug.keystore`，
SHA-256 `74df8499788f972c17bd82167eff8d45fa9c7d327177ea0cc11a1fa0e2cddaed`。
apksigner：v2 scheme = true、v1 = false、v3 = false，单 signer。
**正式发布需要换成 release keystore 并打开 `minifyEnabled`。**

---

## 七、排障手册

### 发版：`git push` 不通，只有 GitHub API 这一条路

`github.com:443` 在这台机器上**连不上**。已试过并全部失败（不要重复试）：

- `140.82.116.4` / `140.82.116.3` / `140.82.114.4` 三个 IP —— TCP 直接超时（`curl` 返回 000）；
- `20.205.243.166`（DNS 当前解析到的那个）—— `curl https://github.com/` 能返回 200，但
  `git-receive-pack` 端点走到 401 后 `schannel: recv returned error 56` / `Connection was reset`；
- `-c http.curloptResolve=github.com:443:<ip>`、`-c http.version=HTTP/1.1`、
  `-c http.sslBackend=openssl`、`-c http.postBuffer=...`、URL 内嵌 token、
  `http.extraheader="Authorization: Basic ..."`、各种 `credential.helper` 写法、重试 3 次。

系统里也没有可用代理（`ProxyEnable=0`，7890 / 10809 端口都不通）。

**唯一可用路径是 GitHub API 的 Git Data 接口**，`api.github.com` 与
`uploads.github.com` 都不受影响：

1. `GET /repos/<repo>/git/ref/heads/main` → 远端 sha；
2. `GET /repos/<repo>/git/commits/<sha>` → `base_tree`；
3. `GET /repos/<repo>/git/trees/<base_tree>?recursive=1` → `path → sha` 表，
   与本地 `git ls-tree -r HEAD` 的 blob sha 比对，得出 changed / deleted；
4. 每个 changed 文件 `git cat-file blob <sha>` → `POST /git/blobs`
   （`{"content": <base64>, "encoding": "base64"}`）；
5. `POST /git/trees`（`{"base_tree": ..., "tree": [{"path","mode","type","sha"}, ...]}`，
   删除项写 `"sha": null`）；
6. `POST /git/commits`（message 取 `git log -1 --pretty=%B`，`parents: [远端 sha]`）；
7. `PATCH /git/refs/heads/main`（`{"sha": <新 commit>}`）。

发 Release 走同一条路：`POST /git/refs`（建 tag）→ `POST /releases` →
`POST https://uploads.github.com/repos/<repo>/releases/<id>/assets?name=<文件名>`
带 `Content-Type: application/vnd.android.package-archive`。同名 asset 要先
`DELETE /releases/assets/<id>`。

token 取法（**必须传 `"get"` 且用 `encoding="utf-8"`**，否则读出来是乱码）：

```python
subprocess.run(
    [r"C:\Program Files\Git\mingw64\bin\git-credential-manager.exe", "get"],
    input="protocol=https\nhost=github.com\n\n",
    capture_output=True, text=True, encoding="utf-8",
)
# 取以 password= 开头那一行的后半段（40 字符）
```

遇 `EOF occurred in violation of protocol` 或 `WinError 10061` 直接重试，是瞬时故障。

> 这两个脚本（`_api_commit3.py` 与 `_rel*.py`）放在仓库外的 `D:\demo` 下，**会被清理掉**，
> 上面七步就是重建依据。

### 用户报「闪退」/「卡死」

本机无设备时**必须靠用户回传日志**。日志已自动落盘在两处：

- `context.filesDir/last_crash.txt`（App 私有目录）
- `context.getExternalFilesDir(null)/last_crash.txt`（adb 可拉）

用户在 App 内可通过 `CrashReportDialog` 查看并复制。

### 历史教训（值得重复的部分）

- **假修复（早期）**：`LyricView` 底部渐隐 `Brush.verticalGradient` 只给了 1 个
  colorStop → 绘制期抛 `IllegalArgumentException: colors must have length of at least 2
  if colorStops is omitted.`。这个确实是个真 bug（且只在唱片皮肤 + 有歌词时触发），
  但**它不是用户报的那个闪退的根因**——修完用户说「没有效果」。
  教训：**能定位到一个真 bug ≠ 找到了用户报的那个 bug**。
- **探针假红**：`ShuffleConcurrencyProbeTest` 第一版用
  `while (!stop.get())` + `reader.join(6000)`，但 `stop.set()` 写在 `join` 之后 →
  线程永不退出，误判成「HashMap 竞争已复现」。**撤回，不可当根因**。
  教训：**建反馈回路时先验证回路本身能红能绿**。
- **测试断言错，不是代码错**：`everyDrawStaysInRangeAcrossManySizes` 首版断言
  size 1 时 `getNextIndex` 必须在 `0 until 1`，但 `size 1 → -1` 是既有契约。
  **改断言，不改代码**。

---

## 八、工程约定（违反会踩坑）

### 写文件：UTF-8 无 BOM，中文建议走转义

**这是本项目最高频的坑。** PowerShell 5.1 的 `Set-Content -Encoding UTF8` **会写 BOM**，
导致 Gradle/Kotlin 解析失败；用 here-string 直接写含中文的 .kt 会产生 GBK 乱码。

**正确做法**（终止行必须顶格，前后不能有空格）：

```powershell
# 终止行必须顶格、前后无空格；标记可以自定义
$py = @'
import io
src = '''...极简music...'''
io.open(r"path/to/file.kt", "w", encoding="utf-8", newline="\n").write(src)
'@
[System.IO.File]::WriteAllText("$PWD/_x.py", $py, (New-Object System.Text.UTF8Encoding($false)))
python -X utf8 _x.py
```

> 坑：here-string 的终止符只要在正文里**顶格**出现就会提前截断脚本。
> 用 @' / '@ 当标记时，文档里凡是要展示这个符号的地方都会踩到（本报告第一次生成就因此失败）。

改已有文件用「读 → `if s.count(a) != 1: sys.exit(1)` 校验唯一 → `replace(a, b, 1)` → 写回」。

> **更省事的做法（统一用它）：直接用文件写工具（`write` / `edit`）。**
> 它自己按 UTF-8 无 BOM 写，中文原样输进去就行，不需要 here-string 那一圈转义，
> 也就没有终止符截断的坑。上面那段 PowerShell 手法只在必须用脚本批量改文件时才有意义。
>
> 读回核实中文时同理：`Get-Content` 在 PowerShell 5.1 下按 ANSI 解码，会把
> 「当前行」显示成「褰撳墠琛岋」——**不是文件坏了，是终端解码错了**。用 `read` 工具，
> 或 `python -X utf8 -c "import io;print(io.open(p,encoding='utf-8').read())"`。

### 其他

- Groovy DSL 不是 Kotlin DSL：`minifyEnabled false`（**不是** `isMinifyEnabled =`）、
  `excludes.add(...)`（**不是** `+=`）
- Room 用 kapt 时 `id("org.jetbrains.kotlin.kapt")` **不带版本号**（避免 classpath 冲突）
- 测试统计**不要用 PowerShell 的 `[xml]`** 解析测试 XML（会崩），用 Python 正则读
  `app/build/test-results/testDebugUnitTest/*.xml`
- `RepeatMode` 是 `com.lxmusic.android.player` 包的**顶层** enum，不是嵌套类；
  `PlayerController.RepeatMode.ALL` 会报 Unresolved reference
- `MediaController` **没有** `audioSessionId`，要从 `PlayerHolder.player` 取
- `Equalizer.getPresetBandLevel(preset, band)` **不存在**；应 `usePreset(preset)` 后读 `getBandLevel(band)`
- `BlurredEdgeTreatment` 在 `androidx.compose.ui.draw`，**不在** `.graphics`
- `Lyric` **没有** `isNotEmpty`，只有 `val isEmpty: Boolean`；判断写 `if (!lyric.isEmpty)`
- `Modifier.padding(horizontal=, top=)` **不存在**这个重载；要链式
  `.padding(horizontal = 26.dp).padding(top = 2.dp)`
- `androidx.compose.foundation.interaction.collectIsPressedAsState()` 才有，别自己造名字
- `Modifier.matchParentSize()` 只能在 `BoxScope` 里调；`Modifier.drawBehind` 里想画满尺寸
  用 `size`（`DrawScope` 的），不要试图在外面调 `matchParentSize`
- 列表分隔线**不要**再用 `androidx.compose.material3.Divider`（已废弃）——本项目统一
  用 `NeonHairline()`

---

## 九、已知问题与技术债（接手后建议优先处理）

### V1.0.2 变更（设置页可检查更新 + 更新内容公告）

| 需求 | 落地 | 回归 |
|---|---|---|
| 设置页加「检查更新」按钮 | 新增 `net/UpdateChecker.kt`：一次 HTTP 拉 `repos/{repo}/releases/latest`，解析成 `UpdateInfo`。`MainViewModel.checkForUpdates()` 里 `viewModelScope.launch { withContext(Dispatchers.IO) { ... } }`，`checkSeq` 递增驱动对话框只弹一次 | `UpdateCheckerTest`（11） |
| 拉取当前 GitHub 版本并判断是否更新 | `UpdateChecker.compareVersions(a, b)` / `isNewer(remote, current)`：`v1.0.2` 与 `1.0.2` 等价、缺位补 0、数字分段**按数值比**（`1.0.10 > 1.0.9`）、预发布后缀字典比且「无后缀 > 有后缀」 | `UpdateCheckerTest`（6 个版本比较用例） |
| 更新内容公告 | `UpdateDialog` 显示本机版本 / 最新版本 / 安装包大小 + release body（`notes`）。**V1.1.7 起改为应用内下载安装**：`ApkDownloader` 流式落盘 + digest 校验，`UpdateInstaller` 经 FileProvider 拉起系统安装器，不再跳浏览器 | 实机点按 |
| ~~私有仓拿不到数据~~ | V1.0.2 时的历史：仓库是私有的，匿名 `/releases/latest` 返回 404，于是加了「访问令牌」设置项。**V1.1.4 仓库转公开后这条与令牌一起删除**，现在匿名请求即可 | — |

> **读写同键是硬约束**：`SettingsStore` 的读写两侧必须用同一个 KEY 常量。键名拼错
> **不会报错**，只会永远读回默认值——「均衡器不保存」那一类缺陷就是这么来的。
> `writeEq` / `writeUpdateToken` 之所以抽成 `internal fun`，就是为了能在 JVM 单测里
> 走真实写入侧验证往返。


### V1.0.3 变更（静默更新检查 + 首页公告条 + CI + 清理）

| 变更 | 说明 | 护栏 |
|---|---|---|
| 启动静默检查更新 | `MainViewModel.maybeCheckForUpdatesOnStartup()`：每 24h 一次（`UPDATE_CHECK_INTERVAL_MS` + 新 KEY `last_update_check_at` 节流）。静默失败或「已是最新」**退回 IDLE**——设置页副标题不显示用户没主动发起过的检查结果；手动检查不节流 | 行为锁在 LxApp 的 LaunchedEffect，节流值持久化 |
| 首页新版本公告条 | `LxApp.UpdateBanner`：仅 `AVAILABLE` 时出现在标签栏上方，玻璃语言（neonGlass + 品牌竖条 + NeonDot 呼吸点），点按复用设置页的 `UpdateDialog`（已从 private 改 internal） | 复用对话框，不新建第二份公告 UI |
| GitHub Actions | `.github/workflows/ci.yml`：push → 全量单测 | 本机 `git push` 不通、`secrets.*` 会让工作流 `startup_failure`，release job 已删；发版实际走 GitHub API（第七节） |
| 死代码清理 | 删 `allowShowUpdateAlert`（`LxModels.kt`/`AppDatabase.kt`，零读者）；三份被 V1.0.1 推翻的设计文档移入 `docs/archive/`；`_cnt.py`/`_per_class.py`/`_probe_stall.ps1` 去 BOM、去硬编码绝对路径；`ShuffleStallProbe` 注释不再指向已删的 `_dex/` | `recordHistory` **有意保留**：MainViewModel 读它 gate `repo.markPlayed`（最近播放），不是死开关 |

### V1.0.1 变更（两个方向都改成抽签 + 两个 seam）

用户报的三条：「这一次的下一首随机，上一首不随机」「耳机切换依然第一次会重新播放这首歌」
「随机播放下这一次的播放顺序是固定的，不是随机的」。三条指向同一件事：
V1.0 的修法只补了一半。

| 缺陷 | 根因 | 修法 | 回归 |
|---|---|---|---|
| 随机模式下「上一首」不随机 | `RandomShuffleState.previousIndex()` 走的是 cycle 前驱——那是**本轮还没播过**的一首，听起来像往前跳；`prev()` 另一条路径弹 `PlayHistory`，历史为空时返回 `-1` → 重播当前曲 | 两个方向都改为**抽签**：`previousIndex` 与 `nextIndex` 同规则（在除当前曲外的槽位上均匀抽），额外避开刚播完的 `lastTrack`，所以连按两次上一首不会在两首之间弹跳。`PlayHistory.kt` 整个删除，`RandomShuffleOrder` 的 `previousOf` 钩子一并删除 | `RandomShuffleStateTest`（18）、`RandomShuffleOrderTest`（11）、`ShuffleStallProbe`（9） |
| 耳机「上一首」第一次必重播当前曲 | V1.0 只堵了 `getMaxSeekToPreviousPosition()` 那条（位置 > 3s）。还有第二条：`hasPreviousMediaItem()` 问 `ShuffleOrder.getPreviousIndex()`，旧的 `previousOf` 在空历史时返回 `-1`，media3 读成「没有上一首」→ `seekToCurrentItem(0)` | 不是给空历史特判，而是**让抽签永不返回 -1**（队列 > 1 时恒返回合法且不同于当前的下标），`else` 分支进不去。阈值覆写保留作顺序模式第二道防线 | `RandomShuffleOrderTest.previousAlwaysAnswersALegalSlot`、实机 `KEYCODE_MEDIA_PREVIOUS`×5 每次换歌 |
| 随机顺序是固定的 | 就是 cycle 本身：一条全序播完才重洗，用户听到的是重复的同一串顺序 | 删除 cycle 全部机制（`cycleFrom` / `advanceCycle` / `cyclesDrawn` / `generation`），改为每次换歌抽一次并缓存到下一首开始 | `RandomShuffleTest`（6）、`ShuffleStallProbe.everyDrawFromTheSameTrackCanReachEveryOtherTrack` |
| 八处复制粘贴的「镜像 playlist 五步」 | `tryMirrorPlaylist` / `ensureMirrorPlaylist` / `enqueue` / `playNext` / `moveInQueue` / `removeFromQueue` / `clearQueue` / `rebuildFrozenSource` 各自手写同一串步骤，v6.1 就因此在 `moveInQueue` 里漏了第 1 步（先移动再装序） | 新增 `player/ShuffleSink.kt`（install / drop 的 seam）与 `player/QueueMirror.kt`（五步按固定顺序收在一处）；`PlayerController.playlistMode` 字段删除，改问 `mirror.isMirrored` | `QueueMirrorTest`（17） |

**同时清掉**：`PlayerController` 里 `shuffleState.advanceCycle` 的两个发散调用点、
`resetShuffle()` 的两行 pass-through 语义模糊、`forgetInstalledOrder()` 在九处被手写调用。

### V1.0 变更（重新编号首发 + 两处播放修复）

版本号从 `6.2.0 / vc26` 回到 **`1.0.0` / `vc1`**，作为正式首发编号；版本号不再表示
「迭代次数」，而是「对外发布序号」。三个改动：

| 缺陷 | 根因 | 修法 | 回归 |
|---|---|---|---|
| 随机播放要按几次「下一首」才真的跳 | `RandomShuffleState.nextIndex()` 在走到全序末位时**在查询内部**重洗（`drawn()` 无条件 `cyclesDrawn++` + `generation.bump()`）。media3 一次决策会反复问 `getNextIndex`（`hasNext`、通知栏按钮、`MediaPeriodQueue`），于是同一个决策拿到**两条不同的洗牌全序**；app 按钮与媒体会话共享同一 state，任一侧重画就让另一侧错位，周期在非边界处被截断 | `nextIndex()` 改为**纯查询**（画序仅发生在 `orderFor` 缺失时，末位只做防御性回绕，且跳过刚播完的那首）；新增 `advanceCycle(size, current)` 作为**周期推进的唯一入口**，只在真的换了歌时调用（`onMediaItemTransition` 与 `playIndex` 两处），且幂等 | `RandomShuffleStateTest`（20，新增 `queryingAtTheCycleEndDoesNotRedrawIt`、`rollingTheCycleTwiceForTheSameTrackIsHarmless`、`queryingAtTheCycleEndNeverAnswersWithTheTrackThatJustPlayed`）、`ShuffleStallProbe`（8） |
| 耳机/蓝牙/锁屏「上一首」把当前曲从头重播 | `BasePlayer.seekToPrevious()` ≠ `seekToPreviousMediaItem()`：它先比对 `getCurrentPosition() <= getMaxSeekToPreviousPosition()`（media3 默认 3000ms），**超过就 `seekToCurrentItem(0)` = 重播当前曲**。这个默认是为播客「回退几秒」设计的，对音乐播放器是错的 | `PlaybackService` 里 `ExoPlayer.Builder().setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)`，让阈值条件不可达，每次「上一首」都走 `seekToPreviousMediaItemInternal`。app 内按钮本来就不受影响（直连 `PlayerController.prev()`） | 反汇编确证 + 手动真机 |
| 版本号语义混乱 | 6.x 系列累积到 vc26，对外发布序号没有意义 | `ext.appVersionCode = 1` / `ext.appVersionName = "1.0.0"`，产物 `jijian-music-1.0.0-vc1-debug.apk` | `aapt2 dump badging` = `versionCode='1' versionName='1.0.0'` |

> **注意升级路径**：`versionCode` 从 26 降到 1，Android 会把它当成**降级**，
> `adb install` 需带 `-d`，用户侧覆盖安装会被系统拒绝，必须卸载重装。

> **V1.0 之前（v6.2.0 / v6.1.x / v6.0.x / v5.3.x）的逐版本缺陷表已移除。**
> 那些修复都还生效，但描述的是已被 V1.0.1 推翻的播放语义，留档只会误导。
> 需要追溯时看 `git log`（V1.0 起）或 `D:\demo\backups\` 里的源码 zip。

### 高优先

0. **文档与代码冲突时改文档**，不要反向迁就旧描述。V1.0.1 这一轮已把 README
   与本节里 V1.0 之前的章节删掉——那些描述的播放语义（全序 / cycle / 播放历史栈）
   已全部作废，留着比没有更危险。

1. **无 release 签名配置**，`minifyEnabled false`，无 ProGuard 规则 → 无法正式发布。

2. **`ui/MainViewModel.kt` 已 30 KB**，是所有 UI 状态的唯一容器，继续增长会难以维护。

### 中优先

3. `data/stats/PlayStats.kt` 的 `inRange` 是 public 但生产路径未使用（只被测试调用）——
   疑似 Speculative Generality。
   建议：删掉，或让 `MainViewModel.refreshStats()` 用它在客户端再过滤一次
   （当前 `buckets()` 对越界记录靠 `if (index in counts.indices)` 静默丢弃，
   `dayBuckets` 靠 `for (i in starts.indices.reversed())` 兜底，两条路径都容错但无测试
   断言「窗口外的记录一定不影响任何一列」）。
4. `ALL` 档的 `windowStart` 返回 `0L`（全历史查询），但直方图只画 30 天，语义不一致。
5. `play_event` 表有 365 天保留期（`MusicRepository.RETENTION_MS`），
   写入时顺带 `prune`，但**没有清理 UI 提示**。
6. `RandomShuffleState` 的缓存（`nextPick` / `previousPick`）无过期机制，靠
   `onTrackStarted` / `invalidate` 两道入口清；任一路径漏调就退化成「按一次不动、
   按第二次才走」——`RandomShuffle.kt` 的 KDoc 专门写了这条，**改换歌路径时必须回来读**。

### 低优先

7. `examples/example-source.js` 全项目零引用（README 只提概念、不给路径）。
8. `docs/adr/0001-shuffle-cycle-and-play-history.md` 与 `docs/v6.1-refactor-plan.md`
   描述的是已被 V1.0.1 推翻的语义。保留是因为推导过程仍有参考价值，但**不要照它改代码**；
   要么补一份 `0003-shuffle-draws.md` 记下当前契约，要么把这两份移进 `docs/archive/`。

---

## 十、测试

**422 项 / 41 个测试类 / 0 失败**（V1.4.6 实测）。全部是 JVM 单元测试（`app/src/test/java/`），
**不需要设备**。

> **V1.0 → 217 项 / 25 类；V1.0.1 → 222 项 / 25 类；V1.0.2 → 235 项 / 26 类；V1.0.3 → 235 项 / 26 类；V1.1.1/1.1.2 → 235 项 / 26 类；V1.1.3 → 230 项 / 26 类；V1.1.4 → 229 项 / 26 类（删访问令牌 2 项、加自动检查 1 项）**（`PlayHistoryTest` 9 项随
> `PlayHistory.kt` 删除，新增 `QueueMirrorTest` 17 项，四个 shuffle 测试类按抽签语义重写）。
> **V1.1.5 → 237 项 / 26 类；V1.1.6 → 273 项 / 29 类**（新增 `BackupCodecTest` 15、`RouteStackTest` 11、
> `AnnualReportTest` 10；`SettingsRoundTripTest` 11 → 10）；**V1.1.7 → 289 项 / 30 类**
> （新增 `ApkDownloaderTest` 12）；**V1.1.8 → 293 项 / 30 类**（`ApkDownloaderTest` 12 → 16，
> 新增 4 个 `canAppend` 续传判定用例）；**V1.2.0 → 350 项 / 36 类**
> （新增 `LocalIndexTest` 10、`IncrementalScanTest` 8、`ScanReportTest` 5、
> `CoverTierTest` 9、`QueueSummaryTest` 12、`QueueDragTest` 13）；
> **V1.3.0 → 382 项 / 38 类**（新增 `SmartCollectionsTest` 17、`UnifiedSearchTest` 15）。
> **V1.4.0 → 411 项 / 41 类**（新增 `TrackDescriptionTest` 12、`LyricTuningTest` 11、`WidePaneTest` 6）。
> **V1.4.1 → 412 项 / 41 类**（`SettingsRoundTripTest` 10 → 11，新增 `showRecentRail` 默认值与往返）。
> **V1.4.2 → 415 项 / 41 类**（`RandomShuffleTest` 6 → 9：重复排除下标不炸候选数组、
> 刚播过的下标仍抽得到、越界下标不占槽位）。
> **V1.4.3 → 418 项 / 41 类**（`RandomShuffleStateTest` 18 → 19：`aCopyIsASnapshotNotASharedView`
> 改写为 `aCopySharesTheMemoSoBothSidesGoStaleTogether`，新增 `everyCurrentIndexKeepsItsOwnAnswer`；
> `RandomShuffleCloneTest` 7 → 9：删 `aSnapshotDoesNotLeakBackIntoTheParent`，新增
> `aCloneHearsTheTrackStartedToo` 与 `invalidateBlindsTheCloneAsWell`）。
> **V1.4.6 → 422 项 / 41 类**（`RandomShuffleStateStalenessTest` +5，压在 `forgetDraws` 上）
> **V1.4.7 → 426 项 / 42 类**（新增 `RandomShuffleBacktrackTest` 4 例；`RandomShuffleStateStalenessTest`
> 里那条按旧语义写的用例改名并改写为 `forgetDrawsKeepsTheTrackWeCameFromSoPreviousStillAvoidsIt`）
> **V1.4.5 → 417 项 / 41 类**（删 `SettingsRoundTripTest.autoCheckUpdatesDefaultsOnAndSurvivesAWrite`
> 一项；`QueueMirrorTest.everyEditResetsBothModes` 改名
> `everyEditResetsRepeatModeButLeavesShuffleToItsOwner`；`UpdateCheckerTest` 两个选包
> 用例按「debug 优先」重写）。
> V1.0 之前的基线（144/17 → 214/25）见 `git log`。

| 测试类 | 项数 | 覆盖 |
|---|---|---|
| `EmbeddedLyricsTest` | 18 | ID3v2/FLAC/Ogg/MP4 内嵌歌词解析 |
| `PlayStatsTest` | 17 | 日/周分桶、排行、时区（固定 `Asia/Shanghai`） |
| `RandomShuffleStateTest` | 19 | **抽签契约**：next 排除当前曲、prev 同样排除且额外避开刚播完的那首、一次抽签在下一首开始前反复问答案不变、**每个 `current` 各有槽位（V1.4.3）**、`onTrackStarted` 清空缓存、**副本与父实例共享同一本账（V1.4.3）**、2 首队列两个方向都能换 |
| `LocalLibraryTest` | 15 | 分组、搜索、排序 |
| `TrackDescriptionTest` | 12 | **V1.4**：一行一句话，而不是三个独立 `Text`；动词随动作变——分组行说「打开」绝不说「播放」，迷你播放器说「打开播放页」；空字段不留孤零零的分隔符，空标题降级成「未知曲目」；队列的两字符串重载与模型重载一致 |
| `LyricTuningTest` | 11 | **V1.4**：加减档位落在与设置页滑块完全相同的 0.7×–1.6× / ±500 ms 区间（否则滑块打开时显示一个它自己拖不到的值）；连续 ±5% 停在两位小数，两处都打印 `1.35×`；偏移文本带显式正负号，`120 ms` 单独看不出方向；到边界时按钮自报 disabled |
| `WidePaneTest` | 6 | **V1.4**：窄屏永远只有一栏；播放页压过专辑页（用户刚点开它）；关掉播放页回落到还开着的专辑页，而不是把右栏清空；没有曲目的播放页不为了显示一张空唱片占掉半屏 |
| `SmartCollectionsTest` | 17 | **V1.3**：`dateAdded = 0` 是「不知道」不是 1970（信它会让首次升级把整库塞进「最近添加」）、未来时间也不算新、最常播放次数相同比最近、次数为 0 不进架、「从未播放」在有播放记录之前保持为空（否则就是整库）、`lastPlayedAt = 0` 不算「很久没听」、四架顺序固定且空了自己消失、一首歌可以同时上多架 |
| `UnifiedSearchTest` | 15 | **V1.3**：空白查询保留原文但不出结果、歌曲段就是调用方已过滤的结果（匹配只在 `LocalIndex` 一处，重复实现会漂移）、已在歌曲段出现的收藏不重复、专辑/歌手从**匹配后的**曲目分组且各封顶六、歌单按名字或按内含曲目都能命中、`totalCount` 是五段相加 |
| `QueueSummaryTest` | 12 | **V1.2**：位置从 1 起算、`remaining` 只数当前曲之后的曲目而 `remainingSeconds` 含当前曲剩余、过期 position 被夹到本曲长度、分钟取整会跨进小时 |
| `QueueDragTest` | 13 | **V1.2**：半行在**两个方向**都进位（`roundToInt` 把 -0.5 取 0，朴素写法会让向上拖慢半行）、落点夹紧队列而浮动偏移保留全程、被跨过的行反向让位一格、起点越界与零行高都是空操作 |
| `LocalIndexTest` | 10 | **V1.2**：索引对每个查询都答出与 `LocalLibrary.search` 相同的曲目（逐查询比对而非手挑断言）、空白查询按引用返回已排序列表、排序在过滤后保持、分组跑在索引子集上 |
| `CoverTierTest` | 9 | **V1.2**：72/200 dp 的档位边界、请求像素**向上**取整（向下会让 Coil 在最后一行把位图放大回去）、上限按档位分组、零尺寸永不传给 Coil（在 Coil 里 0 表示「按原图」） |
| `IncrementalScanTest` | 8 | **V1.2**：只有 `lastModified` 与 `size` **同时**匹配才复用——只信时间戳会在同秒原地重写后留下旧时长，而 provider 报 0 是「没有信息」不是「没变」 |
| `ScanReportTest` | 5 | **V1.2**：真跑过扫描才有 `hasRun`、总数为 0 时 `reusedFraction` 不炸、超过 1000 ms 才切到秒 |
| `QueueMirrorTest` | 17 | **V1.0.1**：镜像 playlist 的五步按固定顺序执行、`move`/`remove` 在播放器改动**之后**才丢弃已装 order、`replace` 拒绝无法镜像的队列 |
| `QueueLogicTest` | 8 | 队列 `move`/`remove` 纯逻辑（`nextIndex`/`prevIndex` 与它们的 4 个用例 V1.1.3 删除） |
| `RandomShuffleOrderTest` | 11 | **V1.0.1** `ShuffleOrder` 契约：prev 恒答一个合法且不等于当前的下标，克隆体继续从同一份 state 抽 |
| `LrcParserTest` | 8 | LRC 时间戳解析 |
| `ShuffleStallProbe` | 9 | **V1.0.1**：app 走 500 步 + 系统走 500 步都不卡、两条路径永远选同一首、从同一首出发的多次抽签能覆盖全部 N-1 个替代 |
| `NeonTokensTest` | 8 | 极光峰值 4.5:1 红线、品牌渐变三色可辨、动效节律 |
| `ThemePaletteTest` | 8 | 8 套配色的 light/dark scheme |
| `RandomShuffleCloneTest` | 7 | 克隆协议 |
| `EqProfileTest` | 7 | 频段编解码往返、坏数据丢弃不抛 |
| `StallWatchdogTest` | 8 | 静默停顿检测——重试预算、二次重缓冲、仅缓冲态停顿、进度一动就清零（假时钟）；缓冲不消耗冻结预算 |
| `OledTokensTest` | 6 | 设计 token 一致性 + 4.5:1 对比度 |
| `RandomShuffleStateStalenessTest` | 6 | 队列缩容后 memo 不越界 |
| `LocalLibraryMergeTest` | 6 | 本地库合并 |
| `RandomShuffleTest` | 9 | **V1.0.1**：`pickOther` 恒落在范围内且永不返回被排除的槽位；600 seed 上的均匀性。**V1.4.2** 加三条：重复的排除下标不得把候选数算成负数（`NegativeArraySizeException`）、刚播过的下标仍然抽得到（钉住「不做近期去重窗口」这条决定）、越界下标不占槽位 |
| `SafMusicScannerTest` | 5 | SAF 扫描 |
| `UpdateCheckerTest` | 11 | **V1.0.2**：release tag 的版本比较（去 `v` 前缀、数字分段、预发布后缀排序）+ GitHub release JSON 解析（取首个 .apk 资产、字段缺失降级、标题为空回落 tag） |
| `SettingsTest` | 5 | 设置默认值与枚举取值 |
| `SettingsRoundTripTest` | 11 | 写入侧与读取侧用同一批 `KEY_*` 往返——每个 EQ 字段 + 枚举回落 + 上次播放指针，堵住「均衡器不保存」那类默认值掩盖的缺陷；**V1.4.1** 加一条 `showRecentRail`：默认开、关掉后读回来仍是关的（这一项的存在意义是「最近在听」的关闭是偏好不是一次性 UI 状态） |
| `QueueIndexTest` | 5 | 队列槽位只有在该槽位的 **id** 就是正在播的那首时才算数（在线队列 `playIndex(3)` 被报成 0 的缺陷） |
| `LocalTrackMapperTest` | 4 | 曲目映射 |
| `PlayerSkinTest` | 4 | 皮肤解析与未知值回落 |
| `RecordPolicyTest` | 3 | `recordHistory` 与 `recordStats` 互相独立 |
| `BackupCodecTest`（V1.1.6） | 15 | 指纹忽略大小写/空白/秒内抖动、精确 id 优先、重复指纹取首条、`report` 三分（精确/指纹/缺失）、JSON 往返、未来版本与垃圾数据被拒、zip **和**裸 JSON 都能读 |
| `RouteStackTest`（V1.1.6） | 11 | 重复打开是 no-op、两个不同 key 的分组页共存、`close` 可乱序、`closeTop` 按绘制顺序而非打开顺序、分组类型由 `album:`/`artist:` 前缀推出 |
| `AnnualReportTest`（V1.1.6） | 10 | 年度窗口**两端闭合**（恢复带进来的未来事件不能漏进今年）、时长按次数累加、空歌手归并成「未知歌手」、歌手/单曲并列时的取舍、空年份返回对象而不是抛异常 |

**跑单个测试类**：

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests "com.lxmusic.android.player.RandomShuffleCloneTest" --no-daemon --console=plain
```

**数测试项不要用 PowerShell 的 `[xml]`**（大文件会慢到超时，且格式化属性会把计数搞错）。
用正则扫 `app/build/test-results/testDebugUnitTest/*.xml` 的 `<testcase ` 即可，
项目根的 `_cnt.py` 就是干这个的（`_per_class.py` 输出每个类的项数，
`_probe_stall.ps1` 是「位置冻住」的长播探针，需要模拟器在线）：

```powershell
& python _cnt.py     # files=41 tests=422 failures=0 errors=0
```

V1.1.6 起 `_cnt.py` / `_per_class.py` 在仓库根，无 BOM、无硬编码路径。

---

## 十一、历史版本与备份

**V1.0 起**用 git：`git log` / `git show <ref>:<path>`，远端
`https://github.com/xxwzh66-byte/jijian-music`（公开），tag `v1.0.0` 对应首发。

**V1.0 之前**只有 `D:\demo\backups\` 里三个源码 zip（v5.3.0.1 ×2、v5.5.2），
里面是旧的 `ShuffleBag.kt` / `ShuffleBagTest.kt`，**没有 `RandomShuffle` 的历史版本**。

项目根**没有** `UPDATE_PLAN.md`（早期版本的控制台报告里提过这个文件名，实际从未入库）。
版本历史的权威记录是 `README.md` 的版本章节：当前版本在最前面的 `V1.1.6` 一节，
往下是时间线留档（早于 V1.0.3 的章节只用于追溯，不代表当前行为）。
逐版本的更新公告在 `docs/release-notes/`（`1.1.2` → `1.1.6`）。

领域词表是 `GLOSSARY.md`（队列 / 镜像 playlist / 抽签 Draw / Next / Previous / 循环 /
静默停顿 / 偏好 / 均衡器曲线 / 效果状态）。

`docs/archive/` 下三份是**早期**设计留档：`0001-shuffle-cycle-and-play-history.md`
（ADR：为什么当时选 cycle，**结论已被 V1.0.1 推翻**）、
`0002-detect-silent-stalls.md`（ADR：静默停顿检测，仍然有效）、
`v6.1-refactor-plan.md`（施工单 + 验收记录，历史）。V1.1.6 删掉了空目录 `docs/adr/`。

---

## 十二、接手检查清单

- [ ] 确认 `JAVA_HOME` 指向 `D:\demo\toolchains\toolchain\jdk17\jdk-17.0.20.1+1`
      （**不要**改成 `C:\Program Files (x86)\Android\openjdk\jdk-21.0.8`：AGP 会报
      `Value ... given for org.gradle.java.home is invalid`），`android-sdk` 存在
- [ ] 确认 `subst X: "D:\demo\01_项目代码"` 已生效（否则 Kotlin 编译守护进程会崩，见第二节）
- [ ] 确认 `D:\demo\_bx4.sh` 存在；不在就照第二节的代码块重建（它会被清理掉）
- [ ] 跑一次标准构建命令，确认 `BUILD SUCCESSFUL` + 426 测试 0 失败
- [ ] 读 `player/RandomShuffle.kt` 的 KDoc + `RandomShuffle*` 五个测试类 + `ShuffleStallProbe` + `QueueMirrorTest` + `StallWatchdogTest`
- [ ] 读 `ui/theme/OledTokens.kt` 与 `ui/neon/NeonTokens.kt`（改 UI 前必读）
- [ ] 读本报告第九节，决定先还哪笔技术债
- [ ] 若要真机调试：`adb install -r -d build/apk/jijian-music-1.4.7-vc22-debug.apk`
      （`-d` 必需：`versionCode` 相对 6.x 时代的 26 是降级，系统默认拒绝）

---

## 十三、常用命令速查

```bash
# 构建 + 出包 + 测试（脚本在仓库外，见第二节）
bash /d/demo/_bx4.sh assembleDebug versionedDebugApk testDebugUnitTest
# 发版只发 debug：从 V1.4.5 起不跑 assembleRelease / versionedApk

# 看 APK 版本（别只看字节数：1.1.3/1.1.4/1.1.5 的 release 包都恰好是 16,098,025 B）
"/d/demo/toolchains/android-sdk/build-tools/34.0.0/aapt2.exe" dump badging build/apk/jijian-music-1.4.7-vc22-debug.apk | grep versionCode

# 验签
"/d/demo/toolchains/android-sdk/build-tools/34.0.0/apksigner.bat" verify --print-certs build/apk/jijian-music-1.4.7-vc22-debug.apk

# 重新生成搜索 bundle
cd D:\demo\lx-builder; node build.mjs

# 设备（本机已装无头模拟器；无真机）
D:\demo\02_开发工具链\android-sdk\platform-tools\adb.exe devices -l
D:\demo\02_开发工具链\android-sdk\platform-tools\adb.exe install -r <apk>
D:\demo\02_开发工具链\android-sdk\platform-tools\adb.exe shell run-as com.lxmusic.android.debug cat files/last_crash.txt
```

---

## 十四、V1.1.6 设计与架构记录

这一版没有 bug 要修，全是产品与结构决定，所以每个决定都写了理由。

### 1. 分组改成一页（`ui/screens/GroupDetailScreen.kt`）

**问题**：`LocalScreen` 里一张专辑是一行 `GroupRow`，点一下从第一首播到底。分组数据
（`MusicGroup.coverUrl` / `count` / `totalDuration`）早就齐了，没有任何页面用它。

**决定**：点分组进详情页，播整组降级成页内的「播放全部」按钮。

**关键约束**：详情页持有的是 **group key 字符串**，不是 `MusicGroup`。`recomputeLocal()`
在每次搜索输入时重建所有分组，捕获对象会在用户敲字后立刻过期。取值走
`MediaLibraryStore.groupByKey(key, kind)`，它从 `allTracks` 现算。

**退场处理**：`GroupDetailHost` 用 `var last by remember { mutableStateOf<Route.Group?>(null) }`
记住最后一页，幕布 260ms 退场期间不白屏；`resolved == null` 的关闭放在
`LaunchedEffect` 里，**不能在组合期调 `onBack()`**（会崩）。

### 2. 幕布栈（`ui/shell/Route.kt`）

`showSettings` / `showFolderManager` / `showStats` 曾在 ViewModel，`showQueue` /
`showEqualizer` / `showNowPlaying` 曾在 `LxApp`，一共六个布尔值。分组页是第一个需要
参数的页面（「打开 key 是 X 的那组」不是一个布尔值能表达的），于是整体换成
`sealed interface Route` + `RouteStack`。

**保留的约束**：`LxShell` 仍然按 `LxApp` 里写死的顺序绘制所有幕布，只问「这条路是不是开着」。
所以**层级**稳定（设置永远压在队列上面），与打开顺序无关；返回键关的是
`closeTop(order)` 命中的那块**看得见的**最上层，不是最后打开的那个。

**明确没做的事**：不上 `navigation-compose`。它虽然一直躺在 `libs.versions.toml` 里，
但零 `NavHost`——已删。真要上也会和舞台模型打架：`LxShell` 是「所有幕布同时在场、让可见的那块
升起」，`NavHost` 是「同一时刻只渲染一个 destination」，退场动画天然冲突。

### 3. 五个 store（`ui/state/`）

`MainViewModel` 887 → **508 行**，从「唯一的 ViewModel」降级为门面。拆分动机不是行数，
是它同时握着扫描、播放、音乐库、统计、更新、删文件六个不相干的关注点，并且直接去
`LxApp.instance.settingsStore` 拿依赖（不是注入的）。

| store | 职责 | 备注 |
|---|---|---|
| `MediaLibraryStore` | 扫描、缓存合并、分组、收藏、歌单、删除记账 | `groupByKey` / `trackById` 是给详情页用的查询口 |
| `PlayerUiStore` | 包住 `PlayerController`、队列、均衡器持久化 | `release()` 在 `onCleared()` 调 |
| `StatsStore` | 三档窗口 + 年度报告 | 年度单独一条 Flow，免得切档时闪 |
| `UpdateStore` | GitHub release 检查 + 24h 节流 | 构造参数 `checker: () -> UpdateChecker` 便于测试 |
| `BackupStore` | 导出/导入（`content://` uri + IO 线程） | 导完要 `libraryStore.refreshLibrary()` |

`ui/StateAliases.kt` 把状态类 `typealias` 回 `ui` 包——**七个屏幕的 import 一行都没改**。
这是刻意的：重构的验收标准是「外部零波及」。

### 4. 备份格式（`data/backup/`）

`.jjmbackup` = 一个 zip，唯一 entry 是 `backup.json`。用 zip 而不是裸 JSON，是为了以后
能加封面而不破坏格式；`BackupArchive.read` 也接受裸 JSON（用户改名/手改过也能恢复）。

**必须用指纹而不是 id**：id 是 `content://` URI（MediaStore）或 SAF document URI，
换机必变。回退身份 = 标题 + 歌手 + 专辑 + 时长，时长**按秒取整**（`(ms + 500) / 1000`）——
同一首歌两次抓轨常差几毫秒，严格比较会当成两首。

**恢复是合并**：收藏/歌单/历史/事件全部 add，不删现有数据；同名歌单复用并接在
`maxPosition` 之后。

**不导出的设置**（每条都有理由）：扫描目录（SAF 授权跨设备失效）、`pendingDeletes`
（那是另一台设备的删除意图）、`lastPlayedId`/`lastPlayedPosMs`（可能指向不存在的文件）、
`lastUpdateCheckAt`（节流无意义）。导出的是 16 项「口味」类设置。

### 5. 年度回顾（`PlayStats.annualReport`）

纯函数，和其余统计一样固定时钟与时区。**踩到一个真 bug**：原本只过滤
`playedAt >= yearStart`，于是恢复备份带进来的「未来时间戳」事件会算进今年。
已改成两端闭合 `[yearStart, yearStart(y+1))`，`AnnualReportTest.eventsFromOtherYearsAreIgnored` 钉住。

UI 上没有入口按钮：一年是一个关于用户的事实，不是需要点开的功能；没数据时不渲染。

### 6. 死代码清理

- Room **v5 → v6**：`song_list` + `list_item` + `SongListDao`（在线音源时代遗留，全仓零调用），
  `MIGRATION_5_6` 两条 `DROP TABLE IF EXISTS`。
- `MusicInfo.qualitys` + `QualityInfo`：零引用。**不需要迁移**——曲目在库里是
  `musicJson` 字符串，Gson 忽略多余键。
- `navigation-compose` 依赖、空目录 `docs/adr/`。

---

---

## 十五、V1.1.7 设计与架构记录

主题是「更新不该把一个正在听歌的人送出应用」。顺带清掉设置页那批解释性副标题。

### 1. 应用内下载安装（`net/ApkDownloader.kt` + `update/UpdateInstaller.kt`）

旧的 `UpdateDialog` 只有一条出路：`Intent.ACTION_VIEW` 打开 `browser_download_url`。
用户被扔进浏览器，能不能装回来看运气。现在整个闭环留在应用内。

**流式落盘，不进内存**。`HttpBridge` 是全量缓冲的（`raw: ByteArray`），对几 KB 的
release JSON 没问题，对 16 MB 的安装包是致命的。`ApkDownloader.download` 用 OkHttp
的 `body.byteStream()` 以 64 KB 缓冲写 `<dest>.part`，成功才 `renameTo(dest)`——
中断的下载不会留下一个「看起来能装」的半截 APK。

**先校验，再交给安装器**。GitHub 的 release asset 带 `digest: "sha256:<hex>"`，
`parseRelease` 现在把它和 `browser_download_url` 一起读进 `UpdateInfo.apkDigest` /
`apkUrl`。下载完成后 `ApkDownloader.sha256(file)` 比对，不匹配**删文件**并报错，
绝不让一个被截断或被替换的包装上去。`digestMatches` 的三条分支各有理由：
发布时没写 digest → 放行（否则那个版本永远装不上）；写了 digest 但文件读不出来
→ 判失败（那正是损坏下载的样子）；否则严格比对。

**必须选 release 包，不是 debug 包**。这条是实测踩出来的：
`releases/latest` 的资产顺序里 debug 包在前
（`jijian-music-1.1.6-vc11-debug.apk` 23,038,164 B 在 `jijian-music-1.1.6-vc11.apk`
15,882,842 B 之前），原来「取第一个 .apk」的实现会把 debug 包下下来——
签名不同，装到正式版上直接冲突。现在收集全部 `.apk` 后
`firstOrNull { !it.name.contains("debug", true) } ?: firstOrNull()`。

**FileProvider 是硬要求**。API 24 起跨应用递 `file://` 会抛
`FileUriExposedException`。`AndroidManifest.xml` 加了
`androidx.core.content.FileProvider`，authority `${applicationId}.fileprovider`，
meta-data 指 `@xml/update_paths`（`<cache-path name="updates" path="updates/" />`），
`UpdateInstaller.install` 用 `FileProvider.getUriForFile` 换 `content://` 再配
`FLAG_GRANT_READ_URI_PERMISSION`。安装包放 `cacheDir/updates/`，**不占用户可见存储**。

**「安装未知应用」要能被重试**。API 26 起非商店安装需要用户逐应用授权。
`InstallOutcome` 三态（`LAUNCHED` / `NEEDS_PERMISSION` / `FAILED`）里，
`NEEDS_PERMISSION` 会跳 `Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`。授权回来后
`install.seq` 没变，驱动安装的 `LaunchedEffect(install.seq)` 不会自己再跑，
对话框原本会退回「下载并安装」——重下 16 MB 只为再撞一次权限墙。现在
`InstallStatus.READY` 单独给一个「去安装」按钮，直接重放本地文件。

**取消要真的取消**。`cancelInstall()` 早期只重置 StateFlow，协程还在后台把 16 MB
流完，然后覆盖状态为 READY，反手弹出「安装」。现在持有 `installJob`，
取消时 `job.cancel()` 并清空缓存目录；`downloadUpdate` 里 `if (!isActive) return@launch`
拦住被取消协程的收尾写入。

### 2. 删除解释性副标题（`ui/screens/SettingsScreen.kt`）

设置页每条开关下面的那行说明（「同时决定首页「接着听」有没有内容」、
「只影响「播放统计」页，与「最近播放」无关」等）全部删除。
`SwitchRow` / `InfoRow` 的 `subtitle` 改成带默认值的可选参数，调用点不传即不渲染。
`TrackActionsSheet` 的两条同风格说明一并删。

**留下的**：`"已授权 N 个文件夹 · 已手动添加 N 首"`（是数据不是解释）、
`updateSubtitle` 的非 IDLE 状态（是结果不是解释）。

---

## 十六、V1.1.8 设计与架构记录

主题是「安装包瘦身」和「下载被打断要能续」。两条都带实测数据，没有一条是猜的。

### 1. R8：15.15 MB → 3.38 MB（-77.7%）

**先量再改**。`_size.py` 按 dex / res / assets / lib / META-INF 分组统计压缩后体积，
V1.1.7 的 release 包构成是：

| 分组 | 压缩后 | 占比 |
|---|---:|---:|
| dex | 13.66 MB（3 个 dex，raw 合计 48.37 MB） | **90.2%** |
| resources.arsc | 832.41 KB | 5.4% |
| res | 395.06 KB | 2.5% |

九成体积在 dex 里，而 `app/build.gradle` 的 release 块同时写着
`minifyEnabled false` 且没开 `shrinkResources`——每一份打进包的库都是完整的，
包括大量从未被任何代码路径调用的部分。这就是全部原因。

打开 R8（`minifyEnabled true` + `shrinkResources true`）后得到 **3,543,104 B**：

| 分组 | 压缩后 | 占比 |
|---|---:|---:|
| dex | 2.45 MB（单文件，raw 5.36 MB） | 72.6% |
| resources.arsc | 386.58 KB | 11.2% |
| res | 287.62 KB（607 条目，原 877） | 8.3% |

**`-dontobfuscate` 是刻意留的**，理由写在 `proguard-rules.pro` 开头：这次的体积来自
死代码，删掉就够；改名则要同时满足四套反射约定——Gson（JSON 字段名即协议）、
Room（运行时按名字找生成的 `*_Impl`）、media3（`SessionToken` 里塞 `ComponentName`）、
Compose（`$default` 合成方法）——任何一处出错都是用户手机上的崩溃，而不是一条失败的
测试。改名留到能做真机回归的版本再单开。

显式 keep 的清单：`data.model.**` 与 `BackupFile`/`BackupPlaylist`/`BackupHistoryRow`/
`BackupEventRow`/`EqProfile`（字段名就是 `.jjmbackup` 格式，改名会让旧备份静默恢复成
null）、`AppDatabase`/`AppDatabase_Impl`、`PlaybackService`、`UpdateInstaller`、
`LxApp`、`MainActivity`、`FileProvider`、media3 的 session/exoplayer 入口点。

**怎么在没有设备的情况下确信没删错**：`_r8check.py` 用
`build-tools/34.0.0/dexdump.exe -f` 把打进包的 dex 里的类描述符全列出来
（5523 个），断言 11 个「只能靠反射或系统绑定找到」的类都在；然后用 `-d` 查
dex 字符串池里的 `albumName` / `localDurationMs` / `dateAdded` / `songmid` /
`apkDigest` / `playedAt` / `trackIds`，证明 Gson 协议没被改名。
`aapt2 dump badging` 再确认 `versionCode='13' versionName='1.1.8'`、
`launchable-activity` 仍是 `com.lxmusic.android.MainActivity`、
应用名仍是「极简music」（图标资源被 shrink 改名成 `res/BW.xml` 是正常的）。

### 2. 断点续传（`net/ApkDownloader.kt`）

`download` 现在从 `.part` 文件已有的长度起步，发 `Range: bytes=$carried-`。

**只有 `206` 且磁盘上有字节才追加**（`canAppend(statusCode, carried)`）。三条否决：

- `200` —— CDN 忽略了 Range，正在整包重发。追加等于把整个 APK 拼两遍。
- `416` —— `.part` 比发布资产还长，是旧版本留下的垃圾。
- `carried == 0` —— 没什么可续的，走普通下载。

否决时 `part.delete()` 再从头取。宁可多下一次，也不拼一个只在安装时才失败、
之前任何一步都不报错的坏包。四条对应单测
（`onlyPartialContentIsAppendedToWhatIsAlreadyOnDisk` /
`aFullBodyRethrownAtAResumeRequestRestartsInstead` /
`anOutOfRangeOffsetRestarts` /
`nothingToResumeMeansNoAppendEvenOnAPartialResponse`）把这四种情况钉死。

`total = carried + declared`——`206` 的 `Content-Length` 只报剩余长度，
直接用会让进度条从 0 开始跳。字节数对上才 `renameTo(dest)`。

**取消不该丢掉进度**。`UpdateStore.purgeApkDir(keepName, keepPart)` 只删「别的版本」
的文件，`cancelInstall()` 不再清空整个缓存目录——本资产的 `.part` 留着当续传点。
取消一次花掉几 MB 缓存，比下次从零下 15 MB 划算。

---

---

## 十七、V1.2.0 设计与架构记录

这一版没有新功能，只有四件「已经算过的东西又算了一遍」的修正。四个改动都遵循
同一条判据：**昂贵的部分必须能证明自己变了**，否则就用上次的结果。

### 1. 增量扫描（`local/IncrementalScan.kt`）

扫描真正的开销不在遍历目录树，而在 `MediaMetadataRetriever` 逐文件打开读标签与
时长。SAF 的列表查询本来就会给出 `lastModified` 与 `length`，于是把它们做成
`Stamp(lastModified, size)` 与缓存比对，命中就直接复用上一轮的 `MusicInfo`。

两条判定都是**与**，不是**或**：

- **两个数都必须匹配**。同一秒内原地重写只改长度；只信 mtime 会把旧时长留在库里。
- **两个数都必须存在**。provider 报 `0` 是「这条记录没有这个信息」，不是「没变化」。
  把 0 当有效值会让「读不到」等价于「没改」，下一轮永远复用一条不完整的记录。

`ScanReport(total, reused, read, elapsedMs)` 把结果落到 UI：文件夹管理页在扫完后
显示 `共 N 首 · 用时 X · 跳过 M 首未改动`。**这条汇总本身就是这次改动的验收标准**
——没有它，「跳过」只是一个不可观测的内部优化。

### 2. 搜索索引（`local/LocalIndex.kt`）

`LocalLibrary.search` 每次调用都对全库每首曲目的 name/singer/albumName 各做一次
`lowercase()`，而 UI 在**每次按键**都调用它。三千首的库、每个字符三万个字符串
分配，全部是在重算两次按键之间根本没变的文本。

`LocalIndex.of(tracks, order)` 在曲目列表变化时做一次小写化，并把排序也一并做掉
（排序只在用户切换排序方式时变，查询每个字符都在变）。`MediaLibraryStore.indexFor`
把索引挂在**它被构造时的两个值**上做缓存：列表用引用相等（store 是整体替换
`copy(allTracks = ...)`，新对象必是新内容）、排序枚举用值相等。这样「索引过期」
不是「不太可能」，而是**不可能**——缓存键就是它的全部输入。

`LocalIndexTest` 的关键用例是 `theIndexAgreesWithThePlainFilterForEveryQuery`：
拿一批查询逐个比对索引答案与旧 `LocalLibrary.search` 的答案。这里真正的风险不是
性能没提上去，而是**行为悄悄变了**，所以测试断言的是等价性而不是某几个字符串。

### 3. 封面分级（`ui/components/CoverTier.kt`）

内嵌封面常见 1000×1000。Coil 按 ARGB_8888 解码就是 4 MB；四十行列表各解码一张，
为了填 52 dp 的方块，churn 掉 160 MB。

Coil 本身会从布局约束推断目标尺寸，所以显眼的情况本来就没问题。它不知道的是
**策略**：播放页那张铺满全屏、压在极光上、只有 12% alpha 的底衬，同样不需要原图
分辨率。于是三档上限被显式命名（THUMB 160 / TILE 320 / LARGE 1080），`Cover()`
按 `ceil(sizeDp × density)` 夹进档位。

两个细节是刻意写反直觉的：

- **向上取整**。向下取整会让 Coil 在最后一行把解码好的位图再放大回去，看起来是糊的。
- **零尺寸绝不传下去**。对 Coil 来说 `size(0)` 表示「不定尺寸」，也就是按原图解码
  ——正好是这套策略要消除的那件事。所以 `pixels()` 的下界是 1。

### 4. 队列 2.0（`player/QueueDrag.kt` + `ui/screens/QueueScreen.kt`）

头部文案是 `3/12 · 剩 34 分`。`QueueSummary` 从队列 + **当前曲内的播放进度**算出
这个数，所以它在一首歌里平滑下降，而不是按整首跳。

有一个刻意的取舍：**不把「剩 N 首」和时长并排**。首数不含当前曲，时长含当前曲的
剩余部分，并排就会出现「剩 1 首 · 5 分」这种自相矛盾的读法。首数由下方的行列表
本身回答，头部只留「还要多久」。

拖动排序：长按拾起，落点由 `QueueDrag.resolve` 按累计位移算，被跨过的行反向让位
一格（`displacementFor`），松手时调一次 `queueMove`。两个坑：

- `roundToInt` 用的是 `Math.round`，对 `-0.5` 取 0、对 `+0.5` 取 1。直接套会让
  **向上拖比向下拖迟钝半行**，所以先取绝对值再补符号。
- 浮动偏移保留**全程**距离，即使落点已经被夹在队列边界——行必须始终跟在手指下，
  否则拖到顶还会继续飘，看起来像松手了。

### 5. 这一版没有做的事

- 没有把 `LocalIndex` 做成可观察对象。它是 store 整体替换的一个值，这样「什么时候
  会过期」只有一个答案。
- 没有给拖动手势加自动滚动。队列通常是几十首，屏幕外拖动需要边缘滚动区，那是
  另一个量级的手势逻辑，留到有人真的抱怨再说。
- 没有打开 R8 的改名。理由同 V1.1.8：收益在删代码，不在改名，而改名要同时说服
  Gson / Room / media3 / Compose 四套反射约定。

---

## 十八、V1.3.0 设计与架构记录

V1.1.6 给了曲库专辑页，V1.2 让浏览变便宜。两件都做完了，曲库大起来仍然没人回答
那个真正的问题：**今晚听什么**。一千二百首按标题排序的列表不是答案，而 App 还在
用户滚过去的时候把这张表重算四遍。

### 1. 四张没人维护的架子（`local/SmartCollections.kt`）

`SmartCollections.build(tracks, played, now)` 从曲库 + 播放记录现算四张架子：
最近添加 / 最常播放 / 从未播放 / 很久没听，各三十首。它们不是歌单：不落盘、不能
改名、不能编辑。三个判断值得写下来：

- **`dateAdded = 0` 是「不知道」，不是 1970。** 从没记录过入库时间的版本升级上来时，
  把 0 当成「很新」会让整库涌进「最近添加」——一张装着所有东西的架子不是架子。
- **「从未播放」在有第一条播放记录之前保持为空。** 全新安装时每首都没播过，这架
  就等于整库，而那正是用户已经看得见的东西。
- **「很久没听」要求播过**（`lastPlayedAt in 1 until now - 90d`）。没播过不是被遗忘，
  是还没轮到；两架只在定义允许的地方重叠。

顺序**固定**而非「智能排序」：每次进来都在挪的架子，等于每次都要重新找一遍。空的
架子自己消失，所以没有播放记录的库只显示一行而不是四行。

`MediaLibraryStore.rebuildCollections` 在读 Room 缓存命中时就先算一次（架子必须和
列表同一帧出现，否则首页先空一格再补），扫描结束、刷新库、删歌之后再各算一次。
`PlayStat` 由 `repo.playRecords(0L, now)` 一次聚合得出，不是在 UI 线程里遍历。

### 2. 一次搜索，五种答案（`local/UnifiedSearch.kt`）

首页工具栏那个输入框只过滤它下面的列表——输入歌单名什么也搜不到，输入专辑名能搜到
里面的歌却搜不到这张专辑。`UnifiedSearch.run(query, tracks, favorites, playlists)`
跑一次查询并拆成五段：歌曲 / 收藏 / 专辑 / 歌手 / 歌单。

两条规则让它保持诚实：

- **匹配只在一处实现。** 命中的判定交给 `LocalIndex`（V1.2 那份预先小写的索引），
  搜索页拿到的是**已经过滤好的**曲目。在 `UnifiedSearch` 里再匹配一遍就是第二份
  「什么算命中」的实现，迟早漂移。
- **专辑与歌手从「匹配后的曲目」分组。** 用户问的是「我哪些专辑里有它」，不是
  「把我的专辑列出来」。所以 `LocalLibrary.byAlbum(tracks)` 收的是过滤结果。

收藏段还会剔掉已经出现在歌曲段里的曲目——同一首歌在一页上出现两次，读起来像 bug。
专辑/歌手/歌单各封顶六条，页面上限是为了「一眼能扫完」，不是分页。

### 3. 架子只播自己的三十首

`playCollection(collection)` 把队列设为这一架的歌，不续到全库。点「最常播放」就是要
那三十首；让队列一首之后继续滑进曲库剩下的部分，架子在一首歌之后就失去意义了。
同理，`playSearchFavorites` 播的是搜索命中的收藏子集，不是全部收藏——用户带着关键词
点下去，队列里冒出无关的歌等于关键词失效。

### 4. 入口放哪

`LocalScreen` 顶栏在输入框与排序之间加了一个全局搜索按钮，架子的横滑条放在「最近在听」
之后、「本地音乐」分段标题之前，并且只在**查询为空 + 分组为歌曲**时出现：在专辑或歌手
分组里，同一个位置已经被那张列表占用了，再插一条横滑带会让两种浏览节奏打架。

搜索页的幕布插在 Group 之后、Queue 之前——从搜索结果点进专辑，专辑页要盖在搜索页
上面，而两者都在队列之下。

### 5. 这一版没有做的事

- 没有把搜索做成跨进程/跨库（在线音源 V1.1.0 已删，没有第二份曲库可搜）。
- 没有给架子加「更多」入口。三十首是刻意的小尺寸；想看全部就进对应分组。
- 没有动首页顶栏。搜索与架子入口目前都长在「本地音乐」页内部，把顶栏变成
  全局搜索是 V1.4 之后的事。

## 十九、V1.4.0 设计与架构记录

### 1. 歌词行第一次真的是个控件

`LyricView` 的 `onSeekTo: ((Long) -> Unit)? = null` 从 V5 就存在，
`NowPlayingScreen` 的两个调用点（唱片版 `:391`、歌词版 `:431`）都老老实实传了
`onSeek`，而**函数体从头到尾没有调用过它**。也就是说：定时歌词在触摸屏上唯一的
用处——点一句跳过去——被静默地废了八个版本，没有任何测试或崩溃暴露它，因为
「点了没反应」看起来就像「这里本来就不能点」。

修法在 `LyricView.kt`：把 `itemsIndexed` 里的 `Column` 整行变成触摸目标。

```kotlin
private fun Modifier.lyricSeekTarget(label: String, atMs: Long, onSeek: (Long) -> Unit): Modifier {
    val stamp = "%d:%02d".format(atMs / 60_000, (atMs / 1000) % 60)
    return this.semantics(mergeDescendants = true) { contentDescription = "跳到这里播放 $stamp，$label" }
        .clickable(role = Role.Button) { onSeek(atMs) }
}
```

三个细节：

- **触摸目标是行，不是两段文字。** 原文行与翻译行共享同一个时间戳，点哪一段都应
  跳到同一处；分开成两个可点区域会出现「点翻译跳到了别的地方」。
- **`LyricMetrics.lineTouchPadding = 6.dp`** 是给短行补身高的。一行只有四五个字时
  文本高度约 22–28dp，光靠文字撑不到 48dp 下限。
- **只有传了回调才可点**（`.then(if (onSeekTo != null) ... else Modifier)`）。
  旧的只读调用点（例如未来做歌词预览）行为完全不变，不会莫名多出一堆可点区域。

### 2. 播放页菜单里的歌词调节

字号与偏移原本只有设置页能改。但真正想调歌词的那一刻，是**正在听某首歌、觉得
某一行早了半拍**的时候；走进设置页、翻两个滑块、再退回来，那一句已经过去了。

`ui/screens/LyricTuning.kt` 是个纯 Kotlin object（没有 Compose 依赖，因此可测），
边界值刻意与设置页那两个 `SliderRow` **逐位相同**：`MIN_SCALE = 0.7f` /
`MAX_SCALE = 1.6f` / `MIN_OFFSET_MS = -500` / `MAX_OFFSET_MS = 500`。不一致的后
果很具体：在播放页按到 1.65×，回到设置页滑块会显示一个它自己永远拖不到的位置。

两个反直觉点：

- **字号吸附到两位小数**（`(raw * 100f).roundToInt() / 100f`）。反复 ±5% 之后浮点
  会漂成 1.0499999，而设置页用 `"%.2f×"` 打印，同一份设置会在两处读出不同的数字。
- **偏移文本必须带正负号**：`120 ms` 单独看不出是「歌词晚一点」还是「早一点」，
  所以 `offsetText` 对正数强制加 `+`。

按钮是 48dp 触摸目标（菜单项本来就这么高），到边界时图标变淡并带 `disabled()`
语义 —— 按三次就知道到底了，比按了没反应更好。

### 3. 平板右栏不再固定 45%

V0.9 起宽屏就是「左 55% 列表 / 右 45% 播放页」，而专辑页是**全屏幕布**盖在上面。
在平板上打开一张专辑，列表整片消失，退出来还得重新找位置。

`ui/shell/WidePane.kt` 把「右栏归谁」变成一个可测的纯函数：

```kotlin
fun resolve(wide: Boolean, nowPlayingOpen: Boolean, hasTrack: Boolean, groupOpen: Boolean): Pane =
    when {
        !wide -> Pane.NONE
        nowPlayingOpen && hasTrack -> Pane.NOW_PLAYING
        groupOpen -> Pane.GROUP
        else -> Pane.NONE
    }
```

- **播放页压过专辑页**，因为播放页是用户刚点出来的东西；反过来会出现「点了播放，
  右栏纹丝不动」。
- **`hasTrack = false` 时不占半屏**：空唱片 + 两个禁用按钮横在半个屏幕上，不如把
  宽度还给列表。
- `share(GROUP) = 0.5f`，其余 `0.45f` —— 专辑页是曲目表，多 5% 就是多一行。

`LxApp.kt` 有一处必须记住的配套改动：**Group 幕布只在窄屏注册**
（`if (!wide) { add(CurtainSpec(...)) }`）。宽屏上同一个 `Route.Group` 会同时存在
于「右栏」和「全屏幕布」两处，幕布的返回键会盖住右栏的，退一次只关掉看不见的那层。
另外 `if (pane == Pane.GROUP) { BackHandler { viewModel.closeGroup() } }` 放在
`LxShell(...)` 调用**之前**，让幕布里的 `BackHandler`（后注册、优先级更高）仍然先接管。

### 4. 一行一句话：`TrackDescription` + `Modifier.describedAs`

原来一行曲目是「封面（`contentDescription = null`）+ 标题 Text + 副标题 Text +
时长 Text」四个节点。读屏要走四步，中间还会静默跳过封面，而且**从不说明「这行能播」**。

`ui/components/TrackDescription.kt` 是纯函数 object（无 Compose 依赖，因此可测），
按动作语义分方法：

| 方法 | 输出 | 用在哪 |
|---|---|---|
| `row` | `播放 晴天，周杰伦，叶惠美，时长 4:29` | `TrackRow`、`RecentRail`、队列行 |
| `open` | `打开 叶惠美，12 首` | 分组行、歌单、搜索结果分组 |
| `play` | `播放 为你挑的，30 首` | 合集架、搜索结果歌单 |
| `resume` | `继续播放 晴天，周杰伦` | 「上次播放」卡 |
| `miniPlayer` | `打开播放页 晴天，周杰伦` | 迷你播放器 |
| `group` | `叶惠美，共 12 首，时长 48 分` | 专辑页头信息块 |

**动词是最重要的部分**：分组行说「打开」绝不能说「播放」（它进的是页面不是播放），
迷你播放器必须说「打开播放页」（点它是开播放页，不是开始播放）。读屏用户唯一能据以
行动的信息就是那个动词。

`Modifier.describedAs(label)`（在 `Common.kt`）为 `semantics(mergeDescendants = true)
{ contentDescription = label; role = Role.Button }`。合并之后封面不再被单独念出来，
`Cover()` 的 `contentDescription` 默认仍是 `null`，需要时可通过新增的第 4 参数传。

> **坑**：`role = Role.Button` 写在 lambda 里时，除了 `Role` 还必须
> `import androidx.compose.ui.semantics.role`，否则报 `Unresolved reference 'role'`。

### 5. 玻璃按钮的触摸目标一直是 38dp（真实缺陷）

`NeonGlassAction` 的注释写着「38dp 视觉 / 42dp 触摸」，代码是：

```kotlin
Modifier.padding(horizontal = 2.dp).size(38.dp).neonChip(...).clickable(...)
```

`padding` 写在 `size` 之前、`clickable` 写在 `padding` 之后 —— Modifier 从左到右
包裹，实际可点区域就是那 38dp，注释里说的 42dp 从来没成立过。改成两层 Box：

```kotlin
Box(Modifier.size(48.dp).clickable(role = Role.Button) { ... }, contentAlignment = Alignment.Center) {
    Box(Modifier.size(38.dp).neonChip(shape = RoundedCornerShape(50))) { Icon(...) }
}
```

顶栏只有 56dp 高，多出来的 10dp 是靠把行内间距还回去换的。同类改动：
`GroupDetailScreen` 的「播放全部」从 44dp 提到 48dp（WCAG 2.5.5 下限）。

### 6. 这一版没有做的事

- **不上 `navigation-compose`**。`gradle/libs.versions.toml` 里那条 2.8.0 声明在
  V1.1.6 已删除。`NavHost` 的「一次只有一个 destination 在组合里」与 `LxShell`
  的「所有页面都在舞台上、只把可见的那块 `AnimatedVisibility` 拉起」是两种模型，
  换过去等于重写转场与返回栈。
- **不做 Android Auto / 车载浏览**（用户 m20089 明确排除到 V1.4 之外）。
- **没做真机验证**。本机模拟器（`emulator -accel-check` 报 WHPX 可用）每次都在
  `Windows Hypervisor Platform accelerator is operational` 之后以 exit code 21
  静默退出，`adb devices` 始终为空，`-gpu swiftshader_indirect` / `-accel off` /
  `-wipe-data` / 新建 AVD 全部复现。V1.1.8 起实机验证一律退化为
  「JVM 单测 + dex/APK 静态核验」。

---

## 二十、V1.4.1 设计与架构记录

这一版没有新功能。它做的是把 V1.3 / V1.4 铺在首页上的东西收回去：用户的原话是
「为你挑的，这段功能的代码先屏蔽掉。然后最近在听，旁边，添加一个 x」。

### 1. 「为你挑的」收起用的是开关，不是删除

四张自动合集（最近添加 / 最常播放 / 从未播放 / 很久没听）不再铺在首页。但一行代码
都没删：

- `local/SmartCollections.kt` —— 纯计算 object，`SmartCollectionsTest` 17 项照跑
- `ui/screens/LocalScreen.kt` 的 `CollectionRail` —— 组件还在
- `MediaLibraryStore.rebuildCollections()` —— 仍然填充 `_collections`，只是没人读

控制点是文件顶部一个常量：

```kotlin
private const val SHOW_SMART_COLLECTIONS = false
```

**为什么是开关而不是 `git revert`**：这几张架子的判断本身是对的（`dateAdded = 0`
不是 1970、一次都没播过时「从未播放」整架为空、次数相同比最近），错的只是「默认
占首页」。删掉会让那 17 项测试一起消失，而那 17 项测的是真正的产品判断。留一个
常量，相当于把决策写进代码里，而不是写进 commit message。

`MediaLibraryStore` 里那半截每次扫描都跑的 `playRecords(0L, now)` 聚合照旧执行——
多花一次全表读。如果要省掉，得把 `rebuildCollections` 的调用点也包进同一个常量判断，
但那样一来开关就变成两处，改回 `true` 时容易漏掉一处。**宁可多算一次也不留半开
状态。**

### 2. 「最近在听」的关闭是偏好，不是 UI 状态

那一排封面标题右侧加了 X。和下面的架子不同，它不是用户自己排的东西，所以关掉之后
必须能在别处重新打开——落到 `AppSettings.showRecentRail`（DataStore 第 31 个键），
设置页「音乐库」分区里有一个「首页显示「最近在听」」。

默认 `true`。默认 `false` 等于新装用户永远看不到这条回头路，而曲库刚扫进来的那几天
正是它最有用的时候。

触摸目标是 48dp 的 `Box`，`Icons.Default.Clear` 18dp 居中放在里面；`contentDescription`
写在 Box 的 `semantics` 上（"关闭「最近在听」"），图标本身是 `null`——否则读屏会
先念一遍「Clear 图标」再念一遍描述。

> **坑（第二次踩）**：`semantics { role = Role.Button }` 里那个 `role` 需要单独
> `import androidx.compose.ui.semantics.role`，只 import `Role` 会报
> `Unresolved reference 'role'`。V1.4 在 `Common.kt` 踩过一次，V1.4.1 在 `LocalScreen.kt`
> 又踩了一次。

### 3. 首页现在的顺序

```
[上次播放卡]            只剩一首曲目时才显示（restore 指针有效）
[最近在听]              可关，横向 12 张
———— (空行)
[本地音乐]  ← SectionHeader
[GroupChip 歌曲/专辑/歌手] [排序] [文件夹] [刷新] [全局搜索]
[搜索框]
[曲目 / 分组列表]
```

「为你挑的」原本在「最近在听」与「本地音乐」之间。它撤掉之后，从顶栏到用户自己的
曲库只剩一个卡片和一排封面——这正是这一版要的结果。

### 4. 这一版没有做的事

- **没有删 `SmartCollections` 的测试**（理由见第 1 节）。
- **没有在设置页给「为你挑的」也配一个开关**。合集架是开发者视角的功能，「最近在听」
  是用户自己产生的数据；前者收回即可，后者要给回退路径。
- **`README.md` 只保留最新版本说明**（用户要求「只保留最新版本说明，然后简写内容」）。
  从 1129 行缩到约 230 行，V1.4.0 及以前的全部版本章节删除——历史留在
  `docs/release-notes/` 与本报告的第十四～十九节里。
- **没做真机验证**（同上，本机模拟器不可用）。

*本报告由当前代码库实况生成，所有文件体积、行号、测试数均为实测值。*

---

## 二十一、V1.4.2 设计与架构记录

### 1. 用户的要求与那个歧义

用户的原话是「迭代1.42版本，用新算法 `D:\demo\随机播放算法设计.md`」。问题是那份
文档已经被改写成**描述现行实现**（V1.0.1 的抽签语义），它不再是一份待实现的方案；
而工作区里还躺着一份未提交的改动，实现了「最近 N 首不重复」的滑动窗口
（`MAX_WINDOW = 32` / `windowFor` / `pickFresh` / `RandomShuffleState.recent`）。

所以先问了两轮：

1. 「新算法」指什么？选项：恢复原版洗牌袋 / 每次重抽 + 近 N 首窗口 / 可切换策略。
   用户自定义答：**要求可重复**。
2. 「可重复」是哪种？选项：固定种子可复现 / 允许同一首歌重复播放 / 两者都要。
   用户选：**允许同一首歌重复播放（不做去重窗口）**。

于是那份滑动窗口实现被 `git checkout --` 回滚了。教训写在下面第 4 节。

### 2. 修掉的真实崩溃

旧实现：

```kotlin
val candidates = IntArray(size - exclude.count { it in 0 until size })
```

一旦 `exclude` 里有重复下标，这个数量会算错。`previousIndex` 传的是
`intArrayOf(current, lastTrack)`——队列正被编辑时（拖动排序、删除后 `lastTrack`
还指着旧位置）两者会相等，于是两首队列上 `2 - 2 = 0`，更旧的队列上直接是负数。
`IntArray(负数)` 抛 `NegativeArraySizeException`，抛在 ExoPlayer 的播放线程上。

新实现只剩一个判定：

```kotlin
private fun banned(value: Int, exclude: IntArray, size: Int): Boolean {
    if (value !in 0 until size) return false
    for (v in exclude) if (v == value) return true
    return false
}
```

它同时修掉第二个小缺陷：旧代码用两个不同的谓词过滤（`count { it in 0 until size }`
算数量、`i !in exclude` 建内容），越界下标在前者被排除、在后者仍生效，两套逻辑
各算各的。现在「越界不算禁用、重复天然无害」是一条规则。

### 3. 为什么明确不做「近期去重窗口」

那条规则看起来是改进：同一首歌不会连着出现。实际代价是「随机」变成「轮转的近亲」
——队列里 N 首、窗口 M 首，任意时刻下一首只可能在剩下 N-1-M 首里选，
`M=32` 时小队列上直接等于顺序播放。

更麻烦的是它不可见：界面上没有任何地方写「最近 30 首不会重复」，用户只能自己猜
为什么某首歌点不出来。用户这次把要求说得很清楚——要允许重复，那就把「不设窗口」
也写进 KDoc 与测试，而不是留一条没人知道存在的隐式约束。

`RandomShuffleTest.anIndexThatJustPlayedIsStillReachable` 就是钉这条的：
4 首队列、只排除当前曲，2000 次抽签里另外三首都必须出现。

### 4. 教训：不要相信工作区里来路不明的新代码

开工时 `git status` 显示 `RandomShuffle.kt` 有 125 行未提交改动、修改时间比本会话
开始还晚。那份改动**恰好是用户刚刚否决的方案**。如果直接在其之上继续做，交付的
就是被明确拒绝的东西，而且 diff 会把它混进「本次改动」里，事后无法解释。

规矩：动手前先 `git status` + 看 `git diff`；不属于本次需求的未提交改动先回滚，
再开始自己的改动。

### 5. 这一版没有做的事

- **没动 `RandomShuffleState` 的双缓存**。抽签语义本身没问题，问题只在候选数组的
  尺寸计算。
- **没改 `D:\demo\随机播放算法设计.md` 的章节结构**，只在 §4 补了 4.1（去重与越界
  过滤）、4.2（明确不做窗口），§12 表格数字更新，§13 增一条「近期去重窗口」，
  §14 落地顺序加第 7 条。
- **没做真机验证**（本机模拟器在 WHPX 初始化后静默退出，见第二节）。

---

## 二十二、V1.4.3 设计与架构记录

### 1. 「开关随机后下一首必然是同一首」不是一个 bug，是两个

两个缺陷单独都不会造成这个现象，叠在一起才致命。

**第一个：单槽位缓存。** `RandomShuffleState` 原本只有 `nextPick` / `previousPick`
两个整数。但一次换歌里 media3 会问好几遍：通知栏要播放中曲目的下一首、
`MediaPeriodQueue` 要预览后面几首、自动续播再问一次。这些问题的 `current` 各不
相同，而缓存只有一个位置——后一个答案覆盖前一个，同一时刻两个屏幕上可能拿到
互相矛盾的下标。

**第二个：副本拿到的是快照。** `copy()` 原本把 `nextPick` / `previousPick` /
`lastTrack` 逐个抄给克隆体。问题在于 `MediaSourceList.setShuffleOrder` 会
`cloneAndClear().cloneAndInsert(...)` 修长度不匹配，而**装进 ExoPlayer、替通知栏
作答的正是那份克隆体**；`onTrackStarted` 只在 `PlayerController` 里对父实例调用
（`onMediaItemTransition` 与 `playIndex` 两条路径），克隆体永远收不到。于是父实例
每次换歌都清账重抽，克隆体却一直端着换歌前抽的那个下标——用户按「下一首」，走的
是通知栏/媒体会话那条线，拿到的就是同一个答案。

**修法**：缓存换成按 `current` 分槽的账本，副本共享同一个账本对象：

```kotlin
@Synchronized fun copy(): RandomShuffleState = RandomShuffleState().also { clone ->
    clone.memo = memo          // 共享，不是快照
    clone.generation = generation
}
```

`ShuffleMemo` 里两本 `HashMap<Int, Int>`（next / previous）按 `current` 建槽，槽满
`MAX_SLOTS = 64` 时整本清空（一个队列的长度远小于 64，清空只在异常输入下发生）。
`started()` 同时清两本并记 `lastTrack`，`clear()` 全清。`memoised` 的语义随之从
「有没有抽定」变成「记账条数」，测试断言跟着改。

### 2. 上一版把「快照」写成了测试契约

V1.4.2 的 `RandomShuffleCloneTest` 里有一条 `aSnapshotDoesNotLeakBackIntoTheParent`，
断言父实例的备忘「不被克隆体污染」。这条用例**把这次要修的 bug 当成了正确行为**。
这次改写为 `aCopySharesTheMemoSoBothSidesGoStaleTogether`：父与克隆先给同一答案，
`onTrackStarted` 之后两边一起变盲，克隆体重抽出一个不同的下标。

教训：**契约变了，测试必须跟着改，而不是改代码去迁就测试**。反过来说，如果一条
测试的存在理由是「描述现状」，那它保护的是 bug 而不是行为。

### 3. 切样式的三处卡顿，全是「在 composition 里读动画值」

| 位置 | 原来 | 现在 |
| --- | --- | --- |
| `RotatingArtwork` 唱片 | `.rotate(rotation.value)` | `.graphicsLayer { rotationZ = ... }` |
| `RotatingArtwork` 唱臂 | `drop: Float` | `drop: () -> Float`，Canvas 内求值 |
| `LyricView` 歌词行 | 动画 `fontSize` / `lineHeight` | 动画 `scaleX/scaleY`，字号固定 |
| `NowPlayingScreen` 切台 | Crossfade 默认 300ms | `tween(200)` |

`Modifier.rotate(x)` 的 `x` 是 composition 参数，每帧变化就每帧重组宿主载体。
唱片那棵子树里有一次 30–50 圈的凹槽 `drawCircle` 循环、一张封面和主轴孔——60fps
下等于每秒重建 60 次整棵树。`graphicsLayer {}` 的 lambda 在**绘制阶段**求值，重组
一次之后只动 matrix。

唱臂的 `drop` 同理：`animateFloatAsState` 的结果用 `by` 解构就等于在 composition
里读值，300ms 内每帧重组。改成传 `() -> Float` 让 Canvas 自己去取。

歌词行原来动画 `fontSize` 与 `lineHeight`——字号是**布局属性**，每帧变化意味着
每帧重新排版一行字（测量 + 换行 + 定位）。改成固定字号 + `graphicsLayer` 缩放，
只有绘制阶段变。

Crossfade 默认 300ms，期间两套舞台（各自的封面 `AsyncImage`、Canvas、动画）同时
存在且都在跑。收到 200ms 既减少重叠时长，也让切换更跟手。

### 4. 编译坑：`drop = { armDrop }` 不行

改成 lambda 参数后第一版写成 `drop = { armDrop }`，编译报

```
Argument type mismatch: actual type is 'androidx.compose.runtime.State<kotlin.Float>',
but 'kotlin.Float' was expected   (RotatingArtwork.kt:195)
```

因为 `animateFloatAsState` 返回 `State<Float>`，去掉 `by` 之后必须显式 `.value`：
`drop = { armDrop.value }`。

### 5. 这一版没有做的事

- **仍然不做近期去重窗口**（V1.4.2 的显式决定保持不变）。`exclude` 只装当前曲，
  `previousIndex` 额外避开刚播完的那首。
- **没上 `animatable` 的帧同步优化**（`withFrameNanos` 手写旋转）。`Animatable` +
  `graphicsLayer` 已经足够，再往下走会把「暂停时定格」的逻辑弄复杂。
- **没改 `LyricView` 的自动滚动**（`animateScrollToItem`）。它每次换行触发一次，
  频率低，不是卡顿来源。
- **没做真机验证**：本机模拟器在 WHPX 初始化后静默退出（见第二节），这一版的
  两个修复都是靠单元测试 + 代码路径推导确认的。

---

## 二十三、V1.4.5 设计与架构记录

这一版是四条产品决定 + 两个真 bug，其中一条产品决定顺手改掉了发布形态。

### 1. 只发 debug 包（发布形态变更）

「应用内下载并安装」在 V1.1.7 上线时就有个隐含前提：下载的包必须和用户手里那份
同一个签名。当时的选择是**下载 release 包**（`parseRelease` 优先挑不带 `debug` 的
资产）。V1.4.5 反转这个选择——发版只发 debug 包，理由有三条：

1. 自动安装是主更新路径，它必须成功，而 debug 包能覆盖 debug 包；
2. 只维护一种签名，`UpdateChecker` 不必再赌资产列表的顺序；
3. debug 包带行号，崩溃栈可读。

代价写在 README 顶部：从正式包（`com.lxmusic.android`）升级到 debug 包
（`com.lxmusic.android.debug`）是**两个不同的 applicationId**，需要先卸载一次正式包。
之后的所有版本都走 debug。

`UpdateChecker.parseRelease` 的选包条件从
`firstOrNull { !it.name.contains("debug") }` 改成
`firstOrNull { it.name.contains("debug") }`，并保留 `?: candidates.firstOrNull()`
的退路——1.4.5 之前的 release 只有正式包，那些版本仍要能被识别为「有更新」，
不能因为选不到 debug 就整条链路失效。

### 2. 删掉「启动时自动检查更新」开关与 24h 节流

开关和节流叠在一起，实际效果是「用户以为在查，其实没查」：开关默认开着，但
`lastUpdateCheckAt` 让第二次启动起就静默跳过。两个都想省网络请求的机制合起来
产出一个只在第一次启动有效的功能。

现在 `UpdateStore.checkOnStartup()` 只保留「状态不是 IDLE 就别重入」这一个守卫，
每次进应用都查。静默语义不变：失败与「已是最新」都退回 IDLE，`UpdateCheckState`
不往设置页塞用户没要的状态。

连带删除：`AppSettings.autoCheckUpdates`、`KEY_AUTO_CHECK_UPDATES`、
`setAutoCheckUpdates`、`MainViewModel.setAutoCheckUpdates`、
`SettingsRoundTripTest.autoCheckUpdatesDefaultsOnAndSurvivesAWrite`。
`lastUpdateCheckAt` 字段留着（备份文件里有它，删了旧备份会读不回）。

### 3. 「随机播放第一次启动直接卡死」

三个东西叠出来的，缺一个都不至于死：

| 环节 | 做了什么 | 为什么致命 |
| --- | --- | --- |
| `QueueMirror.resync` | 每次队列变更都写 `t.shuffleModeEnabled = shuffleFlag()` | 写这个属性会走 `updatePlaybackInfo` → `onTimelineChanged` → 再装一次 order |
| `onQueueMutated` | 同一处又调 `resetShuffle()` → `installShuffleOrder()` | 上面那条路之外，这里独立再装一次 |
| `resetShuffle` | `shuffleState.invalidate()` | generation 一变 memo 守卫失效，**必然**再装一次 |
| `installShuffleOrder` | `exo.setShuffleOrder(...)` → `createMaskingTimeline()` | 每装一次造一条新 timeline，跨进程 `MediaController` 把每轮放大成主线程停顿 |

冷启动时 `restoreLastPlayed` → `prepareRestored`（队列缩到 1 首）→ 另一协程等
首次扫描完成 → `backfillQueue`（队列膨胀到整库）→ `resetShuffle`。这三步正好
把上面四条串在同一帧里。

三处改动，见 README「V1.4.5」章节的代码块。要点：

- **`resync` 只回写 `repeatMode`**。`shuffleModeEnabled` 的所有权归
  `PlayerController.installShuffleOrder` 末尾那一句，两边都写就是两个主人。
- **`resetShuffle` 不再 `invalidate()`**，改成 `forgetInstalledOrder()`。队列变长
  变短时账本里那些旧下标已经被 `ShuffleMemo.slot` 的 `cached in 0 until size`
  挡掉了，不需要整本作废。真正该「重新随机」的只有 `toggleShuffle`——用户按开关
  那一下仍然 `invalidate()`。
- **装 order 加熔断**：1 秒窗口内最多 4 次。正常路径一秒最多进两三次；某个还没
  想到的边缘路径成环时，宁可这一轮不装（下次按键仍能走），也不能让主线程一直转。
  打日志 `shuffle order install throttled`，真出现就说明还有别的环。

`QueueMirror` 的构造参数因此少了一个 `shuffleFlag`——删掉参数比留一个没人用的
lambda 诚实。

### 4. 「切换样式一次性加载多次样式」

`Crossfade` 在过渡期**同时组合两套舞台**，这是它作为「交叉溶解」的定义。两套舞台
各自带一张 `AsyncImage`、一个 `Canvas`（唱片那套还有 30–50 圈凹槽循环）、一套
`pointerInput`，200ms 里同时在跑，看起来就是「切一次、加载好几次」。

改成顺序切换：

```kotlin
val stageAlpha = remember { Animatable(1f) }
var shown by remember { mutableStateOf(skin) }
LaunchedEffect(skin) {
    if (skin == shown) return@LaunchedEffect
    stageAlpha.animateTo(0f, tween(FADE_OUT_MS, easing = LinearEasing))  // 90ms
    shown = skin
    stageAlpha.animateTo(1f, tween(FADE_IN_MS, easing = LinearEasing))   // 150ms
}
```

任一时刻只有一套舞台存在，`shown` 换掉时旧的那棵子树整体离开组合。总时长 240ms
比原来的 200ms 略长，但其中只有一个瞬间存在两套舞台（就是 `shown = skin` 那一帧）。

顺带：`pointerInput(sk)` → `pointerInput(Unit)`。水平拖动是舞台容器的手势，
和里面画什么无关，没有理由随样式重建。

### 5. 这一版没有做的事

- **没做真机验证**。本机模拟器仍在 WHPX 初始化后静默退出（第二节），两个修复都是
  靠单元测试 + 代码路径推导确认的。卡死问题的验证依赖 `shuffle order install
  throttled` 这条日志：真机上一旦看到它，说明还有别的环没找到。
- **没删 `versionedApk` 任务**。发版流程不跑它，但留着不碍事；彻底删掉会牵连
  README 与 CI 的说明。
- **没做「跳过这个版本」**。更新仍只有「下载并安装」与「稍后」两个出口。
- **没改 24h 之外的其它更新行为**（下载续传、SHA-256 校验、NEEDS_PERMISSION 重试
  三条都在 `UpdateStore` / `ApkDownloader` / `UpdateInstaller` 里原样保留）。

---

## 二十四、V1.4.6 设计与架构记录

### 1. 一句话：那张 order 一直是活的

`PlayerController.installShuffleOrder()` 每次 `exo.setShuffleOrder(...)` 装进去的
是一个 `RandomShuffleOrder(size, shuffleState)`。它的八个方法全部转发到同一个
`RandomShuffleState`，而后者的抽样账本 `ShuffleMemo` 在 `copy()` 里是**共享**的
（V1.4.3 起）：

```kotlin
@Synchronized
fun copy(): RandomShuffleState = RandomShuffleState().also { clone ->
    clone.memo = memo
    clone.generation = generation
}
```

所以不管 media3 克隆多少份、装进播放线程的是哪一份，它们看的都是同一本账。队列
换序、账本作废、generation 前进 —— 它下一次被问就给出新答案。

**它就是一张活视图，不是快照。**

### 2. 既然是活的，重装就恒等于零收益

而重装恰恰是那个环：

```
setShuffleOrder
  → ExoPlayerImpl.createMaskingTimeline()
  → PlaylistTimeline 没有重写 equals
  → PlaybackInfo.updatePlaybackInfo 里 Timeline.equals 恒 false
  → 派发 onTimelineChanged
  → 回到 installShuffleOrder
```

V6.1 的 memo 守卫是 `version == installedShuffleVersion && installedShuffleLength == length`，
把无限环截成「一次开关最多多付一轮」；V1.4.5 又叠了一层 1 秒 4 次的
`MAX_INSTALLS_PER_WINDOW` 熔断去兜「多轮」。**两层都是在给一个不该存在的循环装
闸门。**

V1.4.6 直接把闸门连同循环一起删掉 —— 守卫只比长度：

```kotlin
if (installedShuffleLength == length) {
    syncShuffleMode()
    return true
}
```

`installedShuffleVersion` 字段整个删除。长度是唯一需要重装的信号：队列增删时旧
order 捕获的 `size` 就错了，必须换一份；只是换序、只是账本作废，order 自己会跟上。

### 3. 缺口一：熔断那条 return 把模式也吞了

```kotlin
if (++installWindowCount > MAX_INSTALLS_PER_WINDOW) {
    Log.e(TAG, "shuffle order install throttled: ...")
    return false          // ← 在 shuffleModeEnabled 被写下去之前就返回了
}
```

原来的写法里，`controller?.shuffleModeEnabled = shuffle` 只在函数**末尾**执行。
熔断提前 return，这一轮 ExoPlayer 就不知道随机开着 —— 通知栏 / 锁屏 / 蓝牙按它
自带的 `ShuffleOrder` 走，app 内按键按我们的走。**两边各随机各的**，是最难复现
也最难解释的一类「随机播放的 bug」。

现在三条退出路径（熔断 / memo 命中 / 正常装完）统一走：

```kotlin
private fun syncShuffleMode() {
    val c = controller ?: return
    if (c.shuffleModeEnabled != shuffle) c.shuffleModeEnabled = shuffle
}
```

只写一次、只在真的不一致时写。

### 4. 缺口二：账本是按下标记的

V1.4.5 为了让 generation 不动（否则守卫失效、必然重装），把 `resetShuffle()` 里的
`shuffleState.invalidate()` 整个删了。这留下一个真缺陷：

`ShuffleMemo` 按**下标**记账。队列增删或移动之后，同一个下标已经是另一首歌。旧
答案虽然仍在 `0 until size` 范围内合法，`slot()` 的命中条件
（`cached in 0 until size && cached != current`）照样通过 —— 但它指向一首这一次
没被抽中的曲子。表现是「随机老是走那几条路」。

```kotlin
private fun resetShuffle() {
    shuffleState.forgetDraws()
    installShuffleOrder()
}
```

`forgetDraws()` 三件事：扔答案、**留 `lastTrack`**、**不动 generation**。缺一不可
—— 留 `lastTrack` 是因为「上一首避开刚听完的那首」要靠它；不动 generation 是因为
一动就逼安装点重装 order，环又回来了（第 2 节）。

`toggleShuffle()` 仍然走 `invalidate()`（连 `lastTrack` 一起清）—— 用户按下开关
才是真的「重新随机」，包括忘掉刚才那首。

### 5. 这一版没有做的事

- **仍然没有真机验证**。本机模拟器在 WHPX 初始化后静默退出（第二节），改动的
  运行时行为只到「JVM 单测 + dex/APK 静态核验」。若实机仍能复现，抓
  `PlayerController` 标签的 logcat：正常路径除 `shuffle order deferred`（playlist
  编辑还在路上，属预期）与 `shuffle order refused`（ExoPlayer 拒收，属异常）之外
  不该再出现任何一行。
- **没删时间窗熔断**。环已经不存在了，熔断理论上永远不会触发；留着是因为
  `backfillQueue` 这类路径仍可能短时间内多次撞长度变化，删掉它等于假设「所有
  长度变化都是用户动作」，而这个假设没有被验证过。真机上若再看到
  `shuffle order install throttled`，说明还有别的环。
- **没改随机语义**。`exclude` 仍只装当前曲（`previousIndex` 再加 `lastTrack`），
  仍然不做近期去重窗口 / 洗牌袋 / 歌手分散 / 加权 / 种子持久化 —— 与 V1.4.2 的
  决定一致。
- **没动 `QueueMirror.dropInstalled()`**。整份 playlist 被 `setMediaItems` /
  `clearMediaItems` 换掉时那里真的是「没有 order 了」，必须重装，与
  `resetShuffle()` 的增量编辑是两回事。


## 二十五、V1.4.7 设计与架构记录

### 1. 缺陷：一段从 V1.0.1 起就没跑过的分支

`RandomShuffleState.previousIndex` 里有一条避开规则，注释写着「避开刚播完的那
首」，否则「上一首」会退回刚刚听完的曲子、看起来像没生效：

```kotlin
val justPlayed = memo.lastTrack
val avoid = if (size > 2 && justPlayed in 0 until size && justPlayed != current) {
    intArrayOf(current, justPlayed)
} else {
    intArrayOf(current)
}
```

`lastTrack` 由 `ShuffleMemo.started(size, current)` 写入，写进去的就是**刚开播
的这一首**。而 `previousIndex(size, current)` 的 `current` 也正是那一首。第三个
条件 `justPlayed != current` 因此恒为假，`avoid` 里永远只有 `current`。避开规则
是**不可达代码**，从 V1.0.1 写下注释那天起就没生效过。

用户可见后果两条：

- 播放 3 → 按「下一首」到 5 → 按「上一首」：8 个候选里除 5 之外均匀抽，其中
  包含 3，于是有 1/7 概率原样退回。用户会以为按键没生效，再按一次。
- 连按两次「上一首」可以在同一对曲子之间来回弹。

### 2. 修法：`cameFrom`，以及两个不能省的条件

```kotlin
var cameFrom = -1
    private set

fun started(size: Int, current: Int) {
    if (current !in 0 until size) { next.clear(); previous.clear(); return }
    if (current != lastTrack && lastTrack in 0 until size) {
        cameFrom = lastTrack
    }
    lastTrack = current
    next.clear()
    previous.clear()
}
```

- **必须在覆盖 `lastTrack` 之前转存**。晚一行旧值就没了，`cameFrom` 永远是 -1。
- **必须判 `current != lastTrack` 才写 `cameFrom`**。同一首歌会被上报两次：
  media3 的 `onMediaItemTransition`（通知栏/蓝牙/自动续播）与
  `PlayerController.playIndex`（app 内按键、在线曲目单曲播放）。第二条上报若覆
  写，`cameFrom` 会变成当前首，避开规则又白写 —— 这正是原缺陷的成因，只是把
  它从「恒假」换成了「大部分时候为假」。
- `clear()`（换队列、开关随机）连 `cameFrom` 一起清；`forgetDraws()`（队列增量
  编辑）**只清两本账、保留 `cameFrom`** —— 理由与保留 `lastTrack` 相同：编辑队
  列不该让我们忘掉刚才从哪来的。

`previousIndex` 的其余语义没动：`size > 2` 才启用（两首队列排除两个等于无解）、
均匀抽、按 `current` 分槽缓存。

### 3. 一次「测试绿了但什么都没断言」的事故

`RandomShuffleBacktrackTest` 第一版最关键那条断言写的是：

```kotlin
assertNotEquals("previous undid the next press", 50, undone)   // ← 错的
```

50 个种子里从没退回的概率是 (6/7)^50 ≈ 0.0005，所以 `undone == 50` 几乎不可能
出现 —— 这条断言**无论实现是 bug 还是修好的都会通过**。它跑绿了，但它没有证明
任何事。改成 `assertEquals(0, undone)` 之后立刻在 50 个种子里抓到若干次退回，
改完实现才转绿。

教训写在这里而不是别处：**「跑绿」不等于「断言了」**。概率型断言必须把「完全
不发生」写成严格相等，否则它只是在描述一个几乎为真的巧合。

同批把 V1.4.6 留下的 `forgetDrawsKeepsTheTrackThatJustPlayedSoPreviousStillAvoidsIt`
改名改写为 `forgetDrawsKeepsTheTrackWeCameFromSoPreviousStillAvoidsIt`。它断言的
是旧语义（`lastTrack` 作依据），留着会把刚才那个 bug 焊死在测试里 —— 这是本项
目第二次遇到「测试锁住缺陷」（第一次是 V1.4.3 的
`aCopyIsASnapshotNotASharedView`，断言的正是后来被证伪的快照语义）。

### 4. 这一版没有做的事

- **仍然没有真机验证**。本机模拟器在 WHPX 初始化后静默退出（第二节），改动只到
  「JVM 单测 + aapt2/apksigner 静态核验」。
- **没改 `getFirstIndex()/getLastIndex()`**。它们返回固定端点 `0` / `size - 1`，
  而 `UnshuffledShuffleOrder` 的契约是「没有邻居时返回 -1、端点按序」——
  理论上 `repeatMode = ALL` 回绕时通知栏会跳到队列第一首。但随机模式下每步都抽
  得到下一首，`getNextIndex` 永不返回 -1，那条回绕路径够不到，这一版不动。
- **没动 `pendingSeekIndex` / `resolvePendingSeek()`**。`resolvePendingSeek` 在
  `count != queue.size` 时静默丢弃 target（只 `return`、不重试），而整仓测试对它
  零覆盖。它需要能观察到「MediaController 异步补投 playlist 编辑」的时钟才能测，
  单测造不出那个时序，硬写就是假测试。记为待办。
- **没删时间窗熔断**（1 秒 4 次）。理由同 V1.4.6：`backfillQueue` 这类路径仍可能
  短时间内多次撞长度变化，删掉等于假设「所有长度变化都是用户动作」，这个假设没
  被验证过。真机上若再看到 `shuffle order install throttled`，说明还有别的环。
