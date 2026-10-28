package com.example.woodsreader.ui.reader

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.woodsreader.ui.theme.HiddenForest
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

/**
 * Muestra el contenido de una nota al pie en un BottomSheet sin perder la posición de lectura.
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
