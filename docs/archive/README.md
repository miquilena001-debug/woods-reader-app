# Archivo de documentación histórica

Esta carpeta conserva material fuera de la documentación activa. Se mueve aquí porque describe diagnósticos, diseños o planes anteriores, no como inventario del estado actual. El código vigente y sus contratos están en `dona.md`; la arquitectura resumida está en `ARCHITECTURE.md`.

| Documento | Motivo del archivo / estado |
|---|---|
| `REVISION.md` | Auditoría histórica con hallazgos que han cambiado desde su redacción; no usar como diagnóstico actual. |
| `AI_EXECUTION_PLAN.md` | Playbook de ejecución previo; sus límites, umbrales y pasos no coinciden con las reglas vigentes. |
| `GRAPH_PLAN.md` | Diseño de la funcionalidad de grafo, ya presente en los módulos `data/graph`, `ui/graph` y `work`. |
| `FILE_INDEX.md` | Índice estático sustituido por el índice y mapa mantenidos en `dona.md`. |
| `maquetado.md` | Especificación de maqueta/prototipo web previa a la implementación de Compose. |
| `plans/2026-10-23-onboarding-first-voice-pro-purchase.md` | Plan ejecutado: flujo de primera voz IA en ReaderTtsMacroWindow, WoodsProUnlockDialog con compra directa y OnboardingScreen (commits 868dc2a, 6f70865, afd675d). |
| `plans/2026-10-23-pro-license-ecdsa.md` | Plan ejecutado: blindaje asimétrico ECDSA P-256 en ProLicenseManager verificado por ProLicenseManagerTest (commits 868dc2a, 6f70865). |
| `plans/2026-10-23-release-r8-abi-optimization.md` | Plan ejecutado: configuración release R8, shrinkResources, abiFilters y reglas ProGuard endurecidas (commits fb8740e, 7cd879c, 6f70865). |
| `plans/2026-10-24-epub-in-place-translator.md` | Plan ejecutado: pipeline no destructivo in-place en core/translation/calibre/inplace/ validado con 13 tests unitarios (commits c808cd9, 35255cb). |

No se eliminó ningún documento esencial de la arquitectura: estos movimientos son reversibles con Git. `codigo_revision.txt`, `mockup/` y `scripts/` fueron purgados por decisión del usuario; el código de la app se mantiene intacto.
