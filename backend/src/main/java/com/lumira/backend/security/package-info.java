/**
 * Foundational security scaffolding and caller identity boundary.
 *
 * <p>Provides:
 * <ul>
 *   <li>{@link com.lumira.backend.security.AuthenticatedUser}: Representation of authenticated user principal (UUID userId).</li>
 *   <li>{@link com.lumira.backend.security.SecurityContext}: ThreadLocal holder for request-scoped user context.</li>
 *   <li>{@link com.lumira.backend.security.CurrentUser}: Controller parameter annotation for injecting the authenticated user or userId.</li>
 *   <li>{@link com.lumira.backend.security.RequireAuth}: Enforces authentication at the controller/method level.</li>
 *   <li>{@link com.lumira.backend.security.TokenResolver}: Abstraction for resolving tokens, enabling B1 identity implementation without boundary changes.</li>
 * </ul>
 */
package com.lumira.backend.security;
