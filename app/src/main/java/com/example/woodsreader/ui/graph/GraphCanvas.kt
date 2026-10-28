package com.example.woodsreader.ui.graph

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import com.example.woodsreader.data.graph.EntityType
import com.example.woodsreader.data.graph.GraphEntity
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Colores estándar por tipo de entidad en el grafo.
 */
fun entityColor(type: EntityType): Color = when (type) {
    EntityType.PERSONAJE -> Color(0xFF42A5F5) // Azul claro
    EntityType.SECTA -> Color(0xFFAB47BC)      // Violeta
    EntityType.LUGAR -> Color(0xFF26A69A)      // Turquesa / Teal
    EntityType.ARTEFACTO -> Color(0xFFFFA726)   // Ámbar / Naranja
    EntityType.RANGO -> Color(0xFFEF5350)      // Carmesí / Rojo
    EntityType.OTRO -> Color(0xFF78909C)       // Gris azulado
}

/**
 * Canvas interactivo para el grafo de entidades.
 * Soporta zoom y paneo fluidos con gestos, y selección por toque con cálculo de distancia al nodo más cercano.
 */
@Composable
fun GraphCanvas(
    nodes: List<GraphEntity>,
    edges: List<Pair<Int, Int>>,
    layout: ForceLayout,
    tick: Int,
    selectedIdx: Int?,
    onSelect: (Int?) -> Unit,
    modifier: Modifier = Modifier
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    val currentScale by rememberUpdatedState(scale)
    val currentOffset by rememberUpdatedState(offset)
    val currentNodes by rememberUpdatedState(nodes)
    val currentOnSelect by rememberUpdatedState(onSelect)

    val haptic = LocalHapticFeedback.current
    val textMeasurer = rememberTextMeasurer()
    val primaryColor = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurface
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val edgeBaseColor = if (isDark) Color(0xFFB0BEC5) else Color(0xFF546E7A)

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val ns = (scale * zoom).coerceIn(0.2f, 4f)
                    offset = centroid - (centroid - offset) * (ns / scale) + pan
                    scale = ns
                }
            }
            .pointerInput(Unit) {
                detectTapGestures { p ->
                    val center = Offset(size.width / 2f, size.height / 2f)
                    val worldX = (p.x - currentOffset.x - center.x) / currentScale
                    val worldY = (p.y - currentOffset.y - center.y) / currentScale
                    val threshold = 36f / currentScale

                    var closestIdx: Int? = null
                    var minDistance = Float.MAX_VALUE

                    val nodeCount = minOf(currentNodes.size, layout.n)
                    for (i in 0 until nodeCount) {
                        val dx = worldX - layout.x[i]
                        val dy = worldY - layout.y[i]
                        val dist = sqrt(dx * dx + dy * dy)
                        if (dist < threshold && dist < minDistance) {
                            minDistance = dist
                            closestIdx = i
                        }
                    }
                    if (closestIdx != null) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    currentOnSelect(closestIdx)
                }
            }
    ) {
        // Leer tick dentro del Canvas para forzar redibujo continuo durante la simulación de física
        @Suppress("UNUSED_VARIABLE")
        val _tick = tick

        val center = Offset(size.width / 2f, size.height / 2f)

        translate(left = offset.x + center.x, top = offset.y + center.y) {
            scale(scale = scale, pivot = Offset.Zero) {
                // 1. Dibujo de aristas
                for ((u, v) in edges) {
                    if (u in 0 until layout.n && v in 0 until layout.n) {
                        val touchesSelected = selectedIdx != null && (u == selectedIdx || v == selectedIdx)
                        val edgeAlpha = if (touchesSelected) 0.9f else 0.25f
                        val edgeColor = if (touchesSelected) primaryColor else edgeBaseColor
                        val strokeWidth = if (touchesSelected) 2.5f else 1f

                        drawLine(
                            color = edgeColor.copy(alpha = edgeAlpha),
                            start = Offset(layout.x[u], layout.y[u]),
                            end = Offset(layout.x[v], layout.y[v]),
                            strokeWidth = strokeWidth
                        )
                    }
                }

                // 2. Dibujo de nodos
                val nodeCount = minOf(nodes.size, layout.n)
                for (i in 0 until nodeCount) {
                    val node = nodes[i]
                    val radius = 10f + min(sqrt(node.mentionCount.toFloat()), 18f)
                    val centerPos = Offset(layout.x[i], layout.y[i])
                    val nodeColor = entityColor(node.type)

                    // Si el nodo está seleccionado, dibujar anillo resaltado
                    if (i == selectedIdx) {
                        drawCircle(
                            color = primaryColor,
                            radius = radius + 4f,
                            center = centerPos,
                            style = Stroke(width = 2.5f)
                        )
                    }

                    // Círculo principal del nodo
                    drawCircle(
                        color = nodeColor,
                        radius = radius,
                        center = centerPos
                    )

                    // 3. Etiquetas (LOD según zoom y relevancia de la entidad)
                    // - scale <= 0.4: solo el nodo seleccionado
                    // - 0.4 < scale <= 0.7: nodo seleccionado o entidades principales (mentionCount > 200)
                    // - scale > 0.7: todos los nodos visibles
                    val showLabel = when {
                        i == selectedIdx -> true
                        scale <= 0.4f -> false
                        scale <= 0.7f -> node.mentionCount > 200
                        else -> true
                    }
                    if (showLabel) {
                        val textLayout = textMeasurer.measure(
                            text = AnnotatedString(node.name),
                            style = TextStyle(
                                fontSize = 11.sp,
                                color = labelColor,
                                fontWeight = if (i == selectedIdx) FontWeight.Bold else FontWeight.Normal
                            )
                        )
                        val textPos = Offset(
                            x = centerPos.x - textLayout.size.width / 2f,
                            y = centerPos.y + radius + 2f
                        )
                        drawText(
                            textLayoutResult = textLayout,
                            topLeft = textPos
                        )
                    }
                }
            }
        }
    }
}

/**
 * Pantalla de prueba con 30 nodos y aristas para previsualización y verificación de rendimiento/fluidez.
 */
@Preview(showBackground = true)
@Composable
fun GraphCanvasSamplePreview() {
    val sampleNodes = remember {
        List(30) { i ->
            GraphEntity(
                id = (i + 1).toLong(),
                bookId = "sample_book",
                name = when (i % 6) {
                    0 -> "Personaje $i"
                    1 -> "Secta Celestial $i"
                    2 -> "Montaña Nubosa $i"
                    3 -> "Espada Ancestral $i"
                    4 -> "Reino Alma Naciente $i"
                    else -> "Entidad $i"
                },
                type = EntityType.entries[i % EntityType.entries.size],
                mentionCount = (i * 12 + 8),
                confirmed = true
            )
        }
    }
    val sampleEdges = remember {
        val edges = mutableListOf<Pair<Int, Int>>()
        for (i in 0 until 29) {
            edges.add(Pair(i, i + 1))
            if (i % 4 == 0 && i + 4 < 30) {
                edges.add(Pair(i, i + 4))
            }
        }
        edges
    }
    val layout = remember {
        ForceLayout(sampleNodes.size, sampleEdges).also { fl ->
            repeat(300) { fl.step() }
        }
    }
    var selectedIdx by remember { mutableStateOf<Int?>(null) }

    GraphCanvas(
        nodes = sampleNodes,
        edges = sampleEdges,
        layout = layout,
        tick = 0,
        selectedIdx = selectedIdx,
        onSelect = { selectedIdx = it },
        modifier = Modifier.fillMaxSize()
    )
}
