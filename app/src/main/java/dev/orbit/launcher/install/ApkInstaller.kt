package dev.orbit.launcher.install

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import java.io.File

data class ApkMeta(
    val label: String,
    val pkg: String,
    val versionName: String?,
    val versionCode: Long,
    val sizeBytes: Long,
)

object ApkInstaller {

    const val ACTION_RESULT = "dev.orbit.launcher.INSTALL_RESULT"
    const val EXTRA_LABEL = "orbit_label"

    /** Разрешена ли установка из «неизвестных источников» именно для лаунчера */
    fun canInstall(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.O || context.packageManager.canRequestPackageInstalls()

    fun unknownSourcesIntents(context: Context): List<Intent> = buildList {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            add(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}")))
        }
        add(Intent(Settings.ACTION_SECURITY_SETTINGS))
        add(Intent(Settings.ACTION_SETTINGS))
    }

    /** Читает манифест APK без установки. null — файл битый / не APK. */
    fun inspect(context: Context, file: File): ApkMeta? {
        if (!file.isFile) return null
        val pm = context.packageManager
        val info = runCatching { pm.getPackageArchiveInfo(file.absolutePath, 0) }.getOrNull() ?: return null
        val ai = info.applicationInfo
        val label = if (ai != null) {
            ai.sourceDir = file.absolutePath
            ai.publicSourceDir = file.absolutePath
            runCatching { ai.loadLabel(pm).toString() }.getOrNull()
        } else {
            null
        }
        @Suppress("DEPRECATION")
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode.toLong()
        return ApkMeta(
            label = label?.takeIf { it.isNotBlank() } ?: info.packageName,
            pkg = info.packageName,
            versionName = info.versionName,
            versionCode = code,
            sizeBytes = file.length(),
        )
    }

    /**
     * Ставит APK через PackageInstaller session. Результат придёт в [InstallResultReceiver].
     * Вызывать с IO-диспетчера.
     */
    fun install(context: Context, file: File, meta: ApkMeta): Int {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(meta.pkg)
            setSize(file.length())
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Обновления приложений, которые ставил сам лаунчер, — без лишнего подтверждения
                setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
        }
        val sessionId = installer.createSession(params)
        try {
            installer.openSession(sessionId).use { session ->
                file.inputStream().use { input ->
                    session.openWrite("base.apk", 0, file.length()).use { out ->
                        input.copyTo(out, bufferSize = 256 * 1024)
                        session.fsync(out)
                    }
                }
                val intent = Intent(context, InstallResultReceiver::class.java)
                    .setAction(ACTION_RESULT)
                    .putExtra(EXTRA_LABEL, meta.label)
                var flags = PendingIntent.FLAG_UPDATE_CURRENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) flags = flags or PendingIntent.FLAG_MUTABLE
                val pi = PendingIntent.getBroadcast(context, sessionId, intent, flags)
                session.commit(pi.intentSender)
            }
        } catch (e: Exception) {
            runCatching { installer.abandonSession(sessionId) }
            throw e
        }
        return sessionId
    }

    fun statusMessage(status: Int, raw: String?): String {
        val base = when (status) {
            PackageInstaller.STATUS_FAILURE_ABORTED -> "Установка отменена"
            PackageInstaller.STATUS_FAILURE_BLOCKED -> "Установка заблокирована системой"
            PackageInstaller.STATUS_FAILURE_CONFLICT ->
                "Конфликт подписи: уже стоит версия с другим ключом. Удалите её и повторите"
            PackageInstaller.STATUS_FAILURE_INCOMPATIBLE ->
                "APK несовместим с приставкой (архитектура или версия Android). Ищите armeabi-v7a"
            PackageInstaller.STATUS_FAILURE_INVALID -> "APK повреждён или некорректен"
            PackageInstaller.STATUS_FAILURE_STORAGE -> "Недостаточно памяти на приставке"
            else -> "Ошибка установки"
        }
        return if (raw.isNullOrBlank()) base else "$base\n$raw"
    }
}
