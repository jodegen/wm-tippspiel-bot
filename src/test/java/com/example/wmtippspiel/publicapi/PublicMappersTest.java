package com.example.wmtippspiel.publicapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import com.example.wmtippspiel.discord.commands.ProfileStats;
import com.example.wmtippspiel.discord.commands.UserProfile;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.Stage;
import com.example.wmtippspiel.domain.model.Tip;
import com.example.wmtippspiel.leaderboard.LeaderboardRanking;
import com.example.wmtippspiel.leaderboard.RankedRow;
import com.example.wmtippspiel.persistence.LeaderboardEntry;
import com.example.wmtippspiel.persistence.ProfileTipRow;
import com.example.wmtippspiel.publicapi.dto.MatchTipsDto;
import com.example.wmtippspiel.publicapi.dto.ProfileDto;

import com.fasterxml.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Datenschutz-Garantie (FR-003 / SC-001): Die öffentlichen DTOs dürfen — als
 * JSON serialisiert — keinerlei sensible Schlüssel enthalten. Reiner Unit-Test
 * (kein Spring/DB/Docker).
 */
class PublicMappersTest {

    private static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    private static final List<String> FORBIDDEN = List.of("user_id", "userId", "email", "token", "discord");

    private static Match match(long id, MatchStatus status) {
        return new Match(id, "Deutschland", "Frankreich", Instant.parse("2026-06-20T19:00:00Z"),
                Stage.GROUP_STAGE, "A", "ARD", new BigDecimal("1.80"), new BigDecimal("3.40"),
                new BigDecimal("4.20"), 2, 1, status, true, true, 1);
    }

    private static String json(Object dto) throws Exception {
        return JSON.writeValueAsString(dto).toLowerCase();
    }

    private static void assertNoForbidden(String serialized) {
        for (String key : FORBIDDEN) {
            assertThat(serialized).doesNotContain(key.toLowerCase());
        }
    }

    @Test
    @DisplayName("MatchDto/LiveMatchDto leaken keine sensiblen Felder")
    void matchDtosAreClean() throws Exception {
        assertNoForbidden(json(PublicMappers.toMatchDto(match(1, MatchStatus.FINISHED))));
        assertNoForbidden(json(PublicMappers.toLiveMatchDto(match(2, MatchStatus.IN_PLAY))));
    }

    @Test
    @DisplayName("LeaderboardRowDto enthält keine user_id")
    void leaderboardRowIsClean() throws Exception {
        LeaderboardEntry entry = new LeaderboardEntry("discord-123456", "Alice", 12, 5, 2);
        List<RankedRow> ranked = LeaderboardRanking.compute(List.of(entry), java.util.Map.of());
        String serialized = json(PublicMappers.toLeaderboardRow(ranked.get(0), "pub-abc"));
        assertNoForbidden(serialized);
        assertThat(serialized).contains("alice");
    }

    @Test
    @DisplayName("PublicTipDto: Punkte nur bei gewertetem Spiel; keine user_id")
    void publicTipIsClean() throws Exception {
        Tip tip = new Tip("discord-999", 1L, "Bob", 2, 1, Instant.parse("2026-06-20T18:00:00Z"), 4);
        assertNoForbidden(json(PublicMappers.toPublicTip(tip, true)));
        assertThat(PublicMappers.toPublicTip(tip, false).points()).isNull();
        assertThat(PublicMappers.toPublicTip(tip, true).points()).isEqualTo(4);
    }

    @Test
    @DisplayName("ProfileDto leakt keine sensiblen Felder")
    void profileDtoIsClean() throws Exception {
        ProfileTipRow row = new ProfileTipRow("Deutschland", "Frankreich", 2, 1, 2, 1, 4,
                4711L, Instant.parse("2026-06-20T19:00:00Z"), "GROUP_STAGE");
        UserProfile profile = ProfileStats.build("Alice", null, List.of(row));
        ProfileDto dto = PublicMappers.toProfileDto("pub-abc", profile, List.of(row));
        assertNoForbidden(json(dto));
        // Spielbezug ist vorhanden und unterscheidet Wiederholungsbegegnungen
        assertThat(dto.history().get(0).matchId()).isEqualTo(4711L);
        assertThat(dto.history().get(0).stage()).isEqualTo("GROUP_STAGE");
        assertThat(dto.history().get(0).kickoffUtc()).isEqualTo(Instant.parse("2026-06-20T19:00:00Z"));
    }

    @Test
    @DisplayName("Reveal-gesperrtes MatchTipsDto enthält keine Tipps")
    void lockedMatchTipsHasNoTips() throws Exception {
        MatchTipsDto locked = MatchTipsDto.locked(42L);
        assertThat(locked.released()).isFalse();
        assertThat(locked.tips()).isEmpty();
        assertNoForbidden(json(locked));
    }
}
