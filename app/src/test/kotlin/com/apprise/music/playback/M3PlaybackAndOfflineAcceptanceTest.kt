package com.apprise.music.playback

import com.convx.music.extensions.toQueue
import com.convx.music.library.PagedCollectionController
import com.convx.music.models.MediaMetadata
import com.convx.music.models.PersistPlayerState
import com.convx.music.models.PersistQueue
import com.convx.music.models.PersistedPlaybackSnapshot
import com.convx.music.models.QueueType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.ObjectInputStream
import java.io.ObjectOutputStream

/**
 * Verifies M3 playback state serialization/restoration semantics, large-collection
 * paging controller behavior, and offline queue integrity on JVM.
 */
class M3PlaybackAndOfflineAcceptanceTest {

    @Test
    fun `persisted playback snapshot serializes and restores queue, shuffle, repeat, and position`() = runBlocking {
        val track1 = MediaMetadata(
            id = "content://media/external/audio/media/10",
            title = "Offline Song 1",
            artists = listOf(MediaMetadata.Artist(id = "art1", name = "Local Artist")),
            duration = 215,
            thumbnailUrl = "content://media/external/audio/albumart/3",
        )
        val track2 = track1.copy(
            id = "content://media/external/audio/media/11",
            title = "Offline Song 2",
            duration = 190,
        )

        val snapshot = PersistedPlaybackSnapshot(
            queue = PersistQueue(
                title = "All Local Songs",
                items = listOf(track1, track2),
                mediaItemIndex = 1,
                position = 84_320L,
                queueType = QueueType.LIST,
            ),
            automix = PersistQueue(
                title = "automix",
                items = emptyList(),
                mediaItemIndex = 0,
                position = 0L,
            ),
            playerState = PersistPlayerState(
                playWhenReady = false,
                repeatMode = 2, // REPEAT_MODE_ALL
                shuffleModeEnabled = true,
                volume = 0.85f,
                currentPosition = 84_320L,
                currentMediaItemIndex = 1,
                playbackState = 3,
            ),
        )

        val serializedBytes = ByteArrayOutputStream().use { baos ->
            ObjectOutputStream(baos).use { it.writeObject(snapshot) }
            baos.toByteArray()
        }

        val restored = ObjectInputStream(ByteArrayInputStream(serializedBytes)).use {
            it.readObject() as PersistedPlaybackSnapshot
        }

        assertNotNull(restored)
        assertEquals("All Local Songs", restored.queue?.title)
        assertEquals(2, restored.queue?.items?.size)
        assertEquals(1, restored.queue?.mediaItemIndex)
        assertEquals(84_320L, restored.queue?.position)
        assertEquals(2, restored.playerState?.repeatMode)
        assertTrue(restored.playerState?.shuffleModeEnabled == true)
        assertEquals(0.85f, restored.playerState!!.volume, 0.001f)

        val status = restored.queue!!.toQueue(database = null).getInitialStatus()
        assertEquals("All Local Songs", status.title)
        assertEquals(2, status.items.size)
        assertEquals(1, status.mediaItemIndex)
        assertEquals(84_320L, status.position)
        assertEquals("content://media/external/audio/media/11", status.items[1].mediaId)
    }

    @Test
    fun `paged collection controller incrementally loads large collections and handles end state`() = runBlocking {
        val backingData = List(550) { index -> "track_$index" }
        val controller = PagedCollectionController<String>(
            scope = this,
            pageSize = 200,
            keySelector = { it },
            pageLoader = { limit, offset ->
                if (offset >= backingData.size) emptyList()
                else backingData.subList(offset, minOf(offset + limit, backingData.size))
            },
        )

        controller.refresh()
        while (controller.state.value.isLoading) {
            kotlinx.coroutines.delay(5)
        }
        assertEquals(200, controller.state.value.items.size)
        assertFalse(controller.state.value.endReached)

        controller.loadNextPage()
        while (controller.state.value.isLoading) {
            kotlinx.coroutines.delay(5)
        }
        assertEquals(400, controller.state.value.items.size)
        assertFalse(controller.state.value.endReached)

        controller.loadNextPage()
        while (controller.state.value.isLoading) {
            kotlinx.coroutines.delay(5)
        }
        assertEquals(550, controller.state.value.items.size)
        assertTrue(controller.state.value.endReached)
    }
}
