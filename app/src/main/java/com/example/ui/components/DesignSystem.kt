package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.LocalIndication
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.Accent
import com.example.ui.theme.AccentBright
import com.example.ui.theme.Hairline
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkTertiary
import com.example.ui.theme.Navy
import com.example.ui.theme.Outline
import com.example.ui.theme.PwMotion
import com.example.ui.theme.SurfaceMuted
import com.example.ui.theme.SurfaceWhite
import com.example.ui.theme.Track
import com.example.ui.theme.TrackSoft

/**
 * Pennywise design system: the only building blocks screens should use for cards, buttons,
 * chips, fields and rows, so spacing, shapes and type stay consistent everywhere.
 */
object Space {
    val xs = 4.dp
    val s = 8.dp
    val m = 12.dp
    val l = 16.dp
    val xl = 20.dp
    val xxl = 24.dp

    /** Horizontal padding of every screen. */
    val screen = 16.dp

    /** Gap between cards on a screen. */
    val section = 16.dp
}

/** Cards 20, hero 24, sheets 28, buttons and controls are pills. */
val CardShape = RoundedCornerShape(20.dp)
val HeroShape = RoundedCornerShape(24.dp)
val SheetShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
val PillShape = RoundedCornerShape(percent = 50)
val FieldShape = RoundedCornerShape(14.dp)
val ButtonShape = PillShape

/**
 * Soft two-layer elevation tinted toward navy instead of black: a tight contact shadow plus a
 * wide, faint one, so cards lift off the grey canvas without outlines. (Colour is honoured on
 * Android 9+; older versions use grey.)
 */
fun Modifier.softShadow(shape: Shape = CardShape, elevation: Dp = 8.dp): Modifier =
    this
        .shadow(elevation = 1.dp, shape = shape, ambientColor = Navy.copy(alpha = 0.04f), spotColor = Navy.copy(alpha = 0.06f))
        .shadow(elevation = elevation, shape = shape, ambientColor = Navy.copy(alpha = 0.04f), spotColor = Navy.copy(alpha = 0.10f))

/** Shadow tinted with the accent, under primary pill buttons. */
fun Modifier.accentShadow(shape: Shape = PillShape, elevation: Dp = 10.dp): Modifier =
    shadow(elevation = elevation, shape = shape, ambientColor = Accent.copy(alpha = 0.20f), spotColor = Accent.copy(alpha = 0.45f))

/** Shrinks slightly while pressed: the design's press feedback on buttons. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource, pressedScale: Float = 0.97f): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) pressedScale else 1f, PwMotion.fade(120), label = "press_scale")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** White rounded card on the grey canvas. */
@Composable
fun PwCard(
    modifier: Modifier = Modifier,
    color: Color = SurfaceWhite,
    contentPadding: PaddingValues = PaddingValues(Space.l),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .softShadow()
            .clip(CardShape)
            .background(color)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content
    )
}

/** Section title with an optional action on the right ("See all"). */
@Composable
fun PwSectionTitle(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    action: String? = null,
    onAction: (() -> Unit)? = null
) {
    Row(modifier = modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Ink)
            if (subtitle != null) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = InkSecondary)
            }
        }
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                color = Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = Space.xs, vertical = Space.xs)
            )
        }
    }
}

/**
 * Pill chip. Selected is navy with white text; unselected is white with a hairline border
 * (or [unselectedColor] without a border when one is given, e.g. on a white card).
 */
@Composable
fun PwChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unselectedColor: Color = SurfaceWhite,
    leading: (@Composable () -> Unit)? = null
) {
    val background by animateColorAsState(if (selected) Navy else unselectedColor, PwMotion.fade(), label = "chip_bg")
    val content by animateColorAsState(if (selected) SurfaceWhite else Ink, PwMotion.fade(), label = "chip_fg")
    val borderColor = if (selected || unselectedColor != SurfaceWhite) Color.Transparent else Hairline
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 34.dp)
            .clip(PillShape)
            .background(background)
            .border(1.dp, borderColor, PillShape)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.m, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        leading?.invoke()
        Text(
            text,
            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
            color = content,
            maxLines = 1
        )
    }
}

/**
 * Mutually exclusive options in a pill track; the selected one is a white pill that slides
 * across with a small spring. [dark] is the version used on the navy hero card.
 */
