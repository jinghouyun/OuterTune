package com.dd3boh.outertune.remote.sources

import com.dd3boh.outertune.remote.RemoteHttp
import com.dd3boh.outertune.remote.RemoteLyric
import com.dd3boh.outertune.remote.RemoteMusicSource
import com.dd3boh.outertune.remote.RemoteSong
import org.json.JSONObject
import java.util.zip.Inflater

/**
 * KuGou (kg) source. Search via songsearch v2, stream via hash playInfo,
 * lyric via two-step candidate search + download (krc xor+zlib decode).
 */
object KgSource : RemoteMusicSource {

    override val sourceId = "kg"
    override val displayName = "酷狗"

    private val KRC_KEY = byteArrayOf(
        0x40, 0x47, 0x61, 0x77, 0x5e, 0x32, 0x74, 0x47,
        0x51, 0x36, 0x31, 0x2d, 0xce.toByte(), 0xd2.toByte(), 0x6e, 0x69
    )

    override fun search(query: String, page: Int, limit: Int): List<RemoteSong> {
        val url = "https://songsearch.kugou.com/song_search_v2" +
            "?keyword=${java.net.URLEncoder.encode(query, "UTF-8")}" +
            "&page=$page&pagesize=$limit&userid=0&clientver=&platform=WebFilter" +
            "&filter=2&iscorrection=1&privilege_filter=0&area_code=1"
        return runCatching {
            val resp = RemoteHttp.get(url)
            val json = JSONObject(resp)
            if (json.optInt("error_code") != 0) return@runCatching emptyList()
            val lists = json.optJSONObject("data")?.optJSONArray("lists")
                ?: return@runCatching emptyList()
            val out = ArrayList<RemoteSong>()
            for (i in 0 until lists.length()) {
                val item = lists.getJSONObject(i)
                val hash = item.optString("FileHash")
                val audioId = item.optString("AudioID")
                if (hash.isEmpty()) continue
                val singers = mutableListOf<String>()
                item.optJSONArray("Singers")?.let {
                    for (j in 0 until it.length()) singers.add(it.getJSONObject(j).optString("name"))
                }
                out.add(
                    RemoteSong(
                        id = "NMkg$hash",
                        source = sourceId,
                        sourceSongId = audioId,
                        title = item.optString("SongName"),
                        artists = singers,
                        albumName = item.optString("AlbumName").ifEmpty { null },
                        durationSec = item.optInt("Duration", 0),
                        thumbnailUrl = null,
                        extra = mapOf("hash" to hash, "interval" to item.optInt("Duration", 0).toString())
                    )
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    override fun resolveStreamUrl(song: RemoteSong, quality: String): String? {
        val hash = song.extra["hash"] ?: return null
        return runCatching {
            val resp = RemoteHttp.get(
                "http://m.kugou.com/app/i/getSongInfo.php?cmd=playInfo&hash=$hash"
            )
            val json = JSONObject(resp)
            val url = json.optString("url")
            if (url.isEmpty() || url == "null") null else url
        }.getOrNull()
    }

    override fun getLyric(song: RemoteSong): RemoteLyric? {
        val hash = song.extra["hash"] ?: return null
        val interval = song.extra["interval"]?.toLongOrNull() ?: 0L
        return runCatching {
            // step 1: find candidate
            val searchUrl = "http://lyrics.kugou.com/search" +
                "?ver=1&man=yes&client=pc" +
                "&keyword=${java.net.URLEncoder.encode(song.title, "UTF-8")}" +
                "&hash=$hash&timelength=${interval * 1000}&lrctxt=1"
            val headers = mapOf(
                "KG-RC" to "1",
                "KG-THash" to "expand_search_manager.cpp:852736169:451",
                "User-Agent" to "KuGou2012-9020-ExpandSearchManager"
            )
            val searchResp = JSONObject(RemoteHttp.get(searchUrl, headers))
            val candidate = searchResp.optJSONArray("candidates")?.optJSONObject(0)
                ?: return@runCatching null
            val id = candidate.optString("id")
            val accessKey = candidate.optString("accesskey")
            val fmt = candidate.optString("fmt")
            // step 2: download
            val dlUrl = "http://lyrics.kugou.com/download" +
                "?ver=1&client=pc&id=$id&accesskey=$accessKey&fmt=$fmt&charset=utf8"
            val dlResp = JSONObject(RemoteHttp.get(dlUrl, headers))
            val content = dlResp.optString("content")
            if (content.isEmpty()) return@runCatching null
            val text = when (fmt) {
                "krc" -> decodeKrc(content)
                else -> String(android.util.Base64.decode(content, android.util.Base64.DEFAULT), Charsets.UTF_8)
            }
            RemoteLyric(lyric = text)
        }.getOrNull()
    }

    private fun decodeKrc(b64: String): String {
        val raw = android.util.Base64.decode(b64, android.util.Base64.DEFAULT)
        // skip first 4 bytes
        val data = raw.copyOfRange(4, raw.size)
        for (i in data.indices) data[i] = (data[i].toInt() xor KRC_KEY[i % 16].toInt()).toByte()
        // zlib inflate
        val inflater = Inflater(true)
        inflater.setInput(data)
        val out = java.io.ByteArrayOutputStream(64 * 1024)
        val buf = ByteArray(8192)
        while (!inflater.finished()) {
            val n = inflater.inflate(buf)
            if (n == 0) break
            out.write(buf, 0, n)
        }
        inflater.end()
        return out.toString("UTF-8")
    }
}
