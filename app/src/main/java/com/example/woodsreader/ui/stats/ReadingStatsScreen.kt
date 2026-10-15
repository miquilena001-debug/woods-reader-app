package com.example.woodsreader.ui.stats

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import java.time.LocalDate
import java.util.Locale
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import javax.inject.Inject

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

    var selectedPeriod by remember { mutableStateOf(StatsPeriod.Weekly) }
    var dateFilter     by remember { mutableStateOf<LocalDate?>(null) }

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
                        Icon(Icons.Default.ArrowBack, contentDescription = "Volver")
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
            contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
        ) {
            // ── Headline stats ────────────────────────────────────────────────
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatsCard(
                        modifier = Modifier.weight(1f),
                        title = "Tiempo total",
                        value = totalTimeFormatted,
                        icon = Icons.Default.Schedule,
                        iconTint = HiddenForest.Accent
                    )
                    StatsCard(
                        modifier = Modifier.weight(1f),
                        title = "Libros leídos",
                        value = completedBooks.toString(),
                        icon = Icons.Default.MenuBook,
                        iconTint = HiddenForest.Amber
                    )
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    StatsCard(
                        modifier = Modifier.weight(1f),
                        title = "Racha",
                        value = "$streak días",
                        icon = Icons.Default.LocalFireDepartment,
                        iconTint = Color(0xFFFF6D00)
                    )
                    StatsCard(
                        modifier = Modifier.weight(1f),
                        title = "Vel. media",
                        value = "$averageWpm ppm",
                        icon = Icons.Default.Speed,
                        iconTint = Color(0xFF00B0FF)
                    )
                }
            }

            // ── Objetivo diario ───────────────────────────────────────────────
            item {
                DailyGoalCard(
                    todayMinutes  = todayMinutes,
                    todayWords    = todayWords,
                    smartGoal     = smartGoal,
                    dailyCapacity = dailyCapacity
                )
            }

            // ── Heatmap últimos 28 días ────────────────────────────────────────
            item {
                SectionTitle(monthLabel.ifBlank { "ACTIVIDAD RECIENTE" })
                ActivityHeatmap(daysList = heatmapDays as? List<String> ?: emptyList())
            }

            // ── Palabras por día de la semana ─────────────────────────────────
            item {
                SectionTitle("PALABRAS ESTA SEMANA")
                WeeklyWordsChart(weeklyWordsList = weeklyWords as? List<DailyWordCount> ?: emptyList())
            }

            // ── Actividad por hora ────────────────────────────────────────────
            if (hourlyActivity.isNotEmpty()) {
                item {
                    SectionTitle("ACTIVIDAD POR HORA DEL DÍA")
                    HourlyActivityChart(hourlyList = hourlyActivity as? List<HourlyActivity> ?: emptyList())
                }
            }

            // ── Distribución por libro ────────────────────────────────────────
            if (bookDistribution.isNotEmpty()) {
                item {
                    SectionTitle("DISTRIBUCIÓN POR LIBRO")
                    BookDistributionList(distList = bookDistribution as? List<BookDistribution> ?: emptyList())
                }
            }

            // ── Modo de lectura ───────────────────────────────────────────────
            item {
                SectionTitle("COMPARATIVA DE MODOS")
                ModeComparisonCard(
                    modeEnduranceList = modeEndurance as? List<ModeEndurance> ?: emptyList(),
                    timeSaved = timeSaved
                )
            }

            item { Spacer(Modifier.height(32.dp)) }
        }
    }
}

// ── Composables de soporte ────────────────────────────────────────────────────

@Composable
private fun SectionTitle(text: String) {
    Text(
        text  = text.uppercase(),
        style = MaterialTheme.typography.titleMedium.copy(
            fontFamily = FontFamily.SansSerif,
            letterSpacing = 1.2.sp
        ),
        fontWeight = FontWeight.Bold,
        color = HiddenForest.Text,
        modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp)
    )
}

@Composable
private fun StatsCard(
    title    : String,
    value    : String,
    icon     : ImageVector,
    iconTint : Color,
    modifier : Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = HiddenForest.SurfaceVariant)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(26.dp))
            Text(value, style = MaterialTheme.typography.titleLarge.copy(fontFamily = FontFamily.SansSerif), color = HiddenForest.Text, fontWeight = FontWeight.ExtraBold)
            Text(title, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.SansSerif), color = HiddenForest.TextLow)
        }
    }
}

