# SHINY performance audit — handoff (in progress)

Started 21 September 2026. **This is an INSPECT → MEASURE → REPORT pass. No app code may be
changed, committed or pushed.** The only file this work has written is this one.

`PERF_HANDOFF.md` holds the older (14 September) optimisation work and its measurements; this
file holds the new full audit the user asked for. Neither supersedes the other.

---

## 0. STATUS — the audit is COMPLETE

**Finished 21 September 2026. The final report and score are in section 18: 79 / 100.**
No app code was changed, committed or pushed; this file is still the only thing this work wrote.

One item was **not** delivered, and it is not deliverable from here: **user journeys A–E.** The
prompt's own definitions of A–E are recorded nowhere in this repo and were not available in
either session. The flows actually exercised are measured in sections 10 and 11. If you still
want A–E specifically, restate them and they can be driven with the scripts in sections 6 and 17.

**Before quoting any number from sections 1–7, read section 8 first.** Session 2 changed or
corrected several things:

- The AVD is **`emulator-5556`** now, not `-5554` (BlueStacks had squatted that serial).
- **Section 2's 3,307 ms cold start did not reproduce** — the same APK starts in **800 ms** on a
  quiescent host (section 9). Do not score startup off the old number.
- The jank% column in section 10 is contaminated by an emulator GPU stall; rank scrolling by the
  **slow-UI-thread rate** table instead.
- `uiautomator dump` fails silently and serves a stale file; the Liquid Player is invisible to it.

The single strongest code finding of the whole audit is **section 12**
(`LiquidLivingArtwork.kt` — an unthrottled 60 fps frame loop that rebuilds a `RenderEffect` every
frame, next to a background loop that is deliberately capped at 25 fps for exactly this reason).

---

## 1. Environment (verified 21 September)

| Thing | Value |
|---|---|
| Host | Windows 11, Git Bash. **Shell `JAVA_HOME` is broken** — always `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` |
| Emulator | AVD `ShinyStartup`, `emulator-5554`, Android 16 / API 36, x86_64 |
| Display | 1080x2400, density 420, **`mSupportedRefreshRates=[60.000004]` — 60 Hz ONLY** |
| CPU / RAM | 4 cores, 2,971 MB total, ~150 MB free |
| GPU | `Pipeline=Skia (OpenGL)`, host GL |
| APK under test | `app/build/outputs/apk/x86_64Gms/benchmarkRelease/app-x86_64-gms-benchmarkRelease.apk`, **40,031,985 B**, built 21 Sep 00:18 from the current working tree (34 modified + 14 untracked files, uncommitted) |
| Build cmd | `./gradlew :app:assembleX86_64GmsBenchmarkRelease :baselineprofile:assembleX86_64GmsBenchmarkRelease "-Dorg.gradle.jvmargs=-Xmx5g -XX:MaxMetaspaceSize=1g" -Pkotlin.compiler.execution.strategy=in-process --no-daemon` → BUILD SUCCESSFUL in 8m 37s |
| Installed | app + `baselineprofile-x86_64-gms-benchmarkRelease.apk` (test APK from 19 Sep, unchanged) |

`benchmarkRelease` = R8-minified, resource-shrunk, debug-signed, **with** the baseline profile.

---

## 2. Startup — MEASURED

`am start -W` `TotalTime`, 15 iterations per mode, minified + baseline profile.
Macrobenchmark's `StartupBenchmark` could **not** be used — see section 4.1.

| Mode | n | min | **median** | p90 | p95 | max | mean |
|---|---|---|---|---|---|---|---|
| Cold (`am force-stop` each iter) | 15 | 2,463 | **3,307** | 4,450 | 4,450 | 4,489 | 3,408 |
| Warm (`always_finish_activities 1`, same pid) | 15 | 306 | **539** | 1,111 | 1,111 | 1,526 | 623 |
| Warm, excl. iteration 1 | 14 | 306 | **384** | 1,051 | 1,051 | 1,111 | 558 |
| Hot (HOME then relaunch) | 14 | 374 | **501** | 580 | 580 | 592 | 485 |

Raw cold values (ms): 3307, 3330, 3081, 3784, 3320, 3110, 4450, 3777, 4489, 3264, 3883, 3199,
2463, 2569, 3088.
Raw warm: 1526, 563, 715, 715, 663, 539, 1111, 384, 1051, 357, 381, 365, 306, 343, 324.
Raw hot: 428, 399, 395, 398, 374, 515, 504, 580, 592, 501, 502, 562, 547, 493.

- Warm kept **one pid (12880) across all 15 iterations** — the activity really was recreated with
  the process alive, so the warm/hot split is honest.
- Hot iteration 6 returned no `TotalTime` (activity already resumed); excluded, n=14.
- `always_finish_activities` was set back to `0` after the run. If anything looks odd later,
  check: `adb shell settings get global always_finish_activities`.
- For comparison, 14 Sep macrobenchmark `timeToInitialDisplayMs` on the same emulator ranged
  1,638–4,021 ms median **between sessions** for identical APKs. Emulator run-to-run drift is
  ~10%; do not read small differences as real.

---

## 3. Database — MEASURED (synthetic, EXPLAIN QUERY PLAN + timings)

Method: real schema from `core/schemas/com.shiny.music.db.InternalDatabase/45.json`, populated to
heavy-listener scale (song 6,000 / event 60,000 / playCount 36,537 / related_song_map 30,000 /
9.2 MB file), `ANALYZE`, medians of 7 runs. Script in section 6.4.

### Indexes as shipped — mostly correct
Room **does** auto-create FK indexes; an early suspicion of missing junction-table indexes was
**wrong**. Real state: `song(albumId)`, both columns of every map table, `event(songId)`,
`search_history(query)`, `recognition_history(trackId)`. `playCount` PK `(song, year, month)`
already serves `WHERE song=? AND year=? AND month=?`.

### Measured query cost (current schema)

| Query | median |
|---|---|
| `SUM(playTime) FROM event WHERE timestamp BETWEEN` | 1.93 ms (SCAN event) |
| `COUNT(DISTINCT songId) ... BETWEEN` | 8.14 ms (SCAN event + temp B-tree) |
| `COUNT(DISTINCT artistId)` join `song_artist_map` | **26.16 ms** |
| `COUNT(DISTINCT albumId)` join `song` | **20.62 ms** |
| Home Quick Picks (`related_song_map` GROUP BY + 3-way UNION) | **20.83 ms** |
| `mostPlayedSongs` (event GROUP BY → join song) | **18.28 ms** |
| Library songs by playtime | 0.67 ms (SCAN song + temp B-tree ORDER BY) |

### The obvious fix does NOT work — measured, not assumed
Adding `CREATE INDEX ON event(timestamp)` makes the range queries **slower or unchanged**:

