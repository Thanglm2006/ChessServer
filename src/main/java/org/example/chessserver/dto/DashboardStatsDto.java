package org.example.chessserver.dto;

import lombok.*;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardStatsDto {
    private Long totalUsers;
    private Long totalGames;
    private Long totalTournaments;
    private Long onlinePlayersCount;
    private java.util.List<UserProfileDto> topPlayers;
}
