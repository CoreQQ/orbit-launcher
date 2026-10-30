package dev.orbit.launcher

import android.content.Intent
import android.os.Bundle
import android.view.KeyEvent
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

    /** Отпускание кнопки, которую поймали в режиме «назначить», не пускаем в UI */
    private var swallowUpKey = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            OrbitTheme { Root(vm) }
        }
        if (savedInstanceState == null) handleScreenExtra(intent)
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
        handleScreenExtra(intent)
    }

    private fun handleScreenExtra(intent: Intent?) {
        when (intent?.getStringExtra(EXTRA_SCREEN)) {
            SCREEN_HOME -> vm.goHome()
            SCREEN_INSTALL -> vm.go(Screen.Install)
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (vm.isLearning) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                when (event.keyCode) {
                    KeyEvent.KEYCODE_BACK -> {
                        vm.stopLearning()
                        swallowUpKey = event.keyCode
                    }
                    // Стрелки и OK — скорее случайность, ждём нужную кнопку дальше
                    KeyEvent.KEYCODE_DPAD_UP, KeyEvent.KEYCODE_DPAD_DOWN,
                    KeyEvent.KEYCODE_DPAD_LEFT, KeyEvent.KEYCODE_DPAD_RIGHT,
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER,
                    -> Unit
                    else -> {
                        vm.onKeyLearned(event.keyCode, event.scanCode)
                        swallowUpKey = event.keyCode
                    }
                }
            }
            return true
        }
        if (event.action == KeyEvent.ACTION_UP && event.keyCode == swallowUpKey) {
            swallowUpKey = -1
            return true
        }
        return super.dispatchKeyEvent(event)
    }

    companion object {
        const val EXTRA_SCREEN = "orbit_screen"
        const val SCREEN_HOME = "home"
        const val SCREEN_INSTALL = "install"
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
