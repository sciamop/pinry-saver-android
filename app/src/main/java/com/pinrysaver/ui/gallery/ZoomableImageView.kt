package com.pinrysaver.ui.gallery

import android.content.Context
import android.graphics.Matrix
import android.graphics.PointF
import android.util.AttributeSet
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import androidx.appcompat.widget.AppCompatImageView
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class ZoomableImageView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : AppCompatImageView(context, attrs, defStyleAttr) {

    private val matrix = Matrix()
    private var mode = NONE

    // Zoom state
    private var scale = 1f
    private var baseScale = 1f // The "fit to screen" scale
    private var minScale = 1f
    private var maxScale = 5f

    // Pan state
    private val last = PointF()
    private val start = PointF()
    private var baseTranslateX = 0f
    private var baseTranslateY = 0f

    // Gesture detectors
    private val scaleDetector: ScaleGestureDetector
    private val gestureDetector: GestureDetector

    // Callbacks
    var onDismissGesture: ((startX: Float, dy: Float) -> Boolean)? = null
    var onDismissEnd: (() -> Unit)? = null
    var onZoomStateChanged: ((isZooming: Boolean) -> Unit)? = null
    var shouldAllowPaging: ((startX: Float, dx: Float) -> Boolean)? = null

    private var isZooming = false
    private var zoomEndRunnable: Runnable? = null
    private var dragStartX = 0f
    private var isDismissing = false

    init {
        scaleType = ScaleType.FIT_CENTER

        scaleDetector = ScaleGestureDetector(context, ScaleListener())
        gestureDetector = GestureDetector(context, GestureListener())
    }

    override fun setImageDrawable(drawable: android.graphics.drawable.Drawable?) {
        super.setImageDrawable(drawable)
        resetZoom()
    }

    override fun setImageBitmap(bm: android.graphics.Bitmap?) {
        super.setImageBitmap(bm)
        resetZoom()
    }

    private fun initializeMatrix() {
        if (drawable == null) return

        val drawableWidth = drawable.intrinsicWidth.toFloat()
        val drawableHeight = drawable.intrinsicHeight.toFloat()
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (drawableWidth <= 0 || drawableHeight <= 0 || viewWidth <= 0 || viewHeight <= 0) {
            return
        }

        // Calculate scale to fit image in view
        val scaleX = viewWidth / drawableWidth
        val scaleY = viewHeight / drawableHeight
        val fitScale = min(scaleX, scaleY)

        // Center the image
        baseTranslateX = (viewWidth - drawableWidth * fitScale) / 2f
        baseTranslateY = (viewHeight - drawableHeight * fitScale) / 2f

        baseScale = fitScale
        scale = 1f // User scale multiplier

        matrix.reset()
        matrix.postScale(fitScale, fitScale)
        matrix.postTranslate(baseTranslateX, baseTranslateY)

        scaleType = ScaleType.MATRIX
        imageMatrix = matrix
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        super.onLayout(changed, left, top, right, bottom)
        if (changed || scale == 1f) {
            initializeMatrix()
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        scaleDetector.onTouchEvent(event)
        gestureDetector.onTouchEvent(event)

        val curr = PointF(event.x, event.y)

        when (event.action and MotionEvent.ACTION_MASK) {
            MotionEvent.ACTION_DOWN -> {
                last.set(curr)
                start.set(last)
                dragStartX = event.x
                isDismissing = false
                mode = DRAG
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                last.set(curr)
                mode = ZOOM
            }
            MotionEvent.ACTION_MOVE -> {
                if (mode == DRAG) {
                    val dx = curr.x - last.x
                    val dy = curr.y - last.y

                    if (scale > 1f) {
                        // Pan when zoomed
                        matrix.postTranslate(dx, dy)
                        fixTranslation()
                        imageMatrix = matrix
                        last.set(curr)
                        return true
                    } else {
                        // Not zoomed - check for dismiss or allow paging
                        val totalDy = curr.y - start.y
                        val totalDx = curr.x - start.x
                        
                        if (!isDismissing && totalDy > 20 && abs(totalDy) > abs(totalDx) * 1.2f) {
                            // Primarily vertical down gesture
                            val handled = onDismissGesture?.invoke(dragStartX, totalDy) ?: false
                            if (handled) {
                                isDismissing = true
                                parent?.requestDisallowInterceptTouchEvent(true)
                            }
                        }
                        
                        if (isDismissing) {
                            // Continue dismiss gesture - keep calling callback
                            onDismissGesture?.invoke(dragStartX, totalDy)
                            last.set(curr)
                            return true
                        }
                        
                        // Check if paging should be allowed (left/right 33%)
                        val allowPaging = shouldAllowPaging?.invoke(dragStartX, totalDx) ?: true
                        if (!allowPaging) {
                            // Block paging in middle zone
                            parent?.requestDisallowInterceptTouchEvent(true)
                            last.set(curr)
                            return true
                        }
                        
                        // Allow ViewPager to handle horizontal swipes
                        parent?.requestDisallowInterceptTouchEvent(false)
                        return false
                    }
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_POINTER_UP -> {
                if (isDismissing) {
                    onDismissEnd?.invoke()
                    isDismissing = false
                }
                mode = NONE
                parent?.requestDisallowInterceptTouchEvent(false)
            }
        }

        return true
    }

    private inner class ScaleListener : ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScaleBegin(detector: ScaleGestureDetector): Boolean {
            if (!isZooming) {
                isZooming = true
                onZoomStateChanged?.invoke(true)
            }
            return true
        }

        override fun onScale(detector: ScaleGestureDetector): Boolean {
            val scaleFactor = detector.scaleFactor
            val newScale = scale * scaleFactor

            // Allow zoom from 1x to 5x (relative to fit-to-screen)
            if (newScale in minScale..maxScale) {
                scale = newScale
                
                // Get current matrix values
                val values = FloatArray(9)
                matrix.getValues(values)
                
                // Apply scale around the focal point
                matrix.postScale(scaleFactor, scaleFactor, detector.focusX, detector.focusY)
                fixTranslation()
                imageMatrix = matrix
            }

            return true
        }

        override fun onScaleEnd(detector: ScaleGestureDetector) {
            super.onScaleEnd(detector)
            // Snap back if zoomed out too far
            if (scale < minScale) {
                resetZoom()
            }

            // Schedule zoom end callback with delay
            zoomEndRunnable?.let { removeCallbacks(it) }
            val runnable = Runnable {
                isZooming = false
                onZoomStateChanged?.invoke(false)
                zoomEndRunnable = null
            }
            zoomEndRunnable = runnable
            postDelayed(runnable, ZOOM_END_DELAY_MS)
        }
    }

    private inner class GestureListener : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            resetZoom()
            return true
        }
    }

    private fun fixTranslation() {
        val values = FloatArray(9)
        matrix.getValues(values)

        val currentScale = values[Matrix.MSCALE_X]
        val transX = values[Matrix.MTRANS_X]
        val transY = values[Matrix.MTRANS_Y]

        val drawableWidth = drawable?.intrinsicWidth?.toFloat() ?: 0f
        val drawableHeight = drawable?.intrinsicHeight?.toFloat() ?: 0f
        val viewWidth = width.toFloat()
        val viewHeight = height.toFloat()

        if (drawableWidth <= 0 || drawableHeight <= 0) return

        val scaledWidth = drawableWidth * currentScale
        val scaledHeight = drawableHeight * currentScale

        var fixTransX = 0f
        var fixTransY = 0f

        // Fix horizontal translation
        if (scaledWidth <= viewWidth) {
            // Center horizontally if image is smaller than view
            fixTransX = (viewWidth - scaledWidth) / 2f - transX
        } else {
            // Constrain to edges
            if (transX > 0) {
                fixTransX = -transX
            } else if (transX + scaledWidth < viewWidth) {
                fixTransX = viewWidth - scaledWidth - transX
            }
        }

        // Fix vertical translation
        if (scaledHeight <= viewHeight) {
            // Center vertically if image is smaller than view
            fixTransY = (viewHeight - scaledHeight) / 2f - transY
        } else {
            // Constrain to edges
            if (transY > 0) {
                fixTransY = -transY
            } else if (transY + scaledHeight < viewHeight) {
                fixTransY = viewHeight - scaledHeight - transY
            }
        }

        if (fixTransX != 0f || fixTransY != 0f) {
            matrix.postTranslate(fixTransX, fixTransY)
        }
    }

    fun resetZoom() {
        scale = 1f
        initializeMatrix()
    }

    companion object {
        private const val NONE = 0
        private const val DRAG = 1
        private const val ZOOM = 2
        private const val ZOOM_END_DELAY_MS = 200L
    }
}

