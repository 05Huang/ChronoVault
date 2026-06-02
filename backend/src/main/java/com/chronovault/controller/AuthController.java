package com.chronovault.controller;

import com.chronovault.dto.auth.AuthResponse;
import com.chronovault.dto.auth.LoginRequest;
import com.chronovault.dto.auth.RegisterRequest;
import com.chronovault.dto.auth.UserDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.service.AuthService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "认证管理 — 登录、注册、密码修改、个人资料")
public class AuthController {

    private final AuthService authService;

    @PostMapping("/login")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/register")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<UserDTO>> me(Authentication authentication) {
        UserDTO user = authService.getCurrentUser(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PutMapping("/password")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication authentication,
            @RequestBody Map<String, String> body) {
        String oldPassword = body.get("oldPassword");
        String newPassword = body.get("newPassword");
        if (oldPassword == null || newPassword == null || oldPassword.isBlank() || newPassword.isBlank()) {
            throw new com.chronovault.exception.BadRequestException("旧密码和新密码不能为空");
        }
        if (newPassword.length() < 6) {
            throw new com.chronovault.exception.BadRequestException("新密码长度不能少于6位");
        }
        authService.changePassword(authentication.getName(), oldPassword, newPassword);
        return ResponseEntity.ok(ApiResponse.successMsg("密码修改成功"));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserDTO>> updateProfile(
            Authentication authentication,
            @RequestBody Map<String, String> body) {
        String name = body.get("name");
        UserDTO updated = authService.updateProfile(authentication.getName(), name);
        return ResponseEntity.ok(ApiResponse.success(updated));
    }
}
