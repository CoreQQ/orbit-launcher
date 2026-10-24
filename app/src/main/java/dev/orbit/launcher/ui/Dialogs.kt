package dev.orbit.launcher.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.ContextWrapper
import android.text.InputType
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.Text
import dev.orbit.launcher.InstallState
import dev.orbit.launcher.data.AppInfo
import kotlinx.coroutines.delay

/** Каркас модального окна под пульт: сам ставит фокус на первый пункт */
@Composable
fun OrbitDialog(
    onDismiss: () -> Unit,
    width: Int = 420,
    content: @Composable (firstFocus: FocusRequester) -> Unit,
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        val fr = remember { FocusRequester() }
        Column(
            modifier = Modifier
                .width(width.dp)
                .background(OrbitColors.Panel, RoundedCornerShape(18.dp))
                .padding(22.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content(fr)
        }
        LaunchedEffect(Unit) {
            delay(60)
            runCatching { fr.requestFocus() }
        }
    }
}

@Composable
fun AppMenuDialog(
    app: AppInfo,
    isFavorite: Boolean,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMove: () -> Unit,
    onHide: () -> Unit,
    onDetails: () -> Unit,
    onUninstall: () -> Unit,
) {
    OrbitDialog(onDismiss = onDismiss) { first ->
        Text(app.label, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = OrbitColors.Text)
        Text(
            text = app.pkg + (app.versionName?.let { "  ·  v$it" } ?: ""),
            fontSize = 12.sp,
            color = OrbitColors.TextDim,
        )
        Spacer(Modifier.height(6.dp))
        val full = Modifier.fillMaxWidth()
        OrbitButton("Открыть", full.focusRequester(first), icon = Icons.Filled.PlayArrow, onClick = onOpen)
        OrbitButton(
            if (isFavorite) "Убрать из избранного" else "В избранное",
            full,
            icon = Icons.Filled.Star,
            onClick = onToggleFavorite,
        )
        if (isFavorite) {
            OrbitButton("Переместить в избранном", full, icon = Icons.Filled.List, onClick = onMove)
        }
        OrbitButton("Скрыть с экрана", full, icon = Icons.Filled.Lock, onClick = onHide)
        OrbitButton("О приложении", full, icon = Icons.Filled.Info, onClick = onDetails)
        OrbitButton(
            "Удалить",
            full,
            icon = Icons.Filled.Delete,
            enabled = !app.isSystem,
            subtitle = if (app.isSystem) "Системное — можно только скрыть" else null,
            onClick = onUninstall,
        )
    }
}

@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    confirmText: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    OrbitDialog(onDismiss = onDismiss, width = 480) { first ->
        Text(title, fontSize = 20.sp, fontWeight = FontWeight.SemiBold, color = OrbitColors.Text)
        Text(message, fontSize = 14.sp, color = OrbitColors.TextDim)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OrbitButton(confirmText, Modifier.focusRequester(first), onClick = onConfirm)
            OrbitButton("Отмена", onClick = onDismiss)
        }
    }
}

/** Плашка статуса установки внизу экрана */
@Composable
fun InstallStatusBar(state: InstallState, onDismiss: () -> Unit, modifier: Modifier = Modifier) {
    if (state is InstallState.Idle) return
    val (title, detail, color) = when (state) {
        is InstallState.Downloading -> Triple(
            "Загрузка: ${state.name}",
            if (state.progress >= 0) "${(state.progress * 100).toInt()}%" else "…",
            OrbitColors.Text,
        )
        is InstallState.Installing -> Triple("Установка: ${state.name}", "Подтвердите в системном окне, если появится", OrbitColors.Text)
        is InstallState.Done -> Triple("Готово: ${state.name}", "Приложение установлено", OrbitColors.Ok)
        is InstallState.Failed -> Triple("Не удалось: ${state.name}", state.message, OrbitColors.Error)
        InstallState.Idle -> return
    }
    Row(
        modifier = modifier
            .width(640.dp)
            .background(OrbitColors.PanelHi, RoundedCornerShape(14.dp))
            .padding(horizontal = 18.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, fontSize = 15.sp, fontWeight = FontWeight.Medium, color = color, maxLines = 1)
            Text(detail, fontSize = 12.sp, color = OrbitColors.TextDim, maxLines = 3)
            if (state is InstallState.Downloading) ProgressLine(state.progress, Modifier.fillMaxWidth())
            if (state is InstallState.Installing) ProgressLine(-1f, Modifier.fillMaxWidth())
        }
        OrbitButton(
            text = if (state is InstallState.Downloading) "Отмена" else "Скрыть",
            icon = Icons.Filled.Close,
            onClick = onDismiss,
        )
    }
}

// ---------- ввод текста: нативный EditText, т.к. он стабильно дружит с TV-клавиатурой ----------

fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

fun promptText(
    context: Context,
    title: String,
    initial: String,
    hint: String = "",
    onResult: (String) -> Unit,
) {
    val activity = context.findActivity() ?: return
    val input = EditText(activity).apply {
        setText(initial)
        setSelection(text.length)
        this.hint = hint
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI
        imeOptions = EditorInfo.IME_ACTION_DONE
    }
    val container = FrameLayout(activity).apply {
        val pad = (20 * resources.displayMetrics.density).toInt()
        setPadding(pad, pad / 2, pad, 0)
        addView(input)
    }
    val dialog = AlertDialog.Builder(activity, android.R.style.Theme_DeviceDefault_Dialog_Alert)
        .setTitle(title)
        .setView(container)
        .setPositiveButton("OK") { _, _ -> onResult(input.text.toString().trim()) }
        .setNegativeButton("Отмена", null)
        .create()
    input.setOnEditorActionListener { _, actionId, _ ->
        if (actionId == EditorInfo.IME_ACTION_DONE) {
            onResult(input.text.toString().trim())
            dialog.dismiss()
            true
        } else {
            false
        }
    }
    dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE)
    dialog.show()
    input.requestFocus()
}
