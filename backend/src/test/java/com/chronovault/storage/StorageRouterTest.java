package com.chronovault.storage;

import com.chronovault.entity.StorageTarget;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StorageRouterTest {

    private StorageRouter router;
    private StorageProvider localProvider;
    private StorageProvider s3Provider;

    @BeforeEach
    void setUp() {
        localProvider = mock(StorageProvider.class);
        s3Provider = mock(StorageProvider.class);

        when(localProvider.supports(StorageTarget.StorageType.LOCAL)).thenReturn(true);
        when(localProvider.supports(StorageTarget.StorageType.S3)).thenReturn(false);
        when(localProvider.getType()).thenReturn(StorageTarget.StorageType.LOCAL);

        when(s3Provider.supports(StorageTarget.StorageType.S3)).thenReturn(true);
        when(s3Provider.supports(StorageTarget.StorageType.LOCAL)).thenReturn(false);
        when(s3Provider.getType()).thenReturn(StorageTarget.StorageType.S3);

        router = new StorageRouter(List.of(localProvider, s3Provider));
    }

    @Test
    void getProvider_local_returnsLocalProvider() {
        StorageProvider result = router.getProvider(StorageTarget.StorageType.LOCAL);
        assertEquals(localProvider, result);
    }

    @Test
    void getProvider_s3_returnsS3Provider() {
        StorageProvider result = router.getProvider(StorageTarget.StorageType.S3);
        assertEquals(s3Provider, result);
    }

    @Test
    void getProvider_unsupported_throwsException() {
        assertThrows(UnsupportedOperationException.class,
                () -> router.getProvider(StorageTarget.StorageType.WEBDAV));
    }

    @Test
    void upload_delegatesToCorrectProvider() throws Exception {
        StorageTarget target = StorageTarget.builder()
                .type(StorageTarget.StorageType.LOCAL)
                .build();
        var data = new java.io.ByteArrayInputStream("test".getBytes());

        router.upload(target, "path/file.txt", data);

        verify(localProvider).upload(eq(target), eq("path/file.txt"), any());
        verifyNoInteractions(s3Provider);
    }

    @Test
    void download_delegatesToCorrectProvider() throws Exception {
        StorageTarget target = StorageTarget.builder()
                .type(StorageTarget.StorageType.S3)
                .build();

        router.download(target, "backup.tar.gz");

        verify(s3Provider).download(target, "backup.tar.gz");
    }

    @Test
    void getHealth_delegatesToCorrectProvider() throws Exception {
        StorageTarget target = StorageTarget.builder()
                .type(StorageTarget.StorageType.LOCAL)
                .build();
        when(localProvider.getHealth(target))
                .thenReturn(new StorageProvider.StorageHealthInfo("健康", "100", "1ms", "500 MB/s", 0));

        StorageProvider.StorageHealthInfo health = router.getHealth(target);

        assertEquals("健康", health.status());
    }
}
