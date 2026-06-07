package org.example.chessserver.repository;

import org.example.chessserver.entity.TournamentPairing;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TournamentPairingRepository extends JpaRepository<TournamentPairing, Integer> {
    List<TournamentPairing> findByRoundRoundId(Integer roundId);

    @Query("SELECT tp FROM TournamentPairing tp WHERE tp.round.tournament.tournamentId = :tournamentId")
    List<TournamentPairing> findByTournamentId(@Param("tournamentId") Integer tournamentId);

    @Query("SELECT tp FROM TournamentPairing tp WHERE (tp.whitePlayer.userId = :userId OR tp.blackPlayer.userId = :userId) AND tp.round.tournament.tournamentId = :tournamentId")
    List<TournamentPairing> findUserPairingsInTournament(@Param("userId") Integer userId, @Param("tournamentId") Integer tournamentId);
}
