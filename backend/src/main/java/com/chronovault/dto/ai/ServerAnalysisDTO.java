package com.chronovault.dto.ai;

import lombok.Builder;
import lombok.Data;

import java.util.List;

@Data
@Builder
public class ServerAnalysisDTO {
    private int healthScore;
    private String summary;
    private List<String> findings;
    private List<String> recommendations;
    private String rawReport;
}
