package com.chronovault.integration;

import com.chronovault.dto.auth.LoginRequest;
import com.chronovault.dto.auth.RegisterRequest;
import com.chronovault.service.AuthService;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for auth flow.
 * Requires full Spring context with database and Redis.
 * Run with: mvn test -Dtest=AuthFlowIntegrationTest -Dspring.profiles.active=test
 * Or use Testcontainers for CI/CD.
 */
@SpringBootTest
@ActiveProfiles("test")
@Transactional
@Disabled("Requires running database and Redis — enable with Testcontainers or local infrastructure")
class AuthFlowIntegrationTest {

    @Autowired
    private AuthService authService;

    @Test
    void registerAndLogin_flow() {
        // Register a new user
        RegisterRequest registerReq = new RegisterRequest("Test User", "integration@test.com", "password123");
        var authResponse = authService.register(registerReq);

        assertNotNull(authResponse);
        assertNotNull(authResponse.token());
        assertEquals("integration@test.com", authResponse.user().email());

        // Login with the registered user
        LoginRequest loginReq = new LoginRequest("integration@test.com", "password123");
        var loginResponse = authService.login(loginReq);

        assertNotNull(loginResponse);
        assertNotNull(loginResponse.token());
        assertEquals("integration@test.com", loginResponse.user().email());
    }

    @Test
    void login_invalidCredentials_throws() {
        LoginRequest loginReq = new LoginRequest("nonexistent@test.com", "wrongpassword");

        assertThrows(Exception.class, () -> authService.login(loginReq));
    }
}