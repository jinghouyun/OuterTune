package com.dd3boh.outertune.lyrics

import android.content.Context
import android.util.Log
import android.util.LruCache
import com.dd3boh.outertune.constants.LyricSourcePrefKey
import com.dd3boh.outertune.constants.LyricTrimKey
import com.dd3boh.outertune.constants.MultilineLrcKey
import com.dd3boh.outertune.constants.ShowRomanizationKey
import com.dd3boh.outertune.constants.ShowTranslationKey
import com.dd3boh.outertune.db.MusicDatabase
import com.dd3boh.outertune.db.entities.LyricsEntity
import com.dd3boh.outertune.models.MediaMetadata
import com.dd3boh.outertune.remote.RemoteMusicRepository
import com.dd3boh.outertune.utils.dataStore
import com.dd3boh.outertune.utils.get
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import org.akanework.gramophone.logic.utils.LrcUtils
import org.akanework.gramophone.logic.utils.SemanticLyrics
import org.akanework.gramophone.logic.utils.parseLrc
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject

class LyricsHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    val database: MusicDatabase,
    private val remoteRepository: RemoteMusicRepository,
) {
    private val cache = LruCache<String, List<LyricsResult>>(MAX_CACHE_SIZE)

    /** mediaIds whose remote lyric fetch is currently in flight, to avoid duplicate requests. */
    private val inFlight = ConcurrentHashMap.newKeySet<String>()

    /**
     * Retrieve lyrics from all sources
     *
     * How lyrics are resolved are determined by PreferLocalLyrics settings key. If this is true, prioritize local lyric
     * files over all cloud providers, true is vice versa.
     *
     * Lyrics stored in the database are fetched first. If this is not available, it is resolved by other means.
     * If local lyrics are preferred, lyrics from the lrc file is fetched, and then resolve by other means.
     *
     * @param mediaMetadata Song to fetch lyrics for
     * @param database MusicDatabase connection. Database lyrics are prioritized over all sources.
     * If no database is provided, the database source is disabled
     */
    suspend fun getLyrics(mediaMetadata: MediaMetadata): SemanticLyrics? {
        val trim = context.dataStore.get(LyricTrimKey, defaultValue = false)
        val multiline = context.dataStore.get(MultilineLrcKey, defaultValue = true)

        val prefLocal = context.dataStore.get(LyricSourcePrefKey, true)

        val cached = cache.get(mediaMetadata.id)?.firstOrNull()
        if (cached != null) {
            return parseLrc(cached.lyrics, trim, multiline)
        }
        val dbLyrics = database.lyrics(mediaMetadata.id).let { it.first()?.lyrics }
        // prefer database lyrics
        if (dbLyrics != null && !prefLocal) {
            return parseLrc(dbLyrics, trim, multiline)
        }

        // otherwise local lyrics are preferred over database
        val localLyrics: SemanticLyrics? =
            getLocalLyrics(mediaMetadata, LrcUtils.LrcParserOptions(trim, multiline, "Unable to parse lyrics"))
        if (localLyrics != null) {
            return localLyrics
        }
        if (dbLyrics != null) {
            return parseLrc(dbLyrics, trim, multiline)
        }

        // --- remote (online) lyrics -------------------------------------------------
        // No database / local lyric available. If this is an online song (mediaId starts with
        // "NM"), fetch lyrics from the backing source, merge translation/romanization, cache to DB.
        if (mediaMetadata.id.startsWith("NM")) {
            val remote = fetchAndCacheRemoteLyrics(mediaMetadata.id)
            if (!remote.isNullOrBlank()) {
                cache.put(mediaMetadata.id, listOf(LyricsResult("remote", remote)))
                return parseLrc(remote, trim, multiline)
            }
        }

        return null
    }

    /**
     * Fetch lyrics for [mediaId] from the remote source, merge translated/roman lines into a single
     * LRC document, and upsert into the lyrics table. Returns the merged LRC text (null on failure).
     */
    private suspend fun fetchAndCacheRemoteLyrics(mediaId: String): String? {
        if (!inFlight.add(mediaId)) return null // already being fetched elsewhere
        return try {
            val remoteLyric = runCatching { remoteRepository.getLyric(mediaId) }.getOrNull()
                ?: return null
            val base = remoteLyric.lyric ?: return null

            val showTrans = context.dataStore.get(ShowTranslationKey, true)
            val showRoman = context.dataStore.get(ShowRomanizationKey, false)

            val merged = mergeLrc(
                original = base,
                translated = if (showTrans) remoteLyric.translated else null,
                roman = if (showRoman) remoteLyric.roman else null,
            )

            runCatching {
                database.query { upsert(LyricsEntity(id = mediaId, lyrics = merged)) }
            }.onFailure { Log.w(TAG, "Failed to cache remote lyric for $mediaId", it) }

            merged
        } catch (e: Exception) {
            Log.w(TAG, "Remote lyric fetch failed for $mediaId", e)
            null
        } finally {
            inFlight.remove(mediaId)
        }
    }

    /**
     * Merge three LRC documents into one. Lines that share a timestamp with the original are
     * emitted as a second line at the SAME timestamp — the LRC parser treats identical-consecutive
     * timestamps as (original, translation) pairs. Romanization follows the same convention.
     */
    internal fun mergeLrc(original: String, translated: String?, roman: String?): String {
        val transMap = parseTimedMap(translated)
        val romanMap = parseTimedMap(roman)

        val out = StringBuilder()
        val lineRegex = Regex("""(\[(\d+):(\d+)(?:[.:](\d+))?\])""")
        for (rawLine in original.lines()) {
            val matches = lineRegex.findAll(rawLine).toList()
            if (matches.isEmpty()) {
                // header tags like [ar:], [ti:] — pass through unchanged
                out.appendLine(rawLine)
                continue
            }
            val text = rawLine.substring(matches.last().range.last + 1).trim()
            for (m in matches) {
                out.append(m.value).append(text).append('\n')
                val tsKey = timestampKey(m)
                // translation line at same timestamp
                transMap[tsKey]?.takeIf { it.isNotBlank() }?.let {
                    out.append(m.value).append(it).append('\n')
                }
                // romanization line at same timestamp
                romanMap[tsKey]?.takeIf { it.isNotBlank() }?.let {
                    out.append(m.value).append(it).append('\n')
                }
            }
        }
        return out.toString().trimEnd()
    }

    private fun timestampKey(m: MatchResult): String {
        val min = m.groupValues[2]
        val sec = m.groupValues[3]
        val frac = m.groupValues[4].padEnd(3, '0').take(3)
        return "$min:$sec.$frac"
    }

    /** Map of timestamp-key -> text for every timed line in [lrc]. Duplicate timestamps are joined. */
    private fun parseTimedMap(lrc: String?): Map<String, String> {
        if (lrc.isNullOrBlank()) return emptyMap()
        val lineRegex = Regex("""\[(\d+):(\d+)(?:[.:](\d+))?]""")
        val map = LinkedHashMap<String, String>()
        for (rawLine in lrc.lines()) {
            val ms = lineRegex.findAll(rawLine).toList()
            if (ms.isEmpty()) continue
            val text = rawLine.substring(ms.last().range.last + 1).trim()
            for (m in ms) {
                val frac = m.groupValues[3].padEnd(3, '0').take(3)
                val key = "${m.groupValues[1]}:${m.groupValues[2]}.$frac"
                map[key] = text
            }
        }
        return map
    }

    /**
     * Lookup lyrics from local disk (.lrc) file
     */
    private fun getLocalLyrics(
        mediaMetadata: MediaMetadata,
        parserOptions: LrcUtils.LrcParserOptions
    ): SemanticLyrics? {
        if (LocalLyricsProvider.isEnabled(context) && mediaMetadata.localPath != null) {
            return LocalLyricsProvider.getLyricsNew(
                mediaMetadata.localPath,
                parserOptions
            )
        }

        return null
    }

    companion object {
        private const val TAG = "LyricsHelper"
        private const val MAX_CACHE_SIZE = 3
    }
}

data class LyricsResult(
    val providerName: String,
    val lyrics: String,
)
