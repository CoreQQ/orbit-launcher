package dev.orbit.launcher

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.orbit.launcher.ui.HomeScreen
import dev.orbit.launcher.ui.InstallScreen
import dev.orbit.launcher.ui.OrbitTheme
import dev.orbit.launcher.ui.SettingsScreen

class MainActivity : ComponentActivity() {

    private val vm: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OrbitTheme { Root(vm) }
        }
    }

    override fun onResume() {
        super.onResume()
        // Подхватываем изменения, пока были в других приложениях / настройках
        vm.refresh()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Кнопка Home на пульте, когда лаунчер уже открыт → на главный экран
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.goHome()
    }
}

@Composable
private fun Root(vm: MainViewModel) {
    val screen by vm.screen.collectAsStateWithLifecycle()

    // Регистрируем раньше экранов → у их BackHandler приоритет выше
    BackHandler(enabled = screen != Screen.Home) { vm.goHome() }
    // На главном «Назад» ничего не делает — лаунчер не закрывается
    BackHandler(enabled = screen == Screen.Home) { }

    when (screen) {
        Screen.Home -> HomeScreen(vm)
        Screen.Install -> InstallScreen(vm)
        Screen.Settings -> SettingsScreen(vm)
    }
}
