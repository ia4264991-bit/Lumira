package com.aipdfreader.app.ui.reader

/** Geometry for one PDF page currently intersecting the reader viewport. */
internal data class PdfViewportPage(val index: Int, val offset: Int, val size: Int)

/** Pick the page occupying the largest visible vertical area. */
internal fun mostVisiblePdfPageIndex(
    pages: List<PdfViewportPage>,
    viewportStart: Int,
    viewportEnd: Int
): Int? = pages.maxByOrNull { page ->
    (minOf(page.offset + page.size, viewportEnd) - maxOf(page.offset, viewportStart)).coerceAtLeast(0)
}?.takeIf { page ->
    (minOf(page.offset + page.size, viewportEnd) - maxOf(page.offset, viewportStart)).coerceAtLeast(0) > 0
}?.index
