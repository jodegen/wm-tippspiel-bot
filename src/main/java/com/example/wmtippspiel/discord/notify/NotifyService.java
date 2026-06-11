package com.example.wmtippspiel.discord.notify;

import com.example.wmtippspiel.config.AppProperties;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Schaltet die "WM-Notify"-Benachrichtigung per Button an/aus, indem die
 * Discord-Rolle vergeben bzw. entfernt wird. Die Rolle ist die einzige Quelle
 * der Wahrheit – alle Notifications (Reveal, Auswertung, Anpfiff-Hinweis,
 * Tipp-Erinnerung) richten sich nach ihr.
 */
@Service
public class NotifyService {

    public static final String TOGGLE_BUTTON = "notify:toggle";

    private static final Logger log = LoggerFactory.getLogger(NotifyService.class);

    private final String notifyRoleId;

    public NotifyService(AppProperties properties) {
        this.notifyRoleId = properties.discord().notifyRoleId();
    }

    public void toggle(ButtonInteractionEvent event) {
        Member member = event.getMember();
        Guild guild = event.getGuild();
        if (member == null || guild == null) {
            event.reply("Das geht nur auf einem Server.").setEphemeral(true).queue();
            return;
        }
        Role role = notifyRoleId == null || notifyRoleId.isBlank() ? null : guild.getRoleById(notifyRoleId);
        if (role == null) {
            log.warn("Notify-Rolle {} nicht gefunden/konfiguriert", notifyRoleId);
            event.reply("⚠️ Benachrichtigungen sind aktuell nicht eingerichtet.").setEphemeral(true).queue();
            return;
        }

        if (member.getRoles().contains(role)) {
            guild.removeRoleFromMember(member, role).queue(
                    ok -> { },
                    err -> log.warn("Rolle entfernen fehlgeschlagen: {}", err.getMessage()));
            event.reply("🔕 Du bist von den WM-Benachrichtigungen **abgemeldet**.").setEphemeral(true).queue();
        } else {
            guild.addRoleToMember(member, role).queue(
                    ok -> { },
                    err -> log.warn("Rolle vergeben fehlgeschlagen (hat der Bot 'Manage Roles' und steht "
                            + "seine Rolle über '{}'?): {}", role.getName(), err.getMessage()));
            event.reply("🔔 Du bekommst jetzt **WM-Benachrichtigungen** – Anpfiff-Hinweise, Reveals/Auswertungen "
                    + "und eine Erinnerung, falls du noch nicht getippt hast.").setEphemeral(true).queue();
        }
    }
}
