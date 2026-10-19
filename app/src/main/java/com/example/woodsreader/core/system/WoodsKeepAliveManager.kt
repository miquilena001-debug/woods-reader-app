package com.example.woodsreader.core.system

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WoodsKeepAliveManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
    private var audioSessionWakeLock: PowerManager.WakeLock? = null

    private val _isUnrestrictedBattery = MutableStateFlow(checkBatteryOptimizationIgnored())
    val isUnrestrictedBattery: StateFlow<Boolean> = _isUnrestrictedBattery.asStateFlow()

    fun refreshBatteryStatus(): Boolean {
        val ignored = checkBatteryOptimizationIgnored()
        _isUnrestrictedBattery.value = ignored
        return ignored
    }

    private fun checkBatteryOptimizationIgnored(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            runCatching {
                powerManager.isIgnoringBatteryOptimizations(context.packageName)
            }.getOrDefault(false)
        } else {
            true
        }
    }

    /**
     * Lanza el diálogo directo de Android para excluir a Woods Reader de Doze y cierres en segundo plano
     * con un solo toque, sin obligar al usuario a buscar entre menús.
     */
    @SuppressLint("BatteryLife")
    fun requestIgnoreBatteryOptimizations(activityContext: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!checkBatteryOptimizationIgnored()) {
                runCatching {
                    val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                        data = Uri.parse("package:${context.packageName}")
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    activityContext.startActivity(intent)
                }.onFailure {
                    openAppDetailsBatterySettings(activityContext)
                }
            }
        }
    }

    fun openAppDetailsBatterySettings(activityContext: Context) {
        runCatching {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", context.packageName, null)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            activityContext.startActivity(intent)
        }
    }

    /**
     * Mantiene despierto el procesador cuando suena el TTS, el Paisaje Sonoro o la Exportación .M4A
     * con la pantalla apagada.
     */
    @SuppressLint("WakelockTimeout")
    @Synchronized
    fun acquireKeepAliveWakeLock(timeoutMs: Long = 3 * 60 * 60 * 1000L) {
        runCatching {
            val lock = audioSessionWakeLock ?: powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "WoodsReader::KeepAliveSessionLock"
            ).apply {
                setReferenceCounted(false)
                audioSessionWakeLock = this
            }
            if (!lock.isHeld) {
                lock.acquire(timeoutMs)
            }
        }
    }

    @Synchronized
    fun releaseKeepAliveWakeLock() {
        runCatching {
            audioSessionWakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        }
    }
}
