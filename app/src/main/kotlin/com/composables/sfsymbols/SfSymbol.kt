/**
 * Apprise Music Open-Source Clean Iconography Adapter
 * Licensed under Apache-2.0 / GPL-3.0
 *
 * Provides a clean, standard Material Design and HIG vector adapter
 * replacing proprietary third-party Apple SF Symbols libraries.
 */

package com.composables.sfsymbols

import androidx.compose.ui.graphics.vector.ImageVector

data class SfSymbol(
    val name: String,
    val vector: ImageVector
) {
    companion object {
        fun named(name: String): SfSymbol? = SfRegistry.lookup(name)
    }
}

enum class SfRenderingMode {
    Monochrome, Hierarchical, Palette, Multicolor
}

enum class SfWeight {
    UltraLight, Thin, Light, Regular, Medium, Semibold, Bold, Heavy, Black
}

sealed class SfSymbolEffect {
    data class Bounce(val scope: SfEffectScope = SfEffectScope.ByLayer) : SfSymbolEffect()
    object Pulse : SfSymbolEffect()
    object Scale : SfSymbolEffect()
}

enum class SfEffectScope {
    WholeSymbol, ByLayer
}

sealed class SfSymbolTransition {
    object MagicReplace : SfSymbolTransition()
    object Fade : SfSymbolTransition()
}

