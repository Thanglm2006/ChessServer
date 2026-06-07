package org.example.chessserver.repository;

import org.example.chessserver.entity.GameMove;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GameMoveRepository extends JpaRepository<GameMove, Integer> {
    List<GameMove> findByGameGameIdOrderByMoveNumberAsc(Integer gameId);
}
