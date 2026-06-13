---

description: "Task list — F9 Dynamische Bot-Presence"
---

# Tasks: Dynamische Bot-Presence (F9)

**Input**: Design documents from `/specs/004-dynamic-bot-presence/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/presence-manager.md, quickstart.md

**Tests**: Test-Tasks sind enthalten — der Plan entwickelt die reinen Helfer
(`PresenceStateResolver`, `PresenceThrottle`, `TeamCodeResolver`) **freiwillig
test-first** (entkoppelte Logik). Verfassungsprinzip III (Punktewertung/Reveal)
ist von F9 nicht betroffen.

**Organization**: Tasks gruppiert nach User Story (US1 LIVE → US2 UPCOMING →
US3 IDLE) für unabhängige Implementierung/Testung.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelisierbar (andere Datei, keine offene Abhängigkeit)
- **[Story]**: US1/US2/US3; Setup/Foundational/Polish ohne Story-Label
- Basis-Paket: `src/main/java/com/example/wmtippspiel`

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Konfiguration und Ressourcen für F9.

- [X] T001 [P] `app.presence`-Konfiguration ergänzen (`min-update-interval-ms` Default 5000, `idle-text` Default `🏆 WM 2026 /tipp`) in `src/main/resources/application.yml`
- [X] T002 [P] Team-Kürzel-Ressource `src/main/resources/presence/team-codes.properties` anlegen (initiale `Teamname=KÜRZEL`-Einträge, z. B. `Deutschland=GER`, `Frankreich=FRA`)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Gemeinsame Maschinerie, die ALLE drei Zustände benötigen — Presence-Setzen
nur bei Änderung, Throttling, JDA-Reconnect-Wiring, Datenzugriff.

**⚠️ CRITICAL**: Keine User-Story-Arbeit vor Abschluss dieser Phase.

- [X] T003 [P] `PresenceState`-Record (enum `Type{LIVE,UPCOMING,IDLE}` + `String text`) in `src/main/java/com/example/wmtippspiel/presence/PresenceState.java`
- [X] T004 [P] `TeamCodeResolver` (lädt `presence/team-codes.properties`, `code(name)` mit defensiv gekürztem Klartext-Fallback) in `src/main/java/com/example/wmtippspiel/presence/TeamCodeResolver.java`
- [X] T005 [P] Unit-Test `TeamCodeResolverTest` (Mapping-Treffer + Fallback-Kürzung, nie null/überlang) in `src/test/java/com/example/wmtippspiel/presence/TeamCodeResolverTest.java`
- [X] T006 [P] `PresenceThrottle` (reine, `Clock`-basierte Logik: `submit`/`flush`, `MIN_INTERVAL`, einzelner `pendingText`-Slot, `lastSentText`-Dedup) in `src/main/java/com/example/wmtippspiel/presence/PresenceThrottle.java`
- [X] T007 [P] Unit-Test `PresenceThrottleTest` (Mindestabstand ≥ `MIN_INTERVAL` ⇒ nie >5/20 s via Test-`Clock`; Coalescing „letzter gewinnt"; kein Senden bei unverändertem Text — C5/C6/C7) in `src/test/java/com/example/wmtippspiel/presence/PresenceThrottleTest.java`
- [X] T008 `MatchRepository.findInPlay()` (`SELECT * FROM matches WHERE status='IN_PLAY' ORDER BY kickoff ASC`) in `src/main/java/com/example/wmtippspiel/persistence/MatchRepository.java`
- [X] T009 `MatchRepository.updateLiveScore(id, home, away, status)` (UPDATE auf vorhandenen Spalten `home_score`/`away_score`/`status`, kein DDL) in `src/main/java/com/example/wmtippspiel/persistence/MatchRepository.java` (gleiche Datei wie T008)
- [X] T010 `PresenceStateResolver`-Grundgerüst: Prioritätsschema, IDLE-Baseline (`resolve(...)` liefert vorerst IDLE mit konfiguriertem `idleText`) in `src/main/java/com/example/wmtippspiel/presence/PresenceStateResolver.java`
- [X] T011 `PresenceManager` (`@Component`): JDA + `MatchRepository` + Resolver + Throttle + `Clock` injizieren; `recompute()` (liest `findInPlay`/`findUpcoming(now,1)` → Resolver → Throttle → `jda.getPresence().setActivity(Activity.watching(text))`); `@PostConstruct` `jda.addEventListener(this)`; `ListenerAdapter` `onReady`/`onReconnected`/`onSessionRecreate` → `recompute()`; Single-Thread-`ScheduledExecutorService` für verzögerten Flush + `@PreDestroy`-Shutdown; thread-safe (Lock) in `src/main/java/com/example/wmtippspiel/presence/PresenceManager.java`
- [X] T012 [P] Unit-Test `PresenceManagerTest` (gemocktes `JDA`/Repository: `setActivity` nur bei tatsächlicher Textänderung — FR-008/C8; leere Daten ⇒ IDLE) in `src/test/java/com/example/wmtippspiel/presence/PresenceManagerTest.java`

**Checkpoint**: Bot setzt beim Start (`onReady`) IDLE; Throttle, Dedup und Reconnect-Wiring stehen. Reconnect-fest (FR-011/C10).

---

## Phase 3: User Story 1 - Live-Stand in der Bot-Presence (Priority: P1) 🎯 MVP

**Goal**: Während eines laufenden Spiels zeigt der Bot-Status `⚽ LIVE: GER 2:1 FRA`, event-getrieben über den `liveGoalPoll`-Takt; bei mehreren Live-Spielen gewinnt das zuletzt veränderte.

**Independent Test**: Ein Spiel auf `status='IN_PLAY'` mit Stand setzen → nach dem nächsten `liveGoalPoll` zeigt der Status den LIVE-Text; Stand erhöhen → Status folgt (gedrosselt); identischer Re-Poll → kein erneutes `setActivity`.

### Tests for User Story 1 ⚠️ (test-first)

- [X] T013 [P] [US1] `PresenceStateResolverTest`: LIVE-Branch-Textformat (`⚽ LIVE: code h:a code`), Priorität LIVE über UPCOMING, Multi-Live-Auswahl „zuletzt verändert" + Tie-Breaker früherer Anpfiff (FR-002/003/013, C1/C2/C3) in `src/test/java/com/example/wmtippspiel/presence/PresenceStateResolverTest.java`

### Implementation for User Story 1

- [X] T014 [US1] `PresenceStateResolver`: LIVE-Branch ergänzen (Textbau mit `TeamCodeResolver`, Auswahl per `lastChange` dann `kickoff`) in `src/main/java/com/example/wmtippspiel/presence/PresenceStateResolver.java`
- [X] T015 [US1] `PresenceManager`: `ObservedLiveMatch`-Map pflegen (Stand-Diff ⇒ `lastChange=now`; beendete Spiele entfernen) und an Resolver übergeben in `src/main/java/com/example/wmtippspiel/presence/PresenceManager.java`
- [X] T016 [US1] `ScoreDiffGoalEventSource`: Für jedes vom `FootballDataClient` gelieferte Spiel im Zeitfenster `kickoff … kickoff+2,5 h` den frischen Stand+Status via `matches.updateLiveScore(...)` persistieren — **bevor** die bestehende `inLiveWindow`/null-Score-Guard das Spiel für die Goal-Erkennung überspringt, damit auch der **IN_PLAY→FINISHED-Übergang** im Live-Takt in `matches` landet (LIVE-Austritt zeitnah, FR-012). Goal-Erkennung/`notified_*`-Buchführung unverändert (T3) in `src/main/java/com/example/wmtippspiel/live/ScoreDiffGoalEventSource.java`
- [X] T017 [US1] Trigger: am Ende von `LiveGoalPollJob.postLiveGoals()` `presenceManager.recompute()` aufrufen (T1) in `src/main/java/com/example/wmtippspiel/scheduling/LiveGoalPollJob.java`
- [X] T018 [P] [US1] `ScoreDiffGoalEventSourceTest` erweitern: `updateLiveScore` wird für Spiele im Live-Fenster aufgerufen in `src/test/java/com/example/wmtippspiel/live/ScoreDiffGoalEventSourceTest.java`
- [X] T019 [P] [US1] `PersistenceIntegrationTest` erweitern: `findInPlay()` + `updateLiveScore()` Round-Trip in `src/test/java/com/example/wmtippspiel/persistence/PersistenceIntegrationTest.java`

**Checkpoint**: LIVE-Zustand voll funktionsfähig und unabhängig testbar (MVP).

---

## Phase 4: User Story 2 - Nächstes Spiel in der Bot-Presence (Priority: P2)

**Goal**: Wenn kein Spiel läuft, zeigt der Status `👀 Nächstes: GER vs FRA`, aktualisiert beim `boardRefresh`.

**Independent Test**: Kein Spiel `IN_PLAY`, ein künftiges `SCHEDULED`-Spiel vorhanden → nach `boardRefresh` zeigt der Status den UPCOMING-Text; ohne künftiges Spiel ⇒ IDLE.

### Tests for User Story 2 ⚠️ (test-first)

- [X] T020 [P] [US2] `PresenceStateResolverTest`: UPCOMING-Branch-Textformat (`👀 Nächstes: code vs code`), Priorität (LIVE leer, UPCOMING vorhanden) und IDLE wenn kein künftiges Spiel (C1/C3) in `src/test/java/com/example/wmtippspiel/presence/PresenceStateResolverTest.java`

### Implementation for User Story 2

- [X] T021 [US2] `PresenceStateResolver`: UPCOMING-Branch ergänzen (nutzt `findUpcoming(now,1)`-Ergebnis + `TeamCodeResolver`) in `src/main/java/com/example/wmtippspiel/presence/PresenceStateResolver.java`
- [X] T022 [US2] Trigger: nach `boardService.refresh()` in `BoardRefreshJob.refreshBoard()` `presenceManager.recompute()` aufrufen (T2) in `src/main/java/com/example/wmtippspiel/scheduling/BoardRefreshJob.java`

**Checkpoint**: US1 und US2 funktionieren unabhängig; Priorität LIVE > UPCOMING verifiziert.

---

## Phase 5: User Story 3 - Statischer Fallback (IDLE) (Priority: P3)

**Goal**: Ohne laufendes/künftiges Spiel zeigt der Status den statischen Fallback `🏆 WM 2026 /tipp` (konfigurierbar).

**Independent Test**: Keine `IN_PLAY`- und keine künftigen Spiele → Status zeigt den IDLE-Text; beim Start (`onReady`) sofort sichtbar.

### Tests for User Story 3 ⚠️ (test-first)

- [X] T023 [P] [US3] `PresenceStateResolverTest`: IDLE-Fallback nutzt den konfigurierten `idle-text`, wenn weder LIVE noch UPCOMING zutrifft (C1/C3) in `src/test/java/com/example/wmtippspiel/presence/PresenceStateResolverTest.java`

### Implementation for User Story 3

- [X] T024 [US3] Konfigurierten `app.presence.idle-text` durch `PresenceManager` an den Resolver durchreichen und IDLE-Branch finalisieren in `src/main/java/com/example/wmtippspiel/presence/PresenceManager.java`
- [X] T025 [US3] `onReady`-Initial-`recompute()` verifizieren/absichern, dass leere Datenlage IDLE setzt (nie leer/veraltet, FR-007/SC-005) in `src/main/java/com/example/wmtippspiel/presence/PresenceManager.java`

**Checkpoint**: Alle drei Zustände unabhängig funktionsfähig.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: Stabilität, Sichtbarkeit, Regressionsschutz.

- [X] T026 [P] Log-Zeilen ergänzen (Zustandswechsel, gedrosselter/aufgeschobener Flush) in `src/main/java/com/example/wmtippspiel/presence/PresenceManager.java`
- [ ] T027 [P] Team-Kürzel-Mapping auf alle 48 WM-2026-Teams vervollständigen in `src/main/resources/presence/team-codes.properties` — *offen: repräsentativer Starter-Satz (Gastgeber + Top-Nationen, EN+DE) ist angelegt; vollständige 48 erst nach finaler Qualifikation pflegbar. Fehlende Teams nutzen bis dahin den Klartext-Fallback.*
- [X] T028 `mvn test` ausführen und sicherstellen, dass F1–F8 nicht regressiv brechen (bestehende Tests grün) — **78 Tests grün, BUILD SUCCESS**
- [ ] T029 Quickstart-Validierung manuell durchlaufen (IDLE → UPCOMING → LIVE → Priorität) gemäß `specs/004-dynamic-bot-presence/quickstart.md` — *offen: erfordert laufenden Bot mit gültigem `DISCORD_TOKEN`; das Verhalten ist durch die automatisierten Tests (Resolver/Throttle/Manager/Persistenz) abgedeckt.*

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: keine Abhängigkeiten.
- **Foundational (Phase 2)**: nach Setup; **blockt alle User Stories**.
- **User Stories (Phase 3–5)**: nach Foundational. US1 ist MVP. US2/US3 erweitern den
  Resolver (gleiche Datei `PresenceStateResolver.java`) und kommen daher in
  Prioritätsreihenfolge; jede ist eigenständig testbar.
- **Polish (Phase 6)**: nach den gewünschten User Stories.

### User Story Dependencies

- **US1 (P1)**: nach Foundational — keine Abhängigkeit von anderen Stories.
- **US2 (P2)**: nach Foundational — unabhängig testbar; teilt sich Resolver-Datei mit US1 (sequenziell editieren).
- **US3 (P3)**: nach Foundational — IDLE-Baseline existiert bereits aus T010; finalisiert Konfig/Startpfad.

### Within Each User Story

- Test-Task (test-first) zuerst schreiben und scheitern lassen.
- Resolver-Branch vor Trigger-Verdrahtung.
- Story abschließen, bevor zur nächsten Priorität gewechselt wird.

### Parallel Opportunities

- Setup: T001, T002 parallel.
- Foundational: T003, T004, T005, T006, T007 parallel (verschiedene Dateien); T008→T009 sequenziell (gleiche Datei); T012 parallel zu Nicht-Manager-Tasks.
- US1: T013 (Test) parallel vorab; T018, T019 parallel (verschiedene Testdateien). T014/T015 sequenziell ggü. Foundational-Manager/Resolver.
- Resolver-editierende Tasks über Stories hinweg (T014, T021, T024) sind **nicht** untereinander [P] (gleiche Datei).

---

## Parallel Example: Foundational (Phase 2)

```bash
# Reine, entkoppelte Bausteine gleichzeitig (verschiedene Dateien):
Task: "PresenceState record in presence/PresenceState.java"           # T003
Task: "TeamCodeResolver in presence/TeamCodeResolver.java"            # T004
Task: "TeamCodeResolverTest in presence/TeamCodeResolverTest.java"    # T005
Task: "PresenceThrottle in presence/PresenceThrottle.java"           # T006
Task: "PresenceThrottleTest in presence/PresenceThrottleTest.java"   # T007
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 Setup → Phase 2 Foundational (kritisch, blockt alles).
2. Phase 3 US1 (LIVE) komplett.
3. **STOP & VALIDATE**: LIVE unabhängig testen (Spiel IN_PLAY → Presence folgt; Re-Poll → kein Doppel-Update).
4. Deploy/Demo.

### Incremental Delivery

1. Setup + Foundational → Bot zeigt IDLE, Throttle/Reconnect stehen.
2. + US1 (LIVE) → MVP.
3. + US2 (UPCOMING) → unabhängig testen → Demo.
4. + US3 (IDLE-Finalisierung/Konfig) → vollständig.
5. Polish.

---

## Notes

- [P] = andere Datei, keine offene Abhängigkeit.
- **Keine** DB-Schema-Änderung, **kein** Liquibase-Changeset, **keine** neuen Abhängigkeiten (siehe plan.md / data-model.md).
- F9 ist rein additiv: bestehende Pfade nur an Trigger-/Persistenz-Stellen erweitert (T016, T017, T022) — keine Signatur-/Verhaltensänderung bestehender Methoden.
- Throttle-Mindestabstand 5000 ms ⇒ ≤4 Updates/20 s (sichere Marge unter 5/20 s).
- Nach jedem Task oder logischer Gruppe committen; an Checkpoints Story-Unabhängigkeit prüfen.
