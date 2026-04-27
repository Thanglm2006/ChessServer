package org.example.chessserver.service;

import lombok.RequiredArgsConstructor;
import org.example.chessserver.entity.EloRating;
import org.example.chessserver.entity.User;
import org.example.chessserver.repository.EloRatingRepository;
import org.example.chessserver.repository.UserRepository;
import org.example.chessserver.websocket.ChessWebSocketHandler;
import org.json.JSONObject;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisKeyExpiredEvent;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;

@Service
@EnableScheduling
@RequiredArgsConstructor
public class MatchmakingService {

    private static final String MAIN_QUEUE = "chess:queue:matchmaking";
    private final StringRedisTemplate redisTemplate;
    private final ChessWebSocketHandler webSocketHandler;
    private final UserRepository userRepository;
    private final EloRatingRepository eloRatingRepository;

    public void joinQueue(int userId) {
        redisTemplate.opsForList().rightPush(MAIN_QUEUE, String.valueOf(userId));
    }

    @Scheduled(fixedDelay = 500)
    public void processQueue() {
        Long size = redisTemplate.opsForList().size(MAIN_QUEUE);
        if (size != null && size >= 2) {
            String u1 = redisTemplate.opsForList().leftPop(MAIN_QUEUE);
            String u2 = redisTemplate.opsForList().leftPop(MAIN_QUEUE);

            if (u1 != null && u2 != null) {
                if (u1.equals(u2)) {
                    // Same user joined twice? Throw one back, keep looking.
                    redisTemplate.opsForList().rightPush(MAIN_QUEUE, u1);
                } else {
                    createPendingMatch(u1, u2);
                }
            }
        }
    }

    private void createPendingMatch(String u1, String u2) {
        String gameId = UUID.randomUUID().toString();
        String pendingKey = String.format("pending:game:%s:%s:%s", gameId, u1, u2);

        // Set to 10 seconds to confirm
        redisTemplate.opsForValue().set(pendingKey, "WAITING", Duration.ofSeconds(10));

        try {
            User user1 = userRepository.findById(Integer.parseInt(u1)).orElse(null);
            User user2 = userRepository.findById(Integer.parseInt(u2)).orElse(null);
            
            Integer r1 = eloRatingRepository.findById(Integer.parseInt(u1)).map(EloRating::getRating).orElse(1200);
            Integer r2 = eloRatingRepository.findById(Integer.parseInt(u2)).map(EloRating::getRating).orElse(1200);

            // Send to u1 with u2 as opponent
            JSONObject msg1 = new JSONObject()
                    .put("type", "PREPARE_GAME")
                    .put("gameId", gameId)
                    .put("opponentId", u2)
                    .put("opponentName", user2 != null ? user2.getUsername() : "Unknown")
                    .put("opponentCountry", user2 != null ? user2.getCountryCode() : "??")
                    .put("opponentRating", r2)
                    .put("timeout", 10);
            webSocketHandler.sendToUser(Integer.parseInt(u1), msg1.toString());

            // Send to u2 with u1 as opponent
            JSONObject msg2 = new JSONObject()
                    .put("type", "PREPARE_GAME")
                    .put("gameId", gameId)
                    .put("opponentId", u1)
                    .put("opponentName", user1 != null ? user1.getUsername() : "Unknown")
                    .put("opponentCountry", user1 != null ? user1.getCountryCode() : "??")
                    .put("opponentRating", r1)
                    .put("timeout", 10);
            webSocketHandler.sendToUser(Integer.parseInt(u2), msg2.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @EventListener
    public void handleRedisKeyExpiredEvent(RedisKeyExpiredEvent<String> event) {
        String expiredKey = new String(event.getId());
        if (expiredKey.startsWith("pending:game:")) {
            processTimeout(expiredKey);
        }
    }

    private void processTimeout(String expiredKey) {
        String[] parts = expiredKey.split(":");
        if (parts.length < 5) return;

        int u1 = Integer.parseInt(parts[3]);
        int u2 = Integer.parseInt(parts[4]);

        if (webSocketHandler.isUserOnline(u1)) joinQueue(u1);
        if (webSocketHandler.isUserOnline(u2)) joinQueue(u2);
    }
}
