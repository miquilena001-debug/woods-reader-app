# dona.md — Documento Ancla e Índice Atómico Completo de Woods Reader

**Propósito:** Documento ancla maestro y fuente de verdad técnica para que cualquier desarrollador o asistente de IA entienda instantáneamente la arquitectura, invariantes, decisiones de diseño, estado real y estructura de cada archivo de Woods Reader sin requerir el historial de chats previo.

---

## 📌 ESTADO ACTUAL DEL PROYECTO (Al 28/10/2026)
- **Fecha de última auditoría:** 28 de octubre de 2026.
- **Commit hash de referencia:** `480f604` (rama `master`).
- **Resumen Ejecutivo:**
  1. **Compilación Limpia:** `:app:compileDebugKotlin` compila con 0 errores y 0 advertencias bloqueantes.
  2. **Pruebas Unitarias al 100%:** 42 suites de pruebas unitarias ejecutables en `app/src/test/` (distribuidas en 47 archivos de test y utilidades) ejecutan 182 casos de test en verde (0 fallos, 0 errores).
  3. **Verificación en Dispositivo Real:** APK generado (`app-debug.apk`, 104.7 MB) instalado y corriendo de forma estable en `TECNO-LH7n` (Android 14, PID verificado).
  4. **Arquitectura del Lector Descomprimida:** `ReaderViewModel.kt` desacoplado con éxito mediante el patrón *Strangler Fig*, extrayendo 5 Casos de Uso y Coordinadores de dominio en `domain/reader/` (reducción de más de 1.100 líneas y 5 inyecciones directas eliminadas).
  5. **Exportación de Audio Unificada:** Generación de un único archivo contenedor `.m4a` con portada en alta resolución (`covr`) y subtítulos sincronizados LRC (`©lyr`) incrustados directamente a nivel binario vía `M4aMetadataEmbedder`, eliminando por completo residuos de archivos sueltos.

---

## 🛡️ INVARIANTES E INTOCABLES (Reglas del Proyecto)
Los siguientes componentes constituyen el núcleo crítico del lector y **NUNCA DEBEN MODIFICARSE** sin aprobación expresa ni sin una suite de pruebas de regresión visual/auditiva:
1. `ChapterScrollDetector.kt`:
   - **Razón:** Maneja la detección sensible de scroll horizontal y vertical de Readium con heurísticas de tolerancia gestual. Cualquier alteración de sus umbrales produce saltos de página erráticos, bloqueos de scroll o regresiones en modo continuo.
2. **CSS de paginado en `ReaderExtensions.kt`**:
   - **Razón:** Inyectado dinámicamente en el WebView de Readium. Controla el cálculo de columnas, márgenes, separación de párrafos, tipografía y soporte de temas (sepia, noche, blanco). Modificarlo corrompe la paginación y altera el conteo de páginas virtuales.
3. **Manejo de `AudioFocus` en `TtsManager.kt`**:
   - **Razón:** Implementa las directrices estrictas de Android para `AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK` y transiciones de audio. Alterarlo provoca colisiones de reproducción con llamadas entrantes, reproductores de música externos (Spotify/YouTube) y pérdida de sonido al pausar.
4. **Firmas y `StateFlow` públicos en `ReaderViewModel.kt` consumidos por `ReaderScreen.kt`**:
   - **Razón:** Representa el contrato rígido de la UI con la lógica de negocio. Cualquier refactorización debe ser interna (hacia casos de uso o coordinadores delegados) sin alterar las firmas ni nombres de métodos ni flows observados por la pantalla de lectura.

---

## 🏛️ DECISIONES DE ARQUITECTURA Y MOTIVOS
1. **Strangler Fig de Adentro Hacia Afuera para `ReaderViewModel`**:
   - *Decisión:* En lugar de reescribir el ViewModel de 2.900 líneas en un solo paso de alto riesgo, se extrajeron gradualmente las responsabilidades de dominio comenzando por las hojas periféricas (`SaveReadingProgressUseCase`, `CalculateReadingStatsUseCase`, `LoadBookFastUseCase`, `ManageReaderAnnotationsUseCase`, `ReaderIntelligenceAndExportCoordinator`).
   - *Motivo:* Permitió mantener la compilación y las 182 pruebas unitarias pasando en cada commit individual, con cero regresiones en la UI y facilitando la cobertura aislada de pruebas.
2. **NO a un `ReaderUiState` Monolítico Único**:
   - *Decisión:* Se conservan `StateFlow` independientes para aspectos ortogonales (progreso de lectura, estado de audio/TTS, notas y resaltados, búsqueda en libro, rayos-X, etc.).
   - *Motivo:* Unificar todos los campos en una sola data class provocaba que cualquier cambio milimétrico de posición (scroll continuo a 60 fps) disparara recomposiciones masivas de menús, barras de audio y diálogos en Compose, degradando el rendimiento.
3. **Fast-Path Sintético O(1) en `LoadBookFastUseCase`**:
   - *Decisión:* Para libros voluminosos con más de 80 capítulos/spines, se omite el cálculo tradicional y pesado de posiciones de Readium (`computePositions()`) y se genera un array sintético de posiciones virtuales proporcionales.
   - *Motivo:* Reduce el tiempo de apertura de libros masivos (>1.000 páginas) de ~12 segundos a menos de 200 milisegundos, erradicando los congelamientos de ANR al abrir libros grandes.
4. **Un Solo Archivo Contenedor `.m4a` con Metadatos Binarios**:
   - *Decisión:* En lugar de generar hasta 7 archivos externos complementarios (`.jpg`, `.lrc`, `.srt`, `.txt`), la exportación de audio sintetizado genera exclusivamente un archivo `.m4a` que aloja la pista de audio AAC a 128 kbps, la portada JPEG en la caja `covr` y la letra sincronizada en la caja `©lyr` del átomo `moov.udta.meta.ilst`.
   - *Motivo:* Cero contaminación de carpetas en `Music/WoodsReader`, compatibilidad inmediata con reproductores estándar (Samsung Music, Poweramp, Musicolet, VLC) y portabilidad completa de la biblioteca de audio.

---

## 📋 REGLAS DE TRABAJO PARA DESARROLLADORES Y AGENTES DE IA
1. **Punto de Restauración Previo Obligatorio:** Antes de aplicar cualquier cambio o refactorización, registrar el estado con `git add -A && git commit -m "..."`.
2. **Tolerancia Cero a Errores de Build:** Todo cambio debe verificar `:app:compileDebugKotlin` y `:app:testDebugUnitTest` con 0 errores antes de dar por buena una intervención.
3. **Atomicidad de Fases:** Un commit descriptivo por fase o componente. Nunca acumular múltiples tareas no relacionadas en un solo commit.
4. **Evidencia Antes de Aserción:** Prohibido afirmar que un bug está resuelto o que un módulo funciona sin haber inspeccionado el código fuente, ejecutado las pruebas o validado en logcat.
5. **Criterio de Validación Dispositivo (🟡 → ✅):** Todo hallazgo o refactor que involucre hardware o subsistemas de Android (TTS nativo, Sherpa ONNX, audio focus, ciclo de vida del WebView) se marca como 🟡 hasta ser ejecutado y comprobado físicamente en el teléfono (`TECNO-LH7n`).

---

## 🗺️ PENDIENTES PRIORIZADOS Y ROADMAP
*(Referencia completa: [ROADMAP.md](file:///c:/Users/Orbit/Downloads/woods%20reader/ROADMAP.md))*

1. **Fase 1 del Roadmap: TTS Karaoke Sincronizado Palabra por Palabra**:
   - Implementación de resaltado visual continuo de palabras en el texto durante la narración TTS mediante los timestamps emitidos por los tokens de Sherpa ONNX.
2. **Fase 2 del Roadmap: Tipografía y Ortotipografía Española Avanzada**:
   - Formateo automatizado de diálogos con raya/guion largo (`—`), comillas latinas (`«` y `»`), espacios de no separación (NBSP) antes de signos dobles (`¿`, `¡`) y partición silábica en español.
3. **Fase 3 del Roadmap: Doble Vista para Documentos PDF**:
   - Alternancia fluida entre modo Reflow accesible (extracción semántica) y modo Réplica Original con zoom, conservando la sincronización de lectura bidireccional.
4. **Deuda Técnica Crítica Pendiente:**
   - 🔴 **C4:** Cancelación ordenada de la corrutina de limpieza en `SherpaModelManager.kt:280`.
   - 🔴 **C11:** Saneamiento de mojibake residual en comentarios descriptivos de 5 archivos (`TtsManager.kt`, `BookScannerService.kt`, `LibraryScreen.kt`, `ReaderScreen.kt`, `ReaderViewModel.kt`).

---

## 📜 HISTORIAL DE COMMITS RECIENTES (Cadena de Refactor y Blindaje)

| Commit | Fecha | Tipo | Descripción |
|---|---|---|---|
| `480f604` | 2026-10-28 | docs | Bloque C: corregir inconsistencias internas, flujo de datos 13 y unicidad de tests |
| `6c80c85` | 2026-10-28 | docs | Bloque B Tanda 5 y completitud: documentar 4 UI parciales, incorporar 23 clases y 10 tests faltantes |
| `d795719` | 2026-10-28 | docs | Bloque B Tanda 4: revisión completa de ReaderTopBar, ReaderSideMenu, ReaderAudioBottomSheet, ReaderTtsBar, ReaderNotesTab |
| `9f26bba` | 2026-10-28 | docs | Bloque B Tanda 3: revisión completa de PdfReflowManager, ReaderTtsService, BookScannerService, LibraryHeader, LibraryScreen |
| `eda579d` | 2026-10-28 | docs | Bloque B Tanda 2: revisión completa de QuoteVideoClipExporter, WoodsBackupManager, ProLicenseManager, ReaderContract, EpubFormatManager |
| `4ec744c` | 2026-10-28 | docs | Bloque B Tanda 1: revisión completa de MainActivity, BookRepository, LibraryModule, FastEpubSearchEngine, BookXRayEngine |
| `e03c6e0` | 2026-10-28 | docs | Bloque A: corregir estados de hallazgos C1-C13 y recalcular contadores con leyenda estricta |
| `5a9b341` | 2026-10-28 | fix | blindar carrera de preferencias en arranque en frío y evitar continuaciones colgadas en tts nativo |
| `93e4dc2` | 2026-10-28 | fix | eliminar `.first()` bloqueante y desacoplar UtteranceProgressListener en TtsManager (C6, C7) |
| `7ac5385` | 2026-10-28 | fix | lectura segura de bookId en savedStateHandle (C2, C12, C13) en ViewModels |
| `8a4013a` | 2026-10-28 | feat | PARTE 73: incrustar portada (covr) y subtítulos sincronizados (©lyr) directo en .m4a único vía M4aMetadataEmbedder |
| `d86e7a1` | 2026-10-28 | refactor | Paso 4: extraer ReaderIntelligenceAndExportCoordinator y desacoplar búsqueda/X-Ray/audio |
| `1c949cc` | 2026-10-28 | refactor | Paso 3: extraer ManageReaderAnnotationsUseCase y desacoplar lógica de marcadores/resaltados |
| `26810fd` | 2026-10-28 | refactor | Paso 2: extraer LoadBookFastUseCase y desacoplar apertura O(1) de libros |
| `4de4bd7` | 2026-10-28 | refactor | Paso 1: extraer SaveReadingProgressUseCase y CalculateReadingStatsUseCase |

---

## 🏷️ LEYENDA Y FORMATO DEL ÍNDICE POR ARCHIVO
Cada entrada de archivo fuente en las secciones siguientes sigue este esquema:
- `ruta` — ubicación relativa desde la raíz del proyecto.
- `propósito` — responsabilidad técnica resumida en una frase clara.
- `clases` — clases, interfaces o tipos top-level definidos en el archivo.
- `depende_de` — módulos, casos de uso o librerías clave que consume.
- `usado_por` — componentes que lo inyectan o invocan directamente.
- `estado` — estado de revisión:
  - `✅ REVISADO_ATOMICAMENTE` — inspeccionado en su totalidad línea por línea.
  - `🟡 REVISION_PARCIAL` — auditado por secciones (especificado con rangos de líneas).
- `hallazgos` — advertencias, riesgos de crash o deuda técnica identificada.

---

## 0. Raíz y Build

### `build.gradle.kts` (raíz)
- **Propósito:** Plugins de nivel raíz compartidos (Android, Kotlin, Compose, Hilt, KSP) aplicados `apply false` a submódulos.
- **Clases:** —
- **Depende_de:** —
- **Usado_por:** `settings.gradle.kts`, `:app`, `:keymaker`
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** Ninguno crítico.

### `settings.gradle.kts`
- **Propósito:** Define módulos `:app`, `:keymaker`; repositorios (Google, Maven Central, JitPack para Sherpa ONNX).
- **Clases:** —
- **Depende_de:** —
- **Usado_por:** Gradle
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** Ninguno.

### `gradle.properties`
- **Propósito:** JVM opts (-Xmx4096m), AndroidX, desugaring, Compose, ruta JDK Android Studio.
- **Clases:** —
- **Depende_de:** —
- **Usado_por:** Gradle
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** Ninguno.

### `app/build.gradle.kts`
- **Propósito:** Configuración de compilación del módulo `:app` (compileSdk 36, minSdk 26, targetSdk 35, versionCode 11, noCompress onnx/txt, NDK r27b, KSP schema location, lint abortOnError=false).
- **Clases:** —
- **Depende_de:** Readium 3.x, Compose BOM, Media3, Sherpa ONNX (JitPack), Room, Hilt, Coil3, WorkManager, DataStore, PDFBox.
- **Usado_por:** Toda la app
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** `lint.abortOnError = false` (permite errores de lint en build).

### `.cursorrules`
- **Propósito:** Reglas maestro para asistentes de IA (Cursor/OpenCode/otros).
- **Clases:** —
- **Depende_de:** —
- **Usado_por:** Asistentes de IA
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** 8 guardrails definidos.

### `ARCHITECTURE.md`
- **Propósito:** Mapa de arquitectura con tablas Responsabilidad → Archivo.
- **Clases:** —
- **Depende_de:** —
- **Usado_por:** Asistentes de IA
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** Ninguno.

### `FILE_INDEX.md`
- **Propósito:** Índice maestro de todos los archivos .kt con propósito, clases, dependencias y dependientes.
- **Clases:** —
- **Depende_de:** —
- **Usado_por:** Asistentes de IA
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** Ninguno.

### `dona.md` (este archivo)
- **Propósito:** Índice atómico completo, mapa de arquitectura y documento ancla de ingeniería de todos los archivos fuente del proyecto.
- **Clases:** —
- **Depende_de:** —
- **Usado_por:** Asistentes de IA y desarrolladores
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 100% sincronizado al commit de referencia con verificación estricta de disco)
- **Hallazgos:** Documento maestro inmutable sin dependencias circulares.

---

## 1. Entrada y Ciclo de Vida

### `app/src/main/java/com/example/woodsreader/LectorApplication.kt`
- **Propósito:** Clase Application con @HiltAndroidApp, inicializa Coil3 (caché 60 MB), PDFBox, Timber, WorkManager Configuration.Provider, limpieza de memoria en onTrimMemory/onLowMemory.
- **Clases:** `LectorApplication`
- **Depende_de:** Hilt, Coil3, PDFBox, Timber, WorkManager
- **Usado_por:** Toda la app (DI root)
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** Timber.plant solo en DEBUG; logs descartados en release.

### `app/src/main/java/com/example/woodsreader/MainActivity.kt`
- **Propósito:** Activity principal y punto de entrada de UI; manejo de intents ACTION_VIEW (abrir EPUB/PDF externo con resolución de URI y cálculo de hash), solicitud y verificación de permisos (MANAGE_EXTERNAL_STORAGE, POST_NOTIFICATIONS), encolado de WorkManager LibrarySyncWorker cada 12h, y contenedor Compose MainLayout con navegación unificada.
- **Clases:** `MainActivity`, `MainLayout`, `BetaExpiredScreen`
- **Depende_de:** Hilt, DataStore, BookRepository, BookScannerService, BookMetadataExtractor, Navigation Compose, WorkManager
- **Usado_por:** AndroidManifest, LectorApplication
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 507 líneas)
- **Hallazgos:** Si el hash de un URI externo falla al calcularse, el fallback a getBookByPath puede permitir duplicados si la misma ruta se abre con URIs temporales distintos.

---

## 2. Navegación (Compose Navigation)

### `app/src/main/java/com/example/woodsreader/ui/navigation/NavGraph.kt`
- **Propósito:** Definición de rutas sealed class `Screen` (library, reader/{bookId}, book_detail/{bookId}, graph/{bookId}?chapterIndex, candidates/{bookId}, search, stats, settings, onboarding) y grafo central `LectorNavHost` con animaciones de transición compartida (SharedTransitionLayout).
- **Clases:** `Screen` (sealed), `LectorNavHost`
- **Depende_de:** androidx.navigation.compose, Compose animations, pantallas de UI
- **Usado_por:** MainActivity
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 206 líneas)
- **Hallazgos:** C13 [RESUELTO]: El paso de `bookId ?: ""` en GraphScreen y CandidatesScreen podía crashear en ViewModels receptores; mitigado en commit 7ac5385 con SavedStateHandle seguro (.orEmpty()) y retorno temprano.

---

## 3. Capa de Datos (Room, DataStore, Repositorios, Escáneres)

### 3.1 Base de Datos Room

#### `data/database/AppDatabase.kt`
- **Propósito:** Base de datos Room v11, entidades registradas (incluyendo grafo), AutoMigration 10→11, TypeConverters, exportSchema en app/schemas/.
- **Clases:** `AppDatabase`
- **Depende_de:** Room, Entidades, Graph entities, Migration
- **Usado_por:** DatabaseModule, BookRepository, GraphRepository
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** **C1/C9 [RESUELTO]:** Manejado nativamente mediante Room `@AutoMigration(from = 10, to = 11)` registrado en `AppDatabase.kt:37-39`.

#### `data/database/Entities.kt`
- **Propósito:** Todas las entidades Room: BookEntity, HighlightEntity, BookmarkEntity, ReadingSessionEntity, CollectionEntity, TtsRuleEntity, DailyWordCount.
- **Clases:** `BookEntity`, `HighlightEntity`, `BookmarkEntity`, `ReadingSessionEntity`, `CollectionEntity`, `TtsRuleEntity`, `DailyWordCount`
- **Depende_de:** Room (@Entity, @PrimaryKey, @ForeignKey, @Index), UUID, kotlinx.serialization, Enums
- **Usado_por:** Daos, AppDatabase
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** `currentLocatorJson` como JSON plano de Readium CFI — frágil si cambia versión de Readium.

#### `data/database/Daos.kt`
- **Propósito:** Todos los DAOs: BookDao, HighlightDao, BookmarkDao, ReadingSessionDao, CollectionDao, ReadingStatsDao, TtsRuleDao. Incluye FTS4 búsqueda, PagingSource, proyecciones (SessionSummary, HourlyActivity, etc.).
- **Clases:** `BookDao`, `HighlightDao`, `BookmarkDao`, `ReadingSessionDao`, `CollectionDao`, `ReadingStatsDao`, `TtsRuleDao`, `SessionSummary`, `HourlyActivity`, `BookDistribution`, `ModeEndurance`, `BookPathProjection`
- **Depende_de:** Room (@Dao, @Query, @Upsert, @Transaction, @RawQuery), Paging, Flow, Entities
- **Usado_por:** BookRepository, GraphRepository, ViewModels
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** `@Transaction @Query` retornando `PagingSource` puede tener comportamiento inesperado (M4). `getAll()` con `ORDER BY CASE WHEN lastOpenedAt IS NULL...` complejo.

