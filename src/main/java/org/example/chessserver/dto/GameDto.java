package org.example.chessserver.dto;

import lombok.*;
import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameDto {
    private Integer gameId;
    private PlayerDto whitePlayer;
    private PlayerDto blackPlayer;
    private String result;
    private String pgnData;
    private ZonedDateTime playedAt;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class PlayerDto {
        private Integer userId;
        private String username;
    }
}
