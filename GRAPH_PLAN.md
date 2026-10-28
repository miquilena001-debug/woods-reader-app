# GRAPH_PLAN.md - Grafo de conocimiento (Woods Reader)

Paquete base: com.example.woodsreader. Stack: Kotlin, Compose, Hilt, Room, Readium 3.0.3, Media3.
100% offline, sin APIs externas.

## REGLAS DE ORO (leer siempre)
1. Una fase = un objetivo. Antes de editar un archivo existente, MUESTRA su contenido actual.
2. No tocar archivos fuera de la fase. No refactorizar nada existente.
3. Al terminar: lista archivos creados/modificados y di "BUILD OK?" para que el usuario compile.
4. Si una API de Readium no esta clara, NO inventar: reportar y parar.
5. Room: la BD actual se migra con AutoMigration (solo tablas nuevas). PROHIBIDO fallbackToDestructiveMigration.
6. combine de 4+ flows: usar combines anidados de 2. No usar return dentro de derivedStateOf.
7. bookId = fingerprint del libro (con el fallback por URI ya existente).

## FASE 1 - Esquema Room (archivo: data/graph/GraphEntities.kt)
enum EntityType { PERSONAJE, SECTA, LUGAR, ARTEFACTO, RANGO, OTRO }
- GraphEntity(id autoGen, bookId, name, type, description="", firstChapterIndex=0, mentionCount=0, confirmed=false, ignored=false). Indice unico (bookId,name).
- GraphAlias(entityId, alias) PK compuesta, FK CASCADE a GraphEntity.
- GraphRelation(id autoGen, bookId, sourceId, targetId, label="", weight=1, manual=false, firstChapterIndex=0). Indices en bookId/sourceId/targetId, FK CASCADE x2.
- GraphMention(id autoGen, entityId, chapterIndex, href, progression: Double, snippet). Indice unico (entityId,chapterIndex), FK CASCADE.
Tablas: graph_entity, graph_alias, graph_relation, graph_mention. TypeConverter para EntityType (name).
Registrar en la @Database existente, subir version +1, AutoMigration(from=N,to=N+1), exportSchema=true.
Aceptacion: compila y KSP genera sin errores.

## FASE 2 - DAO + Repositorio (data/graph/GraphDao.kt, GraphRepository.kt)
GraphDao:
- entities(bookId, maxCh, limit=150): Flow<List<GraphEntity>> WHERE confirmed=1 AND ignored=0 AND firstChapterIndex<=maxCh ORDER BY mentionCount DESC
- relations(bookId, maxCh, minW=3): Flow WHERE firstChapterIndex<=maxCh AND (manual=1 OR weight>=minW)
- candidates(bookId): Flow WHERE confirmed=0 AND ignored=0 ORDER BY mentionCount DESC
- nextMention(entityId, fromCh): primera mencion con chapterIndex>=fromCh ORDER BY chapterIndex LIMIT 1; prevMention analoga (<=, DESC)
- insertEntity (IGNORE), upsertRelation (REPLACE), update, insertMentions(list), aliases CRUD
- @Transaction mergeInto(sourceId, targetId): mover aliases+mentions+relaciones al target, agregar nombre del source como alias, borrar source.
- exportAll(bookId) para JSON.
GraphRepository (@Singleton, Hilt): envuelve el DAO. Proveer DAO desde el modulo Hilt de BD existente.
Aceptacion: compila.

## FASE 3 - Layout de fuerzas (ui/graph/ForceLayout.kt) - clase pura, sin Android
class ForceLayout(n: Int, edges: List<Pair<Int,Int>>)
- x,y,vx,vy: FloatArray(n); posicion inicial aleatoria en [-400,400].
- alpha=1f, settled = alpha<0.02f.
- step(): repulsion O(n^2) entre todos los pares: d2=max(dx^2+dy^2,25); f=9000/d2*alpha; empuja a lo largo de (dx,dy)/d.
  Atraccion en aristas: f=(d-120)*0.02*alpha hacia el otro nodo. Gravedad al centro: v -= pos*0.01*alpha.
  Amortiguacion v*=0.85; pos+=v; alpha*=0.985.
- Maximo 150 nodos. Test unitario simple: tras 500 steps, ningun NaN y settled=true.

## FASE 4 - Canvas (ui/graph/GraphCanvas.kt)
Composable GraphCanvas(nodes: List<GraphEntity>, edges: List<Pair<Int,Int>>, layout: ForceLayout, tick: Int, selectedIdx: Int?, onSelect: (Int?)->Unit)
- Estado: scale (0.2..4), offset: Offset.
- pointerInput 1: detectTransformGestures { c, pan, zoom, _ -> ns=(scale*zoom).coerceIn(0.2f,4f); offset = c - (c - offset)/scale*ns + pan; scale=ns }
- pointerInput 2: detectTapGestures: mundo = (p - offset - size.center)/scale (el dibujo se centra en size/2: restar el mismo centro en el tap). Nodo mas cercano con distancia < 36/scale -> onSelect, si no onSelect(null).
- Dibujo: translate(offset + size/2) { scale(scale, pivot=Zero) { aristas (alpha .25, resaltadas .9 si tocan el seleccionado) ; nodos (radio 10+min(sqrt(mentionCount),18), color por EntityType) ; etiquetas con rememberTextMeasurer SOLO si scale>0.7 o seleccionado o mentionCount>200 } }
- Leer `tick` dentro del Canvas para forzar redibujo.
Aceptacion: pantalla de prueba con 30 nodos falsos, zoom/pan/tap fluidos.

