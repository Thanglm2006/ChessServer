package org.example.chessserver.dto;

import lombok.Data;

@Data
public class AiStartRequest {
    private String aiModel;
    private Integer difficulty;
    private String playerColor; // "WHITE" or "BLACK"
}
