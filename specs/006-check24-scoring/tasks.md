---

description: "Task list for CHECK24-Punkteschema (vierstufige Staffelung)"
---

# Tasks: CHECK24-Punkteschema (vierstufige Staffelung)

**Input**: Design documents from `/specs/006-check24-scoring/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: VERPFLICHTEND. Verfassung Prinzip III (Punktewertung = Kernlogik,
test-first NON-NEGOTIABLE) und ausdrücklicher User-Wunsch nach Unit-Tests. Tests
werden zuerst geschrieben, müssen rot sein, dann grün.

**Organization**: Tasks gruppiert nach User Story (US1/US2/US3) aus spec.md.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelisierbar (andere Datei, keine offene Abhängigkeit)
- **[Story]**: US1/US2/US3; Setup/Foundational/Polish ohne Story-Label

## Path Conventions

Single Maven project, Basis-Paket `com.example.wmtippspiel`:
`src/main/java/com/example/wmtippspiel/...`, `src/test/java/com/example/wmtippspiel/...`,
`src/main/resources/...`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Ausgangszustand absichern; keine neuen Abhängigkeiten, keine Schema-Änderung.

- [X] T001 Baseline herstellen: `./mvnw test` ausführen und grünen Ausgangszustand bestätigen (Referenz für Red→Green der Folge-Tasks)

**Checkpoint**: Build/Tests grün vor Beginn der Änderungen.

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Zentrale Punkteberechnung `ScoringService.points(...)` — die **einzige**
Berechnungsstelle, die sowohl US1 (Auto-Auswertung) als auch US3 (Neuberechnung) nutzen.

**⚠️ CRITICAL**: US1 und US3 können erst beginnen, wenn diese Phase abgeschlossen ist.

- [X] T002 [P] Test-first: `ScoringServiceTest` auf 4/3/2/0 umstellen (`@CsvSource` mit der vollständigen Referenz-Testmatrix aus `contracts/scoring.md`, inkl. Remis-Sonderfälle 2:2/1:1→3, 0:0/0:0→4, 3:3/0:0→3 und gespiegelte Differenz 2:0/0:2→0) in `src/test/java/com/example/wmtippspiel/domain/scoring/ScoringServiceTest.java` — muss zunächst FEHLSCHLAGEN
- [X] T003 `ScoringService.points(int homeActual, int awayActual, int homeTip, int awayTip)` auf 4/3/2/0 implementieren (Reihenfolge exakt → vorzeichenbehaftete Tordifferenz → Tendenz → 0) in `src/main/java/com/example/wmtippspiel/domain/scoring/ScoringService.java` — macht T002 grün

**Checkpoint**: Punkteberechnung liefert 4/3/2/0 und ist vollständig testabgedeckt.

---

## Phase 3: User Story 1 - Tipps nach CHECK24-Schema bewertet (Priority: P1) 🎯 MVP

**Goal**: Die Auto-Auswertung (F5) vergibt nach Abpfiff Punkte nach dem neuen
4/3/2/0-Schema über die zentrale `points()`-Funktion.

**Independent Test**: Für ein FINISHED-Spiel mit bekanntem Endstand mehrere Tipps
unterschiedlicher Qualität auswerten und 4/3/2/0 gegen die Erwartung prüfen.

### Tests for User Story 1 ⚠️ (test-first)

- [X] T004 [P] [US1] `EvaluationServiceTest` erwartete Punkte auf 4/3/2/0 anpassen (Auswertungs-Workflow, Idempotenz, Korrektur) in `src/test/java/com/example/wmtippspiel/evaluation/EvaluationServiceTest.java` — muss vor T005 die neue Erwartung prüfen

### Implementation for User Story 1

- [X] T005 [US1] `EvaluationService.evaluateFinishedMatches()` verifizieren, dass es ausschließlich `ScoringService.points(...)` nutzt (keine lokale Punktelogik) und die gepostete Ergebnis-/Punkte-Übersicht (`EvaluationPublisher`/`ScoredTip`) die neuen Werte ausweist; nur falls nötig anpassen in `src/main/java/com/example/wmtippspiel/evaluation/EvaluationService.java`
- [X] T006 [US1] Sicherstellen, dass `EvaluationPublisher` die Punkte aus `ScoredTip` ohne hartkodierte 3/1/0-Annahme darstellt in `src/main/java/com/example/wmtippspiel/evaluation/EvaluationPublisher.java`

**Checkpoint**: F5 wertet neue Spiele nach 4/3/2/0 aus; US1 unabhängig testbar (MVP).

---

## Phase 4: User Story 2 - Leaderboard zeigt exakte Treffer unabhängig vom Punktwert (Priority: P2)

**Goal**: `/rangliste` ermittelt die Anzahl exakter Treffer per Score-Vergleich
(Tipp ↔ tatsächliches Ergebnis), nicht aus dem Punktwert; bleibt Tie-Breaker.

**Independent Test**: Für einen User mit gemischten Tipps stimmt die im Leaderboard
ausgewiesene Exakt-Zahl mit der manuell aus Tipp-vs-Ergebnis gezählten überein und
ist invariant gegenüber dem Punkteschema.

### Tests for User Story 2 ⚠️ (test-first)

- [X] T007 [P] [US2] Test für `TipRepository.leaderboard()`: `exact_hits` wird per Score-Vergleich berechnet, NICHT aus `points` (z. B. Tipp mit `points != Höchstwert` aber exaktem Score zählt als Treffer; `points = Höchstwert` ohne Score-Match zählt nicht). Falls DB-Test-Harness (Testcontainers/H2) vorhanden, als Repository-/Integrationstest in `src/test/java/com/example/wmtippspiel/persistence/`; andernfalls Verifikation per `quickstart.md` Schritt 4 dokumentieren — muss zunächst FEHLSCHLAGEN

### Implementation for User Story 2

- [X] T008 [US2] `TipRepository.leaderboard()`-SQL gemäß `contracts/leaderboard.md` umstellen: `JOIN matches m ON m.id = t.match_id` und `exact_hits = COUNT(*) FILTER (WHERE m.evaluated AND t.home_score = m.home_score AND t.away_score = m.away_score)`; `FILTER (WHERE points = 3)` entfernen; Sortierung `total_points DESC, exact_hits DESC` beibehalten in `src/main/java/com/example/wmtippspiel/persistence/TipRepository.java`

**Checkpoint**: Leaderboard-Exakt-Zahl entkoppelt vom Punktwert; US1 + US2 unabhängig funktionsfähig.

---

## Phase 5: User Story 3 - Einmalige rückwirkende Neuberechnung (Priority: P1)

**Goal**: Beim App-Start werden alle bereits ausgewerteten Tipps idempotent nach dem
neuen Schema neu berechnet; `tips.points` wird nur bei Abweichung überschrieben, alte
Werte werden geloggt.

**Independent Test**: Datenbestand mit Alt-Punkten (3/1/0) durch den Lauf führen;
danach entsprechen alle Werte `points()`, zweiter Lauf ändert nichts, nicht-evaluierte
Spiele bleiben unangetastet.

### Tests for User Story 3 ⚠️ (test-first)

- [X] T009 [P] [US3] `RecalculationServiceTest` (Mockito) neu anlegen gemäß `contracts/recalculation.md`: (a) Update bei Abweichung (Alt 3 → 4), genau ein `updatePoints`-Aufruf; (b) Idempotenz: bei bereits korrektem Wert kein `updatePoints`; (c) zweiter Lauf = 0 Writes; (d) `evaluated`-Guard: nicht-evaluierte Spiele nie angefasst; (e) Alt 1 mit richtiger Differenz → 3, Alt 1 ohne → 2 — in `src/test/java/com/example/wmtippspiel/evaluation/RecalculationServiceTest.java` — muss zunächst FEHLSCHLAGEN

### Implementation for User Story 3

- [X] T010 [P] [US3] `MatchRepository.findEvaluated()` ergänzen: `SELECT * FROM matches WHERE evaluated = TRUE` in `src/main/java/com/example/wmtippspiel/persistence/MatchRepository.java`
- [X] T011 [US3] `RecalculationService.recalculateAll()` + `RecalcSummary` implementieren (iteriert `findEvaluated()`, lädt `TipRepository.findByMatch`, berechnet `ScoringService.points(...)` neu, schreibt via `updatePoints` NUR bei Abweichung, loggt je Änderung `user_id`/`match_id`/alt→neu sowie Abschluss-Summary) in `src/main/java/com/example/wmtippspiel/evaluation/RecalculationService.java` (abhängig von T003, T010) — macht T009 grün
- [X] T012 [US3] `ScoreRecalculationRunner implements ApplicationRunner` mit `@ConditionalOnProperty(value="app.scoring.recalc-on-startup", matchIfMissing=true)`, ruft `recalculationService.recalculateAll()` in `src/main/java/com/example/wmtippspiel/recalc/ScoreRecalculationRunner.java` (abhängig von T011)
- [X] T013 [P] [US3] Property `app.scoring.recalc-on-startup: true` ergänzen in `src/main/resources/application.yml`

**Checkpoint**: Startup-Neuberechnung läuft idempotent, sichert Altwerte per Log; alle drei Stories funktionsfähig.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Dokumentation und Gesamtverifikation.

- [X] T014 [P] Master-Spec `wm-tippspiel-bot-spec.md` aktualisieren (FR-012): Abschnitt **F5** auf 4/3/2/0 und Abschnitt **F6** auf Exakt-Treffer per Score-Vergleich
- [X] T015 Volltest: `./mvnw test` — gesamte Suite grün
- [ ] T016 `quickstart.md` Schritt 5 (manuelle E2E) durchführen: Alt-Punkte-DB → Start → Recalc-Log prüfen → `/rangliste` plausibilisieren → erneuter Start meldet `geändert=0` (Idempotenz, SC-004)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: keine Abhängigkeiten.
- **Foundational (Phase 2)**: nach Setup; **BLOCKIERT US1 und US3** (zentrale `points()`).
- **US1 (Phase 3)**: nach Foundational.
- **US2 (Phase 4)**: nur SQL-Änderung — unabhängig von Phase 2; kann parallel zu Phase 2/3 laufen (benötigt `points()` nicht).
- **US3 (Phase 5)**: nach Foundational (braucht `points()` aus T003).
- **Polish (Phase 6)**: nach allen gewünschten Stories.

### User Story Dependencies

- **US1 (P1)**: hängt an Foundational; sonst unabhängig.
- **US2 (P2)**: vollständig unabhängig (eigene Datei `TipRepository`, reine Query-Änderung).
- **US3 (P1)**: hängt an Foundational (T003) und an T010 (findEvaluated).

### Within Each User Story

- Tests zuerst (rot), dann Implementierung (grün).
- Modelle/Repos vor Services, Services vor Runner/Endpoint.

### Parallel Opportunities

- T002 (Foundational-Test) parallel startbar.
- US2 (T007→T008) komplett parallel zu Phase 2/3, da andere Datei und keine Abhängigkeit von `points()`.
- Innerhalb US3: T009 (Test) und T010 (findEvaluated) parallel; T013 (Config) parallel; T011 danach, T012 danach.
- T014 (Doku) parallel zu Code-Tasks.

---

## Parallel Example: Foundational + US2 gleichzeitig

```bash
# Sobald Setup grün ist, parallel anstoßbar:
Task: "T002 ScoringServiceTest auf 4/3/2/0 (rot) — domain/scoring/ScoringServiceTest.java"
Task: "T007 Leaderboard-Test exact_hits per Score-Vergleich (rot) — persistence/"
# US2-Implementierung benötigt points() NICHT und kann direkt folgen:
Task: "T008 TipRepository.leaderboard()-SQL auf JOIN-Vergleich umstellen"
```

## Parallel Example: User Story 3

```bash
# Nach Foundational:
Task: "T009 RecalculationServiceTest (Mockito) anlegen — evaluation/RecalculationServiceTest.java"
Task: "T010 MatchRepository.findEvaluated() — persistence/MatchRepository.java"
Task: "T013 app.scoring.recalc-on-startup in application.yml"
# danach sequentiell:
Task: "T011 RecalculationService.recalculateAll()"
Task: "T012 ScoreRecalculationRunner (ApplicationRunner)"
```

---

## Implementation Strategy

### MVP First (Foundational + User Story 1)

1. Phase 1 Setup → grüne Baseline.
2. Phase 2 Foundational → `points()` 4/3/2/0 (test-first).
3. Phase 3 US1 → F5 wertet neue Spiele nach 4/3/2/0 aus.
4. **STOP & VALIDATE**: US1 unabhängig testen → deploybarer MVP.

### Incremental Delivery

1. Foundational + US1 → MVP (neues Schema für künftige Spiele).
2. + US3 → Altdaten konsistent auf neues Schema migriert (wichtig, da bereits Spiele ausgewertet sein können).
3. + US2 → korrekte, schemaunabhängige Exakt-Treffer im Leaderboard.
4. Polish → Doku + Gesamtverifikation.

> Hinweis Priorität: US3 ist P1 (Datenkonsistenz) und sollte zusammen mit US1
> ausgeliefert werden; US2 (P2) kann parallel oder unmittelbar danach erfolgen.

---

## Notes

- [P] = andere Datei, keine offene Abhängigkeit.
- Verfassung Prinzip III: Punktewertungs-Tests müssen vor Merge grün sein — kein Merge ohne sie.
- Keine Schema-Änderung, kein Liquibase-Changeset, keine neuen Abhängigkeiten.
- `ScoringService.points(...)` bleibt die einzige Punkteberechnungsstelle (FR-011).
- Nach jedem Task oder logischer Gruppe committen; an Checkpoints Story unabhängig validieren.
