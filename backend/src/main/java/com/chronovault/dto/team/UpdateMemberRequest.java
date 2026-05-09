package com.chronovault.dto.team;

public record UpdateMemberRequest(
    String role,
    String permissions
) {}
