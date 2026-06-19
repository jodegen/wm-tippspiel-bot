package com.example.wmtippspiel.publicapi;

import java.time.Duration;
import java.util.List;

import com.example.wmtippspiel.config.AppProperties;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Konfiguration der öffentlichen Read-only-API (Feature 008): leichtes
 * TTL-Caching für Spielplan/Leaderboard (FR-021) und CORS-Freigabe der
 * Frontend-Domain(s) (R8) — nur lesende Methoden, keine Credentials.
 */
@Configuration
@EnableCaching
public class PublicApiConfig implements WebMvcConfigurer {

    private final AppProperties properties;

    public PublicApiConfig(AppProperties properties) {
        this.properties = properties;
    }

    /** Caches mit kurzer, konfigurierbarer TTL (Default 5 s). */
    @Bean
    public CacheManager publicApiCacheManager() {
        long ttl = properties.publicApi() == null ? 5L : properties.publicApi().cacheTtlSeconds();
        CaffeineCacheManager manager = new CaffeineCacheManager("schedule", "leaderboard");
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(Math.max(1L, ttl))));
        return manager;
    }

    /** CORS nur für die konfigurierten Origins (z. B. Vercel-Domain); nur GET. */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        List<String> origins = properties.publicApi() == null ? null : properties.publicApi().corsAllowedOrigins();
        if (origins == null || origins.isEmpty()) {
            return;
        }
        registry.addMapping("/api/public/**")
                .allowedOrigins(origins.toArray(String[]::new))
                .allowedMethods("GET", "OPTIONS")
                .allowCredentials(false);
    }
}
