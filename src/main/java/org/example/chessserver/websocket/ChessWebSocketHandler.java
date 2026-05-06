package org.example.chessserver.websocket;
import com.github.bhlangonijr.chesslib.Board;
import lombok.RequiredArgsConstructor;
import org.example.chessserver.entity.EloRating;
import org.example.chessserver.entity.User;
import org.example.chessserver.repository.EloRatingRepository;
import org.example.chessserver.repository.GameRepository;
import org.example.chessserver.repository.FriendshipRepository;
import org.example.chessserver.entity.Friendship;
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
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
public class ChessWebSocketHandler extends TextWebSocketHandler {

    private final Map<Integer, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final GameRepository gameRepository;
    private final UserRepository userRepository;
    private final FriendshipRepository friendshipRepository;
    private final EloRatingRepository eloRatingRepository;
    private final ChessGameService gameService;
    private final GameRedisService gameRedisService;
    private final StringRedisTemplate redisTemplate;
    private final JwtUtil jwtUtil;
    private final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ChessWebSocketHandler.class);

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        try {
            String token = extractToken(session);
            if (token == null) {
                session.close(CloseStatus.BAD_DATA.withReason("Missing token"));
                return;
            }
            int userId = jwtUtil.getClaims(token).get("userId", Integer.class);
            sessions.put(userId, session);
            broadcastPresence(userId, true);
            handleReconnection(userId);
        } catch (io.jsonwebtoken.ExpiredJwtException e) {
            session.close(CloseStatus.POLICY_VIOLATION.withReason("TOKEN_EXPIRED"));
        } catch (Exception e) {
            session.close(CloseStatus.BAD_DATA.withReason("AUTH_FAILED"));
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
            case "CREATE_ROOM" -> handleCreateRoom(json, userId);
            case "JOIN_ROOM" -> handleJoinRoom(json.getString("code"), userId);
            case "CHAT_MESSAGE" -> handleChatMessage(json, userId);
            case "REMATCH_OFFER" -> handleRematchOffer(json.getString("gameId"), userId);
            case "REMATCH_RESPONSE" -> handleRematchResponse(json, userId);
            case "INVITE_FRIEND" -> handleInviteFriend(json, userId);
            case "ACCEPT_INVITE" -> handleAcceptInvite(json.getInt("hostId"), userId);
        }
    }

    private void handleCreateRoom(JSONObject json, int userId) throws Exception {
        String matchType = json.optString("matchType", "rapid");
        String code = UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        gameRedisService.saveRoomCode(code, userId + ":" + matchType);
        sendToUser(userId, new JSONObject().put("type", "ROOM_CREATED").put("code", code).toString());
    }

    private void handleJoinRoom(String code, int guestId) throws Exception {
        String hostData = gameRedisService.getHostByRoomCode(code);
        if (hostData == null) {
            sendToUser(guestId, new JSONObject().put("type", "ERROR").put("message", "Invalid room code").toString());
            return;
        }
        String[] parts = hostData.split(":");
        int hostId = Integer.parseInt(parts[0]);
        String matchType = parts.length > 1 ? parts[1] : "rapid";
        
        if (hostId == guestId) return;

        gameRedisService.deleteRoomCode(code);
        String gameId = UUID.randomUUID().toString();
        startGameDirectly(gameId, hostId, guestId, matchType);
    }

    private void handleInviteFriend(JSONObject json, int userId) throws Exception {
        int friendId = json.getInt("friendId");
        String matchType = json.optString("matchType", "rapid");
        if (!isUserOnline(friendId)) {
            sendToUser(userId, new JSONObject().put("type", "ERROR").put("message", "Friend is not online").toString());
            return;
        }
        User u = userRepository.findById(userId).orElse(null);
        String username = u != null ? u.getUsername() : "Player";
        
        // Save invite state in Redis
        redisTemplate.opsForValue().set("invite:" + userId + ":" + friendId, "PENDING:" + matchType, Duration.ofMinutes(5));
        
        sendToUser(friendId, new JSONObject()
            .put("type", "MATCH_INVITE")
            .put("hostId", userId)
            .put("hostName", username).toString());
    }

    private void handleAcceptInvite(int hostId, int guestId) throws Exception {
        String key = "invite:" + hostId + ":" + guestId;
        String status = redisTemplate.opsForValue().get(key);
        if (status != null) {
            String[] parts = status.split(":");
            String matchType = parts.length > 1 ? parts[1] : "rapid";
            redisTemplate.delete(key);
            String gameId = UUID.randomUUID().toString();
            startGameDirectly(gameId, hostId, guestId, matchType);
        } else {
            sendToUser(guestId, new JSONObject().put("type", "ERROR").put("message", "Invite expired or invalid").toString());
        }
    }

    private void startGameDirectly(String gameId, int u1, int u2, String matchType) {
        long time = switch(matchType) {
            case "bullet" -> 60;
            case "blitz" -> 180;
            case "classical" -> 1800;
            default -> 600;
        };
        
        String gameKey = "chess:game:" + gameId;
        Map<String, String> data = new HashMap<>();
        data.put("white", String.valueOf(u1));
        data.put("black", String.valueOf(u2));
        data.put("type", matchType);
        data.put("fen", "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1");
        redisTemplate.opsForHash().putAll(gameKey, data);
        redisTemplate.opsForValue().set("user:current_game:" + u1, gameId, Duration.ofSeconds(7200));
        redisTemplate.opsForValue().set("user:current_game:" + u2, gameId, Duration.ofSeconds(7200));
        redisTemplate.expire(gameKey, Duration.ofSeconds(7200));
        gameRedisService.initializeTimers(gameId, time);
        broadcastStart(gameId, u1, u2);
    }

    private void handleChatMessage(JSONObject json, int userId) throws Exception {
        String gameId = json.getString("gameId");
        String text = json.getString("text");
        Map<String, String> data = gameRedisService.getGameData(gameId);
        if (data == null || data.isEmpty()) return;

        User u = userRepository.findById(userId).orElse(null);
        String username = u != null ? u.getUsername() : "Player";
        
        JSONObject msg = new JSONObject()
            .put("type", "CHAT_MESSAGE")
            .put("senderId", userId)
            .put("senderName", username)
            .put("text", text);
        
        gameRedisService.saveChatMessage(gameId, msg.toString());

        int oppId = (userId == Integer.parseInt(data.get("white"))) ? Integer.parseInt(data.get("black")) : Integer.parseInt(data.get("white"));
        sendToUser(oppId, msg.toString());
    }

    private void handleRematchOffer(String gameId, int userId) throws Exception {
        Map<String, String> data = gameRedisService.getGameData(gameId);
        int w = Integer.parseInt(data.get("white"));
        int b = Integer.parseInt(data.get("black"));
        int oppId = (userId == w) ? b : w;

        int r1 = eloRatingRepository.findById(w).map(EloRating::getRating).orElse(1200);
        int r2 = eloRatingRepository.findById(b).map(EloRating::getRating).orElse(1200);
        if (Math.abs(r1 - r2) > 200) {
            sendToUser(userId, new JSONObject().put("type", "ERROR").put("message", "Elo difference too high for rematch").toString());
            return;
        }

        redisTemplate.opsForValue().set("rematch:" + gameId + ":" + oppId, String.valueOf(userId), Duration.ofSeconds(30));
        sendToUser(oppId, new JSONObject().put("type", "REMATCH_OFFERED").put("gameId", gameId).toString());
    }

    private void handleRematchResponse(JSONObject json, int userId) throws Exception {
        String gameId = json.getString("gameId");
        boolean accepted = json.getBoolean("accepted");
        String key = "rematch:" + gameId + ":" + userId;
        String requesterIdStr = redisTemplate.opsForValue().get(key);

        if (requesterIdStr != null) {
            int requesterId = Integer.parseInt(requesterIdStr);
            redisTemplate.delete(key);
            if (accepted) {
                Map<String, String> data = gameRedisService.getGameData(gameId);
                int oldWhite = Integer.parseInt(data.get("white"));
                int oldBlack = Integer.parseInt(data.get("black"));
                String matchType = data.getOrDefault("type", "rapid");
                gameRedisService.cleanGame(gameId, oldWhite, oldBlack);
                String newGameId = UUID.randomUUID().toString();
                startGameDirectly(newGameId, oldBlack, oldWhite, matchType); // switch colors
            } else {
                sendToUser(requesterId, new JSONObject().put("type", "REMATCH_REJECTED").toString());
            }
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
                sendToUser(opponentId, new JSONObject().put("type", "MATCH_CANCELLED").put("reason", "Opponent rejected the match").toString());
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
                    String[] parts = key.split(":");
                    String matchType = parts.length > 5 ? parts[5] : "rapid";
                    startGameDirectly(gameId, Integer.parseInt(parts[3]), Integer.parseInt(parts[4]), matchType);
                    redisTemplate.delete(key);
                }
            }
            redisTemplate.delete(readyKey);
        }
    }

    private void handleMove(JSONObject json, int userId) throws Exception {
        String gameId = json.getString("gameId");
        String moveStr = json.getString("move");

        Map<String, String> data = gameRedisService.getGameData(gameId);
        if (data == null || data.isEmpty()) return;

        long now = System.currentTimeMillis();
        long lastTime = Long.parseLong(data.getOrDefault("last_move_time", String.valueOf(now)));
        long elapsed = (now - lastTime) / 1000;
        
        boolean isWhite = (userId == Integer.parseInt(data.get("white")));
        long timeRemaining = Long.parseLong(data.get(isWhite ? "time_white" : "time_black")) - elapsed;
        
        if (timeRemaining <= 0) {
            handleEndGame(gameId, userId, "TIMEOUT");
            return;
        }

        Board board = gameService.loadBoard(data.get("fen"));
        String newFen = gameService.handleMoveLogic(board, moveStr);

        if (newFen == null) {
            sendToUser(userId, new JSONObject().put("type", "ERROR").put("message", "Invalid move").toString());
            return;
        }

        redisTemplate.opsForHash().put("chess:game:" + gameId, isWhite ? "time_white" : "time_black", String.valueOf(timeRemaining));
        redisTemplate.opsForHash().put("chess:game:" + gameId, "last_move_time", String.valueOf(now));
        gameRedisService.updateGameState(gameId, newFen, moveStr);

        int opponentId = isWhite ? Integer.parseInt(data.get("black")) : Integer.parseInt(data.get("white"));
        sendToUser(opponentId, new JSONObject()
                .put("type", "OPPONENT_MOVE")
                .put("move", moveStr)
                .put("fen", newFen)
                .put("timeRemaining", timeRemaining).toString());

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
                List<String> chat = gameRedisService.getChatHistory(gameId);
                
                long now = System.currentTimeMillis();
                long lastTime = Long.parseLong(data.getOrDefault("last_move_time", String.valueOf(now)));
                long timeWhite = Long.parseLong(data.get("time_white"));
                long timeBlack = Long.parseLong(data.get("time_black"));
                
                JSONObject msg = new JSONObject()
                        .put("type", "RECONNECT_GAME")
                        .put("gameId", gameId)
                        .put("fen", data.get("fen"))
                        .put("side", Integer.parseInt(data.get("white")) == userId ? "WHITE" : "BLACK")
                        .put("history", history)
                        .put("chatHistory", chat)
                        .put("timeWhite", timeWhite)
                        .put("timeBlack", timeBlack)
                        .put("opponentId", opponentId)
                        .put("opponentName", opp != null ? opp.getUsername() : "Opponent #" + opponentId)
                        .put("opponentRating", 1200); 
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
        } else if (reason.equals("TIMEOUT")) {
            result = (userId == w) ? "0-1" : "1-0";
        } else if (reason.equals("CHECKMATE")) {
            result = (userId == w) ? "1-0" : "0-1"; // the user passing this usually made the checkmate move, so they win? No wait, handleMove passes it.
            // If the person who just moved caused checkmate, they win.
            result = (userId == w) ? "1-0" : "0-1";
        }

        gameRepository.processGameEnd(w, b, result, pgn);

        JSONObject endMsg = new JSONObject().put("type", "GAME_OVER").put("result", result).put("reason", reason);
        sendToUser(w, endMsg.toString());
        sendToUser(b, endMsg.toString());
        
        // We do NOT call cleanGame here yet because they might want to rematch.
        // We wait for rematch handling or let redis expire the keys.
        // We will remove current_game so they can start new games.
        redisTemplate.delete("user:current_game:" + w);
        redisTemplate.delete("user:current_game:" + b);
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
        int userId = getUserIdBySession(session);
        sessions.values().remove(session);
        if (userId != -1) {
            broadcastPresence(userId, false);
        }
    }

    private void broadcastPresence(int userId, boolean isOnline) {
        List<Friendship> friends = friendshipRepository.findAcceptedFriendships(userId);
        JSONObject msg = new JSONObject()
            .put("type", isOnline ? "USER_ONLINE" : "USER_OFFLINE")
            .put("userId", userId);
            
        for (Friendship f : friends) {
            int friendId = (f.getUser1().getUserId() == userId) ? f.getUser2().getUserId() : f.getUser1().getUserId();
            if (isUserOnline(friendId)) {
                try {
                    sendToUser(friendId, msg.toString());
                } catch (Exception e) {
                    log.error("Failed to broadcast presence", e);
                }
            }
        }
    }

    private String extractToken(WebSocketSession session) {
        String query = session.getUri().getQuery();
        if (query == null) return null;
        for (String param : query.split("&")) {
            if (param.startsWith("token=")) {
                return param.substring(6);
            }
        }
        return null;
    }
}
