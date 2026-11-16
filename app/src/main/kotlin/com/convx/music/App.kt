/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.os.StrictMode
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.disk.directory
import coil3.gif.AnimatedImageDecoder
import coil3.gif.GifDecoder
import coil3.memory.MemoryCache
import coil3.request.CachePolicy
import coil3.request.allowHardware
import coil3.request.bitmapConfig
import coil3.request.crossfade
import coil3.svg.SvgDecoder
import com.convx.music.constants.LastFMSessionKey
import com.convx.music.constants.MaxImageCacheSizeKey
import com.convx.music.di.ApplicationScope
import com.convx.music.utils.CrashHandler
import com.convx.music.utils.DebugLogs
import com.convx.music.utils.dataStore
import com.convx.music.utils.migrateSharedPreferencesToMMKV
import com.convx.music.utils.warmPreferenceMirror
import com.music.lastfm.LastFM
import com.tencent.mmkv.MMKV
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltAndroidApp
class App : Application(), SingletonImageLoader.Factory {

    @Inject
    @ApplicationScope
    lateinit var applicationScope: CoroutineScope

    @Inject
    lateinit var networkRepository: dagger.Lazy<com.convx.storage.data.NetworkMusicRepository>

    override fun onCreate() {
        super.onCreate()
        // The recovery activity runs in its own process. Do not reopen the database,
        // preferences, native caches or playback integrations that may have crashed.
        val processName = if (Build.VERSION.SDK_INT >= 28) {
            Application.getProcessName()
        } else {
            (getSystemService(Context.ACTIVITY_SERVICE) as android.app.ActivityManager)
                .runningAppProcesses?.firstOrNull { it.pid == android.os.Process.myPid() }?.processName
        }
        if (processName == "$packageName:crash") return

        // StrictMode: debug-only, catches main-thread IO before it ships.
        // Release untouched — zero behavior change there.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder()
                    .detectDiskReads()
                    .detectDiskWrites()
                    .detectNetwork()
                    .detectCustomSlowCalls()
                    .penaltyLog()
                    .build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder()
                    .detectLeakedClosableObjects()
                    .detectActivityLeaks()
                    .penaltyLog()
                    .build()
            )
        }

        // Initialize MMKV before migrating preferences or restoring session state.
        MMKV.initialize(this)
        com.convx.music.ui.theme.colorengine.ColorEngine.init(this)
        // Avoid per-artwork diagnostic work in release builds.
        com.convx.music.ui.theme.colorengine.ColorEngine.setDebugEnabled(BuildConfig.DEBUG)
        applicationScope.launch(Dispatchers.IO) {
            migrateSharedPreferencesToMMKV(this@App, "widget_config")
            migrateSharedPreferencesToMMKV(this@App, "nanosonic_eq_profiles")
            migrateSharedPreferencesToMMKV(this@App, "vivi_eq_prefs")
            migrateSharedPreferencesToMMKV(this@App, "settings")
            dataStore.warmPreferenceMirror()
        }

        CrashHandler.install(this)

        if (BuildConfig.DEBUG) {
            Timber.plant(Timber.DebugTree())
            Timber.plant(DebugLogs.tree(this))
        }

        // Load the coil cache size on a background thread so the first image
        // render never blocks on a DataStore read.
        applicationScope.launch(Dispatchers.IO) {
            cachedCoilCacheSize = dataStore.data.map { it[MaxImageCacheSizeKey] ?: 512 }.first()
        }

        applicationScope.launch {
            initializeSettings()
            observeSettingsChanges()
        }
    }

    private fun initializeSettings() {
        LastFM.initialize(
            apiKey = BuildConfig.LASTFM_API_KEY.takeIf { it.isNotEmpty() } ?: "",
            secret = BuildConfig.LASTFM_SECRET.takeIf { it.isNotEmpty() } ?: ""
        )
    }

    private fun observeSettingsChanges() {
        applicationScope.launch(Dispatchers.IO) {
            dataStore.data
                .map { it[LastFMSessionKey] }
                .distinctUntilChanged()
                .collect { session ->
                    try {
                        LastFM.sessionKey = session
                    } catch (e: Exception) {
                        Timber.e("Error while loading last.fm session key. %s", e.message)
                    }
                }
        }
    }

    @Volatile
    private var cachedCoilCacheSize: Int? = null

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        // Use the startup-seeded value to avoid a synchronous DataStore read on Main.
        val cacheSize = cachedCoilCacheSize ?: 512
        return ImageLoader.Builder(this).apply {
            crossfade(false)
            // Use HARDWARE bitmap configuration for direct zero-copy GPU texture uploads
            allowHardware(true)
            bitmapConfig(Bitmap.Config.HARDWARE)
            // Animated GIF backgrounds (Home background picker allows GIF): the
            // platform decoder needs API 28+, GifDecoder covers everything below.
            components {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                    add(AnimatedImageDecoder.Factory())
                } else {
                    add(GifDecoder.Factory())
                }
                // User-supplied SVGs (player icons, DIY stickers). Vectors rasterise at the
                // size they are drawn at, so a custom icon never goes soft when scaled up.
                add(SvgDecoder.Factory())
                // Local device audio album art (MediaStore stream -> Q+ loadThumbnail -> embedded picture).
                add(com.convx.music.utils.LocalAudioArtworkFetcher.Factory(context))
                // Cover art that lives on an SMB or WebDAV source.
                add(com.convx.music.utils.NetworkArtworkFetcher.Factory { networkRepository.get() })
            }
            // Reuse decoded images while scrolling.
            memoryCache {
                // Limit memory use more on low-RAM devices.
                val activityManager = getSystemService(android.app.ActivityManager::class.java)
                val percent = if (activityManager?.isLowRamDevice == true) 0.15 else 0.25
                MemoryCache.Builder()
                    .maxSizePercent(context, percent)
                    .build()
            }
            if (cacheSize == 0) {
                diskCachePolicy(CachePolicy.DISABLED)
            } else {
                diskCache(
                    DiskCache.Builder()
                        // Keep artwork in cacheDir: Android can clear cacheDir under storage pressure,
                        // and it is automatically excluded from Google Drive cloud backups.
                        .directory(cacheDir.resolve("coil"))
                        .maxSizeBytes(cacheSize * 1024 * 1024L)
                        .build()
                )
            }
        }.build()
    }

    // Best-effort memory relief. Coil re-fetches from disk cache — no state lost.
    @Suppress("DEPRECATION")
    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= android.content.ComponentCallbacks2.TRIM_MEMORY_RUNNING_LOW) {
            runCatching { coil3.SingletonImageLoader.get(this).memoryCache?.clear() }
        }
    }
}
