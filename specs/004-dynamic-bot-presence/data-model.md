# Phase 1 — Data Model: Dynamische Bot-Presence (F9)

> **Keine Schema-Änderung, kein Liquibase-Changeset.** F9 führt **keine**
> persistenten Entitäten ein. Es liest vorhandene `matches`-Daten und hält
> minimalen Zustand prozesslokal. Diese Datei dokumentiert das In-Memory-Modell
> und die genutzten/erweiterten Datenzugriffe.

## In-Memory-Entitäten

### `PresenceState` (Record, rein)
| Feld | Typ | Beschreibung |
|---|---|---|
| `type` | enum `LIVE \| UPCOMING \| IDLE` | Höchstpriorisierter zutreffender Zustand |
| `text` | `String` | Fertiger Anzeigetext inkl. Standard-Emoji (z. B. `⚽ LIVE: GER 2:1 FRA`) |

Erzeugt von `PresenceStateResolver`; vollständig aus Eingaben ableitbar, keine
Persistenz.

### `ObservedLiveMatch` (interne Buchführung im `PresenceManager`)
| Feld | Typ | Beschreibung |
|---|---|---|
| `matchId` | `long` | Spiel-ID |
| `home` / `away` | `int` | Zuletzt gesehener Stand |
| `kickoff` | `Instant` | Anpfiff (UTC) — Tie-Breaker bei mehreren Live-Spielen |
| `lastChange` | `Instant` | Zeitpunkt der letzten Stand-Änderung (FR-013) |

Map `matchId → ObservedLiveMatch`, nur im Speicher; bei LIVE-Austritt eines Spiels
entfernt. Nach Neustart leer → wird beim ersten `recompute()` neu aufgebaut.

### Throttle-Zustand (im `PresenceThrottle`)
| Feld | Typ | Beschreibung |
|---|---|---|
| `lastSentText` | `String` (nullable) | Zuletzt tatsächlich an Discord gesendeter Text (FR-008) |
| `lastSentAt` | `Instant` (nullable) | Zeitpunkt des letzten `setActivity` |
| `pendingText` | `String` (nullable) | Aktuellster, noch nicht gesendeter Text (Coalescing, „letzter gewinnt") |

## Genutzte / erweiterte Datenzugriffe (`MatchRepository`)

### Neu (lesend)
```sql
-- findInPlay(): aktuell laufende Spiele (für LIVE-Bestimmung)
SELECT * FROM matches WHERE status = 'IN_PLAY' ORDER BY kickoff ASC
```

### Neu (schreibend, **vorhandene Spalten**, kein DDL)
```sql
-- updateLiveScore(id, home, away, status): hält den Live-Stand frisch in matches
UPDATE matches SET home_score = :h, away_score = :a, status = :status WHERE id = :id
```
Aufgerufen im bestehenden Live-Poll-Pfad (`ScoreDiffGoalEventSource`) für Spiele im
Live-Fenster. Berührt `revealed`/`evaluated`/`channel`/`odds_*` **nicht**.

### Bestehend (unverändert genutzt)
- `findUpcoming(now, 1)` — nächstes anstehendes Spiel (für UPCOMING).

## Zustandslogik (Priorität & Auswahl)

```
recompute():
  inPlay   = matchRepo.findInPlay()
  observed = merge(inPlay)            # aktualisiert lastChange bei Stand-Diff,
                                      # entfernt nicht mehr laufende Spiele
  if inPlay nicht leer:
      pick   = inPlay max by (lastChange, dann frühester kickoff)   # FR-013
      state  = LIVE  "⚽ LIVE: {code(home)} {h}:{a} {code(away)}"
  elif (next = findUpcoming(now,1)) vorhanden:
      state  = UPCOMING "👀 Nächstes: {code(home)} vs {code(away)}"
  else:
      state  = IDLE  "🏆 WM 2026 /tipp"      # statisch, FR-007
  apply(state.text)                   # FR-008 + Throttle (FR-009)
```

## Konfiguration / Ressourcen

- `app.presence.min-update-interval-ms` (Default `5000`) — Throttle-Mindestabstand.
- `app.presence.idle-text` (Default `🏆 WM 2026 /tipp`) — IDLE-Fallback (optional konfigurierbar).
- `resources/presence/team-codes.properties` — `Teamname=KÜRZEL` (Key Entity
  „Team-Kürzel-Mapping"), read-only, manuell gepflegt analog TV-Sender-Mapping.

## Abgrenzung

- Kein neuer `bot_messages`-Slot, keine neue Tabelle, keine neue Spalte.
- `notified_home`/`notified_away` (F8) bleiben unberührt; F9 nutzt `home_score`/
  `away_score`/`status`.
