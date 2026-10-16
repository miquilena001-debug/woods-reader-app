package com.example.woodsreader.ui.reader

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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



private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

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
    
    val interactionSource = remember { MutableInteractionSource() }
    val isUserInteracting by interactionSource.collectIsPressedAsState()
    
    val currentTtsLocator by viewModel.currentTtsLocator.collectAsStateWithLifecycle()
    val currentTtsSentence by viewModel.currentTtsSentence.collectAsStateWithLifecycle(initialValue = "")
    val currentTtsRange by viewModel.currentTtsRange.collectAsStateWithLifecycle(initialValue = null)
    val globalProgress by viewModel.globalProgress.collectAsStateWithLifecycle(initialValue = 0f)


    val availableVoices by viewModel.availableVoices.collectAsStateWithLifecycle()
    val ttsRules by viewModel.ttsRules.collectAsStateWithLifecycle()
    val bookTimeRemaining by viewModel.bookTimeRemaining.collectAsStateWithLifecycle()
    val chapterTimeRemaining by viewModel.chapterTimeRemaining.collectAsStateWithLifecycle()
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

    androidx.lifecycle.compose.LifecycleEventEffect(androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
        viewModel.saveCurrentProgressBeforeExit()
    }

    var showHighlightMenu by remember { mutableStateOf<Selection?>(null) }
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
    var showDictionarySheet by remember { mutableStateOf(false) }
    var showTranslateSheet by remember { mutableStateOf(false) }
    var selectedTextForQuery by remember { mutableStateOf("") }
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
        showHighlightMenu != null ||
        activeActionMode != null
    val isSelectionModeActiveState = rememberUpdatedState(isSelectionModeActive)

    val clearActiveSelection: () -> Unit = {
        lookupState = LookupResult.Idle
        isLongPressSelecting = false
        try {
            activeActionMode?.finish()
            activeActionMode = null
        } catch (_: Exception) {}
        try {
            (navigatorState.value as? SelectableNavigator)?.clearSelection()
        } catch (_: Exception) {}
        showHighlightMenu = null
        val pubView = (navigatorState.value as? VisualNavigator)?.safePublicationView
        if (pubView != null) {
            pubView.parent?.requestDisallowInterceptTouchEvent(false)
            applyToAllWebViews(pubView) { wv ->
                wv.clearFocus()
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
                    delay(180L)
                    repeat(5) { attempt ->
                        val selection =
                            (navigatorState.value as? SelectableNavigator)?.currentSelection()
                        if (selection != null) {
                            showHighlightMenu = selection
                            viewModel.hideControls()
                            isLongPressSelecting = false
                            return@launch
                        }
                        if (attempt < 4) delay(50)
                    }
                }
                return true
            }
            override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                when (item.itemId) {
                    MENU_ITEM_HIGHLIGHT_ID -> {
                        scope.launch {
                            val sel = (navigatorState.value as? SelectableNavigator)?.currentSelection()
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
                            val sel = (navigatorState.value as? SelectableNavigator)?.currentSelection()
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
                            val sel = (navigatorState.value as? SelectableNavigator)?.currentSelection()
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
                            val sel = (navigatorState.value as? SelectableNavigator)?.currentSelection()
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
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
        }
    }

    LaunchedEffect(showHighlightMenu) {
        if (showHighlightMenu != null) {
            activeActionMode?.finish()
            activeActionMode = null
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
    val showEpubCoverPage by viewModel.showEpubCoverPage.collectAsStateWithLifecycle()
    LaunchedEffect(showEpubCoverPage) {
        if (showEpubCoverPage) viewModel.dismissEpubCoverPage()
    }
    val epubPreferences = remember(userPreferences, isRtl) {
        userPreferences.copy(scrollMode = ScrollMode.CONTINUOUS_VERTICAL).toEpubPreferences().let { prefs ->
            if (isRtl) prefs.copy(readingProgression = org.readium.r2.navigator.preferences.ReadingProgression.RTL)
            else prefs
        }
    }

    // Envolvemos TODO en una Box para permitir la capa Splash
    Box(modifier = Modifier.fillMaxSize()) {

    LaunchedEffect(navigatorState.value, epubPreferences) {
        val nav = navigatorState.value ?: return@LaunchedEffect
        if (nav is EpubNavigatorFragment) {
            nav.submitPreferences(epubPreferences)
            nav.safePublicationView?.let { pubView ->
                applyToAllWebViews(pubView) {
                    it.applyCustomFontFamily(userPreferences.fontFamily)
                }
            }
        } else if (nav is PdfNavigatorFragment<*, *>) {
            @Suppress("UNCHECKED_CAST")
            (nav as PdfNavigatorFragment<*, org.readium.adapter.pdfium.navigator.PdfiumPreferences>).submitPreferences(userPreferences.toPdfPreferences())
        }
    }

    LaunchedEffect(userPreferences.fontFamily) {
        val nav = navigatorState.value as? VisualNavigator ?: return@LaunchedEffect
        nav.safePublicationView?.let { pubView ->
            applyToAllWebViews(pubView) {
                it.applyCustomFontFamily(userPreferences.fontFamily)
            }
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
                val isPaginated = false

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
                if (!isSelectionModeActiveState.value) clearActiveSelection()
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
                    
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .border(
                                width = if (ttsState == TtsState.PLAYING && isPdf && !isReflowMode) 2.dp else 0.dp,
                                color = HiddenForest.Accent.copy(alpha = 0.5f)
                            )
                    ) {
                        AndroidViewBinding(FragmentReaderContainerBinding::inflate) {
                            val isPdf = state.book.format.name == "PDF"
                            val isDarkTheme = userPreferences.theme == com.example.woodsreader.model.ReaderTheme.HIDDEN_FOREST || 
                                              userPreferences.theme == com.example.woodsreader.model.ReaderTheme.AMOLED || 
                                              userPreferences.theme == com.example.woodsreader.model.ReaderTheme.SUNSET
                            
                            if (isPdf && isDarkTheme) {
                                val colorMatrix = android.graphics.ColorMatrix(
                                    floatArrayOf(
                                        -1f,  0f,  0f, 0f, 255f,
                                         0f, -1f,  0f, 0f, 255f,
                                         0f,  0f, -1f, 0f, 255f,
                                         0f,  0f,  0f, 1f,   0f
                                    )
                                )
                                val paint = android.graphics.Paint().apply {
                                    colorFilter = android.graphics.ColorMatrixColorFilter(colorMatrix)
                                }
                                this.readerFragmentContainer.setLayerType(android.view.View.LAYER_TYPE_HARDWARE, paint)
                            } else {
                                this.readerFragmentContainer.setLayerType(android.view.View.LAYER_TYPE_NONE, null)
                            }
                            val containerId = this.readerFragmentContainer.id
                            if (fm != null && fm.findFragmentById(containerId) == null) {
                                val initialLocator = state.initialLocator
                                    ?: state.initialLocatorJson?.let {
                                        try { org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject(it)) } catch (_: Exception) { null }
                                    }
                                    ?: state.book.fallbackLocatorHref?.let { href ->
                                        state.publication.readingOrder.find { it.href.toString() == href }?.let { link ->
                                            state.publication.locatorFromLink(link)
                                        }
                                    }
                                    ?: if (state.book.format.name == "PDF" && (state.book.fallbackLocatorPosition ?: 0) > 0) {
                                        val link = state.publication.readingOrder.firstOrNull()
                                        if (link != null) {
                                            state.publication.locatorFromLink(link)?.copy(
                                                locations = org.readium.r2.shared.publication.Locator.Locations(position = state.book.fallbackLocatorPosition)
                                            )
                                        } else null
                                    } else null
                                
                                val magnetProperties = RsProperties()

                                val factory = when (state.book.format.name) {
                                    "EPUB" -> EpubNavigatorFactory(state.publication).createFragmentFactory(
                                        initialLocator = initialLocator,
                                        initialPreferences = epubPreferences,
                                        listener = object : EpubNavigatorFragment.Listener {
                                            override fun onExternalLinkActivated(url: AbsoluteUrl) {}
                                        },
                                        configuration = EpubNavigatorFragment.Configuration(
                                            selectionActionModeCallback = ttsSelectionCallback,
                                            readiumCssRsProperties = magnetProperties
                                        )
                                    )
                                    "PDF" -> PdfNavigatorFragment.createFactory(
                                        publication = state.publication,
                                        initialLocator = initialLocator,
                                        listener = object : PdfNavigatorFragment.Listener {},
                                        pdfEngineProvider = org.readium.adapter.pdfium.navigator.PdfiumEngineProvider(),
                                        preferences = userPreferences.toPdfPreferences()
                                    )
                                    else -> null
                                }
                                if (factory != null) {
                                    fm.fragmentFactory = factory
                                    val fragmentClass = if (state.book.format.name == "EPUB") EpubNavigatorFragment::class.java.name else PdfNavigatorFragment::class.java.name
                                    val newFragment = fm.fragmentFactory.instantiate(context.classLoader, fragmentClass)
                                    fm.commit { replace(containerId, newFragment) }
                                }
                            }
                        }

                        // ── Capa de animación 3D de paso de página ───────────
                        BookPageTurnOverlay(
                            state = pageTurnState,
                            style = userPreferences.pageTurnStyle,
                            theme = userPreferences.theme,
                            modifier = Modifier.fillMaxSize().zIndex(15f)
                        )
                    }
                }
            }
        }

        // CAPA 1: Menu de Resaltado (Opcional)
        val selection = showHighlightMenu
        if (selection != null) {
            val locator = selection.locator
            val highlightedText = locator.text.highlight ?: ""

            HighlightColorMenu(
                hazeState = hazeState,
                selectedText = highlightedText,
                lookupState = lookupState,
                onColorSelected = { color ->
                    viewModel.addHighlight(locator, color, highlightedText)
                    clearActiveSelection()
                },
                onSaveCharacter = { name, role, noteText ->
                    val customNote = if (noteText.isNotBlank()) "[PERSONAJE: $role] $noteText" else "[PERSONAJE: $role]"
                    viewModel.addHighlight(
                        locator = locator,
                        color = com.example.woodsreader.data.model.HighlightColor.BLUE,
                        text = name,
                        note = customNote
                    )
                    clearActiveSelection()
                },
                onPlayTts = {
                    viewModel.playTts(locator)
                    clearActiveSelection()
                    showTtsMacroWindow = true
                },
                onSearch = {
                    initialSearchQuery = highlightedText
                    clearActiveSelection()
                    showSearch = true
                },
                onTranslate = {
                    scope.launch {
                        lookupState = LookupResult.Loading("Traduciendo...")
                        lookupState = inlineLookupManager.translateToSpanish(highlightedText)
                    }
                },
                onDictionary = {
                    scope.launch {
                        lookupState = LookupResult.Loading("Buscando definición...")
                        lookupState = inlineLookupManager.defineTerm(highlightedText)
                    }
                },
                onIgnoreTts = {
                    if (highlightedText.isNotBlank()) {
                        viewModel.addWordExclusionRule(
                            pattern = highlightedText.trim(),
                            isExactMatch = true,
                            applyToAllBooks = false
                        )
                    }
                    clearActiveSelection()
                },
                onErrorOpenBrowser = {
                    val encoded = Uri.encode(highlightedText.trim())
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
                onDismiss = {
                    lookupState = LookupResult.Idle
                    clearActiveSelection()
                }
            )
        }

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

        AnimatedVisibility(
            visible = !isReflowMode && isPdf && (ttsState == TtsState.PLAYING || ttsState == TtsState.PAUSED),
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = ttsHudBottomPadding)
                .zIndex(4f)
        ) {
            Surface(
                color = HiddenForest.Surface.copy(alpha = 0.94f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, HiddenForest.Outline.copy(alpha = 0.35f)),
                shadowElevation = 8.dp,
                modifier = Modifier
                    .widthIn(max = 520.dp)
                    .padding(horizontal = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Headset,
                                contentDescription = null,
                                tint = HiddenForest.Accent,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "PÁGINA $currentTtsPage",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp
                                ),
                                color = HiddenForest.Accent
                            )
                        }
                        if (ttsState == TtsState.PAUSED) {
                            Surface(
                                color = HiddenForest.TextSecondary.copy(alpha = 0.2f),
                                shape = RoundedCornerShape(6.dp)
                            ) {
                                Text(
                                    text = "EN PAUSA",
                                    style = MaterialTheme.typography.labelSmall.copy(fontWeight = FontWeight.SemiBold),
                                    color = HiddenForest.TextSecondary,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }

                    if (currentTtsSentence.isNotBlank()) {
                        val isDarkTheme = userPreferences.theme != com.example.woodsreader.model.ReaderTheme.DAY && 
                                          userPreferences.theme != com.example.woodsreader.model.ReaderTheme.SEPIA
                        val hudHighlightColor = when (userPreferences.ttsHighlightStyle) {
                            TtsHighlightStyle.BACKGROUND_YELLOW -> if (isDarkTheme) Color(0xFFFFD600) else Color(0xFF9E6500)
                            TtsHighlightStyle.BACKGROUND_ACCENT -> if (isDarkTheme) Color(0xFF2ECC71) else Color(0xFF1B663E)
                            TtsHighlightStyle.BACKGROUND_BLUE -> if (isDarkTheme) Color(0xFF2196F3) else Color(0xFF0D47A1)
                            TtsHighlightStyle.BACKGROUND_ORANGE -> if (isDarkTheme) Color(0xFFFF7A45) else Color(0xFFB03A00)
                            TtsHighlightStyle.UNDERLINE -> if (isDarkTheme) HiddenForest.Text else Color(0xFF1B1B1B)
                            TtsHighlightStyle.NONE -> if (isDarkTheme) HiddenForest.Text else Color(0xFF1B1B1B)
                        }
                        val hudBgColor = Color.Transparent
                        val hudUnderline = if (userPreferences.ttsHighlightStyle == TtsHighlightStyle.UNDERLINE) TextDecoration.Underline else TextDecoration.None
                        val hudWeight = if (userPreferences.ttsHighlightStyle == TtsHighlightStyle.NONE) FontWeight.Normal else FontWeight.Medium

                        Surface(
                            color = hudBgColor,
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = currentTtsSentence,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontFamily = LiterataFamily,
                                    fontSize = 14.sp,
                                    lineHeight = 20.sp,
                                    fontWeight = hudWeight
                                ),
                                color = hudHighlightColor,
                                textDecoration = hudUnderline,
                                maxLines = 4,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                            )
                        }
                    }
                }
            }
        }

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
                onOptionClick = { option ->
                    showMasterMenu = false
                    when (option.type) {
                        MasterMenuOptionType.TTS -> showTtsMacroWindow = true
                        MasterMenuOptionType.TOC -> showToc = true
                        MasterMenuOptionType.SEARCH -> showSearch = true
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

        // Popup de Nota al Pie
        footnoteContent?.let { content ->
            FootnoteSheet(
                content = content,
                hazeState = hazeState,
                onDismiss = { footnoteContent = null }
            )
        }

        // CAPA 5: TTS Controls (style-dependent)
        val chapterInfoForTts by viewModel.chapterInfo.collectAsStateWithLifecycle()
        val chapterProgressForTts = chapterInfoForTts.progress

        val floatingBottomPadding by animateDpAsState(
            targetValue = if (showControls) 136.dp else 28.dp,
            animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
            label = "floatingBottomPadding"
        )

        when (userPreferences.ttsUiStyle) {
            TtsUiStyle.BAR -> {
                // Compact bottom bar — Moon+ Reader style
                AnimatedVisibility(
                    visible = isTtsActive && !showTtsMacroWindow,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(50f)
                ) {
                    ReaderTtsBar(
                        ttsState = ttsState,
                        isLoadingTtsModel = isLoadingTtsModel,
                        currentSentenceFlow = viewModel.currentTtsSentence,
                        currentTtsRangeFlow = viewModel.currentTtsRange,
                        chapterInfoFlow = viewModel.chapterInfo,
                        currentSpeed = currentSpeed,
                        hazeState = hazeState,
                        onPlay = {
                            if (ttsState == TtsState.IDLE || ttsState == TtsState.STOPPED) {
                                viewModel.onPlay()
                            } else {
                                viewModel.resumeTts()
                            }
                        },
                        onPause = { viewModel.pauseTts() },
                        onStop = { viewModel.stopTts() },
                        onSkipForward = { viewModel.skipTtsSentenceForward() },
                        onSkipBackward = { viewModel.skipTtsSentenceBackward() },
                        onSkipWordForward = { viewModel.skipTtsWordForward() },
                        onSkipWordBackward = { viewModel.skipTtsWordBackward() },
                        onSkipChapterForward = { viewModel.skipTtsChapterForward() },
                        onSkipChapterBackward = { viewModel.skipTtsChapterBackward() },
                        onLock = { isTtsScreenLocked = true },
                        onExpandSettings = { showTtsMacroWindow = true },
                        onSpeedUp = { viewModel.ttsSpeedUp() },
                        onSpeedDown = { viewModel.ttsSpeedDown() },
                        onSpeedChange = { s -> viewModel.onSpeedChange(s) },
                        onSwipeUp = {
                            viewModel.updateTtsUiStyle(TtsUiStyle.PANEL)
                            showTtsMacroWindow = true
                        },
                        onSwipeDown = {
                            viewModel.updateTtsUiStyle(TtsUiStyle.FLOATING)
                        }
                    )
                }
            }
            TtsUiStyle.FLOATING -> {
                // Floating button with play/pause toggle and progress ring
                ReaderFloatingTtsButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = floatingBottomPadding)
                        .zIndex(20f),
                    ttsState = ttsState,
                    isLoadingTtsModel = isLoadingTtsModel,
                    showTtsMacroWindow = showTtsMacroWindow,
                    isReflowMode = isReflowMode,
                    chapterProgress = chapterProgressForTts,
                    speed = currentSpeed,
                    hazeState = hazeState,
                    onPlayPause = {
                        if (ttsState == TtsState.PLAYING) {
                            viewModel.pauseTts()
                        } else if (ttsState == TtsState.PAUSED) {
                            viewModel.resumeTts()
                        } else if (ttsState == TtsState.IDLE || ttsState == TtsState.STOPPED) {
                            viewModel.onPlay()
                        }
                    },
                    onSpeedClick = {
                        viewModel.updateTtsUiStyle(TtsUiStyle.PANEL)
                        showTtsMacroWindow = true
                    },
                    onStop = { viewModel.stopTts() },
                    onOpenSettings = {
                        showTtsMacroWindow = true
                    }
                )
            }
            TtsUiStyle.PANEL -> {
                // Panel mode: auto-show TTS macro window when TTS starts
                LaunchedEffect(ttsState) {
                    if (ttsState == TtsState.PLAYING && !showTtsMacroWindow) {
                        showTtsMacroWindow = true
                    }
                }
                // Fallback floating button if user dismisses macro window while TTS is active
                ReaderFloatingTtsButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 20.dp, bottom = floatingBottomPadding)
                        .zIndex(20f),
                    ttsState = ttsState,
                    isLoadingTtsModel = isLoadingTtsModel,
                    showTtsMacroWindow = showTtsMacroWindow,
                    isReflowMode = isReflowMode,
                    chapterProgress = chapterProgressForTts,
                    speed = currentSpeed,
                    hazeState = hazeState,
                    onPlayPause = {
                        if (ttsState == TtsState.PLAYING) {
                            viewModel.pauseTts()
                        } else if (ttsState == TtsState.PAUSED) {
                            viewModel.resumeTts()
                        } else if (ttsState == TtsState.IDLE || ttsState == TtsState.STOPPED) {
                            viewModel.onPlay()
                        }
                    },
                    onSpeedClick = {
                        viewModel.updateTtsUiStyle(TtsUiStyle.PANEL)
                        showTtsMacroWindow = true
                    },
                    onStop = { viewModel.stopTts() },
                    onOpenSettings = {
                        showTtsMacroWindow = true
                    }
                )
            }
        }

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
        if (showDictionarySheet) {
            val dictUrl = "https://www.wordreference.com/definicion/" + Uri.encode(selectedTextForQuery.trim())
            ModalBottomSheet(
                onDismissRequest = { showDictionarySheet = false },
                dragHandle = { BottomSheetDefaults.DragHandle(color = HiddenForest.Outline) },
                containerColor = HiddenForest.Surface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Definición: $selectedTextForQuery",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = HiddenForest.Text,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "WordReference",
                                style = MaterialTheme.typography.labelSmall,
                                color = HiddenForest.TextLow
                            )
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(
                                onClick = {
                                    val processTextIntent = Intent().apply {
                                        action = Intent.ACTION_PROCESS_TEXT
                                        type = "text/plain"
                                        putExtra(Intent.EXTRA_PROCESS_TEXT, selectedTextForQuery)
                                        putExtra(Intent.EXTRA_PROCESS_TEXT_READONLY, true)
                                    }
                                    try {
                                        context.startActivity(Intent.createChooser(processTextIntent, "Buscar definición"))
                                    } catch (e: Exception) {
                                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, selectedTextForQuery)
                                        }
                                        try {
                                            context.startActivity(Intent.createChooser(sendIntent, "Buscar"))
                                        } catch (_: Exception) {}
                                    }
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Book,
                                    contentDescription = "App Externa",
                                    tint = HiddenForest.Accent
                                )
                            }
                            IconButton(onClick = { showDictionarySheet = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Cerrar",
                                    tint = HiddenForest.TextSecondary
                                )
                            }
                        }
                    }
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                loadUrl(dictUrl)
                            }
                        },
                        onRelease = { webView ->
                            webView.stopLoading()
                            webView.clearHistory()
                            webView.removeAllViews()
                            webView.destroy()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }

        if (showTranslateSheet) {
            val translateUrl = "https://translate.google.com/?sl=auto&tl=es&text=" + Uri.encode(selectedTextForQuery)
            ModalBottomSheet(
                onDismissRequest = { showTranslateSheet = false },
                dragHandle = { BottomSheetDefaults.DragHandle(color = HiddenForest.Outline) },
                containerColor = HiddenForest.Surface.copy(alpha = 0.95f),
                shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp),
                modifier = Modifier
                    .fillMaxHeight(0.8f)
                    .clip(RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp))
            ) {
                Column(modifier = Modifier.fillMaxSize()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "Traducción",
                                style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                                color = HiddenForest.Text
                            )
                            Text(
                                text = "Google Translate",
                                style = MaterialTheme.typography.labelSmall,
                                color = HiddenForest.TextLow
                            )
                        }
                        IconButton(onClick = { showTranslateSheet = false }) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Cerrar",
                                tint = HiddenForest.TextSecondary
                            )
                        }
                    }
                    AndroidView(
                        factory = { ctx ->
                            WebView(ctx).apply {
                                webViewClient = WebViewClient()
                                settings.javaScriptEnabled = true
                                settings.domStorageEnabled = true
                                loadUrl(translateUrl)
                            }
                        },
                        onRelease = { webView ->
                            webView.stopLoading()
                            webView.clearHistory()
                            webView.removeAllViews()
                            webView.destroy()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }

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
