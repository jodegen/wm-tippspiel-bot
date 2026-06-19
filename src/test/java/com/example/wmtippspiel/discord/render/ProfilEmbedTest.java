package com.example.wmtippspiel.discord.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import com.example.wmtippspiel.config.AppProperties;
import com.example.wmtippspiel.discord.commands.ProfileStats;
import com.example.wmtippspiel.discord.commands.UserProfile;
import com.example.wmtippspiel.publicapi.PublicIdService;

import net.dv8tion.jda.api.entities.MessageEmbed;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests des Web-Profil-Links im /profil-Embed (Feature 009): der Link trägt den
 * öffentlichen {@code publicId} des angezeigten Nutzers (== {@link PublicIdService},
 * FR-009/SC-002), niemals die Discord-ID; er erscheint auch im Leerfall (FR-003) und
 * entfällt ohne konfigurierte Basis-URL (FR-006).
 */
class ProfilEmbedTest {

    private static final String BASE = "https://wm.xenoria.de";

    private static AppProperties props(String baseUrl) {
        return new AppProperties(null, null, null, null, null,
                new AppProperties.PublicApi(List.of(), "test-secret", 5),
                baseUrl == null ? null : new AppProperties.Website(baseUrl));
    }

    private final ProfilEmbed embed = new ProfilEmbed(new EmbedStyle(Clock.fixed(Instant.EPOCH, ZoneOffset.UTC)));

    private static String linkValue(MessageEmbed result) {
        return result.getFields().stream()
                .map(MessageEmbed.Field::getValue)
                .filter(v -> v != null && v.contains("🔗"))
                .findFirst()
                .orElse(null);
    }

    @Test
    @DisplayName("Profil-Link trägt den publicId des Ziel-Nutzers, nicht die Discord-ID")
    void linkCarriesTargetPublicId() {
        AppProperties props = props(BASE);
        PublicIdService ids = new PublicIdService(props);
        WebsiteLinks links = new WebsiteLinks(props, ids);

        String targetDiscordId = "discord-target-42";
        String profileUrl = links.profileUrl(targetDiscordId).orElseThrow();
        UserProfile profile = ProfileStats.build("Alice", null, List.of());

        String link = linkValue(embed.build(profile, null, profileUrl));

        assertThat(link).contains(ids.publicId(targetDiscordId));
        assertThat(link).doesNotContain(targetDiscordId);
        // anderer Nutzer ⇒ anderer publicId im Link
        assertThat(link).doesNotContain(ids.publicId("discord-other-99"));
        assertThat(link).contains("wm.xenoria.de");
    }

    @Test
    @DisplayName("Leerfall (keine Tipps) erhält trotzdem den Web-Link (FR-003)")
    void emptyProfileStillLinks() {
        AppProperties props = props(BASE);
        WebsiteLinks links = new WebsiteLinks(props, new PublicIdService(props));
        String profileUrl = links.profileUrl("discord-x").orElseThrow();

        UserProfile empty = ProfileStats.build("Niemand", null, List.of());
        assertThat(empty.isEmpty()).isTrue();

        assertThat(linkValue(embed.build(empty, null, profileUrl))).isNotNull();
    }

    @Test
    @DisplayName("Ohne Profil-URL kein Link-Feld (FR-006)")
    void noLinkWhenUrlMissing() {
        UserProfile profile = ProfileStats.build("Alice", null, List.of());
        assertThat(linkValue(embed.build(profile, null, null))).isNull();
    }
}
