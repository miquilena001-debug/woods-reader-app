package com.example.woodsreader.ui.reader

import android.app.Activity
import android.content.ClipData
import android.content.Context
import android.content.ContextWrapper
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import android.util.Log
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.border
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.focusable
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.key.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.res.stringResource
import org.readium.r2.navigator.epub.css.RsProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.viewinterop.AndroidViewBinding
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.compose.ui.zIndex
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import com.example.woodsreader.R
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Close
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.ui.viewinterop.AndroidView
import android.content.Intent
import android.net.Uri
import kotlinx.coroutines.Job
import com.example.woodsreader.databinding.FragmentReaderContainerBinding
import com.example.woodsreader.core.managers.InlineLookupManager
import com.example.woodsreader.core.managers.LookupResult
import com.example.woodsreader.core.xray.ChapterXRayReport
import com.example.woodsreader.ui.theme.HiddenForest
import com.example.woodsreader.model.TtsHighlightStyle
import com.example.woodsreader.ui.theme.LocalHighlightColors
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import androidx.compose.foundation.interaction.collectIsPressedAsState
import org.readium.r2.navigator.epub.EpubNavigatorFactory
import org.readium.r2.navigator.epub.EpubNavigatorFragment
import org.readium.r2.navigator.input.InputListener
import org.readium.r2.navigator.input.TapEvent
import org.readium.r2.navigator.pdf.PdfNavigatorFragment
import org.readium.r2.navigator.SelectableNavigator
import org.readium.r2.navigator.Selection
import org.readium.r2.navigator.VisualNavigator
import org.readium.r2.navigator.DecorableNavigator
import org.readium.r2.navigator.Decoration
import org.readium.r2.navigator.Navigator
import org.readium.r2.shared.publication.Locator
import org.readium.r2.shared.publication.Link
import org.readium.r2.shared.ExperimentalReadiumApi
import org.readium.r2.shared.util.AbsoluteUrl
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.clickable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.material.icons.filled.Headset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import kotlinx.coroutines.delay
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.PointerEventPass
import kotlinx.coroutines.isActive
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.IntOffset
import com.example.woodsreader.model.TtsUiStyle
import com.example.woodsreader.model.ScrollMode
import com.example.woodsreader.model.PageTurnStyle
import com.example.woodsreader.ui.reader.animation.BookPageTurnOverlay
import com.example.woodsreader.ui.reader.animation.PageTurnDirection
import com.example.woodsreader.ui.reader.animation.rememberBookPageTurnState
import androidx.compose.ui.text.style.TextDecoration
import com.example.woodsreader.ui.theme.LiterataFamily





