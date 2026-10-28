package com.example.woodsreader.core.web2epub

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.content.Context
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.woodsreader.core.license.ProLicenseManager
import com.example.woodsreader.ui.common.WoodsProUnlockDialog
import com.example.woodsreader.ui.theme.HiddenForest
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import org.json.JSONTokener
import java.io.File
import java.util.Locale

private const val SCRAPER_JS = """
(function() {
    try {
        var pageTitle = document.title || '';
        var isCf = pageTitle.indexOf('Just a moment') !== -1 ||
                   document.querySelector('#challenge-running, #challenge-stage, #turnstile-wrapper, .cf-turnstile') !== null;
        if (isCf) {
            return JSON.stringify({ isChallenge: true });
        }

        var title = '';
        var titleEl = document.querySelector('h1, h2.chapter-title, .chapter-title, .entry-title, .chr-title');
        if (titleEl) {
            title = titleEl.innerText.trim();
        }
        if (!title) {
            title = pageTitle.split(/[-–—|]/)[0].trim();
        }

        var candidateSelectors = [
            '#chr-content',
            '#chapter-content',
            '.chapter-c',
            '.reading-content',
            '.text-left',
            '.entry-content',
            '.post-content',
            '.chapter-inner',
            '#chapter-container',
            'article'
        ];

        var contentContainer = null;
        for (var i = 0; i < candidateSelectors.length; i++) {
            var el = document.querySelector(candidateSelectors[i]);
            if (el && el.innerText && el.innerText.length > 150) {
                contentContainer = el;
                break;
            }
        }

        if (!contentContainer) {
            var bestScore = -1;
            var divs = document.querySelectorAll('div, section, article, main');
            for (var d = 0; d < divs.length; d++) {
                var div = divs[d];
                if (div.closest('header, nav, footer, .sidebar, #comments, .comments, #footer, .menu')) continue;
                var ps = div.querySelectorAll('p');
                if (ps.length < 2) continue;
                var textLen = 0;
                for (var pIdx = 0; pIdx < ps.length; pIdx++) {
                    textLen += ps[pIdx].innerText.trim().length;
                }
                var links = div.querySelectorAll('a');
                var linkLen = 0;
                for (var aIdx = 0; aIdx < links.length; aIdx++) {
                    linkLen += links[aIdx].innerText.trim().length;
                }
                var score = textLen - (linkLen * 3);
                if (score > bestScore) {
                    bestScore = score;
                    contentContainer = div;
                }
            }
        }

        if (!contentContainer) {
            contentContainer = document.body;
        }

        var clone = contentContainer.cloneNode(true);
        var noise = clone.querySelectorAll('script, style, noscript, nav, header, footer, iframe, svg, button, form, .ads, .adsbygoogle, .sharedaddy, .advertisement, .comment-respond, .post-navigation, .tr-comment, .wp-block-buttons');
        for (var n = 0; n < noise.length; n++) {
            if (noise[n].parentNode) noise[n].parentNode.removeChild(noise[n]);
        }

        var paragraphs = [];
        var pEls = clone.querySelectorAll('p');
        if (pEls.length > 0) {
            for (var k = 0; k < pEls.length; k++) {
                var t = pEls[k].innerText.trim();
                if (t.length > 0) paragraphs.push(t);
            }
        } else {
            var lines = clone.innerText.split(/\n+/);
            for (var l = 0; l < lines.length; l++) {
                var tl = lines[l].trim();
                if (tl.length > 0) paragraphs.push(tl);
            }
        }

        var cleanParagraphs = [];
        for (var cp = 0; cp < paragraphs.length; cp++) {
            var para = paragraphs[cp];
            var low = para.toLowerCase();
            if (low.length < 3) continue;
            if (low.indexOf('window.adsbygoogle') !== -1 || low.indexOf('google-auto-placed') !== -1) continue;
            if (low.indexOf('report chapter') !== -1 || low.indexOf('patreon.com') !== -1) continue;
            cleanParagraphs.push(para);
        }

        var nextUrl = null;
        var nextBtn = document.querySelector("a[rel='next'], a.next_page, a#next_chap, a.btn-next, a#next-btn, a.next-chapter");
        if (nextBtn && nextBtn.href && nextBtn.href.indexOf('javascript') !== 0) {
            nextUrl = nextBtn.href;
        }

        if (!nextUrl) {
            var allLinks = document.querySelectorAll('a');
            var nextKeywords = ['siguiente', 'sig.', 'sig', 'próximo', 'proximo', 'next', 'next chapter', 'next >', 'next >>', 'next »', '»', '›'];
            for (var al = 0; al < allLinks.length; al++) {
                var a = allLinks[al];
                var aText = (a.innerText || '').trim().toLowerCase();
                var aTitle = (a.getAttribute('title') || '').toLowerCase();
                for (var kw = 0; kw < nextKeywords.length; kw++) {
                    var word = nextKeywords[kw];
                    if (aText === word || aTitle.indexOf(word) !== -1 || aText.indexOf(word) !== -1) {
                        if (a.href && a.href.indexOf('javascript') !== 0 && a.href !== window.location.href && a.href.indexOf('#') === -1) {
                            nextUrl = a.href;
                            break;
                        }
                    }
                }
                if (nextUrl) break;
            }
        }

        return JSON.stringify({
            isChallenge: false,
            title: title,
            paragraphs: cleanParagraphs,
            nextUrl: nextUrl
        });
    } catch (err) {
        return JSON.stringify({ error: err.toString() });
    }
})();
"""