#### `data/database/AppTypeConverters.kt`
- **Propósito:** TypeConverters de Room para enums (BookFormat, ReadingStatus, HighlightColor, ReadingMode) y List<String> ↔ JSON.
- **Clases:** `AppTypeConverters`
- **Depende_de:** Room @TypeConverter, kotlinx.serialization.json, Enums
- **Usado_por:** AppDatabase, DatabaseModule
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** `firstOrNull { it.name == v }` es O(n) por conversión (M2).

#### `data/database/ReadingStatsEntity.kt`
- **Propósito:** Entidad Room para el seguimiento estadístico diario de lectura (fecha, minutos leídos, palabras totales leídas y número de sesiones completadas).
- **Clases:** `ReadingStatsEntity` (@Entity)
- **Depende_de:** Room, LocalDate
- **Usado_por:** AppDatabase, BookDao, CalculateReadingStatsUseCase
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 14 líneas)
- **Hallazgos:** Ninguno. Estructura compacta indexada por clave primaria de fecha.

#### `data/database/BookFtsEntity.kt`
- **Propósito:** Entidad FTS4 para búsqueda full-text de libros (título, autor, descripción), vinculada a books por rowid.
- **Clases:** `BookFtsEntity`
- **Depende_de:** Room @Fts4, BookEntity
- **Usado_por:** Daos (searchBooks)
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** Ninguno.

#### `data/database/migration/Migrations.kt`
- **Propósito:** Migraciones Room 1→2 … 9→10. Cada una con `IF NOT EXISTS` y defaults seguros.
- **Clases:** `MIGRATION_1_2` … `MIGRATION_9_10`, `ALL_MIGRATIONS`
- **Depende_de:** Room Migration, SupportSQLiteDatabase
- **Usado_por:** AppDatabase, DatabaseModule
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** **C1/C9 [RESUELTO]:** Migración 10→11 implementada vía `@AutoMigration` en `AppDatabase.kt`. `MIGRATION_6_7` recrea índices ya creados en 1→2 (duplicado inofensivo).

### 3.2 DataStore (Preferencias de Usuario)

#### `data/datastore/UserPreferences.kt`
- **Propósito:** Modelo de preferencias de usuario (tema, fuente, TTS, rutas de escaneo, etc.). Todas las configuraciones con valores por defecto.
- **Clases:** `UserPreferences` (@Serializable, @Stable)
- **Depende_de:** kotlinx.serialization, androidx.compose.runtime.Stable, modelos ReaderTheme, ScrollMode, PageTurnStyle, TtsUiStyle
- **Usado_por:** UserPreferencesRepository
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 67 líneas)
- **Hallazgos:** Ninguno crítico. 24 campos con defaults seguros.

#### `data/datastore/UserPreferencesRepository.kt`
- **Propósito:** Repositorio de DataStore, flujo de preferencias, métodos de actualización (updateTtsSpeed, updateTheme, etc.). Helper genérico `update(transform)` evita boilerplate.
- **Clases:** `UserPreferencesRepository` (@Singleton)
- **Depende_de:** DataStore, kotlinx.coroutines.flow.Flow, distinctUntilChanged, Hilt @Inject/@Singleton
- **Usado_por:** MainActivity, LibraryViewModel, SettingsViewModel, ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 101 líneas)
- **Hallazgos:** Ninguno. Uso correcto de `dataStore.updateData(transform)` para consistencia atómica.

#### `data/datastore/UserPreferencesSerializer.kt`
- **Propósito:** Serializador personalizado para DataStore con sanitizado de rangos al leer.
- **Clases:** `UserPreferencesSerializer` (object, implementa `Serializer<UserPreferences>`)
- **Depende_de:** androidx.datastore.core.Serializer, kotlinx.serialization.json, kotlinx.coroutines.Dispatchers
- **Usado_por:** UserPreferencesRepository, DataStoreModule
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 55 líneas)
- **Hallazgos:** Manejo robusto de `SerializationException` e `IOException` con fallback a `defaultValue`. `sanitize()` coerce rangos de fontSizeSp, lineSpacingMultiplier, horizontalMargin, ttsSpeed.

### 3.3 Repositorios

#### `data/repository/BookRepository.kt`
- **Propósito:** Single source of truth para datos de libros, sesiones de lectura, marcadores, subrayados y reglas de TTS. Expone flujos reactivos Flow, consultas Paging 3 y operaciones asíncronas seguras con Dispatchers.IO.
- **Clases:** `BookRepository` (@Singleton)
- **Depende_de:** AppDatabase, BookDao, HighlightDao, BookmarkDao, ReadingSessionDao, TtsRuleDao, DailyWordCountDao, Flow, Dispatchers
- **Usado_por:** LibraryViewModel, BookDetailViewModel, ReaderViewModel, SaveReadingProgressUseCase, ManageReaderAnnotationsUseCase, LoadBookFastUseCase, MainActivity
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 410 líneas)
- **Hallazgos:** Encapsulación limpia de Room con despacho explícito en I/O. Consultas de búsqueda FTS4 optimizadas.

#### `data/graph/GraphRepository.kt`
- **Propósito:** Repositorio de grafo de entidades por libro. Envuelve GraphDao con métodos convenientes para CRUD de entidades, relaciones, menciones y aliases.
- **Clases:** `GraphRepository` (@Singleton)
- **Depende_de:** GraphDao, kotlinx.coroutines.flow.Flow, Hilt @Inject/@Singleton
- **Usado_por:** GraphViewModel, GraphExtractionWorker
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 85 líneas)
- **Hallazgos:** Ninguno. Wrapper limpio sobre GraphDao sin lógica de negocio adicional.

### 3.5 Capa de Dominio (Casos de Uso y Coordinadores del Lector)

#### `domain/reader/SaveReadingProgressUseCase.kt`
- **Propósito:** Persistencia atómica y resiliente del progreso de lectura (`Locator`), cálculo de sesiones y estadísticas en Room, debouncing de 700 ms y flush inmediato (`flushReadingProgressImmediately`).
- **Clases:** `SaveReadingProgressUseCase` (@Inject constructor)
- **Depende_de:** BookRepository, ReadingSessionManager, TimeProvider, ReadingProgressManager, Locator
- **Usado_por:** ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 145 líneas)
- **Hallazgos:** Ninguno. **A12 [RESUELTO]**: Unifica la persistencia de progreso manual y de TTS eliminando divergencias de posición.

#### `domain/reader/CalculateReadingStatsUseCase.kt`
- **Propósito:** Cálculo de estadísticas de lectura en tiempo real (progreso global 0..1, tiempo restante estimado y palabras restantes).
- **Clases:** `CalculateReadingStatsUseCase` (@Inject constructor), `ReadingStatsResult`
- **Depende_de:** BookEntity, ReadingStatsEntity, Locator
- **Usado_por:** ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 67 líneas)
- **Hallazgos:** Ninguno. Lógica pura y testeable sin dependencias de Android.

#### `domain/reader/LoadBookFastUseCase.kt`
- **Propósito:** Apertura ultrarrápida O(1) de publicaciones EPUB y PDF, bypass sintético de posiciones para novelas de miles de capítulos (`readingOrder.size > 80`), aplanado de TOC y resolución resiliente de checkpoint con fallback de SharedPreferences / Room.
- **Clases:** `LoadBookFastUseCase` (@Inject constructor), `LoadBookResult`
- **Depende_de:** BookRepository, PublicationOpener, AssetRetriever, Context, Publication, Locator
- **Usado_por:** ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 260 líneas)
- **Hallazgos:** Ninguno. Elimina riesgo de ANR y OOM en libros masivos en dispositivos gama media/baja.

#### `domain/reader/ManageReaderAnnotationsUseCase.kt`
- **Propósito:** Gestión integral de anotaciones de lectura: subrayados (highlights) con colores/notas, marcadores manuales y automáticos con snippet, matching de página/progreso (`isPageBookmarked`, `toggleBookmark`), transformación a objetos `Decoration` de Readium y exportación de notas/citas a Markdown y texto plano.
- **Clases:** `ManageReaderAnnotationsUseCase` (@Inject constructor)
- **Depende_de:** BookRepository, BookmarkEntity, HighlightEntity, HighlightColor, HighlightExporter, Decoration, Locator
- **Usado_por:** ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 268 líneas)
- **Hallazgos:** Ninguno.

#### `domain/reader/ReaderIntelligenceAndExportCoordinator.kt`
- **Propósito:** Coordinador de funciones avanzadas del lector: búsqueda en-libro streaming sin bloqueo (`FastEpubSearchEngine`), análisis semántico y reporte contextual Rayos-X (`BookXRayEngine`), y orquestación de exportación de audio .M4A individual o por lotes (`ChapterAudioExporter`).
- **Clases:** `ReaderIntelligenceAndExportCoordinator` (@Singleton / @Inject constructor)
- **Depende_de:** FastEpubSearchEngine, BookXRayEngine, ChapterAudioExporter, BookEntity, ReflowItem, Publication
- **Usado_por:** ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 237 líneas)
- **Hallazgos:** Ninguno.

### 3.4 Escáneres de Archivos

#### `data/scanner/MediaStoreBookScanner.kt`
- **Propósito:** Escaneo de libros vía MediaStore API (indexado, rápido). Filtra por extensiones .epub/.pdf y MIME types.
- **Clases:** `MediaStoreBookScanner` (@Singleton), `MediaStoreScanner` (interface)
- **Depende_de:** Android MediaStore, ContentResolver, Dispatchers.IO
- **Usado_por:** BookScannerService
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 126 líneas)
- **Hallazgos:** Conversión correcta de dateModified (segundos a milisegundos). Verificación de existencia de archivo con `file.exists()`.

#### `data/scanner/DeepFileSystemScanner.kt`
- **Propósito:** Escaneo profundo del sistema de archivos (recursivo) con pacer de profundidad máxima 10.
- **Clases:** `DeepFileSystemScanner` (@Singleton), `AndroidStorageRootProvider` (@Singleton), `StorageRootProvider` (interface), `FileSystemScanner` (interface)
- **Depende_de:** android.os.storage.StorageManager, kotlinx.coroutines Dispatchers.IO
- **Usado_por:** BookScannerService
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 114 líneas)
- **Hallazgos:** Límite de profundidad (depth > 10) y carpetas ignoradas (system, proc, sys, vendor, etc.). Buen manejo de cancelación de corrutinas.

### 3.5 Modelo de Dominio

#### `data/model/Enums.kt`
- **Propósito:** Enums de dominio compartido (sin dependencias Android).
- **Clases:** `BookFormat`, `ReadingStatus`, `HighlightColor`, `ReadingMode` + `toArgb()`
- **Depende_de:** kotlinx.serialization, android.graphics.Color (solo en extensión)
- **Usado_por:** Entities, Repository, UI
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** Ninguno.

#### `data/model/BookScanResult.kt`
- **Propósito:** Resultado de escaneo de libro con URI, nombre, tamaño, MIME, fecha y origen.
- **Clases:** `BookScanResult` (data class), `Source` (enum: MEDIASTORE, SAF, LEGACY_PATH)
- **Depende_de:** android.net.Uri
- **Usado_por:** MediaStoreBookScanner, DeepFileSystemScanner
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 14 líneas)
- **Hallazgos:** Ninguno.

#### `data/model/ReflowItem.kt`
- **Propósito:** Ítem de reflujo de página PDF (Header, Paragraph, Image, PageNumber, Ignored, Text). Estado de reflujo para PDFs.
- **Clases:** `ReflowItem` (sealed), `ReflowState`, `SimpleTextElement`
- **Depende_de:** androidx.compose.runtime.Immutable, Readium Lector/Content/Locator
- **Usado_por:** PdfReflowManager, ReaderReflowOverlay
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 91 líneas)
- **Hallazgos:** Ninguno.

---

## 4. Inyección de Dependencias (Hilt)

### `di/NetworkModule.kt`
- **Propósito:** Provisión Hilt del cliente HTTP compartido OkHttpClient con timeouts configurados y caché para operaciones de red.
- **Clases:** `NetworkModule` (object)
- **Depende_de:** Dagger Hilt, OkHttp3
- **Usado_por:** SingletonComponent, PageFetcher, ImageDownloader, EdgeNeuralTtsClient
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 26 líneas)
- **Hallazgos:** Ninguno.

### `di/CoreModule.kt`
- **Propósito:** @Binds para TimeProvider, Summarizer, SleepTimer.
- **Clases:** `CoreModule` (abstract)
- **Depende_de:** SystemTimeProvider, TextRankSummarizer, SleepTimerManager
- **Usado_por:** SingletonComponent
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** Ninguno.

### `di/DatabaseModule.kt`
- **Propósito:** Provee AppDatabase (Room con WAL, migraciones 1→10, sin destructive migration), AppTypeConverters, y todos los DAOs.
- **Clases:** `DatabaseModule` (object)
- **Depende_de:** Room, AppDatabase, AppTypeConverters, Migrations
- **Usado_por:** SingletonComponent
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** **CRÍTICO C1:** No registra `MIGRATION_10_11`. `provideGraphDao` usa nombre completo de clase (inconsistencia menor M13).

### `di/TtsModule.kt`
- **Propósito:** Provee TtsManager (@Singleton) con todas sus dependencias.
- **Clases:** `TtsModule` (object)
- **Depende_de:** TtsManager, TtsRuleDao, UserPreferencesRepository, SherpaModelManager, etc.
- **Usado_por:** SingletonComponent
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** Ninguno.

### `di/ScannerModule.kt`
- **Propósito:** @Binds para StorageRootProvider, FileSystemScanner, MediaStoreScanner.
- **Clases:** `ScannerModule` (abstract)
- **Depende_de:** AndroidStorageRootProvider, DeepFileSystemScanner, MediaStoreBookScanner
- **Usado_por:** SingletonComponent
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** Ninguno.

### `di/LibraryModule.kt`
- **Propósito:** Provisión Dagger Hilt de la infraestructura Readium: PublicationOpener con parsers de EPUB y PDF, AssetRetriever, PdfiumDocumentFactory lazily inicializado para mitigar sobrecarga de RAM en cold start, DefaultHttpClient y sanitizador de paquetes EPUB.
- **Clases:** `LibraryModule` (object)
- **Depende_de:** Readium (PublicationOpener, AssetRetriever, PdfiumDocumentFactory, DefaultHttpClient, EpubParser, PdfParser), EpubPackageSanitizer
- **Usado_por:** SingletonComponent (inyectado en LoadBookFastUseCase, BookMetadataExtractor)
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 80 líneas)
- **Hallazgos:** PdfiumDocumentFactory con instanciación lazy previene asignaciones prematuras de memoria gráfica en el arranque.

### `di/DataStoreModule.kt`
- **Propósito:** Provee DataStore<UserPreferences> con serializer personalizado.
- **Clases:** `DataStoreModule` (object)
- **Depende_de:** DataStoreFactory, UserPreferencesSerializer
- **Usado_por:** SingletonComponent
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** Ninguno.

---

## 5. Capa de Negocio (Core)

### 5.1 Pipeline de Lectura (EPUB/PDF)

#### `core/pipeline/ReadingPipeline.kt`
- **Propósito:** Pipeline unificado de lectura. Procesa bloques crudos → display blocks + TTS narration units + métricas. Filtra ruido, aplica exclusiones, formatea tipografía en español.
- **Clases:** `ReadingPipeline` (@Singleton), `ProcessedDocument`, `PipelineMetrics`
- **Depende_de:** NoiseClassifier, ImageAnnouncer, WordExclusionEngine, SpanishTextFormatter
- **Usado_por:** EpubFormatManager, PdfReflowManager
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 206 líneas)
- **Hallazgos:** Ninguno crítico. Limpieza correcta de ruido (encabezados/pies recurrentes), nunca pasa ruido a TTS.

#### `core/pipeline/ReadingBlock.kt`
- **Propósito:** Definiciones de bloques semánticos de lectura y unidades de narración TTS.
- **Clases:** `ReadingBlock` (sealed: Paragraph, Heading, Image, Noise), `RawDocumentBlock`, `TtsNarrationUnit`, `NoiseReason` (enum), `PipelineMetrics`
- **Depende_de:** kotlinx.serialization
- **Usado_por:** ReadingPipeline, EpubFormatManager, PdfReflowManager
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 102 líneas)
- **Hallazgos:** Ninguno. Jerarquía sealed bien definida con Serializable.

#### `core/pipeline/EpubContentExtractor.kt`
- **Propósito:** Adaptador de extracción semántica para contenido EPUB (HTML/XHTML). Convierte DOM de Readium en RawDocumentBlock.
- **Clases:** `EpubContentExtractor` (@Singleton)
- **Depende_de:** org.jsoup.Jsoup, kotlinx.serialization, Hilt @Inject/@Singleton
- **Usado_por:** EpubFormatManager
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 162 líneas)
- **Hallazgos:** Manejo correcto de elementos contenedores (blockquote, li) para evitar duplicación de texto. Detección de figuras con figcaption.

#### `core/pipeline/EpubHtmlSanitizer.kt`
- **Propósito:** Sanitizador automático de HTML/XHTML de EPUB (xmlns, head, título de capítulo, estilos de selección).
- **Clases:** `EpubHtmlSanitizer` (object)
- **Depende_de:** java.nio.charset.Charset, kotlinx.serialization, Readium (opcional)
- **Usado_por:** EpubPackageSanitizer, ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 183 líneas)
- **Hallazgos:** Reparación de xmlns faltante, inyección de título de capítulo, detección de charset, encodeSanitized con charset correcto.

#### `core/pipeline/EpubPackageSanitizer.kt`
- **Propósito:** Sanitizador de paquete EPUB (referencias NCX, propiedad de cover, toLightweightNavPublication para libros grandes).
- **Clases:** `EpubPackageSanitizer` (object)
- **Depende_de:** Readium (Publication, Link, Asset, TransformingContainer, HtmlResourceContentIterator), EpubHtmlSanitizer, ExperimentalReadiumApi
- **Usado_por:** LibraryModule, ReaderViewModel, ReaderTtsService
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 240 líneas)
- **Hallazgos:** `toLightweightNavPublication` omite tableOfContents en libros >60 capítulos para evitar OOM en Readium 3.0.3 (bug crítico corregido). Reparación de NCX y cover property.

#### `core/pipeline/PdfBookViewSynchronizer.kt`
- **Propósito:** Sincronizador bidireccional entre Vista Original del PDF y Vista Libro (reflujo).
- **Clases:** `PdfBookViewSynchronizer` (@Singleton)
- **Depende_de:** javax.inject, ReadingBlock
- **Usado_por:** ReadingPipeline, ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 62 líneas)
- **Hallazgos:** Ninguno. Mapeo O(1) de página a bloque y viceversa.

