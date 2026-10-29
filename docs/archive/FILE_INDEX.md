# Woods Reader — Índice de archivos

Formato: `ruta` — clases principales | usado por

## Raíz
`build.gradle.kts` — plugins compartidos | todos
`settings.gradle.kts` — módulos :app, :keymaker | Gradle
`gradle.properties` — JVM, AndroidX, Compose | Gradle
`.cursorrules` — reglas IA | asistentes
`ARCHITECTURE.md` — mapa arquitectura | asistentes
`FILE_INDEX.md` — este índice | asistentes

## Entrada / Navegación
`app/src/main/java/com/example/woodsreader/LectorApplication.kt` — LectorApplication | DI root
`app/src/main/java/com/example/woodsreader/MainActivity.kt` — MainActivity, MainLayout | LectorApplication
`app/src/main/java/com/example/woodsreader/ui/navigation/NavGraph.kt` — Screen (sealed), LectorNavHost | MainActivity

## Datos
`app/src/main/java/com/example/woodsreader/data/database/AppDatabase.kt` — AppDatabase | DI, Repository
`app/src/main/java/com/example/woodsreader/data/database/Entities.kt` — BookEntity, HighlightEntity, BookmarkEntity, ReadingSessionEntity, CollectionEntity, TtsRuleEntity | Daos
`app/src/main/java/com/example/woodsreader/data/database/Daos.kt` — BookDao, HighlightDao, BookmarkDao, ReadingSessionDao, CollectionDao, ReadingStatsDao, TtsRuleDao | Repository
`app/src/main/java/com/example/woodsreader/data/database/migration/Migrations.kt` — migraciones 2→3…9→10 | AppDatabase
`app/src/main/java/com/example/woodsreader/data/database/AppTypeConverters.kt` — AppTypeConverters | AppDatabase
`app/src/main/java/com/example/woodsreader/data/database/BookFtsEntity.kt` — BookFtsEntity | búsqueda FTS
`app/src/main/java/com/example/woodsreader/data/datastore/UserPreferences.kt` — UserPreferences | DataStore
`app/src/main/java/com/example/woodsreader/data/datastore/UserPreferencesRepository.kt` — UserPreferencesRepository | MainActivity, ViewModels
`app/src/main/java/com/example/woodsreader/data/datastore/UserPreferencesSerializer.kt` — UserPreferencesSerializer | DataStore
`app/src/main/java/com/example/woodsreader/data/repository/BookRepository.kt` — BookRepository (@Singleton) | LibraryViewModel, BookDetailViewModel, ReaderViewModel, MainActivity
`app/src/main/java/com/example/woodsreader/data/repository/GraphRepository.kt` — GraphRepository (@Singleton) | GraphViewModel
`app/src/main/java/com/example/woodsreader/data/scanner/MediaStoreBookScanner.kt` — MediaStoreBookScanner | BookScannerService
`app/src/main/java/com/example/woodsreader/data/scanner/DeepFileSystemScanner.kt` — DeepFileSystemScanner | BookScannerService
`app/src/main/java/com/example/woodsreader/data/model/Enums.kt` — BookFormat, ReadingStatus, HighlightColor, ReadingMode | Entities, UI
`app/src/main/java/com/example/woodsreader/data/model/BookScanResult.kt` — BookScanResult | Scanner
`app/src/main/java/com/example/woodsreader/data/model/ReflowItem.kt` — ReflowItem | PdfReflowManager

## DI
`app/src/main/java/com/example/woodsreader/di/CoreModule.kt` — AppDatabase | SingletonComponent
`app/src/main/java/com/example/woodsreader/di/DatabaseModule.kt` — Room instance | SingletonComponent
`app/src/main/java/com/example/woodsreader/di/TtsModule.kt` — TtsManager, SherpaTtsEngine, SherpaTtsEngineProvider | SingletonComponent
`app/src/main/java/com/example/woodsreader/di/ScannerModule.kt` — escáneres | SingletonComponent
`app/src/main/java/com/example/woodsreader/di/LibraryModule.kt` — Readium PublicationOpener, AssetRetriever, PdfiumDocumentFactory | SingletonComponent
`app/src/main/java/com/example/woodsreader/di/DataStoreModule.kt` — DataStore | SingletonComponent

