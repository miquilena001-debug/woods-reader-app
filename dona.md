# dona.md — Documento Ancla e Índice Atómico Completo de Woods Reader

**Propósito:** Documento ancla maestro y fuente de verdad técnica para que cualquier desarrollador o asistente de IA entienda instantáneamente la arquitectura, invariantes, decisiones de diseño, estado real y estructura de cada archivo de Woods Reader sin requerir el historial de chats previo.

---

## 0.0 Estado Actual del Proyecto (29/10/2026)
- **Base verificada tras Tarea P2:** HEAD `e721a31` (`master`).
- **Build configurado:** `versionName = "1.0.10"`, `versionCode = 11` en `app/build.gradle.kts`.
- **Inventario estático:** 259 archivos Kotlin seguidos por Git (producción y tests); 58 archivos Kotlin en `app/src/test/`; suite unitaria ejecutada: **269 casos de test en verde (0 fallos, 0 errores)**.
- **ReaderViewModel:** 2.167 líneas y 119.062 bytes. Constructor verificado por script: **19 parámetros totales** (2 de framework: `Application context` y `SavedStateHandle savedStateHandle` del que sale `bookId` + 17 dependencias inyectadas).
- **Alcance:** este archivo es el índice de código y mapa técnico; el estado de pendientes está en `ROADMAP.md`, la arquitectura resumida en `ARCHITECTURE.md` y los documentos anteriores en `docs/archive/README.md`.

Los resultados de compilación o pruebas mencionados en secciones históricas deben interpretarse en su fecha original, no como resultados de HEAD actual.
---

## 0.1 Mapa de Capas de la Arquitectura

Woods Reader sigue una arquitectura modular en capas orientada al dominio y altamente reactiva. La capa de presentación (Jetpack Compose + ViewModels desacoplados) interactúa exclusivamente con Casos de Uso y Coordinadores de dominio, los cuales encapsulan la lógica de lectura, audio, búsqueda y anotaciones. El dominio delega la persistencia a Repositorios Room/DataStore y la renderización/síntesis a motores de bajo nivel (Readium 3.x para EPUB/PDF y Sherpa ONNX / Android TTS para síntesis vocal), mientras que Servicios en primer plano (*Foreground Services*) y Workers en segundo plano garantizan la supervivencia ininterrumpida de la reproducción de audio y tareas asíncronas pesadas.

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                             CAPA DE UI / PRESENTACIÓN                       │
│    Jetpack Compose UI (Screens, BottomSheets, Dialogs, Theme, Canvas)      │
│         ▲                                                            │
│         │ Observa StateFlows (sin recomposiciones masivas)           │
│         ▼                                                            │
│    ViewModels (@HiltViewModel: ReaderViewModel, LibraryViewModel, etc.)     │
└─────────────────────────────────────┬───────────────────────────────────────┘
                                      │ Invoca
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────┐
│                              CAPA DE DOMINIO                                │
│    Casos de Uso: SaveReadingProgressUseCase, CalculateReadingStatsUseCase,   │
│                  LoadBookFastUseCase, ManageReaderAnnotationsUseCase        │
│    Coordinadores: ReaderIntelligenceAndExportCoordinator                    │
└───────────────────────┬─────────────────────────────┬───────────────────────┘
                        │ Delega                      │ Orquesta
                        ▼                             ▼
┌───────────────────────────────────┐   ┌─────────────────────────────────────┐
│          CAPA DE DATOS            │   │         MOTORES CORE Y AUDIO        │
│  Repositories (Book, Graph, Prefs)│   │  Readium 3.x (Publication, Locator) │
│  DAOs Room v11 (books, sessions)  │   │  Sherpa ONNX (Piper, VITS, Kokoro)  │
│  DataStore (UserPreferences)      │   │  Android TTS + Media3 / MediaCodec  │
│  Scanners (MediaStore, DeepFS)    │   │  M4aMetadataEmbedder (covr, ©lyr)   │
└───────────────────────────────────┘   └─────────────────────────────────────┘
                                      ▲
                                      │ Controla
┌─────────────────────────────────────┴───────────────────────────────────────┐
│                      SERVICIOS Y WORKERS EN BACKGROUND                      │
│    ReaderTtsService (Foreground) · WoodsKeepAliveManager (WakeLock/LMKD)   │
│    WorkManager (Sync, Graph, WebNovel)                        │
└─────────────────────────────────────────────────────────────────────────────┘
```

---

## 0.2 Invariantes e Intocables (Reglas del Proyecto)
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

## 0.3 Decisiones de Arquitectura y Motivos
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
   - *Decisión:* En lugar de generar hasta 7 archivos externos complementarios (`.jpg`, `.lrc`, `.srt`, `.txt`), la exportación de audio sintetizado genera exclusivamente un archivo `.m4a` que aloja la pista de audio AAC a 48 kbps (KEY_BIT_RATE = 48_000, voz optimizada), la portada JPEG en la caja `covr` y la letra sincronizada en la caja `©lyr` del átomo `moov.udta.meta.ilst`.
   - *Motivo:* Cero contaminación de carpetas en `Music/WoodsReader`, compatibilidad inmediata con reproductores estándar (Samsung Music, Poweramp, Musicolet, VLC) y portabilidad completa de la biblioteca de audio.

---

## 0.4 Glosario de Términos del Proyecto
- **`Locator`**: Objeto fundamental de Readium que representa una posición canónica y persistente dentro de un libro (href del spine, fragmento CFI/progression 0.0..1.0, y snippets de texto circundante). Permite restaurar el punto de lectura exacto entre reinicios y sincronizar la narración de audio con el texto visual.
- **`Publication`**: Estructura principal en memoria provista por Readium que representa el libro abierto (EPUB o PDF). Contiene la tabla de contenidos (`tableOfContents`), el orden de lectura (`readingOrder`), metadatos y acceso a recursos internos.
- **`Positions Sintéticas O(1)`**: Array virtual de progresiones matemáticas generado instantáneamente en `LoadBookFastUseCase` para libros con más de 80 capítulos/spines, omitiendo el cálculo intensivo de bytes de Readium para reducir el tiempo de apertura de tiempo lineal O(ZIP) a tiempo instantáneo O(N) en memoria (tiempo objetivo estimado: <200 ms) sin provocar ANR.
- **`Sherpa ONNX`**: Motor de síntesis de voz neural offline de última generación basado en redes neuronales (VITS, Piper, Kokoro). Corre localmente en el dispositivo sin enviar datos a internet ni consumir cuotas de API.
- **`ReadingBlock`**: Unidad semántica de texto estructurado generada por el `ReadingPipeline`. Agrupa párrafos continuos o encabezados tras limpiar ruido recurrente (números de página, encabezados repetitivos de página, créditos de maquetador).
- **`TtsNarrationUnit`**: Unidad de narración atómica (oración o párrafo) derivada de un `ReadingBlock`. Asocia el texto limpio a reproducir con su posición exacta en el texto (`Locator`) para resaltado y auto-scroll.
- **`AutoMigration (Room)`**: Mecanismo declarativo de AndroidX Room para evolucionar el esquema de base de datos entre versiones contiguas (ej. v10 → v11) sin scripts SQL manuales, aplicable cuando los cambios consisten exclusivamente en añadir nuevas tablas o columnas sin borrados ni renombres.
- **`M4aMetadataEmbedder`**: Componente de bajo nivel que escribe átomos MP4/ISO (`moov.udta.meta.ilst`) para incrustar portada en la caja `covr` y letras LRC sincronizadas en la caja `©lyr`, permitiendo exportaciones de audio autocontenidas en un único archivo `.m4a` limpio sin contaminación de archivos sueltos.
- **`Strangler Fig Pattern`**: Patrón de refactorización incremental que extrae responsabilidades de una clase monolítica (*God Class*) hacia casos de uso y coordinadores independientes de afuera hacia adentro, verificando que cada paso intermedio compile y pase el 100% de los tests antes del siguiente.

---

## 0.5 Reglas de Trabajo para Desarrolladores y AgENTES DE IA
1. **Punto de Restauración Previo Obligatorio:** Antes de aplicar cualquier cambio o refactorización, registrar el estado con `git add -A && git commit -m "..."`.
2. **Tolerancia Cero a Errores de Build:** Todo cambio debe verificar `:app:compileDebugKotlin` y `:app:testDebugUnitTest` con 0 errores antes de dar por buena una intervención.
3. **Atomicidad de Fases:** Un commit descriptivo por fase o componente. Nunca acumular múltiples tareas no relacionadas en un solo commit.
4. **Evidencia Antes de Aserción:** Prohibido afirmar que un bug está resuelto o que un módulo funciona sin haber inspeccionado el código fuente, ejecutado las pruebas o validado en logcat.
5. **Criterio de Validación Dispositivo (🟡 → ✅):** Todo hallazgo o refactor que involucre hardware o subsistemas de Android (TTS nativo, Sherpa ONNX, audio focus, ciclo de vida del WebView) se marca como 🟡 hasta ser ejecutado y comprobado físicamente en el teléfono (`TECNO-LH7n`).
6. **Mantenimiento del Documento Ancla:** Todo commit que modifique la firma o propósito de un archivo debe actualizar su entrada en `dona.md`.

---

## 0.6 Pendientes Priorizados y Roadmap
*(Referencia completa: [ROADMAP.md](file:///c:/Users/Orbit/Downloads/woods%20reader/ROADMAP.md))*

1. **Fase 1 del Roadmap: TTS Karaoke Sincronizado Palabra por Palabra**:
   - Implementación de resaltado visual continuo de palabras en el texto durante la narración TTS mediante los timestamps emitidos por los tokens de Sherpa ONNX.
2. **Fase 2 del Roadmap: Tipografía y Ortotipografía Española Avanzada**:
   - Formateo automatizado de diálogos con raya/guion largo (`—`), comillas latinas (`«` y `»`), espacios de no separación (NBSP) antes de signos dobles (`¿`, `¡`) y partición silábica en español.
3. **Fase 3 del Roadmap: Doble Vista para Documentos PDF**:
   - Alternancia fluida entre modo Reflow accesible (extracción semántica) y modo Réplica Original con zoom, conservando la sincronización de lectura bidireccional.
4. **Deuda Técnica Crítica Pendiente:**
   - 🟡 **C3 [Mitigado en código - Pendiente dispositivo]:** Descompresión de God Class en `ReaderViewModel.kt`. Líneas pasaron de 2.905 a 2.476 (casos de uso) y a 2.158 en HEAD tras P5/P6, con objetivo de diseño de ~600 líneas. Constructor reducido de 24 parámetros (2 de framework: `Application context` y `SavedStateHandle savedStateHandle` del que sale `bookId` + 22 dependencias de dominio/servicios, y no 17 ni 27) a 21 parámetros en HEAD (2 de framework + 19 dependencias inyectadas).
   - 🔴 **C4:** Cancelación ordenada de la corrutina de limpieza en `SherpaModelManager.kt:280`.
   - ⚪ **C5 [Falso positivo / No aplica]:** Logging nativo C++ de Sherpa en `SherpaModelManager.kt:437` pasa `debug = com.example.woodsreader.BuildConfig.DEBUG`. En release `BuildConfig.DEBUG` es `false`, desactivando los logs de depuración nativos en producción; nunca estuvo hardcodeado a `true` en release.
   - 🔴 **C11:** Saneamiento de mojibake residual fuera de `ReaderViewModel`: 24 ocurrencias en comentarios (`TtsManager.kt`, `BookScannerService.kt`, `LibraryScreen.kt`, `ReaderScreen.kt`) y 1 ocurrencia de código/regex con riesgo funcional en `MultilingualAudiobookNormalizer.kt:30` (`elongatedCharsRegex`). En `ui/reader/` se verifican 0 cadenas visibles de usuario afectadas.

---

## 0.7 Historial de Commits Recientes (Cadena de Refactor y Blindaje)

| Commit | Fecha | Tipo | Descripción |
|---|---|---|---|
| `fe11ed7` | 2026-10-28 | refactor | extraer ReaderVoiceAndAmbienceController para voces, ambientes y sleep timer (P6) |
| `548289e` | 2026-10-28 | docs | indexar ReaderChapterNavigationCoordinator y actualizar estado de M28 y ReaderViewModel |
| `b0a3f0b` | 2026-10-28 | refactor | extraer ReaderChapterNavigationCoordinator desacoplando navegación, caché y prefetch (P5) |
| `833a866` | 2026-10-28 | refactor | unificar cálculo de reading stats en updateReadingStats y resolver M28 (P5) |
| `75c91e5` | 2026-10-28 | docs | Fase 3: registro consolidado de errores, conexiones y riesgos en dispositivo |
| `029dd43` | 2026-10-28 | docs | Fase 2: mapa de conexiones Hilt, Room, Android y 9 flujos de datos |
| `e18afd7` | 2026-10-28 | docs | Fase 1: veracidad estricta, inventario 237/237 kt y hallazgos con evidencia |
| `5a9b341` | 2026-10-28 | fix | blindar carrera de preferencias en arranque en frío y evitar continuaciones colgadas en tts nativo |
| `93e4dc2` | 2026-10-28 | fix | eliminar `.first()` bloqueante y desacoplar UtteranceProgressListener en TtsManager (C6, C7) |
| `7ac5385` | 2026-10-28 | fix | lectura segura de bookId en savedStateHandle (C2, C12, C13) en ViewModels |
| `8a4013a` | 2026-10-28 | feat | PARTE 73: incrustar portada (covr) y subtítulos sincronizados (©lyr) directo en .m4a único vía M4aMetadataEmbedder |
| `d86e7a1` | 2026-10-28 | refactor | Paso 4: extraer ReaderIntelligenceAndExportCoordinator y desacoplar búsqueda/X-Ray/audio |
| `1c949cc` | 2026-10-28 | refactor | Paso 3: extraer ManageReaderAnnotationsUseCase y desacoplar lógica de marcadores/resaltados |
| `26810fd` | 2026-10-28 | refactor | Paso 2: extraer LoadBookFastUseCase y desacoplar apertura O(1) de libros |
| `4de4bd7` | 2026-10-28 | refactor | Paso 1: extraer SaveReadingProgressUseCase y CalculateReadingStatsUseCase |

---

## 0.8 Leyenda y Formato del Índice por Archivo
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
## 0.9 Mapa de Conexiones del Sistema

### 0.9.1 Grafo de Inyección de Dependencias (Hilt)
El proyecto utiliza Dagger-Hilt para inyección de dependencias con alcance singleton en el contenedor de aplicación:

```
[DatabaseModule]
  ├─ provideAppDatabase() ──────────────► AppDatabase (Room v11, WAL)
  ├─ provideAppTypeConverters() ────────► AppTypeConverters
  ├─ provideBookDao() ──────────────────► BookRepository, BookScannerService
  ├─ provideHighlightDao() ─────────────► BookRepository, ManageReaderAnnotationsUseCase
  ├─ provideBookmarkDao() ──────────────► BookRepository, ManageReaderAnnotationsUseCase
  ├─ provideReadingSessionDao() ────────► ReadingSessionManager
  ├─ provideReadingStatsDao() ──────────► ReadingSessionManager
  ├─ provideCollectionDao() ────────────► BookRepository
  ├─ provideTtsRuleDao() ───────────────► PronunciationLexiconManager
  └─ provideGraphDao() ─────────────────► GraphRepository

[DataStoreModule]
  └─ provideUserPreferencesDataStore() ─► UserPreferencesRepository

[LibraryModule]
  ├─ provideHttpClient() ───────────────► DefaultHttpClient (Readium)
  ├─ providePdfiumDocumentFactory() ────► PdfiumDocumentFactory (Readium PDF)
  └─ provideAssetRetriever() ───────────► PublicationOpener, LoadBookFastUseCase

[NetworkModule]
  └─ provideOkHttpClient() ─────────────► WebNovelImportWorker, GoogleFreeNetworkTranslator

[TtsModule]
  └─ provideTtsManager() ───────────────► ReaderViewModel, ReaderTtsService

[ScannerModule]
  ├─ bindMediaStoreScanner() ───────────► MediaStoreBookScanner
  └─ bindDeepFileSystemScanner() ───────► DeepFileSystemScanner
