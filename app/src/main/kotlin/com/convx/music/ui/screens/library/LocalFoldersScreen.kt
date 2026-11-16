package com.convx.music.ui.screens.library

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavController
import com.convx.music.LocalPlayerAwareWindowInsets
import com.convx.music.R
import com.convx.music.ui.component.*
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.screens.library.apple.Chevron
import com.convx.music.utils.LocalFolderIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeSource

@Composable
fun LocalMusicFolderRow(folder: LocalFolderIndex.Folder, navController: NavController) {
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 64.dp)
            .clickable { navController.navigate("local_folder/" + Uri.encode(folder.path)) }
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SymbolIcon(symbol = sf("folder.fill"), contentDescription = null, size = 28.dp, tint = AppTheme.colorScheme.primary)
        Column(Modifier.weight(1f)) {
            Text(
                text = folder.name,
                style = AppTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = folder.path,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = pluralStringResource(R.plurals.n_song, folder.songIds.size, folder.songIds.size),
            style = AppTheme.typography.bodySmall,
            color = AppTheme.colorScheme.onSurfaceVariant,
        )
        Chevron()
    }
}

@Composable
fun LocalMusicFoldersEntry(count: Int?, navController: NavController) {
    val browseFoldersDesc = stringResource(R.string.folders)
    Row(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable { navController.navigate("local_folders") }
            .semantics {
                role = Role.Button
                contentDescription = browseFoldersDesc
            }
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SymbolIcon(symbol = sf("folder.fill"), contentDescription = null, tint = AppTheme.colorScheme.primary)
        Text(stringResource(R.string.folders), style = AppTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        if (count != null) {
            Text(
                text = count.toString(),
                style = AppTheme.typography.bodyMedium,
                color = AppTheme.colorScheme.onSurfaceVariant,
            )
        }
        Chevron()
    }
}

@Composable
fun LocalFoldersScreen(navController: NavController, viewModel: LocalMusicViewModel = hiltViewModel()) {
    val context = LocalContext.current
    val folders by viewModel.folders.collectAsStateWithLifecycle()
    val scanning by viewModel.isScanning.collectAsStateWithLifecycle()
    val permission = rememberLocalAudioPermission { }
    LaunchedEffect(permission.granted) { if (permission.granted) viewModel.scanDevice(context, force = false) }

    val listState = rememberLazyListState()
    val hazeState = remember { HazeState() }
    val pageInsets = LocalPlayerAwareWindowInsets.current.asPaddingValues()
    val dir = androidx.compose.ui.platform.LocalLayoutDirection.current
    val titleCollapse by rememberTitleCollapseProgressState(listState, titleItemKey = "folders_large_title")
    val foldersTitle = stringResource(R.string.folders)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(AppTheme.colorScheme.background),
    ) {
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .hazeSource(hazeState),
            contentPadding = PaddingValues(
                start = pageInsets.calculateStartPadding(dir),
                top = topBarContentPadding(),
                end = pageInsets.calculateEndPadding(dir),
                bottom = pageInsets.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item(key = "folders_large_title") {
                Text(
                    text = foldersTitle,
                    style = AppTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .semantics { heading() },
                )
            }
            if (!permission.granted) {
                item(key = "folders_permission") { LocalMusicPermissionPrompt(permission) }
            } else if (folders.isEmpty() && scanning) {
                item(key = "folders_loading") {
                    AppLoadingState(message = stringResource(R.string.scanning_local_files))
                }
            } else if (folders.isEmpty()) {
                item(key = "folders_empty") {
                    EmptyPlaceholder(
                        icon = R.drawable.music_note,
                        text = stringResource(R.string.excluded_folders_empty),
                        actionLabel = stringResource(R.string.local_empty_scan),
                        onAction = { viewModel.scanDevice(context, force = true) },
                    )
                }
            } else {
                items(folders, key = { it.path }) { LocalMusicFolderRow(it, navController) }
            }
        }

        TopFadeBlur(
            hazeState = hazeState,
            pageColor = AppTheme.colorScheme.background,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        FrostedTopBar(
            title = foldersTitle,
            titleAlpha = titleCollapse,
            onBack = { navController.navigateUp() },
        )
    }
}
