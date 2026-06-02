package com.chronovault.service;

import com.chronovault.dto.auth.AuthResponse;
import com.chronovault.dto.auth.LoginRequest;
import com.chronovault.dto.auth.RegisterRequest;
import com.chronovault.dto.auth.UserDTO;
import com.chronovault.entity.User;
import com.chronovault.exception.BadRequestException;
import com.chronovault.repository.UserRepository;
import com.chronovault.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    @InjectMocks
    private AuthService authService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .name("Test User")
                .email("test@example.com")
                .passwordHash("$2a$hashed-password")
                .role(User.Role.MEMBER)
                .status(User.UserStatus.OFFLINE)
                .build();
    }

    @Test
    void login_validCredentials_returnsToken() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "$2a$hashed-password")).thenReturn(true);
        when(jwtTokenProvider.generateToken("test@example.com")).thenReturn("jwt-token-123");

        AuthResponse response = authService.login(new LoginRequest("test@example.com", "password123"));

        assertNotNull(response);
        assertNotNull(response.token());
        assertEquals("jwt-token-123", response.token());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void login_invalidEmail_throwsBadRequest() {
        when(userRepository.findByEmail("wrong@example.com")).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> authService.login(new LoginRequest("wrong@example.com", "password")));
    }

    @Test
    void login_invalidPassword_throwsBadRequest() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("wrong-password", "$2a$hashed-password")).thenReturn(false);

        assertThrows(BadRequestException.class,
                () -> authService.login(new LoginRequest("test@example.com", "wrong-password")));
    }

    @Test
    void login_setsUserStatusOnline() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        when(passwordEncoder.matches("password123", "$2a$hashed-password")).thenReturn(true);
        when(jwtTokenProvider.generateToken("test@example.com")).thenReturn("token");

        authService.login(new LoginRequest("test@example.com", "password123"));

        verify(userRepository).save(argThat(user -> user.getStatus() == User.UserStatus.ONLINE));
    }

    @Test
    void register_newUser_returnsToken() {
        when(userRepository.existsByEmail("new@example.com")).thenReturn(false);
        when(userRepository.count()).thenReturn(5L);
        when(passwordEncoder.encode("password123")).thenReturn("$2a$encoded");
        when(jwtTokenProvider.generateToken("new@example.com")).thenReturn("new-token");

        AuthResponse response = authService.register(new RegisterRequest("New User", "new@example.com", "password123"));

        assertNotNull(response);
        assertEquals("new-token", response.token());
        verify(userRepository).save(any(User.class));
    }

    @Test
    void register_firstUser_becomesOwner() {
        when(userRepository.existsByEmail("first@example.com")).thenReturn(false);
        when(userRepository.count()).thenReturn(0L);
        when(passwordEncoder.encode("pass")).thenReturn("$2a$encoded");
        when(jwtTokenProvider.generateToken("first@example.com")).thenReturn("token");

        authService.register(new RegisterRequest("First User", "first@example.com", "pass"));

        verify(userRepository).save(argThat(user -> user.getRole() == User.Role.OWNER));
    }

    @Test
    void register_subsequentUser_becomesMember() {
        when(userRepository.existsByEmail("user@example.com")).thenReturn(false);
        when(userRepository.count()).thenReturn(5L);
        when(passwordEncoder.encode("pass")).thenReturn("$2a$encoded");
        when(jwtTokenProvider.generateToken("user@example.com")).thenReturn("token");

        authService.register(new RegisterRequest("User", "user@example.com", "pass"));

        verify(userRepository).save(argThat(user -> user.getRole() == User.Role.MEMBER));
    }

    @Test
    void register_duplicateEmail_throwsBadRequest() {
        when(userRepository.existsByEmail("existing@example.com")).thenReturn(true);

        assertThrows(BadRequestException.class,
                () -> authService.register(new RegisterRequest("User", "existing@example.com", "pass")));
    }

    @Test
    void getCurrentUser_validEmail_returnsUser() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));

        UserDTO dto = authService.getCurrentUser("test@example.com");

        assertNotNull(dto);
        assertEquals("Test User", dto.name());
    }

    @Test
    void getCurrentUser_invalidEmail_throwsBadRequest() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThrows(BadRequestException.class,
                () -> authService.getCurrentUser("missing@example.com"));
    }
}