package app.veneer.system

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/** One line of lyrics; [timeMs] is when it's sung, or null for lyrics without timing. */
data class LyricLine(val timeMs: Long?, val text: String)

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean)

sealed interface LyricsResult {
    data class Found(val lyrics: Lyrics) : LyricsResult
    data object NotFound : LyricsResult

    /** The lyrics service couldn't be reached; nothing is saved, so it's tried again next time. */
    data object Offline : LyricsResult
}

/** Reads timed lyrics in the LRC format: "[01:23.45] a line", possibly with several times per line. */
object LrcParser {
    private val stamp = Regex("""\[(\d{1,3}):(\d{1,2})(?:[.:](\d{1,3}))?]""")

    fun parse(lrc: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        for (raw in lrc.lineSequence()) {
            val stamps = stamp.findAll(raw).toList()
            if (stamps.isEmpty()) continue // tags like [ar:...] and blank lines without a time
            val text = raw.substring(stamps.last().range.last + 1).trim()
            for (m in stamps) {
                val min = m.groupValues[1].toLong()
                val sec = m.groupValues[2].toLong()
                val frac = m.groupValues[3]
                val ms = when (frac.length) {
                    0 -> 0L
                    1 -> frac.toLong() * 100
                    2 -> frac.toLong() * 10
                    else -> frac.take(3).toLong()
                }
                lines += LyricLine(min * 60_000 + sec * 1000 + ms, text)
            }
        }
        return lines.sortedBy { it.timeMs }
    }

    /** The line being sung at [positionMs]: the last one that has started. */
    fun currentIndex(lines: List<LyricLine>, positionMs: Long): Int {
        var current = -1
        for ((i, line) in lines.withIndex()) {
            if ((line.timeMs ?: continue) <= positionMs) current = i else break
        }
        return current
    }
}

/**
 * Finds lyrics on LRCLIB (lrclib.net, free and without an account) and keeps every answer on the
 * phone, including "no lyrics", so the same song is never looked up twice.
 */
class LyricsRepository(
    private val dir: File,
    private val fetch: (String) -> Http = ::httpGet,
) {
    /** An answer from the server; [fetch] throws an IOException when the server can't be reached. */
    data class Http(val code: Int, val body: String)

    private val memory = ConcurrentHashMap<String, LyricsResult>()
    private val json = Json { ignoreUnknownKeys = true }

    suspend fun lookup(track: String, artist: String, album: String, durationMs: Long): LyricsResult = withContext(Dispatchers.IO) {
        val seconds = (durationMs / 1000).coerceAtLeast(0)
        val key = cacheKey(track, artist, album, seconds)
        memory[key]?.let { return@withContext it }
        readCache(key)?.let { memory[key] = it; return@withContext it }

        val result = try {
            fromServer(track, artist, album, seconds)
        } catch (_: IOException) {
            LyricsResult.Offline
        }
        if (result != LyricsResult.Offline) {
            memory[key] = result
            writeCache(key, result)
        }
        result
    }

    private fun fromServer(track: String, artist: String, album: String, seconds: Long): LyricsResult {
        fun q(s: String) = URLEncoder.encode(s, "UTF-8")
        if (seconds > 0) {
            val url = "https://lrclib.net/api/get?track_name=${q(track)}&artist_name=${q(artist)}&album_name=${q(album)}&duration=$seconds"
            val answer = fetch(url)
            return when (answer.code) {
                200 -> fromRecord(json.parseToJsonElement(answer.body).jsonObject)
                404 -> LyricsResult.NotFound
                else -> throw IOException("lyrics service answered ${answer.code}")
            }
        }
        // Without a length the exact lookup can't match, so search by name instead.
        val answer = fetch("https://lrclib.net/api/search?track_name=${q(track)}&artist_name=${q(artist)}")
        if (answer.code != 200) throw IOException("lyrics service answered ${answer.code}")
        val records = json.parseToJsonElement(answer.body).jsonArray.map { it.jsonObject }
        return records.asSequence().map { fromRecord(it) }.firstOrNull { it is LyricsResult.Found } ?: LyricsResult.NotFound
    }

    /** Timed lyrics when there are some, otherwise the plain text. */
    private fun fromRecord(o: JsonObject): LyricsResult {
        if (o["instrumental"]?.jsonPrimitive?.booleanOrNull == true) return LyricsResult.NotFound
        val synced = o["syncedLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        val plain = o["plainLyrics"]?.jsonPrimitive?.contentOrNull?.takeIf { it.isNotBlank() }
        return toResult(synced, plain)
    }

    private fun toResult(synced: String?, plain: String?): LyricsResult {
        if (synced != null) {
            val lines = LrcParser.parse(synced)
            if (lines.isNotEmpty()) return LyricsResult.Found(Lyrics(lines, synced = true))
        }
        if (plain != null) return LyricsResult.Found(Lyrics(plain.lines().map { LyricLine(null, it.trim()) }, synced = false))
        return LyricsResult.NotFound
    }

    // --- Saved on the phone ----------------------------------------------------------------------

    private fun cacheKey(track: String, artist: String, album: String, seconds: Long): String {
        val raw = listOf(track, artist, album).joinToString("|") { it.trim().lowercase() } + "|$seconds"
        return MessageDigest.getInstance("SHA-256").digest(raw.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    private fun file(key: String) = File(dir, "$key.json")

    private fun readCache(key: String): LyricsResult? = runCatching {
        val f = file(key)
        if (!f.exists()) return null
        val o = json.parseToJsonElement(f.readText()).jsonObject
        when (o["status"]?.jsonPrimitive?.content) {
            "none" -> LyricsResult.NotFound
            "found" -> toResult(o["synced"]?.jsonPrimitive?.contentOrNull, o["plain"]?.jsonPrimitive?.contentOrNull)
            else -> null
        }
    }.getOrNull()

    private fun writeCache(key: String, result: LyricsResult) {
        runCatching {
            dir.mkdirs()
            val o = when (result) {
                is LyricsResult.Found -> buildJsonObject {
                    put("status", "found")
                    if (result.lyrics.synced) {
                        put("synced", result.lyrics.lines.joinToString("\n") { "[${lrcTime(it.timeMs ?: 0)}] ${it.text}" })
                    } else {
                        put("plain", result.lyrics.lines.joinToString("\n") { it.text })
                    }
                }
                else -> buildJsonObject { put("status", "none") }
            }
            file(key).writeText(o.toString())
        }
    }

    private fun lrcTime(ms: Long) = "%02d:%02d.%02d".format(ms / 60_000, (ms / 1000) % 60, (ms % 1000) / 10)

    companion object {
        /** A plain GET with a short timeout. Throws an IOException when there's no connection. */
        fun httpGet(url: String): Http {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 10_000
            conn.readTimeout = 10_000
            conn.setRequestProperty("User-Agent", "Veneer (Android launcher)")
            try {
                val code = conn.responseCode
                val body = (if (code in 200..299) conn.inputStream else conn.errorStream)?.use { it.readBytes().decodeToString() }.orEmpty()
                return Http(code, body)
            } finally {
                conn.disconnect()
            }
        }
    }
}
