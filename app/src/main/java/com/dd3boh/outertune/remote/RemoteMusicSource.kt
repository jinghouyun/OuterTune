package com.dd3boh.outertune.remote

import com.dd3boh.outertune.models.MediaMetadata

/**
 * Normalized remote song result, independent of platform.
 * Maps directly to a [MediaMetadata] for playback / UI.
 */
data class RemoteSong(
    /** Global outer tune id, e.g. "NMwy123456". */
    val id: String,
    /** Platform source id: wy / mg / tx / kg / kw. */
    val source: String,
    /** Platform-internal song id. */
    val sourceSongId: String,
    val title: String,
    val artists: List<String>,
    val albumName: String?,
    val durationSec: Int,
    val thumbnailUrl: String?,
    /** Source-specific extra data (e.g. mg copyrightId, wy hash) used when resolving the stream. */
    val extra: Map<String, String> = emptyMap(),
) {
    fun toMediaMetadata(): MediaMetadata = MediaMetadata(
        id = id,
        title = title,
        artists = artists.map { MediaMetadata.Artist(id = null, name = it) },
        duration = durationSec,
        thumbnailUrl = thumbnailUrl,
        album = albumName?.let { MediaMetadata.Album(id = "${source}_album_$sourceSongId", title = it) },
        genre = null,
        isLocal = false,
        localPath = null,
    )
}

data class RemoteLyric(
    val lyric: String?,
    val translated: String? = null,
    val roman: String? = null,
)

/**
 * One remote music source (wy/mg/tx/kg/kw). All methods are blocking and must be
 * called from a background thread (the repository wraps them in withContext).
 */
interface RemoteMusicSource {
    val sourceId: String
    val displayName: String

    /** Whether this source provides a vocal-separation endpoint. Built-ins do not. */
    val supportsSeparation: Boolean
        get() = false

    fun search(query: String, page: Int = 1, limit: Int = 30): List<RemoteSong>

    /** Resolve a playable direct stream URL for [song]. Returns null if unavailable. */
    fun resolveStreamUrl(song: RemoteSong, quality: String = "128k"): String?

    /** Fetch lyrics for [song]. */
    fun getLyric(song: RemoteSong): RemoteLyric?
}
