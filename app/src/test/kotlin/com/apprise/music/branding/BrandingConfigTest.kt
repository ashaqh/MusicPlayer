package com.apprise.music.branding

import com.convx.music.R
import com.convx.music.utils.AppIcon
import org.junit.Assert.assertEquals
import org.junit.Test

class BrandingConfigTest {

    @Test
    fun `default launcher icon uses Apprise Music placeholder drawable`() {
        assertEquals(R.drawable.ic_apprise_launcher_foreground, AppIcon.DEFAULT.previewRes)
        assertEquals(".LauncherWhite", AppIcon.DEFAULT.alias)
    }
}

