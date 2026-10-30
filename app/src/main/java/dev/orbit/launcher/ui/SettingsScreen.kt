package dev.orbit.launcher.ui

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Star
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.currentStateAsState
import androidx.tv.material3.Text
import dev.orbit.launcher.BuildConfig
import dev.orbit.launcher.MainViewModel
import dev.orbit.launcher.install.ApkInstaller
import dev.orbit.launcher.keys.ButtonStore
import kotlinx.coroutines.delay

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val columns by vm.columns.collectAsStateWithLifecycle()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    val catalogUrl by vm.catalogUrl.collectAsStateWithLifecycle()
    val first = remember { FocusRequester() }
    // Пересчитываем при каждом возврате на экран (после системных настроек)
    val lifecycleState by LocalLifecycleOwner.current.lifecycle.currentStateAsState()
    val canInstall = remember(lifecycleState) { vm.canInstall() }
    val hiddenApps = remember(apps, hidden) { apps.filter { it.pkg in hidden } }
    val bindings by vm.bindings.collectAsStateWithLifecycle()
    val serviceOn = remember(lifecycleState) { ButtonStore.isServiceEnabled(context) }

    LaunchedEffect(Unit) {
        delay(60)
        runCatching { first.requestFocus() }
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundBrush),
        contentPadding = PaddingValues(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 48.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 8.dp)) {
                Text("Настройки", fontSize = 30.sp, fontWeight = FontWeight.Light, color = OrbitColors.Text)
                Spacer(Modifier.weight(1f))
                OrbitButton("На главный", icon = Icons.Filled.Home, onClick = vm::goHome)
            }
        }

        item { SectionTitle("ЛАУНЧЕР", Modifier.padding(start = 4.dp, top = 6.dp)) }
        item {
            OrbitButton(
                text = "Колонок в сетке: $columns",
                subtitle = "Нажмите, чтобы переключить 5 / 6 / 7",
                icon = Icons.Filled.Star,
                modifier = Modifier.fillMaxWidth().focusRequester(first),
                onClick = vm::cycleColumns,
            )
        }
        item {
            OrbitButton(
                text = "Источник витрины",
                subtitle = catalogUrl.ifBlank { "Встроенная (SmartTube). Нажмите, чтобы указать свой JSON" },
                icon = Icons.Filled.ShoppingCart,
                modifier = Modifier.fillMaxWidth(),
            ) {
                promptText(context, "URL витрины (JSON), пусто — встроенная", catalogUrl) { vm.setCatalogUrl(it) }
            }
        }
        item {
            OrbitButton(
                text = "Обновить список приложений",
                icon = Icons.Filled.Refresh,
                modifier = Modifier.fillMaxWidth(),
                onClick = vm::refresh,
            )
        }

        buttonsSection(vm, bindings, serviceOn) { openAccessibilitySettings(vm) }

        item { SectionTitle("СИСТЕМА", Modifier.padding(start = 4.dp, top = 12.dp)) }
        item {
            OrbitButton(
                text = "Установка APK: " + if (canInstall) "разрешена" else "не разрешена",
                subtitle = "Неизвестные источники для Orbit",
                icon = Icons.Filled.Lock,
                modifier = Modifier.fillMaxWidth(),
            ) {
                val ok = ApkInstaller.unknownSourcesIntents(context).any { vm.repo.start(it) }
                if (!ok) vm.toast("Экран недоступен")
            }
        }
        item {
            OrbitButton(
                text = "Системные настройки Android",
                subtitle = "Сеть, экран, звук, пульты, приложения",
                icon = Icons.Filled.Settings,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!vm.repo.start(Intent(Settings.ACTION_SETTINGS))) vm.toast("Настройки недоступны")
            }
        }
        item {
            OrbitButton(
                text = "Лаунчер по умолчанию",
                subtitle = "Если пункт недоступен — отключите стоковый лаунчер через ADB (см. README)",
                icon = Icons.Filled.Build,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (!vm.repo.start(Intent(Settings.ACTION_HOME_SETTINGS))) {
                    vm.toast("На этой приставке выбор лаунчера скрыт — используйте ADB")
                }
            }
        }

        item {
            SectionTitle(
                "СКРЫТЫЕ ПРИЛОЖЕНИЯ · ${hiddenApps.size}",
                Modifier.padding(start = 4.dp, top = 12.dp),
            )
        }
        if (hiddenApps.isEmpty()) {
            item {
                Text(
                    "Нет. Скрыть можно через долгое нажатие OK на приложении.",
                    fontSize = 13.sp,
                    color = OrbitColors.TextDim,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        } else {
            items(hiddenApps, key = { it.pkg }) { app ->
                OrbitButton(
                    text = "Показать: ${app.label}",
                    subtitle = app.pkg,
                    modifier = Modifier.fillMaxWidth(),
                ) { vm.setHidden(app.pkg, false) }
            }
        }

        item {
            Column(Modifier.padding(start = 4.dp, top = 20.dp)) {
                Text(
                    "Orbit Launcher ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})",
                    fontSize = 12.sp,
                    color = OrbitColors.TextDim,
                )
            }
        }
    }
    LearnOverlay(vm)
    }
    ButtonDialogs(vm)
}
