package org.example.chessserver.websocket;
import com.github.bhlangonijr.chesslib.Board;
import lombok.RequiredArgsConstructor;
import org.example.chessserver.entity.Game;
import org.example.chessserver.entity.User;
import org.example.chessserver.repository.GameRepository;
import org.example.chessserver.repository.UserRepository;
import org.example.chessserver.security.JwtUtil;
import org.example.chessserver.service.ChessGameService;
import org.example.chessserver.service.GameRedisService;
import org.json.JSONObject;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ChessWebSocketHandler extends TextWebSocketHandler {

    private final Map<Integer, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final ChessGameService gameService;
    private final GameRedisService gameRedisService;
    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            String query = session.getUri().getQuery();
            String token = query.split("token=")[1].split("&")[0];
            int userId = jwtUtil.getClaims(token).get("userId", Integer.class);
            sessions.put(userId, session);
            handleReconnection(userId);
        } catch (Exception e) {
            e.printStackTrace();
            session.close(CloseStatus.BAD_DATA.withReason("Auth Failed"));
        }
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JSONObject json = new JSONObject(message.getPayload());
        String type = json.getString("type");
        int userId = getUserIdBySession(session);
        if (userId == -1) return;

        switch (type) {
            case "READY" -> handleReady(json.getString("gameId"), userId);
            case "REJECT_MATCH" -> handleMatchReject(json.getString("gameId"), userId);
            case "MOVE" -> handleMove(json, userId);
            case "DRAW_OFFER" -> handleDrawOffer(json.getString("gameId"), userId);
            case "DRAW_RESPONSE" -> handleDrawResponse(json, userId);
            case "SURRENDER", "RESIGN" -> handleEndGame(json.getString("gameId"), userId, "RESIGN");
        }
    }

    private void handleMatchReject(String gameId, int userId) throws Exception {
        String pendingPattern = "pending:game:" + gameId + ":*";
        Set<String> keys = redisTemplate.keys(pendingPattern);
        if (keys != null) {
            for (String key : keys) {
                String[] parts = key.split(":");
                int u1 = Integer.parseInt(parts[3]);
                int u2 = Integer.parseInt(parts[4]);
                
                int opponentId = (userId == u1) ? u2 : u1;
                
                // Notify opponent
                sendToUser(opponentId, new JSONObject()
                    .put("type", "MATCH_CANCELLED")
                    .put("reason", "Opponent rejected the match").toString());
                
                redisTemplate.delete(key);
            }
        }
    }

    private void handleReady(String gameId, int userId) {
        String readyKey = "ready:check:" + gameId;
        redisTemplate.opsForSet().add(readyKey, String.valueOf(userId));

        if (redisTemplate.opsForSet().size(readyKey) == 2L) {
            String pattern = "pending:game:" + gameId + ":*";
            Set<String> keys = redisTemplate.keys(pattern);
            if (keys != null) {
                for (String key : keys) {
                    startGame(gameId, key);
                    redisTemplate.delete(key);
                }
            }
            redisTemplate.delete(readyKey);
        }
    }

    private void startGame(String gameId, String keyInfo) {
        String[] parts = keyInfo.split(":");
        int u1 = Integer.parseInt(parts[3]);
        int u2 = Integer.parseInt(parts[4]);

        String gameKey = "chess:game:" + gameId;
        Map<String, String> data = new HashMap<>();
        data.put("white", String.valueOf(u1));
        data.put("black", String.valueOf(u2));
        data.put("fen", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");

        redisTemplate.opsForHash().putAll(gameKey, data);
        redisTemplate.opsForValue().set("user:current_game:" + u1, gameId, Duration.ofSeconds(7200));
        redisTemplate.opsForValue().set("user:current_game:" + u2, gameId, Duration.ofSeconds(7200));
        redisTemplate.expire(gameKey, Duration.ofSeconds(7200));

        broadcastStart(gameId, u1, u2);
    }

    private void handleMove(JSONObject json, int userId) throws Exception {
        String gameId = json.getString("gameId");
        String moveStr = json.getString("move");

        Map<String, String> data = gameRedisService.getGameData(gameId);
        if (data == null || data.isEmpty()) return;

        Board board = gameService.loadBoard(data.get("fen"));
        String newFen = gameService.handleMoveLogic(board, moveStr);

        if (newFen == null) {
            sendToUser(userId, new JSONObject().put("type", "ERROR").put("message", "Nước đi sai luật!").toString());
            return;
        }

        gameRedisService.updateGameState(gameId, newFen, moveStr);

        int opponentId = (userId == Integer.parseInt(data.get("white")))
                ? Integer.parseInt(data.get("black")) : Integer.parseInt(data.get("white"));

        sendToUser(opponentId, new JSONObject()
                .put("type", "OPPONENT_MOVE")
                .put("move", moveStr)
                .put("fen", newFen).toString());

        String status = gameService.getGameStatus(board);
        if (!status.equals("CONTINUE") && !status.equals("CHECK")) {
            handleEndGame(gameId, userId, status);
        }
    }

    private void handleReconnection(int userId) throws Exception {
        String gameId = redisTemplate.opsForValue().get("user:current_game:" + userId);
        if (gameId != null) {
            Map<String, String> data = gameRedisService.getGameData(gameId);
            if (data != null && !data.isEmpty()) {
                int opponentId = Integer.parseInt(data.get("white")) == userId ? Integer.parseInt(data.get("black")) : Integer.parseInt(data.get("white"));
                User opp = userRepository.findById(opponentId).orElse(null);
                
                List<String> history = gameRedisService.getHistory(gameId);
                JSONObject msg = new JSONObject()
                        .put("type", "RECONNECT_GAME")
                        .put("gameId", gameId)
                        .put("fen", data.get("fen"))
                        .put("side", Integer.parseInt(data.get("white")) == userId ? "WHITE" : "BLACK")
                        .put("history", history)
                        .put("opponentId", opponentId)
                        .put("opponentName", opp != null ? opp.getUsername() : "Opponent #" + opponentId)
                        .put("opponentRating", 1200); // Simplification for now
                sendToUser(userId, msg.toString());
            }
        }
    }

    private void handleDrawOffer(String gameId, int userId) throws Exception {
        Map<String, String> data = gameRedisService.getGameData(gameId);
        int oppId = (Integer.parseInt(data.get("white")) == userId) ? Integer.parseInt(data.get("black")) : Integer.parseInt(data.get("white"));
        sendToUser(oppId, new JSONObject().put("type", "DRAW_OFFERED").put("gameId", gameId).toString());
    }

    private void handleDrawResponse(JSONObject json, int userId) throws Exception {
        String gameId = json.getString("gameId");
        if (json.getBoolean("accepted")) {
            handleEndGame(gameId, userId, "DRAW_AGREED");
        } else {
            Map<String, String> data = gameRedisService.getGameData(gameId);
            int oppId = (Integer.parseInt(data.get("white")) == userId) ? Integer.parseInt(data.get("black")) : Integer.parseInt(data.get("white"));
            sendToUser(oppId, new JSONObject().put("type", "DRAW_REJECTED").toString());
        }
    }

    private void handleEndGame(String gameId, int userId, String reason) throws Exception {
        Map<String, String> data = gameRedisService.getGameData(gameId);
        if (data == null || data.isEmpty()) return;

        int w = Integer.parseInt(data.get("white"));
        int b = Integer.parseInt(data.get("black"));
        String pgn = String.join(" ", gameRedisService.getHistory(gameId));

        String result = "1/2-1/2";
        if (reason.equals("RESIGN") || reason.equals("SURRENDER")) {
            result = (userId == w) ? "0-1" : "1-0";
        } else if (reason.equals("CHECKMATE")) {
            result = (userId == w) ? "1-0" : "0-1";
        }

        Game game = new Game();
        game.setWhitePlayer(userRepository.findById(w).orElse(null));
        game.setBlackPlayer(userRepository.findById(b).orElse(null));
        game.setResult(result);
        game.setPgnData(pgn);
        gameRepository.save(game);

        JSONObject endMsg = new JSONObject().put("type", "GAME_OVER").put("result", result).put("reason", reason);
        sendToUser(w, endMsg.toString());
        sendToUser(b, endMsg.toString());

        gameRedisService.cleanGame(gameId, w, b);
    }

    private int getUserIdBySession(WebSocketSession session) {
        return sessions.entrySet().stream()
                .filter(e -> e.getValue().equals(session))
                .map(Map.Entry::getKey).findFirst().orElse(-1);
    }

    private void broadcastStart(String gId, int u1, int u2) {
        try {
            sendToUser(u1, new JSONObject().put("type", "GAME_START").put("gameId", gId).put("side", "WHITE").put("opponent", u2).toString());
            sendToUser(u2, new JSONObject().put("type", "GAME_START").put("gameId", gId).put("side", "BLACK").put("opponent", u1).toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public void sendToUser(int userId, String message) throws Exception {
        WebSocketSession session = sessions.get(userId);
        if (session != null && session.isOpen()) {
            session.sendMessage(new TextMessage(message));
        }
    }

    public boolean isUserOnline(int userId) {
        WebSocketSession session = sessions.get(userId);
        return session != null && session.isOpen();
    }
    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.values().remove(session);
    }
}
