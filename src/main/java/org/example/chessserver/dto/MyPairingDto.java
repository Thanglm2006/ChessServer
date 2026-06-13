package org.example.chessserver.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    private String result;
    private String gameId;
    private Boolean inBreak;
    private Long breakTimeLeftSeconds;

    @JsonProperty("iAmReady")
    private Boolean iAmReady;

    @JsonProperty("opponentReady")
    private Boolean opponentReady;
}