## FASE 5 - ViewModel + pantalla (ui/graph/GraphViewModel.kt, GraphScreen.kt, ruta de navegacion)
- ViewModel(Hilt): bookId desde SavedStateHandle; estado: spoilerFree (default true), filtros de tipo (set), busqueda, seleccionado.
  maxCh = si spoilerFree capitulo actual de lectura, si no Int.MAX_VALUE. Combinar flows con combines anidados de 2.
  Loop de fisica en viewModelScope/Default: step() cada frame (delay ~16ms) hasta settled; incrementa tick (MutableStateFlow<Int>).
  Recrear ForceLayout solo cuando cambia el conjunto de nodos.
- GraphScreen: TopAppBar con busqueda, chips por tipo, switch "Sin spoilers", boton "Candidatos (N)". Canvas a pantalla completa.
- Entrada desde la biblioteca/lector (icono) hacia la ruta.

## FASE 6 - Ficha de entidad (ui/graph/EntitySheet.kt)
ModalBottomSheet: nombre, tipo (editable), descripcion editable, aliases, lista de relaciones (tap = centrar/seleccionar ese nodo), botones: "Ver en texto", "Anadir relacion" (dialogo: destino + etiqueta).

## FASE 7 - Saltar al texto
"Ver en texto": mencion = nextMention(id, capituloActual) ?: prevMention. Construir Locator (href + progression) y navigator.go(locator).
VERIFICAR en raw GitHub tag 3.0.3 la firma real de Locator/Url/Locations antes de escribir. Anadir flechas anterior/siguiente mencion.

## FASE 8 - Extractor (work/GraphExtractionWorker.kt + util/EntityHeuristics.kt)
HiltWorker CoroutineWorker con setForeground y progreso. Streaming capitulo a capitulo (nunca el libro entero en memoria). Disparado manualmente con boton "Analizar libro".
Pasada 1 (conteo): quitar tags HTML con regex; candidatos con regex \p{Lu}[\p{L}'-]+(?:\s+\p{Lu}[\p{L}'-]+){0,3}.
 Contar total y apariciones a mitad de frase (no precedidas por ". ", "?", "!", inicio de parrafo, comilla de apertura).
 Descartar: stopwords ES con mayuscula inicial (El, La, Los, Pero, Entonces, Cuando, Sin embargo, etc.), nombres de 1 palabra con <3 apariciones a mitad de frase, y cualquiera con <3 apariciones totales.
 Mantener tope de 3000 candidatos (podar los de menor conteo cada N capitulos).
Pasada 2 (relaciones): solo candidatos aceptados. Por capitulo guardar 1 GraphMention por entidad (href, progression del parrafo, snippet de ~80 chars).
 Por parrafo, +1 de peso a cada par de entidades presentes (HashMap<Long,Int> clave min*N+max). Al final, upsertRelation solo si peso>=3.
 firstChapterIndex de entidad/relacion = primer capitulo donde aparece.
Tipo sugerido por palabras clave en el nombre (mapa editable en EntityHeuristics.kt):
 SECTA: Secta|Clan|Pabellon|Templo|Palacio|Gremio ; LUGAR: Montana|Valle|Ciudad|Reino|Bosque|Mar|Isla ; ARTEFACTO: Espada|Pildora|Talisman|Perla|Tesoro ; RANGO: Etapa|Fase|Nucleo|Fundacion|Alma|Reino de ; resto OTRO.
Todas entran confirmed=false.
Aceptacion: probar en un EPUB corto primero; luego en el de 1705 capitulos medir tiempo y memoria.

## FASE 9 - Revision de candidatos (ui/graph/CandidatesScreen.kt)
Lista ordenada por menciones; swipe derecha = confirmar, izquierda = ignorar; menu "Fusionar con..." (mergeInto) y "Cambiar tipo". Contador de pendientes.

## FASE 10 - Lector (integracion)
Boton en la barra superior del lector -> ModalBottomSheet con mini-grafo (ego graph: entidad + vecinos directos) reutilizando GraphCanvas.
Anadir desde seleccion: usar selectionActionModeCallback del navigator SOLO si es publico en 3.0.3 (verificar); si no, dialogo manual con boton en la barra.

## FASE 11 - JSON glosario (data/graph/GraphJson.kt)
Formato {version:1, bookTitle, entities:[{name,type,description,aliases[]}], relations:[{source,target,label}]} (relaciones por NOMBRE, no por id).
Exportar via Storage Access Framework (CreateDocument), importar con OpenDocument; al importar, fusionar por nombre/alias, confirmed=true.

## FASE 12 - Pulido
Cache de posiciones (guardar x,y por entidad en JSON local) para abrir instantaneo; limite 150 nodos; LOD de etiquetas; estado vacio con CTA "Analizar libro"; haptic al seleccionar.
