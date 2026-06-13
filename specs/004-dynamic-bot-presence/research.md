# Phase 0 — Research: Dynamische Bot-Presence (F9)

Alle offenen Punkte aus dem Spec wurden bereits in der `/speckit-clarify`-Session
(2026-06-13) entschieden; diese Datei konsolidiert die daraus folgenden
technischen Entscheidungen gegen die **bestehende** Architektur.

## R1 — Presence über JDA setzen

- **Decision**: `jda.getPresence().setActivity(Activity.watching(text))`. Das
  `JDA`-Bean existiert (`config/DiscordConfig#jda`) und wird in `PresenceManager`
  injiziert. Aktuell setzt **niemand** eine Activity → kein Konflikt.
- **Rationale**: Einziger, von JDA vorgesehener Weg; thread-safe wiederholbar.
- **Alternatives**: `setPresence(...)` mit Online-Status — nicht nötig, nur
  Activity ist gefordert (Typ `watching`).

## R2 — Datenquelle für den LIVE-Stand (Kern-Entscheidung)

- **Decision**: F9 liest den Zustand aus der DB (`MatchRepository`). Damit der
  Live-Stand **frisch aus `matches`** verfügbar ist (Spec FR-003), persistiert der
  bestehende Live-Poll-Pfad (`ScoreDiffGoalEventSource`) den frischen Stand+Status
  der Spiele im Live-Fenster zusätzlich in `matches` — über die **vorhandenen**
  Spalten `home_score`/`away_score`/`status`. Der Poll holt diese Daten ohnehin
  bereits per `FootballDataClient`; es entstehen **keine** zusätzlichen API-Aufrufe
  und **keine** Schema-Änderung.
- **Rationale**: Hält `PresenceManager` als reinen, gut testbaren DB-Leser; LIVE-
  Eintritt (Status `IN_PLAY`, Stand 0:0), Stand-Änderungen und LIVE-Austritt
  (Status `FINISHED`) tracken alle im `liveGoalPoll`-Takt (Default 30 s) statt erst
  im 15-min-`syncMatches`-Takt. Entspricht dem Hinweis des Nutzers „… sofern der
  Live-Stand bereits aus `matches` verfügbar ist".
- **Alternatives considered**:
  - *GoalEvents direkt konsumieren (zero-write)*: `PresenceManager` hält Live-Stände
    nur in-memory aus `GoalEvent`s. Verworfen: LIVE-Eintritt bei 0:0 (ohne Tor) und
    LIVE-Austritt würden bis zum nächsten `syncMatches` (≤15 min) verzögern;
    dupliziert Stand-Buchführung neben `notified_*`.
  - *Eigener API-Abruf in F9*: verworfen — zusätzliche Requests, Rate-Limit-Risiko.

## R3 — Auswahl bei mehreren gleichzeitig laufenden Spielen (FR-013)

- **Decision**: Das **zuletzt veränderte** Spiel gewinnt. `PresenceManager` hält
  je laufendem Spiel den zuletzt gesehenen Stand und einen `lastChange`-Zeitstempel;
  bei jedem `recompute()` wird ein Spiel, dessen Stand sich seit dem letzten
  Snapshot geändert hat, auf `lastChange = now` gesetzt. Maximaler `lastChange`
  gewinnt; **Tie-Breaker** ohne unterscheidendes Tor: früherer Anpfiff.
- **Rationale**: Spec-Klärung 2026-06-13; maximal event-getrieben, deterministisch.
- **Alternatives**: frühester Anpfiff / rotierend — verworfen (rotierend erzeugt
  unnötige Updates und kollidiert mit dem Throttling-Ziel).

## R4 — Throttling-Algorithmus (FR-009, Garantie ≤5/20 s)

