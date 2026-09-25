package com.dd3boh.outertune.playback.queues

import com.dd3boh.outertune.models.MediaMetadata

/**
 * A queue backed by remote search results / remote song lists.
 * Behaves like [ListQueue] but is explicitly typed as a remote queue so the
 * playback layer can distinguish origin if needed in the future.
 */
class RemoteQueue(
    val title: String?,
    private val items: List<MediaMetadata>,
    private val startIndex: Int = 0,
) : Queue {
    override val preloadItem: MediaMetadata? = items.getOrNull(startIndex)
    override val startShuffled: Boolean = false

    override suspend fun getInitialStatus() = Queue.Status(
        title = title,
        items = items,
        mediaItemIndex = startIndex,
        position = 0L,
    )

    override fun hasNextPage(): Boolean = false

    override suspend fun nextPage(): List<MediaMetadata> = emptyList()
}
