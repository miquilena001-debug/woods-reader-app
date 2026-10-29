# Woods Reader — Estado y próximos pasos

> Documento de seguimiento del producto actual; no es una especificación de release. La evidencia de archivos y arquitectura está en [`dona.md`](dona.md), y los diseños históricos se conservan en [`docs/archive/`](docs/archive/README.md).

## Estado de referencia

- Revisión local basada en HEAD `9953374` (`master`); cambios de documentación aún no confirmados.
- Build configurado como `1.0.10` / `versionCode 11`.
- `git ls-files "*.kt"` enumera 263 archivos Kotlin en total (producción y tests); hay 58 archivos Kotlin bajo `app/src/test/`. La búsqueda estática contó 269 anotaciones `@Test`; esto no es un resultado de ejecución. No se ejecutaron tests ni builds para esta actualización documental.
- El inventario de `dona.md` debe consultarse como mapa de código, no como evidencia de que cada función se validó funcionalmente en dispositivo.

## Capacidades presentes en el código

| Área | Evidencia actual | Estado de esta revisión |
|---|---|---|
| EPUB/PDF, lector y navegación | Readium, coordinadores de `domain/reader/`, UI en `ui/reader/` | Implementado en código; sin validación funcional aquí |
| Procesamiento semántico y PDF reflow | `core/pipeline/`, `PdfParagraphHealer`, `PdfReflowManager` | Implementado en código; pruebas no ejecutadas aquí |
| TOC virtual PDF | `LoadBookFastUseCase` y `PdfParagraphHealer` | Integrado en código |
| Búsqueda/exportación PDF | `ReaderIntelligenceAndExportCoordinator` | Integrado en código |
| Conversión PDF → EPUB | `PdfCleanExtractorAndConverter` y opciones de biblioteca | Integrado en código; validar en dispositivo antes de darlo por completo |
| TTS offline | `TtsManager`, Sherpa y `ReaderTtsService` | Presente en la arquitectura |

## Pendientes que requieren cierre funcional

1. **P11 — resaltado de narración por palabra:** `updateNarrationHighlight` y `NarrationHighlight` existen como ganchos, pero no se encontró una llamada activa al método. Conectar timestamps reales y comprobar UI/TTS antes de marcarlo como completado.
2. **P13 — vista libro/original PDF:** hay sincronizador y método `togglePdfBookView`; confirmar que ambas vistas se pueden alternar desde la UI preservando posición y que los criterios de aceptación pasan.
3. **P8 — cobertura de biblioteca:** reemplazar `LibraryViewModelExtremeTest` (actualmente solo `assertTrue(true)`) con pruebas de comportamiento útiles.
4. Ejecutar y registrar `:app:testDebugUnitTest`, `:app:assembleDebug` y `tools/check_dona.ps1` al cerrar un cambio de código. Este documento no atribuye resultados no ejecutados.

## Criterio de actualización
Marcar una tarea como completa solo con evidencia de implementación y verificación correspondiente. Para cambios de audio, navegación, servicios o UI, anotar por separado validación automatizada y validación en dispositivo. El plan de paridad PDF anterior se conserva en `docs/archive/plans/` como referencia, no como backlog autoritativo.
