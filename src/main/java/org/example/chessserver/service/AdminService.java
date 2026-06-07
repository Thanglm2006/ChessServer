package org.example.chessserver.service;

import lombok.RequiredArgsConstructor;
import org.example.chessserver.dto.DashboardStatsDto;
import org.example.chessserver.dto.UserAdminDto;
import org.example.chessserver.dto.UserProfileDto;
import org.example.chessserver.entity.User;
import org.example.chessserver.entity.EloRating;
import org.example.chessserver.repository.EloRatingRepository;
import org.example.chessserver.repository.GameRepository;
import org.example.chessserver.repository.TournamentRepository;
import org.example.chessserver.repository.UserRepository;
import org.example.chessserver.websocket.ChessWebSocketHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {
    private final UserRepository userRepository;
    private final GameRepository gameRepository;
    private final TournamentRepository tournamentRepository;
    private final EloRatingRepository eloRatingRepository;
    private final ChessWebSocketHandler webSocketHandler;

    public DashboardStatsDto getDashboardStats() {
        long totalUsers = userRepository.count();
        long totalGames = gameRepository.count();
        long totalTournaments = tournamentRepository.count();
        
        long onlinePlayersCount = userRepository.findAll().stream()
                .filter(u -> webSocketHandler.isUserOnline(u.getUserId()))
                .count();

        List<UserProfileDto> topPlayers = eloRatingRepository.findAll().stream()
                .sorted((a, b) -> b.getRating().compareTo(a.getRating()))
                .limit(10)
                .map(elo -> {
                    User user = userRepository.findById(elo.getUserId()).orElse(null);
                    return UserProfileDto.builder()
                            .userId(elo.getUserId())
                            .username(user != null ? user.getUsername() : "Unknown")
                            .countryCode(user != null ? user.getCountryCode() : "??")
                            .rating(elo.getRating())
                            .gamesPlayed(elo.getGamesPlayed())
                            .wins(elo.getWins())
                            .losses(elo.getLosses())
                            .draws(elo.getDraws())
                            .build();
                })
                .collect(Collectors.toList());

        return DashboardStatsDto.builder()
                .totalUsers(totalUsers)
                .totalGames(totalGames)
                .totalTournaments(totalTournaments)
                .onlinePlayersCount(onlinePlayersCount)
                .topPlayers(topPlayers)
                .build();
    }

    public List<UserAdminDto> getUsers(String query) {
        List<User> users;
        if (query != null && !query.trim().isEmpty()) {
            users = userRepository.findAll().stream()
                    .filter(u -> u.getUsername().toLowerCase().contains(query.toLowerCase()) || 
                                 u.getEmail().toLowerCase().contains(query.toLowerCase()))
                    .collect(Collectors.toList());
        } else {
            users = userRepository.findAll();
        }

        return users.stream().map(u -> {
            Integer rating = eloRatingRepository.findById(u.getUserId()).map(EloRating::getRating).orElse(1200);
            return UserAdminDto.builder()
                    .userId(u.getUserId())
                    .username(u.getUsername())
                    .email(u.getEmail())
                    .role(u.getRole())
                    .isBanned(u.getIsBanned())
                    .countryCode(u.getCountryCode())
                    .rating(rating)
                    .createdAt(u.getCreatedAt())
                    .build();
        }).collect(Collectors.toList());
    }

    public UserAdminDto getUserProfile(int userId) {
        User u = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        Integer rating = eloRatingRepository.findById(u.getUserId()).map(EloRating::getRating).orElse(1200);
        return UserAdminDto.builder()
                .userId(u.getUserId())
                .username(u.getUsername())
                .email(u.getEmail())
                .role(u.getRole())
                .isBanned(u.getIsBanned())
                .countryCode(u.getCountryCode())
                .rating(rating)
                .createdAt(u.getCreatedAt())
                .build();
    }

    @Transactional
    public void banUser(int userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setIsBanned(true);
        userRepository.save(user);
    }

    @Transactional
    public void unbanUser(int userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));
        user.setIsBanned(false);
        userRepository.save(user);
    }
}
