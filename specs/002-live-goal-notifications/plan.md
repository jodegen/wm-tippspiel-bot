# Implementation Plan: Live-Tor-Benachrichtigungen (F8)

**Branch**: `002-live-goal-notifications` | **Date**: 2026-06-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/002-live-goal-notifications/spec.md`

## Summary

F8 ergänzt die bestehende Anwendung (F1–F7) um Live-Tor-Benachrichtigungen, ohne
vorhandene Komponenten zu brechen. Ein neuer `@Scheduled`-Job `liveGoalPoll`
holt im Live-Fenster (`kickoff` … `kickoff + 2,5 h`, nur Status SCHEDULED/IN_PLAY)
über den **bestehenden** football-data.org-`WebClient` die aktuellen Spielstände
und übergibt sie einer austauschbaren Event-Quelle `GoalEventSource`. Deren
Default-Implementierung (`ScoreDiffGoalEventSource`) vergleicht den frischen Stand
mit dem persistierten „zuletzt gemeldeten" Stand (`notified_home`/`notified_away`)
und lässt einen reinen, idempotenten **`GoalDetector`** je zusätzlichem Tor ein
`GoalEvent` erzeugen (bzw. ein Korrektur-Ereignis bei VAR-Rücknahme). Ein
`GoalNotifier` postet die Ereignisse über die **bestehende** Announce-Channel-
Logik (Tor-Posts mit Role-Ping, Korrektur-Notiz ohne Ping). Neue Felder kommen
per Liquibase-Changeset.

## Technical Context

**Language/Version**: Java 21 (bestehend)

**Primary Dependencies**: Spring Boot 3.x (`@Scheduled`, `WebClient`, `JdbcClient`),
JDA, Liquibase, PostgreSQL — alle bereits vorhanden, **keine neuen Abhängigkeiten**.

**Storage**: PostgreSQL; neue Spalten `notified_home`/`notified_away` an `matches`
via Liquibase-Changeset (Prinzip II). Datenzugriff über bestehende
`MatchRepository` (`JdbcClient`).

**Testing**: JUnit 5 + Mockito/AssertJ für `GoalDetector` (Diff, Idempotenz, VAR,
Mehrfach-Tore, Recovery) und `ScoreDiffGoalEventSource` (Fensterfilter, Persistenz);
Testcontainers für die neuen Repository-Methoden.

**Target Platform**: Bestehender langlaufender Bot-Prozess; `liveGoalPoll` läuft im
selben Scheduler-Pool.

**Project Type**: Single-Module-Backend (bestehend) — additive Erweiterung.

**Performance/Constraints**: Ein API-Request je Poll (Default 60 s) → 1 Req/Min,
weit unter dem Free-Tier-Limit (10/Min). Außerhalb des Live-Fensters **kein**
Polling. Zeit-/Fenstermath in UTC (Prinzip IV).

**Scale/Scope**: wenige gleichzeitig laufende Spiele; nur F8.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Prinzip | Status |
|---|---|
| I. Technologie-Stack (Java 21/Spring/Postgres) | ✅ keine neuen Stack-Elemente |
| II. Liquibase-only | ✅ neue Felder via Changeset `008-add-matches-notified-score.sql`; kein `ddl-auto` |
| III. Test-First Kernlogik (Punktewertung/Reveal-Timing) | ✅ F8 berührt diese nicht. `GoalDetector` ist neue Kernlogik und wird **freiwillig test-first** entwickelt (reine, entkoppelte Funktion), ist aber nicht vom III-Mandat erfasst |
| IV. Zeit UTC↔Berlin | ✅ Live-Fenster/Diff rechnen in UTC (`Clock`); Anzeige unverändert über bestehende Render-Schicht |
| V. JDA dauerhafte Gateway-Verbindung | ✅ Posting über bestehende `AnnounceChannel`; `liveGoalPoll` ist `@Scheduled` (REST-Posting genügt, kein neuer Gateway-Bedarf) |

**Ergebnis (Initial & Post-Design)**: PASS — keine Verstöße, keine Complexity-Einträge.

## Project Structure

### Documentation (this feature)

```text
specs/002-live-goal-notifications/
├── plan.md              # Diese Datei
├── research.md          # Phase 0
├── data-model.md        # Phase 1
├── contracts/
│   └── goal-events.md    # GoalEventSource-/GoalDetector-/Posting-Kontrakte
├── quickstart.md        # Phase 1
└── tasks.md             # /speckit-tasks (separat)
```

### Source Code (additive zur bestehenden Struktur)

```text
src/main/java/com/example/wmtippspiel/
├── live/                         # NEU – F8-Kern
│   ├── GoalEvent.java            # Ereignis-Record (+ Kind GOAL/CORRECTION, Team)
│   ├── GoalDetector.java         # reine Diff-Logik (Tore/Korrektur, idempotent)
│   ├── GoalEventSource.java      # Interface (austauschbare Quelle, FR-012)
│   ├── ScoreDiffGoalEventSource.java  # Default: Polling via FootballDataClient + GoalDetector
│   └── GoalNotifier.java         # postet GoalEvents über AnnounceChannel
├── discord/render/
│   └── GoalEmbed.java            # NEU – "⚽ TOR!"-Embed + "⛔ Korrektur"-Embed
├── discord/publish/
│   └── AnnounceChannel.java      # ERWEITERT – postPlain(embed) ohne Role-Ping (Korrektur)
├── persistence/
│   └── MatchRepository.java      # ERWEITERT – getNotifiedScore / updateNotifiedScore
└── scheduling/
    └── LiveGoalPollJob.java      # NEU – @Scheduled, ruft GoalEventSource + GoalNotifier

src/main/resources/db/changelog/changesets/
└── 008-add-matches-notified-score.sql   # NEU – ALTER TABLE matches ADD notified_home/away

src/test/java/com/example/wmtippspiel/
├── live/GoalDetectorTest.java            # NEU (test-first)
├── live/ScoreDiffGoalEventSourceTest.java # NEU
└── persistence/PersistenceIntegrationTest.java # ERWEITERT (notified-score round-trip)
```

**Structure Decision**: Rein additiv. Bestehende Klassen werden nur an genau zwei
Stellen erweitert (`AnnounceChannel.postPlain`, `MatchRepository`-Methoden) —
keine Signatur-/Verhaltensänderung an vorhandenen Pfaden. `notified_*` bleibt
bewusst **außerhalb** des `Match`-Records (interne F8-Buchführung), sodass keine
bestehenden Konstruktoraufrufe/Tests brechen (`SELECT *` ignoriert die Zusatz-
spalten beim bestehenden Row-Mapping).

## Complexity Tracking

> Keine Constitution-Verstöße — Tabelle entfällt.
