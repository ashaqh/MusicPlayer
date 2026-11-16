/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.utils

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow

@Composable
fun LazyListState.isScrollingUp(): Boolean {
    var isScrollingUp by remember(this) { mutableStateOf(true) }
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }

    LaunchedEffect(this) {
        snapshotFlow { firstVisibleItemIndex to firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                if (currentIndex != previousIndex) {
                    isScrollingUp = previousIndex > currentIndex
                    previousIndex = currentIndex
                    previousScrollOffset = currentOffset
                } else if (currentOffset != previousScrollOffset) {
                    isScrollingUp = previousScrollOffset > currentOffset
                    previousScrollOffset = currentOffset
                }
            }
    }
    return isScrollingUp
}

@Composable
fun LazyGridState.isScrollingUp(): Boolean {
    var isScrollingUp by remember(this) { mutableStateOf(true) }
    var previousIndex by remember(this) { mutableIntStateOf(firstVisibleItemIndex) }
    var previousScrollOffset by remember(this) { mutableIntStateOf(firstVisibleItemScrollOffset) }

    LaunchedEffect(this) {
        snapshotFlow { firstVisibleItemIndex to firstVisibleItemScrollOffset }
            .collect { (currentIndex, currentOffset) ->
                if (currentIndex != previousIndex) {
                    isScrollingUp = previousIndex > currentIndex
                    previousIndex = currentIndex
                    previousScrollOffset = currentOffset
                } else if (currentOffset != previousScrollOffset) {
                    isScrollingUp = previousScrollOffset > currentOffset
                    previousScrollOffset = currentOffset
                }
            }
    }
    return isScrollingUp
}

@Composable
fun ScrollState.isScrollingUp(): Boolean {
    var isScrollingUp by remember(this) { mutableStateOf(true) }
    var previousScrollOffset by remember(this) { mutableIntStateOf(value) }

    LaunchedEffect(this) {
        snapshotFlow { value }
            .collect { currentOffset ->
                if (currentOffset != previousScrollOffset) {
                    isScrollingUp = previousScrollOffset > currentOffset
                    previousScrollOffset = currentOffset
                }
            }
    }
    return isScrollingUp
}
