package dev.orbit.launcher

import android.app.Application
import android.widget.Toast
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import dev.orbit.launcher.data.AppInfo
import dev.orbit.launcher.data.AppRepository
import dev.orbit.launcher.install.ApkInstaller
import dev.orbit.launcher.install.Catalog
import dev.orbit.launcher.install.CatalogState
import dev.orbit.launcher.install.Downloader
import dev.orbit.launcher.install.InstallBus
import dev.orbit.launcher.install.InstallEvent
import dev.orbit.launcher.keys.ButtonStore
import dev.orbit.launcher.keys.KeyBinding
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

enum class Screen { Home, Install, Settings }

data class ButtonDraft(val binding: KeyBinding, val isNew: Boolean)

sealed interface InstallState {
    data object Idle : InstallState
    /** progress: 0..1, либо -1 если размер неизвестен */
    data class Downloading(val name: String, val progress: Float) : InstallState
    data class Installing(val name: String) : InstallState
    data class Done(val name: String) : InstallState
    data class Failed(val name: String, val message: String) : InstallState
}

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val orbit = app as OrbitApp
    val prefs = orbit.prefs
    val repo = orbit.repo
    val icons = orbit.icons

    private val _apps = MutableStateFlow<List<AppInfo>>(emptyList())
    val apps: StateFlow<List<AppInfo>> = _apps.asStateFlow()

    private val _favorites = MutableStateFlow(prefs.favorites)
    val favorites: StateFlow<List<String>> = _favorites.asStateFlow()

    private val _hidden = MutableStateFlow(prefs.hidden)
    val hidden: StateFlow<Set<String>> = _hidden.asStateFlow()

    private val _columns = MutableStateFlow(prefs.columns)
    val columns: StateFlow<Int> = _columns.asStateFlow()

    private val _screen = MutableStateFlow(Screen.Home)
    val screen: StateFlow<Screen> = _screen.asStateFlow()

    /** Пакет, который сейчас двигаем в избранном (режим перестановки) */
    private val _moving = MutableStateFlow<String?>(null)
    val moving: StateFlow<String?> = _moving.asStateFlow()

    private val _install = MutableStateFlow<InstallState>(InstallState.Idle)
    val install: StateFlow<InstallState> = _install.asStateFlow()

    private val _catalog = MutableStateFlow<CatalogState>(CatalogState.Loading)
    val catalog: StateFlow<CatalogState> = _catalog.asStateFlow()

    private val _recentUrls = MutableStateFlow(prefs.recentUrls)
    val recentUrls: StateFlow<List<String>> = _recentUrls.asStateFlow()

    private val _catalogUrl = MutableStateFlow(prefs.catalogUrl)
    val catalogUrl: StateFlow<String> = _catalogUrl.asStateFlow()

    private var installJob: Job? = null
    private var refreshJob: Job? = null
    private var catalogLoaded = false
    private var tempApk: File? = null
    private var currentName: String = ""

    init {
        refresh()
        viewModelScope.launch { orbit.packageEvents.collect { refresh() } }
        viewModelScope.launch { InstallBus.events.collect { onInstallEvent(it) } }
    }

    // ---------- навигация ----------

    fun go(screen: Screen) {
        _moving.value = null
        _screen.value = screen
    }

    fun goHome() = go(Screen.Home)

    // ---------- приложения ----------

    fun refresh() {
        refreshJob?.cancel()
        refreshJob = viewModelScope.launch {
            val list = withContext(Dispatchers.IO) { repo.load() }
            _apps.value = list
            seedFavorites(list)
        }
    }

    private fun seedFavorites(list: List<AppInfo>) {
        if (prefs.seeded || list.isEmpty()) return
        val installed = list.map { it.pkg }.toSet()
        setFavorites(AppRepository.DEFAULT_FAVORITES.filter { it in installed })
        prefs.seeded = true
    }

    fun launch(app: AppInfo) {
        if (!repo.launch(app)) toast("Не удалось запустить ${app.label}")
    }

    fun openDetails(app: AppInfo) {
        if (!repo.openAppDetails(app.pkg)) toast("Экран сведений недоступен")
    }

    fun uninstall(app: AppInfo) {
        if (app.isSystem) {
            toast("Системное приложение — удалить нельзя, можно скрыть")
            return
        }
        if (!repo.uninstall(app.pkg)) toast("Удаление недоступно")
    }

    // ---------- избранное ----------

    private fun setFavorites(list: List<String>) {
        _favorites.value = list
        prefs.favorites = list
    }

    fun isFavorite(pkg: String) = pkg in _favorites.value

    fun toggleFavorite(pkg: String) {
        val cur = _favorites.value
        setFavorites(if (pkg in cur) cur - pkg else cur + pkg)
    }

    fun moveFavorite(pkg: String, delta: Int) {
        // двигаем только среди видимых (установленных и не скрытых)
        val visible = _apps.value.map { it.pkg }.toSet() - _hidden.value
        val cur = _favorites.value.toMutableList()
        val shown = cur.filter { it in visible }
        val i = shown.indexOf(pkg)
        val j = i + delta
        if (i < 0 || j !in shown.indices) return
        val a = cur.indexOf(shown[i])
        val b = cur.indexOf(shown[j])
        cur[a] = shown[j]
        cur[b] = shown[i]
        setFavorites(cur)
    }

    fun startMoving(pkg: String) {
        _moving.value = pkg
    }

    fun stopMoving() {
        _moving.value = null
    }

    // ---------- скрытые / вид ----------

    fun setHidden(pkg: String, hide: Boolean) {
        val next = if (hide) _hidden.value + pkg else _hidden.value - pkg
        _hidden.value = next
        prefs.hidden = next
    }

    fun cycleColumns() {
        val next = when (_columns.value) {
            5 -> 6
            6 -> 7
            else -> 5
        }
        _columns.value = next
        prefs.columns = next
    }

    // ---------- витрина ----------

    fun ensureCatalog() {
        if (!catalogLoaded) loadCatalog()
    }

    fun loadCatalog() {
        catalogLoaded = true
        _catalog.value = CatalogState.Loading
        viewModelScope.launch {
            _catalog.value = Catalog.load(getApplication(), prefs.catalogUrl)
        }
    }

    fun setCatalogUrl(url: String) {
        prefs.catalogUrl = url
        _catalogUrl.value = prefs.catalogUrl
        loadCatalog()
    }

    // ---------- кнопки пульта ----------

    val bindings: StateFlow<List<KeyBinding>> = ButtonStore.bindings

    private val _learning = MutableStateFlow(false)
    val learning: StateFlow<Boolean> = _learning.asStateFlow()
    val isLearning: Boolean get() = _learning.value

    /** Кнопка, которую сейчас настраиваем (null — редактор закрыт) */
    private val _draft = MutableStateFlow<ButtonDraft?>(null)
    val draft: StateFlow<ButtonDraft?> = _draft.asStateFlow()

    private var learnJob: Job? = null

    fun startLearning() {
        ButtonStore.learning = true
        _learning.value = true
        learnJob?.cancel()
        learnJob = viewModelScope.launch {
            delay(15_000)
            if (_learning.value) {
                stopLearning()
                toast("Нажатие не дошло до приставки — кнопка ничего не отправляет или её забирает система")
            }
        }
    }

    fun stopLearning() {
        learnJob?.cancel()
        _learning.value = false
        ButtonStore.learning = false
    }

    fun onKeyLearned(keyCode: Int, scanCode: Int) {
        stopLearning()
        val existing = ButtonStore.find(keyCode, scanCode)
        _draft.value = ButtonDraft(existing ?: KeyBinding(keyCode, scanCode), isNew = existing == null)
    }

    fun editBinding(b: KeyBinding) {
        _draft.value = ButtonDraft(b, isNew = false)
    }

    fun updateDraft(b: KeyBinding) {
        _draft.value = _draft.value?.copy(binding = b)
    }

    fun saveDraft() {
        val d = _draft.value ?: return
        if (d.binding.short == null && d.binding.long == null) {
            ButtonStore.remove(d.binding.id)
        } else {
            ButtonStore.upsert(d.binding)
        }
        _draft.value = null
    }

    fun deleteDraft() {
        _draft.value?.let { ButtonStore.remove(it.binding.id) }
        _draft.value = null
    }

    fun closeDraft() {
        _draft.value = null
    }

    // ---------- установка ----------

    fun canInstall() = ApkInstaller.canInstall(getApplication())

    val isBusy: Boolean
        get() = _install.value is InstallState.Downloading || _install.value is InstallState.Installing

    fun installUrl(rawUrl: String, title: String? = null) {
        if (isBusy) {
            toast("Дождитесь окончания текущей установки")
            return
        }
        val url = normalizeUrl(rawUrl) ?: run {
            toast("Некорректная ссылка")
            return
        }
        prefs.addRecentUrl(url)
        _recentUrls.value = prefs.recentUrls

        val name = title ?: url.substringBefore('?').substringAfterLast('/').ifBlank { "APK" }
        currentName = name
        installJob = viewModelScope.launch {
            _install.value = InstallState.Downloading(name, -1f)
            val file: File = try {
                val dir = File(getApplication<Application>().cacheDir, "apk").apply {
                    mkdirs()
                    listFiles()?.forEach { it.delete() }
                }
                val target = File(dir, "download_${System.currentTimeMillis()}.apk")
                Downloader.download(url, target) { done, total ->
                    _install.value = InstallState.Downloading(name, if (total > 0) done.toFloat() / total else -1f)
                }
            } catch (e: CancellationException) {
                _install.value = InstallState.Idle
                throw e
            } catch (e: Exception) {
                _install.value = InstallState.Failed(name, "Ошибка загрузки: ${e.message ?: e.javaClass.simpleName}")
                return@launch
            }
            installFileInternal(file, temp = true)
        }
    }

    fun installFile(file: File) {
        if (isBusy) {
            toast("Дождитесь окончания текущей установки")
            return
        }
        currentName = file.name
        installJob = viewModelScope.launch { installFileInternal(file, temp = false) }
    }

    private suspend fun installFileInternal(file: File, temp: Boolean) {
        val ctx = getApplication<Application>()
        val meta = withContext(Dispatchers.IO) { ApkInstaller.inspect(ctx, file) }
        if (meta == null) {
            _install.value = InstallState.Failed(file.name, "Файл повреждён или это не APK")
            if (temp) file.delete()
            return
        }
        currentName = meta.label
        tempApk = if (temp) file else null
        _install.value = InstallState.Installing(meta.label)
        try {
            withContext(Dispatchers.IO) { ApkInstaller.install(ctx, file, meta) }
            // дальше ждём InstallBus
        } catch (e: Exception) {
            _install.value = InstallState.Failed(meta.label, "Ошибка установки: ${e.message ?: e.javaClass.simpleName}")
            cleanupTemp()
        }
    }

    private fun onInstallEvent(e: InstallEvent) {
        when (e) {
            is InstallEvent.Success -> {
                val name = e.label ?: currentName
                _install.value = InstallState.Done(name)
                cleanupTemp()
                refresh()
                viewModelScope.launch {
                    delay(4000)
                    if (_install.value == InstallState.Done(name)) _install.value = InstallState.Idle
                }
            }
            is InstallEvent.Failure -> {
                _install.value = InstallState.Failed(e.label ?: currentName, e.message)
                cleanupTemp()
            }
        }
    }

    fun dismissInstall() {
        installJob?.cancel()
        installJob = null
        _install.value = InstallState.Idle
    }

    private fun cleanupTemp() {
        tempApk?.delete()
        tempApk = null
    }

    private fun normalizeUrl(raw: String): String? {
        val s = raw.trim()
        if (s.isEmpty() || s.contains(' ')) return null
        return if (s.startsWith("http://", true) || s.startsWith("https://", true)) s else "https://$s"
    }

    fun toast(msg: String) {
        Toast.makeText(getApplication(), msg, Toast.LENGTH_SHORT).show()
    }
}