```

#### Inyección en ViewModels (@HiltViewModel) y Servicios:
- **`ReaderViewModel`:** Inyecta 26 dependencias de primer nivel:
  - Casos de Uso / Coordinadores: `SaveReadingProgressUseCase`, `CalculateReadingStatsUseCase`, `LoadBookFastUseCase`, `ManageReaderAnnotationsUseCase`, `ReaderIntelligenceAndExportCoordinator`.
  - Repositorios y Gestores de Dominio: `BookRepository`, `UserPreferencesRepository`, `ReadingSessionManager`, `ReadingProgressManager`, `TtsManager`, `SherpaModelManager`, `PronunciationLexiconManager`, `ProLicenseManager`, `WoodsKeepAliveManager`.
- **`LibraryViewModel`:** Inyecta `BookRepository`, `UserPreferencesRepository`, `SherpaModelManager`.
- **`BookDetailViewModel`:** Inyecta `BookRepository`, `SavedStateHandle` (con lectura segura .orEmpty()).
- **`GraphViewModel`:** Inyecta `GraphRepository`, `BookRepository`, `SavedStateHandle`.
- **`CandidatesViewModel`:** Inyecta `GraphRepository`, `SavedStateHandle`.
- **`SettingsViewModel`:** Inyecta `UserPreferencesRepository`, `SherpaModelManager`, `BackupManager`.
- **`StatsViewModel`:** Inyecta `BookRepository`, `ReadingSessionManager`.
- **`ReaderTtsService`:** Inyecta `TtsManager`, `UserPreferencesRepository`, `WoodsKeepAliveManager`.

---

### 0.9.2 Cadena de Datos (Persistencia Room → DAO → Repositorio → ViewModel → UI)

```
[Tablas Room]                 [DAOs]                 [Repositorios/Managers]            [ViewModels]               [Pantallas Compose]
┌─────────────────────────┐   ┌──────────────────┐   ┌──────────────────────────────┐   ┌──────────────────────┐   ┌─────────────────────────┐
│ books                   │──►│ BookDao          │──►│ BookRepository               │──►│ LibraryViewModel     │──►│ LibraryScreen           │
│ books_fts               │   │                  │   │                              │   │ BookDetailViewModel  │──►│ BookDetailScreen        │
│ collections             │──►│ CollectionDao    │   │                              │   │ ReaderViewModel      │──►│ ReaderScreen            │
│ collection_books        │   │                  │   │                              │   │ StatsViewModel       │──►│ ReadingStatsScreen      │
├─────────────────────────┼───┼──────────────────┼───┼──────────────────────────────┼───┼──────────────────────┼───┼─────────────────────────┤
│ reading_sessions        │──►│ ReadingSessionDao│──►│ ReadingSessionManager        │──►│ StatsViewModel       │──►│ ReadingStatsScreen      │
│ daily_word_counts       │   │ ReadingStatsDao  │   │ ReadingProgressManager       │   │ ReaderViewModel      │   │ ReaderScreen (Stats)    │
├─────────────────────────┼───┼──────────────────┼───┼──────────────────────────────┼───┼──────────────────────┼───┼─────────────────────────┤
│ highlights              │──►│ HighlightDao     │──►│ BookRepository               │──►│ ReaderViewModel      │──►│ ReaderScreen            │
│ bookmarks               │   │ BookmarkDao      │   │ ManageAnnotationsUseCase     │   │                      │   │ (ReaderNotesTab)        │
├─────────────────────────┼───┼──────────────────┼───┼──────────────────────────────┼───┼──────────────────────┼───┼─────────────────────────┤
│ tts_pronunciation_rules │──►│ TtsRuleDao       │──►│ PronunciationLexiconManager  │──►│ ReaderViewModel      │──►│ ReaderTtsMacroWindow    │
│                         │   │                  │   │                              │   │                      │   │ TtsRulesBottomSheet     │
├─────────────────────────┼───┼──────────────────┼───┼──────────────────────────────┼───┼──────────────────────┼───┼─────────────────────────┤
│ graph_entity            │──►│ GraphDao         │──►│ GraphRepository              │──►│ GraphViewModel       │──►│ GraphScreen             │
│ graph_relation          │   │                  │   │                              │   │ CandidatesViewModel  │   │ GraphCanvas             │
│ graph_mention           │   │                  │   │                              │   │                      │   │ CandidatesScreen        │
│ graph_alias             │   │                  │   │                              │   │                      │   │ EntitySheet             │
└─────────────────────────┘   └──────────────────┘   └──────────────────────────────┘   └──────────────────────┘   └─────────────────────────┘
```

---

### 0.9.3 Componentes de Android Declarados en el Manifest y Lanzadores

| Componente | Tipo Android | Permisos / Flags | Responsabilidad | Quién lo Inicia |
|---|---|---|---|---|
| `MainActivity` | `Activity` (SingleTop) | Permisos de almacenamiento, audio, bluetooth | Entrada principal, gestión de Deep Links, selector SAF e Intents de apertura (`VIEW` de archivos .epub/.pdf) | Sistema Android / Launcher |
| `ReaderTtsService` | `Service` (Foreground) | `FOREGROUND_SERVICE_MEDIA_PLAYBACK`, `WAKE_LOCK` | Reproducción continua TTS con pantalla bloqueada, notificación MediaStyle y sesión AVRCP Bluetooth | `ReaderViewModel`, `TtsManager` |
| `SystemForegroundService` | `Service` (WorkManager) | `FOREGROUND_SERVICE_DATA_SYNC` | Ejecutor de tareas diferidas de WorkManager | Sistema Android / WorkManager |
| `LibrarySyncWorker` | `CoroutineWorker` | Acceso a almacenamiento | Sincronización diferencial y verificación de integridad de archivos | Programado por WorkManager |
| `GraphExtractionWorker` | `CoroutineWorker` | Acceso a Room | Minería de entidades, aliases y menciones a partir del texto del libro | Disparado desde `GraphViewModel` |
| `WebNovelImportWorker` | `CoroutineWorker` | `INTERNET` | Descarga de capítulos web, sanitización y empaquetado a EPUB | Disparado desde `WebToEpubImporterDialog` |

---

### 0.9.4 Dependencias Externas y Archivos Consumidores

| Dependencia Externa | Versión / Tipo | Archivos que la Consumen Directamente | Rol en el Proyecto |
|---|---|---|---|
| **Readium 3.x** | Binario / Maven Central (31 archivos) | `LoadBookFastUseCase.kt`, `SaveReadingProgressUseCase.kt`, `CalculateReadingStatsUseCase.kt`, `ManageReaderAnnotationsUseCase.kt`, `ReaderIntelligenceAndExportCoordinator.kt`, `EpubFormatManager.kt`, `PdfReflowManager.kt`, `ReaderScreen.kt`, `ReaderViewModel.kt`, `FastEpubSearchEngine.kt`, `SherpaTtsEngine.kt`, `SherpaTtsEngineProvider.kt`, etc. | Core del lector: parseo de contenedores EPUB, extracción de Locator, decoraciones, renderizado de WebView y navegación por capítulos. |
| **Sherpa ONNX** | v1.10.30 via JitPack (2 archivos) | `SherpaModelManager.kt`, `TtsManager.kt` | Motor de síntesis de voz neural offline por hardware local (modelos VITS, Piper, Kokoro). Invoca binarios nativos C++ vía JNI. |
| **Media3 / MediaCompat** | AndroidX Media (3 archivos) | `ReaderTtsService.kt`, `SherpaTtsEngineProvider.kt`, `TtsMediaNotificationProvider.kt` | Control de sesión multimedia, integración con Android Auto, lockscreen y receptor AVRCP de auriculares Bluetooth. |
| **PDFBox / Pdfium** | Readium Adapter / Tom Roush (7 archivos) | `PdfReflowManager.kt`, `BookMetadataExtractor.kt`, `LibraryModule.kt`, `ReaderExtensions.kt`, `ReaderScreen.kt`, `ReaderScreenOverlays.kt`, `LectorApplication.kt` | Renderizado nativo de documentos PDF, cálculo de páginas virtuales y extracción semántica de texto para modo reflow. |
| **Coil3** | Multiplatform Image Loader (13 archivos) | `LibraryScreen.kt`, `BookDetailScreen.kt`, `ContinueReadingHero.kt`, `EpubCoverPage.kt`, `BookAudioExportDialog.kt`, `ReaderHeroSplash.kt`, `SearchScreen.kt`, etc. | Carga asíncrona y almacenamiento en caché de imágenes de portadas y avatares con soporte de animación cruzada. |
| **WorkManager** | AndroidX Work (6 archivos) | `MainActivity.kt`, `LectorApplication.kt`, `GraphExtractionWorker.kt`, `WebNovelImportWorker.kt`, `LibrarySyncWorker.kt`, `GraphViewModel.kt` | Orquestación persistente de tareas pesadas en segundo plano tolerantes a cierre de app. |
| **DataStore** | AndroidX Proto/Preferences (3 archivos) | `UserPreferencesSerializer.kt`, `UserPreferencesRepository.kt`, `DataStoreModule.kt` | Persistencia reactiva tipada de configuración de usuario (velocidad TTS, tema, tipografía, motor de audio, soundscapes). |

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
- **Hallazgos:** 10 reglas de mantenimiento vigentes.

### `ARCHITECTURE.md`
- **Propósito:** Mapa de arquitectura con tablas Responsabilidad → Archivo.
- **Clases:** —
- **Depende_de:** —
- **Usado_por:** Asistentes de IA
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** Ninguno.

### Índice histórico archivado
- **Propósito:** Copia histórica del índice de archivos, archivada; consultar este `dona.md` para el inventario actual.
- **Clases:** —
- **Depende_de:** —
- **Usado_por:** Asistentes de IA
- **Estado:** ✅ REVISADO_ATOMICAMENTE
- **Hallazgos:** Ninguno.

### `dona.md` (este archivo)
- **Propósito:** Índice de archivos fuente y documento ancla técnico del proyecto.
- **Clases:** —
- **Depende_de:** —
- **Usado_por:** Asistentes de IA y desarrolladores
- **Estado:** ✅ Índice sincronizado con el árbol verificado el 29/10/2026 mediante `tools/check_dona.ps1`.
- **Hallazgos:** Mantener actualizado con cada cambio de archivos fuente.

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
- **Propósito:** Persistencia atómica y resiliente del progreso de lectura (`Locator`), cálculo de sesiones y estadísticas en Room, debouncing de 800 ms (10.000 ms para TTS) y flush inmediato (`flushReadingProgressImmediately`).
- **Clases:** `SaveReadingProgressUseCase` (@Inject constructor)
- **Depende_de:** BookRepository, ReadingSessionManager, TimeProvider, Locator
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
- **Propósito:** Apertura ultrarrápida O(1) de publicaciones EPUB y PDF, bypass sintético de posiciones para novelas de miles de capítulos (`readingOrder.size > 80`), aplanado de TOC con detección de capítulos en novelas PDF sin índice (`PdfParagraphHealer`), y resolución resiliente de checkpoint con fallback de SharedPreferences / Room.
- **Clases:** `LoadBookFastUseCase` (@Inject constructor), `LoadBookResult`
- **Depende_de:** BookRepository, PublicationOpener, AssetRetriever, Context, Publication, Locator, PdfParagraphHealer
- **Usado_por:** ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 310 líneas)
- **Hallazgos:** Integra generación de índice de capítulos virtual en memoria para novelas PDF que carecen de tabla de contenidos original. Elimina riesgo de ANR y OOM en libros masivos en dispositivos gama media/baja.

#### `domain/reader/ManageReaderAnnotationsUseCase.kt`
- **Propósito:** Gestión integral de anotaciones de lectura: subrayados (highlights) con colores/notas, marcadores manuales y automáticos con snippet, matching de página/progreso (`isPageBookmarked`, `toggleBookmark`), transformación a objetos `Decoration` de Readium y exportación de notas/citas a Markdown y texto plano.
- **Clases:** `ManageReaderAnnotationsUseCase` (@Inject constructor)
- **Depende_de:** BookRepository, BookmarkEntity, HighlightEntity, HighlightColor, HighlightExporter, Decoration, Locator
- **Usado_por:** ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 268 líneas)
- **Hallazgos:** Ninguno.

#### `domain/reader/ReaderIntelligenceAndExportCoordinator.kt`
- **Propósito:** Coordinador de funciones avanzadas del lector: búsqueda en-libro streaming sin bloqueo (`FastEpubSearchEngine`) para EPUB y PDF, análisis semántico y reporte contextual Rayos-X (`BookXRayEngine`), y orquestación de exportación de audio .M4A individual o por lotes (`ChapterAudioExporter`) para capítulos EPUB y rangos de páginas PDF.
- **Clases:** `ReaderIntelligenceAndExportCoordinator` (@Singleton / @Inject constructor)
- **Depende_de:** FastEpubSearchEngine, BookXRayEngine, ChapterAudioExporter, BookEntity, ReflowItem, Publication
- **Usado_por:** ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 298 líneas)
- **Hallazgos:** Integra `exportPdfChapters` con extracción limpia por páginas y títulos de capítulos virtuales para exportar novelas PDF completas a audiolibro con metadatos.

#### `domain/reader/ReaderChapterNavigationCoordinator.kt`
- **Propósito:** Coordinador de navegación, caché LRU y precarga asíncrona de capítulos del lector. Desacopla la navegación manual (`goToNextChapter`, `goToPreviousChapter`), el avance automático de TTS (`advanceTtsToNextChapter`), la caché en memoria de documentos procesados (`chapterLruCache`) y la resolución de recursos/spines (`findMatchingResourceIndex`) y páginas PDF por TOC.
- **Clases:** `ReaderChapterNavigationCoordinator` (@Singleton / @Inject constructor), `ParsedChapterData` (data class), `ChapterDirection` (enum: NEXT, PREVIOUS)
- **Depende_de:** EpubFormatManager, Context, Navigator, VisualNavigator, Publication, Link, Locator, ProcessedDocument
- **Usado_por:** ReaderViewModel
- **Estado:** 🟡 Validar en tlf. (completo, 444 líneas: pendiente de validación en hardware del avance de capítulo manual y automático con TTS)
- **Hallazgos:** M28 resuelto en código. Centraliza toda la lógica de salto y precarga de capítulos para EPUB y PDF.

#### `domain/reader/ReaderVoiceAndAmbienceController.kt`
- **Propósito:** Controlador y coordinador de dominio para voces de síntesis (Sherpa ONNX offline y Edge Neural online), reglas fonéticas de pronunciación (`PronunciationLexiconManager` + `TtsPresetCatalog`), soundscapes de ambiente (`ForestAmbienceEngine` + `AmbientSoundscapePlayer`) y temporizador de apagado progresivo (`SleepTimer`). Desacopla la lógica de audio y ambiente de `ReaderViewModel`.
- **Clases:** `ReaderVoiceAndAmbienceController` (@Singleton / @Inject constructor)
- **Depende_de:** SleepTimer, SherpaModelManager, ForestAmbienceEngine, AmbientSoundscapePlayer, EdgeNeuralTtsClient, PronunciationLexiconManager, TtsPresetCatalog, UserPreferencesRepository, TtsManager
- **Usado_por:** ReaderViewModel
- **Estado:** 🟡 Validar en tlf. (completo, 255 líneas, 8 tests unitarios en verde: pendiente validación en TECNO-LH7n del cambio de voz Sherpa, descarga de modelo, activación de ambiente y sleep timer)
- **Hallazgos:** C3 mitigado parcialmente: encapsula 7 dependencias de audio/ambiente (`SleepTimer`, `SherpaModelManager`, `ForestAmbienceEngine`, `AmbientSoundscapePlayer`, `EdgeNeuralTtsClient`, `PronunciationLexiconManager`, `TtsPresetCatalog`) y gestión de diálogos en un controlador desacoplado, reduciendo la firma del constructor de `ReaderViewModel` a 21 parámetros totales (2 de framework + 19 dependencias inyectadas).

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

#### `core/pdf/PdfParagraphHealer.kt`
- **Propósito:** Sanador heurístico de renglones cortados (line unwrapping) y detector de capítulos en novelas PDF. Unifica líneas fragmentadas respetando puntuación de diálogo (guiones largos, comillas), puntos suspensivos y sangrías, y detecta títulos de capítulos sintéticos para documentos que carecen de TOC.
- **Clases:** `PdfParagraphHealer` (object), `DetectedChapter` (data class)
- **Depende_de:** Regex, Readium Link, Readium Url
- **Usado_por:** LoadBookFastUseCase, PdfCleanExtractorAndConverter
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 142 líneas)
- **Hallazgos:** Permite lectura continua sin cortes abruptos y autogeneración de índice navegable para web novels empaquetadas en PDF.

#### `core/pdf/PdfCleanExtractorAndConverter.kt`
- **Propósito:** Convertidor de novelas PDF a EPUB 3 con filtro anti-publicidad de 3 capas y conservación de ilustraciones artísticas. Capa 1: Deduplicación SHA-256 de imágenes (bloquea logos/banners repetidos >= 2 veces en el libro, salvo portada). Capa 2: Filtro de proporción y micro-iconos (<140px o aspect ratio achatado). Capa 3: Sanitización de encabezados/pies de página recurrentes (>=3 páginas) y regex de spam/redes (Discord, Patreon, Telegram). Ensambla EPUB estructurado usando LocalEpubBuilder.
- **Clases:** `PdfCleanExtractorAndConverter` (object), `PdfConversionProgress` (data class)
- **Depende_de:** PDFBox-Android (PDDocument, PDImageXObject, PDFTextStripper), LocalEpubBuilder, MessageDigest, Dispatchers.IO
- **Usado_por:** LibraryViewModel, PdfReflowManager
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 411 líneas)
- **Hallazgos:** Procesa libros masivos (350+ capítulos) en streaming conservando portada e ilustraciones legítimas en `OEBPS/images/` y generando XHTML limpio.

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
  - **C11:** Mojibake en comentarios (líneas 309, 548, 613, 627, 697, 711, 720, 779).
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
  - **C5 [Falso positivo / No aplica]:** Reportado originalmente como `debug = true` hardcodeado en producción; verificado en L437 que pasa `debug = com.example.woodsreader.BuildConfig.DEBUG`. En release evalúa a `false`, anulando los logs nativos en producción.
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
- **Hallazgos:** C11: mojibake en Regex de caracteres alargados (L30, `elongatedCharsRegex`), marcado como código con riesgo funcional en evaluación de texto para normalización TTS. Intocable según Adenda P2 hasta fase posterior.

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
- **Propósito:** Temporizador de sueño unificado (interfaz SleepTimer + implementación con rampa de velocidad, soporte de fin de capítulo y minutos arbitrarios).
- **Clases:** `SleepTimer` (interface), `SleepTimerManager` (@Singleton)
- **Depende_de:** TimeProvider, kotlinx.coroutines
- **Usado_por:** ReaderViewModel
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 94 líneas)
- **Hallazgos:** M25 resuelto en código. Actúa como autoridad única para cuentas regresivas continuas y fin de capítulo (-1), asegurando restauración de velocidad y parada de ambientes sonoros.

#### `core/managers/PdfReflowManager.kt`
- **Propósito:** Gestor de extracción semántica y reflujo dinámico de documentos PDF. Transforma páginas PDF fijas en bloques continuos `ReflowItem` (párrafos, títulos, listas, imágenes), deduplica encabezados y pies de página mediante `NoiseClassifier` y genera `NarrationUnit` para TTS sincronizado.
- **Clases:** `PdfReflowManager` (@Singleton), `PdfPageExtractionState`
- **Depende_de:** TtsManager, NoiseClassifier, TextAnalyzer, SpanishTextFormatter, Readium, PdfCleanExtractorAndConverter, Dispatchers.Default
- **Usado_por:** ReaderViewModel, ReaderReflowOverlay
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 795 líneas)
- **Hallazgos:** Integra filtro anti-spam en tiempo real (`isSpamTextLine`) para bloques visuales y unidades de narración TTS, además de filtrado por ratio de aspecto y descarte de micro-iconos en imágenes extraídas. La extracción de bloques semánticos procesa en ventanas alrededor de la página actual para conservar memoria en dispositivos con bajo límite de heap.

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
- **Propósito:** Motor de búsqueda en-libro streaming y no bloqueante para publicaciones EPUB y PDF. Emite resultados en tiempo real mediante Kotlin Flow, filtra texto HTML sin instanciar DOM completo, soporta extracción página a página para PDF, cancelación cooperativa por corrutinas y mantiene una caché LRU de capítulos limpios.
- **Clases:** `FastEpubSearchEngine` (object), `FastInBookSearchHit`, `FastSearchProgress`
- **Depende_de:** Readium Publication, Link, LruCache, Dispatchers.IO, Jsoup
- **Usado_por:** ReaderIntelligenceAndExportCoordinator, LectorApplication (onTrimMemory)
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 332 líneas)
- **Hallazgos:** La caché LRU almacena texto limpio por href (~4-8 MB max) y se limpia ordenadamente ante advertencias de memoria del sistema. Habilitada búsqueda streaming para documentos PDF con extracción de snippets y títulos de página.

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
- **Propósito:** Constructor local de EPUB 3 (ZIP con mimetype, container.xml, content.opf, toc.ncx, capítulos XHTML). Soporta carátula oficial (`cover.jpg`) e incrustación de ilustraciones de historia en `OEBPS/images/` con centrado CSS responsivo.
- **Clases:** `LocalEpubBuilder`, `ScrapedChapterMeta`
- **Depende_de:** java.util.zip, UUID
- **Usado_por:** WebToEpubImporterDialog, CalibreStyleEpubTranslator, PdfCleanExtractorAndConverter
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 281 líneas)
- **Hallazgos:** Permite empaquetar portadas e ilustraciones reales conservando jerarquía semántica.

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
  - **C11:** Mojibake residual en comentarios descriptivos y KDoc (L100, L180, L545, L566, L639).
  - **M6:** `deepEnrich` usa `HashSet` en memoria (se pierde al reiniciar).
  - **M7:** `cleanupOrphanedBooks` asume `content://` como accesible sin verificar.

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
- **Propósito:** ViewModel de biblioteca (filtrado reactivo de libros, búsqueda con debounce, colecciones, traducción masiva estilo Calibre, exportación a audiolibro M4A, conversión completa de novelas PDF a EPUB con auto-importación).
- **Clases:** `LibraryViewModel` (@HiltViewModel), `LibraryUiState`
- **Depende_de:** BookRepository, UserPreferencesRepository, BookScannerService, SherpaModelManager, ProLicenseManager, CalibreStyleEpubTranslator, ChapterAudioExporter, PdfCleanExtractorAndConverter
- **Usado_por:** LibraryScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 557 líneas)
- **Hallazgos:** Proporciona `convertPdfToCleanEpub` orquestado en segundo plano con emisión de progreso `PdfConversionProgress`, cancelación e importación automática a Room.

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
- **Propósito:** Bottom sheet de opciones de libro (metadatos, estado de lectura, colección, traducción masiva EPUB, exportación M4A para EPUB/PDF, conversión completa PDF a EPUB sin publicidad, eliminación).
- **Clases:** `BookOptionsBottomSheet` (@Composable), `MetadataRow` (@Composable)
- **Depende_de:** Compose, Coil3, BookEntity, CollectionEntity, ReadingStatus, File, Locale
- **Usado_por:** LibraryScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 470 líneas)
- **Hallazgos:** Incluye acción destacada Emerald Liquid Glass "⚡ Convertir PDF a EPUB Completo (Sin Publicidad)" para formato PDF y botón de exportación M4A tanto para EPUB como PDF.

