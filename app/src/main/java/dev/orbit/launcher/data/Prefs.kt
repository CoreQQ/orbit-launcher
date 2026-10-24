package dev.orbit.launcher.data

import android.content.Context

class Prefs(context: Context) {

    private val sp = context.getSharedPreferences("orbit", Context.MODE_PRIVATE)

    /** Порядок важен, поэтому храним строкой, а не StringSet. */
    var favorites: List<String>
        get() = sp.getString(K_FAV, "").orEmpty().split('\n').filter { it.isNotBlank() }
        set(value) = sp.edit().putString(K_FAV, value.joinToString("\n")).apply()

    var hidden: Set<String>
        get() = sp.getStringSet(K_HIDDEN, emptySet()).orEmpty().toSet()
        set(value) = sp.edit().putStringSet(K_HIDDEN, HashSet(value)).apply()

    var columns: Int
        get() = sp.getInt(K_COLS, 6)
        set(value) = sp.edit().putInt(K_COLS, value).apply()

    /** Пусто = встроенная витрина из assets/catalog.json */
    var catalogUrl: String
        get() = sp.getString(K_CATALOG, "").orEmpty()
        set(value) = sp.edit().putString(K_CATALOG, value.trim()).apply()

    var recentUrls: List<String>
        get() = sp.getString(K_RECENT, "").orEmpty().split('\n').filter { it.isNotBlank() }
        private set(value) = sp.edit().putString(K_RECENT, value.joinToString("\n")).apply()

    fun addRecentUrl(url: String) {
        recentUrls = (listOf(url) + recentUrls.filter { it != url }).take(6)
    }

    var seeded: Boolean
        get() = sp.getBoolean(K_SEEDED, false)
        set(value) = sp.edit().putBoolean(K_SEEDED, value).apply()

    private companion object {
        const val K_FAV = "favorites"
        const val K_HIDDEN = "hidden"
        const val K_COLS = "columns"
        const val K_CATALOG = "catalog_url"
        const val K_RECENT = "recent_urls"
        const val K_SEEDED = "seeded"
    }
}
