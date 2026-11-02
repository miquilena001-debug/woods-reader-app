package com.example.woodsreader.core.web2epub.novel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SiteProfileVariantFixtureTest {

    private val variantHtml = """
        <!DOCTYPE html>
        <html>
        <head>
            <title>Capítulo Especial - Sitio Personalizado</title>
        </head>
        <body>
            <h1 class="novel-story-title">Capítulo 1: El Despertar del Héroe</h1>

            <!-- Contenedor ruidoso de comentarios/enlaces que NO es el texto de la novela,
                 pero que tiene ALTA densidad de párrafos y palabras, diseñada para ganar
                 la heurística de fallback si el selector de contenido del perfil falla. -->
            <div class="user-comments-and-recommendations">
                <p>Comentario 1: Gran traducción, muchas gracias por subir el capítulo hoy.</p>
                <p>Comentario 2: Me encantó la batalla en las montañas del norte, fue épica.</p>
                <p>Comentario 3: Espero con ansias la siguiente entrega la próxima semana.</p>
                <p>Comentario 4: ¿Alguien sabe si el protagonista aprenderá magia elemental pronto?</p>
                <p>Comentario 5: La ambientación del reino antiguo está muy bien lograda por el autor.</p>
                <p>Comentario 6: No olviden apoyar al autor original en sus redes oficiales.</p>
                <p>Comentario 7: Este arco argumental promete ser el mejor de toda la saga fantástica.</p>
                <p>Comentario 8: Recomiendo esta novela a cualquiera que disfrute la fantasía oscura.</p>
                <p>Comentario 9: El desarrollo de los personajes secundarios es sobresaliente.</p>
                <p>Comentario 10: Saludos a toda la comunidad de lectores apasionados de la obra.</p>
            </div>

            <!-- Contenedor legítimo del contenido, con clase única que NO forma parte
                 de los genericSelectors de ContentExtractor (#content, #chapter-content, etc.) -->
            <div class="site-custom-story-container">
                <p>El caballero avanzó silenciosamente por el sendero ancestral cubierto de nieve perpetua que descendía hacia el valle olvidado.</p>
                <p>Su espada de acero rúnico brillaba con un tenue resplandor azul mientras sus pasos crujían suavemente sobre la escarcha acumulada durante la última tormenta de invierno.</p>
                <p>Dentro del antiguo recinto de piedra negra, las sombras del pasado aguardaban la llegada del heredero prometido por las antiguas profecías del reino del norte.</p>
                <p>Cada respiración en el gélido ambiente nocturno formaba pequeñas nubes blancas que se disipaban en la penumbra del santuario sagrado.</p>
                <p>Sabía que más allá de aquellas puertas de roble blindado no habría vuelta atrás, y que su destino quedaría sellado para siempre ante los ojos de los dioses guardianes.</p>
            </div>
        </body>
        </html>
    """.trimIndent()

    @Test
    fun `profile content selector extracts true narrative and avoids high density noise container`() {
        val profile = SiteProfile(
            id = "customsite",
            domains = listOf("customsite.example.com"),
            contentSelectors = listOf("div.site-custom-story-container"),
            titleSelector = "h1.novel-story-title"
        )

        val extractor = ContentExtractor(profile)
        val result = extractor.extract(
            html = variantHtml,
            baseUri = "https://customsite.example.com/ch1"
        )

        assertEquals(ChapterStatus.OK, result.status)
        assertEquals("Capítulo 1: El Despertar del Héroe", result.title)
        assertTrue(result.wordCount > 100)

        // El contenido legítimo seleccionado por el perfil debe estar presente
        assertTrue(result.bodyXhtml.contains("El caballero avanzó silenciosamente por el sendero ancestral"))
        assertTrue(result.bodyXhtml.contains("Su espada de acero rúnico brillaba con un tenue resplandor"))
        assertTrue(result.bodyXhtml.contains("Dentro del antiguo recinto de piedra negra"))

        // El contenedor de ruido de comentarios NO debe haber sido seleccionado
        assertFalse(result.bodyXhtml.contains("Gran traducción, muchas gracias"))
        assertFalse(result.bodyXhtml.contains("Comentario 10"))
    }

    @Test
    fun `novelfire profile removeSelectors removes site-specific elements not in default selectors`() {
        val novelfireProfile = SiteProfile(
            id = "novelfire",
            domains = listOf("novelfire.net"),
            contentSelectors = listOf("#content", ".content"),
            removeSelectors = listOf(
                ".words",
                ".titles button",
                ".titles .icon-cog-outline",
                ".chapternav",
                ".skiptranslate",
                ".ads",
                ".ad-container",
                "div.box-header",
                "div.chapter-header"
            )
        )

        val htmlWithSiteSpecificJunk = """
            <!DOCTYPE html>
            <html>
            <head><title>Capítulo 1</title></head>
            <body>
                <div class="content">
                    <p>El héroe desenvainó la hoja de fuego para comenzar la expedición.</p>
                    <div class="skiptranslate">Barra de Google Translate del sitio novelfire</div>
                    <div class="chapternav">Botones Anterior Siguiente del sitio</div>
                    <div class="words">[ Conteo de palabras: 2450 palabras ]</div>
                    <p>La cueva oscura resonaba con el eco de sus pasos apresurados.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val extractor = ContentExtractor(novelfireProfile)
        val result = extractor.extract(htmlWithSiteSpecificJunk, "https://novelfire.net/novel/mech/chapter-1")

        assertTrue(result.bodyXhtml.contains("El héroe desenvainó la hoja de fuego"))
        assertTrue(result.bodyXhtml.contains("La cueva oscura resonaba con el eco"))

        // Los elementos específicos de removeSelectors del perfil NO deben estar en el resultado
        assertFalse(result.bodyXhtml.contains("Barra de Google Translate"))
        assertFalse(result.bodyXhtml.contains("Botones Anterior Siguiente"))
        assertFalse(result.bodyXhtml.contains("Conteo de palabras: 2450 palabras"))
    }

    @Test
    fun `novelfire profile removeTextContains strips watermarks embedded in narrative paragraphs`() {
        val novelfireProfile = SiteProfile(
            id = "novelfire",
            domains = listOf("novelfire.net"),
            contentSelectors = listOf("#content", ".content"),
            removeTextContains = listOf(
                "[ ... words ]",
                "Novel Fire",
                "novelfire.net"
            )
        )

        val htmlWithEmbeddedWatermark = """
            <!DOCTYPE html>
            <html>
            <head><title>Capítulo 1</title></head>
            <body>
                <div class="content">
                    <p>El ejército avanzó por las planicies heladas del imperio del norte.</p>
                    <p>Esta traducción pertenece exclusivamente a Novel Fire y no debe ser republicada.</p>
                    <p>Para más capítulos visita novelfire.net cada semana.</p>
                    <p>La batalla decisiva comenzaría al amanecer del siguiente día.</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        val extractor = ContentExtractor(novelfireProfile)
        val result = extractor.extract(htmlWithEmbeddedWatermark, "https://novelfire.net/novel/mech/chapter-1")

        assertTrue(result.bodyXhtml.contains("El ejército avanzó por las planicies heladas"))
        assertTrue(result.bodyXhtml.contains("La batalla decisiva comenzaría al amanecer"))

        // Las marcas de agua prohibidas por removeTextContains deben haber sido eliminadas
        assertFalse(result.bodyXhtml.contains("Novel Fire"))
        assertFalse(result.bodyXhtml.contains("novelfire.net"))
    }

}
