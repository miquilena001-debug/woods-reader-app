# Protocolo de Ejecución Zero-Error para IA (Gemini / LLM Playbook)
# Proyecto: Woods Reader (Android EPUB & PDF Reader)

> **PROPÓSITO:** Esta guía está diseñada para que un modelo de lenguaje (como Gemini) o un agente autónomo ejecute la transformación de Woods Reader hacia un lector de nivel premium sin cometer errores de compilación, alucinaciones de código, rupturas de arquitectura ni regresiones en los 69 tests existentes.

---

## 1. Reglas de Oro Inviolables (Guardrails para la IA)

Cualquier agente IA que trabaje en este repositorio **DEBE** respetar estas 8 restricciones absolutas:

1. **PROHIBIDO inventar dependencias:** No agregar dependencias a `app/build.gradle.kts` ni a `settings.gradle.kts`. Todo lo requerido ya está en el proyecto (Readium 3.x, Compose BOM, Media3 1.4.x, Sherpa ONNX, Room, Hilt, Timber).
2. **PROHIBIDO tocar Room Schemas sin orden explícita:** No alterar entidades Room (`@Entity`) ni DAOs sin crear la migración correspondiente y regenerar el esquema JSON con KSP.
3. **PROHIBIDO bloquear el hilo principal:** Toda operación de I/O de archivos, procesamiento de bitmaps, extracción de texto o inferencia ONNX debe ejecutarse en `Dispatchers.IO` o `Dispatchers.Default`. Compose UI corre exclusivamente en `Dispatchers.Main`.
4. **REGLA DE EDICIÓN ATÓMICA:** Modificar como máximo **2 archivos de producción + 1 archivo de test** por iteración. Nunca editar masivamente más de 3 archivos en un solo paso.
5. **COMPILACIÓN OBLIGATORIA ENTRE PASOS:** Antes de dar por terminado cualquier paso, ejecutar:
   ```powershell
   .\gradlew.bat :app:testDebugUnitTest
   ```
   Si el código de salida no es `0`, el paso **NO** está completado. Si no se puede resolver en 1 intento, revertir con `git checkout -- .` para no acumular deuda técnica.
6. **PRESERVAR MÉTODOS EXISTENTES:** No reemplazar archivos completos a ciegas. Usar sustitución quirúrgica. Nunca borrar métodos auxiliares existentes de `ReaderViewModel` o `TtsManager`.
7. **SOPORTE EXCLUSIVO:** Solo existen dos formatos: `BookFormat.EPUB` y `BookFormat.PDF`. Nunca reintroducir referencias a CBR, CBZ, MOBI o FB2.
8. **COMMIT POR PASO:** Cada micro-paso verificado debe registrarse inmediatamente en Git con un commit atómico documentado.

---

## 2. Mapa de Clases Clave y Arquitectura de Datos

Antes de escribir una sola línea, la IA debe memorizar las siguientes clases reales y sus ubicaciones:

| Responsabilidad | Clase Real | Ubicación |
|---|---|---|
| **Pipeline Semántico** | `ReadingPipeline` | `core/pipeline/ReadingPipeline.kt` |
| **Bloques de Lectura** | `ReadingBlock`, `RawDocumentBlock`, `TtsNarrationUnit` | `core/pipeline/ReadingBlock.kt` |
| **Clasificador de Ruido** | `NoiseClassifier` | `core/pipeline/NoiseClassifier.kt` |
| **Exclusiones de Palabras** | `WordExclusionEngine`, `WordExclusionRule` | `core/pipeline/WordExclusionEngine.kt` |
| **Anunciador de Imágenes** | `ImageAnnouncer` | `core/pipeline/ImageAnnouncer.kt` |
| **Tipografía Español** | `SpanishTextFormatter` | `core/pipeline/SpanishTextFormatter.kt` |
| **Sincronizador PDF/Bloques** | `PdfBookViewSynchronizer` | `core/pipeline/PdfBookViewSynchronizer.kt` |
| **Motor TTS** | `TtsManager` | `core/tts/TtsManager.kt` |
| **Servicio Background TTS** | `ReaderTtsService` | `service/ReaderTtsService.kt` |
| **Coordinador EPUB** | `EpubFormatManager` | `ui/reader/EpubFormatManager.kt` |
| **ViewModel Principal Lector** | `ReaderViewModel` | `ui/reader/ReaderViewModel.kt` |
| **UI Central del Lector** | `ReaderScreen` | `ui/reader/ReaderScreen.kt` |

---

## 3. Plan de Micro-Pasos Atómicos (Paso a Paso)

---

