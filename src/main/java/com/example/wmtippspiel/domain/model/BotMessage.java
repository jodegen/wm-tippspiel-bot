package com.example.wmtippspiel.domain.model;

import java.time.Instant;

/** Persistente, vom Bot editierte Board-Nachricht (F7 / Recovery). */
public record BotMessage(
        String key,
        String channelId,
        String messageId,
        Instant updatedAt) {
}