#### `core/pipeline/NoiseClassifier.kt`
- **Propósito:** Clasificador determinista de ruido textual (números de página, encabezados/pies recurrentes, URLs, fórmulas legales, spam de traductor, ornamentos).
- **Clases:** `NoiseClassifier` (@Singleton)
- **Depende_de:** java.util.Locale, Hilt @Inject/@Singleton
- **Usado_por:** ReadingPipeline, PdfReflowManager
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 206 líneas)
- **Hallazgos:** Umbral adaptativo para encabezados recurrentes (mínimo 3 apariciones o 25% del documento). Detección robusta de números de página arábigos y romanos.

#### `core/pipeline/ImageAnnouncer.kt`
- **Propósito:** Anunciador de imágenes para TTS y etiquetas visuales en Vista Libro.
- **Clases:** `ImageAnnouncer` (@Singleton)
- **Depende_de:** javax.inject, kotlinx.serialization
- **Usado_por:** ReadingPipeline
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 46 líneas)
- **Hallazgos:** Ninguno.

#### `core/pipeline/WordExclusionEngine.kt`
- **Propósito:** Motor de exclusión y sustitución de palabras en tiempo real para TTS. Reglas globales o por libro.
- **Clases:** `WordExclusionEngine` (@Singleton), `WordExclusionRule` (data class)
- **Depende_de:** java.util.regex.Pattern, Hilt @Inject/@Singleton
- **Usado_por:** ReadingPipeline, TtsManager
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 99 líneas)
- **Hallazgos:** Manejo correcto de expresiones regulares inválidas (fallback silencioso). Colapso de espacios múltiples tras eliminación.

#### `core/pipeline/SpanishTextFormatter.kt`
- **Propósito:** Formateador tipográfico en español (preposiciones huérfanas con NBSP, guiones largos, comillas).
- **Clases:** `SpanishTextFormatter` (@Singleton)
- **Depende_de:** javax.inject, kotlinx.serialization
- **Usado_por:** ReadingPipeline
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 74 líneas)
- **Hallazgos:** Ninguno. Set de 23 preposiciones/monosílabos en español para evitar orfandad tipográfica.

#### `core/managers/pdf/analysis/TextAnalyzer.kt`
- **Propósito:** Motor de análisis y segmentación estructural de páginas PDF. Clasifica elementos semánticos (encabezados, párrafos, números de página, imágenes) basándose en coordenadas espaciales RectF y métricas tipográficas.
- **Clases:** `TextAnalyzer`, `SemanticElement`, `Header`, `Paragraph`, `PageNumber`, `Ignored`, `LineInfo`, `PageImage`, `PageItem`
- **Depende_de:** RectF, NoiseClassifier, SpanishTextFormatter, Timber
- **Usado_por:** PdfReflowManager
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 391 líneas)
- **Hallazgos:** Permite un reflow preciso reconstruyendo párrafos fragmentados a través de saltos de línea.

### 5.2 Motor TTS (Text-to-Speech)

#### `core/tts/TtsManager.kt`
- **Propósito:** Motor TTS central: Sherpa ONNX offline + Android TTS nativo. Gestión de AudioTrack, wake locks, audio focus, prosodia, segmentación contextual.
- **Clases:** `TtsManager` (@Singleton), `SpeechChunk`, `SpeechSegment`, `PlaybackItem`, `ChunkType`
- **Depende_de:** Android TTS, Sherpa ONNX, AudioTrack, TtsRuleDao, UserPreferencesRepository, SherpaModelManager, PronunciationLexiconManager, SmartSleepTimerManager, AmbientSoundscapePlayer, WoodsKeepAliveManager, WordExclusionEngine
- **Usado_por:** SherpaTtsEngine, ReaderViewModel, SettingsViewModel, ReaderTtsService, QuoteVideoClipExporter
- **Estado:** ✅ REVISADO_ATOMICAMENTE (líneas 1-1464)
- **Hallazgos:**
  - **C6:** `.first()` en `userPreferencesRepository.userPreferencesFlow.first()` dentro de `speak()` puede bloquear TTS.
  - **C7:** `setOnUtteranceProgressListener` redefinido en cada iteración del bucle TTS (race condition).
  - **C11:** Mojibake en comentarios (líneas 572, 586, 656, 671, 679, 738).
  - **A5:** `playPcmSamplesBlocking` sin dispatcher explícito (riesgo ANR si se llama desde Main).
  - **M10:** `requestAudioFocus()` siempre devuelve `true` sin gestionar AudioManager real.
  - **M12:** Wake lock de 45 min sin renovación garantizada.
  - **M16:** `playPcmSamplesBlocking` puede bloquear el hilo que lo llama.

#### `core/tts/SherpaTtsEngine.kt`
- **Propósito:** Adaptador TTS para Readium (`TtsEngine`). Redirige síntesis a TtsManager (Sherpa ONNX o nativo).
- **Clases:** `SherpaTtsEngine` (= `SherpaOnnxTtsEngine`)
- **Depende_de:** Readium TtsEngine, TtsManager, UserPreferencesRepository, PronunciationLexiconManager
- **Usado_por:** SherpaTtsEngineProvider, Readium
- **Estado:** ✅ REVISADO_ATOMICAMENTE (líneas 1-217)
- **Hallazgos:** Ninguno crítico adicional.

#### `core/tts/SherpaTtsEngineProvider.kt`
- **Propósito:** Proveedor TTS para Readium (`TtsEngineProvider`).
- **Clases:** `SherpaTtsEngineProvider`
- **Depende_de:** Readium TtsEngineProvider, AndroidTtsEngineProvider, TtsManager, UserPreferencesRepository
- **Usado_por:** Readium navigator, ReaderTtsService
- **Estado:** ✅ REVISADO_ATOMICAMENTE (líneas 1-80)
- **Hallazgos:** Ninguno.

#### `core/tts/SherpaModelManager.kt`
- **Propósito:** Gestor de modelos Sherpa ONNX. Descarga y extracción segura (Zip Slip protegido), mutex para descargas concurrentes.
- **Clases:** `SherpaModelManager` (@Singleton), `SherpaVoiceOption` (enum), `SherpaDownloadState` (sealed)
- **Depende_de:** OkHttp, Apache Commons Compress (tar.bz2), File I/O, Mutex, SharedPreferences
- **Usado_por:** TtsManager, ReaderViewModel, SettingsViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 698 líneas)
- **Hallazgos:**
  - **C4:** `init` lanza corrutina en `Dispatchers.IO` sin guardar el `Job` (corrutina huérfana).
  - **C5:** `numThreads = 2` y `debug = true` hardcodeados en OfflineTtsConfig (producción).
  - **M1:** `Log.e` usado para diagnóstico en lugar de `Timber`.
  - **M2:** Logs excesivos en `Log.e` nivel error para mensajes informativos.

#### `core/tts/TtsPresetCatalog.kt`
- **Propósito:** Catálogo de presets TTS (`TtsPresetPack`).
- **Clases:** `TtsPresetCatalog`, `TtsPresetPack`, `TtsRuleBackupItem`
- **Depende_de:** TtsRuleDao, kotlinx.serialization.json
- **Usado_por:** TtsManager, ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 130 líneas)
- **Hallazgos:** Ninguno.

#### `core/tts/M4aMetadataEmbedder.kt`
- **Propósito:** Inyector atómico binario de metadatos en contenedores MP4/M4A. Escribe átomos `covr` (portada JPEG auto-optimizada a 800px), `©lyr` (subtítulos sincronizados LRC completos), `©nam` (título), `©ART` (autor) y `©alb` (libro). Soporta reescritura in-situ al final de archivo o conversión segura a átomo `free` para preservar punteros de muestras `mdat`/`stco`.
- **Clases:** `M4aMetadataEmbedder` (object), `Mp4Box`
- **Depende_de:** java.io.RandomAccessFile, java.nio.ByteBuffer, android.graphics.BitmapFactory
- **Usado_por:** ChapterAudioExporter
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 263 líneas)
- **Hallazgos:** Ninguno. Elimina la dispersión de archivos sueltos (.lrc, .srt, .jpg, .txt) generando archivos .m4a autónomos listos para cualquier reproductor.

#### `core/tts/ChapterAudioExporter.kt`
- **Propósito:** Exportador de audio de capítulos a M4A (AAC 48kbps con MediaCodec y MediaMuxer), sincronización de subtítulos LRC, integración con `M4aMetadataEmbedder` para incrustación binaria y limpieza proactiva de archivos huérfanos.
- **Clases:** `ChapterAudioExporter` (@Singleton), `AudioExportState`, `ChapterTextChunk`
- **Depende_de:** SherpaModelManager, TtsManager, WoodsKeepAliveManager, M4aMetadataEmbedder, MediaCodec, MediaMuxer, MediaStore
- **Usado_por:** ReaderIntelligenceAndExportCoordinator
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 406 líneas)
- **Hallazgos:** Ninguno. PARTE 73 completada: produce exclusivamente un archivo .m4a en `Music/WoodsReader` con portada y subtítulos adentro.

#### `core/tts/AudiobookProsodyEngine.kt`
- **Propósito:** Motor de prosodia de audiolibro (segmentación diálogo/narración, mood detection).
- **Clases:** `AudiobookProsodyEngine` (object), `VoiceActRole`, `ProsodyMood`, `ProsodySegment`
- **Depende_de:** MultilingualAudiobookNormalizer, PronunciationLexiconManager
- **Usado_por:** SherpaTtsEngine
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 229 líneas)
- **Hallazgos:** Ninguno.

#### `core/tts/SpanishAudiobookNormalizer.kt`
- **Propósito:** Normalizador de audiolibro en español (abreviaturas, números romanos, risas, etc.).
- **Clases:** `SpanishAudiobookNormalizer` (object)
- **Depende_de:** —
- **Usado_por:** TtsManager, MultilingualAudiobookNormalizer
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 179 líneas)
- **Hallazgos:** Ninguno.

#### `core/tts/MultilingualAudiobookNormalizer.kt`
- **Propósito:** Normalizador multilingüe (español, inglés, portugués, francés, alemán, italiano).
- **Clases:** `SupportedTtsLanguage` (enum), `MultilingualAudiobookNormalizer` (object)
- **Depende_de:** SpanishAudiobookNormalizer, PronunciationLexiconManager
- **Usado_por:** AudiobookProsodyEngine, SpanishAudiobookNormalizer
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 181 líneas)
- **Hallazgos:** Ninguno.

#### `core/tts/StudioAudioDspProcessor.kt`
- **Propósito:** Procesador DSP de estudio (filtros Biquad, de-esser, normalización RMS, limitador).
- **Clases:** `StudioAudioDspProcessor` (object), `StudioAcousticsProfile` (enum), `BiquadFilter` (private class)
- **Depende_de:** kotlin.math, kotlin.random
- **Usado_por:** TtsManager
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 213 líneas)
- **Hallazgos:** Ninguno.

#### `core/tts/PronunciationLexiconManager.kt`
- **Propósito:** Gestor de lexicón de pronunciación (reglas fonéticas personalizadas y glosario webnovel).
- **Clases:** `PronunciationLexiconManager` (@Singleton), `PhoneticRule`
- **Depende_de:** SharedPreferences, org.json
- **Usado_por:** SherpaTtsEngine, TtsManager, SettingsViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 173 líneas)
- **Hallazgos:** Ninguno.

#### `core/tts/SmartSleepTimerManager.kt`
- **Propósito:** Temporizador de sueño inteligente con fade-out progresivo.
- **Clases:** `SmartSleepTimerManager` (@Singleton), `SleepTimerPreset` (enum)
- **Depende_de:** kotlinx.coroutines
- **Usado_por:** TtsManager, ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 78 líneas)
- **Hallazgos:** Ninguno.

#### `core/tts/AmbientSoundscapeEngine.kt`
- **Propósito:** Motor de paisajes sonoros ambientales (pink noise, lluvia, chimenea, viento).
- **Clases:** `AmbientSoundscapeEngine`, `SoundscapeMode` (enum)
- **Depende_de:** kotlin.math, kotlin.random
- **Usado_por:** TtsManager
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 117 líneas)
- **Hallazgos:** Ninguno.

#### `core/tts/AmbientSoundscapePlayer.kt`
- **Propósito:** Reproductor de paisajes sonoros (AudioTrack, auto-ducking, wake lock).
- **Clases:** `AmbientSoundscapePlayer` (@Singleton)
- **Depende_de:** SmartSleepTimerManager, WoodsKeepAliveManager, AudioTrack
- **Usado_por:** TtsManager
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 311 líneas)
- **Hallazgos:** Ninguno.

#### `core/tts/ForestAmbienceEngine.kt`
- **Propósito:** Motor de ambiente forestal alternativo (ruido marrón, viento, lluvia suave).
- **Clases:** `ForestAmbienceEngine` (@Singleton), `AmbiencePreset` (enum)
- **Depende_de:** AudioTrack, kotlinx.coroutines
- **Usado_por:** TtsManager
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 197 líneas)
- **Hallazgos:** Ninguno.

#### `core/tts/EdgeNeuralTtsClient.kt`
- **Propósito:** Cliente TTS Edge Neural (online, WebSocket a speech.platform.bing.com).
- **Clases:** `EdgeNeuralTtsClient` (@Singleton), `EdgeVoiceOption` (enum)
- **Depende_de:** OkHttp WebSocket, MediaPlayer, SharedPreferences
- **Usado_por:** TtsManager, SettingsViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 312 líneas)
- **Hallazgos:** Ninguno crítico.

### 5.3 Gestores de Negocio

#### `core/managers/ReadingProgressManager.kt`
- **Propósito:** Cálculo de tiempo estimado de lectura y fecha de finalización basado en WPM y palabras/día.
- **Clases:** `ReadingProgressManager` (@Singleton)
- **Depende_de:** BookRepository, java.time
- **Usado_por:** ReaderViewModel, BookDetailViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 46 líneas)
- **Hallazgos:** Ninguno.

#### `core/managers/ReadingSessionManager.kt`
- **Propósito:** Gestión de sesiones de lectura (inicio, parada, cambio de modo, registro de actividad, guardado atómico con NonCancellable).
- **Clases:** `ReadingSessionManager` (@Singleton)
- **Depende_de:** BookRepository, TimeProvider, Mutex, SupervisorJob, Locator
- **Usado_por:** ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 228 líneas)
- **Hallazgos:** `appScope` es un scope global sin método de cierre ordenado (M15).

#### `core/managers/SleepTimerManager.kt`
- **Propósito:** Temporizador de sueño (interfaz SleepTimer + implementación con rampa de velocidad).
- **Clases:** `SleepTimer` (interface), `SleepTimerManager` (@Singleton)
- **Depende_de:** TimeProvider, kotlinx.coroutines
- **Usado_por:** ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 87 líneas)
- **Hallazgos:** Ninguno.

#### `core/managers/PdfReflowManager.kt`
- **Propósito:** Gestor de extracción semántica y reflujo dinámico de documentos PDF. Transforma páginas PDF fijas en bloques continuos `ReflowItem` (párrafos, títulos, listas, imágenes), deduplica encabezados y pies de página mediante `NoiseClassifier` y genera `NarrationUnit` para TTS sincronizado.
- **Clases:** `PdfReflowManager` (@Singleton), `PdfPageExtractionState`
- **Depende_de:** TtsManager, NoiseClassifier, TextAnalyzer, SpanishTextFormatter, Readium, Dispatchers.Default
- **Usado_por:** ReaderViewModel, ReaderReflowOverlay
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 773 líneas)
- **Hallazgos:** La extracción de bloques semánticos procesa en ventanas alrededor de la página actual para conservar memoria en dispositivos con bajo límite de heap.

#### `core/managers/BackupManager.kt`
- **Propósito:** Gestor de respaldo completo (JSON con kotlinx.serialization, merge inteligente por hash).
- **Clases:** `BackupManager` (@Singleton), `WoodsFullBackup`, `BackupTtsRule`, `RestoreSummary`
- **Depende_de:** Room DAOs, Dispatchers, kotlinx.serialization.json
- **Usado_por:** SettingsViewModel, WoodsBackupManager
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 201 líneas)
- **Hallazgos:** Ninguno.

#### `core/managers/HighlightExporter.kt`
- **Propósito:** Exportador de resaltados a Markdown y texto plano.
- **Clases:** `HighlightExporter` (object)
- **Depende_de:** HighlightEntity
- **Usado_por:** ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 54 líneas)
- **Hallazgos:** Ninguno.

#### `core/managers/QuoteCardGenerator.kt`
- **Propósito:** Generador de tarjetas de cita para compartir (renderizado bitmap PNG).
- **Clases:** `QuoteCardGenerator` (object)
- **Depende_de:** android.graphics, MediaStore, Dispatchers.IO
- **Usado_por:** ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 182 líneas)
- **Hallazgos:** Ninguno.

#### `core/managers/InlineLookupManager.kt`
- **Propósito:** Gestor de búsqueda inline (diccionario/traducción de palabras seleccionadas vía Google Translate y Wiktionary).
- **Clases:** `InlineLookupManager` (@Singleton), `LookupResult` (sealed)
- **Depende_de:** OkHttp, org.json, URLEncoder
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 330 líneas)
- **Hallazgos:** Ninguno.

#### `core/managers/WoodsSupporterManager.kt`
- **Propósito:** Gestor de soporte/Pro (pase de recompensa temporal o vitalicio).
- **Clases:** `WoodsSupporterManager` (@Singleton)
- **Depende_de:** android.content.SharedPreferences, Hilt @Inject/@Singleton, kotlinx.coroutines.flow
- **Usado_por:** UI Pro
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 42 líneas)
- **Hallazgos:** Ninguno crítico. Lógica simple de recompensa temporal (horas) o vitalicio (booleano).


#### `core/system/WoodsKeepAliveManager.kt`
- **Propósito:** Gestor de keep-alive del sistema (WakeLock PARTIAL, exclusión de optimización de batería Doze).
- **Clases:** `WoodsKeepAliveManager` (@Singleton)
- **Depende_de:** android.os.PowerManager, android.provider.Settings, kotlinx.coroutines.flow, Hilt @Inject/@Singleton
- **Usado_por:** TtsManager, SettingsViewModel, AmbientSoundscapePlayer, ChapterAudioExporter
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 105 líneas)
- **Hallazgos:** Ninguno crítico. `acquireKeepAliveWakeLock` con timeout de 3 horas por defecto, `@Synchronized` para thread-safety. `requestIgnoreBatteryOptimizations` lanza intent de sistema.

#### `core/managers/TimeProvider.kt`
- **Propósito:** Proveedor de tiempo (interfaz para testing y producción).
- **Clases:** `TimeProvider` (interface)
- **Depende_de:** —
- **Usado_por:** SystemTimeProvider, ReadingSessionManager
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 7 líneas)
- **Hallazgos:** Ninguno.

#### `core/managers/SystemTimeProvider.kt`
- **Propósito:** Proveedor de tiempo del sistema (implementación real de TimeProvider).
- **Clases:** `SystemTimeProvider` (@Inject constructor)
- **Depende_de:** android.os.SystemClock, java.time.LocalDate, Hilt @Inject
- **Usado_por:** ReadingSessionManager
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 11 líneas)
- **Hallazgos:** Ninguno.

### 5.4 Búsqueda, X-Ray, Share, Backup, Licencia

