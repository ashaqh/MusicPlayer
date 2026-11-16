/**
 * Apprise Music (C) 2026
 * Licensed under GPL-3.0 | See git history for contributors
 */

package com.convx.music.ui.menu

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import com.convx.music.R
import com.convx.music.db.entities.Playlist
import com.convx.music.db.entities.PlaylistSong
import com.convx.music.ui.component.IosMenuGroup
import com.convx.music.ui.component.IosMenuItemData
import com.convx.music.ui.component.SymbolIcon
import com.convx.music.ui.component.sf
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.Text

/**
 * Menu for Local Playlist Screen.
 */
@Composable
fun LocalPlaylistMenu(
    playlist: Playlist,
    songs: List<PlaylistSong>,
    context: Context,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onModifyWithAi: () -> Unit,
    onQueue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val menuItems = buildList {
        add(
            IosMenuItemData(
                title = { Text(stringResource(R.string.edit)) },
                description = { Text(stringResource(R.string.edit_playlist)) },
                icon = { SymbolIcon(symbol = sf("pencil"), contentDescription = null) },
                onClick = {
                    onEdit()
                    onDismiss()
                },
            )
        )

        add(
            IosMenuItemData(
                title = { Text(stringResource(R.string.add_to_queue)) },
                description = { Text(stringResource(R.string.add_to_queue_desc)) },
                icon = { SymbolIcon(symbol = sf("music.note.list"), contentDescription = null) },
                onClick = {
                    onQueue()
                    onDismiss()
                },
            )
        )

        if (playlist.playlist.isEditable) {
            add(
                IosMenuItemData(
                    title = { Text(stringResource(R.string.modify_with_ai)) },
                    description = { Text(stringResource(R.string.modify_with_ai_desc)) },
                    icon = { Icon(painter = painterResource(R.drawable.sparks), contentDescription = null) },
                    onClick = {
                        onModifyWithAi()
                        onDismiss()
                    },
                )
            )
        }

        add(
            IosMenuItemData(
                title = { Text(stringResource(R.string.share)) },
                description = { Text(stringResource(R.string.share_playlist_desc)) },
                icon = { SymbolIcon(symbol = sf("square.and.arrow.up"), contentDescription = null) },
                onClick = {
                    val shareText = songs.joinToString("\n") { it.song.song.title }
                    val sendIntent = Intent().apply {
                        action = Intent.ACTION_SEND
                        putExtra(Intent.EXTRA_TEXT, shareText)
                        type = "text/plain"
                    }
                    context.startActivity(Intent.createChooser(sendIntent, null))
                    onDismiss()
                },
            )
        )

        add(
            IosMenuItemData(
                title = { Text(stringResource(R.string.delete)) },
                description = { Text(stringResource(R.string.delete_playlist_desc)) },
                icon = { SymbolIcon(symbol = sf("trash"), contentDescription = null) },
                onClick = {
                    onDelete()
                    onDismiss()
                },
            )
        )
    }

    IosMenuGroup(items = menuItems)
}

/**
 * Menu for Auto Playlist Screen (Liked Songs, Local Songs).
 */
@Composable
fun AutoPlaylistMenu(
    onQueue: () -> Unit,
    onDismiss: () -> Unit,
) {
    IosMenuGroup(
        items = listOf(
            IosMenuItemData(
                title = { Text(stringResource(R.string.add_to_queue)) },
                description = { Text(stringResource(R.string.add_to_queue_desc)) },
                icon = { SymbolIcon(symbol = sf("music.note.list"), contentDescription = null) },
                onClick = {
                    onQueue()
                    onDismiss()
                },
            )
        )
    )
}

/**
 * Menu for Top Playlist Screen.
 */
@Composable
fun TopPlaylistMenu(
    onQueue: () -> Unit,
    onDismiss: () -> Unit,
) {
    IosMenuGroup(
        items = listOf(
            IosMenuItemData(
                title = { Text(stringResource(R.string.add_to_queue)) },
                description = { Text(stringResource(R.string.add_to_queue_desc)) },
                icon = { SymbolIcon(symbol = sf("music.note.list"), contentDescription = null) },
                onClick = {
                    onQueue()
                    onDismiss()
                },
            )
        )
    )
}
