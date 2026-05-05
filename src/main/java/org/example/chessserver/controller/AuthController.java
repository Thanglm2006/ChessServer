package org.example.chessserver.controller;
import lombok.RequiredArgsConstructor;

import java.sql.Ref;

import org.example.chessserver.dto.GoogleLoginRequest;
import org.example.chessserver.dto.LoginRequest;
import org.example.chessserver.dto.MessageResponse;
import org.example.chessserver.dto.RegisterRequest;
import org.example.chessserver.service.AuthService;
import org.example.chessserver.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.example.chessserver.dto.TokenResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    @Autowired
    private AuthService authService;
    private RefreshTokenService refreshTokenService;

    @PostMapping("/register")
    public ResponseEntity<?> register(@RequestBody RegisterRequest request) {
        return authService.register(request);
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest request) {
        return authService.login(request);
    }

    @PostMapping("/google")
    public ResponseEntity<?> googleLogin(@RequestBody GoogleLoginRequest request) {
        return authService.loginWithGoogle(request);
    }
    @GetMapping("/me")
    public ResponseEntity<?> getMe(HttpServletRequest request) {
        return authService.getCurrentUser(request);
    }
    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(HttpServletRequest request) {
        try {
            String refreshToken = extractRefreshToken(request);

            String accessToken = refreshTokenService.refreshAccessToken(refreshToken);

            return ResponseEntity.ok(new TokenResponse(accessToken));

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(new MessageResponse("Invalid or expired refresh token"));
        }
    }

    private String extractRefreshToken(HttpServletRequest request) {
        if (request.getCookies() != null) {
            for (jakarta.servlet.http.Cookie cookie : request.getCookies()) {
                if ("refreshToken".equals(cookie.getName())) {
                    return cookie.getValue();
                }
            }
        }
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new RuntimeException("Refresh token is missing");
    }
}
