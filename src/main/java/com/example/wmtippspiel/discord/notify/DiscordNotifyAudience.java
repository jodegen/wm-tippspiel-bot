package com.example.wmtippspiel.discord.notify;

import java.util.List;

import com.example.wmtippspiel.config.AppProperties;
import com.example.wmtippspiel.reminder.NotifyAudience;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/** Liest die Mitglieder der WM-Notify-Rolle aus dem JDA-Member-Cache. */
@Component
public class DiscordNotifyAudience implements NotifyAudience {

    private static final Logger log = LoggerFactory.getLogger(DiscordNotifyAudience.class);

    private final JDA jda;
    private final String guildId;
    private final String notifyRoleId;

    public DiscordNotifyAudience(JDA jda, AppProperties properties) {
        this.jda = jda;
        this.guildId = properties.discord().guildId();
        this.notifyRoleId = properties.discord().notifyRoleId();
    }

    @Override
    public List<String> roleMemberUserIds() {
        if (guildId == null || guildId.isBlank() || notifyRoleId == null || notifyRoleId.isBlank()) {
            return List.of();
        }
        Guild guild = jda.getGuildById(guildId);
        if (guild == null) {
            return List.of();
        }
        Role role = guild.getRoleById(notifyRoleId);
        if (role == null) {
            log.warn("Notify-Rolle {} nicht gefunden", notifyRoleId);
            return List.of();
        }
        return guild.getMembersWithRoles(role).stream()
                .map(member -> member.getUser().getId())
                .toList();
    }
}
