# Skill: Depuración Sistemática (Systematic Debugging)

## Nombre
systematic-debugging

## Descripción
En lugar de pegar el error completo y decir "arréglalo", la habilidad consiste en pedir primero una hipótesis basada en logs, aislar el componente afectado y proponer un cambio atómico.

## Divulgación Progresiva
- **Nivel 1 (nombre):** Depuración Sistemática
- **Nivel 2 (descripción):** Hipótesis → aislamiento → cambio atómico.
- **Nivel 3 (cuerpo completo):** Protocolo completo de depuración.

## Instrucciones Completas

### 1. Paso 1: Recolectar Evidencia
Antes de proponer cualquier corrección:
- Leer el mensaje de error completo (stack trace, logs de Android Studio/Logcat).
- Identificar la línea exacta y el archivo donde ocurre el fallo.
- Revisar cambios recientes en Git (`git log --oneline -10`, `git diff`).

### 2. Paso 2: Formular Hipótesis
El agente DEBE proponer una hipótesis antes de modificar código:
- "El error ocurre porque X causa Y en el archivo Z, línea N."
- "La causa probable es [falta de migración Room / bloqueo de Main thread / dependencia faltante]."
- Nunca adivinar sin base en logs o evidencia del código.

### 3. Paso 3: Aislar el Componente
- Determinar qué módulo/clase está afectado (ej. `TtsManager`, `ReaderViewModel`, `ReadingPipeline`).
- Verificar si el error es de compilación, runtime o lógica de negocio.
- Reproducir el error de forma aislada si es posible.

### 4. Paso 4: Proponer Cambio Atómico
- Un solo cambio pequeño y bien delimitado.
- Máximo 2 archivos de producción + 1 test.
- Usar parches tipo unified diff, nunca reescribir archivos completos.

### 5. Paso 5: Validar y Commit
- Ejecutar `.\gradlew.bat :app:testDebugUnitTest`.
- Si pasa, commit atómico. Si falla, revertir y proponer nueva hipótesis.

### 6. Ejemplo de Salida de Depuración
```
## Hipótesis
El crash en ReaderViewModel se debe a que `TtsManager.speak()` se llama en Main thread.

## Componente Afectado
- `core/tts/TtsManager.kt` - método `speakWithSherpa()`

## Cambio Atómico
Envolver la llamada TTS en `withContext(Dispatchers.IO) { ... }`.

## Validación
.\gradlew.bat :app:testDebugUnitTest --tests "com.example.woodsreader.ui.reader.ReaderViewModelTest"
```