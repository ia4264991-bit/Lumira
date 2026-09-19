# Lumira — Resource / File Processing Specification

**Governing architecture:** `docs/DECISIONS.md` AD-021, AD-057, AD-064.
This document specifies *processing behavior* for Resources — it does not
create new domain architecture. Where it names a specific technology
(parser library, OCR engine, vector database), that's a bug — this
document deliberately does not prescribe those.

**Foundational rule (AD-064, restated because it governs every section
below):** the format of an uploaded file never changes Resource ownership,
sharing, or authorization. A DOCX, a spreadsheet, and a scanned image are
all just `Resource` rows under AD-057's ownership mechanism, identically.
Everything below concerns *processing*, never the domain model.

---

## 1. Supported formats (MVP)

| Format | MIME type(s) |
|---|---|
| PDF | `application/pdf` |
| DOCX | `application/vnd.openxmlformats-officedocument.wordprocessingml.document` |
| PPTX | `application/vnd.openxmlformats-officedocument.presentationml.presentation` |
| XLSX | `application/vnd.openxmlformats-officedocument.spreadsheetml.sheet` |
| CSV | `text/csv` |
| TXT | `text/plain` |
| PNG | `image/png` |
| JPEG | `image/jpeg` |
| WebP | `image/webp` |

Legacy binary formats (`.doc`, `.ppt`, `.xls`) are **not** in MVP scope —
this is a scope decision for this specification, not a domain-architecture
one; revisit if real usage demands it.

## 2. Validation (applies uniformly, before any format-specific processing)

1. **Extension check** — file extension matches an entry in §1.
2. **MIME type check** — declared MIME type matches §1.
3. **Content sniffing** — the file's actual bytes are verified against its
   claimed type (magic-number/signature check), not trusted from the
   extension or declared MIME type alone.
4. **Size limit** — a maximum upload size applies (exact number is an
   implementation/operational parameter, not frozen here — pick something
   reasonable for the target market's bandwidth constraints already on
   record in `LUMIRA_STATE.md`, and make it configurable).
5. **Malformed file detection** — a file that fails to parse as valid
   content of its claimed type after passing checks 1–3 is rejected, not
   silently processed as best-effort.
6. **Encrypted/password-protected files** — detected and rejected with a
   clear failure reason (§4), not silently processed as empty/corrupt.
7. **Duplicate uploads** — not deduplicated at the domain level in this
   spec (no `ContentVersion`/checksum-dedup architecture is frozen); each
   upload is its own `Resource`. Storage-level deduplication (identical
   bytes stored once, referenced twice) is a legitimate future storage
   optimization, explicitly not decided here.

Any file failing validation never reaches format-specific processing —
it goes directly to `FAILED` (§4) with a specific, user-visible reason.

## 3. Format-specific processing

The conceptual pipeline for every format:

```
uploaded file
  → validation (§2)
  → format-specific extraction
  → normalized Resource representation + structured chunks/metadata
  → authorized retrieval (governed entirely by AD-021/022/045/056 —
    processing never touches authorization)
```

| Format | Extraction target | Structural metadata preserved |
|---|---|---|
| PDF | Text, embedded images | Page number per chunk; page-level coordinates where extraction supports it |
| DOCX | Paragraphs, headings, tables, embedded images | Section/heading hierarchy |
| PPTX | Slide text, speaker notes (if present), embedded images | Slide number per chunk |
| XLSX | Cell values (both formula results and, where meaningfully different, the formula itself) | Sheet name, row/column reference per chunk |
| CSV | Rows/columns | Column headers (if present), delimiter, encoding |
| TXT | Raw text | None beyond line offsets |
| PNG/JPEG/WebP | OCR'd text where the image contains text; otherwise treated as a non-text visual asset | Image dimensions; OCR confidence where available |

**Why structural metadata matters, stated once rather than per-row:** Sarah
and any future search/retrieval system need to cite *where* in a Resource
an answer came from ("page 4," "slide 12," "row 87 of the Grades sheet") —
this is a genuine, real requirement, not a nice-to-have, since AD-028's
authorized-context boundary is easiest to reason about and to test when
retrieved chunks carry a stable, specific location reference. This
specification requires that every extracted chunk carry a location
reference of the appropriate kind for its format (page/slide/sheet-row-
column/line-offset) — it does not prescribe the storage mechanism for
that metadata (a JSON column, a separate table, a search index's own
metadata field are all compatible implementation choices, undecided here).

