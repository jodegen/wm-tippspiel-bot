package com.example.wmtippspiel.sync;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.example.wmtippspiel.config.AppProperties;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.Stage;
import com.example.wmtippspiel.persistence.MatchRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests der Quoten-Zuordnung: Alias + Normalisierung + Heim/Gast-Orientierung. */
class OddsSyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-06-14T12:00:00Z");
    private static final Instant KICKOFF = NOW.plusSeconds(86_400);

    private OddsClient client;
    private MatchRepository matches;
    private OddsSyncService service;

    @BeforeEach
    void setUp() {
        client = mock(OddsClient.class);
        matches = mock(MatchRepository.class);
        AppProperties props = new AppProperties(
                null, null, null, null, new AppProperties.Odds(true, null, null), null);
        service = new OddsSyncService(client, new TeamNameMapping(), matches,
                Clock.fixed(NOW, ZoneOffset.UTC), props);
    }

    @Test
    @DisplayName("Alias-Name (Czech Republic→Czechia) wird zugeordnet, Quoten direkt übernommen")
    void aliasMatchSameOrientation() {
        Match m = match(1L, "Czechia", "Mexico");
        when(matches.findTippable(any(), anyInt())).thenReturn(List.of(m));
        when(client.fetchOdds()).thenReturn(List.of(new OddsEvent(
                "Czech Republic", "Mexico", bd("1.5"), bd("3.0"), bd("2.0"))));

        assertThat(service.sync()).isEqualTo(1);
        verify(matches).updateOdds(1L, bd("1.5"), bd("3.0"), bd("2.0"));
    }

    @Test
    @DisplayName("Normalisierung (Bosnia & Herzegovina↔Bosnia-Herzegovina) + vertauschte Orientierung → Quoten getauscht")
    void normalizedMatchSwappedOrientation() {
        Match m = match(2L, "Switzerland", "Bosnia-Herzegovina");
        when(matches.findTippable(any(), anyInt())).thenReturn(List.of(m));
        // Odds-Event listet Bosnia als Heim → muss auf DB-Orientierung gedreht werden.
        when(client.fetchOdds()).thenReturn(List.of(new OddsEvent(
                "Bosnia & Herzegovina", "Switzerland", bd("2.2"), bd("3.1"), bd("1.6"))));

        assertThat(service.sync()).isEqualTo(1);
        // DB-Heim = Switzerland → dessen Quote ist event.oddsAway (1.6); Bosnia (Gast) → 2.2.
        verify(matches).updateOdds(2L, bd("1.6"), bd("3.1"), bd("2.2"));
    }

    @Test
    @DisplayName("Unbekanntes Team bleibt unzugeordnet (kein updateOdds)")
    void unmatchedIsSkipped() {
        when(matches.findTippable(any(), anyInt())).thenReturn(List.of(match(3L, "Germany", "France")));
        when(client.fetchOdds()).thenReturn(List.of(new OddsEvent(
                "Narnia", "Atlantis", bd("1.1"), bd("2.2"), bd("3.3"))));

        assertThat(service.sync()).isZero();
        verify(matches, org.mockito.Mockito.never())
                .updateOdds(org.mockito.ArgumentMatchers.anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("Deaktivierte Quoten → kein Abruf")
    void disabledDoesNothing() {
        AppProperties off = new AppProperties(null, null, null, null, new AppProperties.Odds(false, null, null), null);
        OddsSyncService disabled = new OddsSyncService(client, new TeamNameMapping(), matches,
                Clock.fixed(NOW, ZoneOffset.UTC), off);
        assertThat(disabled.sync()).isZero();
        verify(client, org.mockito.Mockito.never()).fetchOdds();
    }

    private static Match match(long id, String home, String away) {
        return new Match(id, home, away, KICKOFF, Stage.GROUP_STAGE, "A", null,
                null, null, null, null, null, MatchStatus.SCHEDULED, false, false);
    }

    private static BigDecimal bd(String v) {
        return new BigDecimal(v);
    }
}
