# Woods Reader — Plan Maestro

> **Principio rector:** EPUB y PDF son el mismo libro. El usuario no sabe ni le importa el formato.
> Toda la pantalla y toda la voz salen de la misma tubería. Lo que cambia entre formatos es interno y transparente.

---

## Estado actual (base de partida)

| Lo que ya está hecho | Commit |
|---|---|
| `.gitignore` + commit inicial | `2227a83` |
| APK compression fix, Timber, highlight, tabs crash, lastBookId | `4856aed` |
| Sesiones desacopladas, importación externa, restauración de posición, cortacircuitos | `ffacb76` |
| Sherpa a IO, ContentObserver filtrado, portadas diferidas, positions async, stats lifecycle | `7d18613` |
| Especialización EPUB + PDF (eliminados junrar, commonmark, okhttp, formatos extras) | `fb8740e` |
| Tubería semántica: `ReadingPipeline`, `NoiseClassifier`, `WordExclusionEngine`, `ImageAnnouncer`, `SpanishTextFormatter`, `PdfBookViewSynchronizer` | `86f1fbc` |
| Documentación de auditoría | `1cf4b53` |
| Limpieza de raíz: módulos fantasma, web mockup, basura de build | `40eacf9` |

**Tests al día:** 69/69 ✅ — APK debug: 232 MB

---

## Lo que falta para ser premium

La infraestructura está. Lo que diferencia una app premium no son las funciones — es que **ningún gesto falla nunca** y que **leer se siente como leer un libro físico**, no como usar software.

Hay tres niveles de trabajo pendiente:

1. **Conectar** lo que ya existe pero no está enchufado (tubería → UI, tubería → TTS)
2. **Refinar** la experiencia de lectura (tipografía, gestos, vista PDF como libro)
3. **Consolidar** el diseño para que todo se sienta coherente y sin fisuras

---

## Fases

### Fase 1 — Conectar la tubería a la pantalla y a la voz
**Objetivo:** Que `ReadingPipeline` (ya escrita) sea la única fuente de verdad para pantalla y TTS.

**Por qué primero:** Todo lo demás (tipografía, gestos, vista libro del PDF) depende de que haya un flujo de bloques limpio que alimenta todo. Si se construye antes, se trabaja sobre datos sucios.

**Trabajos:**

#### 1.1 EPUB → Tubería
- Conectar `ReadingPipeline.processEpub()` al resultado de Readium (hoy el EPUB va directo a la WebView sin pasar por la tubería)
- Extraer bloques semánticos del HTML de cada capítulo: `Paragraph`, `Heading`, `Image`, `Noise`
- Pasar los bloques a `SpanishTextFormatter` antes de pintar
- Los bloques `Noise` no llegan ni a pantalla ni a TTS

#### 1.2 PDF → Tubería
- Conectar `ReadingPipeline.processPdf()` a PdfRenderer (hoy `PdfReflowManager` tiene su propio pipeline ad-hoc)
- Retirar `PdfReflowManager` como pipeline principal; usarlo solo como fuente de coordenadas de texto
- El orden de lectura sale de `PdfBookViewSynchronizer` (ya escrito)

#### 1.3 TTS unificado
- `TtsManager` consume `List<TtsNarrationUnit>` de la tubería, no texto crudo
- `WordExclusionEngine` se aplica en la tubería, antes de que el texto llegue a Sherpa (hoy se aplica dentro del TTS, demasiado tarde)
- `ImageAnnouncer` inserta su anuncio en la secuencia de narración en el punto exacto de la imagen
- Transición automática de capítulo/página sin silencio

#### 1.4 Resaltado palabra a palabra en TTS
- Sherpa devuelve timestamps de fonema por palabra; mapearlos al bloque de texto en pantalla
- Iluminar la palabra activa durante la narración (ya hay `_currentTtsRange` — hay que alimentarlo desde los timestamps reales, no desde el avance estimado)

**Criterio de salida:** Narrar un EPUB con imagen en el capítulo anuncia la imagen en su sitio. Narrar un PDF ignora cabeceras y números de página sin configuración manual.

---

### Fase 2 — Tipografía de libro en EPUB
**Objetivo:** Que leer en EPUB se sienta como abrir un libro bien impreso en español.

**Por qué aquí:** Una vez que los bloques llegan limpios a la pantalla (Fase 1), la tipografía es ajuste de superficie. Hacerla antes significaría ajustar sobre texto sucio.

**Trabajos:**