#### `ui/library/PdfConversionProgressDialog.kt`
- **Propósito:** Diálogo modal flotante (Emerald Liquid Glass) para la conversión limpia de novelas PDF a EPUB. Presenta barra de progreso, porcentaje en tiempo real, tarjetas de estadísticas en vivo (capítulos creados, ilustraciones guardadas, banners/spam bloqueados), mensaje de fase y dock minimizable inferior a 60 FPS.
- **Clases:** `PdfConversionProgressDialog` (@Composable), `StatCard` (@Composable)
- **Depende_de:** Compose Material3, PdfConversionProgress, HiddenForest
- **Usado_por:** LibraryScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 321 líneas)
- **Hallazgos:** Totalmente responsivo; soporta cancelación segura en segundo plano e importación automática del EPUB resultante a la biblioteca.

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
- **Hallazgos:** **C12 [RESUELTO]:** Blindado en commit `7ac5385`: `savedStateHandle.get<String>("bookId").orEmpty()`. Si viene vacío expone `BookDetailUiState(error = "...")` en vez de crashear.

#### `ui/settings/SettingsScreen.kt`
- **Propósito:** Pantalla general de configuración y control global de Woods Reader. Gestiona la suscripción y licencias PRO (`ProLicenseManager`), escaneo/enriquecimiento de la biblioteca local, configuración avanzada del motor de narración TTS (selección entre Sherpa ONNX VITS y TTS del sistema, catálogo multilingüe de 12 voces offline, auto-detección de idioma por capítulo, glosario fonético Xuanhuan/LitRPG y reglas personalizadas), preferencias de lectura y notificaciones, blindaje de ejecución en segundo plano con pantalla apagada (`WoodsKeepAliveManager`), exportación e importación de copias de seguridad portátiles (`WoodsBackup Vault`), métricas de almacenamiento (Room DB, caché Coil, modelos Sherpa) con limpieza selectiva, y ficha técnica del sistema con copiado al portapapeles.
- **Clases y Composables:**
  - `SettingsScreen` (público, L49–1361): Composable principal que orquesta todas las tarjetas de configuración dentro de un `LazyColumn`. Gestiona ciclo de vida (`DisposableEffect` para refrescar estado de optimización de batería en `ON_RESUME`), cálculo asíncrono de almacenamiento (`LaunchedEffect(Unit)`), lanzadores SAF (`rememberLauncherForActivityResult` con `ActivityResultContracts.CreateDocument` y `ActivityResultContracts.GetContent`), y diálogos modales (`AlertDialog` para Sherpa, `AddPhoneticRuleDialog`, `WoodsProUnlockDialog`).
  - `SettingsSection` (público, L1364–1383): Contenedor estilizado con borde verde bosque y título para agrupar bloques funcionales.
  - `SettingsToggleRow` (público, L1385–1418): Fila de ajuste booleano con icono estilizado, título, descripción y componente `Switch`.
  - `SettingsActionRow` (público, L1420–1445): Fila interactiva para acciones directas (navegación, diálogos, reseteo) con chevron indicador.
  - `SettingsSliderRow` (público, L1447–1477): Fila de control continuo con `Slider` y visualización formateada del valor en tiempo real (velocidad y tono de voz).
  - `StorageItemRow` (público, L1479–1489): Fila de dos columnas para reporte de peso de almacenamiento de datos y cachés.
  - `SettingsIcon` (privado, L1491–1503): Contenedor visual cuadrado con esquinas redondeadas para iconos temáticos.
  - `formatBytes` (público, L1505–1510): Función utilitaria pura para formateo legible de magnitudes en bytes (B, KB, MB, GB).
  - `BuildSpecRow` (privado, L1512–1543): Fila de dos columnas con alineación justificada para ítems de la ficha técnica.
- **Mapeo de Ajustes, Claves DataStore y Consumidores:**
  | Ajuste en UI | Clave / Almacenamiento DataStore | Tipo | Componentes Consumidores |
  |---|---|---|---|
  | Modo de Motor TTS (`currentEngineMode`) | `UserPreferences.useSherpaTts` (`DataStore<UserPreferences>`) | `Boolean` | `TtsManager`, `ReaderViewModel`, `ReaderScreen`, `ReaderTtsMacroWindow`, `ReaderTtsVoiceTab` |
  | Perfil Acústico DSP (`acousticsProfile`) | `acoustics_profile` (`DataStore` en `PronunciationLexiconManager`) | `StudioAcousticsProfile` | `SherpaTtsEngine`, `MultilingualAudiobookNormalizer`, `ReaderTtsAmbienceTab` |
  | Prosodia Expresiva | `expressive_prosody_enabled` (`PronunciationLexiconManager`) | `Boolean` | `MultilingualAudiobookNormalizer`, `SherpaTtsEngine`, `ReaderTtsAmbienceTab` |
  | Modo Novela Dramatizada | `dramatized_dialogue_enabled` (`PronunciationLexiconManager`) | `Boolean` | `MultilingualAudiobookNormalizer`, `SherpaTtsEngine`, `ReaderTtsAmbienceTab` |
  | Auto-detectar Idioma del Libro | `auto_detect_book_language` (`SherpaModelManager`) | `Boolean` | `TtsManager`, `ReaderVoiceAndAmbienceController`, `ReaderTtsVoiceTab` |
  | Filtro de Idioma de Voces | `selected_language_filter` (`SherpaModelManager`) | `SupportedTtsLanguage` | `SettingsScreen`, `ReaderTtsVoiceTab` |
  | Voz Sherpa Activa | `selected_voice` (`SherpaModelManager`) | `SherpaVoiceOption` | `SherpaTtsEngine`, `TtsManager`, `ReaderTtsVoiceTab` |
  | Velocidad de Voz por Defecto | `UserPreferences.ttsSpeed` (`DataStore<UserPreferences>`) | `Float` (0.5f–3.0f) | `TtsManager`, `ReaderViewModel`, `ReaderTtsActiveControls` |
  | Tono de Voz por Defecto | `UserPreferences.ttsPitch` (`DataStore<UserPreferences>`) | `Float` (0.5f–2.0f) | `TtsManager`, `ReaderViewModel`, `ReaderTtsAmbienceTab` |
  | Glosario Novelas Web (Xuanhuan) | `builtin_lexicon_enabled` (`PronunciationLexiconManager`) | `Boolean` | `MultilingualAudiobookNormalizer`, `SherpaTtsEngine`, `ReaderTtsAmbienceTab` |
  | Reglas Fonéticas Personalizadas | `custom_phonetic_rules` (JSON en `PronunciationLexiconManager`) | `List<CustomPhoneticRule>` | `MultilingualAudiobookNormalizer`, `SherpaTtsEngine`, `ReaderTtsAmbienceTab` |
  | Notificaciones de lectura | `UserPreferences.notificationsEnabled` (`DataStore<UserPreferences>`) | `Boolean` | `TtsNotificationManager`, `TtsManager` |
  | TTS automático al abrir | `UserPreferences.ttsAutoStart` (`DataStore<UserPreferences>`) | `Boolean` | `ReaderViewModel`, `ReaderScreen` |
  | Estadísticas de lectura | `UserPreferences.analyticsEnabled` (`DataStore<UserPreferences>`) | `Boolean` | `ReadingSessionManager`, `CalculateReadingStatsUseCase`, `ReadingStatsViewModel` |
  | Restablecer Bienvenida | `UserPreferences.hasCompletedOnboarding` (`DataStore<UserPreferences>`) | `Boolean` (fija `false`) | `MainActivity`, `NavGraph`, `OnboardingViewModel` |
- **Contrato con ViewModel:**
  - *StateFlows y estados observados:* `preferences`, `sherpaDownloadState`, `showSherpaDownloadDialog`, `storageSizes`, `backupStatusMessage`, `selectedLanguageFilter`, `selectedSherpaVoice`, `isScanning`, `scanProgress`, `isEnriching`, `autoDetectBookLanguage`, `builtInLexiconEnabled`, `customPhoneticRules`, `acousticsProfile`, `expressiveProsodyEnabled`, `dramatizedDialogueEnabled`.
  - *Propiedades y gestores accedidos:* `proLicenseManager` (observa `isProUnlocked`), `woodsKeepAliveManager` (observa `isUnrestrictedBattery`, invoca `refreshBatteryStatus()`, `requestIgnoreBatteryOptimizations(context)`).
  - *Métodos invocados:* `setTtsEngineMode(mode)`, `downloadSherpaModel()`, `dismissSherpaDownloadDialog()`, `downloadSherpaVoice(voice)`, `selectAndActivateSherpaVoice(voice)`, `deleteSherpaVoice(voice)`, `isVoiceDownloaded(voice)`, `getVoiceSizeBytes(voice)`, `setSelectedLanguageFilter(lang)`, `setAutoDetectBookLanguage(bool)`, `setTtsSpeed(speed)`, `setTtsPitch(pitch)`, `setAcousticsProfile(prof)`, `setExpressiveProsodyEnabled(bool)`, `setDramatizedDialogueEnabled(bool)`, `setBuiltInLexiconEnabled(bool)`, `addCustomPhoneticRule(orig, phon)`, `removeCustomPhoneticRule(id)`, `setNotificationsEnabled(bool)`, `setTtsAutoStart(bool)`, `setAnalyticsEnabled(bool)`, `resetOnboarding()`, `startScan()`, `createBackupFile()`, `exportFullBackup(uri)`, `restoreBackup(uri, callback)`, `clearBackupStatusMessage()`, `deleteSherpaModel(context)`, `clearCoverCache(context)`, `calculateStorageSizes(context)`.
- **Navegación y Efectos:**
  - `DisposableEffect(lifecycleOwner)` (L76–86): Observa `Lifecycle.Event.ON_RESUME` para invocar `viewModel.woodsKeepAliveManager.refreshBatteryStatus()`.
  - `LaunchedEffect(Unit)` (L119–121): Dispara `viewModel.calculateStorageSizes(context)` al montar la pantalla.
  - `rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument)` (L90–94): Crea documento SAF JSON y delega en `viewModel.exportFullBackup(uri)`.
  - `rememberLauncherForActivityResult(ActivityResultContracts.GetContent)` (L96–116): Selecciona archivo SAF JSON y delega en `viewModel.restoreBackup(uri) { ... }` con feedback visual vía `Toast`.
  - `Intent(ACTION_SEND)` (L1049–1058): Despacha el archivo de respaldo generado por `createBackupFile()` a través del selector de apps del sistema (`Intent.createChooser`).
- **Depende_de:** Compose UI, Material 3, `SettingsViewModel`, `UserPreferences`, `UserPreferencesRepository`, `ActivityResultContracts`, `ProLicenseManager`, `WoodsKeepAliveManager`, `WoodsProUnlockDialog`, `AddPhoneticRuleDialog`, `SherpaVoiceOption`, `SupportedTtsLanguage`, `StudioAcousticsProfile`, `TtsEngineMode`, `SherpaDownloadState`, `StorageSizes`, `HiddenForest`.
- **Usado_por:** `NavGraph.kt`.
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 1.544 líneas)
- **Hallazgos:**
  - **M23:** `technicalSheetText` (L1171–1184) contiene cadenas fijas de versión ("v1.0.0-ALPHA", "Build 2026.10") en lugar de obtenerlas dinámicamente de `BuildConfig.VERSION_NAME` y `BuildConfig.VERSION_CODE`.
  - **M24:** Acceso directo a propiedades de gestores en `viewModel.woodsKeepAliveManager` (L71, L79, L940) y `viewModel.proLicenseManager` (L53, L1356) exponiendo referencias de dominio internas a la vista Compose en lugar de encapsularlas mediante métodos del ViewModel.

