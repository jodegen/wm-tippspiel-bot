# Phase 0 Research: Live-Tor-Benachrichtigungen (F8)

## R1 — Abstraktion `GoalEventSource` (austauschbare Event-Quelle, FR-012)

- **Decision**: Interface `GoalEventSource` mit einer Methode, die die aktuell
  fälligen `GoalEvent`s liefert (`List<GoalEvent> fetchEvents()`). Default-
  Implementierung `ScoreDiffGoalEventSource` (Polling + Diff). Der `LiveGoalPollJob`
  und der `GoalNotifier` kennen **nur** das Interface bzw. `GoalEvent`.
- **Rationale**: Eine spätere Webhook-/WebSocket-Quelle implementiert dasselbe
  Interface und liefert `GoalEvent`s direkt — Job und Posting bleiben unverändert
  (FR-012). Der `GoalDetector` bleibt als wiederverwendbare Diff-Komponente
  erhalten (von der Polling-Quelle genutzt; eine Push-Quelle braucht ihn ggf. nicht).
- **Alternatives**: `GoalEventSource` liefert rohe Score-Snapshots → passt nicht
  zu Push-Quellen (Webhook liefert Ereignisse, keine Snapshots). Verworfen.

## R2 — Live-Polling-Fenster & Datenquelle

- **Decision**: Der `liveGoalPoll`-Job ruft den **bestehenden**
  `FootballDataClient.fetchMatches()` (ein Request liefert alle Spiele inkl.
  aktueller Stände/Status). `ScoreDiffGoalEventSource` filtert in-memory auf das
  Live-Fenster: `kickoff <= now <= kickoff + 2,5 h` **und** Status SCHEDULED/IN_PLAY.
  Außerhalb des Fensters keine Verarbeitung.
- **Rationale**: FR-001/002/004. Ein Request pro Poll (Default 60 s) = 1 Req/Min,
  klar unter 10/Min. Kein zusätzlicher Endpoint nötig; nutzt die vorhandene,
  defensive Fehlerbehandlung des Clients (Fehler → leere Liste → Poll übersprungen).
- **Alternatives**: Per-Match-Detail-Endpoint (mehr Requests, Rate-Limit-Risiko) —
  verworfen. Eigener WebClient — unnötig, bestehender reicht.

## R3 — Goal-Detector: Diff, Idempotenz, VAR, Mehrfach-Tore, Recovery

- **Decision**: Reine Funktion
  `detect(notifiedHome, notifiedAway, currentHome, currentAway, match) -> List<GoalEvent>`:
  - Gleichstand → leere Liste (**idempotent**, FR-007).
  - Anstieg → je zusätzlichem Tor ein `GOAL`-Event (Team aus der jeweiligen
    Differenz; FR-006/014). Jedes Event trägt den **laufenden Stand nach genau
    diesem Tor** (z. B. 1:0 → 3:0 ergibt zwei Home-Events mit 2:0 und 3:0), da das
    treffende Team bekannt ist und inkrementell hochgezählt wird. Reihenfolge
    zwischen Heim- und Auswärtstoren sowie die Spielminute sind bei Mehrfach-Toren
    best-effort, da die Quelle keine Einzeltor-Historie liefert.
  - Rückgang (mind. ein Wert kleiner) → ein `CORRECTION`-Event (VAR, FR-008);
    **kein** `GOAL`.
  - Persistenz des neuen gemeldeten Standes übernimmt der Aufrufer
    (`ScoreDiffGoalEventSource`) **nach** dem Erzeugen der Events.
- **Recovery/Nachmelden (FR-009/009a)**: Fällt aus der Persistenz von
  `notified_*`; nach Neustart wird die Differenz gegen den gespeicherten Stand
  gebildet → in der Downtime gefallene, noch nicht gemeldete Tore werden
  automatisch nachgereicht (je Tor ein Post).
- **Rationale**: Reine, von DB/Discord entkoppelte Logik → schnell und
  deterministisch test-first prüfbar.
- **Alternatives**: Detektor mit eigener Persistenz/Discord-Kopplung — schlechter
  testbar; verworfen.

## R4 — Discord-Posting (Role-Ping vs. Korrektur-Notiz)

- **Decision**: `GoalNotifier` postet `GOAL`-Events über die **bestehende**
  `AnnounceChannel.post(embed)` → **mit** Role-Ping (geklärt, FR-010).
  `CORRECTION`-Events über eine neue, additive `AnnounceChannel.postPlain(embed)`
  → **ohne** Role-Ping (Korrektur-Notiz soll nicht die ganze Rolle anpingen).
- **Rationale**: Wiederverwendung der Announce-Logik; Tor-Posts sollen pingen
  (Nutzerwunsch), Korrekturen sind reine Information. `postPlain` ist additiv,
  bricht keinen bestehenden Pfad.
- **Alternatives**: Auch Korrektur mit Ping — unnötig laut; verworfen.

## R5 — Persistenz der neuen Felder

- **Decision**: Liquibase-Changeset `008-add-matches-notified-score.sql`:
  `ALTER TABLE matches ADD COLUMN notified_home INT NOT NULL DEFAULT 0,
  ADD COLUMN notified_away INT NOT NULL DEFAULT 0`. `notified_*` bleibt außerhalb
  des `Match`-Domain-Records; Zugriff über neue `MatchRepository`-Methoden
  `getNotifiedScore(id)` / `updateNotifiedScore(id, h, a)`.
- **Rationale**: Prinzip II (Schema nur via Liquibase). Default 0 ist sicher:
  Spiele außerhalb des Live-Fensters werden nie gepollt, lösen also keine Events
  aus; ein zum Deploy-Zeitpunkt laufendes Spiel meldet ab dem aktuellen Stand
  (einmalige Nachmeldung). Außerhalb des `Match`-Records → keine
  Konstruktor-/Test-Brüche (bestehendes `SELECT *`-Mapping ignoriert Zusatzspalten).
- **Alternatives**: Felder in den `Match`-Record aufnehmen — bricht viele
  Konstruktoraufrufe/Tests; verworfen. Eigene Tabelle — unnötig, gehört je Match.

## R6 — Spielminute

- **Decision**: Spielminute ist optional; der `fetchMatches`-Endpoint liefert sie
  i. d. R. nicht zuverlässig → Events tragen meist keine Minute, der Post zeigt
  dann nur den Stand (FR-006/Edge Case „Minute fehlt").
- **Rationale**: Spec erlaubt „falls verfügbar"; kein zusätzlicher Endpoint nötig.
- **Alternatives**: Minute aus Detail-Endpoint holen — extra Requests; später,
  nicht MVP.

**Status**: Keine offenen NEEDS CLARIFICATION. Bereit für Phase 1.
