# Shiny Music Design — "Liquid"

Shiny's interface is modelled on **Apple Music in iOS 26/27 with Liquid Glass**: neutral iOS
grounds, one tint colour, large bold titles, floating glass chrome, and a Now Playing screen
that is the artwork's own world. It is *inspired by*, not copied from, Apple Music: the tint
is **Shiny Rose** (iOS systemPink, `#FF2D55` / `#FF375F`) rather than Apple Music red, the
type is **Inter** rather than SF Pro, and every asset is our own.

All new UI lives in `app/src/main/kotlin/com/shiny/music/ui/liquid/`. Build new screens from
it; do not add new screens on the old `ui/component` look.

---

## 1. Foundations (`ui/liquid/LiquidTheme.kt`)

### Colour
`Liquid.colors` is the iOS system palette, fixed — **grounds never move with artwork**.

| Token | Light | Dark | Use |
|---|---|---|---|
| `background` | `#FFFFFF` | `#000000` | page ground |
| `groupedBackground` | `#F2F2F7` | `#000000` | Settings-style pages |
| `secondaryGroupedBackground` | `#FFFFFF` | `#1C1C1E` | grouped cells |
| `label` / `secondaryLabel` / `tertiaryLabel` | black / 60% / 30% | white / 60% / 30% | text tiers |
| `separator` | `rgba(60,60,67,.29)` | `rgba(84,84,88,.6)` | hairlines |
| `fill` … `quaternaryFill` | system fills | system fills | controls, highlights |
| `accent` | Shiny Rose | Shiny Rose | **only** things you can tap |
| `green` | `#34C759` | `#30D158` | switches |
| `sheetBackground` | `#F2F2F7` | `#131315` (AMOLED `#0A0A0B`) | ground of an option sheet |
| `panel` | `#FFFFFF` | white 7.5% | grouped panels: menu sections, Settings index groups |
| `panelRim` | none | white 13% | the light a panel's upper edge catches |
| `panelSeparator` | `rgba(60,60,67,.2)` | white 9% | hairlines between rows inside a panel |

