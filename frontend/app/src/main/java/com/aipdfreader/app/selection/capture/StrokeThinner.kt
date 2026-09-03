package com.aipdfreader.app.selection.capture

import com.aipdfreader.app.selection.model.StrokePoint

/**
 * Bounds per-gesture memory and CPU cost by dropping a new touch sample if
 * it falls within [minDistancePx] of the last *accepted* point. Without
 * this, a slow, careful drag can emit hundreds of samples per second with
 * almost no positional change between them — [StrokeThinner] keeps the
 * point count roughly proportional to the *distance travelled*, not the
 * *time spent drawing*.
 *
 * Stateful and single-gesture-scoped: call [reset] when a new stroke
 * begins. Pure float math, no allocations on the hot path — safe to call
 * from a pointer-input loop on every touch-move event.
 */
class StrokeThinner(
    private val minDistancePx: Float = DEFAULT_MIN_DISTANCE_PX
) {
    private var lastAccepted: StrokePoint? = null

    fun reset() {
        lastAccepted = null
    }

    /**
     * Returns true if [candidate] is far enough from the last accepted
     * point to be kept (and records it as the new last-accepted point).
     * The very first call after [reset] always accepts, seeding the
     * comparison baseline.
     */
    fun shouldAccept(candidate: StrokePoint): Boolean {
        val last = lastAccepted
        if (last == null) {
            lastAccepted = candidate
            return true
        }
        val dx = candidate.x - last.x
        val dy = candidate.y - last.y
        val distanceSq = dx * dx + dy * dy
        val thresholdSq = minDistancePx * minDistancePx
        if (distanceSq >= thresholdSq) {
            lastAccepted = candidate
            return true
        }
        return false
    }

    companion object {
        /** Minimum screen-pixel distance between two kept points. */
        const val DEFAULT_MIN_DISTANCE_PX = 4f
    }
}
