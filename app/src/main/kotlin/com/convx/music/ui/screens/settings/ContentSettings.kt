/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.settings

import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import com.composables.sfsymbols.SfSymbols
import com.composables.sfsymbols.monochrome.SFGlobeAsiaAustraliaFill
import com.composables.sfsymbols.monochrome.SFLeaf
import com.composables.sfsymbols.monochrome.SFGlobe
import com.composables.sfsymbols.monochrome.SFLine3Horizontal
import com.composables.sfsymbols.monochrome.SFMappinAndEllipse
import com.composables.sfsymbols.monochrome.SFPlayRectangle
import com.composables.sfsymbols.monochrome.SFEyeSlashFill
import com.composables.sfsymbols.monochrome.SFShuffle
import com.composables.sfsymbols.monochrome.SFSliderHorizontal3
import com.convx.music.ui.ios.foundation.IosTheme
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource
import com.convx.music.ui.utils.appTopBarWindowInsets
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.convx.music.ui.ios.compat.Button
import com.convx.music.ui.ios.compat.AppTheme
import androidx.compose.material3.ExperimentalMaterial3Api
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.OutlinedTextField
import com.convx.music.ui.ios.compat.Slider
import com.convx.music.ui.ios.compat.Switch
import androidx.compose.material3.SwitchDefaults
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.ios.compat.TextButton
import com.convx.music.ui.ios.compat.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.R
import com.convx.music.constants.HideExplicitKey
import com.convx.music.constants.HideVideoSongsKey
import com.convx.music.constants.RandomizeHomeOrderKey
import com.convx.music.ui.component.IosSettingsGroup
import com.convx.music.ui.component.IosSettingsItem
import com.convx.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val (hideExplicit, onHideExplicitChange) = rememberPreference(key = HideExplicitKey, defaultValue = false)
    val (hideVideoSongs, onHideVideoSongsChange) = rememberPreference(key = HideVideoSongsKey, defaultValue = false)
    val (randomizeHomeOrder, onRandomizeHomeOrderChange) = rememberPreference(
        RandomizeHomeOrderKey,
        defaultValue = true
    )

    SettingsPage(
        navController = navController,
        title = stringResource(R.string.content),
        pageColor = IosTheme.colors.systemGroupedBackground,
        horizontalPadding = 16.dp,
    ) {
        IosSettingsGroup(
            title = "Library filters",
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.explicit),
                    title = { Text(stringResource(R.string.hide_explicit)) },
                    trailingContent = {
                        Switch(
                            checked = hideExplicit,
                            onCheckedChange = onHideExplicitChange
                        )
                    },
                    onClick = { onHideExplicitChange(!hideExplicit) }
                ),
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFPlayRectangle),
                    title = { Text(stringResource(R.string.hide_video_songs)) },
                    trailingContent = {
                        Switch(
                            checked = hideVideoSongs,
                            onCheckedChange = onHideVideoSongsChange
                        )
                    },
                    onClick = { onHideVideoSongsChange(!hideVideoSongs) }
                ),
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = "Home feed",
            items = listOf(
                IosSettingsItem(
                    icon = rememberVectorPainter(SfSymbols.Monochrome.SFShuffle),
                    title = { Text(stringResource(R.string.randomize_home_order)) },
                    description = { Text(stringResource(R.string.randomize_home_order_desc)) },
                    trailingContent = {
                        Switch(
                            checked = randomizeHomeOrder,
                            onCheckedChange = onRandomizeHomeOrderChange
                        )
                    },
                    onClick = { onRandomizeHomeOrderChange(!randomizeHomeOrder) }
                ),
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = stringResource(R.string.advanced),
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.tune),
                    title = { Text(stringResource(R.string.advanced)) },
                    description = { Text(stringResource(R.string.advanced_settings_desc)) },
                    onClick = { navController.navigate("settings/content/advanced") }
                )
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}
