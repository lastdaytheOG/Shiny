import { verifyGoogleIdToken } from "./google.js";
import {
  HttpError,
  badRequest,
  unauthorized,
  notFound,
  json,
  html,
  preflight,
  readJson,
  sha256Hex,
  randomToken,
} from "./http.js";
import { watchPage, roomPage, profilePage, notFoundPage, badgeSvg, linkPage } from "./pages.js";

const YOUTUBE_ID_RE = /^[A-Za-z0-9_-]{10,64}$/;

const USERNAME_RE = /^[a-z0-9_.]{3,20}$/;
const RESERVED_USERNAMES = new Set(["admin", "shiny", "support", "help", "api", "badge", "listen", "watch", "settings", "friends"]);
const VIDEO_ID_RE = /^[A-Za-z0-9_-]{11}$/;
const ROOM_CODE_RE = /^[A-Za-z0-9]{4,16}$/;
const SHARE_MODES = ["off", "friends", "public"];
// Only artwork from Google's image hosts is stored: pages and badges load it for anyone who views them.
const IMAGE_HOST_RE = /^https:\/\/([a-z0-9-]+\.)*(ytimg\.com|googleusercontent\.com|ggpht\.com)\//i;

const SESSION_TTL_MS = 180 * 24 * 60 * 60 * 1000;
// A playing song the app stopped reporting is dropped after this: three missed heartbeats (the app
// re-sends every minute). A pause is kept for a while, then it is no longer "now playing".
// Same windows as PresenceFreshness in the app.
const PLAYING_STALE_MS = 3 * 60 * 1000;
const PAUSED_STALE_MS = 10 * 60 * 1000;

export default {
  async fetch(request, env, ctx) {
    try {
      return await route(request, env, ctx);
    } catch (error) {
      if (error instanceof HttpError) return json({ error: error.code, message: error.message }, error.status);
      console.error(error);
      return json({ error: "internal", message: "Something went wrong" }, 500);
    }
  },
};

async function route(request, env, ctx) {
  const url = new URL(request.url);
  const path = url.pathname.replace(/\/+$/, "") || "/";
  const method = request.method;
  if (method === "OPTIONS") return preflight();

  let match;
  if (method === "GET" || method === "HEAD") {
    if (path === "/watch") return watchRoute(url, env, ctx);
    if (path === "/listen") return roomRoute(url, env);
    // Listen Together invites: the session server renders them, the site gives them a short address.
    if ((match = path.match(/^\/j\/([A-Za-z0-9]{4,16})$/))) return inviteRoute(match[1], env);
    if (path === "/download") return Response.redirect(env.APP_DOWNLOAD_URL, 302);
    // Shared playlist and artist links from the app.
    if (path === "/playlist") {
      const list = url.searchParams.get("list") ?? "";
      if (!YOUTUBE_ID_RE.test(list)) return html(notFoundPage(env), 404);
      return html(linkPage({ kind: "Playlist", appPath: `playlist?list=${list}`, webUrl: `https://music.youtube.com/playlist?list=${list}` }, env));
    }
    if ((match = path.match(/^\/channel\/([A-Za-z0-9_-]{10,64})$/))) {
      return html(linkPage({ kind: "Artist", appPath: `channel/${match[1]}`, webUrl: `https://music.youtube.com/channel/${match[1]}` }, env));
    }
    if (path === "/.well-known/assetlinks.json") return assetLinks(env);
    if ((match = path.match(/^\/u\/([^/]+)$/))) return profileRoute(decodeURIComponent(match[1]), env);
    if ((match = path.match(/^\/badge\/([^/]+?)(?:\.svg)?$/))) return badgeRoute(decodeURIComponent(match[1]), env, ctx);
  }

  if (path === "/v1/auth/google" && method === "POST") return signInWithGoogle(request, env);
  if (path === "/v1/auth/dev" && method === "POST") return signInForDevelopment(request, env);
  if (path === "/v1/auth/session" && method === "DELETE") return signOut(request, env);
  if (path === "/v1/me" && method === "GET") return getMe(request, env);
  if (path === "/v1/me" && method === "PATCH") return updateMe(request, env);
  if (path === "/v1/me" && method === "DELETE") return deleteMe(request, env);
  if (path === "/v1/usernames/check" && method === "GET") return checkUsername(url, env);
  if (path === "/v1/presence" && method === "PUT") return putPresence(request, env);
  if (path === "/v1/presence" && method === "DELETE") return clearPresence(request, env);
  if (path === "/v1/friends" && method === "GET") return listFriends(request, env);
  if (path === "/v1/friends/requests" && method === "POST") return requestFriend(request, env);
  if ((match = path.match(/^\/v1\/friends\/requests\/([^/]+)\/accept$/)) && method === "POST") {
    return acceptFriend(request, env, decodeURIComponent(match[1]));
  }
  if ((match = path.match(/^\/v1\/friends\/requests\/([^/]+)$/)) && method === "DELETE") {
    return removeRequest(request, env, decodeURIComponent(match[1]));
  }
  if ((match = path.match(/^\/v1\/friends\/([^/]+)$/)) && method === "DELETE") {
    return removeFriend(request, env, decodeURIComponent(match[1]));
  }
  if ((match = path.match(/^\/v1\/users\/([^/]+)$/)) && method === "GET") return publicUser(decodeURIComponent(match[1]), env);

  if (path.startsWith("/v1/")) throw notFound();
  return html(notFoundPage(env), 404);
}

