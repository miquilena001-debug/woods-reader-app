# Evidencia de Verificación de Mutaciones: Paso 11

Este documento registra la verificación empírica de las 22 pruebas de caracterización (P1–P22) en `ReaderViewModelProgressCharacterizationTest.kt` contra el código de producción en HEAD (`e748f1f`) antes de cualquier extracción.

Cada prueba fue confrontada con una mutación real de producción aplicada de forma aislada. Se capturó el diff exacto producido por `git diff` y el mensaje literal de error extraído del reporte XML generado por Gradle (`app/build/test-results/testDebugUnitTest/TEST-com.example.woodsreader.ui.reader.ReaderViewModelProgressCharacterizationTest.xml`).

---

## 1. Resumen de Verificación

| Id | Test | Estado | Tipo de Mutación / Observación |
|---|---|---|---|
| P1 | `testP1_onLocatorChanged_pdfOriginal_calculatesGlobalProgressWithPageFraction` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P2 | `testP2_onLocatorChanged_pdfOriginal_persistsLastKnownReflowPage` | **VERIFICADO** | Mutante equivalente redundante (L807 y L812 comentadas juntas) |
| P3 | `testP3_onLocatorChanged_pdfOriginal_nullPosition_fallbackToOne` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P4 | `testP4_onLocatorChanged_pdfReflow_doesNotSaveOrUpdateProgress` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P5 | `testP5_onLocatorChanged_epub_duplicateJson_ignored` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P6 | `testP6_saveProgress_manualMode_incrementsWords` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P7 | `testP7_saveProgress_ttsMode_doesNotIncrementWordsNorAdvanceProgression` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P8 | `testP8_saveProgress_duplicatePageKey_doesNotAddPageRead` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P9 | `testP9_saveProgress_epub_chapterChange_triggersPrefetchAndCache` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P10 | `testP10_saveProgress_prefersGlobalProgressOverLocatorProgression` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P11 | `testP11_onTtsLocatorChanged_coverPageActive_returnsEarly` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P12 | `testP12_onTtsLocatorChanged_updatesLastSavedLocatorAndSavesTtsProgress` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P13 | `testP13_flushReadingProgress_activeTts_takesTtsLocator` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P14 | `testP14_flushReadingProgress_nullNav_fallsBackToAnnotationsLocator` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P15 | `testP15_flushReadingProgress_deserializesLastSavedLocatorJson` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P16 | `testP16_flushReadingProgress_pdfOriginal_remembersPageInTracker` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P17 | `testP17_flushReadingProgress_epub_executesSaveProgressBeforeFlush` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P18 | `testP18_getCurrentChapterIndex_resolvesFromReadingOrderWhenCacheMiss` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P19 | `testP19_getFallbackLocator_prefersLastTtsLocatorOverCurrent` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P20 | `testP20_onLocatorChanged_pdfOriginal_duplicateInvocation_triggersTwice` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P21 | `testP21_getFallbackLocator_invalidJsonFallsBackWithoutThrowing_blankJsonSkipsParse` | **VERIFICADO** | Mutación puntual de lógica de producción |
| P22 | `testP22_flushReadingProgress_inactiveTts_ignoresTtsLocatorAndFallsBackToNav` | **VERIFICADO** | Mutación puntual de lógica de producción |

---

## 2. Detalle de Mutaciones y Fallos Literales (P1–P22)

