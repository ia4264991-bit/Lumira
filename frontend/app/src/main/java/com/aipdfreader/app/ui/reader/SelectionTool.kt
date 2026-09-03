package com.aipdfreader.app.ui.reader

/**
 * Which input method the reader is currently using to select content on a
 * page.
 *
 * Deliberately a flat enum rather than a sealed hierarchy with per-tool
 * payloads: every current and anticipated tool (see the approved Selection
 * Engine design, §11 — OCR regions, images, handwriting) is a *mode*, not a
 * different kind of object flowing through the UI. Call sites use
 * exhaustive `when` blocks over this enum, so adding a new constant here is
 * a compile-time-enforced checklist of every place that needs to react to
 * it — no shared "mode flag" logic needs to be redesigned to add one.
 *
 * - [TEXT]: the existing long-press `SelectionContainer` text panel
 *   (unchanged, preserved as-is).
 * - [LASSO]: freeform drawing, captured and processed by the Selection
 *   Engine (`com.aipdfreader.app.selection.*`).
 *
 * Future candidates (not implemented yet): an OCR/region-select tool for
 * scanned pages, an image-selection tool, a handwriting-capture tool — each
 * would be a new enum constant plus new `when` branches, not a rewrite of
 * this type or of [ReaderUiState].
 */
enum class SelectionTool {
    TEXT,
    LASSO
}
