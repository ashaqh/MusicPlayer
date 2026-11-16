/**
 * Apprise Music (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.remember
import com.convx.music.constants.HiddenNavigationRoutesKey
import com.convx.music.ui.screens.Screens
import com.convx.music.utils.rememberPreference

/** Always offline/local in M2+; preserved as CompositionLocal so library screens compose consistently. */
val LocalLocalOnlyMode = androidx.compose.runtime.staticCompositionLocalOf { true }

/** Destinations shown by the app's navigation bars. */
@Stable
class AppNavItems(
    val localOnlyMode: Boolean,
    val navigationItems: List<Screens>,
    val floatingNavigationItems: List<Screens>,
)

@Composable
fun rememberAppNavItems(): AppNavItems {
    val (hiddenNavigationRoutes) = rememberPreference(HiddenNavigationRoutesKey, "")
    val hiddenRoutes = remember(hiddenNavigationRoutes) {
        hiddenNavigationRoutes.lineSequence().map(String::trim).filter(String::isNotEmpty).toSet()
    }
    val navigationItems = remember(hiddenRoutes) {
        Screens.MainScreens.filter { it == Screens.Search || it.route !in hiddenRoutes }
    }
    return remember(navigationItems) {
        AppNavItems(
            localOnlyMode = true,
            navigationItems = navigationItems,
            floatingNavigationItems = navigationItems,
        )
    }
}
