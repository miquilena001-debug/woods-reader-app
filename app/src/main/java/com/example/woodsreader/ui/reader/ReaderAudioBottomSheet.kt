package com.example.woodsreader.ui.reader

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.TabRowDefaults.tabIndicatorOffset
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.horizontalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.ui.theme.HiddenForest
import com.example.woodsreader.ui.theme.Day
import com.example.woodsreader.ui.theme.Sepia
import com.example.woodsreader.ui.theme.Sunset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.flow.StateFlow
import androidx.compose.material.icons.automirrored.filled.FormatAlignLeft
import androidx.compose.material.icons.automirrored.filled.StickyNote2
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.FormatAlignJustify
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.material.icons.automirrored.filled.MenuBook
import com.example.woodsreader.model.ScrollMode
import com.example.woodsreader.model.PageTurnStyle

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReaderControlBottomSheet(
    activeTab: ReaderTab,
    onTabChange: (ReaderTab) -> Unit,
    bookTitle: String,
    bookAuthor: String,
    // Notes Props
    highlights: List<com.example.woodsreader.data.database.HighlightEntity>,
    bookmarks: List<com.example.woodsreader.data.database.BookmarkEntity>,
    onHighlightClick: (com.example.woodsreader.data.database.HighlightEntity) -> Unit,
    onBookmarkClick: (com.example.woodsreader.data.database.BookmarkEntity) -> Unit,
    onDeleteHighlight: (com.example.woodsreader.data.database.HighlightEntity) -> Unit,
    onDeleteBookmark: (com.example.woodsreader.data.database.BookmarkEntity) -> Unit,
    // Settings Props
    preferences: com.example.woodsreader.data.datastore.UserPreferences,
    onThemeChange: (com.example.woodsreader.model.ReaderTheme) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onMarginChange: (Float) -> Unit,
    onTextAlignChange: (String) -> Unit,
    onFontFamilyChange: (String) -> Unit,
    onHyphenationChange: (Boolean) -> Unit,
    onVolumeKeysNavigationChange: (Boolean) -> Unit,
    onScrollModeChange: (ScrollMode) -> Unit = {},
    onPageTurnAnimationChange: (Boolean) -> Unit = {},
    onPageTurnStyleChange: (PageTurnStyle) -> Unit = {},
    onInvertedTapZonesChange: (Boolean) -> Unit = {},
    hazeState: HazeState,
    onDismiss: () -> Unit
) {

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = HiddenForest.Surface.copy(alpha = 0.75f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = HiddenForest.Outline) },
        shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
        modifier = Modifier
            .clip(RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
            .hazeChild(
                state = hazeState,
                shape = RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp),
                style = ReaderDesign.GlassStyle
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 24.dp)
        ) {
            // ── TAB SELECTOR ─────────────────────────────────────────────────────
            TabRow(
                selectedTabIndex = activeTab.ordinal,
                containerColor = Color.Transparent,
                contentColor = HiddenForest.Accent,
                indicator = { tabPositions ->
                    TabRowDefaults.SecondaryIndicator(
                        Modifier.tabIndicatorOffset(tabPositions[activeTab.ordinal]),
                        color = HiddenForest.Accent
                    )
                },
                divider = { HorizontalDivider(color = HiddenForest.Outline.copy(alpha = 0.1f)) }
            ) {
                val availableTabs = listOf(ReaderTab.TEXT, ReaderTab.NOTES)
                availableTabs.forEach { tab ->
                    Tab(
                        selected = activeTab == tab,
                        onClick = { onTabChange(tab) },
                        text = { 
                            Text(
                                text = when(tab) {
                                    ReaderTab.NOTES -> "NOTAS"
                                    ReaderTab.TEXT -> "DISEÑO"
                                    else -> ""
                                },
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (activeTab == tab) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        icon = {
                            Icon(
                                imageVector = when(tab) {
                                    ReaderTab.NOTES -> Icons.AutoMirrored.Filled.StickyNote2
                                    ReaderTab.TEXT -> Icons.Default.FormatSize
                                    else -> Icons.Default.FormatSize
                                },
                                contentDescription = null,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    )
                }
            }

            // ── TAB CONTENT ──────────────────────────────────────────────────────
            Box(modifier = Modifier.fillMaxWidth().heightIn(min = 340.dp)) {
                AnimatedContent(
                    targetState = activeTab,
                    transitionSpec = {
                        fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(220))
                    },
                    label = "tabTransition"
                ) { targetTab ->
                    when (targetTab) {
                        ReaderTab.AUDIO -> { }
                        ReaderTab.NOTES -> {
                            ReaderNotesTab(
                                hazeState = hazeState,
                                highlights = highlights,
                                bookmarks = bookmarks,
                                bookTitle = bookTitle,
                                bookAuthor = bookAuthor,
                                onHighlightClick = onHighlightClick,
                                onBookmarkClick = onBookmarkClick,
                                onDeleteHighlight = onDeleteHighlight,
                                onDeleteBookmark = onDeleteBookmark
                            )
                        }
                        ReaderTab.TEXT -> {
                            TextDetailedSettings(
                                preferences = preferences,
                                onThemeChange = onThemeChange,
                                onFontSizeChange = onFontSizeChange,
                                onLineSpacingChange = onLineSpacingChange,
                                onMarginChange = onMarginChange,
                                onFontFamilyChange = onFontFamilyChange,
                                onTextAlignChange = onTextAlignChange,
                                onHyphenationChange = onHyphenationChange,
                                volumeKeysNavigation = preferences.volumeKeysNavigation,
                                onVolumeKeysNavigationChange = onVolumeKeysNavigationChange,
                                onScrollModeChange = onScrollModeChange,
                                onPageTurnAnimationChange = onPageTurnAnimationChange,
                                onPageTurnStyleChange = onPageTurnStyleChange,
                                onInvertedTapZonesChange = onInvertedTapZonesChange
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TextDetailedSettings(
    preferences: com.example.woodsreader.data.datastore.UserPreferences,
    onThemeChange: (com.example.woodsreader.model.ReaderTheme) -> Unit,
    onFontSizeChange: (Float) -> Unit,
    onLineSpacingChange: (Float) -> Unit,
    onMarginChange: (Float) -> Unit,
    onTextAlignChange: (String) -> Unit,
    onFontFamilyChange: (String) -> Unit,
    onHyphenationChange: (Boolean) -> Unit,
    volumeKeysNavigation: Boolean,
    onVolumeKeysNavigationChange: (Boolean) -> Unit,
    onScrollModeChange: (ScrollMode) -> Unit,
    onPageTurnAnimationChange: (Boolean) -> Unit,
    onPageTurnStyleChange: (PageTurnStyle) -> Unit,
    onInvertedTapZonesChange: (Boolean) -> Unit
) {
    val scrollState = rememberScrollState()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(bottom = 64.dp, top = 20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // ═══ SECCIÓN 1: TIPOGRAFÍA ═══
        DesignSectionCard(title = "Tipografía") {
            // Glass stepper
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Tamaño", style = MaterialTheme.typography.labelSmall, color = HiddenForest.TextLow)
                    Text("${preferences.fontSizeSp.toInt()} pt",
                        style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Bold),
                        color = HiddenForest.Text)
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(HiddenForest.Background)
                        .border(1.dp, HiddenForest.Outline.copy(alpha = 0.5f), RoundedCornerShape(16.dp))
                ) {
                    IconButton(onClick = { onFontSizeChange((preferences.fontSizeSp - 1f).coerceIn(8f, 40f)) }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Remove, null, tint = HiddenForest.Accent, modifier = Modifier.size(20.dp))
                    }
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(HiddenForest.Outline.copy(0.4f)))
                    Text(text = "${preferences.fontSizeSp.toInt()}",
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.ExtraBold, fontSize = 20.sp),
                        color = HiddenForest.Accent, modifier = Modifier.padding(horizontal = 20.dp))
                    Box(modifier = Modifier.width(1.dp).height(24.dp).background(HiddenForest.Outline.copy(0.4f)))
                    IconButton(onClick = { onFontSizeChange((preferences.fontSizeSp + 1f).coerceIn(8f, 40f)) }, modifier = Modifier.size(44.dp)) {
                        Icon(Icons.Default.Add, null, tint = HiddenForest.Accent, modifier = Modifier.size(20.dp))
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // Font picker visual
            Text("Fuente", style = MaterialTheme.typography.labelSmall, color = HiddenForest.TextLow)
            Spacer(Modifier.height(10.dp))
            val fontOptions = listOf(
                "serif" to "Serif", "sans-serif" to "Sans", "monospace" to "Mono",
                "lora" to "Lora", "inter" to "Inter", "jetbrains mono" to "JB Mono"
            )
            val fontFamilyMap = mapOf(
                "serif" to androidx.compose.ui.text.font.FontFamily.Serif,
                "sans-serif" to androidx.compose.ui.text.font.FontFamily.SansSerif,
                "monospace" to androidx.compose.ui.text.font.FontFamily.Monospace,
                "lora" to androidx.compose.ui.text.font.FontFamily.Serif,
                "inter" to androidx.compose.ui.text.font.FontFamily.SansSerif,
                "jetbrains mono" to androidx.compose.ui.text.font.FontFamily.Monospace
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())
            ) {
                fontOptions.forEach { (key, displayName) ->
                    FontPreviewCard(
                        fontFamily = fontFamilyMap[key] ?: androidx.compose.ui.text.font.FontFamily.Default,
                        name = displayName,
                        isSelected = preferences.fontFamily == key,
                        onClick = { onFontFamilyChange(key) }
                    )
                }
            }

            Spacer(Modifier.height(20.dp))

            // Alignment picker
            Text("Alineación", style = MaterialTheme.typography.labelSmall, color = HiddenForest.TextLow)
            Spacer(Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(HiddenForest.Background)
                    .border(1.dp, HiddenForest.Outline.copy(alpha = 0.3f), RoundedCornerShape(14.dp))
                    .padding(4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                PremiumAlignButton(selected = preferences.textAlign == "left", onClick = { onTextAlignChange("left") },
                    icon = Icons.AutoMirrored.Filled.FormatAlignLeft, label = "Izquierda", modifier = Modifier.weight(1f))
                PremiumAlignButton(selected = preferences.textAlign == "justify", onClick = { onTextAlignChange("justify") },
                    icon = Icons.Default.FormatAlignJustify, label = "Justificado", modifier = Modifier.weight(1f))
            }
        }

        // ═══ SECCIÓN 2: ESPACIADO ═══
        DesignSectionCard(title = "Espaciado") {
            PremiumSlider(label = "Interlineado", value = preferences.lineSpacingMultiplier,
                range = 0.8f..2.0f, displayFormat = { "%.1f".format(it) }, onValueChange = onLineSpacingChange)
            Spacer(Modifier.height(16.dp))
            PremiumSlider(label = "Márgenes", value = preferences.horizontalMargin,
                range = 0f..40f, displayFormat = { "${it.toInt()}" }, onValueChange = onMarginChange)
        }

        // ═══ SECCIÓN 3: MODO DE DESPLAZAMIENTO ═══
        DesignSectionCard(title = "Desplazamiento") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ScrollModeCard(
                    title = "Paginado",
                    subtitle = "Libro clásico",
                    icon = Icons.AutoMirrored.Filled.MenuBook,
                    isSelected = preferences.scrollMode == ScrollMode.PAGINATED,
                    onClick = { onScrollModeChange(ScrollMode.PAGINATED) },
                    modifier = Modifier.weight(1f)
                )
                ScrollModeCard(
                    title = "Vertical",
                    subtitle = "Continuo / Feed",
                    icon = Icons.Default.SwapVert,
                    isSelected = preferences.scrollMode == ScrollMode.CONTINUOUS_VERTICAL,
                    onClick = { onScrollModeChange(ScrollMode.CONTINUOUS_VERTICAL) },
                    modifier = Modifier.weight(1f)
                )
                ScrollModeCard(
                    title = "Horizontal",
                    subtitle = "Flujo lateral",
                    icon = Icons.Default.SwapHoriz,
                    isSelected = preferences.scrollMode == ScrollMode.CONTINUOUS_HORIZONTAL,
                    onClick = { onScrollModeChange(ScrollMode.CONTINUOUS_HORIZONTAL) },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(Modifier.height(14.dp))
            HorizontalDivider(color = HiddenForest.Outline.copy(alpha = 0.2f))
            Spacer(Modifier.height(14.dp))

            if (preferences.scrollMode == ScrollMode.PAGINATED) {
                Text(
                    "EFECTO DE PÁGINA",
                    style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.2.sp, fontSize = 11.sp, fontWeight = FontWeight.Bold),
                    color = HiddenForest.TextLow,
                    modifier = Modifier.padding(bottom = 10.dp)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    ScrollModeCard(
                        title = "Libro 3D",
                        subtitle = "Físico / Curva",
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        isSelected = preferences.pageTurnStyle == PageTurnStyle.BOOK_FLIP,
                        onClick = { onPageTurnStyleChange(PageTurnStyle.BOOK_FLIP) },
                        modifier = Modifier.weight(1f)
                    )
                    ScrollModeCard(
                        title = "Deslizar",
                        subtitle = "Slide suave",
                        icon = Icons.Default.SwapHoriz,
                        isSelected = preferences.pageTurnStyle == PageTurnStyle.SLIDE,
                        onClick = { onPageTurnStyleChange(PageTurnStyle.SLIDE) },
                        modifier = Modifier.weight(1f)
                    )
                    ScrollModeCard(
                        title = "Rápido",
                        subtitle = "Sin animación",
                        icon = Icons.Default.Bolt,
                        isSelected = preferences.pageTurnStyle == PageTurnStyle.NONE,
                        onClick = { onPageTurnStyleChange(PageTurnStyle.NONE) },
                        modifier = Modifier.weight(1f)
                    )
                }
                Spacer(Modifier.height(14.dp))
                HorizontalDivider(color = HiddenForest.Outline.copy(alpha = 0.2f))
                Spacer(Modifier.height(14.dp))
            }

            PremiumToggleRow(
                label = "Modo zurdos",
                subtitle = "Invertir zonas táctiles (avance/retroceso)",
                icon = Icons.Default.TouchApp,
                checked = preferences.invertedTapZones,
                onCheckedChange = onInvertedTapZonesChange
            )
        }

        // ═══ SECCIÓN 4: INTERACCIÓN ═══
        DesignSectionCard(title = "Interacción") {
            PremiumToggleRow(label = "Botones de Volumen", subtitle = "Pasar página con ±volumen",
                icon = Icons.AutoMirrored.Filled.VolumeUp, checked = volumeKeysNavigation, onCheckedChange = onVolumeKeysNavigationChange)
            HorizontalDivider(modifier = Modifier.padding(vertical = 14.dp), color = HiddenForest.Outline.copy(alpha = 0.2f))
            PremiumToggleRow(label = "Guiones automáticos", subtitle = "Separa sílabas al final de línea",
                icon = Icons.Default.TextFields, checked = preferences.hyphenation, onCheckedChange = onHyphenationChange)
        }

        // ═══ SECCIÓN 4: TEMAS ═══
        Text("TEMA DE LA PÁGINA",
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp),
            color = HiddenForest.TextLow)

        val themes = listOf(
            Triple(com.example.woodsreader.model.ReaderTheme.HIDDEN_FOREST, HiddenForest.Background, HiddenForest.Text),
            Triple(com.example.woodsreader.model.ReaderTheme.DAY,           Day.Background,          Day.Text),
            Triple(com.example.woodsreader.model.ReaderTheme.SEPIA,         Sepia.Background,        Sepia.Text),
            Triple(com.example.woodsreader.model.ReaderTheme.AMOLED,        Color.Black,             Color.White),
            Triple(com.example.woodsreader.model.ReaderTheme.SUNSET,        Sunset.Background,       Sunset.Text)
        )
        val themeLabels = mapOf(
            com.example.woodsreader.model.ReaderTheme.HIDDEN_FOREST to "Bosque",
            com.example.woodsreader.model.ReaderTheme.DAY to "Día",
            com.example.woodsreader.model.ReaderTheme.SEPIA to "Sepia",
            com.example.woodsreader.model.ReaderTheme.AMOLED to "OLED",
            com.example.woodsreader.model.ReaderTheme.SUNSET to "Ocaso"
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            themes.forEach { (theme, bg, textCol) ->
                ThemePreviewCard(
                    modifier = Modifier.weight(1f),
                    bgColor = bg, textColor = textCol,
                    label = themeLabels[theme] ?: "",
                    isSelected = preferences.theme == theme,
                    onClick = { onThemeChange(theme) }
                )
            }
        }
    }
}

// ── Design Section Card ────────────────────────────────────────────────────────
@Composable
private fun DesignSectionCard(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier.fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(HiddenForest.SurfaceVariant)
            .border(1.dp, HiddenForest.Outline.copy(alpha = 0.2f), RoundedCornerShape(20.dp))
            .padding(18.dp)
    ) {
        Text(text = title.uppercase(),
            style = MaterialTheme.typography.labelSmall.copy(letterSpacing = 1.5.sp, fontWeight = FontWeight.Bold),
            color = HiddenForest.Accent, modifier = Modifier.padding(bottom = 16.dp))
        content()
    }
}

// ── Premium Slider ─────────────────────────────────────────────────────────────
@Composable
private fun PremiumSlider(
    label: String, value: Float,
    range: ClosedFloatingPointRange<Float>,
    displayFormat: (Float) -> String,
    onValueChange: (Float) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = HiddenForest.Text)
            Box(
                modifier = Modifier.clip(RoundedCornerShape(8.dp))
                    .background(HiddenForest.Accent.copy(alpha = 0.15f))
                    .border(1.dp, HiddenForest.Accent.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text(displayFormat(value), style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold), color = HiddenForest.Accent)
            }
        }
        Spacer(Modifier.height(4.dp))
        Slider(value = value, onValueChange = onValueChange, valueRange = range,
            colors = SliderDefaults.colors(
                thumbColor = HiddenForest.Accent, activeTrackColor = HiddenForest.Accent,
                inactiveTrackColor = HiddenForest.Outline.copy(alpha = 0.25f),
                activeTickColor = Color.Transparent, inactiveTickColor = Color.Transparent
            ), modifier = Modifier.fillMaxWidth())
    }
}

