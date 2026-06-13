package org.example.chessserver.service;

import lombok.RequiredArgsConstructor;
import org.example.chessserver.dto.*;
import org.example.chessserver.entity.*;
import org.example.chessserver.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.json.JSONObject;
import lombok.extern.slf4j.Slf4j;
import org.example.chessserver.websocket.ChessWebSocketHandler;
import java.time.Duration;

import java.math.BigDecimal;
import java.time.ZonedDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
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
    private final StringRedisTemplate redisTemplate;

    @Autowired
    @Lazy
    private ChessWebSocketHandler webSocketHandler;


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

    public MyPairingDto getMyPairing(Integer tournamentId, Integer userId) {
        String breakKey = "tournament:break:next-round:" + tournamentId;
        String nextRoundStr = (String) redisTemplate.opsForValue().get(breakKey);
        Long breakTimeLeft = redisTemplate.getExpire(breakKey);
        if (nextRoundStr != null && breakTimeLeft != null && breakTimeLeft >= 0) {
            return MyPairingDto.builder()
                    .inBreak(true)
                    .breakTimeLeftSeconds(breakTimeLeft)
                    .roundNumber(Integer.parseInt(nextRoundStr))
                    .build();
        }

        TournamentRound latestRound = roundRepository.findFirstByTournamentTournamentIdOrderByRoundNumberDesc(tournamentId)
                .orElse(null);
        if (latestRound == null) {
            return null;
        }

        TournamentPairing pairing = pairingRepository.findUserPairingInRound(userId, latestRound.getRoundId())
                .orElse(null);
        if (pairing == null) {
            return null;
        }

        boolean isWhite = pairing.getWhitePlayer() != null && pairing.getWhitePlayer().getUserId().equals(userId);
        User opponent = isWhite ? pairing.getBlackPlayer() : pairing.getWhitePlayer();
        String opponentName = (opponent != null) ? opponent.getUsername() : "BYE";
        Integer opponentRating = (opponent != null) ? eloRatingRepository.findById(opponent.getUserId()).map(EloRating::getRating).orElse(1200) : null;

        String lobbyKey = "tournament:lobby:pairing:" + pairing.getPairingId();
        Long timeLeft = redisTemplate.getExpire(lobbyKey);

        Boolean iAmReady = isWhite ? pairing.getWhiteReady() : pairing.getBlackReady();
        Boolean opponentReady = isWhite ? pairing.getBlackReady() : pairing.getWhiteReady();

        return MyPairingDto.builder()
                .pairingId(pairing.getPairingId())
                .roundNumber(latestRound.getRoundNumber())
                .opponentName(opponentName)
                .opponentRating(opponentRating)
                .myColor(isWhite ? "WHITE" : "BLACK")
                .isBye(pairing.getIsBye())
                .lobbyTimeLimitSeconds(300)
                .lobbyTimeLeftSeconds(timeLeft != null && timeLeft >= 0 ? timeLeft : 0L)
                .iAmReady(Boolean.TRUE.equals(iAmReady))
                .opponentReady(Boolean.TRUE.equals(opponentReady))
                .result(pairing.getResult())
                .gameId(pairing.getGame() != null ? String.valueOf(pairing.getGame().getGameId()) : null)
                .build();
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

        // Check for overlapping tournaments
        List<TournamentParticipant> joined = participantRepository.findByUserId(userId);
        for (TournamentParticipant p : joined) {
            Tournament joinedT = p.getTournament();
            if ("FINISHED".equals(joinedT.getStatus())) {
                continue;
            }
            if (t.getStartTime() != null && joinedT.getStartTime() != null) {
                int duration1 = t.getTotalRounds() * (2 * getMinutesFromTimeControl(t.getTimeControl()) + 10) + 15;
                int duration2 = joinedT.getTotalRounds() * (2 * getMinutesFromTimeControl(joinedT.getTimeControl()) + 10) + 15;

                ZonedDateTime start1 = t.getStartTime();
                ZonedDateTime end1 = start1.plusMinutes(duration1);
                ZonedDateTime start2 = joinedT.getStartTime();
                ZonedDateTime end2 = start2.plusMinutes(duration2);

                if (start1.isBefore(end2) && start2.isBefore(end1)) {
                    throw new RuntimeException("Bạn đã đăng ký tham gia giải đấu '" + joinedT.getTournamentName() + "' có thời gian thi đấu trùng khớp");
                }
            }
        }

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
            t.setStatus("FINISHED");
            tournamentRepository.save(t);
            log.warn("Tournament {} (ID: {}) auto-finished because it has less than 2 participants (found {}).", 
                    t.getTournamentName(), t.getTournamentId(), participants.size());
            return;
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
                String breakKey = "tournament:break:next-round:" + round.getTournament().getTournamentId();
                redisTemplate.opsForValue().set(breakKey, String.valueOf(nextRoundNum), Duration.ofMinutes(10));

                JSONObject breakMsg = new JSONObject()
                        .put("type", "ROUND_BREAK_START")
                        .put("tournamentId", round.getTournament().getTournamentId())
                        .put("nextRoundNumber", nextRoundNum)
                        .put("breakDurationSeconds", 600);

                List<TournamentParticipant> participants = participantRepository.findByIdTournamentId(round.getTournament().getTournamentId());
                for (TournamentParticipant p : participants) {
                    if (webSocketHandler.isUserOnline(p.getUser().getUserId())) {
                        try {
                            webSocketHandler.sendToUser(p.getUser().getUserId(), breakMsg.toString());
                        } catch (Exception e) {
                            log.error("Failed to send break message to user " + p.getUser().getUserId(), e);
                        }
                    }
                }
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
            } else {
                p.setLobbyStartedAt(ZonedDateTime.now());
                pairingRepository.save(p);
                
                String lobbyKey = "tournament:lobby:pairing:" + p.getPairingId();
                redisTemplate.opsForValue().set(lobbyKey, "ACTIVE", Duration.ofMinutes(5));
            }
        }

        // Broadcast ROUND_STARTED to all participants
        List<TournamentParticipant> participants = participantRepository.findByIdTournamentId(tournament.getTournamentId());
        for (TournamentParticipant part : participants) {
            if (webSocketHandler.isUserOnline(part.getUser().getUserId())) {
                try {
                    JSONObject startMsg = new JSONObject()
                            .put("type", "ROUND_STARTED")
                            .put("tournamentId", tournament.getTournamentId())
                            .put("roundNumber", roundNumber);
                    webSocketHandler.sendToUser(part.getUser().getUserId(), startMsg.toString());
                } catch (Exception e) {
                    log.error("Failed to notify user " + part.getUser().getUserId() + " of round start", e);
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
        String status = t.getStatus();
        ZonedDateTime now = ZonedDateTime.now();
        if ("REGISTERING".equals(status)) {
            if (t.getRegistrationEnd() != null && now.isAfter(t.getRegistrationEnd())) {
                status = "REGISTRATION_CLOSED";
            } else if (t.getRegistrationStart() != null && now.isBefore(t.getRegistrationStart())) {
                status = "UPCOMING";
            }
        }

        return TournamentDto.builder()
                .tournamentId(t.getTournamentId())
                .tournamentName(t.getTournamentName())
                .description(t.getDescription())
                .totalRounds(t.getTotalRounds())
                .timeControl(t.getTimeControl())
                .registrationStart(t.getRegistrationStart())
                .registrationEnd(t.getRegistrationEnd())
                .startTime(t.getStartTime())
                .status(status)
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

    @Transactional
    public void linkPairingGameAndSubmit(Integer pairingId, Game game, String result) {
        TournamentPairing pairing = pairingRepository.findById(pairingId)
                .orElseThrow(() -> new RuntimeException("Pairing not found"));
        pairing.setGame(game);
        pairingRepository.save(pairing);
        submitPairingResult(pairingId, result);
    }

    @Transactional
    public void joinLobby(Integer pairingId, Integer userId) {
        TournamentPairing pairing = pairingRepository.findById(pairingId)
                .orElseThrow(() -> new RuntimeException("Pairing not found"));

        boolean isWhite = pairing.getWhitePlayer() != null && pairing.getWhitePlayer().getUserId().equals(userId);
        boolean isBlack = pairing.getBlackPlayer() != null && pairing.getBlackPlayer().getUserId().equals(userId);

        if (!isWhite && !isBlack) {
            throw new RuntimeException("User not part of this pairing");
        }

        if (isWhite) {
            pairing.setWhiteReady(true);
        } else {
            pairing.setBlackReady(true);
        }
        pairingRepository.saveAndFlush(pairing);

        // Notify both players about readiness update
        JSONObject updateMsg = new JSONObject()
                .put("type", "TOURNAMENT_LOBBY_UPDATE")
                .put("pairingId", pairingId)
                .put("whiteReady", Boolean.TRUE.equals(pairing.getWhiteReady()))
                .put("blackReady", Boolean.TRUE.equals(pairing.getBlackReady()));

        try {
            if (pairing.getWhitePlayer() != null) {
                webSocketHandler.sendToUser(pairing.getWhitePlayer().getUserId(), updateMsg.toString());
            }
            if (pairing.getBlackPlayer() != null) {
                webSocketHandler.sendToUser(pairing.getBlackPlayer().getUserId(), updateMsg.toString());
            }
        } catch (Exception e) {
            log.error("Failed to send lobby update", e);
        }

        // If both are ready, start the match!
        if (Boolean.TRUE.equals(pairing.getWhiteReady()) && Boolean.TRUE.equals(pairing.getBlackReady())) {
            String lobbyKey = "tournament:lobby:pairing:" + pairingId;
            redisTemplate.delete(lobbyKey);

            String gameId = UUID.randomUUID().toString();
            String matchType = pairing.getRound().getTournament().getTimeControl();

            webSocketHandler.startGameDirectly(gameId, pairing.getWhitePlayer().getUserId(), pairing.getBlackPlayer().getUserId(), matchType, pairingId);

            JSONObject startMsg = new JSONObject()
                    .put("type", "TOURNAMENT_MATCH_START")
                    .put("pairingId", pairingId)
                    .put("gameId", gameId);

            try {
                if (pairing.getWhitePlayer() != null) {
                    webSocketHandler.sendToUser(pairing.getWhitePlayer().getUserId(), startMsg.toString());
                }
                if (pairing.getBlackPlayer() != null) {
                    webSocketHandler.sendToUser(pairing.getBlackPlayer().getUserId(), startMsg.toString());
                }
            } catch (Exception e) {
                log.error("Failed to send match start message", e);
            }
        }
    }

    @Transactional
    public void forfeitPairing(Integer pairingId) {
        TournamentPairing pairing = pairingRepository.findById(pairingId)
                .orElse(null);
        if (pairing == null || pairing.getResult() != null) {
            return;
        }

        boolean whiteReady = Boolean.TRUE.equals(pairing.getWhiteReady());
        boolean blackReady = Boolean.TRUE.equals(pairing.getBlackReady());

        String result;
        if (!whiteReady && !blackReady) {
            result = "0-0";
        } else if (whiteReady) {
            result = "1-0";
        } else {
            result = "0-1";
        }

        submitPairingResult(pairingId, result);

        JSONObject overMsg = new JSONObject()
                .put("type", "TOURNAMENT_PAIRING_FORFEITED")
                .put("pairingId", pairingId)
                .put("result", result);

        try {
            if (pairing.getWhitePlayer() != null) {
                webSocketHandler.sendToUser(pairing.getWhitePlayer().getUserId(), overMsg.toString());
            }
            if (pairing.getBlackPlayer() != null) {
                webSocketHandler.sendToUser(pairing.getBlackPlayer().getUserId(), overMsg.toString());
            }
        } catch (Exception e) {
            log.error("Failed to notify users of pairing forfeit", e);
        }
    }

    @Transactional
    public void generateNextRoundAfterBreak(Integer tournamentId) {
        Tournament t = tournamentRepository.findById(tournamentId)
                .orElseThrow(() -> new RuntimeException("Tournament not found"));
        TournamentRound latestRound = roundRepository.findFirstByTournamentTournamentIdOrderByRoundNumberDesc(tournamentId)
                .orElse(null);
        int nextRoundNum = (latestRound != null) ? latestRound.getRoundNumber() + 1 : 1;
        if (nextRoundNum <= t.getTotalRounds()) {
            generateRound(t, nextRoundNum);
        }
    }

    @Transactional
    public void recoverStuckTournaments() {
        log.info("Starting recovery check for stuck tournaments, rounds, and pairings...");
        ZonedDateTime now = ZonedDateTime.now(java.time.ZoneOffset.UTC);
        List<Tournament> ongoingTournaments = tournamentRepository.findAll().stream()
                .filter(t -> "ONGOING".equals(t.getStatus()))
                .collect(Collectors.toList());

        for (Tournament t : ongoingTournaments) {
            try {
                TournamentRound latestRound = roundRepository.findFirstByTournamentTournamentIdOrderByRoundNumberDesc(t.getTournamentId())
                        .orElse(null);
                if (latestRound != null) {
                    if (latestRound.getEndedAt() != null) {
                        int nextRoundNum = latestRound.getRoundNumber() + 1;
                        if (nextRoundNum <= t.getTotalRounds()) {
                            boolean nextRoundExists = roundRepository.findByTournamentTournamentIdAndRoundNumber(t.getTournamentId(), nextRoundNum).isPresent();
                            if (!nextRoundExists) {
                                String breakKey = "tournament:break:next-round:" + t.getTournamentId();
                                Boolean hasBreakKey = redisTemplate.hasKey(breakKey);
                                if (hasBreakKey == null || !hasBreakKey) {
                                    ZonedDateTime endedAt = latestRound.getEndedAt().withZoneSameLocal(java.time.ZoneOffset.UTC);
                                    long minutesPassed = Duration.between(endedAt, now).toMinutes();
                                    if (minutesPassed >= 10) {
                                        log.info("Recovery: Break ended while offline for tournament {}. Starting next round...", t.getTournamentId());
                                        generateNextRoundAfterBreak(t.getTournamentId());
                                    } else {
                                        long remainingSeconds = 600 - Duration.between(endedAt, now).getSeconds();
                                        if (remainingSeconds > 0) {
                                            log.info("Recovery: Re-scheduling break for tournament {} ({} seconds remaining)", t.getTournamentId(), remainingSeconds);
                                            redisTemplate.opsForValue().set(breakKey, String.valueOf(nextRoundNum), Duration.ofSeconds(remainingSeconds));
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        List<TournamentPairing> activePairings = pairingRepository.findByRoundRoundId(latestRound.getRoundId());
                        for (TournamentPairing p : activePairings) {
                            if (p.getResult() == null && !p.getIsBye() && (p.getWhiteReady() == null || !p.getWhiteReady() || p.getBlackReady() == null || !p.getBlackReady())) {
                                if (p.getLobbyStartedAt() != null) {
                                    String lobbyKey = "tournament:lobby:pairing:" + p.getPairingId();
                                    Boolean hasLobbyKey = redisTemplate.hasKey(lobbyKey);
                                    if (hasLobbyKey == null || !hasLobbyKey) {
                                        ZonedDateTime startedAt = p.getLobbyStartedAt().withZoneSameLocal(java.time.ZoneOffset.UTC);
                                        long secondsPassed = Duration.between(startedAt, now).getSeconds();
                                        if (secondsPassed >= 300) {
                                            log.info("Recovery: Lobby check-in expired while offline for pairing {}. Forfeiting...", p.getPairingId());
                                            forfeitPairing(p.getPairingId());
                                        } else {
                                            long remainingSeconds = 300 - secondsPassed;
                                            log.info("Recovery: Re-scheduling lobby for pairing {} ({} seconds remaining)", p.getPairingId(), remainingSeconds);
                                            redisTemplate.opsForValue().set(lobbyKey, "ACTIVE", Duration.ofSeconds(remainingSeconds));
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("Failed to recover tournament {}", t.getTournamentId(), e);
            }
        }
    }

    private int getMinutesFromTimeControl(String timeControl) {
        try {
            if (timeControl == null || timeControl.isEmpty()) return 10;
            String[] parts = timeControl.split("[^0-9]");
            for (String part : parts) {
                if (!part.isEmpty()) {
                    return Integer.parseInt(part);
                }
            }
        } catch (Exception e) {
            // fallback
        }
        return 10;
    }
}

