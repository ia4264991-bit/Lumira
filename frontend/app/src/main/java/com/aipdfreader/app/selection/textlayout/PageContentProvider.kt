package com.aipdfreader.app.selection.textlayout

/**
 * A source of page content for the Selection Engine.
 *
 * Per the approved design (§4.4/§11), this is the abstraction that lets
 * future content sources — OCR for scanned pages, handwritten-note capture,
 * other document types — plug in without any change to whatever eventually
 * consumes a [PageContentLayout] (the future polygon–text intersection
 * engine). [PositionedTextExtractor] is the first and, as of this
 * milestone, only implementation.
 *
 * Deliberately identical in shape to the extractor's own suspend function
 * so a future composite implementation (e.g. "try embedded text first,
 * fall back to OCR if the page has none") can simply delegate to whichever
 * [PageContentProvider] is appropriate, without a different interface to
 * bridge.
 *
 * Pure Kotlin interface — no Android, Compose, or PDFBox dependency.
 */
interface PageContentProvider {
    suspend fun getContentLayout(pdfId: Long, pageIndex: Int): PageContentLayout
}
