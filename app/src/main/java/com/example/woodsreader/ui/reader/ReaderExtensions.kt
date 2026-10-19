package com.example.woodsreader.ui.reader

import com.example.woodsreader.data.database.HighlightEntity
import com.example.woodsreader.data.database.BookmarkEntity
import com.example.woodsreader.data.datastore.UserPreferences
import com.example.woodsreader.model.ReaderTheme
import com.example.woodsreader.model.ScrollMode
import org.readium.adapter.pdfium.navigator.PdfiumPreferences
import org.readium.r2.navigator.epub.EpubPreferences
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.preferences.Color
import org.readium.r2.navigator.preferences.TextAlign
import org.readium.r2.navigator.preferences.Theme
import org.readium.r2.navigator.Navigator
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import android.view.View
import androidx.fragment.app.Fragment
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.publication.Locator

// ── Extension functions ──────────────────────────────────────────────

/**
 * Acceso seguro a la vista de publicación de Readium.
 * Si el fragmento aún no ha completado onCreateView() o ya fue destruido,
 * requireView() en Readium lanzaría IllegalStateException. Esta propiedad
 * previene cierres inesperados durante navegación rápida o pruebas de estrés.
 */
val VisualNavigator.safePublicationView: View?
    get() = try {
        if (this is Fragment && this.view == null) null
        else this.publicationView
    } catch (_: Exception) {
        null
    }

val Navigator.safePublicationView: View?
    get() = (this as? VisualNavigator)?.safePublicationView

fun Navigator.goForwardCompat(animated: Boolean = false): Boolean {
    return when (this) {
        is EpubNavigatorFragment -> this.goForward(animated)
        is PdfNavigatorFragment<*, *> -> this.goForward(animated)
        else -> false
    }
}

fun Navigator.goBackwardCompat(animated: Boolean = false): Boolean {
    return when (this) {
        is EpubNavigatorFragment -> this.goBackward(animated)
        is PdfNavigatorFragment<*, *> -> this.goBackward(animated)
        else -> false
    }
}

@OptIn(ExperimentalReadiumApi::class)
fun UserPreferences.toEpubPreferences(): EpubPreferences {
    val fontSizeMultiplier = fontSizeSp / 18.0
    val textAlignEnum = when (textAlign) {
        "justify" -> TextAlign.JUSTIFY
        "left"    -> TextAlign.LEFT
        else      -> TextAlign.START
    }
    // El modo unificado utiliza desplazamiento continuo vertical como base nativa,
    // permitiendo scroll libre con el dedo Y paso de página por swipe/tap con efecto 3D/slide simultáneamente.
    val isScroll = true
    val readiumFont = when (fontFamily.lowercase()) {
        "sans-serif", "sans", "inter" -> org.readium.r2.navigator.preferences.FontFamily.SANS_SERIF
        "monospace", "mono", "jetbrains mono" -> org.readium.r2.navigator.preferences.FontFamily.MONOSPACE
        "cursive" -> org.readium.r2.navigator.preferences.FontFamily.CURSIVE
        "literata" -> org.readium.r2.navigator.preferences.FontFamily("Literata")
        "serif", "lora" -> org.readium.r2.navigator.preferences.FontFamily.SERIF
        else -> org.readium.r2.navigator.preferences.FontFamily(fontFamily)
    }

    return when (theme) {
        ReaderTheme.HIDDEN_FOREST -> EpubPreferences(
            backgroundColor = Color(0xFF0A0D0A.toInt()), textColor = Color(0xFFD4E8D4.toInt()),
            fontSize = fontSizeMultiplier, lineHeight = lineSpacingMultiplier.toDouble(),
            pageMargins = horizontalMargin.toDouble() / 20.0, textAlign = textAlignEnum,
            hyphens = hyphenation, fontFamily = readiumFont,
            scroll = isScroll, theme = Theme.DARK, publisherStyles = false
        )
        ReaderTheme.DAY -> EpubPreferences(
            backgroundColor = Color(0xFFFAF9F6.toInt()), textColor = Color(0xFF242424.toInt()),
            theme = Theme.LIGHT, fontSize = fontSizeMultiplier, lineHeight = lineSpacingMultiplier.toDouble(),
            pageMargins = horizontalMargin.toDouble() / 20.0, textAlign = textAlignEnum,
            hyphens = hyphenation, fontFamily = readiumFont,
            scroll = isScroll, publisherStyles = false
        )
        ReaderTheme.SEPIA -> EpubPreferences(
            backgroundColor = Color(0xFFF7EED8.toInt()), textColor = Color(0xFF4A3728.toInt()),
            theme = Theme.SEPIA, fontSize = fontSizeMultiplier, lineHeight = lineSpacingMultiplier.toDouble(),
            pageMargins = horizontalMargin.toDouble() / 20.0, textAlign = textAlignEnum,
            hyphens = hyphenation, fontFamily = readiumFont,
            scroll = isScroll, publisherStyles = false
        )
        ReaderTheme.AMOLED -> EpubPreferences(
            backgroundColor = Color(0xFF000000.toInt()), textColor = Color(0xFFE0E0E0.toInt()),
            fontSize = fontSizeMultiplier, lineHeight = lineSpacingMultiplier.toDouble(),
            pageMargins = horizontalMargin.toDouble() / 20.0, textAlign = textAlignEnum,
            hyphens = hyphenation, fontFamily = readiumFont,
            scroll = isScroll, publisherStyles = false
        )
        ReaderTheme.SUNSET -> EpubPreferences(
            backgroundColor = Color(0xFF1A0D08.toInt()), textColor = Color(0xFFEDD5C0.toInt()),
            fontSize = fontSizeMultiplier, lineHeight = lineSpacingMultiplier.toDouble(),
            pageMargins = horizontalMargin.toDouble() / 20.0, textAlign = textAlignEnum,
            hyphens = hyphenation, fontFamily = readiumFont,
            scroll = isScroll, publisherStyles = false
        )
    }
}

