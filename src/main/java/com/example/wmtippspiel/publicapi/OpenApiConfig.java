package com.example.wmtippspiel.publicapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI-/Swagger-Metadaten für die öffentliche Read-only-API (Feature 008).
 * Die Spezifikation und die Swagger-UI werden von springdoc automatisch aus den
 * Controllern erzeugt; hier nur die beschreibenden Kopfdaten.
 *
 * <p>Ist {@code app.public-api.public-base-url} gesetzt, wird sie als explizite
 * Server-URL hinterlegt — nützlich hinter einem TLS-Reverse-Proxy, falls die
 * {@code X-Forwarded-*}-Header nicht greifen (sonst Mixed-Content in Swagger-UI).
 */
@Configuration
public class OpenApiConfig {

    private final String publicBaseUrl;

    public OpenApiConfig(@Value("${app.public-api.public-base-url:}") String publicBaseUrl) {
        this.publicBaseUrl = publicBaseUrl;
    }

    @Bean
    public OpenAPI publicApiOpenAPI() {
        OpenAPI api = new OpenAPI().info(new Info()
                .title("WM 2026 Tippspiel — Öffentliche Read-only-API")
                .version("1.0.0")
                .description("""
                        Rein lesende, öffentliche Endpoints (ohne Authentifizierung) für die externe \
                        Website. Es sind ausschließlich GET-Operationen verfügbar (keine Schreibpfade). \
                        Antworten enthalten nur unbedenkliche Felder — keine Discord-IDs, E-Mails, Tokens \
                        oder internen Schlüssel. Zeitpunkte werden in UTC (ISO-8601) geliefert; die \
                        Anzeige-Formatierung übernimmt das Frontend. Einzeltipps eines Spiels werden \
                        serverseitig erst nach Anpfiff freigegeben.""")
                .license(new License().name("Privat / intern")));
        if (publicBaseUrl != null && !publicBaseUrl.isBlank()) {
            api.addServersItem(new Server().url(publicBaseUrl.trim()));
        }
        return api;
    }
}
