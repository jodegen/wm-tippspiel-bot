package com.example.wmtippspiel.discord.render;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.wmtippspiel.config.AppProperties;
import com.example.wmtippspiel.publicapi.PublicIdService;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Tests des Website-Link-Helfers (Feature 009): URL-Bau, Trailing-Slash-
 * Normalisierung (C4), Leerwert-Verhalten (C5), Host-Ableitung und Identitäts-
 * gleichheit des Profil-Identifiers mit {@link PublicIdService} (FR-009). Kein
 * Spring/DB nötig.
 */
class WebsiteLinksTest {

    private static final String SECRET = "test-secret";

    private static AppProperties propsWithWebsite(String baseUrl) {
        return new AppProperties(null, null, null, null, null,
                new AppProperties.PublicApi(List.of(), SECRET, 5),
                baseUrl == null ? null : new AppProperties.Website(baseUrl));
    }

    private static WebsiteLinks links(String baseUrl) {
        AppProperties props = propsWithWebsite(baseUrl);
        return new WebsiteLinks(props, new PublicIdService(props));
    }

    @Test
    @DisplayName("Profil-URL = {base}/profil/{publicId}; publicId == PublicIdService, keine Discord-ID")
    void profileUrlUsesPublicId() {
        AppProperties props = propsWithWebsite("https://wm.xenoria.de");
        PublicIdService ids = new PublicIdService(props);
        WebsiteLinks links = new WebsiteLinks(props, ids);

        String discordId = "discord-123456789";
        String expected = "https://wm.xenoria.de/profil/" + ids.publicId(discordId);

        assertThat(links.profileUrl(discordId)).contains(expected);
        assertThat(links.profileUrl(discordId).orElseThrow()).doesNotContain(discordId);
    }

    @Test
    @DisplayName("Leaderboard-URL = {base}/leaderboard")
    void leaderboardUrl() {
        assertThat(links("https://wm.xenoria.de").leaderboardUrl())
                .contains("https://wm.xenoria.de/leaderboard");
    }

    @Test
    @DisplayName("Trailing Slash wird normalisiert (mit/ohne / identisch, C4)")
    void trailingSlashNormalized() {
        assertThat(links("https://wm.xenoria.de/").leaderboardUrl())
                .contains("https://wm.xenoria.de/leaderboard");
        assertThat(links("https://wm.xenoria.de/").profileUrl("user-a"))
                .isEqualTo(links("https://wm.xenoria.de").profileUrl("user-a"));
    }

    @Test
    @DisplayName("leere/blank/fehlende Basis-URL ⇒ Optional.empty (C5)")
    void emptyBaseUrlYieldsNoLinks() {
        for (WebsiteLinks l : List.of(links(""), links("   "), links(null))) {
            assertThat(l.isConfigured()).isFalse();
            assertThat(l.leaderboardUrl()).isEmpty();
            assertThat(l.profileUrl("user-a")).isEmpty();
            assertThat(l.footerHint()).isEmpty();
        }
    }

    @Test
    @DisplayName("Host-Ableitung und Footer-Hinweis aus der Basis-URL")
    void hostAndFooterHint() {
        WebsiteLinks l = links("https://wm.xenoria.de/");
        assertThat(l.host()).contains("wm.xenoria.de");
        assertThat(l.footerHint()).contains("Vollständige Tabelle auf wm.xenoria.de");
    }

    @Test
    @DisplayName("profileUrl mit leerer Discord-ID ⇒ Optional.empty")
    void profileUrlBlankUserId() {
        WebsiteLinks l = links("https://wm.xenoria.de");
        assertThat(l.profileUrl(null)).isEmpty();
        assertThat(l.profileUrl("  ")).isEmpty();
    }
}
