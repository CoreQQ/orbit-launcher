package dev.orbit.launcher.install

import android.content.Context
import org.json.JSONObject

data class CatalogItem(
    val name: String,
    val pkg: String?,
    val description: String,
    /** Прямая ссылка на APK; пусто для приложений из Play Маркета */
    val url: String,
    /** true — ставится через Play Маркет (нет официального APK) */
    val store: Boolean = false,
) {
    val key: String get() = url.ifEmpty { "store:$pkg" }
}

sealed interface CatalogState {
    data object Loading : CatalogState
    data class Ready(
        val items: List<CatalogItem>,
        val source: String,
        val error: String? = null,
    ) : CatalogState
}

object Catalog {

    /**
     * Формат:
     * { "apps": [ { "name": "...", "package": "...", "description": "...", "url": "https://....apk" } ] }
     * Для Play Маркета вместо url: "store": true (нужен "package").
     */
    fun parse(json: String): List<CatalogItem> {
        val arr = JSONObject(json).getJSONArray("apps")
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val url = o.optString("url").trim()
            val name = o.optString("name").trim()
            val pkg = o.optString("package").trim().ifEmpty { null }
            val store = o.optBoolean("store", false) && pkg != null
            if (name.isEmpty() || (url.isEmpty() && !store)) return@mapNotNull null
            CatalogItem(
                name = name,
                pkg = pkg,
                description = o.optString("description").trim(),
                url = url,
                store = store,
            )
        }
    }

    fun builtIn(context: Context): List<CatalogItem> =
        context.assets.open("catalog.json").bufferedReader().use { parse(it.readText()) }

    /** Удалённая витрина, при ошибке — встроенная + текст ошибки */
    suspend fun load(context: Context, remoteUrl: String): CatalogState.Ready {
        if (remoteUrl.isBlank()) return CatalogState.Ready(builtIn(context), "встроенная")
        return try {
            CatalogState.Ready(parse(Downloader.fetchText(remoteUrl)), remoteUrl)
        } catch (e: Exception) {
            CatalogState.Ready(
                builtIn(context),
                "встроенная",
                error = "Не удалось загрузить $remoteUrl: ${e.message ?: e.javaClass.simpleName}",
            )
        }
    }
}
