package com.example.woodsreader.keymaker

import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

object KeymakerEngine {
    // Semilla ofuscada con XOR (0x5A) idéntica al motor ProLicenseManager de Woods Reader
    private val xorSeed = byteArrayOf(
        0x0D, 0x35, 0x35, 0x3E, 0x29, 0x05, 0x09, 0x2E,
        0x2F, 0x3E, 0x33, 0x35, 0x05, 0x17, 0x3B, 0x29,
        0x2E, 0x3F, 0x28, 0x05, 0x68, 0x6A, 0x68, 0x6C
    )

    private fun reconstructSecret(): ByteArray {
        return ByteArray(xorSeed.size) { idx ->
            (xorSeed[idx].toInt() xor 0x5A).toByte()
        }
    }

    /**
     * Extrae y normaliza el código de instalación a partir de cualquier texto arbitrario
     * (por ejemplo un mensaje de WhatsApp: "Mi código es WR-1A2B-3C4D gracias").
     */
    fun extractInstallCode(input: String): String {
        val trimmed = input.trim()
        val regex = Regex("""WR-[A-Za-z0-9]{4}-[A-Za-z0-9]{4}""", RegexOption.IGNORE_CASE)
        val match = regex.find(trimmed)
        if (match != null) return match.value.uppercase()

        // Si el usuario escribe caracteres limpios sin guiones (ej: "1A2B3C4D" o "WR1A2B3C4D")
        val clean = trimmed.filter { it.isLetterOrDigit() }.uppercase()
        val core = if (clean.startsWith("WR")) clean.removePrefix("WR") else clean
        return if (core.length == 8) {
            "WR-${core.substring(0, 4)}-${core.substring(4, 8)}"
        } else {
            trimmed.uppercase()
        }
    }

    /**
     * Valida si el código cumple el formato estándar WR-XXXX-XXXX en hexadecimal.
     */
    fun isValidInstallCode(code: String): Boolean {
        val regex = Regex("""^WR-[A-F0-9]{4}-[A-F0-9]{4}$""")
        return regex.matches(code.trim().uppercase())
    }

    /**
     * Genera la clave de licencia Pro esperada para un dispositivo con HMAC-SHA256.
     * Salida: "PRO-XXXX-XXXX-XXXX" (12 caracteres hex divididos en 3 bloques de 4).
     */
    fun generateProKey(installCode: String): String {
        val normalizedId = installCode.trim().uppercase()
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(reconstructSecret(), "HmacSHA256"))
        val signature = mac.doFinal(normalizedId.toByteArray(Charsets.UTF_8))
        val hex = signature.joinToString("") { "%02X".format(it) }.take(12)
        return "PRO-${hex.substring(0, 4)}-${hex.substring(4, 8)}-${hex.substring(8, 12)}"
    }

    /**
     * Verifica si una clave ingresada es auténtica para un código de instalación dado.
     */
    fun verifyKey(installCode: String, keyToVerify: String): Boolean {
        val cleanKey = keyToVerify.trim().uppercase()
        val expected = generateProKey(installCode)
        return cleanKey == expected
    }

    /**
     * Construye un mensaje listo para enviar al usuario por WhatsApp, Telegram, correo o SMS.
     */
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
}
