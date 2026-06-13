# Implementation Plan: Konsolidiertes Live-Spielplan-Board (F7-Redesign)

**Branch**: `003-consolidated-board` | **Date**: 2026-06-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/003-consolidated-board/spec.md`

## Summary

Überarbeitung der bestehenden F7-Implementierung: Der Board-Channel führt künftig
**genau eine** getrackte Nachricht (`board:main`) statt mehrerer Tages-Slots
(`board:day:*`) plus separater Navigation (`board:nav`). Diese eine Nachricht zeigt
die **nächsten 12 anstehenden Spiele** (Anstoßzeit in der Zukunft, via bestehender
`MatchRepository.findUpcoming`) als zusammenhängende Liste in der Embed-Beschreibung,
defensiv tronkiert unter Beachtung der Discord-Limits (4096 description / 6000 gesamt),
und trägt die **unveränderte** Filter-Komponente (Select-Menu) direkt an sich.

Das Embed-Styling (Akzentfarbe, Author-Header, Footer mit Timestamp, Emoji-Sprache)
wird in einen **gemeinsamen Helper** (`EmbedStyle`) extrahiert, den Info- und
Board-Embed teilen, damit der Look konsistent bleibt. Beim Start läuft eine
**Cleanup-Logik**, die im Board-Channel alle vom Bot stammenden Nachrichten
(Author = Self) entfernt, die **nicht** die getrackte `board:main` sind (begrenzt auf
die letzten 100 Nachrichten). Die Migration der Alt-Slots aus `bot_messages` erfolgt
über ein **Liquibase-Changeset** (009). Edit-statt-Post und 404-Recovery beziehen sich
nur noch auf die eine Nachricht. Der `boardRefresh`-Job bleibt im Trigger unverändert.

## Technical Context

**Language/Version**: Java 21 (bestehend)

**Primary Dependencies**: Spring Boot 3.x (`@Scheduled`, `@EventListener`, `JdbcClient`),
JDA (Gateway + REST: Channel-History/Edit/Delete), Liquibase, PostgreSQL — alle bereits
vorhanden, **keine neuen Abhängigkeiten**.

**Storage**: PostgreSQL. Kein Schema-Change an Spalten; **Daten-Migration** der Alt-Slots
in `bot_messages` via Liquibase-Changeset `009-reduce-board-slots.sql` (Prinzip II).
Datenzugriff über bestehende `BotMessageRepository`/`MatchRepository`.

**Testing**: JUnit 5 + AssertJ. F7 berührt **nicht** Punktewertung/Reveal-Timing →
Prinzip III nicht mandatorisch; Tests dennoch empfohlen für die reine
Truncation-Logik (`BoardEmbed`) und die Cleanup-Auswahl-Prädikate.

**Target Platform**: Bestehender langlaufender Bot-Prozess; Cleanup + erstes Board-Posting
laufen am `ApplicationReadyEvent` (analog `InfoChannelService`), Refresh im bestehenden
Scheduler.

**Project Type**: Single-Module-Backend (bestehend) — Modifikation eines vorhandenen Features.

**Performance/Constraints**: Cleanup liest **eine** History-Seite (≤100 Nachrichten) und
löscht nur eigene, nicht getrackte Nachrichten. Board zeigt max. 12 Spiele → sicher unter
Embed-Limits. Anzeige-Zeitzone Europe/Berlin, Logik/Persistenz UTC (Prinzip IV).

**Scale/Scope**: Ein dedizierter Board-Channel, eine Bot-Instanz pro Guild.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Prinzip | Status |
|---|---|
| I. Technologie-Stack (Java 21/Spring/Postgres) | ✅ keine neuen Stack-Elemente, keine neuen Dependencies |
| II. Liquibase-only | ✅ Alt-Slot-Migration via Changeset `009-reduce-board-slots.sql` (DELETE der `board:day:*`/`board:nav`-Zeilen); kein `ddl-auto`, kein manuelles DDL |
| III. Test-First Kernlogik (Punktewertung/Reveal-Timing) | ✅ F7 berührt diese Kernlogik nicht. Truncation-/Cleanup-Logik wird **freiwillig** mit Unit-Tests abgesichert, ist aber nicht vom III-Mandat erfasst |
| IV. Zeit UTC↔Berlin | ✅ `findUpcoming(now, 12)` vergleicht in UTC; Anzeige/Relative-Timestamps an der Render-Grenze (`TimeFormatting`) |
| V. JDA dauerhafte Gateway-Verbindung | ✅ Cleanup/Posting/Edit über JDA nach `awaitReady()`; Filter-Interaktion bleibt ereignisgetrieben über `InteractionListener` |

**Ergebnis (Initial & Post-Design)**: PASS — keine Verstöße, keine Complexity-Einträge.

## Project Structure

### Documentation (this feature)

```text
specs/003-consolidated-board/
├── plan.md              # Diese Datei
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── contracts/
│   └── board.md          # BoardService-/Cleanup-/EmbedStyle-Kontrakte
├── quickstart.md        # Phase 1
├── checklists/
│   └── requirements.md   # aus /speckit-specify
└── tasks.md             # /speckit-tasks (separat)
```

### Source Code (Modifikation der bestehenden Struktur)

```text
src/main/java/com/example/wmtippspiel/
├── discord/render/
│   ├── EmbedStyle.java           # NEU – gemeinsamer Styling-Helper (Akzentfarbe, Author-Header,
│   │                             #        Footer+Timestamp, Divider), genutzt von Info & Board
│   ├── InfoEmbed.java            # GEÄNDERT – nutzt EmbedStyle (Look unverändert, Chrome zentralisiert)
│   └── BoardEmbed.java           # GEÄNDERT – buildBoard(matches): EIN Embed, Liste in description,
│                                 #            defensive Truncation; buildDay entfällt, buildFiltered bleibt
├── discord/board/
│   └── BoardService.java         # GEÄNDERT – Single-Slot board:main; refresh() rendert 1 Embed + Nav-Row;
│                                 #            Edit/Recovery auf die eine Nachricht; Start-Cleanup
├── discord/components/
│   ├── BoardNavigation.java      # UNVERÄNDERT – Select-Menu bleibt fachlich gleich
│   └── BoardFilterHandler.java   # UNVERÄNDERT – ephemerale Filter-Antworten (buildFiltered)
├── persistence/
│   └── BotMessageRepository.java # ERWEITERT – deleteByKey / (optional) findAllKeys für Migration/Recovery
└── scheduling/
    └── BoardRefreshJob.java      # UNVERÄNDERT im Trigger – ruft weiterhin boardService.refresh()

