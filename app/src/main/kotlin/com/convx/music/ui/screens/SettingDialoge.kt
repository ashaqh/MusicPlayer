package com.convx.music.ui.screens

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.convx.music.BuildConfig
import com.convx.music.LocalDatabase
import com.convx.music.R
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.Surface
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.utils.bounceClick
import kotlinx.coroutines.flow.map

@Composable
fun SettingDialoge(
    onDismissRequest: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val database = LocalDatabase.current.get()

    val likedSongsFlow = remember(database) { database.likedSongsCount() }
    val likedSongsCount by likedSongsFlow.collectAsState(initial = 0)

    val playlistCountFlow = remember(database) { database.playlistsByNameAsc().map { it.size } }
    val playlistCount by playlistCountFlow.collectAsState(initial = 0)

    val albumCountFlow = remember(database) { database.albumsByNameAsc().map { it.size } }
    val albumCount by albumCountFlow.collectAsState(initial = 0)

    Dialog(
        onDismissRequest = onDismissRequest,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val primaryColor = AppTheme.colorScheme.surface
        val onPrimaryColor = AppTheme.colorScheme.onSurface
        val secondaryColor = AppTheme.colorScheme.secondaryContainer
        val onSecondaryColor = AppTheme.colorScheme.onSecondaryContainer

        Surface(
            modifier = Modifier
                .padding(24.dp)
                .fillMaxWidth(),
            shape = RoundedCornerShape(28.dp),
            color = primaryColor,
            tonalElevation = 0.dp,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 4.dp, top = 10.dp, end = 4.dp, bottom = 10.dp),
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.apprise_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .size(24.dp)
                            .clip(CircleShape),
                    )
                    Text(
                        modifier = Modifier.wrapContentWidth(),
                        text = stringResource(R.string.app_name),
                        style = AppTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 1.sp,
                        ),
                        color = onPrimaryColor,
                        textAlign = TextAlign.Center,
                    )
                    Image(
                        painter = painterResource(id = R.drawable.close),
                        contentDescription = stringResource(R.string.dismiss),
                        modifier = Modifier
                            .size(24.dp)
                            .bounceClick { onDismissRequest() },
                        colorFilter = ColorFilter.tint(onPrimaryColor),
                    )
                }

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(25.dp))
                        .background(color = secondaryColor),
                ) {
                    OptionItem(
                        option = Option(stringResource(R.string.playlists), R.drawable.library_music_outlined),
                        tintColor = onPrimaryColor,
                        textColor = onSecondaryColor,
                        trailingText = playlistCount.toString(),
                    )
                    OptionItem(
                        option = Option(stringResource(R.string.albums), R.drawable.album),
                        tintColor = onPrimaryColor,
                        textColor = onSecondaryColor,
                        trailingText = albumCount.toString(),
                    )
                    OptionItem(
                        option = Option(stringResource(R.string.liked_songs), R.drawable.favorite_border),
                        tintColor = onPrimaryColor,
                        textColor = onSecondaryColor,
                        trailingText = likedSongsCount.toString(),
                    )
                }

                listOf(
                    Option(stringResource(R.string.settings), R.drawable.settings),
                    Option(stringResource(R.string.about), R.drawable.info),
                ).forEach { option ->
                    OptionItem(
                        option = option,
                        tintColor = onPrimaryColor,
                        textColor = onPrimaryColor,
                        trailingText = if (option.icon == R.drawable.info) BuildConfig.VERSION_NAME else null,
                        onClick = {
                            if (option.icon == R.drawable.settings) onNavigate("settings")
                            else onNavigate("settings/about")
                        },
                    )
                }
            }
        }
    }
}

private data class Option(
    val title: String,
    val icon: Int,
)

@Composable
private fun OptionItem(
    option: Option,
    tintColor: Color,
    textColor: Color,
    trailingText: String? = null,
    onClick: (() -> Unit)? = null,
) {
    val modifier = Modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.bounceClick { onClick() } else Modifier)
        .padding(12.dp)

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
    ) {
        Icon(
            painter = painterResource(id = option.icon),
            contentDescription = option.title,
            tint = tintColor,
            modifier = Modifier
                .padding(horizontal = 10.dp)
                .size(23.dp),
        )
        Text(
            modifier = Modifier.weight(1f),
            text = option.title,
            color = textColor,
            fontSize = 14.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (trailingText != null) {
            Text(
                text = trailingText,
                color = textColor.copy(alpha = 0.6f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Light,
                modifier = Modifier.padding(end = 10.dp),
            )
        }
    }
}
