package org.example.chessserver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LeaderboardDto {
    private int userId;
    private String username;
    private int rating;
    private int gamesPlayed;
    private int wins;
    private int losses;
    private int draws;
    private String countryCode;
}
