package dev.orbit.launcher.keys

import android.content.ComponentName
import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import android.view.KeyEvent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

enum class ActionType(val title: String) {
    APP("Открыть приложение…"),
    ORBIT_HOME("Главный экран Orbit"),
    ORBIT_INSTALL("Установка APK (Orbit)"),
    RECENTS("Недавние приложения"),
    ANDROID_SETTINGS("Настройки Android"),
    BACK("Назад"),
    HOME("Домой"),
}

data class KeyAction(val type: ActionType, val pkg: String? = null, val label: String? = null) {
    val title: String
        get() = if (type == ActionType.APP) "Открыть: ${label ?: pkg}" else type.title

    fun toJson(): JSONObject = JSONObject().put("type", type.name).put("pkg", pkg).put("label", label)

    companion object {
        fun fromJson(o: JSONObject?): KeyAction? {
            if (o == null) return null
            val type = runCatching { ActionType.valueOf(o.optString("type")) }.getOrNull() ?: return null
            return KeyAction(type, o.optString("pkg").ifEmpty { null }, o.optString("label").ifEmpty { null })
        }
    }
}

/** Кнопка пульта: keyCode, а если Android её не знает (0) — сырой scanCode */
data class KeyBinding(
    val keyCode: Int,
    val scanCode: Int,
    val short: KeyAction? = null,
    val long: KeyAction? = null,
) {
    val id: String get() = if (keyCode != 0) "k$keyCode" else "s$scanCode"

    fun matches(code: Int, scan: Int): Boolean =
        if (keyCode != 0) code == keyCode else scan != 0 && scan == scanCode

    val keyName: String
        get() = if (keyCode != 0) {
            KeyEvent.keyCodeToString(keyCode).removePrefix("KEYCODE_")
        } else {
            "SCAN $scanCode"
        }

    val keyDetails: String get() = "код $keyCode · scan $scanCode"
}

object ButtonStore {

    private const val KEY = "button_bindings"
    private lateinit var sp: SharedPreferences

    private val _bindings = MutableStateFlow<List<KeyBinding>>(emptyList())
    val bindings: StateFlow<List<KeyBinding>> = _bindings.asStateFlow()

    /** Пока Orbit «слушает» новую кнопку — служба не перехватывает ничего */
    @Volatile
    var learning: Boolean = false

    fun init(context: Context) {
        sp = context.getSharedPreferences("orbit", Context.MODE_PRIVATE)
        _bindings.value = load()
    }

    fun find(code: Int, scan: Int): KeyBinding? = _bindings.value.firstOrNull { it.matches(code, scan) }

    fun upsert(b: KeyBinding) {
        val list = _bindings.value.filter { it.id != b.id } + b
        save(list)
    }

    fun remove(id: String) = save(_bindings.value.filter { it.id != id })

    private fun save(list: List<KeyBinding>) {
        _bindings.value = list
        val arr = JSONArray()
        list.forEach { b ->
            arr.put(
                JSONObject()
                    .put("code", b.keyCode)
                    .put("scan", b.scanCode)
                    .put("short", b.short?.toJson())
                    .put("long", b.long?.toJson()),
            )
        }
        sp.edit().putString(KEY, arr.toString()).apply()
    }

    private fun load(): List<KeyBinding> = runCatching {
        val arr = JSONArray(sp.getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            KeyBinding(
                keyCode = o.optInt("code"),
                scanCode = o.optInt("scan"),
                short = KeyAction.fromJson(o.optJSONObject("short")),
                long = KeyAction.fromJson(o.optJSONObject("long")),
            )
        }
    }.getOrDefault(emptyList())

    /** Включена ли служба «Orbit — кнопки» в спец. возможностях */
    fun isServiceEnabled(context: Context): Boolean {
        val me = ComponentName(context, ButtonService::class.java)
        val raw = Settings.Secure.getString(context.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?: return false
        return raw.split(':').any { ComponentName.unflattenFromString(it) == me }
    }

    const val ADB_ENABLE =
        "adb shell settings put secure enabled_accessibility_services " +
            "dev.orbit.launcher/dev.orbit.launcher.keys.ButtonService"
}
