package com.dd3boh.outertune.remote.sources

import com.dd3boh.outertune.remote.RemoteHttp
import com.dd3boh.outertune.remote.RemoteLyric
import com.dd3boh.outertune.remote.RemoteMusicSource
import com.dd3boh.outertune.remote.RemoteSong
import com.dd3boh.outertune.remote.crypto.TxCrypto
import org.json.JSONArray
import org.json.JSONObject

/**
 * QQ Music (tx) source. Search via musics.fcg (zzcSign), stream via musicu.fcg
 * GetVkeyServer, lyric via c.y.qq.com (base64).
 */
object TxSource : RemoteMusicSource {

    override val sourceId = "tx"
    override val displayName = "QQ音乐"

    private const val UA = "QQMusic 14090508(android 12)"

    private fun guid(): String =
        java.util.UUID.randomUUID().toString().replace("-", "").takeDigits(32)

    private fun String.takeDigits(n: Int): String =
        filter { it.isDigit() }.padEnd(n, '0').take(n)

    override fun search(query: String, page: Int, limit: Int): List<RemoteSong> {
        // comm block mirrors lx-music-mobile (device fields required for QQ to return results).
        val comm = JSONObject()
            .put("ct", "11")
            .put("cv", "14090508")
            .put("v", "14090508")
            .put("tmeAppID", "qqmusic")
            .put("phonetype", "EBG-AN10")
            .put("deviceScore", "553.47")
            .put("devicelevel", "50")
            .put("newdevicelevel", "20")
            .put("rom", "HuaWei/EMOTION/EmotionUI_14.2.0")
            .put("os_ver", "12")
            .put("OpenUDID", "0")
            .put("OpenUDID2", "0")
            .put("QIMEI36", "0")
            .put("udid", "0")
            .put("chid", "0")
            .put("aid", "0")
            .put("oaid", "0")
            .put("taid", "0")
            .put("tid", "0")
            .put("wid", "0")
            .put("uid", "0")
            .put("sid", "0")
            .put("modeSwitch", "6")
            .put("teenMode", "0")
            .put("ui_mode", "2")
            .put("nettype", "1020")
        val reqData = JSONObject()
            .put("comm", comm)
            .put("req", JSONObject()
                .put("module", "music.search.SearchCgiService")
                .put("method", "DoSearchForQQMusicMobile")
                .put("param", JSONObject()
                    .put("search_type", 0)
                    .put("searchid", java.util.UUID.randomUUID().toString().replace("-", ""))
                    .put("query", query)
                    .put("page_num", page)
                    .put("num_per_page", limit)
                    .put("highlight", 0)
                    .put("nqc_flag", 0)
                    .put("multi_zhida", 0)
                    .put("cat", 2)
                    .put("grp", 1)
                    .put("sin", 0)
                    .put("sem", 0)
                )
            )
        val body = reqData.toString()
        val sign = TxCrypto.zzcSign(body)
        val resp = RemoteHttp.postJson(
            "https://u.y.qq.com/cgi-bin/musics.fcg?sign=$sign",
            body,
            mapOf("User-Agent" to UA)
        )
        return runCatching {
            val json = JSONObject(resp)
            if (json.optInt("code") != 0) return@runCatching emptyList()
            val req = json.optJSONObject("req") ?: return@runCatching emptyList()
            if (req.optInt("code") != 0) return@runCatching emptyList()
            // Response shape: req.data.body.item_song (not req.data.item_song)
            val body = req.optJSONObject("data") ?: return@runCatching emptyList()
            val items = body.optJSONObject("body")?.optJSONArray("item_song")
                ?: body.optJSONArray("item_song")
                ?: return@runCatching emptyList()
            val out = ArrayList<RemoteSong>()
            for (i in 0 until items.length()) {
                val item = items.getJSONObject(i)
                val mid = item.optString("mid")
                if (mid.isEmpty()) continue
                val singers = mutableListOf<String>()
                item.optJSONArray("singer")?.let {
                    for (j in 0 until it.length()) singers.add(it.getJSONObject(j).optString("name"))
                }
                val album = item.optJSONObject("album")
                val albumMid = album?.optString("mid").orEmpty()
                val albumName = album?.optString("name")
                val interval = item.optInt("interval", 0)
                val cover = if (albumMid.isNotEmpty())
                    "https://y.gtimg.cn/music/photo_new/T002R500x500M000${albumMid}.jpg" else null
                out.add(
                    RemoteSong(
                        id = "NMtx$mid",
                        source = sourceId,
                        sourceSongId = mid,
                        title = item.optString("title"),
                        artists = singers,
                        albumName = albumName,
                        durationSec = interval,
                        thumbnailUrl = cover,
                        extra = mapOf("albumMid" to albumMid)
                    )
                )
            }
            out
        }.getOrDefault(emptyList())
    }

    override fun resolveStreamUrl(song: RemoteSong, quality: String): String? {
        val vkeyBody = JSONObject()
            .put("comm", JSONObject().put("ct", "11").put("cv", "14090508"))
            .put("req_0", JSONObject()
                .put("module", "music.vkey.GetVkeyServer")
                .put("method", "CgiGetVkey")
                .put("param", JSONObject()
                    .put("guid", guid())
                    .put("songmid", JSONArray().put(song.sourceSongId))
                    .put("songtype", JSONArray().put(0))
                    .put("uin", "0")
                    .put("loginflag", 0)
                    .put("platform", "20")
                )
            )
        return runCatching {
            val resp = RemoteHttp.postJson(
                "https://u.y.qq.com/cgi-bin/musicu.fcg",
                vkeyBody.toString(),
                mapOf("User-Agent" to UA)
            )
            val json = JSONObject(resp)
            val info = json.optJSONObject("req_0")
                ?.optJSONObject("data")
                ?.optJSONArray("midurlinfo")
                ?.optJSONObject(0) ?: return@runCatching null
            val purl = info.optString("purl")
            if (purl.isEmpty()) return@runCatching null
            "https://dl.stream.qqmusic.qq.com/$purl"
        }.getOrNull()
    }

    override fun getLyric(song: RemoteSong): RemoteLyric? {
        return runCatching {
            val url = "https://c.y.qq.com/lyric/fcgi-bin/fcg_query_lyric_new.fcg" +
                "?songmid=${song.sourceSongId}&g_tk=5381&loginUin=0&hostUin=0" +
                "&format=json&inCharset=utf8&outCharset=utf8&platform=yqq"
            val resp = RemoteHttp.get(url, mapOf("Referer" to "https://y.qq.com/portal/player.html"))
            val json = JSONObject(resp)
            val lrc = decodeB64Utf8(json.optString("lyric"))
            val trans = decodeB64Utf8(json.optString("trans"))
            RemoteLyric(lyric = lrc, translated = trans)
        }.getOrNull()
    }

    private fun decodeB64Utf8(s: String): String? {
        if (s.isEmpty() || s == "null") return null
        return runCatching {
            String(android.util.Base64.decode(s, android.util.Base64.DEFAULT), Charsets.UTF_8)
        }.getOrNull()
    }
}
