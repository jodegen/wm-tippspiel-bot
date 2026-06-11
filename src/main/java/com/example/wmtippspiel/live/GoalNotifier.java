package com.example.wmtippspiel.live;

import com.example.wmtippspiel.discord.publish.AnnounceChannel;
import com.example.wmtippspiel.discord.render.GoalEmbed;

import org.springframework.stereotype.Component;

/**
 * Postet ein {@link GoalEvent} in den Announce-Channel (F8) — herkunftsunabhängig
 * (FR-011). Tore pingen die Notify-Rolle (FR-010), Korrektur-Notizen nicht.
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
        if (event.kind() == GoalEvent.Kind.GOAL) {
            announceChannel.post(goalEmbed.goal(event));            // mit Role-Ping
        } else {
            announceChannel.postPlain(goalEmbed.correction(event)); // ohne Ping
        }
    }
}
