package org.example.chessserver.dto;

import lombok.Data;

@Data
public class AiMoveRequest {
    private String gameId;
    private String move; // UCI or SAN move notation
}
