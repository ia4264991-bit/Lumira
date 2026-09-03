package com.aipdfreader.app.selection.textlayout

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * A small LRU cache of [PageContentLayout]s in front of
 * [PositionedTextExtractor], keyed by `(pdfId, pageIndex)`.
 *
 * Per the approved design (§8), page content is built lazily and cached
 * per-page rather than upfront for a whole document — this class is that
 * cache. It implements [PageContentProvider] itself (a transparent
 * decorator over the extractor), so callers depend on "a page content
 * provider" without needing to know caching is happening underneath —
 * matching how the extractor and any future OCR/handwriting provider would
 * all be used identically.
 *
 * [getContentLayout] holds [mutex] across the *entire* check-then-extract-
 * then-store sequence for a miss, not just the individual map read/write.
 * This is a deliberate correctness fix (Milestone M1.4): the reader
 * legitimately issues two concurrent requests for the same page — the
 * proactive per-page prefetch in `ReaderViewModel.loadPage` and an
 * on-demand lookup from a lasso resolving on that same page — and without
 * this, both could see a miss and independently run
 * [PositionedTextExtractor] (duplicate PDFBox parsing for the same page).
 * Holding the lock for the whole miss path serializes the *rare* case
 * (concurrent misses on the same, or different, pages) so the *common*
 * case (a warm cache hit) stays effectively lock-free in practice — hits
 * only briefly hold the mutex for a map lookup, exactly as before.
 */
@Singleton
class TextLayoutCache @Inject constructor(
    private val extractor: PositionedTextExtractor
) : PageContentProvider {

    private val mutex = Mutex()
    private val cache = object : LinkedHashMap<CacheKey, PageContentLayout>(
        MAX_ENTRIES, LOAD_FACTOR, ACCESS_ORDER
    ) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<CacheKey, PageContentLayout>): Boolean =
            size > MAX_ENTRIES
    }

    override suspend fun getContentLayout(pdfId: Long, pageIndex: Int): PageContentLayout {
        val key = CacheKey(pdfId, pageIndex)

        return mutex.withLock {
            cache[key]?.let { return@withLock it }
            val layout = extractor.getContentLayout(pdfId, pageIndex)
            cache[key] = layout
            layout
        }
    }

    /** Drops every cached layout for [pdfId] — e.g. if the underlying file changes. */
    suspend fun invalidateDocument(pdfId: Long) {
        mutex.withLock {
            cache.keys.filter { it.pdfId == pdfId }.forEach { cache.remove(it) }
        }
    }

    suspend fun clear() {
        mutex.withLock { cache.clear() }
    }

    private data class CacheKey(val pdfId: Long, val pageIndex: Int)

    companion object {
        /**
         * Current page plus a few neighbors in either direction (approved
         * design §4.4/§8 called for "current page ± a couple"; widened
         * slightly from 5 during the M1.4 cache-sizing review to better
         * tolerate a reader flipping across several pages and back before
         * this LRU would otherwise have evicted the earlier ones — still
         * small and bounded, not a behavior change to eviction policy
         * itself, which remains strict LRU).
         */
        private const val MAX_ENTRIES = 8
        private const val LOAD_FACTOR = 0.75f
        private const val ACCESS_ORDER = true
    }
}
