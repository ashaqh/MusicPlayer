package com.convx.music.library.scan

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.system.measureNanoTime

class LocalScanPlannerTest {

    @Test
    fun `extracts full metadata and falls back safely for missing tags`() {
        val raw = listOf(
            ScannedMediaStoreTrack(
                mediaStoreId = 101L,
                contentUri = "content://media/external/audio/media/101",
                title = "<unknown>",
                displayName = "03 - Midnight City.flac",
                artist = "",
                albumArtist = "<unknown>",
                album = null,
                mediaStoreAlbumId = -1L,
                durationMs = 243_600L,
                dateAddedSec = 1_700_000_000L,
                dateModifiedSec = 1_700_000_100L,
                fileSize = 28_400_123L,
                mimeType = "audio/flac",
                rawTrack = 2003,
                cdTrackNumber = null,
                discNumber = null,
                relativePath = "Music/Synthwave/",
                absolutePath = "/storage/emulated/0/Music/Synthwave/03 - Midnight City.flac",
                year = 2011,
            ),
            ScannedMediaStoreTrack(
                mediaStoreId = 102L,
                contentUri = "content://media/external/audio/media/102",
                title = "Track Two",
                displayName = "track2.mp3",
                artist = "Artist B",
                albumArtist = "Album Artist B",
                album = "Double Disc",
                mediaStoreAlbumId = 55L,
                durationMs = 180_000L,
                dateAddedSec = 1_700_000_200L,
                dateModifiedSec = 1_700_000_200L,
                fileSize = 4_500_000L,
                mimeType = "audio/mpeg",
                rawTrack = 12,
                cdTrackNumber = "7/14",
                discNumber = "2/2",
                relativePath = "Music/Album B/",
                absolutePath = "/storage/emulated/0/Music/Album B/track2.mp3",
                year = 2024,
            ),
        )

        val plan = LocalScanPlanner.plan(raw, emptyList())
        assertEquals(2, plan.tracksToInsert.size)

        val first = plan.tracksToInsert[0]
        assertEquals("03 - Midnight City", first.title)
        assertEquals("Unknown Artist", first.artistName)
        assertEquals("Synthwave", first.albumTitle)
        assertEquals(244, first.durationSec)
        assertEquals(2, first.discNumber)
        assertEquals(3, first.trackNumber)
        assertEquals(28_400_123L, first.fileSize)
        assertEquals("audio/flac", first.mimeType)
        assertEquals("content://media/external/audio/media/101/albumart", first.thumbnailUrl)

        val second = plan.tracksToInsert[1]
        assertEquals(7, second.trackNumber)
        assertEquals(2, second.discNumber)
        assertEquals("content://media/external/audio/albumart/55", second.thumbnailUrl)
        assertEquals("Album Artist B", second.albumArtistName)
    }

    @Test
    fun `disambiguates same album title across different artists`() {
        val trackA = ScannedMediaStoreTrack(
            mediaStoreId = 1L,
            contentUri = "content://media/external/audio/media/1",
            title = "Song A",
            displayName = "songA.mp3",
            artist = "Artist One",
            albumArtist = "Artist One",
            album = "Greatest Hits",
            mediaStoreAlbumId = 0L,
            durationMs = 200_000L,
            dateAddedSec = 100L,
            dateModifiedSec = 100L,
            fileSize = 5_000_000L,
            mimeType = "audio/mpeg",
            rawTrack = 1,
            cdTrackNumber = null,
            discNumber = null,
            relativePath = "Music/One/",
            absolutePath = "/storage/emulated/0/Music/One/songA.mp3",
        )
        val trackB = trackA.copy(
            mediaStoreId = 2L,
            contentUri = "content://media/external/audio/media/2",
            title = "Song B",
            displayName = "songB.mp3",
            artist = "Artist Two",
            albumArtist = "Artist Two",
            relativePath = "Music/Two/",
            absolutePath = "/storage/emulated/0/Music/Two/songB.mp3",
        )

        val plan = LocalScanPlanner.plan(listOf(trackA, trackB), emptyList())
        assertEquals(2, plan.tracksToInsert.size)
        assertNotEquals(plan.tracksToInsert[0].albumId, plan.tracksToInsert[1].albumId)
        assertEquals(2, plan.activeAlbums.size)
    }

