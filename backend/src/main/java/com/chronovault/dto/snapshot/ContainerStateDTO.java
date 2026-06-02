package com.chronovault.dto.snapshot;

import com.chronovault.entity.ContainerState;

public record ContainerStateDTO(
    Long id,
    String containerName,
    String image,
    String status,
    String ports,
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