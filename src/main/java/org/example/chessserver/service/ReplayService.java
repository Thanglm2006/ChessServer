package org.example.chessserver.service;

import lombok.RequiredArgsConstructor;
import org.example.chessserver.dto.GameMoveDto;
import org.example.chessserver.dto.TournamentPairingDto;
import org.example.chessserver.dto.TournamentRoundDto;
import org.example.chessserver.entity.Game;
import org.example.chessserver.entity.Tournament;
import org.example.chessserver.entity.TournamentPairing;
import org.example.chessserver.repository.GameMoveRepository;
import org.example.chessserver.repository.GameRepository;
import org.example.chessserver.repository.TournamentPairingRepository;
import org.example.chessserver.repository.TournamentRepository;
import org.example.chessserver.repository.TournamentRoundRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ReplayService {
    private final TournamentRepository tournamentRepository;
    private final TournamentRoundRepository roundRepository;
    private final TournamentPairingRepository pairingRepository;
    private final GameRepository gameRepository;
    private final GameMoveRepository gameMoveRepository;

    public void verifyTournamentFinished(Integer tournamentId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));
        if (!"FINISHED".equals(t.getStatus())) {
            throw new org.springframework.security.access.AccessDeniedException("Tournament replay is available only after the tournament has finished.");
        }
    }

    public List<TournamentRoundDto> getRounds(Integer tournamentId) {
        verifyTournamentFinished(tournamentId);
        return roundRepository.findByTournamentTournamentIdOrderByRoundNumberAsc(tournamentId).stream()
                .map(r -> TournamentRoundDto.builder()
                        .roundId(r.getRoundId())
                        .tournamentId(r.getTournament().getTournamentId())
                        .roundNumber(r.getRoundNumber())
                        .startedAt(r.getStartedAt())
                        .endedAt(r.getEndedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public List<TournamentPairingDto> getPairings(Integer tournamentId, Integer roundId) {
        verifyTournamentFinished(tournamentId);
        return pairingRepository.findByRoundRoundId(roundId).stream()
                .map(this::mapToPairingDto)
                .collect(Collectors.toList());
    }

    public List<TournamentPairingDto> getTournamentGames(Integer tournamentId) {
        verifyTournamentFinished(tournamentId);
        return pairingRepository.findByTournamentId(tournamentId).stream()
                .filter(p -> p.getGame() != null)
                .map(this::mapToPairingDto)
                .collect(Collectors.toList());
    }

    public Game getGame(Integer gameId) {
        List<TournamentPairing> pairings = pairingRepository.findAll().stream()
                .filter(p -> p.getGame() != null && p.getGame().getGameId().equals(gameId))
                .collect(Collectors.toList());
        if (!pairings.isEmpty()) {
            Tournament t = pairings.get(0).getRound().getTournament();
            if (!"FINISHED".equals(t.getStatus())) {
                throw new org.springframework.security.access.AccessDeniedException("Tournament replay is available only after the tournament has finished.");
            }
        }
        return gameRepository.findById(gameId)
                .orElseThrow(() -> new RuntimeException("Game not found"));
    }

    public List<GameMoveDto> getGameMoves(Integer gameId) {
        getGame(gameId);
        return gameMoveRepository.findByGameGameIdOrderByMoveNumberAsc(gameId).stream()
                .map(m -> GameMoveDto.builder()
                        .moveId(m.getMoveId())
                        .gameId(m.getGame().getGameId())
                        .moveNumber(m.getMoveNumber())
                        .sanMove(m.getSanMove())
                        .fenAfterMove(m.getFenAfterMove())
                        .evaluation(m.getEvaluation())
                        .createdAt(m.getCreatedAt())
                        .build())
                .collect(Collectors.toList());
    }

    public Map<String, Object> getGameAnalysis(Integer gameId) {
        getGame(gameId);
        List<GameMoveDto> moves = getGameMoves(gameId);
        
        long blunders = moves.stream().filter(m -> m.getEvaluation() != null && Math.abs(m.getEvaluation()) > 2.0).count();
        long mistakes = moves.stream().filter(m -> m.getEvaluation() != null && Math.abs(m.getEvaluation()) > 1.0 && Math.abs(m.getEvaluation()) <= 2.0).count();

        return Map.of(
                "gameId", gameId,
                "totalMoves", moves.size(),
                "mistakesCount", mistakes,
                "blundersCount", blunders,
                "moves", moves
        );
    }

    private TournamentPairingDto mapToPairingDto(TournamentPairing p) {
        return TournamentPairingDto.builder()
                .pairingId(p.getPairingId())
                .roundId(p.getRound().getRoundId())
                .whitePlayerId(p.getWhitePlayer() != null ? p.getWhitePlayer().getUserId() : null)
                .whitePlayerName(p.getWhitePlayer() != null ? p.getWhitePlayer().getUsername() : "BYE")
                .blackPlayerId(p.getBlackPlayer() != null ? p.getBlackPlayer().getUserId() : null)
                .blackPlayerName(p.getBlackPlayer() != null ? p.getBlackPlayer().getUsername() : "BYE")
                .gameId(p.getGame() != null ? p.getGame().getGameId() : null)
                .result(p.getResult())
                .isBye(p.getIsBye())
                .build();
    }
}
