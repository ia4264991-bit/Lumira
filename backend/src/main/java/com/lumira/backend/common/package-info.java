/**
 * Truly cross-cutting concerns only: health/status reporting, base
 * exception types, the global exception handler, and shared response
 * envelopes used by more than one module.
 *
 * <p>Does NOT hold business logic, module-specific utilities, or generic
 * "helper" classes that really belong to a single module. If a class is
 * only ever used by one module, it belongs in that module's own package,
 * not here — this package is deliberately kept small to avoid becoming a
 * "God package" as the codebase grows.
 */
package com.lumira.backend.common;
