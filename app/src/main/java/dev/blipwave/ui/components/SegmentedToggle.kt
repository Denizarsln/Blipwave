package dev.blipwave.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.blipwave.ui.theme.BlipTheme

/** Seçili seçeneğin altında kayan gradyan göstergeli ikili/çoklu geçiş. */
@Composable
fun SegmentedToggle(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    segmentWidth: Dp = 64.dp,
) {
    val shape = RoundedCornerShape(50)
    val indicatorOffset by animateDpAsState(
        targetValue = segmentWidth * selectedIndex,
        animationSpec = spring(dampingRatio = 0.7f, stiffness = 400f),
        label = "segmentOffset",
    )

    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
            .padding(3.dp)
            .selectableGroup(),
    ) {
        Box(
            Modifier
                .offset(x = indicatorOffset)
                .width(segmentWidth)
                .height(32.dp)
                .clip(shape)
                .background(BlipTheme.colors.coolGradient),
        )
        Row {
            options.forEachIndexed { index, label ->
                val selected = index == selectedIndex
                val textColor by animateColorAsState(
                    if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    label = "segmentText",
                )
                Box(
                    modifier = Modifier
                        .width(segmentWidth)
                        .height(32.dp)
                        .clip(shape)
                        .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) }),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = textColor)
                }
            }
        }
    }
}
