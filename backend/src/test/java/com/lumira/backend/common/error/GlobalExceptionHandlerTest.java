package com.lumira.backend.common.error;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class GlobalExceptionHandlerTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new TestController())
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    @DisplayName("ResourceNotFoundException produces 404 with standard error code NOT_FOUND")
    void handleResourceNotFoundException() throws Exception {
        mockMvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("Item not found"));
    }

    @Test
    @DisplayName("UnauthorizedException produces 401 with standard error code UNAUTHORIZED")
    void handleUnauthorizedException() throws Exception {
        mockMvc.perform(get("/test/unauthorized"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("Missing auth"));
    }

    @Test
    @DisplayName("ForbiddenException produces 403 with standard error code FORBIDDEN")
    void handleForbiddenException() throws Exception {
        mockMvc.perform(get("/test/forbidden"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.message").value("Access denied"));
    }

    @Test
    @DisplayName("ConflictException produces 409 with standard error code CONFLICT")
    void handleConflictException() throws Exception {
        mockMvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"))
                .andExpect(jsonPath("$.error.message").value("State conflict"));
    }

    @Test
    @DisplayName("MethodArgumentNotValidException produces 400 with VALIDATION_FAILED and field details")
    void handleValidationFailure() throws Exception {
        mockMvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.details[0].field").value("name"));
    }

    @Test
    @DisplayName("Uncaught Exception produces 500 with INTERNAL_SERVER_ERROR and safe generic message")
    void handleGenericException() throws Exception {
        mockMvc.perform(get("/test/unhandled"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.error.message").value("An unexpected internal error occurred"));
    }

    @RestController
    static class TestController {

        @GetMapping("/test/not-found")
        public void notFound() {
            throw new ResourceNotFoundException("Item not found");
        }

        @GetMapping("/test/unauthorized")
        public void unauthorized() {
            throw new UnauthorizedException("Missing auth");
        }

        @GetMapping("/test/forbidden")
        public void forbidden() {
            throw new ForbiddenException("Access denied");
        }

        @GetMapping("/test/conflict")
        public void conflict() {
            throw new ConflictException("State conflict");
        }

        @PostMapping("/test/validate")
        public void validate(@Valid @RequestBody TestRequest request) {
        }

        @GetMapping("/test/unhandled")
        public void unhandled() {
            throw new RuntimeException("Sensitive database stacktrace");
        }
    }

    record TestRequest(@NotBlank(message = "Name must not be blank") String name) {}
}
