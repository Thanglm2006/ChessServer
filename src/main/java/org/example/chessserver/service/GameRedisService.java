package org.example.chessserver.service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GameRedisService {

    private final StringRedisTemplate redisTemplate;

    public Map<String, String> getGameData(String gameId) {
        Map<Object, Object> entries = redisTemplate.opsForHash().entries("chess:game:" + gameId);
        return entries.entrySet().stream()
                .collect(Collectors.toMap(e -> e.getKey().toString(), e -> e.getValue().toString()));
    }

    public void updateGameState(String gameId, String newFen, String moveStr) {
        String key = "chess:game:" + gameId;
        redisTemplate.opsForHash().put(key, "fen", newFen);
        redisTemplate.opsForList().rightPush(key + ":history", moveStr);
    }

    public List<String> getHistory(String gameId) {
        return redisTemplate.opsForList().range("chess:game:" + gameId + ":history", 0, -1);
    }

    public void saveChatMessage(String gameId, String message) {
        redisTemplate.opsForList().rightPush("chess:game:" + gameId + ":chat", message);
    }

    public List<String> getChatHistory(String gameId) {
        return redisTemplate.opsForList().range("chess:game:" + gameId + ":chat", 0, -1);
    }

    public void initializeTimers(String gameId, long durationInSeconds) {
        String key = "chess:game:" + gameId;
        redisTemplate.opsForHash().put(key, "time_white", String.valueOf(durationInSeconds));
        redisTemplate.opsForHash().put(key, "time_black", String.valueOf(durationInSeconds));
        redisTemplate.opsForHash().put(key, "last_move_time", String.valueOf(System.currentTimeMillis()));
    }

    public void saveRoomCode(String code, String hostUserId) {
        redisTemplate.opsForValue().set("room:" + code, hostUserId, Duration.ofMinutes(30));
    }

    public String getHostByRoomCode(String code) {
        return redisTemplate.opsForValue().get("room:" + code);
    }

    public void deleteRoomCode(String code) {
        redisTemplate.delete("room:" + code);
    }

    public void cleanGame(String gameId, int wId, int bId) {
        String gameKey = "chess:game:" + gameId;
        redisTemplate.delete(Arrays.asList(
                gameKey,
                gameKey + ":history",
                gameKey + ":chat",
                "user:current_game:" + wId,
                "user:current_game:" + bId
        ));
    }
}
