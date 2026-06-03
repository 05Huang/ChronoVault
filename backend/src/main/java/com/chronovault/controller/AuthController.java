package com.chronovault.controller;

import com.chronovault.dto.auth.AuthResponse;
import com.chronovault.dto.auth.ChangePasswordRequest;
import com.chronovault.dto.auth.LoginRequest;
import com.chronovault.dto.auth.RefreshTokenRequest;
import com.chronovault.dto.auth.RegisterRequest;
import com.chronovault.dto.auth.UpdateProfileRequest;
import com.chronovault.dto.auth.UserDTO;
import com.chronovault.exception.GlobalExceptionHandler.ApiResponse;
import com.chronovault.security.JwtTokenProvider;
import com.chronovault.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
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
@Tag(name = "Authentication", description = "认证管理 — 登录、注册、密码修改、个人资料、Token 刷新")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

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
            @Valid @RequestBody ChangePasswordRequest body) {
        authService.changePassword(authentication.getName(), body.oldPassword(), body.newPassword());
        return ResponseEntity.ok(ApiResponse.successMsg("密码修改成功"));
    }

    @PutMapping("/profile")
    public ResponseEntity<ApiResponse<UserDTO>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest body) {
        UserDTO updated = authService.updateProfile(authentication.getName(), body.name());
        return ResponseEntity.ok(ApiResponse.success(updated));
    }

    @PostMapping("/refresh")
    @Operation(summary = "刷新 Token", description = "使用 refresh token 获取新的 access token")
    public ResponseEntity<ApiResponse<Map<String, String>>> refreshToken(
            @Valid @RequestBody RefreshTokenRequest body) {
        String newAccessToken = jwtTokenProvider.refreshAccessToken(body.refreshToken());
        if (newAccessToken == null) {
            throw new com.chronovault.exception.BadRequestException("refresh token 无效或已过期");
        }

        return ResponseEntity.ok(ApiResponse.success(Map.of("accessToken", newAccessToken)));
    }
}
