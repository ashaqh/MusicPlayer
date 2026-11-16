/**
 * Apprise Music (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.utils

/**
 * Returns local or embedded artwork URIs unchanged; Coil sizes bitmaps via its request target.
 */
fun String.resize(
    @Suppress("UNUSED_PARAMETER") width: Int? = null,
    @Suppress("UNUSED_PARAMETER") height: Int? = null,
): String = this
