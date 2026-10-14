# 📝 Notas de Versión - Woods Reader

Registro detallado de cambios, correcciones de errores, arquitectura y mejoras de rendimiento implementadas en Woods Reader.

---

## 🚀 Versión 1.0.9 (Build 10) — Octubre 2026

### 📌 Resumen de la Versión
Esta versión se enfoca en la **estabilidad del procesamiento de documentos EPUB y PDF, prevención de condiciones de carrera y duplicación de datos, robustez del motor TTS y resolución de dependencias del proyecto**, corrigiendo 8 defectos identificados en el procesamiento de texto y audio, y desbloqueando el análisis estático de Android Lint (`lintDebug`).

---

### 🛠️ Correcciones en Procesamiento de Documentos, TTS y Concurrencia

#### 1. Extracción de Contenido EPUB sin Duplicación en Elementos Contenedores
* **Problema anterior**: Elementos como `<blockquote>` o `<li>` que contienen párrafos hijos duplicaban su texto: se extraía el texto acumulado del contenedor y luego individualmente el de sus párrafos hijos, provocando lectura y narración TTS duplicada (`EpubContentExtractor.kt:32, 87-89`).
* **Solución aplicada**:
  * Se implementó detección de contenedores con elementos hijos en bloque (`element.children().select("h1, h2, h3, h4, h5, h6, p, figure, img, blockquote, li").isNotEmpty()`).
  * Para elementos contenedores, ahora solo se extrae el texto directo propio (`ownText()`) y se delega la extracción estructurada a los hijos en iteraciones subsecuentes, evitando duplicación y marcando ancestros procesados (`element.parents().any { it in processedElements }`).

#### 2. Deduplicación Estricta en Precarga y Carga de PDF
* **Problema anterior**: Si la lectura normal solicitaba una página mientras la corrutina de precarga en segundo plano la procesaba concurrentemente, ambas rutas podían agregar la misma página a la lista y a las unidades de narración, mostrándola y narrándola dos veces (`PdfReflowManager.kt:130-136, 245-263`).
* **Solución aplicada**:
  * Se implementó deduplicación atómica en `_reflowState` filtrando por `targetPage` (`filterNot { it.pageNumber == targetPage }`).
  * Se deduplicaron las unidades de narración TTS en `_ttsNarrationUnits` filtrando por `pageIndex` (`filterNot { it.pageIndex == pageIndex }`).
  * En `loadRangeInBackground`, se valida que la página no exista previamente en memoria antes de procesarla.

#### 3. Detección Global de Encabezados y Pies de Página Recurrentes en PDF
* **Problema anterior**: El clasificador de ruido (`NoiseClassifier`) requiere comparar múltiples páginas para identificar patrones repetitivos. Al procesar página por página de forma aislada, el detector carecía de contexto y los encabezados/pies permanecían en el texto y en la narración (`PdfReflowManager.kt:299-303`).
* **Solución aplicada**:
  * Se inyectó `NoiseClassifier` como `@Singleton` en `PdfReflowManager`.
  * Se implementó `sampleDocumentHeadersFooters()` y `updateRepetitiveHeadersFooters()`, que muestrean hasta 7 páginas al cargar el PDF para construir un mapa global de patrones repetitivos (`knownRepetitiveHeadersFooters`).
  * Este conjunto se transmite a `readingPipeline.process()`, permitiendo al pipeline suprimir con precisión encabezados y pies de página recurrentes en todo el documento.

#### 4. Cancelación de Precarga y Limpieza de Recursos al Cerrar el Lector
* **Problema anterior**: Tras cerrar el lector (`onCleared`), cambiar de modo o saltar de página, la corrutina de fondo de precarga seguía en ejecución y podía reinsertar páginas o imágenes en el estado tras la limpieza (`PdfReflowManager.kt:245-265, 600-610`).
* **Solución aplicada**:
  * Se asoció la tarea de precarga a `preloadJob: Job?`.
  * En `onCleared()`, `setReflowMode()` y `jumpToPage()`, se invoca inmediatamente `preloadJob?.cancel()` y se limpian las cachés de límites.
  * Dentro del bucle de precarga se evalúa `!_isReflowMode.value || currentDocument == null || document !== currentDocument` antes de cada inserción, impidiendo la reactivación de datos tras el cierre.

