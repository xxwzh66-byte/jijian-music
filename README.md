# 极简music (JiJianMusic) for Android

`applicationId = com.lxmusic.android` · 当前版本 **1.2.0** (`versionCode = 14`)

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
│                       │  facade over 4 stores                                 │
│                       │                    │                                  │
│                       ▼                    ▼                                  │
│              SettingsStore (DataStore)   QueueMirror (one playlist)          │
│              Room DB v6                  RandomShuffleOrder (draws)          │
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
│   ├── db/AppDatabase.kt        # Room DB v6 (playlists, favorites, history, local_track, play_event)
│   ├── model/MusicInfo.kt
│   ├── repo/MusicRepository.kt  # single DB access point
│   ├── settings/SettingsStore.kt# DataStore preferences (30 keys)
│   ├── settings/EqProfile.kt    # persistable tone curve (bands encode/decode)
│   ├── stats/PlayStats.kt       # pure play-count aggregation (day / week / all / year)
│   └── backup/BackupCodec.kt    # .jjmbackup container + track fingerprint matcher
│       backup/BackupRepository.kt # export / merge-import against Room
├── local/
│   ├── LocalMusicScanner.kt     # MediaStore fallback scan
│   ├── SafMusicScanner.kt       # SAF folder scan (T2)
│   ├── LocalFileDeleter.kt      # file deletion (T4)
│   ├── LocalLibrary.kt          # grouping / sorting / search / merge
│   ├── LocalIndex.kt            # V1.2: pre-lowered search index (one lowercase pass)
│   ├── IncrementalScan.kt       # V1.2: (mtime, size) stamp reuse policy
│   └── LocalTrackMapper.kt      # entity <-> model
├── lyric/
│   ├── Lyric.kt                 # Lyric model + LrcParser
│   ├── LyricLoader.kt           # .lrc sibling + embedded lookup
│   ├── LyricEncoding.kt         # UTF-8 / GBK / Latin-1 detection
│   └── EmbeddedLyrics.kt        # ID3 / FLAC / Ogg / MP4 tag reader
├── net/
│   ├── HttpBridge.kt            # OkHttp wrapper
│   ├── ApkDownloader.kt         # V1.1.7: streaming download; V1.1.8: Range resume + digest check
│   └── UpdateChecker.kt         # V1.0.2: GitHub Releases latest -> UpdateInfo
├── update/
│   └── UpdateInstaller.kt       # V1.1.7: FileProvider -> system package installer
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
│   ├── QueueDrag.kt             # V1.2: QueueSummary header numbers + drag-to-reorder math
│   └── EqualizerController.kt   # audiofx wrapper (+ EqProfile replay on attach)
├── ui/
│   ├── LxApp.kt                 # V1.1.3: shell wiring only, 200 lines
│   ├── HomeChrome.kt            # V1.1.3: top bar / banner / mini player / stage body
│   ├── shell/LxShell.kt         # V1.1.3: stage slots + curtains
│   ├── shell/Route.kt           # V1.1.6: typed curtain stack (was 6 booleans)
│   ├── state/                   # V1.1.6: MediaLibraryStore, PlayerUiStore, StatsStore,
│   │                            #   UpdateStore, BackupStore (MainViewModel is a facade)
│   │                            # V1.2: + ScanReport (what the last scan skipped)
│   ├── MainViewModel.kt         # facade: forwards flows/methods to the stores
│   ├── CrashReportDialog.kt     # previous-run crash trace, copy/clear
│   ├── components/              # TrackRow, LyricView, RotatingArtwork, PlayerSkin, ArtworkColors, SearchBar, Hints
│   │                            # V1.2: + CoverTier (decode-size policy)
│   ├── neon/                    # Neon Vinyl design system: tokens, aurora, glass, controls, shell, NeonScreen scaffold
│   ├── screens/                 # Local, Library, GroupDetail, NowPlaying, Queue, Equalizer, Stats, FolderManager, Settings
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

### Browsing albums and artists (V1.1.6)
- Tapping an album or an artist opens its own page: large cover, artist, *N 首 ·
  总时长*, the track list with two-digit positions, and 播放全部 / 随机播放.
- Before V1.1.6 a whole album was one text row that played from the top when
  tapped — a record shelf rendered as a file folder. The grouping data
  (`MusicGroup.coverUrl`, `count`, `totalDuration`) was already there and
  unused.
- The detail page holds the **group key**, never the `MusicGroup` object:
  `recomputeLocal()` rebuilds the grouping on every search keystroke, so a
  captured object would go stale while the page is open.

