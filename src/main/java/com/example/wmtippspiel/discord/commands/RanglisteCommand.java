package com.example.wmtippspiel.discord.commands;

import com.example.wmtippspiel.discord.render.RanglisteEmbed;
import com.example.wmtippspiel.persistence.TipRepository;

import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import org.springframework.stereotype.Component;

/** Behandelt {@code /rangliste} (US4 / F6) – öffentliche, sortierte Rangliste. */
@Component
public class RanglisteCommand {

    public static final String NAME = "rangliste";

    private final TipRepository tips;
    private final RanglisteEmbed embed;

    public RanglisteCommand(TipRepository tips, RanglisteEmbed embed) {
        this.tips = tips;
        this.embed = embed;
    }

    public void handle(SlashCommandInteractionEvent event) {
        event.replyEmbeds(embed.build(tips.leaderboard())).queue();
    }
}
