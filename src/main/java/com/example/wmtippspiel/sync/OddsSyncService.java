package com.example.wmtippspiel.sync;

import java.util.Optional;

import com.example.wmtippspiel.config.AppProperties;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.persistence.MatchRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Ergänzt Quoten an passenden Spielen (optional, FR-003). Zuordnung über das
 * Team-Namens-Mapping (R6); nicht zuordenbare Events werden verworfen.
 */
@Service
public class OddsSyncService {

    private static final Logger log = LoggerFactory.getLogger(OddsSyncService.class);

    private final OddsClient client;
    private final TeamNameMapping nameMapping;
    private final MatchRepository matches;
    private final boolean enabled;

    public OddsSyncService(OddsClient client,
                           TeamNameMapping nameMapping,
                           MatchRepository matches,
                           AppProperties properties) {
        this.client = client;
        this.nameMapping = nameMapping;
        this.matches = matches;
        this.enabled = properties.odds().enabled();
    }

    public int sync() {
        if (!enabled) {
            return 0;
        }
        int updated = 0;
        int unmatched = 0;
        for (OddsEvent event : client.fetchOdds()) {
            String home = nameMapping.canonical(event.homeTeam());
            String away = nameMapping.canonical(event.awayTeam());
            Optional<Match> match = matches.findByTeams(home, away);
            if (match.isPresent()) {
                matches.updateOdds(match.get().id(), event.oddsHome(), event.oddsDraw(), event.oddsAway());
                updated++;
            } else {
                unmatched++;
                // Original-Odds-API-Namen loggen → fehlende Mapping-Einträge sind so
                // direkt aus dem Log in team-mapping.yml übertragbar.
                log.info("Odds ohne Zuordnung: \"{}\" vs \"{}\" (kanonisch: \"{}\" vs \"{}\")",
                        event.homeTeam(), event.awayTeam(), home, away);
            }
        }
        log.info("Odds-Sync abgeschlossen: {} Spiele aktualisiert, {} ohne Zuordnung", updated, unmatched);
        return updated;
    }
}
