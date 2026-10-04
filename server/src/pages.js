import { escapeHtml } from "./http.js";

const STYLE = `
:root{color-scheme:dark;--label:#fff;--secondary:rgba(235,235,245,.62);--fill:rgba(255,255,255,.14);--accent:#fa2d48}
*{box-sizing:border-box}
html,body{margin:0;min-height:100%;background:#000;color:var(--label);
  font:16px/1.4 -apple-system,BlinkMacSystemFont,"SF Pro Text","Segoe UI",Roboto,system-ui,sans-serif;-webkit-font-smoothing:antialiased}
.backdrop{position:fixed;inset:-15%;background:#111 center/cover no-repeat;filter:blur(64px) saturate(1.5) brightness(.5);z-index:0}
main{position:relative;z-index:1;min-height:100vh;display:flex;flex-direction:column;align-items:center;justify-content:center;
  gap:20px;padding:40px 20px;text-align:center}
.art{width:min(72vw,300px);aspect-ratio:1;border-radius:14px;object-fit:cover;background:#1c1c1e;box-shadow:0 24px 64px rgba(0,0,0,.6)}
.art.round{border-radius:50%;width:min(40vw,140px)}
.eyebrow{margin:0;font-size:13px;font-weight:600;letter-spacing:.06em;text-transform:uppercase;color:var(--secondary)}
h1{margin:4px 0 0;font-size:clamp(22px,5.5vw,30px);line-height:1.15;font-weight:700;letter-spacing:-.02em;max-width:24ch;overflow-wrap:anywhere}
.sub{margin:6px 0 0;font-size:17px;color:var(--secondary);max-width:30ch;overflow-wrap:anywhere}
.actions{display:flex;flex-direction:column;gap:10px;width:min(100%,320px)}
.btn{display:block;padding:14px 18px;border-radius:14px;background:var(--fill);color:#fff;font-weight:600;text-decoration:none;
  -webkit-backdrop-filter:blur(20px);backdrop-filter:blur(20px)}
.btn.primary{background:var(--accent)}
.progress{width:min(100%,300px)}
.bar{height:4px;border-radius:2px;background:rgba(255,255,255,.2);overflow:hidden}
.bar>div{height:100%;width:0;background:#fff}
.times{display:flex;justify-content:space-between;margin-top:6px;font-size:12px;color:var(--secondary);font-variant-numeric:tabular-nums}
.muted{margin:0;color:var(--secondary);font-size:14px}
code{font:13px ui-monospace,Menlo,Consolas,monospace;background:var(--fill);padding:2px 6px;border-radius:6px}
footer{font-size:13px;color:var(--secondary)}
footer a{color:inherit}
[hidden]{display:none!important}
`;

// JSON for an inline <script>: "<" is escaped so data can't close the tag.
const safeJson = (value) => JSON.stringify(value).replaceAll("<", "\\u003c");

