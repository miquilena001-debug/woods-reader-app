# Skill: TTS & Accessibility Auditor

## Nombre
tts-accessibility-auditor

## Descripción
Auditoría especializada del motor TTS (Sherpa ONNX offline + Android TTS fallback), accesibilidad, sincronización de resaltado (highlight), prosodia en español y reglas de exclusión de palabras para lectura de audiolibros.

## Divulgación Progresiva
- **Nivel 1 (nombre):** TTS & Accessibility Auditor
- **Nivel 2 (descripción):** Auditoría de TTS, accesibilidad y sincronización de lectura.
- **Nivel 3 (cuerpo completo):** Protocolo de auditoría de voz y accesibilidad.

## Instrucciones Completas

### 1. Motor TTS (Sherpa ONNX + System TTS)
- `TtsManager` debe exponer `enqueueNarrationUnits(List<TtsNarrationUnit>, startIndex)`.
- No re-filtrar strings grandes en `speakWithSherpa` / `speakSystemTts`; usar unidades pre-limpias del pipeline.
- Callbacks de progreso deben emitir `blockId` para sincronizar con UI.
- Liberar motores en `onCleared()` / `onDestroy()` para evitar fugas.
- `ReaderTtsService` en foreground con `MediaSession` para narración en segundo plano.

### 2. Sincronización de Resaltado (Highlight)
- `ReaderState` debe tener `activeNarrationBlockId` y `activeNarrationWordRange`.
- UI observa estos campos y aplica color de `TtsHighlightStyle`.
- Usar `derivedStateOf` para evitar recomposición de toda la pantalla.
- Funciona igual en modo EPUB (Readium) y modo PDF (PdfBookViewSynchronizer).

### 3. Reglas de Exclusión y Prosodia (Español)
- `WordExclusionEngine` filtra ruido: cabeceras, números de página, URLs, repetitivos.
- `SpanishTextFormatter`: comillas angulares (», «), guiones largos (—), espacios no separables (¿ ?, ¡ !).
- Tono diferenciado para anuncios de imagen (`isAnnouncement = true`) con pausa natural (delay 300ms).
- Silabeo automático CSS `hyphens: auto` para EPUB inyectado en Readium.

### 4. Accesibilidad (a11y)
- Todos los Composable tienen `contentDescription` significativo.
- Navegación por TalkBack funcionando en ReaderScreen, LibraryScreen, SettingsScreen.
- Tamaños de fuente escalables con `sp`, no `dp`.
- Contraste mínimo WCAG AA en tema HiddenForest.
- Botones de TTS (play/pause/stop) accesibles y con etiquetas de voz.

### 5. Auditoría por Escenario
```
## Auditoría TTS y Accesibilidad — [Escenario]

### Escenario
- [Ej: Narración EPUB con resaltado sincronizado]

### ✅ Cumple
- [Lista de verificación pasada]

### ❌ Fallos
- [Archivo.kt:línea] Descripción + corrección propuesta.

### Métricas de Voz
- Latencia de inicio: <X ms
- Unidades por minuto: X
- Errores de pronunciación detectados: X

### Listo para producción
Sí / No
```