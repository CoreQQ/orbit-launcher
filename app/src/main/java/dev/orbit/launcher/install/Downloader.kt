package dev.orbit.launcher.install

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.coroutineContext

object Downloader {

    private const val UA = "OrbitLauncher/1.0 (Android TV)"

    /** Открывает соединение, вручную проходя редиректы (в т.ч. http↔https, kutt.to и т.п.) */
    private fun open(url: String): HttpURLConnection {
        var current = URL(url)
        repeat(12) {
            val conn = current.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", UA)
            val code = conn.responseCode
            if (code in 300..399) {
                val location = conn.getHeaderField("Location")
                conn.disconnect()
                if (location.isNullOrBlank()) throw IOException("Редирект без адреса (HTTP $code)")
                current = URL(current, location)
                return@repeat
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw IOException("Сервер ответил HTTP $code")
            }
            return conn
        }
        throw IOException("Слишком много редиректов")
    }

    suspend fun download(
        url: String,
        dest: File,
        onProgress: (done: Long, total: Long) -> Unit,
    ): File = withContext(Dispatchers.IO) {
        val conn = open(url)
        try {
            val total = conn.contentLengthLong
            var done = 0L
            var lastReported = -1L
            conn.inputStream.use { input ->
                dest.outputStream().use { out ->
                    val buf = ByteArray(128 * 1024)
                    while (true) {
                        coroutineContext.ensureActive()
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        // Не чаще чем раз на ~1% (или на каждые 512 КБ, если размер неизвестен)
                        val step = if (total > 0) total / 100 else 512 * 1024L
                        if (done - lastReported >= step) {
                            lastReported = done
                            onProgress(done, total)
                        }
                    }
                }
            }
            onProgress(done, total)
            if (total > 0 && done < total) throw IOException("Загрузка оборвалась ($done из $total байт)")
            dest
        } catch (e: Throwable) {
            dest.delete()
            throw e
        } finally {
            conn.disconnect()
        }
    }

    suspend fun fetchText(url: String, maxBytes: Int = 1 shl 20): String = withContext(Dispatchers.IO) {
        val conn = open(url)
        try {
            conn.inputStream.use { input ->
                val bytes = input.readBytes()
                if (bytes.size > maxBytes) throw IOException("Слишком большой ответ")
                String(bytes, Charsets.UTF_8)
            }
        } finally {
            conn.disconnect()
        }
    }
}