#### 5. Protección contra Zip Slip y Enlaces Simbólicos en Modelos TTS
* **Problema anterior**: La validación de rutas al extraer el archivo tar.bz2 del modelo TTS usaba `startsWith(canonicalBasePath)` sobre texto simple. Un archivo con nombre prefijo malicioso (p. ej. `models-folder/`) podía coincidir con `models/`. Además, `Path.normalize()` solo resuelve componentes léxicos como `..` sin consultar el sistema de archivos ni resolver enlaces simbólicos (`SherpaModelManager.kt:140-142`).
* **Solución aplicada**:
  * **Rechazo estricto de enlaces**: Se verifica cada entrada del tar y se rechazan inmediatamente enlaces simbólicos y rígidos (`entry.isSymbolicLink || entry.isLink`).
  * **Comparación por componentes**: Se normaliza la ruta léxicamente con `canonicalBaseDir.resolve(entry.name).normalize()` y se compara con `Path.startsWith(Path)`, respetando los límites exactos de los componentes de directorio del sistema.
  * **Verificación de destino en el sistema de archivos**: Se valida que ningún destino (`Files.isSymbolicLink`) ni sus carpetas intermediarias o archivo final escapen de `canonicalBaseDir` mediante enlaces simbólicos preexistentes en el disco.

#### 6. Exclusión Mutua y Nombres Temporales Seguros en Descargas TTS
* **Problema anterior**: Llamadas simultáneas a la descarga del modelo TTS competían escribiendo al mismo archivo temporal y carpeta de extracción, provocando corrupción de datos (`SherpaModelManager.kt:75-81, 100-103, 134-150`).
* **Solución aplicada**:
  * Se protegió `downloadAndExtractModel()` mediante `downloadMutex = Mutex()`, garantizando ejecución mutuamente excluyente con verificación temprana de modelo listo.
  * Los archivos temporales ahora utilizan sufijos temporales únicos (`sherpa_model_temp_${System.currentTimeMillis()}.tar.bz2`) y se eliminan de forma segura en bloques `finally`.

#### 7. Liberación Incondicional de MediaSession en Fallos de Reproductor TTS
* **Problema anterior**: Si la creación del navegador fallaba (`createNavigator == null`), la función retornaba anticipadamente sin liberar la `MediaSession` anterior, dejando controles del sistema apuntando a un reproductor destruido (`ReaderTtsService.kt:295-299, 346-347, 400-401`).
* **Solución aplicada**:
  * Se estructuró la liberación de `mediaSession` de forma incondicional (`mediaSession?.let { removeSession(it); it.release(); mediaSession = null }`) antes de evaluar si el nuevo navegador es válido.

#### 8. Temporizador de Sueño Monotónico y Precisión de Ejecución
* **Problema anterior**: El temporizador de apagado dependía de `System.currentTimeMillis()`. Cualquier cambio manual de reloj o ajuste NTP del sistema podía hacer que el temporizador terminara antes o se congelara (`SleepTimerManager.kt:45-49`, `SystemTimeProvider.kt:7`).
* **Solución aplicada**:
  * Se incorporó el método `elapsedRealtime(): Long` en `TimeProvider`, implementado con `SystemClock.elapsedRealtime()` en `SystemTimeProvider`, desacoplando la cuenta regresiva del reloj de pared del sistema.
  * Se ajustó el cálculo de intervalos adaptativos de espera en `SleepTimerManager` para coincidir exactamente con el umbral de atenuación gradual de 20 segundos (`minOf(5000L, remaining - 20000L)`) y la cuenta regresiva final, evitando desviaciones internas.
  * Se documentó el impacto de las restricciones de segundo plano y Doze Mode de Android, donde el reloj monotónico garantiza el tiempo transcurrido correcto pero las políticas de ahorro de energía del SO pueden posponer la reanudación de corrutinas si no se acompaña de servicios en primer plano activos.

