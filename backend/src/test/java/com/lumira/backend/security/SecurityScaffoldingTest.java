package com.lumira.backend.security;

import com.lumira.backend.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class SecurityScaffoldingTest {

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        TokenResolver tokenResolver = new FoundationTokenResolver();
        SecurityInterceptor interceptor = new SecurityInterceptor(tokenResolver);
        CurrentUserArgumentResolver resolver = new CurrentUserArgumentResolver();

        mockMvc = MockMvcBuilders
                .standaloneSetup(new SecuredTestController())
                .addInterceptors(interceptor)
                .setCustomArgumentResolvers(resolver)
                .setControllerAdvice(new GlobalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter())
                .build();
    }

    @Test
    @DisplayName("Protected endpoint with valid Bearer token resolves CurrentUser successfully")
    void validBearerTokenResolvesUser() throws Exception {
        UUID userId = UUID.randomUUID();

        mockMvc.perform(get("/v1/test/protected")
                        .header("Authorization", "Bearer " + userId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value(userId.toString()));
    }

    @Test
    @DisplayName("Protected endpoint with @RequireAuth without token returns 401 UNAUTHORIZED")
    void missingTokenOnProtectedEndpointReturns401() throws Exception {
        mockMvc.perform(get("/v1/test/protected"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.error.message").value("Authentication required to access this endpoint"));
    }

    @Test
    @DisplayName("Protected endpoint with malformed token returns 401 UNAUTHORIZED")
    void malformedTokenReturns401() throws Exception {
        mockMvc.perform(get("/v1/test/protected")
                        .header("Authorization", "Bearer invalid-uuid-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("Public endpoint without token passes through successfully")
    void publicEndpointPassesThrough() throws Exception {
        mockMvc.perform(get("/v1/test/public"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("public-ok"));
    }

    @RestController
    @RequestMapping("/v1/test")
    static class SecuredTestController {

        @GetMapping("/protected")
        @RequireAuth
        public Map<String, Object> protectedEndpoint(@CurrentUser UUID userId) {
            return Map.of("userId", userId.toString());
        }

        @GetMapping("/public")
        public Map<String, Object> publicEndpoint() {
            return Map.of("status", "public-ok");
        }
    }
}
