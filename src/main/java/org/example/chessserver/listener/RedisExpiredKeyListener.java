package org.example.chessserver.listener;

import lombok.extern.slf4j.Slf4j;
import org.example.chessserver.entity.Tournament;
import org.example.chessserver.repository.TournamentRepository;
import org.example.chessserver.service.TournamentService;
import org.example.chessserver.websocket.ChessWebSocketHandler;
import org.springframework.context.event.EventListener;
import org.springframework.data.redis.core.RedisKeyExpiredEvent;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Lazy;
import org.springframework.beans.factory.annotation.Autowired;

@Component
@Slf4j
public class RedisExpiredKeyListener {

    @Autowired
    @Lazy
    private TournamentService tournamentService;

    @Autowired
    private TournamentRepository tournamentRepository;

    @Autowired
    @Lazy
    private ChessWebSocketHandler webSocketHandler;

    @EventListener
    public void handleRedisKeyExpiredEvent(RedisKeyExpiredEvent<String> event) {
        String expiredKey = new String(event.getId());
        log.info("Redis key expired: {}", expiredKey);

        // 1. Tournament 10-minute break ended: tournament:break:next-round:{tournamentId}
        if (expiredKey.startsWith("tournament:break:next-round:")) {
            try {
                int tournamentId = Integer.parseInt(expiredKey.substring("tournament:break:next-round:".length()));
                Tournament tournament = tournamentRepository.findById(tournamentId).orElse(null);
                if (tournament != null) {
                    tournamentService.generateNextRoundAfterBreak(tournamentId);
                }
            } catch (Exception e) {
                log.error("Error processing tournament round break completion", e);
            }
        }

        // 2. Tournament 5-minute lobby check-in expired: tournament:lobby:pairing:{pairingId}
        if (expiredKey.startsWith("tournament:lobby:pairing:")) {
            try {
                int pairingId = Integer.parseInt(expiredKey.substring("tournament:lobby:pairing:".length()));
                tournamentService.forfeitPairing(pairingId);
            } catch (Exception e) {
                log.error("Error forfeiting pairing on lobby timeout", e);
            }
        }

        // 3. Active turn timer expired: chess:timer:game:{gameId}:turn:{userId}
        if (expiredKey.startsWith("chess:timer:game:")) {
            try {
                String[] parts = expiredKey.split(":");
                if (parts.length == 6 && "turn".equals(parts[4])) {
                    String gameId = parts[3];
                    int userId = Integer.parseInt(parts[5]);
                    webSocketHandler.handleActiveTurnTimeout(gameId, userId);
                }
            } catch (Exception e) {
                log.error("Error handling game turn timeout", e);
            }
        }
    }
}
