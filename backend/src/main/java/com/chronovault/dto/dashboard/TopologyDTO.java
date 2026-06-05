package com.chronovault.dto.dashboard;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "服务器拓扑图 DTO")
public record TopologyDTO(
    List<Node> nodes,
    List<Edge> edges
) {
    @Schema(description = "Node")
    public record Node(String id, String label, String type, String status) {}
    @Schema(description = "Edge")
    public record Edge(String source, String target) {}
}