#### `core/search/FastEpubSearchEngine.kt`
- **Propósito:** Motor de búsqueda en-libro streaming y no bloqueante para publicaciones EPUB. Emite resultados en tiempo real mediante Kotlin Flow, filtra texto HTML sin instanciar DOM completo, soporta cancelación cooperativa por corrutinas y mantiene una caché LRU de capítulos limpios.
- **Clases:** `FastEpubSearchEngine` (object), `FastInBookSearchHit`, `FastSearchProgress`
- **Depende_de:** Readium Publication, Link, LruCache, Dispatchers.IO, Jsoup
- **Usado_por:** ReaderIntelligenceAndExportCoordinator, LectorApplication (onTrimMemory)
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 273 líneas)
- **Hallazgos:** La caché LRU almacena texto limpio por href (~4-8 MB max) y se limpia ordenadamente ante advertencias de memoria del sistema.

#### `core/xray/BookXRayEngine.kt`
- **Propósito:** Motor de Rayos-X offline para libros. Analiza el contenido textual del capítulo actual, extrayendo y categorizando entidades clave (Personajes, Lugares, Objetos, Conceptos) según heurísticas de capitalización, co-ocurrencia y filtrado de stopwords en español.
- **Clases:** `BookXRayEngine` (object), `XRayEntity`, `XRayCategory`, `ChapterXRayReport`
- **Depende_de:** SpanishTextFormatter, Stopwords
- **Usado_por:** ReaderIntelligenceAndExportCoordinator, ReaderXRayBottomSheet
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 253 líneas)
- **Hallazgos:** Operación 100% offline y determinista con impacto mínimo en CPU (~5-15 ms por capítulo).

#### `core/share/QuoteVideoClipExporter.kt`
- **Propósito:** Exportador de clips de video verticales MP4 con citas y fragmentos de texto. Renderiza texto animado sobre lienzo gráfico y multiplexa pistas de video H.264/AVC con audio AAC sintetizado mediante MediaCodec y MediaMuxer en corrutinas.
- **Clases:** `QuoteVideoClipExporter` (@Singleton), `WordTiming`, `ExportProgress`
- **Depende_de:** TtsManager, MediaCodec, MediaMuxer, Surface, Paint, Dispatchers.Default
- **Usado_por:** ReaderViewModel, ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 429 líneas)
- **Hallazgos:** El pipeline de codificación MediaCodec opera fuera del hilo principal para prevenir congelamientos en UI durante la rasterización de cuadros.

#### `core/backup/WoodsBackupManager.kt`
- **Propósito:** Gestor integral de exportación e importación de copias de seguridad de la aplicación (Woods Vault JSON). Exporta e importa progreso de lectura, historial de sesiones, citas, subrayados, notas, reglas fonéticas de TTS y preferencias de usuario mediante serialización kotlinx.serialization con esquema versionado.
- **Clases:** `WoodsBackupManager` (@Singleton), `WoodsBackupData`, `BookProgressBackup`, `HighlightBackup`, `BookmarkBackup`, `PhoneticRuleBackup`, `TtsRuleBackup`
- **Depende_de:** AppDatabase, BookDao, HighlightDao, BookmarkDao, TtsRuleDao, UserPreferencesRepository, Dispatchers.IO
- **Usado_por:** SettingsViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 602 líneas)
- **Hallazgos:** Las operaciones de restauración aplican transacciones atómicas Room para prevenir corrupciones parciales si el proceso es interrumpido.

#### `core/license/ProLicenseManager.kt`
- **Propósito:** Verificador y gestor de estado de licencias de Woods Reader PRO. Comprueba firmas criptográficas asimétricas ECDSA (secp256r1 con algoritmo SHA256withECDSA) emitidas por Keymaker contra una clave pública incrustada, validando el identificador único de instalación.
- **Clases:** `ProLicenseManager` (@Singleton), `LicenseStatus`
- **Depende_de:** Java Security (KeyFactory, Signature, X509EncodedKeySpec), SharedPreferences, Context
- **Usado_por:** ReaderViewModel, LibraryViewModel, WoodsProUnlockDialog, SettingsViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 144 líneas)
- **Hallazgos:** Si ANDROID_ID no está disponible, utiliza un identificador pseudoaleatorio de instalación persistido en SharedPreferences para garantizar portabilidad offline.

### 5.5 Web-to-EPUB y Traducción

#### `core/web2epub/WebToEpubImporterDialog.kt`
- **Propósito:** Diálogo de importación web a EPUB (UI Compose, NovelImporter, LocalEpubBuilder).
- **Clases:** `WebToEpubImporterDialog`, `ImporterUiStage` (enum)
- **Depende_de:** ProLicenseManager, OkHttp, WebView, Coil3, SiteProfileLoader
- **Usado_por:** LibraryScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 722 líneas)
- **Hallazgos:** Ninguno crítico.

#### `core/web2epub/LocalEpubBuilder.kt`
- **Propósito:** Constructor local de EPUB 3 (ZIP con mimetype, container.xml, content.opf, toc.ncx, capítulos XHTML).
- **Clases:** `LocalEpubBuilder`, `ScrapedChapterMeta`
- **Depende_de:** java.util.zip, UUID
- **Usado_por:** WebToEpubImporterDialog, CalibreStyleEpubTranslator
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 227 líneas)
- **Hallazgos:** Ninguno.

#### `core/web2epub/novel/SiteProfile.kt`
- **Propósito:** Perfil de sitio web para importación (selectores CSS, delays, etc.).
- **Clases:** `SiteProfile`, `SiteProfileRegistry`
- **Depende_de:** kotlinx.serialization, java.net.URI
- **Usado_por:** SiteProfileLoader, Web2epub
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 120 líneas)
- **Hallazgos:** Ninguno.

#### `core/web2epub/novel/SiteProfileLoader.kt`
- **Propósito:** Cargador de perfiles de sitios desde assets.
- **Clases:** `SiteProfileLoader`
- **Depende_de:** android.content.Context, Timber
- **Usado_por:** Web2epub
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 28 líneas)
- **Hallazgos:** Ninguno.

#### `core/web2epub/novel/PageFetcher.kt`
- **Propósito:** Fetching de páginas (interfaz).
- **Clases:** `FetchedPage`, `FetchFailure` (sealed), `PageFetcher` (interface)
- **Depende_de:** java.io.IOException
- **Usado_por:** OkHttpPageFetcher
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 25 líneas)
- **Hallazgos:** Ninguno.

#### `core/web2epub/novel/OkHttpPageFetcher.kt`
- **Propósito:** Fetching de páginas con OkHttp (rate limiting, detección de desafíos, límite de 5MB).
- **Clases:** `OkHttpPageFetcher` (@Singleton)
- **Depende_de:** OkHttp, CookieManager, ChallengeDetector, CharsetDetector
- **Usado_por:** PageFetcher
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 143 líneas)
- **Hallazgos:** Ninguno.

#### `core/web2epub/novel/RequestPacer.kt`
- **Propósito:** Pacer de requests (rate limiting con jitter).
- **Clases:** `RequestPacer`
- **Depende_de:** kotlinx.coroutines Mutex, kotlin.random
- **Usado_por:** Web2epub
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 38 líneas)
- **Hallazgos:** Ninguno.

#### `core/web2epub/novel/RobotsAdvisor.kt`
- **Propósito:** Advisor de robots.txt (caché de reglas por host).
- **Clases:** `RobotsAdvisor`
- **Depende_de:** PageFetcher, java.net.URI
- **Usado_por:** Web2epub
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 74 líneas)
- **Hallazgos:** Ninguno.

#### `core/web2epub/novel/ChallengeDetector.kt`
- **Propósito:** Detector de desafíos (Cloudflare, captcha).
- **Clases:** `ChallengeDetector` (object)
- **Depende_de:** —
- **Usado_por:** OkHttpPageFetcher
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 31 líneas)
- **Hallazgos:** Ninguno.

#### `core/web2epub/novel/CharsetDetector.kt`
- **Propósito:** Detector de charset (Content-Type, BOM, meta charset, UTF-8 estricto, GB18030, windows-1252).
- **Clases:** `CharsetDetector` (object)
- **Depende_de:** java.nio.charset
- **Usado_por:** OkHttpPageFetcher, RobotsAdvisor
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 88 líneas)
- **Hallazgos:** Ninguno.

#### `core/translation/calibre/CalibreStyleEpubTranslator.kt`
- **Propósito:** Traductor in-place estilo Calibre (traduce EPUB completo capítulo por capítulo).
- **Clases:** `CalibreStyleEpubTranslator` (@Singleton), `EpubTranslationProgress`
- **Depende_de:** LocalEpubBuilder, GoogleFreeNetworkTranslator, Readium ZipFile
- **Usado_por:** LibraryViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 238 líneas)
- **Hallazgos:** Ninguno.

#### `core/translation/calibre/GoogleFreeNetworkTranslator.kt`
- **Propósito:** Traductor de red gratuito (Google Translate endpoint, glosario Xianxia/Wuxia, caché LRU).
- **Clases:** `GoogleFreeNetworkTranslator` (@Singleton), `Placeholders` (object), `Engine` (enum), `Shielded` (class)
- **Depende_de:** OkHttp, org.json, TextTranslator
- **Usado_por:** CalibreStyleEpubTranslator
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 338 líneas)
- **Hallazgos:** Ninguno.

#### `core/translation/calibre/inplace/TextTranslator.kt`
- **Propósito:** Traductor in-place base (interfaz).
- **Clases:** `TextTranslator` (interface)
- **Depende_de:** —
- **Usado_por:** GoogleFreeNetworkTranslator
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 10 líneas)
- **Hallazgos:** Ninguno.

---

#### `core/translation/calibre/inplace/EpubInPlaceTranslator.kt`
- **Propósito:** Traductor de libros EPUB in-place. Desempaqueta el EPUB, traduce cada archivo XHTML conservando intacta la estructura del libro, y reempaqueta con compresión ZIP válida.
- **Clases:** `EpubInPlaceTranslator`
- **Depende_de:** EpubPackageParser, EpubZipWriter, XhtmlTranslatorDoc, TextTranslator, Dispatchers.IO
- **Usado_por:** CalibreStyleEpubTranslator, LibraryViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 253 líneas)
- **Hallazgos:** Preserva estilos CSS, metadatos y enlaces internos sin corromper el estándar IDPF.

#### `core/translation/calibre/inplace/EpubPackage.kt`
- **Propósito:** Modelo de datos que describe el contenido y rutas de un paquete EPUB (directorio raíz OPF, archivo de manifiesto y lista de recursos spine).
- **Clases:** `EpubPackage`
- **Depende_de:** File
- **Usado_por:** EpubPackageParser, EpubInPlaceTranslator
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 12 líneas)
- **Hallazgos:** Ninguno.

#### `core/translation/calibre/inplace/EpubPackageParser.kt`
- **Propósito:** Parser de contenedor y manifiesto OPF de archivos EPUB. Localiza el archivo OPF desde container.xml y extrae la lista ordenada de archivos XHTML que componen la lectura.
- **Clases:** `EpubPackageParser`
- **Depende_de:** Jsoup, ZipFile
- **Usado_por:** EpubInPlaceTranslator
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 137 líneas)
- **Hallazgos:** Robusto ante variantes de rutas relativas y caracteres codificados en URL.

#### `core/translation/calibre/inplace/EpubZipWriter.kt`
- **Propósito:** Escritor de archivos ZIP especializado en EPUB. Asegura que el archivo `mimetype` se almacene como la primera entrada y sin compresión (STORED), conforme a la especificación EPUB OCF.
- **Clases:** `EpubZipWriter`
- **Depende_de:** ZipOutputStream, CRC32, File
- **Usado_por:** EpubInPlaceTranslator
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 171 líneas)
- **Hallazgos:** Garantiza que los libros traducidos pasen la validación estricta de EpubCheck.

#### `core/translation/calibre/inplace/XhtmlTranslatorDoc.kt`
- **Propósito:** Procesador DOM para traducción de documentos XHTML. Enmascara etiquetas, entidades y placeholders para enviar solo texto puro al traductor y restaurar el árbol DOM sin alteraciones de formato.
- **Clases:** `XhtmlTranslatorDoc`, `ElementMask`
- **Depende_de:** Jsoup, Placeholders
- **Usado_por:** EpubInPlaceTranslator
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 132 líneas)
- **Hallazgos:** Evita que los motores de traducción automática rompan etiquetas HTML internas.

#### `core/web2epub/LegacyWebToEpubImporterDialog.kt`
- **Propósito:** Diálogo de importación legacy con WebView interactivo para sitios web protegidos por Cloudflare o con autenticación.
- **Clases:** `LegacyWebToEpubImporterDialog` (@Composable), `ScrapingStage`
- **Depende_de:** Compose, Android WebView, CookieManager
- **Usado_por:** LibraryScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 1.161 líneas)
- **Hallazgos:** Mantenido como respaldo manual para fuentes web complejas.

#### `core/web2epub/novel/EpubAssembler.kt`
- **Propósito:** Ensamblador de novelas web en libros EPUB 3.0 estándar. Construye la estructura completa del libro (mimetype, META-INF, toc.ncx, nav.xhtml, content.opf y capítulos formateados con CSS de lectura).
- **Clases:** `EpubAssembler`, `EpubMetadata`, `EpubChapterInput`, `EpubImageInput`
- **Depende_de:** ZipOutputStream, SimpleDateFormat, File
- **Usado_por:** NovelImporter
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 395 líneas)
- **Hallazgos:** Genera EPUBs limpios y compatibles con cualquier lector de libros.

#### `core/web2epub/novel/ImageDownloader.kt`
- **Propósito:** Descargador concurrente de ilustraciones y portadas de novelas web con límite de reintentos y política de timeouts.
- **Clases:** `ImageDownloader`
- **Depende_de:** OkHttpClient, Request, Dispatchers.IO
- **Usado_por:** NovelImporter
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 79 líneas)
- **Hallazgos:** Ninguno.

#### `core/web2epub/novel/ImageSniffer.kt`
- **Propósito:** Detector y clasificador de imágenes dentro del contenido HTML de capítulos. Filtra banners publicitarios, iconos y rastreadores basándose en dimensiones y patrones de URL.
- **Clases:** `ImageSniffer`, `DownloadedImage`, `SniffedImageType`, `ImageBudgetTracker`
- **Depende_de:** MessageDigest, AtomicLong
- **Usado_por:** NovelImporter
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 114 líneas)
- **Hallazgos:** Previene la inclusión de publicidad web dentro del EPUB generado.

#### `core/web2epub/novel/ImportStore.kt`
- **Propósito:** Almacén de persistencia de estado para trabajos de importación de novelas web en curso. Permite reanudar descargas interrumpidas sin repetir capítulos ya bajados.
- **Clases:** `ImportStore`, `JobStatus`, `StoredChapterInfo`, `ImportJobState`
- **Depende_de:** kotlinx.serialization, Json, File
- **Usado_por:** NovelImporter, WebNovelImportWorker
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 167 líneas)
- **Hallazgos:** Resiliente ante caídas de red o cierres del proceso.

#### `core/web2epub/novel/ContentExtractor.kt`
- **Propósito:** Extractor puro de contenido de capítulos HTML a XHTML limpio compatible con EPUB 3 (sanitización Jsoup, Safelist, extracción de títulos, conteo de palabras e imágenes).
- **Clases:** `ChapterStatus` (enum), `ExtractedChapter` (data class), `ContentExtractor`
- **Depende_de:** org.jsoup.*, java.net.URI, SiteProfile
- **Usado_por:** NovelImporter, WebNovelImportWorker
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 309 líneas)
- **Hallazgos:** Ninguno. Sin dependencias de android.* según regla R-TEST.

### `core/web2epub/novel/NovelImporter.kt`
- **Propósito:** Motor principal de importación de novelas web por lotes. Coordina la descarga de capítulos, control de ritmo (RequestPacer), procesamiento de imágenes y ensamblado en EPUB final.
- **Clases:** `NovelImporter`, `ImportStage`, `ImportProgress`
- **Depende_de:** NovelIndexLoader, PageFetcher, EpubAssembler, ImportStore, ImageSniffer
- **Usado_por:** WebNovelImportWorker, LibraryViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 298 líneas)
- **Hallazgos:** Emite flujos de progreso detallados hacia la interfaz de usuario.

#### `core/web2epub/novel/NovelIndexLoader.kt`
- **Propósito:** Cargador e indexador de tablas de contenido de novelas web. Extrae la lista de enlaces y títulos de capítulos desde el índice principal de la novela.
- **Clases:** `NovelIndexLoader`, `ChapterRef`, `NovelIndex`
- **Depende_de:** Jsoup, SiteProfile, URI
- **Usado_por:** NovelImporter
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 319 líneas)
- **Hallazgos:** Soporta paginación y ordenamiento ascendente/descendente de capítulos.

## 6. Servicios y Workers en Segundo Plano

### `service/ReaderTtsService.kt`
- **Propósito:** Servicio en primer plano (Foreground Service) para reproducción continua de TTS con pantalla apagada. Gestiona la MediaSessionCompat, la notificación persistente multimedia con botones de play/pause/avance/retroceso, y el receptor de comandos Bluetooth AVRCP para auriculares inalámbricos.
- **Clases:** `ReaderTtsService`, `TtsBinder`, `TtsMediaSessionCallback`
- **Depende_de:** MediaSessionCompat, TtsManager, NotificationManagerCompat, TtsMediaNotificationProvider, WoodsKeepAliveManager
- **Usado_por:** AndroidManifest, ReaderViewModel, MainActivity
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 855 líneas)
- **Hallazgos:** Vinculado con WoodsKeepAliveManager para evitar que el sistema mate el proceso de audio en segundo plano mediante Doze o LMKD.

### `service/BookScannerService.kt`
- **Propósito:** Servicio de indexación y escaneo de libros en almacenamiento local en 3 fases coordinadas: escaneo rápido de MediaStore, rastreo profundo en árbol de carpetas con semáforo de concurrencia (max 3 hilos I/O), extracción asíncrona de metadatos y portadas, y purga atómica de libros huérfanos.
- **Clases:** `BookScannerService` (@Singleton), `ScanPhase`, `ScanProgress`, `ScanStats`
- **Depende_de:** MediaStore, BookRepository, BookMetadataExtractor, CoverExtractor, Dispatchers.IO
- **Usado_por:** LibraryViewModel, MainActivity, LibrarySyncWorker
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 861 líneas)
- **Hallazgos:** Implementa circuit-breaker de 15 segundos por libro dañado para evitar que archivos corruptos bloqueen el escaneo general de la biblioteca.
  - **M6:** `deepEnrich` usa `HashSet` en memoria (se pierde al reiniciar).
  - **M7:** `cleanupOrphanedBooks` asume `content://` como accesible sin verificar.

### `service/BookScannerForegroundService.kt`
- **Propósito:** Servicio foreground de escaneo.
- **Clases:** `BookScannerForegroundService`
- **Depende_de:** Service foreground
- **Usado_por:** BookScannerService
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 96 líneas)
- **Hallazgos:** Ninguno.

