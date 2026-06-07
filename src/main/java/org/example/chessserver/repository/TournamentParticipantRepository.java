package org.example.chessserver.repository;

import org.example.chessserver.entity.TournamentParticipant;
import org.example.chessserver.entity.TournamentParticipantId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface TournamentParticipantRepository extends JpaRepository<TournamentParticipant, TournamentParticipantId> {
    List<TournamentParticipant> findByIdTournamentId(Integer tournamentId);

    @Query("SELECT tp FROM TournamentParticipant tp JOIN FETCH tp.user u WHERE tp.id.tournamentId = :tournamentId ORDER BY tp.currentScore DESC, tp.buchholz DESC, tp.sonnebornBerger DESC, tp.initialRating DESC")
    List<TournamentParticipant> findStandings(@Param("tournamentId") Integer tournamentId);
}
