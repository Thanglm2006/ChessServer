package org.example.chessserver.dto;

import lombok.*;
import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameMoveDto {
    private Integer moveId;
    private Integer gameId;
    private Integer moveNumber;
    private String sanMove;
    private String fenAfterMove;
    private Double evaluation;
    private ZonedDateTime createdAt;
}
