# Paridad Total en Modo PDF y Convertidor PDF a EPUB Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Proporcionar paridad funcional total al modo PDF en Woods Reader (búsqueda streaming en-libro, exportación por lotes .M4A con curación de párrafos, detección automática de capítulos reales en TOC, auto-scroll y teleprompter en Reflow, resaltado Emerald Liquid Glass) e implementar el convertidor instantáneo "⚡ Convertir PDF a EPUB Inteligente" con integración en la Biblioteca.

**Architecture:** 
1. `PdfParagraphHealer`: Motor puro de curación tipográfica que deshace cortes artificiales de renglón (line-unwrapping), purga números de página/pies de página aislados y escanea encabezados de novelas (`Chapter \d+`, `Capítulo \d+`, etc.) para generar TOC virtual de capítulos reales.
2. `ReaderIntelligenceAndExportCoordinator` + `PdfSearchHelper`: Búsqueda streaming en segundo plano sobre PDFs emitiendo `FastInBookSearchHit` con navegación O(1) a página/ítem, y exportación por lotes a `.M4A` de rangos de páginas/capítulos con `ChapterAudioExporter`.
3. `ReaderReflowOverlay` + `ReaderScreen`: Resaltado Emerald Liquid Glass en párrafo TTS activo, soporte nativo de Teleprompter WPM a 60 FPS sobre `LazyListState` de Reflow, y auto-avance entre páginas PDF.
4. `PdfToEpubConverter` + `BookOptionsBottomSheet` + `LibraryViewModel`: Extracción de páginas PDF, estructuración de capítulos XHTML mediante `LocalEpubBuilder`, generación de EPUB nativo con portada e importación automática en Room vía `BookScannerService`.

**Tech Stack:** Kotlin, Jetpack Compose, Readium PDF Navigator, Apache PDFBox (Android port `com.tom_roush.pdfbox`), Coroutines & Flow, Hilt, Room.

**Spec:** Prompt de Usuario PARTE 74 (Paridad Total de Funciones en Modo PDF y Convertidor PDF-a-EPUB).

## Global Constraints
- Intocables: `ChapterScrollDetector.kt`, CSS de paginado de `ReaderExtensions.kt`, `AudioFocus` de `TtsManager.kt`, firmas públicas de `ReaderScreen.kt`.
- Prohibido el uso de `// ...` o placeholders incompletos en código.
- Cada commit debe compilar con `:app:compileDebugKotlin` y pasar al 100% `:app:testDebugUnitTest` (250+ casos de test).
- Cada archivo creado o modificado debe sincronizarse en `dona.md` y verificar `check_dona.ps1`.

## Review Focus
1. **Line-unwrapping no destructivo:** No unir líneas que realmente sean fin de párrafo (ej. terminadas en `.` o diálogo con raya `—`).
2. **Detección tolerante de capítulos:** Detectar patrones de novelas en inglés y español (`Chapter 1`, `Capítulo 12`, `Cap. 3`, `0001 - Title`, `Episode 5`) sin falsos positivos con menciones dentro de oraciones.
3. **Cancelación limpia de búsqueda:** Si el usuario cancela o escribe otra búsqueda en PDF, las corrutinas de PDFTextStripper deben abortar inmediatamente (`ensureActive`).
4. **Teleprompter WPM suave en Reflow:** Auto-scroll continuo sin tirones que respete pausa táctil (1200ms) y velocidad configurable.
5. **Conversión atómica PDF a EPUB:** Si el proceso falla o se cancela, no dejar archivos parciales en disco ni registros corruptos en Room.

---

## Tasks

- [ ] **Task 1: Crear `PdfParagraphHealer` y extractor de TOC virtual de capítulos**
  - **Files:**
    - Create: `app/src/main/java/com/example/woodsreader/core/pdf/PdfParagraphHealer.kt`
    - Test: `app/src/test/java/com/example/woodsreader/core/pdf/PdfParagraphHealerTest.kt`
  - **Deliverable:** Clase pura de curación de texto (line-unwrapping, remoción de encabezados/números de página y detección regex de capítulos) con cobertura unitaria completa.

- [ ] **Task 2: Integrar TOC Virtual de Capítulos en `LoadBookFastUseCase` y `ReaderViewModel`**
  - **Files:**
    - Modify: `app/src/main/java/com/example/woodsreader/domain/reader/LoadBookFastUseCase.kt`
    - Modify: `app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt`
  - **Deliverable:** Libros PDF sin bookmarks nativos obtienen automáticamente una Tabla de Contenidos estructurada con los capítulos detectados en `TocBottomSheet` y saltos de capítulo en el lector.

- [ ] **Task 3: Adaptar Búsqueda En-Libro Streaming y Exportación .M4A por Lotes a PDF en `ReaderIntelligenceAndExportCoordinator`**
  - **Files:**
    - Modify: `app/src/main/java/com/example/woodsreader/domain/reader/ReaderIntelligenceAndExportCoordinator.kt`
    - Modify: `app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt`
    - Test: `app/src/test/java/com/example/woodsreader/domain/reader/PdfIntelligenceAndExportTest.kt`
  - **Deliverable:** Búsqueda streaming de texto funcional en PDF con navegación a página de resultado y exportación por lotes de rangos de páginas/capítulos a archivo `.m4a` unificado.

- [ ] **Task 4: Paridad Visual y Teleprompter en PDF Reflow (`ReaderReflowOverlay` y `ReaderScreen`)**
  - **Files:**
    - Modify: `app/src/main/java/com/example/woodsreader/ui/reader/ReaderReflowOverlay.kt`
    - Modify: `app/src/main/java/com/example/woodsreader/ui/reader/ReaderScreen.kt`
  - **Deliverable:** Contenedor Emerald Liquid Glass en párrafo TTS activo, soporte de Teleprompter WPM a 60 FPS sobre `LazyListState` de Reflow, y avance automático al completar página.

- [ ] **Task 5: Implementar Convertidor `PdfToEpubConverter` y Acción en la Hoja de Detalles de la Biblioteca**
  - **Files:**
    - Create: `app/src/main/java/com/example/woodsreader/core/pdf/PdfToEpubConverter.kt`
    - Modify: `app/src/main/java/com/example/woodsreader/ui/library/BookOptionsBottomSheet.kt`
    - Modify: `app/src/main/java/com/example/woodsreader/ui/library/LibraryViewModel.kt`
    - Modify: `app/src/main/java/com/example/woodsreader/ui/library/LibraryScreen.kt`
    - Create: `app/src/main/java/com/example/woodsreader/ui/library/PdfToEpubProgressDialog.kt`
    - Test: `app/src/test/java/com/example/woodsreader/core/pdf/PdfToEpubConverterTest.kt`
  - **Deliverable:** Tarjeta destacada verde esmeralda `⚡ Convertir PDF a EPUB Inteligente` y `🎧 Convertir a Audiolibro (.M4A)` en la hoja de opciones de libros PDF, con diálogo de progreso e importación directa en la Biblioteca.

- [ ] **Task 6: Actualizar `dona.md`, ejecutar `check_dona.ps1` y verificación final de compilación y pruebas unitarias**
  - **Files:**
    - Modify: `dona.md`
  - **Deliverable:** Documento `dona.md` 100% sincronizado con las nuevas clases y suites de pruebas, reporte de tests (casos) y verificación de cero errores.