private fun resolveTocTargetPage(link: Link, locator: Locator?): Int? {
    // 1. Posición directa en el locator
    locator?.locations?.position?.takeIf { it > 0 }?.let { return it }

    // 2. Fragmentos en el locator (ej: page=15)
    locator?.locations?.fragments?.forEach { fragment ->
        Regex("""(?i)(?:page=)?(\d+)""").find(fragment)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    }

    // 3. Href del locator
    locator?.href?.toString()?.let { href ->
        Regex("""(?i)[#?&]page=(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("""(?i)/page[_-]?(\d+)""").find(href)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
        Regex("""#(\d+)$""").find(href)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    }

    // 4. Href del Link de la tabla de contenidos (#page=15, /15, 15, etc.)
    val linkHref = link.href.toString()
    Regex("""(?i)[#?&]page=(\d+)""").find(linkHref)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    Regex("""(?i)/page[_-]?(\d+)""").find(linkHref)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    Regex("""#(\d+)$""").find(linkHref)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }
    Regex("""^/?(\d+)$""").find(linkHref)?.groupValues?.get(1)?.toIntOrNull()?.let { return it }

    return null
}


@OptIn(ExperimentalMaterial3Api::class, ExperimentalReadiumApi::class, ExperimentalSharedTransitionApi::class)
@Composable
fun ReaderScreen(
    onNavigateUp: () -> Unit,
    sharedTransitionScope: SharedTransitionScope,
    animatedVisibilityScope: AnimatedVisibilityScope,
    viewModel: ReaderViewModel = hiltViewModel()
) {
    val scope = rememberCoroutineScope()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val showControls by viewModel.showControls.collectAsStateWithLifecycle()

    val view = LocalView.current
    LaunchedEffect(showControls) {
        val window = (view.context as? Activity)?.window ?: return@LaunchedEffect
        val insetsController = WindowCompat.getInsetsController(window, view)
        insetsController.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        if (showControls) {
            insetsController.show(WindowInsetsCompat.Type.statusBars())
        } else {
            insetsController.hide(WindowInsetsCompat.Type.statusBars())
        }
    }

    DisposableEffect(view) {
        onDispose {
            val window = (view.context as? Activity)?.window
            if (window != null) {
                WindowCompat.getInsetsController(window, view)
                    .show(WindowInsetsCompat.Type.statusBars())
            }
        }
    }
    val isReflowMode by viewModel.isReflowMode.collectAsStateWithLifecycle()
    var showMasterMenu by remember { mutableStateOf(false) }
    
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val highlights by viewModel.highlights.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val isCurrentPageBookmarked by viewModel.isCurrentPageBookmarked.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val isTtsActive = ttsState == TtsState.PLAYING || ttsState == TtsState.PAUSED || ttsState == TtsState.LOADING
    val isLoadingTtsModel by viewModel.isLoadingTtsModel.collectAsStateWithLifecycle()
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsStateWithLifecycle()
    val sherpaDownloadState by viewModel.sherpaDownloadState.collectAsStateWithLifecycle()
    val selectedSherpaVoice by viewModel.selectedSherpaVoice.collectAsStateWithLifecycle()
    val audioExportState by viewModel.audioExportState.collectAsStateWithLifecycle()
    val showSherpaDownloadDialog by viewModel.showSherpaDownloadDialog.collectAsStateWithLifecycle()
    val useEdgeNeuralTts by viewModel.useEdgeNeuralTts.collectAsStateWithLifecycle()
    val selectedEdgeVoice by viewModel.selectedEdgeVoice.collectAsStateWithLifecycle()
    
    val interactionSource = remember { MutableInteractionSource() }
    val isUserInteracting by interactionSource.collectIsPressedAsState()

    val isAutoScrollActive by viewModel.isAutoScrollActive.collectAsStateWithLifecycle()
    val autoScrollWpm by viewModel.autoScrollWpm.collectAsStateWithLifecycle()
    val isAutoScrollManuallyPaused by viewModel.isAutoScrollManuallyPaused.collectAsStateWithLifecycle()
    var isFingerDown by remember { mutableStateOf(false) }
    var lastTouchUpTime by remember { mutableLongStateOf(0L) }
    var endOfChapterTime by remember { mutableStateOf<Long?>(null) }
    var isAdvancingChapter by remember { mutableStateOf(false) }
    
    val currentTtsLocator by viewModel.currentTtsLocator.collectAsStateWithLifecycle()
    val currentTtsSentence by viewModel.currentTtsSentence.collectAsStateWithLifecycle(initialValue = "")
    val globalProgress by viewModel.globalProgress.collectAsStateWithLifecycle(initialValue = 0f)


    val availableVoices by viewModel.availableVoices.collectAsStateWithLifecycle()
    val ttsRules by viewModel.ttsRules.collectAsStateWithLifecycle()
    val bookTimeRemaining by viewModel.bookTimeRemaining.collectAsStateWithLifecycle()
    val chapterTimeRemaining by viewModel.chapterTimeRemaining.collectAsStateWithLifecycle()
    val chapterInfoForTts by viewModel.chapterInfo.collectAsStateWithLifecycle()
    var showTtsRulesSheet by remember { mutableStateOf(false) }
    
    val navigatorState = remember { mutableStateOf<Navigator?>(null) }
    val navigator by navigatorState
    val currentLocator by remember(navigator) {
        navigator?.currentLocator ?: flowOf(null)
    }.collectAsStateWithLifecycle(initialValue = null)

    LaunchedEffect(currentLocator) {
        currentLocator?.let { loc ->
            viewModel.onLocatorChanged(loc)
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE || event == Lifecycle.Event.ON_STOP) {
                viewModel.flushReadingProgressImmediately()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    var pendingSelection by remember { mutableStateOf<Selection?>(null) }
    var showHighlightMenu by remember { mutableStateOf<Selection?>(null) }
    var selectionVersion by remember { mutableLongStateOf(0L) }
    var initialSearchQuery by remember { mutableStateOf("") }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    val hazeState = remember { HazeState() }
    
    var isTtsScreenLocked by remember { mutableStateOf(false) }
    val isTtsPlaying = ttsState == TtsState.PLAYING
    if (isTtsPlaying || isTtsScreenLocked) {
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose {
                view.keepScreenOn = false
            }
        }
    }

    androidx.activity.compose.BackHandler {
        viewModel.stopTts()
        viewModel.saveCurrentProgressBeforeExit()
        onNavigateUp()
    }

    var showAudioSheet by remember { mutableStateOf(false) }
    var showTtsMacroWindow by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(ReaderTab.TEXT) }
    var showToc by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }
    var showXRaySheet by remember { mutableStateOf(false) }
    var xRayReport by remember { mutableStateOf<ChapterXRayReport?>(null) }
    var showDictionarySheet by remember { mutableStateOf(false) }
    var showTranslateSheet by remember { mutableStateOf(false) }
    var selectedTextForQuery by remember { mutableStateOf("") }
    var quoteToShare by remember { mutableStateOf<String?>(null) }
    var pullProgress by remember { mutableFloatStateOf(0f) }
    var footnoteContent by remember { mutableStateOf<String?>(null) }

    val inlineLookupManager = remember { InlineLookupManager() }
    var lookupState by remember { mutableStateOf<LookupResult>(LookupResult.Idle) }

    LaunchedEffect(showHighlightMenu) {
        if (showHighlightMenu == null) {
            lookupState = LookupResult.Idle
        }
    }

    val context = LocalContext.current
    LaunchedEffect(isTtsActive) {
        val activity = context.findFragmentActivity()
        if (activity != null) {
            activity.volumeControlStream = android.media.AudioManager.STREAM_MUSIC
        }
    }
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val highlightColors = LocalHighlightColors.current
    val accentColor = HiddenForest.Accent
    val ttsHighlightColor = highlightColors.yellow.copy(alpha = 0.3f).toArgb()
    val fragmentManager = remember(context) { context.findFragmentActivity()?.supportFragmentManager }
    val fm = fragmentManager
    val focusRequester = remember { FocusRequester() }

    var activeActionMode by remember { mutableStateOf<ActionMode?>(null) }
    var isLongPressSelecting by remember { mutableStateOf(false) }

    val isSelectionModeActive = isLongPressSelecting ||
        pendingSelection != null ||
        showHighlightMenu != null ||
        activeActionMode != null
    val isSelectionModeActiveState = rememberUpdatedState(isSelectionModeActive)

    LaunchedEffect(selectionVersion) {
        val target = pendingSelection
        if (target == null) {
            showHighlightMenu = null
        } else {
            showHighlightMenu = null // Mantener oculto mientras el usuario sigue ajustando los cursores
            delay(2200L) // Retardo de 2.2 segundos para que se estabilice la selección sin tapar el texto
            val current = (navigatorState.value as? SelectableNavigator)?.currentSelection()
            showHighlightMenu = current ?: target
            viewModel.hideControls()
        }
    }

    val clearActiveSelection: () -> Unit = {
        lookupState = LookupResult.Idle
        isLongPressSelecting = false
        pendingSelection = null
        selectionVersion++
        showHighlightMenu = null
        try {
            activeActionMode?.finish()
            activeActionMode = null
        } catch (_: Exception) {}
        try {
            (navigatorState.value as? SelectableNavigator)?.clearSelection()
        } catch (_: Exception) {}
        val pubView = (navigatorState.value as? VisualNavigator)?.safePublicationView
        if (pubView != null) {
            applyToAllWebViews(pubView) { wv ->
                wv.evaluateJavascript("if (window.getSelection) { window.getSelection().removeAllRanges(); }", null)
            }
        }
    }

    val ttsSelectionCallback = remember(viewModel, scope) {
        object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.clear()
                activeActionMode = mode
                isLongPressSelecting = true
                (navigatorState.value as? VisualNavigator)?.safePublicationView
                    ?.parent?.requestDisallowInterceptTouchEvent(true)
                scope.launch {
                    delay(100L)
                    val selection =
                        (navigatorState.value as? SelectableNavigator)?.currentSelection()
                    if (selection != null) {
                        pendingSelection = selection
                        selectionVersion++
                        isLongPressSelecting = false
                    }
                }
                return true
            }
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    MENU_ITEM_HIGHLIGHT_ID -> {
                        scope.launch {
                            val sel = (navigatorState.value as? SelectableNavigator)?.currentSelection() ?: pendingSelection
                            if (sel != null) {
                                showHighlightMenu = sel
                                viewModel.hideControls()
                            }
                            mode.finish()
                            activeActionMode = null
                        }
                        return true
                    }
                    MENU_ITEM_TTS_ID -> {
                        scope.launch {
                            val sel = (navigatorState.value as? SelectableNavigator)?.currentSelection() ?: pendingSelection
                            if (sel != null) {
                                viewModel.playTts(sel.locator)
                                viewModel.hideControls()
                                showTtsMacroWindow = true
                            }
                            mode.finish()
                            activeActionMode = null
                        }
                        return true
                    }
                    MENU_ITEM_DICTIONARY_ID -> {
                        scope.launch {
                            val sel = (navigatorState.value as? SelectableNavigator)?.currentSelection() ?: pendingSelection
                            val text = sel?.locator?.text?.highlight ?: ""
                            if (text.isNotBlank()) {
                                selectedTextForQuery = text
                                showDictionarySheet = true
                                viewModel.hideControls()
                            }
                            mode.finish()
                            activeActionMode = null
                        }
                        return true
                    }
                    MENU_ITEM_TRANSLATE_ID -> {
                        scope.launch {
                            val sel = (navigatorState.value as? SelectableNavigator)?.currentSelection() ?: pendingSelection
                            val text = sel?.locator?.text?.highlight ?: ""
                            if (text.isNotBlank()) {
                                selectedTextForQuery = text
                                showTranslateSheet = true
                                viewModel.hideControls()
                            }
                            mode.finish()
                            activeActionMode = null
                        }
                        return true
                    }
                }
                return false
            }
            override fun onDestroyActionMode(mode: ActionMode) {
                if (activeActionMode == mode) {
                    activeActionMode = null
                }
                isLongPressSelecting = false
                (navigatorState.value as? VisualNavigator)?.safePublicationView
                    ?.parent?.requestDisallowInterceptTouchEvent(false)
            }
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean {
                scope.launch {
                    val sel = (navigatorState.value as? SelectableNavigator)?.currentSelection()
                    if (sel != null) {
                        pendingSelection = sel
                        selectionVersion++
                    }
                }
                return false
            }
        }
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    DisposableEffect(fragmentManager) {
        if (fragmentManager == null) return@DisposableEffect onDispose {}

        val existing = fragmentManager.fragments.firstOrNull { it is Navigator } as? Navigator
        if (existing != null && navigatorState.value != existing) {
            navigatorState.value = existing
            viewModel.navigator = existing
        }

        val callback = object : FragmentManager.FragmentLifecycleCallbacks() {
            override fun onFragmentResumed(fm: FragmentManager, f: Fragment) {
                if (f is Navigator && navigatorState.value != f) {
                    navigatorState.value = f
                    viewModel.navigator = f 
                }
            }
            override fun onFragmentDetached(fm: FragmentManager, f: Fragment) {
                if (navigatorState.value == f) navigatorState.value = null
            }
        }
        fragmentManager.registerFragmentLifecycleCallbacks(callback, false)
        onDispose {
            fragmentManager.unregisterFragmentLifecycleCallbacks(callback)
            val navFragments = fragmentManager.fragments.filter { it is Navigator }
            if (navFragments.isNotEmpty() && !fragmentManager.isStateSaved) {
                try {
                    fragmentManager.commit(allowStateLoss = true) {
                        navFragments.forEach { remove(it) }
                    }
                } catch (e: Exception) {
                    Log.w("ReaderScreen", "Error removing navigator fragments onDispose", e)
                }
            }
            fragmentManager.fragmentFactory = androidx.fragment.app.FragmentFactory()
            navigatorState.value = null
        }
    }

    // << CHANGE: Auto-hide UI despuÃƒÂ©s de 5s de inactividad (Modo Inmersivo)
    LaunchedEffect(isUserInteracting, showControls, ttsState) {
        if (showControls && !isUserInteracting && ttsState != TtsState.PLAYING) {
            delay(5000)
            if (!isUserInteracting) {
                viewModel.hideControls()
            }
        }
    }

    // << CHANGE: Hero Splash Animation Logic
    val heroBook = when (val s = uiState) {
        is ReaderUiState.InitialLoading -> s.book
        is ReaderUiState.Success -> s.book
        else -> null
    }

    // Sin esto, cualquier recomposiciÃƒÂ³n dispara submitPreferences() innecesariamente.
    val isRtl by viewModel.isRtl.collectAsStateWithLifecycle()
    val epubPreferences = remember(userPreferences, isRtl) {
        userPreferences.toEpubPreferences().let { prefs ->
            if (isRtl) prefs.copy(readingProgression = org.readium.r2.navigator.preferences.ReadingProgression.RTL)
            else prefs
        }
    }

    // Envolvemos TODO en una Box para permitir la capa Splash
    Box(modifier = Modifier.fillMaxSize()) {

    var lastSubmittedEpubPrefs by remember { mutableStateOf<org.readium.r2.navigator.epub.EpubPreferences?>(null) }
    LaunchedEffect(navigatorState.value, epubPreferences) {
        val nav = navigatorState.value ?: return@LaunchedEffect
        if (nav is EpubNavigatorFragment) {
            if (lastSubmittedEpubPrefs != null && lastSubmittedEpubPrefs != epubPreferences) {
                nav.submitPreferences(epubPreferences)
            }
            lastSubmittedEpubPrefs = epubPreferences
            nav.safePublicationView?.let { pubView ->
                applyToAllWebViews(pubView) { wv ->
                    wv.applyCustomFontFamily(userPreferences.fontFamily)
                    wv.setOnTouchListener { _, event ->
                        when (event.actionMasked) {
                            android.view.MotionEvent.ACTION_DOWN, android.view.MotionEvent.ACTION_MOVE -> {
                                if (!isFingerDown) isFingerDown = true
                            }
                            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                                if (isFingerDown) {
                                    isFingerDown = false
                                    lastTouchUpTime = System.currentTimeMillis()
                                }
                            }
                        }
                        false
                    }
                }
            }
        } else if (nav is PdfNavigatorFragment<*, *>) {
            @Suppress("UNCHECKED_CAST")
            (nav as PdfNavigatorFragment<*, org.readium.adapter.pdfium.navigator.PdfiumPreferences>).submitPreferences(userPreferences.toPdfPreferences())
        }
    }

    val highlightColors = LocalHighlightColors.current
    LaunchedEffect(navigatorState.value, highlights, highlightColors) {
        val nav = navigatorState.value as? DecorableNavigator ?: return@LaunchedEffect
        val decorations = highlights.mapNotNull { hl ->
            try {
                val color = when (hl.color) {
                    com.example.woodsreader.data.model.HighlightColor.YELLOW -> highlightColors.yellow
                    com.example.woodsreader.data.model.HighlightColor.GREEN -> highlightColors.green
                    com.example.woodsreader.data.model.HighlightColor.BLUE -> highlightColors.blue
                    com.example.woodsreader.data.model.HighlightColor.PINK -> highlightColors.pink
                    com.example.woodsreader.data.model.HighlightColor.RED -> highlightColors.red
                }
                val loc = hl.toLocator() ?: return@mapNotNull null
                Decoration(
                    id = hl.id,
                    locator = loc,
                    style = Decoration.Style.Highlight(tint = color.toArgb())
                )
            } catch (_: Exception) { null }
        }
        nav.applyDecorations(decorations, "highlights")
    }

    val isPdf = (uiState as? ReaderUiState.Success)?.book?.format?.name == "PDF"
    val currentTtsPage by viewModel.currentTtsPage.collectAsStateWithLifecycle()

    // Sincronización TTS para EPUB: resaltado elegante cambiando el color de letra (idéntico a PDF Reflow) y seguimiento visual
    LaunchedEffect(ttsState, navigatorState.value, isUserInteracting, userPreferences.ttsHighlightStyle, userPreferences.scrollMode, userPreferences.ttsAutoScroll, userPreferences.theme) {
        val nav = navigatorState.value ?: return@LaunchedEffect
        val decorableNav = nav as? DecorableNavigator
        
        if (ttsState == TtsState.PLAYING) {
            var lastHighlightedSentence = ""
            var lastTargetResource = ""
            var lastPageTurnTime = 0L

            viewModel.currentTtsLocator.collectLatest { locator ->
                if (locator == null) return@collectLatest
                val sentenceText = locator.text.highlight ?: viewModel.currentTtsSentence.value
                if (sentenceText.isNullOrBlank()) return@collectLatest
                if (sentenceText == lastHighlightedSentence) return@collectLatest
                lastHighlightedSentence = sentenceText

                // 1. Asegurar que Readium no dibuje ninguna caja de fondo nativa
                if (decorableNav != null) {
                    decorableNav.applyDecorations(emptyList(), "tts")
                    decorableNav.applyDecorations(emptyList(), "tts-scrub")
                }

                val cssSelector = locator.locations.otherLocations["cssSelector"] as? String
                val isPaginated = false // En modo unificado el layout base es vertical continuo para scroll libre y fluido

                // 2. Seguimiento visual de lectura: cambio de capítulo o avance suave dentro del capítulo
                val isLockActive = (System.currentTimeMillis() - lastInteractionTime) < 3000L
                val autoScrollEnabled = !isUserInteracting && !isLockActive && userPreferences.ttsAutoScroll

                if (nav is VisualNavigator) {
                    val currentScreenLoc = nav.currentLocator.value
                    val targetRes = locator.href.toString().substringAfterLast('/').substringBefore('#')
                    val currentRes = currentScreenLoc.href.toString().substringAfterLast('/').substringBefore('#')

                    if (targetRes.isNotEmpty() && currentRes.isNotEmpty() && targetRes != currentRes && targetRes != lastTargetResource && autoScrollEnabled) {
                        lastTargetResource = targetRes
                        nav.go(locator, animated = false)
                        kotlinx.coroutines.delay(250L)
                        val newWv = nav.safePublicationView?.let { findActiveWebView(it) }
                        newWv?.applyCustomTtsHighlight(
                            cssSelector = cssSelector,
                            textSnippet = sentenceText,
                            style = userPreferences.ttsHighlightStyle,
                            theme = userPreferences.theme,
                            isPaginated = isPaginated,
                            autoScroll = autoScrollEnabled,
                            onPageAction = { action ->
                                val now = System.currentTimeMillis()
                                if (now - lastPageTurnTime > 350L) {
                                    lastPageTurnTime = now
                                    when (action) {
                                        "NEXT" -> nav.goForwardCompat(animated = true)
                                        "PREV" -> nav.goBackwardCompat(animated = true)
                                        "JUMP" -> nav.go(locator, animated = false)
                                    }
                                }
                            }
                        )
                    } else {
                        val activeWv = nav.safePublicationView?.let { findActiveWebView(it) }
                        activeWv?.applyCustomTtsHighlight(
                            cssSelector = cssSelector,
                            textSnippet = sentenceText,
                            style = userPreferences.ttsHighlightStyle,
                            theme = userPreferences.theme,
                            isPaginated = isPaginated,
                            autoScroll = autoScrollEnabled,
                            onPageAction = { action ->
                                val now = System.currentTimeMillis()
                                if (now - lastPageTurnTime > 350L) {
                                    lastPageTurnTime = now
                                    when (action) {
                                        "NEXT" -> nav.goForwardCompat(animated = true)
                                        "PREV" -> nav.goBackwardCompat(animated = true)
                                        "JUMP" -> nav.go(locator, animated = false)
                                    }
                                }
                            }
                        )
                    }
                }
            }
        } else {
            if (decorableNav != null) {
                decorableNav.applyDecorations(emptyList(), "tts")
                decorableNav.applyDecorations(emptyList(), "tts-scrub")
            }
            if (nav is VisualNavigator) {
                nav.safePublicationView?.let { pubView ->
                    applyToAllWebViews(pubView) { wv ->
                        wv.clearCustomTtsHighlight()
                    }
                }
            }
        }
    }

    // Sincronización automática de página para PDF Original (modo no reflow)
    var lastNavigatedPdfPage by remember { mutableIntStateOf(-1) }
    LaunchedEffect(currentTtsPage, ttsState, isReflowMode, isPdf, navigatorState.value, userPreferences.ttsAutoScroll) {
        if (isPdf && !isReflowMode && ttsState == TtsState.PLAYING && currentTtsPage > 0) {
            val nav = navigatorState.value ?: return@LaunchedEffect
            if (!userPreferences.ttsAutoScroll) return@LaunchedEffect

            // Si el usuario acaba de tocar la pantalla (ej. al pulsar Play), esperar brevemente en vez de descartar el salto de página
            while (isUserInteracting || (System.currentTimeMillis() - lastInteractionTime) < 1200L) {
                delay(250L)
            }

            if (lastNavigatedPdfPage != currentTtsPage) {
                lastNavigatedPdfPage = currentTtsPage
                val loc = viewModel.getLocatorForPdfPage(currentTtsPage)
                if (loc != null && nav is VisualNavigator) {
                    nav.go(loc, animated = false)
                }
            }
        } else if (ttsState != TtsState.PLAYING) {
            lastNavigatedPdfPage = -1
        }
    }

    // Corrutina de Teleprompter / Auto-Scroll suave a 60 FPS con pausa táctil inteligente
    LaunchedEffect(isAutoScrollActive) {
        if (!isAutoScrollActive) {
            viewModel.setAutoScrollManuallyPaused(false)
            endOfChapterTime = null
            isAdvancingChapter = false
        }
    }

    LaunchedEffect(isAutoScrollActive, autoScrollWpm, isAutoScrollManuallyPaused, isFingerDown, lastTouchUpTime, navigatorState.value) {
        if (!isAutoScrollActive) return@LaunchedEffect

        val nav = navigatorState.value as? VisualNavigator ?: return@LaunchedEffect
        var accumulatedPx = 0f

        while (isActive) {
            delay(16L)

            val now = System.currentTimeMillis()
            val isTouchPaused = isFingerDown || (now - lastTouchUpTime < 1200L)

            if (isTouchPaused || isAutoScrollManuallyPaused || isAdvancingChapter) {
                continue
            }

            val pubView = nav.safePublicationView
            val activeWv = pubView?.let { findActiveWebView(it) }

            if (activeWv != null) {
                if (activeWv.canScrollVertically(1)) {
                    endOfChapterTime = null
                    val density = activeWv.resources.displayMetrics.density.coerceAtLeast(1f)
                    val wordsPerSec = autoScrollWpm / 60.0f
                    val pxPerWord = 9.2f * density
                    val pxPerSec = wordsPerSec * pxPerWord
                    val deltaPx = pxPerSec * 0.016f

                    accumulatedPx += deltaPx
                    if (accumulatedPx >= 1.0f) {
                        val pxToScroll = accumulatedPx.toInt()
                        activeWv.scrollBy(0, pxToScroll)
                        accumulatedPx -= pxToScroll
                    }
                } else {
                    val reachedTime = endOfChapterTime
                    if (reachedTime == null) {
                        endOfChapterTime = now
                    } else if (now - reachedTime >= 2500L && !isAdvancingChapter) {
                        isAdvancingChapter = true
                        endOfChapterTime = null
                        viewModel.goToNextChapter(nav)
                        delay(800L)
                        isAdvancingChapter = false
                        accumulatedPx = 0f
                    }
                }
            }
        }
    }

    val pageTurnState = rememberBookPageTurnState()

    val performPageForward: (VisualNavigator) -> Unit = { nav ->
        val pubView = nav.safePublicationView
        if (userPreferences.pageTurnAnimation && 
            userPreferences.pageTurnStyle != PageTurnStyle.NONE &&
            pubView != null
        ) {
            scope.launch {
                pageTurnState.turnPage(
                    view = pubView,
                    dir = PageTurnDirection.FORWARD,
                    style = userPreferences.pageTurnStyle
                ) {
                    viewModel.pageForward(nav)
                }
            }
        } else {
            viewModel.pageForward(nav)
        }
    }

    val turnPageForward: (VisualNavigator, android.webkit.WebView?) -> Unit = { nav, directWv ->
        clearActiveSelection()
        if (nav is EpubNavigatorFragment) {
            val pubView = nav.safePublicationView
            val activeWv = directWv ?: pubView?.let { findActiveWebView(it) }
            if (activeWv != null && activeWv.canScrollVertically(1)) {
                if (userPreferences.pageTurnAnimation && userPreferences.pageTurnStyle != PageTurnStyle.NONE && pubView != null) {
                    scope.launch {
                        pageTurnState.turnPage(
                            view = pubView,
                            dir = PageTurnDirection.FORWARD,
                            style = userPreferences.pageTurnStyle
                        ) {
                            activeWv.scrollByViewportDown(0.85f, animated = false)
                        }
                    }
                } else {
                    activeWv.scrollByViewportDown(0.85f, animated = true)
                }
            } else {
                if (userPreferences.pageTurnAnimation && userPreferences.pageTurnStyle != PageTurnStyle.NONE && pubView != null) {
                    scope.launch {
                        pageTurnState.turnPage(
                            view = pubView,
                            dir = PageTurnDirection.FORWARD,
                            style = userPreferences.pageTurnStyle
                        ) {
                            viewModel.goToNextChapter(nav)
                        }
                    }
                } else {
                    viewModel.goToNextChapter(nav)
                }
            }
        } else {
            performPageForward(nav)
        }
    }

    val performPageBackward: (VisualNavigator) -> Unit = { nav ->
        val pubView = nav.safePublicationView
        if (userPreferences.pageTurnAnimation && 
            userPreferences.pageTurnStyle != PageTurnStyle.NONE &&
            pubView != null
        ) {
            scope.launch {
                pageTurnState.turnPage(
                    view = pubView,
                    dir = PageTurnDirection.BACKWARD,
                    style = userPreferences.pageTurnStyle
                ) {
                    viewModel.pageBackward(nav)
                }
            }
        } else {
            viewModel.pageBackward(nav)
        }
    }

    val turnPageBackward: (VisualNavigator, android.webkit.WebView?) -> Unit = { nav, directWv ->
        clearActiveSelection()
        if (nav is EpubNavigatorFragment) {
            val pubView = nav.safePublicationView
            val activeWv = directWv ?: pubView?.let { findActiveWebView(it) }
            val canGoBack = activeWv != null && (activeWv.canScrollVertically(-1) || activeWv.scrollY > 20)
            if (canGoBack) {
                if (userPreferences.pageTurnAnimation && userPreferences.pageTurnStyle != PageTurnStyle.NONE && pubView != null) {
                    scope.launch {
                        pageTurnState.turnPage(
                            view = pubView,
                            dir = PageTurnDirection.BACKWARD,
                            style = userPreferences.pageTurnStyle
                        ) {
                            activeWv.scrollByViewportUp(0.85f, animated = false)
                        }
                    }
                } else {
                    activeWv.scrollByViewportUp(0.85f, animated = true)
                }
            } else {
                if (userPreferences.pageTurnAnimation && userPreferences.pageTurnStyle != PageTurnStyle.NONE && pubView != null) {
                    scope.launch {
                        pageTurnState.turnPage(
                            view = pubView,
                            dir = PageTurnDirection.BACKWARD,
                            style = userPreferences.pageTurnStyle
                        ) {
                            viewModel.goToPreviousChapter(nav)
                        }
                    }
                } else {
                    viewModel.goToPreviousChapter(nav)
                }
            }
        } else {
            performPageBackward(nav)
        }
    }

    DisposableEffect(navigatorState.value, userPreferences.invertedTapZones, userPreferences.pageTurnStyle, userPreferences.pageTurnAnimation) {
        val nav = navigatorState.value as? VisualNavigator ?: return@DisposableEffect onDispose {}
        val pubView = nav.safePublicationView ?: return@DisposableEffect onDispose {}

        val listener = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val tapX = event.point.x
                val density = pubView.context.resources.displayMetrics.density
                val viewWidthDp = if (density > 0f) pubView.width / density else pubView.width.toFloat()
                val relativeX = when {
                    viewWidthDp > 0f && tapX <= viewWidthDp -> (tapX / viewWidthDp).coerceIn(0f, 1f)
                    pubView.width > 0 -> (tapX / pubView.width.toFloat()).coerceIn(0f, 1f)
                    else -> 0.5f
                }

                lastInteractionTime = System.currentTimeMillis()
                viewModel.onUserInteraction() 

                // Si hay una selección activa o popup de selección abierto, el toque lo cierra de inmediato
                if (isSelectionModeActiveState.value) {
                    isLongPressSelecting = false
                    clearActiveSelection()
                    return true
                }

                val isLeftTap = relativeX < 0.30f
                val isRightTap = relativeX > 0.70f
                val isBackTap = if (userPreferences.invertedTapZones) isRightTap else isLeftTap
                val isForwardTap = if (userPreferences.invertedTapZones) isLeftTap else isRightTap

                return when {
                    isForwardTap -> {
                        turnPageForward(nav, null)
                        true
                    }
                    isBackTap -> {
                        turnPageBackward(nav, null)
                        true
                    }
                    else -> {
                        viewModel.toggleControls()
                        showHighlightMenu = null
                        true
                    }
                }
            }
        }
        nav.addInputListener(listener)

        val detector = ChapterScrollDetector(
            rootView = pubView,
            scope = scope,
            onGoForward = {
                if (!isSelectionModeActiveState.value) viewModel.goToNextChapter(nav)
            },
            onGoBackward = {
                if (!isSelectionModeActiveState.value) viewModel.goToPreviousChapter(nav)
            },
            onPullProgress = {
                pullProgress = if (isSelectionModeActiveState.value) 0f else it
            },
            onPageForward = { wv ->
                if (!isSelectionModeActiveState.value) turnPageForward(nav, wv)
            },
            onPageBackward = { wv ->
                if (!isSelectionModeActiveState.value) turnPageBackward(nav, wv)
            },
            onClearSelection = {
                if (isSelectionModeActiveState.value) clearActiveSelection()
            }
        )
        detector.attach()

        onDispose {
            nav.removeInputListener(listener)
            detector.detach()
        }
    }




    Box(
        modifier = Modifier
            .fillMaxSize()
            .focusRequester(focusRequester)
            .focusable()
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val anyPressed = event.changes.any { it.pressed }
                        if (anyPressed) {
                            if (!isFingerDown) isFingerDown = true
                        } else {
                            if (isFingerDown) {
                                isFingerDown = false
                                lastTouchUpTime = System.currentTimeMillis()
                            }
                        }
                    }
                }
            }
            .onKeyEvent { event ->
                if (isTtsActive) {
                    // Durante TTS, permitir que el sistema operativo y MainActivity controlen el volumen multimedia
                    false
                } else if (userPreferences.volumeKeysNavigation && event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.VolumeUp -> {
                            val nav = navigatorState.value as? VisualNavigator
                            if (nav != null) turnPageBackward(nav, null) else viewModel.pageBackward(navigatorState.value)
                            true
                        }
                        Key.VolumeDown -> {
                            val nav = navigatorState.value as? VisualNavigator
                            if (nav != null) turnPageForward(nav, null) else viewModel.pageForward(navigatorState.value)
                            true
                        }
                        else -> false
                    }
                } else false
            }
            .background(
                color = epubPreferences.backgroundColor?.let { ComposeColor(it.int) }
                    ?: MaterialTheme.colorScheme.background
            )
    ) {
        val successState = uiState as? ReaderUiState.Success

        // Capa 0: Contenido del Libro
        Box(modifier = Modifier.fillMaxSize()) {
            when (val state = uiState) {
                is ReaderUiState.Loading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = HiddenForest.Accent)
                }
                is ReaderUiState.InitialLoading -> {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = HiddenForest.Accent)
                }
                is ReaderUiState.Error -> {
                    Text(state.message, modifier = Modifier.align(Alignment.Center), color = HiddenForest.TextSecondary)
                }
                is ReaderUiState.Success -> {
                    val isPdf = state.book.format.name == "PDF"
                    val isDarkTheme = userPreferences.theme == com.example.woodsreader.model.ReaderTheme.HIDDEN_FOREST || 
                                      userPreferences.theme == com.example.woodsreader.model.ReaderTheme.AMOLED || 
                                      userPreferences.theme == com.example.woodsreader.model.ReaderTheme.SUNSET
                    
                    ReaderNavigatorContainer(
                        state = state,
                        userPreferences = userPreferences,
                        epubPreferences = epubPreferences,
                        pageTurnState = pageTurnState,
                        ttsSelectionCallback = ttsSelectionCallback,
                        ttsState = ttsState,
                        isReflowMode = isReflowMode,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        // CAPA 1: Menu de Resaltado (Opcional)
        ReaderHighlightOverlay(
            selection = showHighlightMenu,
            lookupState = lookupState,
            hazeState = hazeState,
            quoteToShare = quoteToShare,
            bookTitle = successState?.book?.title ?: "",
            bookAuthor = successState?.book?.author ?: "",
            chapterTitle = chapterInfoForTts.title ?: "",
            onColorSelected = { color ->
                showHighlightMenu?.locator?.let { loc ->
                    viewModel.addHighlight(loc, color, loc.text.highlight ?: "")
                }
                clearActiveSelection()
            },
            onSaveCharacter = { name, role, noteText ->
                showHighlightMenu?.locator?.let { loc ->
                    val customNote = if (noteText.isNotBlank()) "[PERSONAJE: $role] $noteText" else "[PERSONAJE: $role]"
                    viewModel.addHighlight(
                        locator = loc,
                        color = com.example.woodsreader.data.model.HighlightColor.BLUE,
                        text = name,
                        note = customNote
                    )
                }
                clearActiveSelection()
            },
            onCopy = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? android.content.ClipboardManager
                val text = showHighlightMenu?.locator?.text?.highlight ?: ""
                val clip = ClipData.newPlainText("Texto seleccionado", text)
                clipboard?.setPrimaryClip(clip)
                Toast.makeText(context, "Texto copiado", Toast.LENGTH_SHORT).show()
                clearActiveSelection()
            },
            onShare = { text ->
                quoteToShare = text
                clearActiveSelection()
            },
            onExpandToSentence = {
                val pubView = (navigatorState.value as? VisualNavigator)?.safePublicationView
                if (pubView != null) {
                    applyToAllWebViews(pubView) { wv ->
                        wv.evaluateJavascript(
                            "if (window.getSelection) { var s = window.getSelection(); s.modify('move', 'backward', 'sentence'); s.modify('extend', 'forward', 'sentence'); }",
                            null
                        )
                    }
                    scope.launch {
                        delay(120L)
                        val sel = (navigatorState.value as? SelectableNavigator)?.currentSelection()
                        if (sel != null) {
                            pendingSelection = sel
                            selectionVersion++
                        }
                    }
                }
            },
            onPlayTts = { loc ->
                viewModel.playTts(loc)
                clearActiveSelection()
                showTtsMacroWindow = true
            },
            onSearch = { text ->
                initialSearchQuery = text
                clearActiveSelection()
                showSearch = true
            },
            onTranslate = { text ->
                scope.launch {
                    lookupState = LookupResult.Loading("Traduciendo...")
                    lookupState = inlineLookupManager.translateToSpanish(text)
                }
            },
            onDictionary = { text ->
                scope.launch {
                    lookupState = LookupResult.Loading("Buscando definición...")
                    lookupState = inlineLookupManager.defineTerm(text)
                }
            },
            onIgnoreTts = { text ->
                if (text.isNotBlank()) {
                    viewModel.addWordExclusionRule(
                        pattern = text.trim(),
                        isExactMatch = true,
                        applyToAllBooks = false
                    )
                }
                clearActiveSelection()
            },
            onErrorOpenBrowser = { text ->
                val encoded = Uri.encode(text.trim())
                val url = if (lookupState is LookupResult.Error && (lookupState as LookupResult.Error).modeTitle.contains("Definición", ignoreCase = true)) {
                    "https://es.wiktionary.org/wiki/$encoded"
                } else {
                    "https://translate.google.com/?sl=auto&tl=es&text=$encoded"
                }
                try {
                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                } catch (_: Exception) {}
            },
            onSpeakSnippet = { viewModel.speakSnippet(it) },
            onDismissHighlight = {
                lookupState = LookupResult.Idle
                clearActiveSelection()
            },
            onDismissQuoteShare = { quoteToShare = null }
        )

        // CAPA 2: Controles Superpuestos Solidos (TopBar)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter).zIndex(15f)
        ) {
            if (successState != null) {
                ReaderTopBar(
                    title = successState.book.title,
                    ttsState = ttsState,
                    hasSelection = showHighlightMenu != null,
                    hazeState = hazeState,
                    isPdf = successState.book.format.name == "PDF",
                    isReflowMode = isReflowMode,
                    onToggleReflow = { viewModel.toggleReflowMode() },
                    onBack = {
                        viewModel.stopTts()
                        viewModel.saveCurrentProgressBeforeExit()
                        onNavigateUp()
                    },
                    onHighlight = {
                        scope.launch {
                            val nav = navigatorState.value as? SelectableNavigator
                            val sel = nav?.currentSelection()
                            if (sel != null) { showHighlightMenu = sel; viewModel.hideControls() }
                        }
                    },
                    onTtsToggle = { 
                        if (ttsState == TtsState.PLAYING || ttsState == TtsState.PAUSED) {
                            showTtsMacroWindow = true
                        } else {
                            viewModel.onPlay()
                        }
                    },
                    onXRayClick = {
                        xRayReport = viewModel.generateXRayReport()
                        showXRaySheet = true
                    },
                    onMenuClick = { showMasterMenu = true }
                )
            }
        }

        // CAPA 1.5: Modo Reflow (Vista Libro PDF)
        val reflowState by viewModel.reflowState.collectAsStateWithLifecycle()
        val isLoadingMore by viewModel.isLoadingMoreReflow.collectAsStateWithLifecycle()
        // Note: ttsState and currentTtsLocator already collected at the top of ReaderScreen

        val ttsActiveBlockIndex by viewModel.ttsActiveBlockIndex.collectAsStateWithLifecycle()
        val currentSpeed by viewModel.currentSpeed.collectAsStateWithLifecycle()

        ReaderReflowOverlay(
            modifier = Modifier.fillMaxSize().zIndex(1f),
            isVisible = isReflowMode,
            reflowState = reflowState,
            ttsActiveBlockIndex = ttsActiveBlockIndex,
            fontScale = userPreferences.fontSizeSp / 18f,
            hazeState = hazeState,
            isLoadingMore = isLoadingMore,
            ttsLocator = currentTtsLocator,
            isTtsPlaying = ttsState == TtsState.PLAYING,
            currentTtsSentence = currentTtsSentence,
            preferences = userPreferences,
            onLoadMore = { viewModel.loadNextPage() },
            onLoadPrevious = { viewModel.loadPreviousPage() },
            onScroll = { index, isScrolling -> viewModel.onReflowScroll(index, isScrolling) },
            onToggleControls = { viewModel.toggleControls() },
            onPlayPause = { 
                if (ttsState == TtsState.PLAYING) viewModel.pauseTts()
                else viewModel.onPlay()
            },
            onSkipNext = { viewModel.skipReflowTtsForward() },
            onSkipPrev = { viewModel.skipReflowTtsBackward() },
            currentSpeed = currentSpeed,
            onSpeedChange = { viewModel.onSpeedChange(it) },
            onDismiss = { 
                viewModel.pauseTts()
                viewModel.toggleReflowMode()
            }
        )

        // CAPA 1.8: Indicador de Lectura y Oración TTS (Solo PDF Normal)
        val ttsHudBottomPadding = if (userPreferences.ttsUiStyle == TtsUiStyle.BAR) 96.dp else 130.dp
        ReaderPdfTtsHud(
            visible = !isReflowMode && isPdf && (ttsState == TtsState.PLAYING || ttsState == TtsState.PAUSED),
            currentTtsPage = currentTtsPage,
            isPaused = ttsState == TtsState.PAUSED,
            currentTtsSentence = currentTtsSentence,
            ttsHighlightStyle = userPreferences.ttsHighlightStyle,
            theme = userPreferences.theme,
            bottomPadding = ttsHudBottomPadding,
            modifier = Modifier.align(Alignment.BottomCenter)
        )

        // CAPA 4: Master Menu (Panel Lateral)
        AnimatedVisibility(
            visible = showMasterMenu,
            modifier = Modifier.zIndex(25f), // Asegurar que esté por encima de todo
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            ReaderSideMenu(
                hazeState = hazeState,
                isReflowAvailable = (uiState as? ReaderUiState.Success)?.book?.format?.name == "PDF",
                isReflowMode = isReflowMode,
                currentScrollMode = userPreferences.scrollMode,
                isCurrentPageBookmarked = isCurrentPageBookmarked,
                isAutoScrollActive = isAutoScrollActive,
                onOptionClick = { option ->
                    showMasterMenu = false
                    when (option.type) {
                        MasterMenuOptionType.TTS -> showTtsMacroWindow = true
                        MasterMenuOptionType.TOC -> showToc = true
                        MasterMenuOptionType.SEARCH -> showSearch = true
                        MasterMenuOptionType.XRAY -> {
                            xRayReport = viewModel.generateXRayReport()
                            showXRaySheet = true
                        }
                        MasterMenuOptionType.AUTO_SCROLL -> viewModel.toggleAutoScroll()
                        MasterMenuOptionType.REFLOW -> viewModel.toggleReflowMode()
                        MasterMenuOptionType.SETTINGS -> {
                            activeTab = ReaderTab.TEXT
                            showAudioSheet = true
                        }
                        MasterMenuOptionType.BOOKMARK -> {
                            viewModel.addBookmark(currentLocator)
                        }
                        MasterMenuOptionType.SCROLL_MODE -> {
                            viewModel.toggleScrollMode()
                        }
                    }
                },
                onDismiss = { showMasterMenu = false }
            )
        }

        // CAPA 2: Slider Inferior (hidden when TTS bar is active in BAR mode)
        val ttsUiStyle = userPreferences.ttsUiStyle
        val showBottomSlider = showControls && !(isTtsActive && ttsUiStyle == TtsUiStyle.BAR)
        AnimatedVisibility(
            visible = showBottomSlider,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter).zIndex(15f)
        ) {
            ReaderBottomSlider(
                navigator = navigator,
                globalProgressFlow = viewModel.globalProgress,
                chapterInfoFlow = viewModel.chapterInfo,
                bookTimeRemaining = bookTimeRemaining,
                chapterTimeRemaining = chapterTimeRemaining,
                hazeState = hazeState,
                onProgressChange = { viewModel.seekToGlobalProgress(it, navigator) },
                onShowToc = { showToc = true },
                onNextChapter = { viewModel.goToNextChapter(navigator) },
                onPreviousChapter = { viewModel.goToPreviousChapter(navigator) }
            )
        }
        

        // CAPA 2.5: Píldora Flotante Teleprompter / Auto-Scroll
        AnimatedVisibility(
            visible = isAutoScrollActive,
            enter = slideInVertically { it } + fadeIn(),
            exit = slideOutVertically { it } + fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .zIndex(20f)
                .navigationBarsPadding()
                .padding(bottom = if (showBottomSlider) 100.dp else 24.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (endOfChapterTime != null) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = Color(0xFF102017).copy(alpha = 0.95f),
                        border = BorderStroke(0.8.dp, HiddenForest.Accent.copy(alpha = 0.5f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                color = HiddenForest.Accent,
                                modifier = Modifier.size(12.dp)
                            )
                            Text(
                                text = "Fin de capítulo — Avanzando...",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 11.5.sp
                                ),
                                color = HiddenForest.Text
                            )
                        }
                    }
                }

                ReaderTeleprompterPill(
                    wpm = autoScrollWpm,
                    isPaused = isAutoScrollManuallyPaused || isFingerDown || (System.currentTimeMillis() - lastTouchUpTime < 1200L),
                    hazeState = hazeState,
                    onDecreaseWpm = { viewModel.setAutoScrollWpm(autoScrollWpm - 10) },
                    onIncreaseWpm = { viewModel.setAutoScrollWpm(autoScrollWpm + 10) },
                    onTogglePause = { viewModel.toggleAutoScrollPause() },
                    onClose = { viewModel.toggleAutoScroll() }
                )
            }
        }

        // CAPA 3: Hoja de Control Multifuncion (Audio, Notas, Diseño)
        if (showAudioSheet) {
            ReaderControlBottomSheet(
                activeTab = activeTab,
                onTabChange = { activeTab = it },
                bookTitle = successState?.book?.title ?: "",
                bookAuthor = successState?.book?.author ?: "",
                // Notes Props
                highlights = highlights,
                bookmarks = bookmarks,
                onHighlightClick = { hl ->
                    val loc = hl.toLocator()
                    if (loc != null) viewModel.goToLocator(navigator, loc)
                    showAudioSheet = false
                },
                onBookmarkClick = { bm ->
                    val loc = bm.toLocator()
                    if (loc != null) viewModel.goToLocator(navigator, loc)
                    showAudioSheet = false
                },
                onDeleteHighlight = { viewModel.deleteHighlight(it) },
                onDeleteBookmark = { viewModel.deleteBookmark(it) },
                // Settings Props
                preferences = userPreferences,
                onThemeChange = { viewModel.onThemeChange(it) },
                onFontSizeChange = { viewModel.onFontSizeChange(it) },
                onLineSpacingChange = { viewModel.onLineSpacingChange(it) },
                onMarginChange = { viewModel.onMarginChange(it) },
                onTextAlignChange = { viewModel.onTextAlignChange(it) },
                onFontFamilyChange = { viewModel.onFontFamilyChange(it) },
                onHyphenationChange = { viewModel.updateHyphenation(it) },
                onVolumeKeysNavigationChange = { viewModel.updateVolumeKeysNavigation(it) },
                onScrollModeChange = { viewModel.onScrollModeChange(it) },
                onPageTurnAnimationChange = { viewModel.onPageTurnAnimationChange(it) },
                onPageTurnStyleChange = { viewModel.onPageTurnStyleChange(it) },
                onInvertedTapZonesChange = { viewModel.onInvertedTapZonesChange(it) },
                onTtsHighlightStyleChange = { viewModel.updateTtsHighlightStyle(it) },
                hazeState = hazeState,
                onDismiss = { showAudioSheet = false }
            )
        }

        if (showTtsMacroWindow) {
            val lastTtsLocator by viewModel.lastTtsLocator.collectAsStateWithLifecycle()
            ReaderTtsMacroWindow(
                hazeState = hazeState,
                coverPath = successState?.book?.coverCachePath,
                title = successState?.book?.title ?: "",
                author = successState?.book?.author ?: "",
                progress = globalProgress,
                ttsState = ttsState,
                isLoadingTtsModel = isLoadingTtsModel,
                currentSpeed = currentSpeed,
                currentLanguage = userPreferences.ttsLanguage,
                ttsAutoScroll = userPreferences.ttsAutoScroll,
                availableVoices = availableVoices,
                currentVoiceName = userPreferences.ttsVoiceName,
                onVoiceChange = { viewModel.updateTtsVoiceName(it) },
                onOpenRules = { showTtsRulesSheet = true },
                onPlay = { 
                    if (ttsState == TtsState.IDLE || ttsState == TtsState.STOPPED) {
                        viewModel.onPlay()
                    } else {
                        viewModel.resumeTts()
                    }
                },
                onPause = { viewModel.pauseTts() },
                onStop = { viewModel.stopTts() },
                onSpeedChange = { s -> viewModel.onSpeedChange(s) },
                onLanguageChange = { lang -> viewModel.onLanguageChange(lang) },
                onTtsAutoScrollChange = { enabled -> viewModel.onTtsAutoScrollChange(enabled) },
                useSherpaTts = userPreferences.useSherpaTts,
                onUseSherpaTtsChange = { enabled -> viewModel.updateUseSherpaTts(enabled) },
                sherpaDownloadState = sherpaDownloadState,
                currentPitch = userPreferences.ttsPitch,
                onPitchChange = { pitch -> viewModel.onPitchChange(pitch) },
                onSkipSentenceForward = { viewModel.skipTtsSentenceForward() },
                onSkipSentenceBackward = { viewModel.skipTtsSentenceBackward() },
                onSkipChapterForward = { viewModel.skipTtsChapterForward() },
                onSkipChapterBackward = { viewModel.skipTtsChapterBackward() },
                onSleepTimer = { mins -> viewModel.startSleepTimer(mins) },
                onCancelSleepTimer = { viewModel.cancelSleepTimer() },
                sleepTimerRemaining = sleepTimerRemaining,
                activeAmbiencePreset = viewModel.activeAmbiencePreset,
                onToggleNextAmbience = { viewModel.toggleNextAmbience() },
                onInstallTtsPresetPack = { pack ->
                    viewModel.installTtsPresetPackWithResult(pack)
                },
                currentSentenceFlow = viewModel.currentTtsSentence,
                currentTtsRangeFlow = viewModel.currentTtsRange,
                chapterInfoFlow = viewModel.chapterInfo,
                ttsUiStyle = userPreferences.ttsUiStyle,
                onTtsUiStyleChange = { viewModel.updateTtsUiStyle(it) },
                ttsHighlightStyle = userPreferences.ttsHighlightStyle,
                onTtsHighlightStyleChange = { viewModel.updateTtsHighlightStyle(it) },
                hasLastPosition = lastTtsLocator != null,
                onResumeLast = {
                    viewModel.playTtsFromLast()
                    showTtsMacroWindow = false
                },
                selectedSherpaVoice = selectedSherpaVoice,
                onSelectSherpaVoice = { viewModel.selectOrDownloadSherpaVoice(it) },
                isSherpaVoiceDownloaded = { viewModel.isSherpaVoiceDownloaded(it) },
                onDeleteSherpaVoice = { viewModel.deleteSherpaVoiceFromReader(it) },
                audioExportState = audioExportState,
                onExportChapterAudio = { viewModel.exportCurrentChapterToAudio() },
                onExportAudioChapters = { viewModel.exportAudioChapters(it) },
                onCancelAudioExport = { viewModel.cancelAudioExport() },
                onResetAudioExportState = { viewModel.resetAudioExportState() },
                useEdgeNeuralTts = useEdgeNeuralTts,
                onUseEdgeNeuralTtsChange = { viewModel.setUseEdgeNeuralTts(it) },
                selectedEdgeVoice = selectedEdgeVoice,
                onSelectEdgeVoice = { viewModel.selectEdgeVoice(it) },
                proLicenseManager = viewModel.proLicenseManager,
                keepAliveManager = viewModel.woodsKeepAliveManager,
                onDismiss = { showTtsMacroWindow = false }
            )
        }

        if (showSherpaDownloadDialog) {
            AlertDialog(
                onDismissRequest = { viewModel.dismissSherpaDownloadDialog() },
                title = {
                    Text(
                        "Descargar Voz Neuronal (IA)",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = HiddenForest.Text
                    )
                },
                text = {
                    Text(
                        "La voz neural de alta calidad (Sherpa ONNX) se ejecuta 100% offline en tu dispositivo.\n\nRequiere una descarga única de aproximadamente 64 MB. ¿Deseas descargarla ahora?",
                        style = MaterialTheme.typography.bodyMedium,
                        color = HiddenForest.TextSecondary
                    )
                },
                confirmButton = {
                    Button(
                        onClick = { viewModel.downloadSherpaModel() },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = HiddenForest.Accent,
                            contentColor = HiddenForest.Background
                        )
                    ) {
                        Text("Descargar (~64 MB)", fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { viewModel.dismissSherpaDownloadDialog() }) {
                        Text("Cancelar", color = HiddenForest.TextLow)
                    }
                },
                containerColor = HiddenForest.Surface,
                shape = RoundedCornerShape(16.dp)
            )
        }

        if (showToc && successState != null) {
            TocBottomSheet(
                publication = successState.publication,
                currentHref = currentLocator?.href?.toString(),
                hazeState = hazeState,
                onChapterClick = { link ->
                    val locator = successState.publication.locatorFromLink(link)
                    val isPdf = successState.book.format.name == "PDF"
                    val targetPage = if (isPdf) resolveTocTargetPage(link, locator) else locator?.locations?.position

                    val finalLocator = locator ?: run {
                        val readingOrderLink = successState.publication.readingOrder.firstOrNull()
                        if (readingOrderLink != null && targetPage != null) {
                            successState.publication.locatorFromLink(readingOrderLink)?.copy(
                                locations = Locator.Locations(position = targetPage)
                            )
                        } else null
                    }

                    if (finalLocator != null) {
                        (navigator as? VisualNavigator)?.go(finalLocator, animated = true)
                    }

                    if (isReflowMode && targetPage != null) {
                        viewModel.jumpToReflowPage(targetPage)
                    }
                    showToc = false
                },
                onDismiss = { showToc = false }
            )
        }

        if (showSearch && successState != null) {
            SearchBottomSheet(
                publication = successState.publication,
                initialQuery = initialSearchQuery,
                hazeState = hazeState,
                onResultClick = { locator ->
                    (navigator as? VisualNavigator)?.go(locator, animated = true)
                    showSearch = false
                    initialSearchQuery = ""
                },
                onDismiss = {
                    showSearch = false
                    initialSearchQuery = ""
                }
            )
        }

        if (showXRaySheet) {
            ReaderXRayBottomSheet(
                report = xRayReport,
                hazeState = hazeState,
                onSearchInBook = { query ->
                    initialSearchQuery = query
                    showSearch = true
                    showXRaySheet = false
                },
                onDismiss = { showXRaySheet = false }
            )
        }

        // Popup de Nota al Pie
        footnoteContent?.let { content ->
            FootnoteSheet(
                content = content,
                hazeState = hazeState,
                onDismiss = { footnoteContent = null }
            )
        }

        // CAPA 5: TTS Controls (style-dependent)
        val chapterProgressForTts = chapterInfoForTts.progress

        val floatingBottomPadding by animateDpAsState(
            targetValue = if (showControls) 136.dp else 28.dp,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "floatingBottomPadding"
        )

        ReaderTtsControlsLayer(
            ttsUiStyle = userPreferences.ttsUiStyle,
            isTtsActive = isTtsActive,
            showTtsMacroWindow = showTtsMacroWindow,
            ttsState = ttsState,
            isLoadingTtsModel = isLoadingTtsModel,
            currentSentenceFlow = viewModel.currentTtsSentence,
            currentTtsRangeFlow = viewModel.currentTtsRange,
            chapterInfoFlow = viewModel.chapterInfo,
            currentSpeed = currentSpeed,
            chapterProgressForTts = chapterProgressForTts,
            isReflowMode = isReflowMode,
            floatingBottomPadding = floatingBottomPadding,
            hazeState = hazeState,
            onPlay = { viewModel.onPlay() },
            onResume = { viewModel.resumeTts() },
            onPause = { viewModel.pauseTts() },
            onStop = { viewModel.stopTts() },
            onSkipSentenceForward = { viewModel.skipTtsSentenceForward() },
            onSkipSentenceBackward = { viewModel.skipTtsSentenceBackward() },
            onSkipWordForward = { viewModel.skipTtsWordForward() },
            onSkipWordBackward = { viewModel.skipTtsWordBackward() },
            onSkipChapterForward = { viewModel.skipTtsChapterForward() },
            onSkipChapterBackward = { viewModel.skipTtsChapterBackward() },
            onSpeedUp = { viewModel.ttsSpeedUp() },
            onSpeedDown = { viewModel.ttsSpeedDown() },
            onSpeedChange = { s -> viewModel.onSpeedChange(s) },
            onUpdateTtsUiStyle = { viewModel.updateTtsUiStyle(it) },
            onLockScreen = { isTtsScreenLocked = true },
            onOpenMacroWindow = { showTtsMacroWindow = true },
            modifier = Modifier.align(if (userPreferences.ttsUiStyle == TtsUiStyle.BAR) Alignment.BottomCenter else Alignment.BottomEnd)
        )

        // CAPA HERO: Animacion Premium Inmersiva
        ReaderHeroSplash(
            uiState = uiState,
            heroBook = heroBook,
            sharedTransitionScope = sharedTransitionScope,
            animatedVisibilityScope = animatedVisibilityScope
        )

        PullToChapterIndicator(
            progress = pullProgress,
            hazeState = hazeState,
            modifier = Modifier.align(Alignment.Center)
        )



        // BottomSheets para Diccionario y Traducción
        ReaderExternalLookupSheets(
            showDictionarySheet = showDictionarySheet,
            showTranslateSheet = showTranslateSheet,
            selectedTextForQuery = selectedTextForQuery,
            onDismissDictionary = { showDictionarySheet = false },
            onDismissTranslate = { showTranslateSheet = false }
        )

        if (showTtsRulesSheet) {
            TtsRulesBottomSheet(
                rules = ttsRules,
                onSaveRule = { viewModel.saveTtsRule(it) },
                onDeleteRule = { viewModel.deleteTtsRule(it) },
                onInstallPresetPack = { pack -> viewModel.installTtsPresetPack(pack) },
                onDismiss = { showTtsRulesSheet = false }
            )
        }

        if (isTtsScreenLocked && successState != null) {
            ReaderTtsLockOverlay(
                modifier = Modifier.zIndex(100f),
                bookTitle = successState.book.title,
                bookAuthor = successState.book.author,
                chapterName = chapterInfoForTts.title ?: "",
                progress = globalProgress,
                isTtsPlaying = ttsState == TtsState.PLAYING,
                hazeState = hazeState,
                onUnlock = { isTtsScreenLocked = false }
            )
        }

    }
}
}

