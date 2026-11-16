/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.playback

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM
import androidx.media3.common.Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM
import androidx.media3.common.Player.REPEAT_MODE_OFF
import androidx.media3.common.Player.STATE_ENDED
import androidx.media3.common.Timeline
import androidx.media3.exoplayer.ExoPlayer
import com.convx.music.db.MusicDatabase
import com.convx.music.extensions.currentMetadata
import com.convx.music.extensions.getCurrentQueueIndex
import com.convx.music.extensions.getQueueWindows
import com.convx.music.extensions.metadata
import com.convx.music.extensions.togglePlayPause
import com.convx.music.playback.MusicService.MusicBinder
import com.convx.music.constants.AskBeforeReplacingQueueKey
import com.convx.music.playback.queues.ListQueue
import com.convx.music.playback.queues.Queue
import com.convx.music.utils.dataStore
import com.convx.music.utils.get
import com.convx.music.utils.reportException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class PlayerConnection(
    context: Context,
    binder: MusicBinder,
    val database: MusicDatabase,
    scope: CoroutineScope,
) : Player.Listener {
    private val connectionJob = SupervisorJob(scope.coroutineContext[Job])
    private val scope = CoroutineScope(scope.coroutineContext + connectionJob)
    private companion object {
        private const val TAG = "PlayerConnection"
        private const val SKIP_COMMIT_DELAY_MS = 140L
    }

    val service = binder.service
    private val appContext = context.applicationContext
    private val playerReadinessFlow = service.isPlayerReady
    
    private fun getPlayerOrThrow(): ExoPlayer {
        return try {
            if (!playerReadinessFlow.value) {
                Timber.tag(TAG).w("Player accessed before service initialization complete; returning best-effort reference")
            }
            service.player
        } catch (e: UninitializedPropertyAccessException) {
            Timber.tag(TAG).e(e, "Fatal: player property accessed but not initialized")
            throw IllegalStateException("MusicService.player not initialized; possible race condition in service startup", e)
        }
    }

    val player: ExoPlayer
        get() = getPlayerOrThrow()

    val playbackState: MutableStateFlow<Int>
    private val playWhenReady: MutableStateFlow<Boolean>
    val isPlaying: kotlinx.coroutines.flow.StateFlow<Boolean>
    
    init {
        Timber.tag(TAG).d("PlayerConnection init: playerReady=${playerReadinessFlow.value}")
        
        val initialPlayer = try {
            getPlayerOrThrow()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during PlayerConnection initialization, using defaults")
            null
        }
        
        playbackState = MutableStateFlow(initialPlayer?.playbackState ?: Player.STATE_IDLE)
        playWhenReady = MutableStateFlow(initialPlayer?.playWhenReady ?: false)
        isPlaying = combine(playbackState, playWhenReady) { state, ready ->
            ready && state != STATE_ENDED
        }.stateIn(
            scope,
            SharingStarted.Lazily,
            initialPlayer?.let { it.playWhenReady && it.playbackState != STATE_ENDED } ?: false
        )
        
        Timber.tag(TAG).d("PlayerConnection state flows initialized successfully")
    }
    
    val isEffectivelyPlaying = isPlaying

    val mediaMetadata = MutableStateFlow(player.currentMetadata)
    val currentSong =
        mediaMetadata.flatMapLatest {
            database.song(it?.id)
        }
    val currentLyrics = mediaMetadata.flatMapLatest { mediaMetadata ->
        database.lyrics(mediaMetadata?.id)
    }
    val currentFormat =
        mediaMetadata.flatMapLatest { mediaMetadata ->
            database.format(mediaMetadata?.id)
        }

    /** BPM, key, tier and transition style, straight from the DJ engine. */
    val djState = service.djState

    val crossfadeDurationMs = service.crossfadeDurationMs

    val queueTitle = MutableStateFlow<String?>(null)
    val queueWindows = MutableStateFlow<List<Timeline.Window>>(emptyList())
    val currentMediaItemIndex = MutableStateFlow(-1)
    val currentWindowIndex = MutableStateFlow(-1)

    val shuffleModeEnabled = MutableStateFlow(false)
    val repeatMode = MutableStateFlow(REPEAT_MODE_OFF)

    val canSkipPrevious = MutableStateFlow(true)
    val canSkipNext = MutableStateFlow(true)

    val requestShowLyrics = MutableStateFlow(false)
    val requestShowQueue = MutableStateFlow(false)

    val error = MutableStateFlow<PlaybackException?>(null)
    val isMuted = service.isMuted

    val waitingForNetworkConnection = service.waitingForNetworkConnection

    var onSkipPrevious: (() -> Unit)? = null
    var onSkipNext: (() -> Unit)? = null

    private var attachedPlayer: Player? = null

    init {
        try {
            scope.launch {
                service.playerFlow.collect { newPlayer ->
                    if (newPlayer != null && newPlayer != attachedPlayer) {
                        updateAttachedPlayer(newPlayer)
                    }
                }
            }
            
            if (attachedPlayer == null && service.isPlayerReady.value) {
                 updateAttachedPlayer(player)
            }

            Timber.tag(TAG).d("PlayerConnection flow observer registered")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Failed to initialize PlayerConnection listener or state")
            // Propagate the error so MainActivity can retry
            throw e
        }
    }

    private fun updateAttachedPlayer(newPlayer: Player) {
        attachedPlayer?.removeListener(this)
        attachedPlayer = newPlayer
        newPlayer.addListener(this)
        
        playbackState.value = newPlayer.playbackState
        playWhenReady.value = newPlayer.playWhenReady
        mediaMetadata.value = newPlayer.currentMetadata
        queueTitle.value = service.queueTitle
        queueWindows.value = newPlayer.getQueueWindows()
        currentWindowIndex.value = newPlayer.getCurrentQueueIndex()
        currentMediaItemIndex.value = newPlayer.currentMediaItemIndex
        shuffleModeEnabled.value = newPlayer.shuffleModeEnabled
        repeatMode.value = newPlayer.repeatMode
        
        Timber.tag(TAG).d("Attached to new player instance: $newPlayer")
    }

    /** Replacement awaiting the user's keep-or-clear choice. */
    val pendingQueue = MutableStateFlow<Queue?>(null)

    /** Previous queue available to the undo action. */
    val queueReplacedForUndo = MutableStateFlow<Queue?>(null)

    /** [force] bypasses the queue replacement prompt. */
    fun playQueue(queue: Queue, force: Boolean = false) {
        if (!playerReadinessFlow.value) {
            Timber.tag(TAG).w("playQueue called before player ready; delegating to service")
        }
        val isReplace = hasLiveQueue() && !startsInsideCurrentQueue(queue)
        if (!force && appContext.dataStore.get(AskBeforeReplacingQueueKey, false) && isReplace) {
            pendingQueue.value = queue
            return
        }
        // Forced replacements already reflect a deliberate choice.
        val previousQueue = if (!force && isReplace) snapshotCurrentQueue() else null
        try {
            service.playQueue(queue)
            previousQueue?.let { queueReplacedForUndo.value = it }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playQueue")
            throw e
        }
    }

    /** Captures what is currently playing as a replayable [ListQueue], for undo. */
    private fun snapshotCurrentQueue(): Queue? = try {
        if (player.mediaItemCount == 0) null else ListQueue(
            items = (0 until player.mediaItemCount).map { player.getMediaItemAt(it) },
            startIndex = player.currentMediaItemIndex,
            position = player.currentPosition,
        )
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "snapshotCurrentQueue failed")
        null
    }

    /** Appends [queue] to what is already playing instead of replacing it. */
    fun enqueueQueue(queue: Queue) {
        service.enqueueQueue(queue)
    }

    private fun hasLiveQueue(): Boolean = try {
        playerReadinessFlow.value &&
            player.mediaItemCount > 0 &&
            player.playbackState != Player.STATE_IDLE
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "hasLiveQueue check failed")
        false
    }

    private fun startsInsideCurrentQueue(queue: Queue): Boolean = try {
        val startId = when (queue) {
            is ListQueue -> queue.items.getOrNull(queue.startIndex)?.mediaId
            else -> queue.preloadItem?.id
        }
        startId != null && (0 until player.mediaItemCount).any { player.getMediaItemAt(it).mediaId == startId }
    } catch (e: Exception) {
        Timber.tag(TAG).w(e, "startsInsideCurrentQueue check failed")
        false
    }

    fun startRadioSeamlessly() {
        if (!playerReadinessFlow.value) {
            Timber.tag(TAG).w("startRadioSeamlessly called before player ready; delegating to service")
        }
        try {
            service.startRadioSeamlessly()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in startRadioSeamlessly")
            throw e
        }
    }

    fun playNext(item: MediaItem) = playNext(listOf(item))

    fun playNext(items: List<MediaItem>) {
        try {
            service.playNext(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in playNext")
            throw e
        }
    }

    fun addToQueue(item: MediaItem) = addToQueue(listOf(item))

    fun addToQueue(items: List<MediaItem>) {
        try {
            service.addToQueue(items)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in addToQueue")
            throw e
        }
    }

    fun toggleLike() {
        try {
            service.toggleLike()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in toggleLike")
        }
    }

    fun toggleMute() {
        service.toggleMute()
    }

    fun setMuted(muted: Boolean) {
        service.setMuted(muted)
    }

    fun toggleLibrary() {
        try {
            service.toggleLibrary()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in toggleLibrary")
        }
    }

    fun togglePlayPause() {
        try {
            player.togglePlayPause()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in togglePlayPause")
        }
    }

    fun play() {
        try {
            when (player.playbackState) {
                Player.STATE_IDLE -> player.prepare()
                Player.STATE_ENDED -> player.seekTo(player.currentMediaItemIndex, 0)
                else -> {}
            }
            player.playWhenReady = true
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in play")
        }
    }

    fun pause() {
        try {
            player.playWhenReady = false
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in pause")
        }
    }

    fun seekTo(position: Long) {
        try {
            player.seekTo(position)
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekTo")
        }
    }

    private var pendingSkip = 0
    private var skipCommitJob: kotlinx.coroutines.Job? = null

    /** Coalesces rapid transport taps into one timeline jump. */
    fun requestSkip(forward: Boolean) {
        pendingSkip += if (forward) 1 else -1
        skipCommitJob?.cancel()
        skipCommitJob = scope.launch {
            kotlinx.coroutines.delay(SKIP_COMMIT_DELAY_MS)
            val steps = pendingSkip
            pendingSkip = 0
            when {
                steps == 1 -> seekToNext()
                steps == -1 -> seekToPrevious()
                steps != 0 -> jumpBy(steps)
            }
        }
    }

    /** Several skips at once: walk the timeline (repeat/shuffle aware), one seek. Also the
     *  swipe-to-skip path: a swiped-in PREVIOUS card must land on the previous track even
     *  3s into this one, where [seekToPrevious] would restart instead. */
    fun jumpBy(steps: Int) {
        try {
            val timeline = player.currentTimeline
            if (timeline.isEmpty) return
            // A multi-skip advances through tracks even in repeat-one mode.
            val mode = if (player.repeatMode == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_OFF else player.repeatMode
            var index = player.currentMediaItemIndex
            for (step in 0 until kotlin.math.abs(steps)) {
                val next = if (steps > 0) {
                    timeline.getNextWindowIndex(index, mode, player.shuffleModeEnabled)
                } else {
                    timeline.getPreviousWindowIndex(index, mode, player.shuffleModeEnabled)
                }
                if (next == androidx.media3.common.C.INDEX_UNSET) break
                index = next
            }
            if (index == player.currentMediaItemIndex) return
            player.seekTo(index, 0)
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
            player.playWhenReady = true
            if (steps > 0) onSkipNext?.invoke() else onSkipPrevious?.invoke()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in jumpBy($steps)")
        }
    }

    fun seekToNext() {
        try {
            Timber.tag(TAG).d(
                "SKIP_DEBUG seekToNext() called: beforeIndex=${player.currentMediaItemIndex} " +
                    "beforeId=${player.currentMediaItem?.mediaId} repeatMode=${player.repeatMode} " +
                    "shuffle=${player.shuffleModeEnabled} hasNext=${player.hasNextMediaItem()} " +
                    "nextIndex=${player.nextMediaItemIndex}"
            )
            player.seekToNext()
            Timber.tag(TAG).d(
                "SKIP_DEBUG seekToNext() returned: afterIndex=${player.currentMediaItemIndex} " +
                    "afterId=${player.currentMediaItem?.mediaId}"
            )
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
            player.playWhenReady = true
            onSkipNext?.invoke()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekToNext")
        }
    }

    var onRestartSong: (() -> Unit)? = null

    fun seekToPrevious() {
        try {
            val restartCurrentItem = player.currentPosition > 3000 || !player.hasPreviousMediaItem()
            if (restartCurrentItem) {
                player.seekTo(0)
            } else {
                player.seekToPreviousMediaItem()
            }
            if (player.playbackState == Player.STATE_IDLE || player.playbackState == Player.STATE_ENDED) {
                player.prepare()
            }
            player.playWhenReady = true
            if (restartCurrentItem) onRestartSong?.invoke() else onSkipPrevious?.invoke()
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error in seekToPrevious")
        }
    }

    override fun onPlaybackStateChanged(state: Int) {
        playbackState.value = state
        error.value = player.playerError
    }

    override fun onPlayWhenReadyChanged(
        newPlayWhenReady: Boolean,
        reason: Int,
    ) {
        playWhenReady.value = newPlayWhenReady
    }

    override fun onMediaItemTransition(
        mediaItem: MediaItem?,
        reason: Int,
    ) {
        mediaMetadata.value = mediaItem?.metadata
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onTimelineChanged(
        timeline: Timeline,
        reason: Int,
    ) {
        queueWindows.value = player.getQueueWindows()
        queueTitle.value = service.queueTitle
        currentMediaItemIndex.value = player.currentMediaItemIndex
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onShuffleModeEnabledChanged(enabled: Boolean) {
        shuffleModeEnabled.value = enabled
        queueWindows.value = player.getQueueWindows()
        currentWindowIndex.value = player.getCurrentQueueIndex()
        updateCanSkipPreviousAndNext()
    }

    override fun onRepeatModeChanged(mode: Int) {
        repeatMode.value = mode
        updateCanSkipPreviousAndNext()
    }

    override fun onPlayerErrorChanged(playbackError: PlaybackException?) {
        if (playbackError != null) {
            reportException(playbackError)
        }
        error.value = playbackError
    }

    private fun updateCanSkipPreviousAndNext() {
        if (!player.currentTimeline.isEmpty) {
            val window =
                player.currentTimeline.getWindow(player.currentMediaItemIndex, Timeline.Window())
            canSkipPrevious.value = player.isCommandAvailable(COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM) ||
                    !window.isLive ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
            canSkipNext.value = window.isLive &&
                    window.isDynamic ||
                    player.isCommandAvailable(COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
        } else {
            canSkipPrevious.value = false
            canSkipNext.value = false
        }
    }

    fun dispose() {
        scope.cancel()
        skipCommitJob = null
        pendingSkip = 0
        try {
            attachedPlayer?.removeListener(this)
            attachedPlayer = null
            Timber.tag(TAG).d("PlayerConnection disposed successfully")
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "Error during PlayerConnection disposal")
        }
    }
}
