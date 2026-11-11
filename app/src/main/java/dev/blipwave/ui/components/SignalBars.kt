package dev.blipwave.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.blipwave.ble.SignalLevel
import dev.blipwave.ui.theme.BlipTheme

@Composable
fun signalColor(level: SignalLevel): Color {
    val colors = BlipTheme.colors
    return when (level) {
        SignalLevel.EXCELLENT -> colors.signalExcellent
        SignalLevel.GOOD -> colors.signalGood
        SignalLevel.FAIR -> colors.signalFair
        SignalLevel.WEAK -> colors.signalWeak
    }
}

/** Dört çubuklu sinyal gücü göstergesi; renk ve dolu çubuk sayısı RSSI'a göre canlanır. */
@Composable
fun SignalBars(
    rssi: Int,
    modifier: Modifier = Modifier,
    barWidth: Dp = 5.dp,
    maxHeight: Dp = 20.dp,
) {
    val level = SignalLevel.fromRssi(rssi)
    val activeColor by animateColorAsState(signalColor(level), tween(400), label = "signalColor")
    val idleColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)

    Row(
        modifier = modifier.height(maxHeight),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (index in 1..4) {
            val color by animateColorAsState(
                if (index <= level.bars) activeColor else idleColor,
                tween(300),
                label = "bar$index",
            )
            Box(
                Modifier
                    .width(barWidth)
                    .height(maxHeight * (0.25f * index + 0f))
                    .clip(RoundedCornerShape(2.dp))
                    .background(color),
            )
        }
    }
}
