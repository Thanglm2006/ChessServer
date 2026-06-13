package org.example.chessserver.service;

import lombok.RequiredArgsConstructor;
import org.example.chessserver.entity.Game;
import org.example.chessserver.entity.User;
import org.example.chessserver.repository.GameRepository;
import org.example.chessserver.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class AiGameService {

    private static final Logger log = LoggerFactory.getLogger(AiGameService.class);
    private final StringRedisTemplate redisTemplate;
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${ai.api.url:http://localhost:3210/api}")
    private String aiApiUrl;

    private static final String GAME_KEY_PREFIX = "chess:ai:game:";
    private static final String CURRENT_GAME_KEY_PREFIX = "user:current_ai_game:";
    private static final Duration GAME_TTL = Duration.ofHours(2);

    /**
     * Fetch available AI checkpoints/models from FastAPI server.
     */
    public Map<String, Object> getAvailableModels() {
        try {
            String url = aiApiUrl + "/models";
            log.info("Fetching available models from: {}", url);
            return restTemplate.getForObject(url, Map.class);
        } catch (Exception e) {
            log.error("Failed to fetch available models from AI service", e);
            throw new RuntimeException("Chess AI service is currently unavailable. Please make sure the AI server is running.");
        }
    }

    /**
     * Start a new game against Chess AI.
     */
    public Map<String, Object> startGame(int userId, String aiModel, int difficulty, String playerColor) {
        String finalColor = (playerColor == null) ? "WHITE" : playerColor.toUpperCase();
        if (!"WHITE".equals(finalColor) && !"BLACK".equals(finalColor)) {
            throw new RuntimeException("Invalid player color. Must be WHITE or BLACK.");
        }

        String finalModel = (aiModel == null || aiModel.trim().isEmpty()) ? "best_model" : aiModel;
        String gameId = UUID.randomUUID().toString();
        String gameKey = GAME_KEY_PREFIX + gameId;
        String userGameKey = CURRENT_GAME_KEY_PREFIX + userId;

        Map<String, String> state = new HashMap<>();
        state.put("gameId", gameId);
        state.put("userId", String.valueOf(userId));
        state.put("playerColor", finalColor);
        state.put("aiModel", finalModel);
        state.put("difficulty", String.valueOf(difficulty));
        state.put("isGameOver", "false");

        Map<String, Object> response = new HashMap<>();
        response.put("gameId", gameId);
        response.put("playerColor", finalColor);
        response.put("aiModel", finalModel);
        response.put("difficulty", difficulty);
        response.put("isGameOver", false);
        response.put("result", null);

        if ("WHITE".equals(finalColor)) {
            String initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
            state.put("fen", initialFen);

            redisTemplate.opsForHash().putAll(gameKey, state);
            redisTemplate.expire(gameKey, GAME_TTL);
            redisTemplate.opsForValue().set(userGameKey, gameId, GAME_TTL);

            response.put("fen", initialFen);
            log.info("Started AI game {} for user {} as WHITE", gameId, userId);
        } else {
            // Player is BLACK -> AI is WHITE and moves first
            String initialFen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
            log.info("Starting AI game {} for user {} as BLACK. Requesting AI opening move.", gameId, userId);

            Map<String, Object> aiResponse = requestAiFirstMove(initialFen, finalModel, difficulty);
            String aiMoveSan = (String) aiResponse.get("ai_move_san");
            String newFen = (String) aiResponse.get("fen");
            boolean isGameOver = Boolean.TRUE.equals(aiResponse.get("is_game_over"));
            String result = (String) aiResponse.get("result");

            state.put("fen", newFen);
            state.put("isGameOver", String.valueOf(isGameOver));

            redisTemplate.opsForHash().putAll(gameKey, state);
            redisTemplate.expire(gameKey, GAME_TTL);
            redisTemplate.opsForValue().set(userGameKey, gameId, GAME_TTL);

            if (aiMoveSan != null) {
                redisTemplate.opsForList().rightPush(gameKey + ":history", aiMoveSan);
            }

            response.put("fen", newFen);
            response.put("isGameOver", isGameOver);
            response.put("result", result);
            response.put("aiFirstMove", aiMoveSan);

            if (isGameOver) {
                saveCompletedGame(userId, gameId, finalColor, result, Collections.singletonList(aiMoveSan), finalModel);
            }
        }

        return response;
    }

    /**
     * Submit a player move and get AI's response.
     */
    public Map<String, Object> makeMove(int userId, String gameId, String playerMove) {
        if (playerMove == null || playerMove.trim().isEmpty()) {
            throw new RuntimeException("Move cannot be empty.");
        }

        String gameKey = GAME_KEY_PREFIX + gameId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(gameKey);

        if (entries.isEmpty()) {
            throw new RuntimeException("Game not found or expired.");
        }

        int storedUserId = Integer.parseInt((String) entries.get("userId"));
        if (storedUserId != userId) {
            throw new RuntimeException("Unauthorized. This game does not belong to you.");
        }

        if (Boolean.parseBoolean((String) entries.get("isGameOver"))) {
            throw new RuntimeException("Game is already over.");
        }

        String currentFen = (String) entries.get("fen");
        String aiModel = (String) entries.get("aiModel");
        int difficulty = Integer.parseInt((String) entries.get("difficulty"));
        String playerColor = (String) entries.get("playerColor");

        log.info("User {} making move {} in game {}", userId, playerMove, gameId);
        Map<String, Object> aiResponse = requestAiMove(currentFen, playerMove, aiModel, difficulty);

        String newFen = (String) aiResponse.get("fen");
        boolean isGameOver = Boolean.TRUE.equals(aiResponse.get("is_game_over"));
        String result = (String) aiResponse.get("result");
        String termination = (String) aiResponse.get("termination");
        String actualPlayerMoveSan = (String) aiResponse.get("player_move_san");
        String aiMoveSan = (String) aiResponse.get("ai_move_san");

        // Push player move and optionally AI move to Redis history list
        String historyKey = gameKey + ":history";
        if (actualPlayerMoveSan != null) {
            redisTemplate.opsForList().rightPush(historyKey, actualPlayerMoveSan);
        } else {
            redisTemplate.opsForList().rightPush(historyKey, playerMove);
        }

        if (aiMoveSan != null) {
            redisTemplate.opsForList().rightPush(historyKey, aiMoveSan);
        }

        // Update the active game state in Redis
        redisTemplate.opsForHash().put(gameKey, "fen", newFen);
        redisTemplate.opsForHash().put(gameKey, "isGameOver", String.valueOf(isGameOver));

        Map<String, Object> response = new HashMap<>();
        response.put("gameId", gameId);
        response.put("playerMove", actualPlayerMoveSan != null ? actualPlayerMoveSan : playerMove);
        response.put("aiMove", aiMoveSan);
        response.put("fen", newFen);
        response.put("isGameOver", isGameOver);
        response.put("result", result);
        response.put("termination", termination);

        if (isGameOver) {
            List<String> moves = redisTemplate.opsForList().range(historyKey, 0, -1);
            saveCompletedGame(userId, gameId, playerColor, result, moves, aiModel);
        }

        return response;
    }

    /**
     * Resign the active game.
     */
    public Map<String, Object> resignGame(int userId, String gameId) {
        String gameKey = GAME_KEY_PREFIX + gameId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(gameKey);

        if (entries.isEmpty()) {
            throw new RuntimeException("Game not found or expired.");
        }

        int storedUserId = Integer.parseInt((String) entries.get("userId"));
        if (storedUserId != userId) {
            throw new RuntimeException("Unauthorized. This game does not belong to you.");
        }

        if (Boolean.parseBoolean((String) entries.get("isGameOver"))) {
            throw new RuntimeException("Game is already over.");
        }

        String playerColor = (String) entries.get("playerColor");
        String aiModel = (String) entries.get("aiModel");
        String result = "WHITE".equals(playerColor) ? "0-1" : "1-0"; // AI wins

        List<String> moves = redisTemplate.opsForList().range(gameKey + ":history", 0, -1);
        if (moves == null) {
            moves = new ArrayList<>();
        }

        log.info("User {} resigned AI game {}. Final result: {}", userId, gameId, result);
        saveCompletedGame(userId, gameId, playerColor, result, moves, aiModel);

        Map<String, Object> response = new HashMap<>();
        response.put("gameId", gameId);
        response.put("isGameOver", true);
        response.put("result", result);
        response.put("message", "Resigned successfully. AI wins.");
        return response;
    }

    /**
     * Get active game details for user if it exists.
     */
    public Map<String, Object> getActiveGame(int userId) {
        String gameId = redisTemplate.opsForValue().get(CURRENT_GAME_KEY_PREFIX + userId);
        if (gameId == null) {
            return null;
        }
        return getGameDetails(userId, gameId);
    }

    /**
     * Get details of a specific AI game.
     */
    public Map<String, Object> getGameDetails(int userId, String gameId) {
        String gameKey = GAME_KEY_PREFIX + gameId;
        Map<Object, Object> entries = redisTemplate.opsForHash().entries(gameKey);

        if (entries.isEmpty()) {
            return null;
        }

        int storedUserId = Integer.parseInt((String) entries.get("userId"));
        if (storedUserId != userId) {
            throw new RuntimeException("Unauthorized to view this game.");
        }

        List<String> history = redisTemplate.opsForList().range(gameKey + ":history", 0, -1);

        Map<String, Object> details = new HashMap<>();
        details.put("gameId", gameId);
        details.put("playerColor", entries.get("playerColor"));
        details.put("aiModel", entries.get("aiModel"));
        details.put("difficulty", Integer.parseInt((String) entries.get("difficulty")));
        details.put("fen", entries.get("fen"));
        details.put("isGameOver", Boolean.parseBoolean((String) entries.get("isGameOver")));
        details.put("history", history != null ? history : new ArrayList<>());

        return details;
    }

    // --- Helper Methods ---

    /**
     * Request AI move from Python FastAPI.
     */
    private Map<String, Object> requestAiMove(String fen, String playerMove, String model, int difficulty) {
        try {
            String url = aiApiUrl + "/move";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("fen", fen);
            body.put("player_move", playerMove);
            body.put("model", model);
            body.put("difficulty", difficulty);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            log.info("Sending move request to AI: fen={}, player_move={}, model={}", fen, playerMove, model);
            return restTemplate.postForObject(url, request, Map.class);
        } catch (HttpClientErrorException.BadRequest e) {
            log.error("Invalid move sent to AI service", e);
            throw new RuntimeException("Invalid move in this position: " + playerMove);
        } catch (Exception e) {
            log.error("Error communicating with AI service", e);
            throw new RuntimeException("Failed to get response from Chess AI service.");
        }
    }

    /**
     * Request AI first move from Python FastAPI.
     */
    private Map<String, Object> requestAiFirstMove(String fen, String model, int difficulty) {
        try {
            String url = aiApiUrl + "/ai-move-first";
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> body = new HashMap<>();
            body.put("fen", fen);
            body.put("model", model);
            body.put("difficulty", difficulty);

            HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);
            return restTemplate.postForObject(url, request, Map.class);
        } catch (Exception e) {
            log.error("Error getting opening move from AI service", e);
            throw new RuntimeException("Failed to get opening move from Chess AI service.");
        }
    }

    /**
     * Format move history to standard PGN notation.
     */
    private String buildPgn(List<String> moves) {
        if (moves == null || moves.isEmpty()) {
            return "";
        }
        StringBuilder pgn = new StringBuilder();
        for (int i = 0; i < moves.size(); i++) {
            if (i % 2 == 0) {
                pgn.append((i / 2 + 1)).append(". ");
            }
            pgn.append(moves.get(i)).append(" ");
        }
        return pgn.toString().trim();
    }

    /**
     * Save completed game history to the database and clean up Redis keys.
     */
    private void saveCompletedGame(int userId, String gameId, String playerColor, String result, List<String> moves, String aiModel) {
        try {
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new RuntimeException("User not found for game saving."));

            Game game = new Game();
            if ("WHITE".equals(playerColor)) {
                game.setWhitePlayer(user);
                game.setBlackPlayer(null); // NULL represents AI player
            } else {
                game.setWhitePlayer(null); // NULL represents AI player
                game.setBlackPlayer(user);
            }

            game.setResult(result != null ? result : "1/2-1/2");
            String pgnPrefix = (aiModel != null && !aiModel.isEmpty()) ? "{AI:" + aiModel + "} " : "";
            game.setPgnData(pgnPrefix + buildPgn(moves));

            gameRepository.save(game);
            log.info("Saved AI game {} to database. Player color={}, Result={}", gameId, playerColor, result);
        } catch (Exception e) {
            log.error("Error saving completed AI game to database", e);
        } finally {
            // Clean up Redis keys
            String gameKey = GAME_KEY_PREFIX + gameId;
            redisTemplate.delete(Arrays.asList(
                    gameKey,
                    gameKey + ":history",
                    CURRENT_GAME_KEY_PREFIX + userId
            ));
            log.info("Cleaned up Redis state for game {}", gameId);
        }
    }
}
