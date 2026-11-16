package com.apprise.music.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/** Source category for the active playback queue item. */
enum class PlaybackSourceKind {
    LOCAL_DEVICE,
    NETWORK_STORAGE,
    EXTERNAL_STREAM,
    NONE,
}

/** High-level transport state exposed outside the Media3 service layer. */
enum class PlaybackTransportState {
    IDLE,
    BUFFERING,
    PLAYING,
    PAUSED,
    ENDED,
}

/** Immutable snapshot of active playback state decoupled from ExoPlayer / Media3 types. */
data class PlaybackSnapshot(
    val activeTrackId: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val sourceKind: PlaybackSourceKind = PlaybackSourceKind.NONE,
    val transportState: PlaybackTransportState = PlaybackTransportState.IDLE,
    val positionMs: Long = 0L,
    val durationMs: Long = 0L,
)

/** Clean boundary for observing playback state without coupling consumers to MusicService or ExoPlayer. */
interface PlaybackControllerBoundary {
    val snapshot: StateFlow<PlaybackSnapshot>

    fun updateSnapshot(snapshot: PlaybackSnapshot)
}

@Singleton
class PlaybackBoundaryStateStore @Inject constructor() : PlaybackControllerBoundary {
    private val _snapshot = MutableStateFlow(PlaybackSnapshot())
    override val snapshot: StateFlow<PlaybackSnapshot> = _snapshot.asStateFlow()

    override fun updateSnapshot(snapshot: PlaybackSnapshot) {
        _snapshot.value = snapshot
    }
}

