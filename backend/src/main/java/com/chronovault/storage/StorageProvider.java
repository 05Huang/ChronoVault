package com.chronovault.storage;

import com.chronovault.entity.StorageTarget;

import java.io.InputStream;

public interface StorageProvider {

    StorageTarget.StorageType getType();

    boolean supports(StorageTarget.StorageType type);

    void initialize(StorageTarget target) throws Exception;

    void upload(StorageTarget target, String remotePath, InputStream data) throws Exception;

    InputStream download(StorageTarget target, String remotePath) throws Exception;

    void delete(StorageTarget target, String remotePath) throws Exception;

    StorageHealthInfo getHealth(StorageTarget target) throws Exception;

    long getUsedBytes(StorageTarget target) throws Exception;

    long getTotalBytes(StorageTarget target) throws Exception;

    record StorageHealthInfo(String status, String iops, String latency, String throughput, long errorCount) {}
}
