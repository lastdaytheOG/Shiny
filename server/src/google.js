import { createRemoteJWKSet, jwtVerify } from "jose";
import { HttpError } from "./http.js";

// Google's signing keys; jose fetches and caches them on first use.
const GOOGLE_KEYS = createRemoteJWKSet(new URL("https://www.googleapis.com/oauth2/v3/certs"));

/**
 * Verifies an ID token from Sign in with Google (Android Credential Manager) and returns its claims.
 * The token's audience is the Web client ID the app passed as `serverClientId`.
 */
export async function verifyGoogleIdToken(idToken, env) {
  const audience = (env.GOOGLE_CLIENT_IDS || "").split(",").map((id) => id.trim()).filter(Boolean);
  if (audience.length === 0) {
    throw new HttpError(503, "google_not_configured", "Google sign-in isn't set up on this server yet");
  }
  try {
    const { payload } = await jwtVerify(idToken, GOOGLE_KEYS, {
      issuer: ["https://accounts.google.com", "accounts.google.com"],
      audience,
    });
    if (typeof payload.sub !== "string" || payload.sub.length === 0) throw new Error("token has no subject");
    return payload;
  } catch {
    throw new HttpError(401, "invalid_google_token", "Google sign-in couldn't be verified");
  }
}