@Suppress("UnusedReceiverParameter")
fun UserPreferences.toPdfPreferences(): PdfiumPreferences = PdfiumPreferences()

@Suppress("UnusedReceiverParameter")
fun ReaderTheme.toPdfPreferences(): PdfiumPreferences = PdfiumPreferences()

fun HighlightEntity.toLocator(): Locator? = try {
    Locator.fromJSON(org.json.JSONObject(this.locatorJson))
} catch (_: Exception) { null }

fun BookmarkEntity.toLocator(): Locator? = try {
    Locator.fromJSON(org.json.JSONObject(this.locatorJson))
} catch (_: Exception) { null }

data class ChapterInfo(
    val title: String? = null,
    val progress: Float = 0f,
    val timeRemainingMinutes: Int? = null
)

fun applyToAllWebViews(view: android.view.View?, action: (android.webkit.WebView) -> Unit) {
    if (view == null) return
    if (view is android.webkit.WebView) {
        action(view)
        return
    }
    if (view is android.view.ViewGroup) {
        for (i in 0 until view.childCount) {
            applyToAllWebViews(view.getChildAt(i), action)
        }
    }
}

fun findActiveWebView(view: android.view.View?): android.webkit.WebView? {
    if (view == null) return null
    val webViews = mutableListOf<android.webkit.WebView>()
    fun collect(v: android.view.View) {
        if (v is android.webkit.WebView) {
            webViews.add(v)
        } else if (v is android.view.ViewGroup) {
            for (i in 0 until v.childCount) {
                collect(v.getChildAt(i))
            }
        }
    }
    collect(view)
    if (webViews.isEmpty()) return null
    if (webViews.size == 1) return webViews[0]

    val rect = android.graphics.Rect()
    // 1. Prioridad: WebView que está realmente visible en pantalla cubriendo la mayor parte del ancho
    val primaryVisible = webViews.firstOrNull { wv ->
        wv.isShown && wv.getGlobalVisibleRect(rect) && rect.width() >= (wv.width * 0.6f)
    }
    if (primaryVisible != null) return primaryVisible

    // 2. Fallback: cualquier WebView con área visible positiva
    val anyVisible = webViews.firstOrNull { wv ->
        wv.isShown && wv.getGlobalVisibleRect(rect) && rect.width() > 0
    }
    if (anyVisible != null) return anyVisible

    return webViews.firstOrNull { it.isShown } ?: webViews.firstOrNull()
}


