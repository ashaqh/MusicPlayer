/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.core.graphics.createBitmap
import androidx.media3.common.util.BitmapLoader
import coil3.imageLoader
import coil3.request.CachePolicy
import coil3.request.ErrorResult
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size as CoilSize
import coil3.toBitmap
import com.convx.music.R
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.future
import timber.log.Timber

/**
 * Bounded Media3 [BitmapLoader] backed by Coil's shared disk & memory cache,
 * ensuring notifications and lock-screen metadata decode offline without OOMs or blank tiles.
 */
class CoilBitmapLoader(
    private val context: Context,
    private val scope: CoroutineScope,
) : BitmapLoader {

    override fun supportsMimeType(mimeType: String): Boolean = mimeType.startsWith("image/")

    private fun createFallbackBitmap(): Bitmap {
        val size = NOTIFICATION_ARTWORK_MAX_PX
        val bitmap = createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(Color.rgb(24, 24, 28))
        val drawable = ContextCompat.getDrawable(context, R.drawable.apprise_logo)
        if (drawable != null) {
            val iconSize = (size * 0.42f).toInt()
            val inset = (size - iconSize) / 2
            drawable.setBounds(inset, inset, inset + iconSize, inset + iconSize)
            drawable.setTint(Color.argb(140, 255, 255, 255))
            drawable.draw(canvas)
        } else {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.DKGRAY }
            canvas.drawRect(0f, 0f, size.toFloat(), size.toFloat(), paint)
        }
        return bitmap
    }

    private fun Bitmap.copyIfNeeded(): Bitmap =
        if (isRecycled) {
            createFallbackBitmap()
        } else {
            try {
                copy(Bitmap.Config.ARGB_8888, false) ?: createFallbackBitmap()
            } catch (_: Exception) {
                createFallbackBitmap()
            }
        }

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            try {
                val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
                var sampleSize = 1
                while (
                    bounds.outWidth / sampleSize > NOTIFICATION_ARTWORK_MAX_PX ||
                    bounds.outHeight / sampleSize > NOTIFICATION_ARTWORK_MAX_PX
                ) {
                    sampleSize *= 2
                }
                val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
                val bitmap = BitmapFactory.decodeByteArray(data, 0, data.size, decodeOpts)
                bitmap?.copyIfNeeded() ?: createFallbackBitmap()
            } catch (e: Exception) {
                Timber.tag(TAG).w(e, "Failed to decode embedded bitmap data")
                createFallbackBitmap()
            }
        }

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        scope.future(Dispatchers.IO) {
            val scheme = uri.scheme?.lowercase()
            val isRemote = scheme == "http" || scheme == "https"
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(CoilSize(NOTIFICATION_ARTWORK_MAX_PX, NOTIFICATION_ARTWORK_MAX_PX))
                .allowHardware(false)
                .memoryCachePolicy(CachePolicy.ENABLED)
                .diskCachePolicy(CachePolicy.ENABLED)
                .networkCachePolicy(if (isRemote) CachePolicy.ENABLED else CachePolicy.DISABLED)
                .build()

            when (val result = context.imageLoader.execute(request)) {
                is ErrorResult -> createFallbackBitmap()
                is SuccessResult -> {
                    try {
                        result.image.toBitmap().copyIfNeeded()
                    } catch (e: Exception) {
                        Timber.tag(TAG).w(e, "Failed to convert image to bitmap")
                        createFallbackBitmap()
                    }
                }
            }
        }

    private companion object {
        const val TAG = "CoilBitmapLoader"
        const val NOTIFICATION_ARTWORK_MAX_PX = 512
    }
}