`ShinyTheme` (`ui/theme/Theme.kt`) provides these and projects them onto the Material
`ColorScheme` via `liquidMaterialColorScheme`, so any screen still using `MaterialTheme`
lands on the same grounds. `themeColor` (the user's chosen colour) only replaces the tint.
Dynamic (artwork-seeded) theming now defaults **off**.

**Accent** (`ui/liquid/appearance/`) is Shiny Rose, Artwork, Mono or Custom. Rose and Mono
are stored sentinels resolved per theme (Mono is graphite `#8E8E93` / `#636366`, which
keeps white glyphs on accent fills above 3:1). The artwork accent is decoded at 128px
once per cover (`ArtworkAccent`), ranked with Score and pulled into the luminance band
0.12–0.30 (`AppearanceColor.sanitizeAccent`), so it reads as text on black and under
white glyphs; a cover with no usable colour falls back to Shiny Rose.

**AMOLED** (`liquidAmoledColors`) — the dark grounds are already `#000000`, so AMOLED
lowers what is raised off them: secondary/tertiary grounds `#111113` / `#1C1C1E`, the
Material container tiers to near-black steps, glass tints, and the Now Playing stage
when no artwork field is drawn. Fills, labels and separators are unchanged.

### Type
Inter variable (`res/font/inter_variable.ttf`, OFL) pinned to its optical-size axis:
`InterDisplay` (opsz 32), `InterTitle` (22), `InterText` (17), `InterCaption` (14).
`LiquidTypography` is the iOS Dynamic Type ramp at "Large", one sp per point:

| Style | Size / weight | Used for |
|---|---|---|
| `largeTitle` | 34 Bold | tab roots, pushed page titles |
| `title1` / `title2` | 28 / 22 Bold | section headers are `title2` |
| `title3` | 20 Semibold | player title, library rows (Regular) |
| `headline` / `body` | 17 Semibold / Regular | rows, buttons |
| `subheadline`, `footnote`, `caption1/2` | 15 / 13 / 12 / 11 | secondary text |
| `lyrics` | 30 ExtraBold | synced lyrics |

`LiquidMaterialTypography` maps the same ramp onto Material slots. Locales whose script
Inter lacks fall back to the system family (`rememberLiquidTypography`).

### Geometry
Page margin **20dp**; shelf gap **12dp**; artwork corner ≈5.5% of its edge (`artworkRadius`,
4–14dp); grouped sections 22dp; sheets 36dp top corners; capsules everywhere else.

Panels (`LiquidPanel.kt`): corner **22dp** (`PanelShape`), quick-action tiles **18dp**
(`TileShape`), **16dp** from the screen or sheet edge to a panel and again from its edge to
its rows' content, every row glyph in a **24dp** box, **12dp** between panels. A row is at
least 56dp tall in a menu and 60dp on the Settings index.

### Motion
Springs only (`AppMotion` still applies). Navigation uses the UIKit push in
`shell/LiquidNav.kt`: the new page slides in over the old, which drifts ⅓ left and dims;
tab switches are a 160ms dissolve. Predictive back scrubs the same pair. Appearance →
Page transitions swaps the pair for a 200ms dissolve (Fade) or none (Instant).

---

## 2. Liquid Glass (`ui/liquid/LiquidGlass.kt`)

`Modifier.liquidGlass(shape, kind)` draws a real refracting material on top of the vendored
Kyant backdrop (`ui/component/backdrop`): saturation → blur → **lens refraction at the rim**
(API 33+) → specular highlight → soft shadow → tint. Three kinds:

| Kind | Blur | Use |
|---|---|---|
| `Regular` | 12dp + lens | tab bar, mini player, floating buttons |
| `Clear` | 3dp + lens | buttons over artwork / coloured pages |
| `Thick` | 26dp | large panels |

**The one rule:** glass must never sample a backdrop that contains itself.
- Chrome outside the NavHost (tab bar, accessory) samples the app backdrop, passed
  explicitly.
- A page that floats glass over its own content records its content with
  `rememberLiquidBackdrop` / `layerBackdrop` and provides it **only** to the overlay
  (`LocalLiquidBackdrop`); list content gets `LocalLiquidBackdrop = null`.
- The player records its background layer and its buttons sample that.

Below API 31, without a backdrop, or with the user's glass switch off (Material → Solid),
glass falls back to a solid translucent fill with a hairline rim — call sites never branch.

The **finish** (Appearance → Material) re-tunes all three kinds at once: *Adaptive* is the
table above; *Clear* thins blur and tint (panels keep most of theirs) with a stronger
lens; *Frosted* blurs harder with a calmer lens and no dispersion. The finish comes from
`LocalShinyAppearance`, so there is one glass system, not a per-component tuning page.
`ScrollEdgeEffect` is iOS 26's progressive blur + fade under the status bar.

---

## 3. Shell (`ui/liquid/shell/`)

- **`LiquidTabBar`** — floating glass capsule (Home · New · [Together] · Library) plus a
  separate glass Search orb. The selected tab is tinted with a soft lozenge that springs
  between tabs.
- **Mini player accessory** — a glass capsule above the tab bar: artwork, title, artist,
  play/pause, next. Tap or swipe up opens Now Playing; swipe sideways skips.
- **Minimise on scroll** — scrolling down shrinks the tab bar to one button and slides the
  accessory inline beside it (iOS 26 `tabBarMinimizeBehavior`); scrolling up restores.
- On pushed pages the tab that was active stays lit.
- Landscape uses `LiquidRail` (vertical capsule) instead of the tab bar.

---

## 4. Pages

- **`LargeTitlePage`** (`LiquidPage.kt`) — tab roots and list pages: large title that
  scrolls away into a centred inline title, glass nav buttons over the scroll edge effect.
- **`DetailTopBar`** — pushed pages with their own header: glass back/actions, title fades
  in once the header has gone.
- **Collection pages** (`screens/LiquidCollectionPage.kt`) — albums and every playlist:
  page coloured from the cover (`rememberArtworkTones`), cover centred with a deep shadow,
  title, tappable artist, meta, **Play** (filled white) and **Shuffle** (glass) capsules,
  tracks, a facts footer, related shelves.
- **Artist page** (iOS 27) — the photo (or Apple Music motion video) melts into a
  full-screen field of its colour; name centred on it; info · Play · follow beneath;
  "Latest Release" in its own box; Top Songs as a 4-row paged grid; shelves; About.
- **Home** (`screens/LiquidHomeScreen.kt`, sections in `ui/liquid/home/`, feed in `app/…/home/`)
  — a feed assembled by `HomeFeedBuilder` from real listening only; the screen renders
  whatever the feed decides, in the feed's order.

  **Personalisation is a slope, not a door.** There is no play count at which Home becomes
  personal. `HomeTaste` turns the history into continuous weights — a `confidence` that
  moves from the first counted play, a per-artist `ArtistAffinity` with an `AffinityTier`
  (Exploring / Warming / Favourite), and leans for albums, artists, offline, exploring,
  habit and activity — and every section minimum is scaled by confidence rather than
  tested against a threshold. One play cannot reach Warming, so a single listen never
  reshapes Home; repeats across songs and days do. `HomeInterest` (a tiny decayed file,
  `home_interest.json`) records what the listener reaches for *on Home* — charts, moods,
  releases, discovery — which the history cannot see; search is read from
  `search_history`'s size. `HomeOrder` then scores every section: editorial slots (charts,
  moods, releases) start high and are pushed down by confidence while personal ones are
  lifted by it, so a new Home opens on the charts and hands over to the rotation over
  weeks. Sections move only *within* five fixed bands (Opening · Immediate · Core ·
  Library · Closing), which is what keeps an adaptive page from becoming a chaotic one,
  and the order is deterministic for a given listener and moment.

  **The opening is a field, not a card.** `AtmosphericField` is a full-width band of the
  lead artwork's colour that rises out of the page ground and sinks back into it, its
  fade measured in pixels from the real height so it never meets a hard edge. Every
  opening state wears the same shape — eyebrow, headline, one true sentence, Play and
  Shuffle: **the day's mix** (*First Light / Daylight / Golden Hour / Afterglow*, with a
  reason line that states the familiar/new split, and a "place to start" eyebrow while
  confidence is low) or, for a listener with no history at all, **Start Here** — the
  chart for where they are, or the music already on the phone. There is no onboarding
  wall and no plays-until-personal meter.

  The rest: **Pick up where you left off** (a paused session, decided on arrival),
  **Keep listening** (more from the artist just played — the first personal shelf, live
  from one play, retiring once the rotation can say it better), **Lately** (two-column
  typographic index), **Your Rotation**, **Your Evenings** etc. (the hour's own songs as
  a paged grid, plus moods), **Discover** (never-played songs, headed from the data —
  "More like *song*" only when one song really did seed the shelf), **Go deeper** (one
  artist, only above Exploring), **New Releases** (one featured record set as editorial,
  no panel), **The Charts** (rank in the display cut, movement flags computed by
  comparing two of Shiny's own fetches, region segmented, "refreshed X ago"),
  **Long Time No Hear**, **Albums You Started**, **Recently Added**, **Ready Offline**,
  **Your Week** and **Surprise Me**. Moods are compact neutral tiles with a colour rule,
  never flooded chips.
- **New**, **Library** (category rows → pushed category pages, Recently Added grid),
  **Search** (field + Cancel, scope switch, history/suggestions, Browse Categories grid,
  results with scope capsules and a Top Result card), **History**, **Settings** (the index
  is set in panels with a muted colour plate behind each glyph, a rimmed "Search settings"
  field and the account in a panel of its own; the pages it opens are plain type on the
  ground, with no panels and no glyphs).
- **Listening Stats** (`LiquidStatsScreen.kt`) — Replay: a coloured hero counting the
  year's minutes, period switch, top artists and songs as ranked rows.
- **On This Device** (`LiquidLocalSongsScreen.kt`) — the local library: a scan sheet,
  Select mode with a glass action bar, and sort as a pull-down.
- **Recognition** (`LiquidRecognitionScreens.kt`) — a glass orb that breathes while
  listening and throws ripples, the result as its own page, and a recognised history.
  The listening animation is composed only while listening.
- **Listen Together** (`ui/liquid/together/`, engine in `together/`, server in `server/together`) —
  the lobby says what it is in one hero (three faces and the sound between them), *Start a
  Session* and *Join with a Code*; the six code cells look the session up before you knock
  (host, song, listeners). In a session the page is the room: a **stage** on a field of the
  current song's colour (live code pill, faces, cover, "added by", the room's progress from
  the server clock, and either controls or *Vote to Skip* / *Pause for Me*), the reaction
  keys, a guest's *On This Phone · Remote* switch with a sync line ("In sync with Ana"), the
  shared **Up Next** (monogram of whoever added each song, vote pills, Add sheet with your
  picks, likes and search), People (host ★, mode, away), Messages (iMessage bubbles), the
  host's **house rules**, Leave / End for everyone. Invites are a QR code any camera opens,
  the code set in two halves (ABC 234), and a share link. Now Playing wears a glass badge
  with the faces in the session and a reaction key; reactions rise over the player. A
  guest's taps anywhere in the app add to the session instead of playing (`PlaybackGate`).
