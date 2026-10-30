package dev.orbit.launcher.data

import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.provider.Settings
import java.util.Locale

data class AppInfo(
    val pkg: String,
    val activity: String,
    val label: String,
    /** true — у приложения есть TV-активити (LEANBACK_LAUNCHER) */
    val isLeanback: Boolean,
    /** Системное и не обновлённое — удалить нельзя */
    val isSystem: Boolean,
    val versionName: String?,
)

class AppRepository(private val context: Context) {

    private val pm get() = context.packageManager

    fun load(): List<AppInfo> {
        val result = LinkedHashMap<String, AppInfo>()

        fun collect(category: String, leanback: Boolean) {
            val intent = Intent(Intent.ACTION_MAIN).addCategory(category)
            for (ri in pm.queryIntentActivities(intent, 0)) {
                val ai = ri.activityInfo ?: continue
                val pkg = ai.packageName
                if (pkg == context.packageName || result.containsKey(pkg)) continue
                val flags = ai.applicationInfo.flags
                val system = flags and ApplicationInfo.FLAG_SYSTEM != 0 &&
                    flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP == 0
                result[pkg] = AppInfo(
                    pkg = pkg,
                    activity = ai.name,
                    label = ri.loadLabel(pm)?.toString()?.trim().orEmpty().ifEmpty { pkg },
                    isLeanback = leanback,
                    isSystem = system,
                    versionName = installedVersion(pkg),
                )
            }
        }

        // TV-активити приоритетнее: у многих приложений есть обе
        collect(Intent.CATEGORY_LEANBACK_LAUNCHER, leanback = true)
        collect(Intent.CATEGORY_LAUNCHER, leanback = false)

        return result.values.sortedBy { it.label.lowercase(Locale.getDefault()) }
    }

    fun installedVersion(pkg: String?): String? {
        if (pkg.isNullOrBlank()) return null
        return try {
            pm.getPackageInfo(pkg, 0).versionName ?: "?"
        } catch (_: Exception) {
            null
        }
    }

    fun launch(app: AppInfo): Boolean {
        val category = if (app.isLeanback) Intent.CATEGORY_LEANBACK_LAUNCHER else Intent.CATEGORY_LAUNCHER
        val intent = Intent(Intent.ACTION_MAIN)
            .addCategory(category)
            .setComponent(ComponentName(app.pkg, app.activity))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        return start(intent) ||
            start(pm.getLeanbackLaunchIntentForPackage(app.pkg)) ||
            start(pm.getLaunchIntentForPackage(app.pkg))
    }

    fun openAppDetails(pkg: String): Boolean =
        start(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:$pkg")))

    fun uninstall(pkg: String): Boolean =
        start(Intent(Intent.ACTION_DELETE, Uri.parse("package:$pkg")))

    fun start(intent: Intent?): Boolean {
        if (intent == null) return false
        return try {
            context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            true
        } catch (_: ActivityNotFoundException) {
            false
        } catch (_: SecurityException) {
            false
        }
    }

    companion object {
        /** При первом запуске эти приложения (если стоят) попадают в избранное */
        val DEFAULT_FAVORITES = listOf(
            "com.teamsmart.videomanager.tv",      // SmartTube
            "com.liskovsoft.smarttubetv.beta",    // SmartTube beta
            "com.google.android.youtube.tv",      // YouTube for Android TV
            "com.netflix.ninja",                  // Netflix
            "com.amazon.amazonvideo.livingroom",  // Prime Video
            "com.disney.disneyplus",
            "org.videolan.vlc",
            "org.xbmc.kodi",
            "com.google.android.youtube.tvmusic",
            "com.ionitech.airscreen",             // AirScreen (AirPlay)
        )
    }
}
