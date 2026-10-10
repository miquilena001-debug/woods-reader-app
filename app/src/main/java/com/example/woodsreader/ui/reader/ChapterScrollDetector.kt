package com.example.woodsreader.ui.reader

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * Detects scroll-to-edge gestures on the WebView inside [rootView] and invokes
 * [onGoForward] / [onGoBackward] when the user drags past a threshold at the boundary.
 *
 * M-10: Soporte para "Pull-to-next-chapter" con progreso visual.
 */
class ChapterScrollDetector(
    private val rootView: View,
    private val scope: CoroutineScope,
    private val onGoForward: () -> Unit,
    private val onGoBackward: () -> Unit,
    private val onPullProgress: (Float) -> Unit = {},
    private val onPageForward: (() -> Unit)? = null,
    private val onPageBackward: (() -> Unit)? = null,
    private val isPaginatedMode: () -> Boolean = { false },
    private val thresholdDp: Float = 120f
) {
    // Convertir dp a píxeles en tiempo de ejecución para densidad correcta en todos los dispositivos
    private val thresholdPx: Float get() = thresholdDp * rootView.context.resources.displayMetrics.density
    // Conjunto de WebViews a los que ya les agregamos el listener (Weak para evitar leaks en destrucción)
    private val attachedWebViews = java.util.Collections.newSetFromMap(java.util.WeakHashMap<WebView, Boolean>())
    private var touchStartX = 0f
    private var touchStartY = 0f
    private var triggered = false

    @SuppressLint("ClickableViewAccessibility")
    private val touchListener = View.OnTouchListener { view, event ->
        val wv = view as? WebView ?: return@OnTouchListener false
        
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                triggered = false
                onPullProgress(0f)
            }
            MotionEvent.ACTION_MOVE -> {
                if (!triggered) {
                    val dx = touchStartX - event.x   // positive = dragging left (going forward)
                    val dy = touchStartY - event.y   // positive = dragging up (read going down edge)
                    
                    // 1. Desplazamiento Vertical (Modo continuo vertical)
                    if (kotlin.math.abs(dy) >= kotlin.math.abs(dx)) {
                        // Arrastre hacia ARRIBA al final de la página -> Siguiente capítulo
                        if (dy > 0 && !wv.canScrollVertically(1)) {
                            val progress = (dy / thresholdPx).coerceIn(0f, 1f)
                            onPullProgress(progress)
                            if (progress >= 1f) {
                                triggered = true
                                onPullProgress(0f)
                                onGoForward()
                            }
                        } 
                        // Arrastre hacia ABAJO al inicio de la página -> Capítulo anterior
                        else if (dy < 0 && !wv.canScrollVertically(-1)) {
                            val progress = (-dy / thresholdPx).coerceIn(0f, 1f)
                            onPullProgress(-progress)
                            if (progress >= 1f) {
                                triggered = true
                                onPullProgress(0f)
                                onGoBackward()
                            }
                        } else {
                            onPullProgress(0f)
                        }
                    } 
                    // 2. Desplazamiento Horizontal (Modo libro paginado)
                    else {
                        // Deslizar hacia la IZQUIERDA al final del capítulo -> Siguiente capítulo
                        if (dx > 0 && !wv.canScrollHorizontally(1)) {
                            val progress = (dx / thresholdPx).coerceIn(0f, 1f)
                            onPullProgress(progress)
                            if (progress >= 1f) {
                                triggered = true
                                onPullProgress(0f)
                                onGoForward()
                            }
                        }
                        // Deslizar hacia la DERECHA al inicio del capítulo -> Capítulo anterior
                        else if (dx < 0 && !wv.canScrollHorizontally(-1)) {
                            val progress = (-dx / thresholdPx).coerceIn(0f, 1f)
                            onPullProgress(-progress)
                            if (progress >= 1f) {
                                triggered = true
                                onPullProgress(0f)
                                onGoBackward()
                            }
                        } else {
                            onPullProgress(0f)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                if (isPaginatedMode() && !triggered) {
                    val dx = touchStartX - event.x
                    val dy = touchStartY - event.y
                    val swipeThreshold = 40f * rootView.context.resources.displayMetrics.density
                    if (kotlin.math.abs(dx) >= swipeThreshold && kotlin.math.abs(dx) > kotlin.math.abs(dy) * 1.2f) {
                        triggered = true
                        onPullProgress(0f)
                        if (dx > 0) {
                            onPageForward?.invoke() ?: onGoForward()
                        } else {
                            onPageBackward?.invoke() ?: onGoBackward()
                        }
                        return@OnTouchListener true
                    }
                }
                onPullProgress(0f)
                triggered = false
            }
            MotionEvent.ACTION_CANCEL -> {
                onPullProgress(0f)
                triggered = false
            }
        }
        false // Let WebView handle other interactions (like taps)
    }

    private val globalLayoutListener = android.view.ViewTreeObserver.OnGlobalLayoutListener {
        val webViews = mutableListOf<WebView>()
        findAllWebViews(rootView, webViews)
        
        for (wv in webViews) {
            if (attachedWebViews.add(wv)) {
                wv.setOnTouchListener(touchListener)
            }
        }
    }

    fun attach() {
        rootView.viewTreeObserver.addOnGlobalLayoutListener(globalLayoutListener)
        rootView.post {
            globalLayoutListener.onGlobalLayout()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    fun detach() {
        rootView.viewTreeObserver.removeOnGlobalLayoutListener(globalLayoutListener)
        for (wv in attachedWebViews) {
            wv.setOnTouchListener(null)
        }
        attachedWebViews.clear()
    }

    // Busca recursivamente TODOS los WebViews dentro de Readium
    private fun findAllWebViews(v: View, out: MutableList<WebView>) {
        if (v is WebView) {
            out.add(v)
        } else if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                findAllWebViews(v.getChildAt(i), out)
            }
        }
    }
}
