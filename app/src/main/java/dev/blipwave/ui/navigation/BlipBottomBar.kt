package dev.blipwave.ui.navigation

import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Radar
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import dev.blipwave.R
import dev.blipwave.ui.theme.BlipTheme

enum class TopDestination(val route: String, @StringRes val label: Int, val icon: ImageVector) {
    SCAN(Routes.SCAN, R.string.nav_scan, Icons.Rounded.Radar),
    CONNECTIONS(Routes.CONNECTIONS, R.string.nav_connections, Icons.Rounded.Link),
    SETTINGS(Routes.SETTINGS, R.string.nav_settings, Icons.Rounded.Settings),
}

/** Yüzen hap biçimli alt menü; seçili sekme gradyanla genişleyip etiketini gösterir. */
@Composable
fun BlipBottomBar(
    current: TopDestination?,
    onSelect: (TopDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(32.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(20.dp, shape, ambientColor = BlipTheme.colors.glow, spotColor = BlipTheme.colors.glow)
                .clip(shape)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh)
                .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f), shape)
                .padding(6.dp)
                .selectableGroup(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            TopDestination.entries.forEach { destination ->
                val selected = destination == current
                val weight by animateFloatAsState(
                    targetValue = if (selected) 1.9f else 1f,
                    animationSpec = spring(dampingRatio = 0.75f, stiffness = 380f),
                    label = "tabWeight",
                )
                BottomBarItem(
                    destination = destination,
                    selected = selected,
                    onClick = { onSelect(destination) },
                    modifier = Modifier.weight(weight),
                )
            }
        }
    }
}

@Composable
private fun BottomBarItem(
    destination: TopDestination,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(26.dp)
    val contentColor by animateColorAsState(
        if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        label = "tabContent",
    )
    val label = stringResource(destination.label)

    Row(
        modifier = modifier
            .height(52.dp)
            .clip(shape)
            .background(if (selected) BlipTheme.colors.brandGradient else SolidColor(Color.Transparent))
            .selectable(selected = selected, role = Role.Tab, onClick = onClick),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Seçili değilken etiket görünmediği için ikon erişilebilirlik adını taşır
        Icon(destination.icon, contentDescription = if (selected) null else label, tint = contentColor)
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Row {
                Spacer(Modifier.width(8.dp))
                Text(label, style = MaterialTheme.typography.labelLarge, color = contentColor, maxLines = 1)
            }
        }
    }
}
