package org.example.chessserver.dto;

import lombok.*;
import java.time.ZonedDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserAdminDto {
    private Integer userId;
    private String username;
    private String email;
    private String role;
    private Boolean isBanned;
    private String countryCode;
    private Integer rating;
    private ZonedDateTime createdAt;
}