// ---- Accounts ------------------------------------------------------------------------------

async function signInWithGoogle(request, env) {
  const { idToken } = await readJson(request);
  if (typeof idToken !== "string" || idToken.length === 0 || idToken.length > 4096) throw badRequest("idToken is required");
  const claims = await verifyGoogleIdToken(idToken, env);
  return startSession(env, { sub: claims.sub, email: claims.email, name: claims.name, picture: claims.picture });
}

/** Local testing only: `.dev.vars` sets ALLOW_DEV_LOGIN=true for `wrangler dev`. Never set it in wrangler.toml. */
async function signInForDevelopment(request, env) {
  if (env.ALLOW_DEV_LOGIN !== "true") throw notFound();
  const { sub, name } = await readJson(request);
  if (typeof sub !== "string" || sub.length === 0) throw badRequest("sub is required");
  return startSession(env, { sub: `dev:${sub}`, email: null, name: typeof name === "string" ? name : sub, picture: null });
}

async function startSession(env, identity) {
  const now = Date.now();
  const avatar = typeof identity.picture === "string" && IMAGE_HOST_RE.test(identity.picture) ? identity.picture : null;
  let user = await env.DB.prepare("SELECT * FROM users WHERE google_sub = ?").bind(identity.sub).first();
  if (user) {
    await env.DB.prepare("UPDATE users SET email = ?, name = ?, avatar = ? WHERE id = ?")
      .bind(identity.email ?? null, identity.name ?? null, avatar, user.id)
      .run();
    user = { ...user, email: identity.email ?? null, name: identity.name ?? null, avatar };
  } else {
    user = {
      id: crypto.randomUUID(),
      google_sub: identity.sub,
      email: identity.email ?? null,
      name: identity.name ?? null,
      avatar,
      username: null,
      share_mode: "friends",
      created_at: now,
    };
    await env.DB.prepare(
      "INSERT INTO users (id, google_sub, email, name, avatar, username, share_mode, created_at) VALUES (?, ?, ?, ?, ?, NULL, 'friends', ?)",
    )
      .bind(user.id, user.google_sub, user.email, user.name, user.avatar, now)
      .run();
  }

  const token = randomToken();
  await env.DB.batch([
    env.DB.prepare("INSERT INTO sessions (token_hash, user_id, created_at, expires_at) VALUES (?, ?, ?, ?)").bind(
      await sha256Hex(token),
      user.id,
      now,
      now + SESSION_TTL_MS,
    ),
    // Expired sessions are swept at sign-in rather than on a schedule.
    env.DB.prepare("DELETE FROM sessions WHERE user_id = ? AND expires_at <= ?").bind(user.id, now),
  ]);
  return json({ token, user: selfView(user, env) });
}

