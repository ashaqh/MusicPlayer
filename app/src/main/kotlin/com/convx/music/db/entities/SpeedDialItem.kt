package com.convx.music.db.entities

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "speed_dial_item")
data class SpeedDialItem(
    @PrimaryKey val id: String,
    val secondaryId: String? = null,
    val title: String,
    val subtitle: String? = null,
    val thumbnailUrl: String? = null,
    val type: String, // "SONG", "ALBUM", "ARTIST", "PLAYLIST"
    val explicit: Boolean = false,
    val createDate: Long = System.currentTimeMillis()
)
