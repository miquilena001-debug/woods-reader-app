package com.example.woodsreader.core.license

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.KeyFactory
import java.security.MessageDigest
import java.security.PublicKey
import java.security.Signature
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ProLicenseManager @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val prefs = context.getSharedPreferences("woods_pro_license_vault", Context.MODE_PRIVATE)

    private val publicKey: PublicKey? by lazy {
        runCatching {
            val encoded = Base64.decode(PUBLIC_KEY_BASE64, Base64.DEFAULT)
            KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
        }.getOrNull()
    }

    val deviceInstallCode: String by lazy { computeDeviceInstallCode() }

    private val _isProUnlocked = MutableStateFlow(false)
    val isProUnlocked: StateFlow<Boolean> = _isProUnlocked.asStateFlow()

    init {
        _isProUnlocked.value = runCatching { verifySavedLicense() }.getOrDefault(false)
    }

    @SuppressLint("HardwareIds")
    private fun computeDeviceInstallCode(): String {
        val androidId = runCatching {
            Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        }.getOrNull().orEmpty()
        val isBrokenId = androidId.isBlank() || androidId.equals("9774d56d682e549c", ignoreCase = true)
        val stableId = if (isBrokenId) getOrCreateFallbackInstallId() else androidId
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(stableId.lowercase(Locale.ROOT).toByteArray(Charsets.UTF_8))
        val hex = "0123456789ABCDEF"
        val shortHash = buildString(8) {
            for (index in 0 until 4) {
                val value = digest[index].toInt() and 0xFF
                append(hex[value ushr 4])
                append(hex[value and 0x0F])
            }
        }
        return "WR-${shortHash.substring(0, 4)}-${shortHash.substring(4, 8)}"
    }

    private fun getOrCreateFallbackInstallId(): String {
        val existingId = prefs.getString(FALLBACK_INSTALL_ID_KEY, null)
        if (!existingId.isNullOrBlank()) return existingId

        val generatedId = UUID.randomUUID().toString()
        val saved = prefs.edit().putString(FALLBACK_INSTALL_ID_KEY, generatedId).commit()
        if (!saved) {
            prefs.edit().putString(FALLBACK_INSTALL_ID_KEY, generatedId).apply()
        }
        return generatedId
    }

    fun activateWithLicenseKey(inputKey: String): Boolean {
        val trimmedKey = inputKey.trim().uppercase(Locale.ROOT)
        if (!verifyLicenseSignature(deviceInstallCode, trimmedKey)) return false
        val committed = prefs.edit().putString(ACTIVATED_LICENSE_KEY, trimmedKey).commit()
        if (!committed) {
            prefs.edit().putString(ACTIVATED_LICENSE_KEY, trimmedKey).apply()
        }
        _isProUnlocked.value = true
        return true
    }

    private fun verifySavedLicense(): Boolean {
        val saved = prefs.getString(ACTIVATED_LICENSE_KEY, null) ?: return false
        return verifyLicenseSignature(deviceInstallCode, saved)
    }

    fun verifyLicenseSignature(installCode: String, licenseKey: String): Boolean {
        return runCatching {
            val key = publicKey ?: return false
            val normalizedInstallCode = installCode.trim().uppercase(Locale.ROOT)
            if (!INSTALL_CODE_PATTERN.matches(normalizedInstallCode)) return false

            val signatureBytes = decodeLicenseSignature(licenseKey) ?: return false
            val verifier = Signature.getInstance("SHA256withECDSA")
            verifier.initVerify(key)
            verifier.update(normalizedInstallCode.toByteArray(Charsets.US_ASCII))
            verifier.verify(signatureBytes)
        }.getOrDefault(false)
    }

    fun verifyLicenseKey(installCode: String, licenseKey: String): Boolean =
        verifyLicenseSignature(installCode, licenseKey)

    private fun decodeLicenseSignature(input: String): ByteArray? {
        return runCatching {
            val normalized = input.trim().uppercase(Locale.ROOT)
            if (!normalized.startsWith(LICENSE_PREFIX)) return null
            val encoded = normalized.substring(LICENSE_PREFIX.length)
                .filterNot { it == '-' || it.isWhitespace() }
            if (encoded.length !in MIN_SIGNATURE_BASE32_LENGTH..MAX_SIGNATURE_BASE32_LENGTH) return null

            var buffer = 0
            var bitCount = 0
            val decoded = ArrayList<Byte>(encoded.length * 5 / 8)
            for (character in encoded) {
                val value = BASE32_ALPHABET.indexOf(character)
                if (value < 0) return null
                buffer = (buffer shl 5) or value
                bitCount += 5
                if (bitCount >= 8) {
                    bitCount -= 8
                    decoded.add(((buffer ushr bitCount) and 0xFF).toByte())
                }
            }
            if (bitCount > 0 && (buffer and ((1 shl bitCount) - 1)) != 0) return null
            decoded.toByteArray()
        }.getOrNull()
    }

    private companion object {
        const val ACTIVATED_LICENSE_KEY = "activated_license_key"
        const val FALLBACK_INSTALL_ID_KEY = "fallback_install_id"
        const val LICENSE_PREFIX = "PRO-"
        const val MIN_SIGNATURE_BASE32_LENGTH = 108
        const val MAX_SIGNATURE_BASE32_LENGTH = 120
        const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
        const val PUBLIC_KEY_BASE64 = "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAElRoZUIgehBObRgMCce5GE4UjPZoRyL08s3Zp2ctoc4yhdOW/Smk6y2EYuGSbeHrCH59AbVt+EirwoVIU3MFM/A=="
        val INSTALL_CODE_PATTERN = Regex("^WR-[A-F0-9]{4}-[A-F0-9]{4}$")
    }
}
