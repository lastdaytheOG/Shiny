-- Shiny social server schema (Cloudflare D1 / SQLite). Safe to run again.

CREATE TABLE IF NOT EXISTS users (
  id          TEXT PRIMARY KEY,
  google_sub  TEXT UNIQUE,
  email       TEXT,
  name        TEXT,
  avatar      TEXT,
  username    TEXT UNIQUE COLLATE NOCASE,
  -- Who can see what this user is playing: 'off', 'friends' or 'public'.
  share_mode  TEXT NOT NULL DEFAULT 'friends',
  created_at  INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS sessions (
  token_hash  TEXT PRIMARY KEY,
  user_id     TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at  INTEGER NOT NULL,
  expires_at  INTEGER NOT NULL
);
CREATE INDEX IF NOT EXISTS sessions_user ON sessions(user_id);

-- One row per user: the latest thing they reported playing.
CREATE TABLE IF NOT EXISTS presence (
  user_id      TEXT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  track_id     TEXT NOT NULL,
  title        TEXT,
  artist       TEXT,
  album        TEXT,
  thumbnail    TEXT,
  duration_ms  INTEGER,
  position_ms  INTEGER,
  is_playing   INTEGER NOT NULL,
  room_code    TEXT,
  updated_at   INTEGER NOT NULL
);

-- Accepted friendships are stored in both directions.
CREATE TABLE IF NOT EXISTS friends (
  user_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  friend_id  TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (user_id, friend_id)
);

CREATE TABLE IF NOT EXISTS friend_requests (
  from_id    TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  to_id      TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  created_at INTEGER NOT NULL,
  PRIMARY KEY (from_id, to_id)
);
CREATE INDEX IF NOT EXISTS friend_requests_to ON friend_requests(to_id);

-- A playlist shared from the app as a link, shinymusic.in/p/ID. A playlist that only lives on a phone
-- (a Spotify import, one made in Shiny) has no page anywhere else, so its songs are kept here.
CREATE TABLE IF NOT EXISTS shared_playlists (
  id           TEXT PRIMARY KEY,
  -- SHA-256 of the name and songs: sharing the same playlist again gives the same link.
  content_hash TEXT NOT NULL UNIQUE,
  name         TEXT NOT NULL,
  song_count   INTEGER NOT NULL,
  -- JSON array of { id, title, artists: [{ name, id }], duration, thumbnail, explicit }.
  songs        TEXT NOT NULL,
  created_at   INTEGER NOT NULL,
  -- Last day the link was opened, so links nobody uses can be told apart later.
  opened_at    INTEGER NOT NULL
);

-- New shared playlists per network address per hour. The address is stored hashed.
CREATE TABLE IF NOT EXISTS share_limits (
  address_hash TEXT NOT NULL,
  hour         INTEGER NOT NULL,
  count        INTEGER NOT NULL,
  PRIMARY KEY (address_hash, hour)
);
