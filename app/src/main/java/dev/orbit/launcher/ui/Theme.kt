package dev.orbit.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.LocalContentColor
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.darkColorScheme

object OrbitColors {
    val Bg1 = Color(0xFF0E1428)
    val Bg2 = Color(0xFF05070D)
    val Accent = Color(0xFF6C8CFF)
    val Panel = Color(0xFF161C2E)
    val PanelHi = Color(0xFF222A42)
    val Focus = Color(0xFFEDEFF7)
    val OnFocus = Color(0xFF0B1020)
    val Text = Color(0xFFEDEFF7)
    val TextDim = Color(0xFF8E97AD)
    val Ok = Color(0xFF4CD58A)
    val Error = Color(0xFFFF6B6B)
}

val BackgroundBrush = Brush.verticalGradient(listOf(OrbitColors.Bg1, OrbitColors.Bg2))

@Composable
fun OrbitTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            primary = OrbitColors.Accent,
            onPrimary = OrbitColors.OnFocus,
            background = OrbitColors.Bg2,
            onBackground = OrbitColors.Text,
            surface = OrbitColors.Panel,
            onSurface = OrbitColors.Text,
            surfaceVariant = OrbitColors.PanelHi,
            onSurfaceVariant = OrbitColors.TextDim,
            border = OrbitColors.Focus,
        ),
        content = content,
    )
}

@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        modifier = modifier,
        color = OrbitColors.TextDim,
        fontSize = 15.sp,
        fontWeight = FontWeight.Medium,
        letterSpacing = 0.5.sp,
    )
}

/** Кнопка под D-pad: тёмная, в фокусе — светлая с лёгким увеличением */
@Composable
fun OrbitButton(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    subtitle: String? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Surface(
        onClick = onClick,
        onLongClick = onLongClick,
        enabled = enabled,
        modifier = modifier,
        shape = ClickableSurfaceDefaults.shape(shape = shape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.04f),
        border = ClickableSurfaceDefaults.border(
            border = if (selected) Border(BorderStroke(2.dp, OrbitColors.Accent), shape = shape) else Border.None,
        ),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) OrbitColors.PanelHi else OrbitColors.Panel,
            contentColor = if (enabled) OrbitColors.Text else OrbitColors.TextDim,
            focusedContainerColor = OrbitColors.Focus,
            focusedContentColor = OrbitColors.OnFocus,
            pressedContainerColor = OrbitColors.Accent,
            pressedContentColor = OrbitColors.OnFocus,
            disabledContainerColor = OrbitColors.Panel.copy(alpha = 0.5f),
            disabledContentColor = OrbitColors.TextDim,
        ),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = if (subtitle == null) 11.dp else 9.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (icon != null) {
                Image(
                    imageVector = icon,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    colorFilter = ColorFilter.tint(LocalContentColor.current),
                )
            }
            Column {
                Text(
                    text = text,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        text = subtitle,
                        fontSize = 12.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        color = LocalContentColor.current.copy(alpha = 0.7f),
                    )
                }
            }
        }
    }
}

/** Простой прогресс-бар; progress < 0 — «неизвестно», рисуем пульсирующий кусок */
@Composable
fun ProgressLine(progress: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(OrbitColors.PanelHi),
    ) {
        if (progress >= 0f) {
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(progress.coerceIn(0f, 1f))
                    .background(OrbitColors.Accent),
            )
        } else {
            Box(
                Modifier
                    .fillMaxHeight()
                    .width(80.dp)
                    .background(OrbitColors.Accent.copy(alpha = 0.6f)),
            )
        }
    }
}

@Composable
fun HSpacer(w: Int) = Spacer(Modifier.width(w.dp))
