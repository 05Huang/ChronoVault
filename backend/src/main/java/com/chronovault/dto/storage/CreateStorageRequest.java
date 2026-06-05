package com.chronovault.dto.storage;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "创建存储目标请求")
public record CreateStorageRequest(
        @NotBlank(message = "存储类型不能为空")
        @Pattern(regexp = "^(LOCAL|S3|OSS|WEBDAV|BLOCK|ARCHIVE)$", message = "无效的存储类型")
        @Schema(description = "类型", example = "FULL")
        String type,

        @NotBlank(message = "名称不能为空")
        @Schema(description = "名称")
        String name,

        @Schema(description = "存储端点地址", example = "https://s3.amazonaws.com")
        String endpoint,

        @Schema(description = "总容量（字节）", example = "10737418240")
        Long totalBytes,

        // Credentials for S3/OSS
        @Schema(description = "访问密钥")
        String accessKey,
        String secretKey,

        // Config fields
        @Schema(description = "存储区域", example = "us-east-1")
        String region,
        String bucket
) {}