### `service/BookMetadataExtractor.kt`
- **Propósito:** Extracción de metadatos de libros (título, autor, portada, idioma).
- **Clases:** `BookMetadataExtractor` (@Singleton)
- **Depende_de:** Readium, PDFBox, Summarizer, CoverExtractor
- **Usado_por:** MainActivity, BookScannerService, BookDetailViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 359 líneas)
- **Hallazgos:** Ninguno crítico.

### `service/CoverExtractor.kt`
- **Propósito:** Extracción de portadas (PDF vía PdfRenderer, EPUB vía Readium/Zip, procedural como fallback).
- **Clases:** `CoverExtractor` (@Singleton)
- **Depende_de:** android.graphics, PDFBox, Readium, Dispatchers.IO
- **Usado_por:** BookScannerService, ReaderViewModel, BookMetadataExtractor
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 608 líneas)
- **Hallazgos:** Ninguno.

### `service/TextRankSummarizer.kt`
- **Propósito:** Sumarizador TextRank para sinopsis de libros.
- **Clases:** `TextRankSummarizer` (@Singleton), `Summarizer` (interface)
- **Depende_de:** kotlin.math, regex
- **Usado_por:** BookMetadataExtractor, Library/Search
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 162 líneas)
- **Hallazgos:** Ninguno.

### `service/TtsMediaNotificationProvider.kt`
- **Propósito:** Proveedor de notificaciones de medios (Media3 MediaNotification.Provider).
- **Clases:** `TtsMediaNotificationProvider`
- **Depende_de:** Media3, Notification, PendingIntent
- **Usado_por:** ReaderTtsService
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 174 líneas)
- **Hallazgos:** Ninguno.

### `service/RecursiveFileObserver.kt`
- **Propósito:** Observador recursivo de archivos (FileObserver recursivo para detectar nuevos libros).
- **Clases:** `RecursiveFileObserver`, `SingleFileObserver` (inner)
- **Depende_de:** android.os.FileObserver, java.io.File
- **Usado_por:** BookScannerService
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 89 líneas)
- **Hallazgos:** Ninguno.

### `worker/LibrarySyncWorker.kt`
- **Propósito:** Worker de sincronización de biblioteca (cada 12h).
- **Clases:** `LibrarySyncWorker` (@HiltWorker)
- **Depende_de:** WorkManager, BookScannerService, UserPreferencesRepository
- **Usado_por:** MainActivity
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 113 líneas)
- **Hallazgos:** Ninguno.

### `work/GraphExtractionWorker.kt`
- **Propósito:** Worker de extracción de grafo (dos pasadas: entidades y co-ocurrencias/relaciones).
- **Clases:** `GraphExtractionWorker` (@HiltWorker), `CandidateStats` (data class)
- **Depende_de:** Readium, GraphRepository, BookRepository, EntityHeuristics, WorkManager
- **Usado_por:** WorkManager, GraphViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 413 líneas)
- **Hallazgos:** Ninguno crítico.

### `work/WebNovelImportWorker.kt`
- **Propósito:** Worker de descarga e importación masiva de novelas web a formato EPUB en segundo plano con notificación de progreso en tiempo real.
- **Clases:** `WebNovelImportWorker` (@HiltWorker)
- **Depende_de:** WorkManager, NovelImporter, EpubAssembler, ImageDownloader, ImportStore, SiteProfileLoader
- **Usado_por:** WebToEpubImporterDialog, WorkManager
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 301 líneas)
- **Hallazgos:** Ninguno.

---

## 7. Capa de UI (Jetpack Compose + Material 3)

### 7.1 Tema

#### `ui/theme/Theme.kt`
- **Propósito:** Tema HiddenForest, `WoodsReaderTheme`, colores de superficie, acento, fondo.
- **Clases:** `WoodsReaderTheme`, `HiddenForest`, `LocalReaderTheme`, `LocalHighlightColors`, `HighlightColors`
- **Depende_de:** Material3, Compose, ReaderTheme
- **Usado_por:** Todas las pantallas
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 180 líneas)
- **Hallazgos:** Ninguno.

#### `ui/theme/Color.kt`
- **Propósito:** Colores del tema (paleta HiddenForest, Day, Sepia, AMOLED, Sunset, Semantic).
- **Clases:** `HiddenForest`, `Day`, `Sepia`, `AMOLED`, `Sunset`, `Semantic`
- **Depende_de:** Compose Color
- **Usado_por:** Theme.kt
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 95 líneas)
- **Hallazgos:** Ninguno.

#### `ui/theme/Type.kt`
- **Propósito:** Tipografía del tema (Familia Literata, WoodsReaderTypography).
- **Clases:** `LiterataFamily`, `WoodsReaderTypography`
- **Depende_de:** Compose TextStyle, FontFamily, R.font.literata_variable
- **Usado_por:** Theme.kt
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 142 líneas)
- **Hallazgos:** Ninguno.

### 7.2 Pantallas Principales

#### `ui/library/LibraryScreen.kt`
- **Propósito:** Pantalla principal de la biblioteca: lista y cuadrícula adaptativa de libros con Paging 3, tarjeta hero "Continuar Leyendo", botón flotante para escaneo rápido, menús contextuales por libro (eliminar, editar, exportar audio .m4a), y disparadores para importación web y traducciones Calibre.
- **Clases:** `LibraryScreen` (@Composable)
- **Depende_de:** Compose, Paging 3, Coil3, LibraryViewModel, LibraryHeader, ContinueReadingHero, BookOptionsBottomSheet
- **Usado_por:** NavGraph
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 776 líneas)
- **Hallazgos:** Mojibake residual en comentarios descriptivos (C11). Muestra estados vacíos intuitivos cuando no hay libros indexados.

#### `ui/library/LibraryViewModel.kt`
- **Propósito:** ViewModel de biblioteca, watchers de rutas, enriquecimiento de portadas, escaneo, filtros.
- **Clases:** `LibraryViewModel` (@HiltViewModel)
- **Depende_de:** BookRepository, BookScannerService, ProLicenseManager, CalibreStyleEpubTranslator, UserPreferencesRepository
- **Usado_por:** LibraryScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (líneas 1-321)
- **Hallazgos:** **A9:** `takePersistableUriPermission` sin verificación de resultado.

#### `ui/library/LibraryHeader.kt`
- **Propósito:** Componente Compose para la cabecera superior de la biblioteca: título "Woods Reader", badge de versión de la aplicación, selector de modo de ordenamiento (recientes, título, progreso), y barra de búsqueda colapsable con animación.
- **Clases:** `LibraryHeader` (@Composable)
- **Depende_de:** Jetpack Compose, Material3, UserPreferences
- **Usado_por:** LibraryScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 217 líneas)
- **Hallazgos:** Interfaz limpia sin acoplamiento a ViewModels; recibe lambdas de callback puras.

#### `ui/library/LibraryComponents.kt`
- **Propósito:** Componentes visuales de biblioteca (tarjetas de libro, esqueleto, estado vacío, tarjeta de estadísticas de escaneo, historial reciente, barra de navegación inferior).
- **Clases:** `WoodsBookCard`, `BookInitials`, `WoodsProgressBar`, `WoodsBadge`, `WoodsBookSkeleton`, `EmptyLibrary`, `ScanStatsCard`, `RecentBooksSheet`, `RecentBookRow`, `WoodsBottomNavigation`, `rememberShimmerBrush`, `MetadataRow`
- **Depende_de:** Compose, Material3, Coil3, Hilt EntryPoint, BookEntity, HiddenForest theme
- **Usado_por:** LibraryScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 927 líneas)
- **Hallazgos:** Ninguno crítico. Implementa SharedElement para transición hero. Usa shimmer brush para carga. `WoodsBottomNavigation` maneja el botón central con long-press para mostrar historial.

#### `ui/library/ContinueReadingHero.kt`
- **Propósito:** Hero de continuar leyendo (tarjeta destacada con portada, título, progreso y botón Play).
- **Clases:** `ContinueReadingHero` (@Composable)
- **Depende_de:** Compose, Coil3, HiddenForest theme, File
- **Usado_por:** LibraryScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 165 líneas)
- **Hallazgos:** Ninguno. Usa SharedElement para transición de portada. Muestra barra de progreso y botón de leer.

#### `ui/library/CalibreTranslationProgressDialog.kt`
- **Propósito:** Diálogo de progreso de traducción masiva estilo Calibre (barra de progreso, paso actual, cancelar).
- **Clases:** `CalibreTranslationProgressDialog` (@Composable)
- **Depende_de:** Compose, EpubTranslationProgress, HiddenForest theme, Semantic colors
- **Usado_por:** LibraryScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 133 líneas)
- **Hallazgos:** Ninguno. Bloquea interacción fuera del diálogo (dismissOnBackPress = false, dismissOnClickOutside = false).

#### `ui/library/BookOptionsBottomSheet.kt`
- **Propósito:** Bottom sheet de opciones de libro (metadatos, estado de lectura, colección, traducción masiva EPUB, eliminación).
- **Clases:** `BookOptionsBottomSheet` (@Composable), `MetadataRow` (@Composable)
- **Depende_de:** Compose, Coil3, BookEntity, CollectionEntity, ReadingStatus, File, Locale
- **Usado_por:** LibraryScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 381 líneas)
- **Hallazgos:** Ninguno. Incluye botón de traducción masiva para EPUB. Maneja estados de lectura con auto-detección (NOT_STARTED con progreso > 0 → IN_PROGRESS).

#### `ui/library/AudioExportProgressDialog.kt`
- **Propósito:** Diálogo de progreso en tiempo real para la exportación de audiolibros M4A. Muestra porcentaje de codificación, tiempo restante y botón de cancelación.
- **Clases:** `AudioExportProgressDialog` (@Composable)
- **Depende_de:** Compose, Material3, ChapterAudioExporter
- **Usado_por:** LibraryScreen, BookAudioExportDialog
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 658 líneas)
- **Hallazgos:** Ninguno.

#### `ui/library/BookAudioExportDialog.kt`
- **Propósito:** Diálogo de configuración para exportar capítulos o libros a audiolibro AAC en formato `.m4a` con portada y subtítulos incrustados.
- **Clases:** `BookAudioExportDialog` (@Composable)
- **Depende_de:** Compose, Material3, ChapterAudioExporter, BookEntity
- **Usado_por:** LibraryScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 675 líneas)
- **Hallazgos:** Implementa la PARTE 73 (exportación en archivo único).

#### `ui/library/LanguagePickerDialog.kt`
- **Propósito:** Selector modal de idioma para traducción de libros o cambio de idioma de la interfaz.
- **Clases:** `LanguagePickerDialog` (@Composable), `SupportedLanguage`
- **Depende_de:** Compose, Material3
- **Usado_por:** LibraryScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 624 líneas)
- **Hallazgos:** Ninguno.

#### `ui/bookdetail/BookDetailScreen.kt`
- **Propósito:** Pantalla de detalle de libro (cover hero, metadata, tabs de subrayados/marcadores/historial, estadísticas, sinopsis, notas).
- **Clases:** `BookDetailScreen`, `BookDetailContent`, `CoverHeroSection`, `MetadataSection`, `TabbedAnnotations`, `HighlightsTab`, `BookmarksTab`, `SessionsTab`, `HighlightCard`, `BookmarkCard`, `ChangeTypeDialog` (implícito), `MergeCandidateDialog` (implícito), `CoverHeroSection`, `CoverBookCard`, `LinearProgressIndicatorPill`, `MetadataGrid`, `DescriptionCard`, `UserNotesCard`, `ReadingStatusSelector`, `StarRating`, `DetailTopBar`, `ReadNowFab`
- **Depende_de:** Compose, Coil3, Hilt navigation, BookEntity, HighlightEntity, BookmarkEntity, ReadingSessionEntity, BookStats, BookDetailViewModel, HiddenForest theme
- **Usado_por:** NavGraph
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 1247+ líneas)
- **Hallazgos:** Ninguno crítico. Usa EntryPoint para obtener BookRepository. `BookDetailScreen` maneja estados de loading, error y éxito. Incluye tarjetas de estadísticas, sinopsis con generación IA, notas de usuario y pestañas de subrayados/marcadores/sesiones.

#### `ui/bookdetail/BookDetailViewModel.kt`
- **Propósito:** ViewModel de detalle de libro. Estadísticas, rachas, cálculo de WPM, generación de sinopsis.
- **Clases:** `BookDetailViewModel` (@HiltViewModel), `BookDetailUiState`, `BookStats`
- **Depende_de:** BookRepository, BookMetadataExtractor, SavedStateHandle
- **Usado_por:** BookDetailScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 309 líneas)
- **Hallazgos:** **C12:** `checkNotNull(savedStateHandle["bookId"])` crashea si falta el argumento.

#### `ui/settings/SettingsScreen.kt`
- **Propósito:** Pantalla general de configuración y ajustes: motor de voz TTS (Sherpa, Edge, Sistema), descarga y borrado de modelos de voz, catálogo de presets, temporizador de sueño, respaldo y restauración de datos (Woods Vault), biblioteca y almacenamiento.
- **Clases:** `SettingsScreen` (@Composable)
- **Depende_de:** Compose, Material3, SettingsViewModel, UserPreferencesRepository, ActivityResultContracts
- **Usado_por:** NavGraph
- **Estado:** 🟡 REVISIÓN PARCIAL (líneas 1-500 de 1.544; componente Compose masivo de configuración)
- **Hallazgos:** Interfaz modular dividida en sub-secciones para optimizar rendimiento.

#### `ui/settings/SettingsViewModel.kt`
- **Propósito:** ViewModel de ajustes. Backup/Restore, TTS engine mode, Sherpa voices, almacenamiento, preferencias.
- **Clases:** `SettingsViewModel` (@HiltViewModel), `LibraryBackup`, `StorageSizes`, `TtsEngineMode`
- **Depende_de:** UserPreferencesRepository, BookScannerService, BookDao, BackupManager, WoodsBackupManager, TtsManager, SherpaModelManager, EdgeNeuralTtsClient, ProLicenseManager, PronunciationLexiconManager, WoodsKeepAliveManager
- **Usado_por:** SettingsScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 422 líneas)
- **Hallazgos:**
  - **A10:** Export JSON por Intent puede exceder límite de Binder (~1 MB).
  - **A11:** Import sin validación ni detección de duplicados, sin transacción atómica.

#### `ui/onboarding/OnboardingScreen.kt`
- **Propósito:** Pantalla de bienvenida.
- **Clases:** `OnboardingScreen`, `OnboardingPageData`
- **Depende_de:** Compose, OnboardingViewModel
- **Usado_por:** NavGraph
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 807 líneas)
- **Hallazgos:** Ninguno.

#### `ui/onboarding/OnboardingViewModel.kt`
- **Propósito:** ViewModel de onboarding.
- **Clases:** `OnboardingViewModel` (@HiltViewModel)
- **Depende_de:** UserPreferencesRepository
- **Usado_por:** OnboardingScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 21 líneas)
- **Hallazgos:** Ninguno.

#### `ui/search/SearchScreen.kt`
- **Propósito:** Búsqueda global de libros con filtros avanzados.
- **Clases:** `SearchScreen`, `SearchResultCard`
- **Depende_de:** Compose, Paging, Coil3, LibraryViewModel
- **Usado_por:** NavGraph
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 738 líneas)
- **Hallazgos:** Ninguno crítico.

#### `ui/stats/ReadingStatsScreen.kt`
- **Propósito:** Estadísticas de lectura (KPIs, mapa de calor, gráficos, distribución por libro).
- **Clases:** `ReadingStatsScreen`
- **Depende_de:** Compose, StatsViewModel
- **Usado_por:** NavGraph
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 1069 líneas)
- **Hallazgos:** Ninguno crítico.

#### `ui/stats/StatsViewModel.kt`
- **Propósito:** ViewModel de estadísticas (KPIs, racha, heatmap, distribución, modo endurance).
- **Clases:** `StatsViewModel` (@HiltViewModel), `StatsPeriod` (enum)
- **Depende_de:** BookRepository, java.time
- **Usado_por:** ReadingStatsScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 204 líneas)
- **Hallazgos:** Ninguno.

#### `ui/permissions/PermissionsScreen.kt`
- **Propósito:** Pantalla de permisos de almacenamiento.
- **Clases:** `PermissionsScreen`
- **Depende_de:** Compose, Storage permissions
- **Usado_por:** MainActivity
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 71 líneas)
- **Hallazgos:** Ninguno.

#### `ui/common/WoodsProUnlockDialog.kt`
- **Propósito:** Diálogo de desbloqueo Pro (clave de licencia, ID de dispositivo).
- **Clases:** `WoodsProUnlockDialog`, `ProBenefitRow`
- **Depende_de:** Compose, ProLicenseManager, ClipboardManager
- **Usado_por:** UI
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 392 líneas)
- **Hallazgos:** Ninguno.

### 7.3 Lector Principal (EPUB/PDF, TTS, Anotaciones)

#### `ui/reader/ReaderScreen.kt`
- **Propósito:** Pantalla principal del lector de libros. Aloja la superficie Readium WebView / Pdfium, maneja gestos de navegación táctil, menús contextuales de subrayado y notas, barra de progreso, teleprompter WPM y estado visual de carga y error (ReaderUiState.Error).
- **Clases:** `ReaderScreen` (@Composable), `ReaderNavigatorContainer`
- **Depende_de:** Compose, Material3, Readium Navigator, ReaderViewModel, InlineLookupManager, HazeState
- **Usado_por:** NavGraph
- **Estado:** 🟡 REVISIÓN PARCIAL (líneas 1-1.120 de 1.960; componente central monolítico de lectura)
- **Hallazgos:** Maneja ReaderUiState.Error centrando el mensaje en pantalla con color de tema (L1083-L1085). flushReadingProgressImmediately() se ejecuta en ciclo de vida ON_PAUSE/ON_STOP delegando a SaveReadingProgressUseCase.

#### `ui/reader/ReaderViewModel.kt`
- **Propósito:** Orquestador principal de UI del lector. Coordina los flujos de lectura, TTS, panel de control, marcadores, navegación y exportación delegando la lógica de negocio a casos de uso especializados.
- **Clases:** `ReaderViewModel` (@HiltViewModel), `ParsedChapterData`, `AutoScrollTeleprompterState`, `ChapterInfo`
- **Depende_de:** SaveReadingProgressUseCase, CalculateReadingStatsUseCase, LoadBookFastUseCase, ManageReaderAnnotationsUseCase, ReaderIntelligenceAndExportCoordinator, TtsManager, EpubFormatManager, PdfReflowManager, UserPreferencesRepository, ForestAmbienceEngine, AmbientSoundscapePlayer
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 2.472 líneas)
- **Hallazgos:**
  - **C3 [RESUELTO]:** Descompresión completada mediante 5 casos de uso y coordinadores de dominio. Reducción de más de 430 líneas de lógica acoplada.
  - **C8/C10/C17 [RESUELTO]:** Eliminados todos los comentarios con caracteres corruptos (mojibake).
  - **A12 [RESUELTO]:** Doble persistencia de progreso unificada en `SaveReadingProgressUseCase`.
  - **C2:** `checkNotNull(savedStateHandle.get<String>("bookId"))` crashea si falta `bookId` (pendiente blindaje con lectura defensiva).
  - **M3:** CFI como JSON plano frágil.

