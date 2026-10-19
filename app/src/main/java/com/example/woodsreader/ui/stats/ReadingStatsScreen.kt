package com.example.woodsreader.ui.stats

import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import com.example.woodsreader.data.model.ReadingMode
import com.example.woodsreader.data.database.*
import com.example.woodsreader.ui.theme.HiddenForest
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.LocalDate
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReadingStatsScreen(
    onBack: () -> Unit = {},
    viewModel: StatsViewModel = hiltViewModel()
) {
    val completedBooks     by viewModel.completedBooksCount.collectAsStateWithLifecycle()
    val totalTimeFormatted by viewModel.totalReadingTimeFormatted.collectAsStateWithLifecycle()
    val streak             by viewModel.currentStreak.collectAsStateWithLifecycle()
    val weeklyWords        by viewModel.weeklyWords.collectAsStateWithLifecycle()
    val heatmapDays        by viewModel.heatmapDays.collectAsStateWithLifecycle()
    val todayMinutes       by viewModel.todayReadingMinutes.collectAsStateWithLifecycle()
    val todayWords         by viewModel.todayWordsProcessed.collectAsStateWithLifecycle()
    val hourlyActivity     by viewModel.hourlyActivity.collectAsStateWithLifecycle()
    val bookDistribution   by viewModel.bookDistribution.collectAsStateWithLifecycle()
    val averageWpm         by viewModel.averageWpm.collectAsStateWithLifecycle()
    val dailyCapacity      by viewModel.dailyCapacityWords.collectAsStateWithLifecycle()
    val modeEndurance      by viewModel.modeEndurance.collectAsStateWithLifecycle()
    val timeSaved          by viewModel.totalTimeSaved.collectAsStateWithLifecycle()
    val smartGoal          by viewModel.smartDailyGoalWords.collectAsStateWithLifecycle()
    val monthLabel         = viewModel.currentMonthLabel

    Scaffold(
        containerColor = HiddenForest.Background,
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = HiddenForest.Background,
                    titleContentColor = HiddenForest.Text,
                    navigationIconContentColor = HiddenForest.TextSecondary
                ),
                title = { Text("Estadísticas", fontWeight = FontWeight.Bold, fontFamily = FontFamily.SansSerif) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                }
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .background(HiddenForest.Background)
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 130.dp)
        ) {
            // ── 1. Tarjeta Hero Superior "Objetivo Diario" con Anillo Circular ──
            item {
                DailyGoalHeroCard(
                    todayMinutes  = todayMinutes,
                    todayWords    = todayWords,
                    smartGoal     = smartGoal,
                    dailyCapacity = dailyCapacity
                )
            }

            // ── 2. Cuadrícula 2x2 de KPIs Modernizada ──────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModernStatsCard(
                        modifier = Modifier.weight(1f),
                        title = "Tiempo total",
                        value = totalTimeFormatted,
                        icon = Icons.Default.Schedule,
                        iconTint = HiddenForest.Accent
                    )
                    ModernStatsCard(
                        modifier = Modifier.weight(1f),
                        title = "Libros leídos",
                        value = completedBooks.toString(),
                        icon = Icons.AutoMirrored.Filled.MenuBook,
                        iconTint = HiddenForest.Amber
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    ModernStatsCard(
                        modifier = Modifier.weight(1f),
                        title = "Racha",
                        value = "$streak días",
                        icon = Icons.Default.LocalFireDepartment,
                        iconTint = Color(0xFFFF6D00)
                    )
                    ModernStatsCard(
                        modifier = Modifier.weight(1f),
                        title = "Vel. media",
                        value = "$averageWpm ppm",
                        icon = Icons.Default.Speed,
                        iconTint = Color(0xFF00B0FF)
                    )
                }
            }

            // ── 3. Mapa de Calor (Mes Actual / Últimos 28 días) ───────────────
            item {
                SectionTitle(monthLabel.ifBlank { "ACTIVIDAD RECIENTE" })
                ActivityHeatmap(daysList = heatmapDays as? List<String> ?: emptyList())
            }

            // ── 4. Palabras esta semana ───────────────────────────────────────
            item {
                SectionTitle("PALABRAS ESTA SEMANA")
                WeeklyWordsChart(weeklyWordsList = weeklyWords as? List<DailyWordCount> ?: emptyList())
            }

            // ── 5. Actividad por hora del día ─────────────────────────────────
            if (hourlyActivity.isNotEmpty()) {
                item {
                    SectionTitle("ACTIVIDAD POR HORA DEL DÍA")
                    HourlyActivityChart(hourlyList = hourlyActivity as? List<HourlyActivity> ?: emptyList())
                }
            }

            // ── 6. Distribución por libro ─────────────────────────────────────
            if (bookDistribution.isNotEmpty()) {
                item {
                    SectionTitle("DISTRIBUCIÓN POR LIBRO")
                    BookDistributionList(distList = bookDistribution as? List<BookDistribution> ?: emptyList())
                }
            }

            // ── 7. Comparativa de modos (Manual vs TTS) ───────────────────────
            item {
                SectionTitle("COMPARATIVA DE MODOS")
                ModeComparisonCard(
                    modeEnduranceList = modeEndurance as? List<ModeEndurance> ?: emptyList(),
                    timeSaved = timeSaved
                )
            }

            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

