package org.example.chessserver.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import java.time.ZonedDateTime;

@Entity
@Table(name = "friendships")
@IdClass(FriendshipId.class)
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Friendship {

    @Id
    @ManyToOne
    @JoinColumn(name = "user_id_1")
    private User user1;

    @Id
    @ManyToOne
    @JoinColumn(name = "user_id_2")
    private User user2;

    @Column(name = "status")
    private String status = "PENDING"; // "PENDING", "ACCEPTED"

    @Column(name = "created_at", insertable = false, updatable = false)
    private ZonedDateTime createdAt;
}
