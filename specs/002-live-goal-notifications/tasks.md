---

description: "Task list for F8 — Live-Tor-Benachrichtigungen"
---

# Tasks: Live-Tor-Benachrichtigungen (F8)

**Input**: Design documents from `/specs/002-live-goal-notifications/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/

**Tests**: Tests sind optional. Für F8 sind die `GoalDetector`-Tests **test-first**
vorgesehen (reine, kritische Diff-/Korrektur-/Recovery-Logik); übrige Tests
(Event-Source, Repository-Migration) sind empfohlen. Punktewertung/Reveal-Timing
(Verfassung Prinzip III) sind von F8 nicht betroffen.

**Organization**: Additive Erweiterung der bestehenden App. Nach User Story
gruppiert; US1 ist die MVP-Schleife (Tore posten), US2/US3 ergänzen Korrektheit
und Recovery.

**Base package**: `com.example.wmtippspiel` (Pfade: `src/main/java/com/example/wmtippspiel/...`)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Parallelisierbar (andere Datei, keine offene Abhängigkeit)
- **[Story]**: Zugehörige User Story (US1–US3)

---

## Phase 1: Setup

**Purpose**: Konfiguration für das Live-Polling

- [X] T001 [P] `src/main/resources/application.yml`: Property `app.jobs.live-goal-poll-interval-ms` (Default 60000) ergänzen; in `.env.example` als optionale `APP_JOBS_LIVE_GOAL_POLL_INTERVAL_MS` dokumentieren

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, gemeinsame Bausteine und additive Erweiterungen, die ALLE
Stories brauchen. Keine bestehenden Pfade verändern (nur additiv).

**⚠️ CRITICAL**: Vor den User Stories abzuschließen.

- [X] T002 Liquibase-Changeset `src/main/resources/db/changelog/changesets/008-add-matches-notified-score.sql`: `ALTER TABLE matches ADD COLUMN notified_home INT NOT NULL DEFAULT 0, ADD COLUMN notified_away INT NOT NULL DEFAULT 0`; Include in `db.changelog-master.yaml` (Prinzip II)
- [X] T003 [P] `live/GoalEvent.java`: Record (matchId, home, away, kind [GOAL/CORRECTION], scoringTeam [HOME/AWAY/null], newHome, newAway, minute) inkl. Enums
- [X] T004 [P] `persistence/MatchRepository.java` erweitern: `getNotifiedScore(long)` (kleines `Score`-Record home/away) und `updateNotifiedScore(long, int, int)` — additiv, bestehendes Mapping unverändert (SELECT * ignoriert Zusatzspalten)
- [X] T005 [P] `discord/publish/AnnounceChannel.java` erweitern: additive Methode `postPlain(MessageEmbed)` (postet ohne Role-Ping; für Korrektur-Notiz) — bestehende `post`/`postUserPing` unverändert

**Checkpoint**: Schema + gemeinsame Bausteine stehen.

---

## Phase 3: User Story 1 - Live-Tor-Benachrichtigung erhalten (Priority: P1) 🎯 MVP

**Goal**: Steigt der Stand eines Spiels im Live-Fenster, erscheint je Tor genau
ein „⚽ TOR!"-Post (mit Role-Ping) im Announce-Channel.

**Independent Test**: Spiel im Live-Fenster, Stand 0:0 → 1:0 in der Quelle →
nach ≤ ~1 Poll-Intervall genau ein Tor-Post; 0:0 → 2:1 → zwei Tor-Posts;
Spiel außerhalb des Fensters → keine Abfrage/kein Post.

### Tests for User Story 1 (test-first) ⚠️

- [X] T006 [P] [US1] `src/test/java/com/example/wmtippspiel/live/GoalDetectorTest.java`: Anstieg → je Tor ein `GOAL`-Event (inkl. Mehrfach-Tore, korrektes Team), Gleichstand → keine Events (FR-006/007/014)

### Implementation for User Story 1

- [X] T007 [US1] `live/GoalDetector.java`: reine Diff-Logik für Anstieg (GOAL-Events je zusätzlichem Tor, Team aus Differenz, neuer Stand je Event); setzt selbst nichts
- [X] T008 [P] [US1] `live/GoalEventSource.java`: Interface `List<GoalEvent> fetchEvents()` (austauschbare Quelle, FR-012)
- [X] T009 [US1] `live/ScoreDiffGoalEventSource.java`: `fetchEvents()` über bestehenden `FootballDataClient.fetchMatches()`; Live-Fenster filtern (`kickoff <= now <= kickoff+2.5h`, Status SCHEDULED/IN_PLAY); je Spiel `getNotifiedScore` → `GoalDetector.detect` → Events sammeln → `updateNotifiedScore`; Fehler → leere Liste (FR-001..004/013/015)
- [X] T010 [P] [US1] `discord/render/GoalEmbed.java`: „⚽ TOR! {home} {newHome}:{newAway} {away}" (+ Minute falls vorhanden)
- [X] T011 [US1] `live/GoalNotifier.java`: `post(GoalEvent)` — `GOAL` → `AnnounceChannel.post(goalEmbed)` (mit Role-Ping, FR-010/011)
- [X] T012 [US1] `scheduling/LiveGoalPollJob.java`: `@Scheduled(fixedDelayString = "${app.jobs.live-goal-poll-interval-ms:60000}")` ruft `goalEventSource.fetchEvents()` und für jedes Event `goalNotifier.post(event)`

**Checkpoint**: Live-Tore werden gepostet — F8-MVP funktionsfähig.

---

## Phase 4: User Story 2 - Keine falschen oder doppelten Tor-Posts (Priority: P2)

**Goal**: Idempotenz (kein zweiter Ping bei unverändertem Stand) und saubere
VAR-Behandlung (Abwärtskorrektur → Korrektur-Notiz statt Tor-Post).

**Independent Test**: 1:0 → 0:0 (VAR) → kein Tor-Post, aber Korrektur-Notiz und
gemeldeter Stand auf 0:0; danach 0:0 → 1:0 → genau ein Tor-Post; doppelt
geliefertes unverändertes Update → kein zweiter Post.

### Tests for User Story 2 (test-first) ⚠️

- [X] T013 [P] [US2] `GoalDetectorTest` erweitern: Abwärtskorrektur → genau ein `CORRECTION`-Event, **kein** `GOAL`; erneuter gleicher Stand → keine Events (FR-007/008)

### Implementation for User Story 2

- [X] T014 [US2] `live/GoalDetector.java` erweitern: Zweig „mind. ein Wert kleiner" → ein `CORRECTION`-Event (kein GOAL); Idempotenz bei Gleichstand sicherstellen
- [X] T015 [US2] `discord/render/GoalEmbed.java` + `live/GoalNotifier.java`: Korrektur-Embed („⛔ Tor aberkannt — jetzt {newHome}:{newAway}"); `CORRECTION` → `AnnounceChannel.postPlain(embed)` (ohne Role-Ping)

**Checkpoint**: US1 + US2 — korrekte, dopplungs-/fehlerfreie Posts.

---

## Phase 5: User Story 3 - Verlässlich über Bot-Neustarts (Priority: P3)

**Goal**: Nach Neustart keine doppelten Tore; in der Downtime gefallene, noch
nicht gemeldete Tore werden nachgereicht.

**Independent Test**: Gemeldeter Stand 2:1 persistiert, „Neustart", Stand
weiterhin 2:1 → kein Post; während Downtime 1:0 → 2:0, nach Neustart erster Poll
→ Tor zum 2:0 wird nachgereicht.

### Tests for User Story 3 (test-first) ⚠️

- [X] T016 [P] [US3] `GoalDetectorTest` erweitern: Diff gegen persistierten `notified_*` reicht verpasste Tore nach (Recovery/Nachmelden), bereits gemeldeter Stand → keine Events (FR-009/009a)

### Implementation for User Story 3

- [X] T017 [US3] `src/test/java/com/example/wmtippspiel/persistence/PersistenceIntegrationTest.java` erweitern: Migration 008 + `getNotifiedScore`/`updateNotifiedScore` Round-Trip gegen echtes PostgreSQL (Testcontainers); bestätigt Persistenz als Recovery-Grundlage

**Checkpoint**: Alle F8-Stories funktionsfähig.

---

## Phase 6: Polish & Cross-Cutting

- [X] T018 [P] `src/test/java/com/example/wmtippspiel/live/ScoreDiffGoalEventSourceTest.java`: Fensterfilter (in/out of window, Status), Fehler der Quelle → leere Liste, `updateNotifiedScore` wird mit aktuellem Stand aufgerufen (gemockte Deps)
- [X] T019 [P] `README.md`: kurzer Abschnitt „Live-Tor-Benachrichtigungen (F8)" + Hinweis auf optionales Poll-Intervall
- [ ] T020 Quickstart-Smoke-Test gemäß `quickstart.md` (Tor, Mehrfach, VAR, Neustart, außerhalb Fenster)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: keine Abhängigkeiten
- **Foundational (Phase 2)**: nach Setup — BLOCKIERT alle Stories
- **US1 (Phase 3)**: nach Foundational — MVP
- **US2 (Phase 4)**: baut auf dem in US1 erstellten `GoalDetector`/`GoalNotifier` auf (erweitert sie)
- **US3 (Phase 5)**: nutzt die in Foundational geschaffene `notified_*`-Persistenz; logisch nach US1
- **Polish (Phase 6)**: nach den gewünschten Stories

### Within Each User Story

- Detector-Tests (T006/T013/T016) vor der jeweiligen Detector-Implementierung
- `GoalDetector` vor `ScoreDiffGoalEventSource`; `GoalEmbed` vor `GoalNotifier`; Notifier+Source vor `LiveGoalPollJob`

### Parallel Opportunities

- Foundational: T003, T004, T005 parallel (verschiedene Dateien); T002 (Schema) unabhängig
- US1: T006 (Test) und T008/T010 (Interface/Embed) parallel zur Detector-/Source-Arbeit
- Polish: T018/T019 parallel

---

## Implementation Strategy

### MVP First (US1)

1. Phase 1 Setup → Phase 2 Foundational
2. US1 (Phase 3): Tore werden live gepostet → **STOP & VALIDATE** (Smoke gegen ein laufendes Spiel)

### Incremental

1. + US2 → Idempotenz & VAR-Korrektur
2. + US3 → Neustart-Recovery/Nachmelden
3. Polish → zusätzliche Tests + Doku

---

## Notes

- Rein additiv: bestehende Klassen nur an zwei Stellen erweitert (`AnnounceChannel.postPlain`, `MatchRepository`-Methoden) — keine Signatur-/Verhaltensänderung bestehender Pfade.
- `notified_*` bleibt außerhalb des `Match`-Records → keine Konstruktor-/Test-Brüche.
- Schema nur via Liquibase (Prinzip II); Live-Fenster-/Diff-Math in UTC (Prinzip IV).
- `GoalEventSource` ist die einzige auszutauschende Stelle für eine spätere Push-Quelle (FR-012).
