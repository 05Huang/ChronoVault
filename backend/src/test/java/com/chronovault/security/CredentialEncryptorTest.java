package com.chronovault.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CredentialEncryptorTest {

    private static final String VALID_KEY = "test-master-key-that-is-long-enough-for-aes256!";

    private CredentialEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new CredentialEncryptor(VALID_KEY);
    }

    @Test
    void encryptAndDecrypt_roundTrip() {
        String plaintext = "super-secret-password-123";
        String encrypted = encryptor.encrypt(plaintext);

        assertNotNull(encrypted);
        assertNotEquals(plaintext, encrypted);

        String decrypted = encryptor.decrypt(encrypted);
        assertEquals(plaintext, decrypted);
    }

    @Test
    void encrypt_returnsDifferentCiphertextEachTime() {
        String plaintext = "same-input";
        String enc1 = encryptor.encrypt(plaintext);
        String enc2 = encryptor.encrypt(plaintext);

        // GCM uses random IV, so ciphertexts should differ
        assertNotEquals(enc1, enc2);
    }

    @Test
    void decrypt_withWrongKey_fails() {
        String encrypted = encryptor.encrypt("secret");
        CredentialEncryptor wrongKeyEncryptor = new CredentialEncryptor("wrong-key-but-long-enough-for-validation!!");

        assertThrows(RuntimeException.class, () -> wrongKeyEncryptor.decrypt(encrypted));
    }

    @Test
    void constructor_tooShortKey_throws() {
        assertThrows(IllegalStateException.class, () -> new CredentialEncryptor("short"));
    }

    @Test
    void encrypt_null_returnsNull() {
        assertNull(encryptor.encrypt(null));
    }

    @Test
    void decrypt_null_returnsNull() {
        assertNull(encryptor.decrypt(null));
    }

    @Test
    void encrypt_sshKey_roundTrip() {
        String sshKey = "-----BEGIN OPENSSH PRIVATE KEY-----\n" +
                "b3BlbnNzaC1rZXktdjEAAAAABG5vbmUAAAAEbm9uZQAAAAAAAAABAAAAMwAAAAtzc2gtZW\n" +
                "QyNTUxOQAAACBq0kHqJvBfEFGiGul0sYUx1BmKJWnNvWKLPq6PGRMpLwAAAJjAABCDwAA\n" +
                "QgAAAAtzc2gtZWQyNTUxOQAAACBq0kHqJvBfEFGiGul0sYUx1BmKJWnNvWKLPq6PGRMpLw\n" +
                "AAAEQDMBk0dpKvnVGZEvFSJFE3LSxlzU+MIjhQKbf3VDtFGVq0kHqJvBfEFGiGul0sYUx1\n" +
                "BmKJWnNvWKLPq6PGRMpLw==\n" +
                "-----END OPENSSH PRIVATE KEY-----\n";

        String encrypted = encryptor.encrypt(sshKey);
        String decrypted = encryptor.decrypt(encrypted);

        assertEquals(sshKey, decrypted);
    }

    @Test
    void encrypt_unicodeContent_roundTrip() {
        String unicode = "密码：测试123🔐";
        String encrypted = encryptor.encrypt(unicode);
        assertEquals(unicode, encryptor.decrypt(encrypted));
    }

    // ===== Additional edge case tests =====

    @Test
    void encrypt_emptyString_roundTrip() {
        String plaintext = "";
        String encrypted = encryptor.encrypt(plaintext);
        assertNotNull(encrypted);
        assertEquals(plaintext, encryptor.decrypt(encrypted));
    }

    @Test
    void encrypt_specialCharacters_roundTrip() {
        String special = "!@#$%^&*()_+-={}[]|\\:\";'<>?,./ ~`";
        String encrypted = encryptor.encrypt(special);
        assertEquals(special, encryptor.decrypt(encrypted));
    }

    @Test
    void encrypt_veryLongString_roundTrip() {
        String longStr = "A".repeat(100_000); // 100KB
        String encrypted = encryptor.encrypt(longStr);
        assertNotNull(encrypted);
        assertEquals(longStr, encryptor.decrypt(encrypted));
    }

    @Test
    void constructor_blankKey_throwsIllegalState() {
        assertThrows(IllegalStateException.class, () -> new CredentialEncryptor("   "));
    }

    @Test
    void constructor_nullKey_throwsIllegalState() {
        assertThrows(IllegalStateException.class, () -> new CredentialEncryptor(null));
    }

    @Test
    void constructor_exactly32Chars_works() {
        String key32 = "12345678901234567890123456789012"; // exactly 32 chars
        assertDoesNotThrow(() -> new CredentialEncryptor(key32));
    }

    @Test
    void constructor_31Chars_throws() {
        String key31 = "1234567890123456789012345678901"; // 31 chars
        assertThrows(IllegalStateException.class, () -> new CredentialEncryptor(key31));
    }

    @Test
    void decrypt_invalidBase64_throwsRuntimeException() {
        assertThrows(RuntimeException.class, () -> encryptor.decrypt("not-valid-base64!!!"));
    }

    @Test
    void decrypt_truncatedData_throwsRuntimeException() {
        // Encrypt something, then truncate the base64 to simulate corrupted data
        String encrypted = encryptor.encrypt("test");
        String truncated = encrypted.substring(0, encrypted.length() / 2);
        assertThrows(RuntimeException.class, () -> encryptor.decrypt(truncated));
    }

    @Test
    void encrypt_outputIsValidBase64() {
        String encrypted = encryptor.encrypt("test");
        // Should not throw - valid base64
        assertDoesNotThrow(() -> java.util.Base64.getDecoder().decode(encrypted));
    }

    @Test
    void multipleEncryptDecrypt_cycles_consistent() {
        String plaintext = "cycle-test-value";
        for (int i = 0; i < 10; i++) {
            String encrypted = encryptor.encrypt(plaintext);
            String decrypted = encryptor.decrypt(encrypted);
            assertEquals(plaintext, decrypted, "Cycle " + i + " failed");
        }
    }

    @Test
    void encrypt_whitespacePreserved() {
        String whitespace = "  hello  \n\tworld\n  ";
        String encrypted = encryptor.encrypt(whitespace);
        assertEquals(whitespace, encryptor.decrypt(encrypted));
    }
}
