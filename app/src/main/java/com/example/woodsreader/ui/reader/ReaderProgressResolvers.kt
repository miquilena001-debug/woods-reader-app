package com.example.woodsreader.ui.reader

import org.readium.r2.shared.publication.Locator

/**
 * Resolutores puros y sin estado para la persistencia y recuperación de posición de lectura.
 *
 * Desacopla la lógica de resolución de prioridad y orden de fallback de los colaboradores
 * mutables de [ReaderViewModel]. Los efectos secundarios, despachos de corrutinas, logging
 * y parsing concreto de JSON permanecen en el ViewModel a través de lambdas perezosas (lazy).
 */
object ReaderProgressResolvers {

    /**
     * Resuelve el [Locator] que representa la posición visual o de narración actual
     * para el guardado inmediato en Room/SharedPreferences ([flushReadingProgressImmediately]).
     *
     * Prioridades:
     * 1. [ttsLocator] si [ttsIsActive] es true.
     * 2. [navLocator] (posición visual en el Navigator).
     * 3. [annotationsLocator] (último locator registrado en el controlador de anotaciones).
     * 4. [lastSavedLocatorJson] deserializado mediante [parseLocator] (solo si no es nulo).
     */
    fun resolveFlushCurrentLocator(
        ttsLocator: Locator?,
        ttsIsActive: Boolean,
        navLocator: Locator?,
        annotationsLocator: Locator?,
        lastSavedLocatorJson: String?,
        parseLocator: (String) -> Locator?
    ): Locator? {
        return (ttsLocator.takeIf { ttsIsActive })
            ?: navLocator
            ?: annotationsLocator
            ?: lastSavedLocatorJson?.let(parseLocator)
    }

    /**
     * Resuelve la posición inicial de reproducción o fallback cuando no hay un locator activo en pantalla.
     *
     * Prioridades:
     * 1. [navLocator] (posición visual actual si está disponible).
     * 2. [lastTtsLocator] o [currentTtsLocator] (última posición conocida de narración).
     * 3. [initialLocatorJson] si no es nulo ni en blanco, parseado mediante [parseInitial].
     * 4. [firstLinkLocator] (primer elemento del readingOrder de la publicación).
     *
     * Laziness: [parseInitial] y [firstLinkLocator] solo se evalúan si los pasos precedentes
     * devolvieron null.
     */
    fun resolveFallbackLocator(
        navLocator: Locator?,
        lastTtsLocator: Locator?,
        currentTtsLocator: Locator?,
        initialLocatorJson: String?,
        parseInitial: (String) -> Locator?,
        firstLinkLocator: () -> Locator?
    ): Locator? {
        return navLocator
            ?: (lastTtsLocator ?: currentTtsLocator)
            ?: (initialLocatorJson?.takeIf { it.isNotBlank() }?.let(parseInitial))
            ?: firstLinkLocator()
    }
}
