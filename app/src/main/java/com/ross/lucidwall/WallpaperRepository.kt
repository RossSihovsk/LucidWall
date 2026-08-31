package com.ross.lucidwall

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/**
 * Persists a capped list of [WallpaperHistoryEntry] items using [SharedPreferences] + plain JSON.
 * No external dependencies are required beyond what is already on the classpath.
 */
class WallpaperRepository private constructor(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Persists [entry] as the most-recent item, keeping at most [MAX_ENTRIES] entries.
     * Oldest entries are dropped when the cap is exceeded.
     */
    fun saveEntry(entry: WallpaperHistoryEntry) {
        val current = loadEntries().toMutableList()
        // Remove any existing entry with the same URI (deduplication)
        current.removeAll { it.imageUriString == entry.imageUriString }
        // Prepend newest
        current.add(0, entry)
        // Trim to cap
        val trimmed = current.take(MAX_ENTRIES)
        prefs.edit().putString(KEY_HISTORY, entriesToJson(trimmed)).apply()
    }

    /**
     * Returns all stored entries, most-recent first.
     */
    fun loadEntries(): List<WallpaperHistoryEntry> {
        val json = prefs.getString(KEY_HISTORY, null) ?: return emptyList()
        return try {
            jsonToEntries(json)
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── JSON helpers ──────────────────────────────────────────────────────────

    private fun entriesToJson(entries: List<WallpaperHistoryEntry>): String {
        val array = JSONArray()
        entries.forEach { e ->
            val obj = JSONObject().apply {
                put(FIELD_ID, e.id)
                put(FIELD_URI, e.imageUriString)
                put(FIELD_BLUR, e.blurRadius.toDouble())
                put(FIELD_CONF, e.configuration)
                put(FIELD_AT, e.appliedAt)
                put(FIELD_THUMB, e.thumbnailBase64)
            }
            array.put(obj)
        }
        return array.toString()
    }

    private fun jsonToEntries(json: String): List<WallpaperHistoryEntry> {
        val array = JSONArray(json)
        val list = mutableListOf<WallpaperHistoryEntry>()
        for (i in 0 until array.length()) {
            val obj = array.getJSONObject(i)
            list.add(
                WallpaperHistoryEntry(
                    id = obj.getString(FIELD_ID),
                    imageUriString = obj.getString(FIELD_URI),
                    blurRadius = obj.getDouble(FIELD_BLUR).toFloat(),
                    configuration = obj.getInt(FIELD_CONF),
                    appliedAt = obj.getLong(FIELD_AT),
                    thumbnailBase64 = obj.getString(FIELD_THUMB)
                )
            )
        }
        return list
    }

    // ── Companion ─────────────────────────────────────────────────────────────

    companion object {
        private const val PREFS_NAME = "lucidwall_history"
        private const val KEY_HISTORY = "history_entries"
        private const val MAX_ENTRIES = 5

        private const val FIELD_ID    = "id"
        private const val FIELD_URI   = "uri"
        private const val FIELD_BLUR  = "blur"
        private const val FIELD_CONF  = "conf"
        private const val FIELD_AT    = "at"
        private const val FIELD_THUMB = "thumb"

        @Volatile
        private var instance: WallpaperRepository? = null

        fun getInstance(context: Context): WallpaperRepository =
            instance ?: synchronized(this) {
                instance ?: WallpaperRepository(context.applicationContext).also { instance = it }
            }
    }
}
