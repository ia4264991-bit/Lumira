/**
 * Cross-cutting backend foundations: health reporting, error responses,
 * standard exception hierarchy, and base entity definitions.
 *
 * <p>Contains:
 * <ul>
 *   <li>{@code common.error}: API error envelope, ErrorCode enum, LumiraException hierarchy, and GlobalExceptionHandler.</li>
 *   <li>{@code common.domain}: BaseEntity mapped superclass and ArtifactOwner polymorphic ownership embeddable (AD-057).</li>
 * </ul>
 */
package com.lumira.backend.common;