### MICRO-PASO 1.1: Adaptador de Extracción Semántica para EPUB
* **Objetivo:** Convertir el contenido del recurso activo de un EPUB (`Publication` de Readium) en una lista de `RawDocumentBlock` para que el `ReadingPipeline` pueda procesarlo.
* **Archivos a crear/modificar:**
  - Crear: `app/src/main/java/com/example/woodsreader/core/pipeline/EpubContentExtractor.kt`
  - Crear: `app/src/test/java/com/example/woodsreader/core/pipeline/EpubContentExtractorTest.kt`
* **Especificación del Contrato:**
  ```kotlin
  package com.example.woodsreader.core.pipeline
  
  import org.jsoup.Jsoup
  import javax.inject.Inject
  import javax.inject.Singleton
  
  @Singleton
  class EpubContentExtractor @Inject constructor() {
      /**
       * Parsea HTML/XHTML de un recurso de EPUB y produce RawDocumentBlock
       * ordenados, preservando imágenes y jerarquía de títulos.
       */
      fun extractBlocks(htmlContent: String, resourceHref: String, pageIndex: Int = 0): List<RawDocumentBlock>
  }
  ```
* **Lógica Interna:**
  1. Usar `Jsoup.parse(htmlContent)` (Jsoup ya está disponible en las dependencias transitivas de Readium).
  2. Iterar elementos de bloque: `p`, `h1..h6`, `img`, `figure`, `blockquote`, `hr`.
  3. Para imágenes: generar `RawDocumentBlock` con `isImage = true`, `imageCaption = element.attr("alt")`.
  4. Para texto: generar con `fontSize` relativo al tag (`h1` = 24f, `h2` = 20f, `p` = 16f).
  5. Asignar `isFirstLineOfPage` al primer elemento y `isLastLineOfPage` al último del recurso.
* **Trampas a Evitar (AI Guardrails):**
  - NO abrir streams síncronos de archivos aquí; la función recibe el `htmlContent: String` ya leído.
  - NO usar selectores CSS complejos que causen lentitud en HTMLs grandes de 500 KB.
* **Comando de Verificación:**
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "com.example.woodsreader.core.pipeline.EpubContentExtractorTest"
  ```
* **Git Anchor:**
  `feat(pipeline): add EpubContentExtractor with HTML block extraction test suite`

---

### MICRO-PASO 1.2: Conexión del Pipeline a EpubFormatManager y ReaderViewModel
* **Objetivo:** Enlazar `EpubContentExtractor` y `ReadingPipeline` en el flujo de carga del EPUB para generar `ProcessedDocument`.
* **Archivos a modificar:**
  - `app/src/main/java/com/example/woodsreader/ui/reader/EpubFormatManager.kt`
  - `app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt`
* **Especificación del Contrato:**
  - En `EpubFormatManager`: Inyectar `readingPipeline: ReadingPipeline` y `epubContentExtractor: EpubContentExtractor`.
  - Exponer `val currentProcessedDocument: StateFlow<ProcessedDocument?>`.
  - En la carga de cada recurso/capítulo, procesar en background:
    ```kotlin
    withContext(Dispatchers.Default) {
        val rawBlocks = epubContentExtractor.extractBlocks(resourceHtml, href)
        val processed = readingPipeline.process(rawBlocks, rules = activeRules, announceImages = true)
        _currentProcessedDocument.value = processed
    }
    ```
* **Trampas a Evitar (AI Guardrails):**
  - NO reemplazar la navegación nativa de Readium (`VisualNavigator.go`). Readium sigue mostrando la WebView; el pipeline genera la pista semántica para el TTS y el resaltado sin alterar la vista visual.
  - Asegurar que `activeRules` se recolecte desde Room o DataStore sin bloquear.
* **Comando de Verificación:**
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "com.example.woodsreader.ui.reader.ReaderViewModelTest"
  ```
* **Git Anchor:**
  `feat(reader): integrate ReadingPipeline into EpubFormatManager and expose ProcessedDocument`

---

### MICRO-PASO 1.3: Extracción Semántica para PDF mediante PdfRenderer & TextAnalyzer
* **Objetivo:** Canalizar la extracción de texto de páginas PDF hacia `ReadingPipeline` usando `RawDocumentBlock`.
* **Archivos a modificar:**
  - `app/src/main/java/com/example/woodsreader/core/managers/pdf/analysis/TextAnalyzer.kt`
  - Crear: `app/src/test/java/com/example/woodsreader/core/pipeline/PdfSemanticExtractionTest.kt`
* **Especificación del Contrato:**
  - `TextAnalyzer` debe exponer:
    ```kotlin
    fun convertPageToRawBlocks(pageIndex: Int, textLines: List<AnalyzedLine>, images: List<AnalyzedImage>): List<RawDocumentBlock>
    ```
  - Mapear cada línea y cada imagen detectada por el analizador con su posición, tamaño de fuente y coordenadas relativas.
