package com.example.wmtippspiel.publicapi;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import com.example.wmtippspiel.config.AppProperties;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifiziert die CORS-Freigabe der konfigurierten Frontend-Domain (Feature 008,
 * R8): ein Preflight des erlaubten Origins erhält den passenden
 * {@code Access-Control-Allow-Origin}-Header. Die Origins müssen bereits bei der
 * Context-Initialisierung feststehen (addCorsMappings läuft beim Start), daher
 * ein echtes {@link AppProperties}-Bean statt eines spät gestubbten Mocks.
 */
@WebMvcTest(PublicApiController.class)
@Import({PublicApiConfig.class, PublicApiCorsTest.Props.class})
class PublicApiCorsTest {

    private static final String ORIGIN = "https://wm-tippspiel.vercel.app";

    @Autowired
    private MockMvc mvc;

    @MockBean
    private PublicQueryService query;

    @TestConfiguration
    static class Props {
        @Bean
        AppProperties appProperties() {
            return new AppProperties(null, null, null, null, null,
                    new AppProperties.PublicApi(List.of(ORIGIN), "secret", 5));
        }
    }

    @Test
    @DisplayName("Preflight des erlaubten Origins erhält Access-Control-Allow-Origin")
    void preflightAllowsConfiguredOrigin() throws Exception {
        mvc.perform(options("/api/public/leaderboard")
                        .header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
    }
}
