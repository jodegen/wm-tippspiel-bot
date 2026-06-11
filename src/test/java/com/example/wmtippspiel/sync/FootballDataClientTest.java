package com.example.wmtippspiel.sync;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import com.example.wmtippspiel.domain.model.Match;
import com.example.wmtippspiel.domain.model.MatchStatus;
import com.example.wmtippspiel.domain.model.Stage;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Testet das Response-Mapping und die defensive Fehlerbehandlung des
 * football-data.org-Clients (FR-001/032) gegen einen MockWebServer.
 */
class FootballDataClientTest {

    private MockWebServer server;
    private FootballDataClient client;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        WebClient webClient = WebClient.builder().baseUrl(server.url("/").toString()).build();
        client = new FootballDataClient(webClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    @DisplayName("Mappt Spiele inkl. Teams, Zeit, Stage, Gruppe, Status und Endstand")
    void mapsMatches() {
        server.enqueue(new MockResponse()
                .addHeader("Content-Type", "application/json")
                .setBody("""
                        {"matches":[
                          {"id":537001,"utcDate":"2026-06-14T19:00:00Z","status":"FINISHED",
                           "stage":"GROUP_STAGE","group":"GROUP_A",
                           "homeTeam":{"name":"Germany"},"awayTeam":{"name":"Scotland"},
                           "score":{"fullTime":{"home":2,"away":1}}},
                          {"id":537002,"utcDate":"2026-06-15T16:00:00Z","status":"SCHEDULED",
                           "stage":"LAST_16","group":null,
                           "homeTeam":{"name":null},"awayTeam":{"name":"Spain"},
                           "score":{"fullTime":{"home":null,"away":null}}}
                        ]}
                        """));

        List<Match> matches = client.fetchMatches();

        assertThat(matches).hasSize(2);
        Match first = matches.get(0);
        assertThat(first.id()).isEqualTo(537001L);
        assertThat(first.home()).isEqualTo("Germany");
        assertThat(first.away()).isEqualTo("Scotland");
        assertThat(first.kickoff()).isEqualTo(Instant.parse("2026-06-14T19:00:00Z"));
        assertThat(first.stage()).isEqualTo(Stage.GROUP_STAGE);
        assertThat(first.groupLabel()).isEqualTo("A");
        assertThat(first.status()).isEqualTo(MatchStatus.FINISHED);
        assertThat(first.homeScore()).isEqualTo(2);
        assertThat(first.awayScore()).isEqualTo(1);

        Match second = matches.get(1);
        assertThat(second.home()).isEqualTo("TBD"); // unbestimmtes Team
        assertThat(second.stage()).isEqualTo(Stage.LAST_16);
        assertThat(second.groupLabel()).isNull();
        assertThat(second.homeScore()).isNull();
    }

    @Test
    @DisplayName("Liefert bei Fehler (z. B. Rate-Limit) eine leere Liste statt zu crashen")
    void returnsEmptyOnError() {
        server.enqueue(new MockResponse().setResponseCode(429));

        assertThat(client.fetchMatches()).isEmpty();
    }
}
