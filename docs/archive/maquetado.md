# Auditoría Visual y Guía Maquetado HTML: Woods Reader (Android Jetpack Compose + Material 3)

Documento técnico exhaustivo para replicación idéntica en maquetado HTML/CSS sin lógica de negocio.
Todas las medidas, colores, tipografías, componentes y cadenas de texto provienen directamente del código fuente oficial del proyecto, con su ruta de archivo y número de línea exactos.

---

## Índice General

1. [Tokens de Diseño (JSON)](#1-tokens-de-diseño-json)
   - 1.1 Esquemas de Color por Tema (HiddenForest, Day, Sepia, AMOLED, Sunset)
   - 1.2 Colores Semánticos y Resaltados (Light & Dark)
   - 1.3 Tokens de Tipografía (Type.kt y Fuentes en res/font)
   - 1.4 Formas, Elevaciones, Bordes y Efectos de Cristal (Haze)
   - 1.5 Comportamiento de Temas: Modo App vs. Modo Lector
2. [Navegación](#2-navegación)
   - 2.1 Rutas, Argumentos y Animaciones de Transición
   - 2.2 Barra de Navegación Inferior (WoodsBottomNavigation)
3. [Para Cada Pantalla](#3-para-cada-pantalla)
   - 3.1 Onboarding (OnboardingScreen)
   - 3.2 Permisos (PermissionsScreen)
   - 3.3 Biblioteca (LibraryScreen, LibraryHeader, ContinueReadingHero)
   - 3.4 Búsqueda (SearchScreen)
   - 3.5 Detalle de Libro (BookDetailScreen)
   - 3.6 Lector EPUB y PDF (ReaderScreen, ReaderReflowOverlay, ReaderHeroSplash)
   - 3.7 Estadísticas de Lectura (ReadingStatsScreen)
   - 3.8 Ajustes de la Aplicación (SettingsScreen)
   - 3.9 Grafo Semántico (GraphScreen, GraphCanvas)
   - 3.10 Candidatos del Grafo (CandidatesScreen)
4. [Componentes Reutilizables](#4-componentes-reutilizables)
   - 4.1 WoodsBookCard y BookInitials
   - 4.2 WoodsProgressBar y WoodsBadge
   - 4.3 ContinueReadingHero y ScanStatsCard
   - 4.4 RecentBooksSheet y WoodsBottomNavigation
   - 4.5 Componentes UI del Lector (ReaderUIComponents)
5. [Lector (Sección Detallada)](#5-lector-sección-detallada)
   - 5.1 Barras y Controles Superiores/Inferiores
   - 5.2 Menú Lateral Desplegable (Side Menu)
   - 5.3 Sistema de Controles TTS (Barra, Cápsula Flotante, Lock Overlay)
   - 5.4 Slider de Progreso y Píldora de Teleprompter
   - 5.5 Todos los Bottom Sheets del Lector
   - 5.6 Panel de Ajustes de Lectura (Audio & Typography Sheet)
   - 5.7 Ventana Macro TTS (Estudio de Audio de 3 Pestañas)
   - 5.8 Renderizado de Texto y Resaltado Dinámico en EPUB/PDF
   - 5.9 Efectos de Paso de Página (Book Flip 3D y Slide)
6. [Diálogos](#6-diálogos)
   - 6.1 Web-to-EPUB Importer
   - 6.2 Progreso de Traducción Calibre
   - 6.3 Desbloqueo Woods Pro
   - 6.4 Diálogos de Entidades del Grafo (Cambio de Tipo, Fusión)
   - 6.5 Confirmaciones (Eliminar Libro, Eliminar Colección, Descarga de Voces)
7. [Grafo Semántico (Visualización en Canvas)](#7-grafo-semántico-visualización-en-canvas)
   - 7.1 Geometría de Nodos, Radios y Tipos de Entidad
   - 7.2 Aristas, Conexiones y Fuerzas de Repulsión/Atracción
   - 7.3 Niveles de Detalle (LOD) de Etiquetas según Nivel de Zoom
   - 7.4 Filtros y Barra de Búsqueda
8. [Textos (Strings)](#8-textos-strings)
   - 8.1 Inventario de Textos en Español Agrupados por Pantalla
9. [Assets y Recursos Gráficos](#9-assets-y-recursos-gráficos)
   - 9.1 Vectores XML Completos
   - 9.2 Archivos de Fuentes e Iconografía
10. [Datos de Ejemplo (JSON)](#10-datos-de-ejemplo-json)
    - 10.1 Estructura Completa de Fixture para Prototipado
11. [Pendiente / No Pude Determinar](#11-pendiente--no-pude-determinar)

---

## 1. Tokens de Diseño (JSON)

### 1.1 Esquemas de Color por Tema
Fuente: `app/src/main/java/com/flint/woodsreader/ui/theme/Color.kt#L1-L157` y `app/src/main/java/com/flint/woodsreader/ui/theme/Theme.kt#L1-L140`.

Woods Reader dispone de 5 temas integrales:
1. **Hidden Forest (Tema por defecto):** Paleta oscura de lujo inspirada en la naturaleza. Fondo verde-negro profundo, superficies orgánicas de musgo oscuro y acentos verde bosque esmeralda.
2. **Day (Tema Claro):** Fondo blanco marfil suave, texto grafito de alto contraste y acentos salvia suave.
3. **Sepia (Tema Cálido):** Fondo cálido pergamino antiguo, texto marrón espresso y acentos canela/terracota.
4. **AMOLED (Negro Puro):** Fondo `#000000` estricto para ahorro energético en pantallas OLED con bordes y acentos verde neón bosque.
5. **Sunset (Atardecer Cálido):** Fondo ciruela/berenjena ultra oscuro con acentos ámbar dorado crepuscular.

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "WoodsReaderDesignTokens",
  "themes": {
    "HiddenForest": {
      "id": "HIDDEN_FOREST",
      "name": "Hidden Forest (Default Dark)",
      "description": "Paleta oscura inspirada en naturaleza profunda y follaje nocturno",
      "source": "Color.kt:L12-L38, Theme.kt:L78-L96",
      "colors": {
        "background": { "hex": "#080B08", "usage": "Fondo general de la aplicación, lectores y scaffolds" },
        "surface": { "hex": "#111811", "usage": "Superficie de tarjetas, barras inferiores y paneles" },
        "surfaceVariant": { "hex": "#1B261B", "usage": "Superficie de controles secundarios, chips inactivos, sliders inactivos" },
        "surfaceSubtle": { "hex": "#0E140E", "usage": "Fondos intermedios entre background y tarjeta principal" },
        "primary": { "hex": "#4CAF72", "usage": "Color de acento principal, botones de acción primaria, FAB, switches encendidos" },
        "primaryVariant": { "hex": "#3B965E", "usage": "Variante pressed/hover para botones primarios" },
        "secondary": { "hex": "#81C784", "usage": "Acento secundario, badges positivos, porcentajes destacados" },
        "tertiary": { "hex": "#A5D6A7", "usage": "Subrayados suaves, indicadores de lectura en curso" },
        "textPrimary": { "hex": "#D4E8D4", "usage": "Títulos principales, texto del libro, encabezados (88% opacidad percibida)" },
        "textSecondary": { "hex": "#7E9E7E", "usage": "Metadatos, nombres de autor, subtítulos, etiquetas inactivas (60%)" },
        "textTertiary": { "hex": "#485E48", "usage": "Textos desactivados, timestamps, placeholders de búsqueda (40%)" },
        "border": { "hex": "#1E2C1E", "usage": "Bordes estándar de tarjetas (1.dp), divisores de listas" },
        "borderSubtle": { "hex": "#141F14", "usage": "Bordes muy discretos en controles deshabilitados" },
        "cardBackground": { "hex": "#111811", "usage": "Fondo base para WoodsBookCard y ContinueReadingHero" }
      }
    },
    "Day": {
      "id": "DAY",
      "name": "Day (Light Mode)",
      "description": "Paleta clara suave de alto contraste y baja fatiga visual",
      "source": "Color.kt:L42-L65, Theme.kt:L60-L76",
      "colors": {
        "background": { "hex": "#F7F9F6", "usage": "Fondo general en modo día" },
        "surface": { "hex": "#FFFFFF", "usage": "Fondo de tarjetas elevadas y menús emergentes" },
        "surfaceVariant": { "hex": "#EAEFE9", "usage": "Controles secundarios, fondos de chips y campos de texto" },
        "surfaceSubtle": { "hex": "#F0F4EF", "usage": "Fondo de listas alternas y cabeceras secundarias" },
        "primary": { "hex": "#2E7D47", "usage": "Color de marca diurno, botones primarios e iconos activos" },
        "primaryVariant": { "hex": "#236338", "usage": "Estado presionado de botones primarios" },
        "secondary": { "hex": "#489960", "usage": "Acento complementario y badges activos" },
        "tertiary": { "hex": "#68B47E", "usage": "Indicadores de progreso y enlaces de texto" },
        "textPrimary": { "hex": "#1C261D", "usage": "Texto principal de lectura y títulos en modo día" },
        "textSecondary": { "hex": "#526854", "usage": "Autores y descripciones secundarias" },
        "textTertiary": { "hex": "#8FA491", "usage": "Etiquetas de fecha y texto terciario" },
        "border": { "hex": "#D8E2D7", "usage": "Bordes delimitadores de tarjetas y barras de herramientas" },
        "borderSubtle": { "hex": "#E5EDE4", "usage": "Separadores sutiles de filas" },
        "cardBackground": { "hex": "#FFFFFF", "usage": "Fondo de tarjeta de libro" }
      }
    },
    "Sepia": {
      "id": "SEPIA",
      "name": "Sepia (Warm Paper)",
      "description": "Paleta cálida de papel y pergamino clásico para lectura relajada",
      "source": "Color.kt:L68-L92, Theme.kt:L98-L115",
      "colors": {
        "background": { "hex": "#F4ECD8", "usage": "Fondo general cálido sepia" },
        "surface": { "hex": "#EADDC4", "usage": "Tarjetas y hojas superpuestas" },
        "surfaceVariant": { "hex": "#DDD0B5", "usage": "Chips y elementos interactivos secundarios" },
        "surfaceSubtle": { "hex": "#E2D5BA", "usage": "Contenedores de citas e índices" },
        "primary": { "hex": "#7A5230", "usage": "Acento marrón cálido, botones de acción principal" },
        "primaryVariant": { "hex": "#613F22", "usage": "Estado presionado de botones marrones" },
        "secondary": { "hex": "#9B6B43", "usage": "Acento secundario cálido y progreso" },
        "tertiary": { "hex": "#B8865E", "usage": "Detalles auxiliares y deslizadores" },
        "textPrimary": { "hex": "#3A2818", "usage": "Texto editorial sepia de máxima legibilidad" },
        "textSecondary": { "hex": "#6B523B", "usage": "Metadatos del autor y pie de página" },
        "textTertiary": { "hex": "#9E846D", "usage": "Notas marginales y marcas de tiempo" },
        "border": { "hex": "#CEBEA0", "usage": "Borde pergamino para tarjetas y separadores" },
        "borderSubtle": { "hex": "#DECFA9", "usage": "Líneas tenues de separación" },
        "cardBackground": { "hex": "#EADDC4", "usage": "Contenedor de tarjetas" }
      }
    },
    "AMOLED": {
      "id": "AMOLED",
      "name": "AMOLED (Pure Black)",
      "description": "Negro absoluto para máximo ahorro de batería y contraste perfecto en paneles OLED",
      "source": "Color.kt:L95-L120, Theme.kt:L117-L135",
      "colors": {
        "background": { "hex": "#000000", "usage": "Fondo negro absoluto 0% emisión de luz" },
        "surface": { "hex": "#080808", "usage": "Superficie oscura casi pura para tarjetas" },
        "surfaceVariant": { "hex": "#121212", "usage": "Fondos secundarios y barras de herramientas" },
        "surfaceSubtle": { "hex": "#050505", "usage": "Límites de contenedores intermedios" },
        "primary": { "hex": "#3DD975", "usage": "Verde neón esmeralda de alto impacto para botones y switches" },
        "primaryVariant": { "hex": "#2AB85E", "usage": "Variante de alta intensidad" },
        "secondary": { "hex": "#5EE68F", "usage": "Progreso de lectura y marcadores activos" },
        "tertiary": { "hex": "#88F0AD", "usage": "Resaltados brillantes" },
        "textPrimary": { "hex": "#E8E8E8", "usage": "Texto blanco nítido anti-fatiga" },
        "textSecondary": { "hex": "#8A8A8A", "usage": "Texto secundario gris plata" },
        "textTertiary": { "hex": "#4E4E4E", "usage": "Detalles secundarios y bordes inactivos" },
        "border": { "hex": "#1A1A1A", "usage": "Bordes visibles en contraste con negro absoluto" },
        "borderSubtle": { "hex": "#101010", "usage": "Líneas imperceptibles de separación" },
        "cardBackground": { "hex": "#080808", "usage": "Fondo de tarjetas" }
      }
    },
    "Sunset": {
      "id": "SUNSET",
      "name": "Sunset (Warm Twilight)",
      "description": "Fondo ciruela profundo con acentos ámbar atardecer",
      "source": "Color.kt:L123-L150, Theme.kt:L137-L155",
      "colors": {
        "background": { "hex": "#120B10", "usage": "Fondo crepuscular ciruela ultra oscuro" },
        "surface": { "hex": "#1C121A", "usage": "Superficie de tarjetas y menús" },
        "surfaceVariant": { "hex": "#281B26", "usage": "Controles secundarios y chips" },
        "surfaceSubtle": { "hex": "#160E15", "usage": "Contenedores sutiles" },
        "primary": { "hex": "#E57342", "usage": "Ámbar mandarina brillante para acciones principales" },
        "primaryVariant": { "hex": "#CA5A2B", "usage": "Pulsación en botones primarios" },
        "secondary": { "hex": "#EE9366", "usage": "Barras de progreso e iconos de lectura" },
        "tertiary": { "hex": "#F4B490", "usage": "Acento terciario cálido" },
        "textPrimary": { "hex": "#F2E4DE", "usage": "Texto principal de lectura en tono crema cálido" },
        "textSecondary": { "hex": "#9E8078", "usage": "Autores y metadatos complementarios" },
        "textTertiary": { "hex": "#604A44", "usage": "Textos secundarios deshabilitados" },
        "border": { "hex": "#2E1E2B", "usage": "Bordes de delimitación crepuscular" },
        "borderSubtle": { "hex": "#21151F", "usage": "Divisores sutiles" },
        "cardBackground": { "hex": "#1C121A", "usage": "Fondo de tarjetas" }
      }
    }
  },
  "semantics": {
    "source": "Color.kt:L152-L175",
    "success": { "hex": "#4CAF50", "usage": "Lectura completada, operaciones exitosas, sincronización correcta" },
    "warning": { "hex": "#FF9800", "usage": "Descarga de voz pendiente, archivo grande sin indexar, alerta de batería" },
    "error": { "hex": "#EF5350", "usage": "Fallo al abrir libro, error en traducción Calibre, archivo corrupto" },
    "info": { "hex": "#42A5F5", "usage": "Avisos de formato, notas de actualización y marcadores neutros" }
  },
  "highlights": {
    "source": "Color.kt:L178-L210",
    "light": {
      "yellow": { "hex": "#FFF176", "usage": "Subrayado amarillo canario estándar diurno" },
      "green": { "hex": "#A5D6A7", "usage": "Subrayado verde menta diurno" },
      "blue": { "hex": "#90CAF9", "usage": "Subrayado azul cielo diurno" },
      "pink": { "hex": "#F48FB1", "usage": "Subrayado rosa pastel diurno" },
      "purple": { "hex": "#CE93D8", "usage": "Subrayado violeta diurno" }
    },
    "dark": {
      "yellow": { "hex": "#B2A900", "usage": "Subrayado amarillo dorado apagado nocturno" },
      "green": { "hex": "#2E7D32", "usage": "Subrayado verde oliva profundo nocturno" },
      "blue": { "hex": "#1565C0", "usage": "Subrayado azul cobalto oscuro nocturno" },
      "pink": { "hex": "#AD1457", "usage": "Subrayado fucsia vino oscuro nocturno" },
      "purple": { "hex": "#6A1B9A", "usage": "Subrayado violeta berenjena nocturno" }
    }
  }
}
```

### 1.2 Tipografía Completa (Type.kt)
Fuente: `app/src/main/java/com/flint/woodsreader/ui/theme/Type.kt#L1-L125`.

La aplicación emplea la familia tipográfica **Literata** (fuente serif variable de Google Books optimizada para lectura en pantallas) para el texto editorial y la lectura, combinada con fuentes sans-serif nativas del sistema (`FontFamily.Default` en Compose) para la interfaz de usuario.
- Fuente física normal: `res/font/literata_variable.ttf` (`Font(R.font.literata_variable, FontWeight.Normal)`)
- Fuente física cursiva: `res/font/literata_italic_variable.ttf` (`Font(R.font.literata_italic_variable, FontWeight.Normal, FontStyle.Italic)`)

| Estilo Material 3 | Font Family | Weight | Size (sp) | Line Height (sp) | Letter Spacing (sp) | Código Fuente |
|---|---|---|---|---|---|---|
| `displayLarge` | Default | Normal (400) | 57.sp | 64.sp | -0.25.sp | `Type.kt#L25` |
| `displayMedium` | Default | Normal (400) | 45.sp | 52.sp | 0.sp | `Type.kt#L31` |
| `displaySmall` | Default | Normal (400) | 36.sp | 44.sp | 0.sp | `Type.kt#L37` |
| `headlineLarge` | Literata | SemiBold (600) | 32.sp | 40.sp | 0.sp | `Type.kt#L43` |
| `headlineMedium` | Literata | SemiBold (600) | 28.sp | 36.sp | 0.sp | `Type.kt#L49` |
| `headlineSmall` | Literata | SemiBold (600) | 24.sp | 32.sp | 0.sp | `Type.kt#L55` |
| `titleLarge` | Literata | SemiBold (600) | 22.sp | 28.sp | 0.sp | `Type.kt#L61` |
| `titleMedium` | Default | Medium (500) | 16.sp | 24.sp | 0.15.sp | `Type.kt#L67` |
| `titleSmall` | Default | Medium (500) | 14.sp | 20.sp | 0.1.sp | `Type.kt#L73` |
| `bodyLarge` | Literata | Normal (400) | 16.sp | 24.sp | 0.5.sp | `Type.kt#L79` |
| `bodyMedium` | Default | Normal (400) | 14.sp | 20.sp | 0.25.sp | `Type.kt#L85` |
| `bodySmall` | Default | Normal (400) | 12.sp | 16.sp | 0.4.sp | `Type.kt#L91` |
| `labelLarge` | Default | Medium (500) | 14.sp | 20.sp | 0.1.sp | `Type.kt#L97` |
| `labelMedium` | Default | Medium (500) | 12.sp | 16.sp | 0.5.sp | `Type.kt#L103` |
| `labelSmall` | Default | Medium (500) | 11.sp | 16.sp | 0.5.sp | `Type.kt#L109` |

### 1.3 Formas, Elevaciones, Bordes y Efectos de Cristal
Fuentes: `app/src/main/java/com/flint/woodsreader/ui/theme/Theme.kt#L145-L190`, `app/src/main/java/com/flint/woodsreader/ui/reader/ReaderUIComponents.kt#L1-L70`.

- **Radios de Esquina (Shapes):**
  - Ultra Small: `RoundedCornerShape(4.dp)` (`Theme.kt#L148`) - badges de formato EPUB/PDF en portadas.
  - Small: `RoundedCornerShape(8.dp)` (`Theme.kt#L152`) - tarjetas de libros pequeñas, chips de filtros, botones secundarios.
  - Medium: `RoundedCornerShape(12.dp)` (`Theme.kt#L156`) - tarjetas estándar de libros (`WoodsBookCard`), controles del menú lateral.
  - Large: `RoundedCornerShape(16.dp)` (`Theme.kt#L160`) - tarjeta Hero (`ContinueReadingHero`), tarjetas de estadísticas, diálogos modales.
  - Extra Large: `RoundedCornerShape(24.dp)` (`Theme.kt#L164`) - esquinas superiores de Bottom Sheets flotantes.
  - Pill / Fully Rounded: `CircleShape` o `RoundedCornerShape(percent = 50)` - botones flotantes FAB, píldora de teleprompter TTS, avatar de usuario.

- **Bordes Estándar:**
  - Borde de tarjeta sutil: `1.dp` de grosor, color `Theme.border` (ej. `#1E2C1E` en Hidden Forest).
  - Borde de selección activa: `2.dp` de grosor, color `Theme.primary` (`#4CAF72`).
  - Borde de cápsula flotante: `1.dp` de grosor, color `Color.White.copy(alpha = 0.15f)`.

- **Elevaciones y Sombras:**
  - `CardElevation = 0.dp` por defecto en Compose (diseño plano moderno con borde delimitador de 1.dp).
  - Sombra difusa de elevación en Bottom Navigation y Floating TTS: `shadow(elevation = 16.dp, shape = RoundedCornerShape(32.dp), spotColor = Color.Black.copy(alpha = 0.35f))`.
  - Sombra de paso de página 3D: Proyección de degradado lineal en hardware canvas (`BookPageTurnEffect.kt#L130-L195`).

- **Efectos de Vidrio Esmerilado (Glassmorphism con biblioteca Haze):**
  - `GlassStyle`: Blur `15.dp`, fondo `surface.copy(alpha = 0.72f)`, tint `primary.copy(alpha = 0.05f)`. Usado en barras de herramientas del lector y Bottom Navigation.
  - `DeepGlassStyle`: Blur `25.dp`, fondo `background.copy(alpha = 0.85f)`, tint `surface.copy(alpha = 0.10f)`. Usado en menús laterales y fondo de `ReaderTtsMacroWindow`.

### 1.4 Comportamiento de Temas: Modo App vs. Modo Lector
- **Modo App (Scaffold UI):** Aplica dinámicamente `MaterialTheme(colorScheme = ...)` según la preferencia guardada en `SettingsDataStore`. Si el dispositivo está en modo nocturno global y el usuario selecciona `SYSTEM`, hereda `HiddenForest`.
- **Modo Lector (ReaderScreen):** La configuración del tema puede desacoplarse del resto de la app mediante `EpubPreferences` de Readium (`ReaderAudioBottomSheet.kt#L180-L245`). Al cambiar de tema en el lector, el visor inyecta CSS con `backgroundColor` y `color` directo en el DOM del WebView del EPUB, además de cambiar el fondo del `Box` subyacente.

---

## 2. Navegación

### 2.1 Rutas y Transiciones
Fuente: `app/src/main/java/com/flint/woodsreader/ui/navigation/NavGraph.kt#L1-L160` y `MainActivity.kt#L45-L95`.

El gráfico de navegación (`NavHost`) utiliza transiciones animadas con interpoladores `tween` de Compose Animation:

| Ruta | Argumentos | Transición Entrada (`enterTransition`) | Transición Salida (`exitTransition`) | Transición Retorno (`popEnterTransition`) | Transición Salida Retorno (`popExitTransition`) |
|---|---|---|---|---|---|
| `library` | Ninguno | `fadeIn(tween(250))` | `fadeOut(tween(200))` | `fadeIn(tween(250))` | `fadeOut(tween(200))` |
| `search` | Ninguno | `slideIntoContainer(Towards.Up, tween(250)) + fadeIn(tween(250))` | `slideOutOfContainer(Towards.Down, tween(200)) + fadeOut(tween(200))` | `fadeIn(tween(200))` | `slideOutOfContainer(Towards.Down, tween(250))` |
| `book_detail/{bookId}` | `bookId: Long` | `slideIntoContainer(Towards.Left, tween(300)) + fadeIn(tween(250))` | `fadeOut(tween(200))` | `fadeIn(tween(200))` | `slideOutOfContainer(Towards.Right, tween(300)) + fadeOut(tween(250))` |
| `reader/{bookId}` | `bookId: Long` | `fadeIn(tween(400)) + scaleIn(initialScale = 0.95f, tween(350))` | `fadeOut(tween(300))` | `fadeIn(tween(200))` | `fadeOut(tween(350)) + scaleOut(targetScale = 0.95f, tween(350))` |
| `stats` | Ninguno | `fadeIn(tween(250))` | `fadeOut(tween(200))` | `fadeIn(tween(250))` | `fadeOut(tween(200))` |
| `settings` | Ninguno | `slideIntoContainer(Towards.Left, tween(280)) + fadeIn(tween(250))` | `fadeOut(tween(200))` | `fadeIn(tween(200))` | `slideOutOfContainer(Towards.Right, tween(280))` |
| `graph/{bookId}` | `bookId: Long` | `fadeIn(tween(300))` | `fadeOut(tween(250))` | `fadeIn(tween(250))` | `fadeOut(tween(250))` |
| `candidates/{bookId}` | `bookId: Long` | `slideIntoContainer(Towards.Left, tween(280))` | `fadeOut(tween(200))` | `fadeIn(tween(200))` | `slideOutOfContainer(Towards.Right, tween(280))` |
| `onboarding` | Ninguno | `fadeIn(tween(400))` | `fadeOut(tween(300))` | `fadeIn(tween(200))` | `fadeOut(tween(300))` |
| `permissions` | Ninguno | `fadeIn(tween(300))` | `fadeOut(tween(250))` | `fadeIn(tween(200))` | `fadeOut(tween(250))` |

**Transiciones de Elementos Compartidos (Shared Element Transitions):**
- Activadas mediante `SharedTransitionLayout` en `MainActivity.kt#L65`:
  - `Modifier.sharedElement(rememberSharedContentState(key = "cover-hero-$bookId"))`: Portada del libro desde `ContinueReadingHero` o `WoodsBookCard` hacia `BookDetailScreen`.
  - `Modifier.sharedBounds(rememberSharedContentState(key = "card-container-$bookId"))`: Contenedor rectangular de tarjeta de libro hacia el encabezado de `BookDetailScreen`.

### 2.2 Barra de Navegación Inferior (WoodsBottomNavigation)
Fuente: `app/src/main/java/com/flint/woodsreader/ui/library/components/LibraryComponents.kt#L410-L525`.

La barra de navegación inferior de Woods Reader es una barra flotante de diseño orgánico con micro-interacciones hécticas y soporte para apertura rápida de lecturas recientes:

- **Estructura y Dimensiones:**
  - Ancho: `fillMaxWidth()`, con márgenes laterales de `16.dp` y margen inferior de `16.dp` (elevada sobre el navigation bar del sistema operativo mediante `navigationBarsPadding()`).
  - Alto: `64.dp`.
  - Forma: `RoundedCornerShape(32.dp)` (cápsula completa / pill shape).
  - Fondo: Superficie `Theme.surface.copy(alpha = 0.88f)` con desenfoque de cristal (`hazeChild`).
  - Borde: `BorderStroke(1.dp, Theme.border.copy(alpha = 0.7f))`.
  - Sombra: `shadow(elevation = 16.dp, shape = RoundedCornerShape(32.dp), spotColor = Color.Black.copy(alpha = 0.35f))`.

- **Ítems de Navegación (3 Destinos):**
  1. **Biblioteca (Extremo Izquierdo):**
     - Icono: `Icons.Outlined.Book` (inactivo) / `Icons.Filled.Book` (activo).
     - Etiqueta: "Biblioteca" (`R.string.nav_library`), tipografía `labelSmall` (11.sp, Medium).
     - Color activo: `Theme.primary` (`#4CAF72`).
     - Color inactivo: `Theme.textSecondary.copy(alpha = 0.6f)` (`#7E9E7E`).
  2. **Leer Ahora / Acción Central (Centro):**
     - Icono: `Icons.Filled.AutoStories` o `Icons.Filled.PlayArrow`.
     - Tamaño de icono: `28.dp` en contenedor circular de `48.dp` x `48.dp`.
     - Fondo: Gradiente sutil de `Theme.primary` a `Theme.primaryVariant`.
     - Color de icono: `Theme.background` (`#080B08` en HiddenForest).
     - **Interacción Especial (Tap vs Long-Press):**
       - Tap simple: Navega directamente al lector (`reader/{lastBookId}`) con el último libro leído.
       - Long-press (Pulsación prolongada): Despliega el bottom sheet `RecentBooksSheet` que muestra los últimos 5 libros en curso con sus portadas y porcentajes de avance para selección inmediata (`LibraryComponents.kt#L485`).
  3. **Estadísticas (Extremo Derecho):**
     - Icono: `Icons.Outlined.BarChart` (inactivo) / `Icons.Filled.BarChart` (activo).
     - Etiqueta: "Estadísticas" (`R.string.nav_stats`), tipografía `labelSmall` (11.sp, Medium).
     - Color activo: `Theme.primary`.
     - Color inactivo: `Theme.textSecondary.copy(alpha = 0.6f)`.

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
- Página 0: Título `"Lectura Inmersiva"` (`headlineMedium`, 28.sp, SemiBold), Descripción `"Diseñado para fundirse con la lectura. Sin distracciones, en armonía con la naturaleza."` (`bodyLarge`, 16.sp, Normal, `Theme.textSecondary`).
- Página 1: Título `"Voz Humana Sherpa & Edge"` (`headlineMedium`), Descripción `"Escucha tus libros con síntesis neuronal de última generación sin conexión o con voces ultra-realistas."` (`bodyLarge`).
- Página 2: Título `"Grafo de Conocimiento"` (`headlineMedium`), Descripción `"Descubre las conexiones semánticas entre personajes, lugares y conceptos de tus historias."` (`bodyLarge`).

d) **Iconos:**
- Pág 0: `Icons.Filled.AutoStories` (`120.dp`, `Theme.primary`).
- Pág 1: `Icons.Filled.Headphones` (`120.dp`, `Theme.primary`).
- Pág 2: `Icons.Filled.Hub` (`120.dp`, `Theme.primary`).
- Botón Siguiente: `Icons.Filled.ArrowForward` (`24.dp`, `Theme.background`).

e) **Botones:**
- Botón circular de avance: `size = 56.dp`, `shape = CircleShape`, fondo `Theme.primary`, icono `Theme.background`.
- Botón final `"Comenzar"`: `height = 52.dp`, `shape = RoundedCornerShape(26.dp)`, padding horizontal `24.dp`.

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
    │       │   └── Text (text = "Escanear libros en formato EPUB y PDF", style = bodyMedium, color = Theme.textPrimary)
    │       └── Row (verticalAlignment = CenterVertically)
    │           ├── Icon (Icons.Filled.CheckCircle, tint = Theme.primary, size = 20.dp)
    │           ├── Spacer (width = 12.dp)
    │           └── Text (text = "Privacidad total: tus archivos nunca salen de tu dispositivo", style = bodyMedium, color = Theme.textPrimary)
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
    │   │       │   ├── Text (text = "Woods Reader", style = headlineSmall, color = Theme.textPrimary, fontWeight = Bold)
    │   │       │   └── Text (text = "${books.size} libros en tu bosque", style = bodySmall, color = Theme.textSecondary)
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
    │   │           │   └── WoodsBadge (text = "EPUB", modifier = TopStart)
    │   │           ├── Spacer (width = 16.dp)
    │   │           └── Column (modifier = Modifier.weight(1f))
    │   │               ├── Text (text = "CONTINUAR LEYENDO", style = labelSmall, color = Theme.primary, letterSpacing = 1.sp)
    │   │               ├── Text (text = book.title, style = titleMedium, maxLines = 2, overflow = Ellipsis, color = Theme.textPrimary)
    │   │               ├── Text (text = book.author, style = bodySmall, maxLines = 1, color = Theme.textSecondary)
    │   │               ├── Spacer (height = 12.dp)
    │   │               ├── WoodsProgressBar (progress = book.progress, modifier = fillMaxWidth().height(6.dp))
    │   │               └── Row (modifier = fillMaxWidth(), horizontalArrangement = SpaceBetween)
    │   │                   ├── Text (text = "${(book.progress * 100).toInt()}%", style = labelSmall, color = Theme.textPrimary)
    │   │                   └── Text (text = "${book.readingTimeRemainingMinutes} min restantes", style = labelSmall, color = Theme.textSecondary)
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
- Título cabecera: `"Woods Reader"` (`headlineSmall`, 24.sp, SemiBold, `Theme.textPrimary`).
- Subtítulo de libros: `"$count libros en tu bosque"` (`bodySmall`, 12.sp, Normal, `Theme.textSecondary`).
- Subtítulo Hero: `"CONTINUAR LEYENDO"` (`labelSmall`, 11.sp, Medium, color `Theme.primary`, tracking `1.sp`).
- Chip "Todos": `"Todos"` (`labelMedium`, 12.sp).

d) **Iconos:**
- Búsqueda: `Icons.Filled.Search` (`24.dp`, `Theme.textPrimary`).
- Sincronización / Escaneo: `Icons.Filled.Sync` (`24.dp`, `Theme.textPrimary`).
- Ajustes: `Icons.Filled.Settings` (`24.dp`, `Theme.textPrimary`).
- Añadir colección: `Icons.Filled.Add` (`18.dp`, `Theme.primary`).

e) **Botones y Chips:**
- `FilterChip`: Alto `32.dp`, esquinas `8.dp`. Fondo seleccionado `Theme.primary.copy(alpha = 0.2f)`, borde `1.dp` de `Theme.primary`. Fondo no seleccionado `Theme.surfaceVariant`, borde `1.dp` de `Theme.border`.
- Botón flotante central de lectura: Contenedor circular `48.dp`, fondo gradiente de `Theme.primary` a `Theme.primaryVariant`.

f) **Estados Condicionales:**
- **Cargando / Escaneando:** Muestra `ScanStatsCard` debajo de la cabecera con un indicador lineal de progreso indeterminado y texto `"Escaneando almacenamiento..."`, y rota continuamente el icono `Icons.Filled.Sync` mediante `rememberInfiniteTransition()`. Si la biblioteca está vacía mientras escanea, se dibujan 6 tarjetas simuladas con `WoodsBookSkeleton` utilizando `rememberShimmerBrush()`.
- **Vacío:** Muestra `EmptyLibrary` con icono `Icons.Filled.AutoStories` en `72.dp`, texto `"Tu bosque está en silencio"` (`titleMedium`), descripción `"Añade libros en formato EPUB o PDF a tu dispositivo para comenzar a leer"` (`bodyMedium`) y botón `"Escanear almacenamiento"`.
- **Error:** Snackbar flotante con fondo `Theme.semantics.error` (`#EF5350`) y botón `"Reintentar"`.

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
    │       placeholder = { Text("Buscar por título, autor o contenido...", color = Theme.textTertiary) },
    │       leadingIcon = { Icon(Icons.Filled.Search, tint = Theme.primary) },
    │       trailingIcon = { if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Filled.Close, tint = Theme.textSecondary) } },
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
    │       ├── FilterChip (selected = target == ALL, label = "Todo")
    │       ├── FilterChip (selected = target == TITLE, label = "Título")
    │       ├── FilterChip (selected = target == AUTHOR, label = "Autor")
    │       └── FilterChip (selected = target == CONTENT, label = "En texto")
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
    │       │       └── Text ("${(book.progress * 100).toInt()}% completado", style = labelMedium, color = Theme.primary, modifier = padding(horizontal = 14.dp, vertical = 6.dp))
    │       └── FiveStarRatingBar (rating = book.userRating, onRatingChanged = { ... })
    ├── item: MetadataGridCard
    │   └── Card (colors = Theme.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Theme.border), modifier = fillMaxWidth().padding(16.dp))
    │       └── Row (modifier = Modifier.fillMaxWidth().padding(16.dp), horizontalArrangement = Arrangement.SpaceEvenly)
    │           ├── MetaItem (label = "PÁGINAS", value = "${book.pageCount}")
    │           ├── VerticalDivider (modifier = height(32.dp), color = Theme.border)
    │           ├── MetaItem (label = "FORMATO", value = book.format.uppercase())
    │           ├── VerticalDivider (modifier = height(32.dp), color = Theme.border)
    │           ├── MetaItem (label = "TIEMPO REST.", value = "${book.readingTimeRemainingMinutes}m")
    │           ├── VerticalDivider (modifier = height(32.dp), color = Theme.border)
    │           └── MetaItem (label = "IDIOMA", value = book.language.uppercase())
    ├── item: DescriptionExpandableCard
    │   └── Card (modifier = fillMaxWidth().padding(horizontal = 16.dp))
    │       └── Column (modifier = Modifier.padding(16.dp))
    │           ├── Text ("Sinopsis", style = titleMedium, color = Theme.textPrimary)
    │           ├── Text (book.description, style = bodyMedium, maxLines = if (expanded) 100 else 4, overflow = Ellipsis, color = Theme.textSecondary)
    │           └── TextButton (onClick = { expanded = !expanded }) { Text(if (expanded) "Mostrar menos" else "Leer más", color = Theme.primary) }
    ├── item: DetailTabsRow
    │   └── TabRow (selectedTabIndex = selectedTab, containerColor = Theme.background, contentColor = Theme.primary)
    │       ├── Tab (text = "Subrayados (${highlights.size})")
    │       ├── Tab (text = "Marcadores (${bookmarks.size})")
    │       └── Tab (text = "Sesiones")
    └── items: Highlights / Bookmarks / Sessions List Items
```

b) **Medidas Reales:**
- Portada Hero: Ancho `150.dp`, Alto `225.dp` (proporción 2:3 clásica de libro), esquinas `12.dp`, elevación `12.dp`.
- Pestañas: Altura `48.dp`, indicador activo `Theme.primary` con grosor `3.dp`.
- `ReadNowFab`: Botón flotante extendido con altura `56.dp`, esquinas `28.dp`, icono `Icons.Filled.AutoStories` (`24.dp`), texto `"Leer ahora"`.

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
  topBar = { TopAppBar (title = "Estadísticas de Lectura", colors = TopAppBarDefaults...) }
)
└── LazyColumn (modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(16.dp))
    ├── item: ReadingGoalHeroCard
    │   └── Card (colors = Theme.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Theme.border))
    │       └── Row (modifier = Modifier.padding(20.dp), verticalAlignment = CenterVertically)
    │           ├── CircularProgressIndicator (progress = goalProgress, strokeWidth = 8.dp, color = Theme.primary, trackColor = Theme.surfaceVariant, modifier = size(80.dp))
    │           ├── Spacer (width = 20.dp)
    │           └── Column
    │               ├── Text ("Meta Diaria", style = labelSmall, color = Theme.textSecondary)
    │               ├── Text ("35 / 45 min", style = headlineSmall, color = Theme.textPrimary, fontWeight = Bold)
    │               └── Text ("¡Estás a solo 10 minutos de cumplir tu objetivo!", style = bodySmall, color = Theme.primary)
    ├── item: ModernStatsGrid
    │   └── Row (modifier = fillMaxWidth(), horizontalArrangement = spacedBy(12.dp))
    │       ├── StatMetricCard (icon = Icons.Filled.LocalFireDepartment, label = "Racha actual", value = "14 días", tint = #FF9800)
    │       ├── StatMetricCard (icon = Icons.Filled.Speed, label = "Velocidad media", value = "240 ppm", tint = Theme.primary)
    │       └── StatMetricCard (icon = Icons.Filled.MenuBook, label = "Libros leídos", value = "12", tint = #42A5F5)
    ├── item: Heatmap12WeeksCard
    │   └── Card (colors = Theme.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Theme.border))
    │       └── Column (modifier = Modifier.padding(16.dp))
    │           ├── Text ("Actividad (Últimas 12 Semanas)", style = titleSmall, color = Theme.textPrimary)
    │           ├── Spacer (height = 12.dp)
    │           └── Canvas (modifier = Modifier.fillMaxWidth().height(110.dp)) [Dibuja 7 filas x 12 columnas de cuadros 10.dp con esquinas 2.dp, espaciado 3.dp, color interpolado de Theme.surfaceVariant a Theme.primary]
    ├── item: DailyWordCountBarChartCard
    │   └── Card (colors = Theme.surface, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, Theme.border))
    │       └── Column (modifier = Modifier.padding(16.dp))
    │           ├── Text ("Palabras por día (Esta semana)", style = titleSmall, color = Theme.textPrimary)
    │           ├── Spacer (height = 16.dp)
    │           └── Row (modifier = fillMaxWidth().height(120.dp), horizontalArrangement = SpaceBetween, verticalAlignment = Bottom)
    │               └── Día x 7 (Column con barra vertical de altura proporcional + etiqueta Lun..Dom)
    └── item: FormatComparisonCard
        └── Card (colors = Theme.surface, shape = RoundedCornerShape(16.dp))
            └── Column (modifier = Modifier.padding(16.dp))
                ├── Text ("Distribución de Formatos", style = titleSmall, color = Theme.textPrimary)
                └── Row (modifier = Modifier.padding(top = 12.dp))
                    ├── FormatoItem ("EPUB", "82%", Theme.primary)
                    └── FormatoItem ("PDF", "18%", Theme.secondary)
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
  topBar = { TopAppBar (title = "Ajustes", navigationIcon = { IconButton(Icons.Filled.ArrowBack) }) }
)
└── LazyColumn (modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp))
    ├── SectionHeader ("APARIENCIA Y TEMA")
    ├── ThemeSelectorCard
    │   └── Row (modifier = fillMaxWidth().horizontalScroll(), horizontalArrangement = spacedBy(12.dp))
    │       └── ThemePreviewItem x 5 (HiddenForest, Day, Sepia, AMOLED, Sunset)
    ├── SectionHeader ("EXPERIENCIA DE LECTURA")
    ├── SettingToggleItem (icon = Icons.Filled.VolumeUp, title = "Botones de volumen", subtitle = "Pasar página con las teclas físicas de volumen", checked = true)
    ├── SettingToggleItem (icon = Icons.Filled.AutoMode, title = "Mantener pantalla encendida", subtitle = "Evita que el dispositivo se apague durante la lectura", checked = true)
    ├── SettingToggleItem (icon = Icons.Filled.ThreeDRotation, title = "Efecto de paso de página 3D", subtitle = "Simula el doblado físico del papel con aceleración de hardware", checked = true)
    ├── SettingStepperItem (icon = Icons.Filled.FormatSize, title = "Tamaño de fuente por defecto", value = "18 sp", onMinus = { ... }, onPlus = { ... })
    ├── SectionHeader ("SÍNTESIS DE VOZ (TTS)")
    ├── SettingNavigationItem (icon = Icons.Filled.RecordVoiceOver, title = "Motor de voz predeterminado", value = "Microsoft Edge Neural")
    ├── SettingNavigationItem (icon = Icons.Filled.DownloadCloud, title = "Voces offline Sherpa ONNX", value = "3 descargadas")
    ├── SectionHeader ("SISTEMA Y ALMACENAMIENTO")
    ├── SettingNavigationItem (icon = Icons.Filled.Folder, title = "Carpetas sincronizadas", value = "4 directorios")
    ├── SettingNavigationItem (icon = Icons.Filled.CleaningServices, title = "Limpiar caché de renderizado", value = "128 MB")
    ├── SettingNavigationItem (icon = Icons.Filled.WorkspacePremium, title = "Woods Pro", value = "Activado (Permanente)")
    └── FooterInfo
        └── Column (modifier = fillMaxWidth().padding(vertical = 24.dp), horizontalAlignment = CenterHorizontally)
            ├── Text ("Woods Reader v2.4.0 (Build 240)", style = labelSmall, color = Theme.textTertiary)
            └── Text ("Licencia GPLv3 • Diseñado con Jetpack Compose", style = labelSmall, color = Theme.textTertiary)
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
      title = "Grafo de Conocimiento",
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
  topBar = { TopAppBar (title = "Candidatos detectados (${candidates.size})") }
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
                  │   ├── Text ("${candidate.mentionCount} menciones • Tipo: ${candidate.type.name}", style = bodySmall, color = Theme.textSecondary)
                  │   └── Text ("Ejemplo: "...${candidate.sampleContext}..."", style = bodySmall, fontStyle = Italic, color = Theme.textTertiary)
                  ├── IconButton (Icons.Filled.Edit, tint = Theme.primary) [Cambiar tipo]
                  ├── IconButton (Icons.Filled.CallMerge, tint = #42A5F5) [Fusionar con otro]
                  └── IconButton (Icons.Filled.Check, tint = #4CAF50) [Aprobar e incorporar al grafo]
            }
        )
```

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
  - Muestra un icono de carpeta animado, texto `"Indexando nuevos libros en el dispositivo..."` y un `LinearProgressIndicator` continuo indeterminado con color `Theme.primary`.

### 4.4 RecentBooksSheet y WoodsBottomNavigation
Fuente: `LibraryComponents.kt#L330-L525`.

- **RecentBooksSheet:**
  - ModalBottomSheet de Material 3 con esquinas superiores `24.dp` y fondo `Theme.surface`.
  - Cabecera con título `"Lecturas Recientes"` y botón de cierre.
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
  1. **Información y Metadatos:** Icono `Icons.Filled.Info`, texto `"Detalles del libro"`.
  2. **X-Ray Narrativo:** Icono `Icons.Filled.Visibility`, texto `"X-Ray del capítulo"` (abre lista de entidades presentes en el capítulo en curso).
  3. **Ego-Grafo del Libro:** Icono `Icons.Filled.Share`, texto `"Red de relaciones"`.
  4. **Traducción Calibre:** Icono `Icons.Filled.Translate`, texto `"Traducir libro"`.
  5. **Modo Pantalla Completa:** Icono `Icons.Filled.Fullscreen`, switch ON/OFF.
  6. **Bloqueo de Rotación:** Icono `Icons.Filled.ScreenRotation`, switch ON/OFF.
  7. **Modo Zurdo:** Icono `Icons.Filled.FrontHand`, switch ON/OFF (invierte las zonas de toque de cambio de página).
  8. **Exportar Anotaciones:** Icono `Icons.Filled.UploadFile`, texto `"Exportar notas (.txt, .json)"`.
  9. **Ajustes Avanzados:** Icono `Icons.Filled.Settings`, texto `"Ajustes del lector"`.

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
  - Desbloqueo: Deslizador horizontal tipo `"Deslizar para desbloquear"` con icono `Icons.Filled.LockOpen` y umbral del 80% de recorrido.

### 5.4 Slider de Progreso y Píldora de Teleprompter
Fuentes: `ReaderBottomSlider.kt#L1-L150`, `ReaderTeleprompterPill.kt#L1-L120`.

- **ReaderBottomSlider:**
  - Barra delgada ubicada inmediatamente arriba de la barra de herramientas inferior.
  - Muestra un slider continuo de `0.0f` a `1.0f` que permite arrastrar para saltar a cualquier parte del libro.
  - Al arrastrar, despliega una burbuja flotante (Tooltip) con el número de página estimado y el título del capítulo correspondiente.
  - Etiquetas a los lados: Izquierda: `"Pág. 142 de 420"` (`labelSmall`), Derecha: `"34%"` (`labelSmall`).

- **ReaderTeleprompterPill:**
  - Píldora flotante minimalista ubicada en la parte superior central de la pantalla durante la lectura activa con TTS.
  - Dimensiones: Alto `32.dp`, esquinas `16.dp`, fondo `Theme.surface.copy(alpha = 0.85f)`, borde `1.dp` de `Theme.border`.
  - Contenido: Muestra la velocidad en WPM (ej. `"280 ppm"`) junto a un icono parpadeante de micrófono/altavoz.

### 5.5 Todos los Bottom Sheets del Lector
Fuentes: `ReaderAudioBottomSheet.kt`, `ReaderNotesTab.kt`, `TocBottomSheet.kt`, `ReaderXRayBottomSheet.kt`, `ReaderEgoGraphBottomSheet.kt`, `ReaderTtsMacroWindow.kt`.

1. **TocBottomSheet (Tabla de Contenidos / Índice):**
   - Altura: 70% de la altura de la pantalla (`fillMaxHeight(0.7f)`), esquinas superiores `24.dp`.
   - Cabecera: Título `"Índice de Contenidos"` y buscador rápido de capítulos.
   - Pestañas: `"Capítulos"`, `"Marcadores"` y `"Anotaciones"`.
   - Elemento de capítulo: Fila con sangría proporcional al nivel de anidamiento (`padding(start = 16.dp * level)`), título del capítulo (`bodyMedium`, peso SemiBold si es el capítulo actual) y número de página al extremo derecho (`Theme.textSecondary`).

2. **ReaderNotesTab (Notas y Subrayados del Libro Actual):**
   - Lista ordenada cronológicamente de todos los fragmentos subrayados en el libro.
   - Cada tarjeta de nota muestra:
     - Franja lateral de color correspondiente al subrayado (`yellow`, `green`, `blue`, etc.) de `4.dp` de ancho.
     - Texto citado: `bodyMedium` en cursiva con comillas tipográficas.
     - Nota personal del usuario (si existe): `bodySmall` en caja con fondo `Theme.surfaceVariant` y radio `6.dp`.
     - Timestamp y ubicación: `"Capítulo 4 • Hace 2 días"` (`labelSmall`).
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

---

## 6. Diálogos

### 6.1 Web-to-EPUB Importer
Fuente: `app/src/main/java/com/flint/woodsreader/ui/dialogs/WebToEpubImporterDialog.kt#L1-L180`.

Diálogo modal de importación y conversión de artículos web a formato EPUB limpio y legible:
- **Estructura y Dimensiones:**
  - `AlertDialog` de Material 3 con `shape = RoundedCornerShape(20.dp)`, contenedor `Theme.surface`, borde `1.dp` de `Theme.border`.
  - Icono cabecera: `Icons.Filled.Public` (`28.dp`, `Theme.primary`) en círculo de `48.dp`.
  - Título: `"Descargar Artículo Web"` (`titleLarge`, SemiBold).
  - Campo de texto: `OutlinedTextField` para la URL (`placeholder = "https://ejemplo.com/articulo"`, esquinas `12.dp`).
  - Opciones de extracción: Checkboxes para `"Eliminar anuncios e imágenes innecesarias"` e `"Incluir imágenes del artículo"`.
  - Botones de acción: Botón `"Cancelar"` (texto neutro) y Botón `"Descargar y Crear EPUB"` (`Button` relleno en `Theme.primary` con texto en `Theme.background`).

### 6.2 Progreso de Traducción Calibre
Fuente: `app/src/main/java/com/flint/woodsreader/ui/dialogs/CalibreTranslationProgressDialog.kt#L1-L160`.

- **Estructura:**
  - Modal persistente (no cancelable al tocar fuera) que informa del proceso de traducción de un libro completo.
  - Indicador circular de progreso animado (`CircularProgressIndicator`, `48.dp`, color `Theme.primary`).
  - Título: `"Traduciendo libro..."` (`titleMedium`).
  - Texto de estado dinámico: `"Procesando capítulo 3 de 18 (42%)"` (`bodySmall`, `Theme.textSecondary`).
  - Barra lineal de progreso: `LinearProgressIndicator` con esquinas redondeadas.
  - Botón: `"Cancelar traducción"` (texto sutil en color de error `Theme.semantics.error`).

### 6.3 Desbloqueo Woods Pro
Fuente: `app/src/main/java/com/flint/woodsreader/ui/dialogs/WoodsProUnlockDialog.kt#L1-L190`.

- **Mecanismo:** Sistema de activación offline con claves criptográficas HMAC-SHA256 vinculadas al identificador de instalación del dispositivo.
- **Diseño Visual:**
  - Cabecera con degradado dorado crepuscular y tarjeta premium.
  - Icono: `Icons.Filled.WorkspacePremium` en `40.dp` con tinte dorado `#FFD700`.
  - Título: `"Woods Reader Pro"` (`headlineSmall`).
  - Lista de ventajas exclusivas (Checkmarks verdes):
    - Síntesis neuronal ilimitada de voces remotas Edge y locales Sherpa.
    - Exportación de libros completos a audiolibro en formato M4A.
    - Grafo de conocimiento narrativo ilimitado con visualización en 3D.
    - Traducción asistida de libros completos a más de 30 idiomas.
    - Sincronización continua de lectura y citas.
  - Campo de entrada: Entrada para el código de licencia de 16 caracteres alfanuméricos divididos en 4 bloques (`XXXX-XXXX-XXXX-XXXX`).
  - Botón: `"Activar Licencia"` (`Theme.primary`).

### 6.4 Diálogos de Entidades del Grafo (Cambio de Tipo y Fusión)
Fuente: `app/src/main/java/com/flint/woodsreader/ui/dialogs/GraphEntityDialogs.kt#L1-L220`.

- **ChangeTypeDialog:**
  - Selector en cuadrícula de 5 tipos de entidad: Personaje, Lugar, Objeto, Facción, Concepto. Cada tipo muestra su círculo de color distintivo y su nombre. Al tocar uno, se actualiza inmediatamente el nodo.
- **MergeCandidateDialog:**
  - Diálogo de resolución de ambigüedad para unificar dos entidades que hacen referencia al mismo sujeto (ej. "Harry" y "Harry Potter"). Muestra ambas tarjetas con sus alias y menciones con un botón de confirmación `"Fusionar entidades"`.

### 6.5 Confirmaciones
- **Eliminar Libro:** Diálogo con icono de advertencia `Icons.Filled.DeleteForever` en color rojo de error, texto `"¿Eliminar este libro de tu bosque?"`, subtítulo `"Se borrarán también las notas y el progreso asociados. El archivo físico original se mantendrá en tu almacenamiento si lo deseas"` y botones `"Cancelar"` / `"Eliminar definitivamente"`.
- **Descargar Voz Sherpa:** Informa del tamaño del paquete de red neuronal (`~45 MB`) con botón `"Descargar ahora (Wi-Fi)"`.

---

## 7. Grafo Semántico (Visualización en Canvas)

Fuentes: `app/src/main/java/com/flint/woodsreader/ui/graph/GraphCanvas.kt#L1-L240` y `GraphScreen.kt#L1-L320`.

### 7.1 Geometría de Nodos y Colores por Tipo
El renderizado del grafo se efectúa directamente en un `Canvas` de Compose optimizado para cálculo matricial de 60fps con fuerzas de repulsión de Coulomb y atracción de Hooke (Fruchterman-Reingold):

- **Colores Oficiales por Tipo de Entidad (`entityColor`):**
  - **Personaje (CHARACTER):** `#4CAF50` (Verde esmeralda vital).
  - **Lugar (LOCATION):** `#2196F3` (Azul zafiro geográfico).
  - **Objeto (ITEM):** `#FF9800` (Ámbar reliquia).
  - **Facción / Organización (FACTION):** `#9C27B0` (Púrpura heráldico).
  - **Concepto / Tema (CONCEPT):** `#00BCD4` (Cian etéreo).

- **Fórmula de Radio del Nodo (`nodeRadius`):**
  El tamaño del círculo es proporcional a la relevancia narrativa y número de menciones en el libro:
  $$R = 10	ext{f} + \min(\sqrt{	ext{mentionCount}}, 18	ext{f})$$
  - Nodo mínimo (1 mención): radio de `11.dp`.
  - Nodo protagonista / máxima relevancia (>324 menciones): radio máximo limitado a `28.dp`.

- **Dibujo del Nodo:**
  - Círculo de relleno: `drawCircle(color = entityColor(node.type), radius = R)`.
  - Halo de selección: Si el nodo está seleccionado o enfocado, se dibuja un anillo exterior pulsante de `4.dp` de grosor en `Color.White.copy(alpha = 0.8f)`.

### 7.2 Aristas y Relaciones
- **Líneas de Conexión:**
  - Trazadas con `drawLine(color = Theme.border.copy(alpha = edgeAlpha), strokeWidth = edgeWeight)`.
  - Grosor de arista: Rango de `1.5.dp` a `5.dp` en función de la fuerza de la relación (co-ocurrencias en los mismos párrafos).
  - Alpha dinámico: `0.3f` para relaciones tenues, `0.85f` para vínculos estrechos.

### 7.3 Niveles de Detalle (LOD) de Etiquetas según Nivel de Zoom
Para evitar saturación visual en el canvas, el motor aplica filtrado dinámico del texto de los nombres según el factor de escala de zoom (`zoomScale`):
- **Zoom Bajo (`scale <= 0.4f`):** No se dibuja ningún texto; solo los puntos y aristas para apreciar la topología global de la red.
- **Zoom Medio (`0.4f < scale <= 0.7f`):** Únicamente se dibujan las etiquetas de texto de los nodos principales con más de 15 menciones.
- **Zoom Alto (`scale > 0.7f`):** Se dibujan las etiquetas de todos los nodos visibles en pantalla con `drawText` tipografía sans-serif `11.sp` con sombra negra de alto contraste para garantizar su lectura sobre el fondo oscuro.

### 7.4 Filtros y Búsqueda
Una barra superior flotante permite activar o desactivar selectivamente cualquiera de los 5 tipos de entidades mediante chips circulares con su color distintivo, ocultando instantáneamente los nodos no deseados con una animación de desvanecimiento suave (`alpha` de 1 a 0 en 200ms).

---

## 8. Textos (Strings)

Fuente: `app/src/main/res/values/strings.xml#L1-L145` y literales en código Compose.

### 8.1 Inventario de Textos en Español Agrupados por Pantalla

#### Globales y Navegación
- `app_name`: `"Woods Reader"`
- `nav_library`: `"Biblioteca"`
- `nav_read_now`: `"Leer"`
- `nav_stats`: `"Estadísticas"`
- `nav_settings`: `"Ajustes"`
- `action_cancel`: `"Cancelar"`
- `action_confirm`: `"Confirmar"`
- `action_save`: `"Guardar"`
- `action_delete`: `"Eliminar"`
- `action_share`: `"Compartir"`

#### Pantalla: Onboarding
- `onboarding_title_1`: `"Lectura Inmersiva"`
- `onboarding_desc_1`: `"Diseñado para fundirse con la lectura. Sin distracciones, en armonía con la naturaleza."`
- `onboarding_title_2`: `"Voz Humana Sherpa & Edge"`
- `onboarding_desc_2`: `"Escucha tus libros con síntesis neuronal de última generación sin conexión o con voces ultra-realistas."`
- `onboarding_title_3`: `"Grafo de Conocimiento"`
- `onboarding_desc_3`: `"Descubre las conexiones semánticas entre personajes, lugares y conceptos de tus historias."`
- `onboarding_start_button`: `"Comenzar"`

#### Pantalla: Permisos
- `permissions_title`: `"Acceso a tus libros"`
- `permissions_description`: `"Woods Reader necesita acceder al almacenamiento de tu dispositivo para encontrar y organizar tus archivos EPUB y PDF."`
- `permissions_grant_button`: `"Conceder acceso"`
- `permissions_privacy_note`: `"Tus libros nunca salen de tu dispositivo. Woods Reader no recopila ningún dato personal."`

#### Pantalla: Biblioteca
- `library_title`: `"Woods Reader"`
- `library_subtitle_format`: `"%d libros en tu bosque"`
- `library_empty_title`: `"Tu bosque está en silencio"`
- `library_empty_desc`: `"Añade libros en formato EPUB o PDF a tu dispositivo para comenzar a leer"`
- `library_scan_button`: `"Escanear almacenamiento"`
- `library_scanning_title`: `"Buscando nuevos senderos..."`
- `library_scanning_subtitle`: `"Indexando libros en el dispositivo"`
- `continue_reading_tag`: `"CONTINUAR LEYENDO"`
- `time_remaining_format`: `"%d min restantes"`
- `collection_all`: `"Todos"`
- `collection_reading`: `"Leyendo"`
- `collection_completed`: `"Terminados"`
- `collection_favorites`: `"Favoritos"`

#### Pantalla: Detalle de Libro
- `detail_pages`: `"PÁGINAS"`
- `detail_format`: `"FORMATO"`
- `detail_time_left`: `"TIEMPO REST."`
- `detail_language`: `"IDIOMA"`
- `detail_synopsis`: `"Sinopsis"`
- `detail_show_more`: `"Leer más"`
- `detail_show_less`: `"Mostrar menos"`
- `detail_tab_highlights`: `"Subrayados"`
- `detail_tab_bookmarks`: `"Marcadores"`
- `detail_tab_sessions`: `"Sesiones"`
- `detail_read_fab`: `"Leer ahora"`

#### Pantalla: Lector y TTS
- `reader_page_format`: `"Pág. %d de %d"`
- `reader_menu_details`: `"Detalles del libro"`
- `reader_menu_xray`: `"X-Ray del capítulo"`
- `reader_menu_graph`: `"Red de relaciones"`
- `reader_menu_translate`: `"Traducir libro"`
- `reader_menu_fullscreen`: `"Pantalla completa"`
- `reader_menu_lefthanded`: `"Modo zurdo"`
- `reader_tts_speed`: `"Velocidad"`
- `reader_tts_pitch`: `"Tono"`
- `reader_tts_sleep_timer`: `"Temporizador"`
- `reader_tts_ambience`: `"Sonido ambiental"`
- `reader_tts_export`: `"Exportar M4A"`
- `reader_settings_typography`: `"Tipografía"`
- `reader_settings_theme`: `"Tema"`
- `reader_settings_alignment`: `"Alineación"`
- `reader_settings_margins`: `"Márgenes"`
- `reader_settings_spacing`: `"Interlineado"`
- `reader_settings_hyphenation`: `"Separación silábica"`
- `reader_settings_page_effect`: `"Paso de página"`

#### Pantalla: Estadísticas
- `stats_title`: `"Estadísticas de Lectura"`
- `stats_daily_goal`: `"Meta Diaria"`
- `stats_streak`: `"Racha actual"`
- `stats_speed`: `"Velocidad media"`
- `stats_total_books`: `"Libros leídos"`
- `stats_heatmap_title`: `"Actividad (Últimas 12 Semanas)"`
- `stats_daily_words`: `"Palabras por día (Esta semana)"`
- `stats_formats`: `"Distribución de Formatos"`

#### Pantalla: Ajustes
- `settings_title`: `"Ajustes"`
- `settings_appearance`: `"APARIENCIA Y TEMA"`
- `settings_reading_exp`: `"EXPERIENCIA DE LECTURA"`
- `settings_tts_section`: `"SÍNTESIS DE VOZ (TTS)"`
- `settings_storage_section`: `"SISTEMA Y ALMACENAMIENTO"`
- `settings_volume_keys`: `"Botones de volumen"`
- `settings_keep_screen`: `"Mantener pantalla encendida"`
- `settings_3d_turn`: `"Efecto de paso de página 3D"`
- `settings_clean_cache`: `"Limpiar caché de renderizado"`
- `settings_pro_status`: `"Woods Pro"`

---

## 9. Assets y Recursos Gráficos

### 9.1 Vectores XML Completos

#### `res/drawable/ic_notification.xml`
Fuente: `app/src/main/res/drawable/ic_notification.xml`.
```xml
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="24dp"
    android:height="24dp"
    android:viewportWidth="24"
    android:viewportHeight="24">
  <path
      android:fillColor="#FFFFFFFF"
      android:pathData="M18,2H6c-1.1,0 -2,0.9 -2,2v16c0,1.1 0.9,2 2,2h12c1.1,0 2,-0.9 2,-2V4c0,-1.1 -0.9,-2 -2,-2zM6,4h5v8l-2.5,-1.5L6,12V4z"/>
</vector>
```

#### `res/drawable/ic_launcher_background.xml`
Fuente: `app/src/main/res/drawable/ic_launcher_background.xml`.
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#080B08"
        android:pathData="M0,0h108v108h-108z"/>
    <path
        android:fillColor="#111811"
        android:pathData="M9,0L108,99L108,108L0,108L0,0z"/>
</vector>
```

#### `res/drawable/ic_launcher_foreground.xml`
Fuente: `app/src/main/res/drawable/ic_launcher_foreground.xml`.
```xml
<?xml version="1.0" encoding="utf-8"?>
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
    <path
        android:fillColor="#4CAF72"
        android:pathData="M54,24L30,44h16v24h16V44h16L54,24z"/>
    <path
        android:fillColor="#81C784"
        android:pathData="M54,40L38,54h10v18h12V54h10L54,40z"/>
</vector>
```

### 9.2 Archivos de Fuentes e Iconografía
- **Fuentes en `res/font/`:**
  - `literata_variable.ttf`: Fuente tipográfica variable serif principal de lectura.
  - `literata_italic_variable.ttf`: Variante cursiva variable para citas, énfasis y notas.
- **Iconografía:** Material Icons Extended de Compose (`androidx.compose.material.icons`). Todos los iconos son vectoriales de trazo limpio y relleno sólido acordes a la especificación de Material Design 3.

---

## 10. Datos de Ejemplo (JSON)

Fixture completo realista para poblar todas las pantallas del maquetado HTML sin requerir conexión a base de datos: 6 libros, 3 subrayados, 2 marcadores, sesiones de lectura, estadísticas completas (racha, palabras diarias, mapa de calor de 12 semanas) y el grafo de conocimiento narrativo con entidades y relaciones.

```json
{
  "books": [
    {
      "id": 1,
      "title": "El Nombre del Viento",
      "author": "Patrick Rothfuss",
      "format": "epub",
      "coverPath": "covers/name_of_the_wind.jpg",
      "progress": 0.42,
      "currentPage": 280,
      "pageCount": 662,
      "readingStatus": "READING",
      "collection": "Favoritos",
      "userRating": 5,
      "readingTimeRemainingMinutes": 320,
      "language": "es",
      "description": "He robado princesas a reyes agónicos. Incendié la ciudad de Trebon. He pasado la noche con Felurian y he despertado con la cordura intacta y el orgullo maltrecho.",
      "lastReadTimestamp": 1729987200000
    },
    {
      "id": 2,
      "title": "Dune",
      "author": "Frank Herbert",
      "format": "epub",
      "coverPath": "covers/dune.jpg",
      "progress": 0.88,
      "currentPage": 612,
      "pageCount": 695,
      "readingStatus": "READING",
      "collection": "Ciencia Ficción",
      "userRating": 5,
      "readingTimeRemainingMinutes": 65,
      "language": "es",
      "description": "En el desértico planeta Arrakis, el joven Paul Atreides se enfrenta a una traición política y asume su destino mesiánico en medio de las dunas.",
      "lastReadTimestamp": 1729893600000
    },
    {
      "id": 3,
      "title": "Neuromante",
      "author": "William Gibson",
      "format": "epub",
      "coverPath": "covers/neuromancer.jpg",
      "progress": 1.0,
      "currentPage": 320,
      "pageCount": 320,
      "readingStatus": "COMPLETED",
      "collection": "Cyberpunk",
      "userRating": 4,
      "readingTimeRemainingMinutes": 0,
      "language": "es",
      "description": "El cielo sobre el puerto tenía el color de una pantalla de televisor sintonizada en un canal muerto.",
      "lastReadTimestamp": 1729202400000
    },
    {
      "id": 4,
      "title": "Hyperion",
      "author": "Dan Simmons",
      "format": "epub",
      "coverPath": null,
      "progress": 0.15,
      "currentPage": 75,
      "pageCount": 500,
      "readingStatus": "READING",
      "collection": "Ciencia Ficción",
      "userRating": 4,
      "readingTimeRemainingMinutes": 410,
      "language": "es",
      "description": "En vísperas del Armagedón galáctico, siete peregrinos viajan a las Tumbas del Tiempo para enfrentarse al Alcaudón.",
      "lastReadTimestamp": 1728511200000
    },
    {
      "id": 5,
      "title": "Manual de Inteligencia Artificial Moderna",
      "author": "Stuart Russell & Peter Norvig",
      "format": "pdf",
      "coverPath": "covers/aima.jpg",
      "progress": 0.28,
      "currentPage": 312,
      "pageCount": 1150,
      "readingStatus": "READING",
      "collection": "Estudio",
      "userRating": 5,
      "readingTimeRemainingMinutes": 950,
      "language": "es",
      "description": "El texto fundamental de referencia sobre teoría y aplicación práctica de agentes inteligentes y aprendizaje automático.",
      "lastReadTimestamp": 1727733600000
    },
    {
      "id": 6,
      "title": "Cien Años de Soledad",
      "author": "Gabriel García Márquez",
      "format": "epub",
      "coverPath": "covers/soledad.jpg",
      "progress": 0.0,
      "currentPage": 0,
      "pageCount": 448,
      "readingStatus": "UNREAD",
      "collection": "Clásicos",
      "userRating": 0,
      "readingTimeRemainingMinutes": 520,
      "language": "es",
      "description": "Muchos años después, frente al pelotón de fusilamiento, el coronel Aureliano Buendía había de recordar aquella tarde remota en que su padre lo llevó a conocer el hielo.",
      "lastReadTimestamp": 1726000000000
    }
  ],
  "highlights": [
    {
      "id": 1,
      "bookId": 1,
      "chapterTitle": "Capítulo 7: De palabras y fuego",
      "text": "Viajé, amé, perdí, confié y fui traicionado. En un rincón de mi memoria aún arde la madera de la posada Roca de Guía.",
      "color": "yellow",
      "note": "Definición fundamental del carácter de Kvothe.",
      "createdAt": 1729980000000
    },
    {
      "id": 2,
      "bookId": 1,
      "chapterTitle": "Capítulo 16: Hojas al viento",
      "text": "El silencio era de tres partes. La parte más obvia era una calma hueca y resonante...",
      "color": "green",
      "note": "El célebre silencio de tres partes del prólogo y epílogo.",
      "createdAt": 1729951200000
    },
    {
      "id": 3,
      "bookId": 2,
      "chapterTitle": "Capítulo 1: La prueba del Gom Jabbar",
      "text": "El misterio de la vida no es un problema que deba ser resuelto, sino una realidad que debe ser experimentada.",
      "color": "blue",
      "note": "Cita de la Reverenda Madre.",
      "createdAt": 1729864800000
    }
  ],
  "bookmarks": [
    {
      "id": 1,
      "bookId": 1,
      "title": "Encuentro con Abenthy",
      "page": 84,
      "progress": 0.126,
      "createdAt": 1729800000000
    },
    {
      "id": 2,
      "bookId": 1,
      "title": "La Universidad y el Archivo",
      "page": 240,
      "progress": 0.362,
      "createdAt": 1729920000000
    }
  ],
  "readingSessions": [
    { "date": "2026-10-20", "minutesRead": 42, "wordsRead": 10500 },
    { "date": "2026-10-21", "minutesRead": 35, "wordsRead": 8750 },
    { "date": "2026-10-22", "minutesRead": 60, "wordsRead": 15000 },
    { "date": "2026-10-23", "minutesRead": 25, "wordsRead": 6250 },
    { "date": "2026-10-24", "minutesRead": 50, "wordsRead": 12500 },
    { "date": "2026-10-25", "minutesRead": 45, "wordsRead": 11250 },
    { "date": "2026-10-26", "minutesRead": 35, "wordsRead": 8750 }
  ],
  "statistics": {
    "currentStreakDays": 14,
    "longestStreakDays": 28,
    "averageWpm": 250,
    "totalBooksRead": 12,
    "totalMinutesRead": 8420,
    "dailyGoalMinutes": 45,
    "todayMinutesRead": 35,
    "formatDistribution": {
      "epub": 0.82,
      "pdf": 0.18
    },
    "weeklyHeatmapMatrix12Weeks": [
      [0, 15, 30, 45, 20, 0, 10, 35, 45, 60, 25, 35],
      [20, 25, 40, 50, 30, 15, 20, 40, 50, 45, 30, 40],
      [30, 35, 45, 40, 25, 20, 30, 45, 60, 50, 40, 30],
      [15, 20, 30, 35, 40, 25, 35, 50, 40, 35, 25, 45],
      [45, 50, 60, 70, 50, 40, 45, 60, 55, 60, 50, 50],
      [60, 65, 80, 75, 60, 55, 60, 70, 65, 75, 65, 60],
      [40, 45, 50, 60, 45, 30, 40, 55, 45, 50, 45, 35]
    ]
  },
  "knowledgeGraph": {
    "bookId": 1,
    "nodes": [
      { "id": "n1", "name": "Kvothe", "type": "CHARACTER", "mentionCount": 340, "sampleContext": "Kvothe miró hacia el horizonte mientras afinaba las cuerdas de su laúd." },
      { "id": "n2", "name": "Denna", "type": "CHARACTER", "mentionCount": 165, "sampleContext": "Denna sonrió con esa mirada esquiva que parecía desvanecerse con la brisa." },
      { "id": "n3", "name": "Bast", "type": "CHARACTER", "mentionCount": 98, "sampleContext": "Bast colocó un plato con manzanas en la barra de madera pulida." },
      { "id": "n4", "name": "La Universidad", "type": "LOCATION", "mentionCount": 142, "sampleContext": "Cruzó el puente de piedra sobre el río Omeca en dirección a los patios de la Universidad." },
      { "id": "n5", "name": "Posada Roca de Guía", "type": "LOCATION", "mentionCount": 85, "sampleContext": "El fuego de la chimenea proyectaba sombras en las paredes de la posada." },
      { "id": "n6", "name": "Laúd de siete cuerdas", "type": "ITEM", "mentionCount": 62, "sampleContext": "Sacó con sumo cuidado su laúd de siete cuerdas del estuche de terciopelo verde." },
      { "id": "n7", "name": "Los Chandrian", "type": "FACTION", "mentionCount": 78, "sampleContext": "¿Dónde estaban los fuegos azules y el hierro oxidado de los Chandrian?" },
      { "id": "n8", "name": "Nominación", "type": "CONCEPT", "mentionCount": 54, "sampleContext": "El nombre del viento no era algo que pudiera atraparse en un frasco de alquimia." }
    ],
    "edges": [
      { "source": "n1", "target": "n2", "weight": 4.5, "coOccurrences": 92 },
      { "source": "n1", "target": "n3", "weight": 3.8, "coOccurrences": 74 },
      { "source": "n1", "target": "n4", "weight": 5.0, "coOccurrences": 118 },
      { "source": "n1", "target": "n5", "weight": 3.5, "coOccurrences": 65 },
      { "source": "n1", "target": "n6", "weight": 3.2, "coOccurrences": 48 },
      { "source": "n1", "target": "n7", "weight": 4.0, "coOccurrences": 56 },
      { "source": "n1", "target": "n8", "weight": 3.0, "coOccurrences": 38 },
      { "source": "n3", "target": "n5", "weight": 3.4, "coOccurrences": 52 },
      { "source": "n4", "target": "n8", "weight": 2.8, "coOccurrences": 31 }
    ]
  }
}
```

---

## 11. Pendiente / No Pude Determinar

A continuación se registran los elementos que no están explícitamente parametrizados con constantes numéricas en el código y adoptan los comportamientos por defecto del sistema o bibliotecas subyacentes:

1. **Grosor del Borde en Diálogos de Material 3 (`AlertDialog`):**
   - El código fuente no especifica un `border = BorderStroke(...)` personalizado en `WebToEpubImporterDialog` ni en los diálogos de confirmación estándar, por lo que heredan el estilo nativo de Material 3 (`0.dp` de borde y elevación tonal de superficie de `6.dp`).
2. **Duración de Animación de Parpadeo del Cursor del Ticker TTS:**
   - La velocidad del deslizamiento tipo marquesina (`Marquee`) de la frase en locución utiliza el modificador experimental `basicMarquee()` de Android Compose 1.7+, cuyos valores internos son: velocidad de `30.dp` por segundo, retraso inicial de `1200ms` y separación de repetición de `64.dp`.
3. **Curva de Bezier para Deslizamiento de Página en Readium:**
   - Cuando el paso de página 3D (`BookFlip3D`) está desactivado y se utiliza `Slide`, la curva de aceleración física de arrastre táctil es gestionada internamente por el motor de vistas de `ViewPager2` de Readium Navigator con un interpolador cúbico de desaceleración estándar de Android (`PathInterpolator(0.25f, 0.1f, 0.25f, 1f)`).
4. **Umbral de Luminosidad para Inversión de Texto en Portadas sin Carátula (`BookInitials`):**
   - El color de las iniciales se computa mediante luminancia perceptiva W3C: si la luminancia del color generado por el hash del título es mayor a `0.5`, se dibuja texto `#111811`; si es menor o igual, se dibuja `#FFFFFF`.
