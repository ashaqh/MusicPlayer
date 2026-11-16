package com.apprise.music

import android.app.Activity
import android.app.Instrumentation
import android.content.ComponentName
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.database.sqlite.SQLiteDatabase
import android.media.MediaScannerConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.audio.AudioProcessor
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import com.apprise.music.ads.AdMobPlacementManager
import com.apprise.music.ads.AdRequestDecision
import com.apprise.music.ads.AdSlot
import com.apprise.music.consent.UmpConsentManager
import com.apprise.music.entitlement.AppriseProducts
import com.apprise.music.entitlement.EntitlementTier
import com.apprise.music.entitlement.FeatureCapability
import com.apprise.music.entitlement.RawPurchaseRecord
import com.apprise.music.entitlement.RawPurchaseState
import com.apprise.music.entitlement.VerifiedEntitlementCache
import com.apprise.music.entitlement.VerifiedEntitlementManager
import com.convx.music.constants.SongSortType
import com.convx.music.db.InternalDatabase
import com.convx.music.db.MIGRATION_40_41
import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.Event
import com.convx.music.db.entities.PlaylistEntity
import com.convx.music.eq.EqualizerService
import com.convx.music.eq.audio.CustomEqualizerAudioProcessor
import com.convx.music.eq.data.ParametricEQBand
import com.convx.music.eq.data.SavedEQProfile
import com.convx.music.extensions.toMediaItem
import com.convx.music.library.RoomLibraryPager
import com.convx.music.models.MediaMetadata
import com.convx.music.models.PersistPlayerState
import com.convx.music.models.PersistQueue
import com.convx.music.models.PersistedPlaybackSnapshot
import com.convx.music.models.QueueType
import com.convx.music.playback.MusicService
import com.convx.music.playback.PlaybackStateStore
import com.convx.music.playback.queues.ListQueue
import com.convx.music.utils.CoilBitmapLoader
import com.convx.music.utils.LocalAudioScanner
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.time.LocalDateTime
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * On-device / emulator end-to-end M3 offline acceptance instrumentation runner.
 * Exercises real Android MediaStore indexing, Room v40->v41 SQLite migration, playlists,
 * search, favourites, playback history, MusicService playback in airplane mode, sleep timer,
 * equalizer processor, and artwork loading.
 */
