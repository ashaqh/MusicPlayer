/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.playback.queues

import androidx.media3.common.MediaItem
import com.convx.music.db.entities.AlbumWithSongs
import com.convx.music.extensions.toMediaItem
import com.convx.music.models.MediaMetadata
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.withContext

class LocalAlbumRadio(
    private val albumWithSongs: AlbumWithSongs,
    private val startIndex: Int = 0,
    @Volatile private var playlistId: String? = null,
    @Volatile private var continuation: String? = null,
    @Volatile private var firstTimeLoaded: Boolean = false,
    private var restoredStatus: Queue.Status? = null,
) : Queue {
    override val preloadItem: MediaMetadata? = null

    override suspend fun getInitialStatus(): Queue.Status {
        restoredStatus?.let {
            restoredStatus = null
            return it
        }

        return withContext(IO) {
            Queue.Status(
                title = albumWithSongs.album.title,
                items = albumWithSongs.songs.map { it.toMediaItem() },
                mediaItemIndex = startIndex
            )
        }
    }

    override fun hasNextPage(): Boolean = false

    fun persistenceData() = QueueState(
        albumId = albumWithSongs.album.id,
        startIndex = startIndex,
        playlistId = playlistId,
        continuation = continuation,
        firstTimeLoaded = firstTimeLoaded,
    )

    override suspend fun nextPage(): List<MediaItem> = emptyList()

    data class QueueState(
        val albumId: String,
        val startIndex: Int,
        val playlistId: String?,
        val continuation: String?,
        val firstTimeLoaded: Boolean,
    )
}
