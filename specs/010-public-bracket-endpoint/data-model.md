# Phase 1 — Datenmodell: Öffentlicher Bracket-Endpoint

Zwei Ebenen: (A) die **statische Topologie** (Code-Konstante, nie persistiert) und (B) die **DTO-Antwortform**. Dazu die minimale, additive Persistenz-Änderung (`winner`).

## A. Statische Topologie (Code-Konstante in `BracketTopology`)

### Entity: `TopologyEntry`
Ein fester Knoten des Baums. Genau 32 Einträge (FIFA-Match 73–104).

| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `stage` | `Stage` (intern) | LAST_32 / LAST_16 / QUARTER_FINAL / SEMI_FINAL / THIRD_PLACE / FINAL |
| `slotIndex` | `int` | 1..n innerhalb der Stage (kickoff-sortiert) |
| `fifaMatchNo` | `int` | 73–104, stabil |
| `sourceMatchNos` | `int[]` (0 oder 2 Einträge) | Quell-Match-Nrn; LAST_32 = leer; sonst genau 2 eindeutige |
| `nextMatchNo` | `Integer` (nullable) | Ziel-Match; `null` nur für FINAL (104) und THIRD_PLACE (103) |
| `sourceRole` | `enum {WINNER, LOSER}` | WINNER überall; LOSER nur für die Quellen von THIRD_PLACE (103) |

**Validierungsregeln** (durch `BracketTopologyConsistencyTest` erzwungen, FR-007 / SC-002):
- Genau 32 Einträge; FIFA-Nrn lückenlos 73..104.
- LAST_32 (73–88): 16 Einträge, `sourceMatchNos` leer (Wurzeln des dargestellten Baums).
- Jedes Nicht-LAST_32-Spiel: genau **2** `sourceMatchNos`, beide **eindeutig**, beide existieren als FIFA-Nr.
- LAST_16: 8, QUARTER_FINALS: 4, SEMI_FINALS: 2, THIRD_PLACE: 1, FINAL: 1.
- FINAL (104).sources = {101, 102} (Sieger beider Halbfinals).
- THIRD_PLACE (103).sources = {101, 102} mit `sourceRole = LOSER`.
- Kein `nextMatchNo`-Zyklus; jede Nicht-Endrunde verweist auf ein existierendes höheres Spiel.
- Jedes Quell→Ziel ist konsistent invertierbar: ist X Quelle von Z, dann ist `nextMatchNo(X) = Z`.

### Entity: `Last32Placeholder`
Platzhalter-Labels je LAST_32-Slot (aus `WC2026_BRACKET_TOPOLOGY.md`, Tabelle 73–88).

| Feld | Typ | Beispiel |
|------|-----|----------|
| `fifaMatchNo` | `int` | 73 |
| `homeLabel` | `String` | „Sieger Gruppe A" |
| `awayLabel` | `String` | „Zweiter Gruppe B" / „Dritter A/B/C/D/F" |

Platzhalter für LAST_16+ werden generisch aus den Quell-Nrn gebildet: „Sieger Match 89" bzw. „Verlierer Match 101" (THIRD_PLACE).

## B. Persistenz-Änderung (additiv)

### `matches.winner` (neue Spalte)
- **Changeset**: `013-add-matches-winner.sql`
- **DDL**: `ALTER TABLE matches ADD COLUMN winner TEXT;` (nullable, kein Default, keine Backfill-Migration nötig)
- **Werte**: `HOME_TEAM` | `AWAY_TEAM` | `DRAW` | `NULL` (unbekannt)
- **Quelle**: football-data `score.winner` (in `FootballDataClient.mapMatch`)
- **Schreibregeln**: `upsert` und `updateLiveScore` setzen `winner` additiv mit COALESCE-Guard (ein bekannter Wert wird nicht durch transientes `null` überschrieben — analog `home_score`/`away_score`).

### `Stage`-Enum (additiv)
- Neuer Wert `LAST_32` (vor `LAST_16`). `stage` ist TEXT → keine DDL.

### `MatchWinner`-Enum (neu, Domäne)
`enum MatchWinner { HOME_TEAM, AWAY_TEAM, DRAW }` — nullable in `Match`.

### `Match`-Record (additiv)
- Neues Feld `MatchWinner winner` (nullable). Bestehende Kompatibilitäts-Konstruktoren bleiben erhalten und setzen `winner = null`.
- `map()` in `MatchRepository` liest `winner` (null-tolerant).

## C. DTO-Antwortform (öffentlich, `publicapi.dto`)

Reine, leak-freie Records (kein `user_id`, keine internen Schlüssel). Stage-Werte im **football-data-Vokabular**.

### `BracketDto`
```
{ "rounds": [ BracketRoundDto, ... ] }   // genau 6, feste Reihenfolge
```

### `BracketRoundDto`
| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `stage` | `String` | LAST_32 / LAST_16 / QUARTER_FINALS / SEMI_FINALS / THIRD_PLACE / FINAL |
| `label` | `String` | Anzeigename, z. B. „Sechzehntelfinale" |
| `matches` | `BracketMatchDto[]` | 16 / 8 / 4 / 2 / 1 / 1 |

### `BracketMatchDto`
| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `fifaMatchNo` | `int` | 73–104 |
| `matchId` | `Long` (nullable) | reale football-data-Fixture-ID, sofern Spiel vorhanden |
| `home` | `BracketParticipantDto` | Heim-Beteiligter |
| `away` | `BracketParticipantDto` | Auswärts-Beteiligter |
| `homeScore` | `Integer` (nullable) | nur falls vorhanden |
| `awayScore` | `Integer` (nullable) | nur falls vorhanden |
| `status` | `String` (nullable) | SCHEDULED/IN_PLAY/FINISHED/…; `null` falls noch kein reales Spiel zugeordnet |
| `winner` | `String` (nullable) | HOME_TEAM/AWAY_TEAM, falls entschieden |
| `sourceMatchNos` | `int[]` | 0 (LAST_32) oder 2 |
| `nextMatchNo` | `Integer` (nullable) | `null` für FINAL/THIRD_PLACE |

### `BracketParticipantDto`
| Feld | Typ | Beschreibung |
|------|-----|--------------|
| `teamName` | `String` (nullable) | gesetzt, wenn Team feststeht |
| `placeholder` | `String` (nullable) | gesetzt, solange offen (z. B. „Sieger Gruppe A", „Sieger Match 89") |

**Invariante (SC-005)**: Genau eines von `teamName`/`placeholder` ist gesetzt — nie beide null, nie beide gesetzt.

## D. Ableitungslogik (Builder, zur Laufzeit, nicht persistiert)

`BracketService.build()`:
1. KO-Spiele laden (`MatchRepository.findKnockout()`), per `BracketSlotMapper` Slot-Index + FIFA-Nr zuweisen → `Map<fifaMatchNo, Match>`.
2. Über die 32 `TopologyEntry` iterieren; je Eintrag das reale `Match` (falls vorhanden) einsetzen.
3. Beteiligte bestimmen:
   - LAST_32: reale Teams, sonst `Last32Placeholder`-Labels.
   - Höhere Runden: Sieger/Verlierer der Quell-Matches via `winnerOf()` (R5); ist eine Quelle unentschieden/offen → generischer Platzhalter „Sieger/Verlierer Match X".
4. `winnerOf(match)` (R5): FINISHED + (`winner`-Spalte → Seite) | (Tordifferenz → Seite) | sonst offen.
5. THIRD_PLACE: Verlierer von Match 101/102.