// ── Font Preview Card ──────────────────────────────────────────────────────────
@Composable
private fun FontPreviewCard(
    fontFamily: androidx.compose.ui.text.font.FontFamily,
    name: String, isSelected: Boolean, onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        if (isSelected) HiddenForest.Accent.copy(alpha = 0.18f) else HiddenForest.Background,
        tween(250), label = "fontBg")
    val borderColor by animateColorAsState(
        if (isSelected) HiddenForest.Accent else HiddenForest.Outline.copy(alpha = 0.3f),
        tween(250), label = "fontBorder")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
        modifier = Modifier.width(72.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(bgColor)
            .border(1.5.dp, borderColor, RoundedCornerShape(14.dp))
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 8.dp)
    ) {
        Text("Aa", fontFamily = fontFamily,
            style = MaterialTheme.typography.headlineSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal, fontSize = 22.sp),
            color = if (isSelected) HiddenForest.Accent else HiddenForest.Text)
        Text(name, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = if (isSelected) HiddenForest.Accent else HiddenForest.TextLow, textAlign = TextAlign.Center)
    }
}

// ── Premium Align Button ───────────────────────────────────────────────────────
@Composable
private fun PremiumAlignButton(selected: Boolean, onClick: () -> Unit,
    icon: ImageVector, label: String, modifier: Modifier = Modifier) {
    val bgColor by animateColorAsState(
        if (selected) HiddenForest.Accent.copy(alpha = 0.2f) else Color.Transparent, tween(220), label = "alignBg")
    Column(
        modifier = modifier.clip(RoundedCornerShape(10.dp)).background(bgColor).clickable { onClick() }.padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Icon(icon, label, tint = if (selected) HiddenForest.Accent else HiddenForest.TextLow, modifier = Modifier.size(22.dp))
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = if (selected) HiddenForest.Accent else HiddenForest.TextLow)
    }
}

