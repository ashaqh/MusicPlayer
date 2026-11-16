/**
 * Apprise Music (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import androidx.compose.runtime.Immutable
import com.convx.music.R

/**
 * A bottom-bar / side-bar destination.
 *
 * One icon per destination per UI style — deliberately not a selected/unselected
 * pair. iOS ships outline/filled pairs because its tab bar has no selection
 * indicator; this bar has both a glass puck and an accent content colour, so a
 * filled variant would be a third signal saying the same thing. Two artworks per
 * tab also have to be optically aligned against each other or the icon visibly
 * shifts on selection, which is exactly the cheap-looking wobble the pair would
 * have bought us.
 */
@Immutable
sealed class Screens(
    @StringRes val titleId: Int,
    val route: String,
    @DrawableRes val icon: Int,
    @DrawableRes val iosIcon: Int = icon,
) {
    /** The icon to draw for the current UI style. */
    fun icon(appleMusicUi: Boolean): Int = if (appleMusicUi) iosIcon else icon

    object Home : Screens(
        titleId = R.string.home,
        route = "home",
        icon = R.drawable.accord_home,
    )

    object Songs : Screens(
        titleId = R.string.songs,
        route = "songs",
        icon = R.drawable.music_note,
    )

    object Search : Screens(
        titleId = R.string.search,
        route = "search_input",
        icon = R.drawable.search,
        iosIcon = R.drawable.cosmos_search,
    )

    object Library : Screens(
        titleId = R.string.filter_library,
        route = "library",
        icon = R.drawable.accord_library,
    )

    object Settings : Screens(
        titleId = R.string.settings,
        route = "settings",
        icon = R.drawable.settings,
    )

    companion object {
        // Lazy initialization avoids reading singleton routes before their objects initialize.
        val MainScreens: List<Screens> by lazy { listOf(Home, Songs, Search, Library) }

        fun mainScreens(@Suppress("UNUSED_PARAMETER") localOnly: Boolean = true): List<Screens> =
            MainScreens

        /** Every route that counts as a tab root. */
        val MainRoutes: Set<String> by lazy { MainScreens.map { it.route }.toSet() }
    }
}