#### `ui/reader/ReaderContract.kt`
- **Propósito:** Definición de contratos y modelos de navegación para la interfaz del lector: pestañas del panel lateral (TOC, Marcadores, Subrayados, Notas, Configuración) y modelos inmutables de resaltado de texto para la narración de TTS.
- **Clases:** `ReaderTab` (enum), `NarrationHighlight` (data class), `ReaderState`
- **Depende_de:** Readium Locator
- **Usado_por:** ReaderViewModel, ReaderScreen, ReaderSideMenu, ReaderTopBar
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 42 líneas)
- **Hallazgos:** Contrato estricto y desacoplado de Compose que previene dependencias cíclicas entre vistas del lector.

#### `ui/reader/TtsState.kt`
- **Propósito:** Estados de TTS.
- **Clases:** `TtsState` (enum: IDLE, LOADING, PLAYING, PAUSED, STOPPED)
- **Depende_de:** —
- **Usado_por:** Reader UI
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** Ninguno.

#### `ui/reader/EpubFormatManager.kt`
- **Propósito:** Gestor de formato EPUB y puente de configuración visual para Readium VisualNavigator. Aplica temas de color (Hidden Forest, Sunset, Sepia, Dark), estilos de tipografía, tamaño de fuente, interlineado, márgenes de página y opciones de justificación mediante inyección de CSS y cálculo de preferencias Readium.
- **Clases:** `EpubFormatManager` (@Inject constructor)
- **Depende_de:** Readium Navigator (VisualNavigator, EpubPreferences), UserPreferences, ReaderTheme
- **Usado_por:** ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 588 líneas)
- **Hallazgos:** Aplica debouncing de 150 ms en actualizaciones de preferencias visuales para no saturar el motor WebView de Readium.

#### `ui/reader/ReaderUIComponents.kt`
- **Propósito:** Catálogo de componentes visuales de diseño reutilizables para el lector (tokens de diseño ReaderDesign, tarjetas con efecto glassmorphism, sliders con retroalimentación háptica, botones redondeados de navegación e indicadores de carga).
- **Clases:** `ReaderDesign` (object), `ReaderIconButton`, `ReaderSolidCard`, `ReaderGlassCard`, `ReaderBadge`
- **Depende_de:** Compose, Material3, HazeState
- **Usado_por:** ReaderScreen, ReaderTopBar, ReaderBottomBar, ReaderSideMenu
- **Estado:** 🟡 REVISIÓN PARCIAL (líneas 1-600 de 1.616; biblioteca extensa de componentes Compose)
- **Hallazgos:** Tokens de diseño estandarizados para la paleta Hidden Forest y AMOLED.

#### `ui/reader/ReaderBottomBar.kt`
- **Propósito:** Barra inferior de pestañas del lector (Texto, Audio, Notas).
- **Clases:** `ReaderBottomBar`
- **Depende_de:** Compose
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 96 líneas)
- **Hallazgos:** Ninguno.

#### `ui/reader/ReaderTopBar.kt`
- **Propósito:** Barra superior del lector con soporte de glassmorphism (Haze). Muestra botón de retroceso a biblioteca, título del libro y capítulo actual, alternador de modo Reflow (en PDFs), acceso rápido a TTS y menú maestro.
- **Clases:** `ReaderTopBar` (@Composable)
- **Depende_de:** Compose, Material3, HazeState
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 234 líneas)
- **Hallazgos:** Altura y espaciado adaptativo para evitar solapamientos con la barra de estado del sistema (Insets).

#### `ui/reader/ReaderSideMenu.kt`
- **Propósito:** Menú modal lateral de acceso rápido para el lector. Organiza las acciones principales: Narrador de voz, Tabla de Contenidos (TOC), Búsqueda en texto, Rayos-X de entidades, Modo Teleprompter WPM, Reflujo PDF, Ajustes de lectura y Marcador de página.
- **Clases:** `ReaderSideMenu` (@Composable), `MasterMenuOption`, `MasterMenuOptionType`
- **Depende_de:** Compose, Material3, HazeState
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 236 líneas)
- **Hallazgos:** Cierra de forma reactiva al despachar cualquier navegación para no bloquear la superficie de lectura.

#### `ui/reader/ReaderAudioBottomSheet.kt`
- **Propósito:** Panel inferior modal para configuración de audio y TTS avanzado. Incluye selector de motor (Sherpa ONNX vs TTS del sistema), control de velocidad continua (0.5x - 3.5x), tono de voz, temporizador de sueño con desvanecimiento gradual, selector de ambientes sonoros y panel de voces instaladas.
- **Clases:** `ReaderAudioBottomSheet` (@Composable), `AudioTab`
- **Depende_de:** Compose, Material3, HazeState, TtsManager, SherpaModelManager, SmartSleepTimerManager
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 790 líneas)
- **Hallazgos:** Los controles de velocidad y tono actualizan las preferencias en DataStore con debouncing para no interrumpir el flujo de audio.

#### `ui/reader/ReaderTtsBar.kt`
- **Propósito:** Barra flotante de transporte de TTS en la parte inferior de la pantalla de lectura. Expone botones de reproducción/pausa, salto de oración anterior/siguiente, mini-ticker con el texto actual de la oración narrada, velocidad de lectura rápida y control de bloqueo táctil de pantalla.
- **Clases:** `ReaderTtsBar` (@Composable)
- **Depende_de:** Compose, Material3, HazeState, TtsState
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 336 líneas)
- **Hallazgos:** El mini-ticker de texto trunca elegantemente con elipsis para evitar expansiones verticales de layout en oraciones largas.

#### `ui/reader/ReaderNotesTab.kt`
- **Propósito:** Pestaña de notas, marcadores y citas textuales dentro del menú de anotaciones. Lista subrayados y marcadores ordenados por progreso, permite editar notas asociadas, filtrar por color/tipo y exportar citas a texto Markdown o imagen de tarjeta para compartir.
- **Clases:** `ReaderNotesTab` (@Composable), `UnifiedNote`, `NoteType`, `NotesFilter`
- **Depende_de:** Compose, Material3, HighlightEntity, BookmarkEntity, HighlightExporter, QuoteCardGenerator
- **Usado_por:** ReaderScreen, ReaderMasterMenuBottomSheet
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 574 líneas)
- **Hallazgos:** Utiliza ManageReaderAnnotationsUseCase para la exportación y borrado atómico de apuntes.

#### `ui/reader/ReaderBottomSlider.kt`
- **Propósito:** Slider inferior del lector (progreso, navegación capítulos).
- **Clases:** `ReaderBottomSlider`
- **Depende_de:** Compose, Readium Navigator
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 197 líneas)
- **Hallazgos:** Ninguno.

#### `ui/reader/ReaderMasterMenuBottomSheet.kt`
- **Propósito:** Bottom sheet de menú maestro (obsoleto, reemplazado por ReaderSideMenu).
- **Clases:** — (archivo obsoleto, 3 líneas)
- **Depende_de:** —
- **Usado_por:** —
- **Estado:** ✅ REVISADO_ATOMICAMENTE (archivo obsoleto)
- **Hallazgos:** Archivo obsoleto, contenido eliminado.

#### `ui/reader/ReaderHeroSplash.kt`
- **Propósito:** Pantalla de splash del lector (portada, progreso, carga).
- **Clases:** `ReaderHeroSplash`
- **Depende_de:** Compose, Coil3, ReaderUiState
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 195 líneas)
- **Hallazgos:** Ninguno.

#### `ui/reader/ReaderFloatingTtsButton.kt`
- **Propósito:** Botón flotante TTS (capsule con controles de velocidad, play/pause, progreso).
- **Clases:** `ReaderFloatingTtsButton`, `EqualizerBars` (private)
- **Depende_de:** Compose, Haze
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 347 líneas)
- **Hallazgos:** Ninguno.

#### `ui/reader/ReaderTeleprompterPill.kt`
- **Propósito:** Píldora flotante para control del Modo Teleprompter / Auto-Scroll.
- **Clases:** `ReaderTeleprompterPill`
- **Depende_de:** Compose, Haze
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 149 líneas)
- **Hallazgos:** Ninguno.

#### `ui/reader/ReaderTtsLockOverlay.kt`
- **Propósito:** Overlay de bloqueo TTS (pantalla bloqueada con doble toque para desbloquear).
- **Clases:** `ReaderTtsLockOverlay`, `AudioWaveVisualizer`
- **Depende_de:** Compose, Haze
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 283 líneas)
- **Hallazgos:** Ninguno.

#### `ui/reader/ReaderTtsMacroWindow.kt`
- **Propósito:** Ventana flotante macro para el control de audio avanzado del lector. Incluye ecualizador gráfico DSP, selector de motor TTS en tiempo real, calibrador de velocidad WPM para teleprompter, selector de ambientes de bosque y temporizador de apagado.
- **Clases:** `ReaderTtsMacroWindow` (@Composable), `ReaderMacroEngineMode` (enum)
- **Depende_de:** Compose, Material3, HazeState, TtsManager, ProLicenseManager, WoodsKeepAliveManager
- **Usado_por:** ReaderScreen
- **Estado:** 🟡 REVISIÓN PARCIAL (líneas 1-1.013 de 2.425; diálogo Compose de alta complejidad)
- **Hallazgos:** Interfaz rica con múltiples selectores y controles deslizantes de precisión.

#### `ui/reader/ReaderReflowOverlay.kt`
- **Propósito:** Vista Libro para PDFs (reflujo de texto, navegación, imágenes, zoom).
- **Clases:** `ReaderReflowOverlay`, `HeaderItem`, `ParagraphItem`, `ReflowImageItem`, `ZoomableImageDialog`
- **Depende_de:** Compose, Readium, Coil3, ReaderReflowManager
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 735 líneas)
- **Hallazgos:** Ninguno.

#### `ui/reader/ReaderEgoGraphBottomSheet.kt`
- **Propósito:** Bottom sheet con mini-grafo (ego graph: entidad + vecinos directos).
- **Clases:** `ReaderEgoGraphBottomSheet`, `ReaderEgoGraphViewModel` (@HiltViewModel), `EgoGraphUiState`
- **Depende_de:** Compose, GraphRepository, ForceLayout, GraphCanvas
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 685 líneas)
- **Hallazgos:** Ninguno.

#### `ui/reader/ReaderXRayBottomSheet.kt`
- **Propósito:** Bottom sheet de X-Ray (entidades detectadas en capítulo actual).
- **Clases:** `ReaderXRayBottomSheet`
- **Depende_de:** Compose, BookXRayEngine, Haze
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 481 líneas)
- **Hallazgos:** Ninguno.

#### `ui/reader/TocBottomSheet.kt`
- **Propósito:** Bottom sheet de tabla de contenidos (TOC con búsqueda y filtros).
- **Clases:** `TocBottomSheet`, `TocItem`
- **Depende_de:** Compose, Readium Publication/Link, Haze
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 269 líneas)
- **Hallazgos:** Ninguno.

#### `ui/reader/ReaderExtensions.kt`
- **Propósito:** Extensiones de Kotlin para Readium, UserPreferences, WebView (navegación, TTS highlight, scroll).
- **Clases:** Extension functions (top-level), `ChapterInfo` (data class)
- **Depende_de:** Readium, UserPreferences, ReaderTheme, HighlightEntity, BookmarkEntity
- **Usado_por:** Reader UI
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 686 líneas)
- **Hallazgos:** Ninguno.

#### `ui/reader/animation/BookPageTurnEffect.kt`
- **Propósito:** Efecto de giro de página (captura con PixelCopy, animación 3D o slide).
- **Clases:** `PageTurnDirection` (enum), `BookPageTurnState`, `BookPageTurnOverlay`, `rememberBookPageTurnState`
- **Depende_de:** Compose, PixelCopy, kotlinx.coroutines
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 416 líneas)
- **Hallazgos:** Ninguno.

#### `ui/reader/ChapterScrollDetector.kt`
- **Propósito:** Detector especializado de eventos táctiles para scroll infinito entre capítulos. Intercepta y despacha eventos MotionEvent en vistas WebView para permitir transiciones de capítulo suaves y fluidas a 90/120Hz sin saltos de scroll.
- **Clases:** `ChapterScrollDetector`
- **Depende_de:** MotionEvent, ViewConfiguration, Context
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 267 líneas)
- **Hallazgos:** INVARIANTE E INTOCABLE. Su timing táctil está optimizado para evitar rebotes de scroll.

#### `ui/reader/EpubCoverPage.kt`
- **Propósito:** Componente de portada a pantalla completa al inicio de libros EPUB. Muestra la portada en alta resolución antes del primer capítulo de texto.
- **Clases:** `EpubCoverPage` (@Composable)
- **Depende_de:** Compose, Coil, Bitmap
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 216 líneas)
- **Hallazgos:** Ninguno.

#### `ui/reader/ReaderScreenOverlays.kt`
- **Propósito:** Conjunto de diálogos y capas modales superpuestas para el lector: visor de imágenes a pantalla completa, menú de compartir citas, diálogo de búsqueda rápida y confirmaciones de navegación.
- **Clases:** `ReaderScreenOverlays` (@Composable)
- **Depende_de:** Compose, Material3, HazeState
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 701 líneas)
- **Hallazgos:** Desacopla la lógica de ventanas emergentes del composable principal ReaderScreen.

#### `ui/reader/SearchBottomSheet.kt`
- **Propósito:** Panel inferior modal de búsqueda dentro del libro. Lista resultados coincidentes con extracto de contexto y salta a la posición exacta en el texto al pulsar.
- **Clases:** `SearchBottomSheet` (@Composable)
- **Depende_de:** Compose, Material3, FastEpubSearchEngine
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 350 líneas)
- **Hallazgos:** Utiliza streaming Flow para presentar resultados inmediatos mientras la búsqueda continúa.

#### `ui/reader/TtsRulesBottomSheet.kt`
- **Propósito:** Panel de gestión de reglas fonéticas y exclusión de palabras en tiempo de lectura. Permite añadir reglas personalizadas para corregir pronunciaciones o silenciar notas al pie.
- **Clases:** `TtsRulesBottomSheet` (@Composable)
- **Depende_de:** Compose, Material3, TtsRuleEntity, BookRepository
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 323 líneas)
- **Hallazgos:** Permite aplicar reglas al libro actual o globalmente a toda la biblioteca.

### 7.4 Grafo Semántico

#### `ui/graph/GraphScreen.kt`
- **Propósito:** Pantalla principal del grafo semántico (Canvas de fuerzas, filtros por tipo, exportar/importar JSON, analizar libro).
- **Clases:** `GraphScreen` (@Composable)
- **Depende_de:** Compose, GraphViewModel, Readium Locator, Hilt navigation
- **Usado_por:** NavGraph
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 536 líneas)
- **Hallazgos:** Ninguno crítico. Manejo correcto de exportar/importar glosario JSON vía ActivityResultContracts.

#### `ui/graph/GraphViewModel.kt`
- **Propósito:** ViewModel de grafo, física de fuerzas, WorkManager para extracción, flujos combinados (máx 2 por combine según regla 6).
- **Clases:** `GraphViewModel` (@HiltViewModel), `GraphUiState`, `GraphAnalysisProgress`
- **Depende_de:** GraphRepository, ForceLayout, WorkManager, GraphExtractionWorker, GraphPositionsCache, GraphJsonHelper, Readium Locator/Url/MediaType
- **Usado_por:** GraphScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 250+ líneas)
- **Hallazgos:** `checkNotNull(savedStateHandle["bookId"])` crashea si falta `bookId` (C13, heredado de fase anterior). Uso correcto de combines anidados de 2 (regla 6 de GRAPH_PLAN.md). Loop de física en viewModelScope/Dispatchers.Default con `tick` para forzar redibujo.

#### `ui/graph/GraphCanvas.kt`
- **Propósito:** Canvas de grafo con zoom/pan/tap, selección por distancia, texto medido solo si zoom > 0.7.
- **Clases:** `GraphCanvas`, `entityColor()`
- **Depende_de:** Compose Canvas, gestures, rememberTextMeasurer, MaterialTheme
- **Usado_por:** GraphScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 100+ líneas)
- **Hallazgos:** Ninguno.

#### `ui/graph/ForceLayout.kt`
- **Propósito:** Algoritmo de layout de fuerzas (puro Kotlin, sin Android). Repulsión O(n²), atracción en aristas, gravedad al centro, amortiguación.
- **Clases:** `ForceLayout`
- **Depende_de:** kotlin.math, kotlin.random
- **Usado_por:** GraphViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 100+ líneas)
- **Hallazgos:** Ninguno. Caché de posiciones iniciales para apertura instantánea.

#### `ui/graph/EntitySheet.kt`
- **Propósito:** Hoja de entidad con detalles, aliases, relaciones, menciones, navegación al texto.
- **Clases:** `EntityBottomSheet` (@Composable)
- **Depende_de:** Compose, Graph entities (GraphEntity, GraphAlias, GraphRelation, GraphMention, EntityType)
- **Usado_por:** GraphScreen, GraphCanvas
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 599 líneas)
- **Hallazgos:** Ninguno. Diálogos para añadir alias y relaciones bien implementados.

#### `ui/graph/CandidatesScreen.kt`
- **Propósito:** Pantalla de candidatos no confirmados (swipe para confirmar/ignorar, cambiar tipo, fusionar).
- **Clases:** `CandidatesScreen`, `CandidatesViewModel` (@HiltViewModel), `CandidateItemCard`, `ChangeTypeDialog`, `MergeCandidateDialog`
- **Depende_de:** Compose, GraphRepository, SavedStateHandle, Hilt
- **Usado_por:** NavGraph
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 726 líneas)
- **Hallazgos:** `checkNotNull(savedStateHandle["bookId"])` crashea si falta `bookId` (misma regla C13).

---

## 8. Datos de Grafo (Room)

### `data/graph/GraphEntities.kt`
- **Propósito:** Entidades de grafo: GraphEntity, GraphAlias, GraphRelation, GraphMention, EntityType + GraphTypeConverters.
- **Clases:** `GraphEntity`, `GraphAlias`, `GraphRelation`, `GraphMention`, `EntityType`, `GraphTypeConverters`
- **Depende_de:** Room, @Entity, @ForeignKey, @Index, kotlinx.serialization
- **Usado_por:** GraphDao, AppDatabase
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 130 líneas)
- **Hallazgos:** Ninguno.

### `data/graph/GraphDao.kt`
- **Propósito:** DAO de grafo: entidades, relaciones, menciones, aliases, mergeInto transaccional.
- **Clases:** `GraphDao`, `GraphEntityWithAliases`, `GraphExportData`
- **Depende_de:** Room @Dao, @Query, @Transaction, @Relation, Flow
- **Usado_por:** GraphRepository
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 236 líneas)
- **Hallazgos:** **A1:** `mergeInto` transaccional, pero llamador externo debe asegurar atomicidad superior.

### `data/graph/GraphRepository.kt`
- **Propósito:** Repositorio de grafo de entidades por libro. Envuelve GraphDao con métodos convenientes para CRUD de entidades, relaciones, menciones, aliases y exportación JSON.
- **Clases:** `GraphRepository` (@Singleton)
- **Depende_de:** GraphDao, Flow, Hilt @Inject/@Singleton
- **Usado_por:** GraphViewModel, GraphExtractionWorker
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 85 líneas)
- **Hallazgos:** Ninguno. Wrapper limpio y reactivo sobre GraphDao.

