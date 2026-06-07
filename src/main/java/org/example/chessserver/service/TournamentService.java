package org.example.chessserver.service;

import lombok.RequiredArgsConstructor;
import org.example.chessserver.dto.*;
import org.example.chessserver.entity.*;
import org.example.chessserver.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TournamentService {
    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository participantRepository;
    private final TournamentRoundRepository roundRepository;
    private final TournamentPairingRepository pairingRepository;
    private final UserRepository userRepository;
    private final EloRatingRepository eloRatingRepository;
    private final SwissPairingService swissPairingService;
    private final GameRepository gameRepository;
    private final GameMoveRepository gameMoveRepository;

    // --- User Actions ---

    public List<TournamentDto> getAllTournaments() {
        return tournamentRepository.findAll().stream()
                .map(this::mapToTournamentDto)
                .collect(Collectors.toList());
    }

    public TournamentDto getTournamentById(Integer tournamentId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));
        return mapToTournamentDto(t);
    }

    public List<TournamentParticipantDto> getStandings(Integer tournamentId) {
        return participantRepository.findStandings(tournamentId).stream()
                .map(this::mapToParticipantDto)
                .collect(Collectors.toList());
    }

    @Transactional
    public void joinTournament(Integer tournamentId, Integer userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found"));

        if (Boolean.TRUE.equals(user.getIsBanned())) {
            throw new RuntimeException("Tài khoản của bạn đã bị khóa");
        }

        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));

        if (!"REGISTERING".equals(t.getStatus())) {
            throw new RuntimeException("Tournament is not open for registration");
        }

        ZonedDateTime now = ZonedDateTime.now();
        if (t.getRegistrationStart() != null && now.isBefore(t.getRegistrationStart())) {
            throw new RuntimeException("Registration has not started yet");
        }
        if (t.getRegistrationEnd() != null && now.isAfter(t.getRegistrationEnd())) {
            throw new RuntimeException("Registration has closed");
        }

        TournamentParticipantId id = new TournamentParticipantId(tournamentId, userId);
        if (participantRepository.existsById(id)) {
            throw new RuntimeException("Already registered for this tournament");
        }

        Integer initialRating = eloRatingRepository.findById(userId).map(EloRating::getRating).orElse(1200);

        TournamentParticipant participant = TournamentParticipant.builder()
                .id(id)
                .tournament(t)
                .user(user)
                .initialRating(initialRating)
                .currentScore(BigDecimal.ZERO)
                .buchholz(BigDecimal.ZERO)
                .sonnebornBerger(BigDecimal.ZERO)
                .byeReceived(false)
                .build();

        participantRepository.save(participant);
    }

    @Transactional
    public void leaveTournament(Integer tournamentId, Integer userId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));

        if (!"REGISTERING".equals(t.getStatus())) {
            throw new RuntimeException("Cannot leave tournament after registration is closed");
        }

        TournamentParticipantId id = new TournamentParticipantId(tournamentId, userId);
        if (!participantRepository.existsById(id)) {
            throw new RuntimeException("Not registered for this tournament");
        }

        participantRepository.deleteById(id);
    }

    // --- Admin Actions ---

    @Transactional
    public TournamentDto createTournament(TournamentRequest request, Integer adminId) {
        User admin = userRepository.findById(adminId)
                .orElseThrow(() -> new RuntimeException("Admin not found"));

        Tournament t = Tournament.builder()
                .tournamentName(request.getTournamentName())
                .description(request.getDescription())
                .totalRounds(request.getTotalRounds())
                .timeControl(request.getTimeControl())
                .registrationStart(request.getRegistrationStart())
                .registrationEnd(request.getRegistrationEnd())
                .startTime(request.getStartTime())
                .status("REGISTERING")
                .createdBy(admin)
                .build();

        return mapToTournamentDto(tournamentRepository.save(t));
    }

    @Transactional
    public TournamentDto updateTournament(Integer tournamentId, TournamentRequest request) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));

        if (!"REGISTERING".equals(t.getStatus())) {
            throw new RuntimeException("Cannot edit tournament after it has started");
        }

        t.setTournamentName(request.getTournamentName());
        t.setDescription(request.getDescription());
        t.setTotalRounds(request.getTotalRounds());
        t.setTimeControl(request.getTimeControl());
        t.setRegistrationStart(request.getRegistrationStart());
        t.setRegistrationEnd(request.getRegistrationEnd());
        t.setStartTime(request.getStartTime());

        return mapToTournamentDto(tournamentRepository.save(t));
    }

    @Transactional
    public void cancelTournament(Integer tournamentId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));
        tournamentRepository.delete(t);
    }

    @Transactional
    public void startTournament(Integer tournamentId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));

        if (!"REGISTERING".equals(t.getStatus())) {
            throw new RuntimeException("Tournament is already started or finished");
        }

        List<TournamentParticipant> participants = participantRepository.findByIdTournamentId(tournamentId);
        if (participants.size() < 2) {
            throw new RuntimeException("Need at least 2 participants to start tournament");
        }

        t.setStatus("ONGOING");
        tournamentRepository.save(t);

        // Generate Round 1
        generateRound(t, 1);
    }

    @Transactional
    public void finishTournament(Integer tournamentId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));

        if (!"ONGOING".equals(t.getStatus())) {
            throw new RuntimeException("Only ongoing tournaments can be finished");
        }

        t.setStatus("FINISHED");
        tournamentRepository.save(t);
    }

    @Transactional
    public void submitPairingResult(Integer pairingId, String result) {
        TournamentPairing pairing = pairingRepository.findById(pairingId)
                .orElseThrow(() -> new RuntimeException("Pairing not found"));

        if (pairing.getResult() != null) {
            throw new RuntimeException("Result already submitted");
        }

        pairing.setResult(result);
        pairingRepository.save(pairing);

        // Check if all pairings in the current round are completed
        TournamentRound round = pairing.getRound();
        List<TournamentPairing> roundPairings = pairingRepository.findByRoundRoundId(round.getRoundId());
        boolean allFinished = roundPairings.stream().allMatch(p -> p.getResult() != null);

        if (allFinished) {
            round.setEndedAt(ZonedDateTime.now());
            roundRepository.save(round);

            updateTournamentScoresAndTiebreaks(round.getTournament().getTournamentId());

            // Check if there are more rounds
            int nextRoundNum = round.getRoundNumber() + 1;
            if (nextRoundNum <= round.getTournament().getTotalRounds()) {
                generateRound(round.getTournament(), nextRoundNum);
            } else {
                // Auto finish tournament
                round.getTournament().setStatus("FINISHED");
                tournamentRepository.save(round.getTournament());
            }
        }
    }

    @Transactional
    public void generateRound(Tournament tournament, int roundNumber) {
        TournamentRound round = TournamentRound.builder()
                .tournament(tournament)
                .roundNumber(roundNumber)
                .startedAt(ZonedDateTime.now())
                .build();
        roundRepository.save(round);

        List<TournamentPairing> pairings = swissPairingService.generatePairings(tournament, round);
        for (TournamentPairing p : pairings) {
            pairingRepository.save(p);
            
            // If it is a BYE, update the byeReceived flag in the participant table
            if (Boolean.TRUE.equals(p.getIsBye())) {
                TournamentParticipant participant = participantRepository.findById(
                        new TournamentParticipantId(tournament.getTournamentId(), p.getWhitePlayer().getUserId())
                ).orElse(null);
                if (participant != null) {
                    participant.setByeReceived(true);
                    participantRepository.save(participant);
                }
            }
        }
    }

    @Transactional
    public void updateTournamentScoresAndTiebreaks(Integer tournamentId) {
        List<TournamentParticipant> participants = participantRepository.findByIdTournamentId(tournamentId);
        List<TournamentPairing> allPairings = pairingRepository.findByTournamentId(tournamentId);

        // Map of player ID to cumulative score
        Map<Integer, BigDecimal> scores = new HashMap<>();
        // Map of player ID to list of opponents they played against (excluding BYEs)
        Map<Integer, List<Integer>> opponents = new HashMap<>();
        // Map of player ID to list of defeated opponents
        Map<Integer, List<Integer>> defeated = new HashMap<>();
        // Map of player ID to list of drawn opponents
        Map<Integer, List<Integer>> drawn = new HashMap<>();

        for (TournamentParticipant p : participants) {
            int pId = p.getUser().getUserId();
            scores.put(pId, BigDecimal.ZERO);
            opponents.put(pId, new ArrayList<>());
            defeated.put(pId, new ArrayList<>());
            drawn.put(pId, new ArrayList<>());
        }

        // Calculate direct scores from pairings
        for (TournamentPairing pairing : allPairings) {
            if (pairing.getResult() == null) continue;

            if (Boolean.TRUE.equals(pairing.getIsBye())) {
                int pId = pairing.getWhitePlayer().getUserId();
                scores.put(pId, scores.get(pId).add(BigDecimal.ONE)); // Bye gets 1.0 point
                continue;
            }

            int wId = pairing.getWhitePlayer().getUserId();
            int bId = pairing.getBlackPlayer().getUserId();

            opponents.get(wId).add(bId);
            opponents.get(bId).add(wId);

            if ("1-0".equals(pairing.getResult())) {
                scores.put(wId, scores.get(wId).add(BigDecimal.ONE));
                defeated.get(wId).add(bId);
            } else if ("0-1".equals(pairing.getResult())) {
                scores.put(bId, scores.get(bId).add(BigDecimal.ONE));
                defeated.get(bId).add(wId);
            } else if ("1/2-1/2".equals(pairing.getResult())) {
                scores.put(wId, scores.get(wId).add(new BigDecimal("0.5")));
                scores.put(bId, scores.get(bId).add(new BigDecimal("0.5")));
                drawn.get(wId).add(bId);
                drawn.get(bId).add(wId);
            }
        }

        // Apply updated cumulative score first
        for (TournamentParticipant p : participants) {
            int pId = p.getUser().getUserId();
            p.setCurrentScore(scores.get(pId));
        }
        participantRepository.saveAll(participants);
        participantRepository.flush();

        // Calculate Buchholz and Sonneborn-Berger
        for (TournamentParticipant p : participants) {
            int pId = p.getUser().getUserId();

            // Buchholz = Sum of scores of all opponents
            BigDecimal bh = BigDecimal.ZERO;
            for (int oppId : opponents.get(pId)) {
                bh = bh.add(scores.getOrDefault(oppId, BigDecimal.ZERO));
            }

            // Sonneborn-Berger = Sum of scores of defeated + 0.5 * sum of scores of drawn
            BigDecimal sb = BigDecimal.ZERO;
            for (int defId : defeated.get(pId)) {
                sb = sb.add(scores.getOrDefault(defId, BigDecimal.ZERO));
            }
            for (int drId : drawn.get(pId)) {
                sb = sb.add(scores.getOrDefault(drId, BigDecimal.ZERO).multiply(new BigDecimal("0.5")));
            }

            p.setBuchholz(bh);
            p.setSonnebornBerger(sb);
        }

        participantRepository.saveAll(participants);
    }

    public List<TournamentRoundDto> getRounds(Integer tournamentId) {
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

    public List<TournamentPairingDto> getPairingsByRound(Integer roundId) {
        return pairingRepository.findByRoundRoundId(roundId).stream()
                .map(this::mapToPairingDto)
                .collect(Collectors.toList());
    }

    // --- Mappers ---

    private TournamentDto mapToTournamentDto(Tournament t) {
        return TournamentDto.builder()
                .tournamentId(t.getTournamentId())
                .tournamentName(t.getTournamentName())
                .description(t.getDescription())
                .totalRounds(t.getTotalRounds())
                .timeControl(t.getTimeControl())
                .registrationStart(t.getRegistrationStart())
                .registrationEnd(t.getRegistrationEnd())
                .startTime(t.getStartTime())
                .status(t.getStatus())
                .createdById(t.getCreatedBy() != null ? t.getCreatedBy().getUserId() : null)
                .createdByName(t.getCreatedBy() != null ? t.getCreatedBy().getUsername() : null)
                .build();
    }

    private TournamentParticipantDto mapToParticipantDto(TournamentParticipant tp) {
        return TournamentParticipantDto.builder()
                .userId(tp.getUser().getUserId())
                .username(tp.getUser().getUsername())
                .countryCode(tp.getUser().getCountryCode())
                .initialRating(tp.getInitialRating())
                .currentScore(tp.getCurrentScore())
                .buchholz(tp.getBuchholz())
                .sonnebornBerger(tp.getSonnebornBerger())
                .byeReceived(tp.getByeReceived())
                .build();
    }

    private TournamentPairingDto mapToPairingDto(TournamentPairing p) {
        Integer whiteRating = p.getWhitePlayer() != null ? eloRatingRepository.findById(p.getWhitePlayer().getUserId()).map(EloRating::getRating).orElse(1200) : 1200;
        Integer blackRating = p.getBlackPlayer() != null ? eloRatingRepository.findById(p.getBlackPlayer().getUserId()).map(EloRating::getRating).orElse(1200) : 1200;
        
        return TournamentPairingDto.builder()
                .pairingId(p.getPairingId())
                .roundId(p.getRound().getRoundId())
                .whitePlayerId(p.getWhitePlayer() != null ? p.getWhitePlayer().getUserId() : null)
                .whitePlayerName(p.getWhitePlayer() != null ? p.getWhitePlayer().getUsername() : "BYE")
                .whitePlayerRating(whiteRating)
                .blackPlayerId(p.getBlackPlayer() != null ? p.getBlackPlayer().getUserId() : null)
                .blackPlayerName(p.getBlackPlayer() != null ? p.getBlackPlayer().getUsername() : "BYE")
                .blackPlayerRating(blackRating)
                .gameId(p.getGame() != null ? p.getGame().getGameId() : null)
                .result(p.getResult())
                .isBye(p.getIsBye())
                .build();
    }

    @Transactional
    public void submitPairingGame(Integer pairingId, Map<String, Object> payload) {
        TournamentPairing pairing = pairingRepository.findById(pairingId)
                .orElseThrow(() -> new RuntimeException("Pairing not found"));

        if (pairing.getResult() != null) {
            throw new RuntimeException("Result already submitted");
        }

        String result = (String) payload.get("result");
        String pgnData = (String) payload.get("pgnData");

        // Save Game
        Game game = new Game();
        game.setWhitePlayer(pairing.getWhitePlayer());
        game.setBlackPlayer(pairing.getBlackPlayer());
        game.setResult(result);
        game.setPgnData(pgnData);
        game = gameRepository.save(game);

        // Save moves
        List<Map<String, Object>> movesPayload = (List<Map<String, Object>>) payload.get("moves");
        if (movesPayload != null) {
            for (Map<String, Object> m : movesPayload) {
                GameMove move = new GameMove();
                move.setGame(game);
                move.setMoveNumber(((Number) m.get("moveNumber")).intValue());
                move.setSanMove((String) m.get("sanMove"));
                move.setFenAfterMove((String) m.get("fenAfterMove"));
                if (m.containsKey("evaluation") && m.get("evaluation") != null) {
                    move.setEvaluation(((Number) m.get("evaluation")).doubleValue());
                }
                gameMoveRepository.save(move);
            }
        }

        pairing.setGame(game);
        pairingRepository.save(pairing);

        // Submit result to advance round
        submitPairingResult(pairingId, result);
    }
}
