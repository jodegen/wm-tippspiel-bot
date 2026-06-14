package com.example.wmtippspiel.sync;

import java.math.BigDecimal;
import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.example.wmtippspiel.config.AppProperties;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.persistence.MatchRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Ergänzt Quoten an passenden Spielen (optional, FR-003). Zuordnung über
 * normalisierte Teamnamen (R6): Alias-Map für echte Wort-Unterschiede plus
 * Diakritika-/Satzzeichen-tolerante Normalisierung; die Heim/Gast-Reihenfolge
 * der Odds-Quelle darf von der DB abweichen. Nicht zuordenbare Events werden
 * geloggt (mit den Original-Odds-Namen) und verworfen.
 */
@Service
public class OddsSyncService {

    private static final Logger log = LoggerFactory.getLogger(OddsSyncService.class);
    private static final int CANDIDATE_LIMIT = 256;

    private final OddsClient client;
    private final TeamNameMapping nameMapping;
    private final MatchRepository matches;
    private final Clock clock;
    private final boolean enabled;

    public OddsSyncService(OddsClient client,
                           TeamNameMapping nameMapping,
                           MatchRepository matches,
                           Clock clock,
                           AppProperties properties) {
        this.client = client;
        this.nameMapping = nameMapping;
        this.matches = matches;
        this.clock = clock;
        this.enabled = properties.odds().enabled();
    }

    public int sync() {
        if (!enabled) {
            return 0;
        }
        // Künftige, tippbare Spiele als Zuordnungskandidaten (Quoten betreffen nur
        // anstehende Begegnungen). Index über ein reihenfolge-unabhängiges Team-Paar.
        Map<String, Match> byPair = new HashMap<>();
        for (Match m : matches.findTippable(clock.instant(), CANDIDATE_LIMIT)) {
            byPair.put(pairKey(m.home(), m.away()), m);
        }

        int updated = 0;
        int unmatched = 0;
        for (OddsEvent event : client.fetchOdds()) {
            String home = nameMapping.canonical(event.homeTeam());
            String away = nameMapping.canonical(event.awayTeam());
            Match match = byPair.get(pairKey(home, away));
            if (match == null) {
                unmatched++;
                log.info("Odds ohne Zuordnung: \"{}\" vs \"{}\" (kanonisch: \"{}\" vs \"{}\")",
                        event.homeTeam(), event.awayTeam(), home, away);
                continue;
            }
            // Heim/Gast ggf. an die DB-Orientierung anpassen, damit odds_home/away stimmen.
            boolean sameOrientation = TeamNameMapping.normalizeForMatch(home)
                    .equals(TeamNameMapping.normalizeForMatch(match.home()));
            BigDecimal oddsHome = sameOrientation ? event.oddsHome() : event.oddsAway();
            BigDecimal oddsAway = sameOrientation ? event.oddsAway() : event.oddsHome();
            matches.updateOdds(match.id(), oddsHome, event.oddsDraw(), oddsAway);
            updated++;
        }
        log.info("Odds-Sync abgeschlossen: {} Spiele aktualisiert, {} ohne Zuordnung", updated, unmatched);
        return updated;
    }

    /** Reihenfolge-unabhängiger Schlüssel aus den normalisierten Teamnamen. */
    private static String pairKey(String a, String b) {
        String na = TeamNameMapping.normalizeForMatch(a);
        String nb = TeamNameMapping.normalizeForMatch(b);
        return na.compareTo(nb) <= 0 ? na + "|" + nb : nb + "|" + na;
    }
}
