/** The two pages the Together server serves to people: the invite a link opens, and a plain home. */

export function invitePage(code, preview, origin, env) {
  const host = preview?.host;
  const playing = preview?.playing;
  const title = preview
    ? host ? `Listen with ${host} on Shiny` : "You're invited to listen together on Shiny"
    : "This session has ended";
  const description = preview
    ? playing ? `Now playing ${playing.title}${playing.artist ? ` · ${playing.artist}` : ""}` : "Join the session and hear the same song at the same moment."
    : "Ask whoever invited you for a new code.";
  const appLink = `shinymusic://listen?code=${code}`;
  const intentLink = `intent://listen?code=${code}#Intent;scheme=shinymusic;S.browser_fallback_url=${encodeURIComponent(env.APP_DOWNLOAD_URL)};end`;
  const art = playing?.thumbnail ? `<img class="art" src="${escapeHtml(playing.thumbnail)}" alt="">` : `<div class="art empty">♪</div>`;
  const listeners = preview?.listeners ?? 0;

  return `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>${escapeHtml(title)}</title>
<meta name="description" content="${escapeHtml(description)}">
<meta property="og:title" content="${escapeHtml(title)}">
<meta property="og:description" content="${escapeHtml(description)}">
${playing?.thumbnail ? `<meta property="og:image" content="${escapeHtml(playing.thumbnail)}">` : ""}
<meta name="theme-color" content="#000000">
<style>
  :root { color-scheme: dark; --rose: #ff375f; --label: #fff; --secondary: rgba(235,235,245,.6); --fill: rgba(118,118,128,.24); }
  * { box-sizing: border-box; }
  body { margin: 0; min-height: 100vh; background: #000; color: var(--label); font: 17px/1.4 -apple-system, "Inter", "Segoe UI", Roboto, sans-serif; display: grid; place-items: center; }
  main { width: min(420px, 100% - 40px); text-align: center; padding: 48px 0; }
  .eyebrow { color: var(--rose); font-weight: 600; font-size: 13px; letter-spacing: .08em; text-transform: uppercase; margin: 0 0 18px; }
  .art { width: 220px; height: 220px; border-radius: 14px; object-fit: cover; box-shadow: 0 24px 60px rgba(255,55,95,.28); margin: 0 auto 24px; display: block; }
  .art.empty { display: grid; place-items: center; background: var(--fill); font-size: 72px; color: var(--secondary); }
  h1 { font-size: 28px; line-height: 1.15; letter-spacing: -.02em; margin: 0 0 8px; }
  p.sub { color: var(--secondary); margin: 0 0 6px; }
  .code { display: inline-block; margin: 18px 0 26px; padding: 10px 18px; border-radius: 14px; background: var(--fill); font: 600 26px/1 ui-monospace, "SF Mono", Menlo, monospace; letter-spacing: .28em; padding-right: 10px; }
  .actions { display: grid; gap: 10px; }
  .btn { display: block; padding: 15px 20px; border-radius: 999px; font-weight: 600; text-decoration: none; color: var(--rose); background: var(--fill); }
  .btn.primary { color: #fff; background: var(--rose); }
  .muted { color: var(--secondary); font-size: 13px; margin-top: 22px; }
</style>
</head>
<body>
<main>
  <p class="eyebrow">Shiny Together</p>
  ${preview ? art : ""}
  <h1>${escapeHtml(title)}</h1>
  <p class="sub">${escapeHtml(description)}</p>
  ${preview && listeners ? `<p class="sub">${listeners} listening now</p>` : ""}
  ${preview ? `<div class="code">${escapeHtml(code)}</div>` : ""}
  <div class="actions">
    ${preview ? `<a class="btn primary" id="open" href="${escapeHtml(appLink)}">Join in Shiny</a>` : ""}
    <a class="btn" href="${escapeHtml(env.APP_DOWNLOAD_URL)}">Get Shiny for Android</a>
  </div>
  ${preview ? `<p class="muted">Already in Shiny? Open Listen Together and enter the code.</p>` : ""}
</main>
<script>
  // Android browsers open the app from an intent link and fall back to the download page.
  var open = document.getElementById("open");
  if (open && /Android/i.test(navigator.userAgent)) open.href = ${JSON.stringify(intentLink)};
</script>
</body>
</html>`;
}

export function homePage(env) {
  return `<!doctype html><html lang="en"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>Shiny Together</title>
<style>body{margin:0;min-height:100vh;display:grid;place-items:center;background:#000;color:#fff;font:17px/1.4 -apple-system,"Inter","Segoe UI",Roboto,sans-serif;text-align:center}a{color:#ff375f}</style></head>
<body><main><h1>Shiny Together</h1><p>Group listening for <a href="${escapeHtml(env.APP_DOWNLOAD_URL)}">Shiny</a>.</p></main></body></html>`;
}

function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}
