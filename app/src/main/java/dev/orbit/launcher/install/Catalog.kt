package dev.orbit.launcher.install

import android.content.Context
import org.json.JSONObject

data class CatalogItem(
    val name: String,
    val pkg: String?,
    val description: String,
    val url: String,
)

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
     */
    fun parse(json: String): List<CatalogItem> {
        val arr = JSONObject(json).getJSONArray("apps")
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.getJSONObject(i)
            val url = o.optString("url").trim()
            val name = o.optString("name").trim()
            if (url.isEmpty() || name.isEmpty()) return@mapNotNull null
            CatalogItem(
                name = name,
                pkg = o.optString("package").trim().ifEmpty { null },
                description = o.optString("description").trim(),
                url = url,
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
