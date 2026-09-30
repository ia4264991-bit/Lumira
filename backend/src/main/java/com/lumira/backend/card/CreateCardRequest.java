package com.lumira.backend.card;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Request body for creating a Card.
 */
public record CreateCardRequest(
        @NotBlank(message = "name is required")
        @Size(max = 255, message = "name must be 255 characters or fewer")
        String name,

        @Size(max = 50, message = "color must be 50 characters or fewer")
        String color
) {
}
