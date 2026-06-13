package com.example.wmtippspiel.presence;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Reine, {@link Instant}-basierte Drossel für Presence-Updates (F9, FR-008/FR-009).
 *
 * <p>Garantiert einen Mindestabstand zwischen zwei tatsächlich gesendeten Texten
 * ({@code minInterval}) und fasst überzählige Wünsche zusammen (Coalescing,
 * „letzter Zustand gewinnt"). Daraus folgt höchstens ein Update je
 * {@code minInterval} — bei Default 5 s also ≤4 Updates pro 20 s, strikt unter
 * dem Discord-Limit (5/20 s), sodass der 60-s-Backoff nie ausgelöst wird.
 *
 * <p>Idempotent: ein Text gleich dem zuletzt gesendeten löst nie ein Update aus
 * (FR-008) und verwirft einen ggf. anstehenden, inzwischen obsoleten Wunsch.
 *
 * <p>Diese Klasse trifft nur die Entscheidung; das eigentliche {@code setActivity}
 * und das Terminieren des verzögerten Flush übernimmt der {@link PresenceManager}.
 * Methoden sind {@code synchronized}, da Trigger aus mehreren Threads kommen.
 */
public class PresenceThrottle {

    private final long minIntervalMs;

    private String lastSentText;
    private Instant lastSentAt;
    private String pendingText;

    public PresenceThrottle(Duration minInterval) {
        this.minIntervalMs = Math.max(0, minInterval.toMillis());
    }

    /**
     * Meldet den gewünschten Anzeigetext an.
     *
     * @return den jetzt zu sendenden Text, falls der Mindestabstand eingehalten ist;
     *         sonst leer (entweder unverändert/dedupliziert oder zur späteren
     *         Auslieferung vorgemerkt — siehe {@link #pendingDelayMillis(Instant)}).
     */
    public synchronized Optional<String> submit(String desiredText, Instant now) {
        if (desiredText == null) {
            return Optional.empty();
        }
        if (desiredText.equals(lastSentText)) {
            pendingText = null;           // anstehenden, nun obsoleten Wunsch verwerfen (FR-008)
            return Optional.empty();
        }
        if (canSend(now)) {
            send(desiredText, now);
            return Optional.of(desiredText);
        }
        pendingText = desiredText;        // Coalescing: jüngster Wunsch gewinnt
        return Optional.empty();
    }

    /**
     * Liefert den vorgemerkten Text aus, sobald der Mindestabstand erreicht ist.
     * Vom verzögerten Flush-Task aufgerufen.
     *
     * @return den jetzt zu sendenden Text oder leer (nichts anstehend, dedupliziert
     *         oder noch zu früh).
     */
    public synchronized Optional<String> flush(Instant now) {
        if (pendingText == null) {
            return Optional.empty();
        }
        if (pendingText.equals(lastSentText)) {
            pendingText = null;
            return Optional.empty();
        }
        if (!canSend(now)) {
            return Optional.empty();      // zu früh – Aufrufer terminiert neu
        }
        String text = pendingText;
        send(text, now);
        return Optional.of(text);
    }

    /** Steht ein noch nicht gesendeter Text an? */
    public synchronized boolean hasPending() {
        return pendingText != null && !pendingText.equals(lastSentText);
    }

    /**
     * Millisekunden bis ein anstehender Text frühestens gesendet werden darf
     * ({@code 0} = sofort; {@code -1} = nichts anstehend).
     */
    public synchronized long pendingDelayMillis(Instant now) {
        if (!hasPending()) {
            return -1L;
        }
        if (lastSentAt == null) {
            return 0L;
        }
        long elapsed = Duration.between(lastSentAt, now).toMillis();
        return Math.max(0L, minIntervalMs - elapsed);
    }

    private boolean canSend(Instant now) {
        return lastSentAt == null || Duration.between(lastSentAt, now).toMillis() >= minIntervalMs;
    }

    private void send(String text, Instant now) {
        lastSentText = text;
        lastSentAt = now;
        pendingText = null;
    }
}
