package org.example.chessserver.controller;

import lombok.RequiredArgsConstructor;
import org.example.chessserver.dto.GameHistoryDto;
import org.example.chessserver.service.GameHistoryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/game")
@RequiredArgsConstructor
public class GameHistoryController {
    private final GameHistoryService gameHistoryService;

    @GetMapping("/history")
    public ResponseEntity<List<GameHistoryDto>> getHistory(
            @RequestParam int userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(gameHistoryService.getHistoryForUser(userId, page, size));
    }

    @GetMapping("/active")
    public ResponseEntity<String> checkActiveGame(@RequestParam int userId) {
        String gameId = gameHistoryService.getActiveGameId(userId);
        return gameId != null ? ResponseEntity.ok(gameId) : ResponseEntity.noContent().build();
    }
}
