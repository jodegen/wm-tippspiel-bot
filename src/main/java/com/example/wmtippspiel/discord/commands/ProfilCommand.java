package com.example.wmtippspiel.discord.commands;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.example.wmtippspiel.discord.render.ProfilEmbed;
import com.example.wmtippspiel.discord.render.WebsiteLinks;
import com.example.wmtippspiel.leaderboard.LeaderboardRanking;
import com.example.wmtippspiel.leaderboard.RankedRow;
import com.example.wmtippspiel.persistence.ProfileTipRow;
import com.example.wmtippspiel.persistence.TipRepository;

import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import org.springframework.stereotype.Component;

/**
 * Behandelt {@code /profil [user]} (F13): zeigt die persönliche Bilanz öffentlich
 * im Channel (FR-023). Aggregiert rein lesend über die bestehende Wertung
 * (Rangliste + gespeicherte {@code points}); berechnet keine Punkte neu (FR-026).
 */
@Component
public class ProfilCommand {

    public static final String NAME = "profil";
    public static final String OPTION_USER = "user";

    private final TipRepository tips;
    private final ProfilEmbed embed;
    private final WebsiteLinks websiteLinks;

    public ProfilCommand(TipRepository tips, ProfilEmbed embed, WebsiteLinks websiteLinks) {
        this.tips = tips;
        this.embed = embed;
        this.websiteLinks = websiteLinks;
    }

    public void handle(SlashCommandInteractionEvent event) {
        User target = Optional.ofNullable(event.getOption(OPTION_USER))
                .map(OptionMapping::getAsUser)
                .orElse(event.getUser());

        // Öffentliche Antwort (nicht ephemeral); deferReply hält das 3s-Fenster ein.
        event.deferReply().queue();

        List<RankedRow> ranked = LeaderboardRanking.compute(tips.leaderboard(), Map.of());
        RankedRow mine = ranked.stream()
                .filter(r -> r.entry().userId().equals(target.getId()))
                .findFirst()
                .orElse(null);
        List<ProfileTipRow> evaluatedTips = tips.findEvaluatedTipsByUser(target.getId());

        UserProfile profile = ProfileStats.build(target.getEffectiveName(), mine, evaluatedTips);
        // Profil-URL zum angezeigten Ziel-Nutzer (publicId, nicht Discord-ID); null wenn unkonfiguriert.
        String profileUrl = websiteLinks.profileUrl(target.getId()).orElse(null);
        event.getHook().editOriginalEmbeds(
                embed.build(profile, target.getEffectiveAvatarUrl(), profileUrl)).queue();
    }
}