- **Account** (`settings/AccountSettingsScreen.kt`) — you, big (picture, name, address) or
  *Sign In with Google*; YouTube Music switches; the accounts on this phone (tap to switch,
  ⋯ to remove, Add Another); other services (Spotify, Amazon Music, ListenBrainz); Sign Out.
  **Sign-in pages** (YouTube Music `LoginScreen`, Spotify `SpotifyLoginPage`) are the
  provider's page full screen under a glass header (name, lock + host, reload) with a
  hairline of progress. Spotify catches *Continue with Google/Facebook* — which those
  providers refuse inside apps — and offers the email login code, a password, or a cookie.
- **Appearance** (`settings/AppearanceSettingsScreen.kt`) — a live Now Playing stage at the
  top (`AppearancePreview`, drawn by the player's own background, glow and glass code),
  an *Experience* preset (Minimal / Balanced / Immersive — derived from the settings, never
  stored, "Custom" otherwise), then Theme, Accent, Now Playing (Atmosphere, Artwork glow),
  Surfaces (Material), Motion (Artwork motion, Page transitions), Layout (Interface size;
  enlarging stops at 320dp wide / 720dp tall so Now Playing still fits) and *Reset appearance*, which clears only appearance keys.
  Every default is the look Shiny shipped with.
- **Onboarding / What's New** (`LiquidWelcome.kt`) — paged, glass over a coloured field,
  shown from `MainActivity` instead of the old dialogs.

Shared building blocks in `LiquidComponents.kt` / `LiquidShelves.kt`: `Artwork`,
`MediaTile`, `SongRow` (separator starts under the text; ⋯ on the right), `SectionHeader`
("Title ›"), `Shelf`, `SongGrid`, `CategoryTile`, `NavigationRow`, `LiquidButton`,
`GlassIconButton`, `LiquidSegmentedControl`, `ScopeCapsule`, `LiquidSearchField`,
`ActivityIndicator` (iOS spinner), `NowPlayingBars`, `EmptyState`. Press feedback is the iOS
cell highlight (`LiquidHighlight`) or a spring scale — **never a ripple**.
`LiquidActions` centralises what tapping/long-pressing any item does.

`LiquidMenu.kt` holds the two things that open in their own window:
- **`LiquidPullDownMenu`** — the iOS pull-down: `MenuAction` rows (label, glyph on the
  right, `destructive` in red), `MenuDivider`, `MenuHeader`; it grows from the corner
  nearest its anchor and dismisses on any choice.
- **`LiquidAlert`** — the iOS alert: title, message, capsule buttons stacked when there are
  more than two, the destructive one red and the cancel one plain.

Both live in a popup window, so they **cannot sample the page** — their surfaces are
opaque. (A 0.97-alpha surface plus a shadow bled the page through visibly.)

### Panels and option sheets (`LiquidPanel.kt`, `ui/component/Menu.kt` · `NewMenuComponents.kt` · `BottomSheetMenu.kt`)

The sheet a ⋯ or a long press opens, and the Settings index, are built from one raised
surface: the **panel**. `Modifier.liquidPanel()` is a fill and a rim and nothing else. In
the dark themes the fill is translucent, so the ground's colour shows through it, and the rim
is a hairline that is brightest along the top edge; in Light it is the plain white grouped
cell. No blur, no backdrop, no shadow: a sheet is its own window and has nothing to sample.

- **The sheet** (`BottomSheetMenu`) stops 8dp below the status bar when fully open, so the
  page shows above it. Its grabber is a 36×5 capsule in a 24dp band.
- **Atmosphere** (`SheetAtmosphere`). A sheet about a piece of artwork takes its colour from
  it: light falling from the cover in the header, in the cover's own tones, dying away
  towards the foot. It is **one radial gradient**, built when the artwork changes and
  cached; the tones come from the 112-pixel decode and cache the collection pages already
  use. The header names the artwork (`MenuHeader(atmosphere = …)`), the sheet draws it, and
  a sheet with no header stays neutral. In the dark themes the tones are held to a luminance
  of 0.05 under the rows (0.065 behind the header), which is what keeps secondary text at
  4.5:1 on a panel; a cover with no colour gives half as much, so it does not turn the sheet
  a lighter grey.
- **Header** (`MenuHeader`). An 88dp cover, the name in `title3`, then up to two quieter
  lines (artist and album; a playlist's length). No divider under it.
- **Tiles** (`NewActionGrid`). Up to three per row, 72dp, glyph over a 13sp label. A tile
  that is on (Repeat) is filled with the accent at 32% and keeps white type.
- **Rows** (`Material3MenuGroup`). Label and one optional line; hairlines start under the
  text and stop short of the trailing edge. By default the glyph sits at the trailing edge,
  the arrangement of a context menu. `MenuGlyphs.Leading` moves it to the front for a sheet
  that is mostly places to go (the Now Playing song sheet), where a row that opens a page
  ends in a chevron (`chevron = true`) and a row that acts on the spot ends in nothing.
  `destructive = true` draws a row's label and glyph in the warning colour; Delete gets a
  panel of its own.
- **Glyphs.** Outline, 24dp, in `label`. Share is `LiquidIcons.Share` on these sheets; a pin
  is the outline `LiquidIcons.Pin` until the thing is pinned, then the filled one.

The colour is the artwork's and belongs to the surface that is about that artwork. **Page
grounds stay neutral**: the Settings index is panels on black, with no atmosphere.

Older screens get the look through swaps rather than rewrites: `LiquidTopAppBar` and
`LiquidSwitch` replace Material's via import alias, `Material3SettingsGroup` and the menu
components (`Menu.kt`, `NewMenuComponents.kt`, `BottomSheetMenu`) render as iOS grouped
sections, and the `arrow_back` / `more_vert` glyphs are now chevron and ellipsis.

---

## 5. Now Playing (`ui/liquid/player/`)

One screen, three modes, one gesture each (the mode-driven design):

- **Stage** — grabber, **living artwork** (breathes to 82% when paused), title/artist with
  glass ★ and ⋯, scrubber, transport, system volume, and the lyrics · output · queue row.
- **Lyrics** — the artwork flies into a header thumbnail; big bold left-aligned lines,
  current line sung **word by word**, neighbours dimmed and blurred by distance,
  auto-follow that yields to the finger until a clear-glass **Sync lyrics** capsule (low,
  centred, arrow pointing to the sung line; only there while scrolled away) hands it back;
  tap a line or seek to resume too.
- **Queue** — Shuffle / Repeat / Autoplay glass capsules, *Playing Next* with drag
  handles, history above; with Autoplay on it always offers a real run of songs.
- **Landscape** — artwork left (living too), controls or the lyrics/queue page right.

### Living artwork (`player/LiquidLivingArtwork.kt`)

If the track has motion artwork (Apple Music, Tidal, or the Shiny canvas providers —
validated and cached) it loops in the cover's place, silently. A track without it shows its
cover as it is: a still picture, never moved to look like one that has it.

The living cover is the exception, and it is **off unless chosen** (Appearance → Motion →
Artwork motion → Living, or the Immersive experience): the cover itself drifts and zooms
under an AGSL **liquid ripple** and a band of light passing over it like a reflection on
glass (API 33+; below that, the drift and a gradient sheen). It is never put over an album's
own clip, nor on the Poster. It is driven from a frame clock read in the layer and draw
phases, so it animates **without recomposing**, and it runs only while playing, expanded,
and in Stage — a paused or hidden player costs nothing. Both halves are switchable
(`LiquidPrefs.LivingArtwork`, default off; `LiquidPrefs.MotionArtwork`, default on).

### Artwork presentations (Appearance → Now Playing → Artwork)

- **Card** (default) — the square cover on a stage of its own colour.
- **Poster** (`player/LiquidPosterStage.kt`) — an album's **portrait artwork** (Apple's 3:4
  motion artwork: its still, with its clip playing over it), edge to edge at the head of the
  player, in its own shape. Its foot goes out of focus into a field of its own colours, which
  carries on under the title and the controls. Portrait only.

**Artwork is never enlarged, stretched or cropped to fill.** A picture is asked for at the
pixels it covers and drawn at that size. That is why the Poster is only for portrait artwork:
a square sleeve hung that way has to have its foot dissolved, and a cover is drawn to its
edges. An album without portrait artwork keeps the Card, exactly as with the Poster off, and
so does every song until its portrait artwork has loaded; the change from card to poster is a
short dip of the whole stage (cover and title together), never a jump. (A "Full screen"
presentation that enlarged the square cover to the screen's height was removed on 2026-10-05
for the same reason: a 720-pixel video still drawn three times its size.)

The Poster is three still pictures and no effects: the **ground** (the picture blurred,
standing on its own blurred mirror image, shaded towards the foot), the **picture**, and the
**veil** (a strip of the same ground with an alpha ramp baked in, laid over the picture's
foot). The ground and veil are baked once per song at ~96 pixels across and drawn as two
textured quads; nothing blurs, masks or composites at draw time, and the picture's own layer
is never masked. The ground is part of the recorded player backdrop, so glass over it
refracts the right picture, and while it covers the stage the rotating field underneath
neither turns nor draws. A change of song mixes the two songs' pixels for 650 ms (an exact
crossfade, no second layer). The shade under the type is solved per picture
(`posterTitleScrim`: the 90th-percentile luminance there is brought to a target), and shaded
colour is given its saturation back (`posterDepthRichness`) so the foot is a rich dark and
not a grey one.

