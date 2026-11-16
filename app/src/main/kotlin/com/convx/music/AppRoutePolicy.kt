package com.convx.music

import com.convx.music.ui.screens.Screens

/** Route-based rules for the app chrome surrounding each screen. */
internal object AppRoutePolicy {
    val tabRootRoutes = setOf(
        Screens.Home.route,
        Screens.Songs.route,
        Screens.Library.route,
        Screens.Settings.route,
    )

    private val routesWithoutNavigation = setOf(
        "equalizer",
        "wrapped",
        "ambient_mode",
    )
    private val routesWithoutPlayer = setOf(
        "wrapped",
        "equalizer",
        "ambient_mode",
    )
    private val routesWithoutBottomSheetPlayer = setOf(
        "wrapped",
        "ambient_mode",
    )

    private val routesWithScreenTopHaze = setOf(
        Screens.Search.route,
    )

    fun isTabRoot(route: String?) = route != null && route in tabRootRoutes
    fun showsNavigation(route: String?) = route == null || route !in routesWithoutNavigation
    fun showsPlayer(route: String?) = route == null || route !in routesWithoutPlayer
    fun showsBottomSheetPlayer(route: String?) = route == null || route !in routesWithoutBottomSheetPlayer

    fun showsFallbackTopHaze(route: String?) = route != null &&
        route !in tabRootRoutes &&
        route !in routesWithScreenTopHaze &&
        !route.startsWith("settings") &&
        !route.startsWith("album/") &&
        !route.startsWith("artist/") &&
        !route.startsWith("local_playlist/") &&
        !route.startsWith("auto_playlist/") &&
        !route.startsWith("top_playlist/") &&
        !route.startsWith("local_folder/") &&
        !route.startsWith("network_browse/")
}
