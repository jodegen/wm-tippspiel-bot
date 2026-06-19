---
description: "Task list for feature 010 — public bracket endpoint"
---

# Tasks: Öffentlicher Bracket-Endpoint (K.o.-Turnierbaum WM 2026)

**Input**: Design documents from `/specs/010-public-bracket-endpoint/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/bracket.openapi.yaml

**Tests**: INCLUDED — der Topologie-Konsistenztest ist vom Nutzer ausdrücklich gefordert; Slot-Mapping und Gewinner-Ableitung werden ebenfalls test-getrieben umgesetzt (reine Logik).

**Organization**: Tasks sind nach User Story gruppiert. Alle drei Stories bedienen denselben Endpoint, sind aber als inkrementelle Verhaltens-Schichten unabhängig testbar (US1 = Struktur, US2 = Platzhalter, US3 = Gewinner-Fortschritt).

## Format: `[ID] [P?] [Story] Description`

- **[P]**: kann parallel laufen (andere Datei, keine offene Abhängigkeit)
- **[Story]**: US1 / US2 / US3 (nur in Story-Phasen)

## Path Conventions

Single-Project Spring-Boot: Quellcode unter `src/main/java/com/example/wmtippspiel/`, Tests unter `src/test/java/com/example/wmtippspiel/`, Liquibase unter `src/main/resources/db/changelog/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Paketstruktur für die Bracket-Logik anlegen.

- [X] T001 Paketverzeichnisse anlegen: `src/main/java/com/example/wmtippspiel/publicapi/bracket/` und `src/test/java/com/example/wmtippspiel/publicapi/bracket/`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Domänen-/Persistenz-Plumbing, das ALLE Stories benötigen (Stage-Erweiterung + Sieger-Persistenz). Touchiert geteilte Dateien (Stage, Match, MatchRepository, FootballDataClient) → bewusst hier gebündelt, um Datei-Konflikte zwischen den Story-Phasen zu vermeiden.

**⚠️ CRITICAL**: Keine Story-Arbeit beginnt, bevor diese Phase steht.

- [X] T002 [P] `LAST_32` zur Enum hinzufügen (vor `LAST_16`) in `src/main/java/com/example/wmtippspiel/domain/model/Stage.java`
- [X] T003 [P] Neue Enum `MatchWinner { HOME_TEAM, AWAY_TEAM, DRAW }` in `src/main/java/com/example/wmtippspiel/domain/model/MatchWinner.java`
- [X] T004 [P] Additives Liquibase-Changeset `src/main/resources/db/changelog/changesets/013-add-matches-winner.sql` (`ALTER TABLE matches ADD COLUMN winner TEXT;`, nullable) und Einhängen in `src/main/resources/db/changelog/db.changelog-master.yaml`
- [X] T005 Feld `MatchWinner winner` (nullable) zum `Match`-Record ergänzen, bestehende Kompatibilitäts-Konstruktoren auf `winner = null` mappen, `withChannel` durchreichen in `src/main/java/com/example/wmtippspiel/domain/model/Match.java` (depends on T003)
- [X] T006 `MatchRepository`: `map()` liest Spalte `winner` (null-tolerant → `MatchWinner`); `upsert` und `updateLiveScore` persistieren `winner` additiv mit COALESCE-Guard (kein Überschreiben durch transientes `null`, analog `home_score`) in `src/main/java/com/example/wmtippspiel/persistence/MatchRepository.java` (depends on T005)
- [X] T007 `FootballDataClient`: `mapStage` um `case "LAST_32" -> Stage.LAST_32` ergänzen UND in `mapMatch` `score.winner` lesen und als `MatchWinner` ins `Match` setzen in `src/main/java/com/example/wmtippspiel/sync/FootballDataClient.java` (depends on T002, T005). **Hinweis (U2): bereits vor Changeset 013 synchronisierte K.o.-Spiele tragen `winner = NULL`, bis der reguläre Sync sie erneut abruft; bei eindeutiger Tordifferenz greift ohnehin die Score-Ableitung — für SC-004 (Elfmeter/Verlängerung) ist ein Re-Sync der betroffenen Spiele Voraussetzung.**

**Checkpoint**: Stage kennt `LAST_32`, jedes `Match` trägt einen optionalen Sieger aus football-data, Spalte migriert.

