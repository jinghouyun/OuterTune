package com.dd3boh.outertune.remote.sources

import com.dd3boh.outertune.remote.RemoteHttp
import com.dd3boh.outertune.remote.RemoteLyric
import com.dd3boh.outertune.remote.RemoteMusicSource
import com.dd3boh.outertune.remote.RemoteSong
import org.json.JSONObject
import java.util.zip.Inflater

/**
 * KuWo (kw) source. Search via search.kuwo.cn/r.s (pn is 0-based),
 * stream via playUrl API, lyric via newlyric.lrc (xor 'yeelion' + zlib + gb18030).
 */
object KwSource : RemoteMusicSource {

    override val sourceId = "kw"
    override val displayName = "酷我"

    private val XOR_KEY = "yeelion".toByteArray(Charsets.UTF_8)

    override fun search(query: String, page: Int, limit: Int): List<RemoteSong> {
        val url = "http://search.kuwo.cn/r.s" +
            "?client=kt&all=${java.net.URLEncoder.encode(query, "UTF-8")}" +
            "&pn=${page - 1}&rn=$limit" +
            "&uid=794762570&ver=kwplayer_ar_9.2.2.1&vipver=1&show_copyright_off=1" +
            "&newver=1&ft=music&cluster=0&strategy=2012&encoding=utf8" +
            "&rformat=json&vermerge=1&mobi=1&issubtitle=1"
        return runCatching {
            val resp = RemoteHttp.get(url)
            val json = JSONObject(resp)
            val total = json.optString("TOTAL")
            if (total == "0" || json.optString("SHOW") == "0") return@runCatching emptyList()
            val list = json.optJSONArray("abslist") ?: return@runCatching emptyList()
            val out = ArrayList<RemoteSong>()
            for (i in 0 until list.length()) {
                val item = list.getJSONObject(i)
                val rid = item.optString("MUSICRID")
                val mid = rid.removePrefix("MUSIC_")
                if (mid.isEmpty()) continue
                val artist = item.optString("ARTIST").replace("&", "、")
                out.add(
                    RemoteSong(
                        id = "NMkw$mid",
                        source = sourceId,
                        sourceSongId = mid,
                        title = item.optString("SONGNAME"),
                        artists = listOf(artist),
                        albumName = item.optString("ALBUM").ifEmpty { null },
                        durationSec = item.optInt("DURATION", 0),
                        thumbnailUrl = null,
                        extra = emptyMap()
                    )
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    override fun resolveStreamUrl(song: RemoteSong, quality: String): String? {
        val br = when (quality) {
            "flac" -> "2000kflac"
            "320k" -> "320kmp3"
            else -> "128kmp3"
        }
        return runCatching {
            val url = "http://www.kuwo.cn/api/v1/www/music/playUrl" +
                "?mid=${song.sourceSongId}&type=music&br=$br"
            val resp = RemoteHttp.get(url, mapOf("Referer" to "http://www.kuwo.cn/"))
            val json = JSONObject(resp)
            val data = json.optJSONObject("data") ?: return@runCatching null
            val playUrl = data.optString("url")
            if (playUrl.isEmpty() || playUrl == "null") null else playUrl
        }.getOrNull()
    }

    override fun getLyric(song: RemoteSong): RemoteLyric? {
        return runCatching {
            val params = buildLyricParams(song.sourceSongId)
            val bytes = RemoteHttp.getBytes(
                "http://newlyric.kuwo.cn/newlyric.lrc?$params"
            )
            decodeKwLyric(bytes)
        }.getOrNull()
    }

    private fun buildLyricParams(mid: String): String {
        val raw = "user=12345,web,web,web&requester=localhost&req=1&rid=MUSIC_${mid}&lrcx=1"
        val data = raw.toByteArray(Charsets.UTF_8)
        for (i in data.indices) data[i] = (data[i].toInt() xor XOR_KEY[i % XOR_KEY.size].toInt()).toByte()
        return android.util.Base64.encodeToString(data, android.util.Base64.NO_WRAP)
    }

    private fun decodeKwLyric(bytes: ByteArray): RemoteLyric? {
        val headerEnd = indexOf(bytes, "\r\n\r\n".toByteArray())
        if (headerEnd < 0) return null
        val header = String(bytes, 0, headerEnd, Charsets.ISO_8859_1)
        if (!header.startsWith("tp=content")) return null
        val body = bytes.copyOfRange(headerEnd + 4, bytes.size)
        val inflater = Inflater()
        inflater.setInput(body)
        val out = java.io.ByteArrayOutputStream(64 * 1024)
        val buf = ByteArray(8192)
        var done = false
        while (!done) {
            val n = try { inflater.inflate(buf) } catch (e: Exception) { done = true; break }
            if (n == 0) break
            out.write(buf, 0, n)
        }
        inflater.end()
        val text = out.toString("GB18030")
        return if (text.isBlank()) null else RemoteLyric(lyric = text)
    }

    private fun indexOf(data: ByteArray, pattern: ByteArray): Int {
        outer@ for (i in 0..data.size - pattern.size) {
            for (j in pattern.indices) if (data[i + j] != pattern[j]) continue@outer
            return i
        }
        return -1
    }
}
