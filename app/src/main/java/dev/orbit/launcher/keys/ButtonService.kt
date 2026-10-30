package dev.orbit.launcher.keys

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import dev.orbit.launcher.MainActivity

/**
 * Перехватывает назначенные кнопки пульта во ВСЕЙ системе (в любом приложении).
 * Короткое нажатие срабатывает на отпускании, долгое — через LONG_MS удержания.
 */
class ButtonService : AccessibilityService() {

    private val handler = Handler(Looper.getMainLooper())
    private var active: KeyBinding? = null
    private var longFired = false

    private val longRunnable = Runnable {
        val action = active?.long ?: return@Runnable
        longFired = true
        perform(action)
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        if (ButtonStore.learning) return false
        val b = ButtonStore.find(event.keyCode, event.scanCode) ?: return false

        when (event.action) {
            KeyEvent.ACTION_DOWN -> if (event.repeatCount == 0) {
                handler.removeCallbacks(longRunnable)
                active = b
                longFired = false
                if (b.long != null) handler.postDelayed(longRunnable, LONG_MS)
            }
            KeyEvent.ACTION_UP -> {
                handler.removeCallbacks(longRunnable)
                if (active?.id == b.id && !longFired) b.short?.let(::perform)
                active = null
            }
        }
        return true // кнопку назначили — системе её не отдаём
    }

    private fun perform(a: KeyAction) {
        when (a.type) {
            ActionType.APP -> {
                val pm = packageManager
                val pkg = a.pkg ?: return
                val intent = pm.getLeanbackLaunchIntentForPackage(pkg) ?: pm.getLaunchIntentForPackage(pkg)
                if (intent == null) {
                    toast("${a.label ?: pkg} не установлено")
                } else {
                    start(intent)
                }
            }
            ActionType.ORBIT_HOME -> start(orbit(MainActivity.SCREEN_HOME))
            ActionType.ORBIT_INSTALL -> start(orbit(MainActivity.SCREEN_INSTALL))
            ActionType.ANDROID_SETTINGS -> start(Intent(Settings.ACTION_SETTINGS))
            ActionType.RECENTS -> performGlobalAction(GLOBAL_ACTION_RECENTS)
            ActionType.BACK -> performGlobalAction(GLOBAL_ACTION_BACK)
            ActionType.HOME -> performGlobalAction(GLOBAL_ACTION_HOME)
        }
    }

    private fun orbit(screen: String) =
        Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_SCREEN, screen)

    private fun start(intent: Intent) {
        try {
            startActivity(
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED),
            )
        } catch (e: Exception) {
            toast("Не удалось открыть: ${e.message}")
        }
    }

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onUnbind(intent: Intent?): Boolean {
        handler.removeCallbacks(longRunnable)
        return super.onUnbind(intent)
    }

    private companion object {
        const val LONG_MS = 600L
    }
}
