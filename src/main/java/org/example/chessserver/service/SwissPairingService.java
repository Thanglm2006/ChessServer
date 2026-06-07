package org.example.chessserver.service;

import lombok.RequiredArgsConstructor;
import org.example.chessserver.entity.*;
import org.example.chessserver.repository.TournamentPairingRepository;
import org.example.chessserver.repository.TournamentParticipantRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SwissPairingService {
    private final TournamentParticipantRepository participantRepository;
    private final TournamentPairingRepository pairingRepository;

    public List<TournamentPairing> generatePairings(Tournament tournament, TournamentRound round) {
        List<TournamentParticipant> participants = participantRepository.findByIdTournamentId(tournament.getTournamentId());
        if (participants.isEmpty()) {
            return Collections.emptyList();
        }

        // Fetch all previous pairings for this tournament to avoid rematches and track colors
        List<TournamentPairing> previousPairings = pairingRepository.findByTournamentId(tournament.getTournamentId());
        
        // Map of player ID to Set of opponent IDs they have played against
        Map<Integer, Set<Integer>> history = new HashMap<>();
        // Map of player ID to count of times they played white
        Map<Integer, Integer> whiteCounts = new HashMap<>();
        
        for (TournamentParticipant p : participants) {
            history.put(p.getUser().getUserId(), new HashSet<>());
            whiteCounts.put(p.getUser().getUserId(), 0);
        }

        for (TournamentPairing pairing : previousPairings) {
            if (Boolean.TRUE.equals(pairing.getIsBye())) {
                continue;
            }
            int whiteId = pairing.getWhitePlayer().getUserId();
            int blackId = pairing.getBlackPlayer().getUserId();
            
            history.computeIfAbsent(whiteId, k -> new HashSet<>()).add(blackId);
            history.computeIfAbsent(blackId, k -> new HashSet<>()).add(whiteId);
            
            whiteCounts.put(whiteId, whiteCounts.getOrDefault(whiteId, 0) + 1);
        }

        List<TournamentPairing> newPairings = new ArrayList<>();
        List<TournamentParticipant> activePlayers = new ArrayList<>(participants);

        // 1. Handle BYE if odd number of players
        if (activePlayers.size() % 2 != 0) {
            // Find a player to receive BYE:
            // Sort by current score asc, then initial rating asc, filtering for those who haven't received a BYE yet.
            TournamentParticipant byePlayer = activePlayers.stream()
                    .filter(p -> !Boolean.TRUE.equals(p.getByeReceived()))
                    .min(Comparator.comparing(TournamentParticipant::getCurrentScore)
                            .thenComparing(TournamentParticipant::getInitialRating))
                    .orElse(null);

            if (byePlayer == null) {
                // If everyone received a BYE, pick the absolute lowest scorer
                byePlayer = activePlayers.stream()
                        .min(Comparator.comparing(TournamentParticipant::getCurrentScore)
                                .thenComparing(TournamentParticipant::getInitialRating))
                        .orElse(activePlayers.get(activePlayers.size() - 1));
            }

            // Create BYE pairing
            TournamentPairing byePairing = TournamentPairing.builder()
                    .round(round)
                    .whitePlayer(byePlayer.getUser())
                    .blackPlayer(null)
                    .result("1-0") // Bye receives 1.0 point
                    .isBye(true)
                    .build();
            
            newPairings.add(byePairing);
            activePlayers.remove(byePlayer);
        }

        // 2. Pair active players
        if (round.getRoundNumber() == 1) {
            // Round 1: Pair by Elo rating descending
            activePlayers.sort((a, b) -> b.getInitialRating().compareTo(a.getInitialRating()));
            int n = activePlayers.size();
            int half = n / 2;
            for (int i = 0; i < half; i++) {
                TournamentParticipant white = activePlayers.get(i);
                TournamentParticipant black = activePlayers.get(i + half);
                
                // Color preference: white gets white
                newPairings.add(TournamentPairing.builder()
                        .round(round)
                        .whitePlayer(white.getUser())
                        .blackPlayer(black.getUser())
                        .isBye(false)
                        .build());
            }
        } else {
            // Round 2 onwards: Pair by Swiss score descending, then by Elo rating descending
            activePlayers.sort((a, b) -> {
                int cmp = b.getCurrentScore().compareTo(a.getCurrentScore());
                if (cmp != 0) return cmp;
                return b.getInitialRating().compareTo(a.getInitialRating());
            });

            // Backtracking search to find a valid pairing combination without rematches
            List<TournamentPairing> pairedList = new ArrayList<>();
            boolean success = backtrackPairings(activePlayers, 0, pairedList, history, whiteCounts, round);
            if (success) {
                newPairings.addAll(pairedList);
            } else {
                // Fallback: Pair greedily, allowing rematches only if absolutely necessary
                boolean[] paired = new boolean[activePlayers.size()];
                for (int i = 0; i < activePlayers.size(); i++) {
                    if (paired[i]) continue;
                    TournamentParticipant p1 = activePlayers.get(i);
                    paired[i] = true;
                    
                    // Look for best opponent
                    int bestOpponentIdx = -1;
                    for (int j = i + 1; j < activePlayers.size(); j++) {
                        if (paired[j]) continue;
                        TournamentParticipant p2 = activePlayers.get(j);
                        if (!history.get(p1.getUser().getUserId()).contains(p2.getUser().getUserId())) {
                            bestOpponentIdx = j;
                            break;
                        }
                    }
                    
                    // If all opponents are rematches, pick the first available unpaired player
                    if (bestOpponentIdx == -1) {
                        for (int j = i + 1; j < activePlayers.size(); j++) {
                            if (!paired[j]) {
                                bestOpponentIdx = j;
                                break;
                            }
                        }
                    }
                    
                    if (bestOpponentIdx != -1) {
                        paired[bestOpponentIdx] = true;
                        TournamentParticipant p2 = activePlayers.get(bestOpponentIdx);
                        
                        // Decide color
                        int w1 = whiteCounts.getOrDefault(p1.getUser().getUserId(), 0);
                        int w2 = whiteCounts.getOrDefault(p2.getUser().getUserId(), 0);
                        TournamentParticipant white = w1 <= w2 ? p1 : p2;
                        TournamentParticipant black = white == p1 ? p2 : p1;
                        
                        newPairings.add(TournamentPairing.builder()
                                .round(round)
                                .whitePlayer(white.getUser())
                                .blackPlayer(black.getUser())
                                .isBye(false)
                                .build());
                    } else {
                        // Odd remainder should not occur as activePlayers is even, but handle safely
                        TournamentPairing byePairing = TournamentPairing.builder()
                                .round(round)
                                .whitePlayer(p1.getUser())
                                .blackPlayer(null)
                                .result("1-0")
                                .isBye(true)
                                .build();
                        newPairings.add(byePairing);
                    }
                }
            }
        }

        return newPairings;
    }

    private boolean backtrackPairings(List<TournamentParticipant> players, int idx, 
                                     List<TournamentPairing> result, 
                                     Map<Integer, Set<Integer>> history,
                                     Map<Integer, Integer> whiteCounts,
                                     TournamentRound round) {
        if (idx >= players.size()) {
            return true;
        }

        // If player already paired (from being chosen as opponent by a previous step)
        if (isAlreadyPaired(players.get(idx).getUser().getUserId(), result)) {
            return backtrackPairings(players, idx + 1, result, history, whiteCounts, round);
        }

        TournamentParticipant p1 = players.get(idx);
        int p1Id = p1.getUser().getUserId();

        // Search for opponent for p1
        for (int j = idx + 1; j < players.size(); j++) {
            TournamentParticipant p2 = players.get(j);
            int p2Id = p2.getUser().getUserId();
            
            if (isAlreadyPaired(p2Id, result)) {
                continue;
            }

            // Check if they already played
            if (history.get(p1Id).contains(p2Id)) {
                continue;
            }

            // Decide color preference
            int w1 = whiteCounts.getOrDefault(p1Id, 0);
            int w2 = whiteCounts.getOrDefault(p2Id, 0);
            User white = w1 <= w2 ? p1.getUser() : p2.getUser();
            User black = white.getUserId() == p1Id ? p2.getUser() : p1.getUser();

            TournamentPairing pairing = TournamentPairing.builder()
                    .round(round)
                    .whitePlayer(white)
                    .blackPlayer(black)
                    .isBye(false)
                    .build();

            result.add(pairing);

            if (backtrackPairings(players, idx + 1, result, history, whiteCounts, round)) {
                return true;
            }

            result.remove(result.size() - 1);
        }

        return false;
    }

    private boolean isAlreadyPaired(int userId, List<TournamentPairing> result) {
        for (TournamentPairing p : result) {
            if (p.getWhitePlayer().getUserId() == userId || 
                (p.getBlackPlayer() != null && p.getBlackPlayer().getUserId() == userId)) {
                return true;
            }
        }
        return false;
    }
}
