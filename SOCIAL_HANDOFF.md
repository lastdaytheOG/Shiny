# Social integrations — handoff

Started 14 September 2026. Nothing is committed; the user does the commits.

## 0. NEXT — start here when the user types `continue`

**State when the session closed (14 September, ~19:45):**
- All code is written: server, web front and app.
- The server passed its local smoke test (36/36).
- **The app has not compiled yet.** The first `:app:assembleX86_64GmsDebug` exited with code 1 during `processX86_64GmsDebugResources`, printing no error: no `e:` lines, no crash log, 16 GB RAM free. A rerun with `--console=plain --stacktrace` was still running at close; its result is lost.
- One compile error was found by reading and fixed: `LiquidSocialScreens.openInBrowser`, where `Intent.setSelector` returns void.
- The background `wrangler dev` server also ends with the session.

Do in order:
1. **Build.** Run `./gradlew :app:assembleX86_64GmsDebug --console=plain --stacktrace`, with JAVA_HOME and one JVM (PERF_HANDOFF section 6). If it dies silently again, try without `-Pkotlin.compiler.execution.strategy=in-process`.
   - Fix errors in the new or changed files: `social/*` (`SocialApi`, `SocialRepository`, `SocialPresencePublisher`, `ShinyLinks`, `GoogleSignIn` in gms and foss), `ui/liquid/screens/LiquidSocialScreens.kt`, `MusicService` (publisher hooks, `playQueue(startPositionMs)`), `PlayerConnection.playQueue`, `MainActivity` deep links, `DiscordRPC` buttons, `NavigationBuilder` routes, `SettingsScreen` group, `core` PreferenceKeys, `values/social_strings.xml`, `AndroidManifest`, the Credential Manager dependencies.
   - Then compile FOSS: `:app:compileX86_64FossDebugKotlin`.
2. **Local testing needs two debug-only additions** (not written yet), because Google sign-in can't work until the user's Firebase config exists:
   - A server URL field (`SocialServerUrlKey`) in SocialSettingsScreen, shown only when `BuildConfig.DEBUG`.
   - A "Dev sign-in" that calls `POST /v1/auth/dev`, which the server accepts only with `ALLOW_DEV_LOGIN=true` in `.dev.vars`.
3. **Emulator test against the local server** (`cd server && npm run dev`; the emulator reaches it at `http://10.0.2.2:8787`; `npm run db:local` if `.wrangler/` was cleared):
   - sign in, set a username, share mode, private session
   - presence appears for a second account (curl a dev session and add friends)
   - Friends list, Listen along starts at the friend's position, Join room
   - deep links: `adb shell am start -a android.intent.action.VIEW -d "shinymusic://watch?v=dQw4w9WgXcQ&t=60"`, `https://shinymusic.vercel.app/u/<name>`
   - share links use the new domain
4. **Waiting on the user** (asked 14 September), then deploy (steps below):
   - ~~a new `google-services.json`~~ **done 15 September**: `app/google-services.json` is now `shinymusic-1` (both packages, Web client `971028658395-vk5qhva85bct39tpk5b5ipdbdv5e7dcg`, verified with the plugin). The re-download with the release SHA-1 (`f9b6f06d…` on `com.shiny.music`) was verified and swapped in later that day. On `com.shiny.music` it *replaced* the debug SHA-1, so Google sign-in doesn't work on debug-signed builds that keep the release package name (`benchmarkRelease`, `nonMinifiedRelease`). Re-add `4D:4D:CA:71…` there if that's needed
   - ~~the release SHA-256~~ **done 15 September**: Shiny's own release key (see Signing below); `ANDROID_CERT_SHA256` is filled in `server/wrangler.toml`
   - their own Discord Application ID
5. **After this feature:** the paused performance work resumes at R-09 (PERF_HANDOFF.md section 0).

---

The user asked for three features, with their options in Settings:
1. **Discord "Listen along"**: the Discord activity shows what you're playing, with a button that opens the same song in Shiny. Connecting Discord must be the easiest possible flow.
2. **Live profile page + badge**: a public web page showing what someone is playing now, and an embeddable badge.
3. **Friend activity in Shiny**: see what friends are playing, and tap to play it or join them.

## Decisions (asked 14 September)
| Question | Answer |
|---|---|
| Server | **Cloudflare Workers** (with D1 for storage) |
| Web address | **`shinymusic.vercel.app`** for now (Vercel free tier). It will later move to shinymusic.co or shinymusic.fun, so keep the base URL in one place. |
| Identity | **Sign in with Google.** Also make connecting Discord easy, so the listening activity shows there. |