#### 2.1 Motor tipográfico
- Justificación real con `hyphens: auto` + diccionario de partición en español (`es-ES`)
- Interlineado proporcional al tamaño de fuente (no un valor fijo)
- Control de viudas y huérfanas (`orphans: 2; widows: 2`)
- Primera línea de capítulo sin sangría, resto con sangría consistente
- `letter-spacing` ajustable (mejora legibilidad en pantallas de densidad alta)

#### 2.2 Panel de ajustes de lectura
- Controles: tamaño de fuente, familia (Literata, Merriweather, sistema), interlineado, márgenes, tema (claro / sepia / oscuro / AMOLED)
- Vista previa en vivo sin cerrar el panel
- Perfiles guardables por libro (el usuario puede tener un perfil para novela y otro para ensayo)
- Valores por defecto elegidos para español a cuerpo de libro (~18 sp, 1.6 interlineado, márgenes generosos)

#### 2.3 Paginación
- Paginación de Readium con doble toque para pasar / retroceder página
- Scroll continuo opcional (sin paginación)
- Indicador de progreso fino: % del libro + tiempo estimado restante a ritmo de lectura personal

**Criterio de salida:** Un párrafo largo en español, justificado, no genera ni un río de espacios ni una línea desbordada.

---

### Fase 3 — Vista libro para PDF
**Objetivo:** Un PDF se lee como un EPUB. El usuario cambia de vista y no cambia de posición.

**Por qué al final de las tres primeras:** Es la parte más compleja. Si se hace sin la tubería lista (Fase 1) y sin la tipografía madura (Fase 2), se construye dos veces.

**Trabajos:**

#### 3.1 Dos vistas, un libro
- **Vista original:** PdfRenderer a resolución óptima para el tamaño de pantalla. Zoom libre. Navegación por página.
- **Vista libro:** Texto extraído por la tubería, presentado con la misma tipografía y controles que el EPUB.
- Botón de alternancia que conserva la posición al cambiar entre vistas.

#### 3.2 Extracción inteligente del PDF
- Orden de lectura correcto para PDFs de una y dos columnas
- Tablas, fórmulas y bloques con layout irregular → se incluyen en vista original sin intentar reformatear (se marca el bloque con un aviso visual discreto)
- Figuras y sus pies de foto anclados al punto del texto donde aparecen
- Notas al pie: se colocan al final de la página/sección, no mezcladas en el flujo

#### 3.3 Coordinación de posición
- `PdfBookViewSynchronizer` (ya escrito) se conecta a ambas vistas
- La posición se guarda como `{página, offset en bloque}` para ser válida en las dos vistas
- Si el usuario pasa de vista original (página 85) a vista libro, aparece en el bloque correspondiente a esa página

#### 3.4 Límites explícitos (no se intenta)
- PDFs escaneados (sin capa de texto): se muestra la vista original con aviso claro. No se hace OCR.
- Tablas numéricas complejas: siempre en vista original dentro de la vista libro.
- Matemáticas / fórmulas LaTeX: bloques sin tocar.

**Criterio de salida:** Los 4 PDFs de prueba (una columna, dos columnas, tablas+figuras, márgenes enormes) se leen en vista libro de corrido. Cambiar a vista original y volver devuelve al mismo punto.

---

### Fase 4 — Reglas de ruido y exclusión de palabras
**Objetivo:** El usuario puede silenciar cualquier patrón que interrumpe su lectura, desde el libro abierto, sin ir a ajustes.

**Por qué separada de la tubería:** La tubería ya detecta ruido automáticamente (Fase 1). Esta fase añade el control manual encima.

**Trabajos:**

#### 4.1 Detección automática ya hecha (`NoiseClassifier`)
- Números de página (árabe, romano, `Pág. X de N`, `— X —`)
- Cabeceras y pies recurrentes (aparecen en >25 % de páginas con el mismo texto)
- URLs, emails, avisos legales, ornamentos (`* * *`)

#### 4.2 Control manual desde la lectura
- Mantener pulsada una palabra → menú con: **Ignorar esta palabra**, **Ignorar este patrón** (frase corta), **No ignorar** (si ya estaba ignorada)
- Las reglas manuales se muestran en Ajustes → Reglas de lectura, editables y borrables
- Ámbito de cada regla: **este libro** o **todos los libros**

#### 4.3 Revisión de lo detectado automáticamente
- Un panel de revisión accesible desde Ajustes del libro: lista de lo que la app está ignorando automáticamente en ese libro
- El usuario puede desmarcar cualquier ítem: "esto sí es parte del libro"

**Criterio de salida:** En un PDF académico, los encabezados con título del artículo y los números de página desaparecen de la narración sin configuración. El usuario puede recuperar uno en dos toques si quiere.

