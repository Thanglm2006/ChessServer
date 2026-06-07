package org.example.chessserver.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.example.chessserver.dto.DashboardStatsDto;
import org.example.chessserver.dto.TournamentDto;
import org.example.chessserver.dto.TournamentRequest;
import org.example.chessserver.dto.UserAdminDto;
import org.example.chessserver.entity.User;
import org.example.chessserver.repository.UserRepository;
import org.example.chessserver.security.JwtUtil;
import org.example.chessserver.service.AdminService;
import org.example.chessserver.service.TournamentService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {
    private final AdminService adminService;
    private final TournamentService tournamentService;
    private final UserRepository userRepository;
    private final JwtUtil jwtUtil;

    private int verifyAdmin(HttpServletRequest request) {
        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            throw new AccessDeniedException("Unauthorized");
        }
        String token = authHeader.substring(7);
        int userId = jwtUtil.getClaims(token).get("userId", Integer.class);
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new AccessDeniedException("User not found"));
        if (!"ROLE_ADMIN".equals(user.getRole())) {
            throw new AccessDeniedException("Admin access required");
        }
        return userId;
    }

    @GetMapping("/stats")
    public ResponseEntity<DashboardStatsDto> getStats(HttpServletRequest request) {
        verifyAdmin(request);
        return ResponseEntity.ok(adminService.getDashboardStats());
    }

    @GetMapping("/users")
    public ResponseEntity<List<UserAdminDto>> getUsers(
            HttpServletRequest request,
            @RequestParam(required = false) String query) {
        verifyAdmin(request);
        return ResponseEntity.ok(adminService.getUsers(query));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<UserAdminDto> getUserProfile(
            HttpServletRequest request,
            @PathVariable int userId) {
        verifyAdmin(request);
        return ResponseEntity.ok(adminService.getUserProfile(userId));
    }

    @PostMapping("/users/{userId}/ban")
    public ResponseEntity<?> banUser(HttpServletRequest request, @PathVariable int userId) {
        verifyAdmin(request);
        adminService.banUser(userId);
        return ResponseEntity.ok(Map.of("message", "User banned successfully"));
    }

    @PostMapping("/users/{userId}/unban")
    public ResponseEntity<?> unbanUser(HttpServletRequest request, @PathVariable int userId) {
        verifyAdmin(request);
        adminService.unbanUser(userId);
        return ResponseEntity.ok(Map.of("message", "User unbanned successfully"));
    }

    @PostMapping("/tournaments")
    public ResponseEntity<TournamentDto> createTournament(
            HttpServletRequest request,
            @RequestBody TournamentRequest tournamentRequest) {
        int adminId = verifyAdmin(request);
        TournamentDto created = tournamentService.createTournament(tournamentRequest, adminId);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/tournaments/{tournamentId}")
    public ResponseEntity<TournamentDto> updateTournament(
            HttpServletRequest request,
            @PathVariable Integer tournamentId,
            @RequestBody TournamentRequest tournamentRequest) {
        verifyAdmin(request);
        TournamentDto updated = tournamentService.updateTournament(tournamentId, tournamentRequest);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/tournaments/{tournamentId}")
    public ResponseEntity<?> cancelTournament(
            HttpServletRequest request,
            @PathVariable Integer tournamentId) {
        verifyAdmin(request);
        tournamentService.cancelTournament(tournamentId);
        return ResponseEntity.ok(Map.of("message", "Tournament cancelled successfully"));
    }

    @PostMapping("/tournaments/{tournamentId}/start")
    public ResponseEntity<?> startTournament(
            HttpServletRequest request,
            @PathVariable Integer tournamentId) {
        verifyAdmin(request);
        tournamentService.startTournament(tournamentId);
        return ResponseEntity.ok(Map.of("message", "Tournament started successfully"));
    }

    @PostMapping("/tournaments/{tournamentId}/finish")
    public ResponseEntity<?> finishTournament(
            HttpServletRequest request,
            @PathVariable Integer tournamentId) {
        verifyAdmin(request);
        tournamentService.finishTournament(tournamentId);
        return ResponseEntity.ok(Map.of("message", "Tournament finished successfully"));
    }

    @PostMapping("/pairings/{pairingId}/result")
    public ResponseEntity<?> submitPairingResult(
            HttpServletRequest request,
            @PathVariable Integer pairingId,
            @RequestBody Map<String, String> body) {
        verifyAdmin(request);
        String result = body.get("result");
        tournamentService.submitPairingResult(pairingId, result);
        return ResponseEntity.ok(Map.of("message", "Result submitted successfully"));
    }

    @PostMapping("/pairings/{pairingId}/complete-game")
    public ResponseEntity<?> submitPairingGame(
            HttpServletRequest request,
            @PathVariable Integer pairingId,
            @RequestBody Map<String, Object> body) {
        verifyAdmin(request);
        tournamentService.submitPairingGame(pairingId, body);
        return ResponseEntity.ok(Map.of("message", "Simulated game submitted successfully"));
    }
}