fun android.webkit.WebView.applyCustomFontFamily(fontKey: String) {
    val cssFont = when (fontKey.lowercase()) {
        "literata" -> "'Literata', Georgia, serif"
        "sans-serif", "sans", "inter" -> "sans-serif"
        "monospace", "mono", "jetbrains mono" -> "monospace"
        "cursive" -> "cursive"
        "serif", "lora" -> "serif"
        else -> fontKey
    }
    val js = """
        (function() {
            try {
                document.documentElement.style.setProperty('--USER__fontFamily', '$cssFont', 'important');
                var el = document.getElementById('woods-custom-font-style');
                if (!el) {
                    el = document.createElement('style');
                    el.id = 'woods-custom-font-style';
                    document.head.appendChild(el);
                }
                el.textContent = '* { font-family: ' + '$cssFont' + ' !important; }';
            } catch(e) {}
        })()
    """.trimIndent()
    evaluateJavascript(js, null)
}

/**
 * Desplaza el WebView verticalmente hacia abajo una fracción de la pantalla (e.g. 85%).
 * Si animated es true, utiliza scroll suave nativo del navegador.
 * Si animated es false, desplaza instantáneamente sincronizado con BookPageTurnEffect.
 */
fun android.webkit.WebView.scrollByViewportDown(fraction: Float = 0.85f, animated: Boolean = false) {
    val px = (height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels).let { (it * fraction).toInt() }
    scrollBy(0, px)
    val js = """
        (function() {
            try {
                var se = document.scrollingElement || document.documentElement || document.body;
                var cur = window.pageYOffset || se.scrollTop || 0;
                var maxScroll = Math.max(se.scrollHeight, document.body.scrollHeight, document.documentElement.scrollHeight) - window.innerHeight;
                var target = Math.min(Math.max(0, maxScroll), cur + $px);
                if ($animated) {
                    window.scrollTo({ top: target, behavior: 'smooth' });
                } else {
                    se.scrollTop = target;
                    window.scrollTo(0, target);
                }
            } catch(e) {}
        })()
    """.trimIndent()
    evaluateJavascript(js, null)
}

/**
 * Desplaza el WebView verticalmente hacia arriba una fracción de la pantalla (e.g. 85%).
 * Si animated es true, utiliza scroll suave nativo del navegador.
 * Si animated es false, desplaza instantáneamente sincronizado con BookPageTurnEffect.
 */
fun android.webkit.WebView.scrollByViewportUp(fraction: Float = 0.85f, animated: Boolean = false) {
    val px = (height.takeIf { it > 0 } ?: resources.displayMetrics.heightPixels).let { (it * fraction).toInt() }
    scrollBy(0, -px)
    val js = """
        (function() {
            try {
                var se = document.scrollingElement || document.documentElement || document.body;
                var cur = window.pageYOffset || se.scrollTop || 0;
                var target = Math.max(0, cur - $px);
                if ($animated) {
                    window.scrollTo({ top: target, behavior: 'smooth' });
                } else {
                    se.scrollTop = target;
                    window.scrollTo(0, target);
                }
            } catch(e) {}
        })()
    """.trimIndent()
    evaluateJavascript(js, null)
}

fun android.webkit.WebView.smoothScrollByViewport(fraction: Float = 0.85f) {
    scrollByViewportDown(fraction, animated = true)
}

fun android.webkit.WebView.smoothScrollByViewportNegative(fraction: Float = 0.85f) {
    scrollByViewportUp(fraction, animated = true)
}

fun android.webkit.WebView.scrollByViewportInstant(fraction: Float = 0.85f) {
    scrollByViewportDown(fraction, animated = false)
}

fun android.webkit.WebView.scrollByViewportInstantNegative(fraction: Float = 0.85f) {
    scrollByViewportUp(fraction, animated = false)
}

