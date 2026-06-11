package com.example.wmtippspiel.live;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.Stage;
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.persistence.MatchRepository.NotifiedScore;
import com.example.wmtippspiel.sync.FootballDataClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/** Tests der Polling-Quelle (F8): Live-Fensterfilter und Persistenz des gemeldeten Standes. */
class ScoreDiffGoalEventSourceTest {

    private static final Instant NOW = Instant.parse("2026-06-14T20:00:00Z");

    private FootballDataClient client;
    private MatchRepository matches;
    private ScoreDiffGoalEventSource source;

    @BeforeEach
    void setUp() {
        client = Mockito.mock(FootballDataClient.class);
        matches = Mockito.mock(MatchRepository.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        source = new ScoreDiffGoalEventSource(client, matches, new GoalDetector(), clock);
    }

    @Test
    @DisplayName("Spiel im Live-Fenster mit neuem Tor → Event + gemeldeter Stand wird persistiert")
    void inWindowProducesEventAndPersists() {
        Match live = match(1L, NOW.minusSeconds(1800), MatchStatus.IN_PLAY, 1, 0); // Anpfiff vor 30 Min
        when(client.fetchMatches()).thenReturn(List.of(live));
        when(matches.getNotifiedScore(1L)).thenReturn(Optional.of(new NotifiedScore(0, 0)));

        List<GoalEvent> events = source.fetchEvents();

        assertThat(events).hasSize(1);
        assertThat(events.get(0).kind()).isEqualTo(GoalEvent.Kind.GOAL);
        verify(matches).updateNotifiedScore(1L, 1, 0);
    }

    @Test
    @DisplayName("Unveränderter Stand → keine Events, kein Persistieren")
    void unchangedDoesNotPersist() {
        Match live = match(1L, NOW.minusSeconds(1800), MatchStatus.IN_PLAY, 1, 0);
        when(client.fetchMatches()).thenReturn(List.of(live));
        when(matches.getNotifiedScore(1L)).thenReturn(Optional.of(new NotifiedScore(1, 0)));

        assertThat(source.fetchEvents()).isEmpty();
        verify(matches, never()).updateNotifiedScore(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("Spiele außerhalb des Live-Fensters werden ignoriert (keine Abfrage des gemeldeten Standes)")
    void outOfWindowIgnored() {
        Match future = match(2L, NOW.plusSeconds(3600), MatchStatus.SCHEDULED, null, null); // erst in 1 h
        Match longOver = match(3L, NOW.minusSeconds(3 * 3600), MatchStatus.IN_PLAY, 2, 2);   // vor 3 h angepfiffen
        when(client.fetchMatches()).thenReturn(List.of(future, longOver));

        assertThat(source.fetchEvents()).isEmpty();
        verify(matches, never()).getNotifiedScore(anyLong());
    }

    @Test
    @DisplayName("Fehler der Quelle (leere Liste) erzeugt keine Events")
    void emptySourceNoEvents() {
        when(client.fetchMatches()).thenReturn(List.of());
        assertThat(source.fetchEvents()).isEmpty();
        verify(matches, never()).updateNotifiedScore(anyLong(), anyInt(), anyInt());
    }

    private static Match match(long id, Instant kickoff, MatchStatus status, Integer home, Integer away) {
        return new Match(id, "Team A", "Team B", kickoff, Stage.GROUP_STAGE, "A", null,
                null, null, null, home, away, status, false, false);
    }
}
