package dev.blipwave.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import dev.blipwave.R
import dev.blipwave.ble.model.ConnectionState
import dev.blipwave.ui.theme.BlipTheme

@Composable
fun connectionStatusColor(state: ConnectionState): Color = when (state) {
    ConnectionState.Idle -> MaterialTheme.colorScheme.onSurfaceVariant
    ConnectionState.Connecting -> BlipTheme.colors.signalFair
    ConnectionState.Connected -> BlipTheme.colors.signalExcellent
    is ConnectionState.Disconnected ->
        if (state.unexpected) BlipTheme.colors.signalWeak else MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
fun connectionStatusLabel(state: ConnectionState): String = stringResource(
    when (state) {
        ConnectionState.Idle -> R.string.status_idle
        ConnectionState.Connecting -> R.string.status_connecting
        ConnectionState.Connected -> R.string.status_connected
        is ConnectionState.Disconnected ->
            if (state.unexpected) R.string.status_lost else R.string.status_disconnected
    },
)

/** Bağlanıyor / Bağlandı / Koptu durumunu renk, nokta ve metinle gösteren hap. */
@Composable
fun ConnectionStatusPill(state: ConnectionState, modifier: Modifier = Modifier) {
    val color by animateColorAsState(connectionStatusColor(state), tween(400), label = "statusColor")
    val shape = RoundedCornerShape(50)

    val transition = rememberInfiniteTransition(label = "statusDot")
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 0.25f,
        animationSpec = infiniteRepeatable(tween(600), RepeatMode.Reverse),
        label = "blink",
    )

    Row(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.55f), shape)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(8.dp)
                .graphicsLayer { alpha = if (state == ConnectionState.Connecting) blink else 1f }
                .clip(CircleShape)
                .background(color),
        )
        Spacer(Modifier.width(8.dp))
        AnimatedContent(
            targetState = connectionStatusLabel(state),
            transitionSpec = {
                (fadeIn(tween(220)) + slideInVertically { it / 2 }) togetherWith
                    (fadeOut(tween(160)) + slideOutVertically { -it / 2 })
            },
            label = "statusLabel",
        ) { label ->
            Text(label, style = MaterialTheme.typography.labelMedium, color = color)
        }
    }
}
