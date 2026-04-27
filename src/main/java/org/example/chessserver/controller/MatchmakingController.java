package org.example.chessserver.controller;

import lombok.RequiredArgsConstructor;
import org.example.chessserver.service.MatchmakingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/matchmaking")
@RequiredArgsConstructor
public class MatchmakingController {

    private final MatchmakingService matchmakingService;

    @PostMapping("/join")
    public ResponseEntity<?> joinQueue(@RequestParam int userId) {
        matchmakingService.joinQueue(userId);
        return ResponseEntity.ok("Joined queue");
    }
}
