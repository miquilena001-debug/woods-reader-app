# Skill: Plan-Driven Development (Desarrollo Dirigido por Planes)

## Nombre
plan-driven-dev

## Descripción
Obliga al agente a escribir un plan detallado en Markdown antes de tocar una sola línea de código. Evita improvisaciones y errores arquitectónicos que luego requieran múltiples mensajes de corrección.

## Divulgación Progresiva
- **Nivel 1 (nombre):** Plan-Driven Development
- **Nivel 2 (descripción):** Escritura previa de planes detallados en Markdown antes de codificar.
- **Nivel 3 (cuerpo completo):** Las instrucciones que siguen se cargan solo cuando la tarea lo requiere.

## Instrucciones Completas

### 1. Fase de Análisis (Modo Plan)
Antes de escribir cualquier código, el agente DEBE:
- Analizar el proyecto y los archivos relevantes (lectura quirúrgica vía MCP Filesystem).
- Devolver un plan paso a paso en Markdown con máximo 3-5 puntos principales.
- Identificar archivos exactos a crear/modificar con rutas completas.
- Estimar el esfuerzo y las pruebas necesarias.

### 2. Fase de Validación
El usuario (arquitecto) revisa el plan. Si hay algo incorrecto, se corrige en una sola frase antes de generar código. El agente NO avanza a la ejecución hasta que el plan sea aprobado.

### 3. Fase de Ejecución Atómica
Implementar SOLO el paso 1 del plan mediante cambios limpios y localizados:
- Máximo 2 archivos de producción + 1 archivo de test por iteración.
- Usar parches tipo unified diff (bloques de reemplazo exactos), nunca reescribir archivos completos.
- No bloquear el hilo principal (I/O en Dispatchers.IO/Default).

### 4. Fase de Revisión Rápida
Validar el resultado antes de pasar al siguiente paso:
- Ejecutar `.\gradlew.bat :app:testDebugUnitTest` (código de salida debe ser 0).
- Si falla, revertir con `git checkout -- .` y proponer un cambio atómico alternativo.
- Commit atómico por paso verificado.

### 5. Guardrails del Proyecto Woods Reader
- No agregar dependencias a `build.gradle.kts` / `settings.gradle.kts`.
- No tocar `@Entity`/`@Dao` sin migración + regenerar esquema JSON.
- Solo formatos EPUB y PDF (no CBR/CBZ/MOBI/FB2).
- No borrar métodos de `ReaderViewModel` o `TtsManager`; sustitución quirúrgica.
- Consulta `FILE_INDEX.md` para localizar archivos `.kt` sin escanear el repo.

### 6. Ejemplo de Salida Esperada (Plan)
```
## Plan: Implementar EpubContentExtractor

1. Crear `core/pipeline/EpubContentExtractor.kt` con clase `@Singleton` y método `extractBlocks()`.
2. Crear `core/pipeline/EpubContentExtractorTest.kt` con tests unitarios.
3. Ejecutar `.\gradlew.bat :app:testDebugUnitTest --tests "..."`.
4. Commit: `feat(pipeline): add EpubContentExtractor`.
```