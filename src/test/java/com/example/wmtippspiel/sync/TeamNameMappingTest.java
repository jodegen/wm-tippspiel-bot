package com.example.wmtippspiel.sync;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Tests des Odds→DB-Team-Namens-Mappings (Quoten-Zuordnung, R6). */
class TeamNameMappingTest {

    private final TeamNameMapping mapping = new TeamNameMapping();

    @Test
    @DisplayName("Bekannte Odds-API-Aliasse werden auf den DB-Namen abgebildet")
    void mapsKnownAliases() {
        assertThat(mapping.canonical("Cape Verde")).isEqualTo("Cape Verde Islands");
        assertThat(mapping.canonical("DR Congo")).isEqualTo("Congo DR");
        assertThat(mapping.canonical("Democratic Republic of the Congo")).isEqualTo("Congo DR");
    }

    @Test
    @DisplayName("Lookup ist case-insensitiv und ignoriert Randleerzeichen")
    void normalizesKey() {
        assertThat(mapping.canonical("  cape verde  ")).isEqualTo("Cape Verde Islands");
    }

    @Test
    @DisplayName("Unbekannter Name bleibt unverändert; \"Congo\" wird NICHT zu \"Congo DR\"")
    void passthroughAndNoFalseCollapse() {
        assertThat(mapping.canonical("Germany")).isEqualTo("Germany");
        assertThat(mapping.canonical("Congo")).isEqualTo("Congo"); // Republik Kongo ≠ Congo DR
    }

    @Test
    @DisplayName("null bleibt null")
    void nullSafe() {
        assertThat(mapping.canonical(null)).isNull();
    }

    @Test
    @DisplayName("normalizeForMatch entfernt Akzente/Satzzeichen/Groß-Klein → Schreibvarianten matchen")
    void normalizeForMatch() {
        assertThat(TeamNameMapping.normalizeForMatch("Bosnia & Herzegovina"))
                .isEqualTo(TeamNameMapping.normalizeForMatch("Bosnia-Herzegovina"));
        assertThat(TeamNameMapping.normalizeForMatch("Türkiye"))
                .isEqualTo(TeamNameMapping.normalizeForMatch("Turkiye"));
        assertThat(TeamNameMapping.normalizeForMatch("Côte d'Ivoire")).isEqualTo("cotedivoire");
        assertThat(TeamNameMapping.normalizeForMatch(null)).isEmpty();
    }
}
