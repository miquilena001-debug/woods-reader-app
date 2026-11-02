package com.example.woodsreader.core.web2epub

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import com.example.woodsreader.R
import com.example.woodsreader.core.license.ProLicenseManager
import com.example.woodsreader.core.web2epub.novel.*
import com.example.woodsreader.ui.common.WoodsProUnlockDialog
import com.example.woodsreader.ui.theme.HiddenForest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import com.example.woodsreader.core.translation.calibre.CalibreStyleEpubTranslator
import com.example.woodsreader.ui.library.TARGET_LANGUAGES
import com.example.woodsreader.ui.library.TargetLanguageRow
import com.example.woodsreader.ui.library.languageByCode
import java.io.File

enum class ImporterUiStage {
    INPUT,
    LOADING_INDEX,
    PREVIEW,
    DOWNLOADING,
    TRANSLATING,
    COMPLETED,
    ERROR
}

@Composable
fun WebToEpubImporterDialog(
    proLicenseManager: ProLicenseManager,
    calibreTranslator: CalibreStyleEpubTranslator? = null,
    onDismiss: () -> Unit,
    onEpubGenerated: (File) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var isMinimized by remember { mutableStateOf(false) }

    var stage by remember { mutableStateOf(ImporterUiStage.INPUT) }
    var urlInput by remember { mutableStateOf("") }
    var statusMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    var novelIndex by remember { mutableStateOf<NovelIndex?>(null) }
    var startIndexText by remember { mutableStateOf("1") }
    var endIndexText by remember { mutableStateOf("") }
    var isReverseToc by remember { mutableStateOf(false) }

    var currentProgress by remember { mutableStateOf(0) }
    var totalProgress by remember { mutableStateOf(0) }
    var currentChapterTitle by remember { mutableStateOf("") }
    var generatedEpubFile by remember { mutableStateOf<File?>(null) }

    // ── Traducción automática ──────────────────────────────────────────────
    var autoTranslate       by remember { mutableStateOf(false) }
    var translateTargetLang by remember { mutableStateOf("es") }
    var translationStatusMsg by remember { mutableStateOf("") }
    var translationProgress  by remember { mutableStateOf<com.example.woodsreader.core.translation.calibre.EpubTranslationProgress?>(null) }

    // Cloudflare Challenge WebView State
    var isCloudflareChallenge by remember { mutableStateOf(false) }
    var webViewRef by remember { mutableStateOf<WebView?>(null) }
    var showProDialog by remember { mutableStateOf(false) }

    val isPro by proLicenseManager.isProUnlocked.collectAsState()

    // Preparar dependencias del importador
    val adapterRegistry = remember { SiteProfileLoader(context).loadRegistry() }
    val okHttpClient = remember {
        OkHttpClient.Builder()
            .callTimeout(40, java.util.concurrent.TimeUnit.SECONDS)
            .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
            .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .build()
    }
    val fetcher = remember {
        val pacer = RequestPacer(minMs = 1500L, maxMs = 3000L)
        OkHttpPageFetcher(client = okHttpClient, pacer = pacer)
    }
    val imageDownloader = remember { ImageDownloader(okHttpClient) }
    val baseStoreDir = remember { File(context.cacheDir, "web2epub").apply { if (!exists()) mkdirs() } }
    val store = remember { ImportStore(baseStoreDir) }
    val importer = remember {
        NovelImporter(
            fetcher = fetcher,
            adapterRegistry = adapterRegistry,
            store = store,
            imageDownloader = imageDownloader
        )
    }

    if (isMinimized) {
        // ── Widget flotante minimizado (Apple Liquid Glass Dock) ──
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 96.dp, start = 16.dp, end = 16.dp),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(
                        elevation = 24.dp,
                        shape = RoundedCornerShape(22.dp),
                        ambientColor = Color.Black.copy(alpha = 0.75f),
                        spotColor = HiddenForest.Accent.copy(alpha = 0.35f)
                    )
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xF2182E22),
                                Color(0xF8102118),
                                Color(0xFC0B1510)
                            )
                        )
                    )
                    .border(
                        width = 1.25.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.38f),
                                HiddenForest.Accent.copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.06f)
                            )
                        ),
                        shape = RoundedCornerShape(22.dp)
                    )
                    .clickable { isMinimized = false }
                    .padding(horizontal = 14.dp, vertical = 10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Squircle con icono según estado
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.18f), RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = when (stage) {
                                ImporterUiStage.DOWNLOADING -> Icons.Default.CloudDownload
                                ImporterUiStage.TRANSLATING -> Icons.Default.Translate
                                ImporterUiStage.COMPLETED -> Icons.Default.CheckCircle
                                ImporterUiStage.ERROR -> Icons.Default.Error
                                else -> Icons.Default.Public
                            },
                            contentDescription = null,
                            tint = if (stage == ImporterUiStage.ERROR) Color(0xFFF87171) else HiddenForest.Accent,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Título y barra de progreso
                    Column(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = when (stage) {
                                    ImporterUiStage.DOWNLOADING -> "Descargando novela…"
                                    ImporterUiStage.TRANSLATING -> "Traduciendo EPUB…"
                                    ImporterUiStage.COMPLETED -> "¡EPUB listo para leer!"
                                    ImporterUiStage.ERROR -> "Error en importación"
                                    else -> "Conversor Web a EPUB"
                                },
                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            if (stage == ImporterUiStage.DOWNLOADING && totalProgress > 0) {
                                Text(
                                    text = "${((currentProgress.toFloat() / totalProgress.toFloat()).coerceIn(0f, 1f) * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = HiddenForest.Accent
                                )
                            } else if (stage == ImporterUiStage.TRANSLATING && translationProgress != null) {
                                Text(
                                    text = "${((translationProgress?.progressFraction ?: 0f) * 100).toInt()}%",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                    color = HiddenForest.Accent
                                )
                            }
                        }

                        Text(
                            text = when (stage) {
                                ImporterUiStage.DOWNLOADING -> if (totalProgress > 0) "Capítulo $currentProgress de $totalProgress" else statusMessage
                                ImporterUiStage.TRANSLATING -> translationStatusMsg.ifBlank { "Procesando capítulos con Google Translate..." }
                                ImporterUiStage.COMPLETED -> generatedEpubFile?.name ?: "Toca para abrir"
                                ImporterUiStage.ERROR -> errorMessage ?: "Toca para ver detalles"
                                else -> "Toca para reanudar"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = HiddenForest.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            fontSize = 11.sp
                        )

                        if (stage == ImporterUiStage.DOWNLOADING || stage == ImporterUiStage.TRANSLATING) {
                            val p = if (stage == ImporterUiStage.DOWNLOADING) {
                                if (totalProgress > 0) (currentProgress.toFloat() / totalProgress.toFloat()).coerceIn(0f, 1f) else 0f
                            } else {
                                translationProgress?.progressFraction ?: 0.05f
                            }
                            LinearProgressIndicator(
                                progress = { p },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp)),
                                color = HiddenForest.Accent,
                                trackColor = Color.White.copy(alpha = 0.08f)
                            )
                        }
                    }

                    // Botones de acción: Maximizar y Cerrar
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(CircleShape)
                                .background(Color.White.copy(alpha = 0.08f))
                                .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                                .clickable { isMinimized = false },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.OpenInFull,
                                contentDescription = "Maximizar",
                                tint = Color.White.copy(alpha = 0.85f),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        if (stage != ImporterUiStage.DOWNLOADING && stage != ImporterUiStage.TRANSLATING) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.16f), CircleShape)
                                    .clickable { onDismiss() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    } else {
        // ── Diálogo Modal Completo (Apple Liquid Glass) ──
        Dialog(
            onDismissRequest = {
                if (stage != ImporterUiStage.DOWNLOADING && stage != ImporterUiStage.TRANSLATING) {
                    onDismiss()
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.94f)
                    .fillMaxHeight(0.88f)
                    .shadow(
                        elevation = 32.dp,
                        shape = RoundedCornerShape(28.dp),
                        ambientColor = Color.Black.copy(alpha = 0.75f),
                        spotColor = HiddenForest.Accent.copy(alpha = 0.35f)
                    )
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                Color(0xF0182E22),
                                Color(0xF6102118),
                                Color(0xFB0B1510)
                            )
                        )
                    )
                    .border(
                        width = 1.25.dp,
                        brush = Brush.verticalGradient(
                            listOf(
                                Color.White.copy(alpha = 0.38f),
                                HiddenForest.Accent.copy(alpha = 0.25f),
                                Color.White.copy(alpha = 0.06f)
                            )
                        ),
                        shape = RoundedCornerShape(28.dp)
                    )
                    .padding(22.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Header (Apple Liquid Glass con Minimizar y Cerrar)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(RoundedCornerShape(13.dp))
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(
                                        1.dp,
                                        Brush.verticalGradient(
                                            listOf(Color.White.copy(alpha = 0.25f), Color.White.copy(alpha = 0.05f))
                                        ),
                                        RoundedCornerShape(13.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Public,
                                    contentDescription = null,
                                    tint = HiddenForest.Accent,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Column {
                                Text(
                                    text = "Web a EPUB 3",
                                    style = MaterialTheme.typography.titleMedium.copy(
                                        fontWeight = FontWeight.Bold,
                                        letterSpacing = 0.2.sp
                                    ),
                                    color = Color.White
                                )
                                Text(
                                    text = "Conversor inteligente de novelas web",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = HiddenForest.TextSecondary
                                )
                            }
                        }

                        // Botones superiores: Minimizar y Cerrar
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Botón circular Apple Glass para minimizar
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.08f))
                                    .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                    .clickable { isMinimized = true },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.KeyboardArrowDown,
                                    contentDescription = "Minimizar",
                                    tint = Color.White.copy(alpha = 0.85f),
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            if (stage != ImporterUiStage.DOWNLOADING && stage != ImporterUiStage.TRANSLATING) {
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(1.dp, Color.White.copy(alpha = 0.18f), CircleShape)
                                        .clickable { onDismiss() },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Close,
                                        contentDescription = "Cerrar",
                                        tint = Color.White.copy(alpha = 0.85f),
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                    // Contenido principal según Stage
                    when (stage) {
                        ImporterUiStage.INPUT -> {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Text(
                                    text = "Ingresa la URL del índice de la novela o de un capítulo:",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )

                                OutlinedTextField(
                                    value = urlInput,
                                    onValueChange = { urlInput = it },
                                    label = { Text("URL de la Novela") },
                                    placeholder = { Text("https://novelfire.net/book/...") },
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
                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                        focusedLabelColor = HiddenForest.Accent,
                                        unfocusedLabelColor = Color.White.copy(alpha = 0.5f),
                                        focusedTextColor = Color.White,
                                        unfocusedTextColor = Color.White.copy(alpha = 0.9f),
                                        focusedContainerColor = Color.White.copy(alpha = 0.04f),
                                        unfocusedContainerColor = Color.White.copy(alpha = 0.02f)
                                    ),
                                    shape = RoundedCornerShape(14.dp)
                                )

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .shadow(
                                            elevation = 8.dp,
                                            shape = RoundedCornerShape(14.dp),
                                            ambientColor = Color.Black.copy(alpha = 0.4f),
                                            spotColor = Color(0xFF10B981).copy(alpha = 0.5f)
                                        )
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color(0xFF34D399),
                                                    Color(0xFF10B981),
                                                    Color(0xFF059669)
                                                )
                                            )
                                        )
                                        .border(1.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(14.dp))
                                        .clickable {
                                            val cleanUrl = urlInput.trim()
                                            if (cleanUrl.startsWith("http://") || cleanUrl.startsWith("https://")) {
                                                stage = ImporterUiStage.LOADING_INDEX
                                                statusMessage = "Analizando novela e índice de capítulos…"
                                                errorMessage = null

                                                scope.launch {
                                                    try {
                                                        val cleanWithoutQuery = cleanUrl.substringBefore("?")
                                                        val resolvedUrl = if (cleanWithoutQuery.contains("/chapter-", ignoreCase = true)) {
                                                            cleanWithoutQuery.substringBeforeLast("/chapter-")
                                                        } else {
                                                            cleanWithoutQuery
                                                        }

                                                        val adapter = adapterRegistry.forUrl(resolvedUrl)
                                                        val normalizedUrl = adapter.normalizeUrl(resolvedUrl)
                                                        val indexUrl = adapter.indexUrl(normalizedUrl)
                                                        val index = withContext(Dispatchers.IO) {
                                                            adapter.parseIndex("", indexUrl, fetcher)
                                                        }
                                                        novelIndex = index
                                                        startIndexText = "1"
                                                        endIndexText = if (!isPro && index.chapters.size > 10) "10" else index.chapters.size.toString()
                                                        isReverseToc = index.looksReversed
                                                        stage = ImporterUiStage.PREVIEW
                                                    } catch (c: FetchFailure.Challenge) {
                                                        isCloudflareChallenge = true
                                                        stage = ImporterUiStage.ERROR
                                                        errorMessage = WebImportErrorFormatter.format(c)
                                                    } catch (e: Exception) {
                                                        stage = ImporterUiStage.ERROR
                                                        errorMessage = WebImportErrorFormatter.format(e)
                                                    }
                                                }
                                            } else {
                                                Toast.makeText(context, "Ingresa una URL válida que empiece por https://", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Search,
                                            contentDescription = null,
                                            tint = Color(0xFF042414),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = "Cargar Novela",
                                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                            color = Color(0xFF042414)
                                        )
                                    }
                                }
                            }
                        }

                        ImporterUiStage.LOADING_INDEX -> {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    CircularProgressIndicator(color = HiddenForest.Accent)
                                    Text(
                                        text = statusMessage,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color.White.copy(alpha = 0.85f),
                                        textAlign = TextAlign.Center
                                    )
                                }
                            }
                        }

                        ImporterUiStage.PREVIEW -> {
                            val index = novelIndex
                            if (index != null) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .verticalScroll(rememberScrollState()),
                                    verticalArrangement = Arrangement.spacedBy(14.dp)
                                ) {
                                    // Tarjeta de detalles de la novela (Apple Glass Card)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(18.dp))
                                            .background(Color.White.copy(alpha = 0.04f))
                                            .border(
                                                1.dp,
                                                Brush.verticalGradient(
                                                    listOf(Color.White.copy(alpha = 0.14f), Color.White.copy(alpha = 0.03f))
                                                ),
                                                RoundedCornerShape(18.dp)
                                            )
                                            .padding(14.dp)
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                            verticalAlignment = Alignment.Top
                                        ) {
                                            if (!index.coverUrl.isNullOrBlank()) {
                                                AsyncImage(
                                                    model = index.coverUrl,
                                                    contentDescription = "Portada",
                                                    modifier = Modifier
                                                        .width(85.dp)
                                                        .height(125.dp)
                                                        .clip(RoundedCornerShape(10.dp))
                                                        .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(10.dp))
                                                )
                                            }

                                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    text = index.title,
                                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                                    color = Color.White,
                                                    maxLines = 3,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                Text(
                                                    text = "Autor: ${index.author ?: "Desconocido"}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = HiddenForest.TextSecondary
                                                )

                                                Box(
                                                    modifier = Modifier
                                                        .clip(RoundedCornerShape(6.dp))
                                                    .background(HiddenForest.Accent.copy(alpha = 0.16f))
                                                    .border(1.dp, HiddenForest.Accent.copy(alpha = 0.35f), RoundedCornerShape(6.dp))
                                                    .padding(horizontal = 8.dp, vertical = 3.dp)
                                                ) {
                                                    Text(
                                                        text = "Capítulos: ${index.chapters.size}",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 10.sp
                                                        ),
                                                        color = HiddenForest.Accent
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (!index.description.isNullOrBlank()) {
                                        Text(
                                            text = index.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = Color.White.copy(alpha = 0.70f),
                                            maxLines = 3,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }

                                    HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                                    // Selectores de Rango en Apple Glass Card
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(Color.White.copy(alpha = 0.035f))
                                            .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                                            .padding(12.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                            Text(
                                                text = "Rango de capítulos a descargar:",
                                                style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                                color = Color.White.copy(alpha = 0.85f)
                                            )

                                            Row(
                                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                OutlinedTextField(
                                                    value = startIndexText,
                                                    onValueChange = { startIndexText = it },
                                                    label = { Text("Desde") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.weight(1f),
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = HiddenForest.Accent,
                                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                                        focusedTextColor = Color.White,
                                                        unfocusedTextColor = Color.White.copy(alpha = 0.9f)
                                                    )
                                                )
                                                OutlinedTextField(
                                                    value = endIndexText,
                                                    onValueChange = { endIndexText = it },
                                                    label = { Text("Hasta") },
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                                    modifier = Modifier.weight(1f),
                                                    singleLine = true,
                                                    shape = RoundedCornerShape(12.dp),
                                                    colors = OutlinedTextFieldDefaults.colors(
                                                        focusedBorderColor = HiddenForest.Accent,
                                                        unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                                                        focusedTextColor = Color.White,
                                                        unfocusedTextColor = Color.White.copy(alpha = 0.9f)
                                                    )
                                                )
                                            }
                                        }
                                    }

                                    // Invertir orden TOC (Apple Glass Pill)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(12.dp))
                                            .background(Color.White.copy(alpha = 0.03f))
                                            .border(1.dp, Color.White.copy(alpha = 0.07f), RoundedCornerShape(12.dp))
                                            .clickable { isReverseToc = !isReverseToc }
                                            .padding(horizontal = 10.dp, vertical = 6.dp)
                                    ) {
                                        Checkbox(
                                            checked = isReverseToc,
                                            onCheckedChange = { isReverseToc = it },
                                            colors = CheckboxDefaults.colors(
                                                checkedColor = HiddenForest.Accent,
                                                uncheckedColor = Color.White.copy(alpha = 0.4f)
                                            )
                                        )
                                        Text(
                                            text = "Invertir orden (TOC descendente)",
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    }

                                    // ── Panel de Auto-Traducción (Apple Glass Card) ──
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(
                                                if (autoTranslate) HiddenForest.Accent.copy(alpha = 0.14f) else Color.White.copy(alpha = 0.035f)
                                            )
                                            .border(
                                                1.dp,
                                                if (autoTranslate) HiddenForest.Accent.copy(alpha = 0.40f) else Color.White.copy(alpha = 0.08f),
                                                RoundedCornerShape(16.dp)
                                            )
                                            .padding(12.dp)
                                    ) {
                                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                            Row(
                                                verticalAlignment = Alignment.CenterVertically,
                                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .clickable { autoTranslate = !autoTranslate }
                                            ) {
                                                Checkbox(
                                                    checked = autoTranslate,
                                                    onCheckedChange = { autoTranslate = it },
                                                    colors = CheckboxDefaults.colors(
                                                        checkedColor = HiddenForest.Accent,
                                                        uncheckedColor = Color.White.copy(alpha = 0.4f)
                                                    )
                                                )
                                                Column {
                                                    Text(
                                                        text = "Traducir automáticamente al terminar",
                                                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                                                        color = if (autoTranslate) HiddenForest.Accent else Color.White
                                                    )
                                                    Text(
                                                        text = "Google Translate · Sin API Key · Gratis",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = HiddenForest.TextSecondary
                                                    )
                                                }
                                            }

                                            if (autoTranslate) {
                                                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                                    Text(
                                                        text = "Idioma de destino:",
                                                        style = MaterialTheme.typography.labelSmall.copy(
                                                            fontWeight = FontWeight.Bold,
                                                            fontSize = 9.sp,
                                                            letterSpacing = 0.6.sp
                                                        ),
                                                        color = Color.White.copy(alpha = 0.7f)
                                                    )
                                                    TargetLanguageRow(
                                                        selectedCode = translateTargetLang,
                                                        onSelect = { translateTargetLang = it },
                                                        modifier = Modifier.fillMaxWidth()
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    if (!isPro) {
                                        Text(
                                            text = "⚡ Versión gratuita limitada a los primeros 10 capítulos.",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = HiddenForest.Accent
                                        )
                                    }

                                    Spacer(modifier = Modifier.height(4.dp))

                                    // Botón Descargar (Apple Liquid Emerald CTA)
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .shadow(
                                                elevation = 8.dp,
                                                shape = RoundedCornerShape(14.dp),
                                                ambientColor = Color.Black.copy(alpha = 0.4f),
                                                spotColor = Color(0xFF10B981).copy(alpha = 0.5f)
                                            )
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(
                                                        Color(0xFF34D399),
                                                        Color(0xFF10B981),
                                                        Color(0xFF059669)
                                                    )
                                                )
                                            )
                                            .border(1.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(14.dp))
                                            .clickable {
                                                val s = (startIndexText.toIntOrNull() ?: 1) - 1
                                                val e = endIndexText.toIntOrNull() ?: index.chapters.size

                                                if (!isPro && (e - s) > 10) {
                                                    showProDialog = true
                                                    return@clickable
                                                }

                                                stage = ImporterUiStage.DOWNLOADING
                                                statusMessage = "Iniciando descarga..."
                                                currentProgress = 0
                                                totalProgress = (e - s).coerceAtLeast(1)

                                                scope.launch {
                                                    val progressJob = launch {
                                                        importer.progressFlow.collectLatest { p ->
                                                            currentProgress = p.currentChapter
                                                            totalProgress = p.totalChapters
                                                            currentChapterTitle = p.currentChapterTitle
                                                            statusMessage = when (p.stage) {
                                                                ImportStage.FETCHING_CHAPTERS -> "Descargando capítulo $currentProgress de $totalProgress…"
                                                                ImportStage.DOWNLOADING_IMAGES -> "Descargando imágenes…"
                                                                ImportStage.ASSEMBLING_EPUB -> "Ensamblando archivo EPUB 3…"
                                                                else -> statusMessage
                                                            }
                                                        }
                                                    }

                                                    val outputDir = try {
                                                        val docs = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOCUMENTS)
                                                        val dir = File(docs, "WoodsReader/Epubs")
                                                        if (!dir.exists()) dir.mkdirs()
                                                        dir
                                                    } catch (_: Exception) {
                                                        File(context.getExternalFilesDir(null) ?: context.filesDir, "WebNovels").apply { mkdirs() }
                                                    }
                                                    val resultFile = withContext(Dispatchers.IO) {
                                                        importer.importNovel(
                                                            novelUrl = index.indexUrl,
                                                            outputDirectory = outputDir,
                                                            startIndex = s,
                                                            endIndex = e,
                                                            forceReverse = isReverseToc
                                                        )
                                                    }
                                                    progressJob.cancel()

                                                    if (resultFile != null && resultFile.exists()) {
                                                        if (autoTranslate && calibreTranslator != null) {
                                                            stage = ImporterUiStage.TRANSLATING
                                                            translationStatusMsg = "Iniciando traducción con Google Translate..."
                                                            val transProgressJob = launch {
                                                                calibreTranslator.progressState.collectLatest { prog ->
                                                                    translationProgress = prog
                                                                    translationStatusMsg = prog.currentStep
                                                                }
                                                            }
                                                            val transResult = withContext(Dispatchers.IO) {
                                                                calibreTranslator.translateEntireEpub(
                                                                    sourceEpubFile = resultFile,
                                                                    sourceLanguageCode = index.language ?: "auto",
                                                                    targetLanguageCode = translateTargetLang
                                                                )
                                                            }
                                                            transProgressJob.cancel()
                                                            if (transResult.isSuccess) {
                                                                generatedEpubFile = transResult.getOrNull() ?: resultFile
                                                                stage = ImporterUiStage.COMPLETED
                                                            } else {
                                                                generatedEpubFile = resultFile
                                                                Toast.makeText(
                                                                    context,
                                                                    "EPUB descargado, pero falló la traducción: ${transResult.exceptionOrNull()?.message}",
                                                                    Toast.LENGTH_LONG
                                                                ).show()
                                                                stage = ImporterUiStage.COMPLETED
                                                            }
                                                        } else {
                                                            generatedEpubFile = resultFile
                                                            stage = ImporterUiStage.COMPLETED
                                                        }
                                                    } else {
                                                        val jobState = store.loadState(ImportStore.computeJobId(index.indexUrl))
                                                        if (jobState?.status == JobStatus.PAUSED_CHALLENGE) {
                                                            isCloudflareChallenge = true
                                                            errorMessage = "Cloudflare ha solicitado verificación de navegador. Resuelve el captcha para continuar."
                                                        } else {
                                                            errorMessage = "La descarga fue interrumpida o se produjo un error de conexión."
                                                        }
                                                        stage = ImporterUiStage.ERROR
                                                    }
                                                }
                                            },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.Center
                                        ) {
                                            Icon(Icons.Default.CloudDownload, contentDescription = null, tint = Color(0xFF042414))
                                            Spacer(modifier = Modifier.width(8.dp))
                                            Text(
                                                text = "Comenzar Descarga EPUB 3",
                                                style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                                color = Color(0xFF042414)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        ImporterUiStage.DOWNLOADING -> {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(modifier = Modifier.height(20.dp))
                                val progressFloat = if (totalProgress > 0) (currentProgress.toFloat() / totalProgress.toFloat()).coerceIn(0f, 1f) else 0f

                                // Tarjeta de progreso Apple Glass
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color.White.copy(alpha = 0.04f))
                                        .border(
                                            1.dp,
                                            Brush.verticalGradient(
                                                listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f))
                                            ),
                                            RoundedCornerShape(18.dp)
                                        )
                                        .padding(16.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        LinearProgressIndicator(
                                            progress = { progressFloat },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = HiddenForest.Accent,
                                            trackColor = Color.White.copy(alpha = 0.08f)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "Descargando novela…",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.75f)
                                            )
                                            Text(
                                                text = "${(progressFloat * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = HiddenForest.Accent
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = statusMessage,
                                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium),
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )

                                if (currentChapterTitle.isNotBlank()) {
                                    Text(
                                        text = currentChapterTitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HiddenForest.TextSecondary,
                                        textAlign = TextAlign.Center,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                // Botón para minimizar mientras descarga
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                                        .clickable { isMinimized = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Minimizar Descarga",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }

                        ImporterUiStage.TRANSLATING -> {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Spacer(modifier = Modifier.height(20.dp))
                                val prog = translationProgress
                                val progressFloat = prog?.progressFraction ?: 0.05f

                                // Tarjeta de progreso de traducción (Apple Glass)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(18.dp))
                                        .background(Color.White.copy(alpha = 0.04f))
                                        .border(
                                            1.dp,
                                            Brush.verticalGradient(
                                                listOf(Color.White.copy(alpha = 0.12f), Color.White.copy(alpha = 0.03f))
                                            ),
                                            RoundedCornerShape(18.dp)
                                        )
                                        .padding(16.dp)
                                ) {
                                    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                        LinearProgressIndicator(
                                            progress = { progressFloat },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .height(8.dp)
                                                .clip(RoundedCornerShape(4.dp)),
                                            color = HiddenForest.Accent,
                                            trackColor = Color.White.copy(alpha = 0.08f)
                                        )

                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = if (prog != null && prog.totalChapters > 0) {
                                                    "Capítulo ${prog.currentChapter} de ${prog.totalChapters}"
                                                } else {
                                                    "Traduciendo con Google Translate…"
                                                },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = Color.White.copy(alpha = 0.75f)
                                            )
                                            Text(
                                                text = "${(progressFloat * 100).toInt()}%",
                                                style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.Bold),
                                                color = HiddenForest.Accent
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = "🌐 Traducción Masiva en Marcha",
                                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = translationStatusMsg.ifBlank { "Procesando capítulos con Google Translate..." },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = HiddenForest.TextSecondary,
                                    textAlign = TextAlign.Center
                                )

                                Text(
                                    text = "El EPUB final se ensamblará con cada capítulo traducido al español.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.5f),
                                    textAlign = TextAlign.Center
                                )

                                // Botón para minimizar mientras traduce
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(44.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                                        .clickable { isMinimized = true },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.KeyboardArrowDown,
                                            contentDescription = null,
                                            tint = Color.White.copy(alpha = 0.85f),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(Modifier.width(6.dp))
                                        Text(
                                            text = "Minimizar Traducción",
                                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.SemiBold),
                                            color = Color.White.copy(alpha = 0.85f)
                                        )
                                    }
                                }
                            }
                        }

                        ImporterUiStage.COMPLETED -> {
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
                                        .background(HiddenForest.Accent.copy(alpha = 0.20f))
                                        .border(1.5.dp, HiddenForest.Accent, CircleShape),
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
                                    text = "¡Libro Generado con Éxito!",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )

                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(Color.White.copy(alpha = 0.05f))
                                        .border(1.dp, Color.White.copy(alpha = 0.10f), RoundedCornerShape(10.dp))
                                        .padding(horizontal = 14.dp, vertical = 6.dp)
                                ) {
                                    Text(
                                        text = generatedEpubFile?.name ?: "Novela.epub",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HiddenForest.TextSecondary
                                    )
                                }

                                // Botón "Abrir en Woods Reader" (Apple Liquid Emerald CTA)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .shadow(
                                            elevation = 8.dp,
                                            shape = RoundedCornerShape(14.dp),
                                            ambientColor = Color.Black.copy(alpha = 0.4f),
                                            spotColor = Color(0xFF10B981).copy(alpha = 0.5f)
                                        )
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(
                                            Brush.horizontalGradient(
                                                listOf(
                                                    Color(0xFF34D399),
                                                    Color(0xFF10B981),
                                                    Color(0xFF059669)
                                                )
                                            )
                                        )
                                        .border(1.dp, Color.White.copy(alpha = 0.40f), RoundedCornerShape(14.dp))
                                        .clickable {
                                            val file = generatedEpubFile
                                            if (file != null && file.exists()) {
                                                onEpubGenerated(file)
                                                onDismiss()
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Abrir en Woods Reader",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                        color = Color(0xFF042414)
                                    )
                                }
                            }
                        }

                        ImporterUiStage.ERROR -> {
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .verticalScroll(rememberScrollState()),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(56.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF3B1E1E).copy(alpha = 0.6f))
                                        .border(1.5.dp, Color(0xFFEF4444).copy(alpha = 0.4f), CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Error,
                                        contentDescription = null,
                                        tint = Color(0xFFF87171),
                                        modifier = Modifier.size(32.dp)
                                    )
                                }

                                Text(
                                    text = "Error de Descarga",
                                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                    color = Color.White
                                )

                                Text(
                                    text = errorMessage ?: "Ocurrió un error inesperado al procesar la novela.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color.White.copy(alpha = 0.8f),
                                    textAlign = TextAlign.Center
                                )

                                if (isCloudflareChallenge) {
                                    Text(
                                        text = "Resuelve el captcha abajo para continuar:",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = HiddenForest.Accent
                                    )

                                    AndroidView(
                                        factory = { ctx ->
                                            WebView(ctx).apply {
                                                settings.javaScriptEnabled = true
                                                settings.domStorageEnabled = true
                                                settings.userAgentString = WEB_UA
                                                CookieManager.getInstance().setAcceptCookie(true)
                                                CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                                                webViewClient = object : WebViewClient() {
                                                    override fun onPageFinished(view: WebView?, url: String?) {
                                                        super.onPageFinished(view, url)
                                                        view?.evaluateJavascript("document.title") { t ->
                                                            if (t != null && !t.contains("Just a moment", ignoreCase = true)) {
                                                                isCloudflareChallenge = false
                                                            }
                                                        }
                                                    }
                                                }
                                                loadUrl(urlInput.trim())
                                                webViewRef = this
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(200.dp)
                                            .clip(RoundedCornerShape(12.dp))
                                            .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(12.dp))
                                    )

                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(46.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(HiddenForest.Accent)
                                            .clickable { stage = ImporterUiStage.PREVIEW },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "Captcha resuelto · Reanudar Descarga",
                                            fontWeight = FontWeight.Bold,
                                            color = HiddenForest.OnAccent
                                        )
                                    }
                                }

                                // Botón Reintentar (Frosted Glass)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(46.dp)
                                        .clip(RoundedCornerShape(14.dp))
                                        .background(Color.White.copy(alpha = 0.08f))
                                        .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                                        .clickable { stage = ImporterUiStage.INPUT },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = "Reintentar con otra URL",
                                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                                        color = Color.White.copy(alpha = 0.9f)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showProDialog) {
        WoodsProUnlockDialog(
            proLicenseManager = proLicenseManager,
            onDismiss = { showProDialog = false }
        )
    }
}