#### `ui/settings/SettingsViewModel.kt`
- **Propósito:** ViewModel de ajustes. Backup/Restore, TTS engine mode, Sherpa voices, almacenamiento, preferencias.
- **Clases:** `SettingsViewModel` (@HiltViewModel), `StorageSizes`, `TtsEngineMode`
- **Depende_de:** UserPreferencesRepository, BookScannerService, BookDao, BackupManager, WoodsBackupManager, TtsManager, SherpaModelManager, EdgeNeuralTtsClient, ProLicenseManager, PronunciationLexiconManager, WoodsKeepAliveManager
- **Usado_por:** SettingsScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 353 líneas)
- **Hallazgos:**
  - **A10/A11 [RESUELTO]:** Métodos heredados de exportación/importación en texto plano (`exportLibrary`, `importLibrary`, `restoreFullBackup`, `LibraryBackup`) eliminados. La app utiliza exclusivamente `WoodsBackupManager` con vault comprimido y transacciones seguras.

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
- **Propósito:** Pantalla principal del lector de libros. Orquestador monolítico y superficie de interacción Readium WebView / Pdfium, gestos de navegación táctil (toques, deslizamientos, `ChapterScrollDetector`), modos de lectura (Paginado, Scroll vertical continuo, Modo Reflow para PDF/EPUB), barra de progreso, teleprompter WPM, gestión de notas/subrayados/entidades de grafo, integración con menús flotantes, bottom sheets (`ReaderTtsMacroWindow`, `ReaderXRaySheet`, `FootnoteSheet`, `ReaderSideMenu`), y estado visual de carga y error (`ReaderUiState.Error`).
- **Clases / Composables:**
  - `ReaderScreen` (público, L168–1855): Composable raíz del lector. Gestiona ciclo de vida (`DisposableEffect` con guardado inmediato en `ON_PAUSE`), `BackHandler`, `HazeState`, eventos de `VisualNavigator` (Readium), integración con `ChapterScrollDetector`, overlays de TTS (`ReaderTtsMacroWindow`), paneles laterales, hojas modales y teleprompter.
  - `resolveTocTargetPage` (privado, L139–163): Resuelve la página de destino numérica en la tabla de contenidos para formatos PDF y EPUB analizando `locations.position`, fragmentos CFI o enlaces de anclaje `#page=`.
- **Contrato con ViewModel:**
  - *StateFlows y estados observados:* `uiState` (`ReaderUiState`), `showControls` (`Boolean`), `isReflowMode` (`Boolean`), `userPreferences` (`UserPreferences`), `highlights` (`List<Highlight>`), `bookmarks` (`List<Bookmark>`), `isCurrentPageBookmarked` (`Boolean`), `ttsState` (`TtsState`), `isLoadingTtsModel` (`Boolean`), `showSherpaDownloadDialog` (`Boolean`), `isAutoScrollActive` (`Boolean`), `autoScrollWpm` (`Int`), `isAutoScrollManuallyPaused` (`Boolean`), `currentTtsLocator` (`Locator?`), `currentTtsSentence` (`String`), `currentTtsPage` (`Int`), `ttsRules` (`List<TtsRule>`), `chapterInfo` (`ChapterInfo`), `isRtl` (`Boolean`), `reflowState` (`PdfReflowState`), `isLoadingMoreReflow` (`Boolean`), `ttsActiveBlockIndex` (`Int`), `currentSpeed` (`Float`), `currentPitch` (`Float`), `bookTimeRemaining` (`Long`), `chapterTimeRemaining` (`Long`), `lastTtsLocator` (`Locator?`), `sleepTimerRemaining` (`Long`), `sherpaDownloadState` (`SherpaDownloadState`), `selectedSherpaVoice` (`SherpaVoiceOption?`), `audioExportState` (`AudioExportState`), `useEdgeNeuralTts` (`Boolean`), `selectedEdgeVoice` (`String`), `availableVoices` (`List<String>`), `globalProgress` (`Float`), `currentTtsRange` (`TtsTextRange`).
  - *Métodos invocados:*
    - Navegación / Lectura: `onLocatorChanged(loc)`, `flushReadingProgressImmediately()`, `saveCurrentProgressBeforeExit()`, `goToLocator(nav, loc)`, `goToNextChapter(nav)`, `goToPreviousChapter(nav)`, `pageForward(nav)`, `pageBackward(nav)`, `getLocatorForPdfPage(page)`, `seekToGlobalProgress(progress, nav)`, `getCurrentChapterIndex()`, `jumpToReflowPage(page)`, `onReflowScroll(index, isScrolling)`, `loadNextPage()`, `loadPreviousPage()`.
    - Controles de UI: `hideControls()`, `toggleControls()`, `onUserInteraction()`.
    - Teleprompter / Auto-scroll: `setAutoScrollManuallyPaused(bool)`, `toggleAutoScroll()`, `toggleAutoScrollPause()`, `setAutoScrollWpm(wpm)`.
    - Resaltados y Marcadores: `addHighlight(loc, color, text, note)`, `deleteHighlight(id)`, `addBookmark(loc)`, `deleteBookmark(id)`.
    - Reflow y X-Ray: `toggleReflowMode()`, `generateXRayReport()`.
    - TTS Básico y Transporte: `stopTts()`, `pauseTts()`, `resumeTts()`, `onPlay()`, `playTts(loc)`, `playTtsFromLast()`, `skipTtsSentenceForward()`, `skipTtsSentenceBackward()`, `skipTtsWordForward()`, `skipTtsWordBackward()`, `skipTtsChapterForward()`, `skipTtsChapterBackward()`, `skipReflowTtsForward()`, `skipReflowTtsBackward()`, `ttsSpeedUp()`, `ttsSpeedDown()`, `onSpeedChange(speed)`, `onPitchChange(pitch)`, `onLanguageChange(lang)`, `updateTtsVoiceName(name)`, `onTtsAutoScrollChange(bool)`, `updateTtsUiStyle(style)`, `updateTtsHighlightStyle(style)`, `speakSnippet(text)`, `addWordExclusionRule(pattern, isExact, applyToAll)`.
    - Audio Macro / Sherpa / Edge / SleepTimer: `startSleepTimer(mins)`, `cancelSleepTimer()`, `toggleNextAmbience()`, `downloadSherpaModel()`, `dismissSherpaDownloadDialog()`, `selectOrDownloadSherpaVoice(voice)`, `isSherpaVoiceDownloaded(voice)`, `deleteSherpaVoiceFromReader(voice)`, `updateUseSherpaTts(bool)`, `setUseEdgeNeuralTts(bool)`, `selectEdgeVoice(voice)`, `exportCurrentChapterToAudio()`, `exportAudioChapters(count)`, `cancelAudioExport()`, `resetAudioExportState()`, `saveTtsRule(rule)`, `deleteTtsRule(id)`, `installTtsPresetPack(pack)`, `installTtsPresetPackWithResult(pack)`.
    - Preferencias Visuales: `onThemeChange(theme)`, `onFontSizeChange(sp)`, `onLineSpacingChange(mul)`, `onMarginChange(margin)`, `onTextAlignChange(align)`, `onFontFamilyChange(font)`, `updateHyphenation(bool)`, `updateVolumeKeysNavigation(bool)`, `onScrollModeChange(mode)`, `onPageTurnAnimationChange(anim)`, `onPageTurnStyleChange(style)`, `onInvertedTapZonesChange(bool)`, `toggleScrollMode()`.
- **Navegación y Ciclo de Vida:**
  - `LaunchedEffect(showControls)` (L182–192): Controla las barras del sistema (status bar) con `WindowInsetsControllerCompat` y comportamiento `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`.
  - `DisposableEffect(view)` (L194–202): Restaura las barras del sistema al destruirse la vista.
  - `LaunchedEffect(targetLocatorJson, navigator)` (L239–247): Salto al texto al retornar desde la pantalla o bottom sheet de Grafo.
  - `LaunchedEffect(currentLocator)` (L252–256): Notifica cambio de ubicación actual a `viewModel.onLocatorChanged(loc)`.
  - `DisposableEffect(lifecycleOwner)` (L259–269): Registra observer en `Lifecycle.Event.ON_PAUSE` y `ON_STOP` para invocar `viewModel.flushReadingProgressImmediately()`.
  - `DisposableEffect(view)` (L281–288): Activa `keepScreenOn` mientras el TTS esté reproduciendo o la pantalla esté bloqueada.
  - `BackHandler` (L290–294): Guarda progreso con `viewModel.saveCurrentProgressBeforeExit()`, detiene TTS con `viewModel.stopTts()` y despacha `onNavigateUp()`.
  - `LaunchedEffect(isTtsActive)` (L326–331): Asigna `volumeControlStream = AudioManager.STREAM_MUSIC` en la actividad.
  - `LaunchedEffect(selectionVersion)` (L349–360): Temporizador estabilizador de 2200 ms tras selección de texto para desplegar `showHighlightMenu` sin interrumpir ajustes de cursor.
  - `DisposableEffect(fragmentManager)` (L515–551): Registra `FragmentLifecycleCallbacks` para enlazar `Navigator` de Readium con `viewModel.navigator`, y limpia fragmentos al desmontar.
  - `LaunchedEffect(isUserInteracting, showControls, ttsState)` (L554–561): Auto-ocultamiento de controles tras 5000 ms de inactividad si no está reproduciendo TTS.
  - `LaunchedEffect(navigatorState.value, epubPreferences)` (L583–599): Somete preferencias de visualización tipográfica a `EpubNavigatorFragment` y `PdfNavigatorFragment`.
  - `LaunchedEffect(navigatorState.value, highlights, highlightColors)` (L602–622): Proyecta decoraciones visuales de subrayados en Readium vía `applyDecorations`.
  - `LaunchedEffect(ttsState, ...)` (L628–730): Sincronización continua de resaltado TTS y seguimiento visual automático de lectura (auto-scroll suave y avance de página).
  - `LaunchedEffect(currentTtsPage, ...)` (L733–753): Sincronización de páginas para PDF original no reflow.
  - `LaunchedEffect(isAutoScrollActive, ...)` (L764–813): Corrutina a 60 FPS (16 ms) de teleprompter y desplazamiento automático con avance de capítulo al final.
  - `DisposableEffect(navigatorState.value, ...)` (L936–1028): Registra `InputListener` para toques en la publicación (toques laterales para avanzar/retroceder página y central para alternar controles) y adjunta `ChapterScrollDetector`.
  - `pointerInput` y `onKeyEvent` (L1038–1073): Captura eventos táctiles raw y eventos de teclas de volumen para navegación (`VolumeUp` / `VolumeDown`), delegándolos a audio cuando TTS está activo.



- **Depende_de:** Compose UI, Material 3, Haze (`HazeState`, `haze`, `hazeChild`), Readium Navigator 3.x (`VisualNavigator`, `SelectableNavigator`, `DecorableNavigator`, `EpubNavigatorFragment`, `PdfNavigatorFragment`), `ReaderViewModel`, `ReaderEgoGraphViewModel`, `ChapterScrollDetector`, `ReaderTopBar`, `ReaderBottomSlider`, `ReaderTeleprompterPill`, `ReaderSideMenu`, `ReaderHighlightOverlay`, `ReaderReflowOverlay`, `ReaderPdfTtsHud`, `ReaderControlBottomSheet`, `ReaderTtsMacroWindow`, `ReaderTtsControlsLayer`, `ReaderHeroSplash`, `PullToChapterIndicator`, `FootnoteSheet`, `TocBottomSheet`, `SearchBottomSheet`, `ReaderXRayBottomSheet`, `ReaderExternalLookupSheets`, `TtsRulesBottomSheet`, `ReaderTtsLockOverlay`, `ReaderEgoGraphBottomSheet`, `AddEntityFromSelectionDialog`, `InlineLookupManager`.
- **Usado_por:** `NavGraph.kt`.
- **Estado:** 🟡 REVISADO_ATOMICAMENTE (modularizado, 1.855 líneas - validar en TECNO-LH7n)
- **Hallazgos:**
  - **M18:** Inyección de JavaScript directo en L1166–1170 (`s.modify('move', 'backward', 'sentence')`) sobre instancias de WebView sin timeout de ejecución ni callback de error ante sintaxis no soportada en motores WebView desactualizados.
  - **M19:** Captura de excepciones vacía (`catch (_: Exception) {}`) en L1220–1222 al disparar el intent implícito `ACTION_VIEW` para abrir definiciones web externas, ocultando posibles `ActivityNotFoundException` si el dispositivo no tiene navegador instalado.
  - **C11:** Mojibake residual en comentarios descriptivos de líneas 553 y 570.

#### `ui/reader/PullToChapterIndicator.kt`
- **Propósito:** Indicador animado de sobre-desplazamiento vertical en los bordes inicial y final de capítulo ("Pull to Chapter"). Muestra una pastilla flotante con efecto glassmorphism (`HazeChild` / `GlassStyle`), icono direccional (`KeyboardArrowUp` / `KeyboardArrowDown`), porcentaje de estiramiento y etiqueta localizada (capítulo anterior o siguiente).
- **Clases / Composables:**
  - `PullToChapterIndicator` (@Composable, L27-88): Indicador visual de tracción táctil entre capítulos.
- **Depende_de:** Compose UI, Material 3, Haze (`HazeState`, `hazeChild`), `ReaderDesign`, `HiddenForest`.
- **Usado_por:** `ReaderScreen.kt`.
- **Estado:** 🟡 REVISADO_ATOMICAMENTE (completo, 88 líneas - validar en TECNO-LH7n)
- **Hallazgos:** Ninguno.

#### `ui/reader/FootnoteSheet.kt`
- **Propósito:** Hoja modal inferior (`ModalBottomSheet`) con estilo visual `ReaderDesign.GlassStyle` que despliega el contenido de notas al pie de página emergentes sin perder la posición de lectura cuando el usuario pulsa un enlace o referencia `[1]` en el EPUB.
- **Clases / Composables:**
  - `FootnoteSheet` (@Composable, L18-59): BottomSheet para lectura fluida de notas al pie.
- **Depende_de:** Compose UI, Material 3, Haze (`HazeState`, `hazeChild`), `ReaderDesign`, `HiddenForest`.
- **Usado_por:** `ReaderScreen.kt`.
- **Estado:** 🟡 REVISADO_ATOMICAMENTE (completo, 59 líneas - validar en TECNO-LH7n)
- **Hallazgos:** Ninguno.

#### `ui/reader/ReaderViewModel.kt`
- **Propósito:** Orquestador principal de UI del lector (~2.167 líneas). Coordina los flujos de lectura, TTS, panel de control, marcadores, navegación y exportación delegando la lógica a 7 casos de uso y coordinadores especializados.
- **Clases:** `ReaderViewModel` (@HiltViewModel), `AutoScrollTeleprompterState`, `ChapterInfo`, `ReaderViewState`
- **Depende_de:** SaveReadingProgressUseCase, CalculateReadingStatsUseCase, LoadBookFastUseCase, ManageReaderAnnotationsUseCase, ReaderIntelligenceAndExportCoordinator, ReaderChapterNavigationCoordinator, ReaderVoiceAndAmbienceController, BookRepository, UserPreferencesRepository, ReadingSessionManager, TtsManager, ProLicenseManager, WoodsKeepAliveManager, EpubFormatManager, PdfReflowManager, PdfBookViewSynchronizer, CoverExtractor
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 2.167 líneas, 135.5 KB)
- **Hallazgos:**
  - **C3 [MITIGADO EN CÓDIGO - PENDIENTE DISPOSITIVO]:** Descompresión de God Class en curso. Evolución de líneas: pasó de 2.905 líneas (original `4de4bd7^`) a 2.476 líneas (tras extracción de Casos de Uso) y actualmente a 2.158 líneas en HEAD (tras P5 y P6), con objetivo de diseño de ~600 líneas. Constructor verificado por script: el original contenía 24 parámetros totales (2 de framework: `Application context` y `SavedStateHandle savedStateHandle` del que sale `bookId` + 22 dependencias de dominio/servicios, y no 17 ni 27 como se había indicado erróneamente en notas previas); en HEAD se redujo a 21 parámetros totales (2 de framework + 19 dependencias inyectadas) tras extraer `ReaderVoiceAndAmbienceController` (255 líneas, P6) y `ReaderChapterNavigationCoordinator` (444 líneas, P5). Pendiente validación en `TECNO-LH7n` del cambio de voz Sherpa, descarga de modelo, activación de ambiente y sleep timer.
  - **A12 [RESUELTO]:** Doble persistencia de progreso unificada en `SaveReadingProgressUseCase`.
  - **C2 [RESUELTO]:** Blindado en commit `7ac5385`: `savedStateHandle.get<String>("bookId").orEmpty()`. Emite `ReaderUiState.Error` seguro si falta el parámetro.
  - **C8/C10 [RESUELTO EN COMENTARIOS]:** Saneados todos los caracteres corruptos (mojibake) en comentarios de `ui/reader/` (separadores de sección en `ReaderViewModel.kt` y notas en `ReaderScreen.kt`). Cero cadenas visibles al usuario alteradas.
  - **M3 [PENDIENTE]:** Locator CFI persistido como JSON plano propenso a desincronización si cambian esquemas de Readium.
  - **M25 [RESUELTO EN CÓDIGO - PENDIENTE DISPOSITIVO]:** Unificada la autoridad del temporizador de sueño en `SleepTimerManager` en L304, L582 y L2149. Se eliminó la doble corrutina y el fallback forzado a `MIN_15`, sincronizando fin de capítulo y parada de ambientes sonoros.
  - **M26 [RESUELTO EN CÓDIGO - PENDIENTE DISPOSITIVO]:** Estado 🟡 hasta la P4. Separado lo confirmado en código (el retorno anticipado `val nav = navigator ?: return` antes de evaluar la rama PDF en `goToNextChapter` y `goToPreviousChapter` impedía ejecutar navegación PDF si navigator era null) de lo no confirmado (que `navigator` sea efectivamente `null` en vista libro en runtime). Desacoplada la navegación PDF/Reflow vía `resolveNextPdfPageFromToc` / `resolvePreviousPdfPageFromToc` delegando en `ReaderChapterNavigationCoordinator`.
  - **M27 [PENDIENTE - GANCHO P13]:** `ReaderViewState` contiene campos de vista libro PDF (`isPdfBookViewActive`, `pdfBookViewBlockIndex`) y método `togglePdfBookView()` que constituyen el gancho de la P13 (alternancia libro/original PDF con `PdfBookViewSynchronizer`). Se conservan intactos hasta su integración con la UI.
  - **GANCHO P11 [PENDIENTE]:** `updateNarrationHighlight()` y `NarrationHighlight` son el punto de entrada para el resaltado palabra por palabra (P11). Hoy el resaltado opera a nivel de bloque/párrafo completo (`activeNarrationBlockId` en EPUB vía decoraciones Readium, y `activeNarrationUnitIndex` en PDF Reflow vía Emerald Liquid Glass en `ReaderReflowOverlay.kt`).
  - **M28 [RESUELTO]:** Unificada la lógica duplicada de cálculo de tiempo restante y progreso de capítulo entre `onTtsLocatorChanged` y `saveProgress` en la función privada `updateReadingStats(locator, activeWpm)`.
  - **M29 [RESUELTO]:** `readingProgressManager` y `quoteVideoClipExporter` eliminados del constructor, y `positionsJob` inerte purgado de `ReaderViewModel.kt`.

