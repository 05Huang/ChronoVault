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
import com.chronovault.audit.Auditable;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.Map;
import com.chronovault.config.ApiVersion;

@RestController
@RequestMapping(ApiVersion.V1 + "/auth")
@RequiredArgsConstructor
@Tag(name = "Authentication", description = "认证管理 — 登录、注册、密码修改、个人资料、Token 刷新")
public class AuthController {

    private final AuthService authService;
    private final JwtTokenProvider jwtTokenProvider;

    @Auditable(action = "用户登录", changeType = "USER_ACTION", resourceType = "USER")
    @PostMapping("/login")
    @Operation(summary = "用户登录", description = "使用邮箱和密码登录，返回 JWT access token 和 refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> login(@Valid @RequestBody LoginRequest request) {
        AuthResponse response = authService.login(request);
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @Auditable(action = "用户注册", changeType = "USER_ACTION", resourceType = "USER")
    @PostMapping("/register")
    @Operation(summary = "用户注册", description = "注册新用户账号，返回 JWT access token 和 refresh token")
    public ResponseEntity<ApiResponse<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.created(URI.create(ApiVersion.V1 + "auth/me"))
                .body(ApiResponse.success(response));
    }

    @GetMapping("/me")
    @Operation(summary = "获取当前用户信息", description = "获取当前已登录用户的个人资料")
    public ResponseEntity<ApiResponse<UserDTO>> me(Authentication authentication) {
        UserDTO user = authService.getCurrentUser(authentication.getName());
        return ResponseEntity.ok(ApiResponse.success(user));
    }

    @PutMapping("/password")
    @Operation(summary = "修改密码", description = "修改当前用户的登录密码")
    public ResponseEntity<ApiResponse<Void>> changePassword(
            Authentication authentication,
            @Valid @RequestBody ChangePasswordRequest body) {
        authService.changePassword(authentication.getName(), body.oldPassword(), body.newPassword());
        return ResponseEntity.ok(ApiResponse.successMsg("密码修改成功"));
    }

    @PutMapping("/profile")
    @Operation(summary = "更新个人资料", description = "更新当前用户的显示名称")
    public ResponseEntity<ApiResponse<UserDTO>> updateProfile(
            Authentication authentication,
            @Valid @RequestBody UpdateProfileRequest body) {
        UserDTO updated = authService.updateProfile(authentication.getName(), body.name());
        return ResponseEntity.ok()
                .location(URI.create(ApiVersion.V1 + "auth/me"))
                .body(ApiResponse.success(updated));
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
