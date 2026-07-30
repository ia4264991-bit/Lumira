/**
 * The {@code StorageService} abstraction and, in Vertical Slice 1, its
 * local-filesystem implementation.
 *
 * <p>Every other module depends only on the {@code StorageService}
 * interface, never on a concrete implementation — so a future S3 / R2 /
 * Azure Blob / GCS implementation can replace local disk storage without
 * changing the resource module (or any other caller) at all.
 */
package com.lumira.backend.storage;