/**
 * Desplaza el WebView al final absoluto del contenido (progression 1.0).
 * Usado al retroceder al capítulo anterior para comenzar en la última página del mismo.
 */
fun android.webkit.WebView.scrollToBottom() {
    post {
        pageDown(true)
    }
    val js = """
        (function() {
            try {
                var se = document.scrollingElement || document.documentElement || document.body;
                var maxScroll = Math.max(se ? se.scrollHeight : 0, document.body ? document.body.scrollHeight : 0, document.documentElement ? document.documentElement.scrollHeight : 0);
                if (se) se.scrollTop = maxScroll;
                window.scrollTo(0, maxScroll);
            } catch(e) {}
        })()
    """.trimIndent()
    evaluateJavascript(js, null)
}

/**
 * En modo paginado: verifica si hay una página siguiente dentro del capítulo actual.
 * Retorna true solo si curCol < maxCol.
 */
fun android.webkit.WebView.canAdvancePageInChapter(onResult: (Boolean) -> Unit) {
    val js = """
        (function() {
            try {
                var se = document.scrollingElement || document.documentElement || document.body;
                var scrollLeft = Math.abs(se.scrollLeft || window.pageXOffset || 0);
                var clientW = window.innerWidth || se.clientWidth || 1;
                var scrollW = Math.max(se.scrollWidth || 0, document.documentElement ? document.documentElement.scrollWidth : 0, document.body ? document.body.scrollWidth : 0);
                var curCol = Math.round(scrollLeft / clientW);
                var maxCol = Math.max(0, Math.ceil((scrollW - 10) / clientW) - 1);
                return (curCol < maxCol) ? "YES" : "NO";
            } catch(e) { return "YES"; }
        })()
    """.trimIndent()
    evaluateJavascript(js) { res ->
        val clean = res?.replace("\"", "")?.trim()
        onResult(clean == "YES")
    }
}

/**
 * En modo paginado: verifica si hay una página anterior dentro del capítulo actual.
 * Retorna true solo si curCol > 0.
 */
fun android.webkit.WebView.canRetreatPageInChapter(onResult: (Boolean) -> Unit) {
    val js = """
        (function() {
            try {
                var se = document.scrollingElement || document.documentElement || document.body;
                var scrollLeft = Math.abs(se.scrollLeft || window.pageXOffset || 0);
                var clientW = window.innerWidth || se.clientWidth || 1;
                var curCol = Math.round(scrollLeft / clientW);
                return (curCol > 0) ? "YES" : "NO";
            } catch(e) { return "YES"; }
        })()
    """.trimIndent()
    evaluateJavascript(js) { res ->
        val clean = res?.replace("\"", "")?.trim()
        onResult(clean == "YES")
    }
}

/**
 * Aplica el estilo TTS cambiando exclusivamente el color de la letra (o subrayado)
 * de forma idéntica a PDF Reflow, sin cajas de fondo, y gestiona el desplazamiento suave o pase de página.
 */
