package com.example.wmtippspiel.config;

import com.example.wmtippspiel.discord.InteractionListener;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.ChunkingFilter;
import net.dv8tion.jda.api.utils.MemberCachePolicy;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Stellt die dauerhafte JDA-Gateway-Verbindung bereit (Verfassung Prinzip V).
 * Slash-Commands und Component-Interaktionen werden ereignisgetrieben über den
 * {@link InteractionListener} verarbeitet.
 *
 * <p>Das privilegierte {@code GUILD_MEMBERS}-Intent ist aktiviert, damit der Bot
 * die Mitglieder der WM-Notify-Rolle kennt und die Tipp-Erinnerung gezielt an
 * Rollenmitglieder ohne Tipp schicken kann. <b>Dieses Intent muss zusätzlich im
 * Discord Developer Portal (Bot → Privileged Gateway Intents → Server Members
 * Intent) eingeschaltet werden</b>, sonst schlägt der Login fehl.
 */
@Configuration
public class DiscordConfig {

    @Bean(destroyMethod = "shutdown")
    public JDA jda(AppProperties properties, InteractionListener listener) {
        return JDABuilder.createDefault(properties.discord().token())
                .enableIntents(GatewayIntent.GUILD_MEMBERS)
                .setMemberCachePolicy(MemberCachePolicy.ALL)
                .setChunkingFilter(ChunkingFilter.ALL)
                .addEventListeners(listener)
                .build();
    }
}
