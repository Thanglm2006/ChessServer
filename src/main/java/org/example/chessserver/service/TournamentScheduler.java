package org.example.chessserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chessserver.entity.Tournament;
import org.example.chessserver.repository.TournamentRepository;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TournamentScheduler {
    private final TournamentRepository tournamentRepository;
    private final TournamentService tournamentService;

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        try {
            tournamentService.recoverStuckTournaments();
        } catch (Exception e) {
            log.error("Failed to execute tournament recovery on startup", e);
        }
    }

    @Scheduled(fixedDelay = 60000) // check every minute
    public void autoStartTournaments() {
        ZonedDateTime now = ZonedDateTime.now();
        List<Tournament> upcoming = tournamentRepository.findAll().stream()
                .filter(t -> "REGISTERING".equals(t.getStatus()))
                .filter(t -> t.getStartTime() != null && t.getStartTime().isBefore(now))
                .toList();

        for (Tournament t : upcoming) {
            try {
                log.info("Auto-starting tournament: {} (ID: {})", t.getTournamentName(), t.getTournamentId());
                tournamentService.startTournament(t.getTournamentId());
            } catch (Exception e) {
                log.error("Failed to auto-start tournament: {}", t.getTournamentId(), e);
            }
        }
    }
}
