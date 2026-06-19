package com.example.wmtippspiel.publicapi;

import java.time.Duration;

import com.example.wmtippspiel.config.AppProperties;
import com.github.benmanes.caffeine.cache.Caffeine;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Konfiguration der öffentlichen Read-only-API (Feature 008): leichtes
 * TTL-Caching für Spielplan/Leaderboard (FR-021). Die CORS-Freigabe für die
 * Frontend-Domain wird in Phase 4 ergänzt.
 */
@Configuration
@EnableCaching
public class PublicApiConfig {

    /** Caches mit kurzer, konfigurierbarer TTL (Default 5 s). */
    @Bean
    public CacheManager publicApiCacheManager(AppProperties properties) {
        long ttl = properties.publicApi() == null ? 5L : properties.publicApi().cacheTtlSeconds();
        CaffeineCacheManager manager = new CaffeineCacheManager("schedule", "leaderboard");
        manager.setCaffeine(Caffeine.newBuilder().expireAfterWrite(Duration.ofSeconds(Math.max(1L, ttl))));
        return manager;
    }
}