* **Trampas a Evitar (AI Guardrails):**
  - NO eliminar métodos existentes que usa `PdfReflowManager`; agregar este método como la ruta limpia y estandarizada.
  - Asegurar que las imágenes incrustadas incluyan su índice de orden relativo en la página.
* **Comando de Verificación:**
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "com.example.woodsreader.core.pipeline.PdfSemanticExtractionTest"
  ```
* **Git Anchor:**
  `feat(pdf): bridge TextAnalyzer to RawDocumentBlock for unified pipeline consumption`

---

### MICRO-PASO 1.4: Alimentación de TTS mediante TtsNarrationUnit
* **Objetivo:** Hacer que `TtsManager` y `ReaderTtsService` narren a través de `List<TtsNarrationUnit>` generadas por el pipeline, garantizando que el ruido (cabeceras, números de página, URLs descartadas) nunca llegue a los motores de voz (Sherpa o System TTS).
* **Archivos a modificar:**
  - `app/src/main/java/com/example/woodsreader/core/tts/TtsManager.kt`
  - `app/src/main/java/com/example/woodsreader/service/ReaderTtsService.kt`
* **Especificación del Contrato:**
  - Agregar en `TtsManager`:
    ```kotlin
    fun enqueueNarrationUnits(units: List<TtsNarrationUnit>, startIndex: Int = 0)
    ```
  - En lugar de recibir un `String` gigante y re-filtrarlo, el TTS itera ordenadamente sobre las unidades pre-limpias y pre-formateadas.
  - Cuando una unidad tiene `isAnnouncement = true` (por ejemplo, "Imagen: mapa de la comarca"), el TTS la pronuncia con un tono ligeramente diferenciado o una pausa natural (`delay(300)`).
* **Trampas a Evitar (AI Guardrails):**
  - NO alterar la firma de `speakSystemTts` ni `speakWithSherpa`; crear la sobrecarga o método orquestador `speakUnits(...)`.
  - Asegurar que el callback de progreso emita el `blockId` para sincronización con la UI.
* **Comando de Verificación:**
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "com.example.woodsreader.core.pipeline.ReadingPipelineTest"
  ```
* **Git Anchor:**
  `feat(tts): orchestrate playback using TtsNarrationUnit queue with image announcement support`

---

### MICRO-PASO 1.5: Sincronización y Resaltado de Lectura en Tiempo Real
* **Objetivo:** Unificar el resaltado de la palabra/párrafo narrado tanto en EPUB como en PDF mediante `blockId` y rango de texto.
* **Archivos a modificar:**
  - `app/src/main/java/com/example/woodsreader/ui/reader/ReaderContract.kt`
  - `app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt`
* **Especificación del Contrato:**
  - En `ReaderState` (en `ReaderContract.kt`):
    ```kotlin
    val activeNarrationBlockId: String? = null,
    val activeNarrationWordRange: IntRange? = null
    ```
  - `ReaderViewModel` actualiza estos dos campos en respuesta a los callbacks de `TtsManager`.
  - La UI de lectura (tanto en modo EPUB como en modo PDF) observa este estado y aplica el color de resaltado configurado en `TtsHighlightStyle`.
* **Trampas a Evitar (AI Guardrails):**
  - Evitar recomposiciones innecesarias de toda la pantalla: usar `derivedStateOf` o componentes aislados para el resaltado del bloque actual.
* **Comando de Verificación:**
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest
  ```
* **Git Anchor:**
  `feat(reader): coordinate word and block level highlighting between TTS and UI state`

---

### MICRO-PASO 2.1: Motor de Tipografía y Reglas de Justificación en Español
* **Objetivo:** Aplicar reglas tipográficas editoriales al CSS inyectado en Readium (EPUB) y a los textos en Compose: silabeo automático (`hyphens: auto`), márgenes proporcionales y ligaduras tipográficas.
* **Archivos a modificar:**
  - `app/src/main/java/com/example/woodsreader/ui/reader/EpubFormatManager.kt`
  - `app/src/main/java/com/example/woodsreader/core/pipeline/SpanishTextFormatter.kt`
* **Especificación del Contrato:**
  - En `SpanishTextFormatter`: expandir soporte para comillas angulares latinas (« »), guiones largos para diálogos (—) y espacios no separables en signos dobles (¿ ?, ¡ !).
  - En `EpubFormatManager`: configurar los estilos CSS de Readium con:
    ```css
    body {
        hyphens: auto;
        -webkit-hyphens: auto;
        text-align: justify;
        text-justify: inter-word;
        orphans: 2;
        widows: 2;
    }
    ```
* **Comando de Verificación:**
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "com.example.woodsreader.core.pipeline.SpanishTextFormatterTest"
  ```
* **Git Anchor:**
  `feat(typography): apply Spanish editorial typography rules and hyphenation CSS`

