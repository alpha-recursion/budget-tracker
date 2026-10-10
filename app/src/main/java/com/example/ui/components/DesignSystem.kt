package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.ui.theme.Accent
import com.example.ui.theme.Hairline
import com.example.ui.theme.Ink
import com.example.ui.theme.InkSecondary
import com.example.ui.theme.InkTertiary
import com.example.ui.theme.SurfaceMuted
import com.example.ui.theme.SurfaceWhite

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
    val screen = 20.dp

    /** Gap between cards on a screen. */
    val section = 16.dp
}

val CardShape = RoundedCornerShape(24.dp)
val FieldShape = RoundedCornerShape(14.dp)
val ButtonShape = RoundedCornerShape(16.dp)

/**
 * Soft, downward shadow tinted toward ink rather than pure black, so cards lift off the grey
 * canvas without a dark smear. (Colour is honoured on Android 9+; older versions use grey.)
 */
fun Modifier.softShadow(shape: Shape = CardShape, elevation: Dp = 6.dp): Modifier =
    shadow(elevation = elevation, shape = shape, ambientColor = Ink.copy(alpha = 0.05f), spotColor = Ink.copy(alpha = 0.10f))

/** White rounded card on the grey canvas. */
@Composable
fun PwCard(
    modifier: Modifier = Modifier,
    color: Color = SurfaceWhite,
    contentPadding: PaddingValues = PaddingValues(Space.xl),
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

/** Card title with an optional action on the right ("See all"). */
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
                Text(subtitle, style = MaterialTheme.typography.labelMedium, color = InkSecondary)
            }
        }
        if (action != null && onAction != null) {
            Text(
                action,
                style = MaterialTheme.typography.labelLarge,
                color = Accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onAction)
                    .padding(horizontal = Space.s, vertical = Space.xs)
            )
        }
    }
}

/** Pill chip: selected is dark ink with white text (reference style), unselected is quiet grey. */
@Composable
fun PwChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    unselectedColor: Color = SurfaceMuted,
    leading: (@Composable () -> Unit)? = null
) {
    val background by animateColorAsState(if (selected) Ink else unselectedColor, label = "chip_bg")
    val content by animateColorAsState(if (selected) SurfaceWhite else Ink, label = "chip_fg")
    Row(
        modifier = modifier
            .defaultMinSize(minHeight = 36.dp)
            .clip(CircleShape)
            .background(background)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = Space.s),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        leading?.invoke()
        Text(text, style = MaterialTheme.typography.labelLarge, color = content, maxLines = 1)
    }
}

/** Two or more mutually exclusive options in a grey track; the selected one is a white pill. */
@Composable
fun PwSegmented(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    selectedColors: List<Color> = emptyList()
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(SurfaceMuted)
            .padding(4.dp)
    ) {
        options.forEachIndexed { index, label ->
            val selected = index == selectedIndex
            val accent = selectedColors.getOrNull(index) ?: Ink
            Box(
                modifier = Modifier
                    .weight(1f)
                    .then(if (selected) Modifier.softShadow(RoundedCornerShape(12.dp), 2.dp) else Modifier)
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (selected) SurfaceWhite else Color.Transparent)
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    label,
                    style = MaterialTheme.typography.labelLarge.copy(
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (selected) accent else InkSecondary
                )
            }
        }
    }
}

/** Main call to action: blue, 52dp tall, full width by default. */
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
    Button(
        onClick = onClick,
        enabled = enabled && !loading,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = containerColor,
            contentColor = SurfaceWhite,
            disabledContainerColor = Hairline,
            disabledContentColor = InkTertiary
        ),
        contentPadding = PaddingValues(horizontal = Space.xl),
        modifier = modifier.height(52.dp)
    ) {
        if (loading) {
            CircularProgressIndicator(color = SurfaceWhite, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            if (icon != null) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(Space.s))
            }
            Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold))
        }
    }
}

/** Secondary action: quiet grey with ink text, same size as the primary button. */
@Composable
fun PwSecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: ImageVector? = null,
    contentColor: Color = Ink
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = ButtonShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = SurfaceMuted,
            contentColor = contentColor,
            disabledContainerColor = SurfaceMuted,
            disabledContentColor = InkTertiary
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = Space.xl),
        modifier = modifier.height(52.dp)
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(20.dp))
            Spacer(modifier = Modifier.width(Space.s))
        }
        Text(text, style = MaterialTheme.typography.labelLarge)
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
    containerColor: Color = SurfaceMuted
) {
    Column(modifier = modifier) {
        if (label != null) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = InkSecondary)
            Spacer(modifier = Modifier.height(6.dp))
        }
        TextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            textStyle = MaterialTheme.typography.bodyLarge.copy(color = Ink),
            placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyLarge, color = InkTertiary) } },
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
    trailing: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = Space.l, vertical = Space.m),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (leading != null) {
            leading()
            Spacer(modifier = Modifier.width(Space.m))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = Ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
fun PwDivider(modifier: Modifier = Modifier, startInset: Dp = 68.dp) {
    HorizontalDivider(modifier = modifier.padding(start = startInset), thickness = 1.dp, color = Hairline)
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
