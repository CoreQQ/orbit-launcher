package dev.orbit.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import dev.orbit.launcher.MainViewModel
import dev.orbit.launcher.keys.ActionType
import dev.orbit.launcher.keys.ButtonStore
import dev.orbit.launcher.keys.KeyAction
import dev.orbit.launcher.keys.KeyBinding

/** Раздел «Кнопки пульта» внутри LazyColumn настроек */
fun LazyListScope.buttonsSection(
    vm: MainViewModel,
    bindings: List<KeyBinding>,
    serviceOn: Boolean,
    openAccessibility: () -> Unit,
) {
    item { SectionTitle("КНОПКИ ПУЛЬТА", Modifier.padding(start = 4.dp, top = 12.dp)) }
    item {
        OrbitButton(
            text = "Служба кнопок: " + if (serviceOn) "включена" else "выключена",
            subtitle = if (serviceOn) {
                "Назначения работают в любом приложении"
            } else {
                "Спец. возможности → «Orbit — кнопки пульта» → Вкл (или командой ADB из README)"
            },
            icon = if (serviceOn) Icons.Filled.CheckCircle else Icons.Filled.Info,
            modifier = Modifier.fillMaxWidth(),
            onClick = openAccessibility,
        )
    }
    items(bindings, key = { it.id }) { b ->
        OrbitButton(
            text = "Кнопка ${b.keyName}",
            subtitle = "Коротко: ${b.short?.title ?: "—"}   ·   Долго: ${b.long?.title ?: "—"}",
            icon = Icons.Filled.PlayArrow,
            modifier = Modifier.fillMaxWidth(),
        ) { vm.editBinding(b) }
    }
    item {
        OrbitButton(
            text = "Назначить кнопку…",
            subtitle = "Нажмите и затем нажмите нужную кнопку на пульте",
            icon = Icons.Filled.Add,
            modifier = Modifier.fillMaxWidth(),
            onClick = vm::startLearning,
        )
    }
}

fun openAccessibilitySettings(vm: MainViewModel) {
    val ok = vm.repo.start(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) ||
        vm.repo.start(Intent(Settings.ACTION_SETTINGS))
    if (!ok) vm.toast("Включите через ADB: ${ButtonStore.ADB_ENABLE}")
}

/** Полноэкранная подсказка «нажмите кнопку» — в том же окне, чтобы Activity видела нажатия */
@Composable
fun LearnOverlay(vm: MainViewModel) {
    val learning by vm.learning.collectAsStateWithLifecycle()
    if (!learning) return
    Box(
        Modifier
            .fillMaxSize()
            .background(OrbitColors.Bg2.copy(alpha = 0.92f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier
                .width(560.dp)
                .background(OrbitColors.Panel, RoundedCornerShape(18.dp))
                .padding(32.dp),
        ) {
            Text("Нажмите кнопку на пульте", fontSize = 26.sp, fontWeight = FontWeight.Light, color = OrbitColors.Text)
            Text(
                "Стрелки и OK игнорируются. «Назад» — отмена.\nЕсли за 15 секунд ничего не произойдёт — кнопка не отправляет код.",
                fontSize = 14.sp,
                color = OrbitColors.TextDim,
                textAlign = TextAlign.Center,
            )
            ProgressLine(-1f, Modifier.fillMaxWidth().padding(top = 8.dp))
        }
    }
}

private enum class Slot { Short, Long }

/** Редактор назначения + выбор действия + выбор приложения */
@Composable
fun ButtonDialogs(vm: MainViewModel) {
    val draft by vm.draft.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val d = draft ?: return
    val b = d.binding

    var picking by remember(d.binding.id) { mutableStateOf<Slot?>(null) }
    var pickingApp by remember(d.binding.id) { mutableStateOf<Slot?>(null) }

    fun set(slot: Slot, a: KeyAction?) {
        vm.updateDraft(if (slot == Slot.Short) b.copy(short = a) else b.copy(long = a))
    }

    when {
        pickingApp != null -> {
            val slot = pickingApp!!
            OrbitDialog(onDismiss = { pickingApp = null }, width = 520) { first ->
                Text("Какое приложение открыть?", fontSize = 20.sp, fontWeight = FontWeight.Medium, color = OrbitColors.Text)
                LazyColumn(
                    modifier = Modifier.heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    itemsIndexed(apps, key = { _, a -> a.pkg }) { i, app ->
                        OrbitButton(
                            text = app.label,
                            subtitle = app.pkg,
                            modifier = Modifier
                                .fillMaxWidth()
                                .let { if (i == 0) it.focusRequester(first) else it },
                        ) {
                            set(slot, KeyAction(ActionType.APP, app.pkg, app.label))
                            pickingApp = null
                            picking = null
                        }
                    }
                }
            }
        }

        picking != null -> {
            val slot = picking!!
            OrbitDialog(onDismiss = { picking = null }, width = 460) { first ->
                Text(
                    if (slot == Slot.Short) "Короткое нажатие" else "Долгое нажатие",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = OrbitColors.Text,
                )
                ActionType.entries.forEachIndexed { i, t ->
                    OrbitButton(
                        text = t.title,
                        modifier = Modifier
                            .fillMaxWidth()
                            .let { if (i == 0) it.focusRequester(first) else it },
                    ) {
                        if (t == ActionType.APP) {
                            pickingApp = slot
                        } else {
                            set(slot, KeyAction(t))
                            picking = null
                        }
                    }
                }
                OrbitButton("Ничего", modifier = Modifier.fillMaxWidth()) {
                    set(slot, null)
                    picking = null
                }
            }
        }

        else -> OrbitDialog(onDismiss = vm::closeDraft, width = 520) { first ->
            Text("Кнопка ${b.keyName}", fontSize = 22.sp, fontWeight = FontWeight.Medium, color = OrbitColors.Text)
            Text(b.keyDetails, fontSize = 12.sp, color = OrbitColors.TextDim)
            Spacer(Modifier.height(6.dp))
            OrbitButton(
                text = "Короткое: ${b.short?.title ?: "ничего"}",
                icon = Icons.Filled.PlayArrow,
                modifier = Modifier.fillMaxWidth().focusRequester(first),
            ) { picking = Slot.Short }
            OrbitButton(
                text = "Долгое: ${b.long?.title ?: "ничего"}",
                subtitle = "Удержание ~0.6 с",
                icon = Icons.Filled.Refresh,
                modifier = Modifier.fillMaxWidth(),
            ) { picking = Slot.Long }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.padding(top = 6.dp)) {
                OrbitButton("Сохранить", icon = Icons.Filled.CheckCircle, onClick = vm::saveDraft)
                if (!d.isNew) OrbitButton("Удалить", icon = Icons.Filled.Delete, onClick = vm::deleteDraft)
                OrbitButton("Отмена", onClick = vm::closeDraft)
            }
        }
    }
}
