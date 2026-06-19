# Quickstart — Öffentlicher Bracket-Endpoint (Feature 010)

## Konfiguration

Keine neue Konfiguration nötig. CORS/Cache werden von Feature 008 (`PublicApiConfig`,
`app.public-api.cors-allowed-origins`, `app.public-api.cache-ttl-seconds`) mitgenutzt;
`/api/public/bracket` fällt automatisch unter das bestehende `/api/public/**`-CORS-Mapping.

## Datenbank-Migration

Beim Start wendet Liquibase das neue, additive Changeset an:

```
013-add-matches-winner.sql  →  ALTER TABLE matches ADD COLUMN winner TEXT;
```

Nullable, kein Backfill nötig. Bestehende Zeilen haben `winner = NULL` (= unbekannt);
ab dem nächsten Sync füllt football-data `score.winner` den Wert für beendete Spiele.

## Manuell aufrufen

```bash
curl -s http://localhost:8080/api/public/bracket | jq .
```

Erwartung (in jedem Turnierzustand):
- `rounds` enthält **genau 6** Einträge in der Reihenfolge
  LAST_32, LAST_16, QUARTER_FINALS, SEMI_FINALS, THIRD_PLACE, FINAL.
- Spielanzahl je Runde: 16 / 8 / 4 / 2 / 1 / 1.
- Jedes Spiel hat `fifaMatchNo` (73–104), `sourceMatchNos` (0 oder 2) und
  `nextMatchNo` (null nur bei 103/104).
- Vor der K.o.-Phase: jede `home`/`away`-Beteiligung trägt ein `placeholder`-Label
  (z. B. „Sieger Gruppe A"), nie leer/null.

Swagger-UI: der neue Endpoint erscheint unter dem Tag „Öffentliche API".

## Verifikation der Slot→Anpfiff-Zuordnung (vor Live-Gang, FR-018)

Sobald die echten K.o.-Spiele in `matches` stehen, einmalig prüfen, ob die
`kickoff`-Reihenfolge je Stage tatsächlich der FIFA-Match-Nr 73..104 entspricht:

```sql
SELECT stage, id, kickoff, home, away
FROM matches
WHERE stage <> 'GROUP_STAGE' AND status <> 'CANCELLED'
ORDER BY stage, kickoff, id;
```

Reihenfolge mit `WC2026_BRACKET_TOPOLOGY.md` abgleichen. Bei Abweichung **nur** die
Slot→Match-Zuordnung in `BracketSlotMapper` anpassen — die Kanten-Logik in
`BracketTopology` bleibt unverändert.

## Tests

```bash
./mvnw test -Dtest='Bracket*Test'
```

- `BracketTopologyConsistencyTest` (Pflicht): genau 32 Knoten, jede Nicht-LAST_32-Runde
  hat 2 eindeutige Quellen, FINAL = Sieger der beiden Halbfinals, THIRD_PLACE = Verlierer
  der beiden Halbfinals, lückenlose Kanten 73–104, keine Zyklen.
- `BracketSlotMapperTest`: kickoff+id-Sortierung → korrekte FIFA-Nr; Tie-Breaker bei
  gleichem kickoff; unvollständige Stage erzeugt vollständige Struktur mit Platzhaltern.
- `BracketServiceTest`: Gewinner aus Tordifferenz; Gewinner aus `winner`-Spalte
  (1:1 + Elfmeter → korrekter Sieger); Remis ohne winner-Info → Folge-Slot bleibt
  Platzhalter; Spiel um Platz 3 erhält Halbfinal-Verlierer.

## Manueller Smoke-Test des Gewinner-Fortschritts

1. Ein LAST_32-Spiel auf `FINISHED` mit eindeutigem Ergebnis setzen → der Sieger
   erscheint beim nächsten Abruf im verknüpften LAST_16-Spiel (`nextMatchNo`).
2. Ein Spiel mit 1:1 und `winner = HOME_TEAM` (Elfmeter) → Heim-Team rückt nach,
   obwohl der Score unentschieden ist.
