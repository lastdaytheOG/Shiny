package com.shiny.music.playback

import android.content.Context
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolved stream URLs kept on disk, so a song played again after the app was closed starts
 * without a new resolve (two `/player` requests and a probe, around a second).
 *
 * A URL from disk is not trusted as it stands: googlevideo URLs are bound to the address they
 * were issued to, and a phone that went from Wi-Fi to mobile data since gets a 403. Every
 * restored entry is therefore [unverified] until one HEAD has shown the CDN still serves it —
 * one round trip, on the same connection playback then reuses — and a URL that fails it is
 * dropped and resolved afresh.
 *
 * Entries carry the in-memory cache's own expiry (YouTube's, less a margin) and are pruned
 * when they pass it. Only the [MAX_ENTRIES] that expire last are kept.
 */
class StreamUrlStore(context: Context) {

    private val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val unverified = ConcurrentHashMap.newKeySet<String>()

    /** Unexpired entries, as `key -> (url, expiresAtMs)`; expired ones are deleted. */
    fun restore(now: Long = System.currentTimeMillis()): Map<String, Pair<String, Long>> {
        val restored = HashMap<String, Pair<String, Long>>()
        val expired = ArrayList<String>()
        for ((key, value) in prefs.all) {
            val entry = (value as? String)?.let(::decode)
            if (entry == null || entry.second <= now) expired += key else restored[key] = entry
        }
        if (expired.isNotEmpty()) prefs.edit().apply { expired.forEach(::remove) }.apply()
        unverified += restored.keys
        return restored
    }

    fun needsCheck(key: String): Boolean = key in unverified

    fun markChecked(key: String) {
        unverified -= key
    }

    fun put(key: String, url: String, expiresAtMs: Long) {
        unverified -= key
        val editor = prefs.edit().putString(key, encode(url, expiresAtMs))
        val all = prefs.all
        if (all.size >= MAX_ENTRIES) {
            // Drop the entries that would have expired first.
            all.entries
                .filter { it.key != key }
                .sortedBy { (it.value as? String)?.let(::decode)?.second ?: 0L }
                .take(all.size - MAX_ENTRIES + 1)
                .forEach { editor.remove(it.key) }
        }
        editor.apply()
    }

    fun remove(key: String) {
        unverified -= key
        prefs.edit().remove(key).apply()
    }

    fun clear() {
        unverified.clear()
        prefs.edit().clear().apply()
    }

    private fun encode(url: String, expiresAtMs: Long) = "$expiresAtMs|$url"

    private fun decode(value: String): Pair<String, Long>? {
        val bar = value.indexOf('|')
        if (bar <= 0) return null
        val expiry = value.substring(0, bar).toLongOrNull() ?: return null
        return value.substring(bar + 1) to expiry
    }

    private companion object {
        const val PREFS = "stream_url_cache"
        const val MAX_ENTRIES = 80
    }
}
