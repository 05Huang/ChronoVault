package com.chronovault.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for JwtTokenProvider.
 * Validates token generation, parsing, expiration, and refresh flows.
 */
class JwtTokenProviderTest {

    private JwtTokenProvider jwtTokenProvider;

    private static final String TEST_SECRET = "test-jwt-secret-key-that-is-at-least-32-chars-long-for-hs256-algorithm";
    private static final String SHORT_SECRET = "too-short";

    @BeforeEach
    void setUp() {
        jwtTokenProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(jwtTokenProvider, "jwtExpiration", 3600000L); // 1 hour
        jwtTokenProvider.validateConfig();
    }

    @Test
    void generateToken_validEmail_returnsToken() {
        String token = jwtTokenProvider.generateToken("user@test.com");
        assertNotNull(token);
        assertFalse(token.isBlank());
    }

    @Test
    void getEmailFromToken_validToken_returnsEmail() {
        String token = jwtTokenProvider.generateToken("user@test.com");
        String email = jwtTokenProvider.getEmailFromToken(token);
        assertEquals("user@test.com", email);
    }

    @Test
    void validateToken_validToken_returnsTrue() {
        String token = jwtTokenProvider.generateToken("user@test.com");
        assertTrue(jwtTokenProvider.validateToken(token));
    }

    @Test
    void validateToken_tamperedToken_returnsFalse() {
        String token = jwtTokenProvider.generateToken("user@test.com");
        String tampered = token.substring(0, token.length() - 5) + "XXXXX";
        assertFalse(jwtTokenProvider.validateToken(tampered));
    }

    @Test
    void validateToken_emptyToken_returnsFalse() {
        assertFalse(jwtTokenProvider.validateToken(""));
        assertFalse(jwtTokenProvider.validateToken(null));
    }

    @Test
    void validateConfig_shortSecret_throwsException() {
        JwtTokenProvider shortProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(shortProvider, "jwtSecret", SHORT_SECRET);
        ReflectionTestUtils.setField(shortProvider, "jwtExpiration", 3600000L);
        assertThrows(IllegalStateException.class, shortProvider::validateConfig);
    }

    @Test
    void validateConfig_nullSecret_throwsException() {
        JwtTokenProvider nullProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(nullProvider, "jwtSecret", null);
        ReflectionTestUtils.setField(nullProvider, "jwtExpiration", 3600000L);
        assertThrows(IllegalStateException.class, nullProvider::validateConfig);
    }

    @Test
    void generateRefreshToken_validEmail_returnsRefreshToken() {
        String refreshToken = jwtTokenProvider.generateRefreshToken("user@test.com");
        assertNotNull(refreshToken);
        assertFalse(refreshToken.isBlank());
    }

    @Test
    void refreshAccessToken_validRefreshToken_returnsNewAccessToken() {
        String refreshToken = jwtTokenProvider.generateRefreshToken("user@test.com");
        String newAccessToken = jwtTokenProvider.refreshAccessToken(refreshToken);
        assertNotNull(newAccessToken);
        // Verify the new token contains the same email
        assertEquals("user@test.com", jwtTokenProvider.getEmailFromToken(newAccessToken));
    }

    @Test
    void refreshAccessToken_invalidToken_returnsNull() {
        assertNull(jwtTokenProvider.refreshAccessToken("invalid-token"));
    }

    @Test
    void refreshAccessToken_regularToken_returnsNull() {
        // A regular access token (not refresh token) should not work for refresh
        String accessToken = jwtTokenProvider.generateToken("user@test.com");
        assertNull(jwtTokenProvider.refreshAccessToken(accessToken));
    }
}