# Plan de Implementación: Traductor In-Place de EPUBs y Resiliencia en Segundo Plano

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sustituir la reconstrucción destructiva de EPUBs (`CalibreStyleEpubTranslator` con regex y `LocalEpubBuilder`) por una arquitectura no destructiva *in-place* (`EpubInPlaceTranslator`), con parsing XML estricto (jsoup), protección de elementos inline (imágenes, svg, br, notas, glosario), ensamblado ZIP fiel preservando portadas y CSS, persistencia en disco de estado y caché para reanudación, y ejecución en segundo plano con `WorkManager` (Foreground Service `dataSync`).

**Architecture:**
1. `EpubPackage`: Modelo orientado a objetos del EPUB que lee `container.xml`, OPF (manifest, spine, metadata, nav/NCX) con jsoup en modo XML y resuelve rutas canónicas.
2. `XhtmlTranslatorDoc`: Parser/serializador XML que extrae párrafos y elementos textuales respetando jerarquía, sustituye elementos inline por marcadores seguros (`{{id_000000}}`), reinserta traducciones con validación estricta y preserva atributos `xml:lang`.
3. `EpubZipWriter`: Escritor de EPUB que replica el archivo ZIP original, preservando entradas intactas y reemplazando únicamente las entradas modificadas (XHTML, OPF, NCX, NAV), con `mimetype` STORED y escritura atómica a archivo `.tmp`.
4. `TranslationStore`: Gestor de persistencia en `filesDir/translation_jobs/<sha1>/` con `state.json` (progreso de capítulos, errores, metadatos) y `cache.jsonl` (hash de texto con marcadores -> traducción) para reanudación inmediata tras desconexión o reinicio.
5. `GoogleFreeNetworkTranslator` (Endurecido): Mutex para pacing y métricas, backoff exponencial ante 429/403, detección de CAPTCHA/HTML con pausa estructurada y cadena de endpoints tras la interfaz `TranslatorEngine`.
6. `TranslationJobWorker`: CoroutineWorker con `setForeground()` (`dataSync`), auto-reprogramación si se alcanza el tiempo límite, y vinculación de estado persistente a la UI (`CalibreTranslationProgressDialog`).

**Tech Stack:** Kotlin, Android WorkManager (`androidx.work`), Hilt (`hilt-work`), jsoup (XML parser), OkHttp, Kotlin Coroutines & Flow, Jetpack Compose.

---

## Global Constraints

- Preservar `LocalEpubBuilder` y no romper su uso en `WebToEpubImporterDialog`.
- La salida traducida se guarda como una nueva copia (`<nombre>_ES.epub`) en `filesDir/translated_epubs/` antes de ofrecerse al usuario/biblioteca.
- Mantener `CalibreStyleEpubTranslator` temporalmente como fallback configurable hasta validar completamente el nuevo pipeline.
- Ningún capítulo se pierde si fallan párrafos aislados (fallback al original para ese párrafo tras reintentos).
- El ZIP final debe ser 100% conforme al estándar EPUB: `mimetype` primero, sin compresión (STORED), sin extra field, CRC32 precalculado.

---

## Tasks

### Task 0: Abstracción de Red y Fixtures de Prueba
**Files:**
- Create: `app/src/main/java/com/example/woodsreader/core/translation/calibre/TranslatorEngine.kt`
- Modify: `app/src/main/java/com/example/woodsreader/core/translation/calibre/GoogleFreeNetworkTranslator.kt`
- Create: `app/src/test/java/com/example/woodsreader/core/translation/calibre/FakeTranslatorEngine.kt`
- Create: `app/src/test/resources/epub_fixtures/` (archivos EPUB de prueba mínimos: normal, EPUB2 con NCX, EPUB3 con nav.xhtml, con imágenes/svg inline, rutas con subcarpetas y espacios)

- [ ] **Step 0.1:** Crear la interfaz `TranslatorEngine` con `suspend fun translateBatch(...)` y `suspend fun translateText(...)`.
- [ ] **Step 0.2:** Hacer que `GoogleFreeNetworkTranslator` implemente `TranslatorEngine`.
- [ ] **Step 0.3:** Crear `FakeTranslatorEngine` para pruebas unitarias sin conexión.
- [ ] **Step 0.4:** Crear generador programático o archivos fixture de prueba EPUB para unit tests.

---

### Task 1: Modelo del Paquete EPUB (`EpubPackage`)
**Files:**
- Create: `app/src/main/java/com/example/woodsreader/core/translation/calibre/model/EpubPackage.kt`
- Create: `app/src/main/java/com/example/woodsreader/core/translation/calibre/model/EpubPackageParser.kt`
- Create: `app/src/test/java/com/example/woodsreader/core/translation/calibre/EpubPackageParserTest.kt`

- [ ] **Step 1.1:** Escribir tests para parsear `container.xml` -> ruta de OPF.
- [ ] **Step 1.2:** Implementar parser de `container.xml` con `Jsoup.parse(..., Parser.xmlParser())`.
- [ ] **Step 1.3:** Escribir tests para parsear manifest, spine, metadatos (`dc:title`, `dc:language`, `dc:creator`), `nav.xhtml` y NCX, resolviendo rutas relativas con `..`, `./`, `%20` y normalización insensible a mayúsculas como fallback.
- [ ] **Step 1.4:** Implementar `EpubPackageParser` y `EpubPackage` pasando todos los tests.

---

