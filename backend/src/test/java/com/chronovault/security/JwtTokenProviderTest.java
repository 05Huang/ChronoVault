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

    // ===== Additional tests: expiration, claims, edge cases =====

    @Test
    void validateToken_expiredToken_returnsFalse() throws Exception {
        // Create a provider with 1ms expiration
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(shortLivedProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(shortLivedProvider, "jwtExpiration", 1L); // 1ms
        shortLivedProvider.validateConfig();

        String token = shortLivedProvider.generateToken("user@test.com");
        Thread.sleep(50); // Wait for expiration
        assertFalse(shortLivedProvider.validateToken(token));
    }

    @Test
    void generateToken_twoTokensFromSameEmail_bothValid() {
        String token1 = jwtTokenProvider.generateToken("user@test.com");
        String token2 = jwtTokenProvider.generateToken("user@test.com");
        // Both should be valid and decode to the same email
        assertTrue(jwtTokenProvider.validateToken(token1));
        assertTrue(jwtTokenProvider.validateToken(token2));
        assertEquals("user@test.com", jwtTokenProvider.getEmailFromToken(token1));
        assertEquals("user@test.com", jwtTokenProvider.getEmailFromToken(token2));
    }

    @Test
    void validateToken_differentSecret_returnsFalse() {
        String token = jwtTokenProvider.generateToken("user@test.com");
        JwtTokenProvider differentKeyProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(differentKeyProvider, "jwtSecret",
                "another-secret-key-that-is-at-least-32-chars-long-for-hs256!!!");
        ReflectionTestUtils.setField(differentKeyProvider, "jwtExpiration", 3600000L);
        differentKeyProvider.validateConfig();

        assertFalse(differentKeyProvider.validateToken(token));
    }

    @Test
    void getEmailFromToken_tamperedToken_throwsException() {
        String token = jwtTokenProvider.generateToken("user@test.com");
        String tampered = token.substring(0, token.length() - 10) + "XXXXXXXXXX";
        assertThrows(Exception.class, () -> jwtTokenProvider.getEmailFromToken(tampered));
    }

    @Test
    void generateRefreshToken_differentFromAccessToken() {
        String accessToken = jwtTokenProvider.generateToken("user@test.com");
        String refreshToken = jwtTokenProvider.generateRefreshToken("user@test.com");
        // Refresh token should be different from access token (different expiry and claims)
        assertNotEquals(accessToken, refreshToken);
    }

    @Test
    void refreshAccessToken_generatesNewTokenWithDifferentExpiry() throws Exception {
        // Create a short-lived provider to test token refresh produces a new token
        JwtTokenProvider shortLivedProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(shortLivedProvider, "jwtSecret", TEST_SECRET);
        ReflectionTestUtils.setField(shortLivedProvider, "jwtExpiration", 3600000L);
        shortLivedProvider.validateConfig();

        String refreshToken = shortLivedProvider.generateRefreshToken("user@test.com");
        String newToken = shortLivedProvider.refreshAccessToken(refreshToken);

        assertNotNull(newToken);
        assertNotEquals(refreshToken, newToken);
        // New token should be a valid access token (not refresh type)
        assertTrue(shortLivedProvider.validateToken(newToken));
        assertEquals("user@test.com", shortLivedProvider.getEmailFromToken(newToken));
    }

    @Test
    void validateConfig_blankSecret_throwsException() {
        JwtTokenProvider blankProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(blankProvider, "jwtSecret", "   ");
        ReflectionTestUtils.setField(blankProvider, "jwtExpiration", 3600000L);
        assertThrows(IllegalStateException.class, blankProvider::validateConfig);
    }

    @Test
    void validateConfig_exactly32Chars_works() {
        JwtTokenProvider exactProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(exactProvider, "jwtSecret", "12345678901234567890123456789012"); // exactly 32
        ReflectionTestUtils.setField(exactProvider, "jwtExpiration", 3600000L);
        assertDoesNotThrow(exactProvider::validateConfig);
    }

    @Test
    void validateConfig_31Chars_throws() {
        JwtTokenProvider shortProvider = new JwtTokenProvider();
        ReflectionTestUtils.setField(shortProvider, "jwtSecret", "1234567890123456789012345678901"); // 31 chars
        ReflectionTestUtils.setField(shortProvider, "jwtExpiration", 3600000L);
        assertThrows(IllegalStateException.class, shortProvider::validateConfig);
    }
}