package com.convx.music.lyrics

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import com.convx.music.constants.EnableLrcLibKey
import com.convx.music.utils.dataStore
import com.convx.music.utils.get

object LyricsProviderSettings {
    fun enabledKey(id: String): Preferences.Key<Boolean> = when (id) {
        "LrcLib" -> EnableLrcLibKey
        else -> booleanPreferencesKey("enableLyrics$id")
    }

    // Opt-in external LRCLIB lookup: defaults to disabled until enabled by the user.
    fun isEnabled(context: Context, provider: LyricsProvider): Boolean =
        context.dataStore[enabledKey(provider.id)] ?: false

    fun initialize(context: Context, provider: LyricsProvider) {
        provider.initialize(context.applicationContext)
    }
}
