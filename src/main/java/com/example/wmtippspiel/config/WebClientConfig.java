package com.example.wmtippspiel.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import reactor.netty.http.client.HttpClient;

/**
 * Zwei {@link WebClient}-Instanzen für die externen APIs (research.md R5):
 * football-data.org (Header {@code X-Auth-Token}) und The Odds API (apiKey als
 * Query-Param am Aufruf). Konservative Timeouts schützen die Jobs.
 */
@Configuration
public class WebClientConfig {

    private static final String FOOTBALL_DATA = "footballDataWebClient";
    private static final String ODDS = "oddsWebClient";

    private HttpClient httpClient() {
        return HttpClient.create()
                .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                .responseTimeout(Duration.ofSeconds(15))
                .doOnConnected(conn -> conn.addHandlerLast(new ReadTimeoutHandler(15)));
    }

    @Bean(FOOTBALL_DATA)
    public WebClient footballDataWebClient(AppProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.footballData().baseUrl())
                .defaultHeader("X-Auth-Token", safe(properties.footballData().apiKey()))
                .clientConnector(new ReactorClientHttpConnector(httpClient()))
                .build();
    }

    @Bean(ODDS)
    public WebClient oddsWebClient(AppProperties properties) {
        return WebClient.builder()
                .baseUrl(properties.odds().baseUrl())
                .clientConnector(new ReactorClientHttpConnector(httpClient()))
                .build();
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }
}
