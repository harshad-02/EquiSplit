package com.equisplit.service;

import com.equisplit.dto.request.LoginRequest;
import com.equisplit.dto.request.RegisterRequest;
import com.equisplit.dto.response.ApiResponse;
import com.equisplit.dto.response.AuthResponse;
import com.equisplit.dto.response.UserDto;
import com.equisplit.entity.User;
import com.equisplit.exception.ConflictException;
import com.equisplit.exception.NotFoundException;
import com.equisplit.exception.UnauthorizedException;
import com.equisplit.repository.UserRepository;
import com.equisplit.security.JwtTokenProvider;
import com.equisplit.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider tokenProvider;
    private final CookieUtil cookieUtil;

    public AuthService(UserRepository userRepository,
                       PasswordEncoder passwordEncoder,
                       JwtTokenProvider tokenProvider,
                       CookieUtil cookieUtil) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.tokenProvider = tokenProvider;
        this.cookieUtil = cookieUtil;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String cleanEmail = request.getEmail().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(cleanEmail)) {
            throw new ConflictException("Email already registered");
        }

        String passwordHash = passwordEncoder.encode(request.getPassword());

        User user = new User(
                request.getName().trim(),
                cleanEmail,
                passwordHash,
                "user"
        );

        User savedUser = userRepository.save(user);

        UserDto userDto = new UserDto(
                savedUser.getId(),
                savedUser.getName(),
                savedUser.getEmail(),
                savedUser.getRole(),
                savedUser.getCreatedAt()
        );

        return new AuthResponse("User registered successfully", userDto);
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request, HttpServletResponse response) {
        String cleanEmail = request.getEmail().trim().toLowerCase();

        User user = userRepository.findByEmailIgnoreCase(cleanEmail)
                .orElseThrow(() -> new UnauthorizedException("Invalid email or password"));

        if (!passwordEncoder.matches(request.getPassword(), user.getPasswordHash())) {
            throw new UnauthorizedException("Invalid email or password");
        }

        String token = tokenProvider.generateToken(user.getId(), user.getRole());
        long maxAgeSeconds = tokenProvider.getExpirationMs() / 1000;
        cookieUtil.addAuthCookie(response, token, maxAgeSeconds);

        UserDto userDto = new UserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole()
        );

        return new AuthResponse("Login successful", userDto);
    }

    public ApiResponse logout(HttpServletResponse response) {
        cookieUtil.clearAuthCookie(response);
        return new ApiResponse("Logout successful");
    }

    @Transactional(readOnly = true)
    public UserDto getCurrentUser(UUID userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User not found"));

        return new UserDto(
                user.getId(),
                user.getName(),
                user.getEmail(),
                user.getRole(),
                user.getCreatedAt()
        );
    }
}
