package com.chronovault.controller;

import com.chronovault.dto.auth.*;
import com.chronovault.exception.BadRequestException;
import com.chronovault.security.JwtTokenProvider;
import com.chronovault.service.AuthService;
import com.chronovault.service.UserService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthControllerTest {

    @Mock private AuthService authService;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private UserService userService;

    @InjectMocks
    private AuthController controller;

    @Test
    void login_validCredentials_returnsToken() {
        LoginRequest request = new LoginRequest("test@test.com", "password123");
        UserDTO userDTO = new UserDTO(1L, "Test", "test@test.com", "MEMBER");
        AuthResponse response = new AuthResponse("access-token", "refresh-token", userDTO);
        when(authService.login(request)).thenReturn(response);

        var result = controller.login(request);
        assertEquals(200, result.getStatusCode().value());
        assertEquals("access-token", result.getBody().data().token());
    }

    @Test
    void login_invalidCredentials_throws() {
        LoginRequest request = new LoginRequest("test@test.com", "wrong");
        when(authService.login(request)).thenThrow(new BadCredentialsException("bad creds"));

        assertThrows(BadCredentialsException.class, () -> controller.login(request));
    }

    @Test
    void register_validRequest_returnsToken() {
        RegisterRequest request = new RegisterRequest("Test User", "new@test.com", "password123");
        UserDTO userDTO = new UserDTO(2L, "Test User", "new@test.com", "MEMBER");
        AuthResponse response = new AuthResponse("access-token", "refresh-token", userDTO);
        when(authService.register(request)).thenReturn(response);

        var result = controller.register(request);
        assertEquals(200, result.getStatusCode().value());
        assertEquals("new@test.com", result.getBody().data().user().email());
    }

    @Test
    void refreshToken_validToken_returnsNewAccessToken() {
        RefreshTokenRequest request = new RefreshTokenRequest("valid-refresh-token");
        when(jwtTokenProvider.refreshAccessToken("valid-refresh-token")).thenReturn("new-access-token");

        var result = controller.refreshToken(request);
        assertEquals(200, result.getStatusCode().value());
        assertEquals("new-access-token", result.getBody().data().get("accessToken"));
    }

    @Test
    void refreshToken_invalidToken_throws() {
        RefreshTokenRequest request = new RefreshTokenRequest("invalid-token");
        when(jwtTokenProvider.refreshAccessToken("invalid-token")).thenReturn(null);

        assertThrows(BadRequestException.class, () -> controller.refreshToken(request));
    }

    @Test
    void changePassword_validRequest_succeeds() {
        ChangePasswordRequest request = new ChangePasswordRequest("oldPass123", "newPass123");
        doNothing().when(authService).changePassword("test@test.com", "oldPass123", "newPass123");

        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test@test.com", null);
        var result = controller.changePassword(auth, request);
        assertEquals(200, result.getStatusCode().value());
        verify(authService).changePassword("test@test.com", "oldPass123", "newPass123");
    }

    @Test
    void updateProfile_validRequest_returnsUpdatedUser() {
        UpdateProfileRequest request = new UpdateProfileRequest("Updated Name");
        UserDTO userDTO = new UserDTO(1L, "Updated Name", "test@test.com", "MEMBER");
        when(authService.updateProfile("test@test.com", "Updated Name")).thenReturn(userDTO);

        Authentication auth = new org.springframework.security.authentication.UsernamePasswordAuthenticationToken("test@test.com", null);
        var result = controller.updateProfile(auth, request);
        assertEquals(200, result.getStatusCode().value());
        assertEquals("Updated Name", result.getBody().data().name());
    }
}