Sharpness, measured (SZA *SOS*, 1080-pixel screen): the portrait still is asked for at
exactly the screen's width with the image server's `-100` quality (48 dB against the
uncompressed picture; the ordinary file is 35 dB), and the clip plays the size that covers
those pixels (1078×1438 of the thirty Apple makes, 310 to 2048 across) at that size's best
bitrate, not adaptively. A clip is ~14 MB at that size, fetched once and kept.

### Artwork sources (`artwork/`)

```
                 Apple catalogue
          ┌────────────┴────────────┐
    static artwork            editorial video
    (master: 3000²)        ┌───────┴───────┐
          │              square           tall
          └────────────────┴───────────────┘
                   AppleArtworkResult → Now Playing
```

`ArtworkResolver` is the one place that knows where a song's artwork comes from:

1. **The song's own artwork** — a file's embedded cover, or what YouTube / Spotify gave. Shown
   first and at once; nothing waits on anything below.
2. **Apple's cover** — for a song whose own picture is a music video's still (720 px at best)
   or missing. Public iTunes Search API, matched on artist + title (+ album, length), never the
   first result on trust; a song that already has a real cover keeps it, and a file on the
   phone is never looked up. Toggle: Now Playing → *Official covers*.
3. **Apple's motion artwork** — the album's `editorialVideo`, square and tall, asked for by
   the album id the match gave. HLS, with the plain MP4 behind one size of each as the
   fallback. Everything that needs the web-player token is behind
   `AppleMotionArtworkProvider`. Toggle: *Animated covers*.

