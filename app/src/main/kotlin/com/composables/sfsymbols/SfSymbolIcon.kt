/**
 * Apprise Music Open-Source Clean Iconography Adapter
 * Licensed under Apache-2.0 / GPL-3.0
 */

package com.composables.sfsymbols

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

@Composable
fun SfSymbolIcon(
    symbol: SfSymbol,
    modifier: Modifier = Modifier,
    size: Dp = 24.dp,
    weight: SfWeight = SfWeight.Regular,
    mode: SfRenderingMode = SfRenderingMode.Monochrome,
    tint: Color = Color.Unspecified,
    variableValue: Float? = null,
    effect: SfSymbolEffect? = null,
    trigger: Int = 0,
    transition: SfSymbolTransition? = null,
    contentDescription: String? = null,
) {
    Image(
        imageVector = symbol.vector,
        contentDescription = contentDescription,
        contentScale = ContentScale.Fit,
        modifier = modifier.size(size),
        colorFilter = if (tint.isSpecified) ColorFilter.tint(tint) else null
    )
}