## Facts found before building
- `shinymusic.fun` and `share.shinymusic.fun` **don't exist in DNS**: every share link the app makes today (`https://share.shinymusic.fun/watch?v=…`, ~15 call sites) is dead on the web.
- `shinymusic-listen-together.onrender.com` is up, but `/listen?code=` returns 404. Listen Together's WebSocket servers are third-party (Metrolist, a Hugging Face space from `app/server.json`), so we can't add endpoints to them.
- `shinymusic.vercel.app` returns Vercel's `DEPLOYMENT_NOT_FOUND`, so the name is probably free to claim.
- `app/google-services.json` (the old cloned project) has **no OAuth clients**. Sign in with Google needs an Android OAuth client (package + SHA-1) and a Web client ID, created in Google Cloud Console by the user.
- Discord: presence already supports up to 2 buttons (`DiscordPresenceButton`, URL sources in `DiscordRPC.resolveUrl`). The OAuth client (`DiscordOAuthRepository`, PKCE, scopes `openid identify`) exists, but **no UI starts it**: Settings has no Discord entry.
- Joining a Listen Together room by link works (`MainActivity.handleDeepLinkIntent`: `/listen?code=`).
- Tools: Node 18.20.4 and the Vercel CLI are installed. Wrangler 4 needs Node ≥ 22, so the server pins **Wrangler 3** (Node ≥ 16.17).

- **The user is replacing Firebase** (14 September): the repo's `google-services.json` is the old cloned project. A new Firebase project will supply analytics, Crashlytics and the Google sign-in OAuth clients.
- Signing: debug builds (and the benchmark/test APKs) use `~/.android/debug.keystore`: SHA-1 `4D:4D:CA:71:7B:A3:B9:D7:3D:EC:91:12:DF:82:6F:57:51:DB:10:6B`, SHA-256 `79:C7:EE:06:6D:80:31:C2:AA:E5:9F:59:26:31:F5:B4:7D:6C:36:9F:6E:AF:F7:5F:AE:92:B8:21:B4:33:DA:05`. Release builds are signed with Shiny's own key, created 15 September (PKCS12, alias `shiny-release`, kept outside the repo; CI secrets `SHINY_KEYSTORE_BASE64` + `SHINY_KEYSTORE_PASSWORD`): SHA-1 `F9:B6:F0:6D:68:E0:57:0E:BA:6A:CF:07:79:00:E1:0A:C4:60:64:3D`, SHA-256 `F3:70:1C:9C:1C:87:54:F4:79:A9:D2:20:D6:E6:56:B1:12:1C:CA:43:B6:1C:7B:BE:FD:15:D8:32:A8:2B:19:46`. The upstream key was never available, so every earlier `com.shiny.music` APK is debug-signed.

## User steps: new Firebase project (sent 14 September)
1. Add project.
2. Android app `com.shiny.music`: the debug SHA-1 above, plus the release key's SHA-1.
3. Android app `com.shiny.music.debug`: the debug SHA-1.
4. Authentication → Sign-in method → Google → Enable (this creates the Web client ID).
5. Download `google-services.json` **after** step 4 and replace `app/google-services.json`.
6. Send the release SHA-256 (for `ANDROID_CERT_SHA256` / assetlinks).

## Plan
1. **Server** (`server/`, Cloudflare Worker + D1): Google ID-token sign-in → Shiny session; username; presence (now playing, updated by the app); friends (request by username, accept, remove); public profile JSON; HTML profile page `/u/:username`; SVG badge `/badge/:username.svg`; listen-along landing page `/watch?v=…&t=…` that opens Shiny or falls back to YouTube Music.
2. **Web front** (`web/`, Vercel): `vercel.json` rewrites to the Worker, plus `/.well-known/assetlinks.json` for app links.
3. **App**:
   - One base-URL constant.
   - Share links moved to it; deep links for the new host (`/watch` with `t`, `/u/:username`).
   - Social settings screen: Google sign-in, username, share toggles, private session, connect Discord, "Listen along" button.
   - Friends screen.
   - Presence publisher in `MusicService`.
4. **User steps**: Google Cloud OAuth clients, `wrangler login` + D1 create + deploy, `vercel login` + deploy.

