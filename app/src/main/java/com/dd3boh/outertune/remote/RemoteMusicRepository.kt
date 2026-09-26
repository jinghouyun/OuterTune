package com.dd3boh.outertune.remote

import android.content.Context
import android.util.Log
import com.dd3boh.outertune.constants.RemoteSourceKgEnabledKey
import com.dd3boh.outertune.constants.RemoteSourceKwEnabledKey
import com.dd3boh.outertune.constants.RemoteSourceMgEnabledKey
import com.dd3boh.outertune.constants.RemoteSourceQualityKey
import com.dd3boh.outertune.constants.RemoteSourceTxEnabledKey
import com.dd3boh.outertune.constants.RemoteSourceWyEnabledKey
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.remote.sources.KgSource
import com.dd3boh.outertune.remote.sources.CustomSourceImpl
import com.dd3boh.outertune.remote.sources.KwSource
import com.dd3boh.outertune.remote.sources.MgSource
import com.dd3boh.outertune.remote.sources.TxSource
import com.dd3boh.outertune.remote.sources.WySource
import com.dd3boh.outertune.remote.js.JsScriptStore
import com.dd3boh.outertune.remote.js.LxScriptManager
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Aggregates all remote sources. Provides a single entry point used by the
 * search UI (ViewModel) and the playback layer (ResolvingDataSource).
 *
 * Remote song media ids have the form "NM<sourceId><sourceSongId>", e.g.
 * "NMwy347230", "NMmg1234567". The source prefix lets us route resolution.
 */
