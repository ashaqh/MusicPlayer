package com.apprise.music.playback

import androidx.media3.common.MediaItem
import com.convx.music.playback.QueueBuilder
import com.convx.music.playback.queues.EmptyQueue
import com.convx.music.playback.queues.ListQueue
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Verifies core local playback queue structures remain operational after M1 rebranding and build updates.
 */
class LocalPlaybackSmokeTest {

    @Test
    fun `list queue preserves local media item ids and start position`() = runBlocking {
        val first = MediaItem.Builder().setMediaId("content://media/external/audio/media/101").build()
        val second = MediaItem.Builder().setMediaId("file:///storage/emulated/0/Music/track.flac").build()

        val queue = ListQueue(
            title = "Local Library",
            items = listOf(first, second),
            startIndex = 1,
            position = 42_500L,
        )

        val status = queue.getInitialStatus()
        assertEquals("Local Library", status.title)
        assertEquals(2, status.items.size)
        assertEquals("content://media/external/audio/media/101", status.items[0].mediaId)
        assertEquals("file:///storage/emulated/0/Music/track.flac", status.items[1].mediaId)
        assertEquals(1, status.mediaItemIndex)
        assertEquals(42_500L, status.position)
        assertFalse(queue.hasNextPage())
    }

    @Test
    fun `empty queue returns empty initial status without paging`() = runBlocking {
        val status = EmptyQueue.getInitialStatus()
        assertNull(status.title)
        assertEquals(0, status.items.size)
        assertEquals(-1, status.mediaItemIndex)
        assertFalse(EmptyQueue.hasNextPage())
    }

    @Test
    fun `queue builder extends candidates without duplicating existing tracks`() {
        val existing = listOf(com.convx.music.db.entities.SongEntity(id = "local-1", title = "Song 1"))
        val candidates = listOf(
            com.convx.music.db.entities.SongEntity(id = "local-1", title = "Song 1"),
            com.convx.music.db.entities.SongEntity(id = "local-2", title = "Song 2"),
            com.convx.music.db.entities.SongEntity(id = "local-3", title = "Song 3"),
        )

        val added = QueueBuilder.extend(existing, candidates, limit = 5)
        assertEquals(listOf("local-2", "local-3"), added.map { it.id })
    }
}

