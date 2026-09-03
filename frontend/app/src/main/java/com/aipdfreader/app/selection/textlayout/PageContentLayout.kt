package com.aipdfreader.app.selection.textlayout

import com.aipdfreader.app.selection.model.ContentBlock

/**
 * Everything the Selection Engine knows about one page's content: its
 * extracted [blocks] and a [spatialIndex] built over them for fast lookup.
 *
 * Deliberately placed in `selection.textlayout` rather than
 * `selection.model`: it bundles a [SpatialGridIndex] — a textlayout-layer
 * concept — so keeping it here preserves `selection.model`'s stricter
 * property of having zero dependencies on any other Selection Engine layer
 * (verified during Milestone M1.0 and re-verified this milestone). This is
 * a small, deliberate refinement of the original design sketch (which
 * informally listed it alongside `ContentBlock`); no other component's
 * shape or responsibility changes as a result.
 *
 * Pure Kotlin — no Android, Compose, or PDFBox dependency.
 */
data class PageContentLayout(
    val pageIndex: Int,
    val blocks: List<ContentBlock>,
    val spatialIndex: SpatialGridIndex
) {
    companion object {
        /** An empty layout for a page that couldn't be read or has no content yet. */
        fun empty(pageIndex: Int): PageContentLayout =
            PageContentLayout(
                pageIndex = pageIndex,
                blocks = emptyList(),
                spatialIndex = SpatialGridIndex.build(emptyList())
            )
    }
}
