package com.example.wmtippspiel.sync;

import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.yaml.snakeyaml.Yaml;

/**
 * Bildet Team-Namen der Odds-API auf die kanonischen (football-data.org) Namen
 * ab (research.md R6). Ohne passenden Eintrag wird der Name unverändert
 * zurückgegeben; nicht zuordenbare Quoten werden später verworfen.
 *
 * <p>Quelle: optionales {@code team-mapping.yml} mit Abschnitt {@code teams:}
 * ({@code odds-name: canonical-name}).
 */
@Component
public class TeamNameMapping {

    private static final Logger log = LoggerFactory.getLogger(TeamNameMapping.class);

    private final Map<String, String> oddsToCanonical = new HashMap<>();

    public TeamNameMapping() {
        load();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        ClassPathResource resource = new ClassPathResource("team-mapping.yml");
        if (!resource.exists()) {
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            if (root != null && root.get("teams") instanceof Map<?, ?> map) {
                map.forEach((k, v) -> {
                    if (k != null && v != null) {
                        oddsToCanonical.put(k.toString(), v.toString());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Team-Namens-Mapping konnte nicht geladen werden: {}", e.getMessage());
        }
    }

    /** Kanonischer Name oder – mangels Eintrag – der unveränderte Eingabename. */
    public String canonical(String oddsName) {
        return oddsToCanonical.getOrDefault(oddsName, oddsName);
    }
}