| Query | no index | with `event(timestamp)` |
|---|---|---|
| `SUM(playTime)` range | 1.93 ms | **4.37 ms** (worse) |
| `COUNT(DISTINCT songId)` range | 8.14 ms | **11.21 ms** (worse) |
| `COUNT(DISTINCT albumId)` range | 20.62 ms | **35.32 ms** (worse) |

Forcing it with `INDEXED BY` confirms it; cost tracks rows-in-range, not plan:

| Range | rows in range | SUM no-idx | SUM idx | DISTINCT no-idx | DISTINCT idx |
|---|---|---|---|---|---|
| week | 411 | 0.10 | 0.10 | 0.21 | 0.20 |
| month | 1,663 | 0.39 | 0.35 | 0.97 | 0.96 |
| year | 19,960 | 4.20 | 4.21 | 11.25 | 10.95 |
| all-time | 60,000 | 12.65 | 12.50 | 29.45 | 29.84 |

**Conclusion:** a plain `timestamp` index is worthless here — the queries read `songId`/`playTime`
off the row, so an index seek just adds a row lookup per hit. The real levers, still untested, are
a **covering index `event(timestamp, songId, playTime)`** and/or pre-aggregation. Week/month
ranges are already fast (<1 ms); only year/all-time views are expensive.

**One genuine index win measured:** `song(totalPlayTime DESC)` took the Library "songs by
playtime" query 0.67 → **0.17 ms** (removes the temp B-tree sort).

---

## 4. Blockers and caveats found while measuring

### 4.1 Macrobenchmark cold-start is blocked on this emulator
`StartupBenchmark.coldStart` ran iterations 0–4 then failed both parameterisations with
`IllegalStateException: Package com.shiny.music must not be running prior to cold start!`
(373 s, 2/2 failures). Traces for completed iterations are on the device under
`/storage/emulated/0/Android/media/com.shiny.music.baselineprofile/`.

Investigated: the process does **not** resurrect on its own — after `am force-stop` it stayed dead
for 6 s (`pidof` empty each second) and `dumpsys activity services com.shiny.music` was empty.
Prime suspect is a **widget provider** waking the process when `pressHome()` shows the launcher:
`dumpsys appwidget` lists `MusicWidgetReceiver`, `TurntableWidgetReceiver`,
`PlaylistWidgetReceiver`, `MusicRecognizerWidgetReceiver`. **NOT yet confirmed** — confirm before
reporting it as an app defect. The `am start -W` route in section 2 sidesteps it.

### 4.2 Compose compiler metrics are unavailable this pass
They need `composeCompiler { metricsDestination = ... }` or compiler args in
`app/build.gradle.kts`. That is a build-file change, which this pass forbids. Compose findings
must therefore be static-analysis + trace based, and must be labelled as such.

### 4.3 Emulator frame timings are NOT phone frame timings — do not quote them as SHINY's
Pilot capture, Home, 3 scroll passes, no warm-up:
`Total frames 70, Janky 57 (81.43%), p50 73 ms, p90 200 ms, p95 350 ms, Slow UI thread 45`,
**`GPU HISTOGRAM ... 4950ms=34`** — 34 frames pinned in the top GPU bucket. That is the host-GL
emulator stalling, not the app. The display is also 60 Hz only.

Therefore:
- **60 Hz** budget violations: measurable, but inflated by the emulator.
- **90 Hz / 120 Hz**: **N/A as a direct measurement** — the panel never runs at those rates. At
  most report what fraction of frames already fit inside 11.11 / 8.33 ms as an indicative proxy,
  clearly labelled as such.
- Emulator numbers are trustworthy only **comparatively** (screen A vs screen B, same session).
- Anything conclusive about real frame pacing needs the arm64 APK on a physical phone:
  `./gradlew :app:assembleArm64GmsBenchmarkRelease -Pshiny.benchmark.abi=arm64`.

### 4.4 App state on the emulator
The installed app has real data: Home shows "26 songs by 27 artists this week, 1 h 30 min",
a restored queue ("No Meio do Luar"), Lately shelf, Set the Mood, Your Week. Good for realistic
scrolling; the Library is small, so **Library scroll results will understate a large library**.

---

## 5. Code-audit findings so far (evidence, not yet ranked)

### 5.1 Scale
app 363 files / 99,938 lines; core 67 / 5,819; innertube 103 / 8,336; playback 20 / 2,691;
lyrics 15 / 2,230. Biggest files: `MusicService.kt` 4,892; `PlayerMenu.kt` 2,005; `Items.kt`
1,930; `DatabaseDao.kt` 1,871; `YouTube.kt` 1,759; `MainActivity.kt` 1,753. UI is 55,524 lines,
of which `ui/liquid` is 22,285.

### 5.2 Glass/backdrop system — already well optimised, verify before touching
`ui/component/backdrop/` (2,681 lines) is Kyant0/backdrop v2.0.0 vendored, **with a local
addition**: a backdrop resolution scale. `DrawBackdropModifier.kt:292-345` records the backdrop
layer downscaled and stretches it back, so the RenderEffect chain covers fewer pixels.
`LiquidGlass.kt:156-160` already picks it by blur radius — `blur >= 16.dp → 0.33`,
`>= 8.dp → 0.5`, else `1f`; `ScrollEdgeEffect` (`LiquidGlass.kt:298`) hard-codes `0.33f`.
**The cheap "render the blur at lower resolution" win the prompt asks for is already taken.**
Do not present it as a new recommendation.

Real remaining cost: `DrawBackdropNode.draw()` (`DrawBackdropModifier.kt:360-380`) calls
`drawBackdropLayer()` → `recordLayer(...)` **every frame, per glass surface**, and
`layoutCoordinates` uses `neverEqualPolicy()` so every `onGloballyPositioned` invalidates the
draw. Correct for glass over moving content; wasted for glass over static content.
`exportedBackdrop` records a **second** layer when set.
Shell glass surfaces: `LiquidChrome.kt:285, 378, 417` (all `GlassKind.Regular`) + mini player.
Count what is actually on screen at once before proposing anything.

Fallback path exists and is clean: `GlassSupported` / `LocalGlassEffectConfig.glassAvailable`
false → solid translucent fill + hairline rim (`LiquidGlass.kt:143-151`).

### 5.3 Main-thread blocking candidates (located, cost NOT yet measured)
- `core/utils/DataStore.kt:17,25` — `operator fun get` / `get(key, default)` are
  `runBlocking(Dispatchers.IO) { data.first() }`. Non-suspend signature, so any caller on the main
  thread blocks. `PreferencesSnapshot` exists to avoid this for composables.
