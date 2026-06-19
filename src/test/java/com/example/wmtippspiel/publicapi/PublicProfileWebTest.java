package com.example.wmtippspiel.publicapi;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.example.wmtippspiel.publicapi.dto.PointDistributionDto;
import com.example.wmtippspiel.publicapi.dto.ProfileDto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Web-Layer-Test des Profil-Endpoints (Feature 008, US4): Adressierung über
 * publicId, kein user_id im JSON, unbekannter Identifier → 404, hitRate null bei
 * 0 Tipps (FR-018).
 */
@WebMvcTest(PublicApiController.class)
class PublicProfileWebTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PublicQueryService query;

    @MockBean
    private com.example.wmtippspiel.config.AppProperties properties;

    @Test
    @DisplayName("GET /players/{publicId} liefert Profil ohne user_id")
    void profileReturned() throws Exception {
        ProfileDto dto = new ProfileDto("pub-abc", "Alice", 1, 12, 2, 4, 50,
                new PointDistributionDto(2, 1, 0, 1), null, null, List.of());
        when(query.profile("pub-abc")).thenReturn(dto);
        mvc.perform(get("/api/public/players/pub-abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicId").value("pub-abc"))
                .andExpect(jsonPath("$.displayName").value("Alice"))
                .andExpect(jsonPath("$.distribution.p4").value(2))
                .andExpect(content().string(not(containsString("user_id"))));
    }

    @Test
    @DisplayName("0 gewertete Tipps: hitRatePercent ist null (keine Division durch 0)")
    void zeroTipsHitRateNull() throws Exception {
        ProfileDto dto = new ProfileDto("pub-empty", "Bob", null, 0, 0, 0, null,
                new PointDistributionDto(0, 0, 0, 0), null, null, List.of());
        when(query.profile("pub-empty")).thenReturn(dto);
        mvc.perform(get("/api/public/players/pub-empty"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hitRatePercent").value(nullValue()));
    }

    @Test
    @DisplayName("Unbekannter Identifier: HTTP 404")
    void unknownProfile404() throws Exception {
        when(query.profile("nope")).thenThrow(new PublicNotFoundException());
        mvc.perform(get("/api/public/players/nope"))
                .andExpect(status().isNotFound());
    }
}
