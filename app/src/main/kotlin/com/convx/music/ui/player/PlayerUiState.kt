/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.PlaybackException
import androidx.media3.common.Timeline
import com.convx.music.db.entities.LyricsEntity
import com.convx.music.db.entities.Song
import com.convx.music.models.MediaMetadata
import com.convx.music.playback.PlayerConnection

/** Shared state collected once for player skins. Fields remain [State] handles so each skin
 * observes only the values it reads. [connection] also provides imperative player actions.
 */
@Stable
class PlayerUiState(
    val connection: PlayerConnection,
    val mediaMetadata: State<MediaMetadata?>,
    val isPlaying: State<Boolean>,
    val crossfadeDurationMs: State<Long>,
    val queueWindows: State<List<Timeline.Window>>,
    val currentWindowIndex: State<Int>,
    val currentSong: State<Song?>,
    val currentLyrics: State<LyricsEntity?>,
    val canSkipPrevious: State<Boolean>,
    val canSkipNext: State<Boolean>,
    val shuffleModeEnabled: State<Boolean>,
    val repeatMode: State<Int>,
    val error: State<PlaybackException?>,
)

@Composable
fun rememberPlayerUiState(connection: PlayerConnection): PlayerUiState {
    val mediaMetadata = connection.mediaMetadata.collectAsStateWithLifecycle()
    val isPlaying = connection.isEffectivelyPlaying.collectAsStateWithLifecycle()
    val crossfadeDurationMs = connection.crossfadeDurationMs.collectAsStateWithLifecycle()
    val queueWindows = connection.queueWindows.collectAsStateWithLifecycle()
    val currentWindowIndex = connection.currentWindowIndex.collectAsStateWithLifecycle()
    val currentSong = connection.currentSong.collectAsStateWithLifecycle(initialValue = null)
    val currentLyrics = connection.currentLyrics.collectAsStateWithLifecycle(initialValue = null)
    val canSkipPrevious = connection.canSkipPrevious.collectAsStateWithLifecycle()
    val canSkipNext = connection.canSkipNext.collectAsStateWithLifecycle()
    val shuffleModeEnabled = connection.shuffleModeEnabled.collectAsStateWithLifecycle()
    val repeatMode = connection.repeatMode.collectAsStateWithLifecycle()
    val error = connection.error.collectAsStateWithLifecycle()
    return remember(connection) {
        PlayerUiState(
            connection = connection,
            mediaMetadata = mediaMetadata,
            isPlaying = isPlaying,
            crossfadeDurationMs = crossfadeDurationMs,
            queueWindows = queueWindows,
            currentWindowIndex = currentWindowIndex,
            currentSong = currentSong,
            currentLyrics = currentLyrics,
            canSkipPrevious = canSkipPrevious,
            canSkipNext = canSkipNext,
            shuffleModeEnabled = shuffleModeEnabled,
            repeatMode = repeatMode,
            error = error,
        )
    }
}