### Browsing model (V1.1.6)
- Every full-screen page is a `Route` on a typed stack (`ui/shell/Route.kt`):
  `Settings`, `Folders`, `Stats`, `Queue`, `Equalizer`, `NowPlaying`, and
  `Group(key)` — the first one that carries an argument, which is why six
  independent booleans became a stack.
- `LxShell` still draws every curtain in a **fixed declared order** and only
  asks the stack whether a route is open, so layering stays predictable
  (settings is always above the queue) regardless of what the user opened first.
- Back closes the topmost *visible* curtain, not the last-opened route.

### Backup and restore (V1.1.6)
- Settings → **备份** → export writes a `.jjmbackup` (a zip whose single entry is
  `backup.json`) through the system file picker; no storage permission needed.
- Carries favourites, playlists, play history, play events and 16 *taste*
  settings (theme, EQ curve, lyric prefs). Deliberately **not** carried: the
  scanning folders (SAF grants do not survive a device move), `pendingDeletes`
  (another device's delete intent), `lastPlayedId` (may point at a file that is
  gone) and the update-check throttle.
- Restore **merges**, never replaces. Tracks are re-identified by a
  title + artist + album + duration fingerprint, because ids are
  `content://` URIs and change across devices; the report says how many were
  exact hits, how many were matched by fingerprint, and how many files are
  missing on this device.

### This year (V1.1.6)
- A 「这一年」 card on the statistics screen: plays, total hours, distinct
  tracks, most-played artist, the one track that got repeated most.
- It is a fact about the user, not a feature, so there is **no entry button** —
  it appears when the year has something to say and stays out of the way
  otherwise. Rendered above the histogram because it does not change with the
  今日 / 本周 / 全部 chips.

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

350 unit tests across 36 classes, all green (run `./gradlew :app:testDebugUnitTest`):

