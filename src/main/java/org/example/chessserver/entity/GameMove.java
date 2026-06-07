package org.example.chessserver.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "game_moves")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GameMove {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "move_id")
    private Integer moveId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "game_id", nullable = false)
    private Game game;

    @Column(name = "move_number", nullable = false)
    private Integer moveNumber;

    @Column(name = "san_move", length = 20)
    private String sanMove;

    @Column(name = "fen_after_move", columnDefinition = "TEXT")
    private String fenAfterMove;

    @Column(name = "evaluation")
    private Double evaluation;

    @Column(name = "created_at", insertable = false, updatable = false)
    private ZonedDateTime createdAt;
}
