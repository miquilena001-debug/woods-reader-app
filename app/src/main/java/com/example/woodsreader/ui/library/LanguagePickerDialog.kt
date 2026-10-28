package com.example.woodsreader.ui.library

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.woodsreader.ui.theme.HiddenForest

// ─────────────────────────────────────────────────────────────────────────────
//  Modelo de idioma
// ─────────────────────────────────────────────────────────────────────────────

data class SupportedLanguage(
    val code: String,
    val flag: String,
    val name: String,
    val nativeName: String
)

val SUPPORTED_LANGUAGES: List<SupportedLanguage> = listOf(
    SupportedLanguage("auto", "🌐", "Auto-detectar", "Auto"),
    SupportedLanguage("es",   "🇪🇸", "Español",       "Español"),
    SupportedLanguage("en",   "🇬🇧", "Inglés",        "English"),
    SupportedLanguage("pt",   "🇧🇷", "Portugués",     "Português"),
    SupportedLanguage("fr",   "🇫🇷", "Francés",       "Français"),
    SupportedLanguage("de",   "🇩🇪", "Alemán",        "Deutsch"),
    SupportedLanguage("it",   "🇮🇹", "Italiano",      "Italiano"),
    SupportedLanguage("zh",   "🇨🇳", "Chino",         "中文"),
    SupportedLanguage("ja",   "🇯🇵", "Japonés",       "日本語"),
    SupportedLanguage("ko",   "🇰🇷", "Coreano",       "한국어"),
    SupportedLanguage("ar",   "🇸🇦", "Árabe",         "العربية"),
    SupportedLanguage("ru",   "🇷🇺", "Ruso",          "Русский"),
    SupportedLanguage("tr",   "🇹🇷", "Turco",         "Türkçe"),
    SupportedLanguage("id",   "🇮🇩", "Indonesio",     "Indonesia"),
    SupportedLanguage("vi",   "🇻🇳", "Vietnamita",    "Tiếng Việt"),
    SupportedLanguage("th",   "🇹🇭", "Tailandés",     "ภาษาไทย"),
)

/** Solo idiomas de destino (sin Auto-detectar) */
val TARGET_LANGUAGES: List<SupportedLanguage> = SUPPORTED_LANGUAGES.filter { it.code != "auto" }

fun languageByCode(code: String): SupportedLanguage =
    SUPPORTED_LANGUAGES.firstOrNull { it.code == code }
        ?: SupportedLanguage(code, "🌐", code.uppercase(), code)

// ─────────────────────────────────────────────────────────────────────────────
//  Diálogo principal (Apple Liquid Glass)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Selector de idioma de traducción con estética Apple Liquid Glass.
 *
 * @param initialSourceLang código inicial del idioma fuente ("auto", "en", etc.)
 * @param initialTargetLang código inicial del idioma destino ("es", etc.)
 * @param onConfirm callback con (sourceLang, targetLang) confirmados
 * @param onDismiss cerrar el diálogo sin confirmar
 */
