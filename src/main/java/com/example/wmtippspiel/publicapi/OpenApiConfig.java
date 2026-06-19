package com.example.wmtippspiel.publicapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI-/Swagger-Metadaten für die öffentliche Read-only-API (Feature 008).
 * Die Spezifikation und die Swagger-UI werden von springdoc automatisch aus den
 * Controllern erzeugt; hier nur die beschreibenden Kopfdaten.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI publicApiOpenAPI() {
        return new OpenAPI().info(new Info()
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
    }
}