// ── Composables de soporte ────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.titleSmall.copy(
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 1.3.sp,
            fontWeight = FontWeight.Bold
        ),
        color = HiddenForest.TextSecondary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp, start = 4.dp)
    )
}

/**
 * 1. Tarjeta Hero Superior "Objetivo Diario" con Anillo de Progreso Circular
 */
@Composable
private fun DailyGoalHeroCard(
    todayMinutes  : Int,
    todayWords    : Int,
    smartGoal     : Int,
    dailyCapacity : Int
) {
    val progress = if (smartGoal > 0) (todayWords.toFloat() / smartGoal).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "hero_goal_progress"
    )
    val isGoalCompleted = todayWords >= smartGoal && smartGoal > 0
    val percentage = (progress * 100).toInt()

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .border(
                width = 1.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        Color(0xFF2A4D37),
                        Color(0xFF162D20)
                    )
                ),
                shape = RoundedCornerShape(24.dp)
            ),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF112419),
                            Color(0xFF0B1610)
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp)
            ) {
                // Anillo circular de progreso a la izquierda
                Box(
                    modifier = Modifier.size(92.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { 1f },
                        modifier = Modifier.fillMaxSize(),
                        color = Color(0xFF1A3324),
                        strokeWidth = 9.dp,
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )
                    CircularProgressIndicator(
                        progress = { animatedProgress },
                        modifier = Modifier.fillMaxSize(),
                        color = if (isGoalCompleted) HiddenForest.Amber else HiddenForest.Accent,
                        strokeWidth = 9.dp,
                        trackColor = Color.Transparent,
                        strokeCap = StrokeCap.Round
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text(
                            text = "$percentage%",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 19.sp
                            ),
                            color = if (isGoalCompleted) HiddenForest.Amber else HiddenForest.Text
                        )
                        Text(
                            text = "META",
                            style = MaterialTheme.typography.labelSmall.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold,
                                fontSize = 9.sp,
                                letterSpacing = 1.sp
                            ),
                            color = HiddenForest.TextLow
                        )
                    }
                }

                // Detalles a la derecha
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Badge de estado
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = if (isGoalCompleted) HiddenForest.Amber.copy(alpha = 0.18f) else HiddenForest.Accent.copy(alpha = 0.15f),
                            border = BorderStroke(
                                1.dp,
                                if (isGoalCompleted) HiddenForest.Amber.copy(alpha = 0.4f) else HiddenForest.Accent.copy(alpha = 0.3f)
                            )
                        ) {
                            Text(
                                text = if (isGoalCompleted) "✨ ¡Objetivo superado!" else "🎯 En progreso",
                                style = MaterialTheme.typography.labelSmall.copy(
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 11.sp
                                ),
                                color = if (isGoalCompleted) HiddenForest.Amber else HiddenForest.Accent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    // Palabras hoy / meta
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "$todayWords",
                            style = MaterialTheme.typography.headlineSmall.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 23.sp
                            ),
                            color = HiddenForest.Text
                        )
                        Text(
                            text = "/ $smartGoal pal",
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = HiddenForest.TextSecondary,
                            modifier = Modifier.padding(bottom = 2.dp)
                        )
                    }

                    // Tiempo y capacidad diaria estimada
                    val minutesText = if (todayMinutes > 0) "${todayMinutes} min hoy" else "$todayWords pal hoy"
                    Text(
                        text = "$minutesText • Cap: $dailyCapacity pal/día",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 11.sp
                        ),
                        color = HiddenForest.TextLow
                    )
                }
            }
        }
    }
}