- `MusicService.kt` reads settings through that operator at lines 569, 780, 783, 784, 803, 804,
  806, 1763-4, 1911, 2009, 2097, 2163, 2563, 2800, 3030, 3041 — service main thread.
  `MusicService.kt:601` `runBlocking(Dispatchers.IO) { dataStore.data.first() }` is the seeding read.
- `MainActivity.kt:419` `dataStore.get(StopMusicOnTaskClearKey, false)`;
  `MainActivity.kt:1596` `dataStore.get(ListenTogetherUsernameKey, "")`.
- `MediaLibrarySessionCallback.kt:378-9, 499-500, 694-5` — up to 3 blocking reads per item filter.
- `core/extensions/ContextExt.kt:16,22` — `isSyncEnabled()` / `isUserLoggedIn()` wrap `runBlocking`.
- `DatabaseDao.kt:1248` `incrementPlayCount` — `runBlocking { ... .first() }` inside a DAO method.
- `MusicService.kt:3151, 3228, 3279` — `runBlocking(Dispatchers.IO)` in the data-source factory
  (likely the ExoPlayer loader thread, **verify** before classifying).
- 38 `runBlocking` occurrences total across app/core/playback/innertube.

**Still to do:** prove which actually execute on the main thread, and time them.

### 5.4 App startup shape (good)
`App.onCreate` (`App.kt:89-124`) keeps work off the main thread: `PreferencesSnapshot.start`,
then `applicationScope.launch(Dispatchers.IO)` for the Coil cache size and `initializeSettings()`.
On the main thread: `CrashHandler.install`, `AppContextHolder.initialize`,
`CipherDeobfuscator.initialize` — cost unmeasured. `App.kt:292` has one `runBlocking` (Coil cache
size fallback).

### 5.5 Not yet examined
Coil/artwork config and bitmap sizes, `HomeFeedBuilder`, `LiquidNowPlaying` / `LiquidLyrics`,
LazyColumn keys and `contentType`, `Items.kt`, network request counts at startup, R8 /
resource-shrink effectiveness, APK composition, baseline-profile coverage of the Liquid screens,
`listentogether` (4,161 lines) and `spotify` (2,588 lines) background cost.

---

## 6. Scripts (inline — the session scratchpad does not survive a restart)

### 6.1 Startup — `startup.sh <cold|warm|hot> [n]`

```bash
#!/bin/bash
export PATH="$PATH:/c/Users/Lenovo/AppData/Local/Android/Sdk/platform-tools"
export MSYS_NO_PATHCONV=1
PKG=com.shiny.music; ACT=$PKG/com.shiny.music.MainActivity; N=${2:-15}; mode=$1
adb shell pm grant $PKG android.permission.POST_NOTIFICATIONS >/dev/null 2>&1
adb shell am start -W -n $ACT >/dev/null 2>&1; sleep 4
case $mode in
  warm) adb shell settings put global always_finish_activities 1 >/dev/null 2>&1 ;;
  *)    adb shell settings put global always_finish_activities 0 >/dev/null 2>&1 ;;
esac
for i in $(seq 1 $N); do
  case $mode in
    cold) adb shell am force-stop $PKG; sleep 2 ;;
    *)    adb shell input keyevent KEYCODE_HOME; sleep 2 ;;
  esac
  r=$(adb shell am start -W -n $ACT 2>/dev/null | tr -d '\r')
  echo "$mode iter$i TotalTime=$(echo "$r" | grep '^TotalTime:' | awk '{print $2}') pid=$(adb shell pidof $PKG | tr -d '\r')"
  sleep 3
done
adb shell settings put global always_finish_activities 0 >/dev/null 2>&1   # ALWAYS restore
```

### 6.2 Frames — `frames.sh <label> [passes]`

```bash
#!/bin/bash
export PATH="$PATH:/c/Users/Lenovo/AppData/Local/Android/Sdk/platform-tools"
export MSYS_NO_PATHCONV=1
PKG=com.shiny.music; label=$1; passes=${2:-6}
for i in 1 2; do adb shell input swipe 540 1700 540 700 300; sleep 1
                 adb shell input swipe 540 700 540 1700 300; sleep 1; done   # warm-up
adb shell dumpsys gfxinfo $PKG reset >/dev/null 2>&1
for i in $(seq 1 $passes); do
  adb shell input swipe 540 1700 540 700 250; sleep 1
  adb shell input swipe 540 700 540 1700 250; sleep 1
done
adb shell dumpsys gfxinfo $PKG > "gfx_$label.txt"
```

Tab-bar taps (1080x2400): Home `180 2303`, New `445 2303`, Library `713 2303`,
Search `961 2303`, mini player (opens Liquid Player) `480 2130`.

### 6.3 `parse.py` — gfxinfo → jank table

```python
import re, glob, os
hdr = "{:<22}{:>7}{:>8}{:>7}{:>6}{:>7}{:>7}{:>7}{:>7}{:>7}{:>7}"
print(hdr.format("screen","frames","janky","jank%","p50","p90","p95",">8.33",">11.1",">16.7","slowUI"))
for f in sorted(glob.glob("gfx_*.txt")):
    t = open(f, encoding="utf8", errors="ignore").read()
    name = os.path.basename(f)[4:-4]
    def g(p, d="0"):
        m = re.search(p, t)
        return m.group(1) if m else d
    total = int(g(r"Total frames rendered: (\d+)"))
    janky = int(g(r"Janky frames: (\d+)"))
    o = [0, 0, 0]
    h = re.search(r"\nHISTOGRAM: (.+)", t)
    if h:
        for pair in h.group(1).split():
            ms, cnt = pair.split("ms=")
            ms, cnt = int(ms), int(cnt)
            if ms > 8:  o[0] += cnt
            if ms > 11: o[1] += cnt
            if ms > 16: o[2] += cnt
    pc = lambda x: 100.0 * x / max(total, 1)
    print(hdr.format(name, total, janky, "{:.1f}%".format(pc(janky)),
          g(r"\n50th percentile: (\d+)ms") + "ms",
          g(r"\n90th percentile: (\d+)ms") + "ms",
          g(r"\n95th percentile: (\d+)ms") + "ms",
          "{:.0f}%".format(pc(o[0])), "{:.0f}%".format(pc(o[1])), "{:.0f}%".format(pc(o[2])),
          g(r"Number Slow UI thread: (\d+)")))
```

Note: the histogram buckets are integer ms, so ">8.33 ms" is counted as bucket > 8 ms — a slight
over-count. State that when reporting.

### 6.4 `dbbench.py` — schema → synthetic DB → EXPLAIN QUERY PLAN + timings

Rebuild `schema.sql` first (run from the repo root):

