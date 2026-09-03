# selection.engine — Milestone M1.2 complete

Implemented (Milestone M1.2):
- `SelectionEngine` — resolves a finalized `SelectionPolygon` (M1.0) against
  a `PageContentLayout` (M1.1) into a `SelectionResult`: broad-phase lookup
  via the existing `SpatialGridIndex`, narrow-phase exact overlap via
  `PolygonClipper`, ordered via `ReadingOrderComparator`.
- `PolygonClipper` — Sutherland–Hodgman polygon clipping (winding-robust),
  used with the block's quad as the convex clip polygon and the lasso as
  the (possibly concave) subject.
- `ReadingOrderComparator` — orders matched blocks in document reading
  order, falling back to position-based ordering for any future non-text
  block type.
- `SelectionEngineConfig` — configurable overlap-ratio threshold.

Explicitly NOT implemented yet (per M1.2 scope, at the time this package
was built):
- OCR or any non-embedded-text content source.
- Backend communication of any selected text.

As of M1.3, `SelectionEngine` IS wired into `ReaderViewModel` — a completed
lasso stroke automatically resolves through this package and routes its
selected text into the existing highlight/Ask AI/chat workflow. See
`docs/engineering/M1.3_Selection_Integration.md`.
