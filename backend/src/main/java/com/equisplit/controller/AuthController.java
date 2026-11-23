`package com.equisplit.controller;

import com.equisplit.dto.request.LoginRequest;
import com.equisplit.dto.request.RegisterRequest;
import com.equisplit.dto.response.ApiResponse;
import com.equisplit.dto.response.AuthResponse;
import com.equisplit.dto.response.UserDto;
import com.equisplit.security.UserPrincipal;
import com.equisplit.service.AuthService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse response = authService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request,
                                             HttpServletResponse response) {
        AuthResponse authResponse = authService.login(request, response);
        return ResponseEntity.ok(authResponse);
    }

    @PostMapping("/logout")
    public ResponseEntity<ApiResponse> logout(HttpServletResponse response) {
        ApiResponse apiResponse = authService.logout(response);
        return ResponseEntity.ok(apiResponse);
    }

    @GetMapping("/me")
    public ResponseEntity<Map<String, UserDto>> me(@AuthenticationPrincipal UserPrincipal principal) {
        UserDto userDto = authService.getCurrentUser(principal.getId());
        return ResponseEntity.ok(Map.of("user", userDto));
    }
}
