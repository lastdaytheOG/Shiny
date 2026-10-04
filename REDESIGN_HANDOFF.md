# Liquid Redesign — Handoff / Resume Notes

_Last updated: 2026-09-12 (end of session 6). Read this first when resuming._

## The goal

The user asked for a **complete UI/UX redesign** of Shiny (`com.shiny.music`,
Jetpack Compose), modelled on **Apple Music in iOS 26/27 with Liquid Glass**: "almost a
clone but not a copy", every screen rebuilt from scratch (no tweaking of the old
layouts), with the smoothest motion possible.

- Design spec: [`DESIGN.md`](DESIGN.md) — tokens, glass rules, shell, pages, Now Playing.
- All new UI: `app/src/main/kotlin/com/shiny/music/ui/liquid/`. Reuse ViewModels / data
  layer only; build new screens on the Liquid components.
- Tint is **Shiny Rose** (`#FF2D55`), type is **Inter** (not SF Pro), no Apple assets.

## To resume

Say **"continue"** (or "Continue the Liquid redesign from REDESIGN_HANDOFF.md"). Session 3's
five player/share features are finished and verified; start at [Next steps](#next-steps).

## Ground rules

- **Do not commit** unless the user asks. The working tree holds a large uncommitted
  package rename; never stash, reset, or commit it implicitly.
- **Glass must never sample a backdrop that contains itself.** Chrome outside the NavHost
  samples the app backdrop. A page that floats glass over its content records the
  content with `rememberLiquidBackdrop`/`layerBackdrop` and provides it only to the
  overlay (`LocalLiquidBackdrop`); list content gets `LocalLiquidBackdrop provides null`.
- **Nothing animates while idle** except the playing Now Playing backdrop. Infinite
  transitions are composed only while their state is active (see the Recognition orb).
- Press feedback is `LiquidHighlight` or a spring scale — **never a ripple**.
- Popups/dialogs live in their own window and cannot sample the page: give them
  **opaque** surfaces (a 0.97-alpha surface plus a shadow bled the page through visibly).
- **Backslashes are eaten before the shell sees them.** A `\\` written into a sed script or
  a `<<'EOF'` heredoc arrives as one `\`, so patterns silently fail to match and Kotlin
  regexes come out wrong. Build them from `chr(92)` in Python, or write `\x5c` for sed.
  (Android string resources still need apostrophes escaped as `\'`.)
- Use `Liquid.colors` / `LiquidTypography`; `MaterialTheme` only in legacy code.

## Build, install, look

```bash
export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"   # shell JAVA_HOME is broken
./gradlew :app:compileX86_64GmsDebugKotlin --offline -q          # fast compile check
./gradlew :app:assembleX86_64GmsDebug --offline -q               # APK
ADB=/c/Users/Lenovo/AppData/Local/Android/Sdk/platform-tools/adb.exe
$ADB install -r app/build/outputs/apk/x86_64Gms/debug/app-x86_64-gms-debug.apk
$ADB shell am start -n com.shiny.music.debug/com.shiny.music.MainActivity
$ADB exec-out screencap -p > shot.png
```

- AVD `ShinyStartup` (1080×2400). Screenshots display at 900×2000 — multiply displayed
  coordinates by **1.2** for `adb shell input tap`.
- Tab bar (displayed coords): Home (150,1930), New (372,1930), Library (593,1930),
  Search orb (800,1915). Library rows: On This Device y≈1022, History 1140, Listening
  Stats 1258, Together 1376, Recognition 1495.
- KSP's "AWT-EventQueue NullPointerException" during compile is harmless; judge by exit code.
- Performance method: `dumpsys gfxinfo <pkg> reset`, wait 20 s, read "Total frames
  rendered"; UI-thread jiffies from `/proc/<pid>/task/<pid>/stat`.

## What is done

### Design system (`ui/liquid/`)
| File | What |
|---|---|
| `LiquidTheme.kt` | colours (iOS palette), Inter families, `LiquidTypography`, Material projection |
| `LiquidGlass.kt` | `Modifier.liquidGlass` (Regular/Clear/Thick), press, `ScrollEdgeEffect` |
| `LiquidComponents.kt` | Artwork, GlassIconButton, GlassCapsule, LiquidButton, SectionHeader, MediaTile, SongRow, NavigationRow, SegmentedControl, SearchField, ActivityIndicator, NowPlayingBars, EmptyState… |
| `LiquidPage.kt` | `LargeTitlePage`, `DetailTopBar`, `LiquidBackButton`, scroll-to-top |
| `LiquidShelves.kt` | Shelf, SongGrid, CategoryTile, skeletons |
| `LiquidMenu.kt` | **new:** `LiquidPullDownMenu` (iOS pull-down: `MenuAction`/`MenuDivider`/`MenuHeader`) and `LiquidAlert` (iOS alert, capsule buttons) |
| `LiquidActions.kt` | one place for tap/long-press of songs/albums/artists/playlists |
| `LiquidArtworkColor.kt` | `rememberArtworkTones(url, fallback)` |
| `LiquidSwitch.kt`, `LiquidTopAppBar.kt` | swapped into old screens by import alias |
| `LiquidPrefs.kt` | PlayerMotion, ArtworkBreathe, ShowVolume, LyricsBlur, WordByWord |
| `shell/` | LiquidTabBar + search orb + mini-player accessory (minimise on scroll), LiquidRail, UIKit push transitions |
| `player/` | Now Playing: stage / lyrics (word-by-word + romanization) / queue, landscape |

### Screens (`ui/liquid/screens/`) and their routes
| Screen | File | Route(s) | Verified on emulator |
|---|---|---|---|
| Home | LiquidHomeScreen.kt | home tab | yes |
| New | LiquidNewScreen.kt | explore tab | yes |
| Library + categories | LiquidLibraryScreens.kt | library tab, `library/*` | yes |
| Search + results | LiquidSearchScreens.kt | search, `search/{query}` | yes |
| Artist (iOS 27) | LiquidArtistScreen.kt | `artist/{id}` | yes |
| Album / playlists | LiquidAlbumScreen.kt, LiquidPlaylistScreens.kt, LiquidCollectionPage.kt | album, online/local/auto/cache/top playlists | yes |
| History, artist see-alls, moods, browse, new releases, account, charts | LiquidSecondaryScreens.kt | various | mostly |
| Settings root | ui/screens/settings/SettingsScreen.kt (rewritten) | `settings` | yes |
| Appearance | LiquidAppearanceScreen.kt | `settings/appearance` (old one at `settings/appearance/classic`) | compiled, not screenshotted |
| **Listening Stats** (Replay-style) | LiquidStatsScreen.kt | `stats` | **yes** |
| **On This Device** (+ scan sheet, Select mode, sort pull-down) | LiquidLocalSongsScreen.kt | `local_songs` | **yes** (empty/permission state) |
| **Recognition** (glass orb, ripples, result page) | LiquidRecognitionScreens.kt | `recognition` | **yes** (ready + listening; result not seen) |
| **Recognized history** | LiquidRecognitionScreens.kt | `recognition_history` | **yes** (empty state) |
| **Listen Together** (SharePlay-style) | LiquidTogetherScreens.kt | `listen_together`, `listen_together_from_topbar` | **yes** (out of session) |
| **Together Messages** (iMessage-style) | LiquidTogetherScreens.kt | `listen_together/chat` | compiled only |
| **Onboarding + What's New** | LiquidWelcome.kt | wired in `MainActivity` (replaces OnboardingScreen / WelcomeDialog) | compiled only |

Old screens still get the look through swaps: all Material TopAppBars → `LiquidTopAppBar`,
all Switches → `LiquidSwitch`, `Material3SettingsGroup` → iOS grouped, menus/bottom
sheets → grouped sections, `arrow_back`/`more_vert` → chevron/ellipsis.

## Session 3 — five player/share features (DONE: all five verified on the emulator in session 4)

User asked for: (1) animated artwork instead of a static poster on Now Playing, (2) word-by-word
lyrics with glass-style animation, (3) bigger glass transport buttons, (4) Up Next showing
10–15 suggested songs instead of ~3, (5) a "Shiny special" animated AMOLED + glass share poster.

| # | What was built | Files |
|---|---|---|
| 1 | `LivingArtwork`: plays motion artwork (Apple Music / Tidal / Shiny canvas providers, validated, cached) when the song has one; otherwise the cover drifts/zooms, an AGSL liquid-ripple + light-sweep shader runs (API 33+; older = drift + gradient sheen). Runs only while playing, sheet expanded, Stage mode. Prefs `LiquidPrefs.LivingArtwork`, `LiquidPrefs.MotionArtwork` (both default on) in Appearance → Now Playing. | `player/LiquidLivingArtwork.kt`, `FlyingArtwork` in `player/LiquidNowPlaying.kt`, `LiquidPrefs.kt`, `screens/LiquidAppearanceScreen.kt` |
| 2 | Karaoke lyrics: every synced line gets word timings (real, or estimated from line length), words drawn with `drawText` from a frame-interpolated clock: soft gradient fill edge, 3.5dp lift, glow + light pool under the sung word, long notes swell; Apple-style interlude dots in gaps ≥4.5 s. Line-level `FlowRow` of words. | `player/LiquidLyrics.kt` (`timeLines`, `rememberLyricClock`, `KaraokeWord`, `Interlude`) |
| 3 | `LiquidTransport` rewritten: glass discs (play 86dp, skips 66dp; landscape 72/56), play⇄pause morphing glyph, skip arrows nudge on tap, press bloom. Old transport removed from `LiquidPlayerControls.kt`. | `player/LiquidTransport.kt` |
| 4 | `MusicService.ensureUpNext(minimum = 15)`: pages the queue, then tops up with the song's radio (`RDAMVM<id>`), de-duplicated; only with Autoplay on. Called from the queue pane on open / index change. | `playback/MusicService.kt`, `player/LiquidQueue.kt` |
| 5 | Shiny Share: `PosterRenderer` draws a 1080×1920 AMOLED poster (drifting colour aurora, stars, frosted glass card, breathing cover with light sweep, live equaliser, Shiny wordmark; 6 s seamless loop). `ShinyShareSheet` = live preview + "Share Animated Poster" (MP4 via MediaCodec + hardware canvas, real-time paced, 1080p→720p fallback) + Image (PNG) + Link. Opened from Player ⋯ menu ("Share this track") and Song menu "Share". | `ui/liquid/share/ShinyPoster.kt`, `ui/liquid/share/ShinyShareSheet.kt`, `ui/menu/PlayerMenu.kt`, `ui/menu/SongMenu.kt` |

### Session 4 — verified on the emulator, and what it cost

All five were checked on the API 36 AVD with a real song playing:

| # | Verified how | Result |
|---|---|---|
| 1 | Frame-diffing the artwork a second apart | 73–78% of the cover's pixels change — it drifts, zooms and ripples. Landscape too (29%). |
| 2 | Screen recording during a sung line | Words light one at a time with the gradient edge and the light pool under the sung word; interlude dots appear in the long gap. |
| 3 | Screenshot | The three glass discs, morphing play glyph. |
| 4 | Opening the queue | *Playing Next* went from empty to ~15 radio songs. **Autoplay was off** on this device, and with it off `ensureUpNext` is a no-op by design — the queue stays what the user built. |
| 5 | Tapping through to the chooser | 1080 × 1920 H.264, 180 frames, 5.91 s, and the exported frames match the preview. |

**Performance** (`dumpsys gfxinfo`, 20 s windows, Now Playing open):

- Paused, Stage: **0 frames rendered.** Nothing idles.
- Playing, Stage: 1266 frames (~63/s), **0 janky**, 99th percentile 25 ms.

The 60 Hz repaint is the living artwork and the lyric clock, and it stops at the source
when playback stops — which is the design, not a leak.

### Fixed in session 4

- **Landscape Now Playing** now uses `LivingArtwork` (it was still a static cover).
- **The interlude line stayed lit.** Once the dots take over a gap, the sung line now fades
  back over 700 ms so the dots hold the eye, as in Music (`InterludeFadeMs`).
- **The chooser could not read the shared file** ("call Intent#setClipData()" in logcat):
  the share intent now carries `ClipData` as well as `EXTRA_STREAM`.
- **Poster files are named after the song** (`Let It Happen - Tame Impala.mp4`) instead of
  `shiny_<videoId>.mp4`, so the chooser row reads as something worth sending.
- **Two menu entries were both called "Share."** The player ⋯ quick actions are now
  **Add · Poster · Link**.
- **The exported video's pose is taken from the wall clock**, matching the timestamps the
  input surface stamps on each frame — a slow device now loses smoothness instead of
  stretching the 6 s loop.
- `DESIGN.md` documents all five features, `LiquidPullDownMenu` / `LiquidAlert`, and the
  session-2 screens.

**Still open on the share sheet:** the chooser shows a document glyph, not the poster —
`FileProvider` serves no thumbnails. A `FileProvider` subclass with its own authority
overriding `getStreamTypes` / `openTypedAssetFile` to hand back a poster JPEG would fix it;
the existing `.FileProvider` authority is used by other features, so give the share one its
own rather than swapping the class underneath them.

## Session 5 — AI removed, old branding removed, share rebuilt

### AI is gone
Deleted, not disabled: `ai/` (playlist generator/modifier, recommendation helper + worker),
`api/FlowNeuroengineApi.kt`, the three AI dialogs, `lyrics/api/` (OpenRouter, OpenRouter
streaming, DeepL, Mistral) and `LyricsTranslationHelper`. Call sites stripped from
`MainActivity` (the daily recommendation worker), both Home screens, `LibraryScreen`,
`LocalPlaylistScreen`, `PlaylistScreenMenus`, `FloatingNavigationToolbar`, and the legacy
`Lyrics.kt` / `LyricsMenu.kt`. 11 preference keys and **840 string entries across 78 locale
files** removed, plus the `FLOW_NEURO_*` build config.

Two knock-ons worth knowing: Library's "how do you want to create this playlist?" chooser
had only one option left, so it now opens the normal create dialog directly; and the AI
lyric translation lived only in the retired player, so nothing the Liquid UI shows changed.

**To bring any of it back:** it is all still in the git history, under the old package path.

### The old branding is gone
The theme is now `Theme.shinymusic` (styles, manifest, every widget layout),
`rootProject.name` → `shinymusic`, the JNI symbols in `cpp/vibrafp` → `com_shiny_music_*`,
the CI artifact name, the logo (`assets/Shiny-logo.png`), and every doc
(README, AGENT, CONTRIBUTING, SETUP, PRIVACY_POLICY, RELEASE_INFO) including the GitHub
URLs, which now point at `lastdaytheOG/Shiny`. Recognition is "Shiny Find".

Since then (15 September) `app/google-services.json` was replaced with Shiny's own
Firebase project, `shinymusic-1`; nothing points at the old cloned project any more. `BluetoothUtils.kt` still
matches the name of Amazon's smart speaker, for speaker detection, and `cpp/vibrafp` is never compiled (no `externalNativeBuild`, no
`System.loadLibrary`).

