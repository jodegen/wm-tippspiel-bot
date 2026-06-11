---

description: "Task list for WM 2026 Tippspiel Discord-Bot"
---

# Tasks: WM 2026 Tippspiel Discord-Bot

**Input**: Design documents from `/specs/001-wm-tippspiel-bot/`

**Prerequisites**: plan.md (required), spec.md (required for user stories), research.md, data-model.md, contracts/

**Tests**: Tests sind grundsätzlich optional — **AUSNAHME**: Verfassung Prinzip III
(NON-NEGOTIABLE) macht Tests für die **Punktewertung (3/1/0)** und das
**Reveal-/Eval-Timing** verpflichtend und **test-first** (zuerst schreiben, rot,
dann implementieren). Diese Pflicht-Tests sind in US2 und US3 markiert. Übrige
Tests (Repositories, API-Clients) sind empfohlen und im Polish-Abschnitt gelistet.

**Organization**: Tasks sind nach User Story gruppiert, damit jede Story
unabhängig implementiert und getestet werden kann.

**Base package**: `com.example.wmtippspiel` (Pfade: `src/main/java/com/example/wmtippspiel/...`)

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Parallelisierbar (andere Datei, keine offene Abhängigkeit)
- **[Story]**: Zugehörige User Story (US1–US6)
- Exakte Dateipfade in jeder Beschreibung

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Maven-Projekt und Grundgerüst

- [X] T001 Maven-Projekt anlegen: `pom.xml` mit Java 21, Spring Boot 3.x (`spring-boot-starter`, `-webflux` für `WebClient`, `-jdbc`), JDA, Liquibase, PostgreSQL-Treiber sowie Test-Deps (JUnit 5, AssertJ, Testcontainers-postgresql, MockWebServer)
- [X] T002 [P] `src/main/java/com/example/wmtippspiel/WmTippspielApplication.java` mit `@SpringBootApplication` und `@EnableScheduling` anlegen
- [X] T003 [P] `src/main/resources/application.yml`: Datasource/Discord/API-Keys über Umgebungsvariablen, **kein** `spring.jpa.ddl-auto`, Liquibase-Changelog-Pfad, `app.timezone.display=Europe/Berlin`
- [ ] T004 [P] Code-Formatierung konfigurieren (Spotless o. Ä.) in `pom.xml`/Config-Datei

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Schema, Modelle, Persistenz, Discord-/HTTP-Infrastruktur und
Match-Sync — Voraussetzung für ALLE User Stories.

**⚠️ CRITICAL**: Keine User-Story-Arbeit vor Abschluss dieser Phase.

**Schema (Liquibase — ein Changeset-File pro Tabelle, Prinzip II)**

- [X] T005 Liquibase-Master `src/main/resources/db/changelog/db.changelog-master.yaml` anlegen, das die Changesets einbindet
- [X] T006 [P] Changeset `.../db/changelog/changesets/001-create-matches.sql` (Tabelle `matches` inkl. `group_label`, Status-Default, Indizes lt. data-model.md)
- [X] T007 [P] Changeset `.../db/changelog/changesets/002-create-tips.sql` (Tabelle `tips`, PK `(user_id, match_id)`, FK→`matches`, Index `idx_tips_match`)
- [X] T008 [P] Changeset `.../db/changelog/changesets/003-create-bot-messages.sql` (Tabelle `bot_messages`, PK `key`)

**Domänenmodelle & Enums**

- [X] T009 [P] `domain/model/MatchStatus.java` (SCHEDULED/IN_PLAY/FINISHED/POSTPONED/CANCELLED) und `domain/model/Stage.java`
- [X] T010 [P] `domain/model/Match.java` (inkl. abgeleiteter Regeln teamsKnown/tippbar als Methoden), `domain/model/Tip.java`, `domain/model/BotMessage.java`

**Zeit & Konfiguration**

- [X] T011 [P] `config/TimeConfig.java`: `Clock` (UTC) als Bean und `ZoneId` Europe/Berlin für Anzeige; `discord/render/TimeFormatting.java` (UTC→Berlin, Discord-`<t:UNIX:R/F>`)
- [X] T012 [P] `config/WebClientConfig.java`: zwei `WebClient`-Beans (football-data.org mit `X-Auth-Token`, The Odds API mit `apiKey`), Timeouts
- [X] T013 `config/DiscordConfig.java`: JDA-Bean mit Token/Intents, dauerhafte Gateway-Verbindung, Bereitstellung von Announce-/Board-Channel-IDs (Prinzip V)

