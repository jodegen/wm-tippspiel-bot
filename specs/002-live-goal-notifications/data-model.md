# Phase 1 Data Model: Live-Tor-Benachrichtigungen (F8)

## Erweiterung: `matches` (bestehende Tabelle)

Neue Spalten via Liquibase-Changeset `008-add-matches-notified-score.sql`:

| Feld | Typ (PostgreSQL) | Constraints | Beschreibung |
|---|---|---|---|
| `notified_home` | `INT` | NOT NULL, DEFAULT 0 | Heim-Tore, die bereits per Tor-Post gemeldet wurden |
| `notified_away` | `INT` | NOT NULL, DEFAULT 0 | Gast-Tore, die bereits per Tor-Post gemeldet wurden |

**Regeln**:
- Getrennt vom tatsächlichen Stand (`home_score`/`away_score`, der weiter vom
  regulären Sync gepflegt wird). `notified_*` = „bis hierhin gemeldet".
- Wird ausschließlich vom F8-Pfad geschrieben (`updateNotifiedScore`), nach dem
  Erzeugen der zugehörigen Events. Persistenz sichert Idempotenz & Recovery
  (FR-007/009/009a).
- Bleibt **außerhalb** des `Match`-Domain-Records; Zugriff über dedizierte
  Repository-Methoden (kein Umbau bestehender Mappings/Konstruktoren).

**Neue Repository-Methoden** (`MatchRepository`):
- `getNotifiedScore(long matchId) -> Score` (kleines Projektions-Record `Score(int home, int away)`).
- `updateNotifiedScore(long matchId, int home, int away)`.

## Flüchtige Entität: `GoalEvent` (keine Tabelle)

Von einer `GoalEventSource` erzeugt, vom `GoalNotifier` konsumiert.

| Feld | Typ | Beschreibung |
|---|---|---|
| `matchId` | long | Bezug zum Spiel |
| `home` / `away` | String | Teamnamen (für die Anzeige) |
| `kind` | enum `GOAL` / `CORRECTION` | Tor vs. Abwärts-Korrektur (VAR) |
| `scoringTeam` | enum `HOME` / `AWAY` (null bei `CORRECTION`) | welches Team getroffen hat |
| `newHome` / `newAway` | int | Laufender Stand **nach genau diesem Tor** (bei Mehrfach-Toren inkrementell hochgezählt); bei `CORRECTION` der korrigierte Stand |
| `minute` | Integer (nullable) | Spielminute, falls verfügbar |

## Zustands-/Ablauflogik (GoalDetector)

Eingabe: gemeldeter Stand (`notified_*`) + aktueller Stand (`current*`):

| Bedingung | Ergebnis |
|---|---|
| `current == notified` | keine Events (idempotent) |
| `current > notified` (Summe steigt, kein Wert kleiner) | je zusätzlichem Tor ein `GOAL`-Event (Team aus der Differenz) |
| mind. ein `current`-Wert `< notified` | ein `CORRECTION`-Event (VAR), kein `GOAL` |

Nach der Erzeugung setzt der Aufrufer `notified_* = current*`.

## Bezug zu Functional Requirements

| Element | FRs |
|---|---|
| `notified_*` Persistenz | FR-005, FR-007, FR-009, FR-009a |
| GoalDetector-Diff/Mehrfach | FR-006, FR-014 |
| VAR-Korrektur | FR-008 |
| GoalEvent / Posting | FR-010, FR-011 |
| Quelle entkoppelt | FR-012, FR-013 |
| Live-Fenster | FR-001, FR-002, FR-003, FR-004 |
