/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

@file:Suppress("DEPRECATION")

package com.convx.music.playback

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.database.SQLException
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.media.audiofx.LoudnessEnhancer
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.Binder
import android.os.Build
import android.os.SystemClock

import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.Player.EVENT_POSITION_DISCONTINUITY
import com.convx.music.playback.smart.TrackAnalyzer
import com.convx.music.playback.smart.TrackAnalysis
import com.convx.music.data.settings.AppSettings
import androidx.media3.common.Player.EVENT_TIMELINE_CHANGED
import androidx.media3.common.Player.REPEAT_MODE_ALL
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.REPEAT_MODE_ONE
import androidx.media3.common.Player.STATE_IDLE
import androidx.media3.common.Timeline
import androidx.media3.common.audio.SonicAudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.analytics.AnalyticsListener
import androidx.media3.exoplayer.analytics.PlaybackStats
import androidx.media3.exoplayer.analytics.PlaybackStatsListener
import androidx.media3.exoplayer.audio.DefaultAudioSink
import androidx.media3.exoplayer.audio.SilenceSkippingAudioProcessor
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.source.ShuffleOrder.DefaultShuffleOrder
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaController
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.music.lastfm.LastFM
import com.convx.music.MainActivity
import com.convx.music.R
import com.convx.music.lyrics.LyricsSyncLevel
import com.convx.music.lyrics.LyricsWithProvider
import com.convx.music.lyrics.LyricsProviderRegistry
import com.convx.music.lyrics.LyricsProviderSettings
import com.convx.music.constants.LyricsProviderOrderKey
import com.convx.music.constants.AudioNormalizationKey
import com.convx.music.constants.AudioOffload
import com.convx.music.constants.AutoLoadMoreKey
import com.convx.music.constants.AutoSkipNextOnErrorKey
import com.convx.music.constants.AutoDjMixingEnabledKey
import com.convx.music.constants.CreativeTransitionsEnabledKey
import com.convx.music.constants.DjReverbTypeKey
import com.convx.music.constants.DjDelayTypeKey
import com.convx.music.constants.CrossfadeDurationKey
import com.convx.music.constants.CrossfadeEnabledKey
import com.convx.music.constants.DataSaverEnabledKey
import com.convx.music.constants.DisableLoadMoreWhenRepeatAllKey
import com.convx.music.constants.EnableLastFMScrobblingKey
import com.convx.music.constants.ListenBrainzEnabledKey
import com.convx.music.constants.ListenBrainzTokenKey
import com.convx.music.constants.HistoryDuration
import com.convx.music.constants.LastFMUseNowPlaying
import com.convx.music.constants.LastFMUseSendLikes
import com.convx.music.constants.MediaSessionConstants.CommandToggleLike
import com.convx.music.constants.MediaSessionConstants.CommandToggleRepeatMode
import com.convx.music.constants.MediaSessionConstants.CommandToggleShuffle
import com.convx.music.constants.MediaSessionConstants.CommandToggleStartRadio
import com.convx.music.constants.PauseListenHistoryKey
import com.convx.music.constants.PauseOnMute
import com.convx.music.constants.PersistentQueueKey
import com.convx.music.constants.PersistentShuffleAcrossQueuesKey
import com.convx.music.constants.PlayerVolumeKey
import com.convx.music.constants.RememberShuffleAndRepeatKey
import com.convx.music.constants.RepeatModeKey
import com.convx.music.constants.ResumeOnBluetoothConnectKey
import com.convx.music.constants.ScrobbleDelayPercentKey
import com.convx.music.constants.ScrobbleDelaySecondsKey
import com.convx.music.constants.ScrobbleMinSongDurationKey
import com.convx.music.constants.ShuffleModeKey
import com.convx.music.constants.ShufflePlaylistFirstKey
import com.convx.music.constants.PreventDuplicateTracksInQueueKey
import com.convx.music.constants.SkipSilenceInstantKey
import com.convx.music.constants.BitPerfectKey
import com.convx.music.constants.SpatialAudioKey
import com.convx.music.constants.VocalSuppressionKey
import com.convx.music.constants.SkipSilenceKey
import com.convx.music.db.MusicDatabase
import com.convx.music.db.entities.Event
import com.convx.music.db.entities.LyricsEntity
import com.convx.music.db.entities.Song
import com.convx.music.eq.EqualizerService
import com.convx.music.playback.audio.BitPerfectAudio
import io.github.anilbeesetti.nextlib.media3ext.ffdecoder.NextRenderersFactory
import com.convx.music.eq.audio.CustomEqualizerAudioProcessor
import com.convx.music.eq.data.EQProfileRepository
import com.convx.music.extensions.SilentHandler
import com.convx.music.extensions.collect
import com.convx.music.extensions.collectLatest
import com.convx.music.extensions.currentMetadata
import com.convx.music.extensions.findNextMediaItemById
import com.convx.music.extensions.mediaItems
import com.convx.music.extensions.metadata
import com.convx.music.extensions.setOffloadEnabled
import com.convx.music.extensions.toEnum
import com.convx.music.extensions.toMediaItem
import com.convx.music.extensions.toPersistQueue
import com.convx.music.extensions.toQueue
import com.convx.music.lyrics.LyricsHelper
import com.convx.music.models.MediaMetadata
import com.convx.music.models.PersistedPlaybackSnapshot
import com.convx.music.models.PersistPlayerState
import com.convx.music.models.PersistQueue
import com.convx.music.playback.audio.TrackAnalyzerAudioProcessor
import com.convx.music.playback.dj.BeatGrid
import com.convx.music.playback.dj.DjEngine
import com.convx.music.playback.audio.DelayAudioProcessor
import com.convx.music.playback.audio.DjFilterAudioProcessor
import com.convx.music.playback.audio.DjTailAudioProcessor
import com.convx.music.playback.audio.DjAmbienceAudioProcessor
import com.convx.music.playback.audio.ReverbType
import com.convx.music.playback.audio.DelayType
import com.convx.music.playback.audio.DjMixPlan
import com.convx.music.playback.audio.DjMixTier
import com.convx.music.playback.audio.SilenceDetectorAudioProcessor
import com.convx.music.playback.audio.SpatialAudioProcessor
import com.convx.music.playback.audio.VocalSuppressionAudioProcessor
import com.convx.music.playback.queues.EmptyQueue
import com.convx.music.playback.queues.Queue
import com.convx.music.utils.CoilBitmapLoader
import com.convx.music.utils.NetworkConnectivityObserver
import com.convx.music.utils.ScrobbleManager
import com.convx.music.constants.StopMusicOnTaskClearKey
import com.convx.music.utils.dataStore
import com.convx.music.utils.get
import com.convx.music.utils.reportException
import com.convx.music.widget.ConvxWidgetManager
import com.convx.music.widget.LyricsWidgetReceiver
import com.convx.music.widget.MusicWidgetReceiver
import com.convx.music.widget.NowPlayingWidgetReceiver
import dagger.hilt.android.AndroidEntryPoint
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import android.os.PowerManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.distinctUntilChangedBy
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.plus
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.ObjectInputStream
import java.time.LocalDateTime
import javax.inject.Inject
import kotlin.coroutines.coroutineContext
import kotlin.time.Duration.Companion.seconds

/** Routing order for [MusicService.currentOutputKey]: first connected one wins. 26 = TYPE_BLE_HEADSET (API 31). */
private val OutputPriority = listOf(
    26,
    android.media.AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
    android.media.AudioDeviceInfo.TYPE_USB_HEADSET,
    android.media.AudioDeviceInfo.TYPE_USB_DEVICE,
    android.media.AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    android.media.AudioDeviceInfo.TYPE_WIRED_HEADSET,
)
private const val INSTANT_SILENCE_SKIP_STEP_MS = 15_000L
private const val DEFAULT_CROSSFADE_SECONDS = 5f
private const val INSTANT_SILENCE_SKIP_SETTLE_MS = 350L

// Throttle repeated writes triggered by frequent playback-stat reports.
private const val STATS_WRITE_INTERVAL_MS = 20_000L

