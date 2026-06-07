package org.example.chessserver.dto;

import lombok.*;
import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentRoundDto {
    private Integer roundId;
    private Integer tournamentId;
    private Integer roundNumber;
    private ZonedDateTime startedAt;
    private ZonedDateTime endedAt;
}