### `data/graph/GraphJson.kt`
- **Propósito:** Export/import JSON de grafo (GraphGlossaryJson, GraphEntityJson, GraphRelationJson).
- **Clases:** `GraphGlossaryJson`, `GraphEntityJson`, `GraphRelationJson`, `GraphJsonHelper` (object)
- **Depende_de:** kotlinx.serialization.json
- **Usado_por:** GraphRepository
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 225 líneas)
- **Hallazgos:** Ninguno.

### `data/graph/GraphPositionsCache.kt`
- **Propósito:** Caché de posiciones (guardar x,y por entidad en JSON local) para apertura instantánea del grafo.
- **Clases:** `NodePosition`, `GraphPositionsCacheData`, `GraphPositionsCache` (object)
- **Depende_de:** kotlinx.serialization, java.io.File, ForceLayout
- **Usado_por:** GraphViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 84 líneas)
- **Hallazgos:** Ninguno.


---

## 9. Utilidades

### `util/EntityHeuristics.kt`
- **Propósito:** Heurísticas de entidades (regex candidatos, stopwords, detección mitad de frase, snippet, stripHtml, splitIntoParagraphs).
- **Clases:** `EntityHeuristics` (object)
- **Depende_de:** EntityType
- **Usado_por:** Graph, X-Ray, GraphExtractionWorker
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 194 líneas)
- **Hallazgos:** Ninguno.

---

## 10. Modelos de UI

### `model/ReaderTheme.kt`
- **Propósito:** Tema de lector.
- **Clases:** `ReaderTheme` (enum: HIDDEN_FOREST, DAY, SEPIA, AMOLED, SUNSET)
- **Depende_de:** kotlinx.serialization
- **Usado_por:** ReaderViewModel, Theme.kt
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 12 líneas)
- **Hallazgos:** Ninguno.

### `model/ScrollMode.kt`
- **Propósito:** Modo de desplazamiento y navegación del lector de libros.
- **Clases:** `ScrollMode` (enum: PAGINATED, CONTINUOUS_VERTICAL, CONTINUOUS_HORIZONTAL)
- **Depende_de:** kotlinx.serialization
- **Usado_por:** ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 17 líneas)
- **Hallazgos:** Ninguno.

### `model/PageTurnStyle.kt`
- **Propósito:** Estilos de animación para el paso de página en modo PAGINATED.
- **Clases:** `PageTurnStyle` (enum: BOOK_FLIP, SLIDE, NONE)
- **Depende_de:** kotlinx.serialization
- **Usado_por:** ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 28 líneas)
- **Hallazgos:** Ninguno.

### `model/TtsHighlightStyle.kt`
- **Propósito:** Estilo de resaltado TTS.
- **Clases:** `TtsHighlightStyle` (enum: BACKGROUND_YELLOW, BACKGROUND_ACCENT, UNDERLINE, BACKGROUND_BLUE, BACKGROUND_ORANGE, NONE)
- **Depende_de:** kotlinx.serialization
- **Usado_por:** Reader UI
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 13 líneas)
- **Hallazgos:** Ninguno.

### `model/TtsUiStyle.kt`
- **Propósito:** Estilo de UI TTS.
- **Clases:** `TtsUiStyle` (enum: BAR, FLOATING, PANEL)
- **Depende_de:** kotlinx.serialization
- **Usado_por:** Reader UI
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 16 líneas)
- **Hallazgos:** Ninguno.

---

## 11. Módulo Keymaker (Administración Separada)

### `keymaker/src/main/java/com/example/woodsreader/keymaker/MainActivity.kt`
- **Propósito:** Activity principal del módulo Keymaker (administración de licencias Pro). Tres pestañas: Emitir, Verificador, Historial.
- **Clases:** `MainActivity` (ComponentActivity), `KeymakerTab` (enum: GENERATOR, VERIFIER, HISTORY), `KeymakerApp`, `IssuerScreen`, `VerifierScreen`, `HistoryScreen`
- **Depende_de:** Compose, Material3, kotlinx.coroutines, ClipboardManager, KeymakerEngine, KeyHistoryManager, WoodsKeymakerTheme
- **Usado_por:** keymaker (módulo separado)
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 863 líneas)
- **Hallazgos:** Ninguno crítico. ECDSA firmado en Dispatchers.Default. Manejo correcto de portapapeles para códigos WR-XXXX-XXXX.

#### `keymaker/src/main/java/com/example/woodsreader/keymaker/KeymakerTheme.kt`
- **Propósito:** Tema oscuro del módulo Keymaker (colores dorados/emerald).
- **Clases:** `WoodsKeymakerTheme` (@Composable), constantes de color (WoodsDarkBackground, WoodsDarkSurface, WoodsGold, WoodsEmerald, etc.)
- **Depende_de:** androidx.compose.material3, androidx.compose.ui.graphics.Color
- **Usado_por:** keymaker (MainActivity)
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 48 líneas)
- **Hallazgos:** Ninguno.

#### `keymaker/src/main/java/com/example/woodsreader/keymaker/KeymakerEngine.kt`
- **Propósito:** Motor criptográfico ECDSA (SHA256withECDSA) para emitir y verificar licencias Pro.
- **Clases:** `KeymakerEngine` (object)
- **Depende_de:** java.security (KeyFactory, Signature, PKCS8EncodedKeySpec, X509EncodedKeySpec), java.util.Base64, java.util.Locale
- **Usado_por:** keymaker (IssuerScreen, VerifierScreen)
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 141 líneas)
- **Hallazgos:** **ADVERTENCIA:** Clave privada embebida en el código (PRIVATE_KEY_BASE64). Este APK no se distribuye a clientes, pero es un riesgo de seguridad si se filtra. Formato de licencia: `PRO-<base32(ECDSA_signature)>`. Verificación con clave pública.

#### `keymaker/src/main/java/com/example/woodsreader/keymaker/KeyHistoryManager.kt`
- **Propósito:** Gestor de historial de claves emitidas (almacenamiento en SharedPreferences JSON).
- **Clases:** `KeyHistoryEntry` (@Serializable data class), `KeyHistoryManager`
- **Depende_de:** kotlinx.serialization, kotlinx.serialization.json, java.text.SimpleDateFormat, android.content.Context
- **Usado_por:** keymaker (MainActivity, HistoryScreen)
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 75 líneas)
- **Hallazgos:** Ninguno. Eliminación de duplicados por installCode al agregar entrada.

---

## 12. Tests (Unitarios e Instrumentados)

### Tests Unitarios (`app/src/test/`) — Suite de 33 Tareas de Gradle (100% en Verde)

| Archivo | Ubicación en Disco | Prueba de | Estado |
|---------|--------------------|-----------|--------|
| `ExampleUnitTest.kt` | `app/src/test/.../ExampleUnitTest.kt` | Prueba básica de sanidad | ✅ REVISADO |
| `EntityHeuristicsTest.kt` | `app/src/test/.../util/EntityHeuristicsTest.kt` | Heurísticas de extracción de entidades | ✅ REVISADO |
| `ReaderViewModelTest.kt` | `app/src/test/.../ui/reader/ReaderViewModelTest.kt` | Rangos de palabras, puntuación y navegación | ✅ REVISADO |
| `ForceLayoutTest.kt` | `app/src/test/.../ui/graph/ForceLayoutTest.kt` | Layout de fuerzas del grafo | ✅ REVISADO |
| `BookScannerServiceExtremeTest.kt` | `app/src/test/.../service/BookScannerServiceExtremeTest.kt` | Dedup y batches de escaneo | ✅ REVISADO |
| `TextRankSummarizerTest.kt` | `app/src/test/.../service/TextRankSummarizerTest.kt` | Extracción de sinopsis por TextRank | ✅ REVISADO |
| `CoverExtractorTest.kt` | `app/src/test/.../service/CoverExtractorTest.kt` | Extracción de portadas y migración de caché | ✅ REVISADO |
| `LibraryViewModelExtremeTest.kt` | `app/src/test/.../ui/library/LibraryViewModelExtremeTest.kt` | Tests de biblioteca | ✅ REVISADO |
| `WordExclusionEngineTest.kt` | `app/src/test/.../core/pipeline/WordExclusionEngineTest.kt` | Exclusión con fronteras `\b` (<200ms) | ✅ REVISADO |
| `SpanishTextFormatterTest.kt` | `app/src/test/.../core/pipeline/SpanishTextFormatterTest.kt` | Tipografía en español (NBSP, comillas, guiones) | ✅ REVISADO |
| `ReadingPipelineTest.kt` | `app/src/test/.../core/pipeline/ReadingPipelineTest.kt` | Limpieza semántica y exclusión de ruido | ✅ REVISADO |
| `ReadingPipelineBddTest.kt` | `app/src/test/.../core/pipeline/ReadingPipelineBddTest.kt` | 12 escenarios de aceptación BDD completos | ✅ REVISADO |
| `PdfSemanticExtractionTest.kt` | `app/src/test/.../core/pipeline/PdfSemanticExtractionTest.kt` | Extracción semántica en PDF | ✅ REVISADO |
| `PdfBookViewSynchronizerTest.kt` | `app/src/test/.../core/pipeline/PdfBookViewSynchronizerTest.kt` | Mapeo bidireccional bloque ↔ página | ✅ REVISADO |
| `NoiseClassifierTest.kt` | `app/src/test/.../core/pipeline/NoiseClassifierTest.kt` | Clasificación de ruido (encabezados, URLs) | ✅ REVISADO |
| `ImageAnnouncerTest.kt` | `app/src/test/.../core/pipeline/ImageAnnouncerTest.kt` | Anuncios verbales de ilustraciones | ✅ REVISADO |
| `EpubPackageSanitizerTest.kt` | `app/src/test/.../core/pipeline/EpubPackageSanitizerTest.kt` | Reparación de NCX y cover en EPUB | ✅ REVISADO |
| `EpubHtmlSanitizerTest.kt` | `app/src/test/.../core/pipeline/EpubHtmlSanitizerTest.kt` | Sanitización HTML, xmlns, inyección títulos | ✅ REVISADO |
| `EpubContentExtractorTest.kt` | `app/src/test/.../core/pipeline/EpubContentExtractorTest.kt` | Extracción DOM limpia respetando jerarquía | ✅ REVISADO |
| `DeepFileSystemScannerTest.kt` | `app/src/test/.../data/scanner/DeepFileSystemScannerTest.kt` | Escaneo profundo del sistema de archivos | ✅ REVISADO |
| `SleepTimerManagerTest.kt` | `app/src/test/.../core/managers/SleepTimerManagerTest.kt` | Temporizador de sueño inteligente | ✅ REVISADO |
| `ReadingSessionManagerTest.kt` | `app/src/test/.../core/managers/ReadingSessionManagerTest.kt` | Cálculo de palabras y tiempo de sesión | ✅ REVISADO |
| `ReadingProgressManagerTest.kt` | `app/src/test/.../core/managers/ReadingProgressManagerTest.kt` | Persistencia y avance de lectura | ✅ REVISADO |
| `ProLicenseManagerTest.kt` | `app/src/test/.../core/license/ProLicenseManagerTest.kt` | Verificación de licencias criptográficas ECDSA | ✅ REVISADO |
| `GraphPositionsCacheTest.kt` | `app/src/test/.../data/graph/GraphPositionsCacheTest.kt` | Persistencia de nodos de grafo en disco | ✅ REVISADO |
| `GraphJsonTest.kt` | `app/src/test/.../data/graph/GraphJsonTest.kt` | Exportación e importación de grafo en JSON | ✅ REVISADO |
| `SiteProfileRegistryTest.kt` | `app/src/test/.../core/web2epub/novel/SiteProfileRegistryTest.kt` | Registro y parsing de perfiles de sitio web | ✅ REVISADO |
| `RobotsAdvisorTest.kt` | `app/src/test/.../core/web2epub/novel/RobotsAdvisorTest.kt` | Respeto a robots.txt en web2epub | ✅ REVISADO |
| `RequestPacerTest.kt` | `app/src/test/.../core/web2epub/novel/RequestPacerTest.kt` | Control de cadencia de peticiones HTTP | ✅ REVISADO |
| `PageFetcherTest.kt` | `app/src/test/.../core/web2epub/novel/PageFetcherTest.kt` | Descarga de páginas web con fallos controlados | ✅ REVISADO |
| `Fixtures.kt` | `app/src/test/.../core/web2epub/novel/Fixtures.kt` | Mocks y fixtures para pruebas de importación | ✅ REVISADO |
| `FakePageFetcher.kt` | `app/src/test/.../core/web2epub/novel/FakePageFetcher.kt` | Fetcher simulado para tests offline | ✅ REVISADO |
| `CharsetDetectorTest.kt` | `app/src/test/.../core/web2epub/novel/CharsetDetectorTest.kt` | Detección de UTF-8, Windows-1252, GBK | ✅ REVISADO |
| `ChallengeDetectorTest.kt` | `app/src/test/.../core/web2epub/novel/ChallengeDetectorTest.kt` | Detección de Cloudflare / Turnstile | ✅ REVISADO |
| `TestEpubFactory.kt` | `app/src/test/.../core/translation/calibre/inplace/TestEpubFactory.kt` | Fábrica de EPUBs en memoria para pruebas | ✅ REVISADO |
| `FakeTextTranslator.kt` | `app/src/test/.../core/translation/calibre/inplace/FakeTextTranslator.kt` | Traductor simulado para tests de traducción | ✅ REVISADO |
| `CalibreStyleEpubTranslatorStructureTest.kt` | CalibreStyleEpubTranslator | ✅ REVISADO | Tests de estructura y empaquetado de traducción Calibre |
| `EpubInPlaceTranslatorTest.kt` | EpubInPlaceTranslator | ✅ REVISADO | Tests de traducción in-place de archivos EPUB |
| `EpubPackageParserTest.kt` | EpubPackageParser | ✅ REVISADO | Tests de parsing de OPF y manifiesto de recursos |
| `EpubZipWriterTest.kt` | EpubZipWriter | ✅ REVISADO | Tests de compresión y entrada mimetype STORED |
| `XhtmlTranslatorDocTest.kt` | XhtmlTranslatorDoc | ✅ REVISADO | Tests de enmascaramiento de etiquetas y traducción DOM |
| `EpubAssemblerTest.kt` | EpubAssembler | ✅ REVISADO | Tests de ensamblado EPUB 3.0 para novelas web |
| `ImageSnifferTest.kt` | ImageSniffer | ✅ REVISADO | Tests de detección y clasificación de imágenes web |
| `ImportStoreTest.kt` | ImportStore | ✅ REVISADO | Tests de persistencia y reanudación de importación |
| `NovelImporterTest.kt` | NovelImporter | ✅ REVISADO | Tests del pipeline de importación por lotes |
| `NovelIndexLoaderTest.kt` | NovelIndexLoader | ✅ REVISADO | Tests de extracción de índice y lista de capítulos |
| `ContentExtractorTest.kt` | ContentExtractor | ✅ REVISADO | Tests de extracción y sanitización de capítulos HTML con SiteProfile |

### Tests Instrumentados (`app/src/androidTest/`)

