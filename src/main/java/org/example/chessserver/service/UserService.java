package org.example.chessserver.service;

import lombok.RequiredArgsConstructor;
import org.example.chessserver.dto.UserProfileDto;
import org.example.chessserver.dto.UserTournamentDto;
import org.example.chessserver.entity.EloRating;
import org.example.chessserver.entity.Tournament;
import org.example.chessserver.entity.TournamentParticipant;
import org.example.chessserver.entity.User;
import org.example.chessserver.repository.EloRatingRepository;
import org.example.chessserver.repository.TournamentParticipantRepository;
import org.example.chessserver.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.ArrayList;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final EloRatingRepository eloRatingRepository;
    private final TournamentParticipantRepository tournamentParticipantRepository;

    public UserProfileDto getUserProfile(int userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User with ID " + userId + " not found"));
        EloRating elo = eloRatingRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Elo rating for user " + userId + " not found"));

        double winRate = 0.0;
        if (elo.getGamesPlayed() > 0) {
            winRate = Math.round((double) elo.getWins() / elo.getGamesPlayed() * 1000.0) / 10.0;
        }

        List<TournamentParticipant> participationList = tournamentParticipantRepository.findByUserId(userId);
        List<UserTournamentDto> tournamentHistory = new ArrayList<>();
        int gold = 0;
        int silver = 0;
        int bronze = 0;

        for (TournamentParticipant p : participationList) {
            Tournament t = p.getTournament();
            List<TournamentParticipant> standings = tournamentParticipantRepository.findStandings(t.getTournamentId());
            int rank = 0;
            for (int i = 0; i < standings.size(); i++) {
                if (standings.get(i).getUser().getUserId() == userId) {
                    rank = i + 1;
                    break;
                }
            }

            String medal = null;
            if ("FINISHED".equals(t.getStatus())) {
                if (rank == 1) {
                    medal = "GOLD";
                    gold++;
                } else if (rank == 2) {
                    medal = "SILVER";
                    silver++;
                } else if (rank == 3) {
                    medal = "BRONZE";
                    bronze++;
                }
            }

            tournamentHistory.add(UserTournamentDto.builder()
                    .tournamentId(t.getTournamentId())
                    .tournamentName(t.getTournamentName())
                    .startTime(t.getStartTime() != null ? t.getStartTime().toString() : null)
                    .status(t.getStatus())
                    .rank(rank)
                    .medal(medal)
                    .score(p.getCurrentScore() != null ? p.getCurrentScore().doubleValue() : 0.0)
                    .build());
        }

        return UserProfileDto.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .countryCode(user.getCountryCode())
                .rating(elo.getRating())
                .gamesPlayed(elo.getGamesPlayed())
                .wins(elo.getWins())
                .losses(elo.getLosses())
                .draws(elo.getDraws())
                .winRate(winRate)
                .goldMedals(gold)
                .silverMedals(silver)
                .bronzeMedals(bronze)
                .tournamentHistory(tournamentHistory)
                .build();
    }

    public List<org.example.chessserver.dto.LeaderboardDto> getLeaderboard() {
        return eloRatingRepository.findAllByOrderByRatingDesc().stream()
                .map(elo -> org.example.chessserver.dto.LeaderboardDto.builder()
                        .userId(elo.getUserId())
                        .username(elo.getUser().getUsername())
                        .rating(elo.getRating())
                        .gamesPlayed(elo.getGamesPlayed())
                        .wins(elo.getWins())
                        .losses(elo.getLosses())
                        .draws(elo.getDraws())
                        .countryCode(elo.getUser().getCountryCode())
                        .build())
                .collect(Collectors.toList());
    }
}
