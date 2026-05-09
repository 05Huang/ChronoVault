package com.chronovault.service;

import com.chronovault.dto.auth.AuthResponse;
import com.chronovault.dto.auth.LoginRequest;
import com.chronovault.dto.auth.RegisterRequest;
import com.chronovault.dto.auth.UserDTO;
import com.chronovault.entity.User;
import com.chronovault.exception.BadRequestException;
import com.chronovault.repository.UserRepository;
import com.chronovault.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;

    @Transactional
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new BadRequestException("邮箱或密码错误"));

        if (!passwordEncoder.matches(request.password(), user.getPasswordHash())) {
            throw new BadRequestException("邮箱或密码错误");
        }

        user.setStatus(User.UserStatus.ONLINE);
        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getEmail());
        return new AuthResponse(token, UserDTO.from(user));
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.email())) {
            throw new BadRequestException("该邮箱已被注册");
        }

        User user = User.builder()
                .name(request.name())
                .email(request.email())
                .passwordHash(passwordEncoder.encode(request.password()))
                .role(User.Role.OWNER)
                .status(User.UserStatus.ONLINE)
                .build();
        userRepository.save(user);

        String token = jwtTokenProvider.generateToken(user.getEmail());
        return new AuthResponse(token, UserDTO.from(user));
    }

    public UserDTO getCurrentUser(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BadRequestException("用户不存在"));
        return UserDTO.from(user);
    }
}
