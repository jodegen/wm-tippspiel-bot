package com.example.wmtippspiel.presence;

import java.util.Comparator;
import java.util.List;

import com.example.wmtippspiel.domain.model.Match;

import org.springframework.stereotype.Component;

/**
 * Reine Zustandslogik der Bot-Presence (F9). Bildet aus dem Datenbestand genau
 * einen priorisierten Zustand — <b>LIVE &gt; UPCOMING &gt; IDLE</b> — und den
 * fertigen Anzeigetext (FR-002). Ohne Seiteneffekte, vollständig unit-testbar
 * (contracts/presence-manager.md C1–C3).
 */
@Component
public class PresenceStateResolver {

    /** Default-Fallback, falls keine IDLE-Konfiguration übergeben wird. */
    public static final String DEFAULT_IDLE = "🏆 WM 2026 /tipp";

    /** Spätester lastChange gewinnt; bei Gleichstand der frühere Anpfiff (FR-013). */
    private static final Comparator<LiveMatchView> MOST_RECENTLY_CHANGED =
            Comparator.comparing(LiveMatchView::lastChange,
                            Comparator.nullsFirst(Comparator.naturalOrder()))
                    .thenComparing(LiveMatchView::kickoff, Comparator.reverseOrder());

    private final TeamCodeResolver codes;

    public PresenceStateResolver(TeamCodeResolver codes) {
        this.codes = codes;
    }

    /**
     * @param inPlay       laufende Spiele (Status IN_PLAY) inkl. Stand/Anpfiff/lastChange
     * @param nextUpcoming nächstes anstehendes Spiel oder {@code null}
     * @param idleText     konfigurierter IDLE-Fallback ({@code null}/leer ⇒ Default)
     */
    public PresenceState resolve(List<LiveMatchView> inPlay, Match nextUpcoming, String idleText) {
        // Priorität 1: LIVE — sobald mindestens ein Spiel läuft (FR-003).
        if (inPlay != null && !inPlay.isEmpty()) {
            LiveMatchView pick = inPlay.stream().max(MOST_RECENTLY_CHANGED).orElseThrow();
            String text = "⚽ LIVE: " + codes.code(pick.home())
                    + " " + pick.homeScore() + ":" + pick.awayScore()
                    + " " + codes.code(pick.away());
            return PresenceState.live(text);
        }
        // Priorität 2: UPCOMING — kein Spiel läuft, ein künftiges existiert (FR-005).
        if (nextUpcoming != null) {
            String text = "👀 Nächstes: " + codes.code(nextUpcoming.home())
                    + " vs " + codes.code(nextUpcoming.away());
            return PresenceState.upcoming(text);
        }
        // Priorität 3: IDLE — statischer Fallback (FR-007).
        String idle = (idleText == null || idleText.isBlank()) ? DEFAULT_IDLE : idleText;
        return PresenceState.idle(idle);
    }
}
