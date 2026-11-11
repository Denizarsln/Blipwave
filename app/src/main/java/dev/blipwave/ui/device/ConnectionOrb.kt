package dev.blipwave.ui.device

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothConnected
import androidx.compose.material.icons.rounded.BluetoothDisabled
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import dev.blipwave.ble.model.ConnectionState
import dev.blipwave.ui.components.connectionStatusColor
import dev.blipwave.ui.theme.ElectricViolet
import dev.blipwave.ui.theme.HotPink
import dev.blipwave.ui.theme.NeonCyan

/**
 * Bağlantı durumunun görsel özeti:
 * - Bağlanıyor: dönen gradyan yay + yayılan halkalar
 * - Bağlandı: yaylanarak "oturan" dolu parlak halka
 * - Koptu: mercan rengi kesikli halka
 */
@Composable
fun ConnectionOrb(state: ConnectionState, modifier: Modifier = Modifier) {
    val accent by animateColorAsState(connectionStatusColor(state), tween(500), label = "orbAccent")
    val connecting by animateFloatAsState(
        if (state == ConnectionState.Connecting) 1f else 0f,
        tween(400),
        label = "connecting",
    )

    val transition = rememberInfiniteTransition(label = "orb")
    val spin by transition.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(1100, easing = LinearEasing)),
        label = "spin",
    )
    val ripple by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(1600, easing = LinearEasing), RepeatMode.Restart),
        label = "ripple",
    )

    // Bağlantı kurulduğu an küre küçülüp yaylanarak geri gelir
    val pop = remember { Animatable(1f) }
    LaunchedEffect(state) {
        if (state == ConnectionState.Connected) {
            pop.snapTo(0.8f)
            pop.animateTo(1f, spring(dampingRatio = Spring.DampingRatioHighBouncy, stiffness = Spring.StiffnessLow))
        }
    }

    val trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val isLost = state is ConnectionState.Disconnected && state.unexpected

    Box(
        modifier = modifier.graphicsLayer {
            scaleX = pop.value
            scaleY = pop.value
        },
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val strokeWidth = 6.dp.toPx()
            val radius = size.minDimension / 2f - strokeWidth

            // Bağlanırken dışa yayılan halkalar
            if (connecting > 0.01f) {
                for (ring in 0 until 2) {
                    val progress = (ripple + ring * 0.5f) % 1f
                    drawCircle(
                        color = accent.copy(alpha = (1f - progress) * 0.35f * connecting),
                        radius = radius * (0.7f + 0.3f * progress) + strokeWidth,
                        style = Stroke(2.dp.toPx()),
                    )
                }
            }

            when {
                state == ConnectionState.Connected -> {
                    drawCircle(accent.copy(alpha = 0.18f), radius + strokeWidth, style = Stroke(strokeWidth * 2))
                    drawCircle(
                        brush = Brush.sweepGradient(listOf(NeonCyan, ElectricViolet, HotPink, NeonCyan)),
                        radius = radius,
                        style = Stroke(strokeWidth),
                    )
                }
                state == ConnectionState.Connecting -> {
                    drawCircle(trackColor, radius, style = Stroke(strokeWidth))
                    rotate(spin) {
                        drawArc(
                            brush = Brush.sweepGradient(listOf(Color.Transparent, accent, HotPink)),
                            startAngle = 0f,
                            sweepAngle = 300f,
                            useCenter = false,
                            topLeft = androidx.compose.ui.geometry.Offset(center.x - radius, center.y - radius),
                            size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                            style = Stroke(strokeWidth, cap = StrokeCap.Round),
                        )
                    }
                }
                isLost -> drawCircle(
                    color = accent,
                    radius = radius,
                    style = Stroke(
                        strokeWidth,
                        cap = StrokeCap.Round,
                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(14.dp.toPx(), 12.dp.toPx())),
                    ),
                )
                else -> drawCircle(trackColor, radius, style = Stroke(strokeWidth))
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(22.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.16f)),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(targetState = state, animationSpec = tween(300), label = "orbIcon") { current ->
                Icon(
                    imageVector = when (current) {
                        ConnectionState.Connected -> Icons.Rounded.BluetoothConnected
                        ConnectionState.Connecting -> Icons.Rounded.BluetoothSearching
                        is ConnectionState.Disconnected ->
                            if (current.unexpected) Icons.Rounded.BluetoothDisabled else Icons.Rounded.Bluetooth
                        ConnectionState.Idle -> Icons.Rounded.Bluetooth
                    },
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(40.dp),
                )
            }
        }
    }
}