---

### MICRO-PASO 3.1: Vista Libro para PDF (Modo Dual de Lectura)
* **Objetivo:** Permitir conmutar instantáneamente un PDF entre "Vista Original" (maquetación fija con zoom) y "Vista Libro" (texto fluido presentado como EPUB a través de los `displayBlocks` del pipeline).
* **Archivos a modificar:**
  - `app/src/main/java/com/example/woodsreader/ui/reader/ReaderContract.kt`
  - `app/src/main/java/com/example/woodsreader/ui/reader/ReaderScreen.kt`
  - `app/src/main/java/com/example/woodsreader/core/pipeline/PdfBookViewSynchronizer.kt`
* **Especificación del Contrato:**
  - En `ReaderState`: agregar `val isPdfBookViewActive: Boolean = false`.
  - Usar `PdfBookViewSynchronizer` para transferir la posición:
    * Si el usuario está en página 15 en Vista Original y conmuta a Vista Libro, calcular `findBlockIndexForPage(15)` y scrollear allí.
    * Si el usuario avanza en Vista Libro y vuelve a Vista Original, calcular `findPageForBlockIndex(currentBlock)` y abrir esa página exacta.
* **Trampas a Evitar (AI Guardrails):**
  - NO recargar el PDF desde disco al conmutar; ambos modos leen del mismo `PdfRenderer` y de la memoria en caché.
* **Comando de Verificación:**
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest --tests "com.example.woodsreader.core.pipeline.PdfBookViewSynchronizerTest"
  ```
* **Git Anchor:**
  `feat(pdf): implement dual-view book mode with seamless position synchronization`

---

### MICRO-PASO 4.1: Reglas de Exclusión Dinámica desde la Selección de Texto
* **Objetivo:** Permitir al usuario mantener pulsada una palabra o frase en el libro y seleccionar "Ignorar en la lectura / TTS", guardando la regla en Room de forma reactiva.
* **Archivos a modificar:**
  - `app/src/main/java/com/example/woodsreader/data/repository/BookRepository.kt`
  - `app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt`
* **Especificación del Contrato:**
  - `ReaderViewModel` expone `fun addWordExclusionRule(pattern: String, isExactMatch: Boolean, applyToAllBooks: Boolean)`
  - La regla se persiste en Room y actualiza inmediatamente el `WordExclusionEngine` en memoria.
* **Comando de Verificación:**
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest
  ```
* **Git Anchor:**
  `feat(rules): allow adding dynamic word exclusion rules directly from text selection`

---

### MICRO-PASO 5.1: Validación Global y Suite de Aceptación BDD
* **Objetivo:** Asegurar que los 12 escenarios de aceptación del `ROADMAP.md` queden respaldados por pruebas automatizadas sin fisuras.
* **Archivos a modificar:**
  - `app/src/test/java/com/example/woodsreader/core/pipeline/ReadingPipelineBddTest.kt`
  - `REVISION.md`
* **Comando de Verificación:**
  ```powershell
  .\gradlew.bat :app:testDebugUnitTest
  .\gradlew.bat :app:assembleDebug
  ```
* **Git Anchor:**
  `test(bdd): verify all 12 acceptance criteria pass on debug build`

---

## 4. Guía de Solución de Problemas (Troubleshooting para la IA)

Si durante la ejecución ocurre alguno de estos errores comunes:

1. **`Unresolved reference: Jsoup`**:
   - Verificar si org.jsoup está en el classpath. Si no estuviera expuesto por Readium, usar un parser de XML/DOM estándar de Android (`org.xmlpull.v1.XmlPullParser` o `android.util.Xml`) sin agregar dependencias externas.
2. **`OutOfMemoryError` en procesamiento de PDF**:
   - Nunca instanciar bitmaps de todas las páginas a la vez. Procesar página por página usando `use { }` en cada `PdfRenderer.Page`.
3. **`IllegalStateException: Module ... already disposed`**:
   - Ocurre si un `CoroutineScope` intenta utilizar un `Publication` de Readium cerrado. Asegurar que las referencias se limpien en `onCleared()`.
4. **Fallo en tests de Room**:
   - Ejecutar los tests con `inMemoryDatabaseBuilder` y `.allowMainThreadQueries()` para tests unitarios.

---

## 5. Criterio de Finalización

El plan se considerará ejecutado con éxito cuando:
1. `.\gradlew.bat :app:testDebugUnitTest` ejecute **>80 pruebas unitarias con 0 fallos**.
2. `.\gradlew.bat :app:assembleDebug` compile en **< 30 segundos** con código de salida 0.
3. El tamaño del APK se mantenga por debajo de los **235 MB**.
4. Cada micro-paso cuente con su respectivo commit en la rama `master`.