#### `ui/reader/ReaderContract.kt`
- **Propósito:** Definición de contratos y modelos de navegación para la interfaz del lector: pestañas del panel lateral (TOC, Marcadores, Subrayados, Notas, Configuración) y modelos inmutables de resaltado de texto para la narración de TTS.
- **Clases:** `ReaderTab` (enum), `NarrationHighlight` (data class), `ReaderState`
- **Depende_de:** Readium Locator
- **Usado_por:** ReaderViewModel, ReaderScreen, ReaderSideMenu, ReaderTopBar
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 42 líneas)
- **Hallazgos:**
  - Contrato estricto y desacoplado de Compose que previene dependencias cíclicas entre vistas del lector.
  - **M27 [PENDIENTE]:** `ReaderState` (L39–42) contiene únicamente `isPdfBookViewActive` y `pdfBookViewBlockIndex`, duplicando la definición de estado PDF de `ReaderViewState` en `ReaderViewModel.kt`.

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
- **Propósito:** Catálogo base de componentes visuales, tokens de diseño y tarjetas reutilizables del lector. Aloja los tokens de diseño glassmorphism (`ReaderDesign`) y componentes atómicos de alta frecuencia (`ReaderIconButton`, `ReaderSolidCard`, `ReaderCrystalCard`, `PremiumSlider`).
- **Clases y Composables:**
  - `ReaderDesign` (object, L94–110): Tokens de diseño estandarizados (`SurfaceColor = HiddenForest.SurfaceVariant`, `GlassStyle` con `HazeStyle`, blur 15.dp y tinte superficie 40%; `DeepGlassStyle` con blur 20.dp y fondo `#020302` al 92%).
  - `ReaderIconButton` (público, L113–144): Botón circular táctil de 14.dp con icono representativo, soporte para estado activo con acento esmeralda (`#10B981`) y borde degradado.
  - `ReaderSolidCard` (público, L148–160): Tarjeta con fondo sólido `ReaderDesign.SurfaceColor` y borde tenue para renderizado ligero sin efecto blur.
  - `ReaderCrystalCard` (público, L166–190): Contenedor con efecto de vidrio translúcido mediante `hazeChild(state = hazeState, style = ReaderDesign.GlassStyle)` y borde degradado blanco translúcido.
  - `PremiumSlider` (público, L193–226): Slider personalizado para velocidad de TTS, tamaño de tipografía y márgenes, con acentos en verde esmeralda y visualización formateada a 2 decimales.
- **Contrato con ViewModel:** Componentes atómicos de diseño y presentación pura; no consumen ni observan directamente ningún ViewModel ni StateFlow.
- **Navegación y Efectos:** Ninguno directo (vistas desacopladas sin efectos de ciclo de vida ni navegación).
- **Depende_de:** Compose UI, Material 3, Haze (`HazeState`, `hazeChild`, `HazeStyle`, `HazeTint`), `HiddenForest`.
- **Usado_por:** `ReaderScreen.kt`, `ReaderScreenOverlays.kt`, `ReaderBottomControls.kt`, `ReaderTtsMacroWindow.kt`, `ReaderTtsAmbienceTab.kt`, `ReaderTtsActiveControls.kt`.
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 226 líneas)
- **Hallazgos:** Ninguno.

#### `ui/reader/HighlightColorMenu.kt`
- **Propósito:** Barra de herramientas flotante (`Popup`) invocada ante la selección de texto en el lector. Controla la paleta de colores de subrayado (`HighlightColor`), ampliación de selección a oración completa (`FormatQuote`), registro de personajes/entidades en grafo narrativo (`SaveCharacterDialog`), botones de acción rápida (Copiar, Compartir cita, Narrar TTS, Traducir, Diccionario, Buscar, Ignorar en TTS) y tarjeta expandible con resultados de consulta léxica offline (`LookupResult`).
- **Clases y Composables:**
  - `HighlightColorMenu` (público, L43–702): Barra flotante contextual y tarjeta léxica expandible.
  - `ActionIconCircle` (privado, L705–726): Botón circular táctil con icono representativo para las acciones secundarias.
- **Depende_de:** Compose UI, Material 3, Haze (`HazeState`, `hazeChild`), `HighlightColor`, `LookupResult`, `SaveCharacterDialog`, `HiddenForest`.
- **Usado_por:** `ReaderScreen.kt`, `ReaderScreenOverlays.kt`.
- **Estado:** 🟡 REVISADO_ATOMICAMENTE (completo, 726 líneas - validar en TECNO-LH7n)
- **Hallazgos:**
  - **M20:** Captura de excepciones vacía (`catch (_: Exception) {}`) en el fallback del navegador web para traducciones/definiciones cuando `onErrorOpenBrowser == null`.

#### `ui/reader/SaveCharacterDialog.kt`
- **Propósito:** Diálogo modal (`Dialog`) para capturar y clasificar nombres propios, entidades y conceptos encontrados en el texto ("Protagonista", "Aliado", "Enemigo", "Clan / Lugar", "Artefacto") con notas opcionales antes de vincularlos al grafo narrativo del lector (`EgoGraph`).
- **Clases y Composables:**
  - `SaveCharacterDialog` (público, L25–171): Diálogo de categorización y persistencia de personajes.
- **Depende_de:** Compose UI, Material 3, `HiddenForest`.
- **Usado_por:** `HighlightColorMenu.kt`.
- **Estado:** 🟡 REVISADO_ATOMICAMENTE (completo, 171 líneas - validar en TECNO-LH7n)
- **Hallazgos:** Ninguno.

#### `ui/reader/QuoteShareDialog.kt`
- **Propósito:** Diálogo modal de exportación visual y multimedia de citas literarias. Proporciona un carrusel de temas estéticos (`QuoteCardTheme`: Bosque Esmeralda, Pergamino Clásico, Noche OLED), renderizado nativo 2D de alta fidelidad en Canvas a Bitmap PNG de 1080x1350 px para MediaStore (`Pictures/WoodsReader`), exportación de clips de video MP4 animados con voz sintetizada (`QuoteVideoClipExporter`) y compartición de texto nativo vía `Intent.ACTION_SEND`.
- **Clases y Composables:**
  - `QuoteCardTheme` (enum, L43–80): Temas visuales para tarjetas de citas (`BOSQUE_ESMERALDA`, `PERGAMINO_CLASICO`, `NOCHE_OLED`).
  - `renderThemedQuoteBitmap` (privado, L82–224): Generador en Canvas Android de bitmaps 1080x1350 px con tipografía adaptativa y firma editorial.
  - `QuoteVideoClipEntryPoint` (interfaz Dagger/Hilt, L226–230): EntryPoint para resolver `QuoteVideoClipExporter` desde Compose.
  - `QuoteShareDialog` (público, L233–633): Diálogo de previsualización, personalización y despacho de exportaciones.
- **Depende_de:** Compose UI, Material 3, Android Graphics (`Bitmap`, `Canvas`, `StaticLayout`, `TextPaint`, `Paint`), MediaStore, Dagger-Hilt (`EntryPointAccessors`), `QuoteVideoClipExporter`, `HiddenForest`.
- **Usado_por:** `ReaderScreen.kt`, `HighlightColorMenu.kt`.
- **Estado:** 🟡 REVISADO_ATOMICAMENTE (completo, 633 líneas - validar en TECNO-LH7n)
- **Hallazgos:**
  - **M21:** `QuoteVideoClipEntryPoint` utiliza `EntryPointAccessors.fromApplication` manualmente dentro de un `remember` en Compose para resolver `QuoteVideoClipExporter` en lugar de inyectarlo formalmente en el ViewModel.
  - **M22:** `renderThemedQuoteBitmap` genera directamente un Bitmap de 1080x1350 en memoria (5.8 MB ARGB_8888) en el hilo de UI cuando el usuario pulsa "Imagen", antes de escribirlo en MediaStore.

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
- **Usado_por:** ReaderScreen, ReaderSideMenu
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 574 líneas)
- **Hallazgos:** Utiliza ManageReaderAnnotationsUseCase para la exportación y borrado atómico de apuntes.

#### `ui/reader/ReaderBottomSlider.kt`
- **Propósito:** Slider inferior del lector (progreso, navegación capítulos).
- **Clases:** `ReaderBottomSlider`
- **Depende_de:** Compose, Readium Navigator
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 197 líneas)
- **Hallazgos:** Ninguno.

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
- **Propósito:** Ventana flotante macro y panel avanzado de orquestación de audio para el lector. Coordina el modal bottom sheet principal (`ModalBottomSheet`), temporizadores de sueño inteligentes, paisajes sonoros (ambient soundscapes), selectores de estilo de controles y de resaltado, y el diálogo de configuración avanzada de estudio que delega a `ReaderTtsVoiceTab`, `ReaderTtsAmbienceTab` y `ReaderTtsExportTab`.
- **Clases / Composables:**
  - `ReaderTtsMacroWindow` (público, L79–878): Diálogo modal principal de pantalla completa que aloja los controles activos mediante `ReaderTtsActiveControls`, temporizador de sueño inteligente, tarjeta de paisajes sonoros de fondo con auto-ducking, selector de estilo de controles (`TtsUiStyle`) y estilo de resaltado (`TtsHighlightStyle`), y la hoja de configuración de estudio (`showLanguageSheet`) con `TabRow` y despacho de pestañas modulares (`ReaderTtsVoiceTab`, `ReaderTtsAmbienceTab`, `ReaderTtsExportTab`).
  - `ReaderMacroEngineMode` (enum, L66–70): Modos de motor TTS disponibles (`EDGE_ONLINE`, `SHERPA_OFFLINE`, `SYSTEM`).
- **Contrato con el ViewModel:**
  - *StateFlows observados:* `currentSentenceFlow` (`StateFlow<String>`), `currentTtsRangeFlow` (`StateFlow<IntRange?>`), `chapterInfoFlow` (`StateFlow<ChapterInfo>`), `activeAmbiencePreset` (`StateFlow<AmbiencePreset>`), `audioExportState` (`AudioExportState`), `useSherpaTts` (`Boolean`), `selectedSherpaVoice` (`SherpaVoiceOption?`), `sherpaDownloadState` (`SherpaDownloadState`), `isProUnlocked` (`Boolean`), `ttsHighlightStyle` (`TtsHighlightStyle`), `ttsAutoScroll` (`Boolean`), `currentSpeed` (`Float`), `currentPitch` (`Float`), `currentLanguage` (`String?`), `currentVoiceName` (`String?`), `availableVoices` (`List<String>`), `sleepTimerRemaining` (`Long?`), `ttsUiStyle` (`TtsUiStyle`), `useEdgeNeuralTts` (`Boolean`), `selectedEdgeVoice` (`EdgeVoiceOption`).
  - *Métodos invocados:* `onVoiceChange(name)`, `onOpenRules()`, `onPlay()`, `onPause()`, `onStop()`, `onSpeedChange(speed)`, `onLanguageChange(lang)`, `onTtsAutoScrollChange(enabled)`, `onUseSherpaTtsChange(enabled)`, `onPitchChange(pitch)`, `onSkipSentenceForward()`, `onSkipSentenceBackward()`, `onSkipChapterForward()`, `onSkipChapterBackward()`, `onSleepTimer(mins)`, `onCancelSleepTimer()`, `onToggleNextAmbience()`, `onInstallTtsPresetPack(pack)`, `onTtsUiStyleChange(style)`, `onTtsHighlightStyleChange(style)`, `onResumeLast()`, `onSelectSherpaVoice(voice)`, `isSherpaVoiceDownloaded(voice)`, `onDeleteSherpaVoice(voice)`, `onExportChapterAudio()`, `onExportAudioChapters(count)`, `onCancelAudioExport()`, `onResetAudioExportState()`, `onUseEdgeNeuralTtsChange(enabled)`, `onSelectEdgeVoice(voice)`, `onDismiss()`.
- **Navegación y Efectos:**
  - `DisposableEffect(lifecycleOwner, keepAliveManager)` (L155–165): Observa `Lifecycle.Event.ON_RESUME` para refrescar el estado de optimización de batería.
  - `LaunchedEffect(currentSpeed)` (L185) y `LaunchedEffect(currentPitch)` (L186): Sincronizan el estado local de los sliders ante cambios externos.
  - `ModalBottomSheet` (L188–758): Hoja modal inferior principal de pantalla completa con estilo `ReaderDesign.DeepGlassStyle`.
  - `ModalBottomSheet` (L761–876): Hoja modal secundaria para la configuración detallada de estudio (despacho a `ReaderTtsVoiceTab`, `ReaderTtsAmbienceTab`, `ReaderTtsExportTab`).
  - Diálogos modales: Despliega `AddPhoneticRuleDialog` (L863) y `WoodsProUnlockDialog` (L870). Invoca `onOpenRules()` para el glosario fonético.
  - Persistencia de paisajes sonoros: Al cambiar `soundscape_mode` o `soundscape_intensity`, guarda inmediatamente en SharedPreferences `"woods_studio_voice_hd"` (L393, L426).
- **Depende_de:** Compose UI, Material 3, Haze (`HazeState`, `hazeChild`), `ReaderTtsActiveControls`, `ReaderTtsVoiceTab`, `ReaderTtsAmbienceTab`, `ReaderTtsExportTab`, `AddPhoneticRuleDialog`, `WoodsProUnlockDialog`, `PronunciationLexiconManager`, `ProLicenseManager`, `WoodsKeepAliveManager`, `SherpaVoiceOption`, `AudioExportState`, `TtsHighlightStyle`, `TtsUiStyle`, `SoundscapeMode`, `AmbiencePreset`, `HiddenForest`.
- **Usado_por:** `ReaderScreen.kt`.
- **Estado:** 🟡 REVISADO_ATOMICAMENTE (modularizado, 878 líneas - validar en TECNO-LH7n)
- **Hallazgos:**
  - **M17:** Uso de SharedPreferences sincrónicas (`"woods_studio_voice_hd"`) con `.edit().apply()` en callbacks de Compose (L393, L426) en lugar de persistir reactivamente en DataStore.

#### `ui/reader/AddPhoneticRuleDialog.kt`
- **Propósito:** Diálogo modal (`AlertDialog`) para añadir reglas fonéticas personalizadas al glosario de pronunciación (`PronunciationLexiconManager`). Permite asociar un texto original (ej. nombres propios o términos de novelas web) con su pronunciación fonética simplificada antes de sintetizar con las voces de estudio.
- **Clases / Composables:**
  - `AddPhoneticRuleDialog` (@Composable, L12-71): Diálogo de captura de mapeo fonético.
- **Depende_de:** Compose UI, Material 3.
- **Usado_por:** `ReaderTtsMacroWindow.kt`.
- **Estado:** 🟡 REVISADO_ATOMICAMENTE (completo, 71 líneas - validar en TECNO-LH7n)
- **Hallazgos:** Ninguno.

#### `ui/reader/ReaderTtsActiveControls.kt`
- **Propósito:** Cabecera del libro, tarjeta con fragmento de texto activo y visualizador de ondas reactivo (Canvas 56dp con 40 barras orgánicas), y bloque de controles de transporte (anterior/siguiente capítulo y oración, play/pausa con botón circular flotante y sombra luminosa, chips interactivos de velocidad, temporizador de sueño y parada).
- **Clases / Composables:**
  - `ReaderTtsActiveControls` (@Composable, L38-546): Controles de transporte y cabecera activa del panel macro de TTS.
- **Depende_de:** Compose UI, Material 3, Haze (`HazeState`), `ReaderCrystalCard`, `WoodsKeepAliveManager`, `ChapterInfo`, Coil3 (`AsyncImage`).
- **Usado_por:** `ReaderTtsMacroWindow.kt`.
- **Estado:** 🟡 REVISADO_ATOMICAMENTE (completo, 546 líneas - validar en TECNO-LH7n)
- **Hallazgos:** Ninguno.

