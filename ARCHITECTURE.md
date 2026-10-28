# Woods Reader — Arquitectura (referencia rápida)

## Build
build.gradle.kts — compileSdk 36, minSdk 26, targetSdk 35, versionCode 11, noCompress onnx/txt
settings.gradle.kts — módulos :app, :keymaker, JitPack (Sherpa ONNX)
gradle.properties — JVM -Xmx4096m, AndroidX, Compose, JDK Android Studio

## Entrada / Navegación
LectorApplication.kt — @HiltAndroidApp, Coil3, PDFBox, Timber, limpieza memoria
MainActivity.kt — punto de entrada, intents ACTION_VIEW, permisos almacenamiento, WorkManager LibrarySyncWorker (12h)
NavGraph.kt — sealed Screen (library, reader/{bookId}, book_detail/{bookId}, graph/{bookId}?chapterIndex, search, stats, settings, onboarding), LectorNavHost

## Datos (Room + DataStore)
data/database/AppDatabase.kt — Room v11, AutoMigration 10→11, exportSchema app/schemas/
data/database/Entities.kt — BookEntity, HighlightEntity, BookmarkEntity, ReadingSessionEntity, CollectionEntity, TtsRuleEntity, DailyWordCount
data/database/Daos.kt — BookDao, HighlightDao, BookmarkDao, ReadingSessionDao, CollectionDao, ReadingStatsDao, TtsRuleDao + proyecciones
data/database/migration/Migrations.kt — MIGRATION_2_3 … MIGRATION_9_10
data/database/AppTypeConverters.kt — TypeConverters Room
data/database/BookFtsEntity.kt — búsqueda FTS4
data/datastore/UserPreferences.kt — modelo prefs
data/datastore/UserPreferencesRepository.kt — DataStore prefs
data/datastore/UserPreferencesSerializer.kt — serializador DataStore
data/repository/BookRepository.kt — @Singleton single source of truth libros
data/repository/GraphRepository.kt — @Singleton grafo entidades
data/scanner/MediaStoreBookScanner.kt — escaneo MediaStore
data/scanner/DeepFileSystemScanner.kt — escaneo FS profundo
data/model/Enums.kt — BookFormat, ReadingStatus, HighlightColor, ReadingMode

## DI (Hilt, SingletonComponent)
di/CoreModule.kt — AppDatabase
di/DatabaseModule.kt — instancia Room
di/TtsModule.kt — TtsManager, SherpaTtsEngine, SherpaTtsEngineProvider, modelos Sherpa
di/ScannerModule.kt — escáneres archivos
di/LibraryModule.kt — Readium (PublicationOpener, AssetRetriever, PdfiumDocumentFactory, DefaultHttpClient)
di/DataStoreModule.kt — DataStore

## Core — Pipeline lectura
core/pipeline/ReadingPipeline.kt — @Singleton ProcessedDocument (displayBlocks, ttsNarrationUnits, metrics)
core/pipeline/ReadingBlock.kt — ReadingBlock, RawDocumentBlock, TtsNarrationUnit
core/pipeline/EpubContentExtractor.kt — extrae HTML EPUB
core/pipeline/EpubHtmlSanitizer.kt / EpubPackageSanitizer.kt — sanitización
core/pipeline/PdfBookViewSynchronizer.kt — orden lectura PDF
core/pipeline/NoiseClassifier.kt — @Singleton encabezados/pies repetitivos
core/pipeline/ImageAnnouncer.kt — anuncios imagen TTS
core/pipeline/WordExclusionEngine.kt — filtro palabras TTS
core/pipeline/SpanishTextFormatter.kt — formato tipográfico ES

## Core — TTS
core/tts/TtsManager.kt — @Singleton Sherpa ONNX offline + Android TTS nativo
core/tts/SherpaTtsEngine.kt — adaptador Readium TtsEngine
core/tts/SherpaTtsEngineProvider.kt — proveedor Readium TtsEngineProvider
core/tts/SherpaModelManager.kt — descarga/extracción modelos ONNX (mutex, tar.bz2)
core/tts/TtsPresetCatalog.kt — presets TtsPresetPack
core/tts/ChapterAudioExporter.kt — export M4A capítulo
core/tts/AudiobookProsodyEngine.kt / SpanishAudiobookNormalizer.kt / MultilingualAudiobookNormalizer.kt — prosodia
core/tts/StudioAudioDspProcessor.kt — DSP estudio
core/tts/PronunciationLexiconManager.kt — lexicón pronunciación
core/tts/SmartSleepTimerManager.kt — temporizador sueño
core/tts/AmbientSoundscapeEngine.kt / AmbientSoundscapePlayer.kt / ForestAmbienceEngine.kt — paisajes sonoros
core/tts/EdgeNeuralTtsClient.kt — cliente Edge Neural

