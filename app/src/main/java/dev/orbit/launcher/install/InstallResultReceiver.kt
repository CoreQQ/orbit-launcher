package dev.orbit.launcher.install

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import androidx.core.content.IntentCompat
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow

sealed interface InstallEvent {
    data class Success(val label: String?, val pkg: String?) : InstallEvent
    data class Failure(val label: String?, val message: String) : InstallEvent
}

object InstallBus {
    private val _events = MutableSharedFlow<InstallEvent>(extraBufferCapacity = 8)
    val events: SharedFlow<InstallEvent> = _events
    fun emit(e: InstallEvent) {
        _events.tryEmit(e)
    }
}

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val label = intent.getStringExtra(ApkInstaller.EXTRA_LABEL)
        when (val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)) {
            PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                // Системный диалог «Установить?»
                val confirm = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_INTENT, Intent::class.java)
                if (confirm != null) {
                    confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(confirm)
                    } catch (e: Exception) {
                        InstallBus.emit(InstallEvent.Failure(label, "Не удалось открыть подтверждение: ${e.message}"))
                    }
                }
            }
            PackageInstaller.STATUS_SUCCESS -> InstallBus.emit(
                InstallEvent.Success(label, intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)),
            )
            else -> InstallBus.emit(
                InstallEvent.Failure(
                    label,
                    ApkInstaller.statusMessage(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)),
                ),
            )
        }
    }
}