function layout({ title, description, image, body, script }) {
  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width,initial-scale=1,viewport-fit=cover">
<title>${escapeHtml(title)}</title>
<meta name="description" content="${escapeHtml(description)}">
<meta name="theme-color" content="#000000">
<meta property="og:site_name" content="Shiny">
<meta property="og:title" content="${escapeHtml(title)}">
<meta property="og:description" content="${escapeHtml(description)}">
${image ? `<meta property="og:image" content="${escapeHtml(image)}">\n<meta name="twitter:card" content="summary_large_image">` : `<meta name="twitter:card" content="summary">`}
<style>${STYLE}</style>
</head>
<body>
${body}
${script ? `<script>${script}</script>` : ""}
</body>
</html>`;
}

const footer = (env) =>
  `<footer>Shiny is a free music app for Android · <a href="${escapeHtml(env.APP_DOWNLOAD_URL)}">Get Shiny</a></footer>`;

// Shared by pages that open the app: an intent:// link on Android falls back to the download page
// when Shiny isn't installed.
const APP_LINK_SCRIPT = `
const isAndroid = /Android/i.test(navigator.userAgent);
function appLink(path, fallback) {
  return isAndroid
    ? "intent://" + path + "#Intent;scheme=shinymusic;S.browser_fallback_url=" + encodeURIComponent(fallback) + ";end"
    : "shinymusic://" + path;
}
function clock(seconds) {
  seconds = Math.max(0, Math.floor(seconds));
  return Math.floor(seconds / 60) + ":" + String(seconds % 60).padStart(2, "0");
}
`;

export function notFoundPage(env) {
  return layout({
    title: "Not found · Shiny",
    description: "This link doesn't lead anywhere.",
    body: `<main>
  <p class="eyebrow">Shiny</p>
  <h1>Nothing here</h1>
  <p class="sub">This link doesn't lead anywhere. It may have been typed wrong, or the profile was deleted.</p>
  <div class="actions"><a class="btn" href="/">Go to Shiny</a></div>
</main>`,
  });
}

/** Listen along: one song, starting where the sharer was. `at` lets the start time keep moving. */
export function watchPage({ videoId, t, at, d, title, author }, env) {
  const art = `https://i.ytimg.com/vi/${videoId}/hqdefault.jpg`;
  const heading = title ?? "Listen on Shiny";
  return layout({
    title: author ? `${heading} · ${author}` : heading,
    description: "Listen along on Shiny.",
    image: art,
    body: `<div class="backdrop" style="background-image:url('${escapeHtml(art)}')"></div>
<main>
  <img class="art" src="${escapeHtml(art)}" alt="">
  <div>
    <p class="eyebrow">Listen along</p>
    <h1>${escapeHtml(heading)}</h1>
    ${author ? `<p class="sub">${escapeHtml(author)}</p>` : ""}
  </div>
  <p class="muted" id="start"></p>
  <div class="actions">
    <a class="btn primary" id="open" hidden>Open in Shiny</a>
    <a class="btn" id="yt">Play on YouTube Music</a>
    <a class="btn" href="${escapeHtml(env.APP_DOWNLOAD_URL)}" id="get">Get Shiny</a>
  </div>
  ${footer(env)}
</main>`,
    script: `${APP_LINK_SCRIPT}
const song = ${safeJson({ videoId, t, at, d, download: env.APP_DOWNLOAD_URL })};
function startSeconds() {
  let seconds = song.t || 0;
  if (song.at) {
    const drift = Math.floor((Date.now() - song.at) / 1000);
    if (drift > 0 && drift < 6 * 3600) seconds += drift;
  }
  // Past the end, the song they were on is over: start it from the top.
  if (song.d && seconds >= song.d) seconds = 0;
  return seconds;
}
const open = document.getElementById("open");
const yt = document.getElementById("yt");
const start = document.getElementById("start");
open.hidden = !isAndroid;
function refresh() {
  const seconds = startSeconds();
  open.href = appLink("watch?v=" + song.videoId + (seconds ? "&t=" + seconds : ""), song.download);
  yt.href = "https://music.youtube.com/watch?v=" + song.videoId + (seconds ? "&t=" + seconds + "s" : "");
  start.textContent = seconds ? "Starts at " + clock(seconds) : "";
}
refresh();
setInterval(refresh, 1000);`,
  });
}

/** A shared playlist or artist: open it in Shiny on Android, or on YouTube Music anywhere. */
export function linkPage({ kind, appPath, webUrl }, env) {
  return layout({
    title: `${kind} on Shiny`,
    description: `Someone shared a ${kind.toLowerCase()} with you on Shiny.`,
    body: `<main>
  <p class="eyebrow">${escapeHtml(kind)}</p>
  <h1>Open this ${escapeHtml(kind.toLowerCase())} in Shiny</h1>
  <div class="actions">
    <a class="btn primary" id="open" hidden>Open in Shiny</a>
    <a class="btn" href="${escapeHtml(webUrl)}">Open on YouTube Music</a>
    <a class="btn" href="${escapeHtml(env.APP_DOWNLOAD_URL)}">Get Shiny</a>
  </div>
  ${footer(env)}
</main>`,
    script: `${APP_LINK_SCRIPT}
const open = document.getElementById("open");
open.href = appLink(${safeJson(appPath)}, ${safeJson(env.APP_DOWNLOAD_URL)});
open.hidden = !isAndroid;`,
  });
}