## Core Pipeline
`app/src/main/java/com/example/woodsreader/core/pipeline/ReadingPipeline.kt` — ReadingPipeline, ProcessedDocument | EpubFormatManager, PdfReflowManager
`app/src/main/java/com/example/woodsreader/core/pipeline/ReadingBlock.kt` — ReadingBlock, RawDocumentBlock, TtsNarrationUnit | ReadingPipeline
`app/src/main/java/com/example/woodsreader/core/pipeline/EpubContentExtractor.kt` — EpubContentExtractor | EpubFormatManager
`app/src/main/java/com/example/woodsreader/core/pipeline/EpubHtmlSanitizer.kt` — EpubHtmlSanitizer | ReadingPipeline
`app/src/main/java/com/example/woodsreader/core/pipeline/EpubPackageSanitizer.kt` — EpubPackageSanitizer | ReadingPipeline
`app/src/main/java/com/example/woodsreader/core/pipeline/PdfBookViewSynchronizer.kt` — PdfBookViewSynchronizer | ReadingPipeline, ReaderViewModel
`app/src/main/java/com/example/woodsreader/core/pipeline/NoiseClassifier.kt` — NoiseClassifier (@Singleton) | ReadingPipeline, PdfReflowManager
`app/src/main/java/com/example/woodsreader/core/pipeline/ImageAnnouncer.kt` — ImageAnnouncer | ReadingPipeline
`app/src/main/java/com/example/woodsreader/core/pipeline/WordExclusionEngine.kt` — WordExclusionEngine, WordExclusionRule | ReadingPipeline
`app/src/main/java/com/example/woodsreader/core/pipeline/SpanishTextFormatter.kt` — SpanishTextFormatter | ReadingPipeline

## Core TTS
`app/src/main/java/com/example/woodsreader/core/tts/TtsManager.kt` — TtsManager (@Singleton) | SherpaTtsEngine, ReaderViewModel
`app/src/main/java/com/example/woodsreader/core/tts/SherpaTtsEngine.kt` — SherpaTtsEngine (= SherpaOnnxTtsEngine) | SherpaTtsEngineProvider, Readium
`app/src/main/java/com/example/woodsreader/core/tts/SherpaTtsEngineProvider.kt` — SherpaTtsEngineProvider | Readium
`app/src/main/java/com/example/woodsreader/core/tts/SherpaModelManager.kt` — SherpaModelManager | TtsManager
`app/src/main/java/com/example/woodsreader/core/tts/TtsPresetCatalog.kt` — TtsPresetCatalog, TtsPresetPack | TtsManager, ReaderViewModel
`app/src/main/java/com/example/woodsreader/core/tts/ChapterAudioExporter.kt` — ChapterAudioExporter | ReaderViewModel
`app/src/main/java/com/example/woodsreader/core/tts/AudiobookProsodyEngine.kt` — AudiobookProsodyEngine | TtsManager
`app/src/main/java/com/example/woodsreader/core/tts/SpanishAudiobookNormalizer.kt` — SpanishAudiobookNormalizer | TtsManager
`app/src/main/java/com/example/woodsreader/core/tts/MultilingualAudiobookNormalizer.kt` — MultilingualAudiobookNormalizer | TtsManager
`app/src/main/java/com/example/woodsreader/core/tts/StudioAudioDspProcessor.kt` — StudioAudioDspProcessor | TtsManager
`app/src/main/java/com/example/woodsreader/core/tts/PronunciationLexiconManager.kt` — PronunciationLexiconManager | SherpaTtsEngine, TtsManager
`app/src/main/java/com/example/woodsreader/core/tts/SmartSleepTimerManager.kt` — SmartSleepTimerManager | TtsManager, ReaderViewModel
`app/src/main/java/com/example/woodsreader/core/tts/AmbientSoundscapeEngine.kt` — AmbientSoundscapeEngine | TtsManager
`app/src/main/java/com/example/woodsreader/core/tts/AmbientSoundscapePlayer.kt` — AmbientSoundscapePlayer | TtsManager
`app/src/main/java/com/example/woodsreader/core/tts/ForestAmbienceEngine.kt` — ForestAmbienceEngine | TtsManager
`app/src/main/java/com/example/woodsreader/core/tts/EdgeNeuralTtsClient.kt` — EdgeNeuralTtsClient | TtsManager

