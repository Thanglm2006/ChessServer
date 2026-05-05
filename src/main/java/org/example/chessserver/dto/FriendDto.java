package org.example.chessserver.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FriendDto {
    private Integer userId;
    private String username;
    private String status; // "ONLINE" or "OFFLINE"
    private Integer rating;
}
