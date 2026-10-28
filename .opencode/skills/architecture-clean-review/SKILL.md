# Skill: Clean Architecture Review

## Nombre
architecture-clean-review

## Descripción
Revisa que el código siga los principios de Arquitectura Limpia: separación de responsabilidades, dependencias que apuntan hacia adentro, dominio puro sin depender de Android, y capas bien definidas (UI, Domain, Data).

## Divulgación Progresiva
- **Nivel 1 (nombre):** Clean Architecture Review
- **Nivel 2 (descripción):** Revisión de capas, dependencias y separación de responsabilidades.
- **Nivel 3 (cuerpo completo):** Protocolo de revisión arquitectónica.

## Instrucciones Completas

### 1. Capas del Proyecto (Woods Reader)
```
UI Layer (Compose, Activities, ViewModels)
    ↓ depende de
Domain Layer (UseCases, Modelos de dominio, Interfaces de repositorio)
    ↓ depende de
Data Layer (Room DAOs, DataStore, Repositorios implementados, Escáneres, Readium/PDFBox)
```

### 2. Reglas de Oro
- **UI no conoce implementaciones de datos:** solo llama a ViewModel y observa StateFlow/Flow.
- **ViewModel no conoce Android directo:** usa `AndroidViewModel` solo si necesita Application; de lo contrario `ViewModel` + `viewModelScope`.
- **UseCases son puros:** clases Kotlin sin import de `android.*`, solo reciben parámetros y devuelven resultados.
- **Repositorios son la única puerta de entrada a datos:** UI nunca llama directamente a DAO, DataStore o escáner.
- **Entidades Room son solo para datos:** no se filtran a UI; se mapean a modelos de dominio.

### 3. Hilt y DI
- Módulos en `di/`: Core, Database, Tts, Scanner, Library, DataStore.
- `@Singleton` para componentes de larga vida (TtsManager, ReadingPipeline, Repositorios).
- `@ActivityScoped` / `@FragmentScoped` solo cuando sea necesario.
- No usar `@Inject` en constructores de clases de UI que no sean ViewModel/Composable con hiltViewModel.

### 4. Room y Migraciones
- Cada cambio en `@Entity` requiere migración en `RoomDatabase.Callback` o archivo de versión.
- Regenerar esquema JSON en `app/schemas/` con KSP después de cambios de entidad.
- No borrar columnas o tablas sin migración hacia atrás.

### 5. Salida de Revisión Arquitectónica
```
## Revisión de Arquitectura Limpia — [Módulo/Clase]

### Capa Asignada
- [UI / Domain / Data]

### Violaciones Detectadas
- [Archivo.kt:línea] UI llama directamente a DAO → Mover a Repository.
- [Archivo.kt] UseCase importa android.* → Quitar dependencia, usar interfaz.

### Dependencias que apuntan hacia afuera (prohibido)
- [Lista]

### Cambios Propuestos (diff)
- [Bloques de reemplazo]

### Puntuación Arquitectura
Limpieza: X/10 | Acoplamiento: Bajo/Medio/Alto | Listo: Sí/No
```