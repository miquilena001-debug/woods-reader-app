package com.example.woodsreader.core.web2epub.novel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentExtractorCssRegressionTest {

    private val extractor = ContentExtractor()

    @Test
    fun `media queries and at-rules do not hide legitimate content`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    @media (max-width: 768px) {
                        .mobile-hidden { display: none; }
                        p.responsive-note { display: none; }
                    }
                    @media print {
                        .print-hidden { visibility: hidden; }
                    }
                    @supports (display: grid) {
                        .no-grid-fallback { display: none; }
                    }
                    .real-trap { display: none; }
                </style>
            </head>
            <body>
                <div id="chapter-content">
                    <h2>Capítulo 1: La Fortaleza del Norte</h2>
                    <p class="mobile-hidden">Este párrafo solo se oculta en móvil pero es texto legítimo de la novela.</p>
                    <p class="responsive-note">Nota de la traducción visible en escritorio.</p>
                    <p class="print-hidden">Párrafo no impreso pero parte de la lectura digital.</p>
                    <p class="no-grid-fallback">Párrafo de soporte técnico que no debe eliminarse.</p>
                    <p class="real-trap">Texto trampa pirata que debe ser eliminado.</p>
                    <p>La expedición continuó su camino a través de la tormenta de nieve sin detenerse.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val result = extractor.extract(html, "https://example.com/chapter-1")

        // El texto dentro de @media y @supports debe preservarse
        assertTrue(result.bodyXhtml.contains("Este párrafo solo se oculta en móvil pero es texto legítimo"))
        assertTrue(result.bodyXhtml.contains("Nota de la traducción visible en escritorio"))
        assertTrue(result.bodyXhtml.contains("Párrafo no impreso pero parte de la lectura digital"))
        assertTrue(result.bodyXhtml.contains("Párrafo de soporte técnico que no debe eliminarse"))
        assertTrue(result.bodyXhtml.contains("La expedición continuó su camino a través de la tormenta"))

        // La regla top-level sí debe actuar
        assertFalse(result.bodyXhtml.contains("Texto trampa pirata que debe ser eliminado"))
    }

    @Test
    fun `not pseudo-class and complex selectors are not mistakenly applied`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    p:not(.trap) { display: none; }
                    div > p.sub-child { display: none; }
                    .compound.active { display: none; }
                    p + p.adjacent { display: none; }
                    * { display: none; }
                    p { display: none; }
                    #real-trap-id { display: none; }
                </style>
            </head>
            <body>
                <div id="chapter-content">
                    <h2>Capítulo 2: Encuentro en la Niebla</h2>
                    <p class="legitimate">Párrafo que un selector :not() erróneo eliminaría.</p>
                    <p class="sub-child">Párrafo hijo con combinador complejo.</p>
                    <p class="compound">Párrafo con clase simple que no tiene .active.</p>
                    <p class="adjacent">Párrafo adyacente legítimo.</p>
                    <div id="real-trap-id">Texto trampa con id simple que sí debe eliminarse.</div>
                    <p>Los exploradores encendieron sus antorchas al divisar las ruinas ancestrales.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val result = extractor.extract(html, "https://example.com/chapter-2")

        // Selectores complejos, bare tags y :not() deben ser ignorados
        assertTrue(result.bodyXhtml.contains("Párrafo que un selector :not() erróneo eliminaría"))
        assertTrue(result.bodyXhtml.contains("Párrafo hijo con combinador complejo"))
        assertTrue(result.bodyXhtml.contains("Párrafo con clase simple que no tiene .active"))
        assertTrue(result.bodyXhtml.contains("Párrafo adyacente legítimo"))
        assertTrue(result.bodyXhtml.contains("Los exploradores encendieron sus antorchas"))

        // Selector simple con id sí debe eliminarse
        assertFalse(result.bodyXhtml.contains("Texto trampa con id simple que sí debe eliminarse"))
    }

    @Test
    fun `only explicit display none and visibility hidden properties trigger removal`() {
        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <style>
                    .custom-vars { --custom-display: none; background: none; list-style: none; }
                    .pointer-prop { pointer-events: none; }
                    .visible-prop { display: block; visibility: visible; }
                    .real-hidden-tag { visibility: hidden !important; }
                </style>
            </head>
            <body>
                <div id="chapter-content">
                    <h2>Capítulo 3: La Cueva de los Ecos</h2>
                    <p class="custom-vars">Texto con variables CSS y background none.</p>
                    <p class="pointer-prop">Texto con pointer-events none.</p>
                    <p class="visible-prop">Texto con display block y visibility visible.</p>
                    <p style="--my-display: none; background: none;">Texto inline con propiedades no ocultantes.</p>
                    <p style="display: inline-block;">Texto inline con display inline-block.</p>
                    <p style="display: none">Texto inline trampa con display none.</p>
                    <p class="real-hidden-tag">Texto trampa oculto con visibility hidden important.</p>
                    <p>El silencio reinaba en el santuario subterráneo hasta que se escuchó un crujido.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val result = extractor.extract(html, "https://example.com/chapter-3")

        // Propiedades que no ocultan deben preservarse
        assertTrue(result.bodyXhtml.contains("Texto con variables CSS y background none"))
        assertTrue(result.bodyXhtml.contains("Texto con pointer-events none"))
        assertTrue(result.bodyXhtml.contains("Texto con display block y visibility visible"))
        assertTrue(result.bodyXhtml.contains("Texto inline con propiedades no ocultantes"))
        assertTrue(result.bodyXhtml.contains("Texto inline con display inline-block"))
        assertTrue(result.bodyXhtml.contains("El silencio reinaba en el santuario subterráneo"))

        // Propiedades explícitas de ocultamiento sí deben eliminarse
        assertFalse(result.bodyXhtml.contains("Texto inline trampa con display none"))
        assertFalse(result.bodyXhtml.contains("Texto trampa oculto con visibility hidden important"))
    }
}
