package com.chronovault.dto.team;

import com.chronovault.entity.TeamMember;

public record TeamMemberDTO(
    Long id, String name, String email, String role,
    String avatar, String status, String lastActive
) {
    public static TeamMemberDTO from(TeamMember m) {
        String lastActive = m.getLastActiveAt() != null ? m.getLastActiveAt().toString() : "";
        return new TeamMemberDTO(m.getId(), m.getName(), m.getEmail(), m.getRole().name(),
                m.getName().substring(0, 1).toUpperCase(), m.getStatus().name(), lastActive);
    }
}
