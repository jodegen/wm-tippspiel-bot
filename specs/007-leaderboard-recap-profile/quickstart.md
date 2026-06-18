# Quickstart — Feature 007 (F11/F12/F13)

## Voraussetzungen

- Bestehender, lauffähiger Bot (Branch `007-leaderboard-recap-profile`), PostgreSQL erreichbar, JDA-Gateway verbunden.
- Build: Maven, Java 21.

## Konfiguration (`application.yml`)

```yaml
app:
  discord:
    leaderboard-channel-id: "<id-des-read-only-ranglisten-channels>"   # NEU (F11), eigener Channel
  leaderboard:
    top-n: 15                                                          # NEU (F11), Default 15
```

`announce-channel-id` (F12) und die DB-Verbindung sind bereits vorhanden.

## Build & Migration

```bash
mvn -q clean verify        # baut + führt Tests aus; Liquibase wendet 010–012 beim Start an
mvn -q spring-boot:run     # startet den Bot (Liquibase migriert: matchday-Spalte + 2 neue Tabellen)
```

## Manuelle Verifikation

### F11 — Leaderboard-Board
1. Ranglisten-Channel öffnen → nach Start steht **genau ein** Board (Slot `board:leaderboard`).
2. Ein Spiel auf FINISHED bringen → `evaluateJob` wertet aus → dieselbe Nachricht wird **editiert** (kein Neu-Post).
3. Rang eines Users verändern (weiteres Spiel auswerten) → Pfeil `↑/↓/–` korrekt; neuer User in Top-N = `NEU`.
4. Board manuell löschen → nächste Auswertung postet es neu (Recovery).
5. Bot neu starten → Pfeile gegen den letzten Batch bleiben korrekt (Snapshot persistent).

### F12 — Spieltags-Rückblick
1. Alle Spiele eines `matchday` auf FINISHED+evaluated bringen → genau **ein** Rückblick im Announce-Channel.
2. `evaluateJob` erneut laufen lassen / Bot neu starten → **kein** zweiter Rückblick (Idempotenz, `matchday_recap`).
3. Unvollständigen Spieltag prüfen → **kein** Post.

### F13 — /profil
1. `/profil` → eigene Bilanz, **öffentlich** im Channel.
2. `/profil @user` → Bilanz des Users.
3. `/profil` auf User ohne Tipps → leere, gültige Bilanz (kein Fehler).

## Tests

```bash
mvn -q test -Dtest=LeaderboardRankDiffTest,MatchdayRecapServiceTest,ProfilAggregationTest,PersistenceIntegrationTest
```

- `LeaderboardRankDiffTest` — Pfeil-Regeln inkl. `NEU`/Gleichstand/Restart-Vergleichsbasis.
- `MatchdayRecapServiceTest` — Completion-Erkennung + ON-CONFLICT-Idempotenz + Leerfall.
- `ProfilAggregationTest` — Trefferquote (inkl. 0-Tipps), 4/3/2/0-Verteilung, bester/schlechtester Tipp.
- `PersistenceIntegrationTest` — neue Repos + `matches.matchday` (Testcontainers).

## Regressionssicherung (bestehende Features dürfen nicht brechen)

- `ScoringServiceTest`, `EvaluationServiceTest`, `RecalculationServiceTest` müssen unverändert grün bleiben (Scoring unberührt).
- Bestehende F7-Board-Tests grün halten nach Extraktion von `TrackedBoardPublisher` (`board:main` verhält sich identisch).
