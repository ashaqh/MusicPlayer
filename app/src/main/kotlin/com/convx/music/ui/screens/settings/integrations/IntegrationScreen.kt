/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.settings.integrations

import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.convx.music.R
import com.convx.music.constants.ListenBrainzEnabledKey
import com.convx.music.constants.ListenBrainzTokenKey
import com.convx.music.ui.component.GlassSwitchCompat as Switch
import com.convx.music.ui.component.IntegrationCard
import com.convx.music.ui.component.IntegrationCardItem
import com.convx.music.ui.component.TextFieldDialog
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.screens.settings.SettingsPage
import com.convx.music.utils.rememberPreference

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun IntegrationScreen(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    var listenBrainzEnabled by rememberPreference(ListenBrainzEnabledKey, defaultValue = false)
    var listenBrainzToken by rememberPreference(ListenBrainzTokenKey, defaultValue = "")
    var showListenBrainzTokenDialog by remember { mutableStateOf(false) }

    if (showListenBrainzTokenDialog) {
        TextFieldDialog(
            title = { Text(stringResource(R.string.set_listenbrainz_token)) },
            initialTextFieldValue = TextFieldValue(listenBrainzToken),
            placeholder = { Text(stringResource(R.string.set_listenbrainz_token)) },
            isInputValid = { true },
            onDone = { token ->
                val trimmed = token.trim()
                listenBrainzToken = trimmed
                if (trimmed.isEmpty()) {
                    listenBrainzEnabled = false
                }
            },
            onDismiss = { showListenBrainzTokenDialog = false },
        )
    }

    SettingsPage(
        navController = navController,
        title = stringResource(R.string.integrations),
        pageColor = AppTheme.colorScheme.background,
        horizontalPadding = 16.dp,
    ) {
        IntegrationCard(
            title = "Services",
            items = listOf(
                IntegrationCardItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text(stringResource(R.string.lastfm_integration)) },
                    onClick = {
                        navController.navigate("settings/integrations/lastfm")
                    }
                ),
                IntegrationCardItem(
                    icon = painterResource(R.drawable.music_note),
                    title = { Text(stringResource(R.string.listenbrainz_scrobbling)) },
                    description = {
                        Text(
                            if (listenBrainzToken.isNotBlank()) {
                                stringResource(R.string.listenbrainz_token_set)
                            } else {
                                stringResource(R.string.listenbrainz_scrobbling_description)
                            }
                        )
                    },
                    trailingContent = {
                        Switch(
                            checked = listenBrainzEnabled && listenBrainzToken.isNotBlank(),
                            onCheckedChange = { enabled ->
                                if (enabled && listenBrainzToken.isBlank()) {
                                    showListenBrainzTokenDialog = true
                                } else {
                                    listenBrainzEnabled = enabled
                                }
                            }
                        )
                    },
                    onClick = { showListenBrainzTokenDialog = true }
                ),
            )
        )
    }
}
