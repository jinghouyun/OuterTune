package com.dd3boh.outertune.remote.sources

import com.dd3boh.outertune.remote.RemoteHttp
import com.dd3boh.outertune.remote.RemoteLyric
import com.dd3boh.outertune.remote.RemoteMusicSource
import com.dd3boh.outertune.remote.RemoteSong
import com.dd3boh.outertune.remote.crypto.Crypto
import org.json.JSONArray
import org.json.JSONObject

/**
 * Migu (mg) source. Search uses a signed GET; stream URL via listen-url; lyric via lrcUrl.
 */
object MgSource : RemoteMusicSource {

    override val sourceId = "mg"
    override val displayName = "咪咕"

    private const val DEVICE_ID = "963B7AA0D21511ED807EE5846EC87D20"
    private const val SIGN_SECRET = "6cdc72a439cef99a3418d2a78aa28c73"
    private const val SIGN_SUFFIX = "yyapp2d16148780a1dcc7408e06336b98cfd50"
    private const val UA =
        "Mozilla/5.0 (Linux; Android 12; MI 11 Build/SKQ1.211006.001) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/107.0.5304.105 Mobile Safari/537.36"

    private fun commonHeaders(time: String, sign: String) = mapOf(
        "uiVersion" to "A_music_3.6.1",
        "deviceId" to DEVICE_ID,
        "timestamp" to time,
        "sign" to sign,
        "channel" to "0146921",
        "User-Agent" to UA,
    )

    override fun search(query: String, page: Int, limit: Int): List<RemoteSong> {
        val time = System.currentTimeMillis().toString()
        val sign = Crypto.md5Hex("$query$SIGN_SECRET$SIGN_SUFFIX$DEVICE_ID$time")
        // searchSwitch mirrors lx-music-mobile exactly
        val searchSwitch = """{"song":1,"album":0,"singer":0,"tagSong":1,"mvSong":0,"bestShow":1,"songlist":0,"lyricSong":0}"""
        val url =
            "https://jadeite.migu.cn/music_search/v3/search/searchAll" +
                "?isCorrect=0&isCopyright=1" +
                "&searchSwitch=" + java.net.URLEncoder.encode(searchSwitch, "UTF-8") +
                "&pageSize=$limit&text=" + java.net.URLEncoder.encode(query, "UTF-8") +
                "&pageNo=$page&sort=0&sid=USS"
        val resp = RemoteHttp.get(url, commonHeaders(time, sign))
        val json = JSONObject(resp)
        if (json.optString("code") != "000000") return emptyList()
        val resultList = json.optJSONObject("songResultData")?.optJSONArray("resultList")
            ?: return emptyList()
        val out = ArrayList<RemoteSong>()
        for (i in 0 until resultList.length()) {
            // resultList is a nested array in the API; unwrap data arrays
            val entry = resultList.opt(i) ?: continue
            val items = when (entry) {
                is JSONArray -> entry
                is JSONObject -> entry.optJSONArray("data") ?: continue
                else -> continue
            }
            for (j in 0 until items.length()) {
                val data = items.optJSONObject(j) ?: continue
                val songId = data.optString("songId").takeIf { it.isNotEmpty() } ?: continue
                val name = data.optString("name")
                val singers = mutableListOf<String>()
                data.optJSONArray("singerList")?.let {
                    for (k in 0 until it.length()) singers.add(it.getJSONObject(k).optString("name"))
                }
                val album = data.optJSONObject("album")
                val albumName = album?.optString("album")?.takeIf { it != "null" && it.isNotEmpty() }
                val duration = data.optInt("duration", 0)
                var img = data.optString("img3").ifEmpty {
                    data.optString("img2").ifEmpty { data.optString("img1") }
                }
                if (img.isNotEmpty() && !img.startsWith("http")) {
                    img = "http://d.musicapp.migu.cn$img"
                } else if (img.isEmpty()) img = ""
                val lrcUrl = data.optString("lrcUrl").ifEmpty { null }
                out.add(
                    RemoteSong(
                        id = "NMmg$songId",
                        source = sourceId,
                        sourceSongId = songId,
                        title = name,
                        artists = singers,
                        albumName = albumName,
                        durationSec = duration,
                        thumbnailUrl = img.ifEmpty { null },
                        extra = mapOf(
                            "copyrightId" to data.optString("copyrightId"),
                            "lrcUrl" to (lrcUrl ?: "")
                        )
                    )
                )
            }
        }
        return out
    }

    override fun resolveStreamUrl(song: RemoteSong, quality: String): String? {
        val toneFlag = when (quality) {
            "flac" -> "SQ"
            "320k" -> "HQ"
            else -> "PQ"
        }
        val url = "https://app.c.nf.migu.cn/MIGUM2.0/v2.0/content/listen-url" +
            "?netType=00&resourceType=2&songId=${song.sourceSongId}&toneFlag=$toneFlag"
        val headers = mapOf(
            "Referer" to "https://app.c.nf.migu.cn/",
            "channel" to "0146921",
            "User-Agent" to UA,
        )
        val resp = RemoteHttp.get(url, headers)
        return runCatching {
            val json = JSONObject(resp)
            val data = json.optJSONObject("data") ?: return@runCatching null
            val streamUrl = data.optString("url").ifEmpty {
                data.optString("audio").ifEmpty { return@runCatching null }
            }
            streamUrl
        }.getOrNull()
    }

    override fun getLyric(song: RemoteSong): RemoteLyric? {
        val lrcUrl = song.extra["lrcUrl"]?.takeIf { it.isNotEmpty() } ?: return null
        return runCatching {
            val headers = mapOf(
                "Referer" to "https://app.c.nf.migu.cn/",
                "channel" to "0146921",
                "User-Agent" to UA,
            )
            val text = RemoteHttp.get(lrcUrl, headers)
            if (text.isBlank()) null else RemoteLyric(lyric = text)
        }.getOrNull()
    }
}
