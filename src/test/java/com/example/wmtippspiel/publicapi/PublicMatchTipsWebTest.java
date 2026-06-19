package com.example.wmtippspiel.publicapi;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.example.wmtippspiel.publicapi.dto.MatchTipsDto;
import com.example.wmtippspiel.publicapi.dto.PublicTipDto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-Layer-Test des reveal-gegateten Tipps-Endpoints (Feature 008, US3).
 * Verifiziert, dass die Vor-Anpfiff-Antwort keinerlei Tipps/Namen im JSON
 * enthält (SC-002), Nach-Anpfiff Tipps liefert und unbekannte Spiele 404 ergeben.
 */
@WebMvcTest(PublicApiController.class)
class PublicMatchTipsWebTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PublicQueryService query;

    @MockBean
    private com.example.wmtippspiel.config.AppProperties properties;

    @Test
    @DisplayName("Vor Anpfiff: released=false, keine Namen/Tipps im JSON")
    void lockedBeforeKickoff() throws Exception {
        when(query.matchTips(5L)).thenReturn(MatchTipsDto.locked(5L));
        mvc.perform(get("/api/public/matches/5/tips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.released").value(false))
                .andExpect(jsonPath("$.tips.length()").value(0))
                .andExpect(content().string(not(containsString("Alice"))));
    }

    @Test
    @DisplayName("Nach Anpfiff: Tipps werden geliefert")
    void releasedAfterKickoff() throws Exception {
        when(query.matchTips(5L)).thenReturn(
                new MatchTipsDto(5L, true, List.of(new PublicTipDto("Alice", 2, 1, 4))));
        mvc.perform(get("/api/public/matches/5/tips"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.released").value(true))
                .andExpect(jsonPath("$.tips[0].displayName").value("Alice"))
                .andExpect(jsonPath("$.tips[0].tipHome").value(2));
    }

    @Test
    @DisplayName("Unbekanntes Spiel: HTTP 404")
    void unknownMatch404() throws Exception {
        when(query.matchTips(999L)).thenThrow(new PublicNotFoundException());
        mvc.perform(get("/api/public/matches/999/tips"))
                .andExpect(status().isNotFound());
    }
}