export function roomPage(code, env) {
  return layout({
    title: "Join a Listen Together room · Shiny",
    description: "Someone invited you to listen together on Shiny.",
    body: `<main>
  <p class="eyebrow">Listen Together</p>
  <h1>You're invited to listen together</h1>
  <p class="sub">Room <code>${escapeHtml(code)}</code></p>
  <div class="actions">
    <a class="btn primary" id="open">Join in Shiny</a>
    <a class="btn" href="${escapeHtml(env.APP_DOWNLOAD_URL)}">Get Shiny</a>
  </div>
  <p class="muted" id="hint" hidden>Open this link on your Android phone to join.</p>
  ${footer(env)}
</main>`,
    script: `${APP_LINK_SCRIPT}
document.getElementById("open").href = appLink("listen?code=" + ${safeJson(encodeURIComponent(code))}, ${safeJson(env.APP_DOWNLOAD_URL)});
document.getElementById("hint").hidden = isAndroid;`,
  });
}

/** A public profile: what they're playing now, refreshed every 15 seconds. */
export function profilePage(profile, env) {
  const playing = profile.nowPlaying;
  const title = `@${profile.username} on Shiny`;
  const description = playing?.title
    ? `Listening to ${playing.title}${playing.artist ? ` by ${playing.artist}` : ""}`
    : profile.isPublic
      ? "Not listening right now"
      : "This profile is private";

  if (!profile.isPublic) {
    return layout({
      title,
      description,
      body: `<main>
  <p class="eyebrow">Shiny</p>
  <h1>@${escapeHtml(profile.username)}</h1>
  <p class="sub">This profile is private.</p>
  <div class="actions"><a class="btn" href="${escapeHtml(env.APP_DOWNLOAD_URL)}">Get Shiny</a></div>
  ${footer(env)}
</main>`,
    });
  }

  return layout({
    title,
    description,
    image: playing?.thumbnail ?? profile.avatar,
    body: `<div class="backdrop" id="backdrop"></div>
<main>
  <p class="eyebrow">@${escapeHtml(profile.username)}</p>
  <img class="art" id="art" alt="" hidden>
  <div>
    <p class="eyebrow" id="state"></p>
    <h1 id="title"></h1>
    <p class="sub" id="artist"></p>
  </div>
  <div class="progress" id="progress" hidden>
    <div class="bar"><div id="fill"></div></div>
    <div class="times"><span id="elapsed">0:00</span><span id="total">0:00</span></div>
  </div>
  <div class="actions">
    <a class="btn primary" id="listen" hidden>Listen along</a>
    <a class="btn" id="room" hidden>Join their room</a>
    <a class="btn" href="${escapeHtml(env.APP_DOWNLOAD_URL)}">Get Shiny</a>
  </div>
  ${footer(env)}
</main>`,
    script: `${APP_LINK_SCRIPT}
const username = ${safeJson(profile.username)};
const download = ${safeJson(env.APP_DOWNLOAD_URL)};
let profile = ${safeJson(profile)};
let fetchedAt = Date.now();
const $ = (id) => document.getElementById(id);

function render() {
  const song = profile.nowPlaying;
  $("art").hidden = !song;
  $("progress").hidden = !song || !song.durationMs;
  $("listen").hidden = !song;
  $("room").hidden = !(song && song.roomCode);
  if (!song) {
    $("state").textContent = "";
    $("title").textContent = "Not listening right now";
    $("artist").textContent = "Check back later, or get Shiny and add them as a friend.";
    $("backdrop").style.backgroundImage = profile.avatar ? "url('" + profile.avatar + "')" : "";
    return;
  }
  $("state").textContent = song.isPlaying ? "Listening now" : "Paused";
  $("title").textContent = song.title || "Unknown song";
  $("artist").textContent = song.artist || "";
  if (song.thumbnail && $("art").src !== song.thumbnail) {
    $("art").src = song.thumbnail;
    $("backdrop").style.backgroundImage = "url('" + song.thumbnail + "')";
  }
  if (song.roomCode) $("room").href = appLink("listen?code=" + encodeURIComponent(song.roomCode), download);
  tick();
}

function positionMs() {
  const song = profile.nowPlaying;
  let position = song.positionMs + (song.isPlaying ? Date.now() - fetchedAt : 0);
  return song.durationMs ? Math.min(position, song.durationMs) : position;
}

function tick() {
  const song = profile.nowPlaying;
  if (!song) return;
  const position = positionMs();
  if (song.durationMs) {
    $("fill").style.width = (100 * position / song.durationMs).toFixed(2) + "%";
    $("elapsed").textContent = clock(position / 1000);
    $("total").textContent = clock(song.durationMs / 1000);
  }
  const params = new URLSearchParams({ v: song.trackId, t: String(Math.floor(position / 1000)), at: String(Date.now()) });
  if (song.durationMs) params.set("d", String(Math.floor(song.durationMs / 1000)));
  $("listen").href = "/watch?" + params;
}

async function poll() {
  try {
    const response = await fetch("/v1/users/" + encodeURIComponent(username), { cache: "no-store" });
    if (response.ok) {
      profile = await response.json();
      fetchedAt = Date.now();
      render();
    }
  } catch {}
}

render();
setInterval(tick, 1000);
setInterval(poll, 15000);`,
  });
}