```bash
python -c "
import json
d = json.load(open('core/schemas/com.shiny.music.db.InternalDatabase/45.json'))
keep = ('song','event','song_artist_map','playCount','related_song_map','song_album_map')
for e in d['database']['entities']:
    if e['tableName'] in keep:
        print(e['createSql'].replace(chr(36)+'{TABLE_NAME}', e['tableName']) + ';')
        for i in e.get('indices', []):
            print(i['createSql'].replace(chr(36)+'{TABLE_NAME}', e['tableName']) + ';')
" > schema.sql
```

Then populate `song 6000 / song_artist_map 9000 / song_album_map 6000 / event 60000 /
related_song_map 30000 / playCount ~36.5k`, run `ANALYZE`, and time the section-3 queries with
`EXPLAIN QUERY PLAN`. Seeds used: `random.seed(7)`, `NOW = 1758400000000`, medians of 7 runs.

---

## 7. Rules for this audit (from the user, verbatim intent)

- **No code changes, no commits, no pushes, no dependency downgrades.**
- **Never** propose removing blur, glass, animation, artwork effects or features. The goal is
  *same visual, same features, less computation*.
- Collect evidence first; the score comes **last** and only from measurements.
- Where a metric could not be measured, write **N/A** — do not estimate it.
- Do not assume something is slow because it looks expensive; find the actual hot paths.
- Final score weights: Startup 20, Scrolling 20, Animation 20, Playback 15, Memory 10,
  I/O+DB 10, Network 5.

---

## 8. Session 2 (21 September, later) — environment changes

- **The AVD is now `emulator-5556`, not `-5554`.** BlueStacks (`HD-Player.exe`) was running at the
  start of this session and had taken the `emulator-5554` serial, with a spoofed
  `ro.product.model=2107113SI` (Xiaomi 11T Pro), Android 9, and a **closed adb shell**
  (`error: closed` on every command). It is not the AVD and must not be measured. `ShinyStartup`
  booted alongside it on 5556. BlueStacks exited on its own later in the session.
  **Always confirm `getprop ro.build.version.sdk` = 36 before trusting a capture.**
- Everything else matches section 1: API 36, x86_64, 1080x2400 @ 420,
  `mSupportedRefreshRates=[60.000004]`, same APK (`versionName=1.2.4`,
  `lastUpdateTime=2026-09-21 00:18:16`), `always_finish_activities=0`.
- Device **does** have working internet: ICMP echo is dropped (`ping` shows 100% loss, misleading)
  but DNS resolves and TCP/443 connects. Do not conclude "offline" from `ping`.
- `uiautomator dump` **fails silently** on this build and leaves the *previous* dump in
  `/sdcard/ui.xml`. Two captures in this session were mislabelled before this was caught.
  Always `rm -f /sdcard/ui.xml` first and check the command printed `UI hierchary dumped`.
  The Liquid Player sheet does **not** appear in the dump at all — it exposes only the Home tree
  underneath, so the player must be driven from screenshots, not the view tree.
- `adb root` is refused (production image), so `drop_caches` is unavailable.

---

## 9. Startup — RE-MEASURED, and section 2 does not reproduce

Same APK, same AVD, same `am start -W` method, host otherwise quiescent.

| Compilation state | n | min | **median** | max | mean |
|---|---|---|---|---|---|
| `speed-profile` (as shipped, baseline profile applied) | 8 | 726 | **800** | 890 | 812 |
| `verify` (simulates freshly installed, no AOT) | 6 | 819 | **883** | 1,492 | 974 |
| `speed-profile` again (restored) | 4 | 806 | **870** | 987 | 883 |

Raw speed-profile: 838, 800, 890, 796, 795, 726, 796, 851.
Raw verify: 1492, 926, 862, 883, 859, 819.
Every iteration produced a **new pid**, so each really was process-cold.

**Section 2's cold median of 3,307 ms did not reproduce — today the same APK starts in 800 ms.**
This is a 4x gap, far outside the ~10% run-to-run drift section 2 allowed for. Investigated:

- **Not AOT compilation.** Forcing the just-installed state (`pm compile -m verify -f`) costs only
  ~80 ms of median (883 vs 800). Only the *first* post-reset launch was slow (1,492 ms). The
  baseline profile is worth little here; it is not what separates 800 from 3,307.
- **Not page cache** — could not be tested directly (`adb root` refused), but `verify` above
  already re-reads the dex, and it did not reproduce 3,307 either.
- **Most likely host contention.** Section 2 was measured minutes after an 8m37s Gradle build and a
  40 MB install, and BlueStacks was found running on this host at the start of this session.
  Unproven, so stated as the likeliest cause, not a fact.

**How to read this:** 800 ms is the honest steady-state process-cold start for this APK on this
emulator. 3,307 ms is what the same APK did on a loaded host. Neither is a phone number.
`pm compile` state was restored to `speed-profile` at the end of the run — verified.

---

## 10. Frame / jank per screen — MEASURED

Protocol as section 6.2: warm-up scroll x2, `gfxinfo reset`, 6 fling passes (1000 px, 250 ms),
dump. Nothing was playing for the six list screens. **Read section 4.3 before quoting these.**

`gpuSTALL` = frames parked in the emulator's top GPU bucket (`4950ms=`). That is the host-GL
stall of section 4.3, **not** the app, and it is a large share of every "janky" count below.

| screen | frames | janky | jank% | p50 | p90 | slowUI | slowDraw | gpuSTALL |
|---|---|---|---|---|---|---|---|---|
| home | 263 | 109 | 41.4% | 44 ms | 73 ms | 91 | 108 | 48 |
| new | 517 | 138 | 26.7% | 36 ms | 57 ms | 107 | 133 | 79 |
| library | 392 | 86 | 21.9% | 34 ms | 53 ms | 68 | 79 | 59 |
| search (browse landing) | 517 | 102 | 19.7% | 34 ms | 48 ms | 79 | 96 | 91 |
| search (remote results) | 404 | 164 | 40.6% | 46 ms | 65 ms | 137 | 162 | 92 |
| settings | 427 | 112 | 26.2% | 32 ms | 46 ms | 79 | 104 | 71 |
| listening stats | 483 | 119 | 24.6% | 32 ms | 48 ms | 92 | 113 | 75 |
| player, queue mode, scrolling | 553 | 272 | 49.2% | 25 ms | 53 ms | 45 | 270 | 12 |

**The jank% column is contaminated and should not be quoted on its own.** `Number Slow UI thread`
normalised by frames is CPU-side and survives the emulator's GPU stalls — that is the ranking to
use:

| screen | slow-UI-thread rate |
|---|---|
| **home** | **34.6%** |
| **search (remote results)** | **33.9%** |
| new | 20.7% |
| listening stats | 19.0% |
| settings | 18.5% |
| library | 17.3% |
| search (browse landing) | 15.3% |
| player, queue mode, scrolling | 8.1% |

