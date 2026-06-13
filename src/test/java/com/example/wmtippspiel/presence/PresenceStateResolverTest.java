package com.example.wmtippspiel.presence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.Stage;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests der reinen Presence-Zustandslogik (F9): Priorität LIVE &gt; UPCOMING &gt; IDLE,
 * Textformat, Auswahl bei mehreren Live-Spielen (FR-002/003/005/007/013).
 */
class PresenceStateResolverTest {

    private static final Instant KICKOFF = Instant.parse("2026-06-14T20:00:00Z");
    private static final String IDLE = "🏆 WM 2026 /tipp";

    private final PresenceStateResolver resolver = new PresenceStateResolver(new TeamCodeResolver());

    @Test
    @DisplayName("Kein Live-/künftiges Spiel → IDLE mit konfiguriertem Text")
    void idleWhenNothing() {
        PresenceState state = resolver.resolve(List.of(), null, IDLE);
        assertThat(state.type()).isEqualTo(PresenceState.Type.IDLE);
        assertThat(state.text()).isEqualTo(IDLE);
    }

    @Test
    @DisplayName("Leerer IDLE-Text → Default-Fallback")
    void idleDefaultWhenBlank() {
        assertThat(resolver.resolve(List.of(), null, "  ").text())
                .isEqualTo(PresenceStateResolver.DEFAULT_IDLE);
    }

    @Test
    @DisplayName("Kein Live-Spiel, aber künftiges → UPCOMING-Text mit Kürzeln")
    void upcomingWhenNoLive() {
        Match next = upcoming(1L, "Germany", "France");
        PresenceState state = resolver.resolve(List.of(), next, IDLE);
        assertThat(state.type()).isEqualTo(PresenceState.Type.UPCOMING);
        assertThat(state.text()).isEqualTo("👀 Nächstes: GER vs FRA");
    }

    @Test
    @DisplayName("Laufendes Spiel → LIVE-Text mit Stand")
    void liveWhenInPlay() {
        LiveMatchView live = view(1L, "Germany", "France", 2, 1, KICKOFF, KICKOFF.plusSeconds(60));
        PresenceState state = resolver.resolve(List.of(live), null, IDLE);
        assertThat(state.type()).isEqualTo(PresenceState.Type.LIVE);
        assertThat(state.text()).isEqualTo("⚽ LIVE: GER 2:1 FRA");
    }

    @Test
    @DisplayName("LIVE hat Vorrang vor UPCOMING (FR-002)")
    void livePriorityOverUpcoming() {
        LiveMatchView live = view(1L, "Germany", "France", 0, 0, KICKOFF, null);
        Match next = upcoming(2L, "Spain", "Italy");
        PresenceState state = resolver.resolve(List.of(live), next, IDLE);
        assertThat(state.type()).isEqualTo(PresenceState.Type.LIVE);
    }

    @Test
    @DisplayName("Mehrere Live-Spiele: das zuletzt veränderte gewinnt (FR-013)")
    void multiLiveMostRecentlyChangedWins() {
        LiveMatchView older = view(1L, "Spain", "Italy", 1, 0, KICKOFF, KICKOFF.plusSeconds(30));
        LiveMatchView newer = view(2L, "Germany", "France", 2, 1, KICKOFF.plusSeconds(5), KICKOFF.plusSeconds(120));
        PresenceState state = resolver.resolve(List.of(older, newer), null, IDLE);
        assertThat(state.text()).isEqualTo("⚽ LIVE: GER 2:1 FRA");
    }

    @Test
    @DisplayName("Mehrere Live-Spiele ohne Tor: Tie-Breaker früherer Anpfiff (FR-013)")
    void multiLiveTieBreakEarliestKickoff() {
        LiveMatchView earlier = view(1L, "Germany", "France", 0, 0, KICKOFF, null);
        LiveMatchView later = view(2L, "Spain", "Italy", 0, 0, KICKOFF.plusSeconds(600), null);
        PresenceState state = resolver.resolve(List.of(later, earlier), null, IDLE);
        assertThat(state.text()).isEqualTo("⚽ LIVE: GER 0:0 FRA");
    }

    @Test
    @DisplayName("Anzeigetext enthält keine custom-Emoji-Syntax (FR-010)")
    void noCustomEmoji() {
        LiveMatchView live = view(1L, "Germany", "France", 1, 0, KICKOFF, KICKOFF);
        assertThat(resolver.resolve(List.of(live), null, IDLE).text()).doesNotContain("<:");
        assertThat(resolver.resolve(List.of(), upcoming(2L, "Spain", "Italy"), IDLE).text()).doesNotContain("<:");
        assertThat(resolver.resolve(List.of(), null, IDLE).text()).doesNotContain("<:");
    }

    private static LiveMatchView view(long id, String home, String away, int h, int a,
                                      Instant kickoff, Instant lastChange) {
        return new LiveMatchView(id, home, away, h, a, kickoff, lastChange);
    }

    private static Match upcoming(long id, String home, String away) {
        return new Match(id, home, away, KICKOFF, Stage.GROUP_STAGE, "A", null,
                null, null, null, null, null, MatchStatus.SCHEDULED, false, false);
    }
}
