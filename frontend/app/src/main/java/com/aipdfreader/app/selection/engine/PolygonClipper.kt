package com.aipdfreader.app.selection.engine

import com.aipdfreader.app.selection.model.NormalizedPoint
import com.aipdfreader.app.selection.model.polygonArea
import javax.inject.Inject

/**
 * Computes the exact overlapping region between an arbitrary polygon and a
 * convex polygon, via Sutherland–Hodgman clipping.
 *
 * **Why the block's quad is the clip polygon, not the lasso.**
 * Sutherland–Hodgman only produces a correct result when the *clip* polygon
 * is convex; the *subject* polygon may be arbitrary — including concave or
 * self-intersecting, which a freehand lasso often is. A text block's
 * [com.aipdfreader.app.selection.model.TextContentBlock.quad] is always
 * exactly four corners of a (possibly rotated) rectangle, hence always
 * convex — so [clip] is always called with the block's quad as
 * [convexClip] and the lasso polygon as [subject], never the other way
 * around. This is the one correctness-critical detail in the narrow phase;
 * getting the two arguments backwards would silently produce wrong results
 * for any non-convex lasso shape.
 *
 * **Winding independence.** [convexClip] may be supplied in either winding
 * order — this function detects it from the clip polygon's own signed area
 * ([polygonArea]) and adapts its inside/outside test accordingly, so
 * callers never need to normalize vertex order themselves.
 *
 * Pure Kotlin, no Android/Compose/PDFBox dependency.
 */
class PolygonClipper @Inject constructor() {

    /**
     * Returns the polygon representing the overlap between [subject] (any
     * simple polygon) and [convexClip] (must be convex), or an empty list
     * if they don't overlap.
     */
    fun clip(subject: List<NormalizedPoint>, convexClip: List<NormalizedPoint>): List<NormalizedPoint> {
        if (subject.size < 3 || convexClip.size < 3) return emptyList()

        val insideSign = if (convexClip.polygonArea() >= 0f) 1f else -1f

        var output: List<NormalizedPoint> = subject
        for (i in convexClip.indices) {
            if (output.size < 3) return emptyList()
            val edgeStart = convexClip[i]
            val edgeEnd = convexClip[(i + 1) % convexClip.size]
            output = clipAgainstEdge(output, edgeStart, edgeEnd, insideSign)
        }

        return if (output.size >= 3) output else emptyList()
    }

    /** One Sutherland–Hodgman clip step: cuts [polygon] down to the inside half-plane of one clip edge. */
    private fun clipAgainstEdge(
        polygon: List<NormalizedPoint>,
        edgeStart: NormalizedPoint,
        edgeEnd: NormalizedPoint,
        insideSign: Float
    ): List<NormalizedPoint> {
        // Output size is bounded by input size + 1 in the overwhelming
        // common case (a convex clip edge adds at most one new vertex per
        // pass); presizing avoids the default ArrayList's growth-doubling
        // step for what is otherwise the hottest allocation in the narrow
        // phase (this runs once per clip edge, i.e. 4x per candidate block).
        val result = ArrayList<NormalizedPoint>(polygon.size + 1)

        for (i in polygon.indices) {
            val current = polygon[i]
            val previous = polygon[(i - 1 + polygon.size) % polygon.size]

            val currentInside = isInside(current, edgeStart, edgeEnd, insideSign)
            val previousInside = isInside(previous, edgeStart, edgeEnd, insideSign)

            if (currentInside) {
                if (!previousInside) {
                    result += edgeIntersection(previous, current, edgeStart, edgeEnd)
                }
                result += current
            } else if (previousInside) {
                result += edgeIntersection(previous, current, edgeStart, edgeEnd)
            }
        }

        return result
    }

    private fun isInside(
        point: NormalizedPoint,
        edgeStart: NormalizedPoint,
        edgeEnd: NormalizedPoint,
        insideSign: Float
    ): Boolean = crossProduct(edgeStart, edgeEnd, point) * insideSign >= 0f

    /** Cross product of (edgeEnd - edgeStart) and (point - edgeStart); sign gives the side of the line. */
    private fun crossProduct(edgeStart: NormalizedPoint, edgeEnd: NormalizedPoint, point: NormalizedPoint): Float =
        (edgeEnd.u - edgeStart.u) * (point.v - edgeStart.v) - (edgeEnd.v - edgeStart.v) * (point.u - edgeStart.u)

    /** Intersection of segment (p1,p2) with the infinite line through (edgeStart,edgeEnd), via line-equation determinants. */
    private fun edgeIntersection(
        p1: NormalizedPoint,
        p2: NormalizedPoint,
        edgeStart: NormalizedPoint,
        edgeEnd: NormalizedPoint
    ): NormalizedPoint {
        val a1 = p2.v - p1.v
        val b1 = p1.u - p2.u
        val c1 = a1 * p1.u + b1 * p1.v

        val a2 = edgeEnd.v - edgeStart.v
        val b2 = edgeStart.u - edgeEnd.u
        val c2 = a2 * edgeStart.u + b2 * edgeStart.v

        val determinant = a1 * b2 - a2 * b1
        if (determinant == 0f) {
            // Parallel (or coincident) lines. Genuinely shouldn't occur given
            // the inside/outside check that gates every call to this
            // function, but falling back to the known-good endpoint is
            // strictly safer than dividing by zero.
            return p2
        }

        val x = (b2 * c1 - b1 * c2) / determinant
        val y = (a1 * c2 - a2 * c1) / determinant
        return NormalizedPoint(x, y)
    }
}