#### `ui/reader/ReaderTtsAmbienceTab.kt`
- **Propósito:** Pestaña 2 ("Acústica y Ambiente") del estudio de voz TTS. Configura el perfil acústico DSP Master (Estudio Cálido, Nocturno Suave, Claridad HD), prosodia expresiva, modo novela dramatizada, slider de tono de voz (`PremiumSlider`), switch de glosario fonético para novelas web y listado interactivo de reglas fonéticas personalizadas con opción de borrado.
- **Clases / Composables:**
  - `ReaderTtsAmbienceTab` (@Composable, L29-324): Pestaña de masterización acústica, dramatización y reglas de pronunciación.
- **Depende_de:** Compose UI, Material 3, `PronunciationLexiconManager`, `StudioAcousticsProfile`, `PremiumSlider`.
- **Usado_por:** `ReaderTtsMacroWindow.kt`.
- **Estado:** 🟡 REVISADO_ATOMICAMENTE (completo, 324 líneas - validar en TECNO-LH7n)
- **Hallazgos:** Ninguno.

#### `ui/reader/ReaderTtsExportTab.kt`
- **Propósito:** Pestaña 3 ("Exportar .M4A") del estudio de voz TTS. Disparador de exportación por lotes de audiolibros comprimidos (1 capítulo, 10 capítulos [Pro], libro completo [Pro]), banner de advertencia si no hay voz offline descargada, y visualización de progreso y estados (`AudioExportState`) con enlace a `Music/WoodsReader`.
- **Clases / Composables:**
  - `ReaderTtsExportTab` (@Composable, L21-272): Pestaña de exportación de audiolibros offline a archivo contenedor M4A.
- **Depende_de:** Compose UI, Material 3, `AudioExportState`, `SherpaVoiceOption`.
- **Usado_por:** `ReaderTtsMacroWindow.kt`.
- **Estado:** 🟡 REVISADO_ATOMICAMENTE (completo, 272 líneas - validar en TECNO-LH7n)
- **Hallazgos:** Ninguno.

#### `ui/reader/ReaderTtsVoiceTab.kt`
- **Propósito:** Pestaña 1 ("Voz e Idioma") del estudio de voz TTS. Permite alternar entre Estudio IA Offline (Sherpa ONNX Piper VITS) y Voz del Sistema, auto-detectar idioma del libro, filtrar por idioma en carrusel horizontal (`SupportedTtsLanguage`), descargar y borrar modelos de voz Sherpa con barra de progreso, asignar rol de narrador y diálogos secundarios, y seleccionar voces nativas de Android.
- **Clases / Composables:**
  - `ReaderTtsVoiceTab` (@Composable, L33-613): Pestaña de selección y gestión de voces offline y de sistema.
- **Depende_de:** Compose UI, Material 3, `SherpaModelManager`, `SherpaVoiceOption`, `SherpaDownloadState`, `SupportedTtsLanguage`, `ReaderMacroEngineMode`.
- **Usado_por:** `ReaderTtsMacroWindow.kt`.
- **Estado:** 🟡 REVISADO_ATOMICAMENTE (completo, 613 líneas - validar en TECNO-LH7n)
- **Hallazgos:**
  - **M16:** `SherpaModelManager(context.applicationContext)` se instancia manualmente en L48 mediante `remember(context)`, eludiendo la inyección de dependencias de Hilt.

#### `ui/reader/ReaderReflowOverlay.kt`
- **Propósito:** Vista Libro para PDFs (reflujo de texto, navegación, imágenes, zoom, teleprompter WPM a 60 FPS, resaltado activo Emerald Liquid Glass `Color(0xFF10B981)`).
- **Clases:** `ReaderReflowOverlay`, `HeaderItem`, `ParagraphItem`, `ReflowImageItem`, `ZoomableImageDialog`
- **Depende_de:** Compose, Readium, Coil3, ReaderReflowManager
- **Usado_por:** ReaderScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 765 líneas)
- **Hallazgos:** Integra desplazamiento suave continuo (`scrollBy`) sincronizado con `autoScrollWpm` y animación de contenedor líquido esmeralda en el párrafo narrado.

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
- **Depende_de:** GraphRepository, BookRepository, SavedStateHandle, ForceLayout, WorkManager, GraphExtractionWorker, GraphPositionsCache, GraphJsonHelper, Readium Locator/Url/MediaType
- **Usado_por:** GraphScreen
- **Estado:** ✅ REVISADO_ATOMICAMENTE (completo, 250+ líneas)
- **Hallazgos:** **C13 [RESUELTO]:** Blindado en commit `7ac5385`: `savedStateHandle.get<String>("bookId").orEmpty()`. Aborta de forma segura sin crashear. Uso correcto de combines anidados de 2 (regla histórica del plan de grafo (archivado)). Loop de física en viewModelScope/Dispatchers.Default con `tick` para forzar redibujo.

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
- **Hallazgos:** **C13 [RESUELTO]:** Blindado en commit `7ac5385`: `savedStateHandle.get<String>("bookId").orEmpty()`. Retorno seguro con colecciones vacías si falta el ID.

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
| `ReaderVoiceAndAmbienceControllerTest.kt` | `app/src/test/.../domain/reader/ReaderVoiceAndAmbienceControllerTest.kt` | Delegación de voces Sherpa/Edge, léxico, ambientes y sleep timer | ✅ REVISADO |
| `SaveReadingProgressUseCaseTest.kt` | `app/src/test/.../domain/reader/SaveReadingProgressUseCaseTest.kt` | Debounce window (800ms, 10s), force=true, checkpoint SharedPreferences y persistencia Room | ✅ REVISADO |
| `CalculateReadingStatsUseCaseTest.kt` | `app/src/test/.../domain/reader/CalculateReadingStatsUseCaseTest.kt` | WPM activa, ETF libro y capítulo, filtro de saltos >10%, racha y sesiones | ✅ REVISADO |
| `LoadBookFastUseCaseTest.kt` | `app/src/test/.../domain/reader/LoadBookFastUseCaseTest.kt` | Cascada 3 niveles de locator (CFI, fallback href, inicio) y bypass sintético >80 capítulos | ✅ REVISADO |
| `ManageReaderAnnotationsUseCaseTest.kt` | `app/src/test/.../domain/reader/ManageReaderAnnotationsUseCaseTest.kt` | Matching marcadores EPUB/PDF/Reflow, toggle, exportación Markdown/TXT y citas | ✅ REVISADO |
| `M4aMetadataEmbedderTest.kt` | `app/src/test/.../core/tts/M4aMetadataEmbedderTest.kt` | Inyección binaria MP4 (moov antes/después de mdat, preservación de audio y átomos id3/iTunes) | ✅ REVISADO |
| `ForceLayoutTest.kt` | `app/src/test/.../ui/graph/ForceLayoutTest.kt` | Layout de fuerzas del grafo | ✅ REVISADO |
| `BookScannerServiceExtremeTest.kt` | `app/src/test/.../service/BookScannerServiceExtremeTest.kt` | Dedup y batches de escaneo | ✅ REVISADO |
| `TextRankSummarizerTest.kt` | `app/src/test/.../service/TextRankSummarizerTest.kt` | Extracción de sinopsis por TextRank | ✅ REVISADO |
| `CoverExtractorTest.kt` | `app/src/test/.../service/CoverExtractorTest.kt` | Extracción de portadas y migración de caché | ✅ REVISADO |
| `LibraryViewModelExtremeTest.kt` | `app/src/test/.../ui/library/LibraryViewModelExtremeTest.kt` | Placeholder vacío (assertTrue(true)) a reemplazar con tests reales en P8 | 🟡 TEST_VACIO |
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
| `PdfParagraphHealerTest.kt` | `app/src/test/.../core/pdf/PdfParagraphHealerTest.kt` | Tests de line-unwrapping, diálogo y detección de capítulos en novelas PDF | ✅ REVISADO |
| `LocalEpubBuilderIllustrationsTest.kt` | `app/src/test/.../core/web2epub/LocalEpubBuilderIllustrationsTest.kt` | Tests de incrustación de portada oficial e ilustraciones de historia en EPUB | ✅ REVISADO |
| `PdfIntelligenceAndExportTest.kt` | `app/src/test/.../domain/reader/PdfIntelligenceAndExportTest.kt` | Tests de búsqueda streaming y exportación por lotes M4A para PDF | ✅ REVISADO |
| `PdfCleanExtractorAndConverterTest.kt` | `app/src/test/.../core/pdf/PdfCleanExtractorAndConverterTest.kt` | Tests de deduplicación SHA-256, filtrado de spam y construcción limpia de EPUB | ✅ REVISADO |
| `LibraryPdfConversionTest.kt` | `app/src/test/.../ui/library/LibraryPdfConversionTest.kt` | Tests de conversión PDF a EPUB en LibraryViewModel, cancelación y reseteo de progreso | ✅ REVISADO |

### Tests Instrumentados (`app/src/androidTest/`)

