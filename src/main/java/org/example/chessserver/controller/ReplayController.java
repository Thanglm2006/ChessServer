package org.example.chessserver.controller;

import lombok.RequiredArgsConstructor;
import org.example.chessserver.dto.GameMoveDto;
import org.example.chessserver.dto.TournamentPairingDto;
import org.example.chessserver.dto.TournamentRoundDto;
import org.example.chessserver.entity.Game;
import org.example.chessserver.service.ReplayService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class ReplayController {
    private final ReplayService replayService;

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, String>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(Map.of("message", "Tournament replay is available only after the tournament has finished."));
    }

    @GetMapping("/api/tournaments/{tournamentId}/rounds")
    public ResponseEntity<List<TournamentRoundDto>> getRounds(@PathVariable Integer tournamentId) {
        return ResponseEntity.ok(replayService.getRounds(tournamentId));
    }

    @GetMapping("/api/tournaments/{tournamentId}/pairings/{roundId}")
    public ResponseEntity<List<TournamentPairingDto>> getPairings(
            @PathVariable Integer tournamentId,
            @PathVariable Integer roundId) {
        return ResponseEntity.ok(replayService.getPairings(tournamentId, roundId));
    }

    @GetMapping("/api/tournaments/{tournamentId}/games")
    public ResponseEntity<List<TournamentPairingDto>> getTournamentGames(@PathVariable Integer tournamentId) {
        return ResponseEntity.ok(replayService.getTournamentGames(tournamentId));
    }

    @GetMapping("/api/games/{gameId}")
    public ResponseEntity<Game> getGame(@PathVariable Integer gameId) {
        return ResponseEntity.ok(replayService.getGame(gameId));
    }

    @GetMapping("/api/games/{gameId}/moves")
    public ResponseEntity<List<GameMoveDto>> getGameMoves(@PathVariable Integer gameId) {
        return ResponseEntity.ok(replayService.getGameMoves(gameId));
    }

    @GetMapping("/api/games/{gameId}/analysis")
    public ResponseEntity<Map<String, Object>> getGameAnalysis(@PathVariable Integer gameId) {
        return ResponseEntity.ok(replayService.getGameAnalysis(gameId));
    }
}
