# Woods Reader — Arquitectura actual

## Producto y build
Woods Reader es una aplicación Android en Kotlin para lectura EPUB/PDF, biblioteca, anotaciones, búsqueda y narración TTS local. El proyecto contiene los módulos Gradle `:app` y `:keymaker`. La configuración de `app/build.gradle.kts` declara `versionName = "1.0.10"` y `versionCode = 11`.

## Entrada y presentación
- `app/src/main/AndroidManifest.xml` y `MainActivity.kt`: entrada Android, apertura de contenido externo y arranque de la aplicación.
- `ui/navigation/NavGraph.kt`: destinos de biblioteca, lector, ficha, búsqueda, estadísticas, ajustes, onboarding y grafo.
- `ui/library/`, `ui/reader/`, `ui/settings/`, `ui/search/`, `ui/stats/`, `ui/graph/`: pantallas Compose y sus ViewModels.
- `ui/reader/ReaderViewModel.kt` coordina flujos del lector y delega responsabilidades también a `domain/reader/`.

## Dominio y lectura
- `domain/reader/`: carga rápida, progreso, anotaciones, navegación de capítulos e inteligencia/exportación del lector.
- `core/pipeline/`: extracción y procesamiento de bloques de lectura, limpieza de EPUB/PDF, clasificación de ruido, formato y unidades para TTS.
- `core/pdf/PdfParagraphHealer.kt`: limpieza de párrafos y detección de encabezados/capítulos PDF.
- `core/pdf/PdfCleanExtractorAndConverter.kt`: extracción/curación y conversión PDF a EPUB.
- `core/managers/PdfReflowManager.kt` y `PdfBookViewSynchronizer.kt`: gestión del texto PDF reflow y sincronización de posiciones.

## Datos y DI
Room, DAOs y repositorios se encuentran en `data/database/` y `data/repository/`; preferencias en `data/datastore/`. El grafo usa `data/graph/` y el escaneo de biblioteca `data/scanner/`. Los módulos de Hilt están en `di/`.

## Audio, servicios y trabajo en segundo plano
- `core/tts/TtsManager.kt` orquesta Sherpa ONNX y el TTS de Android; `service/ReaderTtsService.kt` integra reproducción de fondo y MediaSession.
- `core/tts/ChapterAudioExporter.kt` exporta audio; `M4aMetadataEmbedder` integra metadatos de audio.
- `worker/` y `work/` contienen tareas de WorkManager. `BookScannerForegroundService` permanece declarado en el manifest, pero no se encontró un punto de inicio desde código en esta revisión; tratarlo como ruta heredada hasta verificar todos sus consumidores, no como flujo activo confirmado.

## Flujos principales
1. Apertura: intent/selección → metadatos y repositorio → Room → destino lector.
2. Lectura: `ReaderViewModel`/coordinadores → Readium para publicación y navegación; el procesamiento de contenido alimenta el modo reflow y las unidades de narración.
3. Audio: `TtsManager` → Sherpa/Android TTS → `ReaderTtsService` cuando la reproducción debe continuar en segundo plano.
4. PDF: extracción y limpieza → reflow/búsqueda/TOC virtual; conversión mediante `PdfCleanExtractorAndConverter` y flujo de biblioteca.

## Invariantes de mantenimiento
- EPUB y PDF son los formatos documentados como soportados por la app.
- Mantener `androidx.concurrent:concurrent-futures` y `concurrent-futures-ktx`; se declararon para resolver el conflicto de dependencias de `lintDebug`.
- Cambios Room requieren migración y sincronización del esquema.
- I/O, procesamiento de archivos e inferencia deben ejecutarse fuera del hilo principal.
- El contrato público de `ReaderScreen`, el CSS de paginación y el manejo de AudioFocus requieren pruebas cuidadosas antes de modificarse.
- Consultar `dona.md` para el índice de archivos y la evidencia detallada. Los documentos históricos están en `docs/archive/`.