@Composable
fun LanguagePickerDialog(
    initialSourceLang: String = "auto",
    initialTargetLang: String = "es",
    onConfirm: (sourceLang: String, targetLang: String) -> Unit,
    onDismiss: () -> Unit
) {
    var sourceLang by remember { mutableStateOf(initialSourceLang) }
    var targetLang by remember { mutableStateOf(initialTargetLang) }

    // Dos pasos: 0 = origen, 1 = destino
    var step by remember { mutableIntStateOf(0) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .wrapContentHeight()
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
        ) {
            Column(
                modifier = Modifier.padding(22.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp)
            ) {

                // ── Header con estilo Apple Glass y botón circular de cierre ──
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
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
                                imageVector = Icons.Default.Translate,
                                contentDescription = null,
                                tint = HiddenForest.Accent,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Configurar Traducción",
                                style = MaterialTheme.typography.titleMedium.copy(
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.2.sp
                                ),
                                color = Color.White
                            )
                            Text(
                                text = "Google Translate · Sin API Key · Gratis",
                                style = MaterialTheme.typography.bodySmall,
                                color = HiddenForest.TextSecondary
                            )
                        }
                    }

                    // Botón circular Apple Glass de cierre
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

                // ── Indicador de par de idiomas (Apple Glass Card) ────────
                TranslationPairIndicator(
                    source = languageByCode(sourceLang),
                    target = languageByCode(targetLang),
                    activeStep = step,
                    onStepClick = { step = it }
                )

                HorizontalDivider(color = Color.White.copy(alpha = 0.08f), thickness = 1.dp)

                // ── Título y badge del paso actual ───────────────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (step == 0) "Idioma original del libro:" else "Traducir al idioma:",
                        style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                        color = Color.White.copy(alpha = 0.85f)
                    )

                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(HiddenForest.Accent.copy(alpha = 0.16f))
                            .border(1.dp, HiddenForest.Accent.copy(alpha = 0.35f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    ) {
                        Text(
                            text = if (step == 0) "PASO 1 DE 2" else "PASO 2 DE 2",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                letterSpacing = 0.6.sp
                            ),
                            color = HiddenForest.Accent
                        )
                    }
                }

                // ── Grid de idiomas (Apple Glass Tiles) ───────────────────
                val languageList = if (step == 0) SUPPORTED_LANGUAGES else TARGET_LANGUAGES
                val selectedCode  = if (step == 0) sourceLang else targetLang

                LazyVerticalGrid(
                    columns = GridCells.Fixed(4),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 300.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(languageList, key = { it.code }) { lang ->
                        LanguageCard(
                            language = lang,
                            isSelected = lang.code == selectedCode,
                            onClick = {
                                if (step == 0) sourceLang = lang.code else targetLang = lang.code
                                if (step == 0) step = 1
                            }
                        )
                    }
                }

                // ── Botones de Acción (Apple Liquid Buttons) ───────────────
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Botón Secundario (Frosted Glass)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(Color.White.copy(alpha = 0.08f))
                            .border(1.dp, Color.White.copy(alpha = 0.16f), RoundedCornerShape(14.dp))
                            .clickable {
                                if (step == 1) step = 0 else onDismiss()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (step == 1) "← Origen" else "Cancelar",
                            style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }

                    // Botón Primario (Liquid Emerald Glass CTA)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(46.dp)
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
                                if (step == 0) {
                                    step = 1
                                } else {
                                    onConfirm(sourceLang, targetLang)
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            if (step == 0) {
                                Text(
                                    text = "Siguiente →",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF042414)
                                )
                            } else {
                                Icon(
                                    imageVector = Icons.Default.AutoAwesome,
                                    contentDescription = null,
                                    tint = Color(0xFF042414),
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    text = "Traducir Ahora",
                                    style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.Bold),
                                    color = Color(0xFF042414)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Indicador del par origen → destino (Apple Glass Capsule)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun TranslationPairIndicator(
    source: SupportedLanguage,
    target: SupportedLanguage,
    activeStep: Int,
    onStepClick: (Int) -> Unit
) {
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
            .padding(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Origen
            LanguagePill(
                language = source,
                isActive = activeStep == 0,
                label = "ORIGEN",
                modifier = Modifier.weight(1f),
                onClick = { onStepClick(0) }
            )

            // Flecha con glow esmeralda
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.06f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.ArrowForward,
                    contentDescription = null,
                    tint = HiddenForest.Accent,
                    modifier = Modifier.size(15.dp)
                )
            }

            // Destino
            LanguagePill(
                language = target,
                isActive = activeStep == 1,
                label = "DESTINO",
                modifier = Modifier.weight(1f),
                onClick = { onStepClick(1) }
            )
        }
    }
}

