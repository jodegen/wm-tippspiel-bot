package com.example.wmtippspiel.discord.publish;

import java.util.List;

import com.example.wmtippspiel.discord.render.RevealEmbed;
import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.Tip;
import com.example.wmtippspiel.reveal.RevealPublisher;

import org.springframework.stereotype.Component;

/** Discord-Umsetzung der Tipp-Offenlegung (postet ins Announce-Channel). */
@Component
public class DiscordRevealPublisher implements RevealPublisher {

    private final AnnounceChannel announceChannel;
    private final RevealEmbed revealEmbed;

    public DiscordRevealPublisher(AnnounceChannel announceChannel, RevealEmbed revealEmbed) {
        this.announceChannel = announceChannel;
        this.revealEmbed = revealEmbed;
    }

    @Override
    public void publishReveal(Match match, List<Tip> tips) {
        announceChannel.post(revealEmbed.build(match, tips));
    }
}