| Test class | Cases | Covers |
|---|---:|---|
| `local/LocalIndexTest` | 10 | **V1.2**: the index answers exactly what `LocalLibrary.search` answered for every query (compared case by case), blank queries return the sorted list by identity, sort order survives filtering, grouping runs off the indexed subset |
| `player/QueueDragTest` | 13 | **V1.2**: half a row commits a slot in *both* directions (`roundToInt` rounds -0.5 to 0, so the naive form made upward drags feel half a row late), the target clamps to the queue while the float offset keeps the whole distance, rows the drag passed shift one slot the other way, out-of-range start and zero row height are inert |
| `player/QueueSummaryTest` | 12 | **V1.2**: position is 1-based, `remaining` counts only tracks *after* the current one while `remainingSeconds` includes what is left of it, a stale position clamps to the current track's own length, minute rounding crosses into hours |
| `ui/components/CoverTierTest` | 9 | **V1.2**: tier boundaries at 72/200 dp, requested pixels round *up* (down makes Coil scale the bitmap back up on the last row), the cap is per tier, and a zero size never reaches Coil (0 means "original size" there) |
| `net/ApkDownloaderTest` | 16 | **V1.1.7**: `fileNameFor` refuses path traversal (`../../etc/app.apk` → `app.apk`) and always yields one `.apk`; `fraction` clamps over-run to 1f and returns 0f on an unknown total; `digestMatches` passes without a published digest, fails on a published digest with an unreadable file, and accepts bare hex as well as `sha256:`. **V1.1.8**: `canAppend` only resumes on `206` with bytes already on disk — a `200` (CDN ignored `Range`) or `416` (the `.part` is longer than the asset) restarts from zero |
| `data/backup/BackupCodecTest` | 15 | **V1.1.6**: fingerprint ignores case/whitespace/sub-second drift, exact id wins over fingerprint, duplicates take the first local track, `report` splits exact / matched / missing, JSON round-trip, unknown future version and garbage rejected, zip **and** bare-JSON containers |
| `data/stats/AnnualReportTest` | 10 | **V1.1.6**: year window is closed at both ends (future-stamped events from a restore cannot leak in), duration sums every play, blank singer collapses to 未知歌手, top artist/track tie-breaking, empty year returns a report instead of throwing |
| `ui/shell/RouteStackTest` | 11 | **V1.1.6**: reopening a route is a no-op, two group routes with different keys coexist, `close` works out of order, `closeTop` follows the draw order not the open order, group kind derived from the `album:`/`artist:` prefix |
| `lyric/EmbeddedLyricsTest` | 18 | ID3v2 USLT/TXXX, FLAC VorbisComment, Ogg/Opus, MP4 `©lyr`, GBK/UTF-8/BOM |
| `data/stats/PlayStatsTest` | 17 | day/week bucketing, ranking, timezone (pinned `Asia/Shanghai`) |
| `player/RandomShuffleStateTest` | 18 | **V1.0.1**: draw contract — next excludes the current track, prev excludes it too and additionally skips the track that just played, a draw is remembered until a track starts, `onTrackStarted` clears both, a 2-track queue can still step both ways |
| `local/LocalLibraryTest` | 15 | search, sort orders, album/artist grouping, multi-singer split |
| `player/RandomShuffleOrderTest` | 11 | media3 `ShuffleOrder` contract under **V1.0.1** draw semantics — previous always answers a legal in-range slot different from the current one, clones keep drawing from the same state |
| `player/QueueMirrorTest` | 17 | **V1.0.1**: the mirrored playlist's five steps run in one fixed order, `move`/`remove` drop the installed order *after* the player mutation, `replace` refuses a queue it cannot mirror |
| `player/QueueLogicTest` | 16 | queue move/remove bookkeeping — `nextIndex`/`prevIndex` were deleted in V1.1.3 with their tests, because production never called them; **V1.1.5** adds `removeAll`, which is what deleting a file needs (one delete can match several slots, and the only thing that matters afterwards is whether the *playing* slot went away) |
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
| `data/SettingsRoundTripTest` | 10 | every preference key is written and read back through the *same* key constant — the "equalizer does not survive a restart" class of bug cannot hide behind a default-only assertion; the V1.0.2 update token is checked the same way |
| `net/UpdateCheckerTest` | 15 | **V1.0.2**: release-tag version comparison (leading `v`, numeric segments, prerelease suffix ordering) and GitHub release JSON parsing (title fallback, graceful degradation on missing fields) — no network in unit tests. **V1.1.7** adds four cases pinning asset choice: the release build wins when both APKs are published, debug remains usable when it is all there is, `apkUrl`/`apkDigest` are read off the *chosen* asset, and a release without a digest leaves it null |
| `data/SettingsTest` | 4 | settings defaults (incl. flat EQ profile) / enum values |
| `local/IncrementalScanTest` | 8 | **V1.2**: a cached stamp is reused only when *both* `lastModified` and `size` match — trusting the timestamp alone keeps a stale duration after an in-place rewrite, and a provider reporting 0 means "no information", not "unchanged" |
| `ui/state/ScanReportTest` | 5 | **V1.2**: `hasRun` only after a real scan, `reusedFraction` survives a zero total, elapsed text switches to seconds past 1000 ms |
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

## V1.2.0 — Nothing that was already computed gets computed twice (`versionCode = 14`)

Four changes, one theme: the app was redoing work it had already done.

**Scanning reuses unchanged files.** The expensive part of a scan was never
walking the tree — it was `MediaMetadataRetriever` opening every file to read
tags and duration. The SAF listing already reports `lastModified` and `length`,
so `IncrementalScan` compares a `(mtime, size)` stamp and falls back to the
cached `MusicInfo` when both match. Both, not either: an in-place rewrite inside
the same second only changes the length, so trusting the timestamp keeps a stale
duration; and a provider reporting `0` means "no information", which is not the
same as "unchanged". The folder screen now ends a scan with
`共 128 首 · 用时 1.2 s · 跳过 121 首未改动` instead of silence.

**Search pre-lowers the library.** `LocalLibrary.search` lowercased three fields
of every track on every call, and the UI calls it on every keystroke — thousands
of string allocations per typed character, all of it recomputing text that never
changes between keystrokes. `LocalIndex` does the lowercasing once, when the
track list changes, and applies the sort in the same pass. The store caches it
behind the two values it was built from (reference equality on the list, value
equality on the sort enum), so a stale index is impossible rather than merely
unlikely. `LocalIndexTest` compares the index against the old filter for a spread
of queries rather than asserting on hand-picked strings — the whole risk here is
a silent behaviour change, and one test pins that.

**Covers decode at the size they are drawn.** Embedded pictures are routinely
1000×1000, which Coil decodes into a 4 MB bitmap — forty list rows of that is
160 MB of churn to fill 52 dp squares. Coil already infers a target size from the
layout constraints, but it cannot know the policy: that the full-bleed cover
behind the lyrics sits at 12 % alpha and does not need its resolution either.
`CoverTier` names three ceilings (160 / 320 / 1080 px) and `Cover()` asks for
`ceil(sizeDp × density)` clamped into its tier. Rounding up is deliberate —
rounding down makes Coil scale the decoded bitmap back up on the last row — and
`0` is never passed through, because a zero-size request means "original size".

