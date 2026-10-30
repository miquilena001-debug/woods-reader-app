# Auditoría de Código y Arquitectura - Woods Reader (Reporte Sundar Pichai)

## Resumen Ejecutivo
Woods Reader es un lector Android moderno apoyado en Jetpack Compose, Corrutinas/Flow y Readium 3.x. Su ambición es brindar una experiencia premium unificada para EPUB y PDF, usando Sherpa ONNX para TTS neuronal offline.

Esta auditoría técnica ha examinado ~130 archivos Kotlin enfocándose en el `ROADMAP.md` y el plan de ejecución cero errores. Hemos encontrado aciertos arquitectónicos notables en la orquestación asíncrona, pero también cuellos de botella y deudas técnicas que impiden que los "gestos nunca fallen", como exige el roadmap.

---

## 1. Arquitectura Central y Flujo de Datos

### Aciertos
* **Gestión de Estado Unidireccional (UDF):** El proyecto hace un uso extensivo de `StateFlow` y el patrón UDF, centralizando la lógica compleja en `ReaderViewModel`.
* **Desacoplamiento de Servicios Background:** La decisión de mantener `ReaderTtsService` y `BookScannerService` desacoplados asegura supervivencia fuera del ciclo de vida de UI.

### Deficiencias Críticas
* **God Object `ReaderViewModel` (164KB / >3000 líneas):** Este ViewModel asume excesivas responsabilidades (gestión del TTS, sincronización PDF/EPUB, Sleep Timer, marcadores, in-book search, exportación de audio). Esto dificulta enormemente el mantenimiento y las pruebas aisladas (infringe principios SOLID).
* **Gestión de Sesiones (Pérdida de estados transitorios):** Múltiples llamados directos a Shared Preferences como `flushReadingProgressImmediately()` desde `ReaderViewModel` compiten o saltan a `BookRepository`, lo que puede generar corrupciones en condiciones de carrera.

---

## 2. Pipeline Semántico (Phase 1, 3, 4)

El pipeline de lectura (`ReadingPipeline.kt`) ha sido recientemente introducido para homogeneizar EPUB y PDF (Phase 1 del Roadmap).

### Aciertos
* **`ProcessedDocument` y Unificación:** El uso de `RawDocumentBlock` y su transformación en `TtsNarrationUnit` o `ReadingBlock` (Heading, Paragraph, Image, Noise) es elegante y correcto.
* **Componentización del Pipeline:** Separar la lógica en `NoiseClassifier`, `WordExclusionEngine`, `SpanishTextFormatter` e `ImageAnnouncer` es excelente arquitectura (SOLID).

### Deficiencias Críticas
* **Extracción Síncrona vs Asíncrona:** Aunque `ReadingPipeline.process()` se invoca con `Dispatchers.Default` en `EpubFormatManager`, internamente realiza instanciaciones y regex masivos (ej. `SpanishTextFormatter.normalizeTypography`). En libros grandes, esto aún podría bloquear el frame rate si el particionamiento (chunking) no está bien diseñado.
* **Clasificación de Ruido (`NoiseClassifier`):** Requiere un pre-paso de análisis de todas las páginas de un PDF para detectar patrones iterativos (headers/footers repetitivos). En PDFs pesados (1000+ páginas), esta extracción O(N) retrasará la lectura inicial. Necesita muestreo aleatorio en lugar de iteración total.
* **Sincronizador Vista Libro/PDF (`PdfBookViewSynchronizer`):** Requiere una indexación precisa de bloques contra páginas. La estrategia de bisección o fallback usada en `findBlockIndexForPage` asume una densidad lineal que no se cumple en todos los PDF.

---

## 3. Motor TTS y Servicios de Audio (Phase 1, 4)

