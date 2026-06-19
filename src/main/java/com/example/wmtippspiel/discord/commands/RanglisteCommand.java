package com.example.wmtippspiel.discord.commands;

import com.example.wmtippspiel.discord.render.RanglisteEmbed;
import com.example.wmtippspiel.discord.render.WebsiteLinks;
import com.example.wmtippspiel.persistence.TipRepository;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import org.springframework.stereotype.Component;

/** Behandelt {@code /rangliste} (US4 / F6) – öffentliche, sortierte Rangliste. */
@Component
public class RanglisteCommand {

    public static final String NAME = "rangliste";

    private final TipRepository tips;
    private final RanglisteEmbed embed;
    private final WebsiteLinks websiteLinks;

    public RanglisteCommand(TipRepository tips, RanglisteEmbed embed, WebsiteLinks websiteLinks) {
        this.tips = tips;
        this.embed = embed;
        this.websiteLinks = websiteLinks;
    }

    public void handle(SlashCommandInteractionEvent event) {
        // Feature 009: Link zur vollständigen Web-Tabelle (null, wenn keine Basis-URL konfiguriert).
        String leaderboardUrl = websiteLinks.leaderboardUrl().orElse(null);
        event.replyEmbeds(embed.build(tips.leaderboard(), leaderboardUrl)).queue();
    }
}
