package com.lumira.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Lumira platform backend entry point.
 *
 * <p>Implements Vertical Slice 1 of the Lumira Platform Technical
 * Specification (LPTS Chapters 3 &amp; 4: University Hierarchy and
 * Resources). See {@code docs/specification} in the platform repository for
 * the authoritative specification this backend implements.
 *
 * <p>This is Step 1 of 8 in the incremental build-out of this slice: a bare
 * skeleton with no database, no entities, and no business logic yet.
 */
@SpringBootApplication
public class LumiraBackendApplication {

    public static void main(String[] args) {
        SpringApplication.run(LumiraBackendApplication.class, args);
    }
}
