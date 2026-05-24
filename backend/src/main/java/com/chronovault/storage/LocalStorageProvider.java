package com.chronovault.storage;

import com.chronovault.entity.StorageTarget;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.*;
import java.nio.file.*;

@Slf4j
@Component
public class LocalStorageProvider implements StorageProvider {

    @Override
    public StorageTarget.StorageType getType() {
        return StorageTarget.StorageType.LOCAL;
    }

    @Override
    public boolean supports(StorageTarget.StorageType type) {
        return type == StorageTarget.StorageType.LOCAL || type == StorageTarget.StorageType.BLOCK;
    }

    @Override
    public void initialize(StorageTarget target) throws Exception {
        Path path = Paths.get(target.getEndpoint());
        Files.createDirectories(path);
    }

    @Override
    public void upload(StorageTarget target, String remotePath, InputStream data) throws Exception {
        Path filePath = Paths.get(target.getEndpoint(), remotePath);
        Files.createDirectories(filePath.getParent());
        Files.copy(data, filePath, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public InputStream download(StorageTarget target, String remotePath) throws Exception {
        Path filePath = Paths.get(target.getEndpoint(), remotePath);
        return Files.newInputStream(filePath);
    }

    @Override
    public void delete(StorageTarget target, String remotePath) throws Exception {
        Path filePath = Paths.get(target.getEndpoint(), remotePath);
        Files.deleteIfExists(filePath);
    }

    @Override
    public StorageHealthInfo getHealth(StorageTarget target) throws Exception {
        Path path = Paths.get(target.getEndpoint());
        if (!Files.exists(path)) {
            return new StorageHealthInfo("ERROR", "0", "0ms", "0 MB/s", 1);
        }

        // Measure actual write latency with a small temp file
        Path tempFile = path.resolve(".cv-health-check");
        byte[] payload = new byte[4096];
        long writeStart = System.nanoTime();
        try {
            Files.write(tempFile, payload, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } finally {
            Files.deleteIfExists(tempFile);
        }
        long writeElapsedNs = System.nanoTime() - writeStart;
        long latencyMs = Math.max(1, writeElapsedNs / 1_000_000);

        // Estimate throughput from the write timing
        double throughputMBps = (payload.length / (1024.0 * 1024.0)) / (writeElapsedNs / 1_000_000_000.0);
        String throughput = String.format("%.0f MB/s", Math.max(1, throughputMBps));
        String latency = latencyMs < 1 ? "<1ms" : latencyMs + "ms";

        return new StorageHealthInfo("健康", "N/A", latency, throughput, 0);
    }

    @Override
    public long getUsedBytes(StorageTarget target) throws Exception {
        Path path = Paths.get(target.getEndpoint());
        if (!Files.exists(path)) return 0;
        return Files.walk(path)
                .filter(Files::isRegularFile)
                .mapToLong(p -> {
                    try { return Files.size(p); } catch (Exception e) { return 0; }
                })
                .sum();
    }

    @Override
    public long getTotalBytes(StorageTarget target) throws Exception {
        Path path = Paths.get(target.getEndpoint());
        return path.toFile().getTotalSpace();
    }
}
