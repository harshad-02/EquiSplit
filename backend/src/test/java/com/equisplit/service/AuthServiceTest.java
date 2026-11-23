package com.equisplit.service;

import com.equisplit.dto.request.LoginRequest;
import com.equisplit.dto.request.RegisterRequest;
import com.equisplit.dto.response.AuthResponse;
import com.equisplit.entity.User;
import com.equisplit.exception.ConflictException;
import com.equisplit.exception.UnauthorizedException;
import com.equisplit.repository.UserRepository;
import com.equisplit.security.JwtTokenProvider;
import com.equisplit.util.CookieUtil;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    @Mock
    private CookieUtil cookieUtil;

    @Mock
    private HttpServletResponse response;

    private PasswordEncoder passwordEncoder;
    private AuthService authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        authService = new AuthService(userRepository, passwordEncoder, tokenProvider, cookieUtil);
    }

    @Test
    @DisplayName("register hashes password with BCrypt and never stores plaintext")
    void testRegisterHashesPassword() {
        RegisterRequest request = new RegisterRequest("Test User", "test@example.com", "PlaintextPassword123!");

        when(userRepository.existsByEmailIgnoreCase("test@example.com")).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> {
            User u = invocation.getArgument(0);
            u.setId(UUID.randomUUID());
            return u;
        });

        AuthResponse authResponse = authService.register(request);

        assertNotNull(authResponse);
        assertEquals("User registered successfully", authResponse.getMessage());

        ArgumentCaptor<User> userCaptor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(userCaptor.capture());

        User savedUser = userCaptor.getValue();
        assertNotEquals("PlaintextPassword123!", savedUser.getPasswordHash());
        assertTrue(passwordEncoder.matches("PlaintextPassword123!", savedUser.getPasswordHash()));
    }

    @Test
    @DisplayName("register throws ConflictException when email is already registered")
    void testRegisterDuplicateEmail() {
        RegisterRequest request = new RegisterRequest("Test User", "duplicate@example.com", "password");
        when(userRepository.existsByEmailIgnoreCase("duplicate@example.com")).thenReturn(true);

        assertThrows(ConflictException.class, () -> authService.register(request));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    @DisplayName("login validates credentials and creates auth cookie")
    void testLoginSuccess() {
        String rawPassword = "password123";
        String encoded = passwordEncoder.encode(rawPassword);
        UUID userId = UUID.randomUUID();

        User user = new User("Test User", "test@example.com", encoded, "user");
        user.setId(userId);

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));
        when(tokenProvider.generateToken(userId, "user")).thenReturn("mock.jwt.token");
        when(tokenProvider.getExpirationMs()).thenReturn(3600000L);

        LoginRequest request = new LoginRequest("test@example.com", rawPassword);
        AuthResponse result = authService.login(request, response);

        assertNotNull(result);
        assertEquals("Login successful", result.getMessage());
        assertEquals(userId, result.getUser().getId());

        verify(cookieUtil).addAuthCookie(response, "mock.jwt.token", 3600L);
    }

    @Test
    @DisplayName("login throws UnauthorizedException when password does not match")
    void testLoginInvalidPassword() {
        String encoded = passwordEncoder.encode("correctPassword");
        User user = new User("Test User", "test@example.com", encoded, "user");

        when(userRepository.findByEmailIgnoreCase("test@example.com")).thenReturn(Optional.of(user));

        LoginRequest request = new LoginRequest("test@example.com", "wrongPassword");

        assertThrows(UnauthorizedException.class, () -> authService.login(request, response));
        verify(cookieUtil, never()).addAuthCookie(any(), anyString(), anyLong());
    }
}
