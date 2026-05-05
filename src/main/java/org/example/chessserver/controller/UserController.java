package org.example.chessserver.controller;

import lombok.RequiredArgsConstructor;
import org.example.chessserver.dto.UserProfileDto;
import org.example.chessserver.service.UserService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {
    private final UserService userService;
    private final org.example.chessserver.security.JwtUtil jwtUtil;

    @GetMapping("/me")
    public ResponseEntity<UserProfileDto> getMe(jakarta.servlet.http.HttpServletRequest request) {
        String authHeader = request.getHeader(org.springframework.http.HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            int userId = jwtUtil.getClaims(token).get("userId", Integer.class);
            return ResponseEntity.ok(userService.getUserProfile(userId));
        }
        throw new RuntimeException("Authorization header is missing or invalid");
    }

    @GetMapping("/{id}/stats")
    public ResponseEntity<UserProfileDto> getUserStats(@PathVariable int id) {
        return ResponseEntity.ok(userService.getUserProfile(id));
    }
}
