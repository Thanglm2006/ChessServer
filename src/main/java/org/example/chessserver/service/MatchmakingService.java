package org.example.chessserver.service;

import lombok.RequiredArgsConstructor;
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

    public void joinQueue(int userId) {
        redisTemplate.opsForList().rightPush(MAIN_QUEUE, String.valueOf(userId));
    }

    @Scheduled(fixedDelay = 1000)
    public void processQueue() {
        Long size = redisTemplate.opsForList().size(MAIN_QUEUE);
        if (size != null && size >= 2) {
            String u1 = redisTemplate.opsForList().leftPop(MAIN_QUEUE);
            String u2 = redisTemplate.opsForList().leftPop(MAIN_QUEUE);

            if (u1 != null && u2 != null) {
                createPendingMatch(u1, u2);
            }
        }
    }

    private void createPendingMatch(String u1, String u2) {
        String gameId = UUID.randomUUID().toString();
        String pendingKey = String.format("pending:game:%s:%s:%s", gameId, u1, u2);

        redisTemplate.opsForValue().set(pendingKey, "WAITING", Duration.ofSeconds(30));

        JSONObject msg = new JSONObject()
                .put("type", "PREPARE_GAME")
                .put("gameId", gameId);

        try {
            webSocketHandler.sendToUser(Integer.parseInt(u1), msg.toString());
            webSocketHandler.sendToUser(Integer.parseInt(u2), msg.toString());
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
