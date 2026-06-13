package org.example.chessserver.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "tournament_pairings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TournamentPairing {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pairing_id")
    private Integer pairingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "round_id", nullable = false)
    private TournamentRound round;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "white_player_id")
    private User whitePlayer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "black_player_id")
    private User blackPlayer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id")
    private Game game;

    @Column(name = "result", length = 10)
    private String result; // "1-0", "0-1", "1/2-1/2", etc.

    @Column(name = "is_bye")
    private Boolean isBye;

    @Column(name = "white_ready")
    private Boolean whiteReady;

    @Column(name = "black_ready")
    private Boolean blackReady;

    @Column(name = "lobby_started_at")
    private java.time.ZonedDateTime lobbyStartedAt;
}