// ── Premium Toggle Row ─────────────────────────────────────────────────────────
@Composable
private fun PremiumToggleRow(
    label: String, subtitle: String, icon: ImageVector,
    checked: Boolean, onCheckedChange: (Boolean) -> Unit
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
            Box(
                modifier = Modifier.size(36.dp).clip(RoundedCornerShape(10.dp))
                    .background(if (checked) HiddenForest.Accent.copy(alpha = 0.15f) else HiddenForest.Background),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, null, tint = if (checked) HiddenForest.Accent else HiddenForest.TextLow, modifier = Modifier.size(18.dp))
            }
            Column {
                Text(label, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium), color = HiddenForest.Text)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = HiddenForest.TextLow)
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White, checkedTrackColor = HiddenForest.Accent,
                uncheckedThumbColor = HiddenForest.TextLow, uncheckedTrackColor = HiddenForest.Background
            ))
    }
}

// ── Theme Preview Card ─────────────────────────────────────────────────────────
@Composable
private fun ThemePreviewCard(
    modifier: Modifier = Modifier, bgColor: Color, textColor: Color,
    label: String, isSelected: Boolean, onClick: () -> Unit
) {
    val borderColor by animateColorAsState(
        if (isSelected) HiddenForest.Accent else Color.Transparent, tween(250), label = "themeBorder")
    val scale by animateFloatAsState(if (isSelected) 1.05f else 1f, tween(250), label = "themeScale")

    Column(
        modifier = modifier.graphicsLayer { scaleX = scale; scaleY = scale }.clickable { onClick() },
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(
            modifier = Modifier.fillMaxWidth().aspectRatio(0.65f)
                .clip(RoundedCornerShape(14.dp)).background(bgColor)
                .border(2.dp, borderColor, RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center
        ) {
            Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("Aa", style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold, fontSize = 14.sp), color = textColor)
                repeat(3) {
                    Box(modifier = Modifier.fillMaxWidth(if (it == 2) 0.6f else 1f).height(2.dp)
                        .clip(RoundedCornerShape(1.dp)).background(textColor.copy(alpha = 0.3f)))
                }
            }
            if (isSelected) {
                Box(modifier = Modifier.align(Alignment.TopEnd).padding(5.dp).size(16.dp)
                    .clip(CircleShape).background(HiddenForest.Accent), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Check, null, tint = Color.White, modifier = Modifier.size(10.dp))
                }
            }
        }
        Text(label, style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = if (isSelected) HiddenForest.Accent else HiddenForest.TextLow,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal)
    }
}

// ── Scroll Mode Selector Card ──────────────────────────────────────────────────
@Composable
private fun ScrollModeCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bgColor by animateColorAsState(
        if (isSelected) HiddenForest.Accent.copy(alpha = 0.15f) else HiddenForest.Background,
        tween(250), label = "scrollModeBg"
    )
    val borderColor by animateColorAsState(
        if (isSelected) HiddenForest.Accent else HiddenForest.Outline.copy(alpha = 0.3f),
        tween(250), label = "scrollModeBorder"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(bgColor)
            .border(
                width = if (isSelected) 1.5.dp else 1.dp,
                color = borderColor,
                shape = RoundedCornerShape(16.dp)
            )
            .clickable(onClick = onClick)
            .padding(10.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (isSelected) HiddenForest.Accent else HiddenForest.TextLow,
            modifier = Modifier.size(24.dp)
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.labelMedium.copy(fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium),
            color = if (isSelected) HiddenForest.Accent else HiddenForest.Text,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 9.sp),
            color = HiddenForest.TextLow,
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

