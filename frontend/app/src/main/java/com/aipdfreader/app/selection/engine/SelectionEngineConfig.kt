package com.aipdfreader.app.selection.engine

/**
 * Tunable parameters for [SelectionEngine].
 *
 * Deliberately a plain, non-Hilt-managed data class rather than an
 * injected dependency: Hilt's `@Inject constructor` requires every
 * parameter to be resolvable through the dependency graph, and a bare
 * primitive like [minOverlapRatio] has no natural Hilt binding without
 * adding a `@Provides` method — unwarranted machinery for a single tunable
 * float. Instead, [SelectionEngine.resolve] takes a [SelectionEngineConfig]
 * as a plain method parameter with a sensible default, so ordinary Kotlin
 * default-argument semantics apply and callers can still override it
 * per-call without any DI wiring.
 */
data class SelectionEngineConfig(
    /**
     * Minimum fraction (`[0, 1]`) of a block's own area that the lasso must
     * cover for that block to be included in the selection. Per the
     * approved design (§6), requiring full containment doesn't match how
     * people actually draw a lasso — they circle around the text they
     * mean, rarely tracing a shape that perfectly encloses every glyph — so
     * a majority-overlap rule is used instead of exact containment.
     */
    val minOverlapRatio: Float = DEFAULT_MIN_OVERLAP_RATIO
) {
    companion object {
        const val DEFAULT_MIN_OVERLAP_RATIO = 0.35f
    }
}
