package org.example.chessserver.service;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

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

    public void cleanGame(String gameId, int wId, int bId) {
        String gameKey = "chess:game:" + gameId;
        redisTemplate.delete(Arrays.asList(
                gameKey,
                gameKey + ":history",
                "user:current_game:" + wId,
                "user:current_game:" + bId
        ));
    }
}
