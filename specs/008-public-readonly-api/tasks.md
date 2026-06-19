---
description: "Task list for feature 008 — Öffentliche Read-only-API"
---

# Tasks: Öffentliche Read-only-API

**Input**: Design documents from `/specs/008-public-readonly-api/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/public-api.openapi.yaml

**Tests**: ENTHALTEN. Begründung: Das Reveal-Gate ist Reveal-Timing-Kernlogik ⇒
Tests sind laut Verfassung (Prinzip III) PFLICHT und werden **test-first**
geschrieben (Red-Green-Refactor). Zusätzlich verifizieren Tests die harten
Sicherheits-/Datenschutz-Erfolgskriterien (SC-001 kein Leak, SC-002 Reveal,
SC-006 read-only).

**Organization**: Nach User Story (US1–US4) gruppiert; jede Story ist eigenständig
implementier-, test- und auslieferbar.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Parallelisierbar (andere Datei, keine offene Abhängigkeit)
- **[Story]**: Zuordnung zur User Story (US1–US4)
- Exakte Dateipfade sind in jeder Aufgabe genannt.

**Hinweis zu geteilten Dateien**: `PublicApiController.java`,
`PublicQueryService.java` und `PublicMappers.java` werden von mehreren Stories
ergänzt. Aufgaben, die dieselbe Datei anfassen, sind NICHT mit `[P]` markiert und
müssen sequенziell laufen. Die Stories bleiben dennoch unabhängig testbar.

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Web-Server aktivieren und Konfiguration bereitstellen.

- [X] T001 Maven-Abhängigkeiten ergänzen in `pom.xml`: `spring-boot-starter-web`, `spring-boot-starter-cache`, `com.github.ben-manes.caffeine:caffeine`
- [X] T002 In `src/main/resources/application.yml`: `spring.main.web-application-type` von `none` auf `servlet` setzen, `server.port: ${SERVER_PORT:8080}` ergänzen und Sektion `app.public-api` hinzufügen (`cors-allowed-origins: ${PUBLIC_API_CORS_ALLOWED_ORIGINS:}`, `id-secret: ${PUBLIC_API_ID_SECRET:}`, `cache-ttl-seconds: ${PUBLIC_API_CACHE_TTL_SECONDS:5}`)
- [X] T003 [P] `AppProperties` in `src/main/java/com/example/wmtippspiel/config/AppProperties.java` um verschachteltes Record `PublicApi(java.util.List<String> corsAllowedOrigins, String idSecret, long cacheTtlSeconds)` erweitern (additiv, bestehende Records unverändert)

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Gemeinsame Infrastruktur für alle Stories; Regressionsschutz.

**⚠️ CRITICAL**: Vor diesen Aufgaben darf keine Story-Arbeit beginnen.

- [X] T004 Regressionsschutz: Verifiziert, dass KEIN Test `@SpringBootTest` nutzt (alle 22 Testklassen sind reine JUnit/Testcontainers ohne Spring-Context) — die Umstellung auf `web-application-type: servlet` startet daher in keinem bestehenden Test einen Tomcat; kein Code-Eingriff nötig. (Hinweis: Testcontainers-Tests erfordern Docker, hier nicht ausführbar — vorbestehend, unabhängig von Feature 008.)
- [ ] T005 [P] `PublicApiConfig` in `src/main/java/com/example/wmtippspiel/publicapi/PublicApiConfig.java`: `@Configuration @EnableCaching`, CORS für `/api/public/**` aus `app.public-api.cors-allowed-origins` (Methoden `GET, OPTIONS`, keine Credentials) via `WebMvcConfigurer#addCorsMappings`, Caffeine-`CacheManager` mit TTL aus `app.public-api.cache-ttl-seconds` (Caches `schedule`, `leaderboard`)
- [X] T006 [P] `PublicMappers` in `src/main/java/com/example/wmtippspiel/publicapi/PublicMappers.java`: finale Klasse mit privatem Konstruktor, statische Mapping-Methoden werden je Story ergänzt
- [X] T007 [P] `PublicQueryService` in `src/main/java/com/example/wmtippspiel/publicapi/PublicQueryService.java`: `@Service`, Konstruktor-Injektion von `MatchRepository`, `TipRepository`, `LeaderboardSnapshotRepository` und `java.time.Clock` (für deterministisches Reveal-Gating); Methoden je Story ergänzt
- [X] T008 [P] `PublicApiController` in `src/main/java/com/example/wmtippspiel/publicapi/PublicApiController.java`: `@RestController @RequestMapping("/api/public")`, Konstruktor-Injektion von `PublicQueryService` (und später `PublicIdService`); nur `@GetMapping`-Methoden werden ergänzt (keine Schreibpfade)

**Checkpoint**: Web-Server startet, CORS/Cache konfiguriert, Baseline-Tests grün — Story-Implementierung kann beginnen.

---

## Phase 3: User Story 1 — Spielplan & Live-Spiele (Priority: P1) 🎯 MVP

**Goal**: Anonyme Abfrage des vollständigen/gefilterten Spielplans und der aktuell laufenden Spiele mit Stand.

**Independent Test**: `GET /api/public/schedule` (voll + `?stage=&group=&matchday=`) liefert alle/gefilterte Spiele mit UTC-Anstoß, Sender, Quote, Ergebnis, Status, Gruppe/Phase; `GET /api/public/matches/live` liefert nur `IN_PLAY` bzw. leere Liste; keine `user_id` im JSON.

### Tests for User Story 1

- [X] T009 [P] [US1] Web-Test `src/test/java/com/example/wmtippspiel/publicapi/PublicScheduleWebTest.java` (MockMvc, `@SpringBootTest(webEnvironment=MOCK)` + Testcontainers-PostgreSQL): Spielplan vollständig & gefiltert (stage/group/matchday), Live nur `IN_PLAY`, leere Mengen → leere Liste, JSON ohne `user_id`; assert `kickoffUtc` als UTC-ISO-8601 (`…Z`) serialisiert (FR-005)

### Implementation for User Story 1

- [X] T010 [P] [US1] Read-only `MatchRepository.findAll()` (alle Spiele exkl. `CANCELLED`, sortiert nach `kickoff`) additiv in `src/main/java/com/example/wmtippspiel/persistence/MatchRepository.java`
- [X] T011 [P] [US1] Records `MatchDto` und `LiveMatchDto` in `src/main/java/com/example/wmtippspiel/publicapi/dto/` gemäß data-model.md (UTC-`Instant`, keine internen IDs außer Fixture-`matchId`)
- [X] T012 [US1] `PublicMappers.toMatchDto(Match)` und `toLiveMatchDto(Match)` in `PublicMappers.java` ergänzen (kein `user_id`/intern; null-sichere Quote/Ergebnis)
- [X] T013 [US1] `PublicQueryService.schedule(String stage, String group, Integer matchday)` (`@Cacheable("schedule")`, In-Memory-Filter über `findAll()`) und `liveMatches()` (über `MatchRepository.findInPlay()`) in `PublicQueryService.java`
- [X] T014 [US1] In `PublicApiController.java`: `@GetMapping("/schedule")` (mit `@RequestParam(required=false)` stage/group/matchday) und `@GetMapping("/matches/live")`

**Checkpoint**: US1 eigenständig funktionsfähig und testbar (MVP).

---

## Phase 4: User Story 2 — Leaderboard (Priority: P2)

**Goal**: Vollständige Rangliste mit Anzeigename, Punkten, exakten Treffern (direkter Ergebnisvergleich) und Rang-Veränderung.

**Independent Test**: `GET /api/public/leaderboard` liefert nach Rang sortierte Zeilen mit `rankChange`-Symbol; JSON enthält weder `user_id` noch `email`/`token`; exakte Treffer stimmen mit direktem Tipp-Ergebnis-Vergleich überein.

### Tests for User Story 2

- [X] T015 [P] [US2] Web-Test `src/test/java/com/example/wmtippspiel/publicapi/PublicLeaderboardWebTest.java`: Rangliste vorhanden & sortiert, `rankChange` gesetzt, JSON ohne `user_id`/`email`/`token` (SC-001), exakte Treffer entkoppelt vom Punktwert (FR-010)

### Implementation for User Story 2

- [X] T016 [P] [US2] Record `LeaderboardRowDto` in `src/main/java/com/example/wmtippspiel/publicapi/dto/LeaderboardRowDto.java` (rank, displayName, points, exactHits, rankChange)
- [X] T017 [US2] `PublicMappers.toLeaderboardRow(RankedRow)` in `PublicMappers.java` (rankChange via `RankDelta.symbol()`, displayName aus `LeaderboardEntry.username`)
- [X] T018 [US2] `PublicQueryService.leaderboard()` (`@Cacheable("leaderboard")`) in `PublicQueryService.java`: `TipRepository.leaderboard()` → `LeaderboardRanking.compute(entries, LeaderboardSnapshotRepository.findAllRanks())` → Map auf `LeaderboardRowDto` (KEIN `replaceAll`-Aufruf, nur lesend)
- [X] T019 [US2] In `PublicApiController.java`: `@GetMapping("/leaderboard")`

**Checkpoint**: US1 und US2 unabhängig funktionsfähig.

---

## Phase 5: User Story 3 — Tipps pro Spiel nach Anpfiff (Priority: P3) 🔒 sicherheitskritisch

**Goal**: Tipps eines Spiels NUR ausliefern, wenn `now()(UTC) ≥ kickoff` UND `revealed = true`; davor keine Einzeltipps im JSON.

**Independent Test**: Für ein nicht angepfiffenes Spiel liefert `GET /api/public/matches/{id}/tips` `released=false` mit `tips:[]` (keine Namen/Ergebnisse); für ein angepfiffenes Spiel `released=true` mit Tipps; unbekanntes Spiel → 404.

### Tests for User Story 3 ⚠️ TEST-FIRST (Verfassung Prinzip III — Reveal-Timing)

> **Zuerst schreiben und FEHLSCHLAGEN lassen, bevor T022–T024 implementiert werden.**

- [X] T020 [P] [US3] `RevealGateTest` in `src/test/java/com/example/wmtippspiel/publicapi/RevealGateTest.java`: mit fixer `Clock` — (a) `now()<kickoff` → `released=false`, leere Tipps; (b) `now()≥kickoff` aber `revealed=false` → `released=false`; (c) beide erfüllt → Tipps vorhanden; assert: kein `username`/Ergebnis im DTO bei `released=false`

### Implementation for User Story 3

- [X] T021 [P] [US3] Records `PublicTipDto` und `MatchTipsDto` in `src/main/java/com/example/wmtippspiel/publicapi/dto/` (data-model.md)
- [X] T022 [US3] `PublicMappers.toPublicTip(Tip, boolean evaluated)` in `PublicMappers.java` (displayName, tipHome/away, points nur bei gewertetem Spiel)
- [X] T023 [US3] `PublicQueryService.matchTips(long matchId)` in `PublicQueryService.java`: `MatchRepository.findById` (leer → Signal für 404); Gate `!clock.kickoffReached || !match.revealed()` ⇒ `MatchTipsDto(matchId, released=false, [])` OHNE Laden der Tipps; sonst `TipRepository.findByMatch` → Mapping (nicht gecacht)
- [X] T024 [US3] In `PublicApiController.java`: `@GetMapping("/matches/{matchId}/tips")`; unbekanntes Spiel → HTTP 404 ohne interne Details
- [X] T025 [P] [US3] Web-Test `src/test/java/com/example/wmtippspiel/publicapi/PublicMatchTipsWebTest.java`: Vor-Anpfiff-JSON enthält keinerlei fremde Tipps (SC-002), Nach-Anpfiff liefert Tipps, unbekannte `matchId` → 404

**Checkpoint**: US1–US3 unabhängig funktionsfähig; Reveal-Sicherheit bewiesen.

---

## Phase 6: User Story 4 — Spielerprofil (Priority: P3)

**Goal**: Profil über stabilen, nicht-sensiblen `publicId` (HMAC der `user_id`) mit Statistik, Verteilung 4/3/2/0, best/worst und Historie (nur gewertete Tipps).

**Independent Test**: `GET /api/public/players/{publicId}` liefert Profil ohne `user_id`; unbekannter Identifier → 404; `hitRatePercent` ist `null` bei 0 Tipps.

### Tests for User Story 4

- [ ] T026 [P] [US4] `PublicIdServiceTest` in `src/test/java/com/example/wmtippspiel/publicapi/PublicIdServiceTest.java`: HMAC deterministisch; anderes Secret → anderer `publicId`; `resolve` bekannter Nutzer → user_id, unbekannter → leer
- [ ] T027 [P] [US4] Web-Test `src/test/java/com/example/wmtippspiel/publicapi/PublicProfileWebTest.java`: Profil via `publicId`, JSON ohne `user_id`, unbekannt → 404, `hitRatePercent=null` bei 0 gewerteten Tipps (FR-018)

### Implementation for User Story 4

- [X] T028 [P] [US4] Records `ProfileDto`, `ProfileTipDto`, `PointDistributionDto` in `src/main/java/com/example/wmtippspiel/publicapi/dto/` (data-model.md)
- [ ] T029 [P] [US4] `PublicIdService` in `src/main/java/com/example/wmtippspiel/publicapi/PublicIdService.java`: `publicId(userId)` = Base64Url(HMAC-SHA256(`app.public-api.id-secret`, userId)) gekürzt; `resolve(publicId)` per Enumeration über `TipRepository.leaderboard()`; **Bean-Initialisierung wirft eine Exception, wenn `app.public-api.id-secret` leer/blank ist** (Fail-Fast beim Start, kein unsicherer Laufzeit-Default)
- [X] T030 [US4] `PublicMappers.toProfileDto(UserProfile, publicId, List<ProfileTipRow>)` und `toProfileTip(ProfileTipRow)` in `PublicMappers.java`
- [ ] T031 [US4] `PublicQueryService.profile(String publicId)` in `PublicQueryService.java`: `PublicIdService.resolve` (leer → 404-Signal); Rang via `LeaderboardRanking.compute(...)` (passender `RankedRow`), `TipRepository.findEvaluatedTipsByUser`, `ProfileStats.build(...)` → `toProfileDto`
- [ ] T032 [US4] In `PublicApiController.java`: `@GetMapping("/players/{publicId}")`; unbekannt → HTTP 404

**Checkpoint**: Alle vier User Stories unabhängig funktionsfähig.

---

## Phase 7: Polish & Cross-Cutting Concerns

**Purpose**: Querschnittliche Sicherheits-/Betriebsnachweise und Doku.

- [X] T033 [P] Cross-cutting Datenschutz-Test (DTO-Ebene, JSON-Serialisierung) `src/test/java/com/example/wmtippspiel/publicapi/PublicApiPrivacyTest.java`: JSON ALLER fünf Endpoints enthält keines der Felder `user_id`/`email`/`token` (SC-001)
- [X] T034 [P] Read-only-Nachweis: POST auf einen GET-Endpoint → HTTP 405 (SC-006), abgedeckt in `PublicApiWebTest.postIsRejected()`
- [ ] T035 [P] Web-Test `src/test/java/com/example/wmtippspiel/publicapi/PublicApiCorsTest.java`: OPTIONS-Preflight mit erlaubtem Vercel-Origin liefert `Access-Control-Allow-Origin` (R8)
- [ ] T036 [P] `docker-compose.yml` und `.env.example`: Port (`SERVER_PORT`) exponieren/mappen und `PUBLIC_API_ID_SECRET`, `PUBLIC_API_CORS_ALLOWED_ORIGINS`, `PUBLIC_API_CACHE_TTL_SECONDS` dokumentieren
- [ ] T037 Vollständiger `mvn test` (119 Baseline + neue Tests grün) und Quickstart-Verifikation gemäß `specs/008-public-readonly-api/quickstart.md`
- [ ] T038 [P] `README.md`: öffentliche API (Endpoints, UTC, CORS, Caching, Reveal-Regel) und neue Umgebungsvariablen dokumentieren

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: keine Abhängigkeiten — sofort startbar
- **Foundational (Phase 2)**: nach Setup; T004 (Regressionsschutz) vor allem anderen empfohlen — BLOCKIERT alle Stories
- **User Stories (Phase 3–6)**: alle nach Phase 2. Reihenfolge nach Priorität P1 → P2 → P3 (US3/US4 gleichrangig P3)
- **Polish (Phase 7)**: nach den gewünschten Stories

### User Story Dependencies

- **US1 (P1)**: nach Foundational — keine Abhängigkeit von anderen Stories
- **US2 (P2)**: nach Foundational — unabhängig testbar
- **US3 (P3)**: nach Foundational — unabhängig testbar (sicherheitskritisch, test-first)
- **US4 (P3)**: nach Foundational — unabhängig testbar; nutzt `PublicIdService` (story-eigen)

### Within Each User Story

- Test-first nur zwingend bei US3 (Reveal-Timing, T020 vor T021–T024)
- DTOs (`[P]`) vor Mapper; Mapper/Repo vor Service; Service vor Controller-Endpoint
- Geteilte Dateien (`PublicMappers`, `PublicQueryService`, `PublicApiController`): Aufgaben sequenziell pro Datei

### Parallel Opportunities

- Setup: T003 `[P]` parallel zu T001/T002-Folgearbeiten
- Foundational: T005/T006/T007/T008 `[P]` (verschiedene neue Dateien) nach T004
- Pro Story sind DTO- und Test-Aufgaben `[P]`; die Service-/Controller-Aufgaben nicht (geteilte Dateien)
- Polish: T033/T034/T035/T036/T038 `[P]`

---

## Parallel Example: User Story 1

```bash
# Zuerst die Foundational-Shells parallel:
Task: "T005 PublicApiConfig (CORS + Caffeine)"
Task: "T006 PublicMappers Skelett"
Task: "T007 PublicQueryService Skelett"
Task: "T008 PublicApiController Skelett"

# In US1 parallel:
Task: "T010 MatchRepository.findAll()"
Task: "T011 MatchDto + LiveMatchDto records"
Task: "T009 PublicScheduleWebTest (MockMvc)"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 Setup → 2. Phase 2 Foundational (T004 zuerst!) → 3. Phase 3 US1
4. **STOP & VALIDATE**: Spielplan + Live unabhängig testen → ggf. deploy/demo (MVP)

### Incremental Delivery

1. Setup + Foundational → Fundament steht
2. + US1 (Spielplan/Live) → testen → Demo (MVP)
3. + US2 (Leaderboard) → testen → Demo
4. + US3 (Tipps, reveal-gated, test-first) → testen → Demo
5. + US4 (Profil) → testen → Demo
6. Polish (Privacy/ReadOnly/CORS-Tests, Doku, Compose/Env)

---

## Notes

- `[P]` = andere Datei, keine offene Abhängigkeit
- Geteilte Dateien (`PublicMappers`, `PublicQueryService`, `PublicApiController`) verhindern Cross-Story-Parallelität auf Dateiebene — Stories bleiben dennoch unabhängig auslieferbar
- US3-Tests (Reveal-Timing) sind PFLICHT und test-first (Verfassung Prinzip III) — vor der Implementierung fehlschlagen lassen
- Kein Schema-Eingriff, kein Liquibase-Changeset (HMAC-Identifier zur Laufzeit)
- Nur lesend: kein `LeaderboardSnapshotRepository.replaceAll`, kein `update*`/`upsert` aus dem Public-Layer
- Nach jeder Aufgabe/Logikgruppe committen; an Checkpoints Story-Unabhängigkeit prüfen