@Composable
fun PwSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedColors: List<Color> = emptyList(),
    dark: Boolean = false,
    height: Dp = 34.dp
) {
    if (options.isEmpty()) return
    val trackColor = if (dark) SurfaceWhite.copy(alpha = 0.10f) else Track
    val inset = if (dark) 3.dp else 4.dp
    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .clip(PillShape)
            .background(trackColor)
            .padding(inset)
    ) {
        val thumbWidth = maxWidth / options.size
        val index = selectedIndex.coerceIn(0, options.lastIndex)
        val thumbOffset by animateDpAsState(thumbWidth * index, PwMotion.thumb(), label = "segment_thumb")
        Box(
            modifier = Modifier
                .offset(x = thumbOffset)
                .width(thumbWidth)
                .height(height)
                .softShadow(PillShape, 2.dp)
                .clip(PillShape)
                .background(SurfaceWhite)
        )
        Row(modifier = Modifier.fillMaxWidth()) {
            options.forEachIndexed { i, label ->
                val selected = i == index
                val selectedColor = selectedColors.getOrNull(i) ?: if (dark) Navy else Ink
                val unselectedColor = if (dark) SurfaceWhite.copy(alpha = 0.8f) else InkSecondary
                val textColor by animateColorAsState(if (selected) selectedColor else unselectedColor, PwMotion.fade(250), label = "segment_text")
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(height)
                        .clip(PillShape)
                        .clickable { onSelect(i) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge.copy(
                            fontWeight = if (selected || dark) FontWeight.Bold else FontWeight.Medium
                        ),
                        color = textColor,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

/** Main call to action: purple gradient pill with a tinted shadow, 52dp tall. */
@Composable
fun PwPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    icon: ImageVector? = null,
    containerColor: Color = Accent
) {
    val active = enabled && !loading
    val interaction = remember { MutableInteractionSource() }
    // The default accent gets the design's gradient; other colours (e.g. red for delete) stay flat.
    val fill = when {
        !active -> SolidColor(Hairline)
        containerColor == Accent -> Brush.verticalGradient(listOf(AccentBright, Accent))
        else -> SolidColor(containerColor)
    }
    Row(
        modifier = modifier
            .height(52.dp)
            .pressScale(interaction)
            .then(if (active && containerColor == Accent) Modifier.accentShadow() else Modifier)
            .clip(PillShape)
            .background(fill)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = active, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xl),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        val contentColor = if (active) SurfaceWhite else InkTertiary
        if (loading) {
            CircularProgressIndicator(color = InkSecondary, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, tint = contentColor, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(Space.s))
            }
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontSize = 15.sp), color = contentColor, maxLines = 1)
        }
    }
}

/** Secondary action: white pill with a quiet border and ink text, same size as the primary. */
@Composable
fun PwSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color = Ink
) {
    val interaction = remember { MutableInteractionSource() }
    val color = if (enabled) contentColor else InkTertiary
    Row(
        modifier = modifier
            .height(52.dp)
            .pressScale(interaction)
            .clip(PillShape)
            .background(SurfaceWhite)
            .border(1.dp, Outline, PillShape)
            .clickable(interactionSource = interaction, indication = LocalIndication.current, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = Space.xl),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(Space.s))
        }
        Text(text, style = MaterialTheme.typography.labelLarge, color = color, maxLines = 1)
    }
}

/** Filled text field: grey, no underline or outline, label above. */
@Composable
fun PwTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    placeholder: String? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    errorText: String? = null,
    singleLine: Boolean = true,
    minLines: Int = 1,
    maxLines: Int = if (singleLine) 1 else 4,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    containerColor: Color = SurfaceMuted,
    /** Runs on every keystroke and paste (see InputGuard) so the field can only hold what it should. */
    filter: (String) -> String = { it }
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = InkSecondary)
            Spacer(modifier = Modifier.height(6.dp))
        }
        TextField(
            value = value,
            onValueChange = { onValueChange(filter(it)) },
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
            // Written as if/else so the lambda is typed as @Composable from the parameter.
            placeholder = if (placeholder != null) {
                { Text(placeholder, style = MaterialTheme.typography.bodyLarge, color = InkTertiary) }
            } else {
                null
            },
            leadingIcon = leadingIcon,
            trailingIcon = trailingIcon,
            isError = errorText != null,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = maxLines,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            shape = FieldShape,
            colors = TextFieldDefaults.colors(
                focusedContainerColor = containerColor,
                unfocusedContainerColor = containerColor,
                errorContainerColor = containerColor,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                errorIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
                cursorColor = Accent
            )
        )
        if (errorText != null) {
            Spacer(modifier = Modifier.height(4.dp))
            Text(errorText, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.error)
        }
    }
}

/** Icon in a softly tinted circle, used at the start of list rows. */
@Composable
fun PwIconBadge(icon: ImageVector, tint: Color, modifier: Modifier = Modifier, size: Dp = 40.dp) {
    Box(
        modifier = modifier
            .size(size)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.12f)),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(size * 0.5f))
    }
}

/** Standard list row: leading badge, title + subtitle, trailing content. */
@Composable
fun PwListRow(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    leading: (@Composable () -> Unit)? = null,
    onClick: (() -> Unit)? = null,
    titleColor: Color = Ink,
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 14.dp, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(Space.m))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = titleColor, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) {
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = InkSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (trailing != null) {
            Spacer(modifier = Modifier.width(Space.s))
            trailing()
        }
    }
}

/** Hairline divider, inset to line up with list row text by default. */
@Composable
fun PwDivider(modifier: Modifier = Modifier, startInset: Dp = 0.dp) {
    HorizontalDivider(modifier = modifier.padding(start = startInset), thickness = 1.dp, color = TrackSoft)
}

/** Round white icon button for page headers (settings, back). */
@Composable
fun PwHeaderIconButton(icon: ImageVector, contentDescription: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(44.dp)
            .softShadow(CircleShape, 3.dp)
            .clip(CircleShape)
            .background(SurfaceWhite)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = Ink, modifier = Modifier.size(22.dp))
    }
}