### Aciertos
* **Woods Studio Voice HD:** La configuración de DSP acústico, `SherpaModelManager`, y la exclusión mutua mediante `nativeTtsMutex` en `TtsManager.kt` para Sherpa ONNX previenen crashes JNI catastróficos.
* **Sistema de Pre-síntesis:** Uso de `preSynthesisDispatcher` (limitado a 1 thread) es brillante para evitar cuellos de botella del motor C++.

### Deficiencias Críticas
* **Gestión de Foco de Audio (Audio Focus):** `TtsManager.kt` tiene problemas con la atenuación temporal (ducking) vs pausa total. Se observa una flag volátil `isSynthesizingOrPlayingSherpa` que puentea a veces las directivas del SO, pudiendo generar comportamiento intrusivo (audio que no para al recibir una llamada en ciertos dispositivos).
* **Sobrecarga de Caché TTS:** `preSynthesisCache` se limpia rudimentariamente, pero bajo escenarios de cambios rápidos de capítulos, el motor C++ de Sherpa podría quedar ahogado en tareas de generación pendientes si no se abortan correctamente.

---

## 4. UI y Jetpack Compose (Phase 5)

### Aciertos
* **Componentes Granulares:** `ReaderUIComponents.kt`, `ReaderBottomBar.kt`, etc. Compose está bien fragmentado a nivel visual.
* **Lifecycle Awareness:** Se observan flujos mediante `collectAsStateWithLifecycle()` en varias áreas, lo que es correcto.

### Deficiencias Críticas
* **Recomposiciones Innecesarias:** La integración del resaltado del TTS palabra a palabra (Phase 1.4) a través del state flow `currentTtsRange` puede desencadenar recomposiciones masivas si no se aísla exclusivamente en el `Text` o `Canvas` específico, provocando *jank* (baja de FPS).
* **Complejidad del Modal Bottom Sheet:** Hay más de 6 bottom sheets y menús (`ReaderAudioBottomSheet`, `SearchBottomSheet`, `TocBottomSheet`, `TtsRulesBottomSheet`, `ReaderXRayBottomSheet`, `ReaderMasterMenuBottomSheet`). Combinar esto en un solo panel navegable como sugiere el roadmap (Fase 5.2) es fundamental para simplificar el árbol de Compose y los backstacks internos.

---

## 5. Recomendaciones Arquitectónicas (Next Steps)

Para cumplir el ideal de *"ningún gesto falla nunca"* y lectura tipo libro físico:

1. **Desacoplar el `ReaderViewModel` (Urgente):**
   * Separar la lógica de Exportación/Audio a un `AudioExportViewModel`.
   * Separar la lógica puramente de TTS a un `ReaderTtsViewModel`.
   * Usar el patrón *MVI (Model-View-Intent)* o inyectar "UseCases" para adelgazar el ViewModel.
2. **Optimizar Compose para 60fps:**
   * En `ReaderScreen.kt`, envolver el dibujado de resaltado (highlight) de TTS en un componente `Modifier.drawWithCache` u observar los estados de forma delegada para que el resto del texto no repinte en cada fonema narrado.
3. **Optimizar `PdfReflowManager` y `ReadingPipeline`:**
   * Garantizar que `TextAnalyzer` para PDF use paginación perezosa (*lazy parsing*). Analizar 3 páginas por delante en vez de bloques masivos, mitigando `OutOfMemoryError`.
4. **Unificación de Persistencia:**
   * Restringir todas las lecturas/escrituras a SharedPreferences de progreso únicamente a un "DataSource" seguro (ej: `SessionDataSource`), eliminando el código espagueti de guardado de emergencia (`flushReadingProgressImmediately`) y confiando en Room + WorkManager para integridad.

## Conclusión
La base técnica de **Woods Reader** es robusta y los últimos parches de seguridad demuestran un enfoque proactivo en problemas de concurrencia. La transición final hacia una app verdaderamente "Premium" depende casi enteramente de la reducción de la complejidad ciclomática del `ReaderViewModel` y de la gestión exquisita del repintado en Compose para que la latencia UI/Gestos caiga por debajo de 16ms en todo momento.