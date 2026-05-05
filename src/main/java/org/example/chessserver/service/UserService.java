package org.example.chessserver.service;

import lombok.RequiredArgsConstructor;
import org.example.chessserver.dto.UserProfileDto;
import org.example.chessserver.entity.EloRating;
import org.example.chessserver.entity.User;
import org.example.chessserver.repository.EloRatingRepository;
import org.example.chessserver.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final EloRatingRepository eloRatingRepository;

    public UserProfileDto getUserProfile(int userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User with ID " + userId + " not found"));
        EloRating elo = eloRatingRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("Elo rating for user " + userId + " not found"));

        return UserProfileDto.builder()
                .userId(user.getUserId())
                .username(user.getUsername())
                .countryCode(user.getCountryCode())
                .rating(elo.getRating())
                .gamesPlayed(elo.getGamesPlayed())
                .wins(elo.getWins())
                .losses(elo.getLosses())
                .draws(elo.getDraws())
                .build();
    }
}
