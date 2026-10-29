# Configuración y Optimización de Release con R8 y ABI Splits

> **Estado: EJECUTADO**
> - **Evidencia:** Configuración `release` optimizada con R8, `minifyEnabled = true`, `shrinkResources = true`, `abiFilters` y reglas consolidadas en `app/proguard-rules.pro` y `app/build.gradle.kts` (commits `fb8740e`, `7cd879c` y `6f70865`).

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Configurar la variante de compilación `release` en [app/build.gradle.kts](file:///c:/Users/Orbit/Downloads/woods%20reader/app/build.gradle.kts) y endurecer [app/proguard-rules.pro](file:///c:/Users/Orbit/Downloads/woods%20reader/app/proguard-rules.pro) para ofuscación y reducción de tamaño (`minifyEnabled`, `shrinkResources`), optimizar la distribución para arquitecturas móviles reales (`arm64-v8a` y `armeabi-v7a` opcional) excluyendo `x86`/`x86_64`, y verificar con `assembleRelease` que R8 no rompa JNI de Sherpa-ONNX, Readium ni Room.

---

### File Structure & Changes

| Archivo | Responsabilidad / Cambio |
|---|---|
| [`app/build.gradle.kts`](file:///c:/Users/Orbit/Downloads/woods%20reader/app/build.gradle.kts) | Configurar `splits.abi` (o `ndk.abiFilters`), habilitar `isMinifyEnabled = true`, `isShrinkResources = true`, optimizador `proguard-android-optimize.txt` en `buildTypes.release`. |
| [`app/proguard-rules.pro`](file:///c:/Users/Orbit/Downloads/woods%20reader/app/proguard-rules.pro) | Consolidar y blindar directivas `-keep` para Sherpa-ONNX (JNI/C++ bindings), Readium Toolkit (parsers y modelos), Room DB, Hilt DI, Coroutines y Serialization. |

---

## Tasks

### Task 1: Reforzar las Reglas de Proguard/R8 para JNI, Readium y Room

**Files:**
- Modify: [`app/proguard-rules.pro`](file:///c:/Users/Orbit/Downloads/woods%20reader/app/proguard-rules.pro)

- [ ] **Step 1:** Abrir [app/proguard-rules.pro](file:///c:/Users/Orbit/Downloads/woods%20reader/app/proguard-rules.pro) y verificar las reglas actuales.
- [ ] **Step 2:** Asegurar que se conserven los métodos nativos y bindings JNI para Sherpa-ONNX:
  ```proguard
  -keepclasseswithmembernames class * {
      native <methods>;
  }
  -keep class com.k2fsa.sherpa.onnx.** { *; }
  -dontwarn com.k2fsa.sherpa.onnx.**
  ```
- [ ] **Step 3:** Asegurar que Readium (`org.readium.**` y `org.readium.r2.**`) mantenga todas sus interfaces, modelos y serializadores:
  ```proguard
  -keep class org.readium.** { *; }
  -dontwarn org.readium.**
  -keep class org.readium.r2.** { *; }
  -dontwarn org.readium.r2.**
  ```
- [ ] **Step 4:** Verificar reglas para Room Entities, DAOs y Database:
  ```proguard
  -keep class * extends androidx.room.RoomDatabase
  -keep @androidx.room.Entity class * { *; }
  -keep @androidx.room.Dao class * { *; }
  -keepclassmembers class * extends androidx.room.RoomDatabase {
      <methods>;
  }
  -dontwarn androidx.room.paging.**
  ```
- [ ] **Step 5:** Añadir soporte explícito para `java.time.*` desugared y tipos de reflexión.

---

### Task 2: Configurar `buildTypes.release` y Optimización de ABI en `app/build.gradle.kts`

**Files:**
- Modify: [`app/build.gradle.kts`](file:///c:/Users/Orbit/Downloads/woods%20reader/app/build.gradle.kts)

- [ ] **Step 1:** Verificar que `isMinifyEnabled = true` y `isShrinkResources = true` estén presentes en `buildTypes.release`.
- [ ] **Step 2:** Configurar la generación de APKs de arquitectura:
  - Mantener `ndk.abiFilters += listOf("arm64-v8a", "armeabi-v7a")` para descartar completamente `x86` y `x86_64` (ahorrando decenas de MBs en librerías nativas ONNX/C++).
  - Configurar `splits.abi` para habilitar división opcional por APK si se desea un binario exclusivo de `arm64-v8a`:
  ```kotlin
  splits {
      abi {
          isEnable = false // O true si se desean APKs separados por ABI
          reset()
          include("arm64-v8a", "armeabi-v7a")
          isUniversalApk = true
      }
  }
  ```
- [ ] **Step 3:** Comprobar que `signingConfigs.release` use la clave existente en `keystore/woodsreader.jks`.

---

### Task 3: Verificación con R8 (`:app:assembleRelease`)

**Files:**
- Test / Build target: `.\gradlew.bat :app:assembleRelease`

- [ ] **Step 1:** Ejecutar `.\gradlew.bat :app:assembleRelease` para disparar la tarea `minifyReleaseWithR8` y `optimizeReleaseResources`.
- [ ] **Step 2:** Inspeccionar la salida para verificar que R8 no lance advertencias fatales ni errores de símbolos eliminados.
- [ ] **Step 3:** Confirmar que se genera el archivo APK firmado en `app/build/outputs/apk/release/app-release.apk`.
- [ ] **Step 4:** Medir el tamaño final del APK y verificar la reducción sustancial de peso.
