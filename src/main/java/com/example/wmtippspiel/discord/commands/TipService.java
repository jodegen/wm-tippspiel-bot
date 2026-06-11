package com.example.wmtippspiel.discord.commands;

import java.time.Clock;
import java.util.Optional;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.Tip;
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.persistence.TipRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Gemeinsame Tipp-Logik für den Slash-Command ({@code /tipp}) und den geführten
 * Dropdown→Modal-Flow ({@code /tippen}). Kapselt Tippbarkeitsprüfung (FR-007/009)
 * und Upsert (FR-006).
 */
@Service
public class TipService {

    private static final Logger log = LoggerFactory.getLogger(TipService.class);

    public enum Result { OK, NOT_FOUND, NOT_TIPPABLE }

    public record Outcome(Result result, Match match) {
    }

    private final MatchRepository matches;
    private final TipRepository tips;
    private final Clock clock;

    public TipService(MatchRepository matches, TipRepository tips, Clock clock) {
        this.matches = matches;
        this.tips = tips;
        this.clock = clock;
    }

    public Outcome submit(String userId, String username, long matchId, int homeTip, int awayTip) {
        Optional<Match> match = matches.findById(matchId);
        if (match.isEmpty()) {
            return new Outcome(Result.NOT_FOUND, null);
        }
        Match m = match.get();
        if (!m.isTippable(clock.instant())) {
            return new Outcome(Result.NOT_TIPPABLE, m);
        }
        tips.upsert(new Tip(userId, matchId, username, homeTip, awayTip, clock.instant(), 0));
        log.info("Tipp gespeichert: user={} match={} {}:{}", userId, matchId, homeTip, awayTip);
        return new Outcome(Result.OK, m);
    }

    public Optional<Tip> existingTip(String userId, long matchId) {
        return tips.findByUserAndMatch(userId, matchId);
    }
}
