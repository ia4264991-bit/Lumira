/**
 * Resource and ResourceVersion — LPTS Chapter 4.
 *
 * <p>Owns upload lifecycle, versioning, and (later) the
 * {@code ResourceProcessor} pattern for positional/viewer extraction —
 * making a resource viewable and selectable.
 *
 * <p>Does NOT include chunking, embeddings, or vector indexing for AI
 * retrieval — that is the future Processing Pipeline module's concern, even
 * though both eventually consume the same uploaded file. See Chapter 4 §1
 * and §5 for the full rationale behind this split.
 */
package com.lumira.backend.resource;