@Composable
private fun LanguagePill(
    language: SupportedLanguage,
    isActive: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bgColor by animateColorAsState(
        targetValue = if (isActive) HiddenForest.Accent.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.03f),
        animationSpec = tween(200),
        label = "pill_bg"
    )
    val borderColor by animateColorAsState(
        targetValue = if (isActive) HiddenForest.Accent else Color.White.copy(alpha = 0.09f),
        animationSpec = tween(200),
        label = "pill_border"
    )

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(bgColor)
            .border(1.2.dp, borderColor, RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = if (isActive) HiddenForest.Accent else Color.White.copy(alpha = 0.50f),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 0.8.sp
        )
        Text(
            text = "${language.flag} ${language.nativeName}",
            style = MaterialTheme.typography.labelMedium.copy(
                fontWeight = if (isActive) FontWeight.Bold else FontWeight.Medium
            ),
            color = if (isActive) Color.White else Color.White.copy(alpha = 0.75f),
            maxLines = 1,
            textAlign = TextAlign.Center
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Tarjeta individual de idioma en el grid (Apple Glass Squircle Tile)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LanguageCard(
    language: SupportedLanguage,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val bgBrush = if (isSelected) {
        Brush.verticalGradient(
            listOf(
                HiddenForest.Accent.copy(alpha = 0.32f),
                HiddenForest.Accent.copy(alpha = 0.12f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.055f),
                Color.White.copy(alpha = 0.02f)
            )
        )
    }

    val borderStroke = if (isSelected) {
        Brush.verticalGradient(
            listOf(
                HiddenForest.Accent,
                HiddenForest.Accent.copy(alpha = 0.55f)
            )
        )
    } else {
        Brush.verticalGradient(
            listOf(
                Color.White.copy(alpha = 0.14f),
                Color.White.copy(alpha = 0.03f)
            )
        )
    }

    Column(
        modifier = Modifier
            .aspectRatio(0.92f)
            .clip(RoundedCornerShape(15.dp))
            .background(bgBrush)
            .border(if (isSelected) 1.5.dp else 1.dp, borderStroke, RoundedCornerShape(15.dp))
            .clickable { onClick() }
            .padding(6.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = language.flag,
            fontSize = 24.sp,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = language.nativeName,
            style = MaterialTheme.typography.labelSmall.copy(
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                fontSize = 9.5.sp
            ),
            color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
            textAlign = TextAlign.Center,
            maxLines = 1
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  Mini selector compacto (para usar en WebToEpubImporterDialog)
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Selector de idioma destino compacto: fila de chips horizontales con efecto Apple Glass.
 * Se usa en el diálogo de importación Web→EPUB.
 */
@Composable
fun TargetLanguageRow(
    selectedCode: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    androidx.compose.foundation.lazy.LazyRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 2.dp)
    ) {
        items(TARGET_LANGUAGES.size) { idx ->
            val lang = TARGET_LANGUAGES[idx]
            val isSelected = lang.code == selectedCode

            val bgBrush = if (isSelected) {
                Brush.verticalGradient(
                    listOf(
                        HiddenForest.Accent.copy(alpha = 0.30f),
                        HiddenForest.Accent.copy(alpha = 0.12f)
                    )
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.05f),
                        Color.White.copy(alpha = 0.02f)
                    )
                )
            }

            val borderBrush = if (isSelected) {
                Brush.verticalGradient(
                    listOf(
                        HiddenForest.Accent,
                        HiddenForest.Accent.copy(alpha = 0.6f)
                    )
                )
            } else {
                Brush.verticalGradient(
                    listOf(
                        Color.White.copy(alpha = 0.12f),
                        Color.White.copy(alpha = 0.04f)
                    )
                )
            }

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bgBrush)
                    .border(if (isSelected) 1.5.dp else 1.dp, borderBrush, RoundedCornerShape(20.dp))
                    .clickable { onSelect(lang.code) }
                    .padding(horizontal = 11.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text(text = lang.flag, fontSize = 14.sp)
                Text(
                    text = lang.nativeName,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 11.sp
                    ),
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f)
                )
            }
        }
    }
}