/**
 * 2. Cuadrícula 2x2 de KPIs Modernizada con borde cristalino y resplandor radial
 */
@Composable
private fun ModernStatsCard(
    title    : String,
    value    : String,
    icon     : ImageVector,
    iconTint : Color,
    modifier : Modifier = Modifier
) {
    Card(
        modifier = modifier
            .clip(RoundedCornerShape(22.dp))
            .border(
                width = 1.dp,
                color = Color(0xFF233B2C),
                shape = RoundedCornerShape(22.dp)
            ),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101C15))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            iconTint.copy(alpha = 0.16f),
                            Color.Transparent
                        ),
                        center = androidx.compose.ui.geometry.Offset(Float.POSITIVE_INFINITY, 0f),
                        radius = 200f
                    )
                )
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Cápsula redondeada translúcida para el icono
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(iconTint.copy(alpha = 0.15f))
                        .border(1.dp, iconTint.copy(alpha = 0.3f), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(22.dp)
                    )
                }

                // Valor numérico en negrita grande
                Text(
                    text = value,
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 24.sp
                    ),
                    color = HiddenForest.Text,
                    maxLines = 1
                )

                // Etiqueta secundaria
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontFamily = FontFamily.SansSerif,
                        fontWeight = FontWeight.Medium,
                        fontSize = 12.sp
                    ),
                    color = HiddenForest.TextLow
                )
            }
        }
    }
}

/**
 * 3. Mapa de calor de 28 días con bordes redondeados y elegancia cristalina
 */
@Composable
private fun ActivityHeatmap(daysList: List<String>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, Color(0xFF233B2C), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101C15))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            val today = LocalDate.now()
            val gridDays = (0..27).map { today.minusDays(27L - it) }

            LazyVerticalGrid(
                columns = GridCells.Fixed(7),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.height(130.dp),
                userScrollEnabled = false
            ) {
                items(gridDays.size) { idx ->
                    val date = gridDays[idx]
                    val dateStr = date.toString()
                    val hasActivity = daysList.contains(dateStr)

                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(6.dp))
                            .then(
                                if (hasActivity) {
                                    Modifier
                                        .background(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    HiddenForest.Accent,
                                                    Color(0xFF00B359)
                                                )
                                            )
                                        )
                                        .border(1.dp, HiddenForest.Accent.copy(alpha = 0.8f), RoundedCornerShape(6.dp))
                                } else {
                                    Modifier
                                        .background(Color(0xFF0F1E16))
                                        .border(1.dp, Color(0xFF1E3628), RoundedCornerShape(6.dp))
                                }
                            )
                    )
                }
            }

            // Leyenda inferior
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Hace 28 días",
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
                    color = HiddenForest.TextLow
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = "Menos",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
                        color = HiddenForest.TextLow
                    )
                    Box(
                        modifier = Modifier
                            .size(11.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Color(0xFF0F1E16))
                            .border(1.dp, Color(0xFF1E3628), RoundedCornerShape(3.dp))
                    )
                    listOf(0.35f, 0.65f, 1.0f).forEach { alpha ->
                        Box(
                            modifier = Modifier
                                .size(11.dp)
                                .clip(RoundedCornerShape(3.dp))
                                .background(HiddenForest.Accent.copy(alpha = alpha))
                        )
                    }
                    Text(
                        text = "Más",
                        style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
                        color = HiddenForest.TextLow
                    )
                }
            }
        }
    }
}

/**
 * Palabras leídas por día de la semana
 */
@Composable
private fun WeeklyWordsChart(weeklyWordsList: List<DailyWordCount>) {
    val dayLabels = listOf("D", "L", "M", "X", "J", "V", "S") // 0=Dom..6=Sáb
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, Color(0xFF233B2C), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101C15))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            if (weeklyWordsList.isEmpty()) {
                Text(
                    text = "Sin datos recientes esta semana.",
                    style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif),
                    color = HiddenForest.TextLow
                )
            } else {
                val maxWords = weeklyWordsList.maxOfOrNull { it.totalWords }?.coerceAtLeast(1L) ?: 1L
                val fullWeek = (0..6).map { dayIndex -> 
                    weeklyWordsList.find { it.dayOfWeek == dayIndex }?.totalWords ?: 0L 
                }

                Row(
                    modifier = Modifier.fillMaxWidth().height(120.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.Bottom
                ) {
                    fullWeek.forEachIndexed { i, words ->
                        val frac = (words.toFloat() / maxWords).coerceIn(0f, 1f)
                        val animH by animateFloatAsState(
                            targetValue = frac,
                            animationSpec = tween(800, i * 60),
                            label = "bar_$i"
                        )

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.55f)
                                    .fillMaxHeight(animH.coerceAtLeast(0.04f))
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        if (words > 0) {
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    HiddenForest.Accent,
                                                    Color(0xFF0D331E)
                                                )
                                            )
                                        } else {
                                            Brush.verticalGradient(
                                                colors = listOf(
                                                    Color(0xFF1B2E23),
                                                    Color(0xFF101C15)
                                                )
                                            )
                                        }
                                    )
                            )
                        }
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    dayLabels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold
                            ),
                            textAlign = TextAlign.Center,
                            color = HiddenForest.TextLow,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Actividad por hora del día con píldora de hora pico y líneas guía sutiles
 */
