# Phase 1 — Data Model: Live-Leaderboard-Board, Spieltags-Rückblick & /profil

Alle Änderungen sind **additiv** und erfolgen über Liquibase-Changesets (Verfassung Prinzip II). Bestehende Tabellen/Spalten bleiben unverändert; `tips.points` und das CHECK24-Schema werden nur gelesen.

## Bestehende, nur gelesene Strukturen

- **`tips`** (`user_id`, `username`, `match_id`, `home_score`, `away_score`, `points`, `created_at`) — Quelle für Punkte, exakte Treffer, Verteilung.
- **`matches`** (`id`, `home`, `away`, `kickoff`, `stage`, `group_label`, `channel`, `odds_*`, `home_score`, `away_score`, `status`, `revealed`, `evaluated`, …) — Ergebnis & Status; Quoten für „unwahrscheinlichstes Ergebnis".
- **`bot_messages`** (`key` PK, `channel_id`, `message_id`, `updated_at`) — wiederverwendet mit **neuem Slot** `board:leaderboard`. **Keine** Schema-Änderung.

## Änderung 1 — `matches.matchday` (additive Spalte)

| Feld | Typ | Beschreibung |
|---|---|---|
| `matchday` | INT NULL | Spieltag-Nummer aus football-data.org. NULL für Spiele ohne matchday (z. B. manche K.o.-Daten). |

- **Changeset**: `010-add-matches-matchday.sql` — `ALTER TABLE matches ADD COLUMN matchday INT;`
- **Befüllung**: Match-Sync-Mapping übernimmt `matchday` beim Upsert (additive Mapper-Erweiterung; bestehende Upsert-Spalten unverändert ergänzt).
- **Validierung**: keine Constraints (nullable). Backfill optional beim nächsten Sync.

## Entity: `leaderboard_snapshot` (F11)

Hält den Rang **jedes** Users aus dem zuletzt abgeschlossenen Auswertungs-Batch — Vergleichsbasis für die Rang-Veränderung.

| Feld | Typ | Beschreibung |
|---|---|---|
| `user_id` | TEXT PK | Discord-User-ID |
| `rank` | INT NOT NULL | Rang im letzten Batch (Standard Competition Ranking) |
| `captured_at` | TIMESTAMPTZ NOT NULL | Zeitpunkt der Snapshot-Erstellung (UTC) |

- **Changeset**: `011-create-leaderboard-snapshot.sql`
- **Lifecycle**: Nach jedem Batch mit ≥1 Auswertung **vollständig ersetzt** (`DELETE` + Batch-`INSERT`, oder `upsert` aller aktuellen Ränge + Entfernen verwaister User). Vor dem ersten Batch leer → alle User „NEU".
- **Relationship**: lose über `user_id` zu `tips.user_id` (kein FK, da kein User-Stammsatz).
- **Validierung**: `rank ≥ 1`.

## Entity: `matchday_recap` (F12)

Idempotenz-Marker: ein bereits geposteter Spieltags-Rückblick je Recap-Key.

| Feld | Typ | Beschreibung |
|---|---|---|
| `recap_key` | TEXT PK | `"md:<n>"` bei vorhandenem `matchday`, sonst `"stage:<STAGE>"` |
| `posted_at` | TIMESTAMPTZ NOT NULL | Zeitpunkt des Postings (UTC) |

- **Changeset**: `012-create-matchday-recap.sql`
- **Lifecycle**: Append-only. Eintrag via `INSERT ... ON CONFLICT (recap_key) DO NOTHING`; nur bei tatsächlichem Insert (rowsAffected=1) wird gepostet. Eine spätere Re-Evaluation löscht den Eintrag **nicht** → kein zweiter Post (FR-018).
- **Validierung**: `recap_key` nicht leer.

## Abgeleitete (nicht persistierte) Wertobjekte

- **`RankDelta`** (F11): `userId`, `currentRank`, `previousRank` (nullable), `arrow` (`↑n` / `↓n` / `–` / `NEU`). Berechnet aus `leaderboard()` ⨝ `leaderboard_snapshot`.
- **`LeaderboardEntry`** (bestehend): `userId`, `username`, `totalPoints`, `tipCount`, `exactHits` — aus `TipRepository.leaderboard()`.
- **`MatchdayRecap`** (F12, in-memory): `recapKey`, Top-Punktesammler (Liste), bester Einzeltipp (User + Spiel + Tipp + Punkte), Liste der Nuller.
- **`UserProfile`** (F13, in-memory): `rank`, `totalPoints`, `exactHits`, `evaluatedTipCount`, `hitRate`, `bestTip`, `worstTip`, `distribution` (Map 4/3/2/0 → count).

## Neue/erweiterte Repository-Methoden

| Repository | Methode | Zweck |
|---|---|---|
| `LeaderboardSnapshotRepository` (neu) | `findAllRanks()` → `Map<String,Integer>` | Vergleichsbasis laden |
| | `replaceAll(List<SnapshotRow>)` | Snapshot nach Batch ersetzen |
| `MatchdayRecapRepository` (neu) | `tryClaim(recapKey)` → `boolean` | atomarer ON-CONFLICT-Insert |
| `MatchRepository` (erweitert) | `findByMatchday(...)` / `findByRecapKey(...)` | Vollständigkeitsprüfung + Aggregation |
| | (Mapper/Upsert) `matchday` | Spalte lesen/schreiben |
| `TipRepository` (erweitert) | `findEvaluatedTipsByUser(userId)` | F13-Verteilung/bester/schlechtester Tipp |
| | `matchdayLeaderboard(recapKey)` | F12 Top-Punktesammler/Nuller des Spieltags |

## Liquibase-Registrierung

`db.changelog-master.yaml` erhält drei neue Includes in Reihenfolge nach dem letzten bestehenden Changeset (009):

```yaml
- include: { file: db/changelog/changesets/010-add-matches-matchday.sql }
- include: { file: db/changelog/changesets/011-create-leaderboard-snapshot.sql }
- include: { file: db/changelog/changesets/012-create-matchday-recap.sql }
```
