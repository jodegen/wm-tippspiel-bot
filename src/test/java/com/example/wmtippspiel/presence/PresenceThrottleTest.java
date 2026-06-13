package com.example.wmtippspiel.presence;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Tests der Presence-Drossel (F9, FR-008/FR-009): Mindestabstand-Garantie,
 * Coalescing („letzter Zustand gewinnt") und Idempotenz.
 */
class PresenceThrottleTest {

    private static final Instant T0 = Instant.parse("2026-06-14T20:00:00Z");
    private static final Duration MIN = Duration.ofMillis(5000);

    @Test
    @DisplayName("Erster Wunsch wird sofort gesendet")
    void firstSubmitSendsImmediately() {
        PresenceThrottle throttle = new PresenceThrottle(MIN);
        assertThat(throttle.submit("A", T0)).contains("A");
    }

    @Test
    @DisplayName("Gleicher Text wird nie erneut gesendet (FR-008)")
    void duplicateNeverSends() {
        PresenceThrottle throttle = new PresenceThrottle(MIN);
        throttle.submit("A", T0);
        assertThat(throttle.submit("A", T0.plusSeconds(10))).isEmpty();
        assertThat(throttle.hasPending()).isFalse();
    }

    @Test
    @DisplayName("Wunsch während der Sperrzeit wird verzögert und zusammengefasst (letzter gewinnt)")
    void coalescesDuringCooldownLastWins() {
        PresenceThrottle throttle = new PresenceThrottle(MIN);
        assertThat(throttle.submit("A", T0)).contains("A");

        // Innerhalb von 5 s mehrere Wünsche → kein sofortiges Senden, jüngster gemerkt.
        assertThat(throttle.submit("B", T0.plusMillis(1000))).isEmpty();
        assertThat(throttle.submit("C", T0.plusMillis(2000))).isEmpty();
        assertThat(throttle.submit("D", T0.plusMillis(3000))).isEmpty();
        assertThat(throttle.hasPending()).isTrue();

        // Flush erst ab Mindestabstand → genau der letzte Text ("D").
        assertThat(throttle.flush(T0.plusMillis(4999))).isEmpty();      // noch zu früh
        assertThat(throttle.flush(T0.plusMillis(5000))).contains("D");
        assertThat(throttle.hasPending()).isFalse();
    }

    @Test
    @DisplayName("Obsolet gewordener Wunsch (zurück zum aktuellen Text) wird verworfen")
    void revertingToCurrentTextCancelsPending() {
        PresenceThrottle throttle = new PresenceThrottle(MIN);
        throttle.submit("A", T0);
        throttle.submit("B", T0.plusMillis(1000));      // pending = B
        assertThat(throttle.hasPending()).isTrue();
        throttle.submit("A", T0.plusMillis(2000));      // zurück zu A (= zuletzt gesendet)
        assertThat(throttle.hasPending()).isFalse();
        assertThat(throttle.flush(T0.plusMillis(8000))).isEmpty();
    }

    @Test
    @DisplayName("Tor-Burst: gesendete Updates halten garantiert den Mindestabstand ein (≤5/20 s)")
    void burstNeverExceedsRate() {
        PresenceThrottle throttle = new PresenceThrottle(MIN);
        Instant last = null;
        int sends = 0;
        // 60 Wünsche im Sekundentakt über 60 s; nach jedem Submit ggf. flushen, sobald erlaubt.
        for (int i = 0; i < 60; i++) {
            Instant now = T0.plusSeconds(i);
            Optional<String> sent = throttle.submit("S" + i, now);
            if (sent.isEmpty() && throttle.hasPending() && throttle.pendingDelayMillis(now) == 0) {
                sent = throttle.flush(now);
            }
            if (sent.isPresent()) {
                if (last != null) {
                    assertThat(Duration.between(last, now).toMillis())
                            .as("Mindestabstand zwischen Sendungen")
                            .isGreaterThanOrEqualTo(MIN.toMillis());
                }
                last = now;
                sends++;
            }
        }
        // 60 s bei ≥5 s Abstand ⇒ höchstens ~12 Sendungen, nie >5 pro beliebigen 20 s.
        assertThat(sends).isLessThanOrEqualTo(13);
    }
}
