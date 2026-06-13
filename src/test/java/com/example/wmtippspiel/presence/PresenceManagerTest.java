package com.example.wmtippspiel.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.Stage;
import com.example.wmtippspiel.persistence.MatchRepository;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.managers.Presence;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

/**
 * Tests der Presence-Orchestrierung (F9): Activity nur bei tatsächlicher
 * Textänderung (FR-008) und korrekter Zustand aus dem Datenbestand.
 * Mindestabstand 0 ⇒ Änderungen werden sofort gesendet (Drossel separat getestet).
 */
class PresenceManagerTest {

    private static final Instant NOW = Instant.parse("2026-06-14T20:00:00Z");
    private static final String IDLE = "🏆 WM 2026 /tipp";

    private JDA jda;
    private Presence presence;
    private MatchRepository matches;
    private PresenceManager manager;

    @BeforeEach
    void setUp() {
        jda = mock(JDA.class);
        presence = mock(Presence.class);
        when(jda.getPresence()).thenReturn(presence);
        matches = mock(MatchRepository.class);
        Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
        PresenceStateResolver resolver = new PresenceStateResolver(new TeamCodeResolver());
        manager = new PresenceManager(jda, matches, resolver, clock, 0L, IDLE);
    }

    @Test
    @DisplayName("Leere Datenlage → IDLE, und kein erneutes setActivity bei unverändertem Text (FR-008)")
    void idleSetOnceNoRedundantUpdate() {
        when(matches.findInPlay()).thenReturn(List.of());
        when(matches.findUpcoming(any(), anyInt())).thenReturn(List.of());

        manager.recompute();
        manager.recompute(); // identischer Zustand → kein zweites Update

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(presence, times(1)).setActivity(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo(IDLE);
        assertThat(captor.getValue().getType()).isEqualTo(Activity.ActivityType.WATCHING);
    }

    @Test
    @DisplayName("Laufendes Spiel → LIVE-Activity vom Typ watching")
    void liveActivitySet() {
        Match live = new Match(1L, "Germany", "France", NOW.minusSeconds(600), Stage.GROUP_STAGE, "A",
                null, null, null, null, 2, 1, MatchStatus.IN_PLAY, false, false);
        when(matches.findInPlay()).thenReturn(List.of(live));

        manager.recompute();

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(presence).setActivity(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("⚽ LIVE: GER 2:1 FRA");
        assertThat(captor.getValue().getType()).isEqualTo(Activity.ActivityType.WATCHING);
    }
}
