package com.example.woodsreader.ui.reader

import android.content.Context
import android.content.ContextWrapper
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
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.filled.WbSunny
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



private fun Context.findFragmentActivity(): FragmentActivity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is FragmentActivity) return ctx
        ctx = ctx.baseContext
    }
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
    val isReflowMode by viewModel.isReflowMode.collectAsStateWithLifecycle()
    var showMasterMenu by remember { mutableStateOf(false) }
    
    val userPreferences by viewModel.userPreferences.collectAsStateWithLifecycle()
    val highlights by viewModel.highlights.collectAsStateWithLifecycle()
    val bookmarks by viewModel.bookmarks.collectAsStateWithLifecycle()
    val ttsState by viewModel.ttsState.collectAsStateWithLifecycle()
    val isLoadingTtsModel by viewModel.isLoadingTtsModel.collectAsStateWithLifecycle()
    val sleepTimerRemaining by viewModel.sleepTimerRemaining.collectAsStateWithLifecycle()
    
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

    var showHighlightMenu by remember { mutableStateOf<Selection?>(null) }
    var initialSearchQuery by remember { mutableStateOf("") }
    var lastInteractionTime by remember { mutableLongStateOf(System.currentTimeMillis()) }
    
    val hazeState = remember { HazeState() }
    
    var isTtsScreenLocked by remember { mutableStateOf(false) }
    val isTtsPlaying = ttsState == TtsState.PLAYING
    if (isTtsPlaying || isTtsScreenLocked) {
        val view = LocalView.current
        DisposableEffect(view) {
            view.keepScreenOn = true
            onDispose {
                view.keepScreenOn = false
            }
        }
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

    val context = LocalContext.current
    val haptic = androidx.compose.ui.platform.LocalHapticFeedback.current
    val highlightColors = LocalHighlightColors.current
    val accentColor = HiddenForest.Accent
    val ttsHighlightColor = highlightColors.yellow.copy(alpha = 0.3f).toArgb()
    val fragmentManager = remember(context) { context.findFragmentActivity()?.supportFragmentManager }
    val fm = fragmentManager
    val focusRequester = remember { FocusRequester() }

    val ttsSelectionCallback = remember(viewModel, scope) {
        object : ActionMode.Callback {
            override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                menu.add(Menu.NONE, MENU_ITEM_HIGHLIGHT_ID, Menu.NONE, "✦ Resaltar")
                menu.add(Menu.NONE, MENU_ITEM_TTS_ID, Menu.NONE, "🔊 Narrar")
                menu.add(Menu.NONE, MENU_ITEM_DICTIONARY_ID, Menu.NONE, "📖 Diccionario")
                menu.add(Menu.NONE, MENU_ITEM_TRANSLATE_ID, Menu.NONE, "🌐 Traducir")
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
                        }
                        return true
                    }
                }
                return false
            }
            override fun onDestroyActionMode(mode: ActionMode) {}
            override fun onPrepareActionMode(mode: ActionMode, menu: Menu): Boolean = false
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
        onDispose { fragmentManager.unregisterFragmentLifecycleCallbacks(callback) }
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

    LaunchedEffect(navigatorState.value, epubPreferences) {
        val nav = navigatorState.value ?: return@LaunchedEffect
        if (nav is EpubNavigatorFragment) {
            nav.submitPreferences(epubPreferences)
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

    LaunchedEffect(ttsState, navigatorState.value, isUserInteracting, currentTtsRange, userPreferences.ttsHighlightStyle) {
        val nav = navigatorState.value ?: return@LaunchedEffect
        
        if (ttsState == TtsState.PLAYING) {
            viewModel.currentTtsLocator.collectLatest { locator ->
                if (locator == null) return@collectLatest
                
                val isLockActive = (System.currentTimeMillis() - lastInteractionTime) < 3000L
                if (nav is VisualNavigator && !isUserInteracting && !isLockActive && userPreferences.ttsAutoScroll) {
                    nav.go(locator, animated = true)
                }
                
                if (nav is DecorableNavigator) {
                    val tintColor = when (userPreferences.ttsHighlightStyle) {
                        TtsHighlightStyle.BACKGROUND_YELLOW -> highlightColors.yellow.copy(alpha = 0.35f)
                        TtsHighlightStyle.BACKGROUND_ACCENT -> accentColor.copy(alpha = 0.35f)
                        TtsHighlightStyle.BACKGROUND_BLUE -> highlightColors.blue.copy(alpha = 0.35f)
                        TtsHighlightStyle.BACKGROUND_ORANGE -> androidx.compose.ui.graphics.Color(0xFFFFB74D).copy(alpha = 0.35f)
                        TtsHighlightStyle.UNDERLINE -> accentColor
                        TtsHighlightStyle.NONE -> null
                    }
                    
                    val decorationStyle = if (tintColor != null) {
                        if (userPreferences.ttsHighlightStyle == TtsHighlightStyle.UNDERLINE) {
                            Decoration.Style.Underline(tint = tintColor.toArgb())
                        } else {
                            Decoration.Style.Highlight(tint = tintColor.toArgb())
                        }
                    } else null

                    if (decorationStyle != null) {
                        val currentRange = currentTtsRange
                        val sentence = currentTtsSentence
                        val wordLocator = if (currentRange != null && currentRange.first >= 0 && currentRange.last < sentence.length) {
                            try {
                                locator.copy(
                                    text = locator.text.copy(
                                        highlight = sentence.substring(currentRange),
                                        before = if (currentRange.first > 0) sentence.substring(0, currentRange.first) else "",
                                        after = if (currentRange.last + 1 < sentence.length) sentence.substring(currentRange.last + 1) else ""
                                    )
                                )
                            } catch (_: Exception) {
                                locator
                            }
                        } else {
                            locator
                        }

                        val decoration = Decoration(
                            id = "tts-highlight",
                            locator = wordLocator,
                            style = decorationStyle
                        )
                        nav.applyDecorations(listOf(decoration), "tts")
                    } else {
                        nav.applyDecorations(emptyList(), "tts")
                    }
                }
            }
        } else if (nav is DecorableNavigator && (ttsState == TtsState.STOPPED || ttsState == TtsState.IDLE)) {
             nav.applyDecorations(emptyList(), "tts")
             nav.applyDecorations(emptyList(), "tts-scrub")  // Limpiar también el scrub al detener
        } else if (nav is DecorableNavigator && ttsState == TtsState.PAUSED) {
             nav.applyDecorations(emptyList(), "tts-scrub")  // Al pausar: limpiar scrub, mantener highlight de posición
        }
    }

    val pageTurnState = rememberBookPageTurnState()

    val turnPageForward: (VisualNavigator) -> Unit = { nav ->
        if (userPreferences.scrollMode == ScrollMode.PAGINATED && 
            userPreferences.pageTurnAnimation && 
            userPreferences.pageTurnStyle != PageTurnStyle.NONE
        ) {
            scope.launch {
                pageTurnState.turnPage(
                    view = nav.publicationView,
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

    val turnPageBackward: (VisualNavigator) -> Unit = { nav ->
        if (userPreferences.scrollMode == ScrollMode.PAGINATED && 
            userPreferences.pageTurnAnimation && 
            userPreferences.pageTurnStyle != PageTurnStyle.NONE
        ) {
            scope.launch {
                pageTurnState.turnPage(
                    view = nav.publicationView,
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

    DisposableEffect(navigatorState.value, userPreferences.invertedTapZones, userPreferences.pageTurnStyle, userPreferences.pageTurnAnimation) {
        val nav = navigatorState.value as? VisualNavigator ?: return@DisposableEffect onDispose {}

        val listener = object : InputListener {
            override fun onTap(event: TapEvent): Boolean {
                val tapX = event.point.x
                val viewWidth = nav.publicationView.width.toFloat()
                val relativeX = if (viewWidth > 0f) tapX / viewWidth else 0.5f

                lastInteractionTime = System.currentTimeMillis()
                viewModel.onUserInteraction() 

                val isLeftTap = relativeX < 0.30f
                val isRightTap = relativeX > 0.70f
                val isBackTap = if (userPreferences.invertedTapZones) isRightTap else isLeftTap
                val isForwardTap = if (userPreferences.invertedTapZones) isLeftTap else isRightTap

                return when {
                    isBackTap -> {
                        turnPageBackward(nav)
                        true
                    }
                    isForwardTap -> {
                        turnPageForward(nav)
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
            rootView = nav.publicationView,
            scope = scope,
            onGoForward = { viewModel.goToNextChapter(nav) },
            onGoBackward = { viewModel.goToPreviousChapter(nav) },
            onPullProgress = { pullProgress = it },
            onPageForward = { turnPageForward(nav) },
            onPageBackward = { turnPageBackward(nav) },
            isPaginatedMode = { userPreferences.scrollMode == ScrollMode.PAGINATED }
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
                if (userPreferences.volumeKeysNavigation && event.type == KeyEventType.KeyDown) {
                    when (event.key) {
                        Key.VolumeUp -> {
                            val nav = navigatorState.value as? VisualNavigator
                            if (nav != null) turnPageBackward(nav) else viewModel.pageBackward(navigatorState.value)
                            true
                        }
                        Key.VolumeDown -> {
                            val nav = navigatorState.value as? VisualNavigator
                            if (nav != null) turnPageForward(nav) else viewModel.pageForward(navigatorState.value)
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
                                val initialLocator = state.initialLocatorJson?.let {
                                    try { org.readium.r2.shared.publication.Locator.fromJSON(org.json.JSONObject(it)) } catch (_: Exception) { null }
                                }
                                
                                val magnetProperties = RsProperties(
                                    overrides = mapOf(
                                        "scroll-snap-type" to "y proximity",
                                        "scroll-snap-align" to "start",
                                        "scroll-padding" to "20px"
                                    )
                                )

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
                onColorSelected = { color ->
                    viewModel.addHighlight(locator, color, highlightedText)
                    (navigatorState.value as? SelectableNavigator)?.clearSelection()
                    showHighlightMenu = null
                },
                onPlayTts = {
                    viewModel.playTts(locator)
                    (navigatorState.value as? SelectableNavigator)?.clearSelection()
                    showHighlightMenu = null
                    showTtsMacroWindow = true
                },
                onSearch = {
                    initialSearchQuery = highlightedText
                    (navigatorState.value as? SelectableNavigator)?.clearSelection()
                    showHighlightMenu = null
                    showSearch = true
                },
                onTranslate = {
                    selectedTextForQuery = highlightedText
                    (navigatorState.value as? SelectableNavigator)?.clearSelection()
                    showHighlightMenu = null
                    showTranslateSheet = true
                },
                onDictionary = {
                    selectedTextForQuery = highlightedText
                    (navigatorState.value as? SelectableNavigator)?.clearSelection()
                    showHighlightMenu = null
                    showDictionarySheet = true
                },
                onIgnoreTts = {
                    if (highlightedText.isNotBlank()) {
                        viewModel.addWordExclusionRule(
                            pattern = highlightedText.trim(),
                            isExactMatch = true,
                            applyToAllBooks = false
                        )
                    }
                    (navigatorState.value as? SelectableNavigator)?.clearSelection()
                    showHighlightMenu = null
                },
                onDismiss = { showHighlightMenu = null }
            )
        }

        // CAPA 2: Controles Superpuestos Solidos (TopBar)
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            if (successState != null) {
                ReaderTopBar(
                    title = successState.book.title,
                    ttsState = ttsState,
                    hasSelection = showHighlightMenu != null,
                    hazeState = hazeState,
                    onBack = onNavigateUp,
                    onHighlight = {
                        scope.launch {
                            val nav = navigatorState.value as? SelectableNavigator
                            val sel = nav?.currentSelection()
                            if (sel != null) { showHighlightMenu = sel; viewModel.hideControls() }
                        }
                    },
                    onTtsToggle = { 
                        if (ttsState == TtsState.PLAYING) {
                            viewModel.pauseTts()
                        } else if (ttsState == TtsState.PAUSED) {
                            viewModel.resumeTts()
                        } else {
                            val loc = currentLocator ?: viewModel.getFallbackLocator()
                            if (loc != null) viewModel.playTts(loc) else viewModel.onPlay()
                        }
                    },
                    onMenuClick = { showMasterMenu = true }
                )
            }
        }

        // CAPA 1.5: Modo Reflow (Movil)
        val reflowState by viewModel.reflowState.collectAsStateWithLifecycle()
        val isLoadingMore by viewModel.isLoadingMoreReflow.collectAsStateWithLifecycle()
        // Note: ttsState and currentTtsLocator already collected at the top of ReaderScreen

        // B.2: Modo Reflow ya maneja su propio TTS via TtsManager
        
        val ttsActiveBlockIndex by viewModel.ttsActiveBlockIndex.collectAsStateWithLifecycle()
        
        val currentSpeed by viewModel.currentSpeed.collectAsStateWithLifecycle()
        
        ReaderReflowOverlay(
            modifier = Modifier.zIndex(5f),
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

        // CAPA 1.8: Indicador de Pagina TTS (Solo PDF Normal)
        val currentTtsPage by viewModel.currentTtsPage.collectAsStateWithLifecycle()
        val isPdf = (uiState as? ReaderUiState.Success)?.book?.format?.name == "PDF"
        
        AnimatedVisibility(
            visible = !isReflowMode && isPdf && ttsState == TtsState.PLAYING,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 80.dp).zIndex(4f)
        ) {
            Surface(
                color = HiddenForest.Accent.copy(alpha = 0.9f),
                shape = RoundedCornerShape(20.dp),
                shadowElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Headset,
                        contentDescription = null,
                        tint = Color.Black,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "Leyendo Página $currentTtsPage",
                        style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.Black
                    )
                }
            }
        }

        // CAPA 4: Master Menu (Panel Lateral)
        AnimatedVisibility(
            visible = showMasterMenu,
            modifier = Modifier.zIndex(10f), // Asegurar que estÃƒÂ© por encima de casi todo
            enter = slideInHorizontally(initialOffsetX = { it }) + fadeIn(),
            exit = slideOutHorizontally(targetOffsetX = { it }) + fadeOut()
        ) {
            ReaderSideMenu(
                hazeState = hazeState,
                isReflowAvailable = (uiState as? ReaderUiState.Success)?.book?.format?.name == "PDF",
                currentScrollMode = userPreferences.scrollMode,
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
                            currentLocator?.let { viewModel.addBookmark(it) } 
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
        val isTtsActive = ttsState == TtsState.PLAYING || ttsState == TtsState.PAUSED
        val showBottomSlider = showControls && !(isTtsActive && ttsUiStyle == TtsUiStyle.BAR)
        AnimatedVisibility(
            visible = showBottomSlider,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter)
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
                },
                onBookmarkClick = { bm ->
                    val loc = bm.toLocator()
                    if (loc != null) viewModel.goToLocator(navigator, loc)
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
                hazeState = hazeState,
                onDismiss = { showAudioSheet = false }
            )
        }

        if (showTtsMacroWindow) {
            val lastTtsLocator by viewModel.lastTtsLocator.collectAsStateWithLifecycle()
            ReaderTtsMacroWindow(
                hazeState = hazeState,
                title = successState?.book?.title ?: "",
                author = successState?.book?.author ?: "",
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
                        val loc = currentLocator ?: viewModel.getFallbackLocator()
                        if (loc != null) viewModel.playTts(loc) else viewModel.onPlay()
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
                currentPitch = userPreferences.ttsPitch,
                onPitchChange = { pitch -> viewModel.onPitchChange(pitch) },
                onSkipSentenceForward = { viewModel.skipTtsSentenceForward() },
                onSkipSentenceBackward = { viewModel.skipTtsSentenceBackward() },
                onSleepTimer = { mins -> viewModel.startSleepTimer(mins) },
                onCancelSleepTimer = { viewModel.cancelSleepTimer() },
                sleepTimerRemaining = sleepTimerRemaining,
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
                onDismiss = { showTtsMacroWindow = false }
            )
        }

        if (showToc && successState != null) {
            TocBottomSheet(
                publication = successState.publication,
                currentHref = currentLocator?.href?.toString(),
                hazeState = hazeState,
                onChapterClick = { link ->
                    val locator = successState.publication.locatorFromLink(link)
                    if (locator != null) {
                        (navigator as? VisualNavigator)?.go(locator, animated = true)
                        if (isReflowMode) {
                            val targetPage = locator.locations.position ?: 1
                            viewModel.jumpToReflowPage(targetPage)
                        }
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

        when (userPreferences.ttsUiStyle) {
            TtsUiStyle.BAR -> {
                // Compact bottom bar — Moon+ Reader style
                AnimatedVisibility(
                    visible = isTtsActive && !showTtsMacroWindow && !isReflowMode,
                    enter = fadeIn() + slideInVertically { it },
                    exit = fadeOut() + slideOutVertically { it },
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .zIndex(20f)
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
                                val loc = currentLocator ?: viewModel.getFallbackLocator()
                                if (loc != null) viewModel.playTts(loc) else viewModel.onPlay()
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
                        onLock = { isTtsScreenLocked = true },
                        onExpandSettings = { showTtsMacroWindow = true },
                        onSpeedUp = { viewModel.ttsSpeedUp() },
                        onSpeedDown = { viewModel.ttsSpeedDown() }
                    )
                }
            }
            TtsUiStyle.FLOATING -> {
                // Floating button with play/pause toggle and progress ring
                ReaderFloatingTtsButton(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(end = 24.dp, bottom = 120.dp)
                        .zIndex(20f),
                    ttsState = ttsState,
                    isLoadingTtsModel = isLoadingTtsModel,
                    showTtsMacroWindow = showTtsMacroWindow,
                    isReflowMode = isReflowMode,
                    chapterProgress = chapterProgressForTts,
                    hazeState = hazeState,
                    onPlayPause = {
                        if (ttsState == TtsState.PLAYING) {
                            viewModel.pauseTts()
                        } else if (ttsState == TtsState.PAUSED) {
                            viewModel.resumeTts()
                        } else if (ttsState == TtsState.IDLE || ttsState == TtsState.STOPPED) {
                            val loc = currentLocator ?: viewModel.getFallbackLocator()
                            if (loc != null) viewModel.playTts(loc) else viewModel.onPlay()
                        }
                    },
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
                        .padding(end = 24.dp, bottom = 120.dp)
                        .zIndex(20f),
                    ttsState = ttsState,
                    isLoadingTtsModel = isLoadingTtsModel,
                    showTtsMacroWindow = showTtsMacroWindow,
                    isReflowMode = isReflowMode,
                    chapterProgress = chapterProgressForTts,
                    hazeState = hazeState,
                    onPlayPause = {
                        if (ttsState == TtsState.PLAYING) {
                            viewModel.pauseTts()
                        } else if (ttsState == TtsState.PAUSED) {
                            viewModel.resumeTts()
                        }
                    },
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

        // Overlay de detección de brillo en el borde izquierdo (estilo Moon+ Reader)
        var showBrightnessIndicator by remember { mutableStateOf(false) }
        var currentBrightnessPercent by remember { mutableStateOf(0) }
        var brightnessIndicatorJob by remember { mutableStateOf<Job?>(null) }

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .width(48.dp)
                .align(Alignment.CenterStart)
                .zIndex(30f)
                .pointerInput(Unit) {
                    detectVerticalDragGestures(
                        onDragStart = {
                            showBrightnessIndicator = true
                        },
                        onDragEnd = {
                            brightnessIndicatorJob?.cancel()
                            brightnessIndicatorJob = scope.launch {
                                delay(1200)
                                showBrightnessIndicator = false
                            }
                        },
                        onDragCancel = {
                            showBrightnessIndicator = false
                        },
                        onVerticalDrag = { _, dragAmount ->
                            val delta = -dragAmount / 500f
                            val activity = context.findFragmentActivity()
                            if (activity != null) {
                                val lp = activity.window.attributes
                                val currentVal = if (lp.screenBrightness < 0) {
                                    try {
                                        android.provider.Settings.System.getInt(
                                            activity.contentResolver,
                                            android.provider.Settings.System.SCREEN_BRIGHTNESS
                                        ) / 255f
                                    } catch (e: Exception) {
                                        0.5f
                                    }
                                } else {
                                    lp.screenBrightness
                                }
                                val newVal = (currentVal + delta).coerceIn(0.01f, 1.0f)
                                lp.screenBrightness = newVal
                                activity.window.attributes = lp
                                currentBrightnessPercent = (newVal * 100).toInt()
                            }
                        }
                    )
                }
        )

        // Indicador visual de brillo
        AnimatedVisibility(
            visible = showBrightnessIndicator,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.Center)
                .zIndex(40f)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.8f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier.size(100.dp)
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = Icons.Default.WbSunny,
                        contentDescription = "Brillo",
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "$currentBrightnessPercent%",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = Color.White
                    )
                }
            }
        }

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







