package com.example.woodsreader.core.web2epub.novel

object Fixtures {

    const val TOC_DESCENDING_HTML = """<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <title>Novela de Prueba - Índice</title>
    <meta property="og:title" content="Novela de Prueba" />
    <meta name="author" content="Autor Fantástico" />
    <meta property="og:description" content="Una novela épica de prueba en el bosque." />
    <meta property="og:image" content="https://example.com/cover.jpg" />
</head>
<body>
    <h1 class="novel-title">Novela de Prueba</h1>
    <div id="chapter-list">
        <ul>
            <li><a href="https://example.com/novel/capitulo-5">Capítulo 5: El Fin del Viaje</a></li>
            <li><a href="https://example.com/novel/capitulo-4">Capítulo 4: La Caverna Oculta</a></li>
            <li><a href="https://example.com/novel/capitulo-3">Capítulo 3: Encuentro en la Niebla</a></li>
            <li><a href="https://example.com/novel/capitulo-2">Capítulo 2: Primeros Pasos</a></li>
            <li><a href="https://example.com/novel/capitulo-1">Capítulo 1: El Despertar</a></li>
        </ul>
    </div>
</body>
</html>"""

    const val CHAPTER_RICH_HTML = """<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <title>Capítulo 1: El Despertar - Novela de Prueba</title>
</head>
<body>
    <header><nav><a href="/">Inicio</a></nav></header>
    <div id="chr-content">
        <h2 class="chapter-title">Capítulo 1: El Despertar</h2>
        <p>El sol brillaba con una <em>intensidad inusual</em> sobre las copas de los árboles milenarios.</p>
        <p>Un suave rumor de hojas quebradas anunció que <strong>alguien o algo</strong> se aproximaba.</p>
        <div class="separator">
            <img data-src="https://example.com/images/forest_dawn.jpg" alt="Amanecer en el bosque" />
        </div>
        <p>El viento trajo consigo un misterioso susurro que nadie pudo descifrar.</p>
        <div class="ads">Publicidad no deseada</div>
        <p>Report chapter if you find errors.</p>
    </div>
    <footer><p>Pie de página</p></footer>
</body>
</html>"""

    const val CHAPTER_ONLY_BR_HTML = """<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <title>Capítulo 2: Primeros Pasos</title>
</head>
<body>
    <div id="chr-content">
        <h2>Capítulo 2: Primeros Pasos</h2>
        Avanzaron lentamente por el sendero pedregoso.<br><br>
        La noche caía y las sombras se alargaban sin tregua.<br>
        Cada paso parecía resonar en el silencio del valle.<br><br>
        Finalmente vislumbraron una fogata a lo lejos.
    </div>
</body>
</html>"""

    const val CHAPTER_LOCKED_HTML = """<!DOCTYPE html>
<html lang="es">
<head>
    <meta charset="utf-8">
    <title>Capítulo 3: Encuentro en la Niebla</title>
</head>
<body>
    <div id="chr-content">
        <h2>Capítulo 3: Encuentro en la Niebla</h2>
        <p>Este es un capítulo bloqueado. Por favor suscríbete para leer más.</p>
        <p>Capítulo bloqueado / Premium chapter.</p>
        <a href="/subscribe">Unlock chapter</a>
    </div>
</body>
</html>"""

    const val CLOUDFLARE_CHALLENGE_HTML = """<!DOCTYPE html>
<html lang="en">
<head>
    <meta charset="utf-8">
    <title>Just a moment...</title>
</head>
<body>
    <div id="challenge-running">
        Checking your browser before accessing the site.
        <div class="cf-turnstile" data-sitekey="synthetic-key"></div>
    </div>
</body>
</html>"""
}
