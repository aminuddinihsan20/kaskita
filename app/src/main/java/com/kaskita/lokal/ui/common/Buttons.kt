package com.kaskita.lokal.ui.common

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.kaskita.lokal.ui.theme.Motion
import com.kaskita.lokal.ui.theme.finance

private val Pill = 28.dp // half of the 56dp button height

/** Corner radius that gives way a little while the button is pressed, so the press visibly "squeezes" it. */
@Composable
private fun pressedRadius(interaction: MutableInteractionSource, rest: Dp = Pill, pressed: Dp = 14.dp): State<Dp> {
    val isPressed by interaction.collectIsPressedAsState()
    return animateDpAsState(if (isPressed) pressed else rest, Motion.fastSpatial(), label = "pressedRadius")
}

@Composable
private fun ButtonLabel(text: String, icon: ImageVector?) {
    if (icon != null) Icon(icon, contentDescription = null, modifier = Modifier.padding(end = 8.dp))
    Text(text, style = MaterialTheme.typography.titleSmall)
}

/** The one main action of a screen: pastel periwinkle with dark text (still passes AA). */
@Composable
fun PrimaryButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    val radius by pressedRadius(interaction)
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(radius),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.finance.cta,
            contentColor = MaterialTheme.finance.onCta,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) { ButtonLabel(text, icon) }
}

/** A calmer action next to a primary one. */
@Composable
fun TonalButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    content: Color = MaterialTheme.colorScheme.onSecondaryContainer,
) {
    val interaction = remember { MutableInteractionSource() }
    val radius by pressedRadius(interaction)
    Button(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(radius),
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) { ButtonLabel(text, icon) }
}

/** Outlined, for the least important action (cancel, secondary). */
@Composable
fun QuietButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true, icon: ImageVector? = null) {
    val interaction = remember { MutableInteractionSource() }
    val radius by pressedRadius(interaction)
    OutlinedButton(
        onClick = onClick,
        modifier = modifier.heightIn(min = 56.dp),
        enabled = enabled,
        shape = RoundedCornerShape(radius),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.onSurface),
        interactionSource = interaction,
        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp),
    ) { ButtonLabel(text, icon) }
}

/** The destructive action: tinted with the error container, not shouting in solid red. */
@Composable
fun DangerButton(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    TonalButton(
        text, onClick, modifier, enabled,
        container = MaterialTheme.colorScheme.errorContainer,
        content = MaterialTheme.colorScheme.onErrorContainer,
    )
}

data class GroupButton(val label: String, val container: Color, val content: Color, val onClick: () -> Unit)

private fun groupShape(index: Int, count: Int, inner: Dp, outer: Dp = Pill) = RoundedCornerShape(
    topStart = if (index == 0) outer else inner,
    bottomStart = if (index == 0) outer else inner,
    topEnd = if (index == count - 1) outer else inner,
    bottomEnd = if (index == count - 1) outer else inner,
)

/** Buttons that belong together: joined with a hairline gap, outer corners round, inner corners square-ish. */
@Composable
fun ConnectedButtonGroup(buttons: List<GroupButton>, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        buttons.forEachIndexed { index, b ->
            val interaction = remember { MutableInteractionSource() }
            val inner by pressedRadius(interaction, rest = 10.dp, pressed = Pill)
            Button(
                onClick = b.onClick,
                modifier = Modifier.weight(1f).heightIn(min = 56.dp),
                shape = groupShape(index, buttons.size, inner),
                colors = ButtonDefaults.buttonColors(containerColor = b.container, contentColor = b.content),
                interactionSource = interaction,
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
            ) { Text(b.label, style = MaterialTheme.typography.titleSmall) }
        }
    }
}

/**
 * A single-choice group in the same family. The chosen option swells into a full pill (with a spring), the others stay
 * square-ish, so the selection is visible by shape and weight as well as by color.
 */
@Composable
fun ConnectedToggleGroup(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedContainers: List<Color> = List(options.size) { MaterialTheme.colorScheme.primaryContainer },
    selectedContents: List<Color> = List(options.size) { MaterialTheme.colorScheme.onPrimaryContainer },
) {
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val inner by animateDpAsState(if (selected) Pill else 10.dp, Motion.spatial(), label = "toggleRadius")
            val container by animateColorAsState(
                if (selected) selectedContainers[index] else MaterialTheme.colorScheme.surfaceContainerHigh,
                Motion.effects(), label = "toggleContainer",
            )
            val content by animateColorAsState(
                if (selected) selectedContents[index] else MaterialTheme.colorScheme.onSurfaceVariant,
                Motion.effects(), label = "toggleContent",
            )
            val shape = if (selected) RoundedCornerShape(Pill) else groupShape(index, options.size, inner)
            Box(
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 56.dp)
                    .clip(shape)
                    .background(container)
                    .selectable(selected = selected, role = Role.RadioButton, onClick = { onSelect(index) }),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.titleSmall,
                    color = content,
                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                )
            }
        }
    }
}
