package com.example.woodsreader.core.license

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class ProLicenseManagerTest {

    private val context: Context = mockk(relaxed = true)
    private val prefs: SharedPreferences = mockk(relaxed = true)
    private val editor: SharedPreferences.Editor = mockk(relaxed = true)
    private val storageMap = mutableMapOf<String, String>()

    @Before
    fun setup() {
        storageMap.clear()
        every { context.getSharedPreferences("woods_pro_license_vault", Context.MODE_PRIVATE) } returns prefs
        every { prefs.getString(any(), any()) } answers {
            val key = firstArg<String>()
            val def = secondArg<String?>()
            storageMap[key] ?: def
        }
        every { prefs.edit() } returns editor
        every { editor.putString(any(), any()) } answers {
            val key = firstArg<String>()
            val value = secondArg<String>()
            storageMap[key] = value
            editor
        }
        every { editor.commit() } returns true
        every { editor.apply() } answers { }

        mockkStatic(Settings.Secure::class)
        mockkStatic(android.util.Base64::class)
        every { android.util.Base64.decode(any<String>(), any()) } answers {
            java.util.Base64.getDecoder().decode(firstArg<String>())
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun testBrokenAndroidId_fallsBackToUniqueUuid() {
        every { Settings.Secure.getString(any(), Settings.Secure.ANDROID_ID) } returns "9774d56d682e549c"

        val manager = ProLicenseManager(context)
        val installCode1 = manager.deviceInstallCode
        assertTrue("Debe empezar con WR-", installCode1.startsWith("WR-"))
        assertTrue("Debe haber guardado el fallback_install_id", storageMap.containsKey("fallback_install_id"))

        val manager2 = ProLicenseManager(context)
        assertEquals("Debe ser idéntico al persistirse", installCode1, manager2.deviceInstallCode)
    }

    @Test
    fun testValidSignature_activatesTargetDeviceSuccessfully() {
        // device_test_fixed genera el código WR-6557-3366
        every { Settings.Secure.getString(any(), Settings.Secure.ANDROID_ID) } returns "device_test_fixed"

        val manager = ProLicenseManager(context)
        assertEquals("WR-6557-3366", manager.deviceInstallCode)
        assertFalse("Inicialmente bloqueado", manager.isProUnlocked.value)

        // Clave legítima firmada con la clave privada ECDSA para WR-6557-3366
        val validKey = "PRO-GBCAEIAMDGTGCAD6OT5YQU5T5LSMA3GTNCGTNQKNUCZV2ZZD7NVHJAXA5UBCAJKMAFI6XVU4SERUQ6VQIUYUDYLSHDHBOS7EKCSLTKFWNI37HPGU"
        val activated = manager.activateWithLicenseKey(validKey)

        assertTrue("La clave válida debe activar Pro", activated)
        assertTrue("El estado en memoria debe ser Pro", manager.isProUnlocked.value)

        // Verificamos persistencia: un nuevo manager debe arrancar activado
        val newManagerSession = ProLicenseManager(context)
        assertTrue("La licencia guardada debe verificar en la nueva sesión", newManagerSession.isProUnlocked.value)
    }

    @Test
    fun testSignatureMismatch_failsOnDifferentDevice() {
        // device_other_beta genera el código WR-DAC1-3B37
        every { Settings.Secure.getString(any(), Settings.Secure.ANDROID_ID) } returns "device_other_beta"

        val manager = ProLicenseManager(context)
        assertEquals("WR-DAC1-3B37", manager.deviceInstallCode)

        // Intentar usar la clave firmada para WR-6557-3366 en este otro dispositivo
        val keyFromAnotherDevice = "PRO-GBCAEIAMDGTGCAD6OT5YQU5T5LSMA3GTNCGTNQKNUCZV2ZZD7NVHJAXA5UBCAJKMAFI6XVU4SERUQ6VQIUYUDYLSHDHBOS7EKCSLTKFWNI37HPGU"
        val activated = manager.activateWithLicenseKey(keyFromAnotherDevice)

        assertFalse("La clave de otro dispositivo debe ser rechazada", activated)
        assertFalse(manager.isProUnlocked.value)
    }

    @Test
    fun testTamperedKey_failsGracefullyWithoutException() {
        every { Settings.Secure.getString(any(), Settings.Secure.ANDROID_ID) } returns "device_test_fixed"
        val manager = ProLicenseManager(context)

        // Clave legítima con el último carácter alterado
        val tamperedKey = "PRO-GBCAEIAMDGTGCAD6OT5YQU5T5LSMA3GTNCGTNQKNUCZV2ZZD7NVHJAXA5UBCAJKMAFI6XVU4SERUQ6VQIUYUDYLSHDHBOS7EKCSLTKFWNI37HPGV"
        assertFalse("Clave con 1 bit alterado debe fallar", manager.activateWithLicenseKey(tamperedKey))

        // Claves malformadas
        assertFalse(manager.activateWithLicenseKey("INVALID-KEY-123"))
        assertFalse(manager.activateWithLicenseKey("PRO-SHORT"))
        assertFalse(manager.activateWithLicenseKey(""))
        assertFalse(manager.isProUnlocked.value)
    }

    @Test
    fun testVerifyLicenseSignature_directCall() {
        every { Settings.Secure.getString(any(), Settings.Secure.ANDROID_ID) } returns "device_test_fixed"
        val manager = ProLicenseManager(context)
        val validCode = "WR-6557-3366"
        val validKey = "PRO-GBCAEIAMDGTGCAD6OT5YQU5T5LSMA3GTNCGTNQKNUCZV2ZZD7NVHJAXA5UBCAJKMAFI6XVU4SERUQ6VQIUYUDYLSHDHBOS7EKCSLTKFWNI37HPGU"

        // Direct public API call
        assertTrue("verifyLicenseSignature debe validar la firma correcta", manager.verifyLicenseSignature(validCode, validKey))
        assertFalse("verifyLicenseSignature debe fallar si el código no coincide", manager.verifyLicenseSignature("WR-0000-0000", validKey))
        assertFalse("verifyLicenseSignature debe fallar con código malformado", manager.verifyLicenseSignature("INVALID", validKey))
    }

    @Test
    fun testNullAndEmptyAndroidId_generatePersistentUuid() {
        every { Settings.Secure.getString(any(), Settings.Secure.ANDROID_ID) } returns null
        val managerNull = ProLicenseManager(context)
        val codeNull = managerNull.deviceInstallCode
        assertTrue("Debe empezar con WR- ante null", codeNull.startsWith("WR-"))
        val uuidSaved = storageMap["fallback_install_id"]
        assertTrue("Debe haber guardado UUID", !uuidSaved.isNullOrBlank())

        // Nueva sesión con id vacío debe reutilizar el UUID guardado
        every { Settings.Secure.getString(any(), Settings.Secure.ANDROID_ID) } returns ""
        val managerEmpty = ProLicenseManager(context)
        assertEquals("Debe reutilizar el UUID persistido", codeNull, managerEmpty.deviceInstallCode)
    }

    @Test
    fun testNoSymmetricSecretExposedViaReflection() {
        val methods = ProLicenseManager::class.java.declaredMethods.map { it.name }
        val fields = ProLicenseManager::class.java.declaredFields.map { it.name }

        assertFalse("No debe existir generateExpectedKeyForDevice", methods.contains("generateExpectedKeyForDevice"))
        assertFalse("No debe existir reconstructSecret", methods.contains("reconstructSecret"))
        assertFalse("No debe existir campo xorSeed", fields.any { it.contains("xorSeed", ignoreCase = true) })
        assertFalse("No debe existir campo secret", fields.any { it.contains("secret", ignoreCase = true) })
    }
}
