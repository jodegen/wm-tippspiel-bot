package com.example.wmtippspiel.publicapi;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.example.wmtippspiel.config.AppProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Unit-Tests des öffentlichen Identifiers (Feature 008, FR-015): Determinismus,
 * Nicht-Reversibilität (anderes Secret ⇒ anderer Wert), keine Preisgabe der
 * internen ID, Auflösung per Enumeration. Kein Spring/DB nötig.
 */
class PublicIdServiceTest {

    private static PublicIdService withSecret(String secret) {
        return new PublicIdService(new AppProperties(
                null, null, null, null, null, new AppProperties.PublicApi(List.of(), secret, 5)));
    }

    @Test
    @DisplayName("deterministisch und gibt die user_id nicht preis")
    void deterministicAndOpaque() {
        PublicIdService svc = withSecret("top-secret");
        String id1 = svc.publicId("discord-123456789");
        String id2 = svc.publicId("discord-123456789");
        assertThat(id1).isEqualTo(id2);
        assertThat(id1).doesNotContain("discord-123456789");
        assertThat(id1).isNotBlank();
    }

    @Test
    @DisplayName("verschiedene Nutzer ⇒ verschiedene Identifier")
    void distinctPerUser() {
        PublicIdService svc = withSecret("top-secret");
        assertThat(svc.publicId("user-a")).isNotEqualTo(svc.publicId("user-b"));
    }

    @Test
    @DisplayName("anderes Secret ⇒ anderer Identifier (nicht zurückrechenbar)")
    void differsBySecret() {
        assertThat(withSecret("secret-1").publicId("user-a"))
                .isNotEqualTo(withSecret("secret-2").publicId("user-a"));
    }

    @Test
    @DisplayName("resolve findet bekannten Nutzer, leer bei unbekanntem")
    void resolve() {
        PublicIdService svc = withSecret("top-secret");
        List<String> users = List.of("user-a", "user-b", "user-c");
        String idB = svc.publicId("user-b");
        assertThat(svc.resolve(idB, users)).contains("user-b");
        assertThat(svc.resolve("nicht-existent", users)).isEmpty();
    }

    @Test
    @DisplayName("leeres Secret ⇒ Fail-Fast bei der Initialisierung")
    void failFastOnBlankSecret() {
        org.assertj.core.api.Assertions.assertThatThrownBy(() -> withSecret("  "))
                .isInstanceOf(IllegalStateException.class);
    }
}
