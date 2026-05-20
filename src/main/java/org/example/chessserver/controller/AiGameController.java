package org.example.chessserver.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.chessserver.dto.AiMoveRequest;
import org.example.chessserver.dto.AiResignRequest;
import org.example.chessserver.dto.AiStartRequest;
import org.example.chessserver.security.JwtUtil;
import org.example.chessserver.service.AiGameService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
@RequiredArgsConstructor
public class AiGameController {

    private final AiGameService aiGameService;
    private final JwtUtil jwtUtil;

    /**
     * Get available AI checkpoints/models for frontend selection.
     */
    @GetMapping("/models")
    public ResponseEntity<Map<String, Object>> getAvailableModels() {
        return ResponseEntity.ok(aiGameService.getAvailableModels());
    }

    /**
     * Start a new game against Chess AI.
     */
    @PostMapping("/start")
    public ResponseEntity<Map<String, Object>> startGame(
            HttpServletRequest request,
            @RequestBody AiStartRequest startRequest) {
        int userId = getUserIdFromRequest(request);
        Map<String, Object> game = aiGameService.startGame(
                userId,
                startRequest.getAiModel(),
                startRequest.getDifficulty() != null ? startRequest.getDifficulty() : 3,
                startRequest.getPlayerColor()
        );
        return ResponseEntity.ok(game);
    }

    /**
     * Send player move and receive AI response.
     */
    @PostMapping("/move")
    public ResponseEntity<Map<String, Object>> makeMove(
            HttpServletRequest request,
            @RequestBody AiMoveRequest moveRequest) {
        int userId = getUserIdFromRequest(request);
        Map<String, Object> response = aiGameService.makeMove(
                userId,
                moveRequest.getGameId(),
                moveRequest.getMove()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * Resign the active game.
     */
    @PostMapping("/resign")
    public ResponseEntity<Map<String, Object>> resignGame(
            HttpServletRequest request,
            @RequestBody AiResignRequest resignRequest) {
        int userId = getUserIdFromRequest(request);
        Map<String, Object> response = aiGameService.resignGame(userId, resignRequest.getGameId());
        return ResponseEntity.ok(response);
    }

    /**
     * Check and get any active AI game for the current logged-in user.
     * Helpful for page refreshing or game resuming.
     */
    @GetMapping("/active")
    public ResponseEntity<Map<String, Object>> checkActiveGame(HttpServletRequest request) {
        int userId = getUserIdFromRequest(request);
        Map<String, Object> activeGame = aiGameService.getActiveGame(userId);
        if (activeGame == null) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(activeGame);
    }

    /**
     * Retrieve current state details and move history of a specific game.
     */
    @GetMapping("/game/{gameId}")
    public ResponseEntity<Map<String, Object>> getGameDetails(
            HttpServletRequest request,
            @PathVariable String gameId) {
        int userId = getUserIdFromRequest(request);
        Map<String, Object> gameDetails = aiGameService.getGameDetails(userId, gameId);
        if (gameDetails == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(gameDetails);
    }

    /**
     * Helper to resolve userId from JWT Bearer token in the request header.
     */
    private int getUserIdFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtUtil.getClaims(token).get("userId", Integer.class);
        }
        throw new RuntimeException("Authorization token is missing or invalid");
    }
}
