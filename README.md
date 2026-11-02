# 极简music (JiJianMusic) for Android

`applicationId = com.lxmusic.android` · 当前版本 **1.1.4** (`versionCode = 9`)

一个从 [lyswhut/lx-music-desktop](https://github.com/lyswhut/lx-music-desktop) 的
音源架构出发、已经转型为**纯本地极简音乐播放器**的 Android 应用。
在线搜索与自定义音源在 V1.1.0 整个删除（连同 `user_api` 表与 LX 脚本运行时），
只留本地文件播放。

An Android music player derived from the architecture of
[lyswhut/lx-music-desktop](https://github.com/lyswhut/lx-music-desktop).

The desktop app is Electron + Vue 3. This project borrowed its shape and then
took the opposite direction: playback is local files only, through Media3.

```
┌──────────────────────────── Android app (Kotlin) ────────────────────────────┐
│  Compose UI  ─▶  MainViewModel  ─▶  PlayerController ─▶ Media3 ExoPlayer     │
│                       │                    │                                  │
│                       ▼                    ▼                                  │
│              SettingsStore (DataStore)   QueueMirror (one playlist)          │
│              Room DB v5                  RandomShuffleOrder (draws)          │
└───────────────────────────────────────────────────────────────────────────────┘
```

`ui/shell/LxShell.kt` holds the stage; full-screen pages are curtains registered
with `CurtainSpec(key, visible, onDismiss, content)`, so no page writes its own
`BackHandler`.

---

## Supported sources

Local files, and nothing else. Two ways in:

- **SAF folder scan** — pick any folder through the system picker; the app stores
  the tree URI and rescans on demand. Survives reboots and survives the folder
  being moved within the same volume.
- **MediaStore fallback** — for anything the picker cannot reach.

Recognised extensions and their MIME types live in `local/SafMusicScanner.kt`.
Playback is ExoPlayer over `content://` and `file://` URIs; there is no network
playback path left in the codebase.

---

## Building

```bash
# Requires JDK 21 and the Android SDK (platform 34, build-tools 34.0.0).
# JDK 17 also works; AGP 8.5 does *not* support JDK 25.
export JAVA_HOME=/path/to/jdk21
export ANDROID_SDK_ROOT=/path/to/android-sdk
./gradlew :app:assembleDebug
```

The debug APK is written to `app/build/outputs/apk/debug/app-debug.apk`.

For anything you actually hand to a user, also run `:app:versionedApk` —
a second copy is emitted as
`build/apk/jijian-music-<versionName>-vc<versionCode>-debug.apk`.
That rename is load-bearing: QQ (and most IM file transfers) de-duplicate on
**file name + size**, and consecutive `app-debug.apk` builds were byte-identical
in length, so a new package was silently matched against the one already in the
chat. See [HANDOVER.md](HANDOVER.md) §6.

---

---

## Project layout

```
app/src/main/java/com/lxmusic/android/
├── LxApp.kt                     # Application + singletons
├── MainActivity.kt              # Compose entry point
├── CrashLogger.kt               # uncaught-exception handler -> filesDir/last_crash.txt
├── data/
│   ├── db/AppDatabase.kt        # Room DB v5 (playlists, favorites, history, local_track, play_event)
│   ├── model/MusicInfo.kt
│   ├── repo/MusicRepository.kt  # single DB access point
│   ├── settings/SettingsStore.kt# DataStore preferences (29 keys)
│   ├── settings/EqProfile.kt    # persistable tone curve (bands encode/decode)
│   └── stats/PlayStats.kt       # pure play-count aggregation (day / week / all)
├── local/
│   ├── LocalMusicScanner.kt     # MediaStore fallback scan
│   ├── SafMusicScanner.kt       # SAF folder scan (T2)
│   ├── LocalFileDeleter.kt      # file deletion (T4)
│   ├── LocalLibrary.kt          # grouping / sorting / search / merge
│   └── LocalTrackMapper.kt      # entity <-> model
├── lyric/
│   ├── Lyric.kt                 # Lyric model + LrcParser
│   ├── LyricLoader.kt           # .lrc sibling + embedded lookup
│   ├── LyricEncoding.kt         # UTF-8 / GBK / Latin-1 detection
│   └── EmbeddedLyrics.kt        # ID3 / FLAC / Ogg / MP4 tag reader
├── net/
│   ├── HttpBridge.kt            # OkHttp wrapper
│   └── UpdateChecker.kt         # V1.0.2: GitHub Releases latest -> UpdateInfo
├── player/
│   ├── PlaybackService.kt       # MediaSessionService + ExoPlayer
│   ├── PlayerController.kt      # playback state, queue, draw policy
│   ├── PlayerHolder.kt          # the ExoPlayer instance (audioSessionId, ShuffleOrder)
│   ├── RandomShuffle.kt         # V1.0.1: per-draw shuffle policy (authoritative KDoc)
│   ├── RandomShuffleOrder.kt    # media3 ShuffleOrder adapter
│   ├── ShuffleSink.kt           # V1.0.1: install/drop seam for the ShuffleOrder
│   ├── QueueMirror.kt           # V1.0.1: the one place that mirrors the Queue
│   ├── QueueIndex.kt            # media id -> queue slot resolution
│   ├── StallWatchdog.kt         # silent-stall detection (pure Kotlin)
│   ├── QueueLogic.kt            # queue move / remove pure functions
│   └── EqualizerController.kt   # audiofx wrapper (+ EqProfile replay on attach)
├── ui/
│   ├── LxApp.kt                 # V1.1.3: shell wiring only, 200 lines
│   ├── HomeChrome.kt            # V1.1.3: top bar / banner / mini player / stage body
│   ├── shell/LxShell.kt         # V1.1.3: stage slots + curtains
│   ├── MainViewModel.kt         # single state holder
│   ├── CrashReportDialog.kt     # previous-run crash trace, copy/clear
│   ├── components/              # TrackRow, LyricView, RotatingArtwork, PlayerSkin, ArtworkColors, SearchBar, Hints
│   ├── neon/                    # Neon Vinyl design system: tokens, aurora, glass, controls, shell, NeonScreen scaffold
│   ├── screens/                 # Local, Library, NowPlaying, Queue, Equalizer, Stats, FolderManager, Settings
│   └── theme/                   # OledTokens / ColorPalettes / Theme
```

> `data/Changelog.kt`, `ui/screens/AnnouncementScreen.kt`, `player/ShuffleBag.kt`,
> `player/BagShuffleOrder.kt`, `player/PlayHistory.kt`, `jsengine/`, `source/`,
> `ui/screens/OnlineScreen.kt` and `FeatureFlags.kt` no longer exist —
> `RandomShuffle.kt` is the only shuffle implementation, and playback is
> local-only. Any doc still referring to the others is stale.

---

## Legal

This project ships no music and hosts no content. It plays audio files already
on your device. Respect copyright and the laws of your jurisdiction.

---

## Features

### Local music player
- Scans device audio via `MediaStore` (`LocalMusicScanner`), grouped and sorted.
- Plays local files directly with ExoPlayer (file path **or** `content://` Uri).
- No network playback path: `PlayerController` takes no resolver, and `playIndex`
  only ever produces a `file://`/`content://` URI.
- Embedded / sibling `.lrc` lyric loading (`LyricLoader`).

### Lyrics
- LRC parser (`LrcParser`) supporting multiple timestamps per line, `mm:ss.xx`
  with 1–3 fractional digits, and automatic **translation** detection (duplicate
  timestamps become a parallel track).
- Binary-search line lookup (`Lyric.indexAt`) for O(log n) sync during playback.
- Karaoke-style scrolling view (`LyricView`): active line enlarged, highlighted,
  auto-centred, with the translation shown beneath.

### Cool UI — "Neon Vinyl"
- **Aurora backdrop**: three radial-gradient lights drift on elliptical orbits
  (11 s / 17 s / 23 s, coprime so they never line up). On the now-playing screen
  the palette is **extracted from the current artwork**, so the room changes
  colour with the song.
- **Glass, not blur**: panels are translucent white + 1 dp stroke + a top
  highlight. `RenderEffect` blur is deliberately banned — a full-screen
  `RenderEffect` once crashed on API 31+ and was removed (see `HANDOVER` §7).
- **Self-drawn progress bar**: Material's `Slider` forces a 48 dp touch target
  that steals taps from the play button; `NeonProgress` hand-rolls the gesture
  handling with `awaitEachGesture` so press / drag / release never fight.
- **Breathing play button**: 76 dp gradient sphere with three glow rings whose
  alpha tracks playback state; `NeonMotion` honours the system "remove
  animations" accessibility setting.
- **Motion with purpose**: swipe left/right on the stage to change track;
  the currently playing row in the queue gets a rail + pulsing dot + cover halo;
  the queue scrolls to the current track on open.
- **Dual-theme**: the aurora is dimmed to ~7 % on light themes. A dark backdrop
  under near-black `onSurface` text would be unreadable; `NeonSurface.kt` holds
  both specs.
- Vinyl-style rotating album art (`RotatingArtwork`) that spins only while playing,
  with a tone arm that drops when playback starts.

### Playback behaviour (V1.0.1)
- **Shuffle draws once per track change**: the next track is a uniform pick over
  every queue position except the current one. The draw is remembered only for as
  long as the track does not change, so one decision cannot be answered twice with
  two different tracks — that was what stalled the player in earlier versions,
  and what made media3 and the buttons disagree before V1.0.1. The KDoc with the
  full history of this rule lives in `player/RandomShuffle.kt`.
- **Both directions are random.** "Previous" in shuffle mode is the *same* draw as
  "next", with one extra exclusion: it also avoids the track that just played, so
  pressing previous twice cannot bounce between two tracks. It is deliberately
  **not** a walk back through what the user heard — there is no play-history stack
  anymore. `RandomShuffleOrder` hands media3 the same `RandomShuffleState`, so the
  notification, lockscreen, Bluetooth and in-app buttons answer identically.
- **A hardware "previous" never restarts the current track.**
  `PlaybackService` sets `setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)`, so the
  media3 rule "past 3 s, previous means restart" cannot fire. The older failure —
  `getPreviousIndex()` returning `-1`, which media3 reads as "no previous item" and
  answers by seeking to 0 — is gone too, because the draw never returns `-1` for a
  queue with more than one entry.
- **Silent-stall watchdog**: if the position freezes for 8 s while the player
  still claims to be playing, the player is asked to retry; a second freeze at the
  same spot rebuilds the media source from the frozen position. Stuck buffering
  is timed separately (20 s).
- **Equalizer curve survives restarts and session rebuilds**: Android rebuilds the
  audio effect empty on every new audio session, so the curve is stored as a
  preference (`AppSettings.eq`) and replayed after each `attach()` — including the
  ones that happen mid-session when playback moves to the next track.
- **Back means up**: every full-screen page intercepts system Back and
  returns to the shell instead of closing the app.

### Play statistics
- A **播放统计** screen (top bar, next to the equalizer) answering *what did I
  actually listen to?* over 今日 / 本周 / 全部.
- Backed by a dedicated `play_event` table (Room v4, `MIGRATION_3_4`) that keeps
  one row per play — deliberately not `play_history`, which is keyed by track
  and therefore cannot hold a count. Events older than a year are pruned on
  each write.
- Aggregation lives in `data/stats/PlayStats.kt`, a pure object with no Android
  or database dependency, pinned to a fixed clock and `Asia/Shanghai`.
- Turned off by its own switch: Settings → 播放 → **记录播放统计**. It is
  independent of 播放时记录历史, which only governs the 最近播放 list — the two
  used to be wired together, so hiding 最近播放 silently froze the statistics.

### Crash reporting
- `CrashLogger` chains the default uncaught-exception handler and writes the
  trace to both `filesDir/last_crash.txt` and
  `getExternalFilesDir(null)/last_crash.txt`.
- `ui/CrashReportDialog.kt` shows it on next launch with copy/clear.

### Updating
- Settings → 更新 → **检查更新** pulls the latest GitHub release and shows its
  notes in a dialog; the release tag is compared numerically, so `v1.1.10` is
  newer than `v1.1.9`.
- A silent check runs once per 24 h on launch and surfaces a banner on the home
  screen only when there really is something new.

---

## Tests

```bash
./gradlew :app:testDebugUnitTest
```

230 unit tests across 26 classes, all green (run `./gradlew :app:testDebugUnitTest`):

| Test class | Cases | Covers |
|---|---:|---|
| `lyric/EmbeddedLyricsTest` | 18 | ID3v2 USLT/TXXX, FLAC VorbisComment, Ogg/Opus, MP4 `©lyr`, GBK/UTF-8/BOM |
| `data/stats/PlayStatsTest` | 17 | day/week bucketing, ranking, timezone (pinned `Asia/Shanghai`) |
| `player/RandomShuffleStateTest` | 18 | **V1.0.1**: draw contract — next excludes the current track, prev excludes it too and additionally skips the track that just played, a draw is remembered until a track starts, `onTrackStarted` clears both, a 2-track queue can still step both ways |
| `local/LocalLibraryTest` | 15 | search, sort orders, album/artist grouping, multi-singer split |
| `player/RandomShuffleOrderTest` | 11 | media3 `ShuffleOrder` contract under **V1.0.1** draw semantics — previous always answers a legal in-range slot different from the current one, clones keep drawing from the same state |
| `player/QueueMirrorTest` | 17 | **V1.0.1**: the mirrored playlist's five steps run in one fixed order, `move`/`remove` drop the installed order *after* the player mutation, `replace` refuses a queue it cannot mirror |
| `player/QueueLogicTest` | 8 | queue move/remove bookkeeping — `nextIndex`/`prevIndex` were deleted in V1.1.3 with their tests, because production never called them |
| `lyric/LrcParserTest` | 8 | timestamp parsing, multi-tag lines, translation detection |
| `player/ShuffleStallProbe` | 9 | **V1.0.1**: 500-step app walk + 500-step system walk never stall, the two paths always pick the same track, every draw from one track can reach all N-1 alternatives |
| `player/StallWatchdogTest` | 8 | silent-stall detection — retry budget, re-buffer second stage, buffering-only stall, reset on progress (fake clock); a buffer does not spend the freeze budget |
| `ui/neon/NeonTokensTest` | 8 | aurora peak keeps body text ≥4.5:1 on all 8 palettes, gradient stops, motion cadence |
| `ui/theme/ThemePaletteTest` | 8 | 8 colour palettes, light/dark contrast |
| `data/EqProfileTest` | 7 | band encode/decode round-trip, corrupt entries dropped not thrown |
| `player/RandomShuffleCloneTest` | 7 | clone protocol: candidates never disturb the live order |
| `local/LocalLibraryMergeTest` | 6 | cache merge / prune semantics |
| `player/RandomShuffleStateStalenessTest` | 6 | memo never goes out of range after the queue shrinks |
| `ui/theme/OledTokensTest` | 6 | OLED token contrast ratios |
| `data/SettingsRoundTripTest` | 11 | every preference key is written and read back through the *same* key constant — the "equalizer does not survive a restart" class of bug cannot hide behind a default-only assertion; the V1.0.2 update token is checked the same way |
| `net/UpdateCheckerTest` | 11 | **V1.0.2**: release-tag version comparison (leading `v`, numeric segments, prerelease suffix ordering) and GitHub release JSON parsing (first `.apk` asset, title fallback, graceful degradation on missing fields) — no network in unit tests |
| `data/SettingsTest` | 4 | settings defaults (incl. flat EQ profile) / enum values |
| `local/SafMusicScannerTest` | 5 | audio extension / mime detection |
| `player/QueueIndexTest` | 5 | a queue slot is only real if the *id* at that slot is the track playing — the online-queue `playIndex(3)` → reported-as-0 defect |
| `player/RandomShuffleTest` | 6 | **V1.0.1**: `pickOther` always lands in range and never returns an excluded slot; uniformity over 600 seeds |
| `local/LocalTrackMapperTest` | 4 | entity ↔ MusicInfo round-trip |
| `ui/components/PlayerSkinTest` | 4 | player skin parsing / fallbacks |
| `data/stats/RecordPolicyTest` | 3 | 最近播放 and 播放统计 are independent switches |

The five `RandomShuffle*` classes plus `ShuffleStallProbe` encode every shuffle bug
this project has ever hit — read them before touching that code. [HANDOVER.md](HANDOVER.md) §5
explains the root causes behind them.

`NeonTokensTest` is the same idea for the visual layer: it recomputes WCAG
contrast over the *brightest possible* aurora composite on every palette, so a
future "make it pop more" tweak cannot silently drop body text below AA.

---

## V1.1.4 — Fewer settings, one new switch (`versionCode = 9`)

Four groups in settings now fold: tap a header to collapse it, the chevron shows
which way. Only 外观 starts open, so the whole page fits on one screen and you
scroll to a group instead of hunting for it. 播放 and 歌词 merged into
播放与歌词 — they were two halves of the same topic, and splitting them cost a
scroll for nothing.

New: **启动时自动检查更新**. The daily silent check existed since V1.0.3 with no
way to turn it off; now there is a switch, on by default, and turning it off
leaves only the manual 「立即检查」 button.

Removed:

| Removed | Why |
|---|---|
| 访问令牌 (settings row + `UpdateChecker.fetchLatest(token)`) | the repo is public now, so the token bought nothing — forgetting to fill it only produced a 404 telling you to go find a PAT |
| 缺封面 filter + row badge | embedded cover reading covers nearly every local track; the filter sat empty |
| 不重复曲目 tile in 播放统计 | the ranking list below already tells you how many tracks there are |

Key count is unchanged at 29: `update_token` goes, `auto_check_updates` arrives.

---

## V1.1.3 — Stage and curtains, plus a sweep of dead code (`versionCode = 8`)

`ui/LxApp.kt` was 607 lines because five full-screen pages each wrote the same
three lines — `BackHandler` + close + `return` — and shared one function body
with the wide-screen two-column branch. A sixth page meant repeating it a sixth
time, and forgetting the `BackHandler` made Back exit the app.

`ui/shell/LxShell.kt` keeps the stage in four slots (`backdrop`, `tabs`, `dock`,
`spotlight`) and takes curtains as data:

```kotlin
CurtainSpec(key = "settings", visible = settingsOpen, onDismiss = viewModel::closeSettings) {
    SettingsScreen(viewModel = viewModel, onBack = viewModel::closeSettings)
}
```

Earliest in the list is topmost, which reproduces the old early-return ordering,
so "open the queue from the player" stacks exactly as before. Back closes the top
curtain; with none up the shell does not intercept Back at all.

Home chrome moved out to `ui/HomeChrome.kt` (top bar, update banner, mini player,
stage body). `LxApp.kt` is 607 → 200 lines and now reads as state plus layout.
`Scaffold` went with it: its only contribution was insets, and the structure is a
`Column` where the top bar eats `statusBarPadding` and the dock eats
`navigationBarPadding`.

Dead code removed in the same pass:

| Removed | Why |
|---|---|
| `NeonPanel`, `NeonHalo` | zero call sites; `neonGlass` / `neonChip` stay |
| `QueueLogic.nextIndex` / `prevIndex` + 4 tests | production never called them |
| `AudioQuality`, `quality`, `setQuality` + 3 assertions | lost its consumer when online playback went away |
| Settings → 高级 two sliders | debug parameters; persisted values still apply |
| `user_api` table (schema 4 → 5) | the custom-source stack is gone |

A real bug fixed on the way: **耳机拔出时暂停** was a switch that did nothing.
`MainActivity` paused unconditionally on `ACTION_AUDIO_BECOMING_NOISY` and never
read the setting.

## V1.1.2 — New launcher icon: five pulse bars (`versionCode = 7`)

The launcher icon no longer draws a note glyph. It is five rounded bars whose
heights follow a symmetric pulse envelope (0.42 / 0.72 / 1.0 / 0.72 / 0.42) and
whose colours grade outward from the centre: `#00C7BF` teal, `#8CC7BF` cyan,
`#96A0DC` lilac. The plate reuses the player page's night gradient, `#12102E`
to `#0D0D1E`, so the icon and the app share one ground colour.

Why this shape: it reads as music without drawing an instrument or a note; five
bars stay legible at 48 dp; and the silhouette is complete on its own, so the
Android 13 monochrome layer carries the same form instead of falling back to the
old glyph.

Files: `res/drawable/ic_launcher_foreground.xml`,
`ic_launcher_background.xml`, `ic_launcher_monochrome.xml`, plus the five
`mipmap-*/ic_launcher.png` densities and their `_round` twins.

## V1.1.1 — One signing key for every build (`versionCode = 6`)

Until now every APK was signed with the *debug* keystore of whichever machine
built it. The local certificate and the GitHub Actions certificate differed, so
the release download could never update a locally installed app — Android
reported a signature conflict and demanded an uninstall.

- `app/build.gradle` pins the `release` build type to `signing/jijian.keystore`
  (kept in the repo; the file is the only copy of the key, treat it
  accordingly). CI reads the same keystore from the `JIJIAN_KEYSTORE_B64`
  secret; a local build without it falls back to the debug key for development
  sideloading only.
- `versionedApk` now publishes the **signed release** build as
  `jijian-music-<v>-vc<c>.apk`; the debug artifact keeps its `-debug` suffix
  via `versionedDebugApk`.
- The CI release job decodes the keystore secret outside the work tree, so the
  key material never lands in build artifacts.

**One-time cost:** updating to 1.1.1 requires uninstalling the previous
version — the certificate changes. Every release after this one updates in
place.

---

## V1.1.0 — Lean to the core: local-only, listening-first (`versionCode = 5`)

The biggest release since the 1.0 renumbering, and most of it is *subtraction*.

### Removed: the whole online-search stack

`jsengine/` (WebView script runtime + search bridge), `source/` (custom-source
manager, LX models, KW provider), `OnlineScreen`, `FeatureFlags`, the bundled
`assets/jsengine/*.js` (~40 KB of JS), and the `user_api` table wiring are gone.
`PlayerController` no longer takes a `SourceManager`: every track is local, so
`playIndex`/`rebuildFrozenSource` resolve a `file:///` path directly and the
quality-picker (`preferredQualityId`/`pickQuality`) died with it. The APK
carries no WebView-engine code path any more.

### Home is now "listening first"

The first tab relabels from 本地音乐 to **正在听**: the last-played hero card is
joined by a horizontal rail of recently played covers, and the raw track list
retreats below them. The point of the app is "open it and keep listening" —
the home screen now agrees.

### Smaller UX repairs

- **Queue removal is undoable** — a snackbar ("已移除「…」· 撤销") appears after
  `queueRemove`; `PlayerController` remembers the removed track and its slot and
  `undoQueueRemoval()` puts it back (adjusting `currentIndex` when needed).
- **Missing-cover filter** — a 缺封面 chip shows only tracks without artwork,
  and cover-less rows carry a small amber badge (`missingCoverBadge`).
  `TrackRow` gained a `modifier` parameter for it.

### Visual

- **Aurora follows the album** — the home shell now extracts the dominant
  colour of the current track's cover (`ArtworkColors.schemeFor`, one IO pass
  per track change) and feeds it into `rememberNeonSurface(artworkTint = …)`;
  the palette seed remains the fallback. Hue only — the brightness budget that
  `NeonTokensTest` locks is untouched.
- **Two-pane wide layout** — at ≥ 840 dp the shell and the full
  `NowPlayingScreen` sit side by side instead of the player taking over. The
  shell was extracted into a reusable `HomeShell` composable to make that
  possible.

### Engineering

- **`LyricController`** — lyric loading, offset and line-index moved out of
  `PlayerController` (symmetric with `EqualizerController`); settings write to
  `lyricController.offsetMs/preferLrcFile/readEmbedded`.
- **Settings write funnel** — `SettingsStore.update(transform)` is the single
  write path: current snapshot → transform → `writeAll(new)`, whose read/write
  sides live in the same companion so a mistyped key cannot split again. Every
  existing setter is now one line over it.
- **Configurable watchdog** — 卡住判定/缓冲超时 sliders in a new 高级 settings
  section; `StallWatchdog.stallMs/bufferingMs` are mutable and re-read every
  tick.

### Verification

`build/apk/jijian-music-1.1.0-vc5-debug.apk` (23,369,778 B), `versionCode = 5`,
`versionName = 1.1.0`, confirmed with `aapt2 dump badging`. 235 unit tests
across 26 classes, 0 failures.

---

## V1.0.3 — Silent update check on startup, and a home-screen banner (`versionCode = 4`)

V1.0.2 put the check behind a button — you had to know to look. V1.0.3 closes
the loop: the app asks once a day on its own, and a new release announces
itself on the home screen.

### What was added

- **Silent startup check** (`MainViewModel.maybeCheckForUpdatesOnStartup`) —
  runs once per 24 h, throttled by a persisted timestamp
  (`last_update_check_at`). It shares the result flow with the manual check but
  only *disturbs* you when it finds something: "already up to date" and
  failures both fall back to `IDLE`, so the settings subtitle never shows the
  result of a check you did not ask for, and an offline phone never sees a
  spurious error dialog.
- **Home-screen banner** — when the state lands on `AVAILABLE`, a glass banner
  sits above the tab row: "发现新版本 vX.Y.Z" plus the release title, with the
  brand accent bar and a pulsing dot. Tapping it opens the same release-notes
  dialog the settings page uses (`UpdateDialog` was promoted to `internal`).
- **CI + automated releases** (`.github/workflows/ci.yml`) — every push runs
  the unit-test suite and counts the results; pushing a `v*` tag builds the
  versioned APK and uploads it to a GitHub Release with generated notes. The
  release notes *are* the update announcement the app fetches — publishing a
  release and announcing it are now the same action.
- **Dead-code sweep** — the never-read `allowShowUpdateAlert` field (both
  `UserApiInfo` and `UserApiEntity`), the superseded design docs moved to
  `docs/archive/`, and the repo helper scripts (`_cnt.py`, `_per_class.py`,
  `_probe_stall.ps1`) rewritten to be path-independent and BOM-free.

### Verification

`build/apk/jijian-music-1.0.3-vc4-debug.apk`, `versionCode = 4`,
`versionName = 1.0.3`, confirmed with `aapt2 dump badging`. 235 unit tests
across 26 classes, 0 failures.

---


## V1.0.2 — Check for updates from the settings page (`versionCode = 3`)

The app could tell you its own version number, but not whether a newer build
existed. The settings page now asks GitHub for the latest release and shows its
release notes in place.

### What was added

- `net/UpdateChecker.kt` — one HTTP call to
  `https://api.github.com/repos/<repo>/releases/latest` through the existing
  `HttpBridge`. It parses `tag_name`, `name`, `body`, `html_url` and the first
  `.apk` asset into an `UpdateInfo`. Version comparison strips a leading `v`,
  compares numeric segments as numbers (so `1.0.10` beats `1.0.9`), and treats a
  release with no suffix as newer than a prerelease carrying the same number.
- `SettingsStore` gains one key, `update_token`, together with its read/write
  pair (`writeUpdateToken` / `toSettings`) and tests that walk both sides — the
  same treatment the equalizer profile got after a "does not survive a restart"
  bug turned out to be a mistyped key name that compiled fine and silently fell
  back to the default forever.
- `MainViewModel` owns a small `UpdateCheckState` flow
  (`IDLE / CHECKING / UP_TO_DATE / AVAILABLE / FAILED`) and a `checkForUpdates()`
  that runs the fetch off the main thread.
- The settings page has a **更新** section: a "检查更新" row and an optional
  **访问令牌** field. The result opens a dialog showing the current version, the
  latest version, the APK size, and the full release notes.

### Why there is a token field

The GitHub repository is private. An anonymous `releases/latest` request on a
private repo answers `404`, which is indistinguishable from "this repo has no
releases", so the checker reports 404 as "the update source is unreachable, or
the repo is private" and offers the token field instead of a dead end. With a
`repo`-scoped personal access token the same request succeeds. A public mirror
needs no token.

### Verification

`build/apk/jijian-music-1.0.2-vc3-debug.apk` (23,432,086 B), `versionCode = 3`,
`versionName = 1.0.2`, confirmed with `aapt2 dump badging`. 235 unit tests across
26 classes, 0 failures — 13 of them new (`UpdateCheckerTest`, plus two token
round-trip cases in `SettingsRoundTripTest`).

---

## V1.0.1 — Both shuffle directions are draws, and prev never restarts (`versionCode = 2`)

The V1.0 fixes each solved one half of the problem and left the other half in place.

### "Next is random, previous is not"

`RandomShuffleState.previousIndex()` was still walking the shuffle cycle — it answered
"the slot before this one *in the order*", which is a track this pass has not reached
yet. It read as a random jump *forward*, and the app's own prev button and the media
session's prev button agreed only by accident. `prev()` had the same shape: it popped
`PlayHistory`, so the first press of a session had an empty stack and did nothing useful.

Both directions are now **draws**: a uniform pick over every queue position except the
current one, made once per track change and remembered until the next track actually
starts. `previousIndex()` adds one exclusion — it also skips the track that just played,
so pressing prev twice cannot bounce between two tracks. `PlayHistory` is gone; so is the
`previousOf` hook on `RandomShuffleOrder`, because there is nothing left to hook.

The cycle itself is gone with it. `cycleFrom()`, `advanceCycle()`, `cyclesDrawn` and the
`generation` counter that existed to notice a redraw all disappear; what remains is
`nextPick` / `previousPick` and one `onTrackStarted()` entry point that clears them.

### A headset "previous" restarts the current track, even after the V1.0 fix

`setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)` closed the "past 3 seconds, prev means
restart" path, but media3 has a *second* way to reach `seekToCurrentItem(0)`:

```java
// BasePlayer.seekToPrevious()
if (hasPreviousMediaItem() && pos <= maxSeekToPrevious) seekToPreviousMediaItemInternal(7)
else seekToCurrentItem(0L, 7)
```

`hasPreviousMediaItem()` asks `getPreviousMediaItemIndex()`, which asks the installed
`ShuffleOrder`. With the old `previousOf` hook, an empty history returned `-1`, media3
read that as "there is no previous item", and the `else` branch replayed the track from
zero. That is why the very first headset press always restarted.

The fix is not to special-case the empty stack. A draw never returns `-1` when the queue
holds more than one entry, so the `else` branch cannot be reached in shuffle mode. The
threshold override is kept as a second line of defence for sequential mode, where a real
`-1` at index 0 is still possible.

### Two seams, so this cannot silently fork again

`player/ShuffleSink.kt` (install / drop the order) and `player/QueueMirror.kt` (the whole
mirrored-playlist lifecycle, five steps in one fixed order) replace eight copy-pasted
blocks in `PlayerController`. `QueueMirrorTest` locks the ordering — the historical bug where
`moveInQueue` installed an order describing the *pre-move* list is now a failing test
rather than a code review note.

### Verification

`./gradlew :app:assembleDebug :app:versionedApk :app:testDebugUnitTest` →
**222 tests / 25 classes / 0 failures**; APK
`build/apk/jijian-music-1.0.1-vc2-debug.apk` (23,399,318 B), `versionCode = 2`,
`versionName = 1.0.1` (confirmed with `aapt2 dump badging`).

On the emulator, with a 6-track queue and shuffle on: ten presses of the in-app next
button, six presses of the in-app prev button, five `KEYCODE_MEDIA_PREVIOUS` and five
`KEYCODE_MEDIA_NEXT` events all moved to a different track every time, with no repeat of
the track that had just played and no fixed overall order.

---

## V1.0 — Renumbered for release, with two playback fixes (`versionCode = 1`)

The version number stops counting iterations and starts counting releases: `6.2.0 / vc26`
becomes **`1.0.0` / `vc1`**. Alongside the renumber, two long-standing playback bugs.

> **Upgrade note**: `versionCode` goes *down* from 26 to 1, so Android treats this as a
> downgrade. `adb install -d` is required, and an in-place upgrade by an end user is
> refused by the system — it has to be uninstalled and reinstalled.

### Next now actually advances, once per press

`RandomShuffleState.nextIndex()` used to redraw the whole cycle **inside the query** when
it reached the last slot of the order:

```kotlin
if (pos < size - 1) return o[pos + 1]
return drawn(RandomShuffle.cycleFrom(size, current, random))[1]   // old
```

`drawn()` bumps `cyclesDrawn` and `generation` unconditionally, so it was not idempotent —
despite the KDoc claiming it was. media3 asks `getNextIndex` several times per decision
(`hasNext`, the notification button target, `MediaPeriodQueue`), and the app's own buttons
share the same `RandomShuffleState`. Any query could therefore hand back a *different*
order mid-decision, which truncated the cycle and left the user pressing "next"
several times before anything moved.

`nextIndex()` is now a pure query — it draws only when no order exists yet, and the
last-slot case is a defensive wrap to `o[0]`. Cycle advancement moved to a new
`advanceCycle(size, current)`, the **single** mutating entry point, called only when a
track really started (`onMediaItemTransition` and `playIndex`). It is idempotent: after a
redraw the current track sits at slot 0, so calling it twice changes nothing.

### A headset "previous" goes back one track, never restarts this one

`BasePlayer.seekToPrevious()` is not `seekToPreviousMediaItem()`. It first compares
`getCurrentPosition()` against `getMaxSeekToPreviousPosition()` (media3's default is
3000 ms) and, past that threshold, calls `seekToCurrentItem(0, …)` — i.e. it replays the
current track from the top. That default exists for podcast "skip back a few seconds"
UIs and is exactly wrong for a music player. `PlaybackService` now builds the player with
`setMaxSeekToPreviousPositionMs(Long.MAX_VALUE)`, making the threshold unreachable so
every press takes the `seekToPreviousMediaItemInternal` branch. The on-screen prev button
was never affected: it calls `PlayerController.prev()` directly and never goes through
`BasePlayer.seekToPrevious()`.

### Verification

`./gradlew :app:assembleDebug :app:versionedApk :app:testDebugUnitTest` →
**217 tests / 25 classes / 0 failures**; APK
`build/apk/jijian-music-1.0.0-vc1-debug.apk` (23,513,794 B), `versionCode = 1`,
`versionName = 1.0.0` (confirmed with `aapt2 dump badging`).
