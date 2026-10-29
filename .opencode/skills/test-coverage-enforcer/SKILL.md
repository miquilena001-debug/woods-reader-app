# Skill: Test Coverage Enforcer

## Nombre
test-coverage-enforcer

## Descripción
Garantiza que cada funcionalidad tenga pruebas unitarias o de integración que pasen, aplica cobertura mínima, TDD (pruebas primero) y criterios de aceptación BDD del roadmap para cero regresiones.

## Divulgación Progresiva
- **Nivel 1 (nombre):** Test Coverage Enforcer
- **Nivel 2 (descripción):** Cobertura de pruebas, TDD y criterios de aceptación.
- **Nivel 3 (cuerpo completo):** Protocolo de pruebas y cobertura.

## Instrucciones Completas

### 1. Estructura de Pruebas
- Pruebas unitarias en `app/src/test/java/com/example/woodsreader/...`
- Pruebas de Room con `inMemoryDatabaseBuilder` + `allowMainThreadQueries()`.
- Tests por módulo: `core/pipeline/`, `core/tts/`, `ui/reader/`, `data/repository/`, etc.

### 2. TDD por Iteración
```
RED → Escribir prueba que falle (sin código de producción)
GREEN → Escribir código mínimo para pasar
REFACTOR → Limpiar código, pruebas aún verdes
COMMIT → Commit atómico con mensaje convencional
```

### 3. Cobertura Mínima por Área
- `ReadingPipeline`: 90%+ (motor semántico crítico)
- `TtsManager` / `ReaderTtsService`: 85%+ (narración en segundo plano)
- `Room DAOs` / `BookRepository`: 80%+ (persistencia)
- `EpubContentExtractor` / `PdfBookViewSynchronizer`: 85%+ (parsing EPUB/PDF)
- `ReaderViewModel`: 80%+ (estado del lector)

### 4. Criterios de Aceptación BDD (Roadmap)
- Los 12 escenarios de aceptación del `ROADMAP.md` deben tener pruebas automatizadas.
- Escenario de apertura de archivo EPUB/PDF.
- Escenario de narración TTS con resaltado.
- Escaneo de biblioteca desde MediaStore.
- Reglas de exclusión de palabras desde selección de texto.
- Modo dual PDF (Vista Original ↔ Vista Libro).

### 5. Comando de Validación Obligatorio
```powershell
.\gradlew.bat :app:testDebugUnitTest
```
- Código de salida debe ser 0.
- Reportar el resultado y el recuento que indique la ejecución; no asumir una meta fija.
- Si falla: conservar el trabajo existente, diagnosticar el error y revertir únicamente los cambios propios y acotados si hace falta.

### 6. Salida de Cobertura
```
## Cobertura de Pruebas — [Módulo]

### Pruebas Escritas
- [Lista de clases de prueba y qué validan]

### Cobertura Estimada
- Línea: X% | Rama: X% | Método: X%

### Pruebas Fallando
- [Lista de pruebas rojas y por qué]

### Criterios de Aceptación BDD
- Escenario X: ✅ / ❌

### Listo para commit
Sí / No
```