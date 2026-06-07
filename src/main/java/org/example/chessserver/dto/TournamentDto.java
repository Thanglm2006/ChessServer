package org.example.chessserver.dto;

import lombok.*;
import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentDto {
    private Integer tournamentId;
    private String tournamentName;
    private String description;
    private Integer totalRounds;
    private String timeControl;
    private ZonedDateTime registrationStart;
    private ZonedDateTime registrationEnd;
    private ZonedDateTime startTime;
    private String status;
    private Integer createdById;
    private String createdByName;
}
