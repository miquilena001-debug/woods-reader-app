package com.example.woodsreader.keymaker

import java.security.KeyFactory
import java.security.Signature
import java.security.spec.PKCS8EncodedKeySpec
import java.security.spec.X509EncodedKeySpec
import java.util.Locale
import java.util.Base64

object KeymakerEngine {
    private const val LICENSE_PREFIX = "PRO-"
    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"
    private const val MIN_SIGNATURE_BASE32_LENGTH = 108
    private const val MAX_SIGNATURE_BASE32_LENGTH = 120

    private const val PUBLIC_KEY_BASE64 =
        "MFkwEwYHKoZIzj0CAQYIKoZIzj0DAQcDQgAElRoZUIgehBObRgMCce5GE4UjPZoRyL08s3Zp2ctoc4yhdOW/Smk6y2EYuGSbeHrCH59AbVt+EirwoVIU3MFM/A=="

    // ADMIN-ONLY: clave privada embebida. Este APK NO se distribuye a clientes.
    private const val PRIVATE_KEY_BASE64 =
        "MIGHAgEAMBMGByqGSM49AgEGCCqGSM49AwEHBG0wawIBAQQgCVStr+/euJjcBCAZ" +
        "XRH5n6J+kY2Ad0xGfQ3MnKXH+/WhRANCAASVGhlQiB6EE5tGAwJx7kYThSM9mhHI" +
        "vTyzdmnZy2hzjKF05b9KaTrLYRi4ZJt4esIfn0BtW34SKvChUhTcwUz8"

    private val installCodePattern = Regex("^WR-[A-F0-9]{4}-[A-F0-9]{4}$")

    fun extractInstallCode(input: String): String {
        val trimmed = input.trim()
        val match = Regex("""WR-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}""", RegexOption.IGNORE_CASE)
            .find(trimmed)
        if (match != null) return match.value.uppercase(Locale.ROOT)

        val clean = trimmed.filter { it.isLetterOrDigit() }.uppercase(Locale.ROOT)
        val core = if (clean.startsWith("WR")) clean.removePrefix("WR") else clean
        return if (core.length == 8) {
            "WR-${core.substring(0, 4)}-${core.substring(4, 8)}"
        } else {
            trimmed.uppercase(Locale.ROOT)
        }
    }

    fun isValidInstallCode(code: String): Boolean =
        installCodePattern.matches(code.trim().uppercase(Locale.ROOT))

    /** Firma el installCode con la clave privada embebida y devuelve la licencia PRO-... */
    fun signInstallCode(installCode: String): String {
        val normalized = installCode.trim().uppercase(Locale.ROOT)
        require(isValidInstallCode(normalized)) { "Código de instalación inválido: $normalized" }
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey)
        signer.update(normalized.toByteArray(Charsets.US_ASCII))
        val sigBytes = signer.sign()
        val encoded = base32Encode(sigBytes)
        return "PRO-$encoded"
    }

    fun verifyKey(installCode: String, keyToVerify: String): Boolean {
        val normalizedCode = installCode.trim().uppercase(Locale.ROOT)
        if (!isValidInstallCode(normalizedCode)) return false
        val signatureBytes = decodeSignature(keyToVerify) ?: return false
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(publicKey)
        verifier.update(normalizedCode.toByteArray(Charsets.US_ASCII))
        return runCatching { verifier.verify(signatureBytes) }.getOrDefault(false)
    }

    fun buildShareMessage(installCode: String, proKey: String, buyerName: String = ""): String {
        val greeting = if (buyerName.isNotBlank()) "¡Hola $buyerName!" else "¡Hola!"
        return """
            🌲 Woods Reader Pro — Clave de Activación Oficial

            $greeting
            Aquí tienes tu clave para desbloquear Woods Reader Pro de por vida:

            👉 $proKey

            Instrucciones de activación:
            1. Abre Woods Reader en tu teléfono.
            2. Toca "Woods Pro" (en la barra superior o en Ajustes).
            3. Pega esta clave y pulsa "Activar Woods Pro".

            (Código de instalación asociado: $installCode)

            ¡Gracias por apoyar el desarrollo de Woods Reader!
        """.trimIndent()
    }

    // ── Internals ────────────────────────────────────────────────────────────

    private fun base32Encode(data: ByteArray): String {
        val sb = StringBuilder()
        var buffer = 0
        var bitsLeft = 0
        for (b in data) {
            buffer = (buffer shl 8) or (b.toInt() and 0xFF)
            bitsLeft += 8
            while (bitsLeft >= 5) {
                bitsLeft -= 5
                sb.append(BASE32_ALPHABET[(buffer ushr bitsLeft) and 0x1F])
            }
        }
        if (bitsLeft > 0) {
            sb.append(BASE32_ALPHABET[(buffer shl (5 - bitsLeft)) and 0x1F])
        }
        return sb.toString()
    }

    private fun decodeSignature(input: String): ByteArray? {
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
        return decoded.toByteArray()
    }

    private val publicKey by lazy {
        val encoded = Base64.getDecoder().decode(PUBLIC_KEY_BASE64)
        KeyFactory.getInstance("EC").generatePublic(X509EncodedKeySpec(encoded))
    }

    private val privateKey by lazy {
        val encoded = Base64.getDecoder().decode(PRIVATE_KEY_BASE64)
        KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(encoded))
    }
}
