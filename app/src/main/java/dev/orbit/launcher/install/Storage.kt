package dev.orbit.launcher.install

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import java.io.File
import java.util.Locale

data class StorageRoot(val title: String, val dir: File, val removable: Boolean)

data class FileEntry(val file: File, val isDir: Boolean, val size: Long)

object Storage {

    fun roots(context: Context): List<StorageRoot> {
        val out = mutableListOf<StorageRoot>()

        val sm = context.getSystemService(StorageManager::class.java)
        sm?.storageVolumes?.forEach { v ->
            val dir: File? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                v.directory
            } else {
                // Android 9: скрытый, но доступный getPathFile()
                runCatching { v.javaClass.getMethod("getPathFile").invoke(v) as? File }.getOrNull()
            }
            if (dir != null && dir.exists()) {
                val title = v.getDescription(context) ?: dir.name
                out += StorageRoot(if (v.isRemovable) "USB: $title" else title, dir, v.isRemovable)
            }
        }

        // Запасной путь: всё, что смонтировано в /storage
        File("/storage").listFiles()?.forEach { f ->
            if (f.isDirectory && f.name != "emulated" && f.name != "self" && f.canRead()) {
                out += StorageRoot("USB: ${f.name}", f, true)
            }
        }
        @Suppress("DEPRECATION")
        val internal = Environment.getExternalStorageDirectory()
        out += StorageRoot("Внутренняя память", internal, false)
        val downloads = File(internal, Environment.DIRECTORY_DOWNLOADS)
        if (downloads.isDirectory) out += StorageRoot("Загрузки", downloads, false)

        return out
            .distinctBy { runCatching { it.dir.canonicalPath }.getOrDefault(it.dir.absolutePath) }
            .sortedByDescending { it.removable }
    }

    fun list(dir: File): List<FileEntry> {
        val files = dir.listFiles() ?: return emptyList()
        return files
            .filter { !it.name.startsWith(".") && (it.isDirectory || it.name.lowercase(Locale.ROOT).endsWith(".apk")) }
            .map { FileEntry(it, it.isDirectory, if (it.isDirectory) 0 else it.length()) }
            .sortedWith(compareBy<FileEntry> { !it.isDir }.thenBy { it.file.name.lowercase(Locale.ROOT) })
    }

    /** Все APK на носителе (до 4 уровней вглубь) — быстрый поиск */
    fun findApks(root: File, maxDepth: Int = 4, limit: Int = 200): List<FileEntry> {
        val out = mutableListOf<FileEntry>()
        fun walk(d: File, depth: Int) {
            if (depth > maxDepth || out.size >= limit) return
            val files = d.listFiles() ?: return
            for (f in files) {
                if (f.name.startsWith(".")) continue
                if (f.isDirectory) {
                    if (f.name != "Android") walk(f, depth + 1)
                } else if (f.name.lowercase(Locale.ROOT).endsWith(".apk")) {
                    out += FileEntry(f, false, f.length())
                }
                if (out.size >= limit) return
            }
        }
        walk(root, 0)
        return out.sortedBy { it.file.name.lowercase(Locale.ROOT) }
    }

    fun humanSize(bytes: Long): String = when {
        bytes >= 1L shl 30 -> String.format(Locale.US, "%.1f ГБ", bytes / (1L shl 30).toDouble())
        bytes >= 1L shl 20 -> String.format(Locale.US, "%.1f МБ", bytes / (1L shl 20).toDouble())
        bytes >= 1L shl 10 -> String.format(Locale.US, "%.0f КБ", bytes / (1L shl 10).toDouble())
        else -> "$bytes Б"
    }
}
