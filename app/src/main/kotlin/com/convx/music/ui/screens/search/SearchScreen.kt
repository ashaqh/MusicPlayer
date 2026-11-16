/**
 * Apprise Music (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.search

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.navigation.NavController
import com.convx.music.LocalIsPlayerExpanded
import com.convx.music.ui.component.LocalNavSearchState
import com.convx.music.ui.ios.foundation.IosTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    navController: NavController,
    pureBlack: Boolean,
    @Suppress("UNUSED_PARAMETER") onSettingsClick: () -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    val isPlayerExpanded = LocalIsPlayerExpanded.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val navSearch = LocalNavSearchState.current

    val pageColor = IosTheme.colors.systemBackground
    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val tabletLayout = LocalConfiguration.current.screenWidthDp >= 600
    val focusedTop = if (tabletLayout) statusTop + 10.dp else maxOf(statusTop, 58.dp)

    Box(
        Modifier
            .fillMaxSize()
            .background(pageColor),
    ) {
        Column(Modifier.fillMaxSize().padding(top = focusedTop)) {
            LocalSearchScreen(
                query = navSearch.query.text,
                navController = navController,
                onDismiss = { },
                pureBlack = pureBlack,
            )
        }
    }

    // Keep the keyboard closed on resume; the navigation bar handles search focus.
    DisposableEffect(lifecycleOwner, isPlayerExpanded) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    if (isPlayerExpanded) {
                        keyboardController?.hide()
                        focusManager.clearFocus()
                        navSearch.onCloseKeyboard()
                    }
                }
                Lifecycle.Event.ON_PAUSE -> {
                    focusManager.clearFocus()
                    keyboardController?.hide()
                    navSearch.onCloseKeyboard()
                }
                else -> {}
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        if (isPlayerExpanded) {
            keyboardController?.hide()
            focusManager.clearFocus()
            navSearch.onCloseKeyboard()
        }
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }
}