**Persistenz (JdbcClient-Repositories)**

- [X] T014 [P] `persistence/MatchRepository.java`: Upsert (`ON CONFLICT (id)` ohne Überschreiben von `channel`/`revealed`/`evaluated`), Finder (reveal-fähig, eval-fähig, tippbar, nächste N, by id)
- [X] T015 [P] `persistence/TipRepository.java`: Upsert `(user_id, match_id)` ohne `points`-Reset, Finder by match, Punkte-Update
- [X] T016 [P] `persistence/BotMessageRepository.java`: get/save by `key`

**Match-Sync (Datenbasis für alle Stories)**

- [X] T017 `sync/FootballDataClient.java`: `GET /competitions/WC/matches` via `WebClient`, Mapping lt. contracts/external-apis.md, defensive Fehlerbehandlung (Rate-Limit/Timeout → loggen & überspringen)
- [X] T018 `sync/ChannelMapping.java` + `src/main/resources/tv-channels.yml`: manuelles TV-Sender-Mapping in `matches.channel`
- [X] T019 `sync/MatchSyncService.java`: Matches upserten (UTC), `kickoff`-Verschiebung übernehmen (FR-004a), Absage markieren (FR-004b); **Neubewertungs-Signal**: ändert sich beim Upsert der Endstand eines bereits `evaluated=true`-Spiels, wird `evaluated` auf `false` zurückgesetzt (kein separater Marker) — die reguläre Auswertung in US3 (T032) greift den Fall dann automatisch auf (FR-017a)
- [X] T020 `scheduling/SyncJob.java`: `@Scheduled` (~15 Min) ruft `MatchSyncService`
- [X] T021 `discord/DiscordCommandRegistrar.java`: Slash-Commands beim Start guild-scoped registrieren; zentraler `discord/InteractionListener.java` (Routing für Slash/Autocomplete/Component)

**Checkpoint**: Schema, Daten-Sync und Discord-Gateway stehen — Stories können starten.

---

## Phase 3: User Story 1 - Tipp abgeben und aktualisieren (Priority: P1) 🎯 MVP

**Goal**: Mitglieder geben für tippbare Spiele ephemeral ein Ergebnis ab und
aktualisieren es bis Anpfiff; ein Tipp pro User & Spiel.

**Independent Test**: Tippbares Spiel wählen, `2:1` abgeben → nur für den User
sichtbare Bestätigung; erneut `0:0` → Update statt Duplikat; Abgabe auf
angepfiffenes/TBD-Spiel wird abgelehnt bzw. nicht angeboten.

- [X] T022 [US1] `discord/commands/TippCommand.java`: Slash `/tipp <spiel> <heim> <gast>`, Validierung (`heim/gast >= 0`), Tippbarkeit prüfen (FR-007), Upsert via `TipRepository`, **ephemerale** Antwort (FR-008), `username`/`created_at` setzen (FR-010)
- [X] T023 [US1] `discord/commands/TippAutocomplete.java`: Autocomplete liefert nur tippbare Spiele (kickoff in Zukunft, beide Teams bekannt, nicht abgesagt — FR-009), Value=`match_id`, Label mit Berlin-Zeit
- [X] T024 [US1] `TippCommand`/`TippAutocomplete` im `InteractionListener` registrieren und Ablehnungs-/Erfolgsmeldungen formulieren

**Checkpoint**: US1 eigenständig funktionsfähig und testbar.

---

## Phase 4: User Story 2 - Tipps automatisch offenlegen bei Anpfiff (Priority: P1)

**Goal**: Bei Anpfiff werden alle Tipps eines Spiels genau einmal offengelegt;
Trigger an `kickoff`, idempotent über Neustarts.

**Independent Test**: Spiel mit Anpfiff in unmittelbarer Vergangenheit → nach
Prüfzyklus genau ein Reveal-Post, Spiel als `revealed` markiert; erneuter Lauf
legt nicht erneut offen; Zukunftsspiel wird nicht offengelegt.

