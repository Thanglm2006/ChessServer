package org.example.chessserver.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "tournaments")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tournament {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tournament_id")
    private Integer tournamentId;

    @Column(name = "tournament_name", nullable = false)
    private String tournamentName;

    @Column(name = "description")
    private String description;

    @Column(name = "total_rounds", nullable = false)
    private Integer totalRounds;

    @Column(name = "time_control", nullable = false, length = 50)
    private String timeControl;

    @Column(name = "registration_start")
    private ZonedDateTime registrationStart;

    @Column(name = "registration_end")
    private ZonedDateTime registrationEnd;

    @Column(name = "start_time")
    private ZonedDateTime startTime;

    @Column(name = "status", length = 20)
    private String status; // REGISTERING, ONGOING, FINISHED

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private User createdBy;

    @Column(name = "created_at", insertable = false, updatable = false)
    private ZonedDateTime createdAt;
}
