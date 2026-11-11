package dev.blipwave.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import dev.blipwave.ui.theme.BlipTheme

/** Gradyan dolgulu, basınca hafifçe küçülen ana eylem butonu. */
@Composable
fun GradientButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    brush: Brush = BlipTheme.colors.brandGradient,
    icon: ImageVector? = null,
    enabled: Boolean = true,
) {
    val shape = RoundedCornerShape(50)
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.95f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 500f),
        label = "buttonPress",
    )
    val glow = BlipTheme.colors.glow

    Row(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = if (enabled) 1f else 0.4f
            }
            .shadow(if (enabled) 14.dp else 0.dp, shape, ambientColor = glow, spotColor = glow)
            .clip(shape)
            .background(brush)
            .clickable(
                interactionSource = interaction,
                indication = LocalIndication.current,
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .heightIn(min = 52.dp)
            .padding(horizontal = 22.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** Yumuşak gölgeli, ince kenarlıklı gradyan kart. */
@Composable
fun GradientCard(
    modifier: Modifier = Modifier,
    brush: Brush = BlipTheme.colors.cardGradient,
    shape: Shape = MaterialTheme.shapes.large,
    elevation: Dp = 10.dp,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.() -> Unit,
) {
    val glow = BlipTheme.colors.glow.copy(alpha = 0.25f)
    Box(
        modifier = modifier
            .shadow(elevation, shape, ambientColor = glow, spotColor = glow)
            .clip(shape)
            .background(brush)
            .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.45f), shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        content = content,
    )
}

@Composable
fun GradientText(
    text: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    brush: Brush = BlipTheme.colors.brandGradient,
) {
    Text(text = text, style = style.copy(brush = brush), modifier = modifier)
}

/** Küçük renkli etiket (DEMO, UART, özellik rozetleri). */
@Composable
fun Badge(
    text: String,
    color: Color,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    Text(
        text = text,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.14f))
            .border(1.dp, color.copy(alpha = 0.5f), shape)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    )
}
