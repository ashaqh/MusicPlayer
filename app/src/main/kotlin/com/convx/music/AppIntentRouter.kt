/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music

import android.content.Intent
import android.net.Uri
import androidx.core.net.toUri
import androidx.datastore.preferences.core.edit
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavHostController
import com.convx.music.playback.PlayerConnection
import com.convx.music.ui.screens.Screens
import com.convx.music.utils.dataStore
import kotlinx.coroutines.launch
import timber.log.Timber

/** Routes a deep-link or debug launch intent to the right local screen. */
internal class AppIntentRouter(
    private val activity: MainActivity,
    private val currentPlayerConnection: () -> PlayerConnection?,
) {

    private val allowedIdPattern = Regex("^[A-Za-z0-9_\\-]+$")

    // Keep shared IDs from injecting extra route segments.
    private fun cleanId(raw: String?, min: Int = 4, max: Int = 64): String? {
        if (raw.isNullOrBlank()) return null

        val value = raw.trim()
        if (value.length !in min..max || !allowedIdPattern.matches(value)) return null
        return value
    }

    fun handleDeepLinkIntent(intent: Intent, navController: NavHostController) {
        if (handleDebugIntent(intent, navController)) return

        val uri = intent.data ?: intent.extras?.getString(Intent.EXTRA_TEXT)?.toUri() ?: return
        intent.data = null
        intent.removeExtra(Intent.EXTRA_TEXT)
        routeLocalLink(uri, navController)
    }

    private fun routeLocalLink(uri: Uri, navController: NavHostController) {
        when (uri.pathSegments.firstOrNull()) {
            "playlist" -> cleanId(uri.getQueryParameter("list") ?: uri.pathSegments.getOrNull(1), max = 128)?.let { playlistId ->
                navController.navigate("local_playlist/$playlistId") { launchSingleTop = true }
            }
            "album" -> cleanId(uri.lastPathSegment)?.let { albumId ->
                navController.navigate("album/$albumId") { launchSingleTop = true }
            }
            "artist" -> cleanId(uri.lastPathSegment)?.let { artistId ->
                navController.navigate("artist/$artistId") { launchSingleTop = true }
            }
            "search" -> openSearch(uri, navController)
        }
    }

    private fun openSearch(uri: Uri, navController: NavHostController) {
        if (uri.getQueryParameter("q")?.isNotBlank() != true) return
        navController.navigate(Screens.Search.route) { launchSingleTop = true }
    }

    private fun handleDebugIntent(intent: Intent, navController: NavHostController): Boolean {
        // Debug intents let emulator tests open real destinations directly.
        if (!BuildConfig.DEBUG || !intent.hasExtra("debug_screen_route")) return false

        val route = intent.getStringExtra("debug_screen_route")
        val libraryFilter = intent.getStringExtra("debug_library_filter")
        intent.removeExtra("debug_screen_route")
        intent.removeExtra("debug_library_filter")

        if (!route.isNullOrBlank()) {
            activity.lifecycleScope.launch {
                if (libraryFilter != null) {
                    activity.dataStore.edit {
                        it[com.convx.music.constants.ChipSortTypeKey] = libraryFilter
                    }
                }
                runCatching {
                    navController.navigate(route) {
                        popUpTo(navController.graph.startDestinationId)
                        launchSingleTop = true
                    }
                }.onFailure { Timber.tag("ScreenPass").e(it, "Cannot open %s", route) }
            }
        }
        return true
    }
}
