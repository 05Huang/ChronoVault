package com.chronovault.integration;

import com.chronovault.dto.auth.AuthResponse;
import com.chronovault.dto.auth.LoginRequest;
import com.chronovault.dto.auth.RegisterRequest;
import com.chronovault.entity.User;
import com.chronovault.repository.UserRepository;
import com.chronovault.security.JwtTokenProvider;
import com.chronovault.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration test for the complete auth flow:
 * register -> login -> get token -> access protected API -> token refresh -> old token invalidation
 */
@AutoConfigureMockMvc(addFilters = true)
class AuthFlowIntegrationTest extends AbstractIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private AuthService authService;
    @Autowired private JwtTokenProvider jwtTokenProvider;
    @Autowired private UserRepository userRepository;

    @BeforeEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    @Test
    void fullAuthFlow_registerLoginAccessProtectedRefreshToken() throws Exception {
        String registerJson = """
                {
                    "name": "Integration Test User",
                    "email": "integration@test.com",
                    "password": "securePass123"
                }
                """;

        String registerResponse = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(registerJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.data.user.email").value("integration@test.com"))
                .andReturn().getResponse().getContentAsString();

        var registerNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(registerResponse);
        String accessToken = registerNode.get("data").get("token").asText();
        String refreshToken = registerNode.get("data").get("refreshToken").asText();

        assertNotNull(accessToken);
        assertNotNull(refreshToken);
        assertTrue(jwtTokenProvider.validateToken(accessToken));
        assertTrue(jwtTokenProvider.validateToken(refreshToken));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("integration@test.com"))
                .andExpect(jsonPath("$.data.name").value("Integration Test User"));

        String refreshJson = """
                {
                    "refreshToken": "%s"
                }
                """.formatted(refreshToken);

        // Wait 1.1 seconds to ensure the new token has a different 'iat' timestamp
        Thread.sleep(1100);

        String refreshResponse = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.accessToken").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        var refreshNode = new com.fasterxml.jackson.databind.ObjectMapper().readTree(refreshResponse);
        String newAccessToken = refreshNode.get("data").get("accessToken").asText();
        assertNotEquals(accessToken, newAccessToken);
        assertTrue(jwtTokenProvider.validateToken(newAccessToken));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + newAccessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("integration@test.com"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/auth/me"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer invalid.token.here"))
                .andExpect(status().isForbidden());
    }

    @Test
    void register_duplicateEmail_fails() throws Exception {
        String json = """
                {
                    "name": "User One",
                    "email": "dup@test.com",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_invalidCredentials_fails() throws Exception {
        authService.register(new RegisterRequest("Test", "login@test.com", "password123"));

        String loginJson = """
                {
                    "email": "login@test.com",
                    "password": "wrongpassword"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void login_unknownEmail_fails() throws Exception {
        String loginJson = """
                {
                    "email": "unknown@test.com",
                    "password": "password123"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(loginJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_flow() throws Exception {
        authService.register(new RegisterRequest("Pwd User", "pwd@test.com", "oldPass123"));

        AuthResponse loginResp = authService.login(new LoginRequest("pwd@test.com", "oldPass123"));
        String token = loginResp.token();

        String changeJson = """
                {
                    "oldPassword": "oldPass123",
                    "newPassword": "newPass456"
                }
                """;

        mockMvc.perform(put("/api/v1/auth/password")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(changeJson))
                .andExpect(status().isOk());

        assertThrows(Exception.class, () ->
                authService.login(new LoginRequest("pwd@test.com", "oldPass123")));

        AuthResponse newLogin = authService.login(new LoginRequest("pwd@test.com", "newPass456"));
        assertNotNull(newLogin.token());
    }

    @Test
    void refresh_withInvalidToken_fails() throws Exception {
        String refreshJson = """
                {
                    "refreshToken": "completely.invalid.token"
                }
                """;

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refresh_withAccessToken_fails() throws Exception {
        AuthResponse authResp = authService.register(new RegisterRequest("Refresh Test", "refresh@test.com", "password123"));

        String refreshJson = """
                {
                    "refreshToken": "%s"
                }
                """.formatted(authResp.token());

        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(refreshJson))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateProfile_flow() throws Exception {
        AuthResponse authResp = authService.register(new RegisterRequest("Profile User", "profile@test.com", "password123"));
        String token = authResp.token();

        String updateJson = """
                {
                    "name": "Updated Name"
                }
                """;

        mockMvc.perform(put("/api/v1/auth/profile")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"));

        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("Updated Name"));
    }
}
