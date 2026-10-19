package com.example.woodsreader.core.license

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProLicenseManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("woods_pro_license_vault", Context.MODE_PRIVATE)

    // Semilla ofuscada con XOR (0x5A) para que no aparezca en texto plano al descompilar el APK
    private val xorSeed = byteArrayOf(
        0x0D, 0x35, 0x35, 0x3E, 0x29, 0x05, 0x09, 0x2E,
        0x2F, 0x3E, 0x33, 0x35, 0x05, 0x17, 0x3B, 0x29,
        0x2E, 0x3F, 0x28, 0x05, 0x68, 0x6A, 0x68, 0x6C
    )

    val deviceInstallCode: String by lazy { computeDeviceInstallCode() }

    private val _isProUnlocked = MutableStateFlow(verifySavedLicense())
    val isProUnlocked: StateFlow<Boolean> = _isProUnlocked.asStateFlow()

    @SuppressLint("HardwareIds")
    private fun computeDeviceInstallCode(): String {
        val rawId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty().ifBlank { "WOODS_FALLBACK_DEVICE" }

        val digest = MessageDigest.getInstance("SHA-256")
            .digest(rawId.lowercase().toByteArray(Charsets.UTF_8))
        val hex = digest.joinToString("") { "%02X".format(it) }.take(8)
        return "WR-${hex.substring(0, 4)}-${hex.substring(4, 8)}"
    }

    private fun reconstructSecret(): ByteArray {
        return ByteArray(xorSeed.size) { idx ->
            (xorSeed[idx].toInt() xor 0x5A).toByte()
        }
    }

    fun generateExpectedKeyForDevice(installCode: String): String {
        val normalizedId = installCode.trim().uppercase()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(reconstructSecret(), "HmacSHA256"))
        val signature = mac.doFinal(normalizedId.toByteArray(Charsets.UTF_8))
        val hex = signature.joinToString("") { "%02X".format(it) }.take(12)
        return "PRO-${hex.substring(0, 4)}-${hex.substring(4, 8)}-${hex.substring(8, 12)}"
    }

    fun activateWithLicenseKey(inputKey: String): Boolean {
        val cleanInput = inputKey.trim().uppercase()
        val expected = generateExpectedKeyForDevice(deviceInstallCode)
        if (cleanInput == expected) {
            prefs.edit().putString("activated_license_key", cleanInput).apply()
            _isProUnlocked.value = true
            return true
        }
        return false
    }

    private fun verifySavedLicense(): Boolean {
        val saved = prefs.getString("activated_license_key", null) ?: return false
        return saved.trim().uppercase() == generateExpectedKeyForDevice(deviceInstallCode)
    }
}
