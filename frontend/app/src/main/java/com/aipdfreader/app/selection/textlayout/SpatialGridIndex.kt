package com.aipdfreader.app.selection.textlayout

import com.aipdfreader.app.selection.model.ContentBlock
import com.aipdfreader.app.selection.model.NormalizedRect
import com.aipdfreader.app.selection.model.boundingBox
import kotlin.math.floor

/**
 * Uniform-grid spatial index over a page's [ContentBlock]s, built once per
 * page and reused for every query against it.
 *
 * This is **broad-phase only** — per the approved Selection Engine design
 * (§6), it answers "which blocks are anywhere near this region" cheaply by
 * grid-bucket lookup, turning a full-page scan into a lookup over just the
 * cells a query region touches. It does **not** perform polygon
 * intersection, overlap-ratio computation, or any per-block geometric test
 * beyond axis-aligned bounding boxes — that narrow-phase work is explicitly
 * out of scope until a later milestone (the polygon–text intersection
 * engine), which will consume [candidatesNear]'s output as its *input*
 * rather than duplicating this lookup.
 *
 * Immutable and stateless once built — safe to share and query from
 * multiple coroutines without synchronization.
 */
class SpatialGridIndex private constructor(
    private val columns: Int,
    private val rows: Int,
    private val buckets: Map<Long, List<ContentBlock>>
) {

    /**
     * Returns every block whose bounding box overlaps any grid cell touched
     * by [regionBounds], deduplicated. Callers performing real intersection
     * (a later milestone) should treat this as their candidate set, not as
     * a final answer — some returned blocks may only overlap the region's
     * bounding box, not the region's true shape.
     */
    fun candidatesNear(regionBounds: NormalizedRect): List<ContentBlock> {
        if (buckets.isEmpty()) return emptyList()

        val minCol = columnFor(regionBounds.left)
        val maxCol = columnFor(regionBounds.right)
        val minRow = rowFor(regionBounds.top)
        val maxRow = rowFor(regionBounds.bottom)

        if (minCol > maxCol || minRow > maxRow) return emptyList()

        // Fast path: a query touching exactly one cell — the common case
        // for a modestly sized lasso against a page-spanning 20×20 grid —
        // needs no Set-based dedup at all. `build()` adds a given block to
        // a specific cell's bucket at most once (each (col,row) pair is
        // visited exactly once per block), so a single bucket is already
        // duplicate-free; returning it directly avoids allocating a
        // LinkedHashSet plus copying into a new List for every lookup.
        if (minCol == maxCol && minRow == maxRow) {
            return buckets[cellKey(minCol, minRow)] ?: emptyList()
        }

        val seen = LinkedHashSet<ContentBlock>()
        for (col in minCol..maxCol) {
            for (row in minRow..maxRow) {
                buckets[cellKey(col, row)]?.let { seen.addAll(it) }
            }
        }
        return seen.toList()
    }

    private fun columnFor(u: Float): Int =
        floor(u.coerceIn(0f, 1f) * columns).toInt().coerceIn(0, columns - 1)

    private fun rowFor(v: Float): Int =
        floor(v.coerceIn(0f, 1f) * rows).toInt().coerceIn(0, rows - 1)

    companion object {
        const val DEFAULT_GRID_SIZE = 20

        private fun cellKey(col: Int, row: Int): Long = col.toLong() shl 32 or row.toLong()

        /**
         * Builds an index over [blocks]. [columns]/[rows] default to a
         * 20×20 grid (per the approved design) — dense enough to give real
         * broad-phase savings on text-heavy pages, coarse enough that the
         * bucket map itself stays small and cheap to build per page.
         */
        fun build(
            blocks: List<ContentBlock>,
            columns: Int = DEFAULT_GRID_SIZE,
            rows: Int = DEFAULT_GRID_SIZE
        ): SpatialGridIndex {
            val safeColumns = columns.coerceAtLeast(1)
            val safeRows = rows.coerceAtLeast(1)
            val buckets = HashMap<Long, MutableList<ContentBlock>>()

            for (block in blocks) {
                val box = block.quad.boundingBox()
                val minCol = floor(box.left.coerceIn(0f, 1f) * safeColumns).toInt().coerceIn(0, safeColumns - 1)
                val maxCol = floor(box.right.coerceIn(0f, 1f) * safeColumns).toInt().coerceIn(0, safeColumns - 1)
                val minRow = floor(box.top.coerceIn(0f, 1f) * safeRows).toInt().coerceIn(0, safeRows - 1)
                val maxRow = floor(box.bottom.coerceIn(0f, 1f) * safeRows).toInt().coerceIn(0, safeRows - 1)

                for (col in minCol..maxCol) {
                    for (row in minRow..maxRow) {
                        buckets.getOrPut(cellKey(col, row)) { mutableListOf() }.add(block)
                    }
                }
            }

            return SpatialGridIndex(safeColumns, safeRows, buckets)
        }
    }
}