**Home and remote Search results are ~2x every other screen on UI-thread-bound frames.** Those are
the two scrolling screens worth attention; Library / Settings / Stats / Search-landing are fine.
Caveat from 4.4 still applies: the Library on this device is small, so its result understates a
large library.

---

## 11. The Liquid Player — MEASURED (frames + CPU)

Frames are idle captures: **no input at all**, so every frame is the app animating by itself.
CPU is process-wide jiffies (`utime+stime`, HZ=100) over 12-15 s, as % of one core.

| state | frames | ~fps | jank | CPU total | RenderThread | main thread |
|---|---|---|---|---|---|---|
| Home, idle, **music playing** | **0** | 0 | — | 9.5% | absent | 0.3% |
| app backgrounded, music playing | — | — | — | 10.5% | absent | 0.7% |
| player, Stage, **paused** | **0** | 0 | — | **0.5%** | absent | 0.2% |
| player, Queue mode, playing, idle | 467 | ~23 | 84.6% | 25.6% | 12.5% | 2.8% |
| player, Lyrics, no lyrics for track | 567 | ~28 | 70.5% | — | — | — |
| player, **Stage, playing** | 1209 | **~60** | 0.0% | **35.3%** | 20.7% | 4.2% |
| player, **Lyrics, synced ticker** | 995 | **~60** | 0.1% | **42.7%** | 24.5% | **7.7%** |

Audio alone (backgrounded) is ~10.5%: ExoPlayer 4.1 + MediaCodec 3.3 + binder/misc. Subtracting it,
the **UI** costs roughly 15% (Queue), 25% (Stage), 32% (Lyrics with a ticker) of one core.

### What this says

1. **Idle behaviour is excellent.** Home renders **literally zero frames** in 15 s while music
   plays — the mini player does not repaint. A paused Stage renders zero frames and costs 0.5%.
   Backgrounded playback never touches the RenderThread. There is no always-on repaint anywhere.
2. **Lyrics with a live ticker is the most expensive screen in the app** — 42.7% of a core, and the
   highest main-thread cost measured (7.7%). The ticker takes the Lyrics pane from ~28 fps to a
   sustained 60 fps. This confirms, for the *new* player, what
   `shiny-repaints-at-60hz-while-playing` recorded for the retired one.
3. **Stage vs Queue is +9.7% CPU and +8.2% RenderThread**, entirely from the living artwork.
4. **Stage and Lyrics never miss a deadline** (0 and 1 janky frames out of 1209 and 995) even at a
   sustained 60 fps. They are expensive, not janky.
5. The 70-85% "jank" on the **idle** Queue/Lyrics captures is an artifact, not a user-visible
   stutter: with no input, frames arrive from the ~25 fps drift loop at 17-18 ms latency, just over
   the 16.67 ms deadline. Note `Slow UI thread` = 0-1 but `Slow issue draw commands` = ~400/400 —
   i.e. **every** frame is draw-command-bound. That is the drifting backdrop forcing the window and
   every glass surface over it to re-issue draw commands.

---

## 12. The one clear per-frame finding — `LiquidLivingArtwork.kt`

This is the strongest code-level result of the audit, and it is **not** "remove an effect".

`app/.../ui/liquid/player/LiquidLivingArtwork.kt:81-89` drives the artwork's breathe/drift/ripple
clock from an **unthrottled** frame loop:

```kotlin
LaunchedEffect(running) {
    if (!running) return@LaunchedEffect
    var last = withFrameNanos { it }
    while (isActive) {
        val now = withFrameNanos { it }
        clock.floatValue = (clock.floatValue + (now - last) / 1_000_000_000f) % 3600f
        last = now
    }
}
```

Every tick invalidates `livingMotion` (same file, ~line 124), which on API 33+ does:

```kotlin
renderEffect = LiquidArtShader.effect(size.width, size.height, t)
```

and `LiquidArtShader.effect` (same file, **lines 185-189**) **allocates a new `RenderEffect` on
every call**:

```kotlin
fun effect(width: Float, height: Float, time: Float): RenderEffect {
    shader.setFloatUniform("size", width, height)
    shader.setFloatUniform("time", time)
    return RenderEffect.createRuntimeShaderEffect(shader, "image").asComposeRenderEffect()
}
```

So the Stage does a full-artwork RuntimeShader pass **plus two allocations, 60 times a second**.

**Why this is actionable and safe:** 100 lines away, in `LiquidNowPlaying.kt`
(`NowPlayingBackground`), the *background* field's drift is deliberately stepped with `delay(40)`
to ~25 fps, and the code comments say exactly why:

> *"The field is a heavy blur, so its slow turn is stepped at ~25 fps: the steps are invisible, and
> the window (and every glass button refracting it) redraws at less than half the rate a per-frame
> animation would force."*

The artwork loop never got the same treatment. Its motion periods are **17 s, 23 s and 29 s**
(`livingMotion`), so stepping its clock to ~30 fps is invisible by the same argument the codebase
already makes — and it halves the shader passes and the per-frame allocations. **Same visual, same
feature, less computation**, which is exactly what section 7 asks for.

Measured headroom this targets: the 20.7% RenderThread / 35.3% total on the Stage, and the
24.5% / 42.7% on Lyrics. **Not yet proven by experiment** — proving it needs a code change, which
this pass forbids. Flag it as the first thing to measure in the follow-up.

---

## 13. Memory — MEASURED, no leak

`dumpsys meminfo`, KB, music playing throughout. One cycle = New → Library → Search → Home.

| point | PSS TOTAL | Java heap | Native heap |
|---|---|---|---|
| baseline (Home) | 216,764 | 48,520 | 108,324 |
| after 6 cycles | 224,790 | 53,364 | 107,204 |
| after 12 cycles | 227,074 | 44,808 | 116,844 |
| after `trim-memory RUNNING_CRITICAL` | 217,164 | 40,488 | 112,940 |
| after 10x player open/close | 218,070 | 42,100 | 111,128 |
| after trim again | **210,328** | **34,316** | 111,120 |

- 12 navigation cycles grow PSS by **+10.3 MB**, and a trim gives **all of it back** (217,164 vs a
  216,764 baseline — +0.4 MB, noise). That is bounded, reclaimable cache, not a leak.
- Java heap **falls below** its starting value across the run (48.5 → 34.3 MB after trim), so
  nothing is being retained across tab navigation.
- 10 full open/close cycles of the Liquid Player cost **+0.9 MB**, fully reclaimed. Given the
  player allocates RenderEffects at 60 fps (section 12), this is the reassuring result: they are
  short-lived and collected, they do not accumulate.
- **Return-to-baseline: yes.** No memory finding to report.

---

