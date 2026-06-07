package org.example.chessserver.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.*;
import java.io.Serializable;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentParticipantId implements Serializable {
    @Column(name = "tournament_id")
    private Integer tournamentId;

    @Column(name = "user_id")
    private Integer userId;
}
