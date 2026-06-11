package com.example.wmtippspiel.config;

import com.example.wmtippspiel.discord.InteractionListener;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stellt die dauerhafte JDA-Gateway-Verbindung bereit (Verfassung Prinzip V).
 * Slash-Commands und Component-Interaktionen werden ereignisgetrieben über den
 * {@link InteractionListener} verarbeitet — kein Polling.
 */
@Configuration
public class DiscordConfig {

    /**
     * Baut die JDA-Instanz und registriert den zentralen Listener. Der Login
     * läuft asynchron; die Slash-Command-Registrierung wartet separat auf
     * {@code awaitReady()} (siehe DiscordCommandRegistrar).
     */
    @Bean(destroyMethod = "shutdown")
    public JDA jda(AppProperties properties, InteractionListener listener) {
        return JDABuilder.createLight(properties.discord().token())
                .addEventListeners(listener)
                .build();
    }
}