@Composable
fun PullToChapterIndicator(
    progress: Float,
    hazeState: HazeState,
    modifier: Modifier = Modifier
) {
    if (progress == 0f) return
    
    val isForward = progress > 0
    val absProgress = kotlin.math.abs(progress)
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ComposeColor.Black.copy(alpha = 0.2f * absProgress))
    ) {
        Box(
            modifier = Modifier
                .align(if (isForward) Alignment.BottomCenter else Alignment.TopCenter)
                .padding(bottom = if (isForward) 120.dp else 24.dp, top = if (!isForward) 120.dp else 24.dp)
                .graphicsLayer {
                    alpha = absProgress
                    scaleX = 0.8f + (0.2f * absProgress)
                    scaleY = 0.8f + (0.2f * absProgress)
                }
                .hazeChild(
                    state = hazeState,
                    style = ReaderDesign.GlassStyle
                )
                .clip(RoundedCornerShape(32.dp))
                .background(HiddenForest.Surface.copy(alpha = 0.8f))
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        0.0f to Color.White.copy(alpha = 0.2f),
                        1.0f to Color.Transparent
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = if (isForward) Icons.Default.KeyboardArrowDown else Icons.Default.KeyboardArrowUp,
                    contentDescription = null,
                    tint = if (absProgress >= 0.95f) HiddenForest.Accent else HiddenForest.TextSecondary,
                    modifier = Modifier.size(24.dp)
                )
                Text(
                    text = stringResource(if (isForward) R.string.reader_next_chapter else R.string.reader_previous_chapter),
                    style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.Bold),
                    color = if (absProgress >= 0.95f) HiddenForest.Accent else HiddenForest.TextSecondary
                )
            }
        }
    }
}

