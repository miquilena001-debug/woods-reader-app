package com.example.woodsreader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.lang.reflect.Modifier

class ReaderViewModelApiSnapshotTest {

    @Test
    fun verifyApiSnapshot() {
        val vmClass = ReaderViewModel::class.java
        val companionClass = ReaderViewModel.Companion::class.java

        val lines = mutableListOf<String>()

        // Declared public methods on ReaderViewModel
        for (m in vmClass.declaredMethods) {
            if (Modifier.isPublic(m.modifiers) && !m.isSynthetic) {
                val params = m.parameterTypes.joinToString(",") { it.canonicalName ?: it.name }
                val returnType = m.returnType.canonicalName ?: m.returnType.name
                lines.add("${m.name}($params):$returnType")
            }
        }

        // Declared public methods on Companion (static helpers)
        for (m in companionClass.declaredMethods) {
            if (Modifier.isPublic(m.modifiers) && !m.isSynthetic && m.name != "getEntries") {
                val params = m.parameterTypes.joinToString(",") { it.canonicalName ?: it.name }
                val returnType = m.returnType.canonicalName ?: m.returnType.name
                lines.add("Companion.${m.name}($params):$returnType")
            }
        }

        val sortedApi = lines.sorted().joinToString("\n") + "\n"

        // P11 / P13 hooks assertions:
        val p11p13Hooks = listOf(
            "togglePdfBookView",
            "updateNarrationHighlight",
            "activeNarrationBlockId",
            "activeNarrationWordRange"
        )
        for (hook in p11p13Hooks) {
            val getter = "get" + hook.replaceFirstChar { it.uppercase() }
            assertTrue(
                "Expected hook $hook (or $getter) to be present in ReaderViewModel public API",
                lines.any { it.startsWith("$hook(") || it.startsWith("$getter(") }
            )
        }

        val goldenFile = sequenceOf(
            File("app/src/test/resources/api/ReaderViewModel.api.txt"),
            File("src/test/resources/api/ReaderViewModel.api.txt"),
            File("../app/src/test/resources/api/ReaderViewModel.api.txt")
        ).firstOrNull { it.parentFile?.exists() == true } ?: File("app/src/test/resources/api/ReaderViewModel.api.txt")

        val updateGolden = System.getenv("UPDATE_GOLDEN") == "1" ||
                System.getProperty("UPDATE_GOLDEN") == "1" ||
                !goldenFile.exists()

        if (updateGolden) {
            goldenFile.parentFile?.mkdirs()
            goldenFile.writeText(sortedApi, Charsets.UTF_8)
            println("Golden updated at ${goldenFile.absolutePath} with ${lines.size} methods.")
            return
        }

        assertTrue("Golden file does not exist at ${goldenFile.absolutePath}", goldenFile.exists())
        val goldenContent = goldenFile.readText(Charsets.UTF_8).replace("\r\n", "\n")
        assertEquals("Public API mismatch against golden!", goldenContent, sortedApi)
    }
}
