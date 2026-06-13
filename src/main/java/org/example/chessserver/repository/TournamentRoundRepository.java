package org.example.chessserver.repository;

import org.example.chessserver.entity.TournamentRound;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface TournamentRoundRepository extends JpaRepository<TournamentRound, Integer> {
    List<TournamentRound> findByTournamentTournamentIdOrderByRoundNumberAsc(Integer tournamentId);
    Optional<TournamentRound> findByTournamentTournamentIdAndRoundNumber(Integer tournamentId, Integer roundNumber);
    Optional<TournamentRound> findFirstByTournamentTournamentIdOrderByRoundNumberDesc(Integer tournamentId);
}