## Core Gestores
`app/src/main/java/com/example/woodsreader/core/managers/ReadingProgressManager.kt` — ReadingProgressManager | ReaderViewModel
`app/src/main/java/com/example/woodsreader/core/managers/ReadingSessionManager.kt` — ReadingSessionManager | ReaderViewModel
`app/src/main/java/com/example/woodsreader/core/managers/SleepTimerManager.kt` — SleepTimerManager | ReaderViewModel
`app/src/main/java/com/example/woodsreader/core/managers/PdfReflowManager.kt` — PdfReflowManager | ReaderViewModel
`app/src/main/java/com/example/woodsreader/core/managers/BackupManager.kt` — BackupManager | WoodsBackupManager
`app/src/main/java/com/example/woodsreader/core/managers/HighlightExporter.kt` — HighlightExporter | ReaderViewModel
`app/src/main/java/com/example/woodsreader/core/managers/QuoteCardGenerator.kt` — QuoteCardGenerator | ReaderViewModel
`app/src/main/java/com/example/woodsreader/core/managers/InlineLookupManager.kt` — InlineLookupManager, LookupResult | ReaderScreen
`app/src/main/java/com/example/woodsreader/core/managers/WoodsSupporterManager.kt` — WoodsSupporterManager | UI Pro
`app/src/main/java/com/example/woodsreader/core/managers/WoodsKeepAliveManager.kt` — WoodsKeepAliveManager | TtsManager
`app/src/main/java/com/example/woodsreader/core/system/WoodsKeepAliveManager.kt` — WoodsKeepAliveManager | (dup)
`app/src/main/java/com/example/woodsreader/core/managers/TimeProvider.kt` — TimeProvider | SystemTimeProvider
`app/src/main/java/com/example/woodsreader/core/managers/SystemTimeProvider.kt` — SystemTimeProvider | ReadingSessionManager

## Core Búsqueda / X-Ray / Share / Backup / Licencia
`app/src/main/java/com/example/woodsreader/core/search/FastEpubSearchEngine.kt` — FastEpubSearchEngine | LectorApplication (limpieza), SearchScreen
`app/src/main/java/com/example/woodsreader/core/xray/BookXRayEngine.kt` — BookXRayEngine | ReaderXRayBottomSheet
`app/src/main/java/com/example/woodsreader/core/share/QuoteVideoClipExporter.kt` — QuoteVideoClipExporter | ReaderScreen
`app/src/main/java/com/example/woodsreader/core/backup/WoodsBackupManager.kt` — WoodsBackupManager | Settings
`app/src/main/java/com/example/woodsreader/core/license/ProLicenseManager.kt` — ProLicenseManager | LibraryViewModel, WoodsProUnlockDialog

## Core Web2EPUB + Traducción
`app/src/main/java/com/example/woodsreader/core/web2epub/WebToEpubImporterDialog.kt` — WebToEpubImporterDialog | UI importación
`app/src/main/java/com/example/woodsreader/core/web2epub/LocalEpubBuilder.kt` — LocalEpubBuilder | WebToEpubImporterDialog
`app/src/main/java/com/example/woodsreader/core/web2epub/novel/SiteProfile.kt` — SiteProfile | SiteProfileLoader
`app/src/main/java/com/example/woodsreader/core/web2epub/novel/SiteProfileLoader.kt` — SiteProfileLoader | Web2epub
`app/src/main/java/com/example/woodsreader/core/web2epub/novel/PageFetcher.kt` — PageFetcher | OkHttpPageFetcher
`app/src/main/java/com/example/woodsreader/core/web2epub/novel/OkHttpPageFetcher.kt` — OkHttpPageFetcher | PageFetcher
`app/src/main/java/com/example/woodsreader/core/web2epub/novel/RequestPacer.kt` — RequestPacer | Web2epub
`app/src/main/java/com/example/woodsreader/core/web2epub/novel/RobotsAdvisor.kt` — RobotsAdvisor | Web2epub
`app/src/main/java/com/example/woodsreader/core/web2epub/novel/ChallengeDetector.kt` — ChallengeDetector | Web2epub
`app/src/main/java/com/example/woodsreader/core/web2epub/novel/CharsetDetector.kt` — CharsetDetector | Web2epub
`app/src/main/java/com/example/woodsreader/core/translation/calibre/CalibreStyleEpubTranslator.kt` — CalibreStyleEpubTranslator | LibraryViewModel
`app/src/main/java/com/example/woodsreader/core/translation/calibre/GoogleFreeNetworkTranslator.kt` — GoogleFreeNetworkTranslator | Calibre translator
`app/src/main/java/com/example/woodsreader/core/translation/calibre/inplace/TextTranslator.kt` — TextTranslator | Calibre translator