fun android.webkit.WebView.applyCustomTtsHighlight(
    cssSelector: String?,
    textSnippet: String?,
    style: com.example.woodsreader.model.TtsHighlightStyle,
    theme: com.example.woodsreader.model.ReaderTheme = com.example.woodsreader.model.ReaderTheme.HIDDEN_FOREST,
    isPaginated: Boolean = true,
    autoScroll: Boolean = true,
    onPageAction: ((String) -> Unit)? = null
) {
    val isDark = theme != com.example.woodsreader.model.ReaderTheme.DAY && theme != com.example.woodsreader.model.ReaderTheme.SEPIA
    val colorHex = when (style) {
        com.example.woodsreader.model.TtsHighlightStyle.BACKGROUND_YELLOW -> if (isDark) "#FFD600" else "#9E6500"
        com.example.woodsreader.model.TtsHighlightStyle.BACKGROUND_ACCENT -> if (isDark) "#2ECC71" else "#1B663E"
        com.example.woodsreader.model.TtsHighlightStyle.BACKGROUND_BLUE   -> if (isDark) "#2196F3" else "#0D47A1"
        com.example.woodsreader.model.TtsHighlightStyle.BACKGROUND_ORANGE -> if (isDark) "#FF7A45" else "#B03A00"
        com.example.woodsreader.model.TtsHighlightStyle.UNDERLINE         -> if (isDark) "#2ECC71" else "#1B663E"
        com.example.woodsreader.model.TtsHighlightStyle.NONE              -> ""
    }
    val isUnderline = style == com.example.woodsreader.model.TtsHighlightStyle.UNDERLINE
    val isNone = style == com.example.woodsreader.model.TtsHighlightStyle.NONE

    val safeSnippet = textSnippet?.replace("\\", "\\\\")?.replace("'", "\\'")?.replace("\"", "\\\"")?.replace("\n", " ")?.replace("\r", " ") ?: ""
    val safeSelector = cssSelector?.replace("\\", "\\\\")?.replace("'", "\\'") ?: ""

    val js = """
        (function() {
            try {
                function norm(s) {
                    if (!s) return "";
                    return s.toLowerCase()
                        .replace(/[\u2018\u2019\u201A\u201B\u2032\u2035']/g, "'")
                        .replace(/[\u201C\u201D\u201E\u201F\u2033\u2036"«»„]/g, '"')
                        .replace(/[\u2014\u2013\u2212-]/g, '-')
                        .replace(/\s+/g, ' ')
                        .trim();
                }

                var prev = document.querySelectorAll('.woods-tts-active');
                for (var i = 0; i < prev.length; i++) {
                    prev[i].classList.remove('woods-tts-active');
                }

                if ($isNone) {
                    return "VISIBLE";
                }

                var target = null;
                var selector = '$safeSelector';
                if (selector && selector.length > 0) {
                    try { target = document.querySelector(selector); } catch(e) {}
                }

                var snippet = '$safeSnippet';
                var normSnippet = norm(snippet);

                if (!target && normSnippet.length > 3) {
                    var sub = normSnippet.substring(0, Math.min(25, normSnippet.length));
                    var candidates = document.querySelectorAll('p, div, li, h1, h2, h3, h4, h5, h6');
                    for (var j = 0; j < candidates.length; j++) {
                        var c = candidates[j];
                        if (!c.querySelector('p, div') && norm(c.textContent).indexOf(sub) !== -1) {
                            target = c;
                            break;
                        }
                    }
                    if (!target) {
                        for (var k = 0; k < candidates.length; k++) {
                            if (norm(candidates[k].textContent).indexOf(sub) !== -1) {
                                target = candidates[k];
                                break;
                            }
                        }
                    }
                }

                if (!target) return "NOT_FOUND";

                var styleEl = document.getElementById('woods-tts-highlight-style');
                if (!styleEl) {
                    styleEl = document.createElement('style');
                    styleEl.id = 'woods-tts-highlight-style';
                    document.head.appendChild(styleEl);
                }

                if ($isUnderline) {
                    styleEl.textContent = '.woods-tts-active, .woods-tts-active * { text-decoration: underline !important; text-decoration-color: $colorHex !important; -webkit-text-decoration-color: $colorHex !important; text-underline-offset: 3px !important; }';
                } else if ('$colorHex'.length > 0) {
                    styleEl.textContent = '.woods-tts-active, .woods-tts-active * { color: $colorHex !important; -webkit-text-fill-color: $colorHex !important; transition: color 0.15s ease !important; }';
                }

                target.classList.add('woods-tts-active');

                if (!$autoScroll) return "VISIBLE";

                // Encontrar el bounding rect específico de la frase activa para salto de página inmediato
                var sentenceRect = null;
                if (normSnippet.length >= 3) {
                    var searchSub = normSnippet.substring(0, Math.min(25, normSnippet.length));
                    var walker = document.createTreeWalker(target, NodeFilter.SHOW_TEXT, null, false);
                    var tNode;
                    while ((tNode = walker.nextNode())) {
                        var tNorm = norm(tNode.nodeValue);
                        var idx = tNorm.indexOf(searchSub);
                        if (idx === -1 && searchSub.length > 12) {
                            idx = tNorm.indexOf(searchSub.substring(0, 12));
                        }
                        if (idx !== -1) {
                            try {
                                var r = document.createRange();
                                var sChar = Math.min(idx, tNode.nodeValue.length);
                                var eChar = Math.min(sChar + Math.min(searchSub.length, 25), tNode.nodeValue.length);
                                r.setStart(tNode, sChar);
                                r.setEnd(tNode, eChar);
                                var b = r.getBoundingClientRect();
                                if (b && (b.width > 0 || b.height > 0)) {
                                    sentenceRect = b;
                                    break;
                                }
                            } catch(_) {}
                        }
                    }
                }

                var checkRect = sentenceRect || target.getBoundingClientRect();

                if ($isPaginated) {
                    var vw = window.innerWidth || document.documentElement.clientWidth || 1;
                    if (checkRect.left >= (vw - 15)) {
                        var pagesAhead = Math.round(checkRect.left / vw);
                        if (pagesAhead >= 2) return "JUMP";
                        return "NEXT";
                    }
                    if (checkRect.right <= 15) {
                        var pagesBehind = Math.round((0 - checkRect.right) / vw);
                        if (pagesBehind >= 2) return "JUMP";
                        return "PREV";
                    }
                    return "VISIBLE";
                } else {
                    var vh = window.innerHeight || document.documentElement.clientHeight || 1;
                    var topThird = vh / 3.0;
                    var bottomThird = (2.0 * vh) / 3.0;
                    if (checkRect.top < topThird || checkRect.bottom > bottomThird) {
                        try {
                            target.scrollIntoView({ behavior: 'smooth', block: 'center' });
                        } catch(e) {
                            var targetY = (window.pageYOffset || document.documentElement.scrollTop || 0) + checkRect.top - (vh * 0.5);
                            window.scrollTo({ top: Math.max(0, targetY), behavior: 'smooth' });
                        }
                    }
                    return "VISIBLE";
                }
            } catch(e) {
                return "ERROR";
            }
        })()
    """.trimIndent()

    evaluateJavascript(js) { res ->
        val clean = res?.replace("\"", "")?.trim()
        if (clean == "NEXT" || clean == "PREV" || clean == "JUMP") {
            onPageAction?.invoke(clean)
        }
    }
}

