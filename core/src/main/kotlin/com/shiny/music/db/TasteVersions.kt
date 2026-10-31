package com.shiny.music.db

import androidx.sqlite.db.SupportSQLiteDatabase

object TasteVersions {
    private const val PREFIX = "taste_v1_"

    private const val SAVED_ROW = "liked = 1 OR %1\$s.inLibrary IS NOT NULL OR %1\$s.isLocal = 1 OR %1\$s.isDownloaded = 1"
    private const val SAVED_CHANGED =
        "OLD.liked IS NOT NEW.liked OR OLD.likedDate IS NOT NEW.likedDate OR OLD.inLibrary IS NOT NEW.inLibrary " +
            "OR OLD.isLocal IS NOT NEW.isLocal OR OLD.isDownloaded IS NOT NEW.isDownloaded OR OLD.albumId IS NOT NEW.albumId"

    private class Watch(val group: String, val table: String, val event: String, val condition: String? = null)

    private fun all(group: String, table: String) = listOf(
        Watch(group, table, "INSERT"),
        Watch(group, table, "UPDATE"),
        Watch(group, table, "DELETE"),
    )

    private val watches = buildList {
        addAll(all("history", "event"))
        addAll(all("outcomes", "listen_outcome"))
        addAll(all("feedback", "music_feedback"))
        addAll(all("relations", "music_relation"))
        addAll(all("features", "beat_info"))
        addAll(all("searches", "search_history"))
        addAll(all("saved", "playlist_song_map"))
        add(Watch("saved", "song", "INSERT", "NEW.${SAVED_ROW.format("NEW")}"))
        add(Watch("saved", "song", "DELETE", "OLD.${SAVED_ROW.format("OLD")}"))
        add(Watch("saved", "song", "UPDATE", SAVED_CHANGED))
        add(Watch("saved", "playlist", "INSERT", "NEW.bookmarkedAt IS NOT NULL"))
        add(Watch("saved", "playlist", "DELETE", "OLD.bookmarkedAt IS NOT NULL"))
        add(Watch("saved", "playlist", "UPDATE", "OLD.bookmarkedAt IS NOT NEW.bookmarkedAt"))
    }

    fun install(db: SupportSQLiteDatabase) {
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type = 'trigger' AND name LIKE '$PREFIX%'").use { cursor ->
            if (cursor.moveToFirst() && cursor.getInt(0) == watches.size) return
        }
        db.beginTransaction()
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS taste_version (name TEXT NOT NULL PRIMARY KEY, version INTEGER NOT NULL) WITHOUT ROWID")
            watches.map { it.group }.distinct().forEach { group ->
                db.execSQL("INSERT OR IGNORE INTO taste_version (name, version) VALUES ('$group', 0)")
            }
            watches.forEach { watch ->
                val name = "$PREFIX${watch.table}_${watch.event.lowercase()}"
                val condition = watch.condition?.let { " WHEN $it" }.orEmpty()
                db.execSQL(
                    "CREATE TRIGGER IF NOT EXISTS $name AFTER ${watch.event} ON ${watch.table}$condition " +
                        "BEGIN UPDATE taste_version SET version = version + 1 WHERE name = '${watch.group}'; END",
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    fun read(db: SupportSQLiteDatabase): String =
        db.query("SELECT name, version FROM taste_version ORDER BY name").use { cursor ->
            buildString {
                while (cursor.moveToNext()) append(cursor.getLong(1)).append(':')
            }
        }
}