### Share is a link now, not a video
A shared link already cold-starts Shiny into Now Playing and plays the whole song
(`share.shinymusic.fun/watch?v=…` → `handleDeepLinkIntent` → `playQueue`) — verified from a
force-stopped app. What was wrong was that the sheet led with a 6 MB video, and WhatsApp
and Instagram drop `EXTRA_TEXT` when a media stream is attached, so the tappable part was
the first thing lost. Now: **Send Song** is the primary white capsule (link + subject), and
Poster / Image / Copy are secondary discs. The poster export moved into its disc, which
holds a spinner and counts the percentage under it while rendering.

## Session 6 — Listen Together: connecting

### The bug that made it unreliable

The server (`wss://metroserverx.meowery.eu/ws`) **never sends an opening frame** — it only
answers — and it rejects anything addressed in the wrong wire format. The client opened
every socket in JSON and switched to Protobuf only after *receiving* a Protobuf frame. So
the first message of every connection went out as JSON. For create and join that was
harmless: the user taps later, by which time an earlier error reply had already flipped the
codec. But **`reconnect` is sent the instant the socket opens** — always JSON, always
answered `invalid_message`, so **session resume never worked once**. Every dropped socket
silently lost the room, which is what the "you may get disconnected" note in the UI was
apologising for.

