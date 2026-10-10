package com.example.woodsreader.ui.reader.animation

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Rect
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.PixelCopy
import android.view.View
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.example.woodsreader.model.PageTurnStyle
import com.example.woodsreader.model.ReaderTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume
import kotlin.math.PI
import kotlin.math.sin

/**
 * Dirección del cambio de página.
 */
enum class PageTurnDirection {
    FORWARD,
    BACKWARD
}

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Captura precisa de la pantalla actual usando PixelCopy (hardware framebuffer directo).
 * Clampeado a los límites de la ventana para máxima fiabilidad en WebViews GPU.
 */
private suspend fun captureViewSnapshot(view: View): Bitmap? = withContext(Dispatchers.Main) {
    if (view.width <= 0 || view.height <= 0) return@withContext null

    val activity = view.context.findActivity()
    val window = activity?.window

    if (window != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val decorView = window.decorView
        val winWidth = decorView.width.takeIf { it > 0 } ?: view.width
        val winHeight = decorView.height.takeIf { it > 0 } ?: view.height

        val location = IntArray(2)
        view.getLocationInWindow(location)

        val left = location[0].coerceIn(0, winWidth)
        val top = location[1].coerceIn(0, winHeight)
        val right = (location[0] + view.width).coerceIn(left, winWidth)
        val bottom = (location[1] + view.height).coerceIn(top, winHeight)

        val rectWidth = right - left
        val rectHeight = bottom - top
        if (rectWidth <= 0 || rectHeight <= 0) return@withContext null

        val bitmap = try {
            Bitmap.createBitmap(rectWidth, rectHeight, Bitmap.Config.ARGB_8888)
        } catch (_: Throwable) {
            null
        }

        if (bitmap != null) {
            val rect = Rect(left, top, right, bottom)
            val success = suspendCancellableCoroutine<Boolean> { cont ->
                try {
                    PixelCopy.request(
                        window,
                        rect,
                        bitmap,
                        { copyResult ->
                            if (cont.isActive) {
                                cont.resume(copyResult == PixelCopy.SUCCESS)
                            }
                        },
                        Handler(Looper.getMainLooper())
                    )
                } catch (_: Throwable) {
                    if (cont.isActive) cont.resume(false)
                }
            }

            if (success) {
                return@withContext bitmap
            } else {
                bitmap.recycle()
            }
        }
    }

    // Fallback: draw sobre software canvas si PixelCopy no estuviera disponible
    try {
        val bmp = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = AndroidCanvas(bmp)
        view.draw(canvas)
        bmp
    } catch (_: Throwable) {
        null
    }
}

/**
 * Gestor del estado de la animación de paso de página.
 * Administra el ciclo de vida del bitmap de captura de forma segura,
 * garantizando el reciclado de memoria al finalizar.
 */
@Stable
class BookPageTurnState {
    var isTurning by mutableStateOf(false)
        private set

    var direction by mutableStateOf(PageTurnDirection.FORWARD)
        private set

    var snapshotBitmap by mutableStateOf<Bitmap?>(null)
        private set

    val progress = Animatable(0f)

    suspend fun turnPage(
        view: View?,
        dir: PageTurnDirection,
        style: PageTurnStyle,
        onNavigate: () -> Unit
    ) {
        if (style == PageTurnStyle.NONE || view == null || view.width <= 0 || view.height <= 0) {
            onNavigate()
            return
        }

        // Si ya hay una animación en curso, permitir que continúe
        if (isTurning) {
            onNavigate()
            return
        }

        // Captura con PixelCopy
        val captured = captureViewSnapshot(view)
        if (captured == null) {
            onNavigate()
            return
        }

        snapshotBitmap = captured
        direction = dir
        isTurning = true
        progress.snapTo(0f)

        // Ejecutar navegación de Readium en segundo plano para que la nueva página se prepare de inmediato
        onNavigate()

        try {
            val duration = if (style == PageTurnStyle.BOOK_FLIP) 270 else 230
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = duration,
                    easing = FastOutSlowInEasing
                )
            )
        } finally {
            isTurning = false
            snapshotBitmap?.recycle()
            snapshotBitmap = null
        }
    }
}

@Composable
fun rememberBookPageTurnState(): BookPageTurnState {
    return remember { BookPageTurnState() }
}

/**
 * Capa de superposición que renderiza la animación de libro 3D o deslizamiento.
 * La física garantiza que la página activa siempre se despega suavemente en la
 * dirección correspondiente (hacia la izquierda para avanzar, hacia la derecha para retroceder),
 * revelando la nueva página debajo sin saltos ni rectángulos negros.
 */