fun android.webkit.WebView.clearCustomTtsHighlight() {
    val js = """
        (function() {
            try {
                var prev = document.querySelectorAll('.woods-tts-active');
                for (var i = 0; i < prev.length; i++) {
                    prev[i].classList.remove('woods-tts-active');
                }
            } catch(e) {}
        })()
    """.trimIndent()
    evaluateJavascript(js, null)
}

fun android.webkit.WebView.smoothScrollToTtsHighlight() {
    // Mantener compatibilidad si se llama externamente
    val js = """
        (function() {
            try {
                var el = document.querySelector('.woods-tts-active');
                if (!el) return;
                var rect = el.getBoundingClientRect();
                var vh = window.innerHeight || document.documentElement.clientHeight || 1;
                if (rect.top < 60 || rect.bottom > (vh - 80)) {
                    var targetY = (window.pageYOffset || document.documentElement.scrollTop || 0) + rect.top - (vh * 0.28);
                    window.scrollTo({ top: Math.max(0, targetY), behavior: 'smooth' });
                }
            } catch(e) {}
        })()
    """.trimIndent()
    evaluateJavascript(js, null)
}

fun android.webkit.WebView.checkTtsNextColumn(onNeedsNextPage: (Boolean) -> Unit) {
    val js = """
        (function() {
            try {
                var el = document.querySelector('.woods-tts-active');
                if (!el) return "SAME";
                var rect = el.getBoundingClientRect();
                var vw = window.innerWidth || document.documentElement.clientWidth || 1;
                if (rect.left >= (vw - 10)) return "NEXT";
                if (rect.right <= 10) return "PREV";
                return "SAME";
            } catch(e) { return "SAME"; }
        })()
    """.trimIndent()
    evaluateJavascript(js) { res ->
        val clean = res?.replace("\"", "")?.trim()
        if (clean == "NEXT") onNeedsNextPage(true)
    }
}
