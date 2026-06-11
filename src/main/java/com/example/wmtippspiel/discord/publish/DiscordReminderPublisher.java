package com.example.wmtippspiel.discord.publish;

import java.util.List;
import java.util.stream.Collectors;

import com.example.wmtippspiel.discord.render.TimeFormatting;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.reminder.ReminderPublisher;

import org.springframework.stereotype.Component;

/** Discord-Umsetzung der Tipp-Erinnerung: pingt die offenen Tipper im Announce-Channel. */
@Component
public class DiscordReminderPublisher implements ReminderPublisher {

    private final AnnounceChannel announceChannel;
    private final TimeFormatting time;

    public DiscordReminderPublisher(AnnounceChannel announceChannel, TimeFormatting time) {
        this.announceChannel = announceChannel;
        this.time = time;
    }

    @Override
    public void publishReminder(Match match, List<String> userIds) {
        if (userIds.isEmpty()) {
            return;
        }
        String mentions = userIds.stream().map(id -> "<@" + id + ">").collect(Collectors.joining(" "));
        String content = "⏰ **" + match.home() + " vs " + match.away() + "** — Anpfiff "
                + time.relative(match.kickoff()) + "\n"
                + "Noch nicht getippt: " + mentions + " — schnell noch abgeben! ⚽";
        announceChannel.postUserPing(content);
    }
}
