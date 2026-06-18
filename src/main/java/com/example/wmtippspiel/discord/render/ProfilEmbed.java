package com.example.wmtippspiel.discord.render;

import com.example.wmtippspiel.discord.commands.UserProfile;
import com.example.wmtippspiel.persistence.ProfileTipRow;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.springframework.stereotype.Component;

/** Baut das /profil-Embed (F13) im gemeinsamen {@link EmbedStyle}. */
@Component
public class ProfilEmbed {

    private final EmbedStyle style;

    public ProfilEmbed(EmbedStyle style) {
        this.style = style;
    }

    public MessageEmbed build(UserProfile p) {
        EmbedBuilder embed = style.base("📊  Profil · " + p.username());
        if (p.isEmpty()) {
            embed.setDescription("Noch keine Tipps abgegeben. Mit `/tipp` geht's los! 🎯");
            return embed.build();
        }
        embed.addField("Rang", p.rank() == null ? "—" : "#" + p.rank(), true);
        embed.addField("Gesamtpunkte", "**" + p.totalPoints() + "**", true);
        embed.addField("Exakte Treffer", p.exactHits() + "×", true);
        embed.addField("Trefferquote",
                p.hitRatePercent() == null ? "—" : p.hitRatePercent() + " %", true);
        embed.addField("Gewertete Tipps", String.valueOf(p.evaluatedTips()), true);
        embed.addField("Punktstufen",
                "4️⃣ " + p.count4() + "  ·  3️⃣ " + p.count3()
                        + "  ·  2️⃣ " + p.count2() + "  ·  0️⃣ " + p.count0(), false);
        if (p.best() != null) {
            embed.addField("🔝 Bester Tipp", tipLine(p.best()), false);
        }
        if (p.worst() != null && p.evaluatedTips() > 1) {
            embed.addField("🥶 Schlechtester Tipp", tipLine(p.worst()), false);
        }
        return embed.build();
    }

    private String tipLine(ProfileTipRow t) {
        String result = t.resultHome() == null || t.resultAway() == null
                ? "?" : t.resultHome() + ":" + t.resultAway();
        return t.home() + " vs " + t.away()
                + " — Tipp **" + t.tipHome() + ":" + t.tipAway() + "**, Ergebnis " + result
                + " (" + t.points() + " Pkt)";
    }
}
