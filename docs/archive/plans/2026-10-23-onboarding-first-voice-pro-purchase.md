# Plan: Flujo de Conversión de Primer Uso y Verificación Release

> **Estado: EJECUTADO**
> - **Evidencia:** Implementado en `app/src/main/java/com/example/woodsreader/ui/reader/ReaderTtsMacroWindow.kt` (Hero Banner y descarga primera voz), `app/src/main/java/com/example/woodsreader/ui/common/WoodsProUnlockDialog.kt` (botones directos de compra WhatsApp y Telegram con `deviceInstallCode`), y `app/src/main/java/com/example/woodsreader/ui/onboarding/OnboardingScreen.kt` (commits `868dc2a`, `6f70865` y `afd675d`).

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar la guía automática de primera voz IA (para evitar que el usuario escuche la voz robótica nativa sin saber que existen voces VITS HD offline), dotar a `WoodsProUnlockDialog.kt` de botones de compra directa con mensaje pre-llenado para WhatsApp y Telegram enviando el ID de instalación, y verificar la compilación release.

---

### File Structure & Changes

| Archivo | Responsabilidad / Cambio |
|---|---|
| [`app/src/main/java/com/example/woodsreader/ui/reader/ReaderTtsMacroWindow.kt`](file:///c:/Users/Orbit/Downloads/woods%20reader/app/src/main/java/com/example/woodsreader/ui/reader/ReaderTtsMacroWindow.kt) | 1. Detectar si `isSherpaVoiceDownloaded` es falso para todas las voces cuando el usuario pulsa Play o abre el estudio.<br>2. Si no hay voces instaladas, abrir automáticamente la hoja de voces (`showLanguageSheet = true`, pestaña 0).<br>3. Mostrar en la parte superior de la pestaña de voces un Hero Banner destacado con gradiente Emerald: *"🎙️ Descarga tu primera Voz de Estudio IA (60 MB) para escuchar y exportar tus libros con calidad humana 100% sin internet"*, con botones de descarga rápida de **Claudia HD** (`es_MX-claude-high`) y **David HD** (`es_ES-davefx-medium`). |
| [`app/src/main/java/com/example/woodsreader/ui/common/WoodsProUnlockDialog.kt`](file:///c:/Users/Orbit/Downloads/woods%20reader/app/src/main/java/com/example/woodsreader/ui/common/WoodsProUnlockDialog.kt) | Añadir dos botones de compra directa (**Telegram @WoodsStudio** y **WhatsApp**) con intents `ACTION_VIEW` y URLs con mensaje URL-encoded conteniendo el `deviceInstallCode` para comprar la licencia vitalicia por $3 USD. |
| [`app/build.gradle.kts`](file:///c:/Users/Orbit/Downloads/woods%20reader/app/build.gradle.kts) y [`app/proguard-rules.pro`](file:///c:/Users/Orbit/Downloads/woods%20reader/app/proguard-rules.pro) | Verificar que `isMinifyEnabled = true`, `isShrinkResources = true`, `abiFilters` y reglas de `com.k2fsa.sherpa.onnx.**`, `org.readium.r2.**` y `androidx.room.**` estén vigentes. |

---

## Tasks

### Task 1: Guía Automática de Primera Voz IA (Anti-Voz Robótica)

**Files:**
- Modify: [`app/src/main/java/com/example/woodsreader/ui/reader/ReaderTtsMacroWindow.kt`](file:///c:/Users/Orbit/Downloads/woods%20reader/app/src/main/java/com/example/woodsreader/ui/reader/ReaderTtsMacroWindow.kt)

- [ ] **Step 1:** Comprobar si hay voces Sherpa descargadas con `SherpaVoiceOption.entries.any { isSherpaVoiceDownloaded(it) }`.
- [ ] **Step 2:** Al pulsar el botón central Play en `ReaderTtsMacroWindow`: si `useSherpaTts` está activo y no hay ninguna voz descargada, cambiar a `showLanguageSheet = true` (pestaña 0) para guiar al usuario a obtener su primera voz en vez de fallar o sonar mudo.
- [ ] **Step 3:** En la Pestaña 0 (`🎙️ Voz e Idioma`) de `ReaderTtsMacroWindow`: si `!hasAnyDownloadedVoice`, desplegar un Banner Emerald Card destacado:
  - Título y descripción: *"🎙️ Descarga tu primera Voz de Estudio IA (60 MB) para escuchar y exportar tus libros con calidad humana 100% sin internet"*.
  - Fila con 2 botones de 1 toque:
    - **Claudia HD (Latam)**: activa descarga de `SherpaVoiceOption.CLAUDE_MX`.
    - **David HD (España)**: activa descarga de `SherpaVoiceOption.DAVEFX_ES`.
- [ ] **Step 4:** Asegurar respuesta visual inmediata mostrando el indicador de progreso de descarga si una de ellas se está descargando.

---

### Task 2: Botones de Compra Directa con Mensaje Pre-llenado en `WoodsProUnlockDialog.kt`

**Files:**
- Modify: [`app/src/main/java/com/example/woodsreader/ui/common/WoodsProUnlockDialog.kt`](file:///c:/Users/Orbit/Downloads/woods%20reader/app/src/main/java/com/example/woodsreader/ui/common/WoodsProUnlockDialog.kt)

- [ ] **Step 1:** Preparar el texto del mensaje pre-llenado:
  `"¡Hola! Quiero adquirir mi licencia vitalicia de Woods Reader PRO ($3 USD). Mi ID de dispositivo es: $installCode"`
- [ ] **Step 2:** Codificar el mensaje con `URLEncoder.encode(msg, "UTF-8")`.
- [ ] **Step 3:** Crear URLs de enlace directo:
  - **WhatsApp:** `https://api.whatsapp.com/send?text=$encodedMsg`
  - **Telegram:** `https://t.me/WoodsStudio?text=$encodedMsg` (y fallback `https://t.me/share/url?url=&text=$encodedMsg`)
- [ ] **Step 4:** En la tarjeta del `ID de Dispositivo (WR-XXXX-XXXX)`, debajo del botón de copiar, agregar una fila con 2 botones estilizados:
  - Botón Telegram (`Color(0xFF229ED9)`) con icono y etiqueta *"Pedir por Telegram"*.
  - Botón WhatsApp (`Color(0xFF25D366)`) con icono y etiqueta *"Pedir por WhatsApp"*.
- [ ] **Step 5:** Envolver el lanzamiento del Intent en un `runCatching` con Toast de fallback si el usuario no tiene instalada la app correspondiente.

---

### Task 3: Verificación de Compilación y Configuración Release

**Files:**
- Test target: `.\gradlew.bat :app:compileReleaseKotlin` y `.\gradlew.bat testDebugUnitTest`

- [ ] **Step 1:** Ejecutar `.\gradlew.bat :app:compileReleaseKotlin` y verificar 0 errores de compilación.
- [ ] **Step 2:** Ejecutar `.\gradlew.bat testDebugUnitTest` para revalidar que los tests unitarios pasen en verde.
- [ ] **Step 3:** Confirmar que no hay archivos temporales de scratch pendientes.
