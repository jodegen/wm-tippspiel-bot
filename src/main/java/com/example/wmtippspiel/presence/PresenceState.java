package com.example.wmtippspiel.presence;

/**
 * Der aktuell anzuzeigende Presence-Zustand (F9). Rein abgeleitet aus dem
 * Datenbestand — keine Persistenz (data-model.md).
 *
 * @param type höchstpriorisierter zutreffender Zustand (LIVE &gt; UPCOMING &gt; IDLE)
 * @param text fertiger Anzeigetext inkl. Standard-Emoji (z. B. {@code ⚽ LIVE: GER 2:1 FRA})
 */
public record PresenceState(Type type, String text) {

    public enum Type { LIVE, UPCOMING, IDLE }

    public static PresenceState live(String text) {
        return new PresenceState(Type.LIVE, text);
    }

    public static PresenceState upcoming(String text) {
        return new PresenceState(Type.UPCOMING, text);
    }

    public static PresenceState idle(String text) {
        return new PresenceState(Type.IDLE, text);
    }
}