    @Test
    fun `skips inaccessible, excluded, and duplicate tracks appropriately`() {
        val valid = sampleRawTrack(1L, "Track 1", "/storage/emulated/0/Music/Track1.mp3")
        val unreadable = sampleRawTrack(2L, "Broken", "/storage/emulated/0/Music/Broken.mp3").copy(isReadable = false)
        val zeroSize = sampleRawTrack(3L, "Zero", "/storage/emulated/0/Music/Zero.mp3").copy(fileSize = 0L)
        val excluded = sampleRawTrack(4L, "Voice Memo", "/storage/emulated/0/Recordings/Sub/Voice.m4a")
        val duplicateSameContent = sampleRawTrack(5L, "Track 1", "/storage/emulated/0/Download/Track1_copy.mp3")
        val distinctTrackSameTitle = sampleRawTrack(6L, "Track 1", "/storage/emulated/0/Music/Live/Track1.mp3")
            .copy(album = "Live Album", fileSize = 9_999_999L)

        val plan = LocalScanPlanner.plan(
            rawTracks = listOf(valid, unreadable, zeroSize, excluded, duplicateSameContent, distinctTrackSameTitle),
            existingSnapshots = emptyList(),
            excludedFolders = setOf("/storage/emulated/0/Recordings"),
        )

        assertEquals(2, plan.tracksToInsert.size)
        assertEquals(2, plan.inaccessibleCount)
        assertEquals(1, plan.excludedCount)
        assertEquals(1, plan.duplicatesSkipped)
    }

    @Test
    fun `detects incremental skip, modified files, moved files preserving stats, and removed files`() {
        val rawUnchanged = sampleRawTrack(10L, "Unchanged", "/storage/emulated/0/Music/u.flac")
        val rawModified = sampleRawTrack(11L, "Modified New", "/storage/emulated/0/Music/m.flac")
            .copy(dateModifiedSec = 2_000_000L, fileSize = 12_345_678L)
        val rawMovedNew = sampleRawTrack(99L, "Moved Song", "/storage/emulated/0/Music/NewFolder/moved.flac")
            .copy(dateModifiedSec = 1_500_000L, fileSize = 7_777_777L, durationMs = 210_000L)

        val normUnchanged = LocalScanPlanner.normalize(rawUnchanged)
        val snapshots = listOf(
            ExistingLocalSongSnapshot(
                id = normUnchanged.id,
                title = normUnchanged.title,
                durationSec = normUnchanged.durationSec,
                albumId = normUnchanged.albumId,
                albumName = normUnchanged.albumTitle,
                dateModified = normUnchanged.dateModified,
                inLibrary = normUnchanged.dateAdded,
                fileSize = normUnchanged.fileSize,
                mimeType = normUnchanged.mimeType,
                trackNumber = normUnchanged.trackNumber,
                discNumber = normUnchanged.discNumber,
                localPath = normUnchanged.localPath,
                liked = false,
                likedDate = null,
                totalPlayTime = 0L,
            ),
            ExistingLocalSongSnapshot(
                id = "content://media/external/audio/media/11",
                title = "Modified Old",
                durationSec = 200,
                albumId = "local_album_ms_1",
                albumName = "Album",
                dateModified = LocalDateTime.ofInstant(Instant.ofEpochSecond(1_000_000L), ZoneId.systemDefault()),
                inLibrary = LocalDateTime.ofInstant(Instant.ofEpochSecond(1_000_000L), ZoneId.systemDefault()),
                fileSize = 5_000_000L,
                mimeType = "audio/flac",
                trackNumber = 1,
                discNumber = 1,
                localPath = "/storage/emulated/0/Music/m.flac",
                liked = true,
                likedDate = LocalDateTime.now(),
                totalPlayTime = 420_000L,
            ),
            ExistingLocalSongSnapshot(
                id = "content://media/external/audio/media/12",
                title = "Moved Song",
                durationSec = 210,
                albumId = "local_album_ms_1",
                albumName = "Album",
                dateModified = LocalDateTime.ofInstant(Instant.ofEpochSecond(1_000_000L), ZoneId.systemDefault()),
                inLibrary = LocalDateTime.ofInstant(Instant.ofEpochSecond(900_000L), ZoneId.systemDefault()),
                fileSize = 7_777_777L,
                mimeType = "audio/flac",
                trackNumber = 2,
                discNumber = 1,
                localPath = "/storage/emulated/0/Download/moved.flac",
                liked = true,
                likedDate = LocalDateTime.now(),
                totalPlayTime = 999_000L,
            ),
            ExistingLocalSongSnapshot(
                id = "content://media/external/audio/media/13",
                title = "Deleted Song",
                durationSec = 180,
                albumId = "local_album_ms_1",
                albumName = "Album",
                dateModified = null,
                inLibrary = null,
                fileSize = 3_000_000L,
                mimeType = "audio/mpeg",
                trackNumber = null,
                discNumber = null,
                localPath = "/storage/emulated/0/Music/deleted.mp3",
                liked = false,
                likedDate = null,
                totalPlayTime = 0L,
            ),
        )

        val plan = LocalScanPlanner.plan(
            rawTracks = listOf(rawUnchanged, rawModified, rawMovedNew),
            existingSnapshots = snapshots,
        )

        assertEquals(1, plan.unchangedCount)
        assertEquals(0, plan.tracksToInsert.size)
        assertEquals(1, plan.tracksToUpdate.size)
        assertEquals("content://media/external/audio/media/11", plan.tracksToUpdate[0].track.id)
        assertTrue(plan.tracksToUpdate[0].previous.liked)

        assertEquals(1, plan.movedTracks.size)
        assertEquals("content://media/external/audio/media/12", plan.movedTracks[0].oldSongId)
        assertEquals("content://media/external/audio/media/99", plan.movedTracks[0].track.id)
        assertEquals(999_000L, plan.movedTracks[0].previous.totalPlayTime)

        assertEquals(listOf("content://media/external/audio/media/13"), plan.removedSongIds)
    }

