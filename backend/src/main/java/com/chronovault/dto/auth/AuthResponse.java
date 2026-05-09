package com.chronovault.dto.auth;

public record AuthResponse(String token, UserDTO user) {}
