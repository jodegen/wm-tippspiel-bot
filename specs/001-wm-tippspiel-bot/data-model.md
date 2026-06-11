# Phase 1 Data Model: WM 2026 Tippspiel Discord-Bot

Basierend auf dem Datenmodell der Quell-Spezifikation und den Klarstellungen
(Session 2026-06-11). Alle Zeitfelder sind UTC (`TIMESTAMPTZ` / `Instant`,
Prinzip IV). Schema entsteht ausschließlich über Liquibase-Changesets — ein
Changeset-File pro Tabelle (Prinzip II).

## Entity: `matches`

Eine WM-Begegnung; Identität stammt aus football-data.org.

| Feld | Typ (PostgreSQL) | Constraints | Beschreibung |
|---|---|---|---|
| `id` | `BIGINT` | PK | Match-ID aus der API |
| `home` | `TEXT` | NOT NULL | Heimteam (ggf. "TBD" vor Gruppenabschluss) |
| `away` | `TEXT` | NOT NULL | Gastteam (ggf. "TBD") |
| `kickoff` | `TIMESTAMPTZ` | NOT NULL | Anstoßzeit (UTC) |
| `stage` | `TEXT` | NOT NULL | `GROUP_STAGE`, `LAST_16`, `QUARTER_FINAL`, `SEMI_FINAL`, `THIRD_PLACE`, `FINAL` |
| `group_label` | `TEXT` | NULL | Gruppe A–L (nur Gruppenphase; für Board-Filter) |
| `channel` | `TEXT` | NULL | TV-Sender (manuell gepflegt) |
| `odds_home` | `NUMERIC(6,2)` | NULL | Quote Heimsieg |
| `odds_draw` | `NUMERIC(6,2)` | NULL | Quote Unentschieden |
| `odds_away` | `NUMERIC(6,2)` | NULL | Quote Auswärtssieg |
| `home_score` | `INT` | NULL | Endstand Heim (null bis Abpfiff) |
| `away_score` | `INT` | NULL | Endstand Gast (null bis Abpfiff) |
| `status` | `TEXT` | NOT NULL, DEFAULT `'SCHEDULED'` | `SCHEDULED` / `IN_PLAY` / `FINISHED` / `POSTPONED` / `CANCELLED` |
| `revealed` | `BOOLEAN` | NOT NULL, DEFAULT `false` | Tipps bereits offengelegt? |
| `evaluated` | `BOOLEAN` | NOT NULL, DEFAULT `false` | Punkte bereits vergeben? |

**Validierung / Regeln**:
- `teams_known` (abgeleitet): `home <> 'TBD' AND away <> 'TBD'` → Voraussetzung
  für Tippbarkeit (FR-009).
- Reveal-fähig: `kickoff <= now() AND revealed = false AND status NOT IN ('CANCELLED')`.
- Eval-fähig: `status = 'FINISHED' AND evaluated = false AND home_score IS NOT NULL AND away_score IS NOT NULL`.
- Tippbar: `kickoff > now() AND teams_known AND status NOT IN ('CANCELLED')`.
- `CANCELLED`: nie Reveal/Eval (FR-004b). `POSTPONED`/neuer `kickoff`: Tippfrist
  & Reveal folgen automatisch (FR-004a).

**State Transitions** (`status`):
`SCHEDULED → IN_PLAY → FINISHED`; jederzeit `→ POSTPONED` (zurück zu `SCHEDULED`
mit neuer `kickoff` möglich) oder `→ CANCELLED` (terminal).
`revealed`: `false → true` bei Anpfiff (terminal je Spiel, außer manueller
Reset entfällt). `evaluated`: `false → true` nach Auswertung; **Neubewertung**
(FR-017a) setzt bei geändertem Endstand `true → false → true`.

**Indizes**: `idx_matches_kickoff (kickoff)`, `idx_matches_reveal (revealed,
kickoff)`, `idx_matches_eval (status, evaluated)`.

**Upsert-Strategie**: `INSERT … ON CONFLICT (id) DO UPDATE` beim Sync; manuell
gepflegte Felder (`channel`) und gesetzte Flags (`revealed`, `evaluated`) werden
beim Upsert **nicht** überschrieben. Endstand-Änderung an bereits `evaluated`
Spiel triggert Neubewertung (R9).