## Servicios / Workers
`app/src/main/java/com/example/woodsreader/service/ReaderTtsService.kt` — ReaderTtsService | MainActivity, EpubFormatManager
`app/src/main/java/com/example/woodsreader/service/BookScannerService.kt` — BookScannerService | LibraryViewModel, MainActivity
`app/src/main/java/com/example/woodsreader/service/BookScannerForegroundService.kt` — BookScannerForegroundService | BookScannerService
`app/src/main/java/com/example/woodsreader/service/BookMetadataExtractor.kt` — BookMetadataExtractor | MainActivity
`app/src/main/java/com/example/woodsreader/service/CoverExtractor.kt` — CoverExtractor | BookScannerService
`app/src/main/java/com/example/woodsreader/service/TextRankSummarizer.kt` — TextRankSummarizer | Library/Search
`app/src/main/java/com/example/woodsreader/service/TtsMediaNotificationProvider.kt` — TtsMediaNotificationProvider | ReaderTtsService
`app/src/main/java/com/example/woodsreader/service/RecursiveFileObserver.kt` — RecursiveFileObserver | BookScannerService
`app/src/main/java/com/example/woodsreader/worker/LibrarySyncWorker.kt` — LibrarySyncWorker (@HiltWorker) | MainActivity
`app/src/main/java/com/example/woodsreader/work/GraphExtractionWorker.kt` — GraphExtractionWorker | WorkManager

## UI
`app/src/main/java/com/example/woodsreader/ui/theme/Theme.kt` — WoodsReaderTheme, HiddenForest | todas pantallas
`app/src/main/java/com/example/woodsreader/ui/theme/Color.kt` — paleta HiddenForest | Theme
`app/src/main/java/com/example/woodsreader/ui/theme/Type.kt` — tipografía HiddenForest | Theme
`app/src/main/java/com/example/woodsreader/ui/library/LibraryScreen.kt` — LibraryScreen | NavGraph
`app/src/main/java/com/example/woodsreader/ui/library/LibraryViewModel.kt` — LibraryViewModel (@HiltViewModel) | LibraryScreen
`app/src/main/java/com/example/woodsreader/ui/library/LibraryHeader.kt` — LibraryHeader | LibraryScreen
`app/src/main/java/com/example/woodsreader/ui/library/LibraryComponents.kt` — componentes UI | LibraryScreen
`app/src/main/java/com/example/woodsreader/ui/library/ContinueReadingHero.kt` — ContinueReadingHero | LibraryScreen
`app/src/main/java/com/example/woodsreader/ui/library/CalibreTranslationProgressDialog.kt` — CalibreTranslationProgressDialog | LibraryScreen
`app/src/main/java/com/example/woodsreader/ui/library/BookOptionsBottomSheet.kt` — BookOptionsBottomSheet | LibraryScreen
`app/src/main/java/com/example/woodsreader/ui/bookdetail/BookDetailScreen.kt` — BookDetailScreen | NavGraph
`app/src/main/java/com/example/woodsreader/ui/bookdetail/BookDetailViewModel.kt` — BookDetailViewModel (@HiltViewModel) | BookDetailScreen
`app/src/main/java/com/example/woodsreader/ui/settings/SettingsScreen.kt` — SettingsScreen | NavGraph
`app/src/main/java/com/example/woodsreader/ui/settings/SettingsViewModel.kt` — SettingsViewModel (@HiltViewModel) | SettingsScreen
`app/src/main/java/com/example/woodsreader/ui/onboarding/OnboardingScreen.kt` — OnboardingScreen | NavGraph
`app/src/main/java/com/example/woodsreader/ui/onboarding/OnboardingViewModel.kt` — OnboardingViewModel (@HiltViewModel) | OnboardingScreen
`app/src/main/java/com/example/woodsreader/ui/search/SearchScreen.kt` — SearchScreen | NavGraph
`app/src/main/java/com/example/woodsreader/ui/stats/ReadingStatsScreen.kt` — ReadingStatsScreen | NavGraph
`app/src/main/java/com/example/woodsreader/ui/stats/StatsViewModel.kt` — StatsViewModel (@HiltViewModel) | ReadingStatsScreen
`app/src/main/java/com/example/woodsreader/ui/permissions/PermissionsScreen.kt` — PermissionsScreen | MainActivity
`app/src/main/java/com/example/woodsreader/ui/common/WoodsProUnlockDialog.kt` — WoodsProUnlockDialog | UI

