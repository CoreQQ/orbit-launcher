package dev.orbit.launcher.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Create
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import dev.orbit.launcher.MainViewModel
import dev.orbit.launcher.install.ApkInstaller
import dev.orbit.launcher.install.ApkMeta
import dev.orbit.launcher.install.CatalogItem
import dev.orbit.launcher.install.CatalogState
import dev.orbit.launcher.install.FileEntry
import dev.orbit.launcher.install.Storage
import dev.orbit.launcher.install.StorageRoot
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File

private enum class InstallTab(val title: String) { Store("Витрина"), Usb("USB / память"), Url("По ссылке") }

@Composable
fun InstallScreen(vm: MainViewModel) {
    val context = LocalContext.current
    val install by vm.install.collectAsStateWithLifecycle()
    var tab by remember { mutableStateOf(InstallTab.Store) }
    var askUnknownSources by remember { mutableStateOf<(() -> Unit)?>(null) }
    val firstTab = remember { FocusRequester() }

    /** Любая установка проходит через проверку «неизвестных источников» */
    val guard: (() -> Unit) -> Unit = { action ->
        if (vm.canInstall()) action() else askUnknownSources = action
    }

    LaunchedEffect(Unit) {
        delay(60)
        runCatching { firstTab.requestFocus() }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(BackgroundBrush),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 48.dp, end = 48.dp, top = 32.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Установка приложений", fontSize = 30.sp, fontWeight = FontWeight.Light, color = OrbitColors.Text)
                Spacer(Modifier.weight(1f))
                OrbitButton("На главный", icon = Icons.Filled.Home, onClick = vm::goHome)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                InstallTab.entries.forEachIndexed { i, t ->
                    OrbitButton(
                        text = t.title,
                        selected = tab == t,
                        icon = when (t) {
                            InstallTab.Store -> Icons.Filled.ShoppingCart
                            InstallTab.Usb -> Icons.Filled.Search
                            InstallTab.Url -> Icons.Filled.Share
                        },
                        modifier = (if (i == 0) Modifier.focusRequester(firstTab) else Modifier)
                            .onFocusChanged { if (it.isFocused || it.hasFocus) tab = t },
                        onClick = { tab = t },
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth()) {
                when (tab) {
                    InstallTab.Store -> StoreTab(vm, guard)
                    InstallTab.Usb -> UsbTab(vm, guard)
                    InstallTab.Url -> UrlTab(vm, guard)
                }
            }
        }

        InstallStatusBar(
            state = install,
            onDismiss = vm::dismissInstall,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 20.dp),
        )
    }

    askUnknownSources?.let { pending ->
        if (vm.canInstall()) {
            // пользователь уже разрешил и вернулся — продолжаем
            LaunchedEffect(pending) {
                askUnknownSources = null
                pending()
            }
        } else {
            ConfirmDialog(
                title = "Нужно разрешение",
                message = "Разрешите лаунчеру устанавливать приложения: " +
                    "Настройки → Безопасность → Неизвестные источники → Orbit. " +
                    "Затем вернитесь сюда и нажмите «Установить» ещё раз.",
                confirmText = "Открыть настройки",
                onConfirm = {
                    askUnknownSources = null
                    val ok = ApkInstaller.unknownSourcesIntents(context).any { vm.repo.start(it) }
                    if (!ok) vm.toast("Откройте настройки безопасности вручную")
                },
                onDismiss = { askUnknownSources = null },
            )
        }
    }
}

// ---------------- Витрина ----------------

@Composable
private fun StoreTab(vm: MainViewModel, guard: (() -> Unit) -> Unit) {
    val catalog by vm.catalog.collectAsStateWithLifecycle()
    val apps by vm.apps.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.ensureCatalog() }

    when (val c = catalog) {
        CatalogState.Loading -> Text("Загрузка витрины…", color = OrbitColors.TextDim)
        is CatalogState.Ready -> LazyColumn(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Источник: ${c.source}", fontSize = 13.sp, color = OrbitColors.TextDim)
                        c.error?.let { Text(it, fontSize = 12.sp, color = OrbitColors.Error) }
                    }
                    OrbitButton("Обновить", icon = Icons.Filled.Refresh, onClick = vm::loadCatalog)
                }
            }
            items(c.items, key = { it.url }) { item ->
                // apps в ключе — пересчёт статуса после установки/удаления
                val installed = remember(item.pkg, apps) { vm.repo.installedVersion(item.pkg) }
                CatalogRow(item, installed) { guard { vm.installUrl(item.url, item.name) } }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }
}

