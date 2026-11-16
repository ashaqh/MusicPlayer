package com.apprise.music.entitlement

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.convx.music.R
import com.convx.music.ui.component.DefaultDialog
import com.convx.music.ui.component.shapes.ContinuousCapsule
import com.convx.music.ui.component.shapes.ContinuousRoundedRectangle
import com.convx.music.ui.ios.compat.AppTheme
import com.convx.music.ui.ios.compat.Button
import com.convx.music.ui.ios.compat.Icon
import com.convx.music.ui.ios.compat.Text
import com.convx.music.ui.ios.compat.TextButton

val LocalEntitlementProvider = staticCompositionLocalOf<EntitlementProvider> {
    DefaultFreeEntitlementProvider()
}

/** Reactive convenience hook for any Composable evaluating a centralized [FeatureCapability]. */
@Composable
fun rememberCapabilityEnabled(capability: FeatureCapability): Boolean {
    val provider = LocalEntitlementProvider.current
    val snapshot by provider.entitlements.collectAsStateWithLifecycle()
    return capability in snapshot.unlockedCapabilities
}

/** Reactive convenience hook returning the current [EntitlementSnapshot]. */
@Composable
fun rememberEntitlementSnapshot(): EntitlementSnapshot {
    val provider = LocalEntitlementProvider.current
    val snapshot by provider.entitlements.collectAsStateWithLifecycle()
    return snapshot
}

@Composable
fun ProBadge(
    modifier: Modifier = Modifier,
    text: String = "PRO",
) {
    Text(
        text = text,
        style = AppTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = AppTheme.colorScheme.onPrimary,
        modifier = modifier
            .clip(ContinuousCapsule)
            .background(AppTheme.colorScheme.primary)
            .padding(horizontal = 8.dp, vertical = 2.dp),
    )
}

@Composable
fun ProLockedBanner(
    title: String,
    description: String,
    onUpgradeClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(ContinuousRoundedRectangle(16.dp))
            .background(AppTheme.colorScheme.surfaceContainerHigh)
            .clickable(onClick = onUpgradeClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            painter = painterResource(R.drawable.star),
            contentDescription = null,
            tint = AppTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
        Column(modifier = Modifier.weight(1f)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = title,
                    style = AppTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = AppTheme.colorScheme.onSurface,
                )
                ProBadge()
            }
            Spacer(Modifier.height(2.dp))
            Text(
                text = description,
                style = AppTheme.typography.bodySmall,
                color = AppTheme.colorScheme.onSurfaceVariant,
            )
        }
        Button(onClick = onUpgradeClick) {
            Text("Unlock")
        }
    }
}

@Composable
fun ProUpgradeDialog(
    featureName: String,
    description: String,
    onDismiss: () -> Unit,
    onUpgrade: () -> Unit,
) {
    DefaultDialog(
        onDismiss = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.star),
                    contentDescription = null,
                    tint = AppTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Text(text = "$featureName requires Apprise Pro")
            }
        },
        buttons = {
            TextButton(onClick = onDismiss) {
                Text("Not now")
            }
            Spacer(Modifier.width(4.dp))
            Button(
                onClick = {
                    onDismiss()
                    onUpgrade()
                },
            ) {
                Text("View Pro Options")
            }
        },
    ) {
        Text(
            text = description,
            style = AppTheme.typography.bodyMedium,
            color = AppTheme.colorScheme.onSurfaceVariant,
        )
    }
}

fun capabilityDisplaySummary(capability: FeatureCapability): Pair<String, String> = when (capability) {
    FeatureCapability.AD_FREE -> "Ad-Free Listening" to
        "Removes banner placements on Home, Library, and Playlist screens."
    FeatureCapability.ADVANCED_EQUALIZER,
    FeatureCapability.PARAMETRIC_EQ -> "Advanced Parametric Equalizer" to
        "Unlocks unlimited parametric APO bands, AutoEQ profile import, spatial processing, and bit-perfect controls."
    FeatureCapability.PREMIUM_THEMES,
    FeatureCapability.THEMING_PRESETS -> "Premium Themes & Liquid Glass" to
        "Unlocks curated theme palettes, OLED Pure Black mode, custom fonts, Liquid Glass effects, and custom Home backgrounds."
    FeatureCapability.VISUALIZER -> "Audio Waveform Visualizer" to
        "Unlocks real-time waveform seekbars in the mini-player and Now Playing screen, plus Ambient Visualizer mode."
    FeatureCapability.CROSSFADE,
    FeatureCapability.AUTOMIX_DSP -> "Crossfade & Smart Transitions" to
        "Unlocks custom track crossfading, Automix beat/key transitions, and creative DJ transition effects."
    FeatureCapability.ADVANCED_CUSTOMIZATION -> "Advanced Customization" to
        "Unlocks the DIY Player Editor, custom player control icons, appearance presets, alternate app icons, and grid density overrides."
    FeatureCapability.LOCAL_PLAYBACK -> "Local Music Engine" to
        "Full offline music scanning, library browsing, queue, playlists, favourites, sleep timer, and core playback."
    FeatureCapability.NETWORK_STORAGE -> "Network Storage" to
        "Browse personal local network shares."
}