One catalogue lookup feeds all three parts of an `AppleArtworkResult` (cover at any size up
to the 3000-pixel master, square motion, tall motion). Results (hits and misses) are kept in
memory and in `files/artwork/index.json` under a key that identifies the recording (ISRC,
else artist|album|track), simultaneous askers share one request, and "could not ask" is
never recorded as "has none". Pictures go through Coil (its memory and disk caches); clips
through `MotionArtworkCache` (160 MB, least recently used first). A picture is only shown
once it has loaded into memory, so a change is one picture giving way to another and never a
blank. Nothing is fetched under Data Saver.

### Karaoke lyrics (`player/LiquidLyrics.kt`)

Every synced line gets word timings — the provider's, or an estimate spreading the line's
time over its words by length. Words are drawn with `drawText` from a frame-interpolated
clock: a soft gradient fill edge crosses each word, the sung word lifts 3.5dp with a glow
and a light pool under it, and a long note swells. A silence of 4.5s or more between lines
is an interlude: Apple's three dots breathe and fill under the line, and the line that was
just sung steps back so the dots hold the eye.

### Transport (`player/LiquidTransport.kt`)

Three glass discs — play 86dp, skips 66dp (72/56 in landscape). The play glyph morphs
between ▶ and ❚❚ rather than swapping, the skip arrows nudge in their direction on tap,
and a press blooms the disc. No ripples, no icon buttons.

