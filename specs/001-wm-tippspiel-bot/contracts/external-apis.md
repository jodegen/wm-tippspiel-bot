# Contract: Externe APIs (konsumiert)

Vom Bot konsumierte Schnittstellen. Nur die tatsächlich genutzten Felder sind
gelistet. Zugriff über `WebClient` (R5). Anstoßzeiten werden als UTC interpretiert
und als `Instant` gespeichert (Prinzip IV).

## football-data.org — Spielplan & Ergebnisse

- **Basis-URL**: `https://api.football-data.org/v4`
- **Auth**: Header `X-Auth-Token: <API_KEY>`
- **Competition**: `WC` (FIFA World Cup)
- **Rate-Limit (Free-Tier)**: 10 Requests/Min → Sync-Intervall ~15 Min (R5).

### Endpoint: `GET /competitions/WC/matches`

**Verwendete Response-Felder** (pro `matches[]`-Eintrag):

| API-Feld | → DB-Feld | Mapping-Hinweis |
|---|---|---|
| `id` | `matches.id` | Primärschlüssel |
| `homeTeam.name` | `matches.home` | null/unbestimmt → `"TBD"` |
| `awayTeam.name` | `matches.away` | null/unbestimmt → `"TBD"` |
| `utcDate` | `matches.kickoff` | ISO-8601 UTC → `Instant` |
| `stage` | `matches.stage` | direkt (`GROUP_STAGE`, `LAST_16`, …) |
| `group` | `matches.group_label` | z. B. `"GROUP_A"` → `"A"` (nur Gruppenphase) |
| `status` | `matches.status` | `SCHEDULED`/`IN_PLAY`/`PAUSED`→`IN_PLAY`/`FINISHED`/`POSTPONED`/`CANCELLED` |
| `score.fullTime.home` | `matches.home_score` | null bis Abpfiff |
| `score.fullTime.away` | `matches.away_score` | null bis Abpfiff |

**Upsert-Regel**: `ON CONFLICT (id)` aktualisieren; `channel`, `revealed`,
`evaluated` nicht überschreiben. Endstand-Änderung an `evaluated`-Spiel ⇒
Neubewertung anstoßen (FR-017a, R9).

**Fehlerverhalten**: Timeout/Non-2xx/Rate-Limit → Job loggt und überspringt
diesen Lauf (FR-032); bestehende Daten bleiben unverändert.

## The Odds API — Quoten (optional)

- **Basis-URL**: `https://api.the-odds-api.com/v4`
- **Auth**: Query-Param `apiKey=<API_KEY>`
- **Sport**: `soccer_fifa_world_cup`; **Markt**: `h2h`; **Region**: z. B. `eu`.
- **Sync-Intervall**: ~6 h (Quota schonen, R5). Quoten optional (FR-003).

### Endpoint: `GET /sports/soccer_fifa_world_cup/odds`

Query: `?regions=eu&markets=h2h&apiKey=…`

**Verwendete Response-Felder** (pro Event):

| API-Feld | → DB-Feld | Mapping-Hinweis |
|---|---|---|
| `home_team` | (Match-Zuordnung) | via Team-Namens-Mapping (R6) auf `matches` matchen |
| `away_team` | (Match-Zuordnung) | via Team-Namens-Mapping (R6) |
| `commence_time` | (Plausibilisierung) | UTC, zur Absicherung des Matchings |
| `bookmakers[].markets[ key=h2h ].outcomes[]` | `odds_home/draw/away` | Outcome-Name = Team → home/away; `"Draw"` → draw |

**Matching**: Team-Namens-Mapping-Tabelle (`canonical` ↔ Odds-Name); nicht
zuordenbare Events werden verworfen (Spiel bleibt ohne Quoten, R6).

**Fehlerverhalten**: Fehler/leer → Quoten bleiben unverändert/leer; übrige
Funktionen unbeeinträchtigt (FR-003).

## TV-Sender (kein API)

Manuell gepflegtes Mapping (`tv-channels.yml`): `match_id` bzw. Begegnung →
Sender (ARD, ZDF, MagentaTV). RTL hält 2026 keine Rechte. Schreibt `matches.
channel`; wird vom Sync-Upsert nicht überschrieben. (FR-004)
