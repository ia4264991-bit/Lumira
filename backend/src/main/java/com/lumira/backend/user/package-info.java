/**
 * The {@code User} entity and its persistence.
 *
 * <p>Deliberately minimal in Vertical Slice 1: full authentication, roles,
 * and permissions are LPTS Chapter 15's responsibility and are explicitly
 * out of scope here. This module exists in Vertical Slice 1 only far enough
 * to give other modules (CourseOfferingMembership, Resource) a real foreign
 * key target for "who owns/uploaded this" — see the Vertical Slice 1
 * implementation notes for why a minimal User was needed before Chapter 15
 * exists, and what is explicitly deferred.
 */
package com.lumira.backend.user;
