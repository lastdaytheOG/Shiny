# SHINY performance pass — 24 September 2026

MEASURE → OPTIMISE → MEASURE. Nothing committed or pushed. `PERF_AUDIT_HANDOFF.md` (21 Sep, 79/100)
and `PERF_HANDOFF.md` (14 Sep) are the earlier passes; this file supersedes neither, but it
**corrects** several of the 21 Sep findings (section 2).

## 0. Outcome in one paragraph

A fresh baseline with Perfetto and simpleperf found that SHINY is already close to what this
emulator can measure. Scrolling, idle, background playback, memory and the player's per-frame work are all
lean. The two headline problems from 21 Sep (Home/Search scrolling, the living-artwork loop) do
not hold up under clean measurement. One real defect was found and fixed: **the shipped baseline
profile predated the Liquid rebuild** (zero rules for the Home feed, Library, Now Playing modes,
living artwork). Its fix produced **no measurable gain on this emulator** and is kept as Phase 15
hygiene (section 4). No app source code was changed.

## 1. Environment

AVD `ShinyStartup` (`emulator-5554` this session; BlueStacks absent), API 36, x86_64, 1080x2400 @420,
60 Hz only, 3 GB RAM. APK: `x86_64GmsBenchmarkRelease` (R8 + resource shrink + baseline profile,
debug-signed, profileable). Build:
`./gradlew :app:assembleX86_64GmsBenchmarkRelease "-Dorg.gradle.jvmargs=-Xmx6g -XX:MaxMetaspaceSize=1g" -Pkotlin.compiler.execution.strategy=in-process --no-daemon`

**Emulator caveats learned this session (read before measuring):**
- **The debug build plays in the background.** `com.shiny.music.debug` was *playing* during the first
  scroll/startup captures. `am force-stop com.shiny.music.debug` before every session.
- **The 3 GB AVD runs under memory pressure.** About 850 MB of zram swap is in use right after boot, and
  about 1.1 GB after a few hours of benchmarking. Cold start then drifts from ~790 ms to ~1,900 ms, and
  `system_server`/SystemUI crashed once. Reboot, wait for loadavg < 1, and only compare **interleaved**
  A/B pairs. A bigger AVD (4–6 GB) would make startup A/Bs usable.
- `/data` was 93% full (old traces); `INSTALL_FAILED_INSUFFICIENT_STORAGE`. Delete
  `/data/misc/perfetto-traces/*` after pulling.
- `simpleperf` "read()" hot spots on dispatcher threads are the emulator GPU pipe
  (`qemu_pipe_read ← eglClientWaitSyncKHR`, Coil hardware-bitmap uploads), **not file I/O**.
- Cold-start first frame is dominated by host-GL: `Creating EGLContext` ~216 ms, then per-program
  `shader_compile → cache_miss → driver_link_program` (~280 ms). Not app work; not representative
  of a phone, whose Skia shader cache persists.

## 2. Baseline (build A = working tree as found, clean conditions)

| Metric | Result | vs 21 Sep |
|---|---|---|
| Cold start (`am start -W`, n=12) | **787 ms** median (743–891) | same (800) |
| Warm / hot | 149 / 196 ms (noisy ±50) | 21 Sep numbers were host-loaded |
| Cold start, app-side main-thread CPU on critical path | ~120 ms (bindApplication 38 running, first frame 43 running) | new |
| Launch settles (last frame) | ≤ 4 s after process start, then 0 frames | new |
| Home scroll, slow-UI-thread frames | **0–1%** (3 runs), p50 17–18 ms | **21 Sep said 34.6% — does not reproduce** |
| New / Library / Search landing / **remote Search results** | 0–0.9% slow UI | **21 Sep said 33.9% for Search results — does not reproduce** |
| Player Stage, playing, no input | 32% of one core (RT 21.7%, main 4.3%), ~60 fps | 35.3% |
| Player Lyrics (synced) | ~31% | 42.7% |
| Player Queue | 17% (~26 fps background drift) | 25.6% |
| Paused player | 0.5–0.9%, **0 frames** | same |
| Backgrounded, playing | **5.5%**, 0 frames | 10.5% |
| Idle Home (paused session) | 0.1%, 0 frames | same |
| Tap → audible, needs resolve | **915 ms** = 46 tap→resolve + 599 resolve + 270 first bytes/decoder | n=1 |
| Tap → audible, already resolved | 251 / 303 ms | new |
| Memory, 12 tab cycles from warmed state | **+1.5 MB**, trim → below start (134 MB PSS) | no leak (same) |