### Tests for User Story 2 (PFLICHT — Verfassung Prinzip III: Reveal-Timing) ⚠️

> Zuerst schreiben, FEHLSCHLAGEN lassen, dann implementieren.

- [X] T025 [P] [US2] `src/test/java/com/example/wmtippspiel/reveal/RevealServiceTest.java`: Reveal-Timing (kickoff erreicht vs. Zukunft), Idempotenz (kein zweites Reveal), Absage→kein Reveal; gegen injizierte `Clock` und gemockte Repositories (FR-011/012/013/031)

### Implementation for User Story 2

- [X] T026 [US2] `reveal/RevealService.java`: reveal-fähige Spiele laden, Tipps sammeln, in Transaktion Post koppeln + `revealed=true` setzen (idempotent, Clock-basiert)
- [X] T027 [P] [US2] `discord/render/RevealEmbed.java`: Embed aller Tipps (Name→Tipp), Leerfall „keine Tipps"
- [X] T028 [US2] `scheduling/RevealJob.java`: `@Scheduled` (minütlich) ruft `RevealService`, postet in Announce-Channel

**Checkpoint**: US1 + US2 unabhängig funktionsfähig.

---

## Phase 5: User Story 3 - Automatische Auswertung und Punktevergabe (Priority: P1)

**Goal**: Nach Spielende Punkte nach 3/1/0 berechnen, persistieren, Übersicht
posten; bei Ergebniskorrektur automatische Neubewertung.

**Independent Test**: Beendetes Spiel `2:1` → Tipp `2:1`=3, `3:0`=1, `1:2`=0;
`2:2` mit Tipp `0:0`=1; erneuter Lauf wertet nicht doppelt; geänderter Endstand
löst Neubewertung + Korrektur-Hinweis aus.

### Tests for User Story 3 (PFLICHT — Verfassung Prinzip III: Punktewertung) ⚠️

> Zuerst schreiben, FEHLSCHLAGEN lassen, dann implementieren.

- [X] T029 [P] [US3] `src/test/java/com/example/wmtippspiel/domain/scoring/ScoringServiceTest.java`: parametrisiert 3/1/0 inkl. Unentschieden-Tendenz, Tendenzgrenzen, „daneben" (FR-014)
- [X] T030 [P] [US3] `src/test/java/com/example/wmtippspiel/evaluation/EvaluationServiceTest.java`: Auswertung eval-fähiger Spiele, Idempotenz (FR-016), Neubewertung bei geändertem Endstand (FR-017a); gegen `Clock`/gemockte Repos

### Implementation for User Story 3

- [X] T031 [US3] `domain/scoring/ScoringService.java`: reine Funktion `(homeActual, awayActual, homeTip, awayTip) -> points` (3/1/0, Tendenz inkl. Unentschieden)
- [X] T032 [US3] `evaluation/EvaluationService.java`: eval-fähige Spiele auswerten, `tips.points` setzen, `evaluated=true` (Transaktion); Neubewertung läuft über denselben Pfad — ein durch T019 auf `evaluated=false` zurückgesetztes Spiel wird regulär neu berechnet; der Eval-Post kennzeichnet diesen Fall als Korrektur (FR-017a)
- [X] T033 [P] [US3] `discord/render/EvaluationEmbed.java`: Endstand + Punkteübersicht; Korrektur-Hinweis-Variante (FR-017/017a)
- [X] T034 [US3] `scheduling/EvaluateJob.java`: `@Scheduled` (minütlich) ruft `EvaluationService`, postet in Announce-Channel

**Checkpoint**: Kern-Spielschleife (US1–US3) vollständig.

---

## Phase 6: User Story 4 - Rangliste (Priority: P2)

**Goal**: Auf Abruf nach Punkten sortierte Rangliste mit Tipps-Anzahl und
exakten Treffern; Gleichstand → geteilter Rang.

**Independent Test**: Mehrere User mit ausgewerteten Tipps → absteigende
Sortierung; Punktgleichheit → mehr exakte Treffer oben; sonst geteilter Rang.