@Composable
private fun HourlyActivityChart(hourlyList: List<HourlyActivity>) {
    val peak = hourlyList.maxByOrNull { it.totalWords }
    val peakHourText = if (peak != null && peak.totalWords > 0) {
        String.format(Locale.getDefault(), "Pico: %02d:00 h", peak.hour)
    } else {
        "Sin datos"
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, Color(0xFF233B2C), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101C15))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Cabecera interna con píldora de hora pico
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "DISTRIBUCIÓN HORARIA",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 1.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    color = HiddenForest.TextSecondary
                )
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = HiddenForest.Accent.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, HiddenForest.Accent.copy(alpha = 0.3f))
                ) {
                    Text(
                        text = peakHourText,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        ),
                        color = HiddenForest.Accent,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }

            // Área de barras con líneas guía
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    repeat(4) {
                        HorizontalDivider(
                            color = Color(0xFF1E3628).copy(alpha = 0.5f),
                            thickness = 1.dp
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    verticalAlignment = Alignment.Bottom
                ) {
                    val maxCount = hourlyList.maxOfOrNull { it.totalWords }?.coerceAtLeast(1) ?: 1

                    for (h in 0..23) {
                        val count = hourlyList.find { it.hour == h }?.totalWords ?: 0L
                        val frac = (count.toFloat() / maxCount).coerceIn(0f, 1f)
                        val animH by animateFloatAsState(
                            targetValue = frac,
                            animationSpec = tween(600, h * 20),
                            label = "hour_$h"
                        )
                        val isPeak = peak != null && peak.hour == h && count > 0

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(animH.coerceAtLeast(0.04f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (count > 0) {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                if (isPeak) HiddenForest.Amber else HiddenForest.Accent,
                                                Color(0xFF0D331E)
                                            )
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFF1B2E23),
                                                Color(0xFF101C15)
                                            )
                                        )
                                    }
                                )
                        )
                    }
                }
            }

            // Etiquetas de horas
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("00:00", "06:00", "12:00", "18:00", "23:00").forEach { label ->
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontSize = 10.sp
                        ),
                        color = HiddenForest.TextLow
                    )
                }
            }
        }
    }
}

/**
 * Distribución de lectura por libro
 */
@Composable
private fun BookDistributionList(distList: List<BookDistribution>) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, Color(0xFF233B2C), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101C15))
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val maxWords = distList.maxOfOrNull { it.totalWords }?.coerceAtLeast(1L) ?: 1L

            distList.take(5).forEach { entry ->
                val frac = (entry.totalWords.toFloat() / maxWords).coerceIn(0f, 1f)
                val animF by animateFloatAsState(frac, tween(800), label = "book_${entry.title}")

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = entry.title,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.Bold
                            ),
                            maxLines = 1,
                            color = HiddenForest.Text,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "${entry.totalWords} pal",
                            style = MaterialTheme.typography.labelMedium.copy(
                                fontFamily = FontFamily.SansSerif,
                                fontWeight = FontWeight.SemiBold
                            ),
                            color = HiddenForest.TextSecondary
                        )
                    }
                    LinearProgressIndicator(
                        progress = { animF },
                        modifier = Modifier.fillMaxWidth().height(7.dp).clip(RoundedCornerShape(4.dp)),
                        color = HiddenForest.Amber,
                        trackColor = Color(0xFF1E3628)
                    )
                }
            }
        }
    }
}

/**
 * 4. Comparativa de modos con barra bicolor, tarjetas individuales e insignia de tiempo ahorrado
 */
