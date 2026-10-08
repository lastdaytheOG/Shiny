# Architecture and conventions

How Shiny is put together, and the rules every change follows. Read it before your first pull request, and correct it in the same change when it stops being true.

## What Shiny is

A free Android music player (Kotlin, Jetpack Compose) that plays the YouTube Music catalogue, designed after Apple Music. Package `com.shiny.music`, debug builds `com.shiny.music.debug`. The interface is Shiny's own "Liquid" design system; the product goals are in [PRODUCT.md](PRODUCT.md) and the design rules in [DESIGN.md](DESIGN.md).

## Rules

**Notices stay.** Never edit, move or rename a copyright or licence notice an upstream author put in a file. A notice leaves only together with the code it covers. When Shiny changes such a file substantially, add a separate `Modified by the Shiny Project in <year>` line under it. Search-and-replace must skip notice lines; `UpstreamNoticesTest` fails if one changes. See [LICENSE_COMPLIANCE.md](LICENSE_COMPLIANCE.md).

**Credit what you take.** Code ported or adapted from another project needs, in the same commit: a row in the README's Acknowledgements table saying exactly what was taken, an entry in `licenses/bundled.json`, and for a non-GPL licence its file in `licenses/notices/`. Check the licence is GPL-3.0 compatible first.

**Keep the licence inventory current.** After adding, removing or upgrading a dependency, run `python scripts/third_party_licenses.py` (JDK on `JAVA_HOME`) and commit `licenses/`. Resolve anything reported UNKNOWN in `licenses/overrides.json`, with evidence. `LicenseInventoryTest` fails while it is stale.

**The interface is Liquid, not Material.** Screens are built from the components in `ui/liquid`. Read DESIGN.md before any visual change; don't introduce Material 3 containers, top bars or colour roles into a Liquid screen.

**No slowdowns.** Scrolling, start-up and the player are measured on a real phone. A change that touches them needs a before/after measurement, not an estimate.

**Clean up.** Scratch scripts, logs and data files made during a session are deleted before committing. Reusable tooling goes in `scripts/`.

## Layout

| Module | Holds |
| :-- | :-- |
| `:app` | The application: UI, ViewModels, playback service, DI |
| `:core` | Shared models, constants, the Room database, DataStore |
| `:playback` | Media3 logic: queues, equaliser, data sources, sleep timer |
| `:lyrics` | Lyrics orchestration and parsing |
| `:innertube` | The YouTube Music client |
| `:kugou` `:lrclib` `:betterlyrics` `:youlyplus` `:paxsenixlyrics` `:simpmusic` | One lyrics source each |
| `:shazamkit` | Song recognition |
| `:canvas` `:applecanvas` | Animated artwork |
| `:artistvideo` | Artist videos |
| `:unison` | Shared lyrics utilities |
| `:baselineprofile` | Baseline profile generation |

Outside Gradle: `server/` is the Cloudflare Worker behind Listen Together, Shiny Social, shared links and shinymusic.in (`server/together/PROTOCOL.md` describes the session protocol).

Inside `app/src/main/kotlin/com/shiny/music/`:

```
ui/liquid/      the design system and every current screen
  shell/          tab bar, navigation, mini player
  home/ player/ settings/ together/ share/ appearance/ launch/ screens/
ui/component/   older shared components still in use
ui/menu/        song, album and playlist option sheets
playback/       MusicService, downloads, audio processors
together/       Listen Together session engine and socket
social/         friends, profiles, shared links
home/           the Home feed builder
artwork/        cover and animated-artwork resolution
spotify/ spotifyimport/ discord/ recognition/ localmedia/ export/ downloads/
diagnostics/    service probes and playback health
viewmodels/     one per screen or feature
widget/ quicksettings/
```

A new external source (lyrics, artwork) is a new Gradle module registered in `settings.gradle.kts`, not a package inside `:app`.

## Build

Variants are ABI × flavour × type: `universal` / `arm64` / `armeabi` / `x86` / `x86_64`, `foss` / `gms`, `debug` / `release`.

```bash
./gradlew assembleUniversalFossDebug     # no Google services, needs nothing
./gradlew assembleUniversalGmsDebug      # Google sign-in + Firebase
./gradlew assembleUniversalGmsRelease    # needs the signing variables below
```

