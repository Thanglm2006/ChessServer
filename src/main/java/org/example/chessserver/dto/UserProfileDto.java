package org.example.chessserver.dto;

import lombok.Builder;
import lombok.Data;
import java.util.List;

@Data
@Builder
public class UserProfileDto {
    private Integer userId;
    private String username;
    private String countryCode;
    private Integer rating;
    private Integer gamesPlayed;
    private Integer wins;
    private Integer losses;
    private Integer draws;
    private Double winRate;
    private Integer goldMedals;
    private Integer silverMedals;
    private Integer bronzeMedals;
    private List<UserTournamentDto> tournamentHistory;
}
