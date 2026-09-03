package com.aipdfreader.app.selection.geometry

import com.aipdfreader.app.selection.model.NormalizedPoint
import com.aipdfreader.app.selection.model.SelectionPolygon
import com.aipdfreader.app.selection.model.polygonArea
import kotlin.math.abs
import javax.inject.Inject

/**
 * Closes a simplified, mapped stroke into a validated [SelectionPolygon],
 * rejecting shapes that don't represent a meaningful selection (approved
 * design, §9: "Tiny/accidental lasso").
 *
 * This is the last stage of the geometry pipeline (simplify → map → build),
 * and the only place a [SelectionPolygon] is legally constructed.
 */
class PolygonBuilder @Inject constructor() {

    /**
     * @return a validated [SelectionPolygon], or `null` if the input was
     *   degenerate (too few distinct vertices, or near-zero enclosed area —
     *   e.g. an accidental tap-drag rather than an intentional lasso).
     */
    fun build(pageIndex: Int, mappedPoints: List<NormalizedPoint>): SelectionPolygon? {
        val deduped = dedupeConsecutive(mappedPoints)
        if (deduped.size < SelectionPolygon.MIN_VERTICES) return null

        val area = deduped.polygonArea()
        if (abs(area) < MIN_NORMALIZED_AREA) return null

        return SelectionPolygon(pageIndex = pageIndex, points = deduped)
    }

    /** Drops consecutive points that are effectively the same location. */
    private fun dedupeConsecutive(points: List<NormalizedPoint>): List<NormalizedPoint> {
        if (points.isEmpty()) return points
        // Presized to the input size: dedup only ever removes points, never
        // adds, so this is always enough capacity — avoids the default
        // ArrayList growth-doubling steps that would otherwise happen while
        // this fills up.
        val result = ArrayList<NormalizedPoint>(points.size)
        result.add(points.first())
        for (i in 1 until points.size) {
            val previous = result.last()
            val candidate = points[i]
            val du = candidate.u - previous.u
            val dv = candidate.v - previous.v
            if (du * du + dv * dv >= MIN_DISTANCE_SQ) {
                result += candidate
            }
        }
        // If the closing edge collapses the first/last points together, drop the duplicate.
        if (result.size > SelectionPolygon.MIN_VERTICES) {
            val first = result.first()
            val last = result.last()
            val du = last.u - first.u
            val dv = last.v - first.v
            if (du * du + dv * dv < MIN_DISTANCE_SQ) {
                result.removeAt(result.size - 1)
            }
        }
        return result
    }

    companion object {
        /** Minimum squared distance (in normalized units) between distinct vertices. */
        private const val MIN_DISTANCE_SQ = 1e-6f

        /** Minimum enclosed area, as a fraction of the full page (1.0 = whole page). */
        private const val MIN_NORMALIZED_AREA = 0.0005f
    }
}