@Composable
private fun ModeComparisonCard(
    modeEnduranceList: List<ModeEndurance>,
    timeSaved: String
) {
    val manualEntry = modeEnduranceList.find { it.mode == ReadingMode.MANUAL }
    val ttsEntry = modeEnduranceList.find { it.mode == ReadingMode.TTS }

    val manualMinutes = manualEntry?.avgDurationMinutes ?: 0.0
    val ttsMinutes = ttsEntry?.avgDurationMinutes ?: 0.0
    val totalMinutes = manualMinutes + ttsMinutes
    val manualRatio = if (totalMinutes > 0.0) {
        (manualMinutes / totalMinutes).toFloat().coerceIn(0.05f, 0.95f)
    } else {
        0.5f
    }
    val ttsRatio = 1f - manualRatio

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .border(1.dp, Color(0xFF233B2C), RoundedCornerShape(22.dp)),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF101C15))
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Barra comparativa bicolor con proporción visual
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "PROPORCIÓN DE USO",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.SansSerif,
                            letterSpacing = 1.sp,
                            fontWeight = FontWeight.Bold
                        ),
                        color = HiddenForest.TextSecondary
                    )
                    Text(
                        text = "${(manualRatio * 100).toInt()}% Manual • ${(ttsRatio * 100).toInt()}% Audio",
                        style = MaterialTheme.typography.labelSmall.copy(
                            fontFamily = FontFamily.SansSerif,
                            fontWeight = FontWeight.Bold
                        ),
                        color = HiddenForest.TextLow
                    )
                }

                // Barra segmentada
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(14.dp)
                        .clip(RoundedCornerShape(7.dp))
                        .background(Color(0xFF1A3324))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(manualRatio)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color(0xFF00B359),
                                        HiddenForest.Accent
                                    )
                                )
                            )
                    )
                    Spacer(modifier = Modifier.width(2.dp).fillMaxHeight().background(Color(0xFF101C15)))
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .weight(ttsRatio)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        HiddenForest.Amber,
                                        Color(0xFFFF8F00)
                                    )
                                )
                            )
                    )
                }
            }

            // Tarjetas individuales de modo
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModeChip(
                    label = "Lectura Manual",
                    minutes = manualMinutes.toLong(),
                    sessions = manualEntry?.totalSessions ?: 0,
                    color = HiddenForest.Accent,
                    modifier = Modifier.weight(1f)
                )
                ModeChip(
                    label = "Audio (TTS)",
                    minutes = ttsMinutes.toLong(),
                    sessions = ttsEntry?.totalSessions ?: 0,
                    color = HiddenForest.Amber,
                    modifier = Modifier.weight(1f)
                )
            }

            // Insignia inferior de tiempo ahorrado
            if (timeSaved.isNotBlank() && timeSaved != "0h" && timeSaved != "0h 0m") {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = HiddenForest.Amber.copy(alpha = 0.12f),
                    border = BorderStroke(1.dp, HiddenForest.Amber.copy(alpha = 0.35f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(32.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(HiddenForest.Amber.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                Icons.Default.Bolt,
                                contentDescription = null,
                                tint = HiddenForest.Amber,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "Tiempo ahorrado gracias al TTS",
                                style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
                                color = HiddenForest.TextSecondary
                            )
                            Text(
                                text = "⚡ $timeSaved recuperadas",
                                style = MaterialTheme.typography.titleSmall.copy(
                                    fontFamily = FontFamily.SansSerif,
                                    fontWeight = FontWeight.ExtraBold
                                ),
                                color = HiddenForest.Amber
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ModeChip(
    label: String,
    minutes: Long,
    sessions: Int,
    color: Color,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.08f))
            .border(1.dp, color.copy(alpha = 0.25f), RoundedCornerShape(16.dp))
            .padding(14.dp),
        horizontalAlignment = Alignment.Start,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontFamily = FontFamily.SansSerif,
                    fontWeight = FontWeight.Bold
                ),
                color = HiddenForest.Text
            )
        }
        Text(
            text = "${minutes}m",
            style = MaterialTheme.typography.headlineSmall.copy(
                fontFamily = FontFamily.SansSerif,
                fontWeight = FontWeight.ExtraBold,
                fontSize = 20.sp
            ),
            color = color
        )
        Text(
            text = "media por sesión" + if (sessions > 0) " ($sessions)" else "",
            style = MaterialTheme.typography.labelSmall.copy(
                fontFamily = FontFamily.SansSerif,
                fontSize = 11.sp
            ),
            color = HiddenForest.TextLow
        )
    }
}
