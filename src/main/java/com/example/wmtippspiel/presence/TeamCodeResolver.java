package com.example.wmtippspiel.presence;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Löst Teamnamen in kompakte FIFA-Kürzel auf (F9, Key Entity „Team-Kürzel-Mapping").
 * Lädt das statisch gepflegte Mapping aus {@code presence/team-codes.properties}
 * (research.md R7). Fehlt ein Eintrag, wird der Klartextname defensiv gekürzt,
 * sodass der Presence-Text die JDA-Activity-Längengrenze nie sprengt (research.md R8).
 */
@Component
public class TeamCodeResolver {

    private static final Logger log = LoggerFactory.getLogger(TeamCodeResolver.class);
    private static final String RESOURCE = "/presence/team-codes.properties";
    /** Fallback-Länge eines unbekannten Teamnamens (zwei Codes + Stand bleiben << 128). */
    private static final int FALLBACK_MAX = 12;

    private final Properties codes = new Properties();

    public TeamCodeResolver() {
        try (InputStream in = TeamCodeResolver.class.getResourceAsStream(RESOURCE)) {
            if (in != null) {
                codes.load(new java.io.InputStreamReader(in, StandardCharsets.UTF_8));
            } else {
                log.warn("Team-Kürzel-Ressource {} nicht gefunden – nutze Klartext-Fallback", RESOURCE);
            }
        } catch (IOException e) {
            log.warn("Team-Kürzel-Ressource {} nicht ladbar – nutze Klartext-Fallback", RESOURCE, e);
        }
    }

    /** Kürzel für {@code teamName} oder defensiv gekürzter Klartext (nie {@code null}). */
    public String code(String teamName) {
        if (teamName == null || teamName.isBlank()) {
            return "?";
        }
        String hit = codes.getProperty(teamName);
        if (hit != null && !hit.isBlank()) {
            return hit;
        }
        String trimmed = teamName.strip();
        return trimmed.length() <= FALLBACK_MAX ? trimmed : trimmed.substring(0, FALLBACK_MAX);
    }
}