src/main/resources/db/changelog/
├── db.changelog-master.yaml      # ERWEITERT – include 009
└── changesets/
    └── 009-reduce-board-slots.sql # NEU – DELETE FROM bot_messages WHERE key LIKE 'board:day:%' OR key='board:nav'

src/test/java/com/example/wmtippspiel/
├── discord/render/BoardEmbedTest.java   # NEU (empfohlen) – Truncation/Leerzustand/Listenformat
└── discord/board/BoardCleanupTest.java  # NEU (empfohlen) – Auswahl-Prädikat „eigene, nicht board:main"
```

**Structure Decision**: Modifikation, nicht Greenfield. Das bestehende Edit-in-place-/
Recovery-Muster aus `InfoChannelService`/`BoardService` bleibt erhalten und wird auf den
einzigen Slot `board:main` reduziert. Die Filterkette (`BoardNavigation` →
`InteractionListener` → `BoardFilterHandler` → `BoardEmbed.buildFiltered`) bleibt
**unangetastet**; sie hängt nur an einer anderen (der einen) Nachricht. `EmbedStyle` ist
ein additiver Helper; `InfoEmbed` wird minimal darauf umgestellt, ohne sein sichtbares
Ergebnis zu ändern (Regressionsrisiko niedrig).

## Complexity Tracking

> Keine Constitution-Verstöße — Tabelle entfällt.
