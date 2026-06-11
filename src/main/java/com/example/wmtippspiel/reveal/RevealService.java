package com.example.wmtippspiel.reveal;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.Tip;
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.persistence.TipRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Legt Tipps automatisch bei Anpfiff offen (FR-011/012/013).
 *
 * <p>Der Trigger richtet sich allein nach der gespeicherten Anpfiffzeit
 * (UTC-{@link Clock}), nicht nach dem API-Status (FR-013). Das Setzen von
 * {@code revealed} ist mit dem Posten gekoppelt und idempotent über Neustarts
 * (FR-031). Timing-Logik ist verfassungsmäßig test-pflichtig (Prinzip III).
 */
@Service
public class RevealService {

    private static final Logger log = LoggerFactory.getLogger(RevealService.class);

    private final MatchRepository matches;
    private final TipRepository tips;
    private final RevealPublisher publisher;
    private final Clock clock;

    public RevealService(MatchRepository matches, TipRepository tips, RevealPublisher publisher, Clock clock) {
        this.matches = matches;
        this.tips = tips;
        this.publisher = publisher;
        this.clock = clock;
    }

    /** Legt alle fälligen Spiele offen und gibt deren Anzahl zurück. */
    @Transactional
    public int revealDueMatches() {
        Instant now = clock.instant();
        int revealed = 0;
        for (Match match : matches.findUnrevealed()) {
            if (!match.isRevealEligible(now)) {
                continue;
            }
            List<Tip> matchTips = tips.findByMatch(match.id());
            publisher.publishReveal(match, matchTips);
            matches.markRevealed(match.id());
            revealed++;
            log.info("Spiel {} offengelegt ({} Tipps)", match.id(), matchTips.size());
        }
        return revealed;
    }
}
