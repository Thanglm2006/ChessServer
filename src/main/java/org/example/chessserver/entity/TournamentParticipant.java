package org.example.chessserver.entity;

import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.ZonedDateTime;

@Entity
@Table(name = "tournament_participants")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentParticipant {
    @EmbeddedId
    private TournamentParticipantId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("tournamentId")
    @JoinColumn(name = "tournament_id")
    private Tournament tournament;

    @ManyToOne(fetch = FetchType.LAZY)
    @MapsId("userId")
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "initial_rating")
    private Integer initialRating;

    @Column(name = "current_score", precision = 4, scale = 1)
    private BigDecimal currentScore;

    @Column(name = "buchholz", precision = 6, scale = 2)
    private BigDecimal buchholz;

    @Column(name = "sonneborn_berger", precision = 6, scale = 2)
    private BigDecimal sonnebornBerger;

    @Column(name = "bye_received")
    private Boolean byeReceived;

    @Column(name = "reminder_sent")
    private Boolean reminderSent;

    @Column(name = "joined_at", insertable = false, updatable = false)
    private ZonedDateTime joinedAt;
}
