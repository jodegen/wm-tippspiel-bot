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
 * Liefert den manuell gepflegten TV-Sender je Match (aus {@code tv-channels.yml}).
 * Fehlt ein Eintrag, ist der Sender unbekannt ({@code null}). (FR-004)
 */
@Component
public class ChannelMapping {

    private static final Logger log = LoggerFactory.getLogger(ChannelMapping.class);

    private final Map<Long, String> channels = new HashMap<>();

    public ChannelMapping() {
        load();
    }

    @SuppressWarnings("unchecked")
    private void load() {
        ClassPathResource resource = new ClassPathResource("tv-channels.yml");
        if (!resource.exists()) {
            return;
        }
        try (InputStream in = resource.getInputStream()) {
            Map<String, Object> root = new Yaml().load(in);
            if (root == null) {
                return;
            }
            Object raw = root.get("channels");
            if (raw instanceof Map<?, ?> map) {
                map.forEach((k, v) -> {
                    if (k != null && v != null) {
                        channels.put(Long.parseLong(k.toString()), v.toString());
                    }
                });
            }
        } catch (Exception e) {
            log.warn("TV-Sender-Mapping konnte nicht geladen werden: {}", e.getMessage());
        }
    }

    public String channelFor(long matchId) {
        return channels.get(matchId);
    }

    public Map<Long, String> all() {
        return Map.copyOf(channels);
    }
}