@Composable
private fun CatalogRow(item: CatalogItem, installedVersion: String?, onInstall: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(OrbitColors.Panel.copy(alpha = 0.6f), RoundedCornerShape(14.dp))
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(item.name, fontSize = 18.sp, fontWeight = FontWeight.Medium, color = OrbitColors.Text)
            if (item.description.isNotBlank()) {
                Text(item.description, fontSize = 13.sp, color = OrbitColors.TextDim, maxLines = 2)
            }
            if (installedVersion != null) {
                Text("Установлено · v$installedVersion", fontSize = 12.sp, color = OrbitColors.Ok)
            }
        }
        OrbitButton(
            text = if (installedVersion != null) "Обновить" else "Установить",
            icon = if (installedVersion != null) Icons.Filled.Refresh else Icons.Filled.Add,
            onClick = onInstall,
        )
    }
}

// ---------------- USB / память ----------------

@Composable
private fun UsbTab(vm: MainViewModel, guard: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    fun granted() = Build.VERSION.SDK_INT >= 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_EXTERNAL_STORAGE) ==
        PackageManager.PERMISSION_GRANTED

    var hasPerm by remember { mutableStateOf(granted()) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { hasPerm = it }

    if (!hasPerm) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Нужен доступ к накопителям, чтобы видеть APK на флешке.", color = OrbitColors.TextDim)
            OrbitButton("Разрешить доступ", icon = Icons.Filled.CheckCircle) {
                permLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }
        return
    }

    var rootsVersion by remember { mutableStateOf(0) }
    val roots by produceState(emptyList<StorageRoot>(), rootsVersion) {
        value = withContext(Dispatchers.IO) { Storage.roots(context) }
    }
    var root by remember { mutableStateOf<StorageRoot?>(null) }
    var dir by remember { mutableStateOf<File?>(null) }
    /** true — показываем «все APK на носителе» вместо папок */
    var scanAll by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<File?>(null) }
    /** После перехода по папкам ставим фокус на верхний пункт списка */
    var navTick by remember { mutableStateOf(0) }
    val topItem = remember { FocusRequester() }
    LaunchedEffect(navTick) {
        if (navTick > 0) {
            delay(120)
            runCatching { topItem.requestFocus() }
        }
    }

    val entries by produceState<List<FileEntry>?>(null, dir, scanAll) {
        value = null
        val d = dir ?: return@produceState
        value = withContext(Dispatchers.IO) { if (scanAll) Storage.findApks(d) else Storage.list(d) }
    }

    fun goUp() {
        val d = dir ?: return
        val r = root
        if (scanAll || r == null || d.absolutePath == r.dir.absolutePath) {
            dir = null
            root = null
            scanAll = false
        } else {
            dir = d.parentFile
        }
        navTick++
    }
    BackHandler(enabled = dir != null) { goUp() }

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
    ) {
        if (dir == null) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "Вставьте флешку и выберите носитель",
                        fontSize = 13.sp,
                        color = OrbitColors.TextDim,
                        modifier = Modifier.weight(1f),
                    )
                    OrbitButton(
                        "Обновить",
                        icon = Icons.Filled.Refresh,
                        modifier = Modifier.focusRequester(topItem),
                    ) { rootsVersion++ }
                }
            }
            items(roots, key = { it.dir.absolutePath }) { r ->
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OrbitButton(
                        text = r.title,
                        subtitle = r.dir.absolutePath,
                        icon = if (r.removable) Icons.Filled.Build else Icons.Filled.Home,
                        modifier = Modifier.weight(1f),
                    ) {
                        root = r
                        scanAll = false
                        dir = r.dir
                        navTick++
                    }
                    OrbitButton("Все APK", icon = Icons.Filled.Search) {
                        root = r
                        scanAll = true
                        dir = r.dir
                        navTick++
                    }
                }
            }
        } else {
            item {
                OrbitButton(
                    text = "Назад",
                    subtitle = if (scanAll) "Все APK на ${root?.title}" else dir?.absolutePath,
                    icon = Icons.Filled.ArrowBack,
                    modifier = Modifier.fillMaxWidth().focusRequester(topItem),
                    onClick = ::goUp,
                )
            }
            val list = entries
            when {
                list == null -> item { Text("Чтение…", color = OrbitColors.TextDim) }
                list.isEmpty() -> item { Text("APK не найдено", color = OrbitColors.TextDim) }
                else -> items(list, key = { it.file.absolutePath }) { e ->
                    OrbitButton(
                        text = if (e.isDir) "📁  ${e.file.name}" else e.file.name,
                        subtitle = when {
                            e.isDir -> null
                            scanAll -> "${Storage.humanSize(e.size)} · ${e.file.parent}"
                            else -> Storage.humanSize(e.size)
                        },
                        icon = if (e.isDir) null else Icons.Filled.Add,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        if (e.isDir) {
                            dir = e.file
                            navTick++
                        } else {
                            confirm = e.file
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(80.dp)) }
        }
    }

    confirm?.let { file ->
        ApkConfirmDialog(
            vm = vm,
            file = file,
            onDismiss = { confirm = null },
            onInstall = {
                confirm = null
                guard { vm.installFile(file) }
            },
        )
    }
}

