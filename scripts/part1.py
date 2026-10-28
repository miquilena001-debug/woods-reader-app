content = """# Auditoría Visual y Guía Maquetado HTML: Woods Reader (Android Jetpack Compose + Material 3)

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
  \"$schema\": \"http://json-schema.org/draft-07/schema#\",
  \"title\": \"WoodsReaderDesignTokens\",
  \"themes\": {
    \"HiddenForest\": {
      \"id\": \"HIDDEN_FOREST\",
      \"name\": \"Hidden Forest (Default Dark)\",
      \"description\": \"Paleta oscura inspirada en naturaleza profunda y follaje nocturno\",
      \"source\": \"Color.kt:L12-L38, Theme.kt:L78-L96\",
      \"colors\": {
        \"background\": { \"hex\": \"#080B08\", \"usage\": \"Fondo general de la aplicación, lectores y scaffolds\" },
        \"surface\": { \"hex\": \"#111811\", \"usage\": \"Superficie de tarjetas, barras inferiores y paneles\" },
        \"surfaceVariant\": { \"hex\": \"#1B261B\", \"usage\": \"Superficie de controles secundarios, chips inactivos, sliders inactivos\" },
        \"surfaceSubtle\": { \"hex\": \"#0E140E\", \"usage\": \"Fondos intermedios entre background y tarjeta principal\" },
        \"primary\": { \"hex\": \"#4CAF72\", \"usage\": \"Color de acento principal, botones de acción primaria, FAB, switches encendidos\" },
        \"primaryVariant\": { \"hex\": \"#3B965E\", \"usage\": \"Variante pressed/hover para botones primarios\" },
        \"secondary\": { \"hex\": \"#81C784\", \"usage\": \"Acento secundario, badges positivos, porcentajes destacados\" },
        \"tertiary\": { \"hex\": \"#A5D6A7\", \"usage\": \"Subrayados suaves, indicadores de lectura en curso\" },
        \"textPrimary\": { \"hex\": \"#D4E8D4\", \"usage\": \"Títulos principales, texto del libro, encabezados (88% opacidad percibida)\" },
        \"textSecondary\": { \"hex\": \"#7E9E7E\", \"usage\": \"Metadatos, nombres de autor, subtítulos, etiquetas inactivas (60%)\" },
        \"textTertiary\": { \"hex\": \"#485E48\", \"usage\": \"Textos desactivados, timestamps, placeholders de búsqueda (40%)\" },
        \"border\": { \"hex\": \"#1E2C1E\", \"usage\": \"Bordes estándar de tarjetas (1.dp), divisores de listas\" },
        \"borderSubtle\": { \"hex\": \"#141F14\", \"usage\": \"Bordes muy discretos en controles deshabilitados\" },
        \"cardBackground\": { \"hex\": \"#111811\", \"usage\": \"Fondo base para WoodsBookCard y ContinueReadingHero\" }
      }
    },
    \"Day\": {
      \"id\": \"DAY\",
      \"name\": \"Day (Light Mode)\",
      \"description\": \"Paleta clara suave de alto contraste y baja fatiga visual\",
      \"source\": \"Color.kt:L42-L65, Theme.kt:L60-L76\",
      \"colors\": {
        \"background\": { \"hex\": \"#F7F9F6\", \"usage\": \"Fondo general en modo día\" },
        \"surface\": { \"hex\": \"#FFFFFF\", \"usage\": \"Fondo de tarjetas elevadas y menús emergentes\" },
        \"surfaceVariant\": { \"hex\": \"#EAEFE9\", \"usage\": \"Controles secundarios, fondos de chips y campos de texto\" },
        \"surfaceSubtle\": { \"hex\": \"#F0F4EF\", \"usage\": \"Fondo de listas alternas y cabeceras secundarias\" },
        \"primary\": { \"hex\": \"#2E7D47\", \"usage\": \"Color de marca diurno, botones primarios e iconos activos\" },
        \"primaryVariant\": { \"hex\": \"#236338\", \"usage\": \"Estado presionado de botones primarios\" },
        \"secondary\": { \"hex\": \"#489960\", \"usage\": \"Acento complementario y badges activos\" },
        \"tertiary\": { \"hex\": \"#68B47E\", \"usage\": \"Indicadores de progreso y enlaces de texto\" },
        \"textPrimary\": { \"hex\": \"#1C261D\", \"usage\": \"Texto principal de lectura y títulos en modo día\" },
        \"textSecondary\": { \"hex\": \"#526854\", \"usage\": \"Autores y descripciones secundarias\" },
        \"textTertiary\": { \"hex\": \"#8FA491\", \"usage\": \"Etiquetas de fecha y texto terciario\" },
        \"border\": { \"hex\": \"#D8E2D7\", \"usage\": \"Bordes delimitadores de tarjetas y barras de herramientas\" },
        \"borderSubtle\": { \"hex\": \"#E5EDE4\", \"usage\": \"Separadores sutiles de filas\" },
        \"cardBackground\": { \"hex\": \"#FFFFFF\", \"usage\": \"Fondo de tarjeta de libro\" }
      }
    },
    \"Sepia\": {
      \"id\": \"SEPIA\",
      \"name\": \"Sepia (Warm Paper)\",
      \"description\": \"Paleta cálida de papel y pergamino clásico para lectura relajada\",
      \"source\": \"Color.kt:L68-L92, Theme.kt:L98-L115\",
      \"colors\": {
        \"background\": { \"hex\": \"#F4ECD8\", \"usage\": \"Fondo general cálido sepia\" },
        \"surface\": { \"hex\": \"#EADDC4\", \"usage\": \"Tarjetas y hojas superpuestas\" },
        \"surfaceVariant\": { \"hex\": \"#DDD0B5\", \"usage\": \"Chips y elementos interactivos secundarios\" },
        \"surfaceSubtle\": { \"hex\": \"#E2D5BA\", \"usage\": \"Contenedores de citas e índices\" },
        \"primary\": { \"hex\": \"#7A5230\", \"usage\": \"Acento marrón cálido, botones de acción principal\" },
        \"primaryVariant\": { \"hex\": \"#613F22\", \"usage\": \"Estado presionado de botones marrones\" },
        \"secondary\": { \"hex\": \"#9B6B43\", \"usage\": \"Acento secundario cálido y progreso\" },
        \"tertiary\": { \"hex\": \"#B8865E\", \"usage\": \"Detalles auxiliares y deslizadores\" },
        \"textPrimary\": { \"hex\": \"#3A2818\", \"usage\": \"Texto editorial sepia de máxima legibilidad\" },
        \"textSecondary\": { \"hex\": \"#6B523B\", \"usage\": \"Metadatos del autor y pie de página\" },
        \"textTertiary\": { \"hex\": \"#9E846D\", \"usage\": \"Notas marginales y marcas de tiempo\" },
        \"border\": { \"hex\": \"#CEBEA0\", \"usage\": \"Borde pergamino para tarjetas y separadores\" },
        \"borderSubtle\": { \"hex\": \"#DECFA9\", \"usage\": \"Líneas tenues de separación\" },
        \"cardBackground\": { \"hex\": \"#EADDC4\", \"usage\": \"Contenedor de tarjetas\" }
      }
    },
    \"AMOLED\": {
      \"id\": \"AMOLED\",
      \"name\": \"AMOLED (Pure Black)\",
      \"description\": \"Negro absoluto para máximo ahorro de batería y contraste perfecto en paneles OLED\",
      \"source\": \"Color.kt:L95-L120, Theme.kt:L117-L135\",
      \"colors\": {
        \"background\": { \"hex\": \"#000000\", \"usage\": \"Fondo negro absoluto 0% emisión de luz\" },
        \"surface\": { \"hex\": \"#080808\", \"usage\": \"Superficie oscura casi pura para tarjetas\" },
        \"surfaceVariant\": { \"hex\": \"#121212\", \"usage\": \"Fondos secundarios y barras de herramientas\" },
        \"surfaceSubtle\": { \"hex\": \"#050505\", \"usage\": \"Límites de contenedores intermedios\" },
        \"primary\": { \"hex\": \"#3DD975\", \"usage\": \"Verde neón esmeralda de alto impacto para botones y switches\" },
        \"primaryVariant\": { \"hex\": \"#2AB85E\", \"usage\": \"Variante de alta intensidad\" },
        \"secondary\": { \"hex\": \"#5EE68F\", \"usage\": \"Progreso de lectura y marcadores activos\" },
        \"tertiary\": { \"hex\": \"#88F0AD\", \"usage\": \"Resaltados brillantes\" },
        \"textPrimary\": { \"hex\": \"#E8E8E8\", \"usage\": \"Texto blanco nítido anti-fatiga\" },
        \"textSecondary\": { \"hex\": \"#8A8A8A\", \"usage\": \"Texto secundario gris plata\" },
        \"textTertiary\": { \"hex\": \"#4E4E4E\", \"usage\": \"Detalles secundarios y bordes inactivos\" },
        \"border\": { \"hex\": \"#1A1A1A\", \"usage\": \"Bordes visibles en contraste con negro absoluto\" },
        \"borderSubtle\": { \"hex\": \"#101010\", \"usage\": \"Líneas imperceptibles de separación\" },
        \"cardBackground\": { \"hex\": \"#080808\", \"usage\": \"Fondo de tarjetas\" }
      }
    },
    \"Sunset\": {
      \"id\": \"SUNSET\",
      \"name\": \"Sunset (Warm Twilight)\",
      \"description\": \"Fondo ciruela profundo con acentos ámbar atardecer\",
      \"source\": \"Color.kt:L123-L150, Theme.kt:L137-L155\",
      \"colors\": {
        \"background\": { \"hex\": \"#120B10\", \"usage\": \"Fondo crepuscular ciruela ultra oscuro\" },
        \"surface\": { \"hex\": \"#1C121A\", \"usage\": \"Superficie de tarjetas y menús\" },
        \"surfaceVariant\": { \"hex\": \"#281B26\", \"usage\": \"Controles secundarios y chips\" },
        \"surfaceSubtle\": { \"hex\": \"#160E15\", \"usage\": \"Contenedores sutiles\" },
        \"primary\": { \"hex\": \"#E57342\", \"usage\": \"Ámbar mandarina brillante para acciones principales\" },
        \"primaryVariant\": { \"hex\": \"#CA5A2B\", \"usage\": \"Pulsación en botones primarios\" },
        \"secondary\": { \"hex\": \"#EE9366\", \"usage\": \"Barras de progreso e iconos de lectura\" },
        \"tertiary\": { \"hex\": \"#F4B490\", \"usage\": \"Acento terciario cálido\" },
        \"textPrimary\": { \"hex\": \"#F2E4DE\", \"usage\": \"Texto principal de lectura en tono crema cálido\" },
        \"textSecondary\": { \"hex\": \"#9E8078\", \"usage\": \"Autores y metadatos complementarios\" },
        \"textTertiary\": { \"hex\": \"#604A44\", \"usage\": \"Textos secundarios deshabilitados\" },
        \"border\": { \"hex\": \"#2E1E2B\", \"usage\": \"Bordes de delimitación crepuscular\" },
        \"borderSubtle\": { \"hex\": \"#21151F\", \"usage\": \"Divisores sutiles\" },
        \"cardBackground\": { \"hex\": \"#1C121A\", \"usage\": \"Fondo de tarjetas\" }
      }
    }
  },
  \"semantics\": {
    \"source\": \"Color.kt:L152-L175\",
    \"success\": { \"hex\": \"#4CAF50\", \"usage\": \"Lectura completada, operaciones exitosas, sincronización correcta\" },
    \"warning\": { \"hex\": \"#FF9800\", \"usage\": \"Descarga de voz pendiente, archivo grande sin indexar, alerta de batería\" },
    \"error\": { \"hex\": \"#EF5350\", \"usage\": \"Fallo al abrir libro, error en traducción Calibre, archivo corrupto\" },
    \"info\": { \"hex\": \"#42A5F5\", \"usage\": \"Avisos de formato, notas de actualización y marcadores neutros\" }
  },
  \"highlights\": {
    \"source\": \"Color.kt:L178-L210\",
    \"light\": {
      \"yellow\": { \"hex\": \"#FFF176\", \"usage\": \"Subrayado amarillo canario estándar diurno\" },
      \"green\": { \"hex\": \"#A5D6A7\", \"usage\": \"Subrayado verde menta diurno\" },
      \"blue\": { \"hex\": \"#90CAF9\", \"usage\": \"Subrayado azul cielo diurno\" },
      \"pink\": { \"hex\": \"#F48FB1\", \"usage\": \"Subrayado rosa pastel diurno\" },
      \"purple\": { \"hex\": \"#CE93D8\", \"usage\": \"Subrayado violeta diurno\" }
    },
    \"dark\": {
      \"yellow\": { \"hex\": \"#B2A900\", \"usage\": \"Subrayado amarillo dorado apagado nocturno\" },
      \"green\": { \"hex\": \"#2E7D32\", \"usage\": \"Subrayado verde oliva profundo nocturno\" },
      \"blue\": { \"hex\": \"#1565C0\", \"usage\": \"Subrayado azul cobalto oscuro nocturno\" },
      \"pink\": { \"hex\": \"#AD1457\", \"usage\": \"Subrayado fucsia vino oscuro nocturno\" },
      \"purple\": { \"hex\": \"#6A1B9A\", \"usage\": \"Subrayado violeta berenjena nocturno\" }
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
  - `Modifier.sharedElement(rememberSharedContentState(key = \"cover-hero-$bookId\"))`: Portada del libro desde `ContinueReadingHero` o `WoodsBookCard` hacia `BookDetailScreen`.
  - `Modifier.sharedBounds(rememberSharedContentState(key = \"card-container-$bookId\"))`: Contenedor rectangular de tarjeta de libro hacia el encabezado de `BookDetailScreen`.

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
     - Etiqueta: \"Biblioteca\" (`R.string.nav_library`), tipografía `labelSmall` (11.sp, Medium).
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
     - Etiqueta: \"Estadísticas\" (`R.string.nav_stats`), tipografía `labelSmall` (11.sp, Medium).
     - Color activo: `Theme.primary`.
     - Color inactivo: `Theme.textSecondary.copy(alpha = 0.6f)`.
"""