- [X] T035 [US4] `persistence/TipRepository.java` erweitern: Leaderboard-Aggregation (`SUM(points)`, `COUNT(*)`, `COUNT(*) FILTER (WHERE points=3)`, Sortierung Punkte↓, exakte Treffer↓) — FR-018/019/020
- [X] T036 [US4] `discord/commands/RanglisteCommand.java` + `discord/render/RanglisteEmbed.java`: `/rangliste` mit Rang (geteilt bei Gleichstand), Name, Punkte, Tipps, exakte Treffer; im `InteractionListener` registrieren

**Checkpoint**: US1–US4 unabhängig funktionsfähig.

---

## Phase 7: User Story 5 - Live-Spielplan-Board mit interaktiven Filtern (Priority: P2)

**Goal**: Selbst-aktualisierendes, ortsfestes Board (Edit statt Neu-Post) je
Tages-Slot mit ephemeralen Filter-Antworten und Recovery gelöschter Nachrichten.

**Independent Test**: Erststart postet je Tages-Slot eine Nachricht (merkt
`message_id`); Updates editieren; Filterauswahl liefert ephemerale Ansicht ohne
das öffentliche Board zu ändern; gelöschte Nachricht wird neu gepostet.

- [X] T037 [P] [US5] `discord/render/BoardEmbed.java`: Tages-Slot-Embed je Spiel (Begegnung, `<t:UNIX:R>`, Sender, Quoten, Live-/Endstand); Slot-Keys `board:day:YYYY-MM-DD`/`board:today` (Embed-Limits einhalten, FR-023/024)
- [X] T038 [US5] `discord/board/BoardService.java`: Slots berechnen, vorhandene `bot_messages` editieren statt posten; 404 (`UnknownMessage`) → neu posten + `message_id` aktualisieren (FR-021/022/027); Erststart-Posting
- [X] T039 [P] [US5] `discord/components/BoardNavigation.java`: Navigations-Select/Buttons (`board:filter`: today/tomorrow/day/group:A–L/ko) unter dem Board (`board:nav`)
- [X] T040 [US5] `discord/components/BoardFilterHandler.java`: Component-Interaktion `deferReply(ephemeral)` + gefilterte Ansicht; öffentliches Board unverändert (FR-025/026); im `InteractionListener` registrieren
- [X] T041 [US5] `scheduling/BoardRefreshJob.java`: nach jedem Sync `BoardService` triggern (~15 Min). Häufigere Live-Stand-Aktualisierung während `IN_PLAY` ist bewusst NICHT im MVP — echte Live-Tor-Updates sind E2/Backlog (siehe FR-024-Hinweis)

**Checkpoint**: US1–US5 unabhängig funktionsfähig.

---

## Phase 8: User Story 6 - Spielplan-Übersicht und nächstes Spiel on-demand (Priority: P3)

**Goal**: Direktzugriff/Fallback-Commands neben dem Board.

**Independent Test**: `/spielplan` zeigt nächste N (Default 5) anstehende Spiele
(vergangene/laufende ausgeblendet); `/naechstes` zeigt das nächste Spiel mit
Countdown.

- [X] T042 [P] [US6] `discord/commands/SpielplanCommand.java` + Embed: `/spielplan [anzahl]` (Default 5, max 25), nächste N anstehende Spiele (FR-029)
- [X] T043 [P] [US6] `discord/commands/NaechstesCommand.java` + Embed: `/naechstes` mit `<t:UNIX:R>`-Countdown, Leerfall-Hinweis (FR-030)
- [X] T044 [US6] Beide Commands im `InteractionListener`/`DiscordCommandRegistrar` registrieren

**Checkpoint**: Alle MVP-Stories (US1–US6 / F1–F7) funktionsfähig.

---

## Phase 9: Polish & Cross-Cutting Concerns

**Purpose**: Optionale Quoten, Resilienz, weitere Tests, Doku

