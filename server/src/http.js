export class HttpError extends Error {
  constructor(status, code, message) {
    super(message ?? code);
    this.status = status;
    this.code = code;
  }
}

export const badRequest = (message) => new HttpError(400, "bad_request", message);
export const unauthorized = () => new HttpError(401, "unauthorized", "Sign in first");
export const notFound = (message = "Not found") => new HttpError(404, "not_found", message);
export const tooLarge = () => new HttpError(413, "too_large", "That's too much to send");

const CORS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, PUT, PATCH, DELETE, OPTIONS",
  "Access-Control-Allow-Headers": "Authorization, Content-Type",
};

export function json(body, status = 200, headers = {}) {
  return new Response(JSON.stringify(body), {
    status,
    headers: { "Content-Type": "application/json; charset=utf-8", "Cache-Control": "no-store", ...CORS, ...headers },
  });
}

export function html(body, status = 200, headers = {}) {
  return new Response(body, {
    status,
    headers: { "Content-Type": "text/html; charset=utf-8", "Cache-Control": "no-cache", ...headers },
  });
}

export function preflight() {
  return new Response(null, { status: 204, headers: { ...CORS, "Access-Control-Max-Age": "86400" } });
}

/** [maxLength] is in characters; a body over it is refused before it is parsed. */
export async function readJson(request, maxLength = Infinity) {
  const type = request.headers.get("Content-Type") || "";
  if (!type.includes("application/json")) throw badRequest("Expected a JSON body");
  if (Number(request.headers.get("Content-Length")) > maxLength) throw tooLarge();
  const raw = await request.text();
  if (raw.length > maxLength) throw tooLarge();
  try {
    const body = JSON.parse(raw);
    if (body === null || typeof body !== "object" || Array.isArray(body)) throw new Error("not an object");
    return body;
  } catch {
    throw badRequest("Malformed JSON body");
  }
}

export function escapeHtml(value) {
  return String(value ?? "")
    .replaceAll("&", "&amp;")
    .replaceAll("<", "&lt;")
    .replaceAll(">", "&gt;")
    .replaceAll('"', "&quot;")
    .replaceAll("'", "&#39;");
}

export async function sha256Hex(text) {
  const digest = await crypto.subtle.digest("SHA-256", new TextEncoder().encode(text));
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, "0")).join("");
}

export function randomToken(byteCount = 32) {
  const bytes = crypto.getRandomValues(new Uint8Array(byteCount));
  return btoa(String.fromCharCode(...bytes)).replaceAll("+", "-").replaceAll("/", "_").replace(/=+$/, "");
}