@Singleton
class RemoteMusicRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val database: MusicDatabase,
) {

    private val builtInSources: List<RemoteMusicSource> = listOf(WySource, MgSource, TxSource, KgSource, KwSource)

    private val customStore = CustomSourceStore(context)
    private val jsScriptStore = JsScriptStore(context)

    /**
     * The active lx-music user-script engine. Scripts only enhance built-in sources
     * (kw/kg/tx/wy/mg) for musicUrl / lyric / pic; they never provide search.
     */
    val jsManager = LxScriptManager(context)

    private val builtinSourceIds = setOf("wy", "mg", "tx", "kg", "kw")

    /**
     * Cache of built custom-source instances keyed by CustomSource.id. Rhino scopes are expensive
     * to compile, so we build each REST custom source once and reuse it.
     */
    private val customInstanceCache = HashMap<String, RemoteMusicSource>()

    /** JS scripts are enhancement-only and are NOT registered as searchable sources. */
    private fun buildCustomSource(src: CustomSource): RemoteMusicSource? {
        if (src.isJs) return null
        customInstanceCache[src.id]?.let { return it }
        val instance = CustomSourceImpl(src)
        customInstanceCache[src.id] = instance
        return instance
    }

    /** Reload the JS engine if the active script selection changed since last use. */
    private fun ensureJs() {
        if (customStore.activeJsId() != jsManager.activeId) jsManager.reload()
    }

    /** Build an lx-music oldMusicInfo object to hand to the active JS script. */
    private suspend fun lxMusicInfo(
        mediaId: String,
        sourceId: String,
        sourceSongId: String,
    ): Map<String, Any?> {
        val dbSong = runCatching { database.song(mediaId).first() }.getOrNull()
        val title = dbSong?.title ?: ""
        val artists = dbSong?.artists?.joinToString("/") { it.name } ?: ""
        val durationSec = dbSong?.song?.duration ?: 0
        return buildMap {
            put("name", title)
            put("singer", artists)
            put("source", sourceId)
            put("songmid", sourceSongId)
            if (sourceId == "kg") put("hash", sourceSongId)
            dbSong?.album?.title?.let { put("albumName", it) }
            if (durationSec > 0) put("interval", formatInterval(durationSec))
            put("types", mapOf("128k" to emptyMap<String, Any?>(), "320k" to emptyMap(), "flac" to emptyMap()))
        }
    }

    private fun formatInterval(sec: Int): String {
        val m = sec / 60
        val s = sec % 60
        return "%02d:%02d".format(m, s)
    }

    /** All currently enabled sources: built-ins + user-defined custom sources (REST + JS). */
    private fun allSources(): List<RemoteMusicSource> {
        val customs = runCatching {
            customStore.getEnabled().mapNotNull { buildCustomSource(it) }
        }.getOrDefault(emptyList())
        return builtInSources + customs
    }

    private fun sourceById(id: String): RemoteMusicSource? =
        allSources().firstOrNull { it.sourceId == id }

    /** In-memory cache of resolved stream URLs keyed by outer tune mediaId. */
    private val urlCache = HashMap<String, String>()

    fun availableSources(): List<Pair<String, String>> =
        allSources().map { it.sourceId to it.displayName }

    /** All enabled source ids (built-in enabled + enabled custom sources). */
    fun enabledSourceIds(): List<String> =
        allSources().filter { isSourceEnabled(it.sourceId) }.map { it.sourceId }

    /** Public lookup for a source by id (may be a custom source). */
    fun sourceByIdPublic(id: String): RemoteMusicSource? = sourceById(id)

    /** Parse a mediaId's sourceId, or null. */
    fun parseSourceId(mediaId: String): String? = parseMediaId(mediaId)?.first

    /** Whether the source backing this mediaId supports vocal separation. */
    fun supportsSeparation(mediaId: String): Boolean {
        val sid = parseSourceId(mediaId) ?: return false
        return sourceById(sid)?.supportsSeparation == true
    }

    /** Read the user-configured default quality. */
    private fun configuredQuality(): String =
        context.dataStore[RemoteSourceQualityKey] ?: "128k"

    /** Whether a source is enabled per user preferences (default true). */
    private fun isSourceEnabled(sourceId: String): Boolean {
        val key = when (sourceId) {
            "wy" -> RemoteSourceWyEnabledKey
            "mg" -> RemoteSourceMgEnabledKey
            "tx" -> RemoteSourceTxEnabledKey
            "kg" -> RemoteSourceKgEnabledKey
            "kw" -> RemoteSourceKwEnabledKey
            else -> return true
        }
        return context.dataStore[key] ?: true
    }

    suspend fun search(sourceId: String, query: String, page: Int = 1, limit: Int = 30): List<RemoteSong> =
        withContext(Dispatchers.IO) {
            if (!isSourceEnabled(sourceId)) return@withContext emptyList()
            val source = sourceById(sourceId) ?: return@withContext emptyList()
            runCatching { source.search(query, page, limit) }
                .onFailure { Log.e("RemoteMusicRepo", "search failed", it) }
                .getOrDefault(emptyList())
        }

    /**
     * Resolve a playable stream URL for the given outer tune media id.
     * Falls back to other enabled sources if the primary source returns no URL.
     */
    suspend fun resolveStreamUrl(mediaId: String, quality: String? = null): String? =
        withContext(Dispatchers.IO) {
            urlCache[mediaId]?.let { return@withContext it }
            ensureJs()
            val parsed = parseMediaId(mediaId) ?: return@withContext null
            val (sourceId, sourceSongId) = parsed
            val source = sourceById(sourceId) ?: return@withContext null
            val q = quality ?: configuredQuality()

            // 1) Active JS enhancement script wins for built-in sources (lx-music behavior).
            if (sourceId in builtinSourceIds && jsManager.supports(sourceId, "musicUrl")) {
                val jsUrl = runCatching {
                    jsManager.tryMusicUrl(sourceId, q, lxMusicInfo(mediaId, sourceId, sourceSongId))
                }.onFailure { Log.e("RemoteMusicRepo", "js musicUrl failed", it) }.getOrNull()
                if (!jsUrl.isNullOrBlank()) {
                    Log.i("RemoteMusicRepo", "JS script resolved url for $sourceId")
                    urlCache[mediaId] = jsUrl
                    return@withContext jsUrl
                }
            }

            val probeSong = RemoteSong(
                id = mediaId, source = sourceId, sourceSongId = sourceSongId,
                title = "", artists = emptyList(), albumName = null,
                durationSec = 0, thumbnailUrl = null,
            )
            val primary = runCatching { source.resolveStreamUrl(probeSong, q) }
                .onFailure { Log.e("RemoteMusicRepo", "primary resolve failed", it) }
                .getOrNull()
            if (primary != null) {
                urlCache[mediaId] = primary
                return@withContext primary
            }

            // --- cross-source fallback ---
            Log.i("RemoteMusicRepo", "primary source $sourceId failed, trying other sources")
            val dbSong = runCatching {
                database.song(mediaId).first()
            }.getOrNull()
            if (dbSong == null) return@withContext null
            val title = dbSong.title
            val artists = dbSong.artists.joinToString(" ") { it.name }
            val durationSec = dbSong.song.duration

            val candidates = mutableListOf<RemoteSong>()
            for (other in allSources()) {
                if (other.sourceId == sourceId) continue
                if (!isSourceEnabled(other.sourceId)) continue
                runCatching {
                    val results = other.search("$title $artists", page = 1, limit = 10)
                    candidates += results.filter { matchSong(it, title, artists, durationSec) }
                }
            }
            // try candidates in order
            for (cand in candidates.take(5)) {
                val url = runCatching { cand.let { s ->
                    sourceById(s.source)?.resolveStreamUrl(s, q)
                } }.getOrNull()
                if (url != null) {
                    Log.i("RemoteMusicRepo", "fallback to ${cand.source} succeeded")
                    urlCache[mediaId] = url
                    return@withContext url
                }
            }
            null
        }

    /** NetEase hot search words. */
    suspend fun getHotSearch(): List<String> = withContext(Dispatchers.IO) {
        runCatching { WySource.getHotSearch() }.getOrDefault(emptyList())
    }

    suspend fun getLyric(mediaId: String): RemoteLyric? = withContext(Dispatchers.IO) {
        ensureJs()
        val parsed = parseMediaId(mediaId) ?: return@withContext null
        val (sourceId, sourceSongId) = parsed

        // 1) Active JS enhancement script first.
        if (sourceId in builtinSourceIds && jsManager.supports(sourceId, "lyric")) {
            val jsLyric = runCatching {
                jsManager.tryLyric(sourceId, lxMusicInfo(mediaId, sourceId, sourceSongId))
            }.onFailure { Log.e("RemoteMusicRepo", "js lyric failed", it) }.getOrNull()
            if (jsLyric != null) return@withContext applyS2T(jsLyric)
        }

        val source = sourceById(sourceId) ?: return@withContext null
        val song = RemoteSong(
            id = mediaId, source = sourceId, sourceSongId = sourceSongId,
            title = "", artists = emptyList(), albumName = null,
            durationSec = 0, thumbnailUrl = null,
        )
        val lyric = runCatching { source.getLyric(song) }.getOrNull() ?: return@withContext null
        applyS2T(lyric)
    }

    private fun applyS2T(lyric: RemoteLyric): RemoteLyric {
        if (context.dataStore[com.dd3boh.outertune.constants.S2TConvertKey] == true) {
            return lyric.copy(
                lyric = lyric.lyric?.let { com.dd3boh.outertune.utils.S2TConverter.convert(it) },
                translated = lyric.translated?.let { com.dd3boh.outertune.utils.S2TConverter.convert(it) }
            )
        }
        return lyric
    }

    /**
     * Fill in cover URLs for songs that don't have one (kg/kw). Returns a new list
     * with thumbnailUrl populated where possible.
     */
    suspend fun enrichCovers(songs: List<RemoteSong>): List<RemoteSong> =
        withContext(Dispatchers.IO) {
            ensureJs()
            songs.map { song ->
                if (song.thumbnailUrl != null) return@map song
                val cover = when {
                    song.source in builtinSourceIds && jsManager.supports(song.source, "pic") -> {
                        runCatching {
                            val info = buildMap {
                                put("name", song.title)
                                put("singer", song.artists.joinToString("/"))
                                put("source", song.source)
                                put("songmid", song.sourceSongId)
                                if (song.source == "kg") put("hash", song.extra["hash"] ?: song.sourceSongId)
                                song.albumName?.let { put("albumName", it) }
                                song.extra.forEach { (k, v) -> put(k, v) }
                            }
                            jsManager.tryPic(song.source, info)
                        }.getOrNull()
                    }
                    song.source == "kw" -> fetchKwCover(song.sourceSongId)
                    song.source == "kg" -> fetchKgCover(song)
                    song.source.startsWith("custom_") -> {
                        val s = sourceById(song.source)
                        if (s is CustomSourceImpl) s.fetchCover(song.sourceSongId) else null
                    }
                    else -> null
                }
                if (cover != null) song.copy(thumbnailUrl = cover) else song
            }
        }

    private fun fetchKwCover(mid: String): String? = runCatching {
        val body = RemoteHttp.get(
            "http://artistpicserver.kuwo.cn/pic.web?corp=kuwo&type=rid_pic&pictype=500&size=500&rid=$mid"
        ).trim()
        if (body.startsWith("http")) body else null
    }.getOrNull()

    private fun fetchKgCover(song: RemoteSong): String? = runCatching {
        val hash = song.extra["hash"] ?: return@runCatching null
        val body = """{"resources":[{"hash":"$hash","album_audio_id":0,"album_id":0,"type":"audio"}]}"""
        val resp = RemoteHttp.postJson(
            "http://media.store.kugou.com/v1/get_res_privilege",
            body,
            mapOf("User-Agent" to "Android712-")
        )
        val json = org.json.JSONObject(resp)
        val info = json.optJSONArray("data")?.optJSONObject(0)?.optJSONObject("info")
        var image = info?.optString("image") ?: return@runCatching null
        val size = info.optJSONArray("imgsize")?.optInt(0) ?: 500
        image.replace("{size}", size.toString())
    }.getOrNull()

    /** Match by duration (<5s diff) then title/artist containment. */
    private fun matchSong(cand: RemoteSong, title: String, artists: String, durationSec: Int): Boolean {
        if (durationSec > 0 && kotlin.math.abs(cand.durationSec - durationSec) > 5) return false
        if (cand.title == title) return true
        if (title.contains(cand.title, ignoreCase = true) || cand.title.contains(title, ignoreCase = true)) return true
        return false
    }

    /**
     * Parse "NM<sourceId><sourceSongId>" -> (sourceId, sourceSongId).
     * Source ids are matched against all currently-known sources (built-in wy/mg/tx/kg/kw
     * and dynamic "custom_<uuid>" ids). The longest matching known id wins.
     */
    private fun parseMediaId(mediaId: String): Pair<String, String>? {
        if (!mediaId.startsWith("NM")) return null
        val rest = mediaId.removePrefix("NM")
        // find the longest known sourceId that rest starts with
        val known = allSources().map { it.sourceId }.sortedByDescending { it.length }
        for (sid in known) {
            if (rest.startsWith(sid) && rest.length > sid.length) {
                return sid to rest.removePrefix(sid)
            }
        }
        return null
    }
}