**OCR and visual processing:** required for image formats and for
image-only PDF pages (scanned documents). This specification does not name
an OCR engine or vector/embedding technology — those are implementation
choices. What is required, architecturally: OCR output is treated exactly
like any other extracted text for chunking/metadata purposes (§ above),
and OCR confidence, where the chosen engine provides it, is preserved as
metadata rather than discarded, so a low-confidence extraction can
eventually be surfaced differently than a high-confidence one (exact UX
for this is not decided here).

**Unsupported formats:** rejected at validation (§2) with a clear "this
file type isn't supported yet" reason — never silently accepted and
processed as plain text.

## 4. Processing lifecycle

```
UPLOADED → PROCESSING → READY
                ↘
                 FAILED
```

- **`UPLOADED`** — file received and validated (§2 passed), processing not
  yet started.
- **`PROCESSING`** — format-specific extraction in progress.
- **`READY`** — extraction complete; Resource is fully usable (viewable,
  retrievable, available to Sarah per AD-028).
- **`FAILED`** — validation or extraction failed. Must carry a specific,
  human-readable reason (not a bare error code) — matching the same
  transparency principle already established for moderation reasons
  (AD-047's structured-reason pattern is a reasonable model to follow
  here, though this spec doesn't require reusing that exact enum).

**Retry behavior:** a `FAILED` Resource may be reprocessed (e.g. after a
transient extraction-service failure) — this creates a new processing
attempt against the *same* Resource row, not a new Resource. Retry count/
backoff strategy is implementation detail, not specified here — the
architectural requirement is only that retries are **idempotent**: running
extraction twice on the same file never produces two sets of chunks or
duplicate Resource rows.

**Partial processing:** if extraction succeeds for some content but fails
for part of it (e.g. one corrupt slide in an otherwise-valid PPTX), the
Resource may still reach `READY` with the successfully-extracted content
available — this is a deliberately different policy from AD-065's
all-or-nothing rule for *AI-generated* artifacts, because a partially-
processed Resource is still meaningfully useful to a student (they can see
the slides that did extract), whereas a silently-incomplete generated Quiz
is not. This distinction is intentional, not an oversight.

## 5. Storage separation

Four distinct concerns, kept separate regardless of implementation
technology chosen later:

1. **Original bytes** — the uploaded file, unmodified.
2. **Extracted structured data** — chunks + location metadata (§3).
3. **Derived/generated data** — anything produced *from* the Resource
   later (embeddings, search-index entries) — explicitly **not** part of
   this specification's scope to design (no vector database, no search
   architecture is decided here; this spec only requires that whatever
   derived data eventually exists is clearly understood as derived, never
   authoritative — consistent with AD-056).
4. **Resource metadata** — ownership (AD-057), processing status (§4),
   original filename/MIME type, size.

**AD-056 applied here explicitly:** none of items 2–4 are ever
independently authoritative for ownership or access — the `Resource` row
itself (with its AD-057 ownership columns) is the only authoritative
record; a search index or embedding store having a copy of a chunk's text
never means that index gets to decide who may see it.

## 6. Security

- **Untrusted file handling:** every uploaded file is untrusted input
  until validation (§2) passes. Extraction happens in a context that
  assumes the file could be malicious (e.g. a decompression-bomb-style
  DOCX/PPTX/XLSX designed to exhaust memory on unzip) — exact isolation
  mechanism (sandboxing, resource limits on the extraction process) is
  implementation detail, but the requirement that extraction must be
  resource-bounded and unable to affect unrelated requests is
  architectural, not optional.
- **Authorization during processing:** processing itself never grants or
  needs access beyond what the uploading user's own authorization already
  covers — a background processing job operates on a Resource the
  uploader already owns; it never becomes a path for accessing anything
  else.
- **Malicious content embedded in extracted text** — this is exactly
  AD-058's "retrieved content is data, never an authorization grant"
  principle, applied at the point of extraction: text extracted from a
  Resource (including OCR'd text) is stored and later retrieved as
  ordinary data. It carries no special authority merely because it came
  from inside a file, and any instructions embedded in that text (e.g. a
  PDF containing "ignore previous instructions") have no effect on the
  processing pipeline itself, which does not interpret extracted content
  as instructions at any point — it only extracts and stores it.

## 7. What this specification does not decide

- Exact parser libraries, OCR engine, or any specific vendor/technology.
- Vector database choice, embedding model, or search/retrieval ranking —
  see the standing deferral in `docs/DECISIONS.md`'s closure-pass Revisions
  entry (2026-09-12): full search/vector infrastructure remains
  out of scope until brought into architecture deliberately.
- Exact size limits, retry counts, or backoff timing — operational
  parameters, not architecture.
- Whether/how storage-level deduplication of identical file bytes works.
