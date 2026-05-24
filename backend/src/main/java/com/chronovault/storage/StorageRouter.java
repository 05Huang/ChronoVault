package com.chronovault.storage;

import com.chronovault.entity.StorageTarget;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.List;

@Service
@RequiredArgsConstructor
public class StorageRouter {

    private final List<StorageProvider> providers;

    public StorageProvider getProvider(StorageTarget.StorageType type) {
        return providers.stream()
                .filter(p -> p.supports(type))
                .findFirst()
                .orElseThrow(() -> new UnsupportedOperationException("No storage provider for type: " + type));
    }

    public void upload(StorageTarget target, String remotePath, InputStream data) throws Exception {
        getProvider(target.getType()).upload(target, remotePath, data);
    }

    public InputStream download(StorageTarget target, String remotePath) throws Exception {
        return getProvider(target.getType()).download(target, remotePath);
    }

    public void delete(StorageTarget target, String remotePath) throws Exception {
        getProvider(target.getType()).delete(target, remotePath);
    }

    public StorageProvider.StorageHealthInfo getHealth(StorageTarget target) throws Exception {
        return getProvider(target.getType()).getHealth(target);
    }

    public long getUsedBytes(StorageTarget target) throws Exception {
        return getProvider(target.getType()).getUsedBytes(target);
    }

    public long getTotalBytes(StorageTarget target) throws Exception {
        return getProvider(target.getType()).getTotalBytes(target);
    }
}
