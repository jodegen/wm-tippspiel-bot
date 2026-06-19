package com.example.wmtippspiel.publicapi;

import java.time.Clock;
import java.util.List;
import java.util.Map;

import com.example.wmtippspiel.leaderboard.LeaderboardRanking;
import com.example.wmtippspiel.leaderboard.RankedRow;
import com.example.wmtippspiel.persistence.LeaderboardEntry;
import com.example.wmtippspiel.persistence.LeaderboardSnapshotRepository;
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.persistence.TipRepository;
import com.example.wmtippspiel.discord.commands.ProfileStats;
import com.example.wmtippspiel.discord.commands.UserProfile;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.persistence.ProfileTipRow;
import com.example.wmtippspiel.publicapi.dto.LeaderboardRowDto;
import com.example.wmtippspiel.publicapi.dto.LiveMatchDto;
import com.example.wmtippspiel.publicapi.dto.MatchDto;
import com.example.wmtippspiel.publicapi.dto.MatchTipsDto;
import com.example.wmtippspiel.publicapi.dto.ProfileDto;
import com.example.wmtippspiel.publicapi.dto.PublicTipDto;

import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

/**
 * Liest die öffentlichen Sichten ausschließlich aus den bestehenden Repositories
 * und der vorhandenen reinen Logik (Feature 008, FR-004 — nur lesen, nichts neu
 * berechnen). Spielplan/Leaderboard sind kurz gecacht (FR-021); Live und
 * Tipps-pro-Spiel bleiben ungecacht (Frische bzw. Reveal-Korrektheit).
 */
@Service
public class PublicQueryService {

    private final MatchRepository matches;
    private final TipRepository tips;
    private final LeaderboardSnapshotRepository snapshots;
    private final PublicIdService publicIds;
    private final Clock clock;

    public PublicQueryService(MatchRepository matches, TipRepository tips,
                              LeaderboardSnapshotRepository snapshots, PublicIdService publicIds, Clock clock) {
        this.matches = matches;
        this.tips = tips;
        this.snapshots = snapshots;
        this.publicIds = publicIds;
        this.clock = clock;
    }

    /** Vollständiger bzw. nach Phase/Gruppe/Spieltag gefilterter Spielplan (FR-006/007). */
    @Cacheable(cacheNames = "schedule")
    public List<MatchDto> schedule(String stage, String group, Integer matchday) {
        return matches.findAll().stream()
                .filter(m -> stage == null || m.stage().name().equalsIgnoreCase(stage))
                .filter(m -> group == null || group.equalsIgnoreCase(m.groupLabel()))
                .filter(m -> matchday == null || matchday.equals(m.matchday()))
                .map(PublicMappers::toMatchDto)
                .toList();
    }

    /** Aktuell laufende Spiele mit aktuellem Stand (FR-008); leere Liste, wenn keines läuft. */
    public List<LiveMatchDto> liveMatches() {
        return matches.findInPlay().stream().map(PublicMappers::toLiveMatchDto).toList();
    }

    /** Vollständige Rangliste inkl. Rang-Veränderung (FR-009/010/011). */
    @Cacheable(cacheNames = "leaderboard")
    public List<LeaderboardRowDto> leaderboard() {
        List<LeaderboardEntry> entries = tips.leaderboard();
        Map<String, Integer> previousRanks = snapshots.findAllRanks();
        List<RankedRow> ranked = LeaderboardRanking.compute(entries, previousRanks);
        return ranked.stream()
                .map(r -> PublicMappers.toLeaderboardRow(r, publicIds.publicId(r.entry().userId())))
                .toList();
    }

    /**
     * Tipps eines Spiels — NUR nach Anpfiff (FR-012/013). Serverseitiges,
     * konservatives Gate: {@code now() (UTC) ≥ kickoff} UND {@code revealed}.
     * Ist das Gate nicht erfüllt, werden die Tipps GAR NICHT geladen und eine
     * reveal-gesperrte Antwort zurückgegeben — kein versteckter Leak im JSON.
     *
     * @throws PublicNotFoundException wenn das Spiel unbekannt ist (FR-019)
     */
    public MatchTipsDto matchTips(long matchId) {
        Match match = matches.findById(matchId).orElseThrow(PublicNotFoundException::new);
        boolean kickoffReached = !match.kickoff().isAfter(clock.instant());
        if (!kickoffReached || !match.revealed()) {
            return MatchTipsDto.locked(matchId);
        }
        List<PublicTipDto> released = tips.findByMatch(matchId).stream()
                .map(t -> PublicMappers.toPublicTip(t, match.evaluated()))
                .toList();
        return new MatchTipsDto(matchId, true, released);
    }

    /**
     * Öffentliches Spielerprofil über den stabilen {@code publicId} (FR-015/016).
     * Auflösung per Enumeration über die Teilnehmer der Rangliste; der Rang stammt
     * aus derselben gerankten Liste. Statistik/Verteilung/best-worst werden über
     * die bestehende reine {@link ProfileStats} abgeleitet (FR-004/018). Die
     * Historie umfasst nur gewertete Tipps und wahrt damit die Reveal-Regel (FR-017).
     *
     * @throws PublicNotFoundException wenn der Identifier unbekannt ist (FR-019)
     */
    public ProfileDto profile(String publicId) {
        List<RankedRow> ranked = LeaderboardRanking.compute(tips.leaderboard(), snapshots.findAllRanks());
        RankedRow mine = ranked.stream()
                .filter(r -> publicId != null && publicId.equals(publicIds.publicId(r.entry().userId())))
                .findFirst()
                .orElseThrow(PublicNotFoundException::new);

        String userId = mine.entry().userId();
        List<ProfileTipRow> history = tips.findEvaluatedTipsByUser(userId);
        UserProfile profile = ProfileStats.build(mine.entry().username(), mine, history);
        return PublicMappers.toProfileDto(publicId, profile, history);
    }
}
