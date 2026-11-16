/**
 * Apprise Music (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.utils

import android.content.ContentUris
import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.request.Options
import coil3.size.Dimension
import okio.Buffer

/**
 * Loads album art for local Android MediaStore audio URIs offline without network requests
 * or temporary image files:
 *  1. Direct MediaStore albumart stream (`content://media/.../audio/albumart/<albumId>`)
 *  2. API 29+ sized thumbnail (`ContentResolver.loadThumbnail`) honoring Coil's target size
 *  3. Embedded ID3/FLAC/MP4 picture extracted via [MediaMetadataRetriever]
 */
class LocalAudioArtworkFetcher(
    private val context: Context,
    private val uri: coil3.Uri,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult? {
        val androidUri = Uri.parse(uri.toString())
        val targetPx = targetEdgePx(options.size)

        val path = androidUri.path.orEmpty()
        if (path.endsWith("/albumart") && path.contains("/audio/media/")) {
            val songUri = Uri.parse(uri.toString().removeSuffix("/albumart"))
            return fetchForSongUri(songUri, targetPx)
        }

        // 1. Fast MediaStore cached albumart stream (returned as SourceFetchResult so Coil
        // handles downsampling & DiskCache/MemoryCache automatically).
        val directStream = runCatching { context.contentResolver.openInputStream(androidUri) }.getOrNull()
        if (directStream != null) {
            return directStream.use { stream ->
                val bytes = stream.readBytes()
                if (bytes.isEmpty()) null else bytes.toSourceResult(options)
            }
        }

        // 2. Fallback for Android 10+ where legacy albumart content stream may be absent:
        // resolve a track belonging to this MediaStore album ID and inspect thumbnail / embedded tags.
        val albumId = runCatching { ContentUris.parseId(androidUri) }.getOrDefault(-1L)
        if (albumId > 0L) {
            val songUri = findFirstSongUriForAlbum(albumId) ?: return null
            return fetchForSongUri(songUri, targetPx)
        }

        return null
    }

    private fun fetchForSongUri(songUri: Uri, targetPx: Int): FetchResult? {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val thumb = runCatching {
                context.contentResolver.loadThumbnail(songUri, Size(targetPx, targetPx), null)
            }.getOrNull()
            if (thumb != null) {
                return ImageFetchResult(
                    image = thumb.asImage(),
                    isSampled = true,
                    dataSource = DataSource.DISK,
                )
            }
        }

        val embedded = extractEmbeddedPicture(songUri)
        if (embedded != null && embedded.isNotEmpty()) {
            return embedded.toSourceResult(options)
        }
        return null
    }

    private fun findFirstSongUriForAlbum(albumId: Long): Uri? {
        val projection = arrayOf(MediaStore.Audio.Media._ID)
        return runCatching {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection,
                "${MediaStore.Audio.Media.ALBUM_ID} = ?",
                arrayOf(albumId.toString()),
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)
                } else {
                    null
                }
            }
        }.getOrNull()
    }

    private fun extractEmbeddedPicture(songUri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, songUri)
            retriever.embeddedPicture
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    private fun ByteArray.toSourceResult(options: Options): SourceFetchResult =
        SourceFetchResult(
            source = ImageSource(
                source = Buffer().write(this),
                fileSystem = options.fileSystem,
            ),
            mimeType = null,
            dataSource = DataSource.DISK,
        )

    private fun targetEdgePx(size: coil3.size.Size): Int {
        val w = (size.width as? Dimension.Pixels)?.px ?: DEFAULT_TARGET_PX
        val h = (size.height as? Dimension.Pixels)?.px ?: DEFAULT_TARGET_PX
        return maxOf(w, h).coerceIn(MIN_TARGET_PX, MAX_TARGET_PX)
    }

    class Factory(private val context: Context) : Fetcher.Factory<coil3.Uri> {
        override fun create(data: coil3.Uri, options: Options, imageLoader: ImageLoader): Fetcher? {
            if (data.scheme != "content" || data.authority != "media") return null
            val path = data.path ?: return null
            if (!path.contains("/audio/")) return null
            return LocalAudioArtworkFetcher(context.applicationContext, data, options)
        }
    }

    private companion object {
        const val MIN_TARGET_PX = 128
        const val DEFAULT_TARGET_PX = 512
        const val MAX_TARGET_PX = 1024
    }
}
