package com.example.woodsreader.ui.reader

import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.model.ScrollMode
import com.example.woodsreader.ui.theme.HiddenForest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

@Composable
fun ReaderSideMenu(
    hazeState: HazeState,
    isReflowAvailable: Boolean,
    isReflowMode: Boolean = false,
    currentScrollMode: ScrollMode = ScrollMode.PAGINATED,
    isCurrentPageBookmarked: Boolean = false,
    isAutoScrollActive: Boolean = false,
    onOptionClick: (MasterMenuOption) -> Unit,
    onDismiss: () -> Unit
) {
    // Manejo de botón Atrás del sistema
    BackHandler(onBack = onDismiss)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(end = 16.dp)
                .width(200.dp)
                .heightIn(max = 500.dp) // Limitar altura máxima para que no ocupe todo
                .shadow(16.dp, RoundedCornerShape(32.dp))
                .clip(RoundedCornerShape(32.dp))
                .hazeChild(
                    state = hazeState,
                    style = ReaderDesign.GlassStyle
                )
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF13261C).copy(alpha = 0.96f),
                            Color(0xFF0A140F).copy(alpha = 0.98f)
                        )
                    )
                )
                // Borde de "Espejo/Cristal" (Reflejo en el borde superior izquierdo)
                .border(1.dp, Color.White.copy(alpha = 0.20f), RoundedCornerShape(32.dp))
                .padding(vertical = 24.dp, horizontal = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "PANEL",
                style = MaterialTheme.typography.labelSmall,
                color = HiddenForest.TextLow,
                letterSpacing = 3.sp,
                modifier = Modifier.padding(bottom = 20.dp)
            )

            val options = remember(isReflowAvailable, currentScrollMode, isReflowMode, isCurrentPageBookmarked, isAutoScrollActive) {
                buildList {
                    add(MasterMenuOption("Narrar", Icons.AutoMirrored.Filled.VolumeUp, MasterMenuOptionType.TTS))
                    add(MasterMenuOption("Índice", Icons.Default.Menu, MasterMenuOptionType.TOC))
                    add(MasterMenuOption("Buscar", Icons.Default.Search, MasterMenuOptionType.SEARCH))
                    add(MasterMenuOption("Rayos-X", Icons.Default.AutoAwesome, MasterMenuOptionType.XRAY))
                    add(
                        MasterMenuOption(
                            label = if (isAutoScrollActive) "Teleprompter (Activo)" else "Teleprompter",
                            icon = Icons.Default.ArrowDownward,
                            type = MasterMenuOptionType.AUTO_SCROLL,
                            isSelected = isAutoScrollActive
                        )
                    )
                    if (isReflowAvailable) {
                        val reflowLabel = if (isReflowMode) "Vista Original" else "Vista Libro"
                        val reflowIcon = if (isReflowMode) Icons.Default.PictureAsPdf else Icons.AutoMirrored.Filled.MenuBook
                        add(MasterMenuOption(reflowLabel, reflowIcon, MasterMenuOptionType.REFLOW))
                    }
                    add(MasterMenuOption("Ajustes", Icons.Default.Settings, MasterMenuOptionType.SETTINGS))
                    add(
                        MasterMenuOption(
                            label = if (isCurrentPageBookmarked) "Marcador guardado" else "Marcador",
                            icon = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            type = MasterMenuOptionType.BOOKMARK,
                            isSelected = isCurrentPageBookmarked
                        )
                    )
                }
            }

            // Scroll vertical para adaptabilidad
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                options.forEachIndexed { index, option ->
                    // Animación escalonada (Staggered Entry)
                    var visible by remember { mutableStateOf(false) }
                    LaunchedEffect(Unit) {
                        kotlinx.coroutines.delay(index * 40L)
                        visible = true
                    }
                    
                    AnimatedVisibility(
                        visible = visible,
                        enter = fadeIn(tween(300)) + slideInVertically(animationSpec = tween(300)) { 20 }
                    ) {
                        SideMenuTile(option) { onOptionClick(option) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SideMenuTile(
    option: MasterMenuOption,
    onClick: () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.94f else 1f,
        animationSpec = tween(durationMillis = 100),
        label = "tileScale"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(60.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(RoundedCornerShape(20.dp))
            .background(
                if (isPressed) HiddenForest.Accent.copy(alpha = 0.15f)
                else if (option.isSelected) HiddenForest.Accent.copy(alpha = 0.18f)
                else HiddenForest.SurfaceVariant.copy(alpha = 0.4f)
            )
            .border(
                width = 1.dp,
                color = if (isPressed || option.isSelected) HiddenForest.Accent.copy(0.4f) else Color.Transparent,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(
                interactionSource = interactionSource,
                indication = null,
                onClick = onClick
            )
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Start
    ) {
        Box(
            modifier = Modifier
                .size(38.dp)
                .background(
                    brush = Brush.radialGradient(
                        colors = listOf(HiddenForest.Accent.copy(0.2f), Color.Transparent)
                    ),
                    shape = CircleShape
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = option.icon,
                contentDescription = option.label,
                tint = if (option.isSelected) HiddenForest.Accent else HiddenForest.TextSecondary,
                modifier = Modifier.size(22.dp)
            )
        }
        
        Spacer(modifier = Modifier.width(14.dp))
        
        Text(
            text = option.label,
            style = MaterialTheme.typography.bodyMedium.copy(
                fontWeight = FontWeight.SemiBold,
                fontSize = 15.sp
            ),
            color = HiddenForest.Text,
            maxLines = 1
        )
    }
}

data class MasterMenuOption(
    val label: String,
    val icon: ImageVector,
    val type: MasterMenuOptionType,
    val isSelected: Boolean = false
)

enum class MasterMenuOptionType {
    TTS, TOC, SEARCH, SETTINGS, BOOKMARK, REFLOW, SCROLL_MODE, XRAY, AUTO_SCROLL
}