@Composable
private fun DailyGoalCard(
    todayMinutes  : Int,
    todayWords    : Int,
    smartGoal     : Int,
    dailyCapacity : Int
) {
    val progress = if (smartGoal > 0) (todayWords.toFloat() / smartGoal).coerceIn(0f, 1f) else 0f
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(1000), label = "goal_progress")

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = HiddenForest.SurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    modifier = Modifier.size(36.dp).clip(CircleShape).background(HiddenForest.Accent.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.TrackChanges, contentDescription = null, tint = HiddenForest.Accent, modifier = Modifier.size(20.dp))
                }
                Text("Objetivo de hoy", style = MaterialTheme.typography.titleMedium.copy(fontFamily = FontFamily.SansSerif),
                    fontWeight = FontWeight.Bold, color = HiddenForest.Text)
                Spacer(Modifier.weight(1f))
                Text("$todayWords / $smartGoal",
                    style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.SansSerif),
                    color = HiddenForest.TextSecondary)
            }
            LinearProgressIndicator(
                progress        = { animatedProgress },
                modifier        = Modifier.fillMaxWidth().height(10.dp).clip(RoundedCornerShape(5.dp)),
                color           = HiddenForest.Accent,
                trackColor      = HiddenForest.Outline
            )
            Row(horizontalArrangement = Arrangement.SpaceBetween, modifier = Modifier.fillMaxWidth()) {
                Text("${todayMinutes}m leídos hoy", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
                    color = HiddenForest.TextLow)
                Text("Capacidad: ${dailyCapacity} pal/día", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
                    color = HiddenForest.TextLow)
            }
        }
    }
}

@Composable
private fun ActivityHeatmap(daysList: List<String>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = HiddenForest.SurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            val today = LocalDate.now()
            val gridDays = (0..27).map { today.minusDays(27L - it) }

            LazyVerticalGrid(
                columns             = GridCells.Fixed(7),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
                modifier            = Modifier.height(110.dp),
                userScrollEnabled   = false
            ) {
                items(gridDays.size) { idx ->
                    val dateStr = gridDays[idx].toString()
                    val hasActivity = daysList.contains(dateStr)
                    Box(
                        modifier = Modifier
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(4.dp))
                            .background(
                                if (hasActivity) HiddenForest.Accent
                                else HiddenForest.Outline.copy(alpha = 0.3f)
                            )
                    )
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("Hace 28 días", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif), color = HiddenForest.TextLow)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Menos", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif), color = HiddenForest.TextLow)
                    listOf(0.1f, 0.4f, 0.7f, 1.0f).forEach { alpha ->
                        Box(modifier = Modifier.size(10.dp).clip(RoundedCornerShape(2.dp)).background(HiddenForest.Accent.copy(alpha = alpha)))
                    }
                    Text("Más", style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif), color = HiddenForest.TextLow)
                }
            }
        }
    }
}

@Composable
private fun WeeklyWordsChart(weeklyWordsList: List<DailyWordCount>) {
    val dayLabels = listOf("D", "L", "M", "X", "J", "V", "S") // 0=Sun..6=Sat mapped logic
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = HiddenForest.SurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if (weeklyWordsList.isEmpty()) {
                Text("Sin datos recientes.", style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.SansSerif), color = HiddenForest.TextLow)
            } else {
                val maxWords = weeklyWordsList.maxOfOrNull { it.totalWords }?.coerceAtLeast(1L) ?: 1L
                
                // Agrupamos por dayOfWeek (SQLite 0=Sun, 1=Mon, ..., 6=Sat)
                val fullWeek = (0..6).map { dayIndex -> 
                    weeklyWordsList.find { it.dayOfWeek == dayIndex }?.totalWords ?: 0L 
                }

                Row(
                    modifier              = Modifier.fillMaxWidth().height(120.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment     = Alignment.Bottom
                ) {
                    fullWeek.forEachIndexed { i, words ->
                        val frac  = (words.toFloat() / maxWords).coerceIn(0f, 1f)
                        val animH by animateFloatAsState(frac, tween(800, i * 60), label = "bar_$i")

                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Bottom,
                            modifier            = Modifier.weight(1f).fillMaxHeight()
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(0.6f)
                                    .fillMaxHeight(animH.coerceAtLeast(0.04f))
                                    .clip(RoundedCornerShape(topStart = 6.dp, topEnd = 6.dp))
                                    .background(
                                        if (words > 0) HiddenForest.Accent
                                        else HiddenForest.Outline.copy(alpha = 0.2f)
                                    )
                            )
                        }
                    }
                }
                Row(
                    modifier              = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    dayLabels.forEach { label ->
                        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.SansSerif),
                            textAlign = TextAlign.Center, color = HiddenForest.TextLow, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

@Composable
private fun HourlyActivityChart(hourlyList: List<HourlyActivity>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = HiddenForest.SurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth().height(80.dp),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment     = Alignment.Bottom
            ) {
                val maxCount = hourlyList.maxOfOrNull { it.totalWords }?.coerceAtLeast(1) ?: 1

                for (h in 0..23) {
                    val count = hourlyList.find { it.hour == h }?.totalWords ?: 0L
                    val frac  = (count.toFloat() / maxCount).coerceIn(0f, 1f)
                    val animH by animateFloatAsState(frac, tween(600, h * 20), label = "hour_$h")
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(animH.coerceAtLeast(0.04f))
                            .clip(RoundedCornerShape(topStart = 2.dp, topEnd = 2.dp))
                            .background(if (count > 0) HiddenForest.AccentDim else HiddenForest.Outline.copy(alpha = 0.2f))
                    )
                }
            }
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                listOf("0h", "6h", "12h", "18h", "23h").forEach { label ->
                    Text(label, style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif), color = HiddenForest.TextLow)
                }
            }
        }
    }
}

