package com.aipdfreader.app.pdf

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Thin, coroutine-safe wrapper around [PdfRenderer] used to rasterize pages
 * to bitmaps for display. Zooming is handled by the caller (via a target
 * width/scale passed into [renderPage]) plus Compose's own pinch-to-zoom
 * transform on top of the returned bitmap.
 *
 * One instance should be scoped to a single open document (e.g. per
 * ReaderViewModel) and [close]d when the reader screen is destroyed, since
 * [PdfRenderer] holds a file descriptor open.
 */
class PdfPageRenderer(filePath: String) : AutoCloseable {

    private val fileDescriptor = ParcelFileDescriptor.open(
        File(filePath), ParcelFileDescriptor.MODE_READ_ONLY
    )
    private val renderer = PdfRenderer(fileDescriptor)
    private val mutex = Mutex() // PdfRenderer is not thread-safe; serialize access.

    val pageCount: Int get() = renderer.pageCount

    /**
     * Renders [pageIndex] (0-based) to a [Bitmap] scaled so its width matches
     * [targetWidthPx], preserving aspect ratio. Higher [targetWidthPx] gives a
     * sharper render — callers increase this as the user zooms in.
     */
    suspend fun renderPage(pageIndex: Int, targetWidthPx: Int): Bitmap = withContext(Dispatchers.IO) {
        mutex.withLock {
            renderer.openPage(pageIndex).use { page ->
                val scale = targetWidthPx.toFloat() / page.width
                val width = targetWidthPx
                val height = (page.height * scale).toInt().coerceAtLeast(1)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                bitmap.eraseColor(android.graphics.Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }
    }

    suspend fun getPageAspectRatio(pageIndex: Int): Float = withContext(Dispatchers.IO) {
        mutex.withLock {
            renderer.openPage(pageIndex).use { page ->
                page.width.toFloat() / page.height.toFloat()
            }
        }
    }

    override fun close() {
        renderer.close()
        fileDescriptor.close()
    }
}
