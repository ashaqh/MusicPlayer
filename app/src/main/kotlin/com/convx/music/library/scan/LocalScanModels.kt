/**
 * Apprise Music (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.library.scan

import java.time.LocalDateTime

/** Raw MediaStore (or test double) metadata row extracted for one local audio item. */
data class ScannedMediaStoreTrack(
    val mediaStoreId: Long,
    val contentUri: String,
    val title: String?,
    val displayName: String?,
    val artist: String?,
    val albumArtist: String?,
    val album: String?,
    val mediaStoreAlbumId: Long,
    val durationMs: Long,
    val dateAddedSec: Long,
    val dateModifiedSec: Long,
    val fileSize: Long,
    val mimeType: String?,
    val rawTrack: Int?,
    val cdTrackNumber: String?,
    val discNumber: String?,
    val relativePath: String?,
    val absolutePath: String?,
    val year: Int? = null,
    val isReadable: Boolean = true,
)

/** Lightweight snapshot of an existing local song in Room used for fast incremental diffing. */
data class ExistingLocalSongSnapshot(
    val id: String,
    val title: String,
    val durationSec: Int,
    val albumId: String?,
    val albumName: String?,
    val dateModified: LocalDateTime?,
    val inLibrary: LocalDateTime?,
    val fileSize: Long?,
    val mimeType: String?,
    val trackNumber: Int?,
    val discNumber: Int?,
    val localPath: String?,
    val liked: Boolean,
    val likedDate: LocalDateTime?,
    val totalPlayTime: Long,
)

/** Normalized, deduplicated local track ready for Room persistence. */
data class NormalizedLocalTrack(
    val id: String,
    val title: String,
    val artistName: String,
    val artistId: String,
    val albumArtistName: String,
    val albumArtistId: String,
    val albumTitle: String,
    val albumId: String,
    val durationSec: Int,
    val dateAdded: LocalDateTime,
    val dateModified: LocalDateTime,
    val trackNumber: Int?,
    val discNumber: Int?,
    val fileSize: Long,
    val mimeType: String,
    val localPath: String,
    val folderPath: String,
    val thumbnailUrl: String,
    val year: Int?,
)

/** Remapping instruction when an existing song was moved/renamed on storage. */
data class MovedLocalTrack(
    val oldSongId: String,
    val track: NormalizedLocalTrack,
    val previous: ExistingLocalSongSnapshot,
)

/** Updating instruction when an existing track's file metadata or tags changed. */
data class UpdatedLocalTrack(
    val track: NormalizedLocalTrack,
    val previous: ExistingLocalSongSnapshot,
)

/** Recomputed album metadata aggregate from active local tracks. */
data class AlbumAggregate(
    val albumId: String,
    val title: String,
    val artistId: String,
    val artistName: String,
    val thumbnailUrl: String,
    val songCount: Int,
    val totalDurationSec: Int,
    val year: Int?,
)

/** Complete incremental diff plan computed off the database & main thread. */
data class LocalScanPlan(
    val totalRawRows: Int,
    val tracksToInsert: List<NormalizedLocalTrack>,
    val tracksToUpdate: List<UpdatedLocalTrack>,
    val movedTracks: List<MovedLocalTrack>,
    val removedSongIds: List<String>,
    val unchangedCount: Int,
    val inaccessibleCount: Int,
    val duplicatesSkipped: Int,
    val excludedCount: Int,
    val activeAlbums: List<AlbumAggregate>,
)

enum class LocalScanPhase {
    IDLE,
    QUERYING_MEDIASTORE,
    PLANNING_CHANGES,
    WRITING_DATABASE,
    COMPLETED,
    CANCELLED,
    FAILED,
}

data class LocalScanProgress(
    val phase: LocalScanPhase = LocalScanPhase.IDLE,
    val processed: Int = 0,
    val total: Int = 0,
    val added: Int = 0,
    val updated: Int = 0,
    val moved: Int = 0,
    val removed: Int = 0,
    val skippedUnchanged: Int = 0,
    val skippedInaccessible: Int = 0,
    val skippedDuplicates: Int = 0,
    val errorMessage: String? = null,
) {
    val isScanning: Boolean
        get() = phase == LocalScanPhase.QUERYING_MEDIASTORE ||
            phase == LocalScanPhase.PLANNING_CHANGES ||
            phase == LocalScanPhase.WRITING_DATABASE

    val fraction: Float
        get() = if (total > 0) (processed.toFloat() / total.toFloat()).coerceIn(0f, 1f) else 0f
}

