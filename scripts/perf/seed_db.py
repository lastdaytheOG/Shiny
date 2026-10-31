"""Builds a measuring copy of song.db. Usage: seed_db.py <input song.db> <output.db> <typical|heavy>

Real songs from the input keep their artwork, so Home scrolls and decodes real covers. The heavy
profile adds synthetic songs that reuse those covers. Fixed seed, so every run gets the same data.
"""
import random
import shutil
import sqlite3
import sys
import time

PROFILES = {
    "typical": dict(songs=0, artists=0, events=1_500, outcomes=800, related=1_500, impressions=1_500),
    "heavy": dict(songs=3_200, artists=300, events=60_000, outcomes=40_000, related=40_000, impressions=20_000),
}
DAY = 86_400_000
KINDS = ["Completed"] * 4 + ["Meaningful"] * 2 + ["Skipped"] * 2 + ["Partial", "Accidental", "Interrupted"]


def main(source, target, profile):
    spec = PROFILES[profile]
    shutil.copyfile(source, target)
    db = sqlite3.connect(target)
    db.execute("PRAGMA journal_mode=DELETE")
    rnd = random.Random(46)
    now = int(time.time() * 1000)
    wall_offset = 19_800_000

    real = [row for row in db.execute("SELECT id, thumbnailUrl, albumId FROM song WHERE isLocal = 0 ORDER BY id")]
    covers = [row[1] for row in real if row[1]]
    artists = [row[0] for row in db.execute("SELECT id FROM artist ORDER BY id")]

    for i in range(spec["artists"]):
        artists.append(f"UCseed{i}")
        db.execute("INSERT OR IGNORE INTO artist (id, name, lastUpdateTime, isLocal) VALUES (?, ?, 0, 0)", (f"UCseed{i}", f"Seed Artist {i}"))
    ids = [row[0] for row in real]
    for i in range(spec["songs"]):
        song = f"seed{i:07d}"
        ids.append(song)
        db.execute(
            "INSERT INTO song (id, title, duration, thumbnailUrl, liked, totalPlayTime, isLocal, romanizeLyrics, "
            "isDownloaded, isUploaded, isVideo, lyricsOffset, explicit) VALUES (?, ?, 210, ?, ?, 0, 0, 1, 0, 0, 0, 0, 0)",
            (song, f"Seed Song {i}", rnd.choice(covers), 1 if i % 40 == 0 else 0),
        )
        db.execute("INSERT OR IGNORE INTO song_artist_map (songId, artistId, position) VALUES (?, ?, 0)", (song, artists[i % len(artists)]))

    def favoured():
        return ids[int(rnd.random() ** 2 * len(ids))]

    db.executemany(
        "INSERT INTO event (songId, timestamp, playTime) VALUES (?, ?, 180000)",
        ((favoured(), now + wall_offset - rnd.randrange(500 * DAY)) for _ in range(spec["events"])),
    )

    def outcome():
        at = now - rnd.randrange(300 * DAY)
        return (
            favoured(), at // 1_800_000, at, at + 180_000, 330, 180_000, 210_000, 0.8, "Finished", rnd.choice(KINDS),
            "Radio", "YouTubeRadio", "Autoplay", 0, 1 if rnd.random() < 0.2 else 0,
        )

    db.executemany(
        "INSERT INTO listen_outcome (songId, sessionId, startedAt, endedAt, zoneOffsetMinutes, listenedMs, durationMs, "
        "completion, endReason, kind, surface, pickSource, selection, queuePosition, firstListen) "
        "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
        (outcome() for _ in range(spec["outcomes"])),
    )
    db.executemany(
        "INSERT OR IGNORE INTO related_song_map (songId, relatedSongId) VALUES (?, ?)",
        ((ids[rnd.randrange(len(ids) // 2)], ids[rnd.randrange(len(ids))]) for _ in range(spec["related"])),
    )
    db.executemany(
        "INSERT INTO recommendation_impression (songId, surface, shownAt, position, sessionId) VALUES (?, 'Home', ?, 0, 0)",
        ((rnd.choice(ids), now - rnd.randrange(30 * DAY)) for _ in range(spec["impressions"])),
    )
    db.commit()
    db.execute("VACUUM")
    for table in ["song", "artist", "event", "listen_outcome", "related_song_map", "recommendation_impression"]:
        print(table, db.execute(f"SELECT COUNT(*) FROM {table}").fetchone()[0])
    db.close()


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], sys.argv[3])
