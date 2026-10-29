# Skill: Build & APK Optimizer

## Nombre
build-apk-optimizer

## Descripción
Optimiza el proceso de compilación Gradle, reduce el tamaño del APK, configura R8/ProGuard, firma con keystore y ejecuta las verificaciones relevantes y reporta tiempos y tamaños medidos, sin umbrales no confirmados.

## Divulgación Progresiva
- **Nivel 1 (nombre):** Build & APK Optimizer
- **Nivel 2 (descripción):** Optimización de builds Gradle y tamaño de APK.
- **Nivel 3 (cuerpo completo):** Protocolo de optimización de compilación.

## Instrucciones Completas

### 1. Gradle y Compilación
- Comando obligatorio: `.\gradlew.bat :app:testDebugUnitTest` (salida 0).
- Comando de build: `.\gradlew.bat :app:assembleDebug` (reportar el tiempo medido, sin umbral fijo).
- Usar KSP para Room (generación de esquemas y DAOs).
- No agregar dependencias nuevas (guardrail inviolable del proyecto).
- Revisar `gradle/libs.versions.toml` para versiones actualizadas sin romper compatibilidad.

### 2. Tamaño del APK (medicion real sin umbral fijo)
- `minifyEnabled true` en release con R8/ProGuard.
- Revisar recursos no utilizados (imágenes, idiomas, vectores).
- Comprimir assets de audio/modelos ONNX si es posible.
- Verificar que `icon.png` y otros assets estén optimizados.
- Analizar el APK con Android Studio APK Analyzer para entender su composición; acordar cualquier umbral con el usuario.

### 3. Firma y Keystore
- Usar `keystore/woodsreader.jks` y `keystore.properties` para firmar.
- Nunca commitear `keystore.properties` con contraseñas reales en texto plano (usar variables de entorno).
- `local.properties` para SDK local (no commitear).

### 4. Multidex y Optimización de Código
- Verificar que no haya clases/métodos innecesarios en el DEX final.
- R8 rules para preservar clases de Readium, Hilt, Room, Media3, Sherpa ONNX.
- Evitar reflejo excesivo que impida tree-shaking.

### 5. Salida de Optimización de Build
```
## Optimización de Build — [Versión]

### Tiempos
- Compilación debug: registrar el tiempo medido, sin meta fija.
- Pruebas unitarias: X segundos

### Tamaño APK
- APK debug: reportar el tamaño medido; no hay umbral de tamaño vigente verificado.
- APK release: X MB

### Acciones Tomadas
- [Lista de cambios: R8 reglas, recursos eliminados, dependencias revisadas]

### Validación
- .\gradlew.bat :app:assembleDebug → salida 0
- .\gradlew.bat :app:testDebugUnitTest → salida 0
```