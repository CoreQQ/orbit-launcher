package dev.orbit.launcher.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.tv.material3.Border
import androidx.tv.material3.Card
import androidx.tv.material3.CardDefaults
import androidx.tv.material3.Text
import dev.orbit.launcher.data.AppInfo
import dev.orbit.launcher.data.AppVisual
import dev.orbit.launcher.data.IconCache

@Composable
fun AppCard(
    app: AppInfo,
    icons: IconCache,
    modifier: Modifier = Modifier,
    cardModifier: Modifier = Modifier,
    /** Подсветка режима перестановки */
    highlighted: Boolean = false,
    onPreviewKey: ((KeyEvent) -> Boolean)? = null,
    onLongClick: () -> Unit,
    onClick: () -> Unit,
) {
    val visual by produceState<AppVisual?>(initialValue = icons.peek(app.pkg), app.pkg, app.versionName) {
        value = icons.get(app)
    }
    var focused by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(10.dp)

    Column(modifier.zIndex(if (focused) 1f else 0f)) {
        Card(
            onClick = onClick,
            onLongClick = onLongClick,
            modifier = cardModifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .onFocusChanged { focused = it.hasFocus || it.isFocused }
                .let { m -> if (onPreviewKey != null) m.onPreviewKeyEvent(onPreviewKey) else m },
            shape = CardDefaults.shape(shape = shape),
            scale = CardDefaults.scale(focusedScale = 1.1f),
            colors = CardDefaults.colors(
                containerColor = visual?.tint ?: OrbitColors.Panel,
                focusedContainerColor = visual?.tint ?: OrbitColors.PanelHi,
            ),
            border = CardDefaults.border(
                border = if (highlighted) Border(BorderStroke(3.dp, OrbitColors.Accent), shape = shape) else Border.None,
                focusedBorder = Border(
                    BorderStroke(3.dp, if (highlighted) OrbitColors.Accent else OrbitColors.Focus),
                    shape = shape,
                ),
            ),
        ) {
            val v = visual
            when {
                v == null -> Box(Modifier.fillMaxSize())
                v.isBanner -> Image(
                    bitmap = v.bitmap,
                    contentDescription = app.label,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                else -> Column(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Image(
                        bitmap = v.bitmap,
                        contentDescription = app.label,
                        modifier = Modifier.size(40.dp),
                    )
                    Text(
                        text = app.label,
                        modifier = Modifier.padding(top = 6.dp),
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                        color = OrbitColors.Text,
                    )
                }
            }
        }
        Text(
            text = app.label,
            modifier = Modifier.padding(top = 8.dp, start = 2.dp),
            fontSize = 13.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = if (focused) OrbitColors.Text else OrbitColors.TextDim,
        )
    }
}
