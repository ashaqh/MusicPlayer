package com.apprise.music.preferences

import android.content.Context
import com.convx.music.constants.AppFont
import com.convx.music.constants.AudioOffload
import com.convx.music.constants.BrandFontEnabledKey
import com.convx.music.constants.DarkModeKey
import com.convx.music.constants.LocalOnlyModeKey
import com.convx.music.constants.PureBlackKey
import com.convx.music.constants.SelectedFontKey
import com.convx.music.constants.SelectedThemeColorKey
import com.convx.music.constants.SkipSilenceKey
import com.convx.music.ui.screens.settings.DarkMode
import com.convx.music.ui.theme.DefaultThemeColor
import com.convx.music.utils.dataStore
import androidx.compose.ui.graphics.toArgb
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/** Immutable snapshot of user visual and playback preferences across DataStore keys. */
data class AppPreferenceSnapshot(
    val darkMode: DarkMode = DarkMode.AUTO,
    val pureBlack: Boolean = false,
    val accentColorArgb: Int = DefaultThemeColor.toArgb(),
    val selectedFont: AppFont = AppFont.PLUS_JAKARTA_SANS,
    val brandWordmarkFontEnabled: Boolean = false,
    val localOnlyMode: Boolean = false,
    val audioOffloadEnabled: Boolean = false,
    val skipSilenceEnabled: Boolean = false,
)

/** Clean boundary decoupling feature layers from raw DataStore preference keys and MMKV mirrors. */
interface AppPreferencesBoundary {
    val snapshot: Flow<AppPreferenceSnapshot>
}

@Singleton
class DataStoreAppPreferencesBoundary @Inject constructor(
    @ApplicationContext private val context: Context,
) : AppPreferencesBoundary {

    override val snapshot: Flow<AppPreferenceSnapshot> = context.dataStore.data.map { prefs ->
        val darkModeName = prefs[DarkModeKey]
        val darkMode = DarkMode.entries.firstOrNull { it.name == darkModeName } ?: DarkMode.AUTO
        AppPreferenceSnapshot(
            darkMode = darkMode,
            pureBlack = prefs[PureBlackKey] ?: false,
            accentColorArgb = prefs[SelectedThemeColorKey] ?: DefaultThemeColor.toArgb(),
            selectedFont = AppFont.fromValue(prefs[SelectedFontKey] ?: AppFont.PLUS_JAKARTA_SANS.value),
            brandWordmarkFontEnabled = prefs[BrandFontEnabledKey] ?: false,
            localOnlyMode = prefs[LocalOnlyModeKey] ?: false,
            audioOffloadEnabled = prefs[AudioOffload] ?: false,
            skipSilenceEnabled = prefs[SkipSilenceKey] ?: false,
        )
    }
}

