/**
 * Convx Project (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.screens.settings

import com.convx.music.ui.component.sf
import com.convx.music.ui.component.SymbolIcon
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.edit
import com.convx.music.utils.dataStore
import kotlinx.coroutines.launch
import android.content.ClipData
import android.content.ClipboardManager
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import com.convx.music.ui.ios.compat.AlertDialog
import com.convx.music.ui.ios.compat.BasicAlertDialog
import com.convx.music.ui.ios.compat.Surface
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.DropdownMenuItem
import com.convx.music.ui.ios.compat.ExposedDropdownMenuBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuDefaults
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.OutlinedTextField
import com.convx.music.ui.ios.compat.Slider
import com.convx.music.ui.ios.compat.Switch
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.ios.compat.TextButton
import com.convx.music.ui.ios.compat.TopAppBar
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.LocalPlayerConnection
import com.convx.music.R
import com.convx.music.constants.LyricsProviderOrderKey
import com.convx.music.constants.TopSize
import com.convx.music.lyrics.LyricsProviderRegistry
import com.convx.music.lyrics.LyricsProviderSettings
import com.convx.music.ui.component.DraggableLyricsProviderItem
import com.convx.music.ui.component.DraggableLyricsProviderList
import com.convx.music.ui.component.IosSettingsGroup
import com.convx.music.ui.component.IosSettingsItem
import com.convx.music.ui.component.PlaybackLogsDialog
import com.convx.music.utils.PlaybackLogManager
import com.convx.music.utils.dataStore
import com.convx.music.utils.rememberPreference
import racra.compose.smooth_corner_rect_library.AbsoluteSmoothCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.launch

/**
 * Advanced library & lyrics configuration: lyric source selection/order (including opt-in LRCLIB),
 * romanization, Top playlist size, and local playback logs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ContentAdvancedSettings(
    navController: NavController,
    scrollBehavior: TopAppBarScrollBehavior,
) {
    val context = LocalContext.current

    val (lyricsProviderOrder, onLyricsProviderOrderChange) = rememberPreference(key = LyricsProviderOrderKey, defaultValue = "")
    val (lengthTop, onLengthTopChange) = rememberPreference(key = TopSize, defaultValue = "50")

    var showPlaybackLogsDialog by rememberSaveable { mutableStateOf(false) }
    val playbackLogs by PlaybackLogManager.logs.collectAsStateWithLifecycle()

    var showProviderPriorityDialog by rememberSaveable { mutableStateOf(false) }

    if (showProviderPriorityDialog) {
        val defaultOrder = LyricsProviderRegistry.getDefaultProviderOrder()
        val providerPreferences by context.dataStore.data.collectAsStateWithLifecycle(initialValue = emptyPreferences())
        val providerScope = androidx.compose.runtime.rememberCoroutineScope()
        val enabledStates = defaultOrder.associateWith { providerPreferences[LyricsProviderSettings.enabledKey(it)] ?: false }

        val savedOrder = LyricsProviderRegistry.deserializeProviderOrder(lyricsProviderOrder)
        val normalizedOrder = savedOrder + defaultOrder.filter { it !in savedOrder }

        val lyricsIcon = painterResource(R.drawable.lyrics)
        val draggableItems = remember { mutableStateListOf<DraggableLyricsProviderItem>() }

        LaunchedEffect(normalizedOrder, enabledStates) {
            draggableItems.clear()
            draggableItems.addAll(
                normalizedOrder.map { name ->
                    DraggableLyricsProviderItem(
                        id = name,
                        name = LyricsProviderRegistry.getDisplayName(name),
                        icon = lyricsIcon,
                        enabled = enabledStates[name] ?: false,
                    )
                }
            )
        }

        val cardShape = AbsoluteSmoothCornerShape(30.dp, 60)
        val blockShape = AbsoluteSmoothCornerShape(22.dp, 60)
        val actionShape = AbsoluteSmoothCornerShape(18.dp, 60)

        BasicAlertDialog(onDismissRequest = { showProviderPriorityDialog = false }) {
            Surface(
                modifier = Modifier
                    .padding(horizontal = 24.dp)
                    .widthIn(max = 360.dp),
                shape = cardShape,
                color = AppTheme.colorScheme.surfaceContainerHigh,
                tonalElevation = 8.dp,
            ) {
                Column(
                    modifier = Modifier
                        .padding(horizontal = 18.dp, vertical = 18.dp),
                    verticalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Surface(
                        shape = blockShape,
                        color = AppTheme.colorScheme.surfaceContainer,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = AbsoluteSmoothCornerShape(12.dp, 60),
                                    color = AppTheme.colorScheme.secondaryContainer,
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        SymbolIcon(symbol = sf("quote.bubble"), contentDescription = null, size = 16.dp, tint = AppTheme.colorScheme.onSecondaryContainer)
                                        Text(
                                            text = stringResource(R.string.lyrics_provider_priority),
                                            style = AppTheme.typography.labelMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            color = AppTheme.colorScheme.onSecondaryContainer,
                                        )
                                    }
                                }
                            }

                            Text(
                                text = stringResource(R.string.lyrics_provider_priority_desc),
                                style = AppTheme.typography.bodySmall,
                                color = AppTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }

                    Surface(
                        shape = blockShape,
                        color = AppTheme.colorScheme.surfaceContainerLow,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                    ) {
                        DraggableLyricsProviderList(
                            items = draggableItems,
                            onItemsReordered = { reordered ->
                                onLyricsProviderOrderChange(
                                    LyricsProviderRegistry.serializeProviderOrder(reordered.map { it.id })
                                )
                            },
                            onToggleEnabled = { id ->
                                providerScope.launch {
                                    context.dataStore.edit { prefs ->
                                        val key = LyricsProviderSettings.enabledKey(id)
                                        prefs[key] = !(prefs[key] ?: false)
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End,
                    ) {
                        com.convx.music.ui.ios.compat.Button(
                            onClick = { showProviderPriorityDialog = false },
                            shape = actionShape,
                        ) {
                            Text(text = stringResource(R.string.close))
                        }
                    }
                }
            }
        }
    }

    var showTopLengthDialog by rememberSaveable { mutableStateOf(false) }
    if (showTopLengthDialog) {
        var tempLength by rememberSaveable { mutableFloatStateOf(lengthTop.toFloat()) }

        AlertDialog(
            onDismissRequest = { showTopLengthDialog = false },
            title = { Text(stringResource(R.string.top_length)) },
            text = {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(tempLength.toInt().toString())
                    Slider(
                        value = tempLength,
                        onValueChange = { tempLength = it },
                        valueRange = 1f..100f,
                        steps = 98
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onLengthTopChange(tempLength.toInt().toString())
                        showTopLengthDialog = false
                    }
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        )
    }

    if (showPlaybackLogsDialog) {
        PlaybackLogsDialog(
            logs = playbackLogs,
            onClear = { PlaybackLogManager.clearLogs() },
            onDismiss = { showPlaybackLogsDialog = false }
        )
    }

    SettingsPage(
        navController = navController,
        title = stringResource(R.string.advanced),
        pageColor = AppTheme.colorScheme.background,
        horizontalPadding = 16.dp,
    ) {
        IosSettingsGroup(
            title = "Lyrics sources",
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.lyrics),
                    title = { Text(stringResource(R.string.lyrics_provider_priority)) },
                    description = { Text(stringResource(R.string.lyrics_provider_priority_desc)) },
                    onClick = { showProviderPriorityDialog = true },
                ),
                IosSettingsItem(
                    icon = painterResource(R.drawable.language_korean_latin),
                    title = { Text(stringResource(R.string.lyrics_romanization)) },
                    onClick = { navController.navigate("settings/content/romanization") }
                )
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = "Library",
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.trending_up),
                    title = { Text(stringResource(R.string.top_length)) },
                    description = { Text(lengthTop) },
                    onClick = { showTopLengthDialog = true }
                ),
            )
        )

        Spacer(modifier = Modifier.height(27.dp))

        IosSettingsGroup(
            title = stringResource(R.string.logs_heading),
            items = listOf(
                IosSettingsItem(
                    icon = painterResource(R.drawable.bug_report),
                    title = { Text(stringResource(R.string.playback_logs)) },
                    description = { Text(stringResource(R.string.playback_logs_desc)) },
                    onClick = { showPlaybackLogsDialog = true }
                ),
            )
        )
        Spacer(modifier = Modifier.height(16.dp))
    }
}
