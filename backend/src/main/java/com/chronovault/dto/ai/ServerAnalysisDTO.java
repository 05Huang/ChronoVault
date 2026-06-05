package com.chronovault.dto.ai;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Data
@Builder
@Schema(description = "服务器 AI 分析结果 DTO")
public class ServerAnalysisDTO {
    private int healthScore;
    @Schema(description = "摘要")
    private String summary;
    private List<String> findings;
    @Schema(description = "建议列表")
    private List<String> recommendations;
    private String rawReport;
}
