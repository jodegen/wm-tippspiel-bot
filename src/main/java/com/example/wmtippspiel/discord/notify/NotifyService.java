package com.example.wmtippspiel.discord.notify;

import java.time.Clock;

import com.example.wmtippspiel.config.AppProperties;
import com.example.wmtippspiel.persistence.NotifySubscriberRepository;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Schaltet die "WM-Notify"-Benachrichtigung per Button an/aus. Pflegt sowohl die
 * Discord-Rolle (für Role-Pings in Announce-Posts) als auch das DB-Abo (für
 * gezielte Tipp-Erinnerungen ohne privilegiertes Member-Intent).
 */
@Service
public class NotifyService {

    public static final String TOGGLE_BUTTON = "notify:toggle";

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);

    private final NotifySubscriberRepository subscribers;
    private final String notifyRoleId;
    private final Clock clock;

    public NotifyService(NotifySubscriberRepository subscribers, AppProperties properties, Clock clock) {
        this.subscribers = subscribers;
        this.notifyRoleId = properties.discord().notifyRoleId();
        this.clock = clock;
    }

    public void toggle(ButtonInteractionEvent event) {
        Member member = event.getMember();
        if (member == null) {
            event.reply("Das geht nur auf einem Server.").setEphemeral(true).queue();
            return;
        }
        String userId = member.getUser().getId();
        Role role = resolveRole(event.getGuild());

        if (subscribers.isSubscribed(userId)) {
            subscribers.unsubscribe(userId);
            if (role != null) {
                event.getGuild().removeRoleFromMember(member, role)
                        .queue(ok -> { }, err -> log.warn("Rolle entfernen fehlgeschlagen: {}", err.getMessage()));
            }
            event.reply("🔕 Du bist von den WM-Benachrichtigungen **abgemeldet**.").setEphemeral(true).queue();
        } else {
            subscribers.subscribe(userId, member.getEffectiveName(), clock.instant());
            if (role != null) {
                event.getGuild().addRoleToMember(member, role)
                        .queue(ok -> { }, err -> log.warn("Rolle vergeben fehlgeschlagen "
                                + "(hat der Bot 'Manage Roles' und steht seine Rolle über '{}'?): {}",
                                role.getName(), err.getMessage()));
            }
            event.reply("🔔 Du bekommst jetzt **WM-Benachrichtigungen** – Anpfiff-Posts und eine "
                    + "Erinnerung, falls du noch nicht getippt hast.").setEphemeral(true).queue();
        }
    }

    private Role resolveRole(Guild guild) {
        if (guild == null || notifyRoleId == null || notifyRoleId.isBlank()) {
            return null;
        }
        Role role = guild.getRoleById(notifyRoleId);
        if (role == null) {
            log.warn("Notify-Rolle {} nicht gefunden", notifyRoleId);
        }
        return role;
    }
}
