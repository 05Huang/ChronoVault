package com.chronovault.service;

import com.chronovault.entity.User;
import com.chronovault.exception.ResourceNotFoundException;
import com.chronovault.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder().id(1L).name("Test User").email("test@example.com").role(User.Role.MEMBER).build();
    }

    @Test
    void getByEmail_existingUser_returnsUser() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        User result = userService.getByEmail("test@example.com");
        assertNotNull(result);
        assertEquals("Test User", result.getName());
        assertEquals("test@example.com", result.getEmail());
    }

    @Test
    void getByEmail_nonExistingUser_throwsException() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.getByEmail("missing@example.com"));
    }

    @Test
    void getById_existingUser_returnsUser() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        User result = userService.getById(1L);
        assertNotNull(result);
        assertEquals(1L, result.getId());
    }

    @Test
    void getById_nonExistingUser_throwsException() {
        when(userRepository.findById(999L)).thenReturn(Optional.empty());
        assertThrows(ResourceNotFoundException.class, () -> userService.getById(999L));
    }

    @Test
    void getByEmail_returnsCorrectRole() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(testUser));
        User result = userService.getByEmail("test@example.com");
        assertEquals(User.Role.MEMBER, result.getRole());
    }

    @Test
    void getById_returnsCorrectEmail() {
        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        User result = userService.getById(1L);
        assertEquals("test@example.com", result.getEmail());
    }
}
