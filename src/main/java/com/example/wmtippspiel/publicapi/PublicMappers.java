package com.example.wmtippspiel.publicapi;

import java.util.List;

import com.example.wmtippspiel.discord.commands.UserProfile;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.Tip;
import com.example.wmtippspiel.leaderboard.RankedRow;
import com.example.wmtippspiel.persistence.ProfileTipRow;
import com.example.wmtippspiel.publicapi.dto.LeaderboardRowDto;
import com.example.wmtippspiel.publicapi.dto.LiveMatchDto;
import com.example.wmtippspiel.publicapi.dto.MatchDto;
import com.example.wmtippspiel.publicapi.dto.PointDistributionDto;
import com.example.wmtippspiel.publicapi.dto.ProfileDto;
import com.example.wmtippspiel.publicapi.dto.ProfileTipDto;
import com.example.wmtippspiel.publicapi.dto.PublicTipDto;

/**
 * Reine Abbildung interner Entities auf öffentliche DTOs (Feature 008). Einzige
 * Stelle, an der für die Public-API gemappt wird; Domänen-{@link Tip}/
 * {@link Match} werden NIE direkt serialisiert. Die DTOs besitzen kein Feld für
 * {@code user_id}/E-Mail/Tokens/interne Schlüssel ⇒ strukturelle Garantie gegen
 * Leaks (FR-003).
 */
public final class PublicMappers {

    private PublicMappers() {
    }

    public static MatchDto toMatchDto(Match m) {
        return new MatchDto(
                m.id(),
                m.home(),
                m.away(),
                m.kickoff(),
                m.stage().name(),
                m.groupLabel(),
                m.channel(),
                m.oddsHome(),
                m.oddsDraw(),
                m.oddsAway(),
                m.homeScore(),
                m.awayScore(),
                m.status().name(),
                m.matchday());
    }

    public static LiveMatchDto toLiveMatchDto(Match m) {
        return new LiveMatchDto(
                m.id(),
                m.home(),
                m.away(),
                m.kickoff(),
                m.homeScore(),
                m.awayScore(),
                m.status().name(),
                m.groupLabel(),
                m.matchday());
    }

    public static LeaderboardRowDto toLeaderboardRow(RankedRow r) {
        return new LeaderboardRowDto(
                r.rank(),
                r.entry().username(),
                r.entry().totalPoints(),
                r.entry().exactHits(),
                r.delta().symbol());
    }

    /**
     * @param evaluated ob das zugehörige Spiel bereits gewertet ist; nur dann
     *                  werden Punkte ausgewiesen, sonst {@code null}.
     */
    public static PublicTipDto toPublicTip(Tip t, boolean evaluated) {
        return new PublicTipDto(
                t.username(),
                t.homeScore(),
                t.awayScore(),
                evaluated ? t.points() : null);
    }

    public static ProfileTipDto toProfileTip(ProfileTipRow row) {
        return new ProfileTipDto(
                row.home(),
                row.away(),
                row.tipHome(),
                row.tipAway(),
                row.resultHome(),
                row.resultAway(),
                row.points());
    }

    public static ProfileDto toProfileDto(String publicId, UserProfile p, List<ProfileTipRow> history) {
        List<ProfileTipDto> mappedHistory = history.stream().map(PublicMappers::toProfileTip).toList();
        return new ProfileDto(
                publicId,
                p.username(),
                p.rank(),
                p.totalPoints(),
                p.exactHits(),
                p.evaluatedTips(),
                p.hitRatePercent(),
                new PointDistributionDto(p.count4(), p.count3(), p.count2(), p.count0()),
                p.best() == null ? null : toProfileTip(p.best()),
                p.worst() == null ? null : toProfileTip(p.worst()),
                mappedHistory);
    }
}