async function requireUser(request, env) {
  const header = request.headers.get("Authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (!token) throw unauthorized();
  const user = await env.DB.prepare(
    "SELECT u.* FROM sessions s JOIN users u ON u.id = s.user_id WHERE s.token_hash = ? AND s.expires_at > ?",
  )
    .bind(await sha256Hex(token), Date.now())
    .first();
  if (!user) throw unauthorized();
  return user;
}

async function signOut(request, env) {
  const header = request.headers.get("Authorization") || "";
  const token = header.startsWith("Bearer ") ? header.slice(7).trim() : "";
  if (token) await env.DB.prepare("DELETE FROM sessions WHERE token_hash = ?").bind(await sha256Hex(token)).run();
  return json({ signedOut: true });
}

async function getMe(request, env) {
  return json({ user: selfView(await requireUser(request, env), env) });
}

async function updateMe(request, env) {
  const user = await requireUser(request, env);
  const body = await readJson(request);
  const next = { ...user };

  if (body.username !== undefined) {
    const username = normalizeUsername(body.username);
    if (!isValidUsername(username)) throw badRequest("Usernames are 3–20 characters: letters, numbers, _ and .");
    next.username = username;
  }
  if (body.shareMode !== undefined) {
    if (!SHARE_MODES.includes(body.shareMode)) throw badRequest("shareMode must be off, friends or public");
    next.share_mode = body.shareMode;
  }

  try {
    await env.DB.prepare("UPDATE users SET username = ?, share_mode = ? WHERE id = ?")
      .bind(next.username, next.share_mode, user.id)
      .run();
  } catch (error) {
    if (String(error).includes("UNIQUE")) throw new HttpError(409, "username_taken", "That username is taken");
    throw error;
  }
  if (next.share_mode === "off") await env.DB.prepare("DELETE FROM presence WHERE user_id = ?").bind(user.id).run();
  return json({ user: selfView(next, env) });
}

async function deleteMe(request, env) {
  const { id } = await requireUser(request, env);
  await env.DB.batch([
    env.DB.prepare("DELETE FROM presence WHERE user_id = ?").bind(id),
    env.DB.prepare("DELETE FROM friends WHERE user_id = ? OR friend_id = ?").bind(id, id),
    env.DB.prepare("DELETE FROM friend_requests WHERE from_id = ? OR to_id = ?").bind(id, id),
    env.DB.prepare("DELETE FROM sessions WHERE user_id = ?").bind(id),
    env.DB.prepare("DELETE FROM users WHERE id = ?").bind(id),
  ]);
  return json({ deleted: true });
}

async function checkUsername(url, env) {
  const username = normalizeUsername(url.searchParams.get("u"));
  const valid = isValidUsername(username);
  const taken = valid ? await env.DB.prepare("SELECT 1 FROM users WHERE username = ?").bind(username).first() : null;
  return json({ username, valid, available: valid && !taken });
}

function selfView(user, env) {
  return {
    id: user.id,
    username: user.username,
    name: user.name,
    email: user.email,
    avatar: user.avatar,
    shareMode: user.share_mode,
    profileUrl: user.username ? `${env.WEB_BASE}/u/${user.username}` : null,
    badgeUrl: user.username ? `${env.WEB_BASE}/badge/${user.username}.svg` : null,
  };
}

const normalizeUsername = (value) => String(value ?? "").trim().replace(/^@/, "").toLowerCase();
const isValidUsername = (username) => USERNAME_RE.test(username) && !RESERVED_USERNAMES.has(username);

// ---- Presence ------------------------------------------------------------------------------

async function putPresence(request, env) {
  const user = await requireUser(request, env);
  const body = await readJson(request);
  if (user.share_mode === "off") {
    await env.DB.prepare("DELETE FROM presence WHERE user_id = ?").bind(user.id).run();
    return json({ shared: false });
  }

  const trackId = String(body.trackId ?? "");
  if (!VIDEO_ID_RE.test(trackId)) throw badRequest("trackId must be a YouTube video id");
  const now = Date.now();
  await env.DB.prepare(
    `INSERT INTO presence (user_id, track_id, title, artist, album, thumbnail, duration_ms, position_ms, is_playing, room_code, updated_at)
     VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9, ?10, ?11)
     ON CONFLICT(user_id) DO UPDATE SET track_id = ?2, title = ?3, artist = ?4, album = ?5, thumbnail = ?6,
       duration_ms = ?7, position_ms = ?8, is_playing = ?9, room_code = ?10, updated_at = ?11`,
  )
    .bind(
      user.id,
      trackId,
      text(body.title, 200),
      text(body.artist, 200),
      text(body.album, 200),
      typeof body.thumbnail === "string" && body.thumbnail.length <= 500 && IMAGE_HOST_RE.test(body.thumbnail) ? body.thumbnail : null,
      nonNegativeInt(body.durationMs),
      nonNegativeInt(body.positionMs),
      body.isPlaying === true ? 1 : 0,
      typeof body.roomCode === "string" && ROOM_CODE_RE.test(body.roomCode) ? body.roomCode : null,
      now,
    )
    .run();
  return json({ shared: true, shareMode: user.share_mode });
}

async function clearPresence(request, env) {
  const user = await requireUser(request, env);
  await env.DB.prepare("DELETE FROM presence WHERE user_id = ?").bind(user.id).run();
  return json({ cleared: true });
}

/** What a presence row means now: null once stale; the position moves on while it plays. */
function presenceView(row, env, now = Date.now()) {
  if (!row || !row.track_id) return null;
  const playing = row.is_playing === 1;
  const age = now - row.updated_at;
  if (age > (playing ? PLAYING_STALE_MS : PAUSED_STALE_MS)) return null;

  let positionMs = row.position_ms ?? 0;
  if (playing) positionMs += age;
  if (row.duration_ms > 0) {
    // Well past the end with no new report: the app stopped without saying so.
    if (playing && positionMs > row.duration_ms + 60_000) return null;
    positionMs = Math.min(positionMs, row.duration_ms);
  }
  return {
    trackId: row.track_id,
    title: row.title,
    artist: row.artist,
    album: row.album,
    thumbnail: row.thumbnail,
    durationMs: row.duration_ms,
    positionMs,
    isPlaying: playing,
    roomCode: row.room_code,
    updatedAt: row.updated_at,
    listenUrl: listenAlongUrl(env, row.track_id, positionMs, row.duration_ms, now),
  };
}

function listenAlongUrl(env, trackId, positionMs, durationMs, now) {
  const params = new URLSearchParams({ v: trackId, t: String(Math.floor(positionMs / 1000)), at: String(now) });
  if (durationMs > 0) params.set("d", String(Math.floor(durationMs / 1000)));
  return `${env.WEB_BASE}/watch?${params}`;
}

// ---- Friends -------------------------------------------------------------------------------

async function listFriends(request, env) {
  const user = await requireUser(request, env);
  const now = Date.now();
  const [friends, incoming, outgoing] = await env.DB.batch([
    env.DB.prepare(
      `SELECT u.username, u.name, u.avatar, u.share_mode, p.track_id, p.title, p.artist, p.album, p.thumbnail,
              p.duration_ms, p.position_ms, p.is_playing, p.room_code, p.updated_at
       FROM friends f JOIN users u ON u.id = f.friend_id LEFT JOIN presence p ON p.user_id = u.id
       WHERE f.user_id = ?`,
    ).bind(user.id),
    env.DB.prepare(
      `SELECT u.username, u.name, u.avatar FROM friend_requests r JOIN users u ON u.id = r.from_id
       WHERE r.to_id = ? ORDER BY r.created_at DESC`,
    ).bind(user.id),
    env.DB.prepare(
      `SELECT u.username, u.name, u.avatar FROM friend_requests r JOIN users u ON u.id = r.to_id
       WHERE r.from_id = ? ORDER BY r.created_at DESC`,
    ).bind(user.id),
  ]);

  const list = friends.results
    .map((row) => ({ ...personView(row), nowPlaying: row.share_mode === "off" ? null : presenceView(row, env, now) }))
    .sort((a, b) => rank(b) - rank(a) || (a.username ?? "").localeCompare(b.username ?? ""));
  return json({ friends: list, incoming: incoming.results.map(personView), outgoing: outgoing.results.map(personView) });
}

// Playing first, then paused, then the rest.
const rank = (friend) => (friend.nowPlaying ? (friend.nowPlaying.isPlaying ? 2 : 1) : 0);
const personView = (row) => ({ username: row.username, name: row.name, avatar: row.avatar });

async function requestFriend(request, env) {
  const user = await requireUser(request, env);
  requireUsername(user);
  const { username } = await readJson(request);
  const target = await findUser(env, username);
  if (target.id === user.id) throw badRequest("That's your own username");

  const now = Date.now();
  const alreadyFriends = await env.DB.prepare("SELECT 1 FROM friends WHERE user_id = ? AND friend_id = ?").bind(user.id, target.id).first();
  if (alreadyFriends) return json({ status: "friends" });
  // They already asked: asking back is accepting.
  const theyAsked = await env.DB.prepare("SELECT 1 FROM friend_requests WHERE from_id = ? AND to_id = ?").bind(target.id, user.id).first();
  if (theyAsked) {
    await befriend(env, user.id, target.id, now);
    return json({ status: "friends" });
  }
  await env.DB.prepare("INSERT OR IGNORE INTO friend_requests (from_id, to_id, created_at) VALUES (?, ?, ?)").bind(user.id, target.id, now).run();
  return json({ status: "requested" });
}

async function acceptFriend(request, env, username) {
  const user = await requireUser(request, env);
  requireUsername(user);
  const target = await findUser(env, username);
  const request_ = await env.DB.prepare("SELECT 1 FROM friend_requests WHERE from_id = ? AND to_id = ?").bind(target.id, user.id).first();
  if (!request_) throw notFound("There's no request from them");
  await befriend(env, user.id, target.id, Date.now());
  return json({ status: "friends" });
}

async function removeRequest(request, env, username) {
  const user = await requireUser(request, env);
  const target = await findUser(env, username);
  await env.DB.prepare("DELETE FROM friend_requests WHERE (from_id = ? AND to_id = ?) OR (from_id = ? AND to_id = ?)")
    .bind(user.id, target.id, target.id, user.id)
    .run();
  return json({ status: "none" });
}

async function removeFriend(request, env, username) {
  const user = await requireUser(request, env);
  const target = await findUser(env, username);
  await env.DB.prepare("DELETE FROM friends WHERE (user_id = ? AND friend_id = ?) OR (user_id = ? AND friend_id = ?)")
    .bind(user.id, target.id, target.id, user.id)
    .run();
  return json({ status: "none" });
}

function befriend(env, a, b, now) {
  return env.DB.batch([
    env.DB.prepare("INSERT OR IGNORE INTO friends (user_id, friend_id, created_at) VALUES (?, ?, ?)").bind(a, b, now),
    env.DB.prepare("INSERT OR IGNORE INTO friends (user_id, friend_id, created_at) VALUES (?, ?, ?)").bind(b, a, now),
    env.DB.prepare("DELETE FROM friend_requests WHERE (from_id = ? AND to_id = ?) OR (from_id = ? AND to_id = ?)").bind(a, b, b, a),
  ]);
}

function requireUsername(user) {
  if (!user.username) throw new HttpError(409, "username_required", "Pick a username first");
}

async function findUser(env, value) {
  const username = normalizeUsername(value);
  if (!USERNAME_RE.test(username)) throw notFound("No one has that username");
  const user = await env.DB.prepare("SELECT id, username FROM users WHERE username = ?").bind(username).first();
  if (!user) throw notFound("No one has that username");
  return user;
}

// ---- Public profiles, pages and badges -----------------------------------------------------

async function publicProfileRow(env, value) {
  const username = normalizeUsername(value);
  if (!USERNAME_RE.test(username)) return null;
  return env.DB.prepare(
    `SELECT u.username, u.avatar, u.share_mode, p.track_id, p.title, p.artist, p.album, p.thumbnail,
            p.duration_ms, p.position_ms, p.is_playing, p.room_code, p.updated_at
     FROM users u LEFT JOIN presence p ON p.user_id = u.id WHERE u.username = ?`,
  )
    .bind(username)
    .first();
}

/** Shown to anyone: only the username unless the owner chose public sharing. Never the Google name or email. */
function publicView(row, env) {
  const isPublic = row.share_mode === "public";
  const nowPlaying = isPublic ? presenceView(row, env) : null;
  return {
    username: row.username,
    isPublic,
    avatar: isPublic ? row.avatar : null,
    // A public listener's room is joinable from their page, like a friend's.
    nowPlaying,
    profileUrl: `${env.WEB_BASE}/u/${row.username}`,
    badgeUrl: `${env.WEB_BASE}/badge/${row.username}.svg`,
  };
}

async function publicUser(username, env) {
  const row = await publicProfileRow(env, username);
  if (!row) throw notFound("No one has that username");
  return json(publicView(row, env));
}

async function profileRoute(username, env) {
  const row = await publicProfileRow(env, username);
  if (!row) return html(notFoundPage(env), 404);
  return html(profilePage(publicView(row, env), env));
}

async function badgeRoute(username, env, ctx) {
  const row = await publicProfileRow(env, username);
  const view = row ? publicView(row, env) : null;
  const art = view?.nowPlaying?.thumbnail ? await imageDataUri(view.nowPlaying.thumbnail, ctx) : null;
  return new Response(badgeSvg(view, art, normalizeUsername(username)), {
    headers: {
      "Content-Type": "image/svg+xml; charset=utf-8",
      // Short: GitHub's image proxy and browsers should pick up a new song within a minute.
      "Cache-Control": "max-age=30, s-maxage=30",
      "Access-Control-Allow-Origin": "*",
    },
  });
}

async function watchRoute(url, env, ctx) {
  const videoId = url.searchParams.get("v") ?? "";
  if (!VIDEO_ID_RE.test(videoId)) return html(notFoundPage(env), 404);
  const info = await videoInfo(videoId, ctx);
  return html(
    watchPage(
      {
        videoId,
        t: nonNegativeInt(url.searchParams.get("t")) ?? 0,
        at: nonNegativeInt(url.searchParams.get("at")),
        d: nonNegativeInt(url.searchParams.get("d")),
        title: info.title,
        author: info.author,
      },
      env,
    ),
  );
}

function roomRoute(url, env) {
  const code = url.searchParams.get("code") ?? "";
  if (!ROOM_CODE_RE.test(code)) return html(notFoundPage(env), 404);
  // Invites now live at /j/CODE; older links land there too.
  if (env.TOGETHER || env.TOGETHER_BASE) return Response.redirect(`${new URL(url).origin}/j/${encodeURIComponent(code.toUpperCase())}`, 302);
  return html(roomPage(code, env));
}

async function inviteRoute(code, env) {
  if (!env.TOGETHER && !env.TOGETHER_BASE) return html(roomPage(code, env));
  try {
    const path = `/j/${encodeURIComponent(code.toUpperCase())}`;
    // Through the service binding: Cloudflare refuses one worker fetching another's workers.dev URL.
    const res = env.TOGETHER
      ? await env.TOGETHER.fetch(new Request(`https://together${path}`))
      : await fetch(`${env.TOGETHER_BASE}${path}`);
    return html(await res.text(), res.status);
  } catch {
    return html(roomPage(code, env));
  }
}

function assetLinks(env) {
  const fingerprints = splitList(env.ANDROID_CERT_SHA256).map((value) => value.toUpperCase());
  const body =
    fingerprints.length === 0
      ? []
      : splitList(env.ANDROID_PACKAGES).map((packageName) => ({
          relation: ["delegate_permission/common.handle_all_urls"],
          target: { namespace: "android_app", package_name: packageName, sha256_cert_fingerprints: fingerprints },
        }));
  return json(body, 200, { "Cache-Control": "public, max-age=3600" });
}

// Title and channel for a video, from YouTube's public oEmbed endpoint, cached for a day.
async function videoInfo(videoId, ctx) {
  const endpoint = `https://www.youtube.com/oembed?format=json&url=${encodeURIComponent(`https://www.youtube.com/watch?v=${videoId}`)}`;
  try {
    const cache = caches.default;
    let response = await cache.match(endpoint);
    if (!response) {
      const fetched = await fetch(endpoint);
      if (!fetched.ok) return { title: null, author: null };
      response = new Response(fetched.body, fetched);
      response.headers.set("Cache-Control", "public, max-age=86400");
      ctx.waitUntil(cache.put(endpoint, response.clone()));
    }
    const data = await response.json();
    return { title: data.title ?? null, author: String(data.author_name ?? "").replace(/ - Topic$/, "") || null };
  } catch {
    return { title: null, author: null };
  }
}

// Badges are shown as <img> (GitHub, websites), which can't load external images inside an SVG: the
// artwork is embedded instead.
async function imageDataUri(src, ctx) {
  try {
    const cache = caches.default;
    let response = await cache.match(src);
    if (!response) {
      const fetched = await fetch(src);
      const type = fetched.headers.get("Content-Type") || "";
      if (!fetched.ok || !type.startsWith("image/")) return null;
      response = new Response(fetched.body, fetched);
      response.headers.set("Cache-Control", "public, max-age=86400");
      ctx.waitUntil(cache.put(src, response.clone()));
    }
    const type = response.headers.get("Content-Type") || "image/jpeg";
    const bytes = new Uint8Array(await response.arrayBuffer());
    if (bytes.length > 300_000) return null;
    let binary = "";
    for (let i = 0; i < bytes.length; i += 0x8000) binary += String.fromCharCode(...bytes.subarray(i, i + 0x8000));
    return `data:${type};base64,${btoa(binary)}`;
  } catch {
    return null;
  }
}

function text(value, max) {
  if (typeof value !== "string") return null;
  const trimmed = value.trim();
  return trimmed ? trimmed.slice(0, max) : null;
}

function nonNegativeInt(value) {
  const number = typeof value === "string" ? Number(value) : value;
  return Number.isFinite(number) && number >= 0 ? Math.floor(number) : null;
}

const splitList = (value) => String(value ?? "").split(",").map((item) => item.trim()).filter(Boolean);
