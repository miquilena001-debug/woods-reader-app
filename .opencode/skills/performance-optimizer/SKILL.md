# Skill: Performance Optimizer (Rendimiento)

## Nombre
performance-optimizer

## Descripción
Optimiza memoria, batería, tiempo de inicio, tamaño de APK, renderizado de Compose y fluidez de lectura (EPUB/PDF/TTS) para que Woods Reader sea ligero y fluido en dispositivos de gama media.

## Divulgación Progresiva
- **Nivel 1 (nombre):** Performance Optimizer
- **Nivel 2 (descripción):** Optimización de memoria, batería, APK y renderizado.
- **Nivel 3 (cuerpo completo):** Protocolos de optimización específicos del proyecto.

## Instrucciones Completas

### 1. Memoria y Battery
- Nunca cargar bitmaps de todas las páginas PDF/EPUB a la vez; usar `use { }` por página con `PdfRenderer.Page`.
- Imágenes con Coil3: memoria caché limitada, placeholder, reutilización.
- TTS: liberar motores Sherpa ONNX y System TTS en `onCleared()` / `onDestroy()`.
- Evitar fugas de memoria en ViewModels (no capturar Context sin `AndroidViewModel` o `viewModelScope`).

### 2. Tiempo de Inicio y APK
- Habilitar R8/ProGuard en build de release (`minifyEnabled true`).
- Medir y reportar el tamaño real del APK, sin umbral fijo.
- Revisar dependencias transitivas de Readium 3.x y Media3 para no incluir código muerto.
- No agregar nuevas dependencias sin justificación (guardrail inviolable).

### 3. Renderizado Compose (UI)
- Evitar recomposiciones innecesarias: usar `derivedStateOf` para resaltado de narración.
- Estructuras de layout poco profundas (evitar Column/Column anidadas excesivas).
- Listas largas (biblioteca, búsqueda): usar `LazyColumn` con `key` estable.
- Imágenes en UI: Coil3 `AsyncImage` con `size` y `placeholder`.

### 4. Lectura y TTS
- Pipeline de lectura: procesar bloques en background, no bloquear el hilo de UI.
- Sincronización highlight: actualizar solo el bloque activo, no toda la pantalla.
- TTS: cola `List<TtsNarrationUnit>` pre-procesada, no re-filtrar strings grandes en tiempo de narración.

### 5. Métricas de Éxito
- `.\gradlew.bat :app:testDebugUnitTest` → informar el resultado y recuento reportados por Gradle.
- `.\gradlew.bat :app:assembleDebug` → salida 0; informar el tiempo medido sin umbral fijo.
- Tamaño del APK medido y reportado, sin umbral arbitrario.
- Medir el tiempo de apertura en un dispositivo representativo y registrar el resultado, sin meta fija.

### 6. Salida de Optimización
```
## Optimización de Rendimiento — [Componente]

### Problema Detectado
- [Descripción + métrica actual]

### Cambio Propuesto (diff)
- [Bloque de reemplazo exacto]

### Impacto Esperado
- Memoria: ↓X% | CPU: ↓X% | APK: ↓X MB | Inicio: ↓X ms

### Validación
- Comando de prueba asociado.
```