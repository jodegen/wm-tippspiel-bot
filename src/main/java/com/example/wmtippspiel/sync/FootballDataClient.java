package com.example.wmtippspiel.sync;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.Stage;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

/**
 * Holt Spielplan & Ergebnisse von football-data.org (contracts/external-apis.md).
 * Bei Fehlern (Timeout/Rate-Limit/Non-2xx) wird geloggt und eine leere Liste
 * geliefert, sodass der Sync-Job den Lauf überspringt statt zu crashen (FR-032).
 */
@Component
public class FootballDataClient {

    private static final Logger log = LoggerFactory.getLogger(FootballDataClient.class);

    private final WebClient webClient;

    public FootballDataClient(@Qualifier("footballDataWebClient") WebClient webClient) {
        this.webClient = webClient;
    }

    /** Liefert alle WM-Spiele (Channel/Odds werden anderweitig ergänzt). */
    @SuppressWarnings("unchecked")
    public List<Match> fetchMatches() {
        try {
            Map<String, Object> response = webClient.get()
                    .uri("/competitions/WC/matches")
                    .retrieve()
                    .bodyToMono(Map.class)
                    .block();

            if (response == null || !(response.get("matches") instanceof List<?> rawMatches)) {
                return List.of();
            }
            List<Match> result = new ArrayList<>(rawMatches.size());
            for (Object item : rawMatches) {
                if (item instanceof Map<?, ?> map) {
                    result.add(mapMatch((Map<String, Object>) map));
                }
            }
            return result;
        } catch (WebClientResponseException e) {
            // Den eigentlichen Fehlertext der API mitloggen (z. B. Plan-/Parameter-Hinweis).
            log.warn("football-data.org Sync übersprungen: {} {} – Body: {}",
                    e.getStatusCode().value(), e.getStatusText(), e.getResponseBodyAsString());
            return List.of();
        } catch (Exception e) {
            log.warn("football-data.org Sync übersprungen: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private Match mapMatch(Map<String, Object> m) {
        long id = ((Number) m.get("id")).longValue();
        String home = teamName(m.get("homeTeam"));
        String away = teamName(m.get("awayTeam"));
        Instant kickoff = Instant.parse((String) m.get("utcDate"));
        Stage stage = mapStage((String) m.get("stage"));
        String groupLabel = mapGroup((String) m.get("group"));
        MatchStatus status = mapStatus((String) m.get("status"));
        Integer matchday = asInt(m.get("matchday"));

        Integer homeScore = null;
        Integer awayScore = null;
        com.example.wmtippspiel.domain.model.MatchWinner winner = null;
        if (m.get("score") instanceof Map<?, ?> score) {
            if (score.get("fullTime") instanceof Map<?, ?> fullTime) {
                homeScore = asInt(fullTime.get("home"));
                awayScore = asInt(fullTime.get("away"));
            }
            winner = mapWinner(score.get("winner"));
        }

        return new Match(id, home, away, kickoff, stage, groupLabel,
                null, null, null, null, homeScore, awayScore, status, false, false, matchday, winner);
    }

    /** football-data {@code score.winner} (HOME_TEAM/AWAY_TEAM/DRAW) → {@link com.example.wmtippspiel.domain.model.MatchWinner}; sonst null. */
    private static com.example.wmtippspiel.domain.model.MatchWinner mapWinner(Object apiWinner) {
        if (!(apiWinner instanceof String s)) {
            return null;
        }
        return switch (s) {
            case "HOME_TEAM" -> com.example.wmtippspiel.domain.model.MatchWinner.HOME_TEAM;
            case "AWAY_TEAM" -> com.example.wmtippspiel.domain.model.MatchWinner.AWAY_TEAM;
            case "DRAW" -> com.example.wmtippspiel.domain.model.MatchWinner.DRAW;
            default -> null;
        };
    }

    private static String teamName(Object team) {
        if (team instanceof Map<?, ?> map && map.get("name") != null) {
            return map.get("name").toString();
        }
        return "TBD";
    }

    private static Integer asInt(Object value) {
        return value instanceof Number n ? n.intValue() : null;
    }

    private static Stage mapStage(String apiStage) {
        if (apiStage == null) {
            return Stage.GROUP_STAGE;
        }
        return switch (apiStage) {
            case "LAST_32" -> Stage.LAST_32;
            case "LAST_16" -> Stage.LAST_16;
            case "QUARTER_FINALS", "QUARTER_FINAL" -> Stage.QUARTER_FINAL;
            case "SEMI_FINALS", "SEMI_FINAL" -> Stage.SEMI_FINAL;
            case "THIRD_PLACE" -> Stage.THIRD_PLACE;
            case "FINAL" -> Stage.FINAL;
            default -> Stage.GROUP_STAGE;
        };
    }

    /** "GROUP_A" → "A"; sonst null. */
    private static String mapGroup(String apiGroup) {
        if (apiGroup == null || !apiGroup.startsWith("GROUP_")) {
            return null;
        }
        return apiGroup.substring("GROUP_".length());
    }

    private static MatchStatus mapStatus(String apiStatus) {
        if (apiStatus == null) {
            return MatchStatus.SCHEDULED;
        }
        return switch (apiStatus) {
            case "IN_PLAY", "PAUSED" -> MatchStatus.IN_PLAY;
            case "FINISHED", "AWARDED" -> MatchStatus.FINISHED;
            case "POSTPONED", "SUSPENDED" -> MatchStatus.POSTPONED;
            case "CANCELLED" -> MatchStatus.CANCELLED;
            default -> MatchStatus.SCHEDULED;
        };
    }
}
