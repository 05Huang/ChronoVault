package com.chronovault.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class CredentialEncryptorTest {

    private CredentialEncryptor encryptor;

    @BeforeEach
    void setUp() {
        encryptor = new CredentialEncryptor("test-master-key");
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
        CredentialEncryptor wrongKeyEncryptor = new CredentialEncryptor("wrong-key");

        assertThrows(RuntimeException.class, () -> wrongKeyEncryptor.decrypt(encrypted));
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
}