- `minSdk 26`, `targetSdk 36`, `compileSdk 36`, JDK 21, NDK `27.0.12077973`.
- Versions live in `gradle/libs.versions.toml`. Add dependencies there, never inline.
- Google-only code is `gmsImplementation`-scoped so the `foss` flavour keeps building.
- Room schema changes need a migration and the exported schema JSON under `core/schemas/`.
- User-facing text is a string resource. New Liquid strings go in `res/values/liquid_strings.xml`.

## Config and secrets

- `local.properties` (from `local.properties.template`): the SDK path.
- `app/google-services.json`: optional. Without it a `gms` build compiles with Firebase unconfigured.
- Release signing: Gradle reads `SHINY_KEYSTORE_PATH` and `SHINY_KEYSTORE_PASSWORD`. CI decodes the `SHINY_KEYSTORE_BASE64` secret and checks the APK's certificate. Official SHA-256:
  `F3:70:1C:9C:1C:87:54:F4:79:A9:D2:20:D6:E6:56:B1:12:1C:CA:43:B6:1C:7B:BE:FD:15:D8:32:A8:2B:19:46`.
- Never commit `local.properties`, a keystore or its password, or a real `google-services.json`.

## Tests

Unit tests run on a plain JVM. Run the suite for what you touched:

```bash
./gradlew :app:testUniversalFossDebugUnitTest
./gradlew :lyrics:testDebugUnitTest      # LRC parsing and line-at-position
./gradlew :canvas:test
./gradlew :innertube:test
```

CI builds the app but does not run tests, so a green build proves nothing about behaviour. Pure logic (parsers, feed building, queue rules) gets a test with the change.

## Playback position: one source of truth

Everything time-based in the player — the current lyric line, the karaoke word
fill, the interlude dots, the scrubber — must derive from the ExoPlayer
position, and from a *single* clock per surface. Do not add a second timer that
resolves the same thing independently, and do not compensate for a perceived
lag with a constant.

- `LiquidNowPlaying` polls `player.currentPosition` into one `position` state.
  Its `LaunchedEffect` is keyed on the current track id as well as the surface
  state, so a song change re-polls immediately instead of leaving the previous
  track's position standing.
- `LiquidLyrics.rememberLyricClock` extrapolates between those polls so fills
  glide at frame rate, re-anchors on every poll, and scales by
  `playbackParameters.speed`. The current line index is a `derivedStateOf` over
  that same clock.
- `LyricsUtils.findCurrentLineIndex` has **no** look-ahead or tolerance. A line
  is current from its own timestamp. Tapping a line seeks to the value that
  makes that same line current, so the two can never disagree.

Timing corrections are only legitimate when they come from real metadata: the
LRC `[offset:]` tag in the lyrics file (applied once at parse time in
`parseLyrics`) or the per-song `SongEntity.lyricsOffset` the user sets. Never a
hardcoded constant.

## Storage: what is large on purpose

Two dependencies dominate installed size. Both are load-bearing — check with
someone before touching either.

| What | Size (arm64 APK) | Why it is there |
|---|---|---|
| `com/atilika/kuromoji/ipadic/*.bin` | ~33 MB uncompressed | The IPADIC dictionary for Kuromoji, which is what makes Japanese romanisation (`LyricsUtils.romanizeJapanese`) possible. It is a dictionary, so R8 cannot shrink it, and there is no smaller IPADIC. Removing it removes Japanese romanised lyrics. |
| `lib/*/libav*.so`, `libffmpegkit*.so` | ~17 MB | FFmpegKit, used by `AudioExportService`. Already the `ffmpeg-kit-audio` variant, which is the smallest published build that still covers the audio codecs export needs. |

Runtime caches are user-controlled in Settings → Storage: song cache defaults to
1024 MB (and offers "unlimited"), image cache to `App.DEFAULT_IMAGE_CACHE_MB`.
Keep that constant and the `StorageSettings` slider default in step.


## CI

- `.github/workflows/android-build.yml`: builds the APK; signs and verifies it when the release key is available.
- `.github/workflows/codeql.yml`: static analysis.