### Up Next (`playback/MusicService.kt` · `player/LiquidQueue.kt`)

`ensureUpNext(minimum = 15)` pages the queue and then tops it up from the song's radio
(`RDAMVM<id>`), de-duplicated, so *Playing Next* is a real run of songs rather than the
two or three left in the queue. It runs on open and on every index change, and **only with
Autoplay on** — with Autoplay off the queue stays exactly what the user built.

### Ambient Mode (`ui/screens/ambient/AmbientModeScreen.kt`)
Lyrics first, landscape. The cover (rounded 14dp, soft shadow, clipped so the living effect never
smears past it) with title and artist sits centred in the left 40%; the karaoke pane fills the
rest and dissolves into the backdrop at top and bottom (a DstIn fade, never a hard edge).
Nothing else shows until a tap: the player's own transport and scrubber fade in as one bar
along the foot, on the page grid (transport under the cover, scrubber starting where the lyrics
start), the cover lifts 30dp out of its way, and a close button appears top-right; all of it
fades out 4 s after the last touch. A lyric line tap seeks and shows the controls. A per-song
veil darkens the lyric side just enough for bright covers (from the backdrop field's 90th
percentile brightness, aiming at ~14% luminance under the words). Instant transitions snap.

Rules that keep it smooth:
- Position is polled into state and handed to the scrubber and lyrics as providers;
  they read it in draw/effect scopes, so a tick recomposes nothing.
