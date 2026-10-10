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
    currentScrollMode: ScrollMode = ScrollMode.PAGINATED,
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
                .clip(RoundedCornerShape(32.dp))
                .hazeChild(
                    state = hazeState,
                    style = ReaderDesign.GlassStyle
                )
                .background(HiddenForest.Surface.copy(alpha = 0.75f))
                // Borde de "Espejo/Cristal" (Reflejo en el borde superior izquierdo)
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        0.0f to Color.White.copy(alpha = 0.25f),
                        0.5f to Color.Transparent,
                        1.0f to Color.White.copy(alpha = 0.05f)
                    ),
                    shape = RoundedCornerShape(32.dp)
                )
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

            val options = remember(isReflowAvailable, currentScrollMode) {
                buildList {
                    add(MasterMenuOption("Narrar", Icons.AutoMirrored.Filled.VolumeUp, MasterMenuOptionType.TTS))
                    add(MasterMenuOption("Índice", Icons.Default.Menu, MasterMenuOptionType.TOC))
                    add(MasterMenuOption("Buscar", Icons.Default.Search, MasterMenuOptionType.SEARCH))
                    val scrollLabel = if (currentScrollMode == ScrollMode.PAGINATED) "Continuo" else "Paginado"
                    val scrollIcon = if (currentScrollMode == ScrollMode.PAGINATED) Icons.Default.SwapVert else Icons.AutoMirrored.Filled.MenuBook
                    add(MasterMenuOption(scrollLabel, scrollIcon, MasterMenuOptionType.SCROLL_MODE))
                    if (isReflowAvailable) {
                        add(MasterMenuOption("Modo Móvil", Icons.Default.Smartphone, MasterMenuOptionType.REFLOW))
                    }
                    add(MasterMenuOption("Ajustes", Icons.Default.Settings, MasterMenuOptionType.SETTINGS))
                    add(MasterMenuOption("Marcador", Icons.Default.BookmarkBorder, MasterMenuOptionType.BOOKMARK))
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
                else HiddenForest.SurfaceVariant.copy(alpha = 0.4f)
            )
            .border(
                width = 1.dp,
                color = if (isPressed) HiddenForest.Accent.copy(0.3f) else Color.Transparent,
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
                tint = HiddenForest.Accent,
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
    val type: MasterMenuOptionType
)

enum class MasterMenuOptionType {
    TTS, TOC, SEARCH, SETTINGS, BOOKMARK, REFLOW, SCROLL_MODE
}