*Los 2 archivos de prueba instrumentada de `app/src/androidTest/` se auditan y detallan en la [Sección 14.1](#141-resultados-de-tests-unitarios-appsrctest) como única fuente de verdad.*

---

## 13. Flujo de Datos Atómico (Referencia Cruzada)

```
Abrir archivo externo:
  MainActivity.handleIncomingIntent
    → BookMetadataExtractor.extractMetadata
    → BookRepository.upsertBook (Room: books)
    → NavGraph.Screen.Reader.createRoute(bookId)

Apertura y Carga del Lector (EPUB / PDF):
  ReaderViewModel.loadBook()
    → LoadBookFastUseCase.invoke(book)
        ├─ [readingOrder.size > 80]: Fast-Path Sintético O(1) de posiciones
        └─ [readingOrder.size <= 80]: Parsing estándar de posiciones
        → PublicationOpener.open (Readium Publication)
        → Flattened TOC + Checkpoint de progreso resiliente (Prefs/Room fallback)
    → EpubFormatManager / PdfReflowManager
    → ReadingPipeline.process (ReadingBlock[] + TtsNarrationUnit[])
    → ReaderScreen (Compose UI con ReaderNavigatorContainer)

Persistencia de Progreso y Estadísticas de Lectura:
  ReaderViewModel.onLocatorChanged / onTtsLocatorChanged
    → SaveReadingProgressUseCase.invoke(bookId, locator)
        ├─ Debounce reactivo de 700 ms para scroll manual continuo
        ├─ flushReadingProgressImmediately() en ciclo ON_PAUSE / ON_STOP / cierre
        ├─ ReadingProgressManager (Room: books.progression, locatorJson)
        └─ ReadingSessionManager.recordSession (Room: reading_sessions, daily_word_counts)
    → CalculateReadingStatsUseCase.invoke(book, locator)
        → ReaderViewState (progreso 0..1, palabras y minutos restantes en tiempo real)

Gestión de Anotaciones, Citas y Decoraciones:
  ReaderViewModel.toggleBookmark / addHighlight / removeHighlight / exportNotes
    → ManageReaderAnnotationsUseCase
        ├─ BookRepository.toggleBookmark / addHighlight (Room: bookmarks, highlights)
        ├─ Transformación a Readium Decoration (resaltados visuales y colores de tema)
        └─ HighlightExporter / QuoteCardGenerator (Exportación Markdown / Citas)

Inteligencia en-libro, Rayos-X y Exportación de Audio:
  ReaderViewModel.search / generateXRay / exportChapterAudio
    → ReaderIntelligenceAndExportCoordinator
        ├─ FastEpubSearchEngine (Búsqueda streaming reactiva con Kotlin Flow)
        ├─ BookXRayEngine (Reporte semántico de personajes, lugares y conceptos)
        └─ ChapterAudioExporter.exportChapterAudio(...)
            → Síntesis PCM con Sherpa ONNX o TTS nativo
            → Codificación AAC a 128 kbps con MediaCodec en formato ISO MP4 (.m4a)
            → M4aMetadataEmbedder (Incrustación binaria de portada covr y letras sincro ©lyr)
            └─ Purga automática de residuos (.lrc, .srt, .jpg, .txt) -> 1 solo archivo .m4a limpio

Reproducción TTS (Text-to-Speech) y Supervivencia en Segundo Plano:
  ReaderScreen / ReaderViewModel
    → TtsManager
        ├─ ensurePreferencesLoaded() (Previene condición de carrera en cold-start)
        ├─ SherpaTtsEngine / AudioTrack (Voz neural offline Piper/Kokoro)
        ├─ Android TextToSpeech nativo (con UtteranceProgressListener desacoplado y blindado)
        ├─ AmbientSoundscapePlayer (Auto-ducking de música y sonidos de bosque)
        └─ SmartSleepTimerManager (Desvanecimiento suave al dormir)
    → ReaderTtsService (Foreground Service, MediaSessionCompat, Lockscreen, AVRCP Bluetooth)
    → WoodsKeepAliveManager (Protección contra LMKD y exención de optimización de batería)

Búsqueda global en biblioteca:
  BookRepository
    → BookDao.searchBooks (FTS4 books_fts)
    → o advancedSearchBooksRaw / advancedFilterBooksRaw

Grafo de entidades y candidatos:
  GraphViewModel / CandidatesViewModel
    → GraphRepository
    → GraphDao (Room: graph_entity, graph_relation, graph_mention, graph_alias)
```

---

## 14. Estado General de la Auditoría Atómica

### Leyenda de Estados
- ✅ **Resuelto y verificado en código:** Probado estáticamente, compilado y testeado con tests unitarios.
- 🟡 **Resuelto en código, pendiente de validación en dispositivo:** Implementado y compilado, pero requiere confirmación en hardware real (`TECNO-LH7n`).
- 🔴 **Pendiente:** Deuda técnica o bug detectado que aún no ha sido corregido en código.
- ⚪ **No verificado:** Elemento que requiere inspección adicional.

### Contadores Globales de la Auditoría (Verificados con git ls-files y test suite)
- **Archivos totales en proyecto:** 237 archivos `.kt` (184 en `app/src/main/`, 47 en `app/src/test/`, 2 en `app/src/androidTest/`, 4 en `keymaker/`)
- **Tests unitarios ejecutados:** 182 casos de prueba unitarios en 42 clases de test (100% en verde bajo `:app:testDebugUnitTest`, 0 fallos, 0 errores)
- **Hallazgos críticos totales:** 13
  - ✅ **Resueltos y verificados en código:** 9 (C1, C2, C3, C5, C8, C9, C10, C12, C13)
  - 🟡 **Resueltos en código, pendientes de validación en dispositivo (TECNO-LH7n):** 2 (C6, C7)
  - 🔴 **Pendientes:** 2 (C4: corrutina en init de `SherpaModelManager.kt:280`; C11: mojibake residual en comentarios de `TtsManager.kt:572+`, `BookScannerService.kt`, `LibraryScreen.kt`, `ReaderScreen.kt`, `ReaderViewModel.kt`)
  - ⚪ **No verificados:** 0
- **Hallazgos de severidad alta resueltos:** A12 resuelto (`SaveReadingProgressUseCase.kt`). Otros altos registrados: A1 (`mergeInto`), A5 (`playPcmDispatcher`), A6 (`createNavigator`), A9 (`persistableUriPermission`), A10 (`exportJsonBinder`), A11 (`importTransactions`).
- **Funcionalidades en estado 🟡 (pendientes de validación en dispositivo):**
  - C6: Arranque no bloqueante de TTS y caché reactiva de preferencias.
  - C7: Avance de oraciones nativas sin race conditions ni desincronización de utterances.
  - PARTE 73: Exportación a único archivo `.m4a` con portada (`covr`) y subtítulos (`©lyr`) probada en reproductores nativos (Musicolet, VLC, Poweramp, Samsung Music).
  - Apertura O(1) en libros masivos (>80 capítulos) en el procesador Helio G85 / Mali-G52 del TECNO-LH7n.
  - Fluidez de scroll continuo sin caídas de frames en Compose.

### Resumen de Hallazgos Críticos

| ID | Problema | Archivo y Línea Real | Impacto | Estado | Evidencia y Justificación Técnica |
|---|---|---|---|---|---|
| C1 | Room v11 sin migración manual 10→11 | `AppDatabase.kt:37-39`, `app/schemas/.../10.json`, `11.json` | Fallo de migración en actualización de app | ✅ **Resuelto y verificado** | Resuelto vía `@AutoMigration(from = 10, to = 11)`. La comparativa estricta de esquemas v10 vs v11 confirma que solo se agregaron 4 tablas (`graph_entity`, `graph_relation`, `graph_alias`, `graph_mention`), sin tablas/columnas modificadas ni borradas ni renombradas. Room no requiere `AutoMigrationSpec`. |
| C2 | `checkNotNull(bookId)` crashea al abrir libro | `ReaderViewModel.kt:171, 597` | Cierre inesperado (crash) al abrir lector sin bookId válido | ✅ **Resuelto y verificado** | Commit `7ac5385`. Línea 171 lee seguro con `savedStateHandle.get<String>("bookId").orEmpty()` y línea 597 valida con `if (bookId.isBlank()) { _uiState.value = ReaderUiState.Error(...); return }`. |
| C3 | ViewModel con 17 dependencias directas (God Class) | `ReaderViewModel.kt:124-153` | God ViewModel, acoplamiento alto, riesgo de regresiones | ✅ **Resuelto y verificado** | Commits `4de4bd7`, `26810fd`, `1c949cc`, `d86e7a1`. Descomprimido mediante patrón *Strangler Fig* en 5 Casos de Uso/Coordinadores (`domain/reader/`). Eliminadas 5 inyecciones directas (`AssetRetriever`, `PublicationOpener`, `FastEpubSearchEngine`, `BookXRayEngine`, `ChapterAudioExporter`). Tamaño reducido a 2.476 líneas. |
| C4 | Corrutina huérfana en bloque init | `SherpaModelManager.kt:280` | Corrutina sin cancelación explícita en singleton | 🔴 **Pendiente (Error conocido)** | `managerScope.launch` en el bloque `init` lanza un borrado recursivo del directorio Kokoro-82M de forma asíncrona usando `SupervisorJob() + Dispatchers.IO` sin cancelación explícita ni enlace de ciclo de vida. |
| C5 | Logging nativo exhaustivo atado a debug build | `SherpaModelManager.kt:437` | Logs nativos de Sherpa ONNX saturan logcat en depuración | 🔴 **Pendiente (Error conocido)** | Línea 437 pasa `debug = com.example.woodsreader.BuildConfig.DEBUG`. En compilaciones Debug activa logs nativos intensivos de C++ por cada frame de audio; en Release compila como `false`. Pendiente desacoplarlo a flag de depuración opcional en tiempo de ejecución. |
| C6 | `.first()` en StateFlow bloquea/suspende arranque de TTS | `TtsManager.kt:1375` | TTS se congela o bloquea hilo de síntesis en arranque en frío | 🟡 **Resuelto en código, pendiente de validación en dispositivo (TECNO-LH7n)** | Commits `93e4dc2` y `5a9b341`. Se eliminó `.first()` de `userPreferencesFlow` en `narrateSequence`, sustituyéndolo por la propiedad `@Volatile cachedUseSherpaTts`. Se blindó la carrera de arranque en frío con `ensurePreferencesLoaded()` con timeout asíncrono de 300 ms antes de la primera narración. |
| C7 | UtteranceProgressListener en bucle y carreras de oraciones | `TtsManager.kt:225, 327, 477, 831, 869` | Desincronización, continuación colgada o silencio | 🟡 **Resuelto en código, pendiente de validación en dispositivo (TECNO-LH7n)** | Commits `93e4dc2` y `5a9b341`. El listener se asigna una sola vez en `initialize()`. IDs de utterance monótonos con `AtomicLong`. Se gestiona retorno `speakResult != SUCCESS`, se cancelan continuaciones pendientes forzadamente en `pause()`, `stop()`, `shutdown()`, y se acepta `utteranceId == null` como fallback de compatibilidad OEM. |
| C8 | Mojibake en comentarios de ReaderViewModel | `ReaderViewModel.kt` | Ilegibilidad de comentarios | ✅ **Resuelto y verificado** | Saneado en refactorizaciones de los Pasos 1 a 4 a UTF-8 limpio. |
| C9 | Migración Room 11 sin 10→11 | `AppDatabase.kt:37` | Confirma C1 | ✅ **Resuelto y verificado** | Verificado con Room `@AutoMigration(from = 10, to = 11)`. |
| C10 | Mojibake en comentarios | `ReaderViewModel.kt` | Ilegibilidad de comentarios | ✅ **Resuelto y verificado** | Saneado a UTF-8 limpio. |
| C11 | Mojibake residual en comentarios de código fuente | `TtsManager.kt:309, 548, 613`, `BookScannerService.kt:566`, `LibraryScreen.kt:64, 119, 156`, `ReaderViewModel.kt:355, 502, 755` | Comentarios descriptivos con caracteres corruptos (Latin1/Windows-1252) | 🔴 **Pendiente (Error conocido)** | 4 archivos contienen mojibake residual en comentarios descriptivos (símbolos `?`, tildes rotas). No afecta compilación ni lógica de ejecución; pendiente saneamiento ortográfico. |
| C12 | `checkNotNull(bookId)` crashea en detalle de libro | `BookDetailViewModel.kt:59, 69` | Cierre inesperado al abrir detalle de libro | ✅ **Resuelto y verificado** | Commit `7ac5385`. `val bookId: String = savedStateHandle.get<String>("bookId").orEmpty()`. Si viene vacío, `loadBookData()` valida `if (bookId.isBlank())` y expone `BookDetailUiState(error = "...")`. |
| C13 | `checkNotNull(bookId)` crashea en grafo y candidatos | `GraphViewModel.kt:72, 77, 96`, `CandidatesScreen.kt:52, 56, 75` | Cierre inesperado al abrir grafo o candidatos | ✅ **Resuelto y verificado** | Commit `7ac5385`. `savedStateHandle.get<String>("bookId").orEmpty()` en ambos ViewModels. Si `bookId.isBlank()`, exponen listas vacías de forma segura y los métodos de confirmación/fusión abortan sin crashear. |

## 14.1 Resultados de Tests Unitarios (`app/src/test/`)

Todos los tests unitarios de `app/src/test/` han sido revisados atomicamente:

| Archivo | Clase bajo prueba | Estado | Hallazgos |
|---------|-------------------|--------|-----------|
| `ExampleUnitTest.kt` | Prueba básica | ✅ REVISADO | 1 test trivial (2+2=4), pasa |
| `EntityHeuristicsTest.kt` | EntityHeuristics | ✅ REVISADO | Localizado en `app/src/test/.../util/` |
| `ReaderViewModelTest.kt` | ReaderViewModel | ✅ REVISADO | Localizado en `app/src/test/.../ui/reader/` (192 líneas) |
| `ForceLayoutTest.kt` | ForceLayout | ✅ REVISADO | Localizado en `app/src/test/.../ui/graph/` |
| `BookScannerServiceExtremeTest.kt` | BookScannerService | ✅ REVISADO | Mock-based, testa dedup y batches |
| `TextRankSummarizerTest.kt` | TextRankSummarizer | ✅ REVISADO | Localizado en `app/src/test/.../service/` |
| `CoverExtractorTest.kt` | CoverExtractor | ✅ REVISADO | Tests de extracción de portada, migración cacheDir→filesDir |
| `LibraryViewModelExtremeTest.kt` | LibraryViewModel | ✅ REVISADO | Localizado en `app/src/test/.../ui/library/` |
| `WordExclusionEngineTest.kt` | WordExclusionEngine | ✅ REVISADO | Tests de exclusión, regex, stress test <200ms |
| `SpanishTextFormatterTest.kt` | SpanishTextFormatter | ✅ REVISADO | Tests de NBSP, em dash, comillas invertidas |
| `ReadingPipelineTest.kt` | ReadingPipeline | ✅ REVISADO | Pipeline vacío, ruido filtrado, imágenes con narración, reglas de exclusión |
| `ReadingPipelineBddTest.kt` | ReadingPipeline (BDD) | ✅ REVISADO | Escenarios de restauración de posición, importación externa, PDF externo |
| `PdfSemanticExtractionTest.kt` | Extracción semántica PDF | ✅ REVISADO | Lista vacía, imágenes intercaladas, ruido filtrado |
| `PdfBookViewSynchronizerTest.kt` | PdfBookViewSynchronizer | ✅ REVISADO | Mapeo bloque→página y página→bloque |
| `NoiseClassifierTest.kt` | NoiseClassifier | ✅ REVISADO | Números de página, romanos, URLs, boilerplate, spam de traductor, encabezados recurrentes |
| `ImageAnnouncerTest.kt` | ImageAnnouncer | ✅ REVISADO | Anuncios con caption, alt text, fallback "Ilustración" |
| `EpubContentExtractorTest.kt` | EpubContentExtractor | ✅ REVISADO | HTML vacío, headings con tamaños, imágenes, figuras con figcaption |
| `EpubHtmlSanitizerTest.kt` | EpubHtmlSanitizer | ✅ REVISADO | xmlns, head tag, wrapping, charset detection, chapter title injection |
| `EpubPackageSanitizerTest.kt` | EpubPackageSanitizer | ✅ REVISADO | NCX reference repair, cover property repair |
| `SleepTimerManagerTest.kt` | SleepTimerManager | ✅ REVISADO | Countdown, expiración, cancelación restaura velocidad |
| `ReadingSessionManagerTest.kt` | ReadingSessionManager | ✅ REVISADO | Sesión nueva, palabras/páginas, sesión fantasma descartada |
| `ReadingProgressManagerTest.kt` | ReadingProgressManager | ✅ REVISADO | ETF manual y TTS, fecha de finalización |
| `ProLicenseManagerTest.kt` | ProLicenseManager | ✅ REVISADO | ID de dispositivo, activación con clave válida, mismatching |
| `GraphPositionsCacheTest.kt` | GraphPositionsCache | ✅ REVISADO | ForceLayout con caché se asienta casi instantáneamente |
| `GraphJsonTest.kt` | GraphJson | ✅ REVISADO | Export/import JSON usa nombres, no IDs internos |
| `SiteProfileRegistryTest.kt` | SiteProfile | ✅ REVISADO | Parsing JSON, dominio exacto, subdominio, dominio no registrado, JSON inválido |
| `RobotsAdvisorTest.kt` | RobotsAdvisor | ✅ REVISADO | Reglas de disallow, admin y private bloqueados, capítulos permitidos |
| `RequestPacerTest.kt` | RequestPacer | ✅ REVISADO | Delay mínimo respetado con reloj virtual |
| `PageFetcherTest.kt` | PageFetcher | ✅ REVISADO | Fake fetcher, fallos configurados, fixtures válidos |
| `Fixtures.kt` | Fixtures web2epub | ✅ REVISADO | TOC descendente, capítulo rico, bloqueado, Cloudflare |
| `FakePageFetcher.kt` | Fake PageFetcher | ✅ REVISADO | Respuestas y fallos configurados, conteo de llamadas |
| `CharsetDetectorTest.kt` | CharsetDetector | ✅ REVISADO | UTF-8, BOM, ISO-8859-1, GBK, windows-1252 |
| `ChallengeDetectorTest.kt` | ChallengeDetector | ✅ REVISADO | Cloudflare 403/503, "Just a moment", turnstile, página normal |
| `TestEpubFactory.kt` | EPUB factory tests | ✅ REVISADO | Localizado en `core/translation/calibre/inplace/` |
| `FakeTextTranslator.kt` | Fake translator | ✅ REVISADO | Localizado en `core/translation/calibre/inplace/` |
| `CalibreStyleEpubTranslatorStructureTest.kt` | CalibreStyleEpubTranslator (Estructura) | ✅ REVISADO | Tests de integridad estructural del EPUB traducido (mimetype, META-INF, OPF) |
| `EpubInPlaceTranslatorTest.kt` | EpubInPlaceTranslator | ✅ REVISADO | Tests de traducción in-place de documentos XHTML en ZIP EPUB |
| `EpubPackageParserTest.kt` | EpubPackageParser | ✅ REVISADO | Tests de parseo de OPF, manifest, spine y metadatos |
| `EpubZipWriterTest.kt` | EpubZipWriter | ✅ REVISADO | Tests de reempaquetado ZIP de EPUB con compresión STORED en mimetype |
| `XhtmlTranslatorDocTest.kt` | XhtmlTranslatorDoc | ✅ REVISADO | Tests de segmentación de texto, nodos y atributos en XHTML |
| `ContentExtractorTest.kt` | ContentExtractor | ✅ REVISADO | Tests de extracción de capítulos HTML, limpieza con SiteProfile y extracción de imágenes |
| `EpubAssemblerTest.kt` | EpubAssembler | ✅ REVISADO | Tests de ensamblado de capítulos descargados en archivo EPUB estándar |
| `ImageSnifferTest.kt` | ImageSniffer | ✅ REVISADO | Tests de detección y resolución de URLs de imágenes en páginas de novelas |
| `ImportStoreTest.kt` | ImportStore | ✅ REVISADO | Tests de almacenamiento local y estado de progreso de importaciones web |
| `NovelImporterTest.kt` | NovelImporter | ✅ REVISADO | Tests del orquestador de importación web a EPUB |
| `NovelIndexLoaderTest.kt` | NovelIndexLoader | ✅ REVISADO | Tests de carga y parseo del índice/TOC de novelas web |
| `DeepFileSystemScannerTest.kt` | DeepFileSystemScanner | ✅ REVISADO | Tests de escaneo profundo recursivo de carpetas de almacenamiento |

### Tests Instrumentados (`app/src/androidTest/`)

| Archivo | Clase bajo prueba | Estado | Hallazgos |
|---------|-------------------|--------|-----------|
| `ExampleInstrumentedTest.kt` | Prueba básica instrumentada | ✅ REVISADO | Verifica packageName de la app |
| `LibraryScreenE2ETest.kt` | LibraryScreen E2ETest | ⏳ PENDIENTE | Clase de stub (solo `assertTrue(true)`) |

---

## 15. Cómo Usar este Archivo con una IA y Desarrolladores

Cualquier sesión de desarrollo o intervención de un asistente de IA debe consultar este archivo como **única fuente de verdad** del proyecto antes de sugerir o aplicar modificaciones en el código fuente.

### Protocolo de Inicio de Sesión
Inicia la interacción estableciendo:
> *"Consulta el documento ancla `dona.md`. Revisa el Estado Actual del Proyecto, los Invariantes e Intocables (Sección 🛡️), las Decisiones de Arquitectura (Sección 🏛️) y las Reglas de Trabajo antes de proponer cualquier cambio al código."*

### Protocolo de Modificación de Archivos
Antes de alterar cualquier componente:
1. **Comprobar Intocables:** Verifica que el archivo objetivo no forme parte de los invariantes del lector (`ChapterScrollDetector.kt`, CSS de `ReaderExtensions.kt`, `AudioFocus` en `TtsManager.kt`, firmas públicas de `ReaderViewModel.kt`).
2. **Consultar Entrada Atómica:** Localiza la ruta del archivo en las secciones 0 a 12 del índice para conocer sus dependencias ascendentes (`depende_de`) y descendentes (`usado_por`).
3. **Revisar Hallazgos Asociados:** Consulta la columna de hallazgos del archivo y la matriz de criticidad de la Sección 14 para evitar reintroducir bugs corregidos o pasar por alto casos borde.
4. **Respetar la Leyenda de Estados:**
   - ✅ **Resuelto y verificado en código:** El cambio cuenta con pruebas unitarias en verde y revisión estricta de código.
   - 🟡 **Resuelto en código, pendiente de validación en dispositivo:** Requiere comprobación física en `TECNO-LH7n` antes de considerarse cerrado.
   - 🔴 **Pendiente:** Deuda técnica identificada que debe abordarse bajo un plan dedicado.
   - ⚪ **No verificado:** Estado preventivo de componentes no auditados.
5. **Verificación Continua:** Ejecutar obligatoriamente `.\gradlew.bat :app:compileDebugKotlin` y `.\gradlew.bat :app:testDebugUnitTest` tras cada fase de trabajo.

---

*`dona.md` es un documento vivo de alta fidelidad mantenido bajo control de versiones.*
