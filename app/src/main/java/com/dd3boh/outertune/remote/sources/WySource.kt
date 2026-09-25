package com.dd3boh.outertune.remote.sources

import com.dd3boh.outertune.remote.RemoteHttp
import com.dd3boh.outertune.remote.RemoteLyric
import com.dd3boh.outertune.remote.RemoteMusicSource
import com.dd3boh.outertune.remote.RemoteSong
import com.dd3boh.outertune.remote.crypto.WyCrypto
import org.json.JSONObject

/**
 * NetEase Cloud Music (wy) source.
 * Search via weapi cloudsearch/pc, stream URL via weapi player/url, lyric via eapi.
 */
object WySource : RemoteMusicSource {

    override val sourceId = "wy"
    override val displayName = "网易云"

    private const val UA =
        "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/69.0.3497.100 Safari/537.36"

    override fun search(query: String, page: Int, limit: Int): List<RemoteSong> {
        val offset = limit * (page - 1)
        val payload = JSONObject()
            .put("s", query)
            .put("type", 1)
            .put("limit", limit)
            .put("offset", offset)
            .put("total", page == 1)
            .toString()
        // weapi expects an object
        val form = WyCrypto.weapi(JSONObject(payload))
        val headers = mapOf("User-Agent" to UA, "Referer" to "https://music.163.com")
        val resp = RemoteHttp.postForm(
            "https://music.163.com/weapi/cloudsearch/pc", form, headers
        )
        val json = JSONObject(resp)
        val songs = json.optJSONObject("result")?.optJSONArray("songs") ?: return emptyList()
        val out = ArrayList<RemoteSong>()
        for (i in 0 until songs.length()) {
            val item = songs.getJSONObject(i)
            val id = item.getLong("id").toString()
            val name = item.optString("name")
            val ar = item.optJSONArray("ar")
            val artists = mutableListOf<String>()
            ar?.let { for (j in 0 until it.length()) artists.add(it.getJSONObject(j).optString("name")) }
            val al = item.optJSONObject("al")
            val albumName = al?.optString("name")?.takeIf { it != "null" }
            var pic = al?.optString("picUrl")
            if (pic != null && pic.startsWith("http://")) pic = "https://" + pic.substring(7)
            val dt = item.optLong("dt", 0) / 1000
            out.add(
                RemoteSong(
                    id = "NMwy$id",
                    source = sourceId,
                    sourceSongId = id,
                    title = name,
                    artists = artists,
                    albumName = albumName,
                    durationSec = dt.toInt(),
                    thumbnailUrl = pic,
                    extra = emptyMap()
                )
            )
        }
        return out
    }

    override fun resolveStreamUrl(song: RemoteSong, quality: String): String? {
        val id = song.sourceSongId.toLongOrNull() ?: return null
        val br = when (quality) {
            "flac" -> 999000
            "320k" -> 320000
            else -> 128000
        }
        val payload = JSONObject()
            .put("ids", org.json.JSONArray().put(id))
            .put("br", br)
            .toString()
        val form = WyCrypto.weapi(JSONObject(payload))
        val headers = mapOf("User-Agent" to UA, "Referer" to "https://music.163.com")
        val resp = RemoteHttp.postForm(
            "https://music.163.com/weapi/song/enhance/player/url", form, headers
        )
        return runCatching {
            val data = JSONObject(resp).getJSONArray("data")
            if (data.length() == 0) return null
            val url = data.getJSONObject(0).optString("url")
            if (url.isNullOrEmpty() || url == "null") null else url
        }.getOrNull()
    }

    override fun getLyric(song: RemoteSong): RemoteLyric? {
        val id = song.sourceSongId.toLongOrNull() ?: return null
        val payload = JSONObject()
            .put("id", id)
            .put("lv", -1)
            .put("yv", -1)
            .put("rv", -1)
        val form = WyCrypto.eapi("/api/song/lyric/v1", payload)
        val headers = mapOf("User-Agent" to UA)
        val resp = RemoteHttp.postForm(
            "https://interface3.music.163.com/eapi/song/lyric/v1", form, headers
        )
        return runCatching {
            val json = JSONObject(resp)
            val lrc = json.optJSONObject("lrc")?.optString("lyric")
            val tlyric = json.optJSONObject("tlyric")?.optString("lyric")
            RemoteLyric(
                lyric = lrc?.takeIf { it.isNotEmpty() },
                translated = tlyric?.takeIf { it.isNotEmpty() }
            )
        }.getOrNull()
    }
}
