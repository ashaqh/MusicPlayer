package com.convx.music.ui.utils

import org.junit.Assert.assertEquals
import org.junit.Test

/** Checks that local artwork URIs are preserved without remote CDN rewriting. */
class ThumbnailResizeTest {

    @Test
    fun `local content and file URIs pass through unchanged`() {
        val mediaStoreUri = "content://media/external/audio/albumart/42"
        val fileUri = "file:///storage/emulated/0/Music/cover.jpg"

        assertEquals(mediaStoreUri, mediaStoreUri.resize(544, 544))
        assertEquals(fileUri, fileUri.resize(1200, 1200))
        assertEquals(mediaStoreUri, mediaStoreUri.resize())
    }
}