**The queue says what is left, and can be reordered.** The header reads
`3/12 · 剩 34 分`; `QueueSummary` computes that from the queue plus the position
*inside* the current track, so the number shrinks while a song plays instead of
dropping in whole-track steps. It does not print a track count next to the
duration — the count excludes the current track while the duration includes its
remaining part, and putting them side by side reads as a contradiction. Rows can
also be dragged: long-press to pick one up, and the drop slot follows the finger
(half a row commits, in both directions).

---

## V1.1.8 — The APK loses four fifths of its weight, and a dropped download resumes (`versionCode = 13`)

### 15.15 MB → 3.38 MB

Measured before touching anything: dex was **13.66 MB of a 15.15 MB APK (90.2%)**,
across three dex files whose raw size summed to 48.37 MB. `minifyEnabled false`
plus `shrinkResources` off meant every bundled library shipped whole, including
the large parts no code path ever called.

Release builds now run R8 (`minifyReleaseWithR8`, `shrinkReleaseRes`,
`optimizeReleaseResources`) and land at **3,543,104 B — a 77.7% cut**. What is
left is one `classes.dex` (raw 5.36 MB), `resources.arsc` at 11.2% and `res` at
8.3% (607 entries, down from 877).

`proguard-rules.pro` opens with `-dontobfuscate` on purpose. The weight was
dead code, not long names — deleting it is enough. Renaming would additionally
have to satisfy four separate reflection contracts (Gson field names, Room's
generated `*_Impl`, media3's `SessionToken` carrying a `ComponentName`, Compose's
`$default` synthetics), and a mistake there is a crash on a user's phone, not a
failing test. Obfuscation stays for a release that can be regression-tested on
hardware.

What is kept explicitly: the JSON-backed models (their field names *are* the
`.jjmbackup` format — rename one and an old backup silently restores as nulls),
`AppDatabase`/`AppDatabase_Impl`, `PlaybackService`, `UpdateInstaller`, `LxApp`,
`MainActivity`, `FileProvider`, and media3's session/exoplayer entry points.

`_r8check.py` verifies this without a device: it dumps class descriptors out of
the packaged dex and asserts all eleven reflection-only classes survive, then
greps the dex string pool for `albumName` / `localDurationMs` / `apkDigest` /
`playedAt` / `trackIds` to prove the Gson protocol is intact.

### A cancelled download is now a resume point

`ApkDownloader.download` starts from whatever the `.part` file already holds and
sends `Range: bytes=$carried-`. The response is only appended when
`canAppend(statusCode, carried)` holds — `206` *and* something on disk. A `200`
means the CDN ignored the range and is resending the whole body; a `416` means
the `.part` is longer than the published asset (a stale file from an older
build). Both wipe and restart rather than splice, because a spliced APK fails at
install time and nowhere earlier.

`total` is `carried + declared`, since a `206` reports only the remaining
length; the download is only promoted to the final name once the byte count
matches.

`UpdateStore.purgeApkDir(keepName, keepPart)` now deletes only *other* versions'
files, so cancelling keeps this asset's `.part`. Cancelling a 15 MB download to
save 8 MB of cache was never the right trade.

### Upgrade note

Same package, same signing key — installs over 1.1.7 directly. The APK is
smaller, not different.

---

## V1.1.7 — The update installs itself, and the settings page stops explaining itself (`versionCode = 12`)

### Updating no longer leaves the app

The old update dialog had exactly one exit: `Intent.ACTION_VIEW` onto
`browser_download_url`. You got thrown into a browser and whether the APK ever
came back was your problem. The whole loop now stays inside the app.

`net/ApkDownloader.kt` streams the release asset to `cacheDir/updates/` through a
64 KB buffer, writing `<dest>.part` and renaming on success — an interrupted
download can never masquerade as an installable APK. It is deliberately *not*
`HttpBridge`: that one buffers the whole body into a `ByteArray`, which is fine
for a few KB of release JSON and fatal for a 16 MB installer.

