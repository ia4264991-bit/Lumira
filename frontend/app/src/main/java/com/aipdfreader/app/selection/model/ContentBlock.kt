package com.aipdfreader.app.selection.model

/**
 * Base type for anything a page can contain, in normalized page-display
 * space. This is the extension point the approved Selection Engine design
 * (§11) relies on for future content types: adding OCR'd text, images, or
 * handwritten strokes later means adding a new `ContentBlock` subtype and
 * new `when` branches wherever one is needed — never redesigning
 * [com.aipdfreader.app.selection.textlayout.PageContentLayout],
 * `SpatialGridIndex`, or (in a later milestone) the intersection engine,
 * all of which only ever depend on this common base type.
 *
 * Every block carries a [quad]: its bounding shape as four corner points in
 * normalized space, rather than a plain axis-aligned rectangle, so rotated
 * text (or, later, rotated images/strokes) can still be represented
 * precisely. [NormalizedPoint.boundingBox] (via `quad.boundingBox()`) gives
 * the cheap axis-aligned approximation used for spatial indexing.
 *
 * Pure data — no Android, Compose, or PDFBox dependency, matching the rest
 * of `selection.model`.
 */
sealed class ContentBlock {
    abstract val id: String
    abstract val quad: List<NormalizedPoint>
}

/**
 * A single extracted word (or short run of text) from a PDF page's
 * embedded text layer.
 *
 * @param text the extracted text content, exactly as it appears (no
 *   trimming/casing applied beyond what extraction already does).
 * @param lineIndex which visual line this block belongs to, in document
 *   order — used for line-aware reading-order assembly in a later
 *   milestone; not used for anything in M1.1 beyond being recorded.
 * @param readingOrderIndex this block's position in the page's overall
 *   reading order, as produced by the extractor.
 */
data class TextContentBlock(
    override val id: String,
    val text: String,
    override val quad: List<NormalizedPoint>,
    val lineIndex: Int,
    val readingOrderIndex: Int
) : ContentBlock()

/**
 * Reserved for a future milestone: a selectable image region on a page.
 * Declared now (per the approved design's extensibility goal) so the
 * sealed hierarchy — and everything written against it — never needs to
 * change shape when image selection is implemented; nothing constructs
 * this type yet.
 */
data class ImageContentBlock(
    override val id: String,
    override val quad: List<NormalizedPoint>
) : ContentBlock()

/**
 * Reserved for a future milestone: a captured handwritten annotation.
 * [recognizedText] is null until handwriting recognition exists; declared
 * now for the same reason as [ImageContentBlock] — nothing constructs this
 * type yet.
 */
data class HandwritingStrokeBlock(
    override val id: String,
    override val quad: List<NormalizedPoint>,
    val recognizedText: String? = null
) : ContentBlock()
