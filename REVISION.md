# Diagnóstico y Auditoría Integral de Woods Reader

## Resumen Ejecutivo

Woods Reader es un lector de libros electrónicos (EPUB, PDF, CBZ/CBR) para Android desarrollado en Kotlin con Jetpack Compose, Readium, Room, Hilt y soporte de síntesis de voz neuronal local (Sherpa ONNX / Piper TTS). Cuenta además con una maqueta interactiva web en React + Vite (`woods-reader-web/`).

El estado del proyecto es funcional a nivel de interfaz visual y renderizado de lectura, pero presenta deficiencias críticas en la capa de persistencia, integración con el sistema operativo y ciclo de vida de sesiones, además de optimizaciones clave de empaquetado.

---

## 1. Problemas Críticos / Funciones Incompletas

| Componente | Archivo y Ubicación | Diagnóstico | Impacto |
|---|---|---|---|
| **Resaltado de texto** | `app/.../ui/reader/ReaderViewModel.kt:521-523` | `addHighlight(locator, color, text)` tiene el cuerpo vacío `{ }`. | La UI muestra la opción de resaltar, parece completar la acción, pero nunca persiste nada. `BookRepository.saveHighlight` existe pero no se llama. |
| **Importación de libros externos** | `app/.../MainActivity.kt:195-203` | `handleIncomingIntent` solo emite un `Log.d` y un `Toast`. No importa ni abre el archivo. | Al pulsar sobre un EPUB/PDF desde Files, WhatsApp o navegador, Woods Reader no carga el libro a pesar de tener los `intent-filter` declarados. |
| **Botón central "Leer"** | `app/.../ui/library/LibraryComponents.kt:815` | `lastBookId` es siempre `null` porque `updateLastReadBookId` nunca se invoca. | La barra inferior de navegación no permite retomar la última lectura. |
| **Crash en Pestaña NOTAS** | `app/.../ui/reader/ReaderAudioBottomSheet.kt:102` | `tabPositions[activeTab.ordinal]` lanza `IndexOutOfBoundsException` cuando `activeTab == ReaderTab.NOTES` (ordinal 2 sobre lista de 2 elementos). | Crash inmediato al intentar abrir las notas del libro desde el menú de lectura. |
| **Pérdida de sesiones de lectura** | `app/.../core/managers/ReadingSessionManager.kt:84` y `ReaderViewModel.onCleared()` | Se invoca `scope.launch` en `onCleared()`, pero `viewModelScope` ya está cancelado. | Las sesiones quedan abiertas con `endAt = null`, no se computa tiempo de lectura ni palabras leídas. |
| **Restauración de posición** | `app/.../ui/reader/ReaderViewModel.kt:651-718` | `validateAndRestoreLocator()` nunca se llama; `loadBook` solo hace `Locator.fromJSON` simple. Si falla, reinicia el libro al inicio. | Pérdida de posición en libros con locators complejos o cambios de paginación. |
| **Borrado agresivo de libros** | `app/.../service/BookScannerService.kt:436` | `cleanupOrphanedBooks` borra de Room si un solo `File.exists()` falla (e.g., tarjeta SD desacoplada). | Eliminación en cascada de marcadores, notas y progreso del usuario. |

---

## 2. Rendimiento y Eficiencia

1. **Sherpa TTS en el Hilo Principal (`Dispatchers.Main`):**
   - Inicialización del modelo ONNX y lectura de assets ocurre en el UI thread en `TtsManager.kt` / `SherpaTtsEngine.kt`, provocando congelamientos o riesgo de ANR al pulsar Play.
2. **Bucle reactivo de escaneo (`BookScannerService.kt`):**
   - `ContentObserver` configurado sobre todo el volumen externo con `notifyForDescendants` dispara re-escaneos globales con cada archivo descargado o foto guardada.
3. **Escaneo de portadas bloqueante en el arranque:**
   - `enrichMissingCovers()` en `LibraryViewModel.kt` procesa hasta 60 libros de manera pesada en el arranque compitiendo con la primera pintura visual.

---

## 3. Optimizaciones de Compilación y Tamaño de APK

- **Compresión de Assets (`app/build.gradle.kts:42`):**
  - La directiva `noCompress += listOf("onnx", "txt", "")` incluye una cadena vacía `""`, lo que desactiva la compresión de TODO el contenido del APK (DEX, .so, assets). Eliminar `""` reduce el tamaño del APK de ~318 MB a ~210 MB (~105 MB de ahorro directo).
- **Modelo de voz no utilizado:**
  - `assets/tts/vits-piper-es_MX-ald-medium/` (~60.3 MB) no está referenciado por ninguna clase (solo se usa `es_ES`). Eliminarlo ahorra 60 MB adicionales.
- **Logging de Timber deshabilitado:**
  - `Timber.plant()` nunca se ejecuta en `LectorApplication.kt`. Todos los logs de Timber son descartados en silencio.

---

## 4. Plan de Acción Recomendado