## 14. Network at startup — MEASURED (partially; some metrics N/A)

`dumpsys netstats` buckets are too coarse to resolve a 25 s window — byte counts came back as a
flat zero delta, so **startup bytes transferred: N/A**. Exact HTTP request counts need a proxy
(mitmproxy/Charles), not set up this pass: **request count: N/A**.

What *was* measurable: the app's own sockets, by reading `/proc/net/tcp` + `/proc/net/tcp6`
filtered on `uid=10228` (`u0_a228`), sampled ~6x/s across a cold start plus 38 s idle on Home.

**4 distinct sockets, all TLS/443:**

| endpoint | who |
|---|---|
| `185.199.110.133` | GitHub Pages / raw.githubusercontent CDN range |
| `2606:50c0:8000::154` | same, over IPv6 |
| `192.178.193.91` | Google (YouTube / Firebase) |
| `2404:6800:4000:101d::be` | same, over IPv6 |

That is **lean** — Home is DB-driven and does not fan out on launch. The two GitHub connections are
the startup update check (`shinymusic/updater/ShinyUpdater.kt`,
`api.github.com/.../releases/latest`) and/or the listen-together server list
(`listentogether/ListenTogetherServers.kt`,
`raw.githubusercontent.com/lastdaytheOG/Shiny/.../server.json`).

**The update check is already well-behaved** — `MainActivity.kt:637-648` gates it on
`getAutoUpdateCheckSetting`, throttles it with `claimStartupUpdateCheck`, and **delays it 2000 ms**
so it cannot compete with startup. No change to recommend.

Sampling caveat: at ~6 Hz, a connection living under ~150 ms could be missed. HTTP/2 connections
are long-lived, so the count is sound for its purpose.

---

## 15. Compose — static findings (compiler metrics still unavailable, see 4.2)

- **Lazy-list keys are in good shape.** In `ui/liquid`, **235 of 251** `item`/`items`/`itemsIndexed`
  calls pass a `key`. No action.
- **`contentType` is the gap: 4 uses across those 251 calls.** Three of the four are on Home
  (`LiquidHomeScreen.kt:230, 236, 249`), which does it properly —
  `item(key = section.key, contentType = section.contentType())` with
  `HomeSection.contentType()` returning the section class name. So **the app's most heterogeneous
  list is already correct**, and Home's scroll cost (section 10) is *not* explained by a missing
  `contentType`. The remaining ~248 call sites are mostly homogeneous lists, where `contentType`
  buys little. **Do not file this as a blanket finding.**
- `ui/screens` (the older, pre-Liquid screens) is weaker: 3 of 16 `items` calls pass a key. Low
  value while those screens are being replaced.
- `derivedStateOf`: only 14 uses app-wide (`BottomSheet.kt` 5, `LiquidLyrics.kt` 4,
  `LiquidPlayerControls.kt` 3, `LiquidLocalSongsScreen.kt` 2). Concentrated exactly where scroll /
  progress state is read, which is the right place. No finding.
- **Main-thread blocking (section 5.3) is still not proven.** Evidence gathered this pass:
  one `Choreographer: Skipped 31 frames!` (~516 ms) on a startup, but **zero** such events across a
  clean cold start + 3 navigation cycles + a player open/close. No StrictMode or ANR output. So the
  38 `runBlocking` sites located in 5.3 are **not** currently producing visible main-thread stalls
  in these flows. They remain a latent risk, not a measured defect. Do not report them as a defect
  without a trace that catches one on the main thread.

---

## 16. Follow-up work (the audit itself is done — see section 18)

1. **Throttle the living-artwork clock** (section 12). Highest-confidence change, and the only
   recommendation this audit could not verify by experiment because code changes were forbidden.
2. **Trace Home and remote Search results** (section 10). This pass proved what their cost is
   *not* (lazy keys, `contentType`); it did not find what it is. Needs Perfetto or the Compose
   compiler metrics that 4.2 ruled out.
3. **`song(totalPlayTime DESC)`** — measured at 0.67 → 0.17 ms (section 3).
4. **Test a covering index** `event(timestamp, songId, playTime)`. Test it; the plain `timestamp`
   index measured *worse* (section 3).
5. **Re-measure on a phone:** `./gradlew :app:assembleArm64GmsBenchmarkRelease
   -Pshiny.benchmark.abi=arm64`. Everything about frame pacing and 90/120 Hz here is limited by a
   60 Hz host-GL emulator.
6. **User journeys A–E** — blocked, see section 0.

---

## 17. Scripts added this session

### 17.1 `cpu.sh <label> <seconds>` — per-thread CPU from `/proc` jiffies

```bash
#!/bin/bash
S="-s emulator-5556"; PKG=com.shiny.music
PID=$(adb $S shell pidof $PKG | tr -d '\r' | awk '{print $1}')
snap() { adb $S shell "for t in /proc/$PID/task/*; do echo \"\${t##*/} \$(cat \$t/stat 2>/dev/null)\"; done" 2>/dev/null | tr -d '\r'; }
snap > cpu_a.txt; sleep $2; snap > cpu_b.txt
python - "$1" "$2" cpu_a.txt cpu_b.txt <<'PY'
import sys
label, secs = sys.argv[1], float(sys.argv[2])
def load(p):
    d={}
    for ln in open(p, encoding='utf8', errors='ignore'):
        ln=ln.strip()
        if ') ' not in ln: continue
        head, rest = ln.rsplit(')', 1)
        tid = head.split(None,1)[0]
        name = head.split('(',1)[1] if '(' in head else '?'
        f = rest.split()
        if len(f) < 13: continue
        try: d[tid]=(name, int(f[11])+int(f[12]))
        except ValueError: continue
    return d
a,b=load(sys.argv[3]),load(sys.argv[4])
rows=[]; total=0
for tid,(name,j2) in b.items():
    if tid in a:
        dj=j2-a[tid][1]; total+=dj
        if dj>0: rows.append((dj,name,tid))
rows.sort(reverse=True)
HZ=100.0
print("[%s] %.0fs  TOTAL %d jiffies = %.0f ms CPU = %.1f%% of one core"
      % (label, secs, total, total*1000/HZ, 100.0*total/HZ/secs))
for dj,name,tid in rows[:8]:
    print("   %-22s tid=%-7s %4d j  %6.0f ms  %5.1f%%" % (name,tid,dj,dj*1000/HZ,100.0*dj/HZ/secs))
PY
```

Note: fields 14/15 of `/proc/<tid>/stat` are utime/stime, but the comm field can contain spaces
and parentheses — parse by `rsplit(')', 1)`, never by column index from the left.

### 17.2 `parse2.py` — gfxinfo with the emulator GPU stall separated out

