package com.example.wmtippspiel.discord.publish;

import com.example.wmtippspiel.config.AppProperties;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Kapselt das Posten in den konfigurierten Announce-Channel. */
@Component
public class AnnounceChannel {

    private static final Logger log = LoggerFactory.getLogger(AnnounceChannel.class);

    private final JDA jda;
    private final String announceChannelId;

    public AnnounceChannel(JDA jda, AppProperties properties) {
        this.jda = jda;
        this.announceChannelId = properties.discord().announceChannelId();
    }

    public void post(MessageEmbed embed) {
        if (announceChannelId == null || announceChannelId.isBlank()) {
            log.warn("Kein Announce-Channel konfiguriert – Nachricht verworfen");
            return;
        }
        TextChannel channel = jda.getTextChannelById(announceChannelId);
        if (channel == null) {
            log.warn("Announce-Channel {} nicht gefunden", announceChannelId);
            return;
        }
        channel.sendMessageEmbeds(embed).queue();
    }
}
