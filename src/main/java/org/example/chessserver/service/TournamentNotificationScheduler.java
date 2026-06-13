package org.example.chessserver.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.example.chessserver.entity.Tournament;
import org.example.chessserver.entity.TournamentParticipant;
import org.example.chessserver.repository.TournamentParticipantRepository;
import org.example.chessserver.repository.TournamentRepository;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class TournamentNotificationScheduler {
    private final TournamentRepository tournamentRepository;
    private final TournamentParticipantRepository participantRepository;
    private final EmailService emailService;

    @Scheduled(fixedDelay = 300000) // check every 5 minutes
    public void sendUpcomingTournamentReminders() {
        ZonedDateTime now = ZonedDateTime.now();
        ZonedDateTime threeHoursFromNow = now.plusHours(3);

        // Find tournaments starting in the next 3 hours
        List<Tournament> upcomingTournaments = tournamentRepository.findAll().stream()
                .filter(t -> "REGISTERING".equals(t.getStatus()))
                .filter(t -> t.getStartTime() != null && t.getStartTime().isAfter(now) && t.getStartTime().isBefore(threeHoursFromNow))
                .toList();

        for (Tournament t : upcomingTournaments) {
            List<TournamentParticipant> participants = participantRepository.findByIdTournamentId(t.getTournamentId());
            for (TournamentParticipant p : participants) {
                if (p.getReminderSent() == null || !p.getReminderSent()) {
                    String formattedTime = t.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm dd/MM/yyyy"));
                    emailService.sendTournamentReminder(
                            p.getUser().getEmail(),
                            p.getUser().getUsername(),
                            t.getTournamentName(),
                            formattedTime
                    );
                    p.setReminderSent(true);
                    participantRepository.save(p);
                }
            }
        }
    }
}
