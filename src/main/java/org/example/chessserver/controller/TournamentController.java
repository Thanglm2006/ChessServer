package org.example.chessserver.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.chessserver.dto.TournamentDto;
import org.example.chessserver.dto.TournamentParticipantDto;
import org.example.chessserver.dto.TournamentPairingDto;
import org.example.chessserver.dto.TournamentRoundDto;
import org.example.chessserver.security.JwtUtil;
import org.example.chessserver.service.TournamentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/tournaments")
@RequiredArgsConstructor
public class TournamentController {
    private final TournamentService tournamentService;
    private final JwtUtil jwtUtil;

    private int getUserIdFromRequest(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7);
            return jwtUtil.getClaims(token).get("userId", Integer.class);
        }
        throw new RuntimeException("Authorization token is missing or invalid");
    }

    @GetMapping
    public ResponseEntity<List<TournamentDto>> getAllTournaments() {
        return ResponseEntity.ok(tournamentService.getAllTournaments());
    }

    @GetMapping("/{tournamentId}")
    public ResponseEntity<TournamentDto> getTournamentById(@PathVariable Integer tournamentId) {
        return ResponseEntity.ok(tournamentService.getTournamentById(tournamentId));
    }

    @PostMapping("/{tournamentId}/join")
    public ResponseEntity<?> joinTournament(
            HttpServletRequest request,
            @PathVariable Integer tournamentId) {
        int userId = getUserIdFromRequest(request);
        tournamentService.joinTournament(tournamentId, userId);
        return ResponseEntity.ok(Map.of("message", "Registered for tournament successfully"));
    }

    @PostMapping("/{tournamentId}/leave")
    public ResponseEntity<?> leaveTournament(
            HttpServletRequest request,
            @PathVariable Integer tournamentId) {
        int userId = getUserIdFromRequest(request);
        tournamentService.leaveTournament(tournamentId, userId);
        return ResponseEntity.ok(Map.of("message", "Left tournament successfully"));
    }

    @GetMapping("/{tournamentId}/standings")
    public ResponseEntity<List<TournamentParticipantDto>> getStandings(@PathVariable Integer tournamentId) {
        return ResponseEntity.ok(tournamentService.getStandings(tournamentId));
    }
}
