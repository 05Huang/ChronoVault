package com.chronovault.dto.auth;

public record AuthResponse(String token, String refreshToken, UserDTO user) {}
