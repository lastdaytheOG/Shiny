# Performance work — handoff

Last updated 14 September 2026, 05:20. Work through R-05 was committed by the user as `d00852c` ("perf: complete startup and playback optimizations"). Anything after that (Task 1, section 8) is uncommitted; the user does the commits.

- Audit artifact: https://claude.ai/code/artifact/37824fe7-294d-45f1-ae9b-3e449af65e58
- Roadmap IDs (R-01 … R-22) refer to section 19 of that audit.
- Rules for this work: **no screen-design changes**; measure before and after every change; don't commit.

---

## 0. NEXT — start here when the user types `continue`

R-05 is done (section 3). The cold-restart investigation (section 7) found three concrete, measured problems (Tasks 1–3); Task 4 comes from section 5. Do the tasks **in order**, one at a time. For each: read the code named, make the smallest change, rebuild, re-measure with the method given, update this file, then report to the user before starting the next task.

**Tasks 1–4 and R-06/R-07/R-08 are done (sections 8–12), plus two queue fixes and the double Home load (section 12). Next, in order:**
1. On a phone: Cast connects (Task 1); repeat the release `PlaybackBenchmark` pair for R-07 (the emulator pair was −16% with overlapping ranges); check that a deep link at launch keeps its song (the race fix was never exercised).
2. ~~Follow-ups from section 12~~ — done 26 September (section 13): Account settings already shared the activity's `HomeViewModel`; covers now arrive with the page; touch-down pre-resolve landed in the fast-start pass.
3. ~~R-09~~ — measured 26 September (section 13): no app-code main-thread stall found; the long waits are RenderThread/GPU. Next evidence needs the phone: a Perfetto trace of cold start + tab switching, looking at RenderThread, not the main thread. Leave R-10/R-11 and R-18 for later.

The task plan below is kept for reference.

### Task 1 — Take Cast off the startup path (R-01 leftover, with R-09) — DONE, see section 8
- Cast now loads on a background thread 3 s after the service starts. The handler is a `StateFlow` that the Cast UI readers collect.
- Debug traces: the main-thread GC wait in Cast class loading is gone. It was 1.0 s in one of three before-runs, and 5.4 s in section 7. Player init → `PlayerConnection` fell from 649–2,091 ms to 116–536 ms.
- Still to check on a phone with a Cast device: that Cast discovers and connects.

### Task 2 — Keep the cached audio on a cold resume (R-02 / audit §4 "persisted resolver state") — DONE, see section 9
- **Result:** a cold resume now keeps the cached audio when its stored length matches the resolved format. Before, 4 spans (2.08 MB) were deleted and re-downloaded; after, nothing is deleted or written.

The original plan, kept for reference:
- **Measured:** after a restart `songUrlCache` (an in-memory `HashMap`, `MusicService.kt:510`) is empty. In `createDataSourceFactory` (~3206-3213) a cached song therefore logs `Ghost cache entry … re-fetching` and calls `playerCache.removeResource(mediaId)`. The song's audio (cache id 63, 1.48 MB) was deleted, and the same audio re-downloaded under id 64, on every cold resume.
- **First:** work out what the removal protects against before changing it. The likely case is cached bytes from a different format under the same key; see `bypassCacheForQualityChange`, the locked quality, and the `_diff` key.
- **Change (small):** on a URL-cache miss with cached bytes, resolve as today, but **keep** the cache when the resolved format matches what is cached (content length in `ContentMetadata` / `FormatEntity.contentLength`, itag). Remove only on a mismatch. Optional follow-up, separately measured: persist URL + expiry per mediaId+quality to skip the resolve while valid. URLs are IP-bound, so invalidate on network or `visitorData` change.
- **Verify:** `scripts/perf-cold-trace.sh seed`, `launch`, `play X Y`: no `Ghost cache entry`, span id unchanged, nothing re-written. Also check that loudness is still saved (`song.db` `format.loudnessDb`), that playback is correct after a quality change, and that there is no corrupted audio when the format differs.

### Task 3 — Start the next-song preload after audio starts, not at the tap
- **Measured:** `onPlayWhenReadyChanged` (`MusicService.kt` ~2399-2401) calls `preloadUpcomingItems()` as soon as play is pressed. On the resume, `Preloading stream for hx50R-jMAhQ` started its resolve 110 ms **before** the tapped song's, and the two ran in parallel.
- **Change:** trigger it on the first `onIsPlayingChanged(true)` after a play request instead. Keep the "nothing before the first play" rule from R-03.
- **Verify:** the order of fix403 `resolve.begin` lines; `PlaybackBenchmark` tap → audible, `None` only, back-to-back with a run before the change.

### Task 4 — Crashlytics in release builds (section 5)
- Add Firebase keep rules to `proguard-rules.pro`. Verify on the release-like APK that `Invalid component registrar` is gone and Crashlytics logs its init. Must be done before the next release ships.