Fixed in `ListenTogetherClient`: connect in **Protobuf**, and if the server ever rejects a
frame as `invalid_message` before anything has been accepted, flip format and resend that
frame once (`retryInOtherFormat`) — so a JSON-only server still works. Verified on two
emulators: host resume, guest resume, and a cold join all reconnect into the same room.

Two things that fix enabled:
- The UI sent `create_room` / `join_room` **twice** ("the first message can be dropped while
  the socket settles"). That workaround is gone; it was producing `already_in_room`.
- The "you may get disconnected…" note is gone from the Together page and the player menu.

Also: `canControlMusic` now accounts for `client.isEnteringRoom`, so a guest cannot fire a
playback action into the gap between asking to join and being in the room. The flag clears
on join, create, error, leave, close, failure and disconnect, so it cannot stick.

### The connect flow, redesigned

Out of session was a five-block form: gradient hero card → "Your Name" field → Start → a
Join group → a warning paragraph → a settings row. It is now what SharePlay is: **one
tinted symbol on the page, a title, a line, and two capsules** — *Start Session* and
*Join a Session*. Join expands into an **iOS verification-code field**: eight discrete
boxes, the next one outlined as the caret, auto-joining the moment the eighth character
lands, with Paste underneath. The name is one quiet tappable line, *Joining as Guest_2150*,
pre-filled so it is never a gate. In session, the gradient slab is gone too: the code is set
large and grouped (`Z3BN 0FXZ`) so it can be read aloud, over a green dot and
"You're hosting · 2 listening", with Copy and Invite beneath.

Screens verified in passing: **Onboarding** and **What's New**, both previously "compiled
only".

### Known, and server-side

`update_room_settings` comes back `unknown_message_type` from this server, so the host's
**Shared Control** switch never reaches it. The server reports `allow_participant_control`
as true in room state but then refuses a guest's playback action with `not_host` — it
contradicts itself. The switch currently promises something the server will not honour;
decide whether to hide it, or to run a server that implements the message.

## Session 7 — Home rebuilt as a data-driven feed (2026-09-18, uncommitted)

Home no longer stacks the YouTube Music feed. `app/…/home/HomeFeedBuilder` (pure, tested in
`app/src/test/…/home/HomeFeedBuilderTest.kt`) decides which sections exist and their order
from Room aggregates (`HomeSignalsLoader`, `home*` queries in `DatabaseDao`), a disk-cached
network block (`HomeRemoteRepository` → `files/home_remote.json`) and the moment (daypart,
connectivity, filters, a per-daypart seed). DESIGN.md → Pages → Home lists the sections.

- Charts are YouTube Charts playlists, named verbatim. Country = Settings → Library & Home →
  Charts (default: the listener's location from network/SIM country; Global; Off; any of
  the 68 countries YouTube Charts publishes; "Global chart too").
- `AccountSettingsScreen` now uses the activity's `HomeViewModel` (it used to start a
  second full Home pipeline just to read the account name).
