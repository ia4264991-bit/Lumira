package com.aipdfreader.app.selection.capture

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.consume
import com.aipdfreader.app.selection.model.StrokePath
import com.aipdfreader.app.selection.model.StrokePoint
import com.aipdfreader.app.selection.model.ViewportSnapshot

/**
 * The Compose-facing gesture layer for freeform lasso drawing.
 *
 * Captures a single-pointer drag as a sequence of [StrokePoint]s (thinned by
 * [strokeThinner] as they arrive), and cancels the in-progress stroke the
 * moment a second pointer touches down — per the approved design (§9,
 * "A second finger appears mid-draw"), simultaneous pinch-zoom-while-drawing
 * is explicitly out of scope for this foundation milestone; a second
 * pointer is treated as unambiguous cancellation rather than a mode switch.
 *
 * This class holds no Compose state itself (no `mutableStateOf`) — it is a
 * pure gesture-detection routine that reports what happened via callbacks.
 * Callers decide how to react (e.g. mutating an imperative `Path` for live
 * rendering, per the approved design's performance guidance in §8) — this
 * class never drives recomposition on its own.
 */
class LassoGestureController(
    private val strokeThinner: StrokeThinner = StrokeThinner()
) {

    /**
     * Detects lasso gestures continuously — one at a time, looping for the
     * lifetime of the `pointerInput` block this runs inside, exactly like
     * the platform's own `detectDragGestures`/`detectTransformGestures`.
     * Each finger-down-to-finger-up cycle is one gesture; this suspends
     * between gestures waiting for the next one.
     *
     * @param pageIndex the page this gesture is being drawn on; stamped onto
     *   the resulting [StrokePath] unchanged.
     * @param captureViewportSnapshot invoked once, at the moment the finger
     *   goes down, to record the pan/zoom/bitmap state the stroke should be
     *   interpreted against (see [ViewportSnapshot]).
     * @param onStrokeStarted called once a touch-down is accepted as the
     *   beginning of a new stroke.
     * @param onPointCaptured called for every point that survives
     *   [strokeThinner], including the first — the natural hook for live,
     *   per-frame drawing feedback.
     * @param onStrokeFinished called when the finger lifts normally, with
     *   the complete, finalized [StrokePath].
     * @param onStrokeCancelled called instead of [onStrokeFinished] if a
     *   second pointer interrupted the gesture, or if too few points were
     *   captured to form a meaningful stroke.
     */
    suspend fun detectLassoGesture(
        scope: PointerInputScope,
        pageIndex: Int,
        captureViewportSnapshot: () -> ViewportSnapshot,
        onStrokeStarted: () -> Unit,
        onPointCaptured: (StrokePoint) -> Unit,
        onStrokeFinished: (StrokePath) -> Unit,
        onStrokeCancelled: () -> Unit
    ) {
        with(scope) {
            awaitEachGesture {
                strokeThinner.reset()

                val down = awaitFirstDown(requireUnconsumed = false)
                // Pre-sized rather than growing from empty: a typical lasso,
                // even before RDP simplification, rarely exceeds a few
                // hundred thinned points, so this avoids most/all of the
                // backing array's doubling-reallocation steps during a drag.
                val capturedPoints = ArrayList<StrokePoint>(INITIAL_POINT_CAPACITY)
                val viewportSnapshot = captureViewportSnapshot()
                val downPointerId = down.id

                val firstPoint = StrokePoint(
                    x = down.position.x,
                    y = down.position.y,
                    timestampMs = down.uptimeMillis
                )
                strokeThinner.shouldAccept(firstPoint) // always true; seeds the thinner
                capturedPoints += firstPoint
                onStrokeStarted()
                onPointCaptured(firstPoint)
                down.consume()

                var cancelledByMultitouch = false

                while (true) {
                    val event = awaitPointerEvent()
                    val changes = event.changes

                    var pressedCount = 0
                    for (i in changes.indices) {
                        if (changes[i].pressed) pressedCount++
                    }
                    if (pressedCount > 1) {
                        cancelledByMultitouch = true
                        break
                    }

                    // Manual search instead of `firstOrNull { it.id == downPointerId }`:
                    // that lambda captures `downPointerId`, so a fresh
                    // closure would otherwise be allocated on every one of
                    // these (potentially high-frequency) pointer-event
                    // iterations for the lifetime of the drag.
                    var change = changes.getOrNull(0)
                    for (i in changes.indices) {
                        if (changes[i].id == downPointerId) {
                            change = changes[i]
                            break
                        }
                    }
                    if (change == null) break

                    if (change.changedToUp()) {
                        change.consume()
                        break
                    }

                    val candidate = StrokePoint(
                        x = change.position.x,
                        y = change.position.y,
                        timestampMs = change.uptimeMillis
                    )
                    if (strokeThinner.shouldAccept(candidate)) {
                        capturedPoints += candidate
                        onPointCaptured(candidate)
                    }
                    change.consume()
                }

                if (cancelledByMultitouch || capturedPoints.size < MIN_POINTS_FOR_VALID_STROKE) {
                    onStrokeCancelled()
                } else {
                    onStrokeFinished(
                        StrokePath(
                            pageIndex = pageIndex,
                            points = capturedPoints,
                            viewportSnapshot = viewportSnapshot
                        )
                    )
                }
            }
        }
    }

    companion object {
        private const val MIN_POINTS_FOR_VALID_STROKE = 3
        private const val INITIAL_POINT_CAPACITY = 128
    }
}
