# -*- coding: utf-8 -*-
part4 = """
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
  $$R = 10\text{f} + \min(\sqrt{\text{mentionCount}}, 18\text{f})$$
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
"""

with open("scripts/part4.py", "w", encoding="utf-8") as f:
    f.write('content = """' + part4.replace('\\', '\\\\').replace('"', '\\"') + '"""\n')
print("Part 4 written successfully.")
