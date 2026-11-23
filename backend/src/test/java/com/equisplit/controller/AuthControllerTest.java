package com.equisplit.controller;

import com.equisplit.config.SecurityConfig;
import com.equisplit.dto.request.LoginRequest;
import com.equisplit.dto.request.RegisterRequest;
import com.equisplit.dto.response.ApiResponse;
import com.equisplit.dto.response.AuthResponse;
import com.equisplit.dto.response.UserDto;
import com.equisplit.entity.User;
import com.equisplit.exception.ConflictException;
import com.equisplit.exception.GlobalExceptionHandler;
import com.equisplit.exception.UnauthorizedException;
import com.equisplit.repository.UserRepository;
import com.equisplit.security.CustomUserDetailsService;
import com.equisplit.security.JwtAuthenticationEntryPoint;
import com.equisplit.security.JwtAuthenticationFilter;
import com.equisplit.security.JwtTokenProvider;
import com.equisplit.security.UserPrincipal;
import com.equisplit.service.AuthService;
import com.equisplit.util.CookieUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class, JwtAuthenticationEntryPoint.class, CookieUtil.class, GlobalExceptionHandler.class})
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private CookieUtil cookieUtil;

    @MockBean
    private AuthService authService;

    @MockBean
    private JwtTokenProvider tokenProvider;

    @MockBean
    private CustomUserDetailsService userDetailsService;

    @MockBean
    private UserRepository userRepository;

    @Test
    @DisplayName("POST /api/auth/register with valid data returns 201 Created and user object")
    void testRegisterSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        UserDto userDto = new UserDto(id, "Test User", "test@example.com", "user", Instant.now());
        AuthResponse authResponse = new AuthResponse("User registered successfully", userDto);

        when(authService.register(any(RegisterRequest.class))).thenReturn(authResponse);

        RegisterRequest request = new RegisterRequest("Test User", "test@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("User registered successfully"))
                .andExpect(jsonPath("$.user.id").value(id.toString()))
                .andExpect(jsonPath("$.user.name").value("Test User"))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.user.role").value("user"))
                .andExpect(jsonPath("$.user.password").doesNotExist());
    }

    @Test
    @DisplayName("POST /api/auth/register returns 409 Conflict if email already exists")
    void testRegisterDuplicateEmail() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenThrow(new ConflictException("Email already registered"));

        RegisterRequest request = new RegisterRequest("Test User", "existing@example.com", "password123");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("Email already registered"));
    }

    @Test
    @DisplayName("POST /api/auth/register returns 400 Bad Request if fields are invalid")
    void testRegisterInvalidInput() throws Exception {
        RegisterRequest request = new RegisterRequest("", "invalid-email", "");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("POST /api/auth/login returns 200 OK and Set-Cookie with HTTP-only token")
    void testLoginSuccess() throws Exception {
        UUID id = UUID.randomUUID();
        UserDto userDto = new UserDto(id, "Test User", "test@example.com", "user");
        AuthResponse authResponse = new AuthResponse("Login successful", userDto);

        when(authService.login(any(LoginRequest.class), any())).thenAnswer(invocation -> {
            HttpServletResponse res = invocation.getArgument(1);
            cookieUtil.addAuthCookie(res, "mock.jwt.token", 3600);
            return authResponse;
        });

        LoginRequest request = new LoginRequest("test@example.com", "password123");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie", containsString("token=mock.jwt.token")))
                .andExpect(header().string("Set-Cookie", containsString("HttpOnly")))
                .andExpect(jsonPath("$.message").value("Login successful"))
                .andExpect(jsonPath("$.user.id").value(id.toString()))
                .andExpect(jsonPath("$.user.email").value("test@example.com"))
                .andExpect(jsonPath("$.token").doesNotExist()); // Never return token in JSON response
    }

    @Test
    @DisplayName("POST /api/auth/login returns 401 Unauthorized on invalid password")
    void testLoginInvalidPassword() throws Exception {
        when(authService.login(any(LoginRequest.class), any()))
                .thenThrow(new UnauthorizedException("Invalid email or password"));

        LoginRequest request = new LoginRequest("test@example.com", "wrongpassword");

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 401 when token cookie is missing")
    void testMeWithoutToken() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Authentication required"));
    }

    @Test
    @DisplayName("GET /api/auth/me returns 200 with user data when valid token cookie is provided")
    void testMeWithValidToken() throws Exception {
        UUID userId = UUID.randomUUID();
        String token = "valid.jwt.token";

        User user = new User("Authenticated User", "auth@example.com", "hash", "user");
        user.setId(userId);
        UserPrincipal principal = UserPrincipal.create(user);

        when(tokenProvider.validateToken(token)).thenReturn(true);
        when(tokenProvider.getUserIdFromToken(token)).thenReturn(userId);
        when(userDetailsService.loadUserById(userId)).thenReturn(principal);

        UserDto userDto = new UserDto(userId, "Authenticated User", "auth@example.com", "user", Instant.now());
        when(authService.getCurrentUser(userId)).thenReturn(userDto);

        mockMvc.perform(get("/api/auth/me")
                        .cookie(new Cookie("token", token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value(userId.toString()))
                .andExpect(jsonPath("$.user.userId").value(userId.toString()))
                .andExpect(jsonPath("$.user.name").value("Authenticated User"))
                .andExpect(jsonPath("$.user.email").value("auth@example.com"))
                .andExpect(jsonPath("$.user.role").value("user"));
    }

    @Test
    @DisplayName("POST /api/auth/logout returns 200 and clears cookie")
    void testLogout() throws Exception {
        when(authService.logout(any())).thenAnswer(invocation -> {
            HttpServletResponse res = invocation.getArgument(0);
            cookieUtil.clearAuthCookie(res);
            return new ApiResponse("Logout successful");
        });

        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isOk())
                .andExpect(header().exists("Set-Cookie"))
                .andExpect(header().string("Set-Cookie", containsString("token=")))
                .andExpect(header().string("Set-Cookie", containsString("Max-Age=0")))
                .andExpect(jsonPath("$.message").value("Logout successful"));
    }
}
