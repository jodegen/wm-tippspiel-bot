package com.example.wmtippspiel.sync;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.example.wmtippspiel.config.AppProperties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Holt h2h-Quoten von The Odds API (contracts/external-apis.md). Fehler/leere
 * Antworten liefern eine leere Liste; Quoten sind optional (FR-003).
 */
@Component
public class OddsClient {

    private static final Logger log = LoggerFactory.getLogger(OddsClient.class);

    private final WebClient webClient;
    private final String apiKey;

    public OddsClient(@Qualifier("oddsWebClient") WebClient webClient, AppProperties properties) {
        this.webClient = webClient;
        this.apiKey = properties.odds().apiKey();
    }

    @SuppressWarnings("unchecked")
    public List<OddsEvent> fetchOdds() {
        try {
            List<Map<String, Object>> events = webClient.get()
                    .uri(uri -> uri.path("/sports/soccer_fifa_world_cup/odds")
                            .queryParam("regions", "eu")
                            .queryParam("markets", "h2h")
                            .queryParam("apiKey", apiKey)
                            .build())
                    .retrieve()
                    .bodyToMono(List.class)
                    .block();

            if (events == null) {
                return List.of();
            }
            List<OddsEvent> result = new ArrayList<>(events.size());
            for (Map<String, Object> event : events) {
                OddsEvent mapped = mapEvent(event);
                if (mapped != null) {
                    result.add(mapped);
                }
            }
            return result;
        } catch (Exception e) {
            log.warn("Odds-Sync übersprungen: {}", e.getMessage());
            return List.of();
        }
    }

    @SuppressWarnings("unchecked")
    private OddsEvent mapEvent(Map<String, Object> event) {
        String home = (String) event.get("home_team");
        String away = (String) event.get("away_team");
        if (home == null || away == null) {
            return null;
        }
        if (!(event.get("bookmakers") instanceof List<?> bookmakers) || bookmakers.isEmpty()) {
            return new OddsEvent(home, away, null, null, null);
        }
        // Erster Bookmaker mit h2h-Markt.
        for (Object bm : bookmakers) {
            if (bm instanceof Map<?, ?> bookmaker && bookmaker.get("markets") instanceof List<?> markets) {
                for (Object mk : markets) {
                    if (mk instanceof Map<?, ?> market && "h2h".equals(market.get("key"))
                            && market.get("outcomes") instanceof List<?> outcomes) {
                        return fromOutcomes(home, away, (List<Map<String, Object>>) outcomes);
                    }
                }
            }
        }
        return new OddsEvent(home, away, null, null, null);
    }

    private OddsEvent fromOutcomes(String home, String away, List<Map<String, Object>> outcomes) {
        BigDecimal oddsHome = null;
        BigDecimal oddsDraw = null;
        BigDecimal oddsAway = null;
        for (Map<String, Object> outcome : outcomes) {
            String name = (String) outcome.get("name");
            BigDecimal price = asDecimal(outcome.get("price"));
            if (name == null) {
                continue;
            }
            if (name.equals(home)) {
                oddsHome = price;
            } else if (name.equals(away)) {
                oddsAway = price;
            } else if (name.equalsIgnoreCase("Draw")) {
                oddsDraw = price;
            }
        }
        return new OddsEvent(home, away, oddsHome, oddsDraw, oddsAway);
    }

    private static BigDecimal asDecimal(Object value) {
        if (value instanceof Number n) {
            return BigDecimal.valueOf(n.doubleValue());
        }
        return null;
    }
}