- The artwork's flight between modes is resolved at layout time from measured rects.
- The mini ↔ full flight reuses `PlayerArtworkFlight` / `playerArtworkAnchor`.
- The background is the artwork's pre-blurred field (`rememberPlayerBackdrop`) drawn as two
  oversized, slowly counter-rotating layers under a scrim — only while expanded and
  playing; paused, it is still. **Atmosphere** only moves that scrim and a static
  saturation filter (Off / Soft / Balanced = shipped / Immersive), so it costs nothing per
  frame; Off draws the no-artwork stage, black on AMOLED.
- **Artwork glow** is a radial halo in the palette's vivid swatch (`PlayerBackdrop.halo`),
  drawn by `Modifier.artworkGlow` *before* the cover's clipping layer. Its colour eases once
  per song from a snapshot flow; its strength is read at draw time, dims to 60% while the
  cover settles on pause, and is gone before the cover reaches the Lyrics/Queue header.
- The living artwork and the lyric clock are per-frame **by design** while they are on
  screen and playing. Both stop at the source — the effect that drives them is not composed
  otherwise — so a paused, collapsed or backgrounded player renders no frames at all.
- Sliders only start after horizontal touch slop, so a vertical swipe that begins on
  one still pulls the player down.

Legacy player options in Appearance (background style, button colours, slider style, lyric
animation styles) belong to the old player and do not affect this one.

---

## 6. Do / Don't

- **Do** use `Liquid.colors` and `LiquidTypography`; `MaterialTheme` only in legacy code.
- **Do** keep accent for tappable things; headings are `label`, supporting text
  `secondaryLabel`.
- **Do** give glass something to refract, and give it its own backdrop.
- **Don't** put glass inside the content it would sample.
- **Do** build grouped surfaces with `liquidPanel()` and the panel tokens; don't pick a
  radius, inset or fill per screen.
- **Don't** put an atmosphere on a page ground. Colour comes from artwork, on the surface
  that is about that artwork.
- **Don't** use ripples, Material FABs, filled cards with drop shadows, or gradient badges.
- **Don't** animate anything idle except the playing Now Playing backdrop.
