# Blindaje Asimétrico ECDSA y Robustecimiento de ProLicenseManager

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:test-driven-development and superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Blindar completamente `ProLicenseManager.kt` contra ingeniería inversa (reemplazo de secreto simétrico por firma asimétrica ECDSA P-256), eliminar colisiones en dispositivos sin ANDROID_ID, desacoplar I/O síncrono del constructor, y validar su comportamiento con pruebas unitarias TDD.

---

### Task 1: Crear prueba unitaria TDD para ProLicenseManager (Fase RED)

- [ ] Crear el archivo de test `app/src/test/java/com/example/woodsreader/core/license/ProLicenseManagerTest.kt`
- [ ] Implementar casos de prueba:
  - `validSignature_unlocksProLicense()`: Una firma generada por la clave privada para `WR-4F9A-82C1` activa con éxito.
  - `signatureMismatch_returnsFalse()`: La firma de `WR-4F9A-82C1` aplicada a `WR-11D3-50A8` devuelve `false`.
  - `tamperedOrMalformedKey_returnsFalseWithoutException()`: Claves con un carácter alterado o formato inválido devuelven `false` sin lanzar excepción.
  - `fallbackDeviceUuid_isUniqueAndPersisted()`: Si ANDROID_ID es nulo o el ID roto `"9774d56d682e549c"`, genera y persiste un UUID estable.
- [ ] Ejecutar `.\gradlew.bat testDebugUnitTest --tests com.example.woodsreader.core.license.ProLicenseManagerTest` y verificar comportamiento inicial.

---

### Task 2: Implementar mejoras en ProLicenseManager.kt (Fase GREEN)

- [ ] Verificar filtrado de ANDROID_ID roto `"9774d56d682e549c"` además de nulo/vacío para activar `getOrCreateFallbackInstallId()`.
- [ ] Garantizar inicialización segura de `_isProUnlocked` sin bloquear el hilo principal y con tolerancia total a excepciones en `runCatching`.
- [ ] Confirmar que no exista ningún método de generación de claves en el cliente Android.
- [ ] Ejecutar `.\gradlew.bat testDebugUnitTest --tests com.example.woodsreader.core.license.ProLicenseManagerTest` hasta obtener verde completo.

---

### Task 3: Verificación final y auditoría de código

- [ ] Ejecutar la suite completa de tests `./gradlew.bat testDebugUnitTest`.
- [ ] Compilar el APK debug y verificar que compile limpiamente: `.\gradlew.bat :app:assembleDebug`.