### After these: audit's second sprint
R-06 resume snapshot (Home network shelves painted from disk: their covers can't show at launch today), R-07 one shared network client, R-08 next-song pre-cache. Leave R-10/R-11 (render tiers, Now Playing GPU) and R-18 for later.

---

## 1. Status at a glance

| Item | State |
|---|---|
| Step 1 (R-01, R-02, R-03, R-04) | Done in part and verified on the emulator; see section 2 |
| R-05 baseline profile + benchmarks | **Done (14 September).** Profile generated, packaged and applied; benchmarks run through `am instrument`. **No measurable gain on the emulator** (section 3) |
| Test APKs for a phone | Built and copied to the repo root (section 4). They predate the profile |
| Cold-restart investigation | Done (14 September), section 7 |
| Task 1: Cast off the startup path | **Done (14 September), uncommitted.** Section 8. Cast connecting still to check on a phone |
| Task 2: keep cached audio on a cold resume | **Done (14 September), uncommitted.** Section 9 |
| Task 3: next-song preload after audio starts | **Done (14 September), uncommitted.** Section 10 |
| Task 4: Crashlytics in release builds | **Done (14 September), uncommitted.** Section 11. No startup regression measured |
| R-06, R-07, R-08 (audit's second sprint) | **Uncommitted, section 12.** R-08: skip → audible 658 → 262 ms median, ranges don't overlap. R-07: connection reuse verified; warm tap → audible 1,440 → 1,213 ms in one release pair (not proven). R-06: network shelves from the snapshot at +0.6–0.9 s against +5.0–5.7 s; Home now loads once per launch instead of twice. Also fixed: the deep link / restored-queue race and the first-song preload gap |

---

## 2. Step 1 — what changed

### Results (debug build, `ShinyStartup` emulator, API 36)

| Measurement | Audit | After Step 1 |
|---|---|---|
| Cold start, `am start -W` | 4.98 s median (6 runs) | **4.12 s** median (5 runs: 4255, 4307, 4115, 4106, 4097 ms) |
| First play, fresh process, very first time after install | 13.2 s | **8.8 s** (7.3 s is the one-time signature timestamp extraction) |
| First play, fresh process, timestamp already stored | 13.2 s | **0.74 s** and **0.95 s** in two tests; one run took 5.8 s because the metadata request itself took 5.4 s |
| Work before the first tap | 2 resolves, next-song preload, update check | **none** (checked in logcat) |

These are single-session debug numbers. R-05 exists to replace them with release-build medians.

### Changes by roadmap item

**R-04 — beat pulse no longer recomposes the whole app**
- `ui/theme/BeatPulse.kt`: `LocalBeatState` changed from `staticCompositionLocalOf` to `compositionLocalOf`.

**R-02 — faster play**
- `utils/potoken/PoTokenGenerator.kt`: checks that `po_token.html` is packaged before touching WebView. The PoToken attempt now costs ~0 ms instead of 3.6 s.
- `utils/YTPlayerUtils.kt`:
  - The signature timestamp is no longer extracted before every resolve.
  - WEB_REMIX metadata uses a timestamp **stored on disk** (SharedPreferences `player_signature_timestamp`). It's refreshed in the background once older than 24 h and ignored once older than 7 days.
  - If WEB_REMIX answers `UNPLAYABLE` while VISIONOS serves the video, the stored value is treated as stale and refreshed.
  - Cipher clients (TVHTML5, WEB_CREATOR) still wait for a fresh extraction.
  - The HEAD probe of the VISIONOS URL runs while the metadata request is still in flight (`probeEarly`). Its result is reused only for the exact same URL.

**R-03 — no speculative work at launch**
- `playback/MusicService.kt`:
  - The restored queue is added **without `prepare()`** (`playQueue(prepare = false)`). The first play request from any source prepares it in `onPlayWhenReadyChanged`: the app, the notification or a headset button.
  - The next-song preload only runs once something is playing.
  - The saved queue is restored only if the player is still empty.
  - The saved position is applied only to that restored queue. This fixed a race where a deep link played the wrong song.
- `shinymusic/updater/ShinyUpdater.kt` + `MainActivity.kt`: the automatic update check runs at most **once per 24 h** (`claimStartupUpdateCheck`).

**R-01 — lighter launch**
- `MainActivity.kt`:
  - `DownloadUtil` is injected as `dagger.Lazy` and constructed on an IO thread during `onCreate`.
  - The first bind to `MusicService` waits for the first idle main-thread moment, with a 500 ms fallback. Later starts bind immediately.
  - Bind and unbind are tracked with a `serviceBound` flag. This also fixes a double `unbindService` in `onDestroy`.
- `di/AppModule.kt`: the player cache size is read from `PreferencesSnapshot` when loaded, instead of a blocking DataStore read.

### Bug found and fixed during testing
The first version sent **no** signature timestamp to WEB_REMIX. `/player` then answered `UNPLAYABLE`, so `format.loudnessDb` and `playbackUrl` were saved as NULL. Playback still worked, but loudness normalisation and the history ping were lost.

It was caught by pulling `databases/song.db`: the 21 older rows had loudness, and the new one had none. It was fixed with the stored timestamp above and re-verified (loudness saved in both fresh-process tests).

### Tried and reverted
- **Deferring Cast initialisation to idle.** `LiquidNowPlaying` reads `castConnectionHandler` once (`remember(playerConnection)`), so a late handler would leave the Cast state stuck. Reverted.

### Behaviour differences a tester may notice
- The first press of play after opening the app takes ~1 s, because loading happens then instead of at launch.
- The paused media notification probably no longer appears at launch until play is pressed (not checked on a device).
- The update dialog, when an update exists, shows at most once a day.

### Step 1 items not done
- Firebase init deferral (crash reporting must start early).
- Home "first viewport only" fetch (changes what appears when).
- R-02's fully async metadata. Playback still waits for the WEB_REMIX response, as the 5.8 s run shows.

---

## 3. R-05 — baseline profile and benchmarks (done)

### Added
| File | Purpose |
|---|---|
| `settings.gradle.kts` | `include(":baselineprofile")` |
| `build.gradle.kts` | buildscript classpath `androidx.benchmark:benchmark-baseline-profile-gradle-plugin` |
| `gradle/libs.versions.toml` | `benchmark = 1.5.0`, `profileinstaller = 1.4.1`, `uiautomator = 2.4.0`, `androidxTestExtJunit = 1.3.0` + aliases |
| `app/build.gradle.kts` | Applies `androidx.baselineprofile` (`mergeIntoMain = true`, `automaticGenerationDuringBuild = false`); adds `profileinstaller` and `baselineProfile(project(":baselineprofile"))`; debug-signs `benchmarkRelease` and `nonMinifiedRelease` (release keystore exists only in CI); disables Crashlytics mapping upload for those two build types |
| `baselineprofile/build.gradle.kts` | `com.android.test` module mirroring `:app` flavors. Only one flavor pair is enabled (`-Pshiny.benchmark.abi`, default `x86_64`; `-Pshiny.benchmark.variant`, default `gms`) so generation doesn't build all 10 variants. Passes `targetAppId` to the tests |
| `baselineprofile/.../ShinyApp.kt` | UI helpers: first-run screens, seeding a queue by deep link, finding the unlabeled mini-player button by layout, search via `MEDIA_PLAY_FROM_SEARCH`, `AudioManager.isMusicActive` waits |
| `baselineprofile/.../BaselineProfileGenerator.kt` | `startup` (launch → Home, feeds the startup/DEX-layout profile) and `listeningJourney` (Home scroll → search → play → pause) |
| `baselineprofile/.../StartupBenchmark.kt` | Cold start, 10 iterations: `StartupTimingMetric` (time to first frame) + `FrameTimingMetric` |
| `baselineprofile/.../PlaybackBenchmark.kt` | `freshProcessTapToAudible` and `warmProcessTapToAudible`, 8 iterations each; `pm clear` + seed song before each test |
| `app/.../utils/PlaybackTrace.kt` | Measurement-only trace sections: `ShinyTapToAudible` (async, play request → ExoPlayer `onAudioPositionAdvancing`) and `ShinyResolve` (stream lookup on the loader thread) |
| `PlayerConnection.kt`, `MusicService.kt` | Hooks for those sections (`playQueue`, `play`, `togglePlayPause`, resolve, `AnalyticsListener`). No behaviour change |

The compilation mode is chosen with the instrumentation argument `shiny.compilation` = `None`, `BaselineProfile` or both (comma-separated). `BaselineProfile` uses `BaselineProfileMode.Require`, so it fails instead of silently measuring without a profile.

### How the playback benchmark defines things
- **Tap** = the play request reaching `PlayerConnection` (the click handler).
- **Audible** = ExoPlayer's first audio-position-advancing callback.
- **Fresh process** = kill, launch, then tap the mini-player play button on the restored song straight away. The song's audio is cached from earlier iterations, like resuming yesterday's queue.
- **Warm process** = the app is already running and has played once; tap the top song of a different one-word search each iteration (Radiohead, Adele, Coldplay, …), so nothing is cached or preloaded.
- Seed song: `Rr1Cdli5nE8`.

### History (13 September)
- `:app:assembleX86_64GmsBenchmarkRelease` and `:baselineprofile:assembleX86_64GmsBenchmarkRelease` build, and the minified app launches without crashing.
- `connectedX86_64GmsBenchmarkReleaseAndroidTest` failed with **0 tests run**: the Gradle test runner (UTP) crashed on startup (`SEVERE: Fatal error while executing main`). Still unfixed; the `am instrument` route below replaces it.
- The emulator was then killed by Windows for low memory, with the Gradle daemon (`-Xmx6g`) and Kotlin daemon (`-Xmx8g`) both running.

### Measurements so far (14 September, `benchmarkRelease`, `ShinyStartup` emulator, API 36)

| Benchmark | Compilation | Result |
|---|---|---|
| `StartupBenchmark.coldStart`, 10 runs | None | time to first frame **3,642 ms median** (min 2,622, max 4,098); 12 frames median; frame overrun P50 394 ms, P90 1,489 ms |
| `PlaybackBenchmark.freshProcessTapToAudible`, 8 runs | None | tap → audible **2,435 ms median** (min 975, max 2,863); `ShinyResolve` 678 ms median, 1 per play |
| `PlaybackBenchmark.warmProcessTapToAudible`, 8 runs | None | tap → audible **3,530 ms median** (min 1,591, max 4,629); `ShinyResolve` 681 ms median, 1 per play |

Observation, not acted on: the stream lookup is only ~0.7 s of the 2.4–3.5 s. Most of the wait comes after it: loading the audio and starting the player. The fresh-process song is already in the audio cache and still takes 2.4 s. The traces are on the emulator under the media folder if Step 2 wants to break this down.

Raw output of these runs (`*.txt`, `*.json`) was saved to the session scratchpad only; copy the numbers above rather than relying on those files.

### R-05 steps (all done 14 September)
1. ~~`./gradlew --stop`, restart the emulator.~~ Done.
2. ~~Bypass UTP with `am instrument`.~~ **Verified working** (14 September):
   `adb install -r -t` both APKs, then
   `adb shell am instrument -w -e class com.shiny.music.baselineprofile.StartupBenchmark -e targetAppId com.shiny.music -e shiny.compilation None -e androidx.benchmark.suppressErrors EMULATOR com.shiny.music.baselineprofile/androidx.test.runner.AndroidJUnitRunner`.
   Results print to stdout and are written to `/sdcard/Android/media/com.shiny.music.baselineprofile/com.shiny.music.baselineprofile-benchmarkData.json`, which **each run overwrites**, so pull it before the next run. In Git Bash set `MSYS_NO_PATHCONV=1` or `/sdcard/...` paths get rewritten.
3. Run Startup + Playback with `None`, several times. *(Run 1 of each done, numbers above. More `None` runs come from step 6, which pairs them with `BaselineProfile`.)*
   - **Profile generation done (14 September).** `x86_64GmsNonMinifiedRelease` app and test APKs, same `am instrument` route with `-e class com.shiny.music.baselineprofile.BaselineProfileGenerator`. Both journeys passed in 424 s. Build it with one JVM, `"-Dorg.gradle.jvmargs=-Xmx6g" -Pkotlin.compiler.execution.strategy=in-process`, so the Kotlin daemon doesn't take another 8 GB.
   - Output on the device: `BaselineProfileGenerator_startup-startup-prof.txt` (40,507 rules, 4,519 in `Lcom/shiny/music/`) and `BaselineProfileGenerator_listeningJourney-baseline-prof.txt` (48,676 rules, 5,590 app).
   - Merged by hand the way the plugin merges: `baseline-prof.txt` = sorted union of both (49,909 rules, 5,709 app); `startup-prof.txt` = the startup journey only. Both are now in `app/src/main/generated/baselineProfiles/`.
4. ~~Generate the profile.~~ Done, see above.
5. **Packaged: verified (14 September).** Rebuilt `:app:assembleX86_64GmsBenchmarkRelease` (4 m 41 s, 5 GB heap). In the APK, `assets/dexopt/baseline.prof` grew from 9,485 bytes (library rules only) to **19,985 bytes**, plus a 1,345-byte `baseline.profm`. Rule counts along the build:

   | Stage (`app/build/intermediates/…/x86_64GmsBenchmarkRelease/`) | Rules |
   |---|---|
   | `merged_art_profile` (app + library profiles) | 56,240 |
   | `r8_art_profile/expand…Wildcards` (R8 input) | 79,732 |
   | `r8_art_profile/minify…WithR8` (after R8 drops removed/inlined code) | 30,726 |
   | `combined_art_profile` (compiled into `baseline.prof`) | 30,736 |
   | `merged_startup_profile` | 40,508 |

   **Applied: verified.** `dumpsys package dexopt` shows `com.shiny.music` as `[status=speed-profile] [reason=cmdline]`, and every `BaselineProfileMode.Require` run passed.
6. **Comparison done (14 September, 01:20–01:37).** Run with `-e shiny.compilation None,BaselineProfile`, so each test ran without and then with the profile, back-to-back. Medians in ms, min–max in brackets:

   | Benchmark | `None`, run 1 (00:42) | `None`, run 2 | `BaselineProfile` |
   |---|---|---|---|
   | Cold start, time to first frame | 3,642 (2,622–4,098) | 4,021 (3,607–5,018) | 3,814 (2,946–4,789) |
   | Cold start, frame overrun P50 | 394 | 446 | 439 |
   | Tap → audible, fresh process | 2,435 | 2,288 | 2,699 |
   | Tap → audible, warm process | 3,530 | 4,054 | 3,988 |
   | `ShinyResolve`, fresh / warm | 678 / 681 | 644 / 885 | 782 / 807 |

   Run 2's `None` startup produced a value for only 9 of its 10 iterations.

   **Result: the profile makes no measurable difference on this emulator.**
   - With the profile, startup is 5% faster than the `None` run beside it, but slower than `None` run 1. The same configuration drifted 10% between two runs an hour apart, which is more than the difference. The individual runs overlap completely.
   - Playback didn't change either, as expected: network time dominates it.
   - *Inference:* launch on this emulator isn't limited by code running interpreted. Where the ~3.8 s goes is unmeasured; the startup Perfetto traces on the device can show it. The splash hold explains at most 1 s of it: `MainActivity` keeps the splash until the first composed frame with real preferences, capped at `SPLASH_MAX_HOLD_MS = 1000`.
   - It says nothing about phones. There, right after install and before background dexopt runs, the profile is what spares startup from interpreted code. Only the same comparison on a phone can show whether that matters (`-Pshiny.benchmark.abi=arm64`; no need to regenerate the profile).
   - The audit's proposed CI gate ("fail on regressions over 10%") can't work on this emulator, whose own run-to-run noise is 10%.

   Raw output (`startup-compare-1.*`, `playback-compare-1.*`, `dexopt-after-startup.txt`) is in the session scratchpad only.

   **Keep or drop the profile?** Kept for now (uncommitted). It adds 10.5 KB to the APK, but its benefit is unproven until a phone run.

**Correction (from section 7):** `PlaybackBenchmark.freshProcessTapToAudible` assumed the restored song plays from the audio cache. It doesn't: the "Ghost cache entry" path deletes that cache on every cold resume, so this benchmark measures resolve + re-download. What comes after R-05 is in section 0.

---

## 4. APKs for a phone (repo root)

| File | Package | Size | Use |
|---|---|---|---|
| `Shiny-arm64-debug.apk` | `com.shiny.music.debug` | 84 MB | Installs next to the normal app; debug, so slower |
| `Shiny-arm64-release-test.apk` | `com.shiny.music` | 40 MB | R8-minified like release, debug-signed. Replaces an installed Shiny only after uninstalling it (data is lost). Has library profiles only |

`Shiny-arm64-gms-debug-2026-09-12c.apk` in the same folder is from an earlier session.

---

## 5. Findings to act on later (not fixed)

- **Crashlytics likely off in release builds.** In minified builds Firebase logs `Invalid component registrar … NoSuchMethodException <init>` for `CrashlyticsRegistrar` and `FirebaseInstallationsKtxRegistrar`, and no Crashlytics init line appears. `proguard-rules.pro` has no Firebase keep rules and AGP 9 uses full-mode R8. CI's `assembleUniversalGmsRelease` uses the same rules.
- **Metadata still gates playback.** One fresh-process play waited 5.4 s on the WEB_REMIX request alone.
- **Build memory.** Gradle and Kotlin daemons together can reserve 14 GB, enough to get the emulator killed.

---

## 6. Useful commands

Prefix every Gradle command with `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"` (the shell's own `JAVA_HOME` is broken).

```bash
# Emulator debug build
./gradlew :app:assembleX86_64GmsDebug

# Release-like build + benchmark APK
./gradlew :app:assembleX86_64GmsBenchmarkRelease :baselineprofile:assembleX86_64GmsBenchmarkRelease

# Phone APKs
./gradlew :app:assembleArm64GmsDebug :app:assembleArm64GmsBenchmarkRelease

# Benchmarks through Gradle (currently fails: UTP crash)
./gradlew :baselineprofile:connectedX86_64GmsBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=com.shiny.music.baselineprofile.StartupBenchmark \
  -Pandroid.testInstrumentationRunnerArguments.shiny.compilation=None \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.suppressErrors=EMULATOR
```

Resolver timings are in logcat under the tag `fix403` (debug builds): `resolve.sts.stored`, `resolve.sts.extract`, `client.probe.early`, `resolve.success`. To check loudness after a play, pull `databases/song.db` with `run-as` and look at `format.loudnessDb`.

---

## 7. Cold-restart investigation: artwork and resume (14 September)

The user's questions: are artwork and resume state really restored from disk after a process restart? Does the missing artwork at launch come from intended deferral or from re-fetching? Is the ~1 s resume coming from persisted state?

**Method.** Debug build `com.shiny.music.debug` on the `ShinyStartup` emulator, DEX pre-verified with `cmd package compile -m verify -f`. Ran `scripts/perf-cold-trace.sh`: seed a song, force-stop, cold launch with timed screenshots, Coil journal diff, tap the mini player's play, logcat and player-cache file diff. **One run each**, so read the directions, not the exact times; the emulator exaggerates GC and class-loading stalls. The first attempt, on an unverified fresh install, displayed at 6.1 s and is not used.

### Artwork: persisted and read from disk; not re-downloaded
- Coil's disk cache is `cacheDir/coil`, 256 MB by default (`App.kt:297-316`); a value of 0 in Settings > Storage disables it. It held 783 files (64 MB).
- During the cold launch Coil's journal gained **13 entries, all `READ` (disk hits), none `DIRTY`/`CLEAN`**, so no artwork was downloaded. Home and the mini player showed covers once the UI appeared (screenshot at 02:51). `READ` lines reach the journal file late; read it several seconds after launch.
- **Why covers still aren't instant:**
  1. After process death the memory cache is empty, so each cover is decoded from disk and faded in (`Artwork` uses `crossfade(220)`, `ui/liquid/LiquidComponents.kt:191`).
  2. The mini player's cover (`LiquidChrome.kt:451`, the raw `thumbnailUrl` of the restored queue) waits for `PlayerConnection`. That needs the bind at first idle after the first frame (Step 1), then `MusicService` startup, then the queue read on IO.
  3. `MusicService` startup stalls the main thread. Cast module loading waited 5.4 s on GC, then `Skipped 396 frames`; the emulator stayed on the splash until ~11 s, and `PlayerConnection` arrived at 11.2 s. Display was logged at 3.9 s. → section 0, Task 1.
  4. Home shelves that come from the network aren't persisted (YouTube home, Daily Discover, community and account playlists, similar recommendations), so their covers wait for those requests. That's audit R-06. The local shelves (Quick Picks, Forgotten Favorites, Keep Listening) come from Room.
- Not observed, from code only: `Artwork` asks for `maxresdefault.jpg` for sizes over 640 px and retries `hqdefault` after a 404 (`core/.../YouTubeUtils.kt:63-70`). 404s aren't cached, so large covers of videos without maxres may cost one network round trip per cold start. Check when opening Now Playing.

### Resume: only the signature timestamp is persisted; the audio cache is deleted
- **Persisted and used:** the signature timestamp, `resolve.sts.stored sts=20702 ageMinutes=557`. No 7 s extraction.
- **Not persisted:** resolved stream URLs (`songUrlCache` is in memory). So every resume re-resolves: WEB_REMIX (`mainClient.request.done ms=487`) plus a speculative VISIONOS request and the early URL check. `resolve.begin` 02:53:25.085 → `resolve.success` 26.166 = **1.08 s** here.
- **Cached audio deleted:** `Ghost cache entry for Rr1Cdli5nE8, re-fetching` → `playerCache.removeResource`. Span `files/exoplayer/2/63.0.…exo` (1,480,991 bytes, written 02:42) was gone after play; `64.358508.…exo` and `64.0.…exo` were written at 02:53. → section 0, Task 2.
- **Preload competes with the tap:** `Preloading stream for hx50R-jMAhQ` at 02:53:24.919, from `onPlayWhenReadyChanged`; its resolve began 110 ms before the tapped song's. → section 0, Task 3.
- **So** the ~1 s resume the user sees = persisted timestamp (working as intended) + a fresh resolve + a fresh audio download. It doesn't reuse a saved URL or saved audio.
- **Unconfirmed side note:** in the first trace, a media PLAY key sent while `MusicService` was still starting left the player idle (session state `NONE`). The queue restore may reset `playWhenReady = false` after an early play request. Recheck with a headset or notification play right at launch.

---

## 8. Task 1 — Cast off the startup path (done 14 September, uncommitted)

### Change
| File | What |
|---|---|
| `playback/MusicService.kt` | `castConnectionHandler` is now a getter over `castHandlerFlow: StateFlow<CastConnectionHandler?>`. `initializeCast()` is replaced by `scheduleCastInit()`: 3 s (`CAST_INIT_DELAY_MS`) after `onCreate`, at the next main-thread idle, `ensureCastInitialized()` starts it. The Cast setting comes from `seedCachedPreferences` (`cachedEnableGoogleCast`), the preference read the service already does, rather than a second blocking `dataStore.get`. Logs `Google Cast initialized in N ms`. |
| `gms/.../CastConnectionHandler.kt` | `initialize()` → `initializeAsync(executor, onResult)`: `CastContext.getSharedInstance(context, executor)` (Cast SDK 22.3.1) loads the module and builds the context on `Dispatchers.Default`; only the session wiring (`attach`) runs on the main thread. |
| `foss/.../CastConnectionHandler.kt` | Stub signature kept in sync (`onResult(false)`). |
| `PlayerConnection.kt` | `isEffectivelyPlaying` follows `castHandlerFlow` (`flatMapLatest`) instead of reading the handler once at construction, which would have left it on the local player forever. |
| `LiquidNowPlaying.kt`, `PlayerMenu.kt`, `AudioDeviceBottomSheet.kt`, `gms/.../CastButton.kt` | Collect `castHandlerFlow` instead of `remember { service.castConnectionHandler }`. This is what broke the earlier attempt. |

No visual change. Retired files (`ui/player/Player.kt`, `MiniPlayer.kt`, `Queue.kt`, `OldPlayerMenu.kt`) still read the handler once; nothing calls them. `CastSessionSheet` reads it at open, which only happens while casting.

**Why not `PreferencesSnapshot`:** the service also starts without the activity (media button, Android Auto), and it already reads the whole preference file in `seedCachedPreferences`, so the value costs nothing there.

### Measured: debug build, `scripts/perf-cold-trace.sh launch`, 3 runs each, DEX pre-verified
| Run | Displayed | Player init → `PlayerConnection` | Cast | GC wait in Cast class loading |
|---|---|---|---|---|
| before-1 | +3,421 ms | 762 ms | on main, inside `onCreate` | none logged; `Skipped 59 frames` right after |
| before-2 | +3,144 ms | **2,091 ms** | on main, inside `onCreate` | **1,037 ms on the main thread**, then `Skipped 120 frames` |
| before-3 | +1,994 ms | 649 ms | on main, inside `onCreate` | none logged |
| after-1 | +3,621 ms | 536 ms | background, done at +10.0 s, 2,213 ms | 1,537 ms on a background thread |
| after-2 | +1,298 ms | 129 ms | background, done at +5.0 s, 420 ms | 209 ms, background thread |
| after-3 | +1,250 ms | 116 ms | background, done at +5.4 s, 994 ms | 330 + 522 ms, background thread |

- **The main-thread stall is gone.** Every `WaitForGcToComplete blocked ClassLinker` line is now on a worker thread (tid ≠ pid), and no `Skipped N frames` follows `Google Cast initialized`.
- **The mini player's connection arrives sooner after the player exists:** 116–536 ms, against 649–2,091 ms before. Section 7's 5.4 s stall was the worst case of the same thing.
- `Displayed` improved in after-2/3 too, but Cast ran after `Displayed` even before the change, so that part is emulator noise, not this change. Don't quote it.
- No crash and no Cast init failure in any after-run log. Screenshots match the before-runs (Home and the mini player; covers are placeholders at that moment in both, see section 7).

### Measured: release, `StartupBenchmark.coldStart`, `None`, 10 iterations, back-to-back
Before: the R-05 APK already installed (built 01:18, same code as HEAD). After: rebuilt with the change. Run 05:05 and 05:20.

| Metric | Before | After |
|---|---|---|
| Time to first frame, median (min–max) | 1,971 ms (1,470–2,130) | 1,638 ms (1,524–1,902) |
| Frames in the window, median | 19 | 20.5 |
| Frame overrun P50 / P90 / P95 / P99 | 78 / 734 / 872 / 994 ms | 60 / 701 / 806 / 857 ms |

**Result: no measurable difference in this benchmark.** Cast never ran before the first frame, because the service binds after it (Step 1), so the time-to-first-frame change is noise; the ranges overlap. Frame overrun moved 4–14%, about this emulator's run-to-run noise. No crash in the minified build. These numbers are far below section 3's (P50 394–446 ms) for the same APK. The emulator was cold-booted today, which is another reason to compare only back-to-back pairs.

`StartupBenchmark` measures frames only until the Home tab appears. Before the change Cast could land inside that window; after it, Cast starts ≥ 3 s after the service and is usually outside it. So a frame-overrun gain here partly means the stall left the window. The debug traces above are the direct evidence that it left the main thread.

### Not verified
- **Cast discovers and connects:** not testable here. The emulator sees no Cast devices, and the Liquid UI has no Cast picker (`CastButton` is only used by the retired `OldPlayerMenu`). What was checked is that the handler initialises (`Google Cast initialized in N ms`) and that the Cast state readers compile against the flow. Check on a phone with a Cast device on the network.
- A saved Cast session (`setResumeSavedSession(true)`) now reconnects about 3 s after the service starts instead of immediately.
- The Cast setting is still read once per service start, as before; toggling it takes effect on the next start.

---

## 9. Task 2 — Keep the cached audio on a cold resume (done 14 September, uncommitted)

### What the removal protected against
Bytes cached under the key `mediaId` come from whichever format the last resolve chose. After a restart there is no URL in memory, so before resolving, the code can't know whether the new resolve returns the same stream. Two streams mixed under one key would corrupt the audio, so it deleted the cache. `AudioQuality` has only `OPUS`, so in practice the risk is a client serving another itag: the index holds AAC (itag 140) and Opus (251) songs side by side.

### Change (`playback/MusicService.kt`, `createDataSourceFactory`)
- Cached bytes without a URL no longer trigger a delete before the resolve; they set `verifyCachedFormat`.
- After the resolve, `ContentMetadata.getContentLength(playerCache.getContentMetadata(mediaId))` is compared with `format.contentLength`.
  - Equal: the cache is kept (`Cached audio kept for … matches the resolved format`).
  - Anything else, including an unknown length: removed as before. That log line still starts `Ghost cache entry for …`.
- The quality-bypass (`_diff`) path is unchanged.

**Why length is a safe fingerprint:** the player-cache index (`databases/exoplayer_internal.db`, table `ExoPlayerCacheIndex…`, metadata `exo_len`) stores a total length for all 40 cached songs, partial ones included. For every song that also has a `format` row, it equals `format.contentLength` byte for byte, for itag 140 and 251 alike.

### Measured (debug build; `scripts/perf-cold-trace.sh seed`, `launch`, `play 856 2130`)
| | Before | After |
|---|---|---|
| Log | `Ghost cache entry for Rr1Cdli5nE8, re-fetching` | `Cached audio kept for Rr1Cdli5nE8: length 4546668 matches the resolved format` |
| Spans deleted | all 4 of cache id 64 (2,083,386 bytes) | none |
| Re-downloaded | under new id 65 (1,035,526 bytes written during play) | nothing written |
| `format` row after play | loudness stored | itag 251, length 4,546,668, loudness −1.56, `playbackUrl` set |
| Playing after (adb poll, coarse) | ~2,250 ms | ~980 ms |

One run each, and the before-run overlapped a Gradle build: don't quote the timing. The cache result is the finding.

**Second codec:** `XxOMwwExptA` is cached as AAC (itag 140). A cold deep link resolved itag 140 again → kept; it played (position 12.15 s after 15 s), with no playback errors, and wrote only a new span past the cached range.

### Not verified
- **The delete branch on a device:** no cached song currently resolves to a different format. It is the same `removeResource` as before, now after the resolve instead of before it.
- **Quality change:** not applicable, `AudioQuality` has only `OPUS`.
- **Not done, optional follow-up:** persisting URL + expiry. Every cold resume still pays one resolve (~0.6–0.8 s here).
### Measured: release, `PlaybackBenchmark`, `None`, 8 iterations, back-to-back (Tasks 2–3 together; the after-build also had Task 4)
| Test | Before (Task 1 build, 05:46) | After (Tasks 2–4, 05:59) |
|---|---|---|
| `freshProcessTapToAudible`: tap → audible, median (min–max) | **1,763 ms** (1,144–2,470) | **785 ms** (742–820) |
| `freshProcessTapToAudible`: `ShinyResolve` median | 975 ms | 533 ms |
| `warmProcessTapToAudible`: tap → audible | failed (`StaleObjectException` in the test's `firstSongResult`, not the app) | 1,527 ms (1,189–1,631) |

- **The fresh-process result is a real improvement:** −55%, and the ranges don't overlap (after max 820 < before min 1,144).
  - This test is exactly the cold resume: the cached song is no longer deleted and re-downloaded (Task 2).
  - Its resolve no longer runs alongside the next song's (Task 3), which is likely why `ShinyResolve` fell too.
  - Tasks 2 and 3 can't be separated in this benchmark; the debug traces in sections 9 and 10 show each one's effect.
- **Warm:** no same-session before. Section 3's 3.5–4.1 s is from another day and emulator state; don't compare.
- **Harness fix:** `baselineprofile/.../ShinyApp.kt` `firstSongResult` now looks the row up again on `StaleObjectException`. It is not yet in the installed test APK; rebuild `:baselineprofile` before the next run.

---

## 10. Task 3 — Next-song preload after audio starts (done 14 September, uncommitted)

### Change (`playback/MusicService.kt`)
- `onPlayWhenReadyChanged` no longer calls `preloadUpcomingItems()`.
- A new `onIsPlayingChanged(isPlaying)` override calls it once audio is actually playing.
- The guard inside `preloadUpcomingItems()` changed from `player.playWhenReady` to `player.isPlaying`. The R-03 rule still holds: nothing is fetched for a queue nobody has played.
  - During continuous playback, `onMediaItemTransition` still preloads, because `isPlaying` stays true.
  - A song that was tapped and is still buffering doesn't preload yet; `onIsPlayingChanged(true)` does it once audio starts.

### Measured (debug build; `seed`, `launch`, `play 856 2130`; tapped song `Rr1Cdli5nE8`, next song `hx50R-jMAhQ`)
| Run | Next-song preload begins | Tapped song resolve begins | Order |
|---|---|---|---|
| Before, 05:30 | 51.516 (resolve 51.545) | 51.615 | preload first, in parallel |
| Before, 05:36 (Task 2 build) | 31.199 (resolve 31.231) | 31.240 | preload first, in parallel |
| After, 05:43 | 57.899, after the tapped song resolved (57.744) and started playing | 56.694 | tapped song first; preload alone, done 58.518 |

### Not verified
- Auto-advance and crossfade on the device. From the code, `onMediaItemTransition` still preloads while playing, and the crossfade player gets this listener too (`nextPlayer.addListener(this)`).
- The release `PlaybackBenchmark` for Tasks 2–3 together is in section 9: fresh-process tap → audible went from 1,763 to 785 ms median.

---

## 11. Task 4 — Crashlytics in release builds (done 14 September, uncommitted)

### Cause
- `firebase-components` 18.0.0 ships `-keep class * implements com.google.firebase.components.ComponentRegistrar` with no member spec.
- AGP 9 runs R8 in full mode, where a class-only `-keep` does not keep the default constructor.
- So `ComponentDiscovery` couldn't instantiate the registrars. The before-build logged `Could not instantiate com.google.firebase.installations.FirebaseInstallationsKtxRegistrar` and no Crashlytics init line.

### Change
`app/proguard-rules.pro`: `-keep class * implements com.google.firebase.components.ComponentRegistrar { <init>(); }`.

### Verified (`x86_64GmsBenchmarkRelease`, R8-minified, first launch after install)
- `Could not instantiate` lines: **0** (before: present).
- `FirebaseCrashlytics: Initializing Firebase Crashlytics 19.0.1 for com.shiny.music`, then `FirebaseInitProvider: FirebaseApp initialization successful`, then `FirebaseCrashlytics: Saved version control info`.

### Watch
Crashlytics now really initialises at process start, which is new launch work. Release `StartupBenchmark`, `None`, 10 runs, 06:02:
- **First frame:** median 1,146 ms (1,060–1,237), against 1,638 ms (1,524–1,902) at 05:20 without Crashlytics.
- **Frame overrun P50 / P90:** 146 / 751 ms, against 60 / 701 ms.
- **Frames in the window:** median 12.5, against 20.5.

**No startup regression is visible:** the first frame didn't get slower. The frame numbers moved both ways with a different frame count, which is the emulator's noise, not a trend.

---

## 12. R-06, R-07, R-08 (14 September, uncommitted)

The session that wrote this code was cut off before measuring it; it was picked up at 16:05. The emulator had shut down with that shell. It was rebooted **with audio**: one boot used `-no-audio` and is not used for timing.

### R-06 — Home snapshot (`viewmodels/HomeViewModel.kt`)
- After a successful network `YouTube.home()`, `saveHomeSnapshot()` writes `files/home_snapshot.json` (temp file + rename): the Home page, Explore page and community playlists, the time, and a SHA-256 of the InnerTube cookie.
- On the next launch, `applyHomeSnapshot()` paints them before `load()`, and only if the account key matches and the snapshot is under 7 days old. The Explicit / video songs / Shorts filters are re-applied, because they may have changed since. Network results replace it as they arrive.
- It isn't written when the launch never got a network answer (offline), so stale content is never re-dated.
- `innertube`: `YTItem` and its subclasses, `Artist`, `Album`, `HomePage` (+ `Chip`, `Section`), `ExplorePage`, `MoodAndGenres` (+ `Item`) are `@Serializable`; so is the app's `CommunityPlaylistItem`.
- Logs (tag `HomeSnapshot`, debug): `applied N sections, M min old, +T ms` and `network home arrived +T ms`, both since process start.
- Not in the snapshot: Daily Discover, account playlists, similar recommendations. The audit's route/scroll anchor/tones are not done.
- Snapshot on the emulator: 19,854 bytes.

### R-07 — one client for stream traffic (`utils/StreamHttp.kt`, new)
- `StreamHttp.client` is now shared by ExoPlayer's upstream (`MusicService.createCacheDataSource`), the resolver's probe (`YTPlayerUtils.httpClient` = `client.newBuilder()` with its own timeouts) and the R-08 pre-cache.
- It keeps the old playback client's IP-version `Dns` (`StreamHttp.ipVersion`, kept current by `MusicService`), `YouTube.proxy` and the proxy authenticator. The proxy is read once, when the client is first used. That is the same as before: the old clients also read it once, and `App` sets it at startup.
- Debug builds log `fix403 http.connection METHOD host=… reused=true|false`.
- Scope: stream traffic only. InnerTube's Ktor client, the lyrics modules and the audit's priority lanes are untouched.

**Verified (debug, 16:17 and 16:20):** the resolver's HEAD probe opens the connection (`reused=false`) and ExoPlayer's GET reuses it (`reused=true`), on three different googlevideo hosts. Before, the two clients could not share a connection at all.

### R-08 — pre-cache the start of the next song (`playback/MusicService.kt`)
- When the preload resolves the next song's URL, `precacheStart()` writes its first `CHUNK_LENGTH` (512 KB) into the player cache through `CacheWriter` and records the stream's total length. The length is what lets Task 2's check keep these bytes after a restart.
- Unmetered networks only. The preload that calls it is already off under Data Saver. Bytes of a different format under the same key are removed first.
- Not done from the audit's R-08: cover/field decode and the touch-down pre-resolve.
- Debug-only marks for skip timing: `fix403 media.transition item=…` (`onMediaItemTransition`) and `fix403 audio.advancing item=…` (`onAudioPositionAdvancing`).

**Verified working (debug, 16:20), one skip each, not an A/B:**
| Skip to | Prepared | Skip → audible |
|---|---|---|
| `0qanF-91aJo` | nothing (see "first song" below) | 1,291 ms |
| `ZHwVBirqD2s` | URL + first 524,288 bytes (pre-cache took 3,149 ms) | 211 ms |

### Found while testing (fixed 16:30, see "Fixes" below; verification pending)
1. **A deep link can be replaced by the restored queue.** `playQueue` (MusicService ~1648) loads a queue's initial status on IO and applies it without checking that no newer `playQueue` happened meanwhile. At 16:10:57 a cold deep link started `dQw4w9WgXcQ`. The restored queue's initial status (no preload item → `setMediaItems`) landed at 16:11:02 and replaced it with `hx50R-jMAhQ`. The restore guard (`mediaItemCount == 0`, ~1169) only runs before the restore starts. The window was widened by a Gradle build running alongside, but it exists on phones too.
2. **The first song of a tapped queue never preloads its successor.** A `YouTubeQueue` with a preload item adds its radio songs about 1 s *after* audio starts. `onIsPlayingChanged(true)` (Task 3) fires while there is no next song, and nothing retries until the next transition. So neither the URL preload nor the R-08 pre-cache helps the first skip of a queue started from search, a deep link or a radio. The same gap existed before Task 3 (the play request came even earlier).

### Fixes (`playback/MusicService.kt`)
1. `playQueue` numbers each call (`playQueueGeneration`) and drops an initial status whose call is no longer the latest. It doesn't compare `currentQueue`, because `ensureUpNext` (called while the Liquid queue is open) swaps that for a radio mid-load.
2. `onTimelineChanged(PLAYLIST_CHANGED)` calls `preloadUpcomingItems()` while playing. So that the extra calls don't thrash, `preloadUpcomingItems` returns early when a job for the same upcoming songs is still active (`preloadTargets`). That also stops a resume or rebuffer from cancelling a pre-cache part-way, which was never restarted. A quality change cancels the job first, so it still re-resolves.

### R-08 A/B result (debug, 16:49–17:04)
Method: `r08-ab.sh` skipped through a Rick Astley radio, marking Wi-Fi metered before every other round (`cmd netpolicy set metered-network AndroidWifi true`). Metered switches off only the pre-cache; the URL preload still runs. Skip → audible = `media.transition` → `audio.advancing` on the device clock.

**Label by what was on disk, not by the round.** Several radio songs were already in the player cache from earlier plays. Classify each skip by its first stream GET after the transition: under 35 ms = nothing on disk; 50–155 ms plus a `Pre-cached start` log = the pre-cached 512 KB; after the audio started, or no GET at all = more cached from earlier (excluded).

| Next song at the skip | Skips | Skip → audible, median (min–max) |
|---|---|---|
| URL preloaded, nothing on disk (= before R-08) | 8 | **658 ms** (602–1,108) |
| URL + first 512 KB pre-cached (= after) | 11 | **262 ms** (168–343) |

**Result: −60%, and the ranges don't overlap.** Each pre-cache took 168–307 ms and wrote 524,288 bytes. The audit inferred "≤ 300 ms"; the median meets it on the emulator. Debug build, one emulator session.

### R-06 A/B: first run (16:44) found two problems
| Launch | Displayed | Snapshot applied | Network Home arrived |
|---|---|---|---|
| No snapshot ×4 | 1.24–1.41 s | — | +4.9–6.5 s |
| Snapshot ×4 | 1.27–3.34 s | +545–2,213 ms | +7.1–8.1 s |

1. **The snapshot held no shelves:** `applied 0 sections` every time. Signed out, `YouTube.home()` returns only chips (11) and a continuation; the shelves come from the continuation, which `LiquidHomeScreen` requests once the list is short. The snapshot was saved before that, so it had chips and Explore (24 releases, 49 moods) but 0 sections. The screenshot at 2.5 s shows just the chips row gained.
2. **Every launch ran the Home load twice.** `MainActivity.kt:839` holds an activity-scoped `HomeViewModel` for the account avatar/name, and `LiquidHomeScreen` got its own screen-scoped one. Each ran `init`: local shelves, the ~30-request network phase, `syncUtils.tryAutoSync()`, `accountInfo()`, and the snapshot apply and save. Logs show 2× `applied` and 2× `network home arrived` per launch, in both arms, so this predates R-06. Both instances also wrote the same `.tmp` file. (`AccountSettingsScreen` creates a third instance when opened; not on the launch path, left alone.)

**Fixes (17:05):**
- `HomeViewModel.loadNetworkDataPhase`: when the first page has no sections, fetch up to 3 continuation pages before assigning `homePage`. Snapshot shelves aren't blanked by a chips-only page, and the save has shelves.
- `saveHomeSnapshot`: never saves a page without sections, stores `continuation = null` (a stale token would page before the network answers), and writes through `File.createTempFile`. Logs `saved N sections`.
- `NavigationBuilder`: the Home route uses `hiltViewModel(viewModelStoreOwner = activity)`, the same instance as `MainActivity`'s. `HomeViewModel` has no `SavedStateHandle`, so the scope change loses nothing.

### R-06 A/B after the fixes (debug, 17:10–17:14)
Method: `r06-ab.sh`: cold launches alternating snapshot file deleted (= before R-06) / present (= after), on one debug build, DEX pre-verified. Times since process start.

| Arm | Displayed | Network shelves on screen | Home loads per launch |
|---|---|---|---|
| No snapshot ×4 | 1.27–1.39 s | at network arrival, **+5.0–5.7 s** | 1 (was 2) |
| Snapshot ×4 | 1.31–1.69 s | from the snapshot, **+628–887 ms** (2 sections) | 1 (was 2) |

- **Home loaded twice → once:** each launch logs one `network home arrived` and one `saved`. The first run (16:44) logged two of each, in both arms. The second load also repeated `tryAutoSync` and `accountInfo`.
- **Snapshot:** now 31,659 bytes: chips, 2 shelves, Explore. It is applied about 4.5 s before the network answers, and before the first frame, so there's no layout jump at +5 s.
- **What a listener sees at 2.5 s** (screenshots `r06b/none-1.png`, `snap-1.png`): the chips row is already there with the snapshot, and absent without it. The two shelves are further down this Home, below the first screen. Covers are placeholders in both arms, including local shelves: that is Coil decoding from disk after process start (section 7), not R-06.
- `Displayed` didn't change (the ranges overlap).
- Not measured: how many requests the double load cost (no per-request log), and whether the list jumps on a signed-in account, whose first page does have sections.

### R-07 release benchmark
- R-07: release `PlaybackBenchmark`, `None`: the installed 05:59 APK (Tasks 2–4) against the new build, back-to-back.
  - **Before, 16:26–16:28** (05:59 APK, test APK rebuilt 16:12 with the `StaleObjectException` fix; both tests passed): fresh process tap → audible **837 ms** median (725–1,014), `ShinyResolve` 568 ms; warm process **1,440 ms** (1,167–1,554), `ShinyResolve` 540 ms.
  - **After, 16:39–16:42** (new build: R-06/07/08 + both queue fixes; same test APK; both tests passed):

    | Test | Before | After |
    |---|---|---|
    | Warm process: tap → audible, median (min–max) | 1,440 ms (1,167–1,554) | **1,213 ms** (1,064–1,290) |
    | Warm process: `ShinyResolve` median | 540 ms | 573 ms |
    | Fresh process: tap → audible | 837 ms (725–1,014) | 836 ms (720–951) |
    | Fresh process: `ShinyResolve` median | 568 ms | 575 ms |

    - **Warm: −16%, likely from R-07, not proven.** The resolve didn't change, so the gain is after it, where the GET now reuses the probe's connection. But the ranges overlap (after max 1,290 > before min 1,167), and one pair at −16% is barely above this emulator's ~10% noise. Repeat the pair, or run it on a phone, before quoting it.
    - **Fresh: no change, as expected.** Task 2 keeps this song's audio in cache, so playback opens no stream connection that could be reused.
  - In Git Bash with `MSYS_NO_PATHCONV=1`, give `adb install` a Windows path (`D:/music/...`): `/d/music/...` fails with `failed to stat`.


---

## 13. Covers at cold start, R-09, Account settings (26 September, uncommitted)

**Account settings (follow-up 2a).** Already fixed and committed in `68a3098`: `AccountSettingsScreen` and the Home destination both take the activity's `HomeViewModel`. Nothing to do.

**Covers at cold start.** Measured first (debug, emulator, `HomeT` timing logs, now removed): the ViewModel existed at ~550 ms but the feed build (a ~50 ms Room read) waited for Home to *subscribe* (`stateIn(WhileSubscribed)`), at ~1.5 s; Home composed skeletons, then the feed at ~2.0 s, then covers faded in from disk.
- `HomeViewModel.init`: `viewModelScope.launch { feed.first { it != null } }` starts the build with the ViewModel. Home's first composition now has the feed (no skeleton pass): feed on screen 1.62–1.87 s vs 1.96–2.08 s (3 v 3).
- `ui/liquid/CoverWarmup.kt` (new) + `Artwork` + `MainActivity.onCreate`: for the first 5 s of a process, `Artwork` records covers it showed inside the window (URL + laid-out px size, largest size per URL, max 20) → `files/cover_warmup`. Next cold start decodes those on IO into Coil's memory cache (disk only, never network; software bitmaps, so no GPU work while the first frame's EGL context is created). Same URL + size + `Scale.FILL` + `Precision.INEXACT` as `AsyncImage` = memory hit, which Coil draws without the 220 ms fade.
- Pitfall found: Coil's `enqueue` always starts on `Dispatchers.Main.immediate`, so warmups waited ~1 s behind activity startup; `execute` from an IO coroutine runs in the caller's context (done 0.6–1.0 s).
- Result, screenshot A/B (4 interleaved pairs, pixel-variance on the resume card + chart row covers): before, the page showed with empty tiles for 3 frames (~1 s) in 3 of 4 runs, covers at median ~3.6 s; after, covers are in the **same frame as the page in every run**, median ~3.05 s. Logs confirm every visible cover (hero, chart rows, resume, mini player) is a `MEMORY_CACHE` hit. Caveat: the splash now holds until the full page (the first composition is heavier), so "first non-splash frame" is later in debug; "complete page" is earlier.

**R-09 (main-thread blocking).** Benchmark release build, Perfetto trace of a cold start + 2 rounds of tab switching + scroll, plus simpleperf.
- Navigation: 174 frames over 16.6 ms; inside them the main thread ran app code 174 ms total (3%) and waited 95% in `draw-VRI → postAndWait` (RenderThread). Startup's long waits (551 ms in the first traversal, 253 ms postAndWait) line up with RenderThread / GrallocUploadThread doing `Creating EGLContext` (330 ms) and hardware-bitmap uploads (emulator GPU over qemu-pipe).
- App-attributable main-thread time at startup: App.onCreate ~20 ms (almost all `CipherDeobfuscator.initialize` — synchronous by design, "order is load-bearing"), MainActivity.onCreate ~32 ms (incl. `NetworkConnectivityObserver` binder calls ~9 ms via `ListenTogetherClient.<init>`), MusicService.onCreate ~25 ms (DataStore `runBlocking` ~2 ms). None is a visible freeze; **no change made**.
- Tooling notes: frame slices sit at depth 66 in these traces (an unbalanced app section), so never filter `doFrame` by `depth=0`. `sched_waking` produced no rows on this emulator (no waker data). simpleperf `--trace-offcpu` on the emulator attributes some off-CPU samples to the wrong thread's stack: use it for Java frame names only, not for absolute off-CPU time.

**Network list (item 5).** Already done and phone-measured earlier the same day (fast-start pass: hedge, stored stream URLs, touch prewarm, Home precache, weak-signal stream, Cronet). Not repeated.
