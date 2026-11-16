/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.db.entities

import androidx.compose.runtime.Immutable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDateTime
import kotlin.random.Random

@Immutable
@Entity(
    tableName = "playlist",
    indices = [
        Index(value = ["isLocal"]),
        Index(value = ["browseId"]),
        Index(value = ["name"]),
    ],
)
data class PlaylistEntity(
    @PrimaryKey val id: String = generatePlaylistId(),
    val name: String,
    val browseId: String? = null,
    val createdAt: LocalDateTime? = LocalDateTime.now(),
    val lastUpdateTime: LocalDateTime? = LocalDateTime.now(),
    @ColumnInfo(name = "isEditable", defaultValue = true.toString())
    val isEditable: Boolean = true,
    val bookmarkedAt: LocalDateTime? = null,
    val remoteSongCount: Int? = null,
    val playEndpointParams: String? = null,
    val thumbnailUrl: String? = null,
    val description: String? = null,
    val shuffleEndpointParams: String? = null,
    val radioEndpointParams: String? = null,
    @ColumnInfo(name = "isLocal", defaultValue = false.toString())
    val isLocal: Boolean = false,
    @ColumnInfo(name = "isAutoSync", defaultValue = false.toString())
    val isAutoSync: Boolean = false,
    /** Pin timestamp; newer pins sort first without a separate position column. */
    val pinnedAt: LocalDateTime? = null,
) {
    companion object {
        const val LIKED_PLAYLIST_ID = "LP_LIKED"
        const val DOWNLOADED_PLAYLIST_ID = "LP_DOWNLOADED"
        const val TOP_PLAYLIST_ID = "LP_TOP"
        const val CACHED_PLAYLIST_ID = "LP_CACHED"
        const val UPLOADED_PLAYLIST_ID = "LP_UPLOADED"
        const val LOCAL_PLAYLIST_ID = "LP_LOCAL"

        private const val CHAR_POOL = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
        fun generatePlaylistId() = "LP" + buildString(8) {
            repeat(8) { append(CHAR_POOL[Random.nextInt(CHAR_POOL.length)]) }
        }
    }

    val shareLink: String?
        get() = null

    fun localToggleLike() = copy(
        bookmarkedAt = if (bookmarkedAt != null) null else LocalDateTime.now()
    )

    fun toggleLike() = localToggleLike()

    fun togglePin() = copy(pinnedAt = if (pinnedAt != null) null else LocalDateTime.now())
}
