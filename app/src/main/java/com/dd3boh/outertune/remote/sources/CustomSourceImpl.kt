package com.dd3boh.outertune.remote.sources

import com.dd3boh.outertune.remote.CustomSource
import com.dd3boh.outertune.remote.RemoteHttp
import com.dd3boh.outertune.remote.RemoteLyric
import com.dd3boh.outertune.remote.RemoteMusicSource
import com.dd3boh.outertune.remote.RemoteSong
import org.json.JSONObject
import java.net.URLEncoder

/**
 * A user-defined source implementing the LX Music compatible API.
 * sourceId is "custom_<uuid>".
 */
class CustomSourceImpl(val source: CustomSource) : RemoteMusicSource {

    override val sourceId: String = "custom_${source.id}"
    override val displayName: String = source.name

    private fun url(path: String): String {
        val base = source.baseUrl.trimEnd('/')
        val p = if (path.startsWith("/")) path else "/$path"
        return base + p
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    override fun search(query: String, page: Int, limit: Int): List<RemoteSong> = runCatching {
        val u = url(source.searchPath) + "?keyword=${enc(query)}&page=$page&limit=$limit"
        val resp = RemoteHttp.get(u)
        val json = JSONObject(resp)
        // LX format: { "data": { "list": [ ... ] } }
        val list = json.optJSONObject("data")?.optJSONArray("list")
            ?: json.optJSONArray("list")
            ?: return@runCatching emptyList()
        val out = ArrayList<RemoteSong>()
        for (i in 0 until list.length()) {
            val item = list.getJSONObject(i)
            val songmid = item.optString("songmid").ifBlank { item.optString("id") }
            if (songmid.isBlank()) continue
            val name = item.optString("name").ifBlank { item.optString("title") }
            val singer = item.optString("singer").ifBlank { item.optString("artist") }
            val artists = singer.split("/", ",").map { it.trim() }.filter { it.isNotEmpty() }
            val albumName = item.optString("albumName").ifBlank { item.optString("album") }.ifBlank { null }
            val durationSec = parseInterval(item.optString("interval"))
            val pic = item.optString("img").ifBlank { item.optString("pic") }.ifBlank { null }
            out.add(
                RemoteSong(
                    id = "NM$sourceId$songmid",
                    source = sourceId,
                    sourceSongId = songmid,
                    title = name,
                    artists = artists,
                    albumName = albumName,
                    durationSec = durationSec,
                    thumbnailUrl = pic,
                )
            )
        }
        out
    }.getOrDefault(emptyList())

    override fun resolveStreamUrl(song: RemoteSong, quality: String): String? = runCatching {
        val u = url(source.urlPath) + "?songmid=${enc(song.sourceSongId)}&quality=${enc(quality)}"
        val resp = RemoteHttp.get(u)
        val json = JSONObject(resp)
        json.optJSONObject("data")?.optString("url")?.takeIf { it.isNotBlank() }
            ?: json.optString("url").takeIf { it.isNotBlank() }
    }.getOrNull()

    override fun getLyric(song: RemoteSong): RemoteLyric? = runCatching {
        val u = url(source.lyricPath) + "?songmid=${enc(song.sourceSongId)}"
        val resp = RemoteHttp.get(u)
        val json = JSONObject(resp)
        val data = json.optJSONObject("data")
        val lyric = data?.optString("lyric")?.takeIf { it.isNotBlank() }
            ?: json.optString("lyric").takeIf { it.isNotBlank() }
            ?: return@runCatching null
        val tlyric = data?.optString("tlyric")?.takeIf { it.isNotBlank() }
            ?: json.optString("tlyric").takeIf { it.isNotBlank() }
        RemoteLyric(lyric = lyric, translated = tlyric, roman = null)
    }.getOrNull()

    /** Fetch cover URL for a song. */
    fun fetchCover(songmid: String): String? = runCatching {
        val u = url(source.picPath) + "?songmid=${enc(songmid)}"
        val resp = RemoteHttp.get(u).trim()
        // either { "data": { "url": "..." } } or a raw URL string
        runCatching {
            JSONObject(resp).optJSONObject("data")?.optString("url")
                ?: JSONObject(resp).optString("url")
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: if (resp.startsWith("http")) resp else null
    }.getOrNull()

    private fun parseInterval(s: String): Int {
        if (s.isBlank()) return 0
        if (s.contains(":")) {
            val parts = s.split(":")
            val min = parts.getOrNull(0)?.toIntOrNull() ?: return 0
            val sec = parts.getOrNull(1)?.toIntOrNull() ?: return 0
            return min * 60 + sec
        }
        // assume seconds
        return s.toIntOrNull() ?: 0
    }
}