---

## Phase 3: User Story 1 - Kompletten Turnierbaum abrufen (Priority: P1) 🎯 MVP

**Goal**: `GET /api/public/bracket` liefert die vollständige, korrekt verdrahtete 6-Runden-Struktur (16/8/4/2/1/1) mit FIFA-Match-Nrn (73–104), Quell-/Ziel-Kanten, realen Teams/Scores/Status (wo vorhanden).

**Independent Test**: Endpoint aufrufen → 32 Spiele in korrekter Rundenstruktur, jede FIFA-Nr 73–104 vorhanden, Kanten entsprechen `WC2026_BRACKET_TOPOLOGY.md`.

### Tests for User Story 1 (write FIRST, must FAIL) ⚠️

- [X] T008 [P] [US1] `BracketTopologyConsistencyTest` (PFLICHT): genau 32 Knoten, FIFA-Nrn lückenlos 73–104; jedes Nicht-LAST_32-Spiel hat genau 2 eindeutige Quellen; LAST_16/QF/SF/THIRD_PLACE/FINAL haben 8/4/2/1/1 Spiele; FINAL(104).sources={101,102} (WINNER); THIRD_PLACE(103).sources={101,102} (LOSER); Quell↔Ziel konsistent invertierbar; keine Zyklen; **zusätzlich (F1): `internalStageToApiStage` ist die EINZIGE Singular→Plural-Abbildung und liefert für die 6 Runden genau das football-data-Vokabular `{LAST_32, LAST_16, QUARTER_FINALS, SEMI_FINALS, THIRD_PLACE, FINAL}`** — in `src/test/java/com/example/wmtippspiel/publicapi/bracket/BracketTopologyConsistencyTest.java`
- [X] T009 [P] [US1] `BracketSlotMapperTest`: kickoff-aufsteigend + Tie-Breaker `id` → korrekte FIFA-Nr je Stage (72/88/96/100+slot, 103, 104); unvollständige Stage erzeugt dennoch vollständige Slotzuordnung — in `src/test/java/com/example/wmtippspiel/publicapi/bracket/BracketSlotMapperTest.java`

### Implementation for User Story 1

- [X] T010 [US1] `BracketTopology` — unveränderliche Konstante mit 32 `TopologyEntry(stage, slotIndex, fifaMatchNo, sourceMatchNos[], nextMatchNo, sourceRole)` exakt nach `WC2026_BRACKET_TOPOLOGY.md`; Helper `internalStageToApiStage` (Singular→Plural) in `src/main/java/com/example/wmtippspiel/publicapi/bracket/BracketTopology.java` (macht T008 grün)
- [X] T011 [US1] `BracketSlotMapper` — lädt `MatchRepository.findKnockout()`, gruppiert nach Stage, sortiert nach `kickoff` (Tie-Breaker `id`), vergibt Slot-Index → FIFA-Match-Nr, liefert `Map<Integer, Match>` (FIFA-Nr → Match) in `src/main/java/com/example/wmtippspiel/publicapi/bracket/BracketSlotMapper.java` (macht T009 grün)
- [X] T012 [P] [US1] `BracketParticipantDto(teamName, placeholder)` in `src/main/java/com/example/wmtippspiel/publicapi/dto/BracketParticipantDto.java`
- [X] T013 [P] [US1] `BracketMatchDto(fifaMatchNo, matchId, home, away, homeScore, awayScore, status, winner, sourceMatchNos, nextMatchNo)` mit Swagger-`@Schema` in `src/main/java/com/example/wmtippspiel/publicapi/dto/BracketMatchDto.java`
- [X] T014 [P] [US1] `BracketRoundDto(stage, label, matches)` in `src/main/java/com/example/wmtippspiel/publicapi/dto/BracketRoundDto.java`
- [X] T015 [P] [US1] `BracketDto(rounds)` in `src/main/java/com/example/wmtippspiel/publicapi/dto/BracketDto.java`
- [X] T016 [US1] `BracketService.build()` — Struktur-Assembly: über die 32 `TopologyEntry` iterieren, reale `Match` aus dem Slot-Mapper einsetzen (matchId/Scores/Status), `sourceMatchNos`/`nextMatchNo` aus der Topologie; für LAST_32 reale Teams (Platzhalter-Auflösung folgt in US2), 6 Runden in fester Reihenfolge in `src/main/java/com/example/wmtippspiel/publicapi/bracket/BracketService.java` (depends on T010, T011, T012, T013, T014, T015)
- [X] T017 [US1] `GET /api/public/bracket` (Swagger-annotiert) in `src/main/java/com/example/wmtippspiel/publicapi/PublicApiController.java`, delegiert an `BracketService` (CORS bereits über `PublicApiConfig` für `/api/public/**` abgedeckt — keine Änderung)