---

### ⚙️ Optimizaciones de Dependencias y Compilación
* **Resolución de Conflicto Android Lint**: Se declararon explícitamente `androidx.concurrent:concurrent-futures:1.2.0` y `androidx.concurrent:concurrent-futures-ktx:1.2.0` en `app/build.gradle.kts`, resolviendo el conflicto de resolución entre Espresso 3.7.0 y la app que bloqueaba `lintDebug`.
* **Ajuste de Metaspace Gradle**: Se incrementó `-XX:MaxMetaspaceSize=1024m` en `gradle.properties` para estabilizar el demonio de Gradle durante análisis estáticos intensivos.
* **Control de versiones**: `versionCode = 10`, `versionName = "1.0.9"` en `app/build.gradle.kts`.
* **Verificación de Calidad**:
  * Compilación `lintDebug` ejecutada exitosamente sin conflictos de dependencias.
  * 90 pruebas unitarias superadas (`./gradlew test`).
  * Compilación Release (`./gradlew assembleRelease`) exitosa con optimización R8.
  * Despliegue e inicialización verificados en dispositivo físico (`Android 14`).

---

## 🚀 Versión 1.0.8 (Build 9) — Octubre 2026

### 📌 Resumen de la Versión
Esta versión se enfoca en la **robustez del estado de lectura, sincronización de progreso y navegación en libros EPUB y PDF**, resolviendo 5 defectos críticos detectados en el ciclo de vida del lector y la persistencia de datos en SQLite/Room.

---

### 🛠️ Correcciones Críticas de Estado y Navegación

#### 1. Actualización y Persistencia de Progreso en Tiempo Real en EPUB
* **Problema anterior**: Al avanzar o retroceder páginas manualmente en libros EPUB, el indicador de lectura en la interfaz se quedaba rezagado. La persistencia en base de datos solo ocurría al pulsar el botón de salida; si la app se cerraba inesperadamente, se interrumpía la tarea o el sistema destruía la actividad en segundo plano, se perdía la posición recién leída (`ReaderScreen.kt`, `ReaderViewModel.kt`).
* **Solución aplicada**:
  * Se implementó `observeNavigator(nav)` en `ReaderViewModel`, que observa reactivamente el flujo `nav.currentLocator`.
  * Se conectó `LaunchedEffect(currentLocator)` en Compose (`ReaderScreen.kt`), despachando cada cambio de página al ViewModel con debounce controlado (800ms) para no saturar SQLite.
  * Se integró `LifecycleEventEffect(Lifecycle.Event.ON_PAUSE)` para forzar el guardado de progreso antes de cualquier transición de ciclo de vida.
  * Ahora el porcentaje, las palabras leídas por minuto (WPM) y el tiempo estimado restante se calculan y guardan con total precisión.

#### 2. Restauración de Posición Segura con Cascada Fallback (CFI Inválido)
* **Problema anterior**: Cuando el identificador CFI guardado en JSON no podía ser interpretado por Readium (debido a ediciones en el archivo, conversiones EPUB o cambios de maquetación), el lector descartaba la posición y abría el libro en la página 1 (`ReaderViewModel.kt:367-375`, `ReaderScreen.kt:852-854`).
* **Solución aplicada**:
  * Se añadió el campo `val initialLocator: Locator? = null` en `ReaderUiState.Success` (`ReaderContract.kt`).
  * `validateAndRestoreLocator` aplica una cascada en tres niveles:
    1. **Nivel 1**: Validación estricta del CFI original.
    2. **Nivel 2**: Fallback inmediato por `fallbackLocatorHref`, ubicando el inicio del capítulo o sección donde se quedó el usuario.
    3. **Nivel 3**: Fallback por `fallbackLocatorPosition` o el primer elemento válido de `readingOrder`.
  * `ReaderScreen` recibe el locator ya validado e inmune a errores de parseo JSON.

