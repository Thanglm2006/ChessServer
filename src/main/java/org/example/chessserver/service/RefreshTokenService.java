package org.example.chessserver.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import java.time.Duration;
import java.util.UUID;
import org.example.chessserver.security.JwtUtil;


@Service
@RequiredArgsConstructor
public class RefreshTokenService {

    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;

    public String createRefreshToken(Integer userId, String email) {
    String token = UUID.randomUUID().toString();

    String value = userId + "|" + email;

    redisTemplate.opsForValue().set(
            "refresh:" + token,
            value,
            Duration.ofDays(7)
    );

    return token;
}

    public String refreshAccessToken(String refreshToken) {
        String key = "refresh:" + refreshToken;

        String value = redisTemplate.opsForValue().get(key);

        if (value == null) {
            throw new RuntimeException("Invalid or expired refresh token");
        }

        String[] parts = value.split("\\|");
        String userIdStr = parts[0];
        String email = parts[1];

        Integer userId = Integer.parseInt(userIdStr);

        return jwtUtil.generateToken(email, userId);
    }

    public void deleteRefreshToken(String refreshToken) {
        redisTemplate.delete("refresh:" + refreshToken);
    }
}
