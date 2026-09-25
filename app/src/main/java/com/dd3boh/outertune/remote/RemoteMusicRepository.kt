package com.dd3boh.outertune.remote

import android.util.Log
import com.dd3boh.outertune.remote.sources.KgSource
import com.dd3boh.outertune.remote.sources.KwSource
import com.dd3boh.outertune.remote.sources.MgSource
import com.dd3boh.outertune.remote.sources.TxSource
import com.dd3boh.outertune.remote.sources.WySource
import kotlinx.coroutines.Dispatchers
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
class RemoteMusicRepository @Inject constructor() {

    private val sources: List<RemoteMusicSource> = listOf(WySource, MgSource, TxSource, KgSource, KwSource)
    private val sourceById: Map<String, RemoteMusicSource> = sources.associateBy { it.sourceId }

    /** In-memory cache of resolved stream URLs keyed by outer tune mediaId. */
    private val urlCache = HashMap<String, String>()

    fun availableSources(): List<Pair<String, String>> =
        sources.map { it.sourceId to it.displayName }

    suspend fun search(sourceId: String, query: String, page: Int = 1, limit: Int = 30): List<RemoteSong> =
        withContext(Dispatchers.IO) {
            val source = sourceById[sourceId] ?: return@withContext emptyList()
            runCatching { source.search(query, page, limit) }
                .onFailure { Log.e("RemoteMusicRepo", "search failed", it) }
                .getOrDefault(emptyList())
        }

    /**
     * Resolve a playable stream URL for the given outer tune media id.
     * Called from the ResolvingDataSource callback (already a background thread,
     * but we dispatch to IO for clarity / safety).
     */
    suspend fun resolveStreamUrl(mediaId: String, quality: String = "128k"): String? =
        withContext(Dispatchers.IO) {
            urlCache[mediaId]?.let { return@withContext it }
            val parsed = parseMediaId(mediaId) ?: return@withContext null
            val (sourceId, sourceSongId) = parsed
            val source = sourceById[sourceId] ?: return@withContext null
            // Reconstruct a minimal RemoteSong for resolution.
            val song = RemoteSong(
                id = mediaId,
                source = sourceId,
                sourceSongId = sourceSongId,
                title = "",
                artists = emptyList(),
                albumName = null,
                durationSec = 0,
                thumbnailUrl = null,
            )
            runCatching { source.resolveStreamUrl(song, quality) }
                .onFailure { Log.e("RemoteMusicRepo", "resolveStreamUrl failed", it) }
                .getOrNull()
                ?.also { urlCache[mediaId] = it }
        }

    suspend fun getLyric(mediaId: String): RemoteLyric? = withContext(Dispatchers.IO) {
        val parsed = parseMediaId(mediaId) ?: return@withContext null
        val (sourceId, sourceSongId) = parsed
        val source = sourceById[sourceId] ?: return@withContext null
        val song = RemoteSong(
            id = mediaId, source = sourceId, sourceSongId = sourceSongId,
            title = "", artists = emptyList(), albumName = null,
            durationSec = 0, thumbnailUrl = null,
        )
        runCatching { source.getLyric(song) }.getOrNull()
    }

    /**
     * Parse "NM<sourceId><sourceSongId>" -> (sourceId, sourceSongId).
     * Source ids are wy/tx/kg/kw/mg (2 chars), so we split after "NM" + 2.
     */
    private fun parseMediaId(mediaId: String): Pair<String, String>? {
        if (!mediaId.startsWith("NM")) return null
        val rest = mediaId.removePrefix("NM")
        if (rest.length < 3) return null
        val sourceId = rest.substring(0, 2)
        val songId = rest.substring(2)
        return if (sourceById.containsKey(sourceId) && songId.isNotEmpty()) {
            sourceId to songId
        } else null
    }
}
