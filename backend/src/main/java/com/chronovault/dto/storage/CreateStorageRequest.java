package com.chronovault.dto.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateStorageRequest(
        @NotBlank(message = "存储类型不能为空")
        @Pattern(regexp = "^(LOCAL|S3|OSS|WEBDAV|BLOCK|ARCHIVE)$", message = "无效的存储类型")
        String type,

        @NotBlank(message = "名称不能为空")
        String name,

        String endpoint,

        Long totalBytes,

        // Credentials for S3/OSS
        String accessKey,
        String secretKey,

        // Config fields
        String region,
        String bucket
) {}