Verification happens before the installer ever sees the file. GitHub publishes
each asset with `digest: "sha256:<hex>"`, so `parseRelease` now reads
`browser_download_url` and `digest` alongside the name and size; after the
download `ApkDownloader.sha256(file)` is compared and a mismatch **deletes the
file** instead of offering it. `digestMatches` has three branches on purpose: no
published digest passes (or that release could never install), a published
digest with an unreadable file fails (that is what a corrupted download looks
like), otherwise strict comparison.

`update/UpdateInstaller.kt` hands the file over through a `FileProvider`
(`${applicationId}.fileprovider` → `@xml/update_paths`, `<cache-path
name="updates" path="updates/" />`), because a `file://` URI crossing a package
boundary throws `FileUriExposedException` on API 24+.

### The debug APK was one click away

Found by actually calling the API. GitHub lists release assets in upload order,
and for v1.1.6 that puts `jijian-music-1.1.6-vc11-debug.apk` (23,038,164 B)
*before* `jijian-music-1.1.6-vc11.apk` (15,882,842 B). The original
"first `.apk`" parse would have downloaded the debug build — different signature,
guaranteed conflict against the installed release. `parseRelease` now collects
every `.apk` and prefers `firstOrNull { !it.name.contains("debug", true) }`.

### Two states that would have bitten later

`NEEDS_PERMISSION` — Android asks the user to allow installs from this app
(`Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES`). On return, `install.seq` has not
changed, so the `LaunchedEffect(install.seq)` driving the install does not re-run
and the dialog would fall back to "下载并安装" — re-downloading 16 MB to hit the
same wall. `InstallStatus.READY` now gets its own "去安装" button that replays the
local file.

`cancelInstall()` — resetting the `StateFlow` alone left the coroutine streaming
in the background, which then overwrote the state to `READY` and offered to
install the file the user had just refused. The store now holds `installJob`,
cancels it, purges the cache directory, and the download body bails with
`if (!isActive) return@launch` so a cancelled job cannot write a final state.

### Settings stopped explaining itself

Every explanatory subtitle under a settings toggle is gone — "同时决定首页「接着听」
有没有内容", "只影响「播放统计」页，与「最近播放」无关", and the rest. `SwitchRow` and
`InfoRow` take `subtitle: String? = null`, so call sites simply do not pass one.
The same treatment for the two lines in `TrackActionsSheet`.

What stayed: `"已授权 N 个文件夹 · 已手动添加 N 首"` (that is data, not prose) and
the non-IDLE states of `updateSubtitle` (that is a result, not prose).

---

## V1.1.6 — Albums become pages, pages become routes, and your data can leave the device (`versionCode = 11`)

### Albums and artists got a page

Until this version the library grouping was a dead end: `LocalScreen` rendered one
`GroupRow` per album and tapping it played the whole album from track one. A
record shelf that behaves like a file folder.

The grouping data was already complete — `MusicGroup.coverUrl`, `count`,
`totalSeconds` — it just had no screen to land on. `GroupDetailScreen` is that
screen: 132 dp cover, artist, *N 首 · 总时长*, a numbered track list, and two
actions (播放全部 / 随机播放). It serves albums and artists from one composable;
an artist page drops the redundant subtitle line.

The page holds the **group key**, not the `MusicGroup`: `recomputeLocal()`
rebuilds every group on each keystroke of the search box, so an object captured
at open time would silently show yesterday's track list.

### Six booleans became a route stack

`showSettings`, `showFolderManager`, `showStats`, `showQueue`, `showEqualizer`
and `showNowPlaying` lived half in `MainViewModel` and half in `LxApp`. That was
tolerable while every curtain was argument-free. An album page is the first one
that is not — "open the group whose key is X" is not a boolean.

`ui/shell/Route.kt` introduces a sealed `Route` (`Settings`, `Folders`, `Stats`,
`Queue`, `Equalizer`, `NowPlaying`, `Group(key)`) and a `RouteStack` over a
`StateFlow<List<Route>>`. `LxShell` keeps drawing every curtain in its declared
order and only asks the stack whether a route is open, so the **layering** stays
fixed (settings always above the queue) no matter the opening order, and Back
closes the topmost *visible* curtain rather than the last-opened route.

`navigation-compose` was declared in `libs.versions.toml` and never used once.
It is gone. A real `NavHost` would also have fought the shell: the stage renders
all curtains at once and animates the visible one in and out, which is not what
`NavHost` does.

### `MainViewModel` went from 887 lines to 508

It was a god object running scanning, playback, library, statistics, updates and
file deletion at once, and it reached for `LxApp.instance.settingsStore` instead
of taking its dependencies. It is now a facade over five stores:

