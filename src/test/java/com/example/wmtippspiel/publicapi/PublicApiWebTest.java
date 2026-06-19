package com.example.wmtippspiel.publicapi;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.example.wmtippspiel.publicapi.dto.LeaderboardRowDto;
import com.example.wmtippspiel.publicapi.dto.LiveMatchDto;
import com.example.wmtippspiel.publicapi.dto.MatchDto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;

/**
 * Web-Layer-Tests der öffentlichen GET-Endpoints (Feature 008, US1/US2). Slice
 * über {@link PublicApiController} mit gemocktem {@link PublicQueryService} —
 * kein DB/Docker nötig. Prüft Felder, leere Mengen, UTC-Format, kein Leak (SC-001)
 * und 405 auf nicht-GET (SC-006).
 */
@WebMvcTest(PublicApiController.class)
class PublicApiWebTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PublicQueryService query;

    // PublicApiConfig wird als WebMvcConfigurer automatisch in den Slice gezogen
    // und benötigt AppProperties (CORS/Cache); hier neutral gemockt.
    @MockBean
    private com.example.wmtippspiel.config.AppProperties properties;

    private static MatchDto match() {
        return new MatchDto(101L, "Deutschland", "Frankreich", Instant.parse("2026-06-20T19:00:00Z"),
                "GROUP_STAGE", "A", "ARD", null, null, null, null, null, "SCHEDULED", 1);
    }

    @Test
    @DisplayName("GET /schedule liefert Felder inkl. UTC-Anstoß, kein user_id")
    void scheduleReturnsFields() throws Exception {
        when(query.schedule(null, null, null)).thenReturn(List.of(match()));
        mvc.perform(get("/api/public/schedule"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].matchId").value(101))
                .andExpect(jsonPath("$[0].home").value("Deutschland"))
                .andExpect(jsonPath("$[0].kickoffUtc").value("2026-06-20T19:00:00Z"))
                .andExpect(jsonPath("$[0].stage").value("GROUP_STAGE"))
                .andExpect(content().string(not(containsString("user_id"))));
    }

    @Test
    @DisplayName("GET /schedule reicht Filterparameter durch")
    void scheduleFilters() throws Exception {
        when(query.schedule("GROUP_STAGE", "A", 1)).thenReturn(List.of(match()));
        mvc.perform(get("/api/public/schedule").param("stage", "GROUP_STAGE").param("group", "A").param("matchday", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    @DisplayName("GET /matches/live: leere Liste statt Fehler")
    void liveEmpty() throws Exception {
        when(query.liveMatches()).thenReturn(List.of());
        mvc.perform(get("/api/public/matches/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("GET /matches/live liefert laufende Spiele")
    void liveReturnsInPlay() throws Exception {
        LiveMatchDto live = new LiveMatchDto(101L, "Deutschland", "Frankreich",
                Instant.parse("2026-06-20T19:00:00Z"), 1, 0, "IN_PLAY", "A", 1);
        when(query.liveMatches()).thenReturn(List.of(live));
        mvc.perform(get("/api/public/matches/live"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("IN_PLAY"))
                .andExpect(jsonPath("$[0].homeScore").value(1));
    }

    @Test
    @DisplayName("GET /leaderboard liefert Rang-Zeilen ohne user_id")
    void leaderboardReturnsRows() throws Exception {
        when(query.leaderboard()).thenReturn(List.of(new LeaderboardRowDto(1, "Alice", 12, 2, "↑1", "pub-abc123")));
        mvc.perform(get("/api/public/leaderboard"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].rank").value(1))
                .andExpect(jsonPath("$[0].displayName").value("Alice"))
                .andExpect(jsonPath("$[0].rankChange").value("↑1"))
                .andExpect(jsonPath("$[0].publicId").value("pub-abc123"))
                .andExpect(content().string(not(containsString("user_id"))));
    }

    @Test
    @DisplayName("POST auf einen GET-Endpoint ist nicht erlaubt (405, SC-006)")
    void postIsRejected() throws Exception {
        mvc.perform(post("/api/public/schedule"))
                .andExpect(status().isMethodNotAllowed());
    }
}
