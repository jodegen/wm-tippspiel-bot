package com.example.wmtippspiel.reminder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.Stage;
import com.example.wmtippspiel.domain.model.Tip;
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.persistence.NotifySubscriberRepository;
import com.example.wmtippspiel.persistence.ReminderLogRepository;
import com.example.wmtippspiel.persistence.TipRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

/** Tests der Tipp-Erinnerung (E1): nur Abonnenten ohne Tipp, genau einmal pro Spiel. */
class ReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-14T19:00:00Z");

    private MatchRepository matches;
    private TipRepository tips;
    private NotifySubscriberRepository subscribers;
    private ReminderLogRepository reminderLog;
    private ReminderPublisher publisher;
    private ReminderService service;

    @BeforeEach
    void setUp() {
        matches = Mockito.mock(MatchRepository.class);
        tips = Mockito.mock(TipRepository.class);
        subscribers = Mockito.mock(NotifySubscriberRepository.class);
        reminderLog = Mockito.mock(ReminderLogRepository.class);
        publisher = Mockito.mock(ReminderPublisher.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        service = new ReminderService(matches, tips, subscribers, reminderLog, publisher, clock, 60);
    }

    @Test
    @DisplayName("Pingt nur Abonnenten, die noch nicht getippt haben, und merkt das Spiel vor")
    void remindsOnlyMissingSubscribers() {
        Match soon = match(1L, NOW.plusSeconds(1800)); // in 30 Min → im 60-Min-Fenster
        when(matches.findBetween(eq(NOW), eq(NOW.plusSeconds(3600)))).thenReturn(List.of(soon));
        when(reminderLog.wasReminded(1L)).thenReturn(false);
        when(subscribers.findAllUserIds()).thenReturn(List.of("u1", "u2", "u3"));
        when(tips.findByMatch(1L)).thenReturn(List.of(tip("u1", 1L))); // u1 hat getippt

        int reminded = service.remind();

        assertThat(reminded).isEqualTo(1);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(publisher).publishReminder(eq(soon), captor.capture());
        assertThat(captor.getValue()).containsExactlyInAnyOrder("u2", "u3");
        verify(reminderLog).markReminded(eq(1L), eq(NOW));
    }

    @Test
    @DisplayName("Erinnert nicht erneut, wenn das Spiel bereits erinnert wurde (Idempotenz)")
    void doesNotRemindTwice() {
        Match soon = match(1L, NOW.plusSeconds(1800));
        when(matches.findBetween(eq(NOW), eq(NOW.plusSeconds(3600)))).thenReturn(List.of(soon));
        when(reminderLog.wasReminded(1L)).thenReturn(true);

        assertThat(service.remind()).isZero();
        verify(publisher, never()).publishReminder(Mockito.any(), Mockito.anyList());
        verify(reminderLog, never()).markReminded(anyLong(), Mockito.any());
    }

    @Test
    @DisplayName("Ohne offene Tipper wird nichts gepingt, das Spiel aber dennoch vorgemerkt")
    void marksRemindedEvenWhenAllTipped() {
        Match soon = match(1L, NOW.plusSeconds(1800));
        when(matches.findBetween(eq(NOW), eq(NOW.plusSeconds(3600)))).thenReturn(List.of(soon));
        when(reminderLog.wasReminded(1L)).thenReturn(false);
        when(subscribers.findAllUserIds()).thenReturn(List.of("u1"));
        when(tips.findByMatch(1L)).thenReturn(List.of(tip("u1", 1L)));

        assertThat(service.remind()).isZero();
        verify(publisher, never()).publishReminder(Mockito.any(), Mockito.anyList());
        verify(reminderLog).markReminded(eq(1L), eq(NOW));
    }

    private static Match match(long id, Instant kickoff) {
        return new Match(id, "Team A", "Team B", kickoff, Stage.GROUP_STAGE, "A", null,
                null, null, null, null, null, MatchStatus.SCHEDULED, false, false);
    }

    private static Tip tip(String user, long matchId) {
        return new Tip(user, matchId, user, 1, 0, NOW.minusSeconds(7200), 0);
    }
}