```python
import re, glob, os, sys
d = sys.argv[1] if len(sys.argv)>1 else "."
hdr = "{:<24}{:>7}{:>8}{:>7}{:>7}{:>8}{:>8}{:>9}{:>8}"
print(hdr.format("screen","frames","janky","jank%","p50","p90","slowUI","slowDraw","gpuSTALL"))
for f in sorted(glob.glob(os.path.join(d,"gfx_*.txt"))):
    t = open(f, encoding="utf8", errors="ignore").read()
    name = os.path.basename(f)[4:-4]
    def g(p, dflt="0"):
        m = re.search(p, t); return m.group(1) if m else dflt
    total = int(g(r"Total frames rendered: (\d+)"))
    if total == 0:
        print(hdr.format(name, 0, "-", "-", "-", "-", "-", "-", "-")); continue
    janky = int(g(r"\nJanky frames: (\d+)"))
    stall = 0
    h = re.search(r"GPU HISTOGRAM: (.+)", t)
    if h:
        for pair in h.group(1).split():
            ms, cnt = pair.split("ms=")
            if int(ms) >= 100: stall += int(cnt)
    print(hdr.format(name, total, janky, "{:.1f}%".format(100.0*janky/total),
          g(r"\n50th percentile: (\d+)ms")+"ms", g(r"\n90th percentile: (\d+)ms")+"ms",
          g(r"Number Slow UI thread: (\d+)"), g(r"Number Slow issue draw commands: (\d+)"), stall))
```

**Use `Janky frames:`, never `Janky frames (legacy):`.** The legacy metric compares total pipelined
frame latency against 16 ms and reports 94% jank on a Stage that misses **zero** deadlines.

### 17.3 Socket counting by uid (no root needed)

```bash
# uid from: adb shell ps -A -o USER,PID,NAME | grep shiny   ->  u0_aNNN  ->  uid = 10000 + NNN
adb -s emulator-5556 shell "cat /proc/net/tcp /proc/net/tcp6 2>/dev/null | awk '\$8==10228 {print \$3, \$4, \$10}'"
```

Field 3 = remote addr:port (hex, little-endian words), 4 = state (01 = ESTABLISHED), 10 = inode
(use it to count distinct sockets). Sample it in a host-side loop — a device-side background loop
does **not** survive the adb session.

---

## 18. FINAL REPORT AND SCORE

Completed 21 September 2026. Every number below is measured on the AVD `ShinyStartup`
(`emulator-5556`, API 36, x86_64, 1080x2400 @ 420, **60 Hz only**) against the
`x86_64GmsBenchmarkRelease` APK built 21 Sep 00:18 from the current working tree.
Nothing in the app was changed.

**The three rules for reading this score:**

1. It scores **this APK on this emulator**, not SHINY on a phone. Section 4.3 stands: the host-GL
   emulator injects GPU stalls the app is not responsible for, and the panel is 60 Hz, so
   **90 Hz and 120 Hz behaviour is N/A** — it was never measurable and is not estimated here.
2. Where something could not be measured it is marked **N/A** and costs no points either way.
3. Weights are the user's, from section 7.

### Scorecard — **79 / 100**

| Area | Weight | Score | Confidence | One-line basis |
|---|---|---|---|---|
| Startup | 20 | **17** | High | 800 ms process-cold median, n=8, tight spread |
| Scrolling | 20 | **13** | Medium | 5 of 7 screens fine; Home and remote Search are ~2x worse |
| Animation | 20 | **16** | High | 60 fps with **zero** missed deadlines, but one unthrottled loop |
| Playback | 15 | **13** | Low (n=3) | Sub-second tap-to-audio; 10.5% of a core backgrounded |
| Memory | 10 | **9** | High | No leak; all growth reclaimed on trim |
| I/O + DB | 10 | **7** | Medium | Indexes right; 20-26 ms queries at heavy-listener scale |
| Network | 5 | **4** | Medium | 4 TLS sockets at startup; request counts N/A |
| **Total** | **100** | **79** | | |

---

### Startup — 17 / 20

**Measured (section 9):** process-cold median **800 ms** (n=8, 726-890, every iteration a new pid).
Freshly-installed state, no AOT: 883 ms. The baseline profile is worth only ~80 ms of median here.

Supporting: `App.onCreate` (5.4) pushes the Coil cache size and `initializeSettings()` onto
`Dispatchers.IO` and keeps the main thread to `CrashHandler.install`, `AppContextHolder.initialize`
and `CipherDeobfuscator.initialize`. The startup update check is delayed 2,000 ms and throttled
(section 14), so it cannot compete with first frame. One `Choreographer: Skipped 31 frames!`
(~516 ms) was seen on one launch, and **zero** across a clean cold start plus three navigation
cycles — so there is a stall that exists but does not reproduce reliably.

**Why not higher:** that one unexplained 516 ms stall, and the 1,492 ms first launch after a
compilation reset — a real user's first launch after installing an update pays something similar.

**Correction carried forward:** section 2's 3,307 ms cold median **does not reproduce**. Do not
quote it. Warm (539 ms) and hot (501 ms) from section 2 were taken in that same loaded session and
are therefore also likely pessimistic; they were not re-measured.

### Scrolling — 13 / 20

**Measured (section 10),** ranked by slow-UI-thread rate, which is CPU-side and survives the
emulator's GPU contamination:

| | |
|---|---|
| **Home 34.6%**, **Search results 33.9%** | the two problem screens |
| New 20.7%, Stats 19.0%, Settings 18.5%, Library 17.3%, Search landing 15.3% | healthy |

Home is the app's front door and the worst scroller in it. Remote Search results is the other.
Everything else clusters tightly around 15-21% and needs no work.

**What it is not:** not missing `LazyColumn` keys (235 of 251 call sites pass one) and not a
missing `contentType` — Home is one of the few screens that already does `contentType` properly
(`LiquidHomeScreen.kt:249`). So the usual Compose suspects are already handled, and the cost is
somewhere else. **It has not been localised.** Doing so needs a Perfetto/systrace capture, or the
Compose compiler metrics that section 4.2 rules out this pass.

**Why not lower:** the raw jank% (20-41%) looks far worse than this, but 48-92 frames of every
capture are parked in the emulator's top GPU bucket. Those are the host, not SHINY.
**Why not higher:** two of seven screens, including the most important one, are twice the cost of
the rest, and the reason is still unknown.

Caveat from 4.4: the Library on this device is small, so its good result understates a large one.

### Animation — 16 / 20

**The smoothness result is excellent and deserves stating plainly:** the Liquid Player holds a
**sustained 60 fps with 0 janky frames out of 1,209** on the Stage, and 1 out of 995 in Lyrics.
It is expensive, not janky. Nothing stutters.

**The idle discipline is better still.** Measured, no input:

