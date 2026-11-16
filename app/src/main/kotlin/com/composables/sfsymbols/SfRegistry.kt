/**
 * Apprise Music Open-Source Clean Iconography Adapter
 * Licensed under Apache-2.0 / GPL-3.0
 */

package com.composables.sfsymbols

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.*
import androidx.compose.material.icons.rounded.*
import androidx.compose.ui.graphics.vector.ImageVector

internal object SfRegistry {
    private val map: Map<String, ImageVector> = mapOf(
        // Playback & Transport
        "play" to Icons.Rounded.PlayArrow,
        "play.fill" to Icons.Rounded.PlayArrow,
        "pause" to Icons.Rounded.Pause,
        "pause.fill" to Icons.Rounded.Pause,
        "forward" to Icons.Rounded.SkipNext,
        "forward.fill" to Icons.Rounded.SkipNext,
        "forward.end" to Icons.Rounded.SkipNext,
        "forward.end.fill" to Icons.Rounded.SkipNext,
        "backward" to Icons.Rounded.SkipPrevious,
        "backward.fill" to Icons.Rounded.SkipPrevious,
        "backward.end" to Icons.Rounded.SkipPrevious,
        "backward.end.fill" to Icons.Rounded.SkipPrevious,
        "repeat" to Icons.Rounded.Repeat,
        "repeat.1" to Icons.Rounded.RepeatOne,
        "shuffle" to Icons.Rounded.Shuffle,
        "arrow.clockwise" to Icons.Rounded.Refresh,
        "arrow.counterclockwise" to Icons.Rounded.RotateLeft,
        "clock.arrow.circlepath" to Icons.Rounded.History,
        "arrow.triangle.2.circlepath" to Icons.Rounded.Sync,

        // Audio & Hardware
        "headphones" to Icons.Rounded.Headphones,
        "earbuds" to Icons.Rounded.Headphones,
        "speaker" to Icons.Rounded.VolumeDown,
        "speaker.fill" to Icons.Rounded.VolumeUp,
        "speaker.wave.2.fill" to Icons.Rounded.VolumeDown,
        "speaker.wave.3.fill" to Icons.Rounded.VolumeUp,
        "speaker.slash.fill" to Icons.Rounded.VolumeOff,
        "hifispeaker.fill" to Icons.Rounded.Speaker,
        "hifispeaker.2.fill" to Icons.Rounded.SpeakerGroup,
        "car.fill" to Icons.Rounded.DirectionsCar,
        "tv.fill" to Icons.Rounded.Tv,
        "watch.analog" to Icons.Rounded.Watch,
        "iphone" to Icons.Rounded.PhoneAndroid,
        "ipad" to Icons.Rounded.TabletAndroid,
        "laptopcomputer" to Icons.Rounded.Laptop,
        "cable.connector" to Icons.Rounded.Cable,
        "hearingdevice.ear" to Icons.Rounded.Hearing,
        "airplayaudio" to Icons.Rounded.Cast,

        // Music & Media
        "music.note" to Icons.Rounded.MusicNote,
        "music.note.list" to Icons.AutoMirrored.Rounded.QueueMusic,
        "music.mic" to Icons.Rounded.Mic,
        "microphone.dynamic.on.stand" to Icons.Rounded.Mic,
        "waveform" to Icons.Rounded.GraphicEq,
        "waveform.path" to Icons.Rounded.GraphicEq,
        "e.square.fill" to Icons.Rounded.Explicit,

        // Navigation & Controls
        "chevron.down" to Icons.Rounded.KeyboardArrowDown,
        "chevron.up" to Icons.Rounded.KeyboardArrowUp,
        "chevron.left" to Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
        "chevron.right" to Icons.AutoMirrored.Rounded.KeyboardArrowRight,
        "arrow.left" to Icons.AutoMirrored.Rounded.ArrowBack,
        "arrow.up.left.and.arrow.down.right" to Icons.Rounded.Fullscreen,
        "ellipsis" to Icons.Rounded.MoreVert,
        "line.3.horizontal" to Icons.Rounded.Menu,
        "line.3.horizontal.decrease" to Icons.Rounded.FilterList,
        "list.bullet" to Icons.AutoMirrored.Rounded.FormatListBulleted,
        "sidebar.left" to Icons.Rounded.ViewSidebar,
        "slider.horizontal.3" to Icons.Rounded.Tune,
        "slider.vertical.3" to Icons.Rounded.Tune,

        // Actions & Feedback
        "checkmark" to Icons.Rounded.Check,
        "xmark" to Icons.Rounded.Close,
        "plus" to Icons.Rounded.Add,
        "minus" to Icons.Rounded.Remove,
        "heart" to Icons.Rounded.FavoriteBorder,
        "heart.fill" to Icons.Rounded.Favorite,
        "trash" to Icons.Rounded.Delete,
        "pencil" to Icons.Rounded.Edit,
        "square.and.pencil" to Icons.Rounded.Edit,
        "square.and.arrow.up" to Icons.Rounded.Share,
        "link" to Icons.Rounded.Link,
        "lock.fill" to Icons.Rounded.Lock,
        "lock" to Icons.Rounded.Lock,
        "magnifyingglass" to Icons.Rounded.Search,
        "info.circle" to Icons.Rounded.Info,
        "info.circle.fill" to Icons.Rounded.Info,
        "exclamationmark.circle" to Icons.Rounded.Warning,
        "exclamationmark.circle.fill" to Icons.Rounded.Warning,
        "bell" to Icons.Rounded.Notifications,
        "bell.fill" to Icons.Rounded.Notifications,
        "moon.fill" to Icons.Rounded.DarkMode,
        "moon.zzz.fill" to Icons.Rounded.Bedtime,
        "timer" to Icons.Rounded.Timer,
        "camera" to Icons.Rounded.CameraAlt,
        "photo" to Icons.Rounded.Photo,
        "paintpalette" to Icons.Rounded.Palette,
        "person.fill" to Icons.Rounded.Person,
        "key.fill" to Icons.Rounded.VpnKey,
        "ladybug" to Icons.Rounded.BugReport,
        "folder.fill" to Icons.Rounded.Folder,
        "rectangle.stack" to Icons.Rounded.Layers,
        "square.stack" to Icons.Rounded.Layers,
        "character.bubble" to Icons.Rounded.Chat,
        "quote.bubble" to Icons.Rounded.FormatQuote,
        "text.badge.checkmark" to Icons.Rounded.PlaylistAddCheck,
        "text.badge.plus" to Icons.Rounded.PlaylistAdd,
        "text.line.first.and.arrowtriangle.forward" to Icons.Rounded.FormatLineSpacing,
        "arrow.down.circle" to Icons.Rounded.ArrowCircleDown,
    )

    fun lookup(name: String): SfSymbol? {
        val normalized = name.lowercase().trim()
        val vector = map[normalized]
            ?: map[normalized.removePrefix("sf")]
            ?: map[normalized.replace("_", ".")]
            ?: Icons.Rounded.MusicNote
        return SfSymbol(name, vector)
    }
}

