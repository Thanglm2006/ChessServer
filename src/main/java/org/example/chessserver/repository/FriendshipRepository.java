package org.example.chessserver.repository;

import org.example.chessserver.entity.Friendship;
import org.example.chessserver.entity.FriendshipId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FriendshipRepository extends JpaRepository<Friendship, FriendshipId> {
    @Query("SELECT f FROM Friendship f WHERE (f.user1.userId = :userId OR f.user2.userId = :userId) AND f.status = 'ACCEPTED'")
    List<Friendship> findAcceptedFriendships(@Param("userId") Integer userId);

    @Query("SELECT f FROM Friendship f WHERE f.user2.userId = :userId AND f.status = 'PENDING'")
    List<Friendship> findPendingRequests(@Param("userId") Integer userId);
    
    @Query("SELECT f FROM Friendship f WHERE (f.user1.userId = :u1 AND f.user2.userId = :u2) OR (f.user1.userId = :u2 AND f.user2.userId = :u1)")
    Friendship findFriendshipBetween(@Param("u1") Integer u1, @Param("u2") Integer u2);
}
