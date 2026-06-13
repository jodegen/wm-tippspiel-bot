---
description: "Task list for Konsolidiertes Live-Spielplan-Board (F7-Redesign)"
---

# Tasks: Konsolidiertes Live-Spielplan-Board (F7-Redesign)

**Input**: Design documents from `/specs/003-consolidated-board/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/board.md, quickstart.md

**Tests**: Prinzip III ist hier **nicht** mandatorisch (F7 berührt weder Punktewertung
noch Reveal-Timing). Die Truncation- und Cleanup-Prädikate sind reine Logik und werden
mit **empfohlenen** Unit-Tests abgesichert (als solche markiert).

**Organization**: Tasks sind nach User Story gruppiert. Hinweis: US1/US2/US3 ändern alle
dieselbe Datei `BoardService.java` → ihre `BoardService`-Tasks sind **sequenziell** (kein
echtes Parallelarbeiten an dieser Datei), auch wenn die Stories fachlich unabhängig testbar
sind.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Parallelisierbar (andere Datei, keine offene Abhängigkeit)
- **[Story]**: Zugehörige User Story (US1/US2/US3)

## Path Conventions

Single-Module-Backend (bestehend): Quellcode unter
`src/main/java/com/example/wmtippspiel/`, Tests unter
`src/test/java/com/example/wmtippspiel/`, Liquibase unter
`src/main/resources/db/changelog/`.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Ausgangszustand absichern, bevor bestehendes F7 modifiziert wird

- [X] T001 Baseline verifizieren: `mvn -DskipTests compile test-compile` läuft grün auf Branch `003-consolidated-board` (Referenzpunkt vor F7-Änderungen) — Hinweis: Projekt nutzt **Maven** (`mvn`), nicht Gradle

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Gemeinsamer Styling-Helper, den Info- und Board-Embed teilen (FR-010/011) — Voraussetzung für den konsistenten Look der Stories

**⚠️ CRITICAL**: Muss vor den User-Story-Phasen abgeschlossen sein

- [X] T002 Gemeinsamen Styling-Helper `EmbedStyle` erstellen in `src/main/java/com/example/wmtippspiel/discord/render/EmbedStyle.java` — Konstanten `ACCENT` (0xF1C40F) und `DIVIDER`; Methode `base(String title)` setzt Akzentfarbe + Author-Header ("FIFA WM 2026 · 11. Juni – 19. Juli") + `setTimestamp(clock.instant())` + Standard-Footer; Methode `bare(String title)` ohne Author (für ephemerale Ansichten); injizierte `Clock` nutzen (Prinzip IV, kein `Instant.now()`)
- [X] T003 `InfoEmbed` auf `EmbedStyle` umstellen in `src/main/java/com/example/wmtippspiel/discord/render/InfoEmbed.java` — gemeinsame Chrome (Akzentfarbe/Author/Footer-Timestamp/Divider) über den Helper beziehen; **sichtbares Ergebnis unverändert** (gleicher Footer-Text "Alle Zeiten in Europe/Berlin · Fair play! 🤝")

**Checkpoint**: `EmbedStyle` existiert und wird vom bestehenden Info-Embed genutzt; Look des Info-Embeds unverändert (Regressionscheck)

---

## Phase 3: User Story 1 - Konsolidiertes Spielplan-Board (Priority: P1) 🎯 MVP

**Goal**: Genau **eine** Board-Nachricht (`board:main`) mit den nächsten bis zu 12 anstehenden Spielen als Liste in der Embed-Beschreibung, im Info-Look, edit-in-place + Recovery.

**Independent Test**: Board-Channel konfigurieren, Bot starten, Sync abwarten → eine Board-Nachricht mit ≤12 künftigen Spielen (aufsteigend), die bei Folge-Refreshes editiert (nicht neu gepostet) wird; Look wie Info-Embed.

### Implementation for User Story 1

- [X] T004 [P] [US1] `BoardEmbed.buildBoard(List<Match> upcoming)` ergänzen in `src/main/java/com/example/wmtippspiel/discord/render/BoardEmbed.java` — EIN Embed über `EmbedStyle.base(...)`; Liste in der `description`: pro Spiel `**Heim vs Gast**` + Anpfiff-Relative-Timestamp (`TimeFormatting.relative`), optional `📺 Sender`, optional `💰 H/U/A` (nur wenn alle drei Quoten gesetzt); **defensive Truncation** bei `SAFE_DESC_LIMIT = 4000` mit Anhang „… und N weitere" (Beschreibung <4096, Gesamtembed <6000); **Leerzustand** = freundlicher Hinweis (FR-009); **keine** Live-/Endstände; `buildDay(...)` entfernen (`buildFiltered(...)` unverändert lassen)
- [X] T005 [US1] `BoardService` auf Single-Slot umbauen in `src/main/java/com/example/wmtippspiel/discord/board/BoardService.java` — Konstante `BOARD_KEY = "board:main"`, `UPCOMING_LIMIT = 12`; `refresh()` lädt `matches.findUpcoming(clock.instant(), UPCOMING_LIMIT)` und editiert/postet die EINE Nachricht via `editOrPost(board:main, buildBoard(...))`; 404-`UNKNOWN_MESSAGE`-Recovery + `bot_messages.upsert("board:main", …)`; **Tages-Slot-Schleife (`DAYS_AHEAD`/`board:day:*`) entfernen**; Channel-fehlt/Rechte-Fälle weiterhin nur warnen (FR-006/007/008, FR-020)
- [X] T006 [P] [US1] (empfohlener Test) `BoardEmbedTest` in `src/test/java/com/example/wmtippspiel/discord/render/BoardEmbedTest.java` — Listenformat (Begegnung/Countdown/Sender/Quote, optionale Felder weglassen), Leerzustand, Truncation hält `description ≤ 4096` und `embed.getLength() < 6000`, „… und N weitere"-Anhang bei Überlauf

**Checkpoint**: Eine konsolidierte Board-Nachricht steht und aktualisiert sich per Edit; MVP demonstrierbar

---

## Phase 4: User Story 2 - Migration & Cleanup verwaister Board-Nachrichten (Priority: P2)

**Goal**: Beim Start verbleibt nur `board:main`; Alt-Slots (`board:day:*`, `board:nav`) werden aus `bot_messages` migriert, verwaiste eigene Bot-Nachrichten im Channel gelöscht, fremde Nachrichten bleiben.

**Independent Test**: Channel mit mehreren Alt-/Bot-Nachrichten + einer Fremdnachricht vorbereiten, Bot starten → nur `board:main` verbleibt, Fremdnachricht erhalten.

### Implementation for User Story 2

- [X] T007 [P] [US2] Liquibase-Changeset `009-reduce-board-slots.sql` anlegen in `src/main/resources/db/changelog/changesets/009-reduce-board-slots.sql` — `--changeset wmtippspiel:009-reduce-board-slots`; `DELETE FROM bot_messages WHERE key LIKE 'board:day:%' OR key = 'board:nav';` (idempotent; `board:main`/`info:guide` unberührt)
- [X] T008 [US2] Changeset 009 registrieren in `src/main/resources/db/changelog/db.changelog-master.yaml` (include nach 008)
- [X] T009 [P] [US2] `BotMessageRepository.deleteByKey(String key)` ergänzen in `src/main/java/com/example/wmtippspiel/persistence/BotMessageRepository.java` (`findByKey`/`upsert` unverändert)
- [X] T010 [US2] Start-Cleanup in `BoardService` ergänzen (`src/main/java/com/example/wmtippspiel/discord/board/BoardService.java`) — `@EventListener(ApplicationReadyEvent.class) onStartup()`: `jda.awaitReady()`, Channel auflösen (sonst Warnung+return), getrackte `board:main`-Message-ID laden, letzte `CLEANUP_SCAN = 100` Nachrichten lesen und jede löschen mit `author == jda.getSelfUser()` **und** `id != board:main-Id`; danach `refresh()`; Lösch-Einzelfehler nur loggen, Start nie abbrechen (FR-016/018/019/021)
- [X] T011 [P] [US2] (empfohlener Test) `BoardCleanupTest` in `src/test/java/com/example/wmtippspiel/discord/board/BoardCleanupTest.java` — Prädikat „eigene Nachricht UND nicht `board:main` → löschen"; Fremd-Author bleibt; gültige `board:main` bleibt

**Checkpoint**: Nach Neustart in einem „verschmutzten" Channel bleibt genau `board:main`; Fremdnachrichten erhalten

---

## Phase 5: User Story 3 - Filter-Komponente unter dem konsolidierten Board (Priority: P3)

**Goal**: Die unveränderte Filter-Komponente hängt direkt an der einen `board:main`-Nachricht; ephemerale Filterantworten lassen das öffentliche Board unverändert.

**Independent Test**: Board steht, Filter darunter auswählen → nur die klickende Person sieht die gefilterte Ansicht; `board:main` bleibt unverändert.

### Implementation for User Story 3

- [X] T012 [US3] Nav-Komponente an `board:main` hängen in `src/main/java/com/example/wmtippspiel/discord/board/BoardService.java` — beim Posten **und** bei jedem Edit `.setComponents(navigation.actionRow())` mitgeben (Embed + Filter zusammen); separaten `board:nav`-Slot und `ensureNav(...)` entfernen (FR-012/015)
- [X] T013 [US3] Filterkette gegen das eine Board verifizieren (kein Code-Change erwartet) — `InteractionListener.onStringSelectInteraction` routet weiter auf `BoardFilterHandler` (`BoardNavigation.FILTER_ID`), `BoardFilterHandler.handle` antwortet ephemeral via `buildFiltered`; bei Bedarf nur Javadoc/FR-Verweise anpassen

**Checkpoint**: Filter funktioniert unverändert unter der einen Nachricht; öffentliches Board bleibt statisch

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Aufräumen und Verifikation über alle Stories

- [X] T014 [P] Tote Pfade/Referenzen bereinigen — entfernte Methoden (`BoardEmbed.buildDay`), `board:nav`-Konstante und veraltete FR-Verweise in Javadoc von `BoardService`/`BoardEmbed` aktualisieren; sicherstellen, dass keine Aufrufer auf entfernte Symbole verweisen
- [~] T015 `quickstart.md` durchspielen (`specs/003-consolidated-board/quickstart.md`) — **automatisierte Anteile grün** (Truncation/Cleanup-Prädikat via Tests); **manuelle Discord-Flows offen** (Migration/Cleanup im Live-Channel, Posting/Edit/Recovery, Filter ephemeral, Leerzustand) — erfordern laufenden Bot + Board-Channel
- [X] T016 [P] Volle Testsuite grün: `mvn test` (BUILD SUCCESS, 0 Failures/Errors; neue Tests BoardEmbedTest 4/4, BoardCleanupTest 4/4)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: keine Abhängigkeit
- **Foundational (Phase 2)**: nach Setup — **blockiert** alle Stories (EmbedStyle wird vom Board-Look benötigt)
- **User Stories (Phase 3–5)**: nach Foundational
- **Polish (Phase 6)**: nach den gewünschten Stories

### User Story Dependencies

- **US1 (P1)**: nach Foundational — keine Abhängigkeit von anderen Stories (MVP)
- **US2 (P2)**: nach Foundational — fachlich unabhängig; Cleanup nutzt die `board:main`-ID, die US1 etabliert (für den vollständigen End-to-End-Effekt US1 zuerst)
- **US3 (P3)**: nach Foundational — baut auf der einen Board-Nachricht aus US1 auf

### ⚠️ Datei-Kollision `BoardService.java`

T005 (US1), T010 (US2) und T012 (US3) ändern **dieselbe** Datei → **sequenziell** ausführen (US1 → US2 → US3). Sie sind daher **nicht** mit `[P]` markiert.

### Within Each User Story

- T004 vor T005 (BoardService nutzt `buildBoard`)
- T007 vor T008 (Changeset existiert vor Registrierung)
- T010 nach T005 (baut auf der neuen `BoardService`-Form auf) und nach T007/T008 (Migration entfernt die Alt-Slot-Tracking-Zeilen, damit der Cleanup sie als „nicht getrackt" löscht)
- T012 nach T005/T010 (gleiche Datei)

### Parallel Opportunities

- **Phase 2**: T002 vor T003 (T003 nutzt T002) — nicht parallel
- **US1**: T004 und T006 [P] parallel (Render-Code + Test, andere Dateien); T005 danach
- **US2**: T007 und T009 und T011 [P] parallel (Changeset / Repo / Test, andere Dateien); T008 nach T007; T010 sequenziell (BoardService)
- **Polish**: T014 und T016 [P] parallel

---

## Parallel Example: User Story 2

```bash
# Parallel startbar (unterschiedliche Dateien):
Task: "T007 Liquibase-Changeset 009-reduce-board-slots.sql anlegen"
Task: "T009 BotMessageRepository.deleteByKey(String) ergänzen"
Task: "T011 BoardCleanupTest schreiben"
# Danach sequenziell: T008 (master include), dann T010 (BoardService.onStartup)
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 (Setup) → Phase 2 (Foundational: EmbedStyle + InfoEmbed-Migration)
2. Phase 3 (US1): konsolidiertes Board rendern + Single-Slot-`refresh()`
3. **STOP & VALIDATE**: Eine Board-Nachricht mit 12 Spielen, edit-in-place, Info-Look
4. Demo-fähig als MVP

### Incremental Delivery

1. Foundation fertig → US1 (MVP, ein konsolidiertes Board) → demo
2. US2 ergänzen (Migration + Start-Cleanup) → in „verschmutztem" Channel testen → demo
3. US3 ergänzen (Filter unter der einen Nachricht) → ephemeral testen → demo
4. Polish (Aufräumen, Quickstart, volle Suite)

---

## Notes

- `[P]` = andere Datei, keine offene Abhängigkeit; `BoardService.java`-Tasks sind bewusst seriell.
- Tests (T006/T011) sind empfohlen, nicht III-pflichtig — können entfallen, ohne den Merge zu blockieren, sind aber für die reine Truncation-/Cleanup-Logik ratsam.
- Schemaänderungen ausschließlich über Changeset 009 (Prinzip II); kein `ddl-auto`.
- Nach jedem Task bzw. logischer Gruppe committen.
- Die Anpassung von `wm-tippspiel-bot-spec.md` (F7-Abschnitt, `bot_messages`, Jobs, Offene Punkte) ist bereits in `/speckit-specify` erfolgt — kein Task nötig.