*Los 2 archivos de prueba instrumentada de `app/src/androidTest/` se auditan y detallan en la [Sección 14.1](#141-resultados-de-tests-unitarios-appsrctest) como única fuente de verdad.*

---

## 13. Flujos de Datos Atómicos por Funcionalidad (Verificación de Flechas en Código)

### Flujo 1: Apertura de Libro (EPUB / PDF) y Carga O(1)
```
Usuario pulsa libro en LibraryScreen:
  LibraryScreen.onBookClick(bookId)
    → NavGraph: Screen.Reader.createRoute(bookId)
    → ReaderScreen(bookId)
    → ReaderViewModel.loadBook(bookId)
        ├─ SavedStateHandle seguro: bookId.orEmpty()
        ├─ if (bookId.isBlank()) → ReaderUiState.Error(...) [Resuelto C2]
        ├─ BookRepository.getBook(bookId)
        └─ LoadBookFastUseCase.invoke(book)
            ├─ AssetRetriever.retrieve(book.filePath)
            ├─ [readingOrder.size > 80]: Fast-Path Sintético O(1) de posiciones virtuales
            ├─ [readingOrder.size <= 80]: computePositions() estándar de Readium
            ├─ PublicationOpener.open() → Readium Publication
            ├─ Flattened TOC + Checkpoint de progreso resiliente (DataStore/Room fallback)
            └─ Retorna LoadBookFastResult.Success(publication, initialLocator, toc)
    → ReaderViewModel expone StateFlows:
        ├─ _publication.value = pub
        ├─ _currentLocator.value = initialLocator
        ├─ _toc.value = flatToc
        └─ _uiState.value = ReaderUiState.Success
    → ReaderScreen renderiza ReaderNavigatorContainer (WebView Readium con CSS paginado inyectado)
```

### Flujo 2: Persistencia de Progreso y Cálculo de Estadísticas en Tiempo Real
```
Usuario hace scroll o cambia página / TTS avanza párrafo:
  ReaderScreen / WebView
    → ReaderViewModel.onLocatorChanged(locator) / onTtsLocatorChanged(locator)
        ├─ SaveReadingProgressUseCase.invoke(bookId, locator)
        │   ├─ Debounce reactivo de 800 ms (DEFAULT_DEBOUNCE_MS = 800L; TTS: TTS_DEBOUNCE_MS = 10.000 ms) para scroll continuo (evita IO masivo en DB)
        │   ├─ flushReadingProgressImmediately() invocado en ON_PAUSE / ON_STOP / cierre
        │   ├─ ReadingProgressManager: serializa Locator JSON
        │   ├─ BookDao.updateProgressWithFallback(bookId, progression, locatorJson)
        │   └─ ReadingSessionManager.recordSession(bookId, wordsRead, timeSpent)
        │       → Room: reading_sessions, daily_word_counts
        └─ CalculateReadingStatsUseCase.invoke(book, locator)
            ├─ Calcula tiempo restante de capítulo y libro (palabras restantes / WPM de usuario)
            ├─ Calcula porcentaje total de avance (0.0 .. 1.0)
            └─ Actualiza ReaderViewState: exposed WPM, ETA y lectura del día
```

### Flujo 3: Gestión de Anotaciones, Citas y Decoraciones Visuales
```
Usuario selecciona texto en el lector:
  ReaderScreen (Menú contextual de selección)
    → ReaderViewModel.addHighlight(text, color, note) / toggleBookmark()
        → ManageReaderAnnotationsUseCase
            ├─ Room: HighlightDao.insertHighlight() / BookmarkDao.insertBookmark()
            ├─ Transforma entidades a Readium Decoration (HighlightDecorationListener)
            ├─ Inyecta CSS de resaltado en el WebView sin recargar el capítulo
            └─ QuoteCardGenerator / HighlightExporter (si el usuario pulsa "Compartir Cita")
                ├─ Genera tarjeta estilizada con tipografía del libro y autor
                └─ QuoteVideoClipExporter (genera clip animado MP4 con audio y texto)
```

### Flujo 4: Búsqueda Streaming, Rayos-X y Exportación Unificada `.m4a`
```
Búsqueda y Análisis Semántico:
  ReaderViewModel.searchInBook(query) / generateXRayReport()
    → ReaderIntelligenceAndExportCoordinator
        ├─ FastEpubSearchEngine.search(publication, query)
        │   └─ Kotlin Flow streaming: emite coincidencias parciales con snippet y Locator
        └─ BookXRayEngine.analyze(publication, currentChapterIndex)
            └─ Extracción de entidades clave (personajes, lugares, términos) para ReaderXRayBottomSheet

Exportación a Archivo .M4A Único (PARTE 73):
  ReaderViewModel.exportCurrentChapterToAudio() / exportAudioChapters(maxChaptersToExport)
    → ReaderIntelligenceAndExportCoordinator.exportChapterAudio(...)
        → ChapterAudioExporter.exportChapterAudio(...)
            ├─ 1. Extrae texto limpio del capítulo desde Readium Publication
            ├─ 2. Sintetiza audio PCM con Sherpa ONNX o Android TTS nativo
            ├─ 3. Codifica stream AAC a 48 kbps (KEY_BIT_RATE = 48_000, voz optimizada) usando MediaCodec en contenedor ISO MP4 (.m4a)
            ├─ 4. M4aMetadataEmbedder.embedAllIntoM4a(m4aFile, coverBitmap, lrcString, title, author)
            │   ├─ Incrusta portada JPEG en átomo moov.udta.meta.ilst.covr
            │   ├─ Incrusta subtítulos sincronizados en átomo moov.udta.meta.ilst.©lyr
            │   └─ Escribe etiquetas estándar de título (©nam) y autor (©ART)
            └─ 5. Purga atómica de archivos temporales sueltos (.lrc, .srt, .jpg, .txt)
                └─ Resultado en MediaStore: UN SOLO ARCHIVO .m4a en Music/WoodsReader
```

### Flujo 5: Reproducción TTS (Text-to-Speech) y Supervivencia en Background
```
Usuario pulsa botón Play de audio:
  ReaderScreen → ReaderViewModel.playTts()
    → TtsManager.narrateSequence(blocks, startIndex)
        ├─ ensurePreferencesLoaded() [Resuelto C6]: espera asíncrona máx 300 ms si la caché aún no cargó
        ├─ Lee preferencia cachedUseSherpaTts (sin bloquear con .first())
        ├─ Si usa Sherpa ONNX:
        │   → SherpaTtsEngine.synthesizeStream(text) → AudioTrack nativo PCM
        ├─ Si usa TTS Nativo:
        │   → android.speech.tts.TextToSpeech.speak(text, ..., utteranceId)
        │   → UtteranceProgressListener único [Resuelto C7]:
        │       ├─ Maneja speakResult != SUCCESS inmediatamente
        │       ├─ IDs monótonos con AtomicLong
        │       └─ Fallback para utteranceId == null en dispositivos OEM
        ├─ AmbientSoundscapePlayer: mezcla sonidos de bosque (lluvia, viento, chimenea) con auto-ducking
        └─ SmartSleepTimerManager: cuenta regresiva de sueño con desvanecimiento gradual de volumen
    → ReaderTtsService.start(context)
        ├─ Servicio Foreground con MediaSessionCompat y MediaStyle notification
        ├─ WoodsKeepAliveManager: retiene Partial WakeLock y protección contra LMKD
        └─ Sincronización de progreso: emite onTtsLocatorChanged(locator) para auto-scroll del texto
```

### Flujo 6: Traducción In-Place de Libros EPUB
```
Usuario solicita traducción de libro en biblioteca:
  LibraryScreen → CalibreTranslationProgressDialog
    → CalibreStyleEpubTranslator.translate(bookFile, targetLanguage)
        ├─ EpubInPlaceTranslator: abre archivo ZIP original como contenedor
        ├─ EpubPackageParser: extrae OPF, spine y hojas XHTML del libro
        ├─ Itera documentos XHTML con XhtmlTranslatorDoc:
        │   ├─ Segmenta nodos de texto protegiendo etiquetas HTML y entidades
        │   └─ GoogleFreeNetworkTranslator: traduce texto con reintentos y pacing
        └─ EpubZipWriter: reescribe archivo EPUB destino preservando compresión STORED en mimetype
    → BookRepository.insertTranslatedBook(newBook)
```

### Flujo 7: Importación de Novelas Web (Web2EPUB)
```
Usuario ingresa URL en diálogo de importación:
  LegacyWebToEpubImporterDialog
    → WorkManager: encola WebNovelImportWorker(novelUrl)
        → NovelImporter.importNovel(url)
            ├─ SiteProfileLoader: busca perfil de sitio en JSON (selectores CSS para ads y contenido)
            ├─ NovelIndexLoader: descarga índice de capítulos y metadatos
            ├─ RequestPacer: respeta rate limits y reglas de robots.txt (RobotsAdvisor)
            ├─ ContentExtractor: limpia HTML de cada capítulo usando Jsoup Safelist
            ├─ ImageSniffer: descarga e incrusta imágenes de portada e ilustraciones internas
            ├─ ImportStore: persiste estado y progreso en disco (soporta reanudación ante desconexión)
            └─ EpubAssembler: compila capítulos limpios en archivo EPUB estándar válido
        → BookRepository.upsertBook(...) → Notificación de descarga completada
```

### Flujo 8: Grafo de Entidades y Minería Semántica
```
Usuario abre Grafo o pulsa "Construir Grafo de Personajes":
  GraphScreen / CandidatesScreen
    → GraphViewModel.loadGraph(bookId)
        ├─ SavedStateHandle seguro: bookId.orEmpty() [Resuelto C13]
        ├─ GraphRepository.getEntitiesForBook(bookId) → Room: graph_entity
        ├─ GraphRepository.getRelationsForBook(bookId) → Room: graph_relation
        ├─ Si no existen datos o usuario solicita minería:
        │   └─ WorkManager: lanza GraphExtractionWorker(bookId)
        │       ├─ Lee texto de capítulos con EpubContentExtractor
        │       ├─ EntityHeuristics / TextRankSummarizer: detecta menciones de personajes y co-ocurrencias
        │       └─ GraphDao: inserta entidades, aliases y relaciones en transacción Room
        └─ GraphCanvas renderiza visualización de nodos mediante ForceLayout
            └─ GraphPositionsCache: almacena coordenadas físicas calculadas para asentamiento instantáneo
```

### Flujo 9: Licencia Pro y Verificación Criptográfica Offline
```
Usuario ingresa clave de activación en WoodsProUnlockDialog:
  WoodsProUnlockDialog
    → ProLicenseManager.verifyLicenseKey(key)
        ├─ Obtiene identificador único de hardware del dispositivo
        ├─ Computa hash criptográfico SHA-256 con salt interno del proyecto
        ├─ Compara firma binaria sin requerir conexión a internet
        └─ Si es válida:
            ├─ Persiste estado Pro en DataStore (UserPreferences.isProUser = true)
            └─ Habilita exportador de video (QuoteVideoClipExporter), soundscapes ilimitados y modelos TTS avanzados
```

---

## 14. Registro Consolidado de Errores Conocidos y Deuda Técnica

Esta sección reúne **TODOS** los hallazgos críticos, advertencias, errores resueltos y deuda técnica de Woods Reader identificados en el código fuente, documentando sus componentes afectados, estado y pasos de verificación.

### Leyenda de Estados
- ✅ **Resuelto y verificado en código:** Verificado mediante inspección estricta de código y pruebas unitarias automáticas en verde.
- 🟡 **Resuelto en código, pendiente de validación en dispositivo (TECNO-LH7n):** Implementado y verificado en código, pero requiere validación física en hardware.
- 🔴 **Pendiente (Error conocido / Deuda técnica):** Problema vigente identificado, documentado sin corregir en esta fase.
- ⚪ **No verificado:** Estado preventivo de elementos pendientes de auditoría directa.

---

### Matriz Consolidada de Hallazgos

| ID | Sev. | Archivo : Línea | Síntoma / Riesgo | Componentes Afectados (Conexiones) | Estado | Cómo Reproducirlo o Verificarlo | Workaround / Solución Aplicada |
|---|---|---|---|---|---|---|---|
| **C1** | Crítica | `AppDatabase.kt:37-39` | Fallo de migración (`IllegalStateException`) al actualizar de DB v10 a v11 | `AppDatabase` → Todos los DAOs → Toda la app | ✅ **Resuelto** | Abrir app con DB v10 preexistente. Esquemas `10.json` vs `11.json` confirman que solo se añadieron 4 tablas de grafos sin alterar entidades previas. | Resuelto vía Room `@AutoMigration(from = 10, to = 11)`. No requiere `AutoMigrationSpec`. |
| **C2** | Crítica | `ReaderViewModel.kt:171, 597` | Cierre forzado inmediato (`IllegalStateException`) si el lector se abre con URI sin `bookId` | `NavGraph` → `ReaderScreen` → `ReaderViewModel` | ✅ **Resuelto** | Navegar a `Screen.Reader.createRoute("")`. Antes crasheaba en `checkNotNull(savedStateHandle["bookId"])`. | Commit `7ac5385`: `savedStateHandle.get<String>("bookId").orEmpty()`. Si viene en blanco, emite `ReaderUiState.Error` seguro. |
| **C3** | Crítica | `ReaderViewModel.kt:129-161` | Descompresión de God Class en curso: 21 parámetros en constructor (2 de framework: Application + SavedStateHandle con `bookId` + 19 dependencias inyectadas) tras extraer coordinadores P5/P6. Original tenía 24 parámetros (2 de framework + 22 dependencias de dominio, corregida cifra previa de 17 y 27). Líneas pasaron de 2.905 a 2.476 y a 2.158; objetivo ~600 | `ReaderViewModel` ↔ Toda la lógica de lectura, audio, búsqueda y exportación | 🟡 **Validar en tlf.** | Contar parámetros en constructor (L129-161): 21 parámetros en HEAD. Pendiente validar físicamente en `TECNO-LH7n` cambio de voz Sherpa, descarga de modelo, activación de ambiente y sleep timer. | Encapsuladas 7 dependencias puente en `ReaderVoiceAndAmbienceController` (Sherpa ONNX, Edge Neural, léxico, ambientes y sleep timer) y delegada navegación en `ReaderChapterNavigationCoordinator`. Firmas públicas en UI intactas. |
| **C4** | Alta | `SherpaModelManager.kt:280` | Corrutina huérfana en `init` lanzada en singleton sin control de cancelación | `SherpaModelManager` → Ciclo de vida de app | 🔴 **Pendiente** | Inspeccionar línea 280: `managerScope.launch` borra directorio de Kokoro recursivamente sin almacenar el `Job`. | Deuda técnica documentada. No impacta runtime crítico al ser `@Singleton`, pero carece de método de cancelación ordenado. |
| **C5** | Baja | `SherpaModelManager.kt:437` | Logging nativo C++ de Sherpa ONNX configurado con `debug = com.example.woodsreader.BuildConfig.DEBUG` | `SherpaModelManager` → Logcat del sistema | ⚪ **Falso positivo / No aplica** | Inspeccionar L437: pasa `debug = com.example.woodsreader.BuildConfig.DEBUG`. En compilación de release compila como `false`, anulando logs en producción. | Falso positivo: el reporte original asumía `debug = true` hardcodeado en producción. En release `BuildConfig.DEBUG` es `false`, por lo que el logging C++ está desactivado en producción. |
| **C6** | Crítica | `TtsManager.kt:1375` | Congelamiento o suspensión prolongada del hilo de síntesis de TTS en arranque en frío | `TtsManager` → `narrateSequence` → `ReaderTtsService` | 🟡 **Validar en tlf.** | Pulsar Play de TTS inmediatamente tras abrir la app en frío con motor Sherpa activado. | Commits `93e4dc2` y `5a9b341`: eliminó `.first()`, usa `@Volatile cachedUseSherpaTts` y `ensurePreferencesLoaded(300ms)`. |
| **C7** | Crítica | `TtsManager.kt:225, 327, 477, 831, 869` | Desincronización de oraciones, continuaciones colgadas o silencio por carreras en listeners | `TtsManager` → `TextToSpeech` nativo → `ReaderScreen` | 🟡 **Validar en tlf.** | Reproducir secuencias largas con saltos de párrafo rápidos o pausas intempestivas. | Commits `93e4dc2` y `5a9b341`: listener único en `initialize()`, IDs con `AtomicLong`, manejo de error y fallback OEM para `utteranceId == null`. |
| **C8** | Media | `ReaderViewModel.kt:355, 502, 748, 754, 755, 1026, 1293, 1662, 2012, 2147, 2205, 2245` | Caracteres corruptos (mojibake: `Ã`, `â€`, `Â`) en comentarios de código fuente del ViewModel | `ReaderViewModel.kt` | 🔴 **Pendiente** | Búsqueda grep de caracteres Latin1/Windows-1252: 12 líneas contienen signos `?` y secuencias corruptas. | Pendiente saneamiento ortográfico manual. No altera bytecode ni ejecución. |
| **C9** | Crítica | `AppDatabase.kt:37` | Confirma duplicidad de C1 sobre migración de Room | `AppDatabase` | ✅ **Resuelto** | Verificado en conjunto con C1. | Resuelto mediante `@AutoMigration(from = 10, to = 11)`. |
| **C10** | Media | `ReaderViewModel.kt:355, 502, 748, 754, 755, 1026, 1293, 1662, 2012, 2147, 2205, 2245` | Confirma duplicidad de C8 sobre mojibake en el ViewModel | `ReaderViewModel.kt` | 🔴 **Pendiente** | Mismo hallazgo C8: pendiente saneamiento ortográfico en 12 líneas del archivo. | No afecta ejecución ejecutable ni compilación. |
| **C11** | Baja | `TtsManager.kt:309-779`, `BookScannerService.kt:100-639`, `LibraryScreen.kt:63-277`, `ReaderScreen.kt:553, 570`, `MultilingualAudiobookNormalizer.kt:30` | Mojibake residual en comentarios descriptivos (24 ocurrencias) y regex con riesgo funcional en normalizador (1 ocurrencia) | Comentarios y normalizador TTS | 🔴 **Pendiente** | Fuera de `ReaderViewModel`: comentarios en `TtsManager` (8), `BookScannerService` (5), `LibraryScreen` (9) y `ReaderScreen` (2). Marcado aparte `MultilingualAudiobookNormalizer.kt:30` (`elongatedCharsRegex`) como código/regex con riesgo funcional. En `ui/reader/` hay 0 cadenas visibles al usuario con mojibake. | Saneamiento acotado por fases (P2 en `ui/reader/`, P7 en resto). `MultilingualAudiobookNormalizer.kt` no se toca sin suite de pruebas completa. |
| **C12** | Crítica | `BookDetailViewModel.kt:59, 69` | Cierre forzado (`IllegalStateException`) al abrir detalle de libro con `bookId` nulo o inválido | `NavGraph` → `BookDetailScreen` → `BookDetailViewModel` | ✅ **Resuelto** | Navegar a `Screen.BookDetail.createRoute("")`. | Commit `7ac5385`: `savedStateHandle.get<String>("bookId").orEmpty()`. Expone `BookDetailUiState(error = "...")`. |
| **C13** | Crítica | `GraphViewModel.kt:72, 77, 96`, `CandidatesScreen.kt:52, 56, 75` | Cierre forzado (`IllegalStateException`) al abrir grafo o candidatos de entidades sin `bookId` | `NavGraph` → `GraphScreen` / `CandidatesScreen` → ViewModels | ✅ **Resuelto** | Abrir grafo sin parámetro de libro. | Commit `7ac5385`: `savedStateHandle.get<String>("bookId").orEmpty()`. Abortan retorno seguro con colecciones vacías. |
| **A1** | Alta | `GraphRepository.kt:80` | Riesgo de inconsistencia si `mergeInto` falla a mitad de transacción | `GraphRepository` → `GraphDao` → Room | ✅ **Verificado** | Revisar implementación de `mergeInto`. | La operación ejecuta dentro de `@Transaction` de Room, garantizando rollback atómico ante cualquier excepción. |
| **A9** | Alta | `LibraryViewModel.kt:315` | Fallo de permisos silencioso si `takePersistableUriPermission` arroja `SecurityException` | `LibraryViewModel` → Android SAF / Storage Access | 🔴 **Pendiente** | Importar libro desde un proveedor SAF que revoque permisos o no admita flags de persistencia. | Envolver la llamada en `runCatching` y mostrar feedback visual en `LibraryScreen` en lugar de fallar silenciosamente. |
| **A10** | Alta | `GraphScreen.kt:180` | Intent de exportación de JSON del grafo excede límite de IPC de Binder (~1 MB) | `GraphScreen` → Android Binder → Apps externas | 🔴 **Pendiente** | Exportar un libro con más de 1.500 entidades o relaciones vía Intent directo (`EXTRA_TEXT`). | Deuda técnica: sustituir pasaje en memoria por archivo temporal compartido vía `FileProvider`. |
| **A12** | Alta | `ReaderViewModel.kt:760` | Divergencia entre progreso guardado por scroll visual y progreso de narración TTS | `ReaderViewModel` ↔ `SaveReadingProgressUseCase` | ✅ **Resuelto** | Leer con scroll y activar TTS alternadamente. | Resuelto en Paso 1: ambos flujos delegan en `SaveReadingProgressUseCase`, unificando la base de datos Room. |
| **M2** | Media | `AppTypeConverters.kt:20-60` | Búsqueda $O(N)$ en conversiones de enums mediante `firstOrNull` | `AppTypeConverters` → Room queries | 🔴 **Pendiente** | Uso continuo en mapeo de listas Room. Al tener los enums 3 a 5 elementos, el costo en CPU es de microsegundos. | Optimización recomendada: mapear vía `Map<String, Enum>` estático indexado. |
| **M4** | Media | `BookDao.kt:110` | `@Transaction @Query` retornando `PagingSource` puede disparar invalidaciones masivas | `BookDao` → Paging 3 → `LibraryScreen` | ✅ **Verificado** | Carga de listas paginadas en Compose. | Patrón oficial recomendado por AndroidX Room Paging para tablas con relaciones foráneas. |
| **M13** | Baja | `DatabaseModule.kt:85` | Inconsistencia de estilo: `provideGraphDao` utiliza FQCN en vez de import limpio | `DatabaseModule.kt` | 🔴 **Pendiente** | Inspección de imports. Cero impacto técnico. | Limpieza de import pendiente en refactor cosmético. |
| **M15** | Media | `InlineLookupManager.kt:45` | Scope global `appScope` no dispone de método de cancelación ante salida de lectura | `InlineLookupManager` → Diccionario flotante | 🔴 **Pendiente** | Realizar múltiples consultas de diccionario continuas al cerrar el lector. | Migrar a coroutine scope vinculado al ciclo de vida del lector (`coroutineScope` local). |
| **SEC-1** | Alta | `KeyMaker.kt:18` | Clave privada ECDSA embebida en código fuente (`PRIVATE_KEY_BASE64`) | Módulo `:keymaker` | ✅ **Verificado** | Inspección de `:keymaker`. | El módulo `:keymaker` es una herramienta interna de escritorio para el desarrollador, excluida del build del APK `:app`. |
| **M3** | Media | `ReaderViewModel.kt:502` | CFI de Readium persistido como JSON plano propenso a desincronización | `ReaderViewModel` → `SaveReadingProgressUseCase` → Room | 🔴 **Pendiente** | Inspeccionar serialización de Locator CFI. | Modelar contrato tipado para Locator en lugar de serialización de texto plano. |
| **M16** | Media | `ReaderTtsMacroWindow.kt:1215` | Instanciación manual de `SherpaModelManager(context.applicationContext)` vía `remember` eludiendo DI | `ReaderTtsMacroWindow` → `SherpaModelManager` | 🔴 **Pendiente** | Inspeccionar L1215: crea nueva instancia en lugar de inyectar `@Singleton`. | Deuda técnica de DI: migrar a inyección vía EntryPoint o proveer a través de ViewModel. |
| **M17** | Baja | `ReaderTtsMacroWindow.kt:1230` | Uso de SharedPreferences sincrónicas (`"woods_studio_voice_hd"`) con `.edit().apply()` en callbacks Compose | `ReaderTtsMacroWindow` → SharedPreferences | 🔴 **Pendiente** | Inspeccionar L1230: persiste acústica de voz sincrónicamente. | Deuda técnica de persistencia: migrar a DataStore reactivo (`UserPreferencesRepository`). |
| **M18** | Media | `ReaderScreen.kt:1158-1163` | Inyección de JavaScript directo (`s.modify('move', 'backward', 'sentence')`) en WebViews sin timeout ni callback de error | `ReaderScreen` → Readium WebViews | 🔴 **Pendiente** | Inspeccionar L1158: `evaluateJavascript` ejecutado sin timeout protector ni callback de error. | Envolver la ejecución en script seguro con `catch` y callback de estado. |
| **M19** | Baja | `ReaderScreen.kt:1207-1215` | Captura de excepciones vacía (`catch (_: Exception) {}`) en Intent implícito `ACTION_VIEW` al abrir navegador | `ReaderScreen` → Intent `ACTION_VIEW` | 🔴 **Pendiente** | Inspeccionar L1207: si no hay navegador externo instalado, oculta silenciosamente el fallo. | Registrar log de advertencia o mostrar `Toast` de feedback al usuario. |
| **M20** | Baja | `ReaderUIComponents.kt:838` | Captura de excepciones vacía (`catch (_: Exception) {}`) en fallback de navegador web para traducción | `ReaderUIComponents` (`HighlightColorMenu`) → Intent `ACTION_VIEW` | 🔴 **Pendiente** | Inspeccionar L838: omite manejo si falla el lanzamiento del navegador. | Capturar `ActivityNotFoundException` y avisar con Snackbar/Toast. |
| **M21** | Media | `ReaderUIComponents.kt:1241-1245` | `QuoteVideoClipEntryPoint` usa `EntryPointAccessors.fromApplication` en `remember` de Compose | `ReaderUIComponents` (`QuoteShareDialog`) → `QuoteVideoClipExporter` | 🔴 **Pendiente** | Inspeccionar L1241: acoplamiento de Dagger EntryPoint en la capa de UI. | Inyectar `QuoteVideoClipExporter` en el ViewModel correspondiente y exponer flujo de exportación. |
| **M22** | Media | `ReaderUIComponents.kt:1530-1536` | Generación de Bitmap (1080x1350 px, 5.8 MB) síncrona en el hilo principal de UI al pulsar "Imagen" | `ReaderUIComponents` → Canvas Android → Hilo Main | 🔴 **Pendiente** | Inspeccionar L1530: `renderThemedQuoteBitmap` corre en hilo UI antes de insertar en MediaStore. | Mover la generación y compresión del Bitmap a `Dispatchers.Default` o `Dispatchers.IO`. |
| **M23** | Baja | `SettingsScreen.kt:1171-1184` | Ficha técnica contiene cadenas de versión hardcodeadas ("v1.0.0-ALPHA", "Build 2026.10") | `SettingsScreen` → Ficha Técnica | 🔴 **Pendiente** | Inspeccionar L1175: valores estáticos en texto multilínea. | Sustituir cadenas fijas por interpolación de `BuildConfig.VERSION_NAME` y `BuildConfig.VERSION_CODE`. |
| **M24** | Media | `SettingsScreen.kt:940, 1356` | Exposición directa de instancias de dominio (`viewModel.woodsKeepAliveManager`, `viewModel.proLicenseManager`) a vistas Compose | `SettingsScreen` → Gestores de Dominio | 🔴 **Pendiente** | Inspeccionar L940 y L1356: la UI invoca métodos de los gestores directamente. | Delegar las acciones a través de métodos públicos del `SettingsViewModel`. |
| **M25** | Media | `ReaderViewModel.kt:304, 582, 2149` / `SleepTimerManager.kt:37-43` | Dos temporizadores en paralelo con preset forzado `MIN_15` unificados en `SleepTimerManager` como autoridad única | `ReaderViewModel` ↔ `SleepTimer` ↔ `TtsManager.smartSleepTimerManager` | 🟡 **Validar en tlf.** | Probar en teléfono TECNO-LH7n: temporizador de 20 min (cuenta regresiva real), fin de capítulo (-1, detiene TTS y ambiente) y cancelación (restaura velocidad). | Unificada la autoridad en `SleepTimerManager` con soporte de minutos arbitrarios y fin de capítulo (-1). En `ReaderViewModel` se eliminó la corrutina concurrente de `smartSleepTimerManager`, sincronizando parada de narración y ambientes sonoros. |
| **M26** | Media | `ReaderViewModel.kt:167-195, 1349-1430, 1463-1540` | Retorno anticipado antes de la rama PDF desacoplado de `VisualNavigator` en PDF y Reflow usando TOC | `ReaderViewModel` → Navegación PDF Reflow | 🟡 **Validar en tlf. (hasta P4)** | Confirmado en código: retorno anticipado (`val nav = navigator ?: return`) impedía alcanzar la rama PDF cuando `navigator` era null. No confirmado: que `navigator` sea efectivamente `null` en vista libro. Validar en `TECNO-LH7n` en P4: botones de siguiente/anterior capítulo en barra inferior (modo libro y reflow) y avance automático de TTS. | Desacoplada la navegación por capítulos en PDF/Reflow vía `resolveNextPdfPageFromToc` / `resolvePreviousPdfPageFromToc` en `ReaderChapterNavigationCoordinator`. Estado 🟡 hasta pruebas de integración en P4. |
| **M27** | Media | `ReaderViewModel.kt:2143`, `ReaderContract.kt:39` | `ReaderViewState` contiene 10 de 14 campos obsoletos/nunca actualizados, y `ReaderState` duplica sus campos activos | `ReaderViewModel` ↔ `ReaderContract` ↔ `ReaderViewState` | 🔴 **Pendiente** | Inspeccionar `_viewState`: solo actualiza 4 campos (bloques de narración y PDF); los otros 10 campos nunca se mutan y la UI no observa `viewState`. | Eliminar campos muertos de `ReaderViewState` o fusionarlo con `ReaderState` en `ReaderContract.kt`. |
| **M28** | Media | `ReaderViewModel.kt:1066, 1205` | Bloques duplicados de cálculo de tiempo restante y progreso de capítulo entre `onTtsLocatorChanged` y `saveProgress` | `ReaderViewModel` ↔ `CalculateReadingStatsUseCase` | ✅ **Resuelto** | Comparar L1066 con L1205: cálculo unificado en función privada `updateReadingStats(locator, activeWpm)`. | Unificada la lógica duplicada en `updateReadingStats(locator, activeWpm)`, eliminando 54 líneas repetidas y estandarizando `DEFAULT_READING_WPM`. |
| **M29** | Baja | `ReaderViewModel.kt:141` | `readingProgressManager` inyectado en el constructor de `ReaderViewModel` sin ningún uso en todo el archivo | `ReaderViewModel` ↔ `ReadingProgressManager` | 🔴 **Pendiente** | Buscar `readingProgressManager` en `ReaderViewModel.kt`: solo aparece en L141 en la firma del constructor. | Eliminar la inyección de `ReadingProgressManager` del constructor de `ReaderViewModel`. |

### 📱 Riesgos que Solo se Detectan en Dispositivo Físico (Validación en TECNO-LH7n)

Los siguientes comportamientos dependen de subsistemas de hardware, controladores OEM y el kernel de Android, por lo que **NO PUEDEN DARSE POR RESUELTOS (✅)** mediante pruebas unitarias en JVM ni en emulador estándar:

1. **Pausa y Reanudación de TTS ante Pérdida y Recuperación de `AudioFocus`**:
   - *Riesgo:* Al entrar una llamada telefónica, reproducir un audio en WhatsApp o recibir instrucciones del GPS, el sistema Android quita el foco de audio transitoriamente.
   - *Verificación física:* Probar que `TtsManager` pause suavemente la narración y la reanude en el punto exacto al colgar, sin superponerse ni perder la posición de lectura.
2. **Compatibilidad con `utteranceId == null` o Modificado por Motores TTS OEM**:
   - *Riesgo:* Dispositivos de fabricantes como Samsung, Xiaomi (MIUI) o Tecno (HiOS) ejecutan motores de síntesis propietarios que a veces omiten o alteran el `utteranceId` en el callback `onDone()`.
   - *Verificación física:* Comprobar en el teléfono TECNO-LH7n que la narración de párrafos consecutivos fluya sin detenerse eternamente esperando el listener.
3. **Arranque en Frío con Sherpa ONNX y Latencia de Inferencia por Hardware**:
   - *Riesgo:* Cargar los modelos ONNX (VITS / Piper) en la memoria RAM y asignarlos a la NPU/CPU de la arquitectura MediaTek puede tardar entre 150 y 350 ms.
   - *Verificación física:* Confirmar que el método `ensurePreferencesLoaded(timeoutMs = 300)` impida condiciones de carrera sin congelar la UI de lectura al pulsar Play en frío.
4. **Reproducción del Archivo Único `.M4A` en Reproductores de Terceros**:
   - *Riesgo:* La caja de metadatos `moov.udta.meta.ilst` con portada en `covr` y letras en `©lyr` incrustada por `M4aMetadataEmbedder` debe ser parseada según el estándar ISO/IEC 14496-12.
   - *Verificación física:* Abrir el archivo exportado en reproductores comerciales en el dispositivo (VLC, Poweramp, Musicolet, Samsung Music) y verificar:
     - La carátula del libro se muestra en resolución nítida.
     - Las letras LRC sincronizadas se desplazan al compás de la voz del narrador.
     - No existen archivos `.lrc`, `.srt` ni `.jpg` sueltos en `Music/WoodsReader`.
5. **Fluidez de Scroll a 60 FPS en la GPU Mali (Evitar Lag de Recomposición en Compose)**:
   - *Riesgo:* En procesadores MediaTek con GPU Mali-G52/G57, recomposiciones innecesarias de Compose durante el scroll continuo en el WebView provocan caídas perceptibles de frames (jank).
   - *Verificación física:* Realizar scroll continuo rápido a través de varios capítulos y verificar que el debouncing de 800 ms (10.000 ms para TTS) en `SaveReadingProgressUseCase` y los `StateFlow` desacoplados mantengan 60 FPS estables.
6. **Apertura de Libros Masivos (>80 Capítulos / Spines) y Prevención de ANR**:
   - *Riesgo:* El cálculo pesado de posiciones de Readium en libros con cientos de divisiones (`readingOrder.size > 80`) puede saturar el hilo principal y disparar diálogo de ANR.
   - *Verificación física:* Abrir un libro voluminoso (>1.000 páginas) en el teléfono y cronometrar que el fast-path sintético $O(1)$ abra la pantalla instantáneamente en memoria (tiempo objetivo estimado <200 ms).
7. **Temporizador de Sueño Inteligente, Fin de Capítulo y Cancelación (M25)**:
   - *Riesgo:* Desincronización en la cuenta regresiva, doble parada de audio o falta de restauración de velocidad del motor TTS al cancelar o expirar.
   - *Verificación física:* En el teléfono TECNO-LH7n:
     - Configurar temporizador personalizado de 20 min y verificar que el contador descienda con precisión.
     - Activar modo "Fin del Cap." (-1) y verificar que la narración y los sonidos de ambiente se detengan al culminar el capítulo actual.
     - Cancelar el temporizador durante la rampa de frenado final y verificar que la velocidad del audio retorne a su valor original sin artefactos.
8. **Navegación por Capítulos y Avance Automático de TTS en PDF / Reflow (M26)** (Estado 🟡 hasta la P4):
   - *Confirmado vs no confirmado:* Confirmado en código que el retorno anticipado (`val nav = navigator ?: return`) impedía ejecutar la navegación PDF si `navigator` era null. No confirmado que `navigator` sea efectivamente `null` en vista libro en runtime (validación física en dispositivo requerida).
   - *Riesgo:* En libros PDF (tanto en modo estándar de dos páginas/libro como en modo Reflow vertical), el usuario puede pulsar los botones de salto de capítulo en la barra inferior o finalizar la narración del capítulo actual con avance automático activado (`advanceTtsToNextChapter`).
   - *Verificación física:* En el teléfono TECNO-LH7n:
     - Abrir un PDF con tabla de contenidos (TOC) estructurada en modo libro y en modo Reflow.
     - Pulsar el botón de siguiente capítulo y anterior capítulo en el slider inferior: verificar que la página salte al inicio del capítulo correspondiente sin requerir `navigator` activo en Compose.
     - Activar TTS cerca del final del capítulo y confirmar que al concluir el último bloque de texto, la app cargue y comience a sintetizar automáticamente el siguiente capítulo sin congelarse ni interrumpir la sesión de lectura.
9. **Control de Voces Sherpa/Edge, Soundscapes de Ambiente y Temporizador en Controlador Desacoplado (C3 / P6)**:
   - *Riesgo:* Condiciones de carrera al cambiar entre modelos de voz ONNX en caliente, descarga en segundo plano y extracción de modelos tar.bz2 en almacenamiento local, activación de soundscapes concurrentes (`ForestAmbienceEngine` + `AmbientSoundscapePlayer`) o parada de sleep timer.
   - *Verificación física:* En el teléfono TECNO-LH7n:
     - Cambiar voz de síntesis Sherpa ONNX a una voz ya descargada y probar síntesis en caliente.
     - Seleccionar una voz no descargada: verificar apertura del diálogo oficial de descarga, progreso de descarga porcentual y conmutación automática una vez extraído el modelo.
     - Activar soundscape de ambiente (`ForestAmbienceEngine`) mientras se reproduce TTS y ajustar volumen relativo.
     - Iniciar temporizador de sueño y verificar que detenga TTS y ambiente sonoro simultáneamente al expirar.

---

## 14.1 Resultados de Tests Unitarios (`app/src/test/`)

Todos los tests unitarios de `app/src/test/` han sido revisados atomicamente:

| Archivo | Clase bajo prueba | Estado | Hallazgos |
|---------|-------------------|--------|-----------|
| `ExampleUnitTest.kt` | Prueba básica | ✅ REVISADO | 1 test trivial (2+2=4), pasa |
| `EntityHeuristicsTest.kt` | EntityHeuristics | ✅ REVISADO | Localizado en `app/src/test/.../util/` |
| `ReaderViewModelTest.kt` | ReaderViewModel | ✅ REVISADO | Localizado en `app/src/test/.../ui/reader/` (15 tests: navegación por TOC para PDF/Reflow, error ante bookId vacío y delegación reactiva a ReaderIntelligenceAndExportCoordinator) |
| `ReaderVoiceAndAmbienceControllerTest.kt` | ReaderVoiceAndAmbienceController | ✅ REVISADO | 8 tests: sleep timer (countdown, expiración, fin cap, presets inteligentes), ambientes (presets, volumen, visibilidad), voces Sherpa/Edge, léxico fonético y ciclo de vida |
| `SaveReadingProgressUseCaseTest.kt` | SaveReadingProgressUseCase | ✅ REVISADO | 12 tests: debounce 800ms, debounce TTS 10s, force=true, bookId vacío (no-op), checkpoint síncrono SharedPreferences y persistencia Room |
| `CalculateReadingStatsUseCaseTest.kt` | CalculateReadingStatsUseCase | ✅ REVISADO | 18 tests: cálculo de WPM activa con límites [80, 500], delta de palabras ignorando saltos >10%, ETF libro y capítulo, racha con día de gracia y persistencia de sesiones/stats |
| `LoadBookFastUseCaseTest.kt` | LoadBookFastUseCase | ✅ REVISADO | 8 tests: cascada de 3 niveles de resolución de locator (Nivel 1 CFI, Nivel 2 fallback href, Nivel 3 inicio), fast-path sintético para libros extensos (>80 capítulos) y aplanado recursivo de TOC |
| `ManageReaderAnnotationsUseCaseTest.kt` | ManageReaderAnnotationsUseCase | ✅ REVISADO | 12 tests: coincidencia de marcadores en EPUB y PDF/Reflow, toggleBookmark, generación de etiquetas automáticas, exportación a Markdown/TXT, formato de citas y decoraciones Readium |
| `M4aMetadataEmbedderTest.kt` | M4aMetadataEmbedder | ✅ REVISADO | 4 tests binarios puros: síntesis de estructuras MP4 con moov antes y después de mdat, preservación byte a byte de datos de audio mdat, conversión a átomo free y lectura/parseo inverso de átomos ©nam, ©ART, ©alb, ©lyr y covr |
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
| `SleepTimerManagerTest.kt` | SleepTimerManager | ✅ REVISADO | 5 tests: countdown, expiración, fin de capítulo (-1), duración personalizada (20 min) y cancelación con restauración de velocidad |
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
> *"Consulta el documento ancla `dona.md`. Revisa el Estado Actual (Sección 0.0), el Mapa de Capas (Sección 0.1), los Invariantes e Intocables (Sección 0.2), las Decisiones de Arquitectura (Sección 0.3), el Glosario de Términos (Sección 0.4), las Reglas de Trabajo (Sección 0.5) y el Mapa de Conexiones (Sección 0.9) antes de proponer cualquier cambio al código."*

### Protocolo de Modificación de Archivos
Antes de alterar cualquier componente:
1. **Comprobar Intocables:** Verifica que el archivo objetivo no forme parte de los invariantes del lector (Sección 0.2: `ChapterScrollDetector.kt`, CSS de `ReaderExtensions.kt`, `AudioFocus` en `TtsManager.kt`, firmas públicas de `ReaderViewModel.kt`).
2. **Consultar Entrada Atómica:** Localiza la ruta del archivo en las secciones 0 a 12 del índice para conocer sus dependencias ascendentes (`depende_de`) y descendentes (`usado_por`).
3. **Revisar Registro Consolidado (Sección 14):** Consulta la Matriz Consolidada de Hallazgos y la subsección de Riesgos en Dispositivo para no reintroducir bugs ya mitigados ni pasar por alto condiciones de carrera de hardware.
4. **Respetar la Leyenda de Estados:**
   - ✅ **Resuelto / Verificado en código:** El cambio cuenta con pruebas unitarias en verde y revisión estricta de código.
   - 🟡 **Resuelto en código, pendiente de validación en dispositivo (TECNO-LH7n):** Requiere comprobación física en hardware antes de promoverse a ✅.
   - 🔴 **Pendiente:** Deuda técnica identificada que debe abordarse bajo un plan dedicado.
   - ⚪ **No verificado:** Estado preventivo de componentes no auditados.
5. **Verificación Continua:** Ejecutar obligatoriamente `.\gradlew.bat :app:compileDebugKotlin` y `.\gradlew.bat :app:testDebugUnitTest` tras cada fase de trabajo.

---

*`dona.md` es un documento vivo de alta fidelidad mantenido bajo control de versiones.*