## Discord application (SHINY)
`app/build.gradle.kts` uses SHINY's own Discord application: `discordApplicationId = "1549351860807270513"`, named SHINY (the redirect scheme follows the ID). Discord shows the application's name and icon on its authorization page and in the activity ("Listening to SHINY"), and only accepts the OAuth redirects it lists. The app depends on these Developer Portal settings:
1. OAuth2: the redirect `discord-1549351860807270513:/authorize/callback` (Discord's mobile form: one slash, no host), exactly what the app sends, and **Public Client** on (PKCE, no secret). Discord answers any other redirect with "Invalid OAuth2 redirect_uri".
2. Discord Social SDK: the Getting Started form submitted, so the app may request `sdk.social_layer_presence`. The app asks for `openid identify sdk.social_layer_presence`.
3. Rich Presence → Art Assets: `shiny_logo`, the activity's large image. It is sent as the asset's ID, looked up from the key.
4. General Information → App Icon: SHINY's logo.

Sessions stored before this application existed were issued to the previous, inherited Discord application. They are cleared on first use, and the Discord settings show "Reconnect Discord". Presence goes over the Discord Gateway with the OAuth token; debug builds log each step under `adb logcat -s ShinyDiscord`.

## Deploy (user runs these; each needs their own login)
Run from Git Bash in the repository. Node 18 works with the pinned Wrangler 3.

**1. Cloudflare Worker + D1**
```bash
cd server
npm install
npx wrangler login                          # opens the browser
npx wrangler d1 create shiny-social         # copy the database_id it prints into wrangler.toml
npm run db:remote                           # creates the tables
```
Then in `server/wrangler.toml`, under `[vars]`:
- `GOOGLE_CLIENT_IDS` = the Web client ID. After Firebase step 4 it's in `app/google-services.json` → `oauth_client` with `"client_type": 3`, or in Google Cloud Console → Credentials → "Web client (auto created by Google Service)".
- `ANDROID_CERT_SHA256` = the release SHA-256, plus the debug one above, comma-separated.

```bash
npm run deploy                              # prints https://shiny-social.<subdomain>.workers.dev
```

**2. Vercel front (`shinymusic.vercel.app`)**
- Put the Worker URL from step 1 into `web/vercel.json` (`destination`).
```bash
cd web
npx vercel login
npx vercel link                             # new project named "shinymusic", so the URL is shinymusic.vercel.app
npx vercel --prod
```
- Check: `https://shinymusic.vercel.app/watch?v=dQw4w9WgXcQ` shows the listen-along page, and `/.well-known/assetlinks.json` lists the packages.

**3. App**
- Replace `app/google-services.json` (Firebase steps above), then rebuild.

**Local testing without deploying**
- `cd server && cp .dev.vars.example .dev.vars && npm run db:local && npm run dev` serves on http://127.0.0.1:8787, with `POST /v1/auth/dev {"sub":"x"}` for a test session.
- An emulator reaches it at `http://10.0.2.2:8787`: debug builds read `SocialServerUrlKey` (DataStore `social_server_url`) instead of the Vercel address.

## Status
- [x] **Server** (`server/`): API, pages, badge, `/playlist` and `/channel` link pages. Local `wrangler dev` smoke test: 36/36 checks passed (sign-in, usernames, share modes, presence, friends, public profile, pages, badge with embedded art, account deletion, sign-out).
- [x] **Web front** (`web/vercel.json`): a catch-all rewrite to the Worker. The Worker URL is filled in after the first deploy.
- [ ] **App**:
  - [x] `social/ShinyLinks`, `SocialApi`, `SocialRepository`, `SocialPresencePublisher`
  - [x] `GoogleSignIn` (gms: Credential Manager; foss: stub)
  - [x] Credential Manager dependencies and R8 rule
  - [x] Preference keys
  - [x] MusicService hooks: `socialPresencePublisher.attach(::socialPlaybackSnapshot)` once the player is ready; `notifyChanged()` on song change, play/pause, seek and Listen Together room change; `detach()` in `onDestroy`
  - [x] Discord listen-along button: the first button, `ShinyLinks.listenAlong(id, position, duration)`, toggled by `DiscordListenAlongButtonKey` (default on)
  - [x] Deep links: `shinymusic://watch|playlist|channel|u` are read as website paths; `t` starts playback there (`playQueue(startPositionMs)`); `/u/<name>` opens `friends?add=`
  - [x] Share links and Listen Together invites → `ShinyLinks.WEB_BASE` (app) or the literal new URL (`core`, `innertube`); the manifest has the new host
  - [x] Routes `settings/social`, `settings/discord`, `friends?add=`; a Settings group "Friends & Profile" + "Discord"; `values/social_strings.xml`
  - [x] Screens (`ui/liquid/screens/LiquidSocialScreens.kt`):
    - **SocialSettingsScreen:** Google sign-in card, account header, username field, who sees what you're playing (Nobody / Friends / Everyone), private session, profile page / share / copy badge, links to Friends and Discord, sign out, delete account.
    - **DiscordSettingsScreen:** one-tap Connect (browser OAuth, returns through `DiscordOAuthCallbackActivity`), show in status, Listen along button, show when paused, disconnect.
    - **FriendsScreen:** add by username, incoming and sent requests, friends with live songs refreshed every 20 s. Listen along plays in Shiny from their position; Join room when they're in Listen Together; long-press removes a friend; `?add=` from a profile link asks to add.
  - [ ] Build (gms debug started 14 September), then the foss flavor
- [ ] Deployed and tested end to end
