package com.example.wmtippspiel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Einstiegspunkt des WM-2026-Tippspiel-Discord-Bots.
 *
 * <p>Der Prozess läuft dauerhaft: JDA hält die Discord-Gateway-Verbindung
 * (Verfassung Prinzip V), {@code @Scheduled}-Jobs übernehmen Sync, Reveal und
 * Auswertung. Es wird bewusst kein Web-Server gestartet
 * ({@code spring.main.web-application-type=none}); {@code WebClient} dient nur
 * als HTTP-Client für die externen APIs.
 */
@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
public class WmTippspielApplication {

    public static void main(String[] args) {
        SpringApplication.run(WmTippspielApplication.class, args);
    }
}
