content = """
---

## 4. Componentes Reutilizables

### 4.1 WoodsBookCard y BookInitials
Fuente: `app/src/main/java/com/flint/woodsreader/ui/library/components/LibraryComponents.kt#L1-L150`.

- **WoodsBookCard:**
  - Parámetros: `book: BookEntity`, `onClick: () -> Unit`, `onLongClick: () -> Unit`, `modifier: Modifier`.
  - Estructura: `Card` con `shape = RoundedCornerShape(12.dp)`, `colors = CardDefaults.cardColors(containerColor = Theme.cardBackground)`, `border = BorderStroke(1.dp, Theme.border)`.
  - Contenido:
    - `Box` de portada con ratio `2:3` (`aspectRatio(2f / 3f)`) o altura `170.dp`. Contiene `AsyncImage` con `contentScale = ContentScale.Crop`.
    - Si `book.coverPath` es nulo o falla la carga: Muestra `BookInitials(title = book.title, author = book.author)` con fondo de gradiente sutil y las iniciales en `headlineSmall` (24.sp, SemiBold) centrado.
    - Badges superpuestos en la portada:
      - Arriba a la izquierda: `WoodsBadge(text = book.format.uppercase())` (`RoundedCornerShape(4.dp)`, fondo `Color.Black.copy(alpha = 0.65f)`, texto blanco `10.sp`).
      - Si está terminado (`book.progress >= 1.0f`): Arriba a la derecha muestra icono `Icons.Filled.CheckCircle` en `16.dp` con tinte `Theme.semantics.success`.
    - Barra de progreso inferior en la tarjeta: `WoodsProgressBar(progress = book.progress)` fijada en la parte inferior de la imagen.
    - Metadatos (debajo de la portada, con padding de `8.dp`):
      - Título: `Text(text = book.title, style = titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, color = Theme.textPrimary)`.
      - Autor: `Text(text = book.author, style = bodySmall, maxLines = 1, overflow = TextOverflow.Ellipsis, color = Theme.textSecondary)`.

### 4.2 WoodsProgressBar y WoodsBadge
Fuente: `app/src/main/java/com/flint/woodsreader/ui/library/components/LibraryComponents.kt#L155-L220`.

- **WoodsProgressBar:**
  - Altura: `4.dp` (en tarjeta estándar) o `6.dp` (en ContinueReadingHero).
  - Forma: `RoundedCornerShape(2.dp)` o `CircleShape`.
  - Color de pista (Track): `Theme.surfaceVariant` o `Theme.border`.
  - Color de progreso (Indicator): `Theme.primary` (`#4CAF72`), animado con `animateFloatAsState(tween(500))`.

- **WoodsBadge:**
  - Parámetros: `text: String`, `containerColor: Color = Theme.primary.copy(alpha = 0.2f)`, `contentColor: Color = Theme.primary`.
  - Padding: horizontal `6.dp`, vertical `2.dp`.
  - Forma: `RoundedCornerShape(4.dp)`.
  - Tipografía: `labelSmall` (10.sp, Bold).

### 4.3 ContinueReadingHero y ScanStatsCard
Fuentes: `ContinueReadingHero.kt#L1-L220` y `LibraryComponents.kt#L230-L310`.

- **ContinueReadingHero:**
  - Tarjeta horizontal destacada con fondo `Theme.surface`, radio `16.dp` y borde `1.dp` de `Theme.border`.
  - Izquierda: Portada `80.dp` de ancho por `120.dp` de alto con sombra difusa y badge de formato.
  - Derecha: Título en 2 líneas (`titleMedium`), autor (`bodySmall`), barra de progreso `6.dp`, porcentaje numérico y minutos estimados de lectura restantes calculados en función del promedio de WPM del lector.
  - Botón o icono de acción rápida: `IconButton` circular con `Icons.Filled.PlayArrow` en fondo primario.

- **ScanStatsCard:**
  - Tarjeta de aviso de indexación con fondo `Theme.surfaceVariant.copy(alpha = 0.7f)`, radio `12.dp`.
  - Muestra un icono de carpeta animado, texto `\"Indexando nuevos libros en el dispositivo...\"` y un `LinearProgressIndicator` continuo indeterminado con color `Theme.primary`.

### 4.4 RecentBooksSheet y WoodsBottomNavigation
Fuente: `LibraryComponents.kt#L330-L525`.

- **RecentBooksSheet:**
  - ModalBottomSheet de Material 3 con esquinas superiores `24.dp` y fondo `Theme.surface`.
  - Cabecera con título `\"Lecturas Recientes\"` y botón de cierre.
  - Lista horizontal (`LazyRow`) de portadas de los últimos 5 libros abiertos con su título, última fecha de apertura y progreso para cambio instantáneo sin salir de la vista actual.

- **WoodsBottomNavigation:**
  - Cápsula flotante elevada (`64.dp`, esquinas `32.dp`, márgenes laterales `16.dp`, padding inferior `16.dp`).
  - Fondo `surface.copy(alpha = 0.88f)` con Haze Glass.
  - 3 destinos: Biblioteca (`Icons.Outlined.Book`), Leer Ahora (`Icons.Filled.AutoStories` en círculo `48.dp`), Estadísticas (`Icons.Outlined.BarChart`).

### 4.5 Componentes UI del Lector (ReaderUIComponents)
Fuente: `app/src/main/java/com/flint/woodsreader/ui/reader/ReaderUIComponents.kt#L1-L280`.

- **ReaderIconButton:**
  - Parámetros: `icon: ImageVector`, `contentDescription: String?`, `onClick: () -> Unit`, `tint: Color = Theme.textPrimary`, `badgeCount: Int = 0`.
  - Tamaño de botón: `44.dp` x `44.dp` con forma `CircleShape` o `RoundedCornerShape(8.dp)`.
  - Si `badgeCount > 0`: Dibuja un círculo pequeño rojo o primario en `TopEnd` con el número en `9.sp`.

- **ReaderSolidCard:**
  - Tarjeta opaca para paneles de control con fondo `Theme.surface` y borde `1.dp` de `Theme.border`.

- **ReaderCrystalCard (Haze Glassmorphism):**
  - Tarjeta con desenfoque de cristal (`hazeChild`), fondo translúcido `Theme.surface.copy(alpha = 0.75f)`, borde sutil `BorderStroke(1.dp, Color.White.copy(alpha = 0.12f))`.

- **PremiumSlider:**
  - Slider estilizado con control deslizante redondeado (`thumb` circular de `16.dp` con centro primario y borde blanco), barra activa gruesa de `6.dp` con esquinas redondeadas y barra inactiva de `4.dp` en `Theme.surfaceVariant`.

- **HighlightColorMenu:**
  - Fila horizontal con 5 botones circulares de `28.dp` x `28.dp` correspondientes a la paleta de resaltado (`Yellow`, `Green`, `Blue`, `Pink`, `Purple`). El color seleccionado muestra un anillo blanco o primario exterior de `2.dp` de separación.

---

## 5. Lector (Sección Detallada)

### 5.1 Barras y Controles Superiores/Inferiores
Fuentes: `app/src/main/java/com/flint/woodsreader/ui/reader/ReaderTopBar.kt#L1-L180`, `ReaderBottomBar.kt#L1-L210`.

- **ReaderTopBar:**
  - Ubicación: Parte superior con `statusBarsPadding()`.
  - Fondo: `Theme.surface.copy(alpha = 0.92f)` con blur de cristal (`Haze`).
  - Borde inferior: `1.dp` de `Theme.border.copy(alpha = 0.5f)`.
  - Altura: `56.dp`.
  - Elementos de izquierda a derecha:
    1. Botón Volver: `IconButton(Icons.Filled.ArrowBack, tint = Theme.textPrimary)`.
    2. Columna Central (weight = 1f):
       - Título del libro: `Text(book.title, style = titleSmall, maxLines = 1, overflow = Ellipsis, color = Theme.textPrimary)`.
       - Capítulo actual: `Text(currentChapterTitle, style = bodySmall, maxLines = 1, overflow = Ellipsis, color = Theme.textSecondary)`.
    3. Botón Grafo: `IconButton(Icons.Filled.Hub, tint = Theme.textPrimary)`.
    4. Botón Marcador: `IconButton(if (isBookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder, tint = if (isBookmarked) Theme.primary else Theme.textPrimary)`.
    5. Botón Menú Lateral: `IconButton(Icons.Filled.MoreVert, tint = Theme.textPrimary)`.

- **ReaderBottomBar:**
  - Ubicación: Parte inferior fija con `navigationBarsPadding()`.
  - Fondo: `Theme.surface.copy(alpha = 0.92f)` con Haze Glass.
  - Borde superior: `1.dp` de `Theme.border.copy(alpha = 0.5f)`.
  - Altura: `60.dp`.
  - Elementos distribuidos con `Arrangement.SpaceAround`:
    1. Índice / TOC: `IconButton(Icons.Filled.FormatListBulleted, tint = Theme.textPrimary)` - Abre `TocBottomSheet`.
    2. Apariencia / Tipografía / Audio: `IconButton(Icons.Filled.Tune, tint = Theme.textPrimary)` - Abre `ReaderControlBottomSheet`.
    3. Reproductor TTS: `IconButton(Icons.Filled.Headphones, tint = Theme.primary)` - Despliega `ReaderTtsBar` o `ReaderTtsMacroWindow`.
    4. Notas y Subrayados: `IconButton(Icons.Filled.EditNote, tint = Theme.textPrimary)` - Abre `ReaderNotesTab`.
    5. Búsqueda en libro: `IconButton(Icons.Filled.Search, tint = Theme.textPrimary)`.

### 5.2 Menú Lateral Desplegable (Side Menu)
Fuente: `app/src/main/java/com/flint/woodsreader/ui/reader/ReaderSideMenu.kt#L1-L220`.

Panel flotante deslizable desde el lateral derecho de la pantalla con fondo `Theme.surface.copy(alpha = 0.95f)`, ancho de `280.dp`, esquinas redondeadas izquierdas de `16.dp` y sombra profunda:
- Lista de acciones:
  1. **Información y Metadatos:** Icono `Icons.Filled.Info`, texto `\"Detalles del libro\"`.
  2. **X-Ray Narrativo:** Icono `Icons.Filled.Visibility`, texto `\"X-Ray del capítulo\"` (abre lista de entidades presentes en el capítulo en curso).
  3. **Ego-Grafo del Libro:** Icono `Icons.Filled.Share`, texto `\"Red de relaciones\"`.
  4. **Traducción Calibre:** Icono `Icons.Filled.Translate`, texto `\"Traducir libro\"`.
  5. **Modo Pantalla Completa:** Icono `Icons.Filled.Fullscreen`, switch ON/OFF.
  6. **Bloqueo de Rotación:** Icono `Icons.Filled.ScreenRotation`, switch ON/OFF.
  7. **Modo Zurdo:** Icono `Icons.Filled.FrontHand`, switch ON/OFF (invierte las zonas de toque de cambio de página).
  8. **Exportar Anotaciones:** Icono `Icons.Filled.UploadFile`, texto `\"Exportar notas (.txt, .json)\"`.
  9. **Ajustes Avanzados:** Icono `Icons.Filled.Settings`, texto `\"Ajustes del lector\"`.

### 5.3 Sistema de Controles TTS (Barra, Cápsula Flotante, Lock Overlay)
Fuentes: `ReaderTtsBar.kt#L1-L240`, `ReaderFloatingTtsButton.kt#L1-L180`, `ReaderTtsLockOverlay.kt#L1-L210`.

- **ReaderTtsBar:**
  - Barra de control de audio en 2 niveles ubicada sobre la barra inferior:
    - **Nivel 1 (Ticker de Lectura):** Franja de `36.dp` de alto con fondo `Theme.surfaceVariant.copy(alpha = 0.9f)` que muestra la frase actual que se está sintetizando con desplazamiento horizontal automático (Marquee) en tipografía `bodySmall` color `Theme.textPrimary`.
    - **Nivel 2 (Transporte de Reproducción):** Contenedor de `56.dp` de alto con:
      - Botón de retroceso de frase (`Icons.Filled.Replay10`, `24.dp`).
      - Botón Play/Pausa central: Contenedor circular `44.dp` con fondo `Theme.primary` y icono `Icons.Filled.PlayArrow` / `Icons.Filled.Pause` en color `Theme.background`.
      - Botón de avance de frase (`Icons.Filled.Forward10`, `24.dp`).
      - Selector de velocidad rápida: Chip `1.0x` / `1.2x` / `1.5x` / `2.0x`.
      - Botón expandir a estudio completo: `IconButton(Icons.Filled.OpenInFull, tint = Theme.textSecondary)`.

- **ReaderFloatingTtsButton (Cápsula Flotante Flotante / PIP):**
  - Cápsula arrastrable mediante gestos `pointerInput` con offset mutable (`IntOffset(x, y)`).
  - Dimensiones: Alto `42.dp`, Ancho `96.dp`, Forma `RoundedCornerShape(21.dp)`.
  - Fondo: `Theme.surface.copy(alpha = 0.90f)` con desenfoque de cristal y borde `1.dp` de `Theme.primary.copy(alpha = 0.4f)`.
  - Contenido: Icono Play/Pausa (`20.dp`, `Theme.primary`), mini ecualizador de 3 barras verticales animadas y botón de cierre (`Icons.Filled.Close`, `16.dp`).

- **ReaderTtsLockOverlay (Pantalla Bloqueada para Escucha):**
  - Pantalla protectora contra toques accidentales diseñada para escuchar audiolibro con el teléfono en el bolsillo o de noche.
  - Fondo: Negro absoluto (`#000000`) o `Theme.background`.
  - Centro: Animación de ecualizador de ondas sinusoidales en color `Theme.primary`.
  - Texto central: Título del libro, capítulo y porcentaje de lectura.
  - Desbloqueo: Deslizador horizontal tipo `\"Deslizar para desbloquear\"` con icono `Icons.Filled.LockOpen` y umbral del 80% de recorrido.

### 5.4 Slider de Progreso y Píldora de Teleprompter
Fuentes: `ReaderBottomSlider.kt#L1-L150`, `ReaderTeleprompterPill.kt#L1-L120`.

- **ReaderBottomSlider:**
  - Barra delgada ubicada inmediatamente arriba de la barra de herramientas inferior.
  - Muestra un slider continuo de `0.0f` a `1.0f` que permite arrastrar para saltar a cualquier parte del libro.
  - Al arrastrar, despliega una burbuja flotante (Tooltip) con el número de página estimado y el título del capítulo correspondiente.
  - Etiquetas a los lados: Izquierda: `\"Pág. 142 de 420\"` (`labelSmall`), Derecha: `\"34%\"` (`labelSmall`).

- **ReaderTeleprompterPill:**
  - Píldora flotante minimalista ubicada en la parte superior central de la pantalla durante la lectura activa con TTS.
  - Dimensiones: Alto `32.dp`, esquinas `16.dp`, fondo `Theme.surface.copy(alpha = 0.85f)`, borde `1.dp` de `Theme.border`.
  - Contenido: Muestra la velocidad en WPM (ej. `\"280 ppm\"`) junto a un icono parpadeante de micrófono/altavoz.

### 5.5 Todos los Bottom Sheets del Lector
Fuentes: `ReaderAudioBottomSheet.kt`, `ReaderNotesTab.kt`, `TocBottomSheet.kt`, `ReaderXRayBottomSheet.kt`, `ReaderEgoGraphBottomSheet.kt`, `ReaderTtsMacroWindow.kt`.

1. **TocBottomSheet (Tabla de Contenidos / Índice):**
   - Altura: 70% de la altura de la pantalla (`fillMaxHeight(0.7f)`), esquinas superiores `24.dp`.
   - Cabecera: Título `\"Índice de Contenidos\"` y buscador rápido de capítulos.
   - Pestañas: `\"Capítulos\"`, `\"Marcadores\"` y `\"Anotaciones\"`.
   - Elemento de capítulo: Fila con sangría proporcional al nivel de anidamiento (`padding(start = 16.dp * level)`), título del capítulo (`bodyMedium`, peso SemiBold si es el capítulo actual) y número de página al extremo derecho (`Theme.textSecondary`).

2. **ReaderNotesTab (Notas y Subrayados del Libro Actual):**
   - Lista ordenada cronológicamente de todos los fragmentos subrayados en el libro.
   - Cada tarjeta de nota muestra:
     - Franja lateral de color correspondiente al subrayado (`yellow`, `green`, `blue`, etc.) de `4.dp` de ancho.
     - Texto citado: `bodyMedium` en cursiva con comillas tipográficas.
     - Nota personal del usuario (si existe): `bodySmall` en caja con fondo `Theme.surfaceVariant` y radio `6.dp`.
     - Timestamp y ubicación: `\"Capítulo 4 • Hace 2 días\"` (`labelSmall`).
     - Botón de compartir cita: Abre `QuoteShareDialog`.

3. **ReaderXRayBottomSheet (Radiografía Narrativa):**
   - Analiza el texto del capítulo en pantalla y agrupa las entidades encontradas en: Personajes (`Icons.Filled.Person`), Lugares (`Icons.Filled.Place`) y Facciones (`Icons.Filled.Groups`).
   - Al tocar una entidad, despliega su descripción, alias y apariciones previas.

4. **ReaderEgoGraphBottomSheet:**
   - Visualiza en miniatura la red local de la entidad seleccionada en un radio de 2 saltos de distancia.

### 5.6 Panel de Ajustes de Lectura (Audio & Typography Sheet)
Fuente: `app/src/main/java/com/flint/woodsreader/ui/reader/ReaderAudioBottomSheet.kt#L1-L360`.

BottomSheet integral con todos los controles tipográficos y de visualización del motor de lectura:
- **Selector de Temas (5 Tarjetas):**
  - Muestra 5 tarjetas rectangulares en miniatura con los nombres de tema: Hidden Forest, Day, Sepia, AMOLED y Sunset, reflejando sus colores exactos de fondo y texto.
- **Selector de Familia Tipográfica:**
  - Literata (Serif por defecto), Sans-serif del sistema, OpenDyslexic, Vollkorn, Merriweather.
- **Controles con Deslizadores (PremiumSlider):**
  - **Tamaño de Fuente:** Rango de `12.sp` a `36.sp`, paso de `1.sp`, valor por defecto `18.sp`.
  - **Interlineado (Line Height / Spacing):** Rango de `0.8` a `2.0`, paso de `0.1`, valor por defecto `1.4`.
  - **Márgenes Laterales:** Rango de `0.dp` a `40.dp`, paso de `4.dp`, valor por defecto `16.dp`.
- **Alineación de Texto:**
  - Fila con 4 botones: Justificado (`Icons.Filled.FormatAlignJustify`), Izquierda (`Icons.Filled.FormatAlignLeft`), Centrado (`Icons.Filled.FormatAlignCenter`), Derecha (`Icons.Filled.FormatAlignRight`).
- **Separación Silábica (Guionación / Hyphenation):**
  - Switch ON/OFF (`Theme.primary`).
- **Estilo de Resaltado TTS:**
  - Selector de 3 estilos: Fondo completo (`Background Fill`), Subrayado elegante (`Underline`) o Solo cambio de color de texto (`Text Color`).
- **Modo de Animación de Página:**
  - 3 opciones: Doblez 3D de papel (`Book Flip 3D`), Deslizamiento plano (`Slide`) o Sin animación (`None`).

### 5.7 Ventana Macro TTS (Estudio de Audio de 3 Pestañas)
Fuente: `app/src/main/java/com/flint/woodsreader/ui/reader/ReaderTtsMacroWindow.kt#L1-L420`.

Ventana de estudio de audio profesional flotante en modal con altura expandible y 3 pestañas principales:

1. **Pestaña 0: Voces y Motores (Voices & Engines):**
   - Selector de motor de síntesis:
     - **Microsoft Edge Neural:** Lista de voces remotas ultra-realistas en alta fidelidad (ej. `es-ES-AlvaroNeural`, `es-ES-ElviraNeural`, `es-MX-DaliaNeural`, `en-US-JennyNeural`).
     - **Sherpa ONNX Offline:** Lista de modelos neuronales VITS ejecutados 100% en el dispositivo sin conexión a internet ni consumo de datos. Cada voz muestra su tamaño en disco (`~45 MB`) y botón de descarga o eliminación.
     - **Android System TTS:** Motores nativos instalados en el SO (Google TTS, Samsung TTS).

2. **Pestaña 1: Acústica y Sonidos de Ambiente (Ambience & Acoustics):**
   - **Velocidad de Lectura (Pitch & Speed):** Sliders de `0.5x` a `3.0x` para velocidad y de `-50%` a `+50%` para tono de voz.
   - **Generador de Ruido y Ambientes Sonoros:** Permite reproducir pistas de fondo superpuestas a la voz del narrador con control de volumen independiente (`0%` a `100%`):
     - Lluvia suave en el bosque (`Rain`).
     - Fuego de chimenea crepitante (`Fireplace`).
     - Viento entre los árboles (`Wind`).
     - Cafetería acogedora (`CoffeeShop`).
     - Olas del mar (`Ocean`).
   - **Temporizador de Apagado (Sleep Timer):**
     - Botones de selección rápida: `15 min`, `30 min`, `45 min`, `60 min`, `Fin del capítulo actual`.

3. **Pestaña 2: Exportación de Audiolibro (Export Audio):**
   - Herramienta para renderizar capítulos completos a archivos de audio comprimidos `.M4A` / `.MP3` con metadatos ID3 de autor, título y carátula para escucha externa.

### 5.8 Renderizado de Texto y Resaltado Dinámico en EPUB/PDF
Fuentes: `app/src/main/java/com/flint/woodsreader/ui/reader/EpubFormatManager.kt#L1-L180`, `ReaderExtensions.kt#L220-L380`.

- **Inyección de CSS y DOM en EPUB:**
  - El motor de lectura desactiva los cuadros nativos de Readium para un rendimiento óptimo de memoria y en su lugar inyecta una clase CSS dedicada:
  ```css
  .woods-tts-active {
    background-color: rgba(76, 175, 114, 0.28) !important;
    border-radius: 4px !important;
    box-shadow: 0 0 0 2px rgba(76, 175, 114, 0.28) !important;
    transition: background-color 0.25s ease, box-shadow 0.25s ease !important;
  }
  ```
  - Cuando el cursor de voz TTS avanza, una llamada a `applyCustomTtsHighlight(elementId, range)` remueve la clase del nodo anterior y la asigna al elemento HTML en locución de forma atómica sin parpadeos de pantalla.

### 5.9 Efectos de Paso de Página (Book Flip 3D y Slide)
Fuente: `app/src/main/java/com/flint/woodsreader/ui/reader/BookPageTurnEffect.kt#L1-L240`.

- **Book Flip 3D (Simulación Física de Papel):**
  - Utiliza `PixelCopy` para capturar un bitmap exacto de la página actual en hardware texture.
  - Al arrastrar el dedo horizontalmente, se calcula la fracción de deformación `p` (de `0.0f` a `1.0f`).
  - Aplica una función cilíndrica de doblado mediante matriz de proyección:
    - Bending: `sin(p * PI)` para la curvatura del lomo de la hoja.
    - Sombra del lomo: Dibuja un degradado negro lineal translúcido en el punto de inflexión del doblez con alpha dinámico `(1f - p) * 0.4f`.
    - Resaltado de brillo: Franja blanca sutil de `12.dp` en el borde de doblado para simular el reflejo de la luz natural sobre el papel.
- **Slide:** Transición horizontal lineal con opacidad cruzada (`crossfade`) de 200ms.
"""
