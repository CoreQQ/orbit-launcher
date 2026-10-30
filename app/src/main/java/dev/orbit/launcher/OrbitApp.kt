package dev.orbit.launcher

import android.app.Application
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat
import dev.orbit.launcher.data.AppRepository
import dev.orbit.launcher.data.IconCache
import dev.orbit.launcher.data.Prefs
import dev.orbit.launcher.keys.ButtonStore
import kotlinx.coroutines.flow.MutableSharedFlow

class OrbitApp : Application() {

    lateinit var prefs: Prefs
        private set
    lateinit var repo: AppRepository
        private set
    lateinit var icons: IconCache
        private set

    /** Имя пакета, который установили/удалили/обновили (null — «что-то поменялось»). */
    val packageEvents = MutableSharedFlow<String?>(extraBufferCapacity = 16)

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val pkg = intent.data?.schemeSpecificPart
            pkg?.let(icons::invalidate)
            packageEvents.tryEmit(pkg)
        }
    }

    override fun onCreate() {
        super.onCreate()
        prefs = Prefs(this)
        ButtonStore.init(this)
        repo = AppRepository(this)
        icons = IconCache(this)

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)
    }
}