#### 3. Sincronización Previa del Cálculo de Posiciones (`totalPositions`)
* **Problema anterior**: El lector emitía el estado `ReaderUiState.Success` antes de que la corrutina de cálculo de posiciones terminara de ejecutarse (`positionsJob` asíncrono sin esperar). Si el usuario intentaba mover el slider de progreso o utilizar la búsqueda de texto inmediatamente, estas funciones no respondían porque `totalPositions` era `0`.
* **Solución aplicada**:
  * `loadBook` ahora sincroniza la inicialización con `positionsJob?.join()` antes de declarar el lector listo (`_uiState = Success`).
  * Si la publicación no provee un mapa de posiciones precalculado, se genera un esquema sintetizado a partir de `pub.readingOrder` (`pub.locatorFromLink(link)`), garantizando siempre que `totalPositions > 0`.
  * El slider de navegación y la barra inferior están 100% operativos desde el primer instante.

#### 4. Corrección de Barra de Progreso en Lector PDF Original
* **Problema anterior**: En libros PDF ejecutados en modo visual/original (sin texto reflow), mover la barra de progreso inferior intentaba saltar páginas en el motor reflow oculto (`pdfReflowManager`), dejando la pantalla visual del PDF completamente inerte (`ReaderViewModel.kt:952-960`, `ReaderScreen.kt:995-998`).
* **Solución aplicada**:
  * Se separó el flujo de navegación entre Reflow y PDF Original en `seekToGlobalProgress`.
  * En modo PDF original (`isPdfBook && !isReflowMode.value`), el arrastre del slider calcula la página destino y despacha `nav.go(base.copy(locations = Locations(position = targetPage)))`, actualizando la página visible de manera fluida e inmediata.

#### 5. Sincronización de Página Actual y Guardado al Salir en PDF Original
* **Problema anterior**: La variable `lastKnownReflowPage` solo se actualizaba a través de eventos del motor reflow. En el visor original de PDF, el usuario podía leer varias páginas mediante deslizamiento o toques, pero al salir o cambiar de capítulo, la app registraba la página inicial con la que se abrió la sesión (`ReaderViewModel.kt:363, 1023, 1118, 1698-1712`).
* **Solución aplicada**:
  * `onLocatorChanged` ahora actualiza continuamente `lastKnownReflowPage` a partir de `locator.locations.position`.
  * `goToNextChapter` y `goToPreviousChapter` toman la posición dinámica del `VisualNavigator`.
  * Al salir (`saveCurrentProgressBeforeExit`), se consulta la página visual en vivo del navegador de Readium, guardando en la base de datos el progreso porcentual real y el número de página exacto.

---

### ⚙️ Optimizaciones de Compilación y Sistema
* **Control de versiones**: `versionCode = 9`, `versionName = "1.0.8"` en `app/build.gradle.kts`.
* **Compatibilidad de API Readium**: Eliminación de llamadas directas `suspend positions()` fuera de corrutinas en ViewModel, sustituidas por el contador atómico en memoria `totalPositions`.
* **Verificación de Calidad**:
  * Compilación Release limpia con ProGuard/R8.
  * Todas las pruebas unitarias pasaron con éxito (`./gradlew test`).
  * Instalación y verificación exitosa en dispositivo físico conectado (`Android 14`).

---

## 📦 Versiones Anteriores (Histórico)

### Versión 1.0.7b
* **Corrección de URLs en Capítulos EPUB**: Manejo de rutas relativas y decodificación de espacios `%20` en `readingOrder`.
* **Modo Unificado**: Retiro definitivo de modos disjuntos continuo/vertical en favor de una experiencia unificada fluida con paginación y scroll adaptativo.
* **Manejo de Selección de Texto**: Prevención de bloqueo de navegación táctil cuando se activa el menú contextual de texto (resaltar / narrar).

### Versión 1.0.7
* **Optimizaciones de Rendimiento y APK**: Eliminación de compresión indebida en assets y depuración de modelos TTS no utilizados (`es_MX`), reduciendo el tamaño del paquete.
* **Seguridad en Ciclo de Vida**: Corrección del guardado de sesiones de lectura en `onCleared()`.
* **Estabilización de UI**: Corrección de excepción en pestañas de notas y resaltado persistente en base de datos.