/** How far ahead in play order the queue must run dry before the next page is fetched. */
private const val LOAD_MORE_LOOKAHEAD = 5

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@androidx.annotation.OptIn(UnstableApi::class)
@AndroidEntryPoint
class MusicService :
    MediaLibraryService(),
    Player.Listener,
    PlaybackStatsListener.Callback {
    @Inject
    lateinit var database: MusicDatabase

    @Inject
    lateinit var lyricsHelper: LyricsHelper

    @Inject
    lateinit var networkRepository: com.convx.storage.data.NetworkMusicRepository

    @Inject
    lateinit var mediaLibrarySessionCallback: MediaLibrarySessionCallback

    @Inject
    lateinit var equalizerService: EqualizerService

    @Inject
    lateinit var eqProfileRepository: EQProfileRepository

    @Inject
    lateinit var widgetManager: ConvxWidgetManager

    @Inject
    lateinit var entitlementProvider: com.apprise.music.entitlement.EntitlementProvider

    private lateinit var audioManager: AudioManager
    private var wifiLock: android.net.wifi.WifiManager.WifiLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var wasPlayingBeforeAudioFocusLoss = false
    private var hasAudioFocus = false
    private var pendingFocusStart = false
    private var isPausingForAudioFocus = false
    private var audioFocusRequestOutstanding = false
    private var focusResumeJob: Job? = null
    private var wasPlayingBeforeVolumeMute = false
    private var isPausedByVolumeMute = false
    private val MIN_AUDIBLE_VOLUME = 0.05f
    private val VOLUME_WATCHDOG_MS = 2_000L
    var preferredDeviceId: Int? = null
        private set

    private var cachedSkipSilence = false
    private var cachedSkipSilenceInstant = false
    private var cachedBitPerfect = false
    private var cachedOffloadEnabled = false
    private var cachedSpatialAudio = false
    private var cachedVocalSuppression = 0f

    private var crossfade: CrossfadeController? = null
    private var trackAnalyzer: TrackAnalyzer? = null

    private var sparePlayer: ExoPlayer? = null
    // Keep sibling service tasks alive if one coroutine fails.
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Serialize persistence writes and keep file I/O off the main thread.
    private val persistenceScope =
        CoroutineScope(SupervisorJob() + Dispatchers.IO.limitedParallelism(1))

    // Last database write time per media item, used to throttle stats updates.
    private val lastStatsWriteAt = HashMap<String, Long>()

    private val binder = MusicBinder()

    /** App UI connections via [MusicBinder]; see [scheduleIdleStop]. */
    private var uiClients = 0
    private var idleStopJob: Job? = null

    inner class MusicBinder : Binder() {
        val service: MusicService
            get() = this@MusicService
    }

    private lateinit var connectivityManager: ConnectivityManager
    lateinit var connectivityObserver: NetworkConnectivityObserver
    val waitingForNetworkConnection = MutableStateFlow(false)
    private val isNetworkConnected = MutableStateFlow(false)

    private var currentQueue: Queue = EmptyQueue
    private var queueRequestVersion = 0L
    var queueTitle: String? = null

    val currentMediaMetadata = MutableStateFlow<com.convx.music.models.MediaMetadata?>(null)
    private val currentSong =
        currentMediaMetadata
            .flatMapLatest { mediaMetadata ->
                database.song(mediaMetadata?.id)
            }.stateIn(scope, SharingStarted.Lazily, null)
    private val currentFormat =
        currentMediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    lateinit var playerVolume: MutableStateFlow<Float>
    val isMuted = MutableStateFlow(false)

    fun toggleMute() {
        val newMutedState = !isMuted.value
        isMuted.value = newMutedState
        player.volume = if (newMutedState) 0f else playerVolume.value
    }

    fun setMuted(muted: Boolean) {
        isMuted.value = muted
        player.volume = if (muted) 0f else playerVolume.value
    }

    fun setPreferredAudioDevice(deviceId: Int?) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val devices = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
            val deviceInfo = devices.find { it.id == deviceId }
            player.setPreferredAudioDevice(deviceInfo)
            preferredDeviceId = deviceId
        }
    }

    lateinit var sleepTimer: SleepTimer

    lateinit var player: ExoPlayer
        private set
    private var duckVolumeRestoreJob: Job? = null

    private lateinit var mediaSession: MediaLibrarySession

    private val playerInitialized = MutableStateFlow(false)
    val isPlayerReady: kotlinx.coroutines.flow.StateFlow<Boolean> = playerInitialized.asStateFlow()

    private val _playerFlow = MutableStateFlow<ExoPlayer?>(null)
    val playerFlow = _playerFlow.asStateFlow()

    // Lets the UI match its artwork fade to the audio fade; zero selects the default.
    private val _crossfadeDurationMs = MutableStateFlow(0L)
    val crossfadeDurationMs = _crossfadeDurationMs.asStateFlow()

    private val playerSilenceProcessors = HashMap<Player, SilenceDetectorAudioProcessor>()
    private val playerEqualizers = HashMap<Player, CustomEqualizerAudioProcessor>()
    private val playerSpatialProcessors = HashMap<Player, SpatialAudioProcessor>()
    private val playerVocalSuppressionProcessors = HashMap<Player, VocalSuppressionAudioProcessor>()

    private val instantSilenceSkipEnabled = MutableStateFlow(false)

    private var isAudioEffectSessionOpened = false
    private var loudnessEnhancer: LoudnessEnhancer? = null

    private var scrobbleManager: ScrobbleManager? = null

    private var listenBrainzEnabled = false
    private var listenBrainzToken = ""
    private var listenBrainzCurrentMediaId: String? = null
    private var listenBrainzCurrentStartTs: Long = 0L

    val automixItems = MutableStateFlow<List<MediaItem>>(emptyList())

    // Separates user-queued items from items added automatically.
    private var originalQueueSize: Int = 0

    private var consecutivePlaybackErr = 0
    private var retryJob: Job? = null
    private var retryCount = 0
    private var silenceSkipJob: Job? = null

    private var currentMediaIdRetryCount = mutableMapOf<String, Int>()
    private val MAX_RETRY_PER_SONG = 3
    private val RETRY_DELAY_MS = 1000L

    // Owns DJ analysis and transitions in a scope independent of service restarts.
    private val djEngine: DjEngine by lazy {
        DjEngine(
            database = database,
            createProbePlayer = { createExoPlayer(PlayerRole.ANALYSIS) },
            releaseProbePlayer = ::releasePlaybackPlayer,
        )
    }

    /** BPM / key / tier readout for the UI, owned by the engine. */
    val djState get() = djEngine.state

    private val recentlyFailedSongs = mutableSetOf<String>()
    private var failedSongsClearJob: Job? = null

    private val audioDeviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>?) {
            super.onAudioDevicesAdded(addedDevices)
            val hasBluetooth = addedDevices?.any { device ->
                device.type == AudioDeviceInfo.TYPE_BLUETOOTH_A2DP ||
                    device.type == AudioDeviceInfo.TYPE_BLUETOOTH_SCO ||
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && (
                        device.type == AudioDeviceInfo.TYPE_BLE_HEADSET ||
                            device.type == AudioDeviceInfo.TYPE_BLE_SPEAKER ||
                            device.type == AudioDeviceInfo.TYPE_HEARING_AID
                        ))
            } == true

            if (hasBluetooth) {
                if (dataStore.get(ResumeOnBluetoothConnectKey, false)) {
                    if (player.playbackState == Player.STATE_READY && !player.isPlaying) {
                        player.play()
                    }
                }
            }
            onOutputChanged()
        }

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>?) {
            onOutputChanged()
        }
    }

    /** Per-output EQ: restore the profile last used on whatever is now playing audio. */
    private fun onOutputChanged() {
        val key = currentOutputKey()
        scope.launch { eqProfileRepository.switchOutput(key) }
    }

    /** Identifies the active output device, preferring external devices to the speaker. */
    private fun currentOutputKey(): String {
        val outputs = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)
        val routed = OutputPriority.firstNotNullOfOrNull { type -> outputs.firstOrNull { it.type == type } }
        return routed?.let { "${it.type}:${it.productName}" } ?: "speaker"
    }

    override fun onCreate() {
        super.onCreate()
        isRunning = true

        // Handle foreground promotion refusal through Media3's supported callback.
        // Qualify the listener because this service also implements Player.Listener.
        setListener(object : MediaSessionService.Listener {
            override fun onForegroundServiceStartNotAllowedException() {
                Timber.tag(TAG).w("Foreground promotion refused by platform — staying unpromoted")
            }
        })

        playerInitialized.value = false

        startForegroundNotification()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider(
                this,
                { NOTIFICATION_ID },
                CHANNEL_ID,
                R.string.music_player
            )
                .apply {
                    setSmallIcon(R.drawable.apprise_notification)
                },
        )
        player = createExoPlayer()
        player.addListener(this@MusicService)

        trackAnalyzer = TrackAnalyzer(this)
        djEngine.smartGrid = { id ->
            trackAnalyzer?.analysisFor(id)?.takeIf { it.isUsable }?.let {
                BeatGrid(it.bpm.toFloat(), (it.firstBeat * 1000).toFloat(), it.beatConfidence.toFloat())
            }
        }
        crossfade = CrossfadeController(
            scope = scope,
            active = { player },
            standby = { sparePlayer ?: createExoPlayer(PlayerRole.STANDBY).also { sparePlayer = it } },
            onHandoff = ::adoptPlayer,
            analysisFor = { trackAnalyzer?.analysisFor(it.mediaId) ?: TrackAnalysis() },
            requestAnalysis = { mediaItem, durationMs ->
                val id = mediaItem.mediaId
                if (id.startsWith("content://") || id.startsWith("file://")) {
                    trackAnalyzer?.request(mediaItem.mediaId, id.toUri(), durationMs / 1000.0)
                }
            },
            djEngine = djEngine,
            warmUpcoming = {},
        )
        crossfade?.start()

        // Keep the transition-path pref snapshot warm (see cached* fields).
        scope.launch {
            dataStore.data.collect { prefs ->
                cachedRepeatMode = prefs[RepeatModeKey] ?: REPEAT_MODE_OFF
                cachedAutoLoadMore = prefs[AutoLoadMoreKey] ?: true
                cachedDisableLoadMoreWhenRepeatAll = prefs[DisableLoadMoreWhenRepeatAllKey] ?: false
                cachedPersistentQueue = prefs[PersistentQueueKey] ?: true
            }
        }
        sleepTimer = SleepTimer(scope, player)
        player.addListener(sleepTimer)

        playerInitialized.value = true
        Timber.tag(TAG).d("Player successfully initialized")

        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioResources.setupAudioFocusRequest()

        initializeMediaSession()

        connectivityManager = getSystemService()!!
        connectivityObserver = NetworkConnectivityObserver(this)

        audioManager.registerAudioDeviceCallback(audioDeviceCallback, null)
        onOutputChanged()

        // A saved volume of ~0 would come back silent on every launch with no on-screen way to tell
        // (the system volume does not touch this one), so only an audible value is restored.
        playerVolume = MutableStateFlow(
            dataStore.get(PlayerVolumeKey, 1f).coerceIn(0f, 1f).takeIf { it >= MIN_AUDIBLE_VOLUME } ?: 1f,
        )

        scope.launch {
            eqProfileRepository.activeProfile.collect { profile ->
                if (profile != null) {
                    equalizerService.applyProfile(profile)
                } else {
                    equalizerService.disable()
                }
            }
        }

        scope.launch {
            connectivityObserver.networkStatus.collect { isConnected ->
                isNetworkConnected.value = isConnected
                if (player.isPlaying) audioResources.acquireWifiLock() else audioResources.releaseWifiLock()
                if (isConnected && waitingForNetworkConnection.value) {
                    errorRecovery.triggerRetry()
                }
            }
        }

        observePlaybackUiAndLyrics()

        observeAudioProcessingPreferences()

        observeScrobblingPreferences()

        observeTransitionPreferences()

        restorePersistentPlaybackState()

        startQueuePersistence()
    }

    private fun startQueuePersistence() {
        scope.launch {
            while (isActive) {
                delay(if (player.isPlaying) 10.seconds else 30.seconds)
                if (cachedPersistentQueue) queueRepository.saveQueueToDisk()
            }
        }
    }

    /**
     * Playing but silent with the system volume up means the player's own volume was left near zero
     * (an interrupted fade, a synced host volume, a stale duck). Put it back to what the user chose.
     */
    private fun startVolumeWatchdog() {
        scope.launch {
            while (isActive) {
                delay(VOLUME_WATCHDOG_MS)
                if (player.isPlaying && !isMuted.value && crossfade?.isTransitioning() != true &&
                    duckVolumeRestoreJob == null && player.volume < MIN_AUDIBLE_VOLUME &&
                    playerVolume.value >= MIN_AUDIBLE_VOLUME
                ) {
                    Timber.tag(TAG).w("Player volume stuck at ${player.volume}; restoring ${playerVolume.value}")
                    player.volume = playerVolume.value
                }
            }
        }
    }

    private fun observePlaybackUiAndLyrics() {
        startVolumeWatchdog()
        combine(playerVolume, isMuted) { volume, muted ->
            if (muted) 0f else volume
        }.collectLatest(scope) {
            player.volume = it
        }

        playerVolume.debounce(1000).collect(scope) { volume ->
            dataStore.edit { settings ->
                settings[PlayerVolumeKey] = volume
            }
        }

        currentSong.debounce(1000).collect(scope) { song ->
            notificationUpdater.updateNotification()
            notificationUpdater.updateWidgetUI(player.isPlaying)
        }

        var previousPolicy: String? = null
        combine(
            currentMediaMetadata.distinctUntilChangedBy { it?.id },
            dataStore.data.map { prefs ->
                val policy = prefs[LyricsProviderOrderKey].orEmpty() + "|" +
                    LyricsProviderRegistry.providerIds.joinToString(",") { id ->
                        "$id:${prefs[LyricsProviderSettings.enabledKey(id)] ?: true}"
                    }
                (prefs[DataSaverEnabledKey] != true) to policy
            }.distinctUntilChanged(),
        ) { metadata, policy -> metadata to policy }.collectLatest(scope) { (metadata, settings) ->
            val (showLyrics, policy) = settings
            val changed = previousPolicy != null && previousPolicy != policy
            previousPolicy = policy
            if (!showLyrics || metadata == null) return@collectLatest
            val existing = database.lyrics(metadata.id).first()
            if (existing != null) {
                val provider = LyricsProviderRegistry.getProviderForStoredName(existing.provider)
                // Embedded or manually edited lyrics keep their own source.
                if (provider == null && existing.provider != "Unknown") return@collectLatest
                if (!changed && provider != null && LyricsProviderSettings.isEnabled(this, provider) &&
                    LyricsSyncLevel.detect(existing.lyrics) == LyricsSyncLevel.WORD) return@collectLatest
            }
            var lastWritten = existing
            var manuallyChanged = false
            suspend fun persistUpgrade(found: LyricsWithProvider) {
                if (manuallyChanged) return
                database.withTransaction {
                    if (lyrics(metadata.id).first() != lastWritten) {
                        manuallyChanged = true
                    } else {
                        val next = LyricsEntity(metadata.id, found.lyrics, found.provider)
                        if (lastWritten != next) upsert(next)
                        lastWritten = next
                    }
                }
            }
            val initial = existing?.let { LyricsWithProvider(it.lyrics, it.provider) }
            val found = lyricsHelper.getLyrics(metadata, initial, ::persistUpgrade)
            if (found.lyrics != LyricsEntity.LYRICS_NOT_FOUND || lastWritten == null || changed) persistUpgrade(found)

        }
    }

    private fun observeAudioProcessingPreferences() {
        dataStore.data
            .map { (it[SkipSilenceKey] ?: false) to (it[SkipSilenceInstantKey] ?: false) }
            .distinctUntilChanged()
            .collectLatest(scope) { (skipSilence, instantSkip) ->
                cachedSkipSilence = skipSilence
                cachedSkipSilenceInstant = instantSkip

                player.skipSilenceEnabled = skipSilence
                sparePlayer?.skipSilenceEnabled = skipSilence

                val enableInstant = skipSilence && instantSkip
                instantSilenceSkipEnabled.value = enableInstant

                playerSilenceProcessors.values.forEach { processor ->
                    processor.instantModeEnabled = enableInstant
                    if (!enableInstant) {
                        processor.resetTracking()
                    }
                }

                if (!enableInstant) {
                    silenceSkipJob?.cancel()
                }
            }

        dataStore.data
            .map { it[BitPerfectKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) { bitPerfect ->
                cachedBitPerfect = bitPerfect
                equalizerService.setBitPerfectBypass(bitPerfect)
                // Spatial widening rewrites every sample, so it bypasses under Bit
                // Perfect for the same reason the EQ does — the toggle's own value is
                // left alone and restores when Bit Perfect goes back off.
                audioEffects.applySpatialAudio()
                audioEffects.applyVocalSuppression()

                if (bitPerfect) {
                    player.skipSilenceEnabled = false
                    sparePlayer?.skipSilenceEnabled = false
                } else {
                    player.skipSilenceEnabled = cachedSkipSilence
                    sparePlayer?.skipSilenceEnabled = cachedSkipSilence
                    BitPerfectAudio.clearBitPerfectMixer(this@MusicService)
                }
            }

        dataStore.data
            .map { it[SpatialAudioKey] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) { spatial ->
                cachedSpatialAudio = spatial
                audioEffects.applySpatialAudio()
            }

        dataStore.data
            .map { it[VocalSuppressionKey] ?: 0f }
            .distinctUntilChanged()
            .collectLatest(scope) { amount ->
                cachedVocalSuppression = amount.coerceIn(0f, 1f)
                audioEffects.applyVocalSuppression()
            }

        combine(
            currentFormat,
            dataStore.data
                .map { it[AudioNormalizationKey] ?: true }
                .distinctUntilChanged(),
        ) { format, normalizeAudio ->
            format to normalizeAudio
        }.collectLatest(scope) {
            audioEffects.setupLoudnessEnhancer()
        }

        dataStore.data.map { preferences ->
            val transitionsEnabled = (preferences[CrossfadeEnabledKey] ?: false) &&
                !(preferences[BitPerfectKey] ?: false)
            (preferences[AudioOffload] ?: false) && !transitionsEnabled
        }
            .distinctUntilChanged()
            .collectLatest(scope) { useOffload ->
                cachedOffloadEnabled = useOffload
                player.setOffloadEnabled(useOffload)
                sparePlayer?.setOffloadEnabled(useOffload)
            }
    }

    private fun observeScrobblingPreferences() {
        dataStore.data
            .map { it[EnableLastFMScrobblingKey] ?: false }
            .debounce(300)
            .distinctUntilChanged()
            .collect(scope) { enabled ->
                if (enabled && scrobbleManager == null) {
                    val delayPercent = dataStore.get(ScrobbleDelayPercentKey, LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT)
                    val minSongDuration = dataStore.get(ScrobbleMinSongDurationKey, LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION)
                    val delaySeconds = dataStore.get(ScrobbleDelaySecondsKey, LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS)
                    scrobbleManager = ScrobbleManager(
                        scope,
                        minSongDuration = minSongDuration,
                        scrobbleDelayPercent = delayPercent,
                        scrobbleDelaySeconds = delaySeconds
                    )
                    scrobbleManager?.useNowPlaying = dataStore.get(LastFMUseNowPlaying, false)
                } else if (!enabled && scrobbleManager != null) {
                    scrobbleManager?.destroy()
                    scrobbleManager = null
                }
            }

        dataStore.data
            .map { it[LastFMUseNowPlaying] ?: false }
            .distinctUntilChanged()
            .collectLatest(scope) {
                scrobbleManager?.useNowPlaying = it
            }

        dataStore.data
            .map {
                val listenBrainz = it[ListenBrainzEnabledKey] ?: false
                val dataSaver = it[DataSaverEnabledKey] ?: false
                if (dataSaver) false else listenBrainz
            }
            .distinctUntilChanged()
            .collect(scope) { listenBrainzEnabled = it }

        dataStore.data
            .map { it[ListenBrainzTokenKey] ?: "" }
            .distinctUntilChanged()
            .collect(scope) { listenBrainzToken = it }

        dataStore.data
            .map { prefs ->
                Triple(
                    prefs[ScrobbleDelayPercentKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_PERCENT,
                    prefs[ScrobbleMinSongDurationKey] ?: LastFM.DEFAULT_SCROBBLE_MIN_SONG_DURATION,
                    prefs[ScrobbleDelaySecondsKey] ?: LastFM.DEFAULT_SCROBBLE_DELAY_SECONDS
                )
            }
            .distinctUntilChanged()
            .collect(scope) { (delayPercent, minSongDuration, delaySeconds) ->
                scrobbleManager?.let {
                    it.scrobbleDelayPercent = delayPercent
                    it.minSongDuration = minSongDuration
                    it.scrobbleDelaySeconds = delaySeconds
                }
            }
    }

    private data class TransitionPreferences(
        val enabled: Boolean,
        val durationSeconds: Float,
        val autoMix: Boolean,
        val creative: Boolean,
        val reverb: ReverbType,
        val delay: DelayType,
    )

    private fun observeTransitionPreferences() {
        combine(
            dataStore.data,
            entitlementProvider.entitlements,
        ) { prefs, snapshot ->
            val crossfadePermitted = com.apprise.music.entitlement.FeatureCapability.CROSSFADE in snapshot.unlockedCapabilities
            val enabled = crossfadePermitted && (prefs[CrossfadeEnabledKey] ?: false) && !(prefs[BitPerfectKey] ?: false)
            val autoMix = enabled && (prefs[AutoDjMixingEnabledKey] ?: false)
            val seconds = (prefs[CrossfadeDurationKey] ?: DEFAULT_CROSSFADE_SECONDS)
                .takeIf { it.isFinite() } ?: DEFAULT_CROSSFADE_SECONDS
            TransitionPreferences(
                enabled = enabled,
                durationSeconds = seconds.coerceIn(1f, 12f),
                autoMix = autoMix,
                creative = autoMix && (prefs[CreativeTransitionsEnabledKey] ?: false),
                reverb = prefs[DjReverbTypeKey].toEnum(ReverbType.AUTO),
                delay = prefs[DjDelayTypeKey].toEnum(DelayType.AUTO),
            )
        }.distinctUntilChanged().collect(scope) { preferences ->
            AppSettings.crossfadeSeconds.floatValue = if (preferences.enabled) preferences.durationSeconds else 0f
            AppSettings.smartFadeEnabled.value = preferences.autoMix
            if (!preferences.autoMix) trackAnalyzer?.cancelPending()
            djEngine.enabled = preferences.autoMix
            djEngine.creativeEnabled = preferences.creative
            djEngine.reverbType = preferences.reverb
            djEngine.delayType = preferences.delay
            djEngine.refreshState(player.currentMediaItem?.mediaId)
        }
    }

    private fun restorePersistentPlaybackState() {
        if (!dataStore.get(PersistentQueueKey, true)) return

        persistenceScope.launch {
            val snapshot = readPersistedPlaybackSnapshot() ?: return@launch
            scope.launch {
                snapshot.automix?.let { savedAutomix ->
                    automixItems.value = savedAutomix.items.map { it.toMediaItem() }
                }

                snapshot.playerState?.let { savedState ->
                    // A muted or ducked snapshot contains the effective player volume.
                    if (savedState.volume > 0f) playerVolume.value = savedState.volume
                }

                val savedQueue = snapshot.queue ?: return@launch
                playerInitialized.first { it }
                if (isActive) {
                    val restoredQueue = withContext(Dispatchers.IO) {
                        savedQueue.toQueue(database)
                    }
                    playQueue(queue = restoredQueue, playWhenReady = false)
                    snapshot.playerState?.let { savedState ->
                        player.repeatMode = savedState.repeatMode
                        player.shuffleModeEnabled = savedState.shuffleModeEnabled
                    }
                }
            }
        }
    }

    private fun readPersistedPlaybackSnapshot(): PersistedPlaybackSnapshot? {
        val store = PlaybackStateStore(filesDir.resolve(PERSISTENT_PLAYBACK_SNAPSHOT_FILE))
        val currentSnapshot = runCatching { store.read() }
            .onFailure { error ->
                Timber.tag(TAG).w(error, "Failed to read persisted playback snapshot")
                store.delete()
            }
            .getOrNull()
        if (currentSnapshot != null) return currentSnapshot

        val legacySnapshot = PersistedPlaybackSnapshot(
            queue = readLegacyPlaybackFile(PERSISTENT_QUEUE_FILE, PersistQueue::class.java),
            automix = readLegacyPlaybackFile(PERSISTENT_AUTOMIX_FILE, PersistQueue::class.java),
            playerState = readLegacyPlaybackFile(PERSISTENT_PLAYER_STATE_FILE, PersistPlayerState::class.java),
        )
        if (
            legacySnapshot.queue == null &&
            legacySnapshot.automix == null &&
            legacySnapshot.playerState == null
        ) return null

        runCatching {
            store.write(legacySnapshot)
            deleteLegacyPlaybackFiles()
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Failed to migrate saved playback state")
        }
        return legacySnapshot
    }

    private fun <T : Any> readLegacyPlaybackFile(fileName: String, type: Class<T>): T? {
        val file = filesDir.resolve(fileName)
        if (!file.exists()) return null

        return runCatching {
            file.inputStream().use { input ->
                ObjectInputStream(input).use { it.readObject().let(type::cast) }
            }
        }.onFailure { error ->
            Timber.tag(TAG).w(error, "Failed to read legacy playback file $fileName")
            file.delete()
        }.getOrNull()
    }

    private fun deleteLegacyPlaybackFiles() {
        filesDir.resolve(PERSISTENT_QUEUE_FILE).delete()
        filesDir.resolve(PERSISTENT_AUTOMIX_FILE).delete()
        filesDir.resolve(PERSISTENT_PLAYER_STATE_FILE).delete()
    }

    private fun startForegroundNotification() {
        try {
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.music_player),
                    NotificationManager.IMPORTANCE_LOW,
                ),
            )

            val pendingIntent = PendingIntent.getActivity(
                this,
                0,
                Intent(this, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE,
            )
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.music_player))
                .setContentText("")
                .setSmallIcon(R.drawable.apprise_notification)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build()

            ServiceCompat.startForeground(
                this,
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK,
            )
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to create foreground notification")
            reportException(e)
        }
    }

    private fun initializeMediaSession() {
        mediaLibrarySessionCallback.apply {
            toggleLike = ::toggleLike
            toggleStartRadio = ::toggleStartRadio
            toggleLibrary = ::toggleLibrary
        }
        mediaSession =
            MediaLibrarySession
                .Builder(this, player, mediaLibrarySessionCallback)
                .setSessionActivity(
                    PendingIntent.getActivity(
                        this,
                        0,
                        Intent(this, MainActivity::class.java),
                        PendingIntent.FLAG_IMMUTABLE,
                    ),
                ).setBitmapLoader(CoilBitmapLoader(this, scope))
                .build()
        player.repeatMode = dataStore.get(RepeatModeKey, REPEAT_MODE_OFF)

        if (dataStore.get(RememberShuffleAndRepeatKey, true)) {
            player.shuffleModeEnabled = dataStore.get(ShuffleModeKey, false)
        }

        val sessionToken = SessionToken(this, ComponentName(this, MusicService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()
        controllerFuture.addListener({ controllerFuture.get() }, MoreExecutors.directExecutor())
    }

    private enum class PlayerRole { SESSION, STANDBY, ANALYSIS }

    private fun createExoPlayer(role: PlayerRole = PlayerRole.SESSION): ExoPlayer {
        val analysisOnly = role == PlayerRole.ANALYSIS
        var createdPlayer: ExoPlayer? = null
        val eqProcessor = CustomEqualizerAudioProcessor()
        if (!analysisOnly) equalizerService.addAudioProcessor(eqProcessor)

        val silenceProcessor = SilenceDetectorAudioProcessor(
            minSilenceDurationUs = 3_000_000L,
            silenceThreshold = 128,
            onLongSilence = {
                if (playerInitialized.value && createdPlayer === this@MusicService.player) {
                    silenceSkipGuard.handleLongSilenceDetected()
                }
            }
        )

        lateinit var bpmAnalyzerRef: TrackAnalyzerAudioProcessor
        val bpmAnalyzer = TrackAnalyzerAudioProcessor(
            onAnalysisReady = { djEngine.onAnalysisReady(bpmAnalyzerRef) },
            analysisEnabled = { djEngine.enabled },
        )
        bpmAnalyzerRef = bpmAnalyzer

        val djFilter = DjFilterAudioProcessor()
        val djTail = DjTailAudioProcessor()
        val djDelay = DelayAudioProcessor()
        val djAmbience = DjAmbienceAudioProcessor()
        val spatialProcessor = SpatialAudioProcessor()
        val vocalSuppressionProcessor = VocalSuppressionAudioProcessor()
        // Use cached preferences; reading DataStore here could stall player creation during crossfade.
        silenceProcessor.instantModeEnabled = cachedSkipSilence && cachedSkipSilenceInstant
        spatialProcessor.enabled = cachedSpatialAudio && !cachedBitPerfect
        vocalSuppressionProcessor.amount = if (cachedBitPerfect) 0f else cachedVocalSuppression

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(audioSourceBuilder.createMediaSourceFactory())
            .setRenderersFactory(
                audioSourceBuilder.createRenderersFactory(
                    eqProcessor, silenceProcessor, bpmAnalyzer, spatialProcessor, vocalSuppressionProcessor,
                    djFilter, djTail, djDelay, djAmbience, analysisOnly,
                )
            )
            .setLoadControl(
                androidx.media3.exoplayer.DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        50_000,   // Min buffer: 50s
                        50_000,   // Max buffer: 50s
                        750,      // Buffer for playback: 750ms — start audio as soon as we have it
                        2_000     // Buffer for playback after re-buffer: 2s
                    )
                    .build()
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                false,
            )
            .setSeekBackIncrementMs(5000)
            .setSeekForwardIncrementMs(5000)
            .setDeviceVolumeControlEnabled(true)
            .build()

        createdPlayer = player
        if (!analysisOnly) {
            playerSilenceProcessors[player] = silenceProcessor
            playerEqualizers[player] = eqProcessor
            playerSpatialProcessors[player] = spatialProcessor
            playerVocalSuppressionProcessors[player] = vocalSuppressionProcessor
        }
        djEngine.registerPlayer(player, bpmAnalyzer, djFilter, djTail, djDelay, djAmbience)

        player.setOffloadEnabled(cachedOffloadEnabled && !analysisOnly)
        player.skipSilenceEnabled = cachedSkipSilence && !cachedBitPerfect
        if (!analysisOnly) player.addAnalyticsListener(crashSafe(PlaybackStatsListener(false, this@MusicService)))

        if (role == PlayerRole.SESSION) _playerFlow.value = player
        return player
    }

    private fun releasePlaybackPlayer(target: ExoPlayer) {
        djEngine.unregisterPlayer(target)
        playerEqualizers.remove(target)?.let(equalizerService::removeAudioProcessor)
        playerSilenceProcessors.remove(target)
        playerSpatialProcessors.remove(target)
        playerVocalSuppressionProcessors.remove(target)
        target.release()
    }

    /** Manages audio focus and the Wi-Fi lock used during playback. */
    inner class AudioResourceCoordinator {

        fun setupAudioFocusRequest() {
            audioFocusRequest = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(
                    android.media.AudioAttributes.Builder()
                        .setUsage(android.media.AudioAttributes.USAGE_MEDIA)
                        .setContentType(android.media.AudioAttributes.CONTENT_TYPE_MUSIC)
                        .build()
                )
                .setOnAudioFocusChangeListener { focusChange ->
                    handleAudioFocusChange(focusChange)
                }
                .setAcceptsDelayedFocusGain(true)
                .build()
        }

        fun handleAudioFocusChange(focusChange: Int) {
            when (focusChange) {

                AudioManager.AUDIOFOCUS_GAIN,
                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT -> {
                    hasAudioFocus = true
                    duckVolumeRestoreJob?.cancel()
                    duckVolumeRestoreJob = null
                    player.volume = if (isMuted.value) 0f else playerVolume.value
                    resumeAfterFocusGain()
                }

                AudioManager.AUDIOFOCUS_LOSS -> {
                    hasAudioFocus = false
                    pendingFocusStart = false
                    wasPlayingBeforeAudioFocusLoss = false
                    pauseForAudioFocus()
                    abandonAudioFocus()
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                    hasAudioFocus = false
                    wasPlayingBeforeAudioFocusLoss = player.playWhenReady
                    pauseForAudioFocus()
                }

                AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                    hasAudioFocus = false
                    if (player.isPlaying) {
                        player.volume = if (isMuted.value) 0f else (playerVolume.value * 0.2f)
                        // Safety net: some devices deliver CAN_DUCK and never follow
                        // up with AUDIOFOCUS_GAIN, leaving volume at 20% indefinitely.
                        duckVolumeRestoreJob?.cancel()
                        duckVolumeRestoreJob = scope.launch {
                            delay(DUCK_RESTORE_MS)
                            if (isActive && player.isPlaying) {
                                player.volume = if (isMuted.value) 0f else playerVolume.value
                                Timber.tag(TAG).w("Restoring volume after unreleased audio duck")
                            }
                            duckVolumeRestoreJob = null
                        }
                    }
                }

                AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK -> {
                    hasAudioFocus = true
                    duckVolumeRestoreJob?.cancel()
                    duckVolumeRestoreJob = null
                    player.volume = if (isMuted.value) 0f else playerVolume.value
                }
            }
        }

        fun requestAudioFocus(): FocusRequestResult {
            if (hasAudioFocus) return FocusRequestResult.GRANTED

            audioFocusRequest?.let { request ->
                val result = audioManager.requestAudioFocus(request)
                return when (result) {
                    AudioManager.AUDIOFOCUS_REQUEST_GRANTED -> {
                        hasAudioFocus = true
                        audioFocusRequestOutstanding = true
                        FocusRequestResult.GRANTED
                    }

                    AudioManager.AUDIOFOCUS_REQUEST_DELAYED -> {
                        hasAudioFocus = false
                        audioFocusRequestOutstanding = true
                        FocusRequestResult.DELAYED
                    }

                    else -> {
                        hasAudioFocus = false
                        audioFocusRequestOutstanding = false
                        FocusRequestResult.DENIED
                    }
                }
            }
            return FocusRequestResult.DENIED
        }

        fun abandonAudioFocus() {
            if (audioFocusRequestOutstanding) {
                audioFocusRequest?.let(audioManager::abandonAudioFocusRequest)
            }
            audioFocusRequestOutstanding = false
            hasAudioFocus = false
            pendingFocusStart = false
        }

        fun cancelDelayedFocusStart() {
            pendingFocusStart = false
            if (!hasAudioFocus) abandonAudioFocus()
        }

        private fun resumeAfterFocusGain() {
            val shouldResume = pendingFocusStart || wasPlayingBeforeAudioFocusLoss
            if (!shouldResume) return

            focusResumeJob?.cancel()
            focusResumeJob = scope.launch {
                delay(300)
                if (
                    hasAudioFocus &&
                    (pendingFocusStart || wasPlayingBeforeAudioFocusLoss) &&
                    !player.playWhenReady
                ) {
                    pendingFocusStart = false
                    wasPlayingBeforeAudioFocusLoss = false
                    player.play()
                }
            }
        }

        fun pauseForAudioFocus() {
            focusResumeJob?.cancel()
            if (!player.playWhenReady) return

            isPausingForAudioFocus = true
            try {
                player.pause()
            } finally {
                isPausingForAudioFocus = false
            }
        }

        fun acquireWifiLock() {
            // Offline music player: do not hold a high-performance Wi-Fi lock that drains battery.
            releaseWifiLock()
        }

        fun releaseWifiLock() {
            if (wifiLock?.isHeld == true) {
                wifiLock?.release()
            }
            wifiLock = null
        }
    }

    private val audioResources = AudioResourceCoordinator()

    enum class FocusRequestResult {
        GRANTED,
        DELAYED,
        DENIED,
    }

    fun hasAudioFocusForPlayback(): Boolean = hasAudioFocus

    /** Handles playback retry, skip, stop, and cache recovery. */
    inner class PlaybackErrorRecoveryHandler {

        fun waitOnNetworkError() {
            if (retryCount >= MAX_RETRY_COUNT) {
                Timber.tag(TAG).w("Max retry count ($MAX_RETRY_COUNT) reached, stopping playback")
                stopOnError()
                retryCount = 0
                return
            }

            waitingForNetworkConnection.value = true

            // Avoid leaving the service waiting for network with no active retry job.
            if (retryJob?.isActive == true) return

            retryJob?.cancel()
            retryJob = scope.launch {
                // Exponential backoff: 3s, 6s, 12s, 24s... max 30s
                val delayMs = minOf(3000L * (1 shl retryCount), 30000L)
                Timber.tag(TAG).d("Waiting ${delayMs}ms before retry attempt ${retryCount + 1}/$MAX_RETRY_COUNT")
                delay(delayMs)

                if (!isActive) return@launch
                retryCount++
                if (isNetworkConnected.value) {
                    triggerRetry()
                } else {
                    // Keep waiting until the connection returns.
                    waitOnNetworkError()
                }
            }
        }

        fun triggerRetry() {
            waitingForNetworkConnection.value = false
            retryJob?.cancel()

            if (player.currentMediaItem != null) {
                // Seeking in place forces ExoPlayer to re-resolve the data source and get a fresh URL.
                if (retryCount > 3) {
                    Timber.tag(TAG).d("Retry count > 3, attempting to refresh stream URL")
                    val currentPosition = player.currentPosition
                    player.seekTo(player.currentMediaItemIndex, currentPosition)
                }
                // No play() here: let it auto-resume via playWhenReady instead of stealing audio focus mid-retry.
                player.prepare()
            }
        }

        fun skipOnError() {
            // Stop retrying after repeated errors and wait for user input.
            consecutivePlaybackErr += 2
            val nextWindowIndex = player.nextMediaItemIndex

            if (consecutivePlaybackErr <= MAX_CONSECUTIVE_ERR && nextWindowIndex != C.INDEX_UNSET) {
                player.seekTo(nextWindowIndex, C.TIME_UNSET)
                player.prepare()
                player.play()
                return
            }

            player.pause()
            consecutivePlaybackErr = 0
        }

        fun stopOnError() {
            player.pause()
            // Stop the service so Media3 removes the stale playback notification.
            stopSelf()
            currentMediaMetadata.value = null
            notificationUpdater.updateWidgetUI(false)
        }

        fun hasExceededRetryLimit(mediaId: String): Boolean {
            val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
            return currentRetries >= MAX_RETRY_PER_SONG
        }

        fun incrementRetryCount(mediaId: String) {
            val currentRetries = currentMediaIdRetryCount[mediaId] ?: 0
            currentMediaIdRetryCount[mediaId] = currentRetries + 1
            Timber.tag(TAG).d("Retry count for $mediaId: ${currentRetries + 1}/$MAX_RETRY_PER_SONG")
        }

        /** Called on successful playback to let a song retry again later if it fails again. */
        fun resetRetryCount(mediaId: String) {
            currentMediaIdRetryCount.remove(mediaId)
            recentlyFailedSongs.remove(mediaId)
        }

        fun markSongAsFailed(mediaId: String) {
            recentlyFailedSongs.add(mediaId)
            currentMediaIdRetryCount.remove(mediaId)

            // Schedule cleanup of failed songs list after 5 minutes
            failedSongsClearJob?.cancel()
            failedSongsClearJob = scope.launch {
                delay(5 * 60 * 1000L)
                recentlyFailedSongs.clear()
                Timber.tag(TAG).d("Cleared recently failed songs list")
            }
        }

        private fun retryStream(
            mediaId: String,
            reason: String,
            fromStart: Boolean = false,
        ) {
            incrementRetryCount(mediaId)
            retryJob?.cancel()
            retryJob = scope.launch {
                delay(RETRY_DELAY_MS)

                val position = if (fromStart) 0L else player.currentPosition
                player.seekTo(player.currentMediaItemIndex, position)
                player.prepare()
                Timber.tag(TAG).d("Retrying playback for $mediaId after $reason")
            }
        }

        fun handleParsingError(mediaId: String?) {
            if (mediaId == null) {
                handleFinalFailure()
                return
            }

            retryStream(
                mediaId = mediaId,
                reason = "parsing error (from position 0)",
                fromStart = true,
            )
        }

        fun handleAudioRendererError(mediaId: String?) {
            if (mediaId == null) {
                handleFinalFailure()
                return
            }

            incrementRetryCount(mediaId)

            retryJob?.cancel()
            retryJob = scope.launch {
                try {
                    // Pause immediately to stop the renderer, then wait longer than a normal
                    // retry (3s instead of 1s) so it doesn't keep failing in a loop.
                    player.pause()
                    Timber.tag(TAG).d("Paused playback due to AudioTrack error")
                    delay(RETRY_DELAY_MS * 3)

                    if (!playerInitialized.value) {
                        Timber.tag(TAG).w("Player no longer initialized, aborting AudioTrack recovery")
                        return@launch
                    }

                    val currentIndex = player.currentMediaItemIndex
                    if (currentIndex != C.INDEX_UNSET) {
                        // Seek to current position to force a clean audio renderer reinit
                        val currentPosition = player.currentPosition
                        player.seekTo(currentIndex, currentPosition)
                        player.prepare()

                        Timber.tag(TAG).d("Retrying playback for $mediaId after AudioTrack error")

                        if (wasPlayingBeforeAudioFocusLoss) {
                            delay(500)
                            if (hasAudioFocus && playerInitialized.value) {
                                player.play()
                            }
                        }
                    } else {
                        Timber.tag(TAG).w("Invalid media item index during AudioTrack recovery")
                        handleFinalFailure()
                    }
                } catch (e: Exception) {
                    Timber.tag(TAG).e(e, "Error during AudioTrack error recovery")
                    handleFinalFailure()
                }
            }
        }

        /** Samsung's resource manager reclaims the FLAC hardware decoder mid-playback. */
        fun handleDecoderReclaimError(mediaId: String?) {
            if (mediaId == null) { handleFinalFailure(); return }
            incrementRetryCount(mediaId)
            retryJob?.cancel()
            retryJob = scope.launch {
                Timber.tag(TAG).d("Decoder reclaim: pausing, waiting 2s, re-prepare for $mediaId")
                player.pause()
                delay(2000)
                if (!playerInitialized.value) return@launch
                val idx = player.currentMediaItemIndex
                val pos = player.currentPosition
                player.seekTo(idx, pos)
                player.prepare()
                delay(500)
                if (hasAudioFocus && playerInitialized.value) {
                    player.play()
                }
            }
        }

        fun handleGenericIOError(mediaId: String?) {
            if (mediaId == null) {
                handleFinalFailure()
                return
            }

            retryStream(mediaId, reason = "generic IO error")
        }

        fun handleFinalFailure() {
            if (dataStore.get(AutoSkipNextOnErrorKey, false)) {
                Timber.tag(TAG).d("All recovery attempts exhausted, auto-skipping to next track")
                skipOnError()
            } else {
                Timber.tag(TAG).d("All recovery attempts exhausted, stopping playback")
                stopOnError()
            }
        }
    }

    private val errorRecovery = PlaybackErrorRecoveryHandler()

    private val songRecoveryInFlight = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    /**
     * Seeking re-opens the data source for every probe, and a lossless file without a seek table can
     * mean dozens of probes. Recovering the song's metadata once per session is enough; doing it per
     * open queued a database read and a main-thread hop for each probe.
     */
    private val recoveredSongIds: MutableSet<String> = java.util.concurrent.ConcurrentHashMap.newKeySet()

    private fun recoverSongOnce(mediaId: String) {
        if (recoveredSongIds.add(mediaId)) scope.launch(Dispatchers.IO) { recoverSong(mediaId) }
    }

    private suspend fun recoverSong(mediaId: String) {
        if (!songRecoveryInFlight.add(mediaId)) return
        try {
            val song = database.song(mediaId).first()
            val mediaMetadata = withContext(Dispatchers.Main) {
                player.findNextMediaItemById(mediaId)?.metadata
            } ?: return
            val duration = song?.song?.duration?.takeIf { it != -1 }
                ?: mediaMetadata.duration.takeIf { it != -1 }
                ?: -1
            database.query {
                if (song == null) insert(mediaMetadata.copy(duration = duration))
                else {
                    var updatedSong = song.song
                    if (song.song.duration == -1 && duration != -1) {
                        updatedSong = updatedSong.copy(duration = duration)
                    }
                    if (updatedSong != song.song) {
                        update(updatedSong)
                    }
                }
            }
        } finally {
            songRecoveryInFlight.remove(mediaId)
        }
    }

    fun playQueue(
        queue: Queue,
        playWhenReady: Boolean = true,
    ) {
        val requestVersion = ++queueRequestVersion
        if (!playerInitialized.value) {
            Timber.tag(TAG).w("playQueue called before player initialization, queuing request")
            scope.launch {
                playerInitialized.first { it }
                if (requestVersion == queueRequestVersion) {
                    playQueue(queue, playWhenReady)
                }
            }
            return
        }

        currentQueue = queue
        queueTitle = null
        val persistShuffleAcrossQueues = dataStore.get(PersistentShuffleAcrossQueuesKey, false)
        if (!persistShuffleAcrossQueues) {
            player.shuffleModeEnabled = false
        }
        originalQueueSize = 0
        queue.preloadItem?.let { preloadItem ->
            player.setMediaItem(preloadItem.toMediaItem())
            player.prepare()
            player.playWhenReady = playWhenReady
        }
        scope.launch(SilentHandler) {
            val initialStatus =
                withContext(Dispatchers.IO) {
                    queue.getFilteredInitialStatus()
                }
            if (requestVersion != queueRequestVersion) return@launch
            if (queue.preloadItem != null && player.playbackState == STATE_IDLE) return@launch
            if (initialStatus.title != null) {
                queueTitle = initialStatus.title
            }
            if (initialStatus.items.isEmpty()) return@launch
            // Record the original playlist boundary for shuffle-playlist-first.
            originalQueueSize = initialStatus.items.size
            if (queue.preloadItem != null) {
                player.addMediaItems(
                    0,
                    initialStatus.items.subList(0, initialStatus.mediaItemIndex)
                )
                player.addMediaItems(
                    initialStatus.items.subList(
                        initialStatus.mediaItemIndex + 1,
                        initialStatus.items.size
                    )
                )
            } else {
                player.setMediaItems(
                    initialStatus.items,
                    initialStatus.mediaItemIndex.coerceAtLeast(0),
                    initialStatus.position,
                )
                player.prepare()
                player.playWhenReady = playWhenReady
            }

            if (player.shuffleModeEnabled) {
                val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
                applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
            }
        }
    }

    private suspend fun Queue.getFilteredInitialStatus(): Queue.Status = getInitialStatus()

    /** Appends [queue] after the current items without changing the playhead. */
    fun enqueueQueue(queue: Queue) {
        scope.launch(SilentHandler) {
            val status = withContext(Dispatchers.IO) { queue.getFilteredInitialStatus() }
            if (status.items.isEmpty()) return@launch
            // Start with the selected item, then wrap to the earlier entries.
            val start = status.mediaItemIndex.coerceIn(0, status.items.lastIndex)
            addToQueue(status.items.drop(start) + status.items.take(start))
        }
    }

    fun startRadioSeamlessly() {
        if (!playerInitialized.value) {
            Timber.tag(TAG).w("startRadioSeamlessly called before player initialization")
            return
        }

        val currentMediaMetadata = player.currentMetadata ?: return
        val currentIndex = player.currentMediaItemIndex
        val currentMediaId = currentMediaMetadata.id
        val requestVersion = queueRequestVersion

        scope.launch(SilentHandler) {
            val allLocal = withContext(Dispatchers.IO) {
                database.allSearchableSongs().first()
                    .filter { it.id != currentMediaId }
            }
            if (
                allLocal.isEmpty() ||
                requestVersion != queueRequestVersion ||
                player.currentMediaItem?.mediaId != currentMediaId ||
                player.currentMediaItemIndex != currentIndex
            ) return@launch

            val artistNames = currentMediaMetadata.artists.map { it.name.lowercase() }.toSet()
            val sameArtist = allLocal.filter { s -> s.artists.any { it.name.lowercase() in artistNames } }.shuffled()
            val others = allLocal.filter { s -> s.artists.none { it.name.lowercase() in artistNames } }.shuffled()
            val radioItems = (sameArtist + others).take(25).map { it.toMediaItem() }
            replaceUpcomingItems(currentIndex, radioItems)
        }
    }

    private fun replaceUpcomingItems(currentIndex: Int, items: List<MediaItem>) {
        if (items.isEmpty()) return

        val queueEnd = player.mediaItemCount
        if (queueEnd > currentIndex + 1) {
            player.removeMediaItems(currentIndex + 1, queueEnd)
        }
        player.addMediaItems(currentIndex + 1, items)

        if (player.shuffleModeEnabled) {
            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
        }
    }

    fun getAutomixAlbum(albumId: String) {}

    fun getAutomix(playlistId: String) {}

    fun addToQueueAutomix(
        item: MediaItem,
        position: Int,
    ) {
        removeAutomixSuggestion(position)
        addToQueue(listOf(item))
    }

    fun playNextAutomix(
        item: MediaItem,
        position: Int,
    ) {
        removeAutomixSuggestion(position)
        playNext(listOf(item))
    }

    private fun removeAutomixSuggestion(position: Int) {
        automixItems.value = automixItems.value.toMutableList().apply {
            removeAt(position)
        }
    }

    fun clearAutomix() {
        automixItems.value = emptyList()
    }

    fun playNext(items: List<MediaItem>) {
        if (player.mediaItemCount == 0 || player.playbackState == STATE_IDLE) {
            player.setMediaItems(items)
            player.prepare()
            player.play()
            return
        }

        removeDuplicateQueueItems(items)

        val insertIndex = player.currentMediaItemIndex + 1
        val shuffleEnabled = player.shuffleModeEnabled

        player.addMediaItems(insertIndex, items)
        player.prepare()

        if (shuffleEnabled) {
            rebuildShuffleOrderAfterPlayNext(insertIndex, items.size)
        }
    }

    private fun rebuildShuffleOrderAfterPlayNext(insertIndex: Int, insertedCount: Int) {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return

        val windowCount = timeline.windowCount
        val currentIndex = player.currentMediaItemIndex
        val insertedIndices = (insertIndex until insertIndex + insertedCount).toSet()

        val itemsAfterCurrent = mutableListOf<Int>()
        var nextIndex = currentIndex
        while (true) {
            nextIndex = timeline.getNextWindowIndex(
                nextIndex,
                Player.REPEAT_MODE_OFF,
                /*shuffleModeEnabled=*/true,
            )
            if (nextIndex == C.INDEX_UNSET) break
            if (nextIndex != currentIndex) itemsAfterCurrent.add(nextIndex)
        }

        val itemsBeforeCurrent = mutableListOf<Int>()
        var previousIndex = currentIndex
        while (true) {
            previousIndex = timeline.getPreviousWindowIndex(
                previousIndex,
                Player.REPEAT_MODE_OFF,
                /*shuffleModeEnabled=*/true,
            )
            if (previousIndex == C.INDEX_UNSET) break
            if (previousIndex != currentIndex) itemsBeforeCurrent.add(previousIndex)
        }
        itemsBeforeCurrent.reverse()

        val existingOrder = (itemsBeforeCurrent + itemsAfterCurrent)
            .filter { it != currentIndex && it !in insertedIndices }
        val insertedOrder = (insertIndex until insertIndex + insertedCount).toList()
        val finalOrder = IntArray(windowCount)
        val placed = BooleanArray(windowCount)
        var position = 0

        finalOrder[position++] = currentIndex
        placed[currentIndex] = true
        insertedOrder.forEach { index ->
            if (index in 0 until windowCount) {
                finalOrder[position++] = index
                placed[index] = true
            }
        }
        existingOrder.forEach { index ->
            if (position < windowCount) {
                finalOrder[position++] = index
                placed[index] = true
            }
        }

        // Keep the membership check constant-time while filling a valid permutation.
        for (index in 0 until windowCount) {
            if (position == windowCount) break
            if (!placed[index]) {
                finalOrder[position++] = index
            }
        }

        player.setShuffleOrder(DefaultShuffleOrder(finalOrder, System.currentTimeMillis()))
    }

    fun addToQueue(items: List<MediaItem>) {
        removeDuplicateQueueItems(items)

        player.addMediaItems(items)
        if (player.shuffleModeEnabled) {
            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            applyShuffleOrder(player.currentMediaItemIndex, player.mediaItemCount, shufflePlaylistFirst)
        }
        player.prepare()
    }

    private fun removeDuplicateQueueItems(items: List<MediaItem>) {
        if (!dataStore.get(PreventDuplicateTracksInQueueKey, false)) return

        val itemIds = items.mapTo(mutableSetOf()) { it.mediaId }
        var currentIndex = player.currentMediaItemIndex

        for (index in player.mediaItemCount - 1 downTo 0) {
            if (index == currentIndex) continue
            if (player.getMediaItemAt(index).mediaId !in itemIds) continue

            player.removeMediaItem(index)
            // Removing an earlier item shifts the active item's index.
            if (index < currentIndex) currentIndex--
        }
    }

    /** Returns the database row for the player's current track, inserting it if needed. */
    private suspend fun liveCurrentSong(): Song? {
        val metadata = player.currentMetadata ?: return null
        // Read from the player directly; currentSong may be uncollected or one track behind.
        database.song(metadata.id).first()?.let { return it }
        // Await insertion because database.query returns before the write completes.
        database.withTransaction { insert(metadata) }
        return database.song(metadata.id).first()
    }

    fun toggleLibrary() {
        scope.launch {
            val songToToggle = liveCurrentSong()
            songToToggle?.let {
                database.query {
                    update(it.song.toggleLibrary())
                }
                currentMediaMetadata.value = player.currentMetadata
            }
        }
    }

    fun toggleLike() {
        scope.launch {
            val songToToggle = liveCurrentSong()
            songToToggle?.let {
                val song = it.song.toggleLike()
                database.query {
                    update(song)
                }
                if (dataStore.get(LastFMUseSendLikes, false) && LastFM.sessionKey != null) {
                    launch(Dispatchers.IO) {
                        runCatching {
                            LastFM.setLoveStatus(
                                artist = it.artists.firstOrNull()?.name.orEmpty(),
                                track = song.title,
                                love = song.liked,
                            )
                        }
                    }
                }
                currentMediaMetadata.value = player.currentMetadata
            }
        }
    }

    fun toggleStartRadio() {
        startRadioSeamlessly()
    }

    /** Loudness normalization + spatial audio + the system audio-effect-session broadcast, grouped as one effects chain. */
    inner class AudioEffectsChain {

        fun applySpatialAudio() {
            val on = cachedSpatialAudio && !cachedBitPerfect
            playerSpatialProcessors.values.forEach { it.enabled = on }
        }

        fun applyVocalSuppression() {
            val amount = if (cachedBitPerfect) 0f else cachedVocalSuppression
            playerVocalSuppressionProcessors.values.forEach { it.amount = amount }
        }

        fun setupLoudnessEnhancer() {
            val audioSessionId = player.audioSessionId

            if (audioSessionId == C.AUDIO_SESSION_ID_UNSET || audioSessionId <= 0) {
                Timber.tag(TAG).w("setupLoudnessEnhancer: invalid audioSessionId ($audioSessionId), cannot create effect yet")
                return
            }

            if (loudnessEnhancer == null) {
                try {
                    loudnessEnhancer = LoudnessEnhancer(audioSessionId)
                    Timber.tag(TAG).d("LoudnessEnhancer created for sessionId=$audioSessionId")
                } catch (e: Exception) {
                    reportException(e)
                    loudnessEnhancer = null
                    return
                }
            }

            scope.launch {
                try {
                    val currentMediaId = withContext(Dispatchers.Main) {
                        player.currentMediaItem?.mediaId
                    }

                    val normalizeAudio = withContext(Dispatchers.IO) {
                        dataStore.data.map { it[AudioNormalizationKey] ?: true }.first()
                    }

                    if (normalizeAudio && currentMediaId != null) {
                        val format = withContext(Dispatchers.IO) {
                            database.format(currentMediaId).first()
                        }

                        Timber.tag(TAG).d("Audio normalization enabled: $normalizeAudio")
                        Timber.tag(TAG).d("Format loudnessDb: ${format?.loudnessDb}, perceptualLoudnessDb: ${format?.perceptualLoudnessDb}")

                        val loudness = format?.loudnessDb ?: format?.perceptualLoudnessDb

                        withContext(Dispatchers.Main) {
                            if (loudness != null) {
                                val loudnessDb = loudness.toFloat()
                                val targetGain = (-loudnessDb * 100).toInt()
                                val clampedGain = targetGain.coerceIn(MIN_GAIN_MB, MAX_GAIN_MB)

                                Timber.tag(TAG).d("Calculated raw normalization gain: $targetGain mB (from loudness: $loudnessDb)")

                                try {
                                    loudnessEnhancer?.setTargetGain(clampedGain)
                                    loudnessEnhancer?.enabled = true
                                    Timber.tag(TAG).i("LoudnessEnhancer gain applied: $clampedGain mB")
                                } catch (e: Exception) {
                                    Timber.tag(TAG).e(e, "Failed to apply loudness enhancement")
                                    reportException(e)
                                    releaseLoudnessEnhancer()
                                }
                            } else {
                                loudnessEnhancer?.enabled = false
                                Timber.tag(TAG).w("Normalization enabled but no loudness data available - no normalization applied")
                            }
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            loudnessEnhancer?.enabled = false
                            Timber.tag(TAG).d("setupLoudnessEnhancer: normalization disabled or mediaId unavailable")
                        }
                    }
                } catch (e: Exception) {
                    reportException(e)
                    releaseLoudnessEnhancer()
                }
            }
        }

        fun releaseLoudnessEnhancer() {
            try {
                loudnessEnhancer?.release()
                Timber.tag(TAG).d("LoudnessEnhancer released")
            } catch (e: Exception) {
                reportException(e)
                Timber.tag(TAG).e(e, "Error releasing LoudnessEnhancer: ${e.message}")
            } finally {
                loudnessEnhancer = null
            }
        }

        fun openAudioEffectSession() {
            if (isAudioEffectSessionOpened) return
            isAudioEffectSessionOpened = true
            setupLoudnessEnhancer()
            sendBroadcast(
                Intent(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                    putExtra(AudioEffect.EXTRA_CONTENT_TYPE, AudioEffect.CONTENT_TYPE_MUSIC)
                },
            )
        }

        fun closeAudioEffectSession() {
            if (!isAudioEffectSessionOpened) return
            isAudioEffectSessionOpened = false
            releaseLoudnessEnhancer()
            sendBroadcast(
                Intent(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION).apply {
                    putExtra(AudioEffect.EXTRA_AUDIO_SESSION, player.audioSessionId)
                    putExtra(AudioEffect.EXTRA_PACKAGE_NAME, packageName)
                },
            )
        }
    }

    private val audioEffects = AudioEffectsChain()

    private var previousMediaItemIndex = C.INDEX_UNSET

    // Player callbacks use this snapshot instead of blocking Main on DataStore reads.
    @Volatile private var cachedRepeatMode = REPEAT_MODE_OFF
    @Volatile private var cachedAutoLoadMore = true
    @Volatile private var cachedDisableLoadMoreWhenRepeatAll = false
    @Volatile private var cachedPersistentQueue = true

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        Timber.tag(TAG).d(
            "SKIP_DEBUG onMediaItemTransition: reason=$reason (0=REPEAT,1=AUTO,2=SEEK,3=PLAYLIST_CHANGED) " +
                "newIndex=${player.currentMediaItemIndex} newId=${mediaItem?.mediaId} " +
                "prevTrackedIndex=$previousMediaItemIndex mediaItemCount=${player.mediaItemCount} " +
                "repeatMode=${player.repeatMode} shuffle=${player.shuffleModeEnabled}"
        )
        // Force Repeat One if the player ignored it and auto-advanced
        if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO) {
            val repeatMode = cachedRepeatMode
            if (repeatMode == REPEAT_MODE_ONE &&
                previousMediaItemIndex != C.INDEX_UNSET &&
                previousMediaItemIndex != player.currentMediaItemIndex) {

                Timber.tag(TAG).d("SKIP_DEBUG onMediaItemTransition: forcing repeat-one seekTo($previousMediaItemIndex)")
                player.seekTo(previousMediaItemIndex, 0)
            }
        }
        previousMediaItemIndex = player.currentMediaItemIndex
        // Normal track changes use the default artwork dissolve; DJ handoffs bypass this listener.
        _crossfadeDurationMs.value = 0L
        if (player.isPlaying) audioResources.acquireWifiLock()

        djEngine.onTrackChanged(player, mediaItem?.mediaId)

        audioEffects.setupLoudnessEnhancer()

        updateScrobblingForCurrentTrack()

        loadMoreQueueIfNeeded(reason)

        if (cachedPersistentQueue) {
            queueRepository.saveQueueToDisk()
        }
    }

    private fun updateScrobblingForCurrentTrack() {
        scrobbleManager?.onSongStop()
        scrobbleCoordinator.checkAndSubmitListenBrainzFinished()

        if (!player.playWhenReady || player.playbackState != Player.STATE_READY) return

        scrobbleManager?.onSongStart(player.currentMetadata, duration = player.duration)
        val mediaId = player.currentMediaItem?.mediaId ?: return

        if (listenBrainzCurrentMediaId != mediaId) {
            listenBrainzCurrentMediaId = mediaId
            listenBrainzCurrentStartTs = System.currentTimeMillis()
        }
        scrobbleCoordinator.checkAndSubmitListenBrainzPlayingNow(mediaId)
    }

    private fun loadMoreQueueIfNeeded(reason: Int) {
        val shouldLoadMore = cachedAutoLoadMore &&
            reason != Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT &&
            remainingInPlayOrder(LOAD_MORE_LOOKAHEAD) <= LOAD_MORE_LOOKAHEAD &&
            currentQueue.hasNextPage() &&
            !(cachedDisableLoadMoreWhenRepeatAll && player.repeatMode == REPEAT_MODE_ALL)
        if (!shouldLoadMore) return

        scope.launch(SilentHandler) {
            val mediaItems = withContext(Dispatchers.IO) {
                currentQueue.nextPage()
            }
            if (player.playbackState != STATE_IDLE && mediaItems.isNotEmpty()) {
                // Appending preserves ExoPlayer's shuffle order; rebuilding it moves the current item and loses played entries.
                player.addMediaItems(mediaItems)
            }
        }
    }

    override fun onPlaybackStateChanged(
        @Player.State playbackState: Int,
    ) {
        if (playbackState == Player.STATE_ENDED) {
            val repeatMode = cachedRepeatMode
            if (repeatMode == REPEAT_MODE_ALL && player.mediaItemCount > 0) {
                player.seekTo(0, 0)
                player.prepare()
                player.play()
            }
        }

        if (cachedPersistentQueue && !isSilenceSkipping) {
            queueRepository.saveQueueToDisk()
        }

        if (playbackState == Player.STATE_READY) {
            consecutivePlaybackErr = 0
            retryCount = 0
            waitingForNetworkConnection.value = false
            retryJob?.cancel()

            player.currentMediaItem?.mediaId?.let { mediaId ->
                errorRecovery.resetRetryCount(mediaId)
                Timber.tag(TAG).d("Playback successful for $mediaId, reset retry count")
            }

            // Bit Perfect: re-request the hardware mixer for every track, since a
            // sample-rate change needs a fresh negotiation with AudioFlinger.
            if (cachedBitPerfect) {
                player.audioFormat?.let { format ->
                    BitPerfectAudio.requestBitPerfectMixer(this, format.sampleRate, format.pcmEncoding)
                }
            }
        }

        if (playbackState == Player.STATE_IDLE || playbackState == Player.STATE_ENDED) {
            scrobbleManager?.onSongStop()
            scrobbleCoordinator.checkAndSubmitListenBrainzFinished()
        }
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (playWhenReady) {
            when (audioResources.requestAudioFocus()) {
                FocusRequestResult.GRANTED -> {
                    pendingFocusStart = false
                    wasPlayingBeforeAudioFocusLoss = false
                }

                FocusRequestResult.DELAYED -> {
                    pendingFocusStart = true
                    audioResources.pauseForAudioFocus()
                }

                FocusRequestResult.DENIED -> {
                    pendingFocusStart = false
                    wasPlayingBeforeAudioFocusLoss = false
                    audioResources.pauseForAudioFocus()
                }
            }
        } else if (
            reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST &&
            !isPausingForAudioFocus
        ) {
            wasPlayingBeforeAudioFocusLoss = false
            audioResources.cancelDelayedFocusStart()
        }

        if (playWhenReady && !player.playWhenReady) return

        if (reason == Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST) {
            if (playWhenReady) {
                isPausedByVolumeMute = false
            }

            if (!playWhenReady && !isPausedByVolumeMute) {
                wasPlayingBeforeVolumeMute = false
            }
        }

        if (playWhenReady) {
            audioEffects.setupLoudnessEnhancer()
        }
        scheduleIdleStop(playWhenReady)
    }

    /** Stops an unbound paused service after [IdleStopMs]; the persisted queue restores on reopen. */
    private fun scheduleIdleStop(playWhenReady: Boolean) {
        idleStopJob?.cancel()
        if (playWhenReady) return
        idleStopJob = scope.launch {
            delay(IdleStopMs)
            val idle = !player.playWhenReady && uiClients == 0 &&
                !wasPlayingBeforeAudioFocusLoss
            if (idle) {
                Timber.tag(TAG).d("Paused and unbound for ${IdleStopMs / 60_000}m, stopping service")
                if (dataStore.get(PersistentQueueKey, true)) queueRepository.saveQueueToDisk()
                pauseAllPlayersAndStopSelf()
            }
        }
    }

    override fun onEvents(
        player: Player,
        events: Player.Events,
    ) {
        if (events.containsAny(
                Player.EVENT_PLAYBACK_STATE_CHANGED,
                Player.EVENT_PLAY_WHEN_READY_CHANGED
            )
        ) {
            val isBufferingOrReady =
                player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_READY
            if (isBufferingOrReady && player.playWhenReady && hasAudioFocus) {
                audioEffects.openAudioEffectSession()
            } else {
                audioEffects.closeAudioEffectSession()
            }
        }
        if (events.containsAny(EVENT_TIMELINE_CHANGED, EVENT_POSITION_DISCONTINUITY)) {
            currentMediaMetadata.value = player.currentMetadata
        }

        if (events.containsAny(Player.EVENT_IS_PLAYING_CHANGED)) {
            notificationUpdater.updateWidgetUI(player.isPlaying)
            notificationUpdater.restartWidgetProgressTicker()
            if (player.isPlaying) {
                audioResources.acquireWifiLock()
            } else {
                audioResources.releaseWifiLock()
            }
            scrobbleManager?.onPlayerStateChanged(player.isPlaying, player.currentMetadata, duration = player.duration)

            if (player.isPlaying) {
                player.currentMediaItem?.mediaId?.let { mediaId ->
                    if (listenBrainzCurrentMediaId != mediaId) {
                        scrobbleCoordinator.checkAndSubmitListenBrainzFinished()
                        listenBrainzCurrentMediaId = mediaId
                        listenBrainzCurrentStartTs = System.currentTimeMillis()
                    }
                    scrobbleCoordinator.checkAndSubmitListenBrainzPlayingNow(mediaId)
                }
            }
        }

    }

    override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
        notificationUpdater.updateNotification()
        if (shuffleModeEnabled) {
            if (player.mediaItemCount == 0) return

            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            val currentIndex = player.currentMediaItemIndex
            val totalCount = player.mediaItemCount

            applyShuffleOrder(currentIndex, totalCount, shufflePlaylistFirst)
        }

        if (dataStore.get(RememberShuffleAndRepeatKey, true)) {
            scope.launch {
                dataStore.edit { settings ->
                    settings[ShuffleModeKey] = shuffleModeEnabled
                }
            }
        }

        if (dataStore.get(PersistentQueueKey, true)) {
            queueRepository.saveQueueToDisk()
        }
    }

    override fun onRepeatModeChanged(repeatMode: Int) {
        notificationUpdater.updateNotification()
        scope.launch {
            dataStore.edit { settings ->
                settings[RepeatModeKey] = repeatMode
            }
        }

        if (dataStore.get(PersistentQueueKey, true)) {
            queueRepository.saveQueueToDisk()
        }
    }

    /** Counts upcoming items in playback order, stopping once there are more than [limit]. */
    private fun remainingInPlayOrder(limit: Int): Int {
        val timeline = player.currentTimeline
        if (timeline.isEmpty) return 0
        val shuffled = player.shuffleModeEnabled
        var index = player.currentMediaItemIndex
        var remaining = 0
        while (remaining <= limit) {
            index = timeline.getNextWindowIndex(index, Player.REPEAT_MODE_OFF, shuffled)
            if (index == C.INDEX_UNSET) break
            remaining++
        }
        return remaining
    }

    /** Keeps the current item first and can shuffle original and added items separately. */
    private fun applyShuffleOrder(
        currentIndex: Int,
        totalCount: Int,
        shufflePlaylistFirst: Boolean
    ) {
        if (totalCount == 0) return

        val indices = if (shufflePlaylistFirst && originalQueueSize in 1 until totalCount) {
            createPlaylistFirstShuffleOrder(currentIndex, totalCount)
        } else {
            createShuffleOrderWithCurrentFirst(currentIndex, totalCount)
        }
        player.setShuffleOrder(DefaultShuffleOrder(indices, System.currentTimeMillis()))
    }

    private fun createPlaylistFirstShuffleOrder(currentIndex: Int, totalCount: Int): IntArray {
        val originalIndices = (0 until originalQueueSize)
            .filterTo(mutableListOf()) { it != currentIndex }
        val addedIndices = (originalQueueSize until totalCount)
            .filterTo(mutableListOf()) { it != currentIndex }

        originalIndices.shuffle()
        addedIndices.shuffle()

        val shuffledIndices = IntArray(totalCount)
        var nextIndex = 0
        shuffledIndices[nextIndex++] = currentIndex

        originalIndices.forEach { shuffledIndices[nextIndex++] = it }
        addedIndices.forEach { shuffledIndices[nextIndex++] = it }
        return shuffledIndices
    }

    private fun createShuffleOrderWithCurrentFirst(currentIndex: Int, totalCount: Int): IntArray {
        val shuffledIndices = IntArray(totalCount) { it }
        shuffledIndices.shuffle()

        val currentIndexInOrder = shuffledIndices.indexOf(currentIndex)
        if (currentIndexInOrder != -1) {
            val firstIndex = shuffledIndices[0]
            shuffledIndices[0] = shuffledIndices[currentIndexInOrder]
            shuffledIndices[currentIndexInOrder] = firstIndex
        }
        return shuffledIndices
    }

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
        super.onPlaybackParametersChanged(playbackParameters)

        // Offloaded AudioTrack may ignore or reject non-default speed and pitch.
        val defaultParameters = playbackParameters.speed == 1f && playbackParameters.pitch == 1f
        player.setOffloadEnabled(cachedOffloadEnabled && defaultParameters)
    }

    /** Add HTTP, network, retry, source, and cause details to playback error logs. */
    private fun logPlayerErrorDetail(error: PlaybackException, mediaId: String?) {
        val httpCode = PlaybackErrorClassifier.getHttpResponseCode(error)?.toString() ?: "-"
        val transport = runCatching {
            val net = connectivityManager.activeNetwork
            if (net == null) "none"
            else {
                val caps = connectivityManager.getNetworkCapabilities(net)
                when {
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "WIFI"
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "CELLULAR"
                    caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "ETHERNET"
                    else -> "OTHER"
                }
            }
        }.getOrDefault("unknown")
        val source = player.currentMediaItem?.localConfiguration?.uri?.let { uri ->
            "${uri.host ?: "?"}(${uri.path?.length ?: 0}p,${uri.query?.length ?: 0}q)"
        } ?: "?"
        val retries = mediaId?.let { currentMediaIdRetryCount[it] ?: 0 } ?: 0

        Timber.tag(TAG).w(
            "Player error detail | mediaId=$mediaId | errorCode=${error.errorCode} | http=$httpCode | " +
                "net=${if (isNetworkConnected.value) "connected" else "disconnected"}/$transport | " +
                "retries=$retries | source=$source | state=${player.playbackState}"
        )
        var cause = error.cause
        var depth = 0
        while (cause != null && depth < 6) {
            Timber.tag(TAG).w("Player error cause #$depth: ${cause::class.java.simpleName}: ${cause.message}")
            cause = cause.cause
            depth++
        }
    }

    override fun onPlayerError(error: PlaybackException) {
        super.onPlayerError(error)

        // Ignore delayed errors after the player has been released.
        if (!playerInitialized.value) {
            Timber.tag(TAG).e(error, "Player error occurred but player not initialized")
            return
        }

        val mediaId = player.currentMediaItem?.mediaId
        logPlayerErrorDetail(error, mediaId)
        Timber.tag(TAG).w(error, "Player error occurred for $mediaId: errorCode=${error.errorCode}, message=${error.message}")
        reportException(error)

        if (mediaId != null && errorRecovery.hasExceededRetryLimit(mediaId)) {
            Timber.tag(TAG).w("Song $mediaId has exceeded retry limit, skipping")
            errorRecovery.markSongAsFailed(mediaId)
            errorRecovery.handleFinalFailure()
            return
        }

        val errorType = PlaybackErrorClassifier.classify(error, isNetworkConnected.value)
        if (handleClassifiedError(errorType, error, mediaId)) return

        // For IO_UNSPECIFIED and IO_BAD_HTTP_STATUS, try recovery first
        if (error.errorCode == PlaybackException.ERROR_CODE_IO_UNSPECIFIED ||
            error.errorCode == PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS) {
            Timber.tag(TAG).d("IO error detected (${error.errorCode}), attempting recovery")
            errorRecovery.handleGenericIOError(mediaId)
            return
        }

        if (dataStore.get(AutoSkipNextOnErrorKey, false)) {
            Timber.tag(TAG).d("Auto-skipping to next track due to unrecoverable error")
            errorRecovery.skipOnError()
        } else {
            Timber.tag(TAG).d("Stopping playback due to unrecoverable error")
            errorRecovery.stopOnError()
        }
    }

    private fun handleClassifiedError(
        errorType: PlaybackErrorType,
        error: PlaybackException,
        mediaId: String?,
    ): Boolean {
        when (errorType) {
            PlaybackErrorType.DecoderReclaim -> {
                Timber.tag(TAG).d("FLAC decoder reclaim detected, retrying with fresh codec")
                errorRecovery.handleDecoderReclaimError(mediaId)
            }
            PlaybackErrorType.AudioRenderer -> {
                Timber.tag(TAG).d("AudioTrack error detected (${error.errorCode}), performing safe recovery")
                errorRecovery.handleAudioRendererError(mediaId)
            }
            PlaybackErrorType.LocalFileUnavailable,
            PlaybackErrorType.CorruptOrUnsupportedMedia -> {
                Timber.tag(TAG).w("Unrecoverable local media error ($errorType) for $mediaId")
                if (mediaId != null) errorRecovery.markSongAsFailed(mediaId)
                errorRecovery.handleFinalFailure()
            }
            PlaybackErrorType.RangeNotSatisfiable,
            PlaybackErrorType.PageReload,
            PlaybackErrorType.ExpiredUrl -> {
                Timber.tag(TAG).d("Stream error (${errorType}) detected, attempting IO recovery")
                errorRecovery.handleGenericIOError(mediaId)
            }
            PlaybackErrorType.Parsing -> {
                val isLocalUri = mediaId?.startsWith("content://") == true ||
                    mediaId?.startsWith("file://") == true ||
                    mediaId?.startsWith("/storage/") == true ||
                    player.currentMediaItem?.localConfiguration?.uri?.scheme in setOf("content", "file")
                if (isLocalUri) {
                    Timber.tag(TAG).w("Corrupt local audio container for $mediaId; skipping retry loop")
                    if (mediaId != null) errorRecovery.markSongAsFailed(mediaId)
                    errorRecovery.handleFinalFailure()
                } else {
                    Timber.tag(TAG).d("Parsing error detected (${error.errorCode}), attempting recovery")
                    errorRecovery.handleParsingError(mediaId)
                }
            }
            PlaybackErrorType.Network -> handleNetworkPlaybackError()
            PlaybackErrorType.Other -> return false
        }
        return true
    }

    private fun handleNetworkPlaybackError() {
        // Skip to on-device tracks when offline instead of waiting on a LAN share.
        if (!isNetworkConnected.value) {
            val nextLocalIndex = nextLocalPlayableIndex()
            if (nextLocalIndex != null) {
                Timber.tag(TAG).d("Offline: skipping to local track $nextLocalIndex")
                player.seekTo(nextLocalIndex, 0L)
                player.prepare()
                player.play()
                return
            }
        }

        Timber.tag(TAG).d("Network-related error detected, waiting for connection")
        errorRecovery.waitOnNetworkError()
    }

    private fun nextLocalPlayableIndex(): Int? {
        val count = player.mediaItemCount
        var i = player.currentMediaItemIndex
        repeat(count - 1) {
            i = if (i + 1 < count) i + 1 else if (player.repeatMode == Player.REPEAT_MODE_ALL) 0 else return null
            val id = player.getMediaItemAt(i).mediaId
            if (id.startsWith("content://") || id.startsWith("file://") || id.startsWith("/storage/")) return i
        }
        return null
    }

    override fun onDeviceVolumeChanged(volume: Int, muted: Boolean) {
        super.onDeviceVolumeChanged(volume, muted)
        val pauseOnMute = dataStore.get(PauseOnMute, false)

        if ((volume == 0 || muted) && pauseOnMute) {
            if (player.isPlaying) {
                wasPlayingBeforeVolumeMute = true
                isPausedByVolumeMute = true
                player.pause()
            }
        } else if (volume > 0 && !muted && pauseOnMute) {
            if (wasPlayingBeforeVolumeMute && !player.isPlaying) {
                wasPlayingBeforeVolumeMute = false
                isPausedByVolumeMute = false
                player.play()
            }
        }
    }

    /** Builds local/LAN data sources and Media3 renderers with the active DSP/DJ chain. */
    inner class AudioSourceBuilder {

        fun createDataSourceFactory(): DataSource.Factory =
            networkRepository.dataSourceFactory(DefaultDataSource.Factory(this@MusicService))

        fun createMediaSourceFactory(): androidx.media3.exoplayer.source.MediaSource.Factory =
            DefaultMediaSourceFactory(
                createDataSourceFactory(),
                // Constant-bitrate seeking gives an MP3 without a Xing/VBR header a duration and a
                // working seek bar straight away. Index seeking (the old flag here) leaves the
                // duration unknown until the whole file has been read, which hid the scrubber.
                androidx.media3.extractor.DefaultExtractorsFactory()
                    .setConstantBitrateSeekingEnabled(true),
            )

        fun createRenderersFactory(
            eqProcessor: CustomEqualizerAudioProcessor,
            silenceProcessor: SilenceDetectorAudioProcessor,
            bpmAnalyzer: TrackAnalyzerAudioProcessor,
            spatialProcessor: SpatialAudioProcessor,
            vocalSuppressionProcessor: VocalSuppressionAudioProcessor,
            djFilter: DjFilterAudioProcessor,
            djTail: DjTailAudioProcessor,
            djDelay: DelayAudioProcessor,
            djAmbience: DjAmbienceAudioProcessor,
            analysisOnly: Boolean,
        ) =
            object : NextRenderersFactory(this@MusicService) {
                override fun buildAudioSink(
                    context: Context,
                    enableFloatOutput: Boolean,
                    enableAudioTrackPlaybackParams: Boolean,
                ) = DefaultAudioSink
                    .Builder(this@MusicService)
                    // The effects chain consumes PCM16, including decoded hi-res.
                    .setEnableFloatOutput(false)
                    .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
                    .setAudioProcessorChain(
                        DefaultAudioSink.DefaultAudioProcessorChain(
                            if (analysisOnly) arrayOf(bpmAnalyzer) else arrayOf(
                                eqProcessor,
                                silenceProcessor,
                                bpmAnalyzer,
                                // After the analyzer so BPM/key detection still sees the
                                // untouched mix, before the DJ chain so a transition's own
                                // low end isn't fed back in by the widener's crossfeed.
                                spatialProcessor,
                                vocalSuppressionProcessor,
                                djFilter,
                                djTail,
                                djDelay,
                                djAmbience,
                            ),
                            SilenceSkippingAudioProcessor(2_000_000, 20_000, 256),
                            SonicAudioProcessor(),
                        ),
                    ).build().let { sink ->
                        com.convx.music.playback.audio.PcmProcessingAudioSink(sink) { cachedOffloadEnabled && !analysisOnly }
                    }
            }.apply {
                // Platform MediaCodec has no ALAC decoder on most Android devices (no AOSP
                // one exists at all). ON tries the platform decoder first per format and
                // only falls back to NextRenderersFactory's bundled FfmpegAudioRenderer —
                // this doesn't change decode for any format a device already handles.
                setExtensionRendererMode(androidx.media3.exoplayer.DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON)
                setEnableDecoderFallback(true)
            }
    }

    private val audioSourceBuilder = AudioSourceBuilder()

    // Flag to prevent queue saving during silence skip operations
    private var isSilenceSkipping = false

    /** Jumps past long continuous silent spans when instant silence-skipping is enabled. */
    inner class SilenceSkipGuard {

        fun handleLongSilenceDetected() {
            if (!instantSilenceSkipEnabled.value) return
            if (silenceSkipJob?.isActive == true) return

            silenceSkipJob = scope.launch {
                // Debounce so short fades or transitions do not trigger a jump.
                delay(200)
                performInstantSilenceSkip()
            }
        }

        private suspend fun performInstantSilenceSkip() {
            val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: return
            if (duration <= INSTANT_SILENCE_SKIP_STEP_MS) return

            isSilenceSkipping = true
            try {
                var hops = 0
                val silenceProcessor = playerSilenceProcessors[player] ?: return
                while (coroutineContext.isActive && instantSilenceSkipEnabled.value && silenceProcessor.isCurrentlySilent()) {
                    val current = player.currentPosition
                    val target = (current + INSTANT_SILENCE_SKIP_STEP_MS).coerceAtMost(duration - 500)

                    if (target <= current) break

                    // Reset silence tracking before seeking to prevent immediate re-trigger
                    silenceProcessor.resetTracking()
                    player.seekTo(target)
                    hops++

                    if (hops >= 60 || target >= duration - 1000) break

                    delay(INSTANT_SILENCE_SKIP_SETTLE_MS)
                }
                if (hops > 0) {
                    Timber.tag(TAG).d("Silence skip: jumped $hops times")
                }
            } finally {
                isSilenceSkipping = false
            }
        }
    }

    private val silenceSkipGuard = SilenceSkipGuard()

    // PlaybackStatsListener throws a message-less IllegalArgumentException when an event
    // carries an older timestamp than its tracker state; a stats glitch must not kill playback.
    private fun crashSafe(inner: AnalyticsListener): AnalyticsListener =
        java.lang.reflect.Proxy.newProxyInstance(
            AnalyticsListener::class.java.classLoader,
            arrayOf(AnalyticsListener::class.java),
        ) { _, method, args ->
            try {
                method.invoke(inner, *(args ?: emptyArray()))
            } catch (e: java.lang.reflect.InvocationTargetException) {
                if (e.cause !is IllegalArgumentException) throw e.cause ?: e
                reportException(e.cause!!)
                null
            }
        } as AnalyticsListener


    override fun onPlaybackStatsReady(
        eventTime: AnalyticsListener.EventTime,
        playbackStats: PlaybackStats,
    ) {
        val mediaItem = eventTime.timeline.getWindow(eventTime.windowIndex, Timeline.Window()).mediaItem
        val historyDurationMs = dataStore[HistoryDuration]?.times(1000f) ?: 30000f

        if (playbackStats.totalPlayTimeMs >= historyDurationMs &&
            !dataStore.get(PauseListenHistoryKey, false)
        ) {
            // Coalesce stats writes: ExoPlayer reports every ~3s during playback, so
            // without this a single listen inserts a history row and invalidates the
            // events/stats flows every few seconds. Writing at most once per song per
            // window keeps short listens recorded while stopping the storm.
            val now = System.currentTimeMillis()
            if (now - (lastStatsWriteAt[mediaItem.mediaId] ?: 0L) >= STATS_WRITE_INTERVAL_MS) {
                lastStatsWriteAt[mediaItem.mediaId] = now
                database.query {
                    incrementTotalPlayTime(mediaItem.mediaId, playbackStats.totalPlayTimeMs)
                    try {
                        insert(
                            Event(
                                songId = mediaItem.mediaId,
                                timestamp = LocalDateTime.now(),
                                playTime = playbackStats.totalPlayTimeMs,
                            ),
                        )
                    } catch (_: SQLException) {
                        // A track can be removed between playback and event insertion.
                    }
                }
            }
        }
    }

    /** Queue/automix/player-state persistence to disk, used for crash/kill recovery. */
    inner class QueuePersistenceRepository {
        private var lastWrittenSnapshot: PersistedPlaybackSnapshot? = null

        // ExoPlayer state must be read on its application thread (main), so only the
        // file IO goes to the worker; the single worker serializes saves so a newer
        // snapshot can't be overwritten by an older one.
        fun saveQueueToDisk() {
            val queue = currentQueue
            val title = queueTitle
            val items = player.mediaItems.mapNotNull { it.metadata }
            if (items.isEmpty()) return
            val mediaItemIndex = player.currentMediaItemIndex
            val position = player.currentPosition
            val playWhenReady = player.playWhenReady
            val repeatMode = player.repeatMode
            val shuffleModeEnabled = player.shuffleModeEnabled
            val volume = playerVolume.value
            val playbackState = player.playbackState
            val automixItems = automixItems.value.mapNotNull { it.metadata }
            val snapshot = PersistedPlaybackSnapshot(
                queue = queue.toPersistQueue(title, items, mediaItemIndex, position),
                automix = PersistQueue(
                    title = "automix",
                    items = automixItems,
                    mediaItemIndex = 0,
                    position = 0,
                ),
                playerState = PersistPlayerState(
                    playWhenReady = playWhenReady,
                    repeatMode = repeatMode,
                    shuffleModeEnabled = shuffleModeEnabled,
                    volume = volume,
                    currentPosition = position,
                    currentMediaItemIndex = mediaItemIndex,
                    playbackState = playbackState,
                ),
            )
            persistenceScope.launch {
                persistQueueToDisk(snapshot)
            }
        }

        private fun persistQueueToDisk(snapshot: PersistedPlaybackSnapshot) {
            if (snapshot == lastWrittenSnapshot) return
            try {
                PlaybackStateStore(filesDir.resolve(PERSISTENT_PLAYBACK_SNAPSHOT_FILE)).write(snapshot)
                lastWrittenSnapshot = snapshot
                deleteLegacyPlaybackFiles()
                Timber.tag(TAG).d("Playback state saved successfully")
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to save playback state")
                reportException(e)
            }
        }
    }

    private val queueRepository = QueuePersistenceRepository()

    override fun onDestroy() {
        isRunning = false

        audioManager.unregisterAudioDeviceCallback(audioDeviceCallback)
        if (dataStore.get(PersistentQueueKey, true)) {
            queueRepository.saveQueueToDisk()
        }
        connectivityObserver.unregister()
        audioResources.releaseWifiLock()
        audioResources.abandonAudioFocus()
        audioEffects.releaseLoudnessEnhancer()
        djEngine.release()
        crossfade?.release()
        trackAnalyzer?.release()
        sparePlayer?.let(::releasePlaybackPlayer)
        sparePlayer = null
        mediaSession.release()
        mediaLibrarySessionCallback.release()
        player.removeListener(this)
        player.removeListener(sleepTimer)
        releasePlaybackPlayer(player)
        scope.cancel()
        persistenceScope.cancel()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?) = super.onBind(intent) ?: binder.also { uiClients++ }

    override fun onUnbind(intent: Intent?): Boolean {
        // Media3's own actions (session / browser interfaces) are not the app UI.
        if (intent?.action == null) uiClients = (uiClients - 1).coerceAtLeast(0)
        super.onUnbind(intent)
        return true
    }

    override fun onRebind(intent: Intent?) {
        if (intent?.action == null) uiClients++
        super.onRebind(intent)
    }

    /** Stops playback when the app task is removed, if the user enabled that setting. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        if (dataStore.get(StopMusicOnTaskClearKey, false)) {
            Timber.tag(TAG).d("Task removed with stop-on-task-clear enabled, stopping playback")
            player.stop()
            // Release the session so its notification also disappears.
            stopSelf()
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    /** Catches platform refusal when Media3 starts foreground service from a notification callback. */
    override fun startForegroundService(service: Intent): ComponentName? =
        try {
            super.startForegroundService(service)
        } catch (e: Exception) {
            Timber.tag(TAG).w(e, "Foreground service start refused by platform")
            null
        }

    // Media3 calls final Service.startForeground asynchronously; CrashHandler handles its fallback.

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            MusicWidgetReceiver.ACTION_PLAY_PAUSE -> {
                if (player.isPlaying) player.pause() else player.play()
                notificationUpdater.updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_LIKE -> {
                toggleLike()
            }
            MusicWidgetReceiver.ACTION_NEXT -> {
                player.seekToNext()
                notificationUpdater.updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_PREVIOUS -> {
                player.seekToPrevious()
                notificationUpdater.updateWidgetUI(player.isPlaying)
            }
            MusicWidgetReceiver.ACTION_UPDATE_WIDGET -> {
                notificationUpdater.updateWidgetUI(player.isPlaying)
                // A widget was just placed or resized; pick up the new widget set.
                notificationUpdater.restartWidgetProgressTicker()
            }
        }

        return super.onStartCommand(intent, flags, startId)
    }

    /**
     * Repaints widget progress only while playing, with the screen on, and when a
     * widget that displays progress is placed. Lyrics widgets keep the fast rate
     * so their highlighted line follows playback.
     */
    private var widgetProgressJob: Job? = null

    /** Media-session custom layout + home-screen widget state — the "what the OS shows" surface. */
    inner class PlaybackNotificationUpdater {

        fun updateNotification() {
            mediaSession.setCustomLayout(
                listOf(
                    CommandButton
                        .Builder()
                        .setDisplayName(
                            getString(
                                if (currentSong.value?.song?.liked ==
                                    true
                                ) {
                                    R.string.action_remove_like
                                } else {
                                    R.string.action_like
                                },
                            ),
                        )
                        .setIconResId(if (currentSong.value?.song?.liked == true) R.drawable.ic_heart else R.drawable.ic_heart_outline)
                        .setSessionCommand(CommandToggleLike)
                        .setEnabled(currentSong.value != null)
                        .build(),
                    CommandButton
                        .Builder()
                        .setDisplayName(
                            getString(
                                when (player.repeatMode) {
                                    REPEAT_MODE_OFF -> R.string.repeat_mode_off
                                    REPEAT_MODE_ONE -> R.string.repeat_mode_one
                                    REPEAT_MODE_ALL -> R.string.repeat_mode_all
                                    else -> throw IllegalStateException()
                                },
                            ),
                        ).setIconResId(
                            when (player.repeatMode) {
                                REPEAT_MODE_OFF -> R.drawable.repeat
                                REPEAT_MODE_ONE -> R.drawable.repeat_one_on
                                REPEAT_MODE_ALL -> R.drawable.repeat_on
                                else -> throw IllegalStateException()
                            },
                        ).setSessionCommand(CommandToggleRepeatMode)
                        .build(),
                    CommandButton
                        .Builder()
                        .setDisplayName(getString(if (player.shuffleModeEnabled) R.string.action_shuffle_off else R.string.action_shuffle_on))
                        .setIconResId(if (player.shuffleModeEnabled) R.drawable.shuffle_on else R.drawable.shuffle)
                        .setSessionCommand(CommandToggleShuffle)
                        .build(),
                    CommandButton.Builder()
                        .setDisplayName(getString(R.string.start_radio))
                        .setIconResId(R.drawable.radio)
                        .setSessionCommand(CommandToggleStartRadio)
                        .setEnabled(currentSong.value != null)
                        .build(),
                ),
            )
        }

        fun restartWidgetProgressTicker() {
            widgetProgressJob?.cancel()
            widgetProgressJob = null
            if (!player.isPlaying) return

            val appWidgetManager = AppWidgetManager.getInstance(this@MusicService)
            fun placed(receiver: Class<*>) =
                appWidgetManager.getAppWidgetIds(ComponentName(this@MusicService, receiver)).isNotEmpty()
            val hasLyricsWidget = placed(LyricsWidgetReceiver::class.java)
            val hasProgressWidget = hasLyricsWidget ||
                placed(NowPlayingWidgetReceiver::class.java) ||
                placed(MusicWidgetReceiver::class.java)
            if (!hasProgressWidget) return
            val interval = if (hasLyricsWidget) WIDGET_LYRICS_INTERVAL_MS else WIDGET_PROGRESS_INTERVAL_MS

            widgetProgressJob = scope.launch {
                val powerManager = getSystemService(PowerManager::class.java)
                while (isActive) {
                    delay(interval)
                    if (powerManager?.isInteractive != false) {
                        updateWidgetUI(player.isPlaying)
                    }
                }
            }
        }

        fun updateWidgetUI(isPlaying: Boolean) {
            scope.launch {
                try {
                    val songData = currentSong.value
                    val song = songData?.song
                    val songTitle = song?.title ?: getString(R.string.no_song_playing)
                    val artistName = songData?.artists?.joinToString(", ") { it.name } ?: getString(R.string.tap_to_open)
                    val isLiked = songData?.song?.liked == true

                    widgetManager.updateWidgets(
                        title = songTitle,
                        artist = artistName,
                        artworkUri = song?.thumbnailUrl,
                        isPlaying = isPlaying,
                        isLiked = isLiked,
                        duration = if (player.duration != C.TIME_UNSET) player.duration else 0,
                        currentPosition = player.currentPosition,
                        mediaId = song?.id,
                    )
                } catch (e: Exception) {
                    reportException(e)
                }
            }
        }
    }

    private val notificationUpdater = PlaybackNotificationUpdater()

    private fun shareSong() {
        val songData = currentSong.value ?: return
        val artists = songData.artists.joinToString { it.name }
        val shareText = if (artists.isNotBlank()) "${songData.song.title} — $artists" else songData.song.title

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, shareText)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        startActivity(Intent.createChooser(shareIntent, null).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        })
    }

    override fun onPositionDiscontinuity(
        oldPosition: Player.PositionInfo,
        newPosition: Player.PositionInfo,
        reason: Int
    ) {
        Timber.tag(TAG).d(
            "SKIP_DEBUG onPositionDiscontinuity: reason=$reason (0=REPEAT,1=SEEK,2=SEEK_ADJUSTMENT," +
                "3=SKIP,4=REMOVE,5=INTERNAL) oldIndex=${oldPosition.mediaItemIndex} " +
                "oldId=${oldPosition.mediaItem?.mediaId} newIndex=${newPosition.mediaItemIndex} " +
                "newId=${newPosition.mediaItem?.mediaId}"
        )
    }

    /**
     * Called by [CrossfadeController] when the incoming track becomes audible.
     * Swaps the session player, audio focus, listeners, and notification onto
     * the incoming player and releases the old one.
     */
    private fun adoptPlayer(outgoing: ExoPlayer, incoming: ExoPlayer, fadeMs: Long) {
        outgoing.removeListener(this@MusicService)
        outgoing.removeListener(sleepTimer)

        incoming.addListener(this@MusicService)
        incoming.addListener(sleepTimer)
        sleepTimer.player = incoming

        player = incoming
        sparePlayer = outgoing
        _crossfadeDurationMs.value = fadeMs
        _playerFlow.value = player

        try {
            (mediaSession as MediaSession).player = player
        } catch (e: Exception) {
            timber.log.Timber.e(e, "Failed to swap player in MediaSession")
        }

        if (incoming.shuffleModeEnabled) {
            val shufflePlaylistFirst = dataStore.get(ShufflePlaylistFirstKey, false)
            applyShuffleOrder(incoming.currentMediaItemIndex, incoming.mediaItemCount, shufflePlaylistFirst)
        }

        sleepTimer.notifySongTransition()
        if (player.isPlaying) audioResources.acquireWifiLock()
    }

    private data class ScrobbleTrack(
        val title: String,
        val artistNames: String,
        val releaseName: String,
        val durationMs: Long,
    )

    private suspend fun scrobbleTrack(mediaId: String): ScrobbleTrack? {
        val metadata = player.mediaItems.find { it.mediaId == mediaId }?.metadata
        val song = if (metadata == null) database.song(mediaId).first() else null
        val title = metadata?.title ?: song?.song?.title ?: return null

        return ScrobbleTrack(
            title = title,
            artistNames = metadata?.artists?.joinToString(" & ") { it.name }
                ?: song?.artists?.joinToString(" & ").orEmpty(),
            releaseName = metadata?.album?.title ?: song?.album?.title.orEmpty(),
            durationMs = metadata?.duration?.takeIf { it != -1 }?.times(1000L)
                ?: song?.song?.duration?.takeIf { it != -1 }?.times(1000L)
                ?: 0L,
        )
    }

    /** Sends ListenBrainz "now playing" and finished listen events. */
    inner class ScrobbleCoordinator {

        fun updateListenBrainz(
            title: String,
            artistNames: String,
            releaseName: String,
            durationMs: Long,
            isFinished: Boolean,
            startMs: Long = 0,
            endMs: Long = 0,
            positionMs: Long = 0,
        ) {
            val cleanToken = listenBrainzToken.trim()
            if (!listenBrainzEnabled || cleanToken.isBlank()) return
            scope.launch {
                if (isFinished) {
                    com.convx.music.ui.screens.settings.ListenBrainzManager.submitFinished(
                        context = this@MusicService,
                        token = cleanToken,
                        title = title,
                        artistNames = artistNames,
                        releaseName = releaseName,
                        durationMs = durationMs,
                        startMs = startMs,
                        endMs = endMs
                    )
                } else {
                    com.convx.music.ui.screens.settings.ListenBrainzManager.submitPlayingNow(
                        context = this@MusicService,
                        token = cleanToken,
                        title = title,
                        artistNames = artistNames,
                        releaseName = releaseName,
                        durationMs = durationMs,
                        positionMs = positionMs
                    )
                }
            }
        }

        fun checkAndSubmitListenBrainzFinished() {
            listenBrainzCurrentMediaId?.let { mediaId ->
                val startTs = listenBrainzCurrentStartTs
                if (startTs > 0) {
                    scope.launch {
                        val track = scrobbleTrack(mediaId) ?: return@launch
                        updateListenBrainz(
                            title = track.title,
                            artistNames = track.artistNames,
                            releaseName = track.releaseName,
                            durationMs = track.durationMs,
                            isFinished = true,
                            startMs = startTs,
                            endMs = System.currentTimeMillis(),
                        )
                    }
                }
            }
            listenBrainzCurrentStartTs = 0L
            listenBrainzCurrentMediaId = null
        }

        fun checkAndSubmitListenBrainzPlayingNow(mediaId: String) {
            scope.launch {
                val track = scrobbleTrack(mediaId) ?: return@launch
                updateListenBrainz(
                    title = track.title,
                    artistNames = track.artistNames,
                    releaseName = track.releaseName,
                    durationMs = track.durationMs,
                    isFinished = false,
                )
            }
        }
    }

    private val scrobbleCoordinator = ScrobbleCoordinator()

    companion object {
        /** Widget progress repaint cadence while playing with the screen on. */
        private const val WIDGET_PROGRESS_INTERVAL_MS = 1000L
        private const val WIDGET_LYRICS_INTERVAL_MS = 200L
        /** Paused-and-backgrounded time before the service stops itself; matches Media3's FGS timeout. */
        private const val IdleStopMs = 10 * 60_000L
        const val ROOT = "root"
        const val SONG = "song"
        const val ARTIST = "artist"
        const val ALBUM = "album"
        const val PLAYLIST = "playlist"
        const val SEARCH = "search"
        const val SHUFFLE_ACTION = "__shuffle__"

        const val CHANNEL_ID = "music_channel_01"
        const val NOTIFICATION_ID = 888
        const val ERROR_CODE_NO_STREAM = 1000001
        const val PERSISTENT_QUEUE_FILE = "persistent_queue.data"
        const val PERSISTENT_AUTOMIX_FILE = "persistent_automix.data"
        const val PERSISTENT_PLAYER_STATE_FILE = "persistent_player_state.data"
        const val PERSISTENT_PLAYBACK_SNAPSHOT_FILE = "persistent_playback_snapshot.data"
        const val MAX_CONSECUTIVE_ERR = 5
        const val MAX_RETRY_COUNT = 10
        // Constants for audio normalization
        private const val MAX_GAIN_MB = 300 // Maximum gain in millibels (3 dB)
        private const val MIN_GAIN_MB = -1500 // Minimum gain in millibels (-15 dB)

        private const val TAG = "MusicService"

        /** Ceiling on how long an audio duck is allowed to hold the volume at
         *  20% before it is force-restored (some devices never send the gain). */
        private const val DUCK_RESTORE_MS = 10_000L

        @Volatile
        var isRunning = false
            private set
    }
}
