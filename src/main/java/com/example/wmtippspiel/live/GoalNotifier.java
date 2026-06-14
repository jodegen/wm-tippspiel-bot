package com.example.wmtippspiel.live;

import com.example.wmtippspiel.discord.publish.AnnounceChannel;
import com.example.wmtippspiel.discord.render.GoalEmbed;

import org.springframework.stereotype.Component;

/**
 * Postet ein {@link GoalEvent} in den Announce-Channel (F8) — herkunftsunabhängig
 * (FR-011). Tor- und Korrektur-Posts pingen die Notify-Rolle bewusst NICHT (bei
 * laufenden Spielen zu häufig); Role-Pings bleiben den selteneren Anpfiff-/Reveal-/
 * Auswertungs-Posts vorbehalten.
 */
@Component
public class GoalNotifier {

    private final AnnounceChannel announceChannel;
    private final GoalEmbed goalEmbed;

    public GoalNotifier(AnnounceChannel announceChannel, GoalEmbed goalEmbed) {
        this.announceChannel = announceChannel;
        this.goalEmbed = goalEmbed;
    }

    public void post(GoalEvent event) {
        // Beide Pfade ohne Role-Ping (postPlain).
        if (event.kind() == GoalEvent.Kind.GOAL) {
            announceChannel.postPlain(goalEmbed.goal(event));
        } else {
            announceChannel.postPlain(goalEmbed.correction(event));
        }
    }
}