@Composable
fun BookPageTurnOverlay(
    state: BookPageTurnState,
    style: PageTurnStyle,
    theme: ReaderTheme,
    modifier: Modifier = Modifier
) {
    if (!state.isTurning || style == PageTurnStyle.NONE) return
    val bitmap = state.snapshotBitmap ?: return

    val p = state.progress.value
    val density = LocalDensity.current.density
    val isForward = state.direction == PageTurnDirection.FORWARD

    val spineShadowColor = when (theme) {
        ReaderTheme.DAY, ReaderTheme.SEPIA -> Color(0x33000000)
        else -> Color(0x55000000)
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (style == PageTurnStyle.BOOK_FLIP) {
            // ── 1. Sombra Permanente del Lomo (Spine Crease) ──────────────────────
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(18.dp)
                    .align(Alignment.CenterStart)
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(spineShadowColor, Color.Transparent)
                        )
                    )
            )

            // ── 2. Curvatura y Despegue de Página con Iluminación Dinámica ────────
            // Curva sinusoidal: 0 en reposo, máxima flexión en el centro del pase (p=0.5), 0 al salir
            val bend = sin(p * PI.toFloat())

            // Desplazamiento horizontal fluido:
            // FORWARD: la hoja se desliza de 0 a -width * 1.05f (saliendo por la izquierda)
            // BACKWARD: la hoja se desliza de 0 a +width * 1.05f (saliendo por la derecha)
            val translationFactor = if (isForward) -p * 1.05f else p * 1.05f

            // Ángulo 3D de inclinación en perspectiva:
            // FORWARD: se eleva hacia adentro (-24° max)
            // BACKWARD: se eleva hacia afuera (+24° max)
            val tiltAngle = if (isForward) -24f * bend else 24f * bend

            // Contracción de perspectiva (escala cilíndrica)
            val scaleFactor = 1f - (0.04f * bend)

            // Origen de transformación 3D:
            // FORWARD: gira con bisagra izquierda (0f, 0.5f)
            // BACKWARD: gira con bisagra derecha (1f, 0.5f)
            val origin = if (isForward) TransformOrigin(0f, 0.5f) else TransformOrigin(1f, 0.5f)

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = size.width * translationFactor
                        rotationY = tiltAngle
                        scaleX = scaleFactor
                        cameraDistance = 16f * density
                        transformOrigin = origin
                    }
            ) {
                // Imagen capturada de la página en transición
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )

                // Efectos de papel: Brillo especular y sombreado de curvatura
                Canvas(modifier = Modifier.fillMaxSize()) {
                    // ── Brillo especular que barre el cilindro de curvatura ──────
                    if (bend > 0.05f) {
                        val highlightCenter = if (isForward) {
                            size.width * (1f - p * 0.8f)
                        } else {
                            size.width * (p * 0.8f)
                        }
                        val highlightWidth = size.width * 0.22f
                        val highlightAlpha = 0.18f * bend

                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(
                                    Color.Transparent,
                                    Color.White.copy(alpha = highlightAlpha),
                                    Color.Transparent
                                ),
                                startX = (highlightCenter - highlightWidth).coerceAtLeast(0f),
                                endX = (highlightCenter + highlightWidth).coerceAtMost(size.width)
                            )
                        )
                    }

                    // ── Sombra de elevación sobre el borde que se despega ────────
                    if (isForward) {
                        // Sombra en el borde derecho que se levanta primero
                        val edgeShadowWidth = size.width * 0.15f
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.22f * bend)),
                                startX = size.width - edgeShadowWidth,
                                endX = size.width
                            ),
                            topLeft = Offset(size.width - edgeShadowWidth, 0f),
                            size = Size(edgeShadowWidth, size.height)
                        )
                    } else {
                        // Sombra en el borde izquierdo que se levanta primero
                        val edgeShadowWidth = size.width * 0.15f
                        drawRect(
                            brush = Brush.horizontalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.22f * bend), Color.Transparent),
                                startX = 0f,
                                endX = edgeShadowWidth
                            ),
                            topLeft = Offset(0f, 0f),
                            size = Size(edgeShadowWidth, size.height)
                        )
                    }

                    // Sombreado sutil global de elevación en el pliegue
                    val liftShadow = (0.12f * bend).coerceIn(0f, 0.15f)
                    drawRect(color = Color.Black.copy(alpha = liftShadow))
                }

                // ── Sombra proyectada exterior que cae sobre la página de abajo ───
                // Al avanzar (FORWARD), la sombra se proyecta hacia la derecha del borde derecho
                // Al retroceder (BACKWARD), la sombra se proyecta hacia la izquierda del borde izquierdo
                if (isForward) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(28.dp)
                            .align(Alignment.CenterEnd)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.35f * (1f - p * 0.4f))
                                    )
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(28.dp)
                            .align(Alignment.CenterStart)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.35f * (1f - p * 0.4f)),
                                        Color.Transparent
                                    )
                                )
                            )
                    )
                }
            }
        } else if (style == PageTurnStyle.SLIDE) {
            // ── Estilo Deslizamiento (Slide Horizontal Limpio) ────────────────────
            // FORWARD: se desliza hacia la izquierda revelando la nueva página (0 -> -width)
            // BACKWARD: se desliza hacia la derecha revelando la página anterior (0 -> +width)
            val translationFactor = if (isForward) -p else p

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = size.width * translationFactor
                    }
            ) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.FillBounds
                )

                // Sombra de borde proyectada sobre la página entrante
                if (isForward) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(24.dp)
                            .align(Alignment.CenterEnd)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f))
                                )
                            )
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(24.dp)
                            .align(Alignment.CenterStart)
                            .background(
                                Brush.horizontalGradient(
                                    colors = listOf(Color.Black.copy(alpha = 0.32f), Color.Transparent)
                                )
                            )
                    )
                }
            }
        }
    }
}