- Home while music plays: **0 frames rendered in 15 s.** The mini player does not repaint.
- Player paused: **0 frames, 0.5% of one core.**
- Backgrounded: the RenderThread never runs at all.

There is no always-on repaint anywhere in this app. That is the thing most music apps get wrong,
and SHINY gets it right.

**Why not higher — one concrete, fixable finding (section 12).** `LiquidLivingArtwork.kt:81-89`
drives the artwork's motion from an **unthrottled** `withFrameNanos` loop, and every tick rebuilds
a `RuntimeShader` `RenderEffect` from scratch (`LiquidArtShader.effect`, lines 185-189, allocates
a new one per call) — 60 times a second. One hundred lines away, the *background* field's drift is
deliberately stepped to ~25 fps with `delay(40)`, and the comment explains exactly why. The artwork
loop never got the same treatment, although its motion periods are 17 s, 23 s and 29 s, so
stepping its clock would be invisible by the codebase's own argument.

Cost this sits on: Stage **35.3%** of one core (RenderThread 20.7%), Lyrics with a live ticker
**42.7%** (RenderThread 24.5%, main thread 7.7% — the highest main-thread figure in the audit).

**90 Hz / 120 Hz: N/A.** The panel is 60 Hz; it was never measurable.

### Playback — 13 / 15

**Measured:** tap to first audio on remote tracks — **505 / 773 / 1,043 ms** (n=3, median ~773 ms).
Poll granularity is ~150-250 ms, so these are upper bounds within about a quarter second.
**Confidence is low on n=3** and the sample could not be extended to never-played tracks before the
run ended.

Backgrounded playback costs **10.5% of one core**: ExoPlayer 4.1 + MediaCodec 3.3 + binder/misc,
with the RenderThread completely absent. Audio cost is stable at ~10% across every UI state
measured, so the player's UI cost is cleanly separable from it (that separation is what makes
section 11's table readable).

Sub-second start on a remote track is a good result, and consistent with
`shiny-potoken-dead-end`: the NewPipe re-extraction cost is genuinely gone.

**Why not higher:** n=3, and local-file playback was never measured (the On This Device screen
wanted a storage permission this pass chose not to grant).

### Memory — 9 / 10

**No leak (section 13).** 12 navigation cycles grow PSS by +10.3 MB; a `trim-memory
RUNNING_CRITICAL` returns **all** of it (217,164 KB against a 216,764 KB baseline — +0.4 MB, noise).
Java heap *falls below* where it started (48.5 → 34.3 MB). Ten full open/close cycles of the Liquid
Player cost +0.9 MB, fully reclaimed — reassuring given the player allocates a `RenderEffect` every
frame: they are short-lived and collected, they do not accumulate.

**Why not 10:** PSS sits at 210-227 MB while playing on a 2,971 MB device. Bounded and reclaimable,
but not light, and the artwork caches are the obvious contributor.

### I/O + Database — 7 / 10

From section 3, at heavy-listener scale (6,000 songs / 60,000 events):

- Indexes as shipped are **correct** — Room does auto-create the FK indexes, and the early
  suspicion of missing junction-table indexes was wrong.
- Week and month ranges are already sub-millisecond. Only year and all-time views are expensive:
  `COUNT(DISTINCT artistId)` **26.16 ms**, `COUNT(DISTINCT albumId)` **20.62 ms**, Home Quick Picks
  **20.83 ms**, `mostPlayedSongs` **18.28 ms**.
- The obvious fix was **tested and rejected**: a plain `event(timestamp)` index makes these
  *slower* (1.93 → 4.37 ms, 20.62 → 35.32 ms). That negative result is worth as much as a fix.
- One genuine win identified but not applied: `song(totalPlayTime DESC)` takes the Library
  songs-by-playtime query 0.67 → **0.17 ms**.

**Why not higher:** those 20-26 ms queries are real at scale, and the two untested levers (a
covering index `event(timestamp, songId, playTime)`, or pre-aggregation) were never measured.
The 38 `runBlocking` sites from 5.3 remain **unproven** — this pass found no main-thread stall
attributable to them, so they are a latent risk, not a defect.

### Network — 4 / 5

A cold start plus 38 s idle on Home opens **4 TLS sockets, all :443** — two to the GitHub Pages /
raw CDN range, two to Google. Home is DB-driven and does not fan out on launch. The startup update
check is gated by a setting, throttled by `claimStartupUpdateCheck`, and deliberately delayed
2,000 ms. Nothing here needs changing.

**N/A, and costing no points:** startup **bytes** (netstats buckets are too coarse for a 25 s
window — the delta came back a flat zero) and exact **HTTP request counts, duplicates and cache
hit rates**, which need a proxy that was not set up. The socket count is a sound floor but cannot
see duplicate requests multiplexed over one HTTP/2 connection — so "no duplicate requests" is
**not** something this audit verified.

---

### Ranked recommendations

Every one of these keeps the visual and the feature intact, per section 7.

1. **Throttle the living-artwork clock** (`LiquidLivingArtwork.kt:81-89`) the way the background
   field is already throttled, and stop allocating a `RenderEffect` per frame
   (lines 185-189 — hold one and mutate its uniforms). Targets 20.7% RenderThread on Stage and
   24.5% in Lyrics. Highest confidence, smallest change, and the codebase already contains the
   pattern and the rationale. **Measure it — this pass could not.**
2. **Trace Home and remote Search results** to find the UI-thread cost. This audit proved what it
   is *not* (keys, `contentType`) but did not localise it. Needs Perfetto, or the Compose compiler
   metrics that 4.2 forbade.
3. **Add `song(totalPlayTime DESC)`** — already measured at 0.67 → 0.17 ms.
4. **Test a covering index** `event(timestamp, songId, playTime)` for the year and all-time stats
   views. Test it; do not assume it — the plain `timestamp` index measured *worse*.
5. **Re-measure on a phone.** `./gradlew :app:assembleArm64GmsBenchmarkRelease
   -Pshiny.benchmark.abi=arm64`. Everything about frame pacing, 90/120 Hz and GPU cost in this
   report is limited by a 60 Hz host-GL emulator.

### What this audit did not establish

- Frame behaviour above 60 Hz — **N/A**, never measurable here.
- Why Home and Search results are UI-thread-bound — narrowed, not found.
- Whether any of the 38 `runBlocking` sites actually blocks the main thread in practice.
- Startup bytes, HTTP request counts, duplicate requests, cache hit rates — **N/A**.
- Local-file playback latency, and playback start on never-before-played tracks (n=3, all
  previously played).
- User journeys A-E as originally specified: **the prompt's definitions of A-E are recorded
  nowhere in this repo and were not available.** The flows actually exercised are in sections 10
  and 11.