### P1: `testP1_onLocatorChanged_pdfOriginal_calculatesGlobalProgressWithPageFraction`
**Descripción:** L814: cambiar cálculo de _globalProgress.value por 0.99f en PDF original

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..5a840d0 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -811,7 +811,7 @@ class ReaderViewModel @Inject constructor(
             val page = locator.locations.position ?: 1
             progressTracker.lastKnownReflowPage = page
             val totalPages = progressTracker.totalPdfPages(totalPositions, pdfReflowManager.reflowState.value.totalPages, publication?.readingOrder?.size)
-            _globalProgress.value = progressTracker.pageFraction(page, totalPages)
+            _globalProgress.value = 0.99f // MUTATION P1
 
             saveReadingProgressUseCase.saveProgressDebounced(
                 scope = viewModelScope,
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP1_onLocatorChanged_pdfOriginal_calculatesGlobalProgressWithPageFraction`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: globalProgress debe calcularse como 5/20 = 0.25 expected:<0.25> but was:<0.99>
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at org.junit.Assert.assertEquals(Assert.java:577)
```

### P2: `testP2_onLocatorChanged_pdfOriginal_persistsLastKnownReflowPage`
**Descripción:** L807 & L812: mutante equivalente redundante - comentar ambas asignaciones consecutivas de lastKnownReflowPage

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..5ab5f6a 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -804,12 +804,12 @@ class ReaderViewModel @Inject constructor(
     fun onLocatorChanged(locator: Locator) {
         annotations.currentLocator = locator
         if (isPdfBook && !isReflowMode.value) {
-            progressTracker.lastKnownReflowPage = locator.locations.position ?: 1
+            // progressTracker.lastKnownReflowPage = locator.locations.position ?: 1 // MUTATION P2
         }
         refreshCurrentBookmarkState()
         if (isPdfBook && !isReflowMode.value) {
             val page = locator.locations.position ?: 1
-            progressTracker.lastKnownReflowPage = page
+            // progressTracker.lastKnownReflowPage = page // MUTATION P2
             val totalPages = progressTracker.totalPdfPages(totalPositions, pdfReflowManager.reflowState.value.totalPages, publication?.readingOrder?.size)
             _globalProgress.value = progressTracker.pageFraction(page, totalPages)
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP2_onLocatorChanged_pdfOriginal_persistsLastKnownReflowPage`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: lastKnownReflowPage debe ser 7 expected:<7> but was:<1>
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at org.junit.Assert.assertEquals(Assert.java:120)
```

### P3: `testP3_onLocatorChanged_pdfOriginal_nullPosition_fallbackToOne`
**Descripción:** L811: cambiar fallback de posición en segunda asignación ?: 1 a ?: 99

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..8cb763e 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -808,7 +808,7 @@ class ReaderViewModel @Inject constructor(
         }
         refreshCurrentBookmarkState()
         if (isPdfBook && !isReflowMode.value) {
-            val page = locator.locations.position ?: 1
+            val page = locator.locations.position ?: 99 // MUTATION P3
             progressTracker.lastKnownReflowPage = page
             val totalPages = progressTracker.totalPdfPages(totalPositions, pdfReflowManager.reflowState.value.totalPages, publication?.readingOrder?.size)
             _globalProgress.value = progressTracker.pageFraction(page, totalPages)
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP3_onLocatorChanged_pdfOriginal_nullPosition_fallbackToOne`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: Fallback position debe ser 1 expected:<1> but was:<99>
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at org.junit.Assert.assertEquals(Assert.java:120)
```

### P4: `testP4_onLocatorChanged_pdfReflow_doesNotSaveOrUpdateProgress`
**Descripción:** L810: relajar segunda guarda if (isPdfBook && !isReflowMode.value) a if (isPdfBook) para forzar guardado en reflow

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..578f822 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -807,7 +807,7 @@ class ReaderViewModel @Inject constructor(
             progressTracker.lastKnownReflowPage = locator.locations.position ?: 1
         }
         refreshCurrentBookmarkState()
-        if (isPdfBook && !isReflowMode.value) {
+        if (isPdfBook) { // MUTATION P4
             val page = locator.locations.position ?: 1
             progressTracker.lastKnownReflowPage = page
             val totalPages = progressTracker.totalPdfPages(totalPositions, pdfReflowManager.reflowState.value.totalPages, publication?.readingOrder?.size)
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP4_onLocatorChanged_pdfReflow_doesNotSaveOrUpdateProgress`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: Verification failed: call 1 of 1: com.example.woodsreader.domain.reader.SaveReadingProgressUseCase@491cc0eb.saveProgressDebounced(any(), any(), any(), any(), any(), any(), any())) should not be called

Calls:
1) com.example.woodsreader.domain.reader.SaveReadingProgressUseCase@491cc0eb.saveProgressDebounced(androidx.lifecycle.viewmodel.internal.CloseableCoroutineScope@1df8c7f4, test_book_id, 0.25, Locator(#68), 5, 800, false)


Stack traces:
1)                                                                                                                io.mockk.impl.InternalPlatform.captureStackTrace                                             (InternalPlatform.kt:130)                           
                                                                                                                    io.mockk.impl.stub.MockKStub.handleInvocation                                              (MockKStub.kt:255)                                  
                                                                                  io.mockk.impl.instantiation.JvmMockFactoryHelper$mockHandler$1.invocation                                                    (JvmMockFactoryHelper.kt:24)                        
                                                                                                           io.mockk.proxy.jvm.advice.Interceptor.call                                                          (Interceptor.kt:21)                                 
                                                                                com.example.woodsreader.domain.reader.SaveReadingProgressUseCase.saveProgressDebounced                                         (SaveReadingProgressUseCase.kt:86)                  
                                                                                com.example.woodsreader.domain.reader.SaveReadingProgressUseCase.saveProgressDebounced$default                                 (SaveReadingProgressUseCase.kt:54)                  
                                                                                               com.example.woodsreader.ui.reader.ReaderViewModel.onLocatorChanged                                              (ReaderViewModel.kt:816)                            
   com.example.woodsreader.ui.reader.ReaderViewModelProgressCharacterizationTest$testP4_onLocatorChanged_pdfReflow_doesNotSaveOrUpdateProgress$1.invokeSuspend                                                 (ReaderViewModelProgressCharacterizationTest.kt:154)
... [truncadas 76 líneas de volcado de llamadas mockk]
```

### P5: `testP5_onLocatorChanged_epub_duplicateJson_ignored`
**Descripción:** L828: forzar condición if (locJson != progressTracker.lastSavedLocatorJson) a if (true)

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..3abf5e2 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -825,7 +825,7 @@ class ReaderViewModel @Inject constructor(
 
         if (!isPdfBook) {
             val locJson = saveReadingProgressUseCase.serializeLocator(locator)
-            if (locJson != progressTracker.lastSavedLocatorJson) {
+            if (true) { // MUTATION P5
                 progressTracker.lastSavedLocatorJson = locJson
                 saveReadingProgressUseCase.setLastSavedLocatorJson(locJson)
                 saveProgress(locator)
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP5_onLocatorChanged_epub_duplicateJson_ignored`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: Verification failed: call 1 of 1: com.example.woodsreader.domain.reader.SaveReadingProgressUseCase@491cc0eb.setLastSavedLocatorJson(eq({"href":"c1.html"}))). 2 matching calls found, but needs at least 1 and at most 1 calls
Calls:
1) com.example.woodsreader.domain.reader.SaveReadingProgressUseCase@491cc0eb.serializeLocator(Locator(#74))
2) com.example.woodsreader.domain.reader.SaveReadingProgressUseCase@491cc0eb.setLastSavedLocatorJson({"href":"c1.html"})
3) com.example.woodsreader.domain.reader.SaveReadingProgressUseCase@491cc0eb.saveProgressDebounced(androidx.lifecycle.viewmodel.internal.CloseableCoroutineScope@cc4a0dd, test_book_id, 0.1, Locator(#74), null, 800, false)
4) com.example.woodsreader.domain.reader.SaveReadingProgressUseCase@491cc0eb.serializeLocator(Locator(#74))
5) com.example.woodsreader.domain.reader.SaveReadingProgressUseCase@491cc0eb.setLastSavedLocatorJson({"href":"c1.html"})
6) com.example.woodsreader.domain.reader.SaveReadingProgressUseCase@491cc0eb.saveProgressDebounced(androidx.lifecycle.viewmodel.internal.CloseableCoroutineScope@cc4a0dd, test_book_id, 0.1, Locator(#74), null, 800, false)


Stack traces:
1)                                                                                                     io.mockk.impl.InternalPlatform.captureStackTrace                                  (InternalPlatform.kt:130)                           
                                                                                                         io.mockk.impl.stub.MockKStub.handleInvocation                                   (MockKStub.kt:255)                                  
                                                                       io.mockk.impl.instantiation.JvmMockFactoryHelper$mockHandler$1.invocation                                         (JvmMockFactoryHelper.kt:24)                        
                                                                                                io.mockk.proxy.jvm.advice.Interceptor.call                                               (Interceptor.kt:21)                                 
... [truncadas 483 líneas de volcado de llamadas mockk]
```

### P6: `testP6_saveProgress_manualMode_incrementsWords`
**Descripción:** L846: comentar incremento de palabras en readingSessionManager.incrementWords(wordsRead)

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..0cd60ba 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -843,7 +843,7 @@ class ReaderViewModel @Inject constructor(
                 estimatedWordCount = totalWords
             )
             if (wordsRead > 0) {
-                readingSessionManager.incrementWords(wordsRead)
+                // readingSessionManager.incrementWords(wordsRead) // MUTATION P6
             }
             progressTracker.lastRecordedProgression = currentProg
         }
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP6_saveProgress_manualMode_incrementsWords`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: Verification failed: call 1 of 1: com.example.woodsreader.core.managers.ReadingSessionManager@632b5c79.incrementWords(eq(42))) was not called.

Calls to same mock:
1) com.example.woodsreader.core.managers.ReadingSessionManager@632b5c79.getCurrentSessionMode()
2) com.example.woodsreader.core.managers.ReadingSessionManager@632b5c79.switchSessionMode(androidx.lifecycle.viewmodel.internal.CloseableCoroutineScope@2c10f9f3, test_book_id, MANUAL, null)
3) com.example.woodsreader.core.managers.ReadingSessionManager@632b5c79.startNewSession(androidx.lifecycle.viewmodel.internal.CloseableCoroutineScope@2c10f9f3, test_book_id, MANUAL, Locator(#54))
4) com.example.woodsreader.core.managers.ReadingSessionManager@632b5c79.getCurrentSessionMode()
5) com.example.woodsreader.core.managers.ReadingSessionManager@632b5c79.getPagesRead()
6) com.example.woodsreader.core.managers.ReadingSessionManager@632b5c79.addPageRead(c1.html#2)
7) com.example.woodsreader.core.managers.ReadingSessionManager@632b5c79.getSessionStartTime()
8) com.example.woodsreader.core.managers.ReadingSessionManager@632b5c79.getWordsProcessed()
9) com.example.woodsreader.core.managers.ReadingSessionManager@632b5c79.recordActivity(androidx.lifecycle.viewmodel.internal.CloseableCoroutineScope@2c10f9f3, test_book_id, null)


Stack traces:
... [truncadas 786 líneas de volcado de llamadas mockk]
```

### P7: `testP7_saveProgress_ttsMode_doesNotIncrementWordsNorAdvanceProgression`
**Descripción:** L837: forzar if (readingSessionManager.currentSessionMode == ReadingMode.MANUAL) a if (true) en saveProgress

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..7e225b8 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -834,7 +834,7 @@ class ReaderViewModel @Inject constructor(
     }
 
     private fun saveProgress(locator: Locator) {
-        if (readingSessionManager.currentSessionMode == ReadingMode.MANUAL) {
+        if (true) { // MUTATION P7
             val currentProg = locator.locations.totalProgression ?: 0.0
             val totalWords = (uiState.value as? ReaderUiState.Success)?.book?.estimatedWordCount ?: 100_000L
             val wordsRead = calculateReadingStatsUseCase.calculateWordsDelta(
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP7_saveProgress_ttsMode_doesNotIncrementWordsNorAdvanceProgression`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: Verification failed: call 1 of 1: com.example.woodsreader.core.managers.ReadingSessionManager@632b5c79.incrementWords(any())) should not be called

Calls:
1) com.example.woodsreader.core.managers.ReadingSessionManager@632b5c79.incrementWords(50)


Stack traces:
1)                                                                                                                         io.mockk.impl.InternalPlatform.captureStackTrace                                                      (InternalPlatform.kt:130)                           
                                                                                                                             io.mockk.impl.stub.MockKStub.handleInvocation                                                       (MockKStub.kt:255)                                  
                                                                                           io.mockk.impl.instantiation.JvmMockFactoryHelper$mockHandler$1.invocation                                                             (JvmMockFactoryHelper.kt:24)                        
                                                                                                                    io.mockk.proxy.jvm.advice.Interceptor.call                                                                   (Interceptor.kt:21)                                 
                                                                                              com.example.woodsreader.core.managers.ReadingSessionManager.incrementWords                                                         (ReadingSessionManager.kt:74)                       
                                                                                                        com.example.woodsreader.ui.reader.ReaderViewModel.saveProgress                                                           (ReaderViewModel.kt:846)                            
                                                                                                        com.example.woodsreader.ui.reader.ReaderViewModel.onLocatorChanged                                                       (ReaderViewModel.kt:831)                            
   com.example.woodsreader.ui.reader.ReaderViewModelProgressCharacterizationTest$testP7_saveProgress_ttsMode_doesNotIncrementWordsNorAdvanceProgression$1.invokeSuspend                                                          (ReaderViewModelProgressCharacterizationTest.kt:209)
... [truncadas 76 líneas de volcado de llamadas mockk]
```

### P8: `testP8_saveProgress_duplicatePageKey_doesNotAddPageRead`
**Descripción:** L853: forzar isNewPage a true ignorando pagesRead.contains(pageKey)

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..f682a9c 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -850,7 +850,7 @@ class ReaderViewModel @Inject constructor(
 
         // Track unique pages read in this session
         val pageKey = "${locator.href}#${locator.locations.position ?: 0}"
-        val isNewPage = !readingSessionManager.pagesRead.value.contains(pageKey)
+        val isNewPage = true // MUTATION P8
         if (isNewPage) {
             readingSessionManager.addPageRead(pageKey)
             viewModelScope.launch {
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP8_saveProgress_duplicatePageKey_doesNotAddPageRead`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: Verification failed: call 1 of 1: com.example.woodsreader.core.managers.ReadingSessionManager@632b5c79.addPageRead(eq(c1.html#2))) should not be called

Calls:
1) com.example.woodsreader.core.managers.ReadingSessionManager@632b5c79.addPageRead(c1.html#2)


Stack traces:
1)                                                                                                          io.mockk.impl.InternalPlatform.captureStackTrace                                       (InternalPlatform.kt:130)                           
                                                                                                              io.mockk.impl.stub.MockKStub.handleInvocation                                        (MockKStub.kt:255)                                  
                                                                            io.mockk.impl.instantiation.JvmMockFactoryHelper$mockHandler$1.invocation                                              (JvmMockFactoryHelper.kt:24)                        
                                                                                                     io.mockk.proxy.jvm.advice.Interceptor.call                                                    (Interceptor.kt:21)                                 
                                                                               com.example.woodsreader.core.managers.ReadingSessionManager.addPageRead                                             (ReadingSessionManager.kt:80)                       
                                                                                         com.example.woodsreader.ui.reader.ReaderViewModel.saveProgress                                            (ReaderViewModel.kt:855)                            
                                                                                         com.example.woodsreader.ui.reader.ReaderViewModel.onLocatorChanged                                        (ReaderViewModel.kt:831)                            
   com.example.woodsreader.ui.reader.ReaderViewModelProgressCharacterizationTest$testP8_saveProgress_duplicatePageKey_doesNotAddPageRead$1.invokeSuspend                                           (ReaderViewModelProgressCharacterizationTest.kt:224)
... [truncadas 76 líneas de volcado de llamadas mockk]
```

### P9: `testP9_saveProgress_epub_chapterChange_triggersPrefetchAndCache`
**Descripción:** L866: forzar if (lastProcessedChapterHref != href) a if (false) impidiendo procesamiento de capítulo

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..5fd472f 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -863,7 +863,7 @@ class ReaderViewModel @Inject constructor(
 
         if (!isPdfBook) {
             val href = locator.href.toString()
-            if (lastProcessedChapterHref != href) {
+            if (false) { // MUTATION P9
                 lastProcessedChapterHref = href
                 val pub = publication
                 val currentIdx = if (pub != null) {
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP9_saveProgress_epub_chapterChange_triggersPrefetchAndCache`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: Verification failed: call 1 of 1: com.example.woodsreader.ui.reader.EpubFormatManager@5b23c1f6.processResource(eq(c2.html), null(), eq([]), any())) was not called.

Calls to same mock:
1) com.example.woodsreader.ui.reader.EpubFormatManager@5b23c1f6.getTtsState()
2) com.example.woodsreader.ui.reader.EpubFormatManager@5b23c1f6.syncPreferences(1.0, null, 1.0)
3) com.example.woodsreader.ui.reader.EpubFormatManager@5b23c1f6.initialize(android.app.Application@e111c7c, lambda {}, lambda {}, lambda {})
4) com.example.woodsreader.ui.reader.EpubFormatManager@5b23c1f6.stopTts()
5) com.example.woodsreader.ui.reader.EpubFormatManager@5b23c1f6.initNavigator(org.readium.r2.shared.publication.Publication@7e24a2e2, Locator(#54), androidx.lifecycle.viewmodel.internal.CloseableCoroutineScope@324afa73, null, test_book_id, continuation {})


Stack traces:
1)                                                                                                                  io.mockk.impl.InternalPlatform.captureStackTrace                                               (InternalPlatform.kt:130)                           
                                                                                                                      io.mockk.impl.stub.MockKStub.handleInvocation                                                (MockKStub.kt:255)                                  
                                                                                    io.mockk.impl.instantiation.JvmMockFactoryHelper$mockHandler$1.invocation                                                      (JvmMockFactoryHelper.kt:24)                        
                                                                                                             io.mockk.proxy.jvm.advice.Interceptor.call                                                            (Interceptor.kt:21)                                 
... [truncadas 457 líneas de volcado de llamadas mockk]
```

### P10: `testP10_saveProgress_prefersGlobalProgressOverLocatorProgression`
**Descripción:** L902: forzar totalProg = locProg ignorando calculatedProg (_globalProgress)

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..dc3182d 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -899,7 +899,7 @@ class ReaderViewModel @Inject constructor(
 
         val calculatedProg = _globalProgress.value
         val locProg = (locator.locations.totalProgression ?: 0.0).toFloat()
-        val totalProg = if (calculatedProg > 0f) calculatedProg else locProg
+        val totalProg = locProg // MUTATION P10
 
         saveReadingProgressUseCase.saveProgressDebounced(
             scope = viewModelScope,
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP10_saveProgress_prefersGlobalProgressOverLocatorProgression`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: Verification failed: call 1 of 1: com.example.woodsreader.domain.reader.SaveReadingProgressUseCase@71eafb64.saveProgressDebounced(any(), any(), eq(0.85), eq(Locator(#75)), any(), any(), any())). Only one matching call to SaveReadingProgressUseCase(#17)/saveProgressDebounced(CoroutineScope, String, Float, Locator, Int, Long, Boolean) happened, but arguments are not matching:
[0]: argument: androidx.lifecycle.viewmodel.internal.CloseableCoroutineScope@318155b1, matcher: any(), result: +
[1]: argument: test_book_id, matcher: any(), result: +
[2]: argument: 0.3, matcher: eq(0.85), result: -
[3]: argument: Locator(#75), matcher: eq(Locator(#75)), result: +
[4]: argument: null, matcher: any(), result: +
[5]: argument: 800, matcher: any(), result: +
[6]: argument: false, matcher: any(), result: +

Stack trace:
                                                                                                                  io.mockk.impl.InternalPlatform.captureStackTrace                                                (InternalPlatform.kt:130)                           
                                                                                                                    io.mockk.impl.stub.MockKStub.handleInvocation                                                 (MockKStub.kt:255)                                  
                                                                                  io.mockk.impl.instantiation.JvmMockFactoryHelper$mockHandler$1.invocation                                                       (JvmMockFactoryHelper.kt:24)                        
                                                                                                           io.mockk.proxy.jvm.advice.Interceptor.call                                                             (Interceptor.kt:21)                                 
                                                                                com.example.woodsreader.domain.reader.SaveReadingProgressUseCase.serializeLocator                                                 (SaveReadingProgressUseCase.kt:47)                  
... [truncadas 78 líneas de volcado de llamadas mockk]
```

### P11: `testP11_onTtsLocatorChanged_coverPageActive_returnsEarly`
**Descripción:** L789: cambiar if (_showEpubCoverPage.value) return por if (false) return

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..4729913 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -786,7 +786,7 @@ class ReaderViewModel @Inject constructor(
     }
 
     private fun onTtsLocatorChanged(locator: Locator) {
-        if (_showEpubCoverPage.value) return
+        if (false) return // MUTATION P11
         annotations.currentLocator = locator
         val locJson = saveReadingProgressUseCase.serializeLocator(locator)
         progressTracker.lastSavedLocatorJson = locJson
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP11_onTtsLocatorChanged_coverPageActive_returnsEarly`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: Verification failed: call 1 of 1: com.example.woodsreader.domain.reader.SaveReadingProgressUseCase@491cc0eb.saveTtsProgressDebounced(any(), any(), any(), any())) should not be called

Calls:
1) com.example.woodsreader.domain.reader.SaveReadingProgressUseCase@491cc0eb.saveTtsProgressDebounced(androidx.lifecycle.viewmodel.internal.CloseableCoroutineScope@36b86248, test_book_id, Locator(#74), 10000)


Stack traces:
1)                                                                                                           io.mockk.impl.InternalPlatform.captureStackTrace                                        (InternalPlatform.kt:130)                           
                                                                                                               io.mockk.impl.stub.MockKStub.handleInvocation                                         (MockKStub.kt:255)                                  
                                                                             io.mockk.impl.instantiation.JvmMockFactoryHelper$mockHandler$1.invocation                                               (JvmMockFactoryHelper.kt:24)                        
                                                                                                      io.mockk.proxy.jvm.advice.Interceptor.call                                                     (Interceptor.kt:21)                                 
                                                                           com.example.woodsreader.domain.reader.SaveReadingProgressUseCase.saveTtsProgressDebounced                                 (SaveReadingProgressUseCase.kt:120)                 
                                                                           com.example.woodsreader.domain.reader.SaveReadingProgressUseCase.saveTtsProgressDebounced$default                         (SaveReadingProgressUseCase.kt:92)                  
                                                                                          com.example.woodsreader.ui.reader.ReaderViewModel.onTtsLocatorChanged                                      (ReaderViewModel.kt:797)                            
                                                                                            jdk.internal.reflect.DirectMethodHandleAccessor.invoke                                                   (-:-1)                                              
... [truncadas 80 líneas de volcado de llamadas mockk]
```

### P12: `testP12_onTtsLocatorChanged_updatesLastSavedLocatorAndSavesTtsProgress`
**Descripción:** L792: comentar asignación progressTracker.lastSavedLocatorJson = locJson en TTS

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..40122e2 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -789,7 +789,7 @@ class ReaderViewModel @Inject constructor(
         if (_showEpubCoverPage.value) return
         annotations.currentLocator = locator
         val locJson = saveReadingProgressUseCase.serializeLocator(locator)
-        progressTracker.lastSavedLocatorJson = locJson
+        // progressTracker.lastSavedLocatorJson = locJson // MUTATION P12
         saveReadingProgressUseCase.setLastSavedLocatorJson(locJson)
         updateChapterMetadata(locator)
         updateReadingStats(locator, DEFAULT_READING_WPM)
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP12_onTtsLocatorChanged_updatesLastSavedLocatorAndSavesTtsProgress`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: expected:<{"tts":"loc12"}> but was:<null>
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at org.junit.Assert.assertEquals(Assert.java:120)
```

### P13: `testP13_flushReadingProgress_activeTts_takesTtsLocator`
**Descripción:** L1547: forzar condición de TTS activo a false en flushReadingProgressImmediately

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..ccf240a 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -1544,7 +1544,7 @@ class ReaderViewModel @Inject constructor(
     fun flushReadingProgressImmediately() {
         val nav = navigator as? VisualNavigator
         val ttsLocator = _currentTtsLocator.value.takeIf {
-            ttsState.value == TtsState.PLAYING || ttsState.value == TtsState.PAUSED
+            false // MUTATION P13
         }
         val currentLoc = ttsLocator ?: nav?.currentLocator?.value ?: annotations.currentLocator
             ?: progressTracker.lastSavedLocatorJson?.let { saved ->
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP13_flushReadingProgress_activeTts_takesTtsLocator`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: expected:<Locator(#74)> but was:<Locator(#81)>
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at org.junit.Assert.assertEquals(Assert.java:120)
```

### P14: `testP14_flushReadingProgress_nullNav_fallsBackToAnnotationsLocator`
**Descripción:** L1549: omitir fallback ?: annotations.currentLocator en resolución de currentLoc

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..fcc0b9d 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -1546,7 +1546,7 @@ class ReaderViewModel @Inject constructor(
         val ttsLocator = _currentTtsLocator.value.takeIf {
             ttsState.value == TtsState.PLAYING || ttsState.value == TtsState.PAUSED
         }
-        val currentLoc = ttsLocator ?: nav?.currentLocator?.value ?: annotations.currentLocator
+        val currentLoc = ttsLocator ?: nav?.currentLocator?.value // MUTATION P14
             ?: progressTracker.lastSavedLocatorJson?.let { saved ->
                 runCatching { Locator.fromJSON(org.json.JSONObject(saved)) }.getOrNull()
             }
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP14_flushReadingProgress_nullNav_fallsBackToAnnotationsLocator`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: expected:<Locator(#74)> but was:<null>
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at org.junit.Assert.assertEquals(Assert.java:120)
```

### P15: `testP15_flushReadingProgress_deserializesLastSavedLocatorJson`
**Descripción:** L1550: omitir fallback deserializador ?: progressTracker.lastSavedLocatorJson?.let { ... }

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..66e45c7 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -1547,9 +1547,7 @@ class ReaderViewModel @Inject constructor(
             ttsState.value == TtsState.PLAYING || ttsState.value == TtsState.PAUSED
         }
         val currentLoc = ttsLocator ?: nav?.currentLocator?.value ?: annotations.currentLocator
-            ?: progressTracker.lastSavedLocatorJson?.let { saved ->
-                runCatching { Locator.fromJSON(org.json.JSONObject(saved)) }.getOrNull()
-            }
+            // MUTATION P15: omit deserialization fallback
         val chapterIdx = currentLoc?.let { locator ->
             val order = publication?.readingOrder.orEmpty()
             findMatchingResourceIndex(locator.href.toString(), order).takeIf { it >= 0 }
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP15_flushReadingProgress_deserializesLastSavedLocatorJson`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: currentLoc no debe ser nulo cuando lastSavedLocatorJson es deserializable
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.assertTrue(Assert.java:42)
	at org.junit.Assert.assertNotNull(Assert.java:713)
```

### P16: `testP16_flushReadingProgress_pdfOriginal_remembersPageInTracker`
**Descripción:** L1571: comentar actualización plan.pageToRemember?.let { progressTracker.lastKnownReflowPage = it }

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..ad4f966 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -1568,7 +1568,7 @@ class ReaderViewModel @Inject constructor(
             readingOrderSize = if (isPdfBook) publication?.readingOrder?.size else null,
             globalProgress = _globalProgress.value
         )
-        plan.pageToRemember?.let { progressTracker.lastKnownReflowPage = it }
+        // plan.pageToRemember?.let { progressTracker.lastKnownReflowPage = it } // MUTATION P16
         if (plan.saveProgressFirst) saveProgress(currentLoc!!)
         saveReadingProgressUseCase.flushImmediately(
             bookId = bookId, chapterIndex = chapterIdx, progress = plan.progress, currentLocator = currentLoc,
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP16_flushReadingProgress_pdfOriginal_remembersPageInTracker`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: lastKnownReflowPage debe actualizarse a 9 en PDF original flush expected:<9> but was:<1>
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at org.junit.Assert.assertEquals(Assert.java:120)
```

### P17: `testP17_flushReadingProgress_epub_executesSaveProgressBeforeFlush`
**Descripción:** L1572: comentar if (plan.saveProgressFirst) saveProgress(currentLoc!!) antes de flushImmediately

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..1a5a8b7 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -1569,7 +1569,7 @@ class ReaderViewModel @Inject constructor(
             globalProgress = _globalProgress.value
         )
         plan.pageToRemember?.let { progressTracker.lastKnownReflowPage = it }
-        if (plan.saveProgressFirst) saveProgress(currentLoc!!)
+        // if (plan.saveProgressFirst) saveProgress(currentLoc!!) // MUTATION P17
         saveReadingProgressUseCase.flushImmediately(
             bookId = bookId, chapterIndex = chapterIdx, progress = plan.progress, currentLocator = currentLoc,
             fallbackLocatorJson = locJson, fallbackHref = href, fallbackPosition = plan.fallbackPosition
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP17_flushReadingProgress_epub_executesSaveProgressBeforeFlush`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: Verification failed: fewer calls happened than demanded by order verification sequence. 

Matchers: 
com.example.woodsreader.domain.reader.SaveReadingProgressUseCase@491cc0eb.saveProgressDebounced(any(), any(), any(), eq(Locator(#75)), any(), any(), any()))
+com.example.woodsreader.domain.reader.SaveReadingProgressUseCase@491cc0eb.flushImmediately(any(), any(), any(), any(), eq(Locator(#75)), any(), any(), any()))

Calls:
1) +com.example.woodsreader.domain.reader.SaveReadingProgressUseCase@491cc0eb.flushImmediately(CoroutineScope(coroutineContext=[JobImpl{Active}@119f02d2, Dispatchers.IO]), test_book_id, 0, 0.25, Locator(#75), , 3, chap1.xhtml)

Stack traces:
1)                                                                                                                    io.mockk.impl.InternalPlatform.captureStackTrace                                                 (InternalPlatform.kt:130)                           
                                                                                                                        io.mockk.impl.stub.MockKStub.handleInvocation                                                  (MockKStub.kt:255)                                  
                                                                                      io.mockk.impl.instantiation.JvmMockFactoryHelper$mockHandler$1.invocation                                                        (JvmMockFactoryHelper.kt:24)                        
                                                                                                               io.mockk.proxy.jvm.advice.Interceptor.call                                                              (Interceptor.kt:21)                                 
                                                                                    com.example.woodsreader.domain.reader.SaveReadingProgressUseCase.flushImmediately                                                  (SaveReadingProgressUseCase.kt:193)                 
... [truncadas 79 líneas de volcado de llamadas mockk]
```

### P18: `testP18_getCurrentChapterIndex_resolvesFromReadingOrderWhenCacheMiss`
**Descripción:** L1595: comentar fallback a findMatchingResourceIndex en getCurrentChapterIndex

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..48b83b3 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -1592,7 +1592,7 @@ class ReaderViewModel @Inject constructor(
             ?: _currentTtsLocator.value?.href?.toString().orEmpty()
         return if (currentHref.isNotBlank()) {
             resourceIndexCache?.get(currentHref)
-                ?: findMatchingResourceIndex(currentHref, pub.readingOrder).takeIf { it != -1 }
+                // ?: findMatchingResourceIndex(currentHref, pub.readingOrder).takeIf { it != -1 } // MUTATION P18
                 ?: 0
         } else 0
     }
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP18_getCurrentChapterIndex_resolvesFromReadingOrderWhenCacheMiss`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: Debe resolver índice 2 por readingOrder expected:<2> but was:<0>
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at org.junit.Assert.assertEquals(Assert.java:120)
```

### P19: `testP19_getFallbackLocator_prefersLastTtsLocatorOverCurrent`
**Descripción:** L1107: anular val lastLoc = null en getFallbackLocator ignorando _lastTtsLocator y _currentTtsLocator

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..0fce3ff 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -1104,7 +1104,7 @@ class ReaderViewModel @Inject constructor(
         val navLocator = (navigator as? VisualNavigator)?.currentLocator?.value
         if (navLocator != null) return navLocator
 
-        val lastLoc = _lastTtsLocator.value ?: _currentTtsLocator.value
+        val lastLoc = null as Locator? // MUTATION P19
         if (lastLoc != null) return lastLoc
 
         val successState = _uiState.value as? ReaderUiState.Success
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP19_getFallbackLocator_prefersLastTtsLocatorOverCurrent`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: Debe preferir _lastTtsLocator a _currentTtsLocator expected:<Locator(#74)> but was:<Locator(child of #51#86)>
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at org.junit.Assert.assertEquals(Assert.java:120)
```

### P20: `testP20_onLocatorChanged_pdfOriginal_duplicateInvocation_triggersTwice`
**Descripción:** L816: comentar saveReadingProgressUseCase.saveProgressDebounced en PDF original onLocatorChanged

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..4c3ae5d 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -813,13 +813,13 @@ class ReaderViewModel @Inject constructor(
             val totalPages = progressTracker.totalPdfPages(totalPositions, pdfReflowManager.reflowState.value.totalPages, publication?.readingOrder?.size)
             _globalProgress.value = progressTracker.pageFraction(page, totalPages)
 
-            saveReadingProgressUseCase.saveProgressDebounced(
-                scope = viewModelScope,
-                bookId = bookId,
-                progress = _globalProgress.value,
-                locator = locator,
-                fallbackPosition = page
-            )
+            // saveReadingProgressUseCase.saveProgressDebounced( // MUTATION P20
+            //     scope = viewModelScope,
+            //     bookId = bookId,
+            //     progress = _globalProgress.value,
+            //     locator = locator,
+            //     fallbackPosition = page
+            // )
             return
         }
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP20_onLocatorChanged_pdfOriginal_duplicateInvocation_triggersTwice`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: Verification failed: call 1 of 1: com.example.woodsreader.domain.reader.SaveReadingProgressUseCase@71eafb64.saveProgressDebounced(any(), eq(test_book_id), eq(0.25), eq(Locator(#68)), eq(5), any(), any())) was not called
	at io.mockk.impl.recording.states.VerifyingState.failIfNotPassed(VerifyingState.kt:63)
	at io.mockk.impl.recording.states.VerifyingState.recordingDone(VerifyingState.kt:42)
	at io.mockk.impl.recording.CommonCallRecorder.done(CommonCallRecorder.kt:47)
```

### P21: `testP21_getFallbackLocator_invalidJsonFallsBackWithoutThrowing_blankJsonSkipsParse`
**Descripción:** L1113: eliminar bloque try/catch en parseo de initialLocatorJson en getFallbackLocator

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..2ac2bfc 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -1110,12 +1110,8 @@ class ReaderViewModel @Inject constructor(
         val successState = _uiState.value as? ReaderUiState.Success
         val initialJson = successState?.initialLocatorJson
         if (!initialJson.isNullOrBlank()) {
-            try {
-                val parsed = Locator.fromJSON(org.json.JSONObject(initialJson))
-                if (parsed != null) return parsed
-            } catch (e: Exception) {
-                Log.w(TAG, "Error parsing initialLocatorJson", e)
-            }
+            val parsed = Locator.fromJSON(org.json.JSONObject(initialJson)) // MUTATION P21: no try/catch
+            if (parsed != null) return parsed
         }
 
         val pub = publication ?: successState?.publication
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP21_getFallbackLocator_invalidJsonFallsBackWithoutThrowing_blankJsonSkipsParse`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.reflect.InvocationTargetException
	at java.base/jdk.internal.reflect.DirectMethodHandleAccessor.invoke(Unknown Source)
	at java.base/java.lang.reflect.Method.invoke(Unknown Source)
	at com.example.woodsreader.ui.reader.ReaderViewModelProgressCharacterizationTest.invokeGetFallbackLocator(ReaderViewModelProgressCharacterizationTest.kt:109)
```

### P22: `testP22_flushReadingProgress_inactiveTts_ignoresTtsLocatorAndFallsBackToNav`
**Descripción:** L1546: omitir verificación takeIf { ttsState.value == PLAYING || PAUSED } en ttsLocator

**(a) Hunk exacto de `git diff` aplicado en producción:**
```diff
diff --git a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
index 3a72d46..c59d3fd 100644
--- a/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
+++ b/app/src/main/java/com/example/woodsreader/ui/reader/ReaderViewModel.kt
@@ -1543,9 +1543,7 @@ class ReaderViewModel @Inject constructor(
      */
     fun flushReadingProgressImmediately() {
         val nav = navigator as? VisualNavigator
-        val ttsLocator = _currentTtsLocator.value.takeIf {
-            ttsState.value == TtsState.PLAYING || ttsState.value == TtsState.PAUSED
-        }
+        val ttsLocator = _currentTtsLocator.value // MUTATION P22
         val currentLoc = ttsLocator ?: nav?.currentLocator?.value ?: annotations.currentLocator
             ?: progressTracker.lastSavedLocatorJson?.let { saved ->
                 runCatching { Locator.fromJSON(org.json.JSONObject(saved)) }.getOrNull()
```

**(b) Test que falló y mensaje de aserción literal (XML):**
- **Test:** `testP22_flushReadingProgress_inactiveTts_ignoresTtsLocatorAndFallsBackToNav`
- **Estado:** VERIFIED
- **Resultado XML:**
```text
java.lang.AssertionError: Con TTS STOPPED no debe usar locator TTS, debe caer a nav expected:<Locator(#81)> but was:<Locator(#74)>
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.failNotEquals(Assert.java:835)
	at org.junit.Assert.assertEquals(Assert.java:120)
```

---

## 3. Tests que NO fallaron ante una mutación real

**Ninguno.** Los 22 tests (P1 a P22) fallaron ante mutaciones reales sobre el código de producción:
- 22 de 22 tests verificados mediante fallo comprobado en Gradle y registrado en el XML de JUnit.
- 0 mutaciones sobrevivieron.
- P2 reportado explícitamente como mutante equivalente redundante (ambas asignaciones L807 y L812 comentadas juntas).

---

## 4. Estado del repositorio antes del commit A

- `git status`: limpio en archivos rastreados.
- `git diff --stat -- app/src/main`: completamente vacío (producción intacta).
---

## 5. Verificación de Cableado Post-Commit C (Código Extraído)

Tras aplicar la delegación en `ReaderProgressResolvers` y la división de `saveProgress` en `ReaderViewModel.kt`, se ejecutaron las 3 mutaciones de cableado solicitadas sobre el código ya refactorizado:

### (a) Comentar `if (plan.saveProgressFirst) saveProgress(currentLoc!!)` en `flushReadingProgressImmediately`
- **Mutación aplicada en Commit C:**
```diff
@@ -1586,3 +1586,3 @@
         plan.pageToRemember?.let { progressTracker.lastKnownReflowPage = it }
-        if (plan.saveProgressFirst) saveProgress(currentLoc!!)
+        // if (plan.saveProgressFirst) saveProgress(currentLoc!!) // WIRING MUTATION A
         saveReadingProgressUseCase.flushImmediately(
```
- **Test que falló:** `testP17_flushReadingProgress_epub_executesSaveProgressBeforeFlush`
- **Resultado XML literal:**
```text
java.lang.AssertionError: Verification failed: fewer calls happened than demanded by order verification sequence. 

Matchers: 
com.example.woodsreader.domain.reader.SaveReadingProgressUseCase.saveProgressDebounced(any(), any(), any(), eq(Locator(#75)), any(), any(), any()))
+com.example.woodsreader.domain.reader.SaveReadingProgressUseCase.flushImmediately(any(), any(), any(), any(), eq(Locator(#75)), any(), any(), any()))

Calls:
1) +com.example.woodsreader.domain.reader.SaveReadingProgressUseCase.flushImmediately(CoroutineScope(...), test_book_id, 0, 0.25, Locator(#75), , 3, chap1.xhtml)
```
- **Estado:** **VERIFICADO** (detecta la omisión de `saveProgress` antes de `flushImmediately`).

### (b) Cambiar el orden de `trackPageRead` y `trackWordsRead` dentro de `saveProgress`
- **Mutación aplicada en Commit C:**
```diff
@@ -896,4 +896,4 @@
     private fun saveProgress(locator: Locator) {
-        trackWordsRead(locator)
-        trackPageRead(locator)
+        trackPageRead(locator) // WIRING MUTATION B (swapped)
+        trackWordsRead(locator)
         warmChapterCacheIfNeeded(locator)
```
- **Resultado en la suite:** Ningún test existente ni de caracterización falló (código de salida Gradle: 0).
- **Estado:** **MUTANTE EQUIVALENTE COMPROBADO**.
- **Motivo técnico:** `trackWordsRead` opera exclusivamente sobre `readingSessionManager.incrementWords` y `progressTracker.lastRecordedProgression`. `trackPageRead` opera sobre `readingSessionManager.pagesRead` y `calculateReadingStatsUseCase.recordPageRead`. Ninguno de los dos métodos lee el estado modificado por el otro ni existe una aserción de orden (`verifyOrder`) entre ellos en la suite de pruebas. El orden original (`trackWordsRead` primero, `trackPageRead` después) fue preservado de forma estricta en el código final de producción.

### (c) Devolver siempre null en el lambda de `parseLocator` en `flushReadingProgressImmediately`
- **Mutación aplicada en Commit C:**
```diff
@@ -1563,3 +1563,3 @@
             lastSavedLocatorJson = progressTracker.lastSavedLocatorJson,
             parseLocator = { saved ->
-                runCatching { Locator.fromJSON(org.json.JSONObject(saved)) }.getOrNull()
+                null // WIRING MUTATION C: always null
             }
```
- **Test que falló:** `testP15_flushReadingProgress_deserializesLastSavedLocatorJson`
- **Resultado XML literal:**
```text
java.lang.AssertionError: currentLoc no debe ser nulo cuando lastSavedLocatorJson es deserializable
	at org.junit.Assert.fail(Assert.java:89)
	at org.junit.Assert.assertTrue(Assert.java:42)
	at org.junit.Assert.assertNotNull(Assert.java:713)
	at com.example.woodsreader.ui.reader.ReaderViewModelProgressCharacterizationTest$testP15_flushReadingProgress_deserializesLastSavedLocatorJson$1.invokeSuspend(ReaderViewModelProgressCharacterizationTest.kt:377)
```
- **Estado:** **VERIFICADO** (detecta que el lambda delegado es el que provee la deserialización al resolver).