- **Decision**: Garantierter **Mindestabstand** zwischen zwei tatsächlichen
  `setActivity`-Aufrufen plus **Coalescing**. `PresenceThrottle` (rein, `Clock`-
  basiert) hält `lastSentAt` und einen einzigen `pendingText`-Slot:
  - Wenn `now - lastSentAt >= MIN_INTERVAL` → sofort senden, `lastSentAt = now`.
  - Sonst → `pendingText = text` (überschreibt, „letzter Zustand gewinnt") und es
    wird **ein** verzögerter Flush auf `lastSentAt + MIN_INTERVAL` geplant.
  - Beim Flush wird der jeweils aktuellste `pendingText` gesendet (sofern ≠ zuletzt
    gesendet) und neu terminiert, falls inzwischen wieder etwas anliegt.
- **Parameter**: `MIN_INTERVAL` konfigurierbar `${app.presence.min-update-interval-ms:5000}`.
  5000 ms ⇒ höchstens 4 Änderungen pro 20 s — Sicherheitsmarge unter dem
  5/20-s-Limit (20 s / 5 = 4 s wäre der Grenzwert; 5 s ist bewusst defensiver).
- **Rationale**: Ein fester Mindestabstand ist die einfachste **garantierte** obere
  Schranke; Coalescing verhindert Rückstau bei Tor-Bursts und stellt sicher, dass
  immer der aktuellste Stand landet.
- **Alternatives**: Token-Bucket/Sliding-Window (5 Tokens/20 s) — flexibler, aber
  komplexer und ohne Mehrwert für ein einzelnes, selten wechselndes Status-Feld.

## R5 — Trigger der Neuberechnung (Spec-Klärung)

- **Decision**: `presenceManager.recompute()` wird aufgerufen
  (a) am Ende jedes `liveGoalPoll`-Zyklus (`LiveGoalPollJob`),
  (b) nach jedem `boardRefresh` (`BoardRefreshJob`),
  (c) bei JDA `onReady`/`onReconnected`/`onSessionRecreate` (Startup/Reconnect).
  **Kein eigener Scheduler** in F9.
- **Rationale**: Klärung 2026-06-13. (a) deckt LIVE-Eintritt/-Stand/-Austritt im
  Live-Takt, (b) deckt UPCOMING (≥ stündlich begrenzt der Mindestabstand ohnehin die
  reale Update-Frequenz), (c) erfüllt FR-011.
- **Note (UPCOMING „höchstens stündlich")**: `boardRefresh` läuft alle ~15 min;
  da der UPCOMING-Text sich nur ändert, wenn ein anderes „nächstes Spiel" ansteht,
  greift FR-008 (kein Update bei gleichem Text) und der Mindestabstand — eine
  zusätzliche harte Stundengrenze ist nicht nötig, wird aber durch die seltene
  Textänderung faktisch eingehalten.

## R6 — Reconnect-Festigkeit (FR-011, Prinzip V)

- **Decision**: `PresenceManager` registriert sich per `@PostConstruct`
  (`jda.addEventListener(this)`) als `ListenerAdapter` und ruft bei
  `onReady`/`onReconnected`/`onSessionRecreate` `recompute()`. Beim Reconnect wird
  der intern gehaltene „zuletzt gesetzte Text" verworfen-geprüft neu gesetzt, damit
  die Presence nie leer/veraltet bleibt.
- **Rationale**: Selbst-Registrierung nach Bean-Bau vermeidet den Build-Zeit-Zyklus
  (das `JDA`-Bean hängt nicht von `PresenceManager` ab). JDA sendet eine gesetzte
  Activity zwar i. d. R. nach Reconnect erneut — der explizite Listener garantiert
  es unabhängig davon.
- **Alternatives**: Listener im `JDABuilder` von `DiscordConfig` ergänzen —
  verworfen (erzwänge `PresenceManager` als Konstruktor-Argument des `jda`-Beans →
  Zyklus).

## R7 — Team-Kürzel (Spec-Klärung, Key Entity „Team-Kürzel-Mapping")

- **Decision**: `TeamCodeResolver` lädt eine statische Ressource
  `resources/presence/team-codes.properties` (`Teamname=KÜRZEL`, z. B.
  `Deutschland=GER`). Fehlt ein Eintrag, wird der Klartextname defensiv gekürzt.
- **Rationale**: Analog zum bereits manuell gepflegten TV-Sender-Mapping; akkurate
  3-Letter-Codes ohne externe Abhängigkeit. 48 Teams sind überschaubar pflegbar.
- **Alternatives**: algorithmische Ableitung (ungenau) / volle Namen (Längenrisiko)
  — verworfen gemäß Klärung.

## R8 — Textlänge & Emojis (FR-010)

- **Decision**: Nur Standard-Unicode-Emojis (`⚽`, `👀`, `🏆`). Der Resolver hält den
  Text defensiv kurz (Kürzel + Stand); bei fehlendem Kürzel wird der Klartext so
  gekürzt, dass die **JDA-Activity-Längengrenze von 128 Zeichen** sicher eingehalten
  wird (Kürzung greift defensiv ab dieser Grenze).
- **Rationale**: Keine custom Emojis nötig; kompakte Codes halten den Text klein.

## Offene Punkte

Keine. Alle `NEEDS CLARIFICATION` sind aufgelöst; F9 benötigt keine Schema-Änderung
und keine neuen Abhängigkeiten.
