package com.chronovault.storage;

import com.chronovault.entity.StorageTarget;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class LocalStorageProviderTest {

    private LocalStorageProvider provider;

    @TempDir
    Path tempDir;

    private StorageTarget target;

    @BeforeEach
    void setUp() {
        provider = new LocalStorageProvider();
        target = StorageTarget.builder()
                .type(StorageTarget.StorageType.LOCAL)
                .endpoint(tempDir.toString())
                .build();
    }

    @Test
    void supports_local_returnsTrue() {
        assertTrue(provider.supports(StorageTarget.StorageType.LOCAL));
    }

    @Test
    void supports_block_returnsTrue() {
        assertTrue(provider.supports(StorageTarget.StorageType.BLOCK));
    }

    @Test
    void supports_s3_returnsFalse() {
        assertFalse(provider.supports(StorageTarget.StorageType.S3));
    }

    @Test
    void getType_returnsLocal() {
        assertEquals(StorageTarget.StorageType.LOCAL, provider.getType());
    }

    @Test
    void upload_createsFile() throws Exception {
        String content = "hello world";
        InputStream data = new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));

        provider.upload(target, "test/file.txt", data);

        Path uploaded = tempDir.resolve("test/file.txt");
        assertTrue(Files.exists(uploaded));
        assertEquals(content, Files.readString(uploaded));
    }

    @Test
    void upload_overwritesExisting() throws Exception {
        provider.upload(target, "file.txt", new ByteArrayInputStream("old".getBytes()));
        provider.upload(target, "file.txt", new ByteArrayInputStream("new".getBytes()));

        assertEquals("new", Files.readString(tempDir.resolve("file.txt")));
    }

    @Test
    void download_returnsFileContent() throws Exception {
        Files.writeString(tempDir.resolve("data.txt"), "test data");
        InputStream in = provider.download(target, "data.txt");

        assertNotNull(in);
        String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        assertEquals("test data", content);
    }

    @Test
    void download_nonExistent_throwsException() {
        assertThrows(Exception.class, () -> provider.download(target, "missing.txt"));
    }

    @Test
    void delete_existingFile_removesIt() throws Exception {
        Files.writeString(tempDir.resolve("to-delete.txt"), "delete me");
        provider.delete(target, "to-delete.txt");
        assertFalse(Files.exists(tempDir.resolve("to-delete.txt")));
    }

    @Test
    void delete_nonExistent_noException() {
        assertDoesNotThrow(() -> provider.delete(target, "nonexistent.txt"));
    }

    @Test
    void getHealth_existingDir_returnsHealthy() throws Exception {
        StorageProvider.StorageHealthInfo health = provider.getHealth(target);
        assertEquals("健康", health.status());
        assertEquals(0, health.errorCount());
    }

    @Test
    void getHealth_nonExistentDir_returnsError() throws Exception {
        StorageTarget badTarget = StorageTarget.builder()
                .type(StorageTarget.StorageType.LOCAL)
                .endpoint("/nonexistent/path")
                .build();
        StorageProvider.StorageHealthInfo health = provider.getHealth(badTarget);
        assertEquals("ERROR", health.status());
        assertEquals(1, health.errorCount());
    }

    @Test
    void getUsedBytes_withFiles_returnsCorrectSize() throws Exception {
        Files.writeString(tempDir.resolve("a.txt"), "aaaa"); // 4 bytes
        Files.writeString(tempDir.resolve("b.txt"), "bb");   // 2 bytes

        long used = provider.getUsedBytes(target);
        assertEquals(6, used);
    }

    @Test
    void getUsedBytes_emptyDir_returnsZero() throws Exception {
        assertEquals(0, provider.getUsedBytes(target));
    }
}
