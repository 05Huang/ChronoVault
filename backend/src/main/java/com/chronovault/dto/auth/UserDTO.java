package com.chronovault.dto.auth;

import com.chronovault.entity.User;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "用户信息 DTO")
public record UserDTO(Long id, String name, String email, String role) {
    public static UserDTO from(User user) {
        return new UserDTO(user.getId(), user.getName(), user.getEmail(), user.getRole().name());
    }
}
