/**
 * Apprise Music (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavController
import com.convx.music.R
import com.convx.music.ui.ios.component.IosMenuItem
import com.convx.music.ui.ios.component.IosMenuSection
import com.convx.music.ui.screens.Screens

/** The shortcuts behind a press-and-hold on a nav bar tab. */
@Composable
internal fun navTabContextMenu(
    screen: Screens,
    navController: NavController,
): List<IosMenuSection> {
    @Composable
    fun item(@StringRes titleId: Int, route: String) = IosMenuItem(
        title = stringResource(titleId),
        onClick = { navController.navigate(route) },
    )

    val items = when (screen) {
        Screens.Home, Screens.Library -> listOf(
            item(R.string.history, "history"),
            item(R.string.stats, "stats"),
        )
        else -> emptyList()
    }
    return if (items.isEmpty()) emptyList() else listOf(IosMenuSection(items))
}