## Entity: `tips`

Vorhersage eines Mitglieds für ein Spiel. Genau ein Tipp pro User & Spiel.

| Feld | Typ (PostgreSQL) | Constraints | Beschreibung |
|---|---|---|---|
| `user_id` | `TEXT` | NOT NULL, PK-Teil | Discord-User-ID |
| `match_id` | `BIGINT` | NOT NULL, PK-Teil, FK → `matches(id)` | Referenz auf Spiel |
| `username` | `TEXT` | NOT NULL | Anzeigename (denormalisiert, Stand Abgabe) |
| `home_score` | `INT` | NOT NULL, `>= 0` | Getipptes Ergebnis Heim |
| `away_score` | `INT` | NOT NULL, `>= 0` | Getipptes Ergebnis Gast |
| `created_at` | `TIMESTAMPTZ` | NOT NULL | Zeitpunkt der (letzten) Abgabe (UTC) |
| `points` | `INT` | NOT NULL, DEFAULT `0` | Nach Auswertung gesetzt |

**Primärschlüssel**: `(user_id, match_id)` — ein Tipp pro User pro Spiel,
Update bis Anpfiff erlaubt (FR-006).

**Validierung / Regeln**:
- Abgabe/Update nur, wenn zugehöriges Spiel tippbar ist (FR-007/009); sonst
  Ablehnung.
- `points` wird nur durch die Auswertung gesetzt (FR-014/015); Neubewertung
  überschreibt (FR-017a).
- Upsert: `INSERT … ON CONFLICT (user_id, match_id) DO UPDATE SET home_score,
  away_score, username, created_at` — setzt `points` **nicht** zurück (relevant
  nur vor Anpfiff, wo `points` ohnehin 0 ist).

**Index**: `idx_tips_match (match_id)` (Reveal/Eval lesen pro Spiel).

## Entity: `bot_messages`

Persistente, vom Bot editierte Board-Nachrichten (F7-Board, Recovery).

| Feld | Typ (PostgreSQL) | Constraints | Beschreibung |
|---|---|---|---|
| `key` | `TEXT` | PK | Logischer Slot, z. B. `board:today`, `board:day:2026-06-14`, `board:nav` |
| `channel_id` | `TEXT` | NOT NULL | Discord-Channel der Nachricht |
| `message_id` | `TEXT` | NOT NULL | Discord-Message-ID (zum Editieren) |
| `updated_at` | `TIMESTAMPTZ` | NOT NULL | Letzter Edit-Zeitpunkt (UTC) |

**Validierung / Regeln**:
- Existiert die `message_id` beim Edit nicht mehr (Discord 404), wird neu
  gepostet und die Zeile aktualisiert (FR-027).
- `key` ist stabil je Slot, sodass nach Neustart dieselbe Nachricht editiert
  wird (FR-022).

## Abgeleitete Sichten / Aggregationen (kein eigenes Schema)

- **Leaderboard** (FR-018–020): Aggregation über `tips`:
  `SUM(points)` als Gesamtpunkte, `COUNT(*)` als abgegebene Tipps,
  `COUNT(*) FILTER (WHERE points = 3)` als exakte Treffer; Sortierung
  `ORDER BY total_points DESC, exact_hits DESC`; Gleichstand in beidem ⇒
  geteilter Rang (gleiche Platzierung).

## Bezug zu Functional Requirements

| Entität / Regel | FRs |
|---|---|
| `matches` Felder & Status | FR-001, FR-002, FR-003, FR-004, FR-004a, FR-004b |
| Tippbarkeit / TBD | FR-005, FR-006, FR-007, FR-009, FR-010 |
| Reveal-Guards | FR-011, FR-012, FR-013, FR-031 |
| Eval & Punkte & Neubewertung | FR-014, FR-015, FR-016, FR-017, FR-017a |
| Leaderboard | FR-018, FR-019, FR-020 |
| `bot_messages` / Board | FR-021, FR-022, FR-023, FR-027 |