enum class ScrapingStage {
    IDLE,
    SCRAPING,
    COMPLETED,
    ERROR
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LegacyWebToEpubImporterDialog(
    proLicenseManager: ProLicenseManager? = null,
    onDismiss: () -> Unit,
    onEpubGenerated: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val isPro = proLicenseManager?.isProUnlocked?.collectAsState()?.value ?: false
    var showProUnlockDialog by remember { mutableStateOf(false) }

    var urlInput by remember { mutableStateOf("") }
    var bookTitleInput by remember { mutableStateOf("") }
    var selectedBatch by remember { mutableIntStateOf(10) }
    val batchOptions = listOf(1, 10, 25, 50, 100)

    var stage by remember { mutableStateOf(ScrapingStage.IDLE) }
    var statusMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var downloadedChaptersCount by remember { mutableIntStateOf(0) }
    var lastChapterTitle by remember { mutableStateOf("") }
    var currentJitterWaitMs by remember { mutableLongStateOf(0L) }
    var isWaitingJitter by remember { mutableStateOf(false) }

    var isWebViewExpanded by remember { mutableStateOf(false) }
    var isCloudflareChallenge by remember { mutableStateOf(false) }

    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var epubBuilderRef by remember { mutableStateOf<LocalEpubBuilder?>(null) }
    var outputFileRef by remember { mutableStateOf<File?>(null) }
    var currentLoadingUrl by remember { mutableStateOf("") }
    var isProcessingPage by remember { mutableStateOf(false) }
    var emptyRetryCount by remember { mutableIntStateOf(0) }

    // Periodic detection when Cloudflare challenge is active
    LaunchedEffect(isCloudflareChallenge, stage) {
        while (isCloudflareChallenge && stage == ScrapingStage.SCRAPING) {
            delay(1500)
            webViewRef?.evaluateJavascript(
                "(function() { return (!document.title.includes('Just a moment') && !document.querySelector('#challenge-running, #challenge-stage, .cf-turnstile')); })()"
            ) { result ->
                val cleared = result?.replace("\"", "")?.trim().equals("true", ignoreCase = true)
                if (cleared) {
                    isCloudflareChallenge = false
                    isWebViewExpanded = false
                    statusMessage = "Cloudflare superado. Reanudando extracción…"
                    scope.launch {
                        delay(600)
                        webViewRef?.evaluateJavascript(SCRAPER_JS) { res ->
                            scope.launch {
                                processScrapedJson(
                                    rawResult = res,
                                    currentUrl = currentLoadingUrl,
                                    targetCount = selectedBatch,
                                    context = context,
                                    scope = scope,
                                    onUpdateStage = { stage = it },
                                    onUpdateStatus = { statusMessage = it },
                                    onUpdateError = { errorMessage = it },
                                    onUpdateDownloaded = { downloadedChaptersCount = it },
                                    onUpdateLastTitle = { lastChapterTitle = it },
                                    onUpdateJitter = { isWaitingJitter = it; currentJitterWaitMs = if (it) currentJitterWaitMs else 0L },
                                    onUpdateNextUrl = { currentLoadingUrl = it },
                                    onUpdateChallenge = { isChallenge, expand ->
                                        isCloudflareChallenge = isChallenge
                                        if (expand) isWebViewExpanded = true
                                    },
                                    onSetBuilder = { epubBuilderRef = it },
                                    onSetOutputFile = { outputFileRef = it },
                                    onSetProcessing = { isProcessingPage = it },
                                    onSetRetry = { emptyRetryCount = it },
                                    onFinishSuccess = { file ->
                                        onEpubGenerated(file)
                                    },
                                    getBuilder = { epubBuilderRef },
                                    getOutputFile = { outputFileRef },
                                    getDownloaded = { downloadedChaptersCount },
                                    getRetry = { emptyRetryCount },
                                    getBookTitleInput = { bookTitleInput },
                                    getWebView = { webViewRef }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Cleanup upon dismissal
    DisposableEffect(Unit) {
        onDispose {
            runCatching {
                webViewRef?.stopLoading()
                if (stage != ScrapingStage.COMPLETED) {
                    epubBuilderRef?.close()
                }
            }
        }
    }

    if (showProUnlockDialog && proLicenseManager != null) {
        WoodsProUnlockDialog(
            proLicenseManager = proLicenseManager,
            onDismiss = { showProUnlockDialog = false },
            onUnlocked = { showProUnlockDialog = false }
        )
    }

    Dialog(
        onDismissRequest = {
            if (stage == ScrapingStage.SCRAPING) {
                // Confirm cancel or allow user to dismiss and finalize
            } else {
                onDismiss()
            }
        },
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .fillMaxHeight(if (isWebViewExpanded) 0.92f else 0.82f)
                .clip(RoundedCornerShape(20.dp))
                .border(1.dp, HiddenForest.OutlineHi, RoundedCornerShape(20.dp))
                .animateContentSize(),
            colors = CardDefaults.cardColors(containerColor = HiddenForest.Surface)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(20.dp)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(HiddenForest.AccentContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Public,
                                contentDescription = null,
                                tint = HiddenForest.Accent,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Web a EPUB",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = HiddenForest.Text
                            )
                            Text(
                                text = "Conversor Universal de Novelas",
                                style = MaterialTheme.typography.bodySmall,
                                color = HiddenForest.TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (stage == ScrapingStage.SCRAPING) {
                                epubBuilderRef?.finishEpub()
                                outputFileRef?.let { if (it.exists() && downloadedChaptersCount > 0) onEpubGenerated(it) }
                            }
                            onDismiss()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = HiddenForest.TextLow
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Cloudflare Challenge Alert Banner
                if (isCloudflareChallenge) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF2C2210)),
                        border = BorderStroke(1.dp, HiddenForest.Amber),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Warning,
                                contentDescription = null,
                                tint = HiddenForest.Amber,
                                modifier = Modifier.size(24.dp)
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Verificación Cloudflare requerida",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = HiddenForest.Amber
                                )
                                Text(
                                    text = "Toca la casilla 'Verifica que eres humano' en el visor web para reanudar.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HiddenForest.Text
                                )
                            }
                        }
                    }
                }

                // Main Content depending on stage
                when (stage) {
                    ScrapingStage.IDLE -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Text(
                                text = "Pega el enlace del primer capítulo de la novela. Woods Reader limpiará los anuncios, detectará los enlaces 'Siguiente' y empaquetará un libro EPUB estándar.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HiddenForest.TextSecondary
                            )

                            // Input URL with Paste action
                            OutlinedTextField(
                                value = urlInput,
                                onValueChange = { urlInput = it },
                                label = { Text("URL del Capítulo Inicial") },
                                placeholder = { Text("https://novelbin.com/novel/capitulo-1") },
                                singleLine = true,
                                trailingIcon = {
                                    IconButton(
                                        onClick = {
                                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                            val clip = clipboard?.primaryClip?.getItemAt(0)?.text?.toString()
                                            if (!clip.isNullOrBlank()) {
                                                urlInput = clip.trim()
                                            }
                                        }
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.ContentPaste,
                                            contentDescription = "Pegar",
                                            tint = HiddenForest.Accent
                                        )
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = HiddenForest.Accent,
                                    unfocusedBorderColor = HiddenForest.Outline,
                                    focusedLabelColor = HiddenForest.Accent,
                                    unfocusedLabelColor = HiddenForest.TextLow,
                                    focusedTextColor = HiddenForest.Text,
                                    unfocusedTextColor = HiddenForest.Text
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Input Title (Optional)
                            OutlinedTextField(
                                value = bookTitleInput,
                                onValueChange = { bookTitleInput = it },
                                label = { Text("Título del Libro (Opcional)") },
                                placeholder = { Text("Ej. Shadow Slave / Overlord") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = HiddenForest.Accent,
                                    unfocusedBorderColor = HiddenForest.Outline,
                                    focusedLabelColor = HiddenForest.Accent,
                                    unfocusedLabelColor = HiddenForest.TextLow,
                                    focusedTextColor = HiddenForest.Text,
                                    unfocusedTextColor = HiddenForest.Text
                                ),
                                shape = RoundedCornerShape(12.dp)
                            )

                            // Batch chips
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(
                                    text = "Capítulos a descargar en lote:",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = HiddenForest.TextSecondary
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    batchOptions.forEach { count ->
                                        val isSelected = selectedBatch == count
                                        val isProGated = count > 10 && !isPro
                                        FilterChip(
                                            selected = isSelected,
                                            onClick = {
                                                if (isProGated) {
                                                    showProUnlockDialog = true
                                                } else {
                                                    selectedBatch = count
                                                }
                                            },
                                            label = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Text(if (count == 1) "1 Cap" else "$count Caps")
                                                    if (isProGated) {
                                                        Icon(
                                                            imageVector = Icons.Default.Lock,
                                                            contentDescription = "PRO",
                                                            tint = HiddenForest.Amber,
                                                            modifier = Modifier.size(12.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            colors = FilterChipDefaults.filterChipColors(
                                                selectedContainerColor = HiddenForest.AccentContainer,
                                                selectedLabelColor = HiddenForest.Accent,
                                                containerColor = HiddenForest.SurfaceVariant,
                                                labelColor = HiddenForest.Text
                                            ),
                                            border = FilterChipDefaults.filterChipBorder(
                                                enabled = true,
                                                selected = isSelected,
                                                borderColor = HiddenForest.Outline,
                                                selectedBorderColor = HiddenForest.Accent
                                            )
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Start Button
                            Button(
                                onClick = {
                                    val cleanUrl = urlInput.trim()
                                    if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
                                        stage = ScrapingStage.SCRAPING
                                        statusMessage = "Conectando al primer capítulo…"
                                        downloadedChaptersCount = 0
                                        lastChapterTitle = ""
                                        errorMessage = null
                                        currentLoadingUrl = cleanUrl
                                        isProcessingPage = false
                                        emptyRetryCount = 0
                                        webViewRef?.loadUrl(cleanUrl)
                                    } else {
                                        Toast.makeText(context, "Ingresa una URL válida que empiece por https://", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(50.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HiddenForest.Accent,
                                    contentColor = HiddenForest.OnAccent
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Comenzar Descarga ($selectedBatch ${if (selectedBatch == 1) "capítulo" else "capítulos"})",
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    ScrapingStage.SCRAPING -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Progress indicator
                            val progressFloat = (downloadedChaptersCount.toFloat() / selectedBatch.toFloat()).coerceIn(0f, 1f)
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = "Progreso del lote",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = HiddenForest.TextSecondary
                                    )
                                    Text(
                                        text = "$downloadedChaptersCount / $selectedBatch caps",
                                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                        color = HiddenForest.Accent
                                    )
                                }
                                LinearProgressIndicator(
                                    progress = { progressFloat },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(8.dp)
                                        .clip(RoundedCornerShape(4.dp)),
                                    color = HiddenForest.Accent,
                                    trackColor = HiddenForest.Outline
                                )
                            }

                            // Current status card
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = HiddenForest.SurfaceVariant),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        CircularProgressIndicator(
                                            modifier = Modifier.size(16.dp),
                                            color = HiddenForest.Accent,
                                            strokeWidth = 2.dp
                                        )
                                        Text(
                                            text = if (isWaitingJitter) "Pausa humana anti-bloqueo (${currentJitterWaitMs}ms)…" else statusMessage,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = HiddenForest.Text,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    if (lastChapterTitle.isNotBlank()) {
                                        Text(
                                            text = "Último: $lastChapterTitle",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = HiddenForest.TextLow,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }

                            // Toggle WebView button
                            OutlinedButton(
                                onClick = { isWebViewExpanded = !isWebViewExpanded },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HiddenForest.TextSecondary),
                                border = BorderStroke(1.dp, HiddenForest.Outline),
                                shape = RoundedCornerShape(10.dp)
                            ) {
                                Icon(
                                    imageVector = if (isWebViewExpanded) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(if (isWebViewExpanded) "Ocultar Visor Web" else "Ver Visor Web (Bypass / Depuración)")
                            }

                            // Actions: Finalize now or Cancel
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                if (downloadedChaptersCount > 0) {
                                    Button(
                                        onClick = {
                                            epubBuilderRef?.let { builder ->
                                                val ok = builder.finishEpub()
                                                val file = outputFileRef
                                                if (ok && file != null && file.exists()) {
                                                    stage = ScrapingStage.COMPLETED
                                                    onEpubGenerated(file)
                                                }
                                            }
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = HiddenForest.AccentContainer,
                                            contentColor = HiddenForest.Accent
                                        ),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Guardar $downloadedChaptersCount caps")
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        webViewRef?.stopLoading()
                                        epubBuilderRef?.close()
                                        outputFileRef?.delete()
                                        stage = ScrapingStage.IDLE
                                        onDismiss()
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = HiddenForest.TextLow),
                                    border = BorderStroke(1.dp, HiddenForest.Outline),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text("Cancelar")
                                }
                            }
                        }
                    }

                    ScrapingStage.COMPLETED -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Spacer(modifier = Modifier.height(10.dp))
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(HiddenForest.AccentContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = HiddenForest.Accent,
                                    modifier = Modifier.size(36.dp)
                                )
                            }

                            Text(
                                text = "¡Novela Creada con Éxito!",
                                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                                color = HiddenForest.Text,
                                textAlign = TextAlign.Center
                            )

                            Text(
                                text = "Se empaquetaron $downloadedChaptersCount capítulos en formato estándar EPUB 3.0 con consumo de RAM O(1). Ya está disponible en tu Biblioteca listo para leer o escuchar con Woods Studio Voice HD.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HiddenForest.TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Button(
                                onClick = onDismiss,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = HiddenForest.Accent,
                                    contentColor = HiddenForest.OnAccent
                                ),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Abrir en Biblioteca", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    ScrapingStage.ERROR -> {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .verticalScroll(rememberScrollState()),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF3B1E1E)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Error,
                                    contentDescription = null,
                                    tint = Color(0xFFE57373),
                                    modifier = Modifier.size(32.dp)
                                )
                            }

                            Text(
                                text = "Error de Extracción",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = HiddenForest.Text
                            )

                            Text(
                                text = errorMessage ?: "No se pudo continuar con la extracción de los capítulos.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = HiddenForest.TextSecondary,
                                textAlign = TextAlign.Center
                            )

                            if (downloadedChaptersCount > 0) {
                                Button(
                                    onClick = {
                                        epubBuilderRef?.let { builder ->
                                            val ok = builder.finishEpub()
                                            val file = outputFileRef
                                            if (ok && file != null && file.exists()) {
                                                stage = ScrapingStage.COMPLETED
                                                onEpubGenerated(file)
                                            }
                                        }
                                    },
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = HiddenForest.Accent,
                                        contentColor = HiddenForest.OnAccent
                                    ),
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Guardar los $downloadedChaptersCount capítulos descargados")
                                }
                            }

                            OutlinedButton(
                                onClick = {
                                    stage = ScrapingStage.IDLE
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = HiddenForest.Text),
                                border = BorderStroke(1.dp, HiddenForest.Outline),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Text("Volver a Intentar")
                            }
                        }
                    }
                }

                // Expandable/Hidden WebView container
                val webViewHeight = if (isWebViewExpanded || isCloudflareChallenge) 260.dp else 1.dp
                val webViewAlpha = if (isWebViewExpanded || isCloudflareChallenge) 1f else 0.01f

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(webViewHeight)
                        .alpha(webViewAlpha)
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, if (isCloudflareChallenge) HiddenForest.Amber else HiddenForest.Outline, RoundedCornerShape(10.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                layoutParams = android.view.ViewGroup.LayoutParams(
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                                    android.view.ViewGroup.LayoutParams.MATCH_PARENT
                                )
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                                settings.userAgentString = "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36 WoodsReader/1.0"

                                val cookieManager = CookieManager.getInstance()
                                cookieManager.setAcceptCookie(true)
                                cookieManager.setAcceptThirdPartyCookies(this, true)

                                webViewClient = object : WebViewClient() {
                                    override fun onPageFinished(view: WebView?, url: String?) {
                                        super.onPageFinished(view, url)
                                        if (stage == ScrapingStage.SCRAPING && !isProcessingPage && url != null) {
                                            isProcessingPage = true
                                            scope.launch {
                                                // Short pause to allow DOM hydration on dynamic/SPA sites
                                                delay(650)
                                                view?.evaluateJavascript(SCRAPER_JS) { result ->
                                                    scope.launch {
                                                        processScrapedJson(
                                                            rawResult = result,
                                                            currentUrl = url,
                                                            targetCount = selectedBatch,
                                                            context = context,
                                                            scope = scope,
                                                            onUpdateStage = { stage = it },
                                                            onUpdateStatus = { statusMessage = it },
                                                            onUpdateError = { errorMessage = it },
                                                            onUpdateDownloaded = { downloadedChaptersCount = it },
                                                            onUpdateLastTitle = { lastChapterTitle = it },
                                                            onUpdateJitter = { isWaitingJitter = it; currentJitterWaitMs = if (it) currentJitterWaitMs else 0L },
                                                            onUpdateNextUrl = { currentLoadingUrl = it },
                                                            onUpdateChallenge = { isChallenge, expand ->
                                                                isCloudflareChallenge = isChallenge
                                                                if (expand) isWebViewExpanded = true
                                                            },
                                                            onSetBuilder = { epubBuilderRef = it },
                                                            onSetOutputFile = { outputFileRef = it },
                                                            onSetProcessing = { isProcessingPage = it },
                                                            onSetRetry = { emptyRetryCount = it },
                                                            onFinishSuccess = { file ->
                                                                onEpubGenerated(file)
                                                            },
                                                            getBuilder = { epubBuilderRef },
                                                            getOutputFile = { outputFileRef },
                                                            getDownloaded = { downloadedChaptersCount },
                                                            getRetry = { emptyRetryCount },
                                                            getBookTitleInput = { bookTitleInput },
                                                            getWebView = { webViewRef }
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                                webViewRef = this
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
}

private suspend fun processScrapedJson(
    rawResult: String?,
    currentUrl: String,
    targetCount: Int,
    context: Context,
    scope: kotlinx.coroutines.CoroutineScope,
    onUpdateStage: (ScrapingStage) -> Unit,
    onUpdateStatus: (String) -> Unit,
    onUpdateError: (String?) -> Unit,
    onUpdateDownloaded: (Int) -> Unit,
    onUpdateLastTitle: (String) -> Unit,
    onUpdateJitter: (Boolean) -> Unit,
    onUpdateNextUrl: (String) -> Unit,
    onUpdateChallenge: (Boolean, Boolean) -> Unit,
    onSetBuilder: (LocalEpubBuilder) -> Unit,
    onSetOutputFile: (File) -> Unit,
    onSetProcessing: (Boolean) -> Unit,
    onSetRetry: (Int) -> Unit,
    onFinishSuccess: (File) -> Unit,
    getBuilder: () -> LocalEpubBuilder?,
    getOutputFile: () -> File?,
    getDownloaded: () -> Int,
    getRetry: () -> Int,
    getBookTitleInput: () -> String,
    getWebView: () -> WebView?
) {
    val cleanRaw = rawResult?.trim().orEmpty()
    val unquoted = if (cleanRaw.startsWith("\"") && cleanRaw.endsWith("\"")) {
        runCatching {
            JSONTokener(cleanRaw).nextValue().toString()
        }.getOrDefault(cleanRaw.removeSurrounding("\"").replace("\\\"", "\"").replace("\\\\", "\\"))
    } else {
        cleanRaw
    }

    val json = runCatching { JSONObject(unquoted) }.getOrNull()
    if (json == null) {
        val currentRetry = getRetry()
        if (currentRetry < 2) {
            onSetRetry(currentRetry + 1)
            delay(1000)
            getWebView()?.evaluateJavascript(SCRAPER_JS) { res ->
                scope.launch {
                    processScrapedJson(
                        rawResult = res,
                        currentUrl = currentUrl,
                        targetCount = targetCount,
                        context = context,
                        scope = scope,
                        onUpdateStage = onUpdateStage,
                        onUpdateStatus = onUpdateStatus,
                        onUpdateError = onUpdateError,
                        onUpdateDownloaded = onUpdateDownloaded,
                        onUpdateLastTitle = onUpdateLastTitle,
                        onUpdateJitter = onUpdateJitter,
                        onUpdateNextUrl = onUpdateNextUrl,
                        onUpdateChallenge = onUpdateChallenge,
                        onSetBuilder = onSetBuilder,
                        onSetOutputFile = onSetOutputFile,
                        onSetProcessing = onSetProcessing,
                        onSetRetry = onSetRetry,
                        onFinishSuccess = onFinishSuccess,
                        getBuilder = getBuilder,
                        getOutputFile = getOutputFile,
                        getDownloaded = getDownloaded,
                        getRetry = getRetry,
                        getBookTitleInput = getBookTitleInput,
                        getWebView = getWebView
                    )
                }
            }
        } else {
            onSetProcessing(false)
            onUpdateError("No se pudo analizar la estructura de la página.")
            onUpdateStage(ScrapingStage.ERROR)
        }
        return
    }

    if (json.optBoolean("isChallenge", false)) {
        onUpdateChallenge(true, true)
        onUpdateStatus("⚠️ Esperando verificación Cloudflare…")
        onSetProcessing(false)
        return
    }

    onUpdateChallenge(false, false)

    val title = json.optString("title", "").trim()
    val nextUrl = json.optString("nextUrl", "").trim().takeIf { it.isNotBlank() }
    val paragraphsArray = json.optJSONArray("paragraphs") ?: JSONArray()
    val paragraphs = ArrayList<String>(paragraphsArray.length())
    for (i in 0 until paragraphsArray.length()) {
        val p = paragraphsArray.optString(i, "").trim()
        if (p.isNotEmpty()) {
            paragraphs.add(p)
        }
    }

    if (paragraphs.isEmpty()) {
        val currentRetry = getRetry()
        if (currentRetry < 2) {
            onSetRetry(currentRetry + 1)
            onUpdateStatus("Reintentando carga del texto…")
            delay(1200)
            getWebView()?.evaluateJavascript(SCRAPER_JS) { res ->
                scope.launch {
                    processScrapedJson(
                        rawResult = res,
                        currentUrl = currentUrl,
                        targetCount = targetCount,
                        context = context,
                        scope = scope,
                        onUpdateStage = onUpdateStage,
                        onUpdateStatus = onUpdateStatus,
                        onUpdateError = onUpdateError,
                        onUpdateDownloaded = onUpdateDownloaded,
                        onUpdateLastTitle = onUpdateLastTitle,
                        onUpdateJitter = onUpdateJitter,
                        onUpdateNextUrl = onUpdateNextUrl,
                        onUpdateChallenge = onUpdateChallenge,
                        onSetBuilder = onSetBuilder,
                        onSetOutputFile = onSetOutputFile,
                        onSetProcessing = onSetProcessing,
                        onSetRetry = onSetRetry,
                        onFinishSuccess = onFinishSuccess,
                        getBuilder = getBuilder,
                        getOutputFile = getOutputFile,
                        getDownloaded = getDownloaded,
                        getRetry = getRetry,
                        getBookTitleInput = getBookTitleInput,
                        getWebView = getWebView
                    )
                }
            }
        } else {
            onSetProcessing(false)
            if (getDownloaded() > 0) {
                // We reached the end of novel or unparsable page, finalize
                val builder = getBuilder()
                val outputFile = getOutputFile()
                if (builder != null && outputFile != null) {
                    val ok = builder.finishEpub()
                    if (ok && outputFile.exists()) {
                        onUpdateStage(ScrapingStage.COMPLETED)
                        onFinishSuccess(outputFile)
                        return
                    }
                }
            }
            onUpdateError("No se encontraron párrafos de texto en esta página.")
            onUpdateStage(ScrapingStage.ERROR)
        }
        return
    }

    // Reset retry count on success
    onSetRetry(0)

    // Ensure LocalEpubBuilder is initialized
    var builder = getBuilder()
    if (builder == null) {
        val outputDir = File(context.getExternalFilesDir(null) ?: context.filesDir, "WebNovels").apply { mkdirs() }
        val manualTitle = getBookTitleInput().trim()
        val resolvedBookTitle = if (manualTitle.isNotBlank()) manualTitle else title.ifBlank { "Novela_Web" }
        val safeFileName = resolvedBookTitle
            .replace(Regex("[^a-zA-Z0-9áéíóúÁÉÍÓÚñÑüÜ_ -]"), "_")
            .take(35)
        val file = File(outputDir, "${safeFileName}_${System.currentTimeMillis()}.epub")
        val newBuilder = LocalEpubBuilder(
            outputFile = file,
            bookTitle = resolvedBookTitle,
            bookAuthor = "Web Novel",
            languageCode = "es"
        )
        onSetBuilder(newBuilder)
        onSetOutputFile(file)
        builder = newBuilder
    }

    // Append chapter
    builder.appendChapter(
        rawTitle = title,
        paragraphs = paragraphs,
        sourceUrl = currentUrl
    )

    val currentDownloaded = getDownloaded() + 1
    onUpdateDownloaded(currentDownloaded)
    onUpdateLastTitle(title)
    onUpdateStatus("Capítulo $currentDownloaded de $targetCount descargado")

    // Check completion condition
    if (currentDownloaded >= targetCount || nextUrl.isNullOrBlank()) {
        val ok = builder.finishEpub()
        val file = getOutputFile()
        if (ok && file != null && file.exists()) {
            onUpdateStage(ScrapingStage.COMPLETED)
            onFinishSuccess(file)
        } else {
            onUpdateError("Error al escribir el archivo EPUB en disco.")
            onUpdateStage(ScrapingStage.ERROR)
        }
        onSetProcessing(false)
        return
    }

    // Human jitter before loading next chapter: 950ms .. 1350ms
    val jitter = (950..1350).random().toLong()
    onUpdateJitter(true)
    delay(jitter)
    onUpdateJitter(false)

    // Load next URL
    onUpdateNextUrl(nextUrl)
    onUpdateStatus("Cargando capítulo ${currentDownloaded + 1}…")
    onSetProcessing(false)
    getWebView()?.loadUrl(nextUrl)
}
