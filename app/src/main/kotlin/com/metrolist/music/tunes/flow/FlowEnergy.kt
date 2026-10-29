/**
 * 튠즈: how "strong" a song is (0 = calm, 1 = intense).
 *
 * YouTube gives no tempo or loudness, so we look the song up on Deezer's public API
 * (no key needed) and mix its tempo (bpm) with its loudness (replay gain).
 * When Deezer doesn't know the song, we guess from words in the title.
 * Measured values are cached on the phone, guesses are not (so they get retried).
 */

package com.metrolist.music.tunes.flow

import android.content.Context
import com.metrolist.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

class FlowEnergy(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("tunes_flow_energy", Context.MODE_PRIVATE)

    /** Instant value: the cached measurement, else a guess from the title. */
    fun quick(meta: MediaMetadata): Pair<Float, Boolean> {
        val cached = prefs.getFloat(meta.id, -1f)
        return if (cached >= 0f) cached to true else guess(meta) to false
    }

    /** Asks Deezer; null when it doesn't know the song or can't be reached. */
    suspend fun measure(meta: MediaMetadata): Float? {
        if (failures >= 5) return null
        val measured = withContext(Dispatchers.IO) {
            runCatching { deezer(meta) }
                .onSuccess { failures = 0 }
                .onFailure { failures++ }
                .getOrNull()
        }
        if (measured != null) prefs.edit().putFloat(meta.id, measured).apply()
        return measured
    }

    // After several network errors in a row Deezer is treated as unreachable for this run
    @Volatile private var failures = 0

    private fun deezer(meta: MediaMetadata): Float? {
        val artist = meta.artists.firstOrNull()?.name.orEmpty()
        val title = cleanTitle(meta.title)
        val strict = "artist:\"$artist\" track:\"$title\""
        val hit = search(strict) ?: search("$artist $title") ?: return null
        val track = get("https://api.deezer.com/track/${hit.optLong("id")}") ?: return null
        val bpm = track.optDouble("bpm", 0.0)
        val gain = track.optDouble("gain", Double.NaN)
        // Louder masters need a more negative replay gain: about -5 (quiet) to -14 (very loud)
        val loud = if (gain.isNaN()) null else ((-gain - 5.0) / 9.0).coerceIn(0.0, 1.0)
        val tempo = if (bpm > 40.0) ((bpm - 70.0) / 90.0).coerceIn(0.0, 1.0) else null
        val value = when {
            loud != null && tempo != null -> 0.55 * loud + 0.45 * tempo
            loud != null -> loud
            tempo != null -> tempo
            else -> return null
        }
        return value.toFloat()
    }

    private fun search(query: String): JSONObject? {
        val json = get("https://api.deezer.com/search?limit=1&q=" + URLEncoder.encode(query, "UTF-8")) ?: return null
        val data = json.optJSONArray("data") ?: return null
        return if (data.length() > 0) data.getJSONObject(0) else null
    }

    private fun get(url: String): JSONObject? {
        val conn = URL(url).openConnection() as HttpURLConnection
        return try {
            conn.connectTimeout = 3000
            conn.readTimeout = 3000
            if (conn.responseCode != 200) return null
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
        } finally {
            conn.disconnect()
        }
    }

    private fun cleanTitle(title: String): String =
        title.replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
            .replace(Regex("(?i)\\b(feat\\.?|ft\\.?|official|mv|m/v|audio|lyrics?)\\b.*"), " ")
            .trim()

    private fun guess(meta: MediaMetadata): Float {
        val text = (meta.title + " " + meta.album?.title.orEmpty()).lowercase()
        val calm = listOf("acoustic", "piano", "ballad", "lofi", "lo-fi", "sleep", "calm", "slowed", "unplugged",
            "어쿠스틱", "발라드", "피아노", "잔잔", "자장가", "lullaby", "ambient")
        val loud = listOf("remix", "edm", "rock", "metal", "punk", "dance", "sped up", "nightcore", "club", "hype",
            "party", "workout", "phonk", "drill", "trap", "댄스", "록", "파티")
        return when {
            calm.any { it in text } -> 0.22f
            loud.any { it in text } -> 0.8f
            else -> 0.5f
        }
    }
}
