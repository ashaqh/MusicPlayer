/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.component

import androidx.annotation.DrawableRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import com.convx.music.ui.ios.compat.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.R
import com.convx.music.ui.utils.isScrollingUp
import dev.chrisbanes.haze.HazeState

/** Pass the screen's scrolling [HazeState] for a frosted blur; null uses a flat fallback to avoid backdrop self-capture. */
@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    lazyListState: LazyListState,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    hazeState: HazeState? = null,
    contentDescription: String? = stringResource(R.string.shuffle),
) {
    HideOnScrollFABImpl(
        isScrollingUp = lazyListState.isScrollingUp(),
        visible = visible,
        icon = icon,
        onClick = onClick,
        hazeState = hazeState,
        contentDescription = contentDescription,
    )
}

@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    lazyListState: LazyGridState,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    hazeState: HazeState? = null,
    contentDescription: String? = stringResource(R.string.shuffle),
) {
    HideOnScrollFABImpl(
        isScrollingUp = lazyListState.isScrollingUp(),
        visible = visible,
        icon = icon,
        onClick = onClick,
        hazeState = hazeState,
        contentDescription = contentDescription,
    )
}

@Composable
fun BoxScope.HideOnScrollFAB(
    visible: Boolean = true,
    scrollState: ScrollState,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    hazeState: HazeState? = null,
    contentDescription: String? = stringResource(R.string.shuffle),
) {
    HideOnScrollFABImpl(
        isScrollingUp = scrollState.isScrollingUp(),
        visible = visible,
        icon = icon,
        onClick = onClick,
        hazeState = hazeState,
        contentDescription = contentDescription,
    )
}

@Composable
private fun BoxScope.HideOnScrollFABImpl(
    isScrollingUp: Boolean,
    visible: Boolean,
    @DrawableRes icon: Int,
    onClick: () -> Unit,
    hazeState: HazeState?,
    contentDescription: String?,
) {
    AnimatedVisibility(
        visible = visible && isScrollingUp,
        enter = slideInVertically { it },
        exit = slideOutVertically { it },
        modifier =
        Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(
                LocalPlayerAwareWindowInsets.current
                    .only(WindowInsetsSides.Bottom + WindowInsetsSides.Horizontal),
            ),
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(16.dp),
        ) {
            GlassCircleButton(
                onClick = onClick,
                size = 56.dp,
                hazeState = hazeState,
                forceFlat = true,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = contentDescription,
                )
            }
        }
    }
}
