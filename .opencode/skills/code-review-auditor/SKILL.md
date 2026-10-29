# Skill: Code Review & Auditor de Calidad

## Nombre
code-review-auditor

## Descripción
Auditoría exhaustiva de calidad de código: detección de code smells, vulnerabilidades, bugs silenciosos, incumplimiento de guardrails del proyecto y deuda técnica antes de que lleguen a producción.

## Divulgación Progresiva
- **Nivel 1 (nombre):** Code Review & Auditor
- **Nivel 2 (descripción):** Revisión de calidad, seguridad y cumplimiento de reglas del proyecto.
- **Nivel 3 (cuerpo completo):** Protocolo de auditoría completo para Woods Reader.

## Instrucciones Completas

### 1. Checklist de Auditoría por Cambio
Antes de aprobar cualquier commit o fusión, verificar:

#### Arquitectura y Capas
- [ ] Respeta separación de capas: UI (Compose) → ViewModel → UseCase/Repository → Data (Room/DataStore/Scanner)
- [ ] No hay lógica de negocio en Composable o Activity
- [ ] DI correcto con Hilt (@Singleton, @Inject, módulos en di/)
- [ ] No hay dependencias cíclicas entre módulos

#### Rendimiento y Hilos
- [ ] I/O, bitmaps, texto, ONNX en Dispatchers.IO o Default
- [ ] UI solo en Dispatchers.Main
- [ ] No hay corrutinas GlobalScope sin Scope vinculado al ciclo de vida
- [ ] Lecturas de Room en corrutinas, nunca en Main thread bloqueante

#### Guardrails del Proyecto (.cursorrules)
- [ ] No se agregaron dependencias nuevas a build.gradle.kts / settings.gradle.kts
- [ ] No se tocó @Entity/@Dao sin migración + regenerar esquema JSON (app/schemas/)
- [ ] Edición atómica: un cambio logico por commit; modificar los archivos necesarios y validar
- [ ] No se borraron métodos de ReaderViewModel o TtsManager (sustitución quirúrgica)
- [ ] Solo formatos EPUB y PDF (no CBR/CBZ/MOBI/FB2)

#### Seguridad y Permisos
- [ ] No hay secretos, claves o tokens hardcodeados (usar keystore.properties / variables de entorno)
- [ ] Permisos de archivos solicitados en tiempo de ejecución (Android 10+ scoped storage)
- [ ] Archivos sensibles no se almacenan en texto plano sin cifrar

### 2. Salida de Auditoria (Formato)
```
## Auditoría de Calidad — [Nombre del cambio]

### ✅ Paso
- Archivos revisados: [lista]
- Reglas cumplidas: [lista]

### ⚠️ Advertencias
- [Archivo.kt:línea] Descripción del problema y por qué es riesgoso.

### ❌ Bloqueantes (no mergear hasta corregir)
- [Archivo.kt:línea] Error crítico + corrección propuesta en diff.

### 📊 Puntuación
Calidad: X/10 | Riesgo: Bajo/Medio/Alto | Listo para commit: Sí/No
```

### 3. Ejemplo de Salida para Woods Reader
```
## Auditoría — Integración EpubContentExtractor

### ✅ Paso
- Archivos: core/pipeline/EpubContentExtractor.kt, EpubContentExtractorTest.kt
- Reglas: Sin nuevas dependencias, sin bloqueo Main thread, edición atómica (2+1)

### ⚠️ Advertencias
- EpubContentExtractor.kt:45 — Jsoup.parse en hilo llamante; asegurar que se invoque desde Dispatchers.Default.

### ❌ Bloqueantes
- Ninguno.

### 📊 Puntuación
Calidad: 9/10 | Riesgo: Bajo | Listo para commit: Sí
```