@Composable
private fun ApkConfirmDialog(vm: MainViewModel, file: File, onDismiss: () -> Unit, onInstall: () -> Unit) {
    val context = LocalContext.current
    // Pair(meta, загружено ли)
    val state by produceState<Pair<ApkMeta?, Boolean>>(null to false, file) {
        value = withContext(Dispatchers.IO) { ApkInstaller.inspect(context, file) } to true
    }
    val (meta, loaded) = state
    val installed = remember(meta) { vm.repo.installedVersion(meta?.pkg) }

    OrbitDialog(onDismiss = onDismiss, width = 520) { first ->
        Text(meta?.label ?: file.name, fontSize = 22.sp, fontWeight = FontWeight.SemiBold, color = OrbitColors.Text)
        when {
            !loaded -> Text("Читаю APK…", color = OrbitColors.TextDim)
            meta == null -> Text("Файл повреждён или это не APK", color = OrbitColors.Error)
            else -> {
                Text(meta.pkg, fontSize = 13.sp, color = OrbitColors.TextDim)
                Text(
                    "Версия: ${meta.versionName ?: "?"} (${meta.versionCode}) · ${Storage.humanSize(meta.sizeBytes)}",
                    fontSize = 14.sp,
                    color = OrbitColors.Text,
                )
                if (installed != null) {
                    Text("Сейчас установлено: v$installed — будет обновлено", fontSize = 13.sp, color = OrbitColors.Ok)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OrbitButton(
                text = if (installed != null) "Обновить" else "Установить",
                icon = Icons.Filled.Add,
                enabled = meta != null,
                modifier = Modifier.focusRequester(first),
                onClick = onInstall,
            )
            OrbitButton("Отмена", onClick = onDismiss)
        }
    }
}

// ---------------- По ссылке ----------------

@Composable
private fun UrlTab(vm: MainViewModel, guard: (() -> Unit) -> Unit) {
    val context = LocalContext.current
    val recent by vm.recentUrls.collectAsStateWithLifecycle()

    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(10.dp),
        contentPadding = PaddingValues(vertical = 8.dp, horizontal = 4.dp),
    ) {
        item {
            OrbitButton(
                text = "Ввести ссылку на APK…",
                subtitle = "Работают и короткие ссылки, например kutt.to/stn_stable",
                icon = Icons.Filled.Create,
                modifier = Modifier.fillMaxWidth(),
            ) {
                promptText(context, "Ссылка на APK", "https://", hint = "https://…/app.apk") { url ->
                    if (url.isNotBlank() && url != "https://") guard { vm.installUrl(url) }
                }
            }
        }
        if (recent.isNotEmpty()) {
            item { SectionTitle("НЕДАВНИЕ", Modifier.padding(top = 10.dp, start = 4.dp)) }
            items(recent, key = { it }) { url ->
                OrbitButton(
                    text = url.substringBefore('?').substringAfterLast('/').ifBlank { url },
                    subtitle = url,
                    icon = Icons.Filled.Refresh,
                    modifier = Modifier.fillMaxWidth(),
                ) { guard { vm.installUrl(url) } }
            }
        }
        item { Spacer(Modifier.height(80.dp)) }
    }
}