## Core — Gestores
core/managers/ReadingProgressManager.kt — progreso lectura
core/managers/ReadingSessionManager.kt — sesiones lectura
core/managers/SleepTimerManager.kt — temporizador sueño
core/managers/PdfReflowManager.kt — reflujo PDF, deduplicación, ruido global
core/managers/BackupManager.kt / core/backup/WoodsBackupManager.kt — respaldo
core/managers/HighlightExporter.kt — exportar resaltados
core/managers/QuoteCardGenerator.kt — tarjetas cita
core/managers/InlineLookupManager.kt — búsqueda inline
core/managers/WoodsSupporterManager.kt — Pro
core/managers/WoodsKeepAliveManager.kt / core/system/WoodsKeepAliveManager.kt — keep-alive
core/managers/TimeProvider.kt / SystemTimeProvider.kt — tiempo
core/search/FastEpubSearchEngine.kt — búsqueda rápida EPUB (LruCache)
core/xray/BookXRayEngine.kt — entidades libro
core/share/QuoteVideoClipExporter.kt — clips video citas
core/license/ProLicenseManager.kt — licencia ECDSA

## Core — Web2EPUB + Traducción
core/web2epub/WebToEpubImporterDialog.kt
core/web2epub/LocalEpubBuilder.kt
core/web2epub/novel/SiteProfile.kt, SiteProfileLoader.kt, PageFetcher.kt, OkHttpPageFetcher.kt, RequestPacer.kt, RobotsAdvisor.kt, ChallengeDetector.kt, CharsetDetector.kt
core/translation/calibre/CalibreStyleEpubTranslator.kt, GoogleFreeNetworkTranslator.kt, inplace/TextTranslator.kt

## Servicios / Workers
service/ReaderTtsService.kt — TTS foreground, MediaSession, notificación
service/BookScannerService.kt — escaneo biblioteca, hash, limpieza huérfanos
service/BookScannerForegroundService.kt
service/BookMetadataExtractor.kt — metadatos libro
service/CoverExtractor.kt — portadas
service/TextRankSummarizer.kt
service/TtsMediaNotificationProvider.kt
service/RecursiveFileObserver.kt
worker/LibrarySyncWorker.kt — sync cada 12h
work/GraphExtractionWorker.kt — extracción grafo

## UI (Compose Material 3)
ui/theme/Theme.kt, Color.kt, Type.kt — HiddenForest
ui/navigation/NavGraph.kt — (ver Entrada)
ui/library/LibraryScreen.kt, LibraryViewModel.kt, LibraryHeader.kt, LibraryComponents.kt, ContinueReadingHero.kt, CalibreTranslationProgressDialog.kt, BookOptionsBottomSheet.kt
ui/bookdetail/BookDetailScreen.kt, BookDetailViewModel.kt
ui/settings/SettingsScreen.kt, SettingsViewModel.kt
ui/onboarding/OnboardingScreen.kt, OnboardingViewModel.kt
ui/search/SearchScreen.kt
ui/stats/ReadingStatsScreen.kt, StatsViewModel.kt
ui/permissions/PermissionsScreen.kt
ui/common/WoodsProUnlockDialog.kt
ui/reader/ReaderScreen.kt, ReaderViewModel.kt, ReaderContract.kt (ReaderTab, ReaderUiState, NarrationHighlight, ReaderState), TtsState.kt, EpubFormatManager.kt + 25 componentes UI/overlays
ui/graph/GraphScreen.kt, GraphViewModel.kt, GraphCanvas.kt, ForceLayout.kt, EntitySheet.kt, CandidatesScreen.kt

## Grafo (Room)
data/graph/GraphEntities.kt — GraphEntity, GraphAlias, GraphRelation, GraphMention, EntityType
data/graph/GraphDao.kt — DAO grafo
data/graph/GraphJson.kt — export/import JSON
data/graph/GraphPositionsCache.kt — caché posiciones

## Utilidades
util/EntityHeuristics.kt — heurísticas entidades

## Modelos UI
model/ReaderTheme.kt, ScrollMode.kt, PageTurnStyle.kt, TtsHighlightStyle.kt, TtsUiStyle.kt

## Keymaker (módulo aparte)
keymaker/MainActivity.kt, KeymakerTheme.kt, KeymakerEngine.kt, KeyHistoryManager.kt

## Flujo datos rápido
Abrir archivo: MainActivity.handleIncomingIntent → BookMetadataExtractor → BookRepository → Room books → Screen.Reader
Leer: ReaderViewModel → EpubFormatManager → ReadingPipeline → ReaderScreen
TTS: TtsManager → SherpaTtsEngine → ReaderTtsService
Progreso: BookDao.updateProgress + ReadingSessionDao.insert
Buscar: BookDao.searchBooks (FTS4 books_fts)

## Reglas IA
Ver .cursorrules (8 reglas). Índice archivos: FILE_INDEX.md.