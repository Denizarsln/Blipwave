package dev.blipwave.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bluetooth
import androidx.compose.material.icons.rounded.BluetoothSearching
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import dev.blipwave.ui.theme.BlipTheme
import dev.blipwave.ui.theme.Coral
import dev.blipwave.ui.theme.ElectricViolet
import dev.blipwave.ui.theme.HotPink
import dev.blipwave.ui.theme.NeonCyan
import kotlin.math.cos
import kotlin.math.sin

/**
 * Tarama radarı: merkezden yayılan pulse halkaları, dönen tarama ışını ve
 * bulunan cihaz sayısı kadar yanıp sönen "blip" noktaları.
 */
@Composable
fun PulseRadar(
    active: Boolean,
    modifier: Modifier = Modifier,
    blipCount: Int = 0,
) {
    val transition = rememberInfiniteTransition(label = "radar")
    val pulse by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(2600, easing = LinearEasing)),
        label = "pulse",
    )
    val sweep by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(3200, easing = LinearEasing)),
        label = "sweep",
    )
    val breathe by transition.animateFloat(
        initialValue = 0.94f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(tween(900), RepeatMode.Reverse),
        label = "breathe",
    )
    // Başlat / durdur geçişinde animasyon yumuşakça açılıp kapanır
    val activity by animateFloatAsState(if (active) 1f else 0f, tween(700), label = "activity")

    val guideColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val glow = BlipTheme.colors.glow

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val maxRadius = size.minDimension / 2f

            // Sabit kılavuz halkaları
            for (ring in 1..3) {
                drawCircle(guideColor, radius = maxRadius * ring / 3f, style = Stroke(1.dp.toPx()))
            }

            if (activity > 0.01f) {
                // Dışa doğru yayılıp sönen pulse halkaları
                for (ring in 0 until 3) {
                    val progress = (pulse + ring / 3f) % 1f
                    drawCircle(
                        color = lerp(ElectricViolet, Coral, progress).copy(alpha = (1f - progress) * 0.7f * activity),
                        radius = maxRadius * progress,
                        style = Stroke(width = 1.dp.toPx() + 4.dp.toPx() * (1f - progress)),
                    )
                }

                // Dönen tarama ışını
                rotate(sweep) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            0f to Color.Transparent,
                            0.24f to NeonCyan.copy(alpha = 0.38f * activity),
                            0.25f to Color.Transparent,
                            1f to Color.Transparent,
                        ),
                        startAngle = 0f,
                        sweepAngle = 90f,
                        useCenter = true,
                        topLeft = Offset(center.x - maxRadius, center.y - maxRadius),
                        size = androidx.compose.ui.geometry.Size(maxRadius * 2, maxRadius * 2),
                    )
                }

                // Bulunan cihazları temsil eden noktalar (altın açı ile dağıtılır)
                for (index in 0 until blipCount.coerceAtMost(MAX_BLIPS)) {
                    val angle = Math.toRadians(index * 137.5)
                    val distance = maxRadius * (0.42f + (index % 4) * 0.14f)
                    val twinkle = 0.45f + 0.55f * ((pulse + index * 0.17f) % 1f)
                    val position = Offset(
                        center.x + (cos(angle) * distance).toFloat(),
                        center.y + (sin(angle) * distance).toFloat(),
                    )
                    drawCircle(NeonCyan.copy(alpha = 0.25f * twinkle * activity), 7.dp.toPx(), position)
                    drawCircle(NeonCyan.copy(alpha = twinkle * activity), 3.dp.toPx(), position)
                }
            }
        }

        // Merkez küre
        val orbScale = 1f + (breathe - 1f) * activity
        Box(
            modifier = Modifier
                .size(76.dp)
                .graphicsLayer {
                    scaleX = orbScale
                    scaleY = orbScale
                }
                .shadow(24.dp, CircleShape, ambientColor = glow, spotColor = HotPink)
                .clip(CircleShape)
                .background(BlipTheme.colors.brandGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = if (active) Icons.Rounded.BluetoothSearching else Icons.Rounded.Bluetooth,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(34.dp),
            )
        }
    }
}

private const val MAX_BLIPS = 10
