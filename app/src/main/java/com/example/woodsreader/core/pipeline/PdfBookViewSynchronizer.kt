package com.example.woodsreader.core.pipeline

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Sincronizador bidireccional entre la Vista Original del PDF (basada en páginas y lienzos fijos)
 * y la Vista Libro (texto fluido con tipografía personalizada).
 * Garantiza que el usuario jamás pierda el contexto ni la posición al alternar entre ambas vistas.
 */
@Singleton
class PdfBookViewSynchronizer @Inject constructor() {

    /**
     * Mapea la página actual de la Vista Original al índice del primer bloque en la Vista Libro.
     * Retorna 0 si la página es menor a 1 o no hay bloques en esa página.
     */
    fun findBlockIndexForPage(blocks: List<ReadingBlock>, targetPage: Int): Int {
        if (blocks.isEmpty() || targetPage < 1) return 0

        val index = blocks.indexOfFirst { it.pageIndex == targetPage }
        if (index != -1) return index

        // Si la página solicitada no tiene bloques (e.g. página en blanco o solo imagen filtrada),
        // busca el bloque de la página más cercana anterior o posterior
        val closestBefore = blocks.indexOfLast { it.pageIndex < targetPage }
        if (closestBefore != -1) return closestBefore

        val closestAfter = blocks.indexOfFirst { it.pageIndex > targetPage }
        if (closestAfter != -1) return closestAfter

        return 0
    }

    /**
     * Mapea el bloque actualmente visible en la Vista Libro a la página correspondiente en la Vista Original.
     */
    fun findPageForBlockIndex(blocks: List<ReadingBlock>, blockIndex: Int, totalPages: Int): Int {
        if (blocks.isEmpty()) return 1
        val safeIndex = blockIndex.coerceIn(0, blocks.size - 1)
        val blockPage = blocks[safeIndex].pageIndex
        return blockPage.coerceIn(1, maxOf(1, totalPages))
    }

    /**
     * Calcula el progreso porcentual relativo (0.0 a 1.0) dentro de la página actual en Vista Libro.
     */
    fun calculatePageInternalProgress(
        blocks: List<ReadingBlock>,
        currentBlockIndex: Int
    ): Float {
        if (blocks.isEmpty()) return 0f
        val safeIndex = currentBlockIndex.coerceIn(0, blocks.size - 1)
        val currentPage = blocks[safeIndex].pageIndex

        val pageBlocks = blocks.filter { it.pageIndex == currentPage }
        if (pageBlocks.size <= 1) return 1f

        val indexInPage = pageBlocks.indexOf(blocks[safeIndex])
        return if (indexInPage == -1) 0f else indexInPage.toFloat() / (pageBlocks.size - 1).toFloat()
    }
}
