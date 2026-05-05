package org.example.chessserver.repository;

import org.example.chessserver.entity.Game;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface GameRepository extends JpaRepository<Game, Integer> {
    
    @Query(value = "SELECT process_game_end(:whiteId, :blackId, :result, :pgn)", nativeQuery = true)
    void processGameEnd(@Param("whiteId") Integer whiteId, 
                        @Param("blackId") Integer blackId, 
                        @Param("result") String result, 
                        @Param("pgn") String pgn);

    @Query("SELECT g FROM Game g WHERE g.whitePlayer.userId = :userId OR g.blackPlayer.userId = :userId ORDER BY g.playedAt DESC")
    Page<Game> findGamesByUserId(@Param("userId") Integer userId, Pageable pageable);
}