---

### Fase 5 — Diseño y navegación
**Objetivo:** Todo se siente coherente. Ningún control visible existe si no hace lo que promete.

**Por qué al final:** El diseño vive sobre las funciones. Si se hace antes, se rediseña dos veces.

**Trabajos:**

#### 5.1 Fundaciones de diseño
- Escala de espaciado de 4 dp (4, 8, 12, 16, 24, 32, 48, 64)
- Tipografía base: Display / Title / Body / Label con tamaños y pesos fijos
- Tiempos de animación: Entrada 300 ms / Salida 200 ms / Micro-interacción 150 ms (curvas `EaseInOut` / `EaseOut`)
- Paleta: Material You dinámica + temas de lectura propios (sepia, AMOLED)
- Estados obligatorios para toda pantalla: Cargando / Vacío / Error / Contenido

#### 5.2 Navegación
- Dos destinos raíz: **Biblioteca** y **Lector** (sin barra inferior en el lector)
- Gestos en el lector: toque centro → UI, toque borde derecho/izquierdo → página, swipe arriba → panel de ajustes
- Panel único del lector (hoy hay varios hojas): Ajustes de lectura / TTS / Marcadores y resaltados / Notas, en pestañas dentro de un solo modal

#### 5.3 Biblioteca
- Grid de portadas con metadatos editables inline (título, autor, serie)
- Filtros instantáneos: formato, leído/leyendo/sin empezar, serie, recientes
- Búsqueda de texto completo en los libros (título, autor, contenido si está indexado)
- Estado vacío con instrucción clara de cómo importar

#### 5.4 Lector reconstruido
- Pantalla de lectura sin ningún elemento permanente visible (todo se muestra/oculta con un toque)
- Animación de cambio de página: simple fade o slide según preferencia
- Barra de progreso del libro en la parte inferior, discreta
- Al cerrar el lector: animación de "guardar posición" que da confianza visual

**Criterio de salida:** Todo control que se ve en pantalla funciona. Ningún botón hace nada vacío.

---

## Lo que no entra en este plan

| Fuera | Motivo |
|---|---|
| Sincronización multi-dispositivo | Requiere servidor, es un proyecto aparte |
| IA / resúmenes / "pregúntale al libro" | Pediste que salga del alcance |
| OCR para PDFs escaneados | Decisión tuya: si tus PDFs son escaneos, se añade como fase propia |
| CBZ, CBR, MOBI, FB2, Markdown | Fuera del alcance desde Fase 4 del trabajo previo |
| Publicación en Play Store | Es personal. No entra ninguna restricción de política de tienda |

---

## Orden de ejecución y dependencias

```
Fase 1 (Tubería conectada)
   ├── Fase 2 (Tipografía EPUB)
   ├── Fase 3 (Vista libro PDF)
   └── Fase 4 (Reglas de ruido)
         └── Fase 5 (Diseño final)
```

La Fase 1 desbloquea todo. Las fases 2, 3 y 4 pueden ir en paralelo una vez terminada la 1. La 5 va al final porque el diseño final vive sobre las funciones terminadas.

---

## Escenarios de aceptación (la app es premium cuando estos pasan todos)

| # | Escenario | Resultado esperado |
|---|---|---|
| 1 | Cierro la app a mitad de capítulo | Al reabrir estoy en la misma línea |
| 2 | Abro un EPUB desde el explorador de archivos | Se importa y se abre automáticamente |
| 3 | Abro un PDF desde WhatsApp | Igual que el anterior |
| 4 | Expulso la tarjeta SD mientras leo | Mi biblioteca no desaparece |
| 5 | Pulso Play con voz neuronal | No hay congelamiento. El audio empieza en < 1 s |
| 6 | Narro un capítulo con una imagen | La app dice "Imagen: [descripción]" en el sitio exacto |
| 7 | Narro un PDF académico | Cabeceras y páginas no se narran. El texto fluye |
| 8 | Resalto un párrafo | Al reabrir el libro sigo viendo el resaltado |
| 9 | Cambio un PDF a vista libro y vuelvo a original | Mismo punto en los dos cambios |
| 10 | Mantengo pulsada una palabra → Ignorar | Esa palabra desaparece de la narración en ese libro |
| 11 | Estadísticas de hoy | Reflejan el tiempo real que leí, no `null` |
| 12 | Cierro el lector con TTS activo | La sesión se guarda. No hay `endAt = null` |

---

*Generado: 2026-10-10 — Base: commit `40eacf9` — 101 archivos Kotlin — 69/69 tests*