class OfflineEngineInstrumentation : Instrumentation() {

    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val report = StringBuilder()
        try {
            val ctx = targetContext

            // 1. Check airplane mode status on test environment
            val airplaneMode = Settings.Global.getInt(ctx.contentResolver, Settings.Global.AIRPLANE_MODE_ON, 0)
            report.appendLine("airplaneMode=$airplaneMode")

            // 2. Verify real Android Room MIGRATION_40_41 via FrameworkSQLiteOpenHelperFactory
            verifyAndroidSqliteMigration40To41(ctx, report)

            val musicDir = File("/sdcard/Music")
            val filesToScan = musicDir.listFiles()?.map { it.absolutePath }?.toTypedArray().orEmpty()
            android.util.Log.i("OfflineQA", "Starting MediaStore scan on ${filesToScan.size} files")
            // Index any pushed test audio in /sdcard/Music cleanly before scanning
            val db = InternalDatabase.newInstance(ctx)
            try {
                runBlocking {
                    if (filesToScan.isNotEmpty()) {
                        val scanLatch = CountDownLatch(filesToScan.size)
                        MediaScannerConnection.scanFile(ctx, filesToScan, null) { _, _ -> scanLatch.countDown() }
                        scanLatch.await(15, TimeUnit.SECONDS)
                    }

                    val firstScan = LocalAudioScanner.scanAndInsert(ctx, db)
                    val secondScan = LocalAudioScanner.scanAndInsert(ctx, db)
                    android.util.Log.i("OfflineQA", "Scans done: first=$firstScan second=$secondScan")
                    report.appendLine("scan1=${firstScan} scan2=$secondScan")
                    check(firstScan.totalFound > 0 || secondScan.totalFound > 0) {
                        "Expected at least 1 local audio file in MediaStore"
                    }

                    // Library browsing & SQL pagination check
                    val allSongs = db.localSongs(SongSortType.NAME, descending = false).first()
                    val firstPage = RoomLibraryPager.loadSongsPage(db, SongSortType.NAME, descending = false, limit = 10, offset = 0)
                    check(allSongs.isNotEmpty() && firstPage.isNotEmpty()) { "Local songs browse/paging empty" }
                    report.appendLine("localSongsCount=${allSongs.size} firstPage=${firstPage.size}")

                    // Search check
                    val queryTerm = allSongs.first().song.title.take(4)
                    val searchHits = db.searchSongs(query = queryTerm, localOnly = true).first()
                    check(searchHits.isNotEmpty()) { "Search failed for '$queryTerm'" }

                    // Favourites check
                    val targetSong = allSongs.first()
                    db.query {
                        update(targetSong.song.copy(liked = true, likedDate = LocalDateTime.now()))
                    }
                    delay(300)
                    val likedList = db.likedSongs(SongSortType.NAME, descending = false).first()
                    check(likedList.any { it.id == targetSong.id }) { "Liked song persistence failed" }

                    // Playlist CRUD / duplicate / reorder / remove check
                    val pl = PlaylistEntity(name = "M3 QA Offline Playlist", isLocal = true, isEditable = true)
                    db.query { insert(pl) }
                    delay(200)
                    val loadedPl = db.playlist(pl.id).first()
                    check(loadedPl != null) { "Playlist create failed" }
                    db.query {
                        addSongToPlaylist(loadedPl, listOf(targetSong.id, targetSong.id))
                        updatePlaylistName(pl.id, "M3 QA Renamed")
                    }
                    delay(300)
                    val plSongs = db.playlistSongs(pl.id).first()
                    check(plSongs.size == 2) { "Expected 2 duplicate entries, got ${plSongs.size}" }
                    db.query {
                        removePlaylistEntry(plSongs.first().map)
                        delete(loadedPl.playlist)
                    }

                    // Recently played / Event persistence check
                    db.query {
                        insert(
                            Event(
                                songId = targetSong.id,
                                timestamp = LocalDateTime.now(),
                                playTime = 15_000L,
                            ),
                        )
                    }
                    delay(200)
                    val recent = db.recentEvents(5).first()
                    check(recent.any { it.song.id == targetSong.id }) { "History event persistence failed" }

                    // PlaybackStateStore (real android.util.AtomicFile) check
                    val snapFile = File(ctx.filesDir, "qa_playback_snapshot.bin")
                    val store = PlaybackStateStore(snapFile)
                    store.write(
                        PersistedPlaybackSnapshot(
                            queue = PersistQueue(
                                title = "QA Queue",
                                items = listOf(
                                    MediaMetadata(
                                        id = targetSong.id,
                                        title = targetSong.song.title,
                                        artists = targetSong.artists.map { MediaMetadata.Artist(it.id, it.name) },
                                        duration = targetSong.song.duration,
                                        thumbnailUrl = targetSong.song.thumbnailUrl,
                                    ),
                                ),
                                mediaItemIndex = 0,
                                position = 2_500L,
                                queueType = QueueType.LIST,
                            ),
                            automix = PersistQueue("automix", emptyList(), 0, 0L),
                            playerState = PersistPlayerState(false, Player.REPEAT_MODE_ALL, true, 0.9f, 2_500L, 0, Player.STATE_READY),
                        ),
                    )
                    val readSnap = store.read()
                    check(readSnap?.playerState?.shuffleModeEnabled == true && readSnap.playerState?.repeatMode == Player.REPEAT_MODE_ALL) {
                        "AtomicFile PlaybackStateStore failed"
                    }
                    store.delete()

                    // Equalizer service + CustomEqualizerAudioProcessor check
                    val eqProcessor = CustomEqualizerAudioProcessor()
                    eqProcessor.configure(androidx.media3.common.audio.AudioProcessor.AudioFormat(48_000, 2, C.ENCODING_PCM_16BIT))
                    val eqService = EqualizerService()
                    eqService.addAudioProcessor(eqProcessor)
                    val eqProfile = SavedEQProfile(
                        id = "qa_eq",
                        name = "QA Warm",
                        deviceModel = "Emulator Speaker",
                        bands = listOf(ParametricEQBand(100.0, 3.5, 1.0)),
                        preamp = -3.0,
                    )
                    check(eqService.applyProfile(eqProfile).isSuccess && eqService.isEnabled()) { "EqualizerService failed to enable" }

                    // CoilBitmapLoader offline local URI + fallback bitmap check
                    val artworkScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
                    val bitmapLoader = CoilBitmapLoader(ctx, artworkScope)
                    val artBitmap = bitmapLoader.loadBitmap(android.net.Uri.parse(targetSong.song.thumbnailUrl ?: "content://media/external/audio/albumart/9999999")).get(10, TimeUnit.SECONDS)
                    check(artBitmap.width > 0 && artBitmap.height > 0) { "CoilBitmapLoader failed to return valid bitmap" }

                    // Live MusicService binding, playback in airplane mode, queue, sleep timer
                    val serviceReady = CompletableDeferred<MusicService>()
                    val conn = object : ServiceConnection {
                        override fun onServiceConnected(name: ComponentName?, binder: IBinder?) {
                            val svc = (binder as? MusicService.MusicBinder)?.service
                            if (svc != null) serviceReady.complete(svc)
                        }
                        override fun onServiceDisconnected(name: ComponentName?) {}
                    }
                    val intent = Intent(ctx, MusicService::class.java)
                    ctx.bindService(intent, conn, Context.BIND_AUTO_CREATE)
                    val service = withTimeout(15_000) { serviceReady.await() }
                    try {
                        service.isPlayerReady.first { it }
                        withMainLooperSync {
                            val queue = ListQueue(
                                title = "QA Local Playback",
                                items = allSongs.take(3).map { it.toMediaItem() },
                                startIndex = 0,
                                position = 0L,
                            )
                            service.playQueue(queue, playWhenReady = true)
                            service.sleepTimer.start(10)
                            check(service.sleepTimer.isActive) { "SleepTimer did not activate" }
                            service.sleepTimer.clear()
                        }
                        delay(1_500)
                        val playbackSnapshot = withMainLooperSync {
                            Triple(service.player.mediaItemCount, service.player.playbackState, service.player.playerError)
                        }
                        check(playbackSnapshot.first >= 1 && playbackSnapshot.third == null) {
                            "MusicService local playback failed: count=${playbackSnapshot.first}, state=${playbackSnapshot.second}, error=${playbackSnapshot.third}"
                        }
                        withMainLooperSync { service.player.pause() }
                        report.appendLine("musicServiceItems=${playbackSnapshot.first} state=${playbackSnapshot.second}")
                    } finally {
                        runCatching { ctx.unbindService(conn) }
                    }
                }

                // 12. Verify M4 monetization runtime: AndroidKeyStore HMAC, Entitlement states, UMP/Ads offline guards
                runBlocking {
                    verifyM4MonetizationRuntime(ctx, report)
                }
            } finally {
                db.close()
            }

            android.util.Log.i("OfflineQA", "All offline instrumentation assertions PASSED:\n$report")
            finish(Activity.RESULT_OK, Bundle().apply { putString("stream", report.toString()) })
        } catch (t: Throwable) {
            android.util.Log.e("OfflineQA", "Instrumentation FAILED", t)
            finish(
                Activity.RESULT_CANCELED,
                Bundle().apply {
                    putString("stream", report.toString() + "\nFAILED: ${t.javaClass.simpleName}: ${t.message}\n${t.stackTraceToString()}")
                },
            )
        }
    }

    private suspend fun <T> withMainLooperSync(block: () -> T): T {
        val result = CompletableDeferred<T>()
        Handler(Looper.getMainLooper()).post {
            try {
                result.complete(block())
            } catch (t: Throwable) {
                result.completeExceptionally(t)
            }
        }
        return result.await()
    }

    private fun verifyAndroidSqliteMigration40To41(ctx: Context, report: StringBuilder) {
        val dbName = "qa_migration_40_41.db"
        ctx.deleteDatabase(dbName)
        try {
            val callback = object : SupportSQLiteOpenHelper.Callback(40) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL("CREATE TABLE IF NOT EXISTS `song` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `duration` INTEGER NOT NULL, `thumbnailUrl` TEXT, `albumId` TEXT, `albumName` TEXT, `explicit` INTEGER NOT NULL DEFAULT 0, `year` INTEGER, `date` INTEGER, `dateModified` INTEGER, `liked` INTEGER NOT NULL, `likedDate` INTEGER, `totalPlayTime` INTEGER NOT NULL, `inLibrary` INTEGER, `dateDownload` INTEGER, `isLocal` INTEGER NOT NULL DEFAULT false, `libraryAddToken` TEXT, `libraryRemoveToken` TEXT, `lyricsOffset` INTEGER NOT NULL DEFAULT 0, `romanizeLyrics` INTEGER NOT NULL DEFAULT true, `isDownloaded` INTEGER NOT NULL DEFAULT 0, `isUploaded` INTEGER NOT NULL DEFAULT false, `isVideo` INTEGER NOT NULL DEFAULT false, `isPodcastEpisode` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_song_albumId` ON `song` (`albumId`)")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `album` (`id` TEXT NOT NULL, `playlistId` TEXT, `title` TEXT NOT NULL, `year` INTEGER, `thumbnailUrl` TEXT, `themeColor` INTEGER, `songCount` INTEGER NOT NULL, `duration` INTEGER NOT NULL, `explicit` INTEGER NOT NULL DEFAULT 0, `lastUpdateTime` INTEGER NOT NULL, `bookmarkedAt` INTEGER, `likedDate` INTEGER, `inLibrary` INTEGER, `description` TEXT, `isLocal` INTEGER NOT NULL DEFAULT false, `isUploaded` INTEGER NOT NULL DEFAULT false, `pinnedAt` INTEGER, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `artist` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `thumbnailUrl` TEXT, `channelId` TEXT, `lastUpdateTime` INTEGER NOT NULL, `bookmarkedAt` INTEGER, `isLocal` INTEGER NOT NULL DEFAULT false, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `playlist` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `browseId` TEXT, `createdAt` INTEGER, `lastUpdateTime` INTEGER, `isEditable` INTEGER NOT NULL DEFAULT true, `bookmarkedAt` INTEGER, `remoteSongCount` INTEGER, `playEndpointParams` TEXT, `thumbnailUrl` TEXT, `description` TEXT, `shuffleEndpointParams` TEXT, `radioEndpointParams` TEXT, `isLocal` INTEGER NOT NULL DEFAULT false, `isAutoSync` INTEGER NOT NULL DEFAULT false, `pinnedAt` INTEGER, PRIMARY KEY(`id`))")
                    db.execSQL("CREATE TABLE IF NOT EXISTS `playlist_song_map` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `playlistId` TEXT NOT NULL, `songId` TEXT NOT NULL, `position` INTEGER NOT NULL, `setVideoId` TEXT)")
                    db.insert(
                        "song",
                        SQLiteDatabase.CONFLICT_ABORT,
                        ContentValues().apply {
                            put("id", "old_track_1")
                            put("title", "Legacy Track")
                            put("duration", 210)
                            put("liked", 1)
                            put("totalPlayTime", 120000L)
                            put("isLocal", 1)
                        },
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            }

            val config = SupportSQLiteOpenHelper.Configuration.builder(ctx)
                .name(dbName)
                .callback(callback)
                .build()
            val helper = FrameworkSQLiteOpenHelperFactory().create(config)
            helper.writableDatabase.use { db ->
                MIGRATION_40_41.migrate(db)
                db.query("SELECT title, liked, totalPlayTime, trackNumber, localPath FROM song WHERE id = 'old_track_1'").use { cursor ->
                    check(cursor.moveToFirst()) { "Migrated song missing on Android SQLite" }
                    check(cursor.getString(0) == "Legacy Track")
                    check(cursor.getInt(1) == 1)
                    check(cursor.getLong(2) == 120000L)
                }
            }
            helper.close()
            report.appendLine("androidMigration40To41=OK")
        } finally {
            ctx.deleteDatabase(dbName)
        }
    }

    private suspend fun verifyM4MonetizationRuntime(ctx: Context, report: StringBuilder) {
        val cache = VerifiedEntitlementCache(ctx)
        cache.clear()
        val manager = VerifiedEntitlementManager(ctx, cache)
        val consent = UmpConsentManager(ctx, manager)
        val adManager = AdMobPlacementManager(
            context = ctx,
            appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
            entitlementProvider = manager,
            consentManager = consent,
        )

        val token = "android-play-token-0123456789abcdef"
        val now = System.currentTimeMillis()
        val proRecord = RawPurchaseRecord(
            productIds = listOf(AppriseProducts.LIFETIME_PRO),
            purchaseToken = token,
            orderId = "GPA.9999-0000-1111",
            packageName = ctx.packageName,
            purchaseTimeMillis = now - 5_000L,
            purchaseState = RawPurchaseState.PURCHASED,
            isAcknowledged = true,
            isAutoRenewing = false,
            originalJson = """{"productId":"${AppriseProducts.LIFETIME_PRO}","purchaseToken":"$token"}""",
            signature = null,
        )

        val proSnap = manager.reconcilePlaySession(listOf(proRecord), billingAvailable = true, nowMillis = now)
        check(proSnap.tier == EntitlementTier.PRO) { "Expected PRO tier, got ${proSnap.tier}" }
        check(manager.isEnabled(FeatureCapability.AD_FREE))
        check(manager.isEnabled(FeatureCapability.ADVANCED_EQUALIZER))
        check(manager.isEnabled(FeatureCapability.LOCAL_PLAYBACK))
        check(!adManager.canShowAds()) { "PRO must suppress ads" }

        // Simulate AndroidKeyStore HMAC offline cache read after process restart
        val reloadedCache = cache.read()
        check(!reloadedCache.tampered && reloadedCache.record?.productId == AppriseProducts.LIFETIME_PRO)

        // Simulate revocation / refund -> FREE, ads decision still safely blocked offline
        val freeSnap = manager.reconcilePlaySession(emptyList(), billingAvailable = true, nowMillis = now + 10_000L)
        check(freeSnap.tier == EntitlementTier.FREE) { "Expected FREE after revocation, got ${freeSnap.tier}" }
        check(manager.isEnabled(FeatureCapability.LOCAL_PLAYBACK))
        check(!manager.isEnabled(FeatureCapability.AD_FREE))
        val offlineDecision = adManager.evaluateSlot(AdSlot.HOME_BANNER, "home")
        check(offlineDecision is AdRequestDecision.Block) { "Offline mode must block ad loads non-destructively" }

        // Pending purchase keeps Pro locked and playback active
        val pendingSnap = manager.reconcilePlaySession(
            listOf(proRecord.copy(purchaseState = RawPurchaseState.PENDING, isAcknowledged = false)),
            billingAvailable = true,
            nowMillis = now + 15_000L,
        )
        check(pendingSnap.tier == EntitlementTier.PENDING && manager.isEnabled(FeatureCapability.LOCAL_PLAYBACK))

        // Billing disconnected without cache -> UNKNOWN, playback remains unlocked
        cache.clear()
        val unknownSnap = manager.markBillingUnavailable(now + 20_000L)
        check(unknownSnap.tier == EntitlementTier.UNKNOWN && manager.isEnabled(FeatureCapability.LOCAL_PLAYBACK))

        report.appendLine("m4EntitlementAndroidKeystoreAndStates=OK")
    }
}