@Composable
private fun BookDistributionList(distList: List<BookDistribution>) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = HiddenForest.SurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            val maxWords = distList.maxOfOrNull { it.totalWords }?.coerceAtLeast(1L) ?: 1L

            distList.take(5).forEach { entry ->
                val frac    = (entry.totalWords.toFloat() / maxWords).coerceIn(0f, 1f)
                val animF   by animateFloatAsState(frac, tween(800), label = "book_${entry.title}")

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier              = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(entry.title, style = MaterialTheme.typography.labelLarge.copy(fontFamily = FontFamily.SansSerif),
                            maxLines = 1, color = HiddenForest.Text, modifier = Modifier.weight(1f))
                        Text(
                            "${entry.totalWords} pal",
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.SansSerif),
                            color = HiddenForest.TextLow
                        )
                    }
                    LinearProgressIndicator(
                        progress   = { animF },
                        modifier   = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        color      = HiddenForest.Amber,
                        trackColor = HiddenForest.Outline.copy(alpha = 0.3f)
                    )
                }
            }
        }
    }
}

@Composable
private fun ModeComparisonCard(
    modeEnduranceList: List<ModeEndurance>,
    timeSaved: String
) {
    val manualEntry = modeEnduranceList.find { it.mode == ReadingMode.MANUAL }
    val ttsEntry = modeEnduranceList.find { it.mode == ReadingMode.TTS }

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape    = RoundedCornerShape(20.dp),
        colors   = CardDefaults.cardColors(containerColor = HiddenForest.SurfaceVariant)
    ) {
        Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(
                modifier              = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                ModeChip(
                    label    = "Lectura Manual",
                    minutes  = manualEntry?.avgDurationMinutes?.toLong() ?: 0L,
                    color    = HiddenForest.Accent,
                    modifier = Modifier.weight(1f)
                )
                ModeChip(
                    label    = "Audio (TTS)",
                    minutes  = ttsEntry?.avgDurationMinutes?.toLong() ?: 0L,
                    color    = HiddenForest.Amber,
                    modifier = Modifier.weight(1f)
                )
            }
            if (timeSaved != "0h") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(HiddenForest.Amber.copy(alpha = 0.15f))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment     = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(Icons.Default.Bolt, contentDescription = null, tint = HiddenForest.Amber, modifier = Modifier.size(20.dp))
                    Text("Tiempo ahorrado: $timeSaved",
                        style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.SansSerif),
                        color = HiddenForest.Amber, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ModeChip(
    label    : String,
    minutes  : Long,
    color    : Color,
    modifier : Modifier = Modifier
) {
    Column(
        modifier            = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(color.copy(alpha = 0.1f))
            .border(1.dp, color.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
        Text(label, style = MaterialTheme.typography.labelMedium.copy(fontFamily = FontFamily.SansSerif), color = HiddenForest.Text, fontWeight = FontWeight.Bold)
        Text(
            "${minutes}m por sesión",
            style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.SansSerif),
            color = HiddenForest.TextLow
        )
    }
}