## UI Reader
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderScreen.kt` — ReaderScreen | NavGraph
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt` — ReaderViewModel (@HiltViewModel), ParsedChapterData, AutoScrollTeleprompterState | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderContract.kt` — ReaderTab, ReaderUiState, NarrationHighlight, ReaderState | ReaderViewModel, ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/TtsState.kt` — TtsState | UI reader
`app/src/main/java/com/example/woodsreader/ui/reader/EpubFormatManager.kt` — EpubFormatManager (@Singleton), NoOpTtsRuleDao | ReaderViewModel
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderUIComponents.kt` — componentes UI | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderScreenOverlays.kt` — overlays | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderBottomBar.kt` — ReaderBottomBar | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderTopBar.kt` — ReaderTopBar | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderBottomSlider.kt` — ReaderBottomSlider | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderSideMenu.kt` — ReaderSideMenu | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderMasterMenuBottomSheet.kt` — ReaderMasterMenuBottomSheet | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderAudioBottomSheet.kt` — ReaderAudioBottomSheet | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderTtsBar.kt` — ReaderTtsBar | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderTtsMacroWindow.kt` — ReaderTtsMacroWindow | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderTtsLockOverlay.kt` — ReaderTtsLockOverlay | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderTeleprompterPill.kt` — ReaderTeleprompterPill | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderFloatingTtsButton.kt` — ReaderFloatingTtsButton | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderHeroSplash.kt` — ReaderHeroSplash | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderNotesTab.kt` — ReaderNotesTab | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderReflowOverlay.kt` — ReaderReflowOverlay | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderEgoGraphBottomSheet.kt` — ReaderEgoGraphBottomSheet | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderXRayBottomSheet.kt` — ReaderXRayBottomSheet | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/TocBottomSheet.kt` — TocBottomSheet | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/SearchBottomSheet.kt` — SearchBottomSheet | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/TtsRulesBottomSheet.kt` — TtsRulesBottomSheet | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/EpubCoverPage.kt` — EpubCoverPage | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ChapterScrollDetector.kt` — ChapterScrollDetector | ReaderScreen
`app/src/main/java/com/example/woodsreader/ui/reader/ReaderExtensions.kt` — extensiones | Reader UI
`app/src/main/java/com/example/woodsreader/ui/reader/animation/BookPageTurnEffect.kt` — BookPageTurnEffect | ReaderScreen

## UI Grafo
`app/src/main/java/com/example/woodsreader/ui/graph/GraphScreen.kt` — GraphScreen | NavGraph
`app/src/main/java/com/example/woodsreader/ui/graph/GraphViewModel.kt` — GraphViewModel (@HiltViewModel) | GraphScreen
`app/src/main/java/com/example/woodsreader/ui/graph/GraphCanvas.kt` — GraphCanvas | GraphScreen
`app/src/main/java/com/example/woodsreader/ui/graph/ForceLayout.kt` — ForceLayout | GraphViewModel
`app/src/main/java/com/example/woodsreader/ui/graph/EntitySheet.kt` — EntitySheet | GraphScreen
`app/src/main/java/com/example/woodsreader/ui/graph/CandidatesScreen.kt` — CandidatesScreen | NavGraph

## Grafo (datos)
`app/src/main/java/com/example/woodsreader/data/graph/GraphEntities.kt` — GraphEntity, GraphAlias, GraphRelation, GraphMention, EntityType | GraphDao
`app/src/main/java/com/example/woodsreader/data/graph/GraphDao.kt` — GraphDao | GraphRepository
`app/src/main/java/com/example/woodsreader/data/graph/GraphJson.kt` — GraphJson | GraphRepository
`app/src/main/java/com/example/woodsreader/data/graph/GraphPositionsCache.kt` — GraphPositionsCache | GraphViewModel

## Utilidades
`app/src/main/java/com/example/woodsreader/util/EntityHeuristics.kt` — EntityHeuristics | Graph, X-Ray

## Modelos UI
`app/src/main/java/com/example/woodsreader/model/ReaderTheme.kt` — ReaderTheme | ReaderViewModel
`app/src/main/java/com/example/woodsreader/model/ScrollMode.kt` — ScrollMode | ReaderViewModel
`app/src/main/java/com/example/woodsreader/model/PageTurnStyle.kt` — PageTurnStyle | ReaderViewModel
`app/src/main/java/com/example/woodsreader/model/TtsHighlightStyle.kt` — TtsHighlightStyle | Reader UI
`app/src/main/java/com/example/woodsreader/model/TtsUiStyle.kt` — TtsUiStyle | Reader UI

## Keymaker
`keymaker/src/main/java/com/example/woodsreader/keymaker/MainActivity.kt` — MainActivity | keymaker
`keymaker/src/main/java/com/example/woodsreader/keymaker/KeymakerTheme.kt` — KeymakerTheme | keymaker
`keymaker/src/main/java/com/example/woodsreader/keymaker/KeymakerEngine.kt` — KeymakerEngine | keymaker
`keymaker/src/main/java/com/example/woodsreader/keymaker/KeyHistoryManager.kt` — KeyHistoryManager | keymaker

## Tests (app/src/test/)
`ExampleUnitTest.kt` — prueba básica
`EntityHeuristicsTest.kt` — EntityHeuristics
`ReaderViewModelTest.kt` — ReaderViewModel
`ForceLayoutTest.kt` — ForceLayout
`BookScannerServiceExtremeTest.kt` — BookScannerService
`TextRankSummarizerTest.kt` — TextRankSummarizer
`CoverExtractorTest.kt` — CoverExtractor
`LibraryViewModelExtremeTest.kt` — LibraryViewModel
`WordExclusionEngineTest.kt` — WordExclusionEngine
`SpanishTextFormatterTest.kt` — SpanishTextFormatter
`ReadingPipelineTest.kt` — ReadingPipeline
`ReadingPipelineBddTest.kt` — ReadingPipeline (BDD)
`PdfSemanticExtractionTest.kt` — extracción semántica PDF
`PdfBookViewSynchronizerTest.kt` — PdfBookViewSynchronizer
`NoiseClassifierTest.kt` — NoiseClassifier
`ImageAnnouncerTest.kt` — ImageAnnouncer
`EpubPackageSanitizerTest.kt` — EpubPackageSanitizer
`EpubHtmlSanitizerTest.kt` — EpubHtmlSanitizer
`EpubContentExtractorTest.kt` — EpubContentExtractor
`DeepFileSystemScannerTest.kt` — DeepFileSystemScanner
`SleepTimerManagerTest.kt` — SleepTimerManager
`ReadingSessionManagerTest.kt` — ReadingSessionManager
`ReadingProgressManagerTest.kt` — ReadingProgressManager
`ProLicenseManagerTest.kt` — ProLicenseManager
`GraphPositionsCacheTest.kt` — GraphPositionsCache
`GraphJsonTest.kt` — GraphJson
`SiteProfileRegistryTest.kt` — SiteProfile
`RobotsAdvisorTest.kt` — RobotsAdvisor
`RequestPacerTest.kt` — RequestPacer
`PageFetcherTest.kt` — PageFetcher
`Fixtures.kt` — fixtures web2epub
`FakePageFetcher.kt` — fake PageFetcher
`CharsetDetectorTest.kt` — CharsetDetector
`ChallengeDetectorTest.kt` — ChallengeDetector
`TestEpubFactory.kt` — EPUB factory tests
`FakeTextTranslator.kt` — fake translator

## Tests instrumentados (app/src/androidTest/)
`ExampleInstrumentedTest.kt` — prueba básica instrumentada
`LibraryScreenE2ETest.kt` — LibraryScreen E2E