/** A 380×88 SVG badge for READMEs and websites. */
export function badgeSvg(profile, artDataUri, requestedUsername) {
  const song = profile?.nowPlaying;
  const username = profile?.username ?? requestedUsername ?? "";
  let eyebrow;
  let line1;
  let line2;
  if (!profile) {
    eyebrow = "SHINY";
    line1 = "No such profile";
    line2 = username ? `@${username}` : "";
  } else if (!profile.isPublic) {
    eyebrow = "SHINY";
    line1 = `@${username}`;
    line2 = "Profile is private";
  } else if (!song) {
    eyebrow = "SHINY";
    line1 = `@${username}`;
    line2 = "Not listening right now";
  } else {
    eyebrow = song.isPlaying ? "LISTENING ON SHINY" : "PAUSED ON SHINY";
    line1 = song.title ?? "Unknown song";
    line2 = song.artist ?? `@${username}`;
  }

  const clip = (value, max) => {
    const chars = [...String(value)];
    return chars.length > max ? `${chars.slice(0, max - 1).join("")}…` : chars.join("");
  };
  const e = escapeHtml;
  const font = `-apple-system,BlinkMacSystemFont,'Segoe UI',Helvetica,Arial,sans-serif`;
  const artwork = artDataUri
    ? `<image href="${e(artDataUri)}" x="12" y="12" width="64" height="64" preserveAspectRatio="xMidYMid slice" clip-path="url(#art)"/>`
    : `<rect x="12" y="12" width="64" height="64" rx="10" fill="#2c2c2e"/>
  <path d="M50 30v21.5a7 7 0 1 1-3-5.74V36l-12 3v16.5a7 7 0 1 1-3-5.74V34l18-4.5z" fill="#fa2d48"/>`;
  const bars = song?.isPlaying
    ? `<g fill="#fa2d48" transform="translate(352 16)">
    <rect x="0" y="4" width="3" height="10" rx="1.5"><animate attributeName="height" values="10;4;12;10" dur="1s" repeatCount="indefinite"/><animate attributeName="y" values="4;10;2;4" dur="1s" repeatCount="indefinite"/></rect>
    <rect x="5" y="2" width="3" height="12" rx="1.5"><animate attributeName="height" values="12;6;9;12" dur=".8s" repeatCount="indefinite"/><animate attributeName="y" values="2;8;5;2" dur=".8s" repeatCount="indefinite"/></rect>
    <rect x="10" y="6" width="3" height="8" rx="1.5"><animate attributeName="height" values="8;12;5;8" dur="1.2s" repeatCount="indefinite"/><animate attributeName="y" values="6;2;9;6" dur="1.2s" repeatCount="indefinite"/></rect>
  </g>`
    : "";

  return `<svg xmlns="http://www.w3.org/2000/svg" width="380" height="88" viewBox="0 0 380 88" role="img" aria-label="${e(`${eyebrow}: ${line1}${line2 ? ` — ${line2}` : ""}`)}">
  <defs><clipPath id="art"><rect x="12" y="12" width="64" height="64" rx="10"/></clipPath></defs>
  <rect width="380" height="88" rx="16" fill="#111113"/>
  <rect x=".5" y=".5" width="379" height="87" rx="15.5" fill="none" stroke="#ffffff" stroke-opacity=".08"/>
  ${artwork}
  <text x="92" y="31" font-family="${font}" font-size="11" font-weight="600" letter-spacing=".6" fill="#ebebf5" fill-opacity=".6">${e(eyebrow)}</text>
  <text x="92" y="53" font-family="${font}" font-size="16" font-weight="700" fill="#ffffff">${e(clip(line1, 30))}</text>
  <text x="92" y="73" font-family="${font}" font-size="13" fill="#ebebf5" fill-opacity=".6">${e(clip(line2, 38))}</text>
  ${bars}
</svg>`;
}
