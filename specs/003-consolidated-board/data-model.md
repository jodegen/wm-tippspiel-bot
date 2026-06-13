# Phase 1 — Data Model: Konsolidiertes Board (F7-Redesign)

Dieses Feature führt **keine neuen Tabellen oder Spalten** ein. Es reduziert die
genutzten Slot-Schlüssel der bestehenden Tabelle `bot_messages` und migriert die
Alt-Slots per Liquibase.

## Tabelle `bot_messages` (bestehend, unverändert im Schema)

| Feld | Typ | Beschreibung |
|---|---|---|
| `key` | TEXT (PK) | Logischer Slot. **Neu**: für das Board genau **ein** Wert `board:main`. |
| `channel_id` | TEXT NOT NULL | Discord-Channel der Nachricht |
| `message_id` | TEXT NOT NULL | Discord-Message-ID (Edit/Recovery) |
| `updated_at` | TIMESTAMPTZ NOT NULL | Letzter Edit-Zeitpunkt (UTC gespeichert) |

### Slot-Schlüssel — vorher/nachher

| Slot (alt) | Status nach Migration |
|---|---|
| `board:day:<datum>` (mehrere) | **entfernt** (Changeset 009 DELETE; Discord-Nachricht via Start-Cleanup gelöscht) |
| `board:nav` | **entfernt** (Changeset 009 DELETE; Komponenten wandern an `board:main`) |
| `board:main` | **einziger** Board-Slot (neu/bestehend) — Embed + Filter-Komponente |
| `info:guide` | unverändert (anderes Feature, nur Stil-Refactor durch `EmbedStyle`) |

### Migration (Liquibase-Changeset `009-reduce-board-slots.sql`)

```sql
--liquibase formatted sql

--changeset wmtippspiel:009-reduce-board-slots
DELETE FROM bot_messages WHERE key LIKE 'board:day:%' OR key = 'board:nav';
--rollback SELECT 1; -- entfernte Tracking-Zeilen werden zur Laufzeit neu aufgebaut; kein echtes Rollback nötig
```

- Schema bleibt identisch (Prinzip II: Datenänderung als Changeset).
- Idempotent: erneute Anwendung bleibt korrekt (nichts mehr zu löschen).
- `board:main` und `info:guide` sind durch das Prädikat nicht betroffen.

## Quelle der Anzeige-Daten: `matches` (bestehend, unverändert)

Für das konsolidierte Board relevant (read-only Zugriff via
`MatchRepository.findUpcoming(now, 12)`):

| Feld | Verwendung im Board |
|---|---|
| `home`, `away` | Begegnung (fett) |
| `kickoff` (UTC) | Relative-Timestamp-Countdown (`<t:UNIX:R>`) |
| `status` | Filterkriterium (nur `kickoff > now`, nicht IN_PLAY/FINISHED/CANCELLED) |
| `channel` (TV) | optional „📺 Sender" |
| `odds_home/draw/away` | optional „💰 H/U/A" (nur wenn alle drei vorhanden) |

Live-/Endstand-Felder (`home_score`/`away_score`) werden im Board **nicht** mehr
gerendert (nur künftige Spiele). Die Filteransicht (`buildFiltered`) nutzt sie
weiterhin unverändert.

## Laufzeit-Zustände (keine Persistenz)

- **Board-Recovery**: Schlägt der Edit der `board:main`-Nachricht mit
  `UNKNOWN_MESSAGE` (404) fehl, wird neu gepostet und `bot_messages` (`board:main`)
  via `upsert` aktualisiert.
- **Start-Cleanup**: arbeitet rein über Discord (History-Lesen + Löschen), kein
  zusätzlicher persistenter Zustand; Entscheidungsbasis ist die getrackte
  `board:main`-Message-ID aus `bot_messages`.
