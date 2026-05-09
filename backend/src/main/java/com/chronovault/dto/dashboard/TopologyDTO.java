package com.chronovault.dto.dashboard;

import java.util.List;

public record TopologyDTO(
    List<Node> nodes,
    List<Edge> edges
) {
    public record Node(String id, String label, String type, String status) {}
    public record Edge(String source, String target) {}
}
