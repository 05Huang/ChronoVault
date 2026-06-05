package com.chronovault.dto.dashboard;

import io.swagger.v3.oas.annotations.media.Schema;
import java.util.List;

@Schema(description = "变更热力图 DTO — 类似 GitHub 贡献图")
public record HeatmapDTO(
    @Schema(description = "星期标签列表（周一到周日）")
    List<String> dayLabels,
    @Schema(description = "周数标签列表")
    List<String> weekLabels,
    @Schema(description = "热力图数据 [week][day] = 变更数量")
    List<List<Integer>> data,
    @Schema(description = "总变更数")
    int totalChanges,
    @Schema(description = "平均每日变更数")
    double averageDailyChanges
) {}