- [X] T045 [P] `sync/OddsClient.java` + `sync/OddsSyncService.java` + `scheduling/OddsSyncJob.java` (~6 h): Quoten holen, via Team-Namens-Mapping (R6) zuordnen, sonst verwerfen; optional, blockiert nichts (FR-003)
- [X] T046 [P] `sync/TeamNameMapping.java` + Properties: canonical ↔ Odds-API-Namen (R6)
- [X] T047 [P] Reconnect-/Persistenz-Resilienz: nach JDA-Reconnect getrackte `bot_messages` weiter editieren; Jobs idempotent (FR-031) — verifizieren/härten
- [X] T048 [P] `src/test/java/.../persistence/` Repository-Tests mit Testcontainers (PostgreSQL): Upserts, Leaderboard-Query, Reveal/Eval-Finder
- [X] T049 [P] `src/test/java/.../sync/` API-Client-Tests mit MockWebServer (football-data.org + Odds, inkl. Fehler/Rate-Limit)
- [X] T050 [P] `README.md` aus `quickstart.md` ableiten; Betriebshinweise (durchlaufender Prozess, Env-Vars, keine Secrets im Repo)
- [ ] T051 Quickstart-Smoke-Test gemäß `quickstart.md` durchführen

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: keine Abhängigkeiten
- **Foundational (Phase 2)**: nach Setup — BLOCKIERT alle Stories
- **User Stories (Phase 3–8)**: alle nach Foundational
  - P1: US1, US2, US3 (Kernschleife) zuerst
  - US3-Neubewertung: T019 setzt bei geändertem Endstand `evaluated=false`; T032 wertet regulär neu aus
  - P2: US4 (braucht ausgewertete Punkte aus US3), US5
  - P3: US6
- **Polish (Phase 9)**: nach den gewünschten Stories

### User Story Dependencies

- **US1 (P1)**: nur Foundational
- **US2 (P1)**: nur Foundational (unabhängig von US1)
- **US3 (P1)**: nur Foundational; Neubewertung über `evaluated=false`-Reset aus T019
- **US4 (P2)**: fachlich sinnvoll nach US3 (Punkte vorhanden); technisch nach Foundational
- **US5 (P2)**: nur Foundational
- **US6 (P3)**: nur Foundational

### Within Each User Story

- Pflicht-Tests (US2/US3) MÜSSEN zuerst geschrieben sein und fehlschlagen
- Modelle/Repos (Foundational) vor Services; Services vor Jobs/Commands
- Render/Embeds parallel zu Service-Logik möglich [P]

### Parallel Opportunities

- Setup: T002, T003, T004 parallel
- Foundational: Changesets T006–T008 parallel; Modelle T009/T010 parallel; T011/T012 parallel; Repos T014–T016 parallel
- US2: T025 (Test) und T027 (Embed) parallel zur Service-Arbeit
- US3: T029/T030 (Tests) parallel; T033 (Embed) parallel
- Nach Foundational können US1, US2, US3, US5 von verschiedenen Personen parallel bearbeitet werden
- Polish: T045–T050 weitgehend parallel

---

## Parallel Example: Foundational Schema & Models

```bash
# Changesets gemeinsam:
Task: "Changeset 001-create-matches.sql"
Task: "Changeset 002-create-tips.sql"
Task: "Changeset 003-create-bot-messages.sql"

# Modelle gemeinsam:
Task: "MatchStatus/Stage enums"
Task: "Match/Tip/BotMessage models"
```

---

## Implementation Strategy

### MVP First (Kernschleife)

1. Phase 1 Setup
2. Phase 2 Foundational (KRITISCH — blockiert alles)
3. US1 (Tipp), US2 (Reveal), US3 (Auswertung) — die spielentscheidende Schleife
4. **STOP & VALIDATE**: Tippen → Reveal → Auswertung end-to-end testen
5. Demo möglich

### Incremental Delivery

1. Setup + Foundational → Basis steht
2. US1 + US2 + US3 → spielfähiges Tippspiel (MVP-Kern)
3. US4 Rangliste → Wettbewerb sichtbar
4. US5 Live-Board → Alltags-Oberfläche
5. US6 on-demand Commands → Fallback/Komfort
6. Polish → Quoten, Resilienz, Tests, Doku

---

## Notes

- [P] = andere Datei, keine offene Abhängigkeit
- [Story]-Label = Zuordnung zur User Story (Traceability)
- Pflicht-Tests (US2/US3) gemäß Verfassung Prinzip III VOR Implementierung schreiben und fehlschlagen lassen
- Schema NUR über Liquibase-Changesets (Prinzip II) — kein `ddl-auto`
- Zeiten UTC speichern, Europe/Berlin anzeigen (Prinzip IV)
- Nach jedem Task oder logischer Gruppe committen
