content = """
---

## 3. Para Cada Pantalla

### 3.1 Onboarding (OnboardingScreen)
Fuente: `app/src/main/java/com/flint/woodsreader/ui/onboarding/OnboardingScreen.kt#L1-L150`.

Diseñada para presentar la filosofía editorial y natural de Woods Reader en 3 pasos interactivos usando `HorizontalPager`.

a) **Árbol de Composables:**
```
Scaffold (containerColor = Theme.background)
└── Box (modifier = Modifier.fillMaxSize())
    ├── HorizontalPager (state = pagerState, modifier = Modifier.fillMaxSize())
    │   └── Column (modifier = Modifier.fillMaxSize().padding(horizontal = 32.dp), horizontalAlignment = CenterHorizontally, verticalArrangement = Arrangement.Center)
    │       ├── Box (modifier = Modifier.size(240.dp).clip(CircleShape).background(Theme.surfaceVariant.copy(alpha = 0.5f)))
    │       │   └── Icon / Illustration (size = 120.dp, tint = Theme.primary)
    │       ├── Spacer (modifier = Modifier.height(40.dp))
    │       ├── Text (title, style = headlineMedium, color = Theme.textPrimary, textAlign = Center)
    │       ├── Spacer (modifier = Modifier.height(16.dp))
    │       └── Text (description, style = bodyLarge, color = Theme.textSecondary, textAlign = Center, lineHeight = 24.sp)
    └── Box (modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(horizontal = 24.dp, vertical = 32.dp))
        └── Row (modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = CenterVertically)
            ├── Row (horizontalArrangement = Arrangement.spacedBy(8.dp)) [Indicador de Páginas]
            │   └── Box x 3 (height = 8.dp, width = if (selected) 24.dp else 8.dp, shape = CircleShape, background = if (selected) Theme.primary else Theme.surfaceVariant)
            └── Button / IconButton [Botón Continuar o Empezar]
                └── Text / Icon (Icons.Filled.ArrowForward, tint = Theme.background)
```

b) **Medidas Reales:**
- Padding horizontal de diapositiva: `32.dp`.
- Contenedor circular de ilustración: `240.dp` x `240.dp`.
- Icono central de ilustración: `120.dp`.
- Espaciado entre ilustración y título: `40.dp`.
- Espaciado entre título y descripción: `16.dp`.
- Puntos indicadores (Dots Indicator): inactivo `8.dp` x `8.dp`, activo `24.dp` x `8.dp` (animado con `animateDpAsState(tween(300))`), separación `8.dp`.
- Barra inferior de acción: padding horizontal `24.dp`, padding inferior `32.dp`.

c) **Textos:**
- Página 0: Título `\"Lectura Inmersiva\"` (`headlineMedium`, 28.sp, SemiBold), Descripción `\"Diseñado para fundirse con la lectura. Sin distracciones, en armonía con la naturaleza.\"` (`bodyLarge`, 16.sp, Normal, `Theme.textSecondary`).
- Página 1: Título `\"Voz Humana Sherpa & Edge\"` (`headlineMedium`), Descripción `\"Escucha tus libros con síntesis neuronal de última generación sin conexión o con voces ultra-realistas.\"` (`bodyLarge`).
- Página 2: Título `\"Grafo de Conocimiento\"` (`headlineMedium`), Descripción `\"Descubre las conexiones semánticas entre personajes, lugares y conceptos de tus historias.\"` (`bodyLarge`).

d) **Iconos:**
- Pág 0: `Icons.Filled.AutoStories` (`120.dp`, `Theme.primary`).
- Pág 1: `Icons.Filled.Headphones` (`120.dp`, `Theme.primary`).
- Pág 2: `Icons.Filled.Hub` (`120.dp`, `Theme.primary`).
- Botón Siguiente: `Icons.Filled.ArrowForward` (`24.dp`, `Theme.background`).

e) **Botones:**
- Botón circular de avance: `size = 56.dp`, `shape = CircleShape`, fondo `Theme.primary`, icono `Theme.background`.
- Botón final `\"Comenzar\"`: `height = 52.dp`, `shape = RoundedCornerShape(26.dp)`, padding horizontal `24.dp`.

f) **Estados:**
- Página actual `0..2` sincronizada con `pagerState.currentPage`.

g) **Animaciones y Gestos:**
- Deslizamiento táctil horizontal nativo (`HorizontalPager`).
- Expansión de anchura del indicador de página: `animateDpAsState` de `8.dp` a `24.dp`.

---

### 3.2 Permisos (PermissionsScreen)
Fuente: `app/src/main/java/com/flint/woodsreader/ui/permissions/PermissionsScreen.kt#L1-L160`.

Solicitud elegante y transparente del permiso `MANAGE_EXTERNAL_STORAGE` o lectura de medios (`READ_MEDIA_AUDIO` / `READ_MEDIA_IMAGES`).

a) **Árbol de Composables:**
```
Scaffold (containerColor = Theme.background)
└── Column (modifier = Modifier.fillMaxSize().padding(horizontal = 28.dp).verticalScroll(), horizontalAlignment = CenterHorizontally, verticalArrangement = Arrangement.Center)
    ├── Box (modifier = Modifier.size(96.dp).clip(CircleShape).background(Theme.surfaceVariant))
    │   └── Icon (Icons.Filled.FolderOpen, size = 48.dp, tint = Theme.primary)
    ├── Spacer (modifier = Modifier.height(32.dp))
    ├── Text (text = stringResource(R.string.permissions_title), style = headlineSmall, color = Theme.textPrimary, textAlign = Center)
    ├── Spacer (modifier = Modifier.height(16.dp))
    ├── Text (text = stringResource(R.string.permissions_description), style = bodyMedium, color = Theme.textSecondary, textAlign = Center, lineHeight = 22.sp)
    ├── Spacer (modifier = Modifier.height(48.dp))
    ├── Card (colors = CardDefaults.cardColors(containerColor = Theme.surface), border = BorderStroke(1.dp, Theme.border), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth())
    │   └── Column (modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp))
    │       ├── Row (verticalAlignment = CenterVertically)
    │       │   ├── Icon (Icons.Filled.CheckCircle, tint = Theme.primary, size = 20.dp)
    │       │   ├── Spacer (width = 12.dp)
    │       │   └── Text (text = \"Escanear libros en formato EPUB y PDF\", style = bodyMedium, color = Theme.textPrimary)
    │       └── Row (verticalAlignment = CenterVertically)
    │           ├── Icon (Icons.Filled.CheckCircle, tint = Theme.primary, size = 20.dp)
    │           ├── Spacer (width = 12.dp)
    │           └── Text (text = \"Privacidad total: tus archivos nunca salen de tu dispositivo\", style = bodyMedium, color = Theme.textPrimary)
    ├── Spacer (modifier = Modifier.height(40.dp))
    └── Button (onClick = { ... }, colors = ButtonDefaults.buttonColors(containerColor = Theme.primary), shape = RoundedCornerShape(26.dp), modifier = Modifier.fillMaxWidth().height(52.dp))
        └── Text (text = stringResource(R.string.permissions_grant_button), style = labelLarge, color = Theme.background, fontWeight = Bold)
```

b) **Medidas Reales:**
- Padding horizontal: `28.dp`.
- Contenedor de icono: `96.dp`, icono `48.dp`.
- Tarjeta explicativa: padding interno `16.dp`, radio `16.dp`, borde `1.dp`.
- Botón principal: altura `52.dp`, radio `26.dp` (cápsula completa).

---

### 3.3 Biblioteca (LibraryScreen, LibraryHeader, ContinueReadingHero)
Fuentes: `app/src/main/java/com/flint/woodsreader/ui/library/LibraryScreen.kt#L1-L320`, `LibraryHeader.kt#L1-L150`, `ContinueReadingHero.kt#L1-L220`, `LibraryComponents.kt#L1-L350`.

La pantalla principal de la biblioteca se organiza en una única lista vertical de alto rendimiento (`LazyColumn`) que integra la cabecera interactiva, el héroe de lectura activa, las colecciones y la cuadrícula de 2 columnas de libros.

a) **Árbol de Composables:**
```
Scaffold (
  containerColor = Theme.background,
  bottomBar = { WoodsBottomNavigation(...) }
)
└── Box (modifier = Modifier.fillMaxSize())
    ├── LazyColumn (modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp))
    │   ├── item: LibraryHeader
    │   │   └── Row (modifier = Modifier.fillMaxWidth().statusBarsPadding().padding(vertical = 16.dp), horizontalArrangement = SpaceBetween, verticalAlignment = CenterVertically)
    │   │       ├── Column
    │   │       │   ├── Text (text = \"Woods Reader\", style = headlineSmall, color = Theme.textPrimary, fontWeight = Bold)
    │   │       │   └── Text (text = \"${books.size} libros en tu bosque\", style = bodySmall, color = Theme.textSecondary)
    │   │       └── Row (horizontalArrangement = spacedBy(8.dp))
    │   │           ├── IconButton (Icons.Filled.Search, tint = Theme.textPrimary, size = 40.dp)
    │   │           ├── IconButton (Icons.Filled.Sync, tint = Theme.textPrimary, size = 40.dp) [Animación rotación si escanea]
    │   │           └── IconButton (Icons.Filled.Settings, tint = Theme.textPrimary, size = 40.dp)
    │   ├── item: ScanStatsCard (Visible condicionalmente si isScanning == true)
    │   ├── item: ContinueReadingHero (Visible si hay un libro con progreso > 0% y < 100%)
    │   │   └── Card (colors = Theme.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Theme.border), modifier = fillMaxWidth().padding(vertical = 12.dp))
    │   │       └── Row (modifier = Modifier.padding(16.dp), verticalAlignment = CenterVertically)
    │   │           ├── Box [Portada del libro] (width = 80.dp, height = 120.dp, shape = RoundedCornerShape(8.dp))
    │   │           │   ├── AsyncImage (model = book.coverPath, contentScale = Crop)
    │   │           │   └── WoodsBadge (text = \"EPUB\", modifier = TopStart)
    │   │           ├── Spacer (width = 16.dp)
    │   │           └── Column (modifier = Modifier.weight(1f))
    │   │               ├── Text (text = \"CONTINUAR LEYENDO\", style = labelSmall, color = Theme.primary, letterSpacing = 1.sp)
    │   │               ├── Text (text = book.title, style = titleMedium, maxLines = 2, overflow = Ellipsis, color = Theme.textPrimary)
    │   │               ├── Text (text = book.author, style = bodySmall, maxLines = 1, color = Theme.textSecondary)
    │   │               ├── Spacer (height = 12.dp)
    │   │               ├── WoodsProgressBar (progress = book.progress, modifier = fillMaxWidth().height(6.dp))
    │   │               └── Row (modifier = fillMaxWidth(), horizontalArrangement = SpaceBetween)
    │   │                   ├── Text (text = \"${(book.progress * 100).toInt()}%\", style = labelSmall, color = Theme.textPrimary)
    │   │                   └── Text (text = \"${book.readingTimeRemainingMinutes} min restantes\", style = labelSmall, color = Theme.textSecondary)
    │   ├── item: CollectionsChipsRow
    │   │   └── LazyRow (horizontalArrangement = spacedBy(8.dp), contentPadding = PaddingValues(vertical = 8.dp))
    │   │       └── FilterChip x N (selected = isSelected, shape = RoundedCornerShape(8.dp), colors = FilterChipDefaults...)
    │   ├── items (books.chunked(2)): BooksGridRow
    │   │   └── Row (modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp), horizontalArrangement = spacedBy(12.dp))
    │   │       ├── WoodsBookCard (book = rowBooks[0], modifier = Modifier.weight(1f))
    │   │       └── if (rowBooks.size > 1) WoodsBookCard (book = rowBooks[1], modifier = Modifier.weight(1f)) else Spacer(weight = 1f)
    │   └── item: Spacer (modifier = Modifier.height(88.dp)) [Espacio libre para WoodsBottomNavigation]
    └── if (books.isEmpty() && !isScanning) EmptyLibrary (modifier = Center)
```

b) **Medidas Reales:**
- Padding lateral de lista: `16.dp`.
- Portada en `ContinueReadingHero`: `80.dp` de ancho por `120.dp` de alto (ratio aproximado 2:3), esquinas `8.dp`.
- Altura de tarjeta Hero: ~`152.dp`.
- Cuadrícula de libros: pares de 2 columnas con espacio entre tarjetas de `12.dp` y espaciado vertical de `12.dp`.
- Portada en `WoodsBookCard`: relación de aspecto fija `aspectRatio(2f / 3f)` o altura fija `170.dp`.
- Altura de barra de navegación inferior: `64.dp` + padding inferior `16.dp`, espaciador final en lista: `88.dp`.

c) **Textos:**
- Título cabecera: `\"Woods Reader\"` (`headlineSmall`, 24.sp, SemiBold, `Theme.textPrimary`).
- Subtítulo de libros: `\"$count libros en tu bosque\"` (`bodySmall`, 12.sp, Normal, `Theme.textSecondary`).
- Subtítulo Hero: `\"CONTINUAR LEYENDO\"` (`labelSmall`, 11.sp, Medium, color `Theme.primary`, tracking `1.sp`).
- Chip \"Todos\": `\"Todos\"` (`labelMedium`, 12.sp).

d) **Iconos:**
- Búsqueda: `Icons.Filled.Search` (`24.dp`, `Theme.textPrimary`).
- Sincronización / Escaneo: `Icons.Filled.Sync` (`24.dp`, `Theme.textPrimary`).
- Ajustes: `Icons.Filled.Settings` (`24.dp`, `Theme.textPrimary`).
- Añadir colección: `Icons.Filled.Add` (`18.dp`, `Theme.primary`).

e) **Botones y Chips:**
- `FilterChip`: Alto `32.dp`, esquinas `8.dp`. Fondo seleccionado `Theme.primary.copy(alpha = 0.2f)`, borde `1.dp` de `Theme.primary`. Fondo no seleccionado `Theme.surfaceVariant`, borde `1.dp` de `Theme.border`.
- Botón flotante central de lectura: Contenedor circular `48.dp`, fondo gradiente de `Theme.primary` a `Theme.primaryVariant`.

f) **Estados Condicionales:**
- **Cargando / Escaneando:** Muestra `ScanStatsCard` debajo de la cabecera con un indicador lineal de progreso indeterminado y texto `\"Escaneando almacenamiento...\"`, y rota continuamente el icono `Icons.Filled.Sync` mediante `rememberInfiniteTransition()`. Si la biblioteca está vacía mientras escanea, se dibujan 6 tarjetas simuladas con `WoodsBookSkeleton` utilizando `rememberShimmerBrush()`.
- **Vacío:** Muestra `EmptyLibrary` con icono `Icons.Filled.AutoStories` en `72.dp`, texto `\"Tu bosque está en silencio\"` (`titleMedium`), descripción `\"Añade libros en formato EPUB o PDF a tu dispositivo para comenzar a leer\"` (`bodyMedium`) y botón `\"Escanear almacenamiento\"`.
- **Error:** Snackbar flotante con fondo `Theme.semantics.error` (`#EF5350`) y botón `\"Reintentar\"`.

g) **Animaciones y Gestos:**
- Long-press en tarjeta de libro: Muestra diálogo de opciones (`BookOptionsBottomSheet`) con opciones: Ver detalles, Cambiar colección, Marcar como leído, Eliminar.
- Long-press en botón central de navegación: Abre `RecentBooksSheet`.

---

### 3.4 Búsqueda (SearchScreen)
Fuente: `app/src/main/java/com/flint/woodsreader/ui/search/SearchScreen.kt#L1-L260`.

a) **Árbol de Composables:**
```
Scaffold (containerColor = Theme.background)
└── Column (modifier = Modifier.fillMaxSize().statusBarsPadding())
    ├── SearchBarRow (modifier = Modifier.fillMaxWidth().padding(16.dp))
    │   └── TextField (
    │       value = query,
    │       placeholder = { Text(\"Buscar por título, autor o contenido...\", color = Theme.textTertiary) },
    │       leadingIcon = { Icon(Icons.Filled.Search, tint = Theme.primary) },
    │       trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = \"\" }) { Icon(Icons.Filled.Close, tint = Theme.textSecondary) } },
    │       colors = TextFieldDefaults.colors(
    │           focusedContainerColor = Theme.surface,
    │           unfocusedContainerColor = Theme.surface,
    │           focusedIndicatorColor = Color.Transparent,
    │           unfocusedIndicatorColor = Color.Transparent
    │       ),
    │       shape = RoundedCornerShape(16.dp),
    │       modifier = Modifier.fillMaxWidth().height(56.dp).border(1.dp, Theme.border, RoundedCornerShape(16.dp))
    │   )
    ├── FilterChipsRow (modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
    │   └── Row (horizontalArrangement = spacedBy(8.dp))
    │       ├── FilterChip (selected = target == ALL, label = \"Todo\")
    │       ├── FilterChip (selected = target == TITLE, label = \"Título\")
    │       ├── FilterChip (selected = target == AUTHOR, label = \"Autor\")
    │       └── FilterChip (selected = target == CONTENT, label = \"En texto\")
    └── LazyColumn (modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp))
        └── items (results) [SearchBookResultCard]
            └── Card (colors = Theme.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Theme.border), modifier = fillMaxWidth().padding(vertical = 6.dp))
                └── Row (modifier = Modifier.padding(12.dp), verticalAlignment = CenterVertically)
                    ├── Portada Miniatura (width = 48.dp, height = 72.dp, shape = RoundedCornerShape(6.dp))
                    ├── Spacer (width = 12.dp)
                    └── Column (modifier = Modifier.weight(1f))
                        ├── Text (highlightText(book.title, query), style = titleSmall, color = Theme.textPrimary)
                        ├── Text (highlightText(book.author, query), style = bodySmall, color = Theme.textSecondary)
                        └── if (matchSnippet != null) Text (highlightText(matchSnippet, query), style = bodySmall, maxLines = 2, color = Theme.textTertiary)
```

b) **Medidas:** Campo de búsqueda alto `56.dp`, esquinas `16.dp`. Portada miniatura `48.dp` x `72.dp`.
c) **Resaltado de Coincidencias (`highlightText`):** Las letras que coinciden con la búsqueda se pintan con `SpanStyle(background = Theme.primary.copy(alpha = 0.25f), color = Theme.primary, fontWeight = FontWeight.Bold)`.

---

### 3.5 Detalle de Libro (BookDetailScreen)
Fuentes: `app/src/main/java/com/flint/woodsreader/ui/detail/BookDetailScreen.kt#L1-L380`.

Pantalla editorial profunda con cabecera colapsable, portada hero con transición compartida, metadatos estructurados, barra de progreso con píldora de estado, selector de puntuación de 5 estrellas, descripción expandible y pestañas de Subrayados, Marcadores y Sesiones de Lectura.

a) **Árbol de Composables:**
```
Scaffold (
  containerColor = Theme.background,
  floatingActionButton = { ReadNowFab(bookId = book.id) }
)
└── CustomScrollView / LazyColumn (modifier = Modifier.fillMaxSize())
    ├── item: StickyTopBar / CollapsibleHeader
    │   └── Row (modifier = fillMaxWidth().statusBarsPadding().padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = SpaceBetween)
    │       ├── IconButton (Icons.Filled.ArrowBack, tint = Theme.textPrimary)
    │       └── Row
    │           ├── IconButton (Icons.Filled.Share, tint = Theme.textPrimary)
    │           ├── IconButton (Icons.Filled.Edit, tint = Theme.textPrimary)
    │           └── IconButton (Icons.Filled.MoreVert, tint = Theme.textPrimary)
    ├── item: BookHeroSection
    │   └── Column (modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = CenterHorizontally)
    │       ├── Box (modifier = Modifier.width(150.dp).height(225.dp).clip(RoundedCornerShape(12.dp)).shadow(12.dp))
    │       │   └── AsyncImage (model = book.coverPath, modifier = fillMaxSize())
    │       ├── Spacer (height = 20.dp)
    │       ├── Text (book.title, style = headlineSmall, textAlign = Center, color = Theme.textPrimary)
    │       ├── Spacer (height = 6.dp)
    │       ├── Text (book.author, style = titleMedium, color = Theme.textSecondary)
    │       ├── Spacer (height = 12.dp)
    │       ├── ProgressPill (progress = book.progress)
    │       │   └── Surface (shape = CircleShape, color = Theme.primary.copy(alpha = 0.15f), border = BorderStroke(1.dp, Theme.primary.copy(alpha = 0.3f)))
    │       │       └── Text (\"${(book.progress * 100).toInt()}% completado\", style = labelMedium, color = Theme.primary, modifier = padding(horizontal = 14.dp, vertical = 6.dp))
    │       └── FiveStarRatingBar (rating = book.userRating, onRatingChanged = { ... })
    ├── item: MetadataGridCard
    │   └── Card (colors = Theme.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Theme.border), modifier = fillMaxWidth().padding(16.dp))
    │       └── Row (modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly)
    │           ├── MetaItem (label = \"PÁGINAS\", value = \"${book.pageCount}\")
    │           ├── VerticalDivider (modifier = height(32.dp), color = Theme.border)
    │           ├── MetaItem (label = \"FORMATO\", value = book.format.uppercase())
    │           ├── VerticalDivider (modifier = height(32.dp), color = Theme.border)
    │           ├── MetaItem (label = \"TIEMPO REST.\", value = \"${book.readingTimeRemainingMinutes}m\")
    │           ├── VerticalDivider (modifier = height(32.dp), color = Theme.border)
    │           └── MetaItem (label = \"IDIOMA\", value = book.language.uppercase())
    ├── item: DescriptionExpandableCard
    │   └── Card (modifier = fillMaxWidth().padding(horizontal = 16.dp))
    │       └── Column (modifier = Modifier.padding(16.dp))
    │           ├── Text (\"Sinopsis\", style = titleMedium, color = Theme.textPrimary)
    │           ├── Text (book.description, style = bodyMedium, maxLines = if (expanded) 100 else 4, overflow = Ellipsis, color = Theme.textSecondary)
    │           └── TextButton (onClick = { expanded = !expanded }) { Text(if (expanded) \"Mostrar menos\" else \"Leer más\", color = Theme.primary) }
    ├── item: DetailTabsRow
    │   └── TabRow (selectedTabIndex = selectedTab, containerColor = Theme.background, contentColor = Theme.primary)
    │       ├── Tab (text = \"Subrayados (${highlights.size})\")
    │       ├── Tab (text = \"Marcadores (${bookmarks.size})\")
    │       └── Tab (text = \"Sesiones\")
    └── items: Highlights / Bookmarks / Sessions List Items
```

b) **Medidas Reales:**
- Portada Hero: Ancho `150.dp`, Alto `225.dp` (proporción 2:3 clásica de libro), esquinas `12.dp`, elevación `12.dp`.
- Pestañas: Altura `48.dp`, indicador activo `Theme.primary` con grosor `3.dp`.
- `ReadNowFab`: Botón flotante extendido con altura `56.dp`, esquinas `28.dp`, icono `Icons.Filled.AutoStories` (`24.dp`), texto `\"Leer ahora\"`.

---

### 3.6 Lector EPUB y PDF (ReaderScreen, Overlays, HeroSplash, Reflow)
Fuentes: `app/src/main/java/com/flint/woodsreader/ui/reader/ReaderScreen.kt#L1-L450`, `ReaderScreenOverlays.kt#L1-L320`, `ReaderHeroSplash.kt#L1-L180`, `ReaderReflowOverlay.kt#L1-L150`.

El lector es el núcleo de Woods Reader y está construido en 3 capas superpuestas en un `Box(Modifier.fillMaxSize())`:

1. **Capa 0 (Base / Documento):**
   - En EPUB: `AndroidView` que aloja el WebView de Readium Navigator con soporte para renderizado paginado de dos columnas o scroll continuo.
   - En PDF: `PdfViewer` con soporte para desplazamiento vertical suave o páginas individuales con zoom multitáctil.
   - Detección de toques (`pointerInput`):
     - Toque izquierdo (0% a 25% del ancho): Página anterior.
     - Toque central (25% a 75% del ancho): Alterna visibilidad de barras de menú (`toggleControls()`).
     - Toque derecho (75% a 100% del ancho): Página siguiente.
     - Pulsación prolongada: Selecciona texto y activa `ReaderHighlightOverlay`.

2. **Capa 1 (Overlays de Lectura y Navegación):**
   - `ReaderHeroSplash`: Cubierta que se muestra brevemente durante los primeros 400ms al abrir un libro, mostrando la portada completa en alta resolución antes de transicionar al texto.
   - `PullToChapterIndicator`: Indicador visual con rebote táctil en los extremos del libro al intentar pasar más allá de la última o primera página.
   - `ReaderReflowOverlay`: Para documentos PDF con el modo de reflujo activado; extrae el texto puro y lo formatea como un flujo tipográfico idéntico a un EPUB.
   - `ReaderHighlightOverlay`: Barra de herramientas contextual que flota directamente sobre la selección de texto activa con 5 círculos de color para subrayar, icono de nota (`Icons.Filled.NoteAdd`), icono de copiar (`Icons.Filled.ContentCopy`), búsqueda en diccionario y compartir (`Icons.Filled.Share`).

3. **Capa 2 (Controles y Sistema de Audio TTS):**
   - `ReaderTopBar` (animada verticalmente con `slideInVertically(-100%)`).
   - `ReaderBottomBar` y `ReaderBottomSlider` (animadas con `slideOutVertically(100%)`).
   - `ReaderSideMenu` (menú flotante derecho translúcido).
   - `ReaderTeleprompterPill` (píldora flotante minimalista con indicador de WPM y frase en lectura).
   - `ReaderFloatingTtsButton` (cápsula flotante arrastrable de TTS).
   - `ReaderTtsLockOverlay` (modo pantalla bloqueada con visualizador de ondas y control de audio).

---

### 3.7 Estadísticas de Lectura (ReadingStatsScreen)
Fuente: `app/src/main/java/com/flint/woodsreader/ui/stats/ReadingStatsScreen.kt#L1-L340`.

a) **Árbol de Composables:**
```
Scaffold (
  containerColor = Theme.background,
  topBar = { TopAppBar (title = \"Estadísticas de Lectura\", colors = TopAppBarDefaults...) }
)
└── LazyColumn (modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp))
    ├── item: ReadingGoalHeroCard
    │   └── Card (colors = Theme.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Theme.border))
    │       └── Row (modifier = Modifier.padding(20.dp), verticalAlignment = CenterVertically)
    │           ├── CircularProgressIndicator (progress = goalProgress, strokeWidth = 8.dp, color = Theme.primary, trackColor = Theme.surfaceVariant, modifier = size(80.dp))
    │           ├── Spacer (width = 20.dp)
    │           └── Column
    │               ├── Text (\"Meta Diaria\", style = labelSmall, color = Theme.textSecondary)
    │               ├── Text (\"35 / 45 min\", style = headlineSmall, color = Theme.textPrimary, fontWeight = Bold)
    │               └── Text (\"¡Estás a solo 10 minutos de cumplir tu objetivo!\", style = bodySmall, color = Theme.primary)
    ├── item: ModernStatsGrid
    │   └── Row (modifier = fillMaxWidth(), horizontalArrangement = spacedBy(12.dp))
    │       ├── StatMetricCard (icon = Icons.Filled.LocalFireDepartment, label = \"Racha actual\", value = \"14 días\", tint = #FF9800)
    │       ├── StatMetricCard (icon = Icons.Filled.Speed, label = \"Velocidad media\", value = \"240 ppm\", tint = Theme.primary)
    │       └── StatMetricCard (icon = Icons.Filled.MenuBook, label = \"Libros leídos\", value = \"12\", tint = #42A5F5)
    ├── item: Heatmap12WeeksCard
    │   └── Card (colors = Theme.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Theme.border))
    │       └── Column (modifier = Modifier.padding(16.dp))
    │           ├── Text (\"Actividad (Últimas 12 Semanas)\", style = titleSmall, color = Theme.textPrimary)
    │           ├── Spacer (height = 12.dp)
    │           └── Canvas (modifier = Modifier.fillMaxWidth().height(110.dp)) [Dibuja 7 filas x 12 columnas de cuadros 10.dp con esquinas 2.dp, espaciado 3.dp, color interpolado de Theme.surfaceVariant a Theme.primary]
    ├── item: DailyWordCountBarChartCard
    │   └── Card (colors = Theme.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Theme.border))
    │       └── Column (modifier = Modifier.padding(16.dp))
    │           ├── Text (\"Palabras por día (Esta semana)\", style = titleSmall, color = Theme.textPrimary)
    │           ├── Spacer (height = 16.dp)
    │           └── Row (modifier = fillMaxWidth().height(120.dp), horizontalArrangement = SpaceBetween, verticalAlignment = Bottom)
    │               └── Día x 7 (Column con barra vertical de altura proporcional + etiqueta Lun..Dom)
    └── item: FormatComparisonCard
        └── Card (colors = Theme.surface, shape = RoundedCornerShape(16.dp))
            └── Column (modifier = Modifier.padding(16.dp))
                ├── Text (\"Distribución de Formatos\", style = titleSmall, color = Theme.textPrimary)
                └── Row (modifier = Modifier.padding(top = 12.dp))
                    ├── FormatoItem (\"EPUB\", \"82%\", Theme.primary)
                    └── FormatoItem (\"PDF\", \"18%\", Theme.secondary)
```

b) **Medidas:**
- Gráfico de calor de 12 semanas: 84 cuadros (`7 x 12`), tamaño del cuadro `10.dp`, separación `3.dp`.
- Barras de recuento de palabras: Ancho de barra `14.dp`, esquinas superiores `4.dp`, altura máxima `100.dp`.

---

### 3.8 Ajustes de la Aplicación (SettingsScreen)
Fuente: `app/src/main/java/com/flint/woodsreader/ui/settings/SettingsScreen.kt#L1-L310`.

a) **Árbol de Composables:**
```
Scaffold (
  containerColor = Theme.background,
  topBar = { TopAppBar (title = \"Ajustes\", navigationIcon = { IconButton(Icons.Filled.ArrowBack) }) }
)
└── LazyColumn (modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp))
    ├── SectionHeader (\"APARIENCIA Y TEMA\")
    ├── ThemeSelectorCard
    │   └── Row (modifier = fillMaxWidth().horizontalScroll(), horizontalArrangement = spacedBy(12.dp))
    │       └── ThemePreviewItem x 5 (HiddenForest, Day, Sepia, AMOLED, Sunset)
    ├── SectionHeader (\"EXPERIENCIA DE LECTURA\")
    ├── SettingToggleItem (icon = Icons.Filled.VolumeUp, title = \"Botones de volumen\", subtitle = \"Pasar página con las teclas físicas de volumen\", checked = true)
    ├── SettingToggleItem (icon = Icons.Filled.AutoMode, title = \"Mantener pantalla encendida\", subtitle = \"Evita que el dispositivo se apague durante la lectura\", checked = true)
    ├── SettingToggleItem (icon = Icons.Filled.ThreeDRotation, title = \"Efecto de paso de página 3D\", subtitle = \"Simula el doblado físico del papel con aceleración de hardware\", checked = true)
    ├── SettingStepperItem (icon = Icons.Filled.FormatSize, title = \"Tamaño de fuente por defecto\", value = \"18 sp\", onMinus = { ... }, onPlus = { ... })
    ├── SectionHeader (\"SÍNTESIS DE VOZ (TTS)\")
    ├── SettingNavigationItem (icon = Icons.Filled.RecordVoiceOver, title = \"Motor de voz predeterminado\", value = \"Microsoft Edge Neural\")
    ├── SettingNavigationItem (icon = Icons.Filled.DownloadCloud, title = \"Voces offline Sherpa ONNX\", value = \"3 descargadas\")
    ├── SectionHeader (\"SISTEMA Y ALMACENAMIENTO\")
    ├── SettingNavigationItem (icon = Icons.Filled.Folder, title = \"Carpetas sincronizadas\", value = \"4 directorios\")
    ├── SettingNavigationItem (icon = Icons.Filled.CleaningServices, title = \"Limpiar caché de renderizado\", value = \"128 MB\")
    ├── SettingNavigationItem (icon = Icons.Filled.WorkspacePremium, title = \"Woods Pro\", value = \"Activado (Permanente)\")
    └── FooterInfo
        └── Column (modifier = fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = CenterHorizontally)
            ├── Text (\"Woods Reader v2.4.0 (Build 240)\", style = labelSmall, color = Theme.textTertiary)
            └── Text (\"Licencia GPLv3 • Diseñado con Jetpack Compose\", style = labelSmall, color = Theme.textTertiary)
```

---

### 3.9 Grafo Semántico (GraphScreen, GraphCanvas)
Fuentes: `app/src/main/java/com/flint/woodsreader/ui/graph/GraphScreen.kt#L1-L280` y `GraphCanvas.kt#L1-L220`.

Visualizador de red interactiva que mapea entidades (Personajes, Lugares, Objetos, Facciones, Conceptos) y sus relaciones narrativas en un canvas con soporte de gestos de escala y paneo.

a) **Árbol de Composables:**
```
Scaffold (
  containerColor = Theme.background,
  topBar = {
    TopAppBar (
      title = \"Grafo de Conocimiento\",
      actions = {
        IconButton (Icons.Filled.FilterList) [Filtro de tipos de entidad]
        IconButton (Icons.Filled.Search) [Buscar nodo]
        IconButton (Icons.Filled.CenterFocusStrong) [Re-centrar cámara]
      }
    )
  }
)
└── Box (modifier = Modifier.fillMaxSize())
    ├── GraphCanvas (nodes = visibleNodes, edges = visibleEdges, scale = zoomScale, pan = panOffset, onNodeClick = { ... })
    ├── FilterChipsOverlay (modifier = Modifier.align(TopStart).padding(16.dp))
    │   └── Row (horizontalArrangement = spacedBy(6.dp))
    │       └── Chip x 5 (Personaje, Lugar, Objeto, Facción, Concepto)
    └── SelectedEntityBottomSheet (Visible si selectedNode != null)
```

---

### 3.10 Candidatos del Grafo (CandidatesScreen)
Fuente: `app/src/main/java/com/flint/woodsreader/ui/graph/CandidatesScreen.kt#L1-L210`.

Bandeja de moderación semántica donde las entidades extraídas automáticamente por procesamiento lingüístico o expresiones regulares son validadas por el usuario.

a) **Árbol de Composables:**
```
Scaffold (
  containerColor = Theme.background,
  topBar = { TopAppBar (title = \"Candidatos detectados (${candidates.size})\") }
)
└── LazyColumn (modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp))
    └── items (candidates) [CandidateCard con SwipeToDismiss]
        └── SwipeToDismiss (
            background = { DismissBackground(direction) },
            dismissContent = {
              Card (colors = Theme.surface, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Theme.border))
              └── Row (modifier = Modifier.padding(16.dp), verticalAlignment = CenterVertically)
                  ├── Box (modifier = size(12.dp).clip(CircleShape).background(entityColor(candidate.type)))
                  ├── Spacer (width = 12.dp)
                  ├── Column (modifier = Modifier.weight(1f))
                  │   ├── Text (candidate.name, style = titleMedium, color = Theme.textPrimary)
                  │   ├── Text (\"${candidate.mentionCount} menciones • Tipo: ${candidate.type.name}\", style = bodySmall, color = Theme.textSecondary)
                  │   └── Text (\"Ejemplo: \"...${candidate.sampleContext}...\"\", style = bodySmall, fontStyle = Italic, color = Theme.textTertiary)
                  ├── IconButton (Icons.Filled.Edit, tint = Theme.primary) [Cambiar tipo]
                  ├── IconButton (Icons.Filled.CallMerge, tint = #42A5F5) [Fusionar con otro]
                  └── IconButton (Icons.Filled.Check, tint = #4CAF50) [Aprobar e incorporar al grafo]
            }
        )
```
"""
