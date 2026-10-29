# Skill: Flujo de Trabajo Ideal para Proyectos (Atomic Workflow)

## Nombre
atomic-workflow

## Descripción
Ciclo paso a paso para maximizar la eficiencia en proyectos como aplicaciones móviles o backends: Análisis → Validación → Ejecución atómica → Revisión rápida.

## Divulgación Progresiva
- **Nivel 1 (nombre):** Flujo de Trabajo Atómico
- **Nivel 2 (descripción):** Ciclo de 4 fases para desarrollo eficiente y seguro.
- **Nivel 3 (cuerpo completo):** Protocolo completo del ciclo.

## Instrucciones Completas

### Fase 1: Análisis (Modo Plan)
- Dar el objetivo general al agente.
- Pedir: "Analiza el proyecto y devuélveme un plan paso a paso en 3 puntos".
- El agente usa lectura quirúrgica y dona.md para localizar archivos.

### Fase 2: Validación (Tú eres el Arquitecto)
- Revisar el plan generado por la IA.
- Si hay algo mal, corregirlo en una sola frase antes de generar código.
- La IA es el ejecutor; tú decides qué se implementa.

### Fase 3: Ejecución Atómica
- Pedir que implemente SOLO el paso 1 del plan.
- Cambios limpios y localizados; modificar los archivos necesarios para el cambio lógico y validarlos.
- Usar parches tipo unified diff.
- No bloquear Main thread.

### Fase 4: Revisión Rápida
- Validar con pruebas locales o ejecución rápida.
- Ejecutar `.\gradlew.bat :app:testDebugUnitTest` (código de salida 0).
- Si pasa: registrar el resultado y pasar al siguiente paso; crear un commit solo si se solicita o el flujo lo requiere explícitamente.
- Si falla: conservar el trabajo existente, diagnosticar el error y revertir únicamente los cambios propios y acotados si hace falta.

### Ciclo Completo
```
Análisis → Validación → Ejecución Atómica → Revisión Rápida
     ↓                                          ↑
     └────────────── Repetir por cada paso ─────┘
```

### Criterio de Finalización (Woods Reader)
1. Ejecutar `:app:testDebugUnitTest` cuando corresponda e informar su resultado y recuento reales.
2. `.\gradlew.bat :app:assembleDebug` compila con salida 0; informar el tiempo medido sin umbral fijo.
3. Medir y reportar el tamaño real del APK, sin umbral fijo.
4. Mantener un cambio lógico por commit; no crear commits salvo que se solicite o el flujo lo requiera explícitamente.