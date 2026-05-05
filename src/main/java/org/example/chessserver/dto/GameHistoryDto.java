package org.example.chessserver.dto;

import lombok.Builder;
import lombok.Data;
import java.time.ZonedDateTime;

@Data
@Builder
public class GameHistoryDto {
    private Integer gameId;
    private Integer opponentId;
    private String opponentName;
    private String myColor; // "WHITE" or "BLACK"
    private String result;
    private String pgn;
    private ZonedDateTime playedAt;
}
