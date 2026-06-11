package com.example.wmtippspiel.sync;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.evaluation.EvaluationService;
import com.example.wmtippspiel.persistence.MatchRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Synchronisiert {@link Match}-Daten aus der externen Quelle in die DB.
 *
 * <p>Verschiebungen übernehmen automatisch die neue Anpfiffzeit (FR-004a),
 * Absagen werden über den Status getragen (FR-004b). Ändert sich der Endstand
 * eines bereits ausgewerteten Spiels, wird sofort eine Neubewertung mit
 * Korrektur-Hinweis angestoßen (FR-017a).
 */
@Service
public class MatchSyncService {

    private static final Logger log = LoggerFactory.getLogger(MatchSyncService.class);

    private final FootballDataClient client;
    private final MatchRepository matches;
    private final ChannelMapping channelMapping;
    private final EvaluationService evaluationService;

    public MatchSyncService(FootballDataClient client,
                            MatchRepository matches,
                            ChannelMapping channelMapping,
                            EvaluationService evaluationService) {
        this.client = client;
        this.matches = matches;
        this.channelMapping = channelMapping;
        this.evaluationService = evaluationService;
    }

    public int sync() {
        List<Match> fetched = client.fetchMatches();
        int corrections = 0;
        for (Match incoming : fetched) {
            Optional<Match> existing = matches.findById(incoming.id());
            Match toStore = incoming.withChannel(channelMapping.channelFor(incoming.id()));
            matches.upsert(toStore);

            if (needsReEvaluation(existing.orElse(null), toStore)) {
                log.info("Endstand-Korrektur für Spiel {} erkannt – Neubewertung wird angestoßen", toStore.id());
                evaluationService.reevaluate(toStore);
                corrections++;
            }
        }
        log.info("Match-Sync abgeschlossen: {} Spiele, {} Neubewertungen", fetched.size(), corrections);
        return fetched.size();
    }

    /** Bereits ausgewertetes, beendetes Spiel mit geändertem Endstand → Neubewertung (FR-017a). */
    static boolean needsReEvaluation(Match existing, Match incoming) {
        if (existing == null || !existing.evaluated()) {
            return false;
        }
        if (incoming.status() != MatchStatus.FINISHED
                || incoming.homeScore() == null || incoming.awayScore() == null) {
            return false;
        }
        return !Objects.equals(existing.homeScore(), incoming.homeScore())
                || !Objects.equals(existing.awayScore(), incoming.awayScore());
    }
}
