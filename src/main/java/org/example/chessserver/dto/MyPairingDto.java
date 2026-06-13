package org.example.chessserver.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyPairingDto {
    private Integer pairingId;
    private Integer roundNumber;
    private String opponentName;
    private Integer opponentRating;
    private String myColor;
    private Boolean isBye;
    private Integer lobbyTimeLimitSeconds;
    private Long lobbyTimeLeftSeconds;
    private Boolean iAmReady;
    private Boolean opponentReady;
}