**Checkpoint**: Endpoint liefert die vollständige verdrahtete Struktur — US1 unabhängig testbar/demonstrierbar (MVP).

---

## Phase 4: User Story 2 - Baum vor Ende der Gruppenphase darstellen (Priority: P2)

**Goal**: Jede noch offene Beteiligten-Position trägt ein beschreibendes Platzhalter-Label; nie leer/null (SC-005).

**Independent Test**: Endpoint in einem Zustand ohne feststehende K.o.-Teams aufrufen → jede `home`/`away` hat `placeholder` gesetzt, `teamName` null; LAST_32 nutzt die Gruppen-Notation, höhere Runden „Sieger Match X".

### Tests for User Story 2 (write FIRST, must FAIL) ⚠️

- [X] T018 [P] [US2] `BracketServiceTest`-Fälle: ohne K.o.-Spiele → vollständige Struktur, jede Position `placeholder != null` & `teamName == null`; LAST_32-Labels exakt gemäß Topologie-Tabelle; Invariante „genau eines von teamName/placeholder" — in `src/test/java/com/example/wmtippspiel/publicapi/bracket/BracketServiceTest.java`

### Implementation for User Story 2

- [X] T019 [US2] `Last32Placeholder`-Tabelle (Heim-/Auswärts-Label je FIFA-Nr 73–88) aus `WC2026_BRACKET_TOPOLOGY.md` in `src/main/java/com/example/wmtippspiel/publicapi/bracket/BracketTopology.java` ergänzen
- [X] T020 [US2] `BracketService`: Beteiligten-Auflösung Team-vs-Platzhalter — LAST_32 aus `Last32Placeholder`, höhere Runden generisch „Sieger Match {sourceNo}" (THIRD_PLACE: „Verlierer Match {sourceNo}"); Invariante „genau eines gesetzt" durchsetzen in `src/main/java/com/example/wmtippspiel/publicapi/bracket/BracketService.java` (depends on T016, T019)

**Checkpoint**: Baum ist vor und während der K.o.-Phase vollständig zeichenbar (US1 + US2).

---

## Phase 5: User Story 3 - Gewinner rücken automatisch nach (Priority: P2)

**Goal**: Sieger abgeschlossener K.o.-Spiele rücken zur Laufzeit ins Folge-Spiel; Elfmeter/Verlängerung korrekt via `winner`-Spalte; THIRD_PLACE erhält die Halbfinal-Verlierer (FR-010/011/012/013, SC-003/004).

**Independent Test**: Abgeschlossenes Spiel mit eindeutigem Ergebnis → Sieger erscheint im `nextMatchNo`-Spiel; 1:1 mit `winner=HOME_TEAM` → Heim rückt nach; Remis ohne winner → Platzhalter bleibt.

### Tests for User Story 3 (write FIRST, must FAIL) ⚠️

- [X] T021 [P] [US3] `BracketServiceTest`-Fälle zur Gewinner-Ableitung: Tordifferenz entscheidet; 1:1 + `winner=HOME_TEAM` (Elfmeter) → Heim; Remis ohne `winner` → Folge-Slot bleibt Platzhalter; nicht-FINISHED → kein Nachrücken; THIRD_PLACE = beide Halbfinal-Verlierer — in `src/test/java/com/example/wmtippspiel/publicapi/bracket/BracketServiceTest.java`

### Implementation for User Story 3

