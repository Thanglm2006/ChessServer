package org.example.chessserver.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserTournamentDto {
    private Integer tournamentId;
    private String tournamentName;
    private String startTime;
    private String status;
    private Integer rank;
    private String medal; // "GOLD", "SILVER", "BRONZE", or null
    private Double score;
}