Why 21 Sep's scroll numbers were wrong: that capture ran on a loaded host (and plausibly with the
debug build playing). Same protocol, quiescent host → ~0%.

## 3. Findings that were investigated and rejected — with evidence

**Living artwork (21 Sep "strongest finding").** Traced Stage playback (`A-stage`):
- Glass surfaces sample `playerBackdrop` = the background only, so they re-blur only on the 25 fps
  background ticks (`flush layers` on 159/252 frames), never for the artwork.
- An artwork-only frame costs **~2.4 ms of real RenderThread work** (7.34 ms draw, 4.91 ms of which is
  the emulator's `eglSwapBuffers` wait). Main thread per frame: `animation` 0.53 ms, `Record View#draw`
  0.25 ms, `Recomposer:recompose` 0.14 ms — no stray recomposition, no big re-record.
- "Hold one RenderEffect and mutate its uniforms" is **not possible**:
  `RenderEffect.createRuntimeShaderEffect` snapshots the uniforms when it is created.
- Therefore its cost is per-frame overhead that scales with frame rate. The only lever is a lower
  frame rate, which the user ruled out. **No change.** (On phones with partial-update support the
  damage is the artwork rect only, so the emulator overstates this; real-device test R3.)

**Main-thread `runBlocking` DataStore reads (21 Sep 5.3).** Confirmed on the main thread in
`MusicService` creation: 58 short sleeps = 15 ms of a 37.5 ms `serviceCreate`. It runs at first idle
*after* first frame, once per process: at most one dropped frame during launch settle. Below the
"meaningful improvement" bar. **No change.**

**Home feed rebuild.** `HomeViewModel`: DB loads keyed on `homeInvalidation()` (one multi-COUNT query,
`distinctUntilChanged`), `mapLatest`, build on `Dispatchers.Default`, interest changes thresholded at
5%. No whole-feed rebuild on small changes. **No change.**

**Stream resolve (599 ms).** The loader thread is asleep 598 of 599 ms — network. MAIN_CLIENT and the
first stream client already run concurrently, NewPipe is skipped when a direct URL exists, sts is
cached. PoToken generation (the one sequential step) returns in 0 ms ("BotGuard asset is not packaged").
**Nothing left to overlap.**

**Cold-start "57% of main thread runnable-not-running".** This is CPU contention on 4 emulated cores,
mostly from RenderThread GPU setup and Coil hardware uploads (emulator). Deferring app work
would not change it measurably. **No change.**

**DB indexes** (`song(totalPlayTime)`, covering `event` index from 21 Sep): best measured win is 0.5 ms on
a background query, and it needs a schema migration. Not worth the migration risk. **No change.**

## 4. The one change: baseline profile regenerated

**Defect:** `app/src/main/generated/baselineProfiles/*` were generated 15 Sep. Rules for
`HomeFeedBuilder`, `HomeSections`, `HomeHero`, `HomeTaste`, `HomeSignalsLoader`, `LiquidLibraryScreens`,
`LiquidLivingArtwork` = **0**; `LiquidNowPlaying` = 5. The generator never opened Now Playing,
New or Library.

**Changes (test module + generated data only, no app code):**
- `baselineprofile/.../BaselineProfileGenerator.kt` — the listening journey now visits Now Playing
  (Stage → Lyrics → Queue) while music plays, then flings New and Library (lists looked up again per
  fling; they re-render and went stale).
- `baselineprofile/.../ShinyApp.kt` — `visitNowPlaying()`, driven by display-relative position (the
  Liquid sheet is not in the UiAutomator tree).
- `baseline-prof.txt` = **union** of the old rules and the new journeys (49,909 → 60,462; nothing dropped,
  because a single run did not revisit some still-live paths such as Ktor TLS or Firebase Sessions). `startup-prof.txt` =
  the new startup journey as generated (40,508 → 42,502; launch path at parity or better).
- New coverage: LiquidNowPlaying 280, LiquidLyrics 137, LiquidQueue 80, LivingArtwork 46, Home feed
  65–79 per file, New 97, Library 42.

**Measured effect (A = old profile, B = new; each prepared as a fresh install: `compile --reset` →
ProfileInstaller broadcast → `speed-profile`):**

| | A | B |
|---|---|---|
| traced cold start, n=4 | 1,843 (1,773–1,908) | 1,678 (1,537–1,806) |
| untraced pairs, n=10 each (swap-pressured AVD) | 1,855 / 1,962 | 1,912 / 1,998 |
| main-thread CPU: launch / first scroll / first player open | 130 / 47 / 50 ms | 129 / 42 / 54 ms |

**Verdict: no measurable improvement on this emulator.** The new code runs mostly off the main
thread, and a fast x86 host hides interpreter cost. It is kept anyway as Phase 15 hygiene: the old profile no
longer describes the app, and the union can't regress any path. **If the phone test R1 also shows
nothing, it can be reverted with `git checkout -- app/src/main/generated/baselineProfiles`.** The
generator change should stay regardless.

Regenerate later with the procedure in section 6 (not `:app:generateBaselineProfile`: Gradle's test
runner uninstalls the app, which wipes the emulator's realistic data).

## 5. Tests that need a physical phone

| # | Test | Why the emulator can't answer it |
|---|---|---|
| R1 | Cold start after a fresh install/update, old vs new profile (`startup.sh cold 15` after `prep.sh`) | Interpreter cost is hidden by a fast x86 host; the AVD's swap pressure makes startup noisy by ±40% |
| R2 | Home / Search / Library scroll at 90/120 Hz (`frames.sh`) | Panel is 60 Hz only; host-GL stalls |
| R3 | Stage vs Queue CPU and GPU while playing (`idle.sh`) | Emulator redraws the full window; phones use partial updates (damage = artwork rect) |
| R4 | First-open stalls of Now Playing / first scroll (270–530 ms frames here) | They are `shader_compile` cache misses on host GL; phones keep a persistent shader cache |
| R5 | Tap → audible on never-played tracks, n ≥ 10, Wi-Fi and cellular | Network-bound; emulator network is the host's |
| R6 | Background playback battery over 1 h, screen off (`dumpsys batterystats`) | No battery or thermal model |
| R7 | Memory on a 4 GB phone with a 5,000-song library | Emulator library is small |

Build for arm64: `./gradlew :app:assembleArm64GmsBenchmarkRelease -Pshiny.benchmark.abi=arm64`.

## 6. Scripts (the scratchpad does not survive; recreate from here)

All scripts `source env.sh`:
```bash
export PATH="$PATH:/c/Users/Lenovo/AppData/Local/Android/Sdk/platform-tools"
export MSYS_NO_PATHCONV=1; export ANDROID_SERIAL=emulator-5554
PKG=${PKG:-com.shiny.music}; ACT=$PKG/com.shiny.music.MainActivity
```
Under `MSYS_NO_PATHCONV=1`, never use `/tmp` or `/c/...` host paths with adb.exe; use `pwd -W` / `C:/...`.

- `startup.sh`, `frames.sh`, `cpu.sh`, `parse2.py`: as `PERF_AUDIT_HANDOFF.md` sections 6.1, 6.2, 17.1, 17.2.
- `idle.sh <label> [s]`: `dumpsys gfxinfo reset`, `cpu.sh`, then read `Total frames rendered`.
- `prep.sh <apk>` (fresh-install compile state):
  ```bash
  adb install -r "$1"; adb shell am force-stop $PKG; adb shell cmd package compile --reset $PKG
  adb shell am broadcast -a androidx.profileinstaller.action.INSTALL_PROFILE -n $PKG/androidx.profileinstaller.ProfileInstallReceiver
  sleep 2; adb shell am force-stop $PKG; adb shell cmd package compile -f -m speed-profile $PKG
  ```
- `trace.sh <name> <ms> <cmd…>`: pushes a Perfetto text config (sched incl. waking/blocked_reason,
  frametimeline, atrace am/wm/gfx/view/dalvik/binder_driver/input/res/ss/aidl/database,
  `atrace_apps: "com.shiny.music"`), runs `perfetto --txt -c - --background`, runs the command, pulls
  the trace. Analysed with `pip install perfetto` (`TraceProcessor`); useful queries: `android_startups`,
  `thread_state` per main-thread slice (the S/R/D split found the EGL and contention facts), and
  `descendant_slice`.
- simpleperf (APK is profileable):
  `adb shell "/data/local/tmp/simpleperf record --app com.shiny.music -g -f 2000 --duration 6 -o /data/local/tmp/perf.data"`,
  then `binary_cache_builder.py` and `report_sample.py` from `ndk/27.0.12077973/simpleperf`.
- Profile generation without wiping data:
  `./gradlew :app:assembleX86_64GmsNonMinifiedRelease :baselineprofile:assembleX86_64GmsNonMinifiedRelease`,
  `adb install -r` both (`-t` for the test APK), then per test
  `adb shell am instrument -w -r -e class com.shiny.music.baselineprofile.BaselineProfileGenerator#<test> -e targetAppId com.shiny.music -e androidx.benchmark.enabledRules BaselineProfile com.shiny.music.baselineprofile/androidx.test.runner.AndroidJUnitRunner`.
  **Pull the outputs after each test**: each run clears `/sdcard/Android/media/com.shiny.music.baselineprofile/`.
  Merge as described in section 4.

## 7. Side effects on the emulator

The generator's journey searched "Radiohead" and played it several times on `com.shiny.music`, so
that install's history and Home personalisation now include it. Build B (current tree + new profile)
is what's installed. `always_finish_activities` = 0, `wm size`/`density` untouched.

## 8. Real phone results (realme RMX5108, Android 16, arm64, 60/90/120/144 Hz) — same day

Build: `./gradlew :app:assembleArm64GmsBenchmarkRelease -Pshiny.benchmark.abi=arm64`. Scripts as in section 6 with
`ANDROID_SERIAL=UCDADEIFV445AYJ7`; tab bar is at y=2232 and the mini player at y=2040 on this phone.
realme blocks `pm grant` over adb (tap Allow once). The phone reported Thermal Status 3 while charging.

| Metric | Result |
|---|---|
| Cold start, old vs new profile (n=12, interleaved) | A 360.5 / 340.5 ms, B 340 / 350.5 ms — **no difference** |
| First use (n=3), old vs new profile | startup 411 vs 387 ms; main-thread CPU launch 487/448, scroll 710/711, player 331/332 — **no difference** |
| Scrolling Home/New/Library (B) | 0.4–0.8% slow-UI frames, p50 10–11 ms (up to 120 Hz) |
| Paused Home | 2% of a core, 0 frames |

**Baseline profile regeneration: reverted** (no gain on the phone either). The generator improvements stay.

### The real defect the emulator hid: off-screen "now playing" bars

`NowPlayingBars` (`LiquidComponents.kt`) ran its `rememberInfiniteTransition` whenever the row was composed. On
Home the playing song sits in the Charts section, which is composed while below the fold. Each tick redrew the
window and re-blurred the glass tab bar and mini player (`drawLayer 390x139` 3.9 ms + `393x93` 3.2 ms of RenderThread,
~20 ms per frame in total), at up to ~103 fps. **Fix:** the bars animate only while `boundsInWindow()` (clipped by every
parent) is non-empty; off screen they draw the resting bars.

| Home while playing, bars row off screen | Before | After |
|---|---|---|
| run 1 | 138.8% of a core, 827 frames / 8 s | **22.0%, 0 frames** |
| run 2 | 141.1%, 822 frames | **23.5%, 0 frames** |

With the bars visible the animation is unchanged (736 vs 730 frames / 8 s).

Still open (measured, not fixed): Home's bars keep animating **under the open player**, because the sheet is
opaque but nothing tells Home it is covered. The player on the phone costs ~170% of a core in every mode, and 31% of its RenderThread
samples are `drawShadow` → `SkIDChangeListener::List::add` (Skia's per-path shadow-cache listener list, which grows
when a path shadow is re-cached every frame). The source node was not isolated.

## 9. RESUME HERE — push to 90–92 (paused mid-A/B, phone disconnected by the user)

On `continue`: reconnect the phone (`adb devices` → `UCDADEIFV445AYJ7`), then run the C vs D player A/B with the
state-verified driver. The blind tap sequence drifted out of sync with the UI and produced void numbers, so trust
only cells where `ensure.sh` printed `ok`.

**Uncommitted code changes (all under test, none measured on the phone yet except #1):**
1. `ui/liquid/LiquidComponents.kt` `NowPlayingBars`: animates only while `boundsInWindow()` is non-empty. **Measured:**
   Home while playing, row off screen, 140% → 22% of a core, 0 frames. KEEP.
2. `ui/liquid/player/LiquidNowPlaying.kt` `NowPlayingBackground`, the second (alpha 0.55) field image:
   `compositingStrategy = ModulateAlpha`. Removes the `alpha caused saveLayer 3480x3480` (~4 per frame). Same pixels,
   because it is a single bitmap draw. NOT YET MEASURED.
3. `MainActivity.kt`, the content `Row`: `graphicsLayer { alpha = 0 when playerConnection != null && sheet.isExpanded }`, so
   the page under the fully open (opaque) player is not drawn. NOT YET MEASURED. Expected small (the over-Home vs
   over-Library A/B showed about 8%).

APKs (scratchpad, session-local; rebuild if missing): `C-arm64.apk` = #1 only, `D-arm64.apk` = #1+#2+#3.
Build: `./gradlew :app:assembleArm64GmsBenchmarkRelease -Pshiny.benchmark.abi=arm64`.

**Valid C baseline (player, playing, 10 s, phone):** Stage 125% of a core / 1,009 frames, Lyrics 149% / 519,
Queue 189% / 872.

**Driver** (`ensure.sh <home|stage|lyrics|queue>`): screenshot → classify with PIL
(pixel (540,190) > 200 = light Home; (155,2234) > 200 = Lyrics lit; (924,2234) > 200 = Queue lit; else Stage) → tap
mini player (480,2040) / Lyrics (136,2234) / Queue (943,2234) / BACK, then retry up to 6 times. Measure with `idle.sh`.

**Next after the A/B:** the player's `drawShadow` slow path (31% of RenderThread on the phone,
`SkIDChangeListener::List::add`). The only elevation caster is `FlyingArtwork` (shadowElevation 30 dp, LiquidNowPlaying ~875).
The emulator took the fast path (`ShadowCircularRRectOp`); the phone does not. Find out why, and find a same-looking fix. Then
re-score. Target 90–92 needs #2/#3 + the shadow, then phone measurements of playback/DB/network.

## 10. Player A/B results on the phone (state-verified) — session end

Driver: `ensure.sh` (grab-handle classifier, works in light and dark mode) + playback started by deep link
(`watch?v=Rr1Cdli5nE8`) and confirmed via `dumpsys media_session` (the state line is 7 lines below `package=`).

| Player, playing, 10 s | C (bars fix only) | D (+ background ModulateAlpha + hide covered page) |
|---|---|---|
| Stage | 134 / 129% | **124 / 119%** (−8%, consistent) |
| Lyrics | 181 / 170% | 177 / 172% (no change) |
| Queue | 156 / 148% | 154 / 158% (no change) |

Profiling D in Queue mode: `drawShadow`, `AddGenIDChangeListener` and `saveLayer` are now **0%**. The slow-path shadows were
Home's rotated hero covers (`.shadow` + rotation → no Skia fast path), drawn under the open player and inside the glass
backdrops at several scales. What remains is `renderLayersImpl` at 78% of RenderThread, a third of it `glBindFramebuffer`:
every glass surface re-blurs its backdrop in its own framebuffer on each background drift tick.

**Tried and reverted (no gain):** re-recording the glass drop-shadow/highlight layers only when their inputs change
(E vs D: Stage 125 vs 114/123, Lyrics 174 vs 175, Queue 151 vs 155).

**Kept (uncommitted):** `NowPlayingBars` visibility gate; `ModulateAlpha` on the second background image; content `Row`
alpha 0 while the player is fully expanded (MainActivity).

**Next lever (not started, needs the user's go-ahead because it is a structural change in the vendored backdrop library):**
the player's glass buttons all refract the same background with the same blur, but each blurs it separately. One shared
pre-blurred background layer, sampled by every button, would replace N blurs + N framebuffer switches per drift tick with one.
Same pixels in principle; medium risk.

## 11. Shared glass blur — built, measured on the phone, REVERTED (26 September)

Built behind `debug.shiny.sharedglass`: one colour+blur layer for all `GlassKind.Clear` player controls, each control
keeping only its lens. Phone (realme RMX5108, `.debug` build, same APK, switch alternated, player open and playing, 10 s):

| mode | old app / RenderThread | shared app / RenderThread |
|---|---|---|
| Stage (5 v 5) | 136–137% / 48–49% | 135–136% / 48–49% |
| Lyrics (3 v 3) | 143–144% / 41–42% | 143–145% / 41–42% |
| Queue (3 v 3) | 119–121% / 46–47% | 118–120% / 45–46% |

No measurable gain. The RenderThread profile shows why: the shared path was active (`SkComposeImageFilter` vanished), but
ALL image-filter work (blur + colour + lens) is only ~5% of RenderThread, the blur ~2%. The cost is the number of
offscreen layers: `renderLayersImpl` ~49%, `glBindFramebuffer` ~23%. Each glass control still owns its layers
(clip/offscreen compositing + the lens layer), so the count did not change. Reverted; the experiment is kept at
`C:\Users\Lenovo\ShinyBackups\shared-glass-2026-09-26\experiment\`.

**What would actually move it:** fewer offscreen layers per glass control, e.g. drawing the lens as a shader brush over a
shared blurred image instead of a RenderEffect on a layer. Bigger change; the player's look would need re-verifying.

**New finding, both arms:** Queue mode, idle with music playing, renders only ~24 fps (240–252 frames in 10 s) with
~85% janky frames, against ~62 fps and ~1.6% janky in Stage. Not investigated yet.

Phone tooling: the NDK arm64 simpleperf fails here ("cpu-clock not supported"); use the phone's `/system/bin/simpleperf`
with `--app com.shiny.music.debug`. The phone's `com.shiny.music` is release-signed: never install a debug-signed
benchmark build over it (would need an uninstall = data loss).

## 12. Audit follow-up — MEASURED on the phone (26 September, evening)

realme RMX5108, `.debug` build, Jio 5G (Wi-Fi was off), display at 60 Hz, Thermal Status 3. One APK with a temporary
`debug.shiny.ab old|new` switch (removed afterwards), fresh app start per cell, old/new alternated, the mode checked
by screenshot before every cell. Song: `Rr1Cdli5nE8` ("Like That", dense word-synced rap).

**`/proc` CPU time misleads here.** With less work the governor lowers clocks, so the same thread shows *more* time
(Lyrics main thread 70% → 75%). Judge by instructions/cycles: `/system/bin/simpleperf stat -e instructions,cpu-cycles
--per-thread --app com.shiny.music.debug --duration 10` (`-p PID` is refused; `--app` works, ignore its
"child command" errors).

| 10 s, playing | old insn / cycles | new insn / cycles | change (cycles) |
|---|---|---|---|
| Stage (Home's playing row under the player) | 15.0–15.1 G / 20.6–21.0 G | 11.1 G / 14.4–14.5 G | **−30%**, RenderThread −50% |
| Lyrics | 18.4–18.7 G / 23.9–24.3 G | 13.1–13.3 G / 15.8–16.0 G | **−34%**, main −17% insn, RenderThread −53% |
| Ambient Mode, controls hidden | 13.2–13.4 G / 16.7 G | 10.5–10.7 G / 13.3–13.4 G | **−20%**, frames unchanged (~62 fps) |

Stage's gain is `LocalPageCovered` alone (the bars made the glass behind the page re-record every frame).

**Lyrics design, measured:** a `derivedStateOf` per word cut RenderThread but not the main thread. What shipped is one
`wordPhase` value per line (started/finished word counts packed in a Long); only the word being sung reads the clock.

**gfxinfo "Janky frames" rises in Lyrics (≈2% → ≈18%) and is not visible jank.** framestats: the old build drew every
vsync (identical frames included), so every frame had a 2-vsync deadline. The new one idles for 1–2 vsyncs between
words; the first frame after idle gets a 1-vsync deadline (16.6 ms) and the player's ~14 ms of GPU issue misses it.
Nothing moves during those gaps, so it only delays a word's sweep start by one frame. p50/p90 frame time improved
(25/32 vs 27/40 ms).

**Skip on mobile data** (Jio 5G, `files/exoplayer` emptied before each run, same queue, `media.transition` →
`audio.advancing`): old 273/536/522, 330/588/499 ms (median ~510); new 305/329/410, 322/372/375 ms (median ~350).
The next song's 512 KB start pre-caches in 86–265 ms on 5G.

**Not measured:** the network timeouts and write-retry rule (need a dead/flaky link), the expired-URL preload fix
(needs a 5.5 h session), album/artist first-open placeholder, suggestion/lyric-search debounce, 226 px row covers.

**Still open, needs the user's go-ahead:** Queue-mode jank = offscreen layers per glass control (§11). Fewer layers
means drawing the lens as a shader brush over a shared blurred image — a change to the vendored backdrop library and
a visual re-check of the player.

## 13. Glass controls: 4 offscreen layers → 1 — KEPT, phone-measured (26 September, night)

Each `liquidGlass` control drew into four framebuffers: the drop shadow (Offscreen, Clear-mask cut-out), the rim
highlight (layer with BlendMode.Plus), the control itself (Offscreen clip layer) and the blur/lens RenderEffect layer.
Now only the RenderEffect layer remains:
- `backdrop/shadow/ShadowModifier.kt`: drawn directly, shape removed with `clipOutOutline` (new, `internal/Outline.kt`),
  alpha and blend mode on the paint.
- `backdrop/highlight/HighlightModifier.kt`: stroked directly with the blend mode and alpha on the paint.
- `DrawBackdropModifier.kt`: new `isolated` parameter (default true = library behaviour); `liquidGlass` passes false →
  `CompositingStrategy.Auto` with a native rounded clip. `ScrollEdgeEffect` keeps isolation (its DstIn mask needs it).
- `LiquidQueue.kt`: played (dimmed) rows use `CompositingStrategy.ModulateAlpha` instead of a layer each.

**Pixels:** same paused frame captured old/new/new with a live switch: new vs new 0 px; new vs old differs only on the
1-px bright rim of each glass shape (max 34/255, ≤1 px shift). Indistinguishable at 3x zoom.

**Queue, playing (live switch, same session):** GPU per frame (issue → GPU done) median 28.6 → 18.9 ms and 25.8 →
15.7 ms (−34 to −39%); RenderThread issue 25.5 → 15.1 and 20.1 → 9.6 ms. Queue's ~24 fps is by design (the background
drift ticks at 25 fps and nothing else moves); its "85% janky" is each of those frames missing a one-vsync deadline.

**Not changed by this:** Queue *scrolling* — GPU ~9.6 ms either way; its jank (37–48%, p90 48–65 ms) is the UI thread
(p90 36 ms) composing rows. Next lever there, not started. The last per-control layer (RenderEffect) would need the
lens as a shader over a shared blurred image; not attempted.

## 14. Queue scroll "jank" — investigated, NOT a queue problem; nothing kept but a build switch (26 September, night)

**The §13 reading was wrong.** "37–48% janky, UI thread p90 36 ms" came from (a) the debuggable `.debug` build, whose main
thread runs largely in ART's switch interpreter (`ExecuteSwitchImplCpp`), and (b) swipes that reached the list top and
dragged the player sheet (`preUpPostDownNestedScrollConnection`). Queue swipes must stay inside the list: on this phone
it spans y ≈ 480–1500; y=1700 is the scrubber.

**Measure UI-thread work on a release-speed build.** New opt-in `-Pshiny.measure.suffix=.debug` (app/build.gradle.kts)
installs `benchmarkRelease` / `nonMinifiedRelease` as `com.shiny.music.debug`, over the debug app (same debug key, data
kept) instead of over the real app. Note: `nonMinifiedRelease` does NOT merge `src/main/generated/baselineProfiles`
(its merged profile has only library rules). For a true first-install compile state:
`pm art clear-app-profiles <pkg>` + `compile --reset` + profileinstaller broadcast + `compile -m speed-profile -f`;
check with `cmd package dump-profiles <pkg>` → `/data/misc/profman/<pkg>-primary.prof.txt`.

**Release-speed results, 120 Hz (the panel boosts to 120 Hz under a finger; deadline 11.6 ms after vsync):**

| Slow upward swipes | janky | frame p50 / p90 / p99 | UI thread p50 / p90 | GPU (issue→done) |
|---|---|---|---|---|
| Queue | 12.5–15.2% | 11–12 / 17–19 / 29–36 ms | 2.4–2.5 / 3.3–3.7 ms | 8–10 ms |
| Home (reference) | 14.0–14.4% | 13 / 20–21 / 29–32 ms | 2.2 / 2.5–2.8 ms | 9.9 ms |

Queue scrolls like Home. The residual ~14% is app-wide: GPU ~8–10 ms per frame against an 8.3 ms refresh.

**Tried and reverted (no gain, all measured):**
- Fade bands through band-sized layers instead of a full-pane Offscreen layer: pixel-identical, GPU 9.8–9.9 vs 9.9–10.2 ms.
- Player background cached as one texture for screen + glass (`layerBackdrop(cached)`): pixel-identical, GPU median
  −7% but p90 +10%.
- Baseline-profile wildcard rules for `ui/liquid/player/**` (expanded on device: 442 queue, 667 lyrics entries): first-
  install scroll 14.7–15.2% vs 12.5–14.4% without — the JIT compiles the queue while it opens. (`sh/calvin/**` rules
  did not expand at all.)
- Bisect, GPU median while scrolling: base 9.4–9.6; no background images 8.8; no glass at all 9.7; no fade 8.1; no row
  covers 9.0. No single part owns the GPU time: it behaves like a floor (likely GPU clocking at light load), not content.

**If the 120 Hz floor is ever worth attacking:** needs GPU counters (AGI / Perfetto `gpu.renderstages`), not
bisection. The shipped generated baseline profile still predates the player journey (5 LiquidNowPlaying rules, no
queue/lyrics); regenerating it did not help startup/Home before (§4) and was not re-tried.
