# Skill: Test-Driven Development (TDD) Asistido por IA

## Nombre
tdd-assisted

## Descripción
Pide primero que se escriban las pruebas unitarias o de integración, y luego el código mínimo necesario para pasarlas. Reduce alucinaciones del modelo al proporcionar un marco de validación matemático y automatizado.

## Divulgación Progresiva
- **Nivel 1 (nombre):** TDD Asistido por IA
- **Nivel 2 (descripción):** Pruebas primero, código después.
- **Nivel 3 (cuerpo completo):** Instrucciones completas de TDD.

## Instrucciones Completas

### 1. Escribir Pruebas Primero
Antes de cualquier código de producción:
- Identificar el comportamiento esperado de la función/clase.
- Escribir pruebas unitarias que definan ese comportamiento (Arrange-Act-Assert).
- Incluir casos borde (edge cases): nulos, vacíos, valores extremos, errores de red/archivo.
- Las pruebas deben fallar inicialmente (RED).

### 2. Escribir Código Mínimo
- Implementar solo lo necesario para que las pruebas pasen (GREEN).
- No agregar funcionalidad anticipada ni "por si acaso".
- Usar sustitución quirúrgica: modificar solo las funciones afectadas.

### 3. Refactorizar
- Una vez las pruebas pasan, limpiar el código sin cambiar el comportamiento.
- Mantener las pruebas verdes en todo momento.

### 4. Flujo TDD por Iteración
```
RED → Escribir prueba que falle
GREEN → Escribir código mínimo para pasar
REFACTOR → Limpiar código, pruebas aún verdes
COMMIT → Commit atómico con mensaje descriptivo
```

### 5. Comando de Verificación Obligatorio
```powershell
.\gradlew.bat :app:testDebugUnitTest
```
El código de salida debe ser `0`. Si no, el paso NO está completado.

### 6. Aplicación en Woods Reader
Ejemplos de pruebas a escribir primero:
- `EpubContentExtractorTest` → extracción de bloques desde HTML.
- `ReadingPipelineTest` → procesamiento semántico de bloques.
- `PdfBookViewSynchronizerTest` → sincronización de posición PDF.
- `ReaderViewModelTest` → estado del lector y narración TTS.

### 7. Guardrails
- No borrar métodos existentes de `ReaderViewModel` o `TtsManager`.
- No bloquear Main thread en pruebas de I/O.
- Usar `inMemoryDatabaseBuilder` para tests de Room.