private const val MENU_ITEM_TTS_ID = 0x7F0A_BEEF
private const val MENU_ITEM_HIGHLIGHT_ID = 0x7F0A_FEED
private const val MENU_ITEM_DICTIONARY_ID = 0x7F0A_FEEC
private const val MENU_ITEM_TRANSLATE_ID = 0x7F0A_FEEB

/**
 * Muestra el contenido de una nota al pie en un BottomSheet sin perder la posiciÃƒÂ³n de lectura.
 * Se activa cuando el usuario toca una referencia [1] en el EPUB.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FootnoteSheet(
    content: String,
    hazeState: HazeState,
    onDismiss: () -> Unit
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        dragHandle = { BottomSheetDefaults.DragHandle(color = HiddenForest.Outline) },
        containerColor = HiddenForest.Surface.copy(alpha = 0.7f),
        shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            .hazeChild(state = hazeState, style = ReaderDesign.GlassStyle)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 48.dp)
        ) {
            Text(
                text = "NOTA",
                style = MaterialTheme.typography.labelSmall,
                color = HiddenForest.Accent,
                letterSpacing = 2.sp,
                modifier = Modifier.padding(bottom = 12.dp)
            )
            Text(
                text = content,
                style = MaterialTheme.typography.bodyMedium.copy(
                    lineHeight = 24.sp,
                    color = HiddenForest.Text
                )
            )
        }
    }
}
