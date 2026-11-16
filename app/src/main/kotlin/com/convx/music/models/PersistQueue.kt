/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.models

import androidx.compose.runtime.Immutable
import androidx.compose.runtime.Stable
import java.io.Serializable

@Immutable
data class PersistQueue(
    val title: String?,
    val items: List<MediaMetadata>,
    val mediaItemIndex: Int,
    val position: Long,
    val queueType: QueueType = QueueType.LIST,
    val queueData: QueueData? = null,
) : Serializable

@Immutable
sealed class QueueType : Serializable {
    object LIST : QueueType() {
        private fun readResolve(): Any = LIST
    }

    object LOCAL_ALBUM_RADIO : QueueType() {
        private fun readResolve(): Any = LOCAL_ALBUM_RADIO
    }
}

@Stable
sealed class QueueData : Serializable {
    data class LocalAlbumRadioData(
        val albumId: String,
        val startIndex: Int = 0,
        val playlistId: String? = null,
        val continuation: String? = null,
        val firstTimeLoaded: Boolean = false
    ) : QueueData()
}
