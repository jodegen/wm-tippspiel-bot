# Implementation Plan: Öffentlicher Bracket-Endpoint (K.o.-Turnierbaum WM 2026)

**Branch**: `010-public-bracket-endpoint` | **Date**: 2026-06-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/010-public-bracket-endpoint/spec.md`

## Summary

Ein neuer öffentlicher, rein lesender Endpoint `GET /api/public/bracket` liefert den kompletten K.o.-Turnierbaum der WM 2026 als Baumstruktur: sechs Runden (LAST_32 → LAST_16 → QUARTER_FINALS → SEMI_FINALS → FINAL, plus THIRD_PLACE) mit je Spiel den zwei Beteiligten (Team oder Platzhalter-Label), Ergebnis, Status und den Kanten zum nächsten Spiel. Die feste FIFA-Topologie (Match 73–104 inkl. aller Quell→Ziel-Kanten und der LAST_32-Platzhalter) wird **statisch im Code** als Konstante abgebildet (nicht in der DB). Ein Slot-Mapper sortiert die vorhandenen K.o.-Spiele je Stage nach `kickoff` (Tie-Breaker `id`) und leitet daraus Slot-Index und FIFA-Match-Nr ab; ein Bracket-Builder kombiniert die statische Topologie mit den aktuellen `matches`-Daten, berechnet den Gewinner-Fortschritt zur Laufzeit (ohne Persistenz) und füllt offene Positionen mit Platzhaltern.

Technischer Ansatz, getragen von zwei beim Code-Review entdeckten Voraussetzungen:

1. **`Stage.LAST_32` ergänzen** — die `Stage`-Enum kennt aktuell kein `LAST_32`; `FootballDataClient.mapStage()` lässt `"LAST_32"` still auf `GROUP_STAGE` durchfallen. Für die 48-Team-WM 2026 ist die Runde der letzten 32 Pflicht. Rein additive Enum-Erweiterung + Mapping; `stage` ist als `TEXT` gespeichert → **keine DB-Schemaänderung**.
2. **Additive `winner`-Spalte** (Klärung 2026-06-19) — `matches` führt bislang nur `home_score`/`away_score`. Für korrekten Gewinner-Fortschritt bei Elfmeter-/Verlängerungsentscheidungen wird eine nullable Spalte `winner` per neuem Liquibase-Changeset ergänzt und im Sync aus football-data `score.winner` befüllt.

Wiederverwendet werden die bestehenden Public-API-Konventionen aus Feature 008 (Controller `PublicApiController`, CORS-/Cache-Konfiguration `PublicApiConfig` für `/api/public/**`, reine DTO-Mapper, Swagger-Annotationen).

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**: Spring Boot 3.x (Spring Web MVC, JdbcClient), springdoc-openapi (Swagger), Caffeine (vorhandenes TTL-Caching); keine neuen Abhängigkeiten.

**Storage**: PostgreSQL. Eine additive, nullable Spalte `matches.winner` (neues Liquibase-Changeset `013-add-matches-winner.sql`). Bracket-Topologie wird NICHT persistiert.

**Testing**: JUnit 5 (vorhandenes Setup). Pflicht-Test: Topologie-Konsistenz. Zusätzlich: Slot-Mapping und Gewinner-Ableitung (reine Logik).

**Target Platform**: Linux-Server (Docker-Compose-Betrieb), als Teil der bestehenden Spring-Boot-Anwendung.

**Project Type**: Single-Project Web-Service (Backend-Erweiterung eines bestehenden Discord-Bots + Public-REST-API).

**Performance Goals**: Ein Endpoint-Aufruf rechnet über ≤32 K.o.-Spiele in O(n log n) (Sortierung je Stage) — vernachlässigbar. Optionales kurzes TTL-Caching analog Spielplan möglich, aber nicht zwingend.

**Constraints**: Read-only (nur `@GetMapping`), keine sensiblen Felder im DTO, CORS exakt wie übrige `/api/public/**`-Endpoints (bereits über `PublicApiConfig` abgedeckt). Zeit in UTC ausliefern.

**Scale/Scope**: Fixe Obergrenze von 32 K.o.-Spielen / 6 Runden. Ein Endpoint, eine statische Topologie-Konstante, ein Slot-Mapper, ein Builder, vier DTO-Records.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Prinzip | Status | Begründung |
|--------|--------|------------|
| I. Festgelegter Technologie-Stack (Java 21 / Spring Boot 3.x) | ✅ PASS | Reine Erweiterung im bestehenden Stack, keine neuen Frameworks/Abhängigkeiten. |
| II. Schema-Änderungen nur über Liquibase | ✅ PASS | Einzige Schemaänderung (`winner`-Spalte) erfolgt als neues, additives Changeset `013-add-matches-winner.sql`; `LAST_32` ist eine Enum-/Code-Änderung ohne DDL (`stage` ist TEXT). |
| III. Test-First für Kernlogik (NON-NEGOTIABLE) | ✅ PASS | Bracket-Logik ist nicht Punktewertung/Reveal-Timing → formal nicht test-pflichtig. Dennoch test-getrieben: Topologie-Konsistenz (Pflicht laut Nutzer), Slot-Mapping und Gewinner-Ableitung. Punktewertung/Reveal werden NICHT berührt. |
| IV. Zeit: UTC speichern, Europe/Berlin anzeigen | ✅ PASS | Sortierung und Auslieferung auf Basis des bestehenden UTC-`kickoff`; DTO liefert UTC-`Instant`, Formatierung bleibt Frontend-Sache. |
| V. Discord über JDA mit dauerhafter Gateway-Verbindung | ✅ N/A | Feature berührt die Discord-Anbindung nicht (reiner REST-Lesepfad). |

**Ergebnis: PASS** — keine Verstöße, Complexity-Tracking nicht erforderlich.

## Project Structure

### Documentation (this feature)

```text
specs/010-public-bracket-endpoint/
├── plan.md              # Diese Datei (/speckit-plan)
├── spec.md              # Feature-Spezifikation (+ Clarifications)
├── research.md          # Phase 0 (/speckit-plan)
├── data-model.md        # Phase 1 (/speckit-plan)
├── quickstart.md        # Phase 1 (/speckit-plan)
├── contracts/
│   └── bracket.openapi.yaml   # Phase 1 — Endpoint-Contract
├── checklists/
│   └── requirements.md  # Spec-Quality-Checklist (/speckit-specify)
└── tasks.md             # Phase 2 (/speckit-tasks — NICHT von /speckit-plan)
```

### Source Code (repository root)

```text
src/main/java/com/example/wmtippspiel/
├── domain/model/
│   ├── Stage.java                       # ÄNDERUNG: LAST_32 ergänzen
│   ├── MatchWinner.java                 # NEU: Enum HOME_TEAM/AWAY_TEAM/DRAW (nullable)
│   └── Match.java                       # ÄNDERUNG: Feld `winner` (additiv, kompat. Konstruktoren)
├── sync/
│   └── FootballDataClient.java          # ÄNDERUNG: LAST_32 mappen + score.winner lesen
├── persistence/
│   └── MatchRepository.java             # ÄNDERUNG: map() liest winner; upsert/updateLiveScore persistieren winner (additiv, COALESCE-Guard)
└── publicapi/
    ├── PublicApiController.java         # ÄNDERUNG: GET /api/public/bracket
    ├── bracket/
    │   ├── BracketTopology.java         # NEU: statische Kanten + LAST_32-Platzhalter (Konstante)
    │   ├── BracketSlotMapper.java       # NEU: KO-Spiele je Stage sortieren → Slot-Index → FIFA-Match-Nr (reine Logik)
    │   └── BracketService.java          # NEU: Topologie + matches kombinieren, Gewinner ableiten, Platzhalter
    └── dto/
        ├── BracketDto.java              # NEU: { rounds: [...] }
        ├── BracketRoundDto.java         # NEU: { stage, label, matches: [...] }
        ├── BracketMatchDto.java         # NEU: { fifaMatchNo, matchId?, home, away, homeScore, awayScore, status, winner?, sourceMatchNos[], nextMatchNo? }
        └── BracketParticipantDto.java   # NEU: { teamName? , placeholder? } (genau eines gesetzt)

src/main/resources/db/changelog/
├── db.changelog-master.yaml            # ÄNDERUNG: Changeset 013 einhängen
└── changesets/
    └── 013-add-matches-winner.sql      # NEU: ALTER TABLE matches ADD COLUMN winner TEXT (nullable)

src/test/java/com/example/wmtippspiel/publicapi/bracket/
├── BracketTopologyConsistencyTest.java # NEU (PFLICHT): jedes Nicht-LAST_32-Spiel hat genau 2 eindeutige Quellen; FINAL = Sieger beider Halbfinals; vollständige Kanten 73–104
├── BracketSlotMapperTest.java          # NEU: kickoff+id-Sortierung → korrekte FIFA-Match-Nr; Tie-Breaker; unvollständige Stage
└── BracketServiceTest.java             # NEU: Gewinner-Ableitung (Tordifferenz / winner-Spalte / Remis→offen), Platzhalter-Nachrücken, Spiel um Platz 3
```

**Structure Decision**: Single-Project-Web-Service. Die Bracket-Logik lebt in einem neuen Unterpaket `publicapi.bracket` (Sichtbarkeit „nur lesende Public-API"), die statische Topologie als Code-Konstante in `BracketTopology`. DTOs reihen sich in das bestehende `publicapi.dto`-Paket ein. Domänen-/Persistenz-Änderungen (`Stage.LAST_32`, `Match.winner`, Sync, Repository) sind rein additiv und an den vorhandenen Stellen verortet.

## Complexity Tracking

> Keine Verfassungs-Verstöße — Tabelle entfällt.