### Task 2: Extractor y Marcadores de XHTML (`XhtmlTranslatorDoc`)
**Files:**
- Create: `app/src/main/java/com/example/woodsreader/core/translation/calibre/parser/XhtmlTranslatorDoc.kt`
- Create: `app/src/test/java/com/example/woodsreader/core/translation/calibre/XhtmlTranslatorDocTest.kt`

- [ ] **Step 2.1:** Escribir test de roundtrip: parsear XHTML -> extraer elementos -> reinsertar sin modificar -> el XHTML resultante mantiene estructura, atributos y contenido intactos.
- [ ] **Step 2.2:** Implementar selección jerárquica de bloques de texto (evitando duplicar hijos si el padre fue seleccionado) con `Jsoup.parse(html, "", Parser.xmlParser())` y `prettyPrint(false)`.
- [ ] **Step 2.3:** Implementar enmascaramiento de elementos inline (`<img>`, `<br>`, `<sub>`, `<sup>`, `<svg>`, `<a>` noterefs) con marcadores unificados `{{id_000000}}`.
- [ ] **Step 2.4:** Implementar re-inserción validada con soporte para caracteres especiales, entidades XML y atributos `xml:lang` en `<html>`.
- [ ] **Step 2.5:** Validar fallback de párrafo cuando el traductor corrompe o elimina un marcador.

---

### Task 3: Escritor In-Place de EPUB (`EpubZipWriter`)
**Files:**
- Create: `app/src/main/java/com/example/woodsreader/core/translation/calibre/writer/EpubZipWriter.kt`
- Create: `app/src/test/java/com/example/woodsreader/core/translation/calibre/EpubZipWriterTest.kt`

- [ ] **Step 3.1:** Escribir test que copie un EPUB reemplazando solo un archivo XHTML y el OPF, y verifique que el ZIP resultante tiene `mimetype` STORED al inicio y abre correctamente con `ZipFile`.
- [ ] **Step 3.2:** Implementar `EpubZipWriter` con escritura a archivo temporal (`.epub.tmp`), cálculo de CRC32 para `mimetype`, copia byte-a-byte de entradas no modificadas y reemplazo de entradas modificadas.
- [ ] **Step 3.3:** Añadir chequeo previo de espacio en disco (`usableSpace >= 2 * size`) y verificación post-escritura abriendo el ZIP resultante.

---

### Task 4: Almacén de Estado, Caché en Disco y Reanudación (`TranslationStore`)
**Files:**
- Create: `app/src/main/java/com/example/woodsreader/core/translation/calibre/store/TranslationStore.kt`
- Create: `app/src/main/java/com/example/woodsreader/core/translation/calibre/store/TranslationState.kt`
- Create: `app/src/test/java/com/example/woodsreader/core/translation/calibre/TranslationStoreTest.kt`

- [ ] **Step 4.1:** Escribir tests para guardar y reanudar estado (`state.json`), caché en disco (`cache.jsonl`), y capítulos intermedios (`chapters/<index>.xhtml`).
- [ ] **Step 4.2:** Implementar `TranslationStore` con escritura atómica de `state.json` y append-only en `cache.jsonl`.
- [ ] **Step 4.3:** Endurecer `GoogleFreeNetworkTranslator` con Mutex thread-safe, detección de HTML/bloqueo, y backoff exponencial para cuotas.

---

### Task 5: Orquestador In-Place (`EpubInPlaceTranslator`)
**Files:**
- Create: `app/src/main/java/com/example/woodsreader/core/translation/calibre/EpubInPlaceTranslator.kt`
- Modify: `app/src/main/java/com/example/woodsreader/core/translation/calibre/CalibreStyleEpubTranslator.kt` (delegación o flag)
- Create: `app/src/test/java/com/example/woodsreader/core/translation/calibre/EpubInPlaceTranslatorTest.kt`

- [ ] **Step 5.1:** Escribir test end-to-end con `FakeTranslatorEngine`: EPUB original -> traducción in-place -> EPUB resultante contiene portada, imágenes y texto traducido.
- [ ] **Step 5.2:** Implementar `EpubInPlaceTranslator` orquestando `EpubPackageParser`, `XhtmlTranslatorDoc`, `TranslationStore`, `EpubZipWriter` y traducción de TOC (`nav.xhtml` y NCX).
- [ ] **Step 5.3:** Configurar flag en `CalibreStyleEpubTranslator` para alternar entre el nuevo motor in-place y el legado si fuese necesario.

---

### Task 6: Ejecución en Segundo Plano con WorkManager (`TranslationJobWorker`) y UI
**Files:**
- Create: `app/src/main/java/com/example/woodsreader/service/TranslationJobWorker.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/example/woodsreader/ui/library/LibraryViewModel.kt`
- Modify: `app/src/main/java/com/example/woodsreader/ui/library/CalibreTranslationProgressDialog.kt`

- [ ] **Step 6.1:** Implementar `TranslationJobWorker` con `@HiltWorker`, notificación foreground `FOREGROUND_SERVICE_DATA_SYNC` con acciones Cancelar y Pausar.
- [ ] **Step 6.2:** Integrar `WorkManager.enqueueUniqueWork` por SHA-1 del EPUB con `ExistingWorkPolicy.KEEP`.
- [ ] **Step 6.3:** Conectar `LibraryViewModel` y `CalibreTranslationProgressDialog` para observar el progreso persistido, permitiendo cerrar la app y regresar al diálogo en tiempo real.
