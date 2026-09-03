# selection.textlayout — Milestone M1.1 complete

Implemented (Milestone M1.1):
- `PositionedTextExtractor` — extracts embedded PDF text with normalized,
  rotation-corrected bounding boxes (PDFBox-based; implements `PageContentProvider`).
- `SpatialGridIndex` — uniform-grid broad-phase spatial index over a page's `ContentBlock`s.
- `PageContentLayout` — bundles a page's extracted blocks with its spatial index.
- `PageContentProvider` — the interface `PositionedTextExtractor` and
  `TextLayoutCache` both implement; the extension point for future OCR/
  handwriting content sources.
- `TextLayoutCache` — LRU-caching decorator over `PositionedTextExtractor`.

Explicitly NOT implemented yet (per M1.1 scope, at the time this package
was built):
- Polygon–text intersection (implemented in M1.2, `selection.engine`).
- Selection generation / selected-text output (implemented in M1.2).
- OCR or any non-embedded-text content source.

As of M1.3, `TextLayoutCache` IS wired into `ReaderViewModel` — a lasso
stroke resolves through this package's output automatically. See
`docs/engineering/M1.3_Selection_Integration.md`.
