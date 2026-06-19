package com.example.wmtippspiel.publicapi;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Stellt sicher, dass die OpenAPI-Kopfdaten (Feature 008) korrekt aufgebaut sind.
 * Reiner Unit-Test; die eigentliche Spec/Swagger-UI generiert springdoc zur Laufzeit.
 */
class OpenApiConfigTest {

    @Test
    @DisplayName("OpenAPI-Metadaten sind gesetzt")
    void metadataPresent() {
        OpenAPI api = new OpenApiConfig("").publicApiOpenAPI();
        assertThat(api.getInfo()).isNotNull();
        assertThat(api.getInfo().getTitle()).contains("Öffentliche Read-only-API");
        assertThat(api.getInfo().getVersion()).isEqualTo("1.0.0");
        assertThat(api.getInfo().getDescription()).contains("nach Anpfiff");
    }

    @Test
    @DisplayName("Ohne public-base-url: keine explizite Server-URL")
    void noExplicitServerByDefault() {
        assertThat(new OpenApiConfig("  ").publicApiOpenAPI().getServers()).isNullOrEmpty();
    }

    @Test
    @DisplayName("Mit public-base-url: explizite Server-URL gesetzt")
    void explicitServerWhenConfigured() {
        OpenAPI api = new OpenApiConfig("https://api.xenoria.de").publicApiOpenAPI();
        assertThat(api.getServers()).hasSize(1);
        assertThat(api.getServers().get(0).getUrl()).isEqualTo("https://api.xenoria.de");
    }
}
