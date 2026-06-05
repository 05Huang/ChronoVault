package com.chronovault.dto.snapshot;

import com.chronovault.entity.ContainerState;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "容器状态快照 DTO")
public record ContainerStateDTO(
    Long id,
    @Schema(description = "容器名称", example = "nginx")
    String containerName,
    String image,
    @Schema(description = "当前状态", example = "ONLINE")
    String status,
    String ports,
    @Schema(description = "卷挂载", example = "/host:/container")
    String volumes,
    String networks
) {
    public static ContainerStateDTO from(ContainerState cs) {
        return new ContainerStateDTO(
            cs.getId(),
            cs.getContainerName(),
            cs.getImage(),
            cs.getStatus(),
            cs.getPorts(),
            cs.getVolumes(),
            cs.getNetworks()
        );
    }
}