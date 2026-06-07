package org.example.chessserver.dto;

import lombok.*;
import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TournamentRequest {
    private String tournamentName;
    private String description;
    private Integer totalRounds;
    private String timeControl;
    private ZonedDateTime registrationStart;
    private ZonedDateTime registrationEnd;
    private ZonedDateTime startTime;
}
