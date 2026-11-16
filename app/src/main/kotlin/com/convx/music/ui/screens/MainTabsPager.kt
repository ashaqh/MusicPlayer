/**
 * Apprise Music (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import com.convx.music.ui.screens.library.LibraryScreen
import com.convx.music.ui.screens.settings.SettingsScreen

/** Main pager pages in tab-bar order. Search is an overlay, not a pager page. */
val MainTabsScreens = listOf(
    Screens.Home,
    Screens.Library,
    Screens.Settings,
)

const val MainTabsRoute = "main_tabs"

suspend fun PagerState.slideToPage(page: Int) {
    val from = currentPage
    if (page - from > 1 || from - page > 1) {
        scrollToPage(page + if (page > from) -1 else 1)
    }
    animateScrollToPage(page)
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MainTabsPager(
    pagerState: PagerState,
    navController: NavHostController,
    scrollBehavior: TopAppBarScrollBehavior,
    @Suppress("UNUSED_PARAMETER") snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    HorizontalPager(
        state = pagerState,
        modifier = modifier.fillMaxSize(),
        userScrollEnabled = false,
    ) { page ->
        when (MainTabsScreens.getOrNull(page)) {
            Screens.Home -> LocalHomeScreen(navController = navController)
            Screens.Library -> LibraryScreen(navController)
            Screens.Settings -> SettingsScreen(navController, scrollBehavior)
            else -> Unit
        }
    }
}