- [X] T022 [US3] Reine Funktion `winnerOf(Match)` in `BracketService` mit Reihenfolge: nicht FINISHED → leer; `winner`-Spalte HOME/AWAY → Seite; sonst Tordifferenz → Seite; sonst leer (Remis/DRAW) in `src/main/java/com/example/wmtippspiel/publicapi/bracket/BracketService.java` (depends on T020)
- [X] T023 [US3] `BracketService`: Sieger ins `nextMatchNo`-Spiel als `teamName` einsetzen und `winner` im `BracketMatchDto` füllen; THIRD_PLACE-Beteiligte = Verlierer von Match 101/102 (sobald entschieden) in `src/main/java/com/example/wmtippspiel/publicapi/bracket/BracketService.java` (depends on T022)

**Checkpoint**: Vollständiger, lebendiger Baum inkl. korrektem Gewinner-Fortschritt — alle drei Stories funktionsfähig.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [X] T024 [P] Swagger-/README-Notiz für `GET /api/public/bracket` ergänzen (analog F008) in `README.md`
- [ ] T025 [P] Quickstart-Validierung ausführen: `curl /api/public/bracket | jq` (6 Runden, 16/8/4/2/1/1) und SQL-Verifikation der Slot↔kickoff-Zuordnung gemäß `quickstart.md`
- [X] T026 Leak-Check + Gesamttestlauf: bestätigen, dass das DTO keine sensiblen Felder enthält, und `./mvnw test` grün

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: keine Abhängigkeiten.
- **Foundational (Phase 2)**: nach Setup — BLOCKIERT alle Stories.
- **US1 (Phase 3)**: nach Foundational. MVP.
- **US2 (Phase 4)**: nach US1 (erweitert `BracketService.build()` + `BracketTopology`).
- **US3 (Phase 5)**: nach US2 (erweitert dieselbe `BracketService`).
- **Polish (Phase 6)**: nach den gewünschten Stories.

> Hinweis: US2 und US3 erweitern bewusst dieselben Dateien (`BracketService`, `BracketTopology`, `BracketServiceTest`) und laufen daher **sequenziell**, nicht parallel zueinander.

### Within Each User Story

- Tests zuerst schreiben und scheitern sehen, dann implementieren.
- Topologie + Slot-Mapper + DTOs vor `BracketService`; Service vor Endpoint.

### Parallel Opportunities

- **Phase 2**: T002, T003, T004 parallel (verschiedene Dateien); T005→T006, T007 danach.
- **US1 Tests**: T008, T009 parallel.
- **US1 DTOs**: T012, T013, T014, T015 parallel (vier getrennte Records).

---

## Parallel Example: User Story 1

```bash
# Tests zuerst (parallel):
Task: "BracketTopologyConsistencyTest in src/test/.../BracketTopologyConsistencyTest.java"
Task: "BracketSlotMapperTest in src/test/.../BracketSlotMapperTest.java"

# Danach die vier DTOs parallel:
Task: "BracketParticipantDto in src/main/.../dto/BracketParticipantDto.java"
Task: "BracketMatchDto in src/main/.../dto/BracketMatchDto.java"
Task: "BracketRoundDto in src/main/.../dto/BracketRoundDto.java"
Task: "BracketDto in src/main/.../dto/BracketDto.java"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 Setup → 2. Phase 2 Foundational → 3. Phase 3 US1 → **STOP & VALIDATE**: vollständige Baumstruktur über den Endpoint. Demo-fähig.

### Incremental Delivery

1. Foundational fertig → Plumbing steht.
2. + US1 → Struktur sichtbar (MVP).
3. + US2 → Platzhalter, Baum vor Gruppenphase zeichenbar.
4. + US3 → Gewinner-Fortschritt live.

---

## Notes

- [P] = andere Datei, keine offene Abhängigkeit.
- Keine neuen Abhängigkeiten; CORS/Cache aus F008 wiederverwendet.
- Schemaänderung ausschließlich über Changeset 013 (Verfassung Prinzip II).
- Punktewertung/Reveal-Timing werden NICHT berührt.
- Nach jeder logischen Gruppe committen.
- **F1**: Singular↔Plural-Stage-Abbildung nur über `internalStageToApiStage` (per T008 abgesichert) — interner Enum-Name wird nie direkt serialisiert.
- **U2**: `matches.winner` ist für historische K.o.-Spiele erst nach einem Re-Sync gefüllt; ohne gesetzten `winner` bleibt ein Remis-Score im Baum offen (Platzhalter).
