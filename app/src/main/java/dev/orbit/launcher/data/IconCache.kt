package dev.orbit.launcher.data

import android.content.ComponentName
import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap

data class AppVisual(
    val bitmap: ImageBitmap,
    /** true — это TV-баннер 16:9, рисуем на всю карточку */
    val isBanner: Boolean,
    /** Фон карточки для обычных иконок (затемнённый средний цвет) */
    val tint: Color,
)

class IconCache(private val context: Context) {

    private val cache = ConcurrentHashMap<String, AppVisual>()

    fun peek(pkg: String): AppVisual? = cache[pkg]

    suspend fun get(app: AppInfo): AppVisual =
        cache[app.pkg] ?: withContext(Dispatchers.IO) { load(app) }.also { cache[app.pkg] = it }

    fun invalidate(pkg: String) {
        cache.remove(pkg)
    }

    private fun load(app: AppInfo): AppVisual {
        val pm = context.packageManager
        val cn = ComponentName(app.pkg, app.activity)

        val banner: Drawable? = runCatching { pm.getActivityBanner(cn) }.getOrNull()
            ?: runCatching { pm.getApplicationBanner(app.pkg) }.getOrNull()

        if (banner != null) {
            val bmp = banner.safeBitmap(BANNER_W, BANNER_H)
            if (bmp != null) return AppVisual(bmp.asImageBitmap(), true, averageColor(bmp))
        }

        val icon: Drawable = runCatching { pm.getActivityIcon(cn) }.getOrNull()
            ?: runCatching { pm.getApplicationIcon(app.pkg) }.getOrNull()
            ?: pm.defaultActivityIcon
        val bmp = icon.safeBitmap(ICON, ICON) ?: pm.defaultActivityIcon.toBitmap(ICON, ICON)
        return AppVisual(bmp.asImageBitmap(), false, averageColor(bmp))
    }

    private fun Drawable.safeBitmap(w: Int, h: Int): Bitmap? =
        runCatching { toBitmap(w, h, Bitmap.Config.ARGB_8888) }.getOrNull()

    /** Средний цвет иконки, затемнённый — фон карточки без баннера */
    private fun averageColor(bmp: Bitmap): Color = runCatching {
        val px = Bitmap.createScaledBitmap(bmp, 1, 1, true).getPixel(0, 0)
        val r = android.graphics.Color.red(px)
        val g = android.graphics.Color.green(px)
        val b = android.graphics.Color.blue(px)
        Color(
            red = (r * 0.35f + 18) / 255f,
            green = (g * 0.35f + 22) / 255f,
            blue = (b * 0.35f + 36) / 255f,
        )
    }.getOrDefault(Color(0xFF1A2033))

    private companion object {
        const val BANNER_W = 320
        const val BANNER_H = 180
        const val ICON = 160
    }
}
