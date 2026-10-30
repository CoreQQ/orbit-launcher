package dev.orbit.launcher.ui

import android.text.format.DateFormat
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Share
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Text
import dev.orbit.launcher.MainViewModel
import dev.orbit.launcher.Screen
import dev.orbit.launcher.data.AppInfo
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(vm: MainViewModel) {
    val apps by vm.apps.collectAsStateWithLifecycle()
    val favs by vm.favorites.collectAsStateWithLifecycle()
    val hidden by vm.hidden.collectAsStateWithLifecycle()
    val columns by vm.columns.collectAsStateWithLifecycle()
    val moving by vm.moving.collectAsStateWithLifecycle()
    val install by vm.install.collectAsStateWithLifecycle()

    val visible = remember(apps, hidden) { apps.filter { it.pkg !in hidden } }
    val favApps = remember(visible, favs) {
        val byPkg = visible.associateBy { it.pkg }
        favs.mapNotNull { byPkg[it] }
    }
    val rows = remember(visible, columns) { visible.chunked(columns) }

    var menuFor by remember { mutableStateOf<AppInfo?>(null) }
    val firstFocus = remember { FocusRequester() }
    var initialFocusDone by rememberSaveable { mutableStateOf(false) }
    val favRowState = rememberLazyListState()

    // Начальный фокус — на первое избранное или первое приложение
    LaunchedEffect(visible.isNotEmpty()) {
        if (visible.isNotEmpty() && !initialFocusDone) {
            delay(80)
            runCatching { firstFocus.requestFocus() }
            initialFocusDone = true
        }
    }

    // В режиме перестановки держим двигаемую карточку в зоне видимости
    LaunchedEffect(favApps, moving) {
        val pkg = moving ?: return@LaunchedEffect
        val idx = favApps.indexOfFirst { it.pkg == pkg }
        if (idx < 0) return@LaunchedEffect
        val visibleIdx = favRowState.layoutInfo.visibleItemsInfo.map { it.index }
        if (visibleIdx.isEmpty() || idx <= visibleIdx.first() || idx >= visibleIdx.last()) {
            favRowState.animateScrollToItem((idx - 1).coerceAtLeast(0))
        }
    }

    BackHandler(enabled = moving != null) { vm.stopMoving() }

    Box(
        Modifier
            .fillMaxSize()
            .background(BackgroundBrush),
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 40.dp, end = 40.dp, top = 28.dp, bottom = 64.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "header") {
                Header(
                    onAirPlay = vm::openAirPlay,
                    onInstall = { vm.go(Screen.Install) },
                    onSettings = { vm.go(Screen.Settings) },
                )
            }

            item(key = "fav_title") {
                SectionTitle(
                    text = if (moving != null) "ИЗБРАННОЕ · ◀ ▶ двигать, OK — готово" else "ИЗБРАННОЕ",
                    modifier = Modifier.padding(start = 8.dp, top = 10.dp),
                )
            }

            item(key = "fav_row") {
                if (favApps.isEmpty()) {
                    Text(
                        text = "Зажмите OK на любом приложении → «В избранное»",
                        color = OrbitColors.TextDim,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 8.dp, top = 4.dp, bottom = 8.dp),
                    )
                } else {
                    LazyRow(
                        state = favRowState,
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    ) {
                        itemsIndexed(favApps, key = { _, a -> a.pkg }) { i, app ->
                            val isMoving = moving == app.pkg
                            AppCard(
                                app = app,
                                icons = vm.icons,
                                modifier = Modifier.width(184.dp),
                                cardModifier = if (i == 0) Modifier.focusRequester(firstFocus) else Modifier,
                                highlighted = isMoving,
                                onPreviewKey = if (!isMoving) null else { ev ->
                                    if (ev.type != KeyEventType.KeyDown) {
                                        false
                                    } else {
                                        when (ev.key) {
                                            Key.DirectionLeft -> { vm.moveFavorite(app.pkg, -1); true }
                                            Key.DirectionRight -> { vm.moveFavorite(app.pkg, +1); true }
                                            Key.DirectionUp, Key.DirectionDown -> true
                                            else -> false
                                        }
                                    }
                                },
                                onLongClick = { if (moving == null) menuFor = app },
                                onClick = { if (moving != null) vm.stopMoving() else vm.launch(app) },
                            )
                        }
                    }
                }
            }

            item(key = "apps_title") {
                SectionTitle(
                    text = "ВСЕ ПРИЛОЖЕНИЯ · ${visible.size}",
                    modifier = Modifier.padding(start = 8.dp, top = 6.dp),
                )
            }

            itemsIndexed(rows, key = { i, _ -> "row_$i" }) { ri, row ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(18.dp),
                ) {
                    row.forEachIndexed { ci, app ->
                        AppCard(
                            app = app,
                            icons = vm.icons,
                            modifier = Modifier.weight(1f),
                            cardModifier = if (favApps.isEmpty() && ri == 0 && ci == 0) {
                                Modifier.focusRequester(firstFocus)
                            } else {
                                Modifier
                            },
                            onLongClick = { if (moving == null) menuFor = app },
                            onClick = { if (moving == null) vm.launch(app) },
                        )
                    }
                    repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
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

    menuFor?.let { app ->
        AppMenuDialog(
            app = app,
            isFavorite = app.pkg in favs,
            onDismiss = { menuFor = null },
            onOpen = { menuFor = null; vm.launch(app) },
            onToggleFavorite = { menuFor = null; vm.toggleFavorite(app.pkg) },
            onMove = { menuFor = null; vm.startMoving(app.pkg) },
            onHide = { menuFor = null; vm.setHidden(app.pkg, true) },
            onDetails = { menuFor = null; vm.openDetails(app) },
            onUninstall = { menuFor = null; vm.uninstall(app) },
        )
    }
}

@Composable
private fun Header(onAirPlay: () -> Unit, onInstall: () -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val now by produceState(System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(60_000L - value % 60_000L + 50)
        }
    }
    val is24 = remember { DateFormat.is24HourFormat(context) }
    val timeFmt = remember(is24) { SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()) }
    val dateFmt = remember { SimpleDateFormat("EEEE, d MMMM", Locale.getDefault()) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column {
            Text(
                text = timeFmt.format(Date(now)),
                fontSize = 46.sp,
                fontWeight = FontWeight.Light,
                color = OrbitColors.Text,
            )
            Text(
                text = dateFmt.format(Date(now)).replaceFirstChar { it.titlecase(Locale.getDefault()) },
                fontSize = 15.sp,
                color = OrbitColors.TextDim,
            )
        }
        Spacer(Modifier.weight(1f))
        OrbitButton(text = "AirPlay", icon = Icons.Filled.Share, onClick = onAirPlay)
        HSpacer(14)
        OrbitButton(text = "Установить APK", icon = Icons.Filled.Add, onClick = onInstall)
        HSpacer(14)
        OrbitButton(text = "Настройки", icon = Icons.Filled.Settings, onClick = onSettings)
    }
}
