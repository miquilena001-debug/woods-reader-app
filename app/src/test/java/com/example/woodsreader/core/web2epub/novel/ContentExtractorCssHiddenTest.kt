package com.example.woodsreader.core.web2epub.novel

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ContentExtractorCssHiddenTest {

    private val htmlWithHiddenElements = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Capítulo de Prueba</title>
            <style>
                .anti-piracy-trap { display: none; }
                #watermark-text { display:none; }
                span.hidden-warning { visibility: hidden; }
                .complex-ignored > span:hover { display: none; }
            </style>
        </head>
        <body>
            <div id="chapter-content">
                <h2>Capítulo 10: Secretos del Bosque</h2>
                <p>Este es el párrafo principal y legítimo de la novela que sí debe aparecer.</p>
                <p class="anti-piracy-trap">Texto robado de piratería no autorizado.</p>
                <div id="watermark-text">Marca de agua oculta en css.</div>
                <p>Los aventureros continuaron su marcha bajo la lluvia densa. <span class="hidden-warning">Advertencia oculta en span.</span></p>
                <p style="display: none">Párrafo con inline display none.</p>
                <p>Al final del camino divisaron la antigua fortaleza. <span style="visibility:hidden">Span con inline visibility hidden.</span></p>
            </div>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun `ContentExtractor removes elements hidden by CSS rules in style tags and inline styles`() {
        val extractor = ContentExtractor()
        val result = extractor.extract(
            html = htmlWithHiddenElements,
            baseUri = "https://example.com/chapter-10",
            fallbackTitle = "Capítulo 10"
        )

        // Contenido legítimo debe preservarse
        assertTrue(result.bodyXhtml.contains("Este es el párrafo principal y legítimo de la novela que sí debe aparecer."))
        assertTrue(result.bodyXhtml.contains("Los aventureros continuaron su marcha bajo la lluvia densa."))
        assertTrue(result.bodyXhtml.contains("Al final del camino divisaron la antigua fortaleza."))

        // Elementos ocultos por reglas en <style> deben eliminarse
        assertFalse("Texto con clase .anti-piracy-trap no debe aparecer", result.bodyXhtml.contains("Texto robado de piratería"))
        assertFalse("Elemento con id #watermark-text no debe aparecer", result.bodyXhtml.contains("Marca de agua oculta"))
        assertFalse("Elemento con span.hidden-warning no debe aparecer", result.bodyXhtml.contains("Advertencia oculta"))

        // Elementos ocultos por style inline deben eliminarse
        assertFalse("Elemento con style inline display:none no debe aparecer", result.bodyXhtml.contains("Párrafo con inline display none"))
        assertFalse("Elemento con style inline visibility:hidden no debe aparecer", result.bodyXhtml.contains("Span con inline visibility hidden"))
    }
}
