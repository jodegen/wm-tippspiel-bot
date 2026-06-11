package com.example.wmtippspiel.discord.publish;

import java.util.EnumSet;

import com.example.wmtippspiel.config.AppProperties;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Message.MentionType;
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
    private final String notifyRoleId;

    public AnnounceChannel(JDA jda, AppProperties properties) {
        this.jda = jda;
        this.announceChannelId = properties.discord().announceChannelId();
        this.notifyRoleId = properties.discord().notifyRoleId();
    }

    /** Postet ein Embed; pingt – falls konfiguriert – die WM-Notify-Rolle. */
    public void post(MessageEmbed embed) {
        TextChannel channel = resolve();
        if (channel == null) {
            return;
        }
        var action = channel.sendMessageEmbeds(embed);
        if (notifyRoleId != null && !notifyRoleId.isBlank()) {
            action = action.setContent("<@&" + notifyRoleId + ">")
                    .setAllowedMentions(EnumSet.of(MentionType.ROLE));
        }
        action.queue(ok -> { }, err -> log.warn("Announce-Post fehlgeschlagen: {}", err.getMessage()));
    }

    /** Postet eine Nachricht, die gezielt die genannten Nutzer pingt (Tipp-Erinnerung). */
    public void postUserPing(String content) {
        TextChannel channel = resolve();
        if (channel == null) {
            return;
        }
        channel.sendMessage(content)
                .setAllowedMentions(EnumSet.of(MentionType.USER))
                .queue(ok -> { }, err -> log.warn("Reminder-Post fehlgeschlagen: {}", err.getMessage()));
    }

    private TextChannel resolve() {
        if (announceChannelId == null || announceChannelId.isBlank()) {
            log.warn("Kein Announce-Channel konfiguriert – Nachricht verworfen");
            return null;
        }
        TextChannel channel = jda.getTextChannelById(announceChannelId);
        if (channel == null) {
            log.warn("Announce-Channel {} nicht gefunden", announceChannelId);
        }
        return channel;
    }
}