| Store | Owns |
|---|---|
| `state/MediaLibraryStore` | scan, cache merge, grouping, favourites, playlists, delete bookkeeping |
| `state/PlayerUiStore` | wraps `PlayerController`, queue ops, equalizer persistence |
| `state/StatsStore` | range windows, tallies, this-year report |
| `state/UpdateStore` | GitHub release check + 24 h throttle |
| `state/BackupStore` | export / import through a `content://` uri |

`ui/StateAliases.kt` re-exports the moved state classes, so not one screen or
component had to change its imports.

### Backup and restore

Settings → **备份**. Export writes a `.jjmbackup` through the system picker; the
file is a zip whose single entry is `backup.json`, which leaves room for cover
art later without a format break. Restore reads that zip *or* a bare JSON file,
because a user who renamed the entry should not be locked out of their own data.

Ids are `content://` URIs (MediaStore) or SAF document URIs, so they change
across devices and even across a factory reset. Restore therefore re-identifies
tracks by a fingerprint of title + artist + album + duration, rounded to the
second — two rips of the same song routinely differ by a few milliseconds. The
report afterwards says *恢复 120 首（110 精确 / 10 指纹匹配），缺失 3 首*.

Restore is a **merge**: favourites, playlists (same name reuses the playlist, new
tracks append after `maxPosition`), history and play events are added, nothing is
deleted. Settings that travel are the 16 that describe taste. Settings that do
not: scanning folders (SAF grants do not survive a device move),
`pendingDeletes` (that is another device's delete intent), `lastPlayedId` (may
point at a file that is not here) and the update-check throttle.

### This year

The `play_event` table already kept a year of events; the statistics screen only
ever folded them into 今日 / 本周 / 全部. `PlayStats.annualReport` collapses them
into plays, total seconds, distinct tracks, most-played artist and the most
repeated single track.

It is shown as a card above the histogram — **no entry button**, because a year
is a fact about the listener, not a feature to open. It renders nothing until
the year has something to say.

One bug the tests caught: `annualReport` used to filter only `playedAt >=
yearStart`. A restore can bring in events stamped in the future, and those were
counting toward this year. The window is now closed at both ends.

### Dead code removed

- Room **v5 → v6**: `song_list` and `list_item` tables plus `SongListDao` — a
  leftover of the online-source era, zero call sites. `MIGRATION_5_6` drops them.
- `MusicInfo.qualitys` and `QualityInfo`: zero references. No migration needed —
  tracks live in the database as `musicJson`, and Gson ignores unknown keys.
- `navigation-compose` (see above) and the empty `docs/adr/` directory.

### Tests

237 → **273 across 29 classes**, all green. The three new classes are
`BackupCodecTest` (15), `RouteStackTest` (11) and `AnnualReportTest` (10).
`BackupCodecTest` runs entirely on the JVM: no `Uri`, no `ContentResolver`, so
the matching rules are testable without an emulator.

---

## V1.1.5 — Missing covers are visible, and deleting a file no longer leaves ghosts (`versionCode = 10`)

### Finding tracks without artwork

`LocalMusicScanner` used to hand every track a
`content://media/external/audio/albumart/<albumId>` URI without checking whether
that album had any artwork. So `img` was never null, "does this track have a
cover" was unanswerable, and every miss paid for a Coil decode that ended in an
empty grey box.

The scanner now probes the URI first (`openFileDescriptor`) and stores `null`
when nothing is there. `Cover()` renders a `MusicNote` placeholder on error
instead of a blank rectangle, so a missing cover looks deliberate rather than
broken.

### Deleting a file

Four defects, all in the same path:

1. **The queue was not told.** The slot stayed, pointing at a URI that no longer
   resolved, so the next "next" press landed on a ghost and reported an error.
   `PlayerController.forgetTrack(id)` now removes every matching slot; if the
   playing track was the one deleted, playback stops rather than silently
   starting a neighbour.
2. **The pending-delete set was in memory.** MediaStore deletion needs a system
   confirmation dialog, and a process killed while that dialog is up produced
   "file gone, database row still there". The set is now persisted
   (`pending_deletes`).
3. **Reconciliation was never called.** `reconcilePendingDeletes()` existed but
   had no caller. It now runs from `scanLocal()`.
4. **Cached covers never expired.** The SAF scanner writes
   `cacheDir/covers/<id>.jpg` once; nothing removed it.
   `LocalFileDeleter.dropCachedCover()` deletes it with the track, and only
   touches files under our own cache directory.

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
