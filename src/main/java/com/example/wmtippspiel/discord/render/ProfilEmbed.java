package com.example.wmtippspiel.discord.render;

import com.example.wmtippspiel.discord.commands.UserProfile;
import com.example.wmtippspiel.persistence.ProfileTipRow;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import org.springframework.stereotype.Component;

/**
 * Baut das /profil-Embed (F13) im gemeinsamen {@link EmbedStyle}: prominenter
 * Rang-Header mit Medaille, Avatar als Thumbnail, eine Balken-Visualisierung der
 * Punktstufen und kompakte Karten für besten/schwächsten Tipp.
 */
@Component
public class ProfilEmbed {

    /** Breite der Punktstufen-Balken. */
    private static final int BAR_WIDTH = 10;
    private static final char BAR_FULL = '▰';
    private static final char BAR_EMPTY = '▱';

    private final EmbedStyle style;

    public ProfilEmbed(EmbedStyle style) {
        this.style = style;
    }

    public MessageEmbed build(UserProfile p, String avatarUrl) {
        EmbedBuilder embed = style.base("Profil · " + p.username());
        if (avatarUrl != null) {
            embed.setThumbnail(avatarUrl);
        }
        if (p.isEmpty()) {
            embed.setDescription("Noch keine Tipps abgegeben. Mit `/tipp` geht's los! 🎯");
            return embed.build();
        }

        // Kopfzeile: Rang (mit Medaille) und Gesamtpunkte prominent.
        String rank = p.rank() == null ? "—" : "#" + p.rank();
        embed.setDescription(medal(p.rank()) + " **Rang " + rank + "** · 🏆 **"
                + p.totalPoints() + "** Punkte");

        // Kennzahlen kompakt nebeneinander.
        embed.addField("🎯 Exakte Treffer", p.exactHits() + "×", true);
        embed.addField("📈 Trefferquote",
                p.hitRatePercent() == null ? "—" : p.hitRatePercent() + " %", true);
        embed.addField("📝 Gewertet", p.evaluatedTips() + " Tipps", true);

        embed.addField("Punktverteilung", distribution(p), false);

        if (p.best() != null) {
            embed.addField("🔝 Bester Tipp", tipCard(p.best()), false);
        }
        if (p.worst() != null && p.evaluatedTips() > 1) {
            embed.addField("🥶 Schwächster Tipp", tipCard(p.worst()), false);
        }
        return embed.build();
    }

    /** Medaille für die Top-3, sonst ein neutraler Marker. */
    private static String medal(Integer rank) {
        if (rank == null) {
            return "📍";
        }
        return switch (rank) {
            case 1 -> "🥇";
            case 2 -> "🥈";
            case 3 -> "🥉";
            default -> "📍";
        };
    }

    /** Balken-Visualisierung der Punktstufen (relativ zum häufigsten Wert). */
    private String distribution(UserProfile p) {
        int max = Math.max(Math.max(p.count4(), p.count3()), Math.max(p.count2(), p.count0()));
        return bar("4️⃣ Exakt    ", p.count4(), max)
                + bar("3️⃣ Differenz", p.count3(), max)
                + bar("2️⃣ Tendenz ", p.count2(), max)
                + bar("0️⃣ Daneben ", p.count0(), max);
    }

    private String bar(String label, int count, int max) {
        int filled = max <= 0 ? 0 : (int) Math.round((double) count / max * BAR_WIDTH);
        if (count > 0 && filled == 0) {
            filled = 1;
        }
        String bars = String.valueOf(BAR_FULL).repeat(filled)
                + String.valueOf(BAR_EMPTY).repeat(BAR_WIDTH - filled);
        return label + "  `" + bars + "`  **" + count + "**\n";
    }

    /** Kompakte Tipp-Karte: Begegnung mit Ergebnis und der abgegebene Tipp. */
    private String tipCard(ProfileTipRow t) {
        String result = t.resultHome() == null || t.resultAway() == null
                ? "?" : t.resultHome() + ":" + t.resultAway();
        return "**" + t.home() + " " + result + " " + t.away() + "**\n"
                + "Tipp `" + t.tipHome() + ":" + t.tipAway() + "` → **" + t.points()
                + " Pkt** " + pointsBadge(t.points());
    }

    private static String pointsBadge(int points) {
        return switch (points) {
            case 4 -> "🎯";
            case 3 -> "🔥";
            case 2 -> "👍";
            default -> "💤";
        };
    }
}
