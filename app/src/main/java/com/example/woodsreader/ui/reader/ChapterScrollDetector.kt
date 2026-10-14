package com.example.woodsreader.ui.reader

import android.annotation.SuppressLint
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.viewpager.widget.ViewPager
import kotlinx.coroutines.CoroutineScope

/**
 * Detecta y gestiona los gestos de lectura unificados para una experiencia fluida e integral:
 *
 * - NAVEGACIÓN HORIZONTAL (Swipe izq/der): Avanza o retrocede de página dentro del capítulo actual.
 *   El ViewPager de Readium se neutraliza para evitar que secuestre los gestos y salte capítulos enteros.
 * - NAVEGACIÓN VERTICAL (Scroll continuo): Desplazamiento fluido. Al llegar al extremo inferior o superior,
 *   un arrastre suave supera el umbral (45dp) y pasa fluidamente al siguiente o anterior capítulo.
 *   En modo paginado fijo, un arrastre vertical arriba/abajo también permite avanzar o retroceder de página,
 *   reservando el salto de capítulo por pull exclusivamente para cuando se llega al final del capítulo.
 */
class ChapterScrollDetector(
    private val rootView: View,
    private val scope: CoroutineScope,
    private val onGoForward: () -> Unit,
    private val onGoBackward: () -> Unit,
    private val onPullProgress: (Float) -> Unit = {},
    private val onPageForward: ((WebView?) -> Unit)? = null,
    private val onPageBackward: ((WebView?) -> Unit)? = null,
    private val onClearSelection: () -> Unit = {},
    private val thresholdDp: Float = 68f
) {
    private val thresholdPx: Float get() = thresholdDp * rootView.context.resources.displayMetrics.density
    private val attachedWebViews = java.util.Collections.newSetFromMap(java.util.WeakHashMap<WebView, Boolean>())

    private var touchStartX = 0f
    private var touchStartY = 0f
    private var touchStartTime = 0L
    private var triggered = false
    private var isHorizontalSwipe = false
    private var isVerticalDrag = false
    private var canAdvancePage = true
    private var canRetreatPage = true

    private fun disallowParentIntercept(view: View) {
        var p = view.parent
        while (p != null) {
            p.requestDisallowInterceptTouchEvent(true)
            p = p.parent
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private val touchListener = View.OnTouchListener { view, event ->
        val wv = view as? WebView ?: return@OnTouchListener false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_POINTER_DOWN -> {
                touchStartX = event.x
                touchStartY = event.y
                touchStartTime = System.currentTimeMillis()
                triggered = false
                isHorizontalSwipe = false
                isVerticalDrag = false
                onPullProgress(0f)
                disallowParentIntercept(view)

                // En modo paginado (sin scroll vertical interno), verificar si hay más páginas adelante o atrás
                if (!wv.canScrollVertically(1) && !wv.canScrollVertically(-1)) {
                    wv.canAdvancePageInChapter { canAdvancePage = it }
                    wv.canRetreatPageInChapter { canRetreatPage = it }
                } else {
                    canAdvancePage = false
                    canRetreatPage = false
                }
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = touchStartX - event.x
                val dy = touchStartY - event.y
                val absDy = kotlin.math.abs(dy)
                val absDx = kotlin.math.abs(dx)
                val density = wv.context.resources.displayMetrics.density

                // Descartar cualquier selección previa de texto al comenzar a deslizar o arrastrar
                if (absDy > 8f * density || absDx > 8f * density) {
                    onClearSelection()
                }

                // 1. Detección y bloqueo exclusivo de Swipe Horizontal
                // Consumir el evento (return true) y mantener disallowParentIntercept para evitar
                // que Chromium libere el bloqueo y ViewPager salte al siguiente capítulo de forma descontrolada.
                if (!isVerticalDrag && (isHorizontalSwipe || (absDx > absDy * 1.35f && absDx > 12f * density))) {
                    isHorizontalSwipe = true
                    disallowParentIntercept(view)
                    return@OnTouchListener true
                }

                // 2. Detección de arrastre vertical para cambio fluido de capítulo en los extremos
                if (!isHorizontalSwipe && (isVerticalDrag || (absDy >= absDx && absDy > 8f * density))) {
                    isVerticalDrag = true
                    val hasVerticalScroll = wv.canScrollVertically(1) || wv.canScrollVertically(-1)

                    if (hasVerticalScroll) {
                        // En contenido con scroll vertical continuo:
                        if (dy > 0 && !wv.canScrollVertically(1)) {
                            // Arrastre hacia arriba al final del capítulo -> Actualizar progreso visual
                            val progress = (dy / thresholdPx).coerceIn(0f, 1f)
                            onPullProgress(progress)
                        } else if (dy < 0 && !wv.canScrollVertically(-1)) {
                            // Arrastre hacia abajo al inicio del capítulo -> Actualizar progreso visual
                            val progress = (-dy / thresholdPx).coerceIn(0f, 1f)
                            onPullProgress(-progress)
                        } else {
                            onPullProgress(0f)
                        }
                    } else {
                        // En contenido paginado por columnas:
                        if (dy > 0 && !canAdvancePage) {
                            val progress = (dy / thresholdPx).coerceIn(0f, 1f)
                            onPullProgress(progress)
                        } else if (dy < 0 && !canRetreatPage) {
                            val progress = (-dy / thresholdPx).coerceIn(0f, 1f)
                            onPullProgress(-progress)
                        } else {
                            onPullProgress(0f)
                        }
                    }
                }
            }
            MotionEvent.ACTION_UP -> {
                val dx = touchStartX - event.x
                val dy = touchStartY - event.y
                val dt = System.currentTimeMillis() - touchStartTime
                val density = wv.context.resources.displayMetrics.density
                val absDx = kotlin.math.abs(dx)
                val absDy = kotlin.math.abs(dy)
                val minSwipeDistance = 24f * density
                val isFlick = dt < 350 && (absDx > 18f * density)

                val wasHorizontal = isHorizontalSwipe || (absDx > absDy * 1.35f && (absDx >= minSwipeDistance || isFlick))

                if (wasHorizontal) {
                    // Swipe horizontal predominante -> Avanzar o retroceder página dentro del capítulo actual
                    onClearSelection()
                    if (dx > 0) {
                        onPageForward?.invoke(wv)
                    } else {
                        onPageBackward?.invoke(wv)
                    }
                } else if (isVerticalDrag) {
                    val hasVerticalScroll = wv.canScrollVertically(1) || wv.canScrollVertically(-1)
                    if (hasVerticalScroll) {
                        if (dy >= thresholdPx && !wv.canScrollVertically(1)) {
                            onGoForward()
                        } else if (-dy >= thresholdPx && !wv.canScrollVertically(-1)) {
                            onGoBackward()
                        }
                    } else {
                        if (dy >= thresholdPx && !canAdvancePage) {
                            onGoForward()
                        } else if (-dy >= thresholdPx && !canRetreatPage) {
                            onGoBackward()
                        }
                    }
                }

                onPullProgress(0f)
                isHorizontalSwipe = false
                isVerticalDrag = false
                triggered = false
            }
            MotionEvent.ACTION_CANCEL -> {
                onPullProgress(0f)
                isHorizontalSwipe = false
                isVerticalDrag = false
                triggered = false
            }
        }

        false // Permitir que WebView procese clicks, selección y scroll interno
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

    private fun findAllWebViews(v: View, outWebViews: MutableList<WebView>) {
        if (v is WebView) {
            outWebViews.add(v)
        }
        if (v is ViewGroup) {
            for (i in 0 until v.childCount) {
                findAllWebViews(v.getChildAt(i), outWebViews)
            }
        }
    }
}
