package org.example.chessserver.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.ZonedDateTime;

@Entity
@Table(name = "elo_ratings")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EloRating {
    @Id
    @Column(name = "user_id")
    private Integer userId;

    @OneToOne
    @MapsId
    @JoinColumn(name = "user_id")
    private User user;

    private Integer rating;

    @Column(name = "games_played")
    private Integer gamesPlayed;

    private Integer wins;
    private Integer losses;
    private Integer draws;

    @Column(name = "last_updated")
    private ZonedDateTime lastUpdated;
}