- Verified on the emulator: feed renders from real history, SQL checked against the device
  DB, remote cache written. Still to look at on a phone: the hero/resume card after a cold
  start, Discover once the related-song top-up has run, and the Charts settings page.

## Next steps

0. **Asked for and not yet built** (session 5):
   - **Background Shazam.** Recognise whatever is playing around the phone while the user
     is in another app (scrolling reels), and surface it as a heads-up notification with
     artwork and a Play action. Needs a `microphone`-typed foreground service, a duty cycle
     (a short listen every N seconds, not a continuous stream), an opt-in with a persistent
     notification, and a kill switch. `recognition/MusicRecognitionService.kt` +
     `:shazamkit` already do the matching; what is missing is the always-on driver and the
     notification. Watch battery: this is the one feature that can ruin the app's reputation.
   - **Artist video beyond the artist page.** `ArtistVideo` already plays behind the hero on
     `LiquidArtistScreen` (gated on `ShowArtistVideoKey` + data saver). Apple also runs the
     artist's motion video behind **Now Playing** — that is the missing piece, and
     `LivingArtwork` is the place to put it.
1. **Install and screenshot** the latest build: Onboarding (clear app data or flip
   `OnboardingCompletedKey`), What's New, Appearance, Messages. Fix what looks off.
2. **Verify in a real session** (needs user OK — it talks to the external Listen Together
   server): in-room Together page, host pull-down, requests/suggestions, Messages.
