# Skill: Estrategias de Ultra-Bajo Consumo de Tokens

## Nombre
low-token

## Descripción
Tácticas para minimizar el consumo de tokens en cada interacción con el agente: lectura quirúrgica, edición por diff, e instrucciones globales persistentes.

## Divulgación Progresiva
- **Nivel 1 (nombre):** Ultra-Bajo Consumo de Tokens
- **Nivel 2 (descripción):** Leer solo lo necesario, editar solo lo que cambia, no repetir instrucciones.
- **Nivel 3 (cuerpo completo):** Estrategias detalladas.

## Instrucciones Completas

### 1. Lectura Quirúrgica (Surgical File Injection)
- NUNCA arrastrar carpetas enteras ni pegar archivos de 1,000 líneas al chat.
- Usar el servidor MCP Filesystem para leer únicamente las funciones o clases estrictamente necesarias.
- Especificar `file_path:line_range` para lecturas focalizadas.
- Consultar `FILE_INDEX.md` para localizar archivos `.kt` sin escanear el repositorio.

### 2. Patrón de Edición por Diff (Patching / Unified Diffs)
- NUNCA reescribir archivos completos.
- Devolver únicamente bloques de reemplazo tipo diff (código exacto a cambiar).
- Reduce la salida de tokens hasta en un 80% en proyectos grandes.
- Formato esperado:
  ```
  --- a/ruta/archivo.kt
  +++ b/ruta/archivo.kt
  @@ -10,7 +10,7 @@
  -codigo_antiguo
  +codigo_nuevo
  ```

### 3. Instrucciones Globales Persistentes
- Todo lo que se repite en cada prompt (estándares de código, librerías, idioma, restricciones de arquitectura) es desperdicio de tokens.
- Guardar una sola vez en `.cursorrules` (raíz del proyecto).
- OpenCode lo carga al inicio de la sesión y queda en caché.
- Este archivo es la fuente de verdad para guardrails del proyecto.

### 4. Límites por Iteración
- Máximo 2 archivos de producción + 1 archivo de test por iteración.
- Nunca editar masivamente más de 3 archivos en un solo paso.
- Si una tarea requiere más, dividirla en micro-pasos atómicos.

### 5. Contexto Limpio
- Mantener la ventana de contexto libre de información irrelevante.
- Evitar que la IA se sature y comience a cometer errores por falta de atención.