    @Test
    fun `benchmarks planning performance across 100, 1000, and 10000 tracks`() {
        val counts = listOf(100, 1_000, 10_000)
        for (count in counts) {
            val tracks = List(count) { idx ->
                val albumIndex = (idx / 12) + 1
                sampleRawTrack(
                    id = idx + 1L,
                    title = "Track #${idx + 1}",
                    path = "/storage/emulated/0/Music/Album$albumIndex/track_${idx + 1}.flac",
                ).copy(
                    album = "Album $albumIndex",
                    mediaStoreAlbumId = albumIndex.toLong(),
                    artist = "Artist ${(albumIndex % 50) + 1}",
                    albumArtist = "Artist ${(albumIndex % 50) + 1}",
                    fileSize = 10_000_000L + idx,
                    rawTrack = (idx % 12) + 1,
                )
            }

            lateinit var initialPlan: LocalScanPlan
            val initialMs = measureNanoTime {
                initialPlan = LocalScanPlanner.plan(tracks, emptyList())
            } / 1_000_000.0

            val existingSnapshots = initialPlan.tracksToInsert.map { norm ->
                ExistingLocalSongSnapshot(
                    id = norm.id,
                    title = norm.title,
                    durationSec = norm.durationSec,
                    albumId = norm.albumId,
                    albumName = norm.albumTitle,
                    dateModified = norm.dateModified,
                    inLibrary = norm.dateAdded,
                    fileSize = norm.fileSize,
                    mimeType = norm.mimeType,
                    trackNumber = norm.trackNumber,
                    discNumber = norm.discNumber,
                    localPath = norm.localPath,
                    liked = false,
                    likedDate = null,
                    totalPlayTime = 0L,
                )
            }

            lateinit var incrementalPlan: LocalScanPlan
            val incrementalMs = measureNanoTime {
                incrementalPlan = LocalScanPlanner.plan(tracks, existingSnapshots)
            } / 1_000_000.0

            assertEquals(count, initialPlan.tracksToInsert.size)
            assertEquals(count, incrementalPlan.unchangedCount)
            assertEquals(0, incrementalPlan.tracksToInsert.size)
            assertEquals(0, incrementalPlan.tracksToUpdate.size)

            println(
                "[BENCHMARK][Planner] tracks=$count initialMs=%.2f incrementalNoOpMs=%.2f albums=%d"
                    .format(initialMs, incrementalMs, initialPlan.activeAlbums.size),
            )
            assertTrue("Initial plan for $count tracks took too long: $initialMs ms", initialMs < 10_000.0)
        }
    }

    private fun sampleRawTrack(
        id: Long,
        title: String,
        path: String,
    ) = ScannedMediaStoreTrack(
        mediaStoreId = id,
        contentUri = "content://media/external/audio/media/$id",
        title = title,
        displayName = path.substringAfterLast('/'),
        artist = "Sample Artist",
        albumArtist = "Sample Artist",
        album = "Sample Album",
        mediaStoreAlbumId = 42L,
        durationMs = 200_000L,
        dateAddedSec = 1_700_000_000L,
        dateModifiedSec = 1_700_000_000L,
        fileSize = 5_000_000L,
        mimeType = "audio/mpeg",
        rawTrack = 1,
        cdTrackNumber = null,
        discNumber = null,
        relativePath = path.substringBeforeLast('/') + "/",
        absolutePath = path,
        year = 2025,
        isReadable = true,
    )
}

