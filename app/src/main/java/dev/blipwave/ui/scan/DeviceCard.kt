package dev.blipwave.ui.scan

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.blipwave.R
import dev.blipwave.ble.NordicUart
import dev.blipwave.ble.SignalLevel
import dev.blipwave.ble.model.ScannedDevice
import dev.blipwave.ui.components.Badge
import dev.blipwave.ui.components.GradientButton
import dev.blipwave.ui.components.GradientCard
import dev.blipwave.ui.components.SignalBars
import dev.blipwave.ui.components.signalColor
import dev.blipwave.ui.theme.BlipTheme
import dev.blipwave.ui.theme.MonoStyle

/**
 * Tek bir tarama sonucu. İlk göründüğünde aşağıdan yaylanarak gelir;
 * dokununca genişleyip ayrıntıları ve "Bağlan" butonunu gösterir.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DeviceCard(
    device: ScannedDevice,
    expanded: Boolean,
    onToggle: () -> Unit,
    onConnect: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val appear = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        appear.animateTo(1f, spring(dampingRatio = Spring.DampingRatioLowBouncy, stiffness = Spring.StiffnessMediumLow))
    }
    val chevronRotation by animateFloatAsState(if (expanded) 180f else 0f, label = "chevron")
    val level = SignalLevel.fromRssi(device.rssi)

    GradientCard(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = appear.value.coerceIn(0f, 1f)
                translationY = (1f - appear.value) * 48.dp.toPx()
                val scale = 0.92f + 0.08f * appear.value
                scaleX = scale
                scaleY = scale
            },
        onClick = onToggle,
    ) {
        Column(
            modifier = Modifier
                .animateContentSize(spring(dampingRatio = 0.8f, stiffness = Spring.StiffnessMediumLow))
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                DeviceAvatar(named = device.hasName)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = device.name ?: stringResource(R.string.device_unnamed),
                        style = MaterialTheme.typography.titleMedium,
                        fontStyle = if (device.hasName) FontStyle.Normal else FontStyle.Italic,
                        color = if (device.hasName) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = device.address,
                        style = MonoStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column(horizontalAlignment = Alignment.End) {
                    SignalBars(rssi = device.rssi)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.rssi_value, device.rssi),
                        style = MaterialTheme.typography.labelSmall,
                        color = signalColor(level),
                    )
                }
                Icon(
                    Icons.Rounded.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .padding(start = 6.dp)
                        .size(20.dp)
                        .rotate(chevronRotation),
                )
            }

            AnimatedVisibility(
                visible = expanded,
                enter = fadeIn() + expandVertically(),
                exit = fadeOut() + shrinkVertically(),
            ) {
                Column {
                    Spacer(Modifier.height(14.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Badge(
                            text = stringResource(
                                if (device.isConnectable) R.string.device_connectable else R.string.device_not_connectable,
                            ),
                            color = if (device.isConnectable) {
                                BlipTheme.colors.signalExcellent
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                        if (NordicUart.SERVICE in device.serviceUuids) {
                            Badge(stringResource(R.string.device_uart_badge), BlipTheme.colors.signalFair)
                        }
                        if (device.serviceUuids.isNotEmpty()) {
                            Badge(
                                stringResource(R.string.device_services_count, device.serviceUuids.size),
                                MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    GradientButton(
                        text = stringResource(R.string.device_connect),
                        icon = Icons.Rounded.Link,
                        onClick = onConnect,
                        enabled = device.isConnectable,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        }
    }
}

@Composable
private fun DeviceAvatar(named: Boolean) {
    val brush: Brush = if (named) {
        BlipTheme.colors.coolGradient
    } else {
        SolidColor(MaterialTheme.colorScheme.surfaceContainerHighest)
    }
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Bluetooth,
            contentDescription = null,
            tint = if (named) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp),
        )
    }
}