3. **Remaining old-layout screens** (routes in `ui/screens/NavigationBuilder.kt`):
   settings sub-pages (`settings/update|account|content|player|storage|backup_restore|about`,
   `settings/integrations/listen_together`, romanization, theme, changelog, uptime),
   `settings/equalizer` + `equalizer` dialog, `login`, `settings/spotify_import`,
   `ambient_mode`, `update`. Settings sub-pages are the biggest visible gap.
4. **Delete dead old UI** — mostly done on 15 September: 96 unreferenced files went (old
   Home, Stats, Charts, History, Explore, Album, Account, Listen Together, CommentTogether,
   Onboarding, WelcomeDialog, the old artist/library/playlist/search/recognition screens,
   FloatingNavBar/FloatingNavigationToolbar, the commit screen) with their orphaned strings
   and drawables; they are in git history. Still referenced and so still here: old
   `Player.kt` / `MiniPlayer.kt` (via `BottomSheetPlayer` in `MainActivity`) and the
   settings sub-pages in step 3. `OptionStats` already lives in `core` (`OptionStats.kt`).
5. Optional: Charts on the New tab didn't show in the last check; local playlist lost
   sort/select/search/AI tools; the share chooser's poster thumbnail (above).

## Known issues / notes

- Pushed pages hide the tab bar (only the mini-player accessory shows) — check this is
  intended against DESIGN.md §3 ("on pushed pages the tab that was active stays lit").
- Recognition's `recognition?autoStart=true` deep link doesn't auto-start (it didn't in
  the old screen either).
- The Together page shows a back button when Together isn't a tab
  (`ListenTogetherInTopBarKey` = true) and none when it is.
- Recognition no longer files the same song twice if you come back to a result within
  10 minutes.

## Research sources (iOS 27 Apple Music)

- https://9to5mac.com/2026/06/19/apple-music-in-ios-27-introduces-new-design-changes-in-two-key-areas/
- https://9to5mac.com/2026/07/20/apple-music-in-ios-27-five-new-features-coming-to-your-iphone/
- https://www.macrumors.com/2026/06/09/apple-music-gains-automix-upgrades-and-more-in-ios-27/
- https://apple.gadgethacks.com/news/apple-music-ios-27-design-changes-whats-live-vs-promised/
