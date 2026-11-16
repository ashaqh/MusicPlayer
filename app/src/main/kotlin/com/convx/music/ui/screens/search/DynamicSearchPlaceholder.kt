/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.search

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import com.convx.music.R
import com.convx.music.constants.SearchSource
import com.convx.music.ui.ios.compat.Text

@Composable
fun DynamicSearchPlaceholder(
    @Suppress("UNUSED_PARAMETER") searchSource: SearchSource,
    modifier: Modifier = Modifier,
    style: TextStyle = TextStyle(),
    color: Color = Color.Unspecified
) {
    Text(
        text = stringResource(R.string.search_library),
        style = style,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
    )
}
