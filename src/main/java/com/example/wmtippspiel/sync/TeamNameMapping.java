package com.example.wmtippspiel.sync;

import java.io.InputStream;
import java.text.Normalizer;
import java.util.HashMap;
import java.util.Locale;
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
                        oddsToCanonical.put(normalize(k.toString()), v.toString());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("Team-Namens-Mapping konnte nicht geladen werden: {}", e.getMessage());
        }
    }

    /** Kanonischer Name oder – mangels Eintrag – der unveränderte Eingabename. */
    public String canonical(String oddsName) {
        if (oddsName == null) {
            return null;
        }
        return oddsToCanonical.getOrDefault(normalize(oddsName), oddsName);
    }

    /** Schlüssel-Normalisierung für die Alias-Map: case-insensitiv, ohne Randleerzeichen. */
    private static String normalize(String name) {
        return name.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * Vergleichs-Normalisierung für das Team-Matching: entfernt Diakritika,
     * Groß-/Kleinschreibung und alle Nicht-Alphanumerischen. Damit matchen
     * Schreibvarianten ohne Wort-Unterschied automatisch, z. B.
     * „Bosnia &amp; Herzegovina" ↔ „Bosnia-Herzegovina" oder „Türkiye" ↔ „Turkiye".
     * Echte Wort-Unterschiede (Czech Republic↔Czechia) bleiben Sache der Alias-Map.
     */
    public static String normalizeForMatch(String name) {
        if (name == null) {
            return "";
        }
        String stripped = Normalizer.normalize(name, Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return stripped.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]", "");
    }
}
