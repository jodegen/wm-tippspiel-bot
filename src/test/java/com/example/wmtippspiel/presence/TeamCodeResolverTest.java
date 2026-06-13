package com.example.wmtippspiel.presence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests des Team-Kürzel-Mappings (F9): Treffer aus der Ressource und Klartext-Fallback. */
class TeamCodeResolverTest {

    private final TeamCodeResolver resolver = new TeamCodeResolver();

    @Test
    @DisplayName("Bekannter Teamname (englisch) → FIFA-Kürzel")
    void knownEnglishName() {
        assertThat(resolver.code("Germany")).isEqualTo("GER");
        assertThat(resolver.code("France")).isEqualTo("FRA");
    }

    @Test
    @DisplayName("Deutscher Alias → FIFA-Kürzel")
    void germanAlias() {
        assertThat(resolver.code("Deutschland")).isEqualTo("GER");
    }

    @Test
    @DisplayName("Mehrwort-Name (escaptes Leerzeichen) wird aufgelöst")
    void multiWordName() {
        assertThat(resolver.code("United States")).isEqualTo("USA");
    }

    @Test
    @DisplayName("Unbekannter kurzer Name → unveränderter Klartext")
    void unknownShortNameKept() {
        assertThat(resolver.code("Quizland")).isEqualTo("Quizland");
    }

    @Test
    @DisplayName("Unbekannter langer Name → defensiv gekürzt (nie überlang)")
    void unknownLongNameTruncated() {
        String code = resolver.code("Irgendeinsehrlangerteamname");
        assertThat(code).hasSizeLessThanOrEqualTo(12);
    }

    @Test
    @DisplayName("null/leer → Platzhalter, nie null")
    void nullSafe() {
        assertThat(resolver.code(null)).isEqualTo("?");
        assertThat(resolver.code("   ")).isEqualTo("?");
    }
}
