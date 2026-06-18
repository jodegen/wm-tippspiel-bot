package com.example.wmtippspiel.recap;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import com.example.wmtippspiel.discord.publish.AnnounceChannel;
import com.example.wmtippspiel.discord.render.MatchdayRecapEmbed;
import com.example.wmtippspiel.persistence.MatchRepository;
import com.example.wmtippspiel.persistence.MatchdayRecapRepository;
import com.example.wmtippspiel.persistence.MatchdayScore;
import com.example.wmtippspiel.persistence.MatchdayTipRow;
import com.example.wmtippspiel.persistence.TipRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Service;

/**
 * Spieltags-Rückblick (F12): erkennt abgeschlossene Spieltage (alle Spiele eines
 * Recap-Keys FINISHED+evaluated) und postet je Spieltag <b>genau einmal</b> eine
 * Zusammenfassung in den Announce-Channel. Idempotenz über
 * {@link MatchdayRecapRepository#tryClaim} (FR-016); rein lesend bzgl. der Wertung
 * (keine Punkte-Neuberechnung, FR-026).
 */
@Service
public class MatchdayRecapService {

    private static final Logger log = LoggerFactory.getLogger(MatchdayRecapService.class);

    private final MatchRepository matches;
    private final TipRepository tips;
    private final MatchdayRecapRepository recaps;
    private final MatchdayRecapEmbed embed;
    private final AnnounceChannel announce;
    private final Clock clock;

    public MatchdayRecapService(MatchRepository matches,
                                TipRepository tips,
                                MatchdayRecapRepository recaps,
                                MatchdayRecapEmbed embed,
                                AnnounceChannel announce,
                                Clock clock) {
        this.matches = matches;
        this.tips = tips;
        this.recaps = recaps;
        this.embed = embed;
        this.announce = announce;
        this.clock = clock;
    }

    /**
     * Markiert beim Start alle bereits abgeschlossenen Spieltage als erledigt,
     * <b>ohne</b> zu posten. Verhindert, dass ein Deploy mitten im Turnier
     * Rückblicke für längst beendete Spieltage nachschiebt; nur künftig
     * abschließende Spieltage werden gepostet.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void seedExistingOnStartup() {
        int seeded = 0;
        for (String recapKey : matches.findCompletedRecapKeys()) {
            if (recaps.tryClaim(recapKey, clock.instant())) {
                seeded++;
            }
        }
        if (seeded > 0) {
            log.info("Spieltags-Rückblick: {} bereits abgeschlossene Spieltag(e) beim Start als erledigt "
                    + "markiert (kein Nachposten)", seeded);
        }
    }

    /** Postet für jeden neu abgeschlossenen Spieltag genau einen Rückblick (idempotent). */
    public void postCompletedRecaps() {
        for (String recapKey : matches.findCompletedRecapKeys()) {
            if (recaps.tryClaim(recapKey, clock.instant())) {
                announce.postPlain(embed.build(buildRecap(recapKey)));
                log.info("Spieltags-Rückblick gepostet: {}", recapKey);
            }
        }
    }

    private MatchdayRecap buildRecap(String recapKey) {
        List<MatchdayScore> board = tips.matchdayLeaderboard(recapKey);
        List<MatchdayScore> topScorers = board.stream().filter(s -> s.points() > 0).toList();
        List<String> emptyHanded = board.stream()
                .filter(s -> s.points() == 0)
                .map(MatchdayScore::username)
                .toList();
        Optional<MatchdayTipRow> best = MatchdayRecaps.bestTip(tips.matchdayEvaluatedTips(recapKey));
        return new MatchdayRecap(MatchdayRecaps.label(recapKey), topScorers, best, emptyHanded);
    }
}
