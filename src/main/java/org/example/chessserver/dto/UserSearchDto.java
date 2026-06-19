package org.example.chessserver.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class UserSearchDto {
    private Integer userId;
    private String username;
    private Integer rating;
    private String friendshipStatus; // "NONE", "PENDING_SENT", "PENDING_RECEIVED", "ACCEPTED"
}