### Fase 0: Seguridad y Control de Versiones
- [x] Configurar `.gitignore` completo (excluir logs de build, volcados JVM, caches).
- [x] Crear el commit inicial en el repositorio Git local para proteger el código.

### Fase 1: Quick Wins (Ganancia Inmediata)
- [x] Eliminar `""` de `noCompress` en `app/build.gradle.kts`.
- [x] Remover assets huérfanos del modelo `es_MX` (~63.2 MB).
- [x] Inicializar `Timber.plant(Timber.DebugTree())` en `LectorApplication.kt`.
- [x] Corregir el índice en `ReaderAudioBottomSheet.kt` para evitar crash en NOTAS.
- [x] Implementar el cuerpo de `addHighlight` en `ReaderViewModel.kt`.
- [x] Conectar `updateLastReadBookId` para restaurar navegación en el botón "Leer".

### Fase 2: Integridad de Datos y Funciones Esenciales
- [x] Corregir el cierre de sesiones en `ReadingSessionManager` utilizando un scope desacoplado del ViewModel (`appScope`).
- [x] Prevenir contaminación cruzada de sesiones al alternar entre libros (`activeBookId`).
- [x] Implementar la importación real de libros en `MainActivity.kt` (`handleIncomingIntent` para EPUB/PDF/CBZ).
- [x] Conectar `validateAndRestoreLocator` en la carga del libro con cascada CFI -> href -> inicio.
- [x] Proteger `cleanupOrphanedBooks` contra desconexiones transitorias de almacenamiento y añadir interruptor cortacircuitos (>40%).

### Fase 3: Optimización de Rendimiento
- [x] Mover la carga y arranque de Sherpa TTS a `Dispatchers.IO` y `Dispatchers.Default`.
- [x] Filtrar el `ContentObserver` para evitar bucles de re-escaneo continuo en descargas no documentales.
- [x] Diferir `enrichMissingCovers` con retardo tras el inicio en IO para no competir con el primer render.
- [x] Mover el cálculo de `pub.positions()` a una corrutina de fondo para evitar congelamiento de 2-5s en splash de EPUBs grandes.
- [x] Eliminar llamadas I/O síncronas bloqueantes en el hilo de UI (`File.exists()` / `length()`) en componentes y listas de libros.
- [x] Migrar 14 flows en `ReadingStatsScreen` a `collectAsStateWithLifecycle()` y corregir métricas acumuladas de tiempo ahorrado.

### Fase 4: Especialización Radical (Solo EPUB y PDF)
- [x] Poda de dependencias obsoletas: eliminados `junrar`, `commonmark` y `okhttp` de `build.gradle.kts` y reglas ProGuard.
- [x] Restricción de `BookFormat` exclusivamente a `EPUB`, `PDF` y `UNKNOWN`.
- [x] Optimización de escáneres (`MediaStoreBookScanner`, `DeepFileSystemScanner`, `BookScannerService`): consultas SQL directas y filtros limitados estrictamente a `.epub` y `.pdf`.
- [x] Limpieza en `CoverExtractor` y `BookMetadataExtractor` eliminando ramas de extracción de cómics y formatos secundarios.

### Fase 5: Pipeline Semántico Unificado y Experiencia Premium
- [x] **Clasificador de Ruido (`NoiseClassifier`)**: Detección determinista de números de página (arábigos, romanos, formateados), avisos legales/copyright, URLs/correos y ornamentos visuales. Normalización de encabezados repetitivos con preservación de la `ñ` y acentos.
- [x] **Motor de Exclusión de Palabras (`WordExclusionEngine`)**: Filtrado con fronteras de palabra (`\b`), preservando palabras compuestas ("con" no mutila "continuar"), colapsando espacios y signos de puntuación huérfanos.
- [x] **Locutor de Figuras (`ImageAnnouncer`)**: Integración visual de imágenes y generación de anuncios verbales contextuales para TTS ("*Imagen: [descripción]*"), silenciables a voluntad.
- [x] **Formateador Tipográfico en Español (`SpanishTextFormatter`)**: Enlace de preposiciones y monosílabos huérfanos al final de línea (`\u00A0`), normalización de comillas y guiones de diálogo.
- [x] **Pipeline Orquestador (`ReadingPipeline`)**: Desacopla la extracción del renderizado; garantiza que el ruido nunca contamine las unidades de narración TTS.
- [x] **Sincronizador Bidireccional PDF (`PdfBookViewSynchronizer`)**: Mapeo exacto entre Vista Original (por página) y Vista Libro (por bloques semánticos y progreso continuo).

---

## 5. Resultados de Validación y Benchmarks

- **Pruebas unitarias y BDD (`:app:testDebugUnitTest`)**: **69/69 tests SUPERADOS exitosamente (100% de éxito)**.
  - Incluye escenarios BDD (`ReadingPipelineBddTest`) con especificaciones de comportamiento Given-When-Then.
  - Pruebas de estrés de 10,000 palabras en menos de 200 ms.
- **Compilación de empaquetado (`:app:assembleDebug`)**: Exitosa (código 0, 24s).
- **Control de versiones**: 7 commits estructurados en rama `master`.
