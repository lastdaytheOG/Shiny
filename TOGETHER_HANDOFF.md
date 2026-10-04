# Listen Together (Shiny Together) — handoff

Rebuilt from scratch on 27 September 2026. Nothing is committed; the user does the commits.

## Why it was rebuilt, and what changed underneath

The old Listen Together spoke Metrolist's protocol to third-party servers. Probed on 27 September:
- `wss://metroserverx.meowery.eu/ws` (the app's default) now refuses to host: `host_not_allowed` — *"Only allowlisted clients
  can host rooms"*. Starting a room from Shiny could not work any more.
- The fallback list (`raw.githubusercontent.com/lastdaytheOG/Shiny/…/app/server.json`) is a 404.
- A Hugging Face space from another app still answered in JSON, but it's someone else's server.

So Listen Together now runs on **Shiny's own server**: `server/together`, a Cloudflare Worker with one SQLite-backed
Durable Object per session (Workers Free plan). Protocol: `server/together/PROTOCOL.md`.

## Deployed (27 September 2026, Cloudflare account "shinymusic")

| Worker | Address | What |
|---|---|---|
| `shiny-together` | https://shiny-together.shinymusic.workers.dev | Listen Together sessions (the app's API, baked in via `shiny.together.url` in `gradle.properties`) |
| `shiny-social` | https://shiny-social.shinymusic.workers.dev → **shinymusic.in** once the domain is on Cloudflare | Shiny's site: song / playlist / artist / profile pages, `/j/CODE` invites (from `shiny-together` through a service binding), `/download`, assetlinks, the social API (D1 `shiny-social`) |

Both passed their checks live: `TOGETHER_URL=https://shiny-together.shinymusic.workers.dev npm run together:test` → 35/35.

Redeploy after changes: `cd server && npm run together:deploy` (sessions) / `npm run deploy` (site).

### shinymusic.in — live (27 September)
The domain (bought at GoDaddy) is on Cloudflare (nameservers ernest/harleigh.ns.cloudflare.com). `shiny-social` answers
on `shinymusic.in/*` and `www.shinymusic.in/*` through **Worker routes** (see `server/wrangler.toml`), not custom
domains: the zone still holds the proxied records imported from GoDaddy (they point at a GoDaddy Website Builder page),
and wrangler's login may not edit DNS. The routes answer every path first, so those records are never reached. To switch
to custom domains later, delete the records for `@` and `www` in the Cloudflare dashboard and use
`{ pattern = "shinymusic.in", custom_domain = true }`.

Verified: home, `/watch`, `/j/CODE`, `/download`, `www`, `/.well-known/assetlinks.json`; on the emulator
`pm get-app-links com.shiny.music.debug` → `shinymusic.in: verified`, `www.shinymusic.in: verified`, and an invite link
opened Shiny straight into the session.

## Local testing

```bash
cd server
npm run together:dev        # http://127.0.0.1:8788, also 10.0.2.2:8788 from the emulator
npm run together:test       # 35 checks: host, approval, block, open door, playback, shared queue + attribution,
                            # guest control, votes, reactions, chat, skip vote, mode, token resume, hand-over, end
```

Scripted guests/hosts used for the emulator runs are simple `ws` clients (see `together/test/smoke.mjs` for the shape).

## What was verified on the emulator (27 September, x86_64 GMS debug, local server)

- Lobby, start a session (code, stage coloured from the song, reactions, Up Next from the player's queue).
- Join request as a heads-up notification with **Let In / Decline**, and as a row in the room; approving from the page.
- A guest's add, play-next, reactions (floating with names over the page and over Now Playing), chat, votes.
- Following as a guest: joins at the room's position, pause / resume / seek follow; drift closes with a 1.5 % speed bend
  and holds within 35 ms (≈ 100 ms closed in 6 s; a 10 s seek settles in 3 s). *Pause for Me* / *Catch Up*, *Vote to
  Skip* (1 of 2), the ⋯ menu entries, the Now Playing badge.
- Host hand-over: the scripted host left and after 45 s the emulator became host and carried on.
- Leave.

**Not verified:** two real phones over a deployed server (Bluetooth latency, mobile networks); the *Remote* mode by hand;
QR scanning with a camera; the invite page on a phone without Shiny; release (R8) build.

## 28 September: search-and-play, no "waiting for friends"

- The Now Playing badge only appears once someone else is in the session (it used to say "Together · waiting for
  friends"); the string is gone.
- The room page has a search field under the reactions. It opens the pick sheet focused, with **Play Now · Play Next ·
  Play Last** for whoever drives (host, or guests when "guests control playback" is on) and Play Last · Play Next for
  everyone else. `TogetherSession.playNow`: the host inserts after the current song and seeks to it; a driving guest
  sends `add(next)` then `control(next)`, which the server delivers to the host in that order (the add is stored first;
  a Durable Object takes no new message while storing). Local files are left out of the sheet's lists.
- Verified on the emulator against the deployed server: host Play Now kept Up Next; guest Play Now with a scripted host
  logged `ADD next=true` then `CONTROL next` in the same millisecond, and the guest stayed in sync.
- From this PC one of the server's Cloudflare addresses (172.67.189.93) times out while 104.21.57.62 answers; Node
  scripts had to pin the working one. Not seen from the emulator.

## Where things are

| | |
|---|---|
| Engine | `app/…/together/` — `TogetherSession` (state, hosting, following, `PlaybackGate`), `TogetherSocket` (reconnect, clock pings), `TogetherSync` (clock + drift policy), `TogetherModels`, `TogetherNotifications` |
| UI | `app/…/ui/liquid/together/` — `TogetherScreen` (lobby / arriving / room), `TogetherChatScreen`, `TogetherAddSongs`, `TogetherPlayerBadge`, `TogetherComponents` (monograms, reactions, QR, code cells) |
| Settings | `ui/liquid/settings/TogetherSettingsScreen.kt` (`settings/together`) |
| Hooks | `PlayerConnection.gate` (guests' picks → shared queue), `MusicService` (no crossfade in a session, no radio top-ups while following), `MainActivity` (deep links `listen?code=`, `/j/CODE`, `shinymusic://together`), `PlayerMenu`, Library row, Settings row, Friends "Join" |
| Server | `server/together/src/index.js`, `pages.js`, `PROTOCOL.md`, `test/smoke.mjs`, `wrangler.toml` |
| Tests | `app/src/test/…/together/TogetherSyncTest.kt` (clock, drift policy, codes) |

The old `listentogether/` package, its protobuf schema and the protobuf Gradle plugin, `ListenTogetherSettings`,
`LiquidTogetherScreens`, the 1 000-line dialog in `PlayerMenu` and ~110 unused strings (all locales) are gone.
