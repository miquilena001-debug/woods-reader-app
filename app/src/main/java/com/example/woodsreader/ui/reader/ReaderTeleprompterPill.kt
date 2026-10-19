package com.example.woodsreader.ui.reader

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.ui.theme.HiddenForest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

/**
 * Píldora flotante minimalista para el control del Modo Teleprompter / Auto-Scroll.
 * Permite modular WPM en tiempo real (- / +), pausar/reanudar y cerrar el modo manos libres.
 */
@Composable
fun ReaderTeleprompterPill(
    wpm: Int,
    isPaused: Boolean,
    hazeState: HazeState,
    onDecreaseWpm: () -> Unit,
    onIncreaseWpm: () -> Unit,
    onTogglePause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(32.dp),
        color = Color(0xFF0C1811).copy(alpha = 0.95f),
        border = BorderStroke(1.dp, Color(0xFF233B2C)),
        shadowElevation = 8.dp,
        modifier = modifier
            .clip(RoundedCornerShape(32.dp))
            .hazeChild(
                state = hazeState,
                style = ReaderDesign.DeepGlassStyle
            )
    ) {
        Row(
            modifier = Modifier
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Botón Reducir WPM (-)
            IconButton(
                onClick = onDecreaseWpm,
                enabled = wpm > 100,
                modifier = Modifier
                    .size(34.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Reducir velocidad WPM",
                    tint = if (wpm > 100) HiddenForest.Accent else HiddenForest.TextLow,
                    modifier = Modifier.size(17.dp)
                )
            }

            // Indicador de WPM
            Text(
                text = "$wpm WPM",
                style = MaterialTheme.typography.labelMedium.copy(
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp,
                    letterSpacing = 0.5.sp
                ),
                color = if (isPaused) HiddenForest.Amber else HiddenForest.Text,
                modifier = Modifier.padding(horizontal = 6.dp)
            )

            // Botón Aumentar WPM (+)
            IconButton(
                onClick = onIncreaseWpm,
                enabled = wpm < 450,
                modifier = Modifier
                    .size(34.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Aumentar velocidad WPM",
                    tint = if (wpm < 450) HiddenForest.Accent else HiddenForest.TextLow,
                    modifier = Modifier.size(17.dp)
                )
            }

            // Separador vertical sutil
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .height(20.dp)
                    .width(1.dp)
                    .background(Color(0xFF233B2C))
            )

            // Botón Pausar / Reanudar
            IconButton(
                onClick = onTogglePause,
                modifier = Modifier
                    .size(34.dp)
                    .background(
                        if (isPaused) HiddenForest.Amber.copy(alpha = 0.15f)
                        else HiddenForest.Accent.copy(alpha = 0.15f),
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = if (isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                    contentDescription = if (isPaused) "Reanudar Auto-Scroll" else "Pausar Auto-Scroll",
                    tint = if (isPaused) HiddenForest.Amber else HiddenForest.Accent,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Botón Cerrar Teleprompter
            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .size(34.dp)
                    .background(Color.White.copy(alpha = 0.05f), CircleShape)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Cerrar Teleprompter",
                    tint = HiddenForest.TextSecondary,
                    modifier = Modifier.size(17.dp)
                )
            }
        }
    }
}
