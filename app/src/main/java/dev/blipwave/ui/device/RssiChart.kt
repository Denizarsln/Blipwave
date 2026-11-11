package dev.blipwave.ui.device

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import dev.blipwave.R
import dev.blipwave.data.RssiHistory
import dev.blipwave.data.RssiSample
import dev.blipwave.ui.theme.ElectricViolet
import dev.blipwave.ui.theme.HotPink
import dev.blipwave.ui.theme.NeonCyan

private const val CHART_MIN_RSSI = -100f
private const val CHART_MAX_RSSI = -30f
private val GRID_LEVELS = listOf(-40, -60, -80)

/**
 * Son 60 saniyenin RSSI çizgi grafiği. [live] iken zaman ekseni her karede kayar,
 * böylece saniyede bir gelen örnekler arasında grafik akıcı görünür.
 */
@Composable
fun RssiChart(
    samples: List<RssiSample>,
    live: Boolean,
    modifier: Modifier = Modifier,
) {
    if (samples.isEmpty()) {
        Box(modifier, contentAlignment = Alignment.Center) {
            Text(
                stringResource(R.string.detail_signal_waiting),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val lastTimestamp = samples.last().timestampMillis
    val now by produceState(initialValue = lastTimestamp, live, lastTimestamp) {
        if (!live) {
            value = lastTimestamp
            return@produceState
        }
        while (true) {
            withFrameMillis { }
            value = System.currentTimeMillis()
        }
    }

    val gridColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f)
    val labelStyle = MaterialTheme.typography.labelSmall.copy(color = MaterialTheme.colorScheme.onSurfaceVariant)
    val textMeasurer = rememberTextMeasurer()

    Canvas(modifier.fillMaxSize()) {
        val labelGutter = 34.dp.toPx()
        val chartWidth = size.width - labelGutter
        val chartHeight = size.height

        fun xFor(timestamp: Long): Float =
            chartWidth * (1f - (now - timestamp).toFloat() / RssiHistory.WINDOW_MILLIS)

        fun yFor(rssi: Float): Float {
            val fraction = (rssi.coerceIn(CHART_MIN_RSSI, CHART_MAX_RSSI) - CHART_MIN_RSSI) /
                (CHART_MAX_RSSI - CHART_MIN_RSSI)
            return chartHeight * (1f - fraction)
        }

        // Yatay kılavuz çizgileri ve dBm etiketleri
        val dash = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 6.dp.toPx()))
        for (level in GRID_LEVELS) {
            val y = yFor(level.toFloat())
            drawLine(gridColor, Offset(0f, y), Offset(chartWidth, y), strokeWidth = 1.dp.toPx(), pathEffect = dash)
            val label = textMeasurer.measure(level.toString(), labelStyle)
            drawText(label, topLeft = Offset(size.width - label.size.width, y - label.size.height / 2f))
        }

        val points = samples.map { Offset(xFor(it.timestampMillis), yFor(it.rssi.toFloat())) }
        val line = Path().apply {
            moveTo(points.first().x, points.first().y)
            // Komşu noktaların orta x'inde kontrol noktası: yumuşak ama taşmayan eğri
            for (index in 1 until points.size) {
                val previous = points[index - 1]
                val current = points[index]
                val midX = (previous.x + current.x) / 2f
                cubicTo(midX, previous.y, midX, current.y, current.x, current.y)
            }
        }
        val area = Path().apply {
            addPath(line)
            lineTo(points.last().x, chartHeight)
            lineTo(points.first().x, chartHeight)
            close()
        }

        clipRect(left = 0f, top = 0f, right = chartWidth, bottom = chartHeight) {
            drawPath(
                area,
                Brush.verticalGradient(listOf(ElectricViolet.copy(alpha = 0.38f), Color.Transparent)),
            )
            drawPath(
                line,
                Brush.horizontalGradient(listOf(ElectricViolet, HotPink, NeonCyan), endX = chartWidth),
                style = Stroke(3.dp.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round),
            )
            val head = points.last()
            drawCircle(NeonCyan.copy(alpha = 0.3f), 9.dp.toPx(), head)
            drawCircle(NeonCyan, 4.5.dp.toPx(), head)
        }
    }
}
