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
import com.example.wmtippspiel.publicapi.dto.LeaderboardRowDto;
import com.example.wmtippspiel.publicapi.dto.LiveMatchDto;
import com.example.wmtippspiel.publicapi.dto.MatchDto;

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
    private final Clock clock;

    public PublicQueryService(MatchRepository matches, TipRepository tips,
                              LeaderboardSnapshotRepository snapshots, Clock clock) {
        this.matches = matches;
        this.tips = tips;
        this.snapshots = snapshots;
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
        return ranked.stream().map(PublicMappers::toLeaderboardRow).toList();
    }
}
