/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.extensions

import com.convx.music.db.MusicDatabase
import com.convx.music.models.MediaMetadata
import com.convx.music.models.PersistQueue
import com.convx.music.models.QueueData
import com.convx.music.models.QueueType
import com.convx.music.playback.queues.ListQueue
import com.convx.music.playback.queues.LocalAlbumRadio
import com.convx.music.playback.queues.Queue
import kotlinx.coroutines.flow.first

fun Queue.toPersistQueue(
    title: String?,
    items: List<MediaMetadata>,
    mediaItemIndex: Int,
    position: Long,
): PersistQueue {
    val queueType: QueueType
    val queueData: QueueData?

    when (this) {
        is LocalAlbumRadio -> {
            val state = persistenceData()
            queueType = QueueType.LOCAL_ALBUM_RADIO
            queueData = QueueData.LocalAlbumRadioData(
                albumId = state.albumId,
                startIndex = state.startIndex,
                playlistId = state.playlistId,
                continuation = state.continuation,
                firstTimeLoaded = state.firstTimeLoaded,
            )
        }

        else -> {
            queueType = QueueType.LIST
            queueData = null
        }
    }

    return PersistQueue(
        title = title,
        items = items,
        mediaItemIndex = mediaItemIndex,
        position = position,
        queueType = queueType,
        queueData = queueData,
    )
}

suspend fun PersistQueue.toQueue(database: MusicDatabase? = null): Queue {
    val status = Queue.Status(
        title = title,
        items = items.map { it.toMediaItem() },
        mediaItemIndex = if (items.isEmpty()) 0 else mediaItemIndex.coerceIn(items.indices),
        position = position.coerceAtLeast(0L),
    )

    return when (queueType) {
        QueueType.LIST -> ListQueue(title, status.items, status.mediaItemIndex, status.position)

        QueueType.LOCAL_ALBUM_RADIO -> {
            val data = queueData as? QueueData.LocalAlbumRadioData
            val album = data?.takeUnless { it.albumId == "local_album_radio" }
                ?.let { database?.albumWithSongs(it.albumId)?.first() }
            if (data == null || album == null) {
                ListQueue(title, status.items, status.mediaItemIndex, status.position)
            } else {
                LocalAlbumRadio(
                    albumWithSongs = album,
                    startIndex = data.startIndex,
                    playlistId = data.playlistId,
                    continuation = data.continuation,
                    firstTimeLoaded = data.firstTimeLoaded,
                    restoredStatus = status,
                )
            }
        }
    }
}
