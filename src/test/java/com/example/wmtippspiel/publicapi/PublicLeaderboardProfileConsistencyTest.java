package com.example.wmtippspiel.publicapi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.example.wmtippspiel.config.AppProperties;
import com.example.wmtippspiel.persistence.LeaderboardEntry;
import com.example.wmtippspiel.persistence.LeaderboardSnapshotRepository;
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.persistence.TipRepository;
import com.example.wmtippspiel.publicapi.dto.LeaderboardRowDto;
import com.example.wmtippspiel.publicapi.dto.ProfileDto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

/**
 * Konsistenz zwischen Leaderboard und Profil (Feature 008): Der je Zeile
 * ausgewiesene {@code publicId} ist exakt der Identifier, den
 * {@code GET /players/{publicId}} akzeptiert — derselbe HMAC, keine zweite
 * Ableitung. Echter {@link PublicIdService} + echte {@link PublicQueryService},
 * Repositories gemockt (kein Spring/DB nötig).
 */
class PublicLeaderboardProfileConsistencyTest {

    @Test
    @DisplayName("Leaderboard-publicId löst unter /players/{publicId} denselben Spieler auf")
    void leaderboardPublicIdResolvesToSamePlayer() {
        TipRepository tips = Mockito.mock(TipRepository.class);
        MatchRepository matches = Mockito.mock(MatchRepository.class);
        LeaderboardSnapshotRepository snapshots = Mockito.mock(LeaderboardSnapshotRepository.class);

        LeaderboardEntry alice = new LeaderboardEntry("discord-111", "Alice", 12, 5, 2);
        LeaderboardEntry bob = new LeaderboardEntry("discord-222", "Bob", 8, 4, 1);
        when(tips.leaderboard()).thenReturn(List.of(alice, bob));
        when(snapshots.findAllRanks()).thenReturn(Map.of());
        when(tips.findEvaluatedTipsByUser(Mockito.anyString())).thenReturn(List.of());

        PublicIdService publicIds = new PublicIdService(new AppProperties(
                null, null, null, null, null, new AppProperties.PublicApi(List.of(), "test-secret", 5)));
        PublicQueryService query = new PublicQueryService(matches, tips, snapshots, publicIds, Clock.systemUTC());

        List<LeaderboardRowDto> rows = query.leaderboard();
        assertThat(rows).hasSize(2);

        for (LeaderboardRowDto row : rows) {
            assertThat(row.publicId()).isNotBlank();
            // exakt der Wert, den /players/{publicId} akzeptiert → 200, gleicher displayName
            ProfileDto profile = query.profile(row.publicId());
            assertThat(profile.displayName()).isEqualTo(row.displayName());
        }

        // Querprobe: die publicId stammt aus genau derselben HMAC-Ableitung
        LeaderboardRowDto aliceRow = rows.stream()
                .filter(r -> "Alice".equals(r.displayName())).findFirst().orElseThrow();
        assertThat(aliceRow.publicId()).isEqualTo(publicIds.publicId("discord-111"));
    }
}
