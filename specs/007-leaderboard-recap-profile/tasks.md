---

description: "Task list for Feature 007 — Live-Leaderboard-Board, Spieltags-Rückblick & /profil"
---

# Tasks: Live-Leaderboard-Board, Spieltags-Rückblick & /profil

**Input**: Design documents from `/specs/007-leaderboard-recap-profile/`

**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/, quickstart.md

**Tests**: Included — der Plan benennt konkrete Testklassen für die neue Logik (Rang-Diff, Matchday-Completion, Profil-Aggregation). Scoring/Reveal werden nicht berührt, daher greift der Pflicht-Gate (Verfassung III) nicht, die Tests sind aber Teil der Lieferung.

**Organization**: Aufgaben nach User Story gruppiert (US1=F11, US2=F12, US3=F13), jede unabhängig lieferbar/testbar.

**Package base**: `src/main/java/com/example/wmtippspiel/` (abgekürzt `…/`), Tests unter `src/test/java/com/example/wmtippspiel/`.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: parallelisierbar (andere Datei, keine offene Abhängigkeit)
- **[Story]**: US1 / US2 / US3 (nur in Story-Phasen)

---

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: Regressions-Baseline vor jeder Änderung sichern.

- [X] T001 Baseline herstellen: `mvn -q clean verify` ausführen und bestätigen, dass die bestehende Test-Suite grün ist (Ausgangspunkt für Regressionsschutz).

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: Geteilte Board-Mechanik extrahieren, die F7 (`board:main`) und F11 (`board:leaderboard`) gemeinsam nutzen. Blockiert US1.

**⚠️ CRITICAL**: Muss abgeschlossen sein, bevor US1 beginnt; darf F7-Verhalten nicht ändern.

- [X] T002 [P] `TrackedBoardPublisher` neu erstellen mit `editOrPost(channel, key, embed)` (inkl. 404-/`UNKNOWN_MESSAGE`-Recovery → Neu-Post + `bot_messages`-Update) und `cleanupOrphans(channel, keepKey)` (nur eigene verwaiste Bot-Nachrichten), extrahiert aus der bestehenden `BoardService`-Logik, in `…/discord/board/TrackedBoardPublisher.java`
- [X] T003 `BoardService` (Slot `board:main`, F7) auf Delegation an `TrackedBoardPublisher` umstellen, ohne Verhaltensänderung, in `…/discord/board/BoardService.java` (depends on T002)
- [X] T004 Regression: bestehende F7-Board-/Integrationstests nach der Extraktion grün halten (`mvn -q test`); `board:main` verhält sich identisch (depends on T003)

**Checkpoint**: Geteilte Board-Mechanik verfügbar und F7 unverändert lauffähig.

---

## Phase 3: User Story 1 - Live-Leaderboard-Board (Priority: P1) 🎯 MVP

**Goal**: Selbst-aktualisierendes Ranglisten-Board (Slot `board:leaderboard`) im eigenen read-only Channel, getriggert pro Auswertungs-Batch, mit Rang-Pfeilen gegen den vorigen Batch.

**Independent Test**: Nach einer Spielauswertung zeigt der Ranglisten-Channel ein editiertes (nicht neu gepostetes) Board mit Top-N, exakten Treffern und korrekten ↑/↓/–/NEU-Pfeilen; nach Neustart bleiben die Pfeile korrekt.

### Tests for User Story 1 ⚠️ (zuerst schreiben, müssen fehlschlagen)

- [X] T005 [P] [US1] Unit-Test `LeaderboardRankDiffTest` für Pfeil-Regeln (`NEU` bei `previousRank==null`, `↑n`, `↓n`, `–`, Standard-Competition-Gleichstand, Vergleichsbasis nach Neustart) in `src/test/java/com/example/wmtippspiel/leaderboard/LeaderboardRankDiffTest.java`
- [X] T006 [P] [US1] `PersistenceIntegrationTest` um `LeaderboardSnapshotRepository` erweitern (`findAllRanks`/`replaceAll`, vollständiges Ersetzen, leerer Erst-Snapshot) in `src/test/java/com/example/wmtippspiel/persistence/PersistenceIntegrationTest.java`

### Implementation for User Story 1

- [X] T007 [P] [US1] Liquibase-Changeset `011-create-leaderboard-snapshot.sql` (`user_id` TEXT PK, `rank` INT NOT NULL, `captured_at` TIMESTAMPTZ NOT NULL; mit `--rollback`) in `src/main/resources/db/changelog/changesets/011-create-leaderboard-snapshot.sql`
- [X] T008 [P] [US1] Config ergänzen: `app.discord.leaderboard-channel-id` und `app.leaderboard.top-n` (Default 15) in `src/main/resources/application.yml` sowie `…/config/AppProperties.java` (Discord um `leaderboardChannelId`, neuer `Leaderboard(topN)`-Record)
- [X] T009 [P] [US1] Wertobjekt `RankDelta` (userId, currentRank, previousRank, `arrow()`-Berechnung) in `…/leaderboard/RankDelta.java`
- [X] T010 [P] [US1] `LeaderboardSnapshotRepository` mit `findAllRanks(): Map<String,Integer>` und `replaceAll(List<SnapshotRow>)` (DELETE + Batch-INSERT via JdbcClient) in `…/persistence/LeaderboardSnapshotRepository.java`
- [X] T011 [US1] `LeaderboardBoardEmbed.build(List<LeaderboardEntry> topN, Map<String,RankDelta> deltas)` über `EmbedStyle.base("Rangliste")`, kompakte Description (eine Zeile/User), Footer-Zeitstempel, defensive Truncation (~4000 Zeichen) in `…/discord/render/LeaderboardBoardEmbed.java`
- [X] T012 [US1] `LeaderboardBoardService.refreshAfterEvaluation()` implementieren: `TipRepository.leaderboard()` → Diff gegen `findAllRanks()` → `LeaderboardBoardEmbed` → `TrackedBoardPublisher.editOrPost(channel, "board:leaderboard", embed)` → `replaceAll(currentRanks aller User)`; zusätzlich Initial-Build + `cleanupOrphans` beim `ApplicationReadyEvent` in `…/leaderboard/LeaderboardBoardService.java` (depends on T009, T010, T011, T002)
- [X] T013 [US1] Auswertungs-Batch-Ergebnis nutzbar machen und `EvaluateJob` so erweitern, dass nach dem Batch bei ≥1 ausgewerteten Spiel `LeaderboardBoardService.refreshAfterEvaluation()` **einmal** aufgerufen wird, in `…/scheduling/EvaluateJob.java` (ggf. Rückgabewert von `EvaluationService.evaluateFinishedMatches()` verwenden) (depends on T012)

**Checkpoint**: F11 vollständig, unabhängig testbar (Board editiert sich pro Batch, Recovery/Cleanup/Neustart korrekt).

---

## Phase 4: User Story 2 - Spieltags-Rückblick (Priority: P2)

**Goal**: Nach Abschluss aller Spiele eines `matchday` (Recap-Key) genau einmal eine Zusammenfassung im Announce-Channel.

**Independent Test**: Alle Spiele eines matchday auf FINISHED+evaluated bringen → genau ein Rückblick; erneuter Job-Lauf/Neustart → kein zweiter; unvollständiger Spieltag → kein Post.

### Tests for User Story 2 ⚠️ (zuerst schreiben, müssen fehlschlagen)

- [ ] T014 [P] [US2] Unit-Test `MatchdayRecapServiceTest` (Completion-Erkennung über Recap-Key, `tryClaim`-Idempotenz via Mock, Leerfall „keine Tipps", Re-Evaluation löst keinen zweiten Post aus) in `src/test/java/com/example/wmtippspiel/recap/MatchdayRecapServiceTest.java`
- [ ] T015 [P] [US2] `PersistenceIntegrationTest` um `MatchdayRecapRepository.tryClaim` (ON CONFLICT DO NOTHING → zweiter Aufruf false) und `matches.matchday` (Lesen/Schreiben) erweitern in `src/test/java/com/example/wmtippspiel/persistence/PersistenceIntegrationTest.java`

### Implementation for User Story 2

- [ ] T016 [P] [US2] Liquibase-Changeset `010-add-matches-matchday.sql` (`ALTER TABLE matches ADD COLUMN matchday INT;` mit `--rollback`) in `src/main/resources/db/changelog/changesets/010-add-matches-matchday.sql`
- [ ] T017 [P] [US2] Liquibase-Changeset `012-create-matchday-recap.sql` (`recap_key` TEXT PK, `posted_at` TIMESTAMPTZ NOT NULL; mit `--rollback`) in `src/main/resources/db/changelog/changesets/012-create-matchday-recap.sql`
- [ ] T018 [US2] Includes für `010-add-matches-matchday.sql` und `012-create-matchday-recap.sql` in `src/main/resources/db/changelog/db.changelog-master.yaml` registrieren (in numerischer Reihenfolge: 010 vor 011, dann 012) (depends on T016, T017; T007 für 011)
- [ ] T019 [US2] `matchday` durchreichen: Record-Komponente in `…/domain/model/Match.java`, Row-Mapper + Upsert-Spalte in `…/persistence/MatchRepository.java`, sowie Übernahme aus football-data.org in der bestehenden Match-Sync-Mapping-Stelle (`…/sync/…`)
- [ ] T020 [P] [US2] `MatchRepository.findByRecapKey(recapKey)` bzw. `findByMatchday(...)` für die Vollständigkeitsprüfung (alle FINISHED+evaluated) in `…/persistence/MatchRepository.java` (depends on T019)
- [ ] T021 [P] [US2] `TipRepository.matchdayLeaderboard(recapKey)` (Top-Punktesammler + Nuller nur aus Spielen dieses Recap-Keys) in `…/persistence/TipRepository.java`
- [ ] T022 [P] [US2] `MatchdayRecapRepository.tryClaim(recapKey): boolean` (`INSERT … ON CONFLICT (recap_key) DO NOTHING`, rowsAffected==1) in `…/persistence/MatchdayRecapRepository.java`
- [ ] T023 [US2] `MatchdayRecapEmbed.build(...)` über `EmbedStyle.base("Spieltags-Rückblick · …")`: Top-Punktesammler, bester Einzeltipp (primär exakter Treffer; sonst höchste Punktzahl; Tie-Break unwahrscheinlichstes Ergebnis per Quote), Nuller, in `…/discord/render/MatchdayRecapEmbed.java`
- [ ] T024 [US2] `MatchdayRecapService.postCompletedRecaps()`: Recap-Keys der im Batch ausgewerteten Spiele bilden (`md:<n>` bzw. Fallback `stage:<STAGE>`), je Key Vollständigkeit prüfen, `tryClaim`, bei Erfolg Embed in Announce-Channel posten, in `…/recap/MatchdayRecapService.java` (depends on T020, T021, T022, T023)
- [ ] T025 [US2] `EvaluateJob` nach dem Batch zusätzlich `MatchdayRecapService.postCompletedRecaps()` aufrufen lassen in `…/scheduling/EvaluateJob.java` (depends on T024)

**Checkpoint**: F12 vollständig, idempotent, unabhängig von US1 testbar.

---

## Phase 5: User Story 3 - /profil [user] (Priority: P3)

**Goal**: Öffentlicher Slash-Command mit persönlicher Bilanz (Rang, Punkte, exakte Treffer, Trefferquote, bester/schlechtester Tipp, 4/3/2/0-Verteilung).

**Independent Test**: `/profil` zeigt eigene Bilanz öffentlich; `/profil @user` die des Users; User ohne Tipps → gültige Leer-Bilanz ohne Fehler.

### Tests for User Story 3 ⚠️ (zuerst schreiben, müssen fehlschlagen)

- [ ] T026 [P] [US3] Unit-Test `ProfilAggregationTest` (Trefferquote inkl. 0 ausgewerteter Tipps → „—", 4/3/2/0-Verteilung, bester/schlechtester Tipp inkl. Tie-Break) in `src/test/java/com/example/wmtippspiel/discord/command/ProfilAggregationTest.java`

### Implementation for User Story 3

- [ ] T027 [P] [US3] `TipRepository.findEvaluatedTipsByUser(userId)` — Zeilen je ausgewertetem Tipp (Heim/Gast, Ergebnis, getippter Stand, `points`, Quoten) in `…/persistence/TipRepository.java`
- [ ] T028 [P] [US3] `UserProfile`-Aggregation (Rang aus `leaderboard()`, Verteilung, Trefferquote, bester/schlechtester Tipp) + `ProfilEmbed.build(profile)` über `EmbedStyle.base("Profil · …")` in `…/discord/render/ProfilEmbed.java`
- [ ] T029 [US3] `ProfilCommand` (`NAME="profil"`, optionale `USER`-Option): Ziel auflösen (Arg oder Aufrufer), `deferReply()` **öffentlich**, aggregieren, `getHook().editOriginalEmbeds(...)`, Leerfall sauber, in `…/discord/command/ProfilCommand.java` (depends on T027, T028)
- [ ] T030 [US3] `/profil` registrieren in `…/discord/DiscordCommandRegistrar.java` (mit optionaler User-Option) und Dispatch-Case in `…/discord/InteractionListener.java` (depends on T029)

**Checkpoint**: Alle drei User Stories unabhängig funktionsfähig.

---

## Phase 6: Polish & Cross-Cutting Concerns

- [ ] T031 [P] Embed-Limit-Härtung für F11 prüfen: Unit-Assertion, dass `LeaderboardBoardEmbed` bei vielen/langen Namen unter den Discord-Grenzen bleibt (defensive Truncation greift)
- [ ] T032 [P] Konsistenzcheck: Gesamtpunkte/exakte Treffer identisch über `/rangliste` (F6), Board (F11) und `/profil` (F13) für denselben Stand (FR-025, SC-008)
- [ ] T033 Volle Regression: `mvn -q clean verify` — alle Tests grün, insbesondere `ScoringServiceTest`, `EvaluationServiceTest`, `RecalculationServiceTest` unverändert (Scoring unberührt)
- [ ] T034 quickstart.md-Verifikation manuell durchführen (F11/F12/F13 Schritte)

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: keine Abhängigkeit.
- **Foundational (Phase 2)**: nach Setup; **blockiert US1** (geteilte Board-Mechanik).
- **US1 (Phase 3)**: nach Phase 2.
- **US2 (Phase 4)**: nach Setup; unabhängig von US1 (berührt aber `EvaluateJob` und `db.changelog-master.yaml` — siehe Konflikt-Hinweis).
- **US3 (Phase 5)**: nach Setup; unabhängig von US1/US2.
- **Polish (Phase 6)**: nach den gewünschten Stories.

### Story-übergreifende Datei-Konflikte (nicht parallel editieren)

- `…/scheduling/EvaluateJob.java`: T013 (US1) und T025 (US2) ändern dieselbe Datei → sequenziell.
- `src/main/resources/db/changelog/db.changelog-master.yaml`: T018 (US2) ergänzt Includes, die T007 (US1, 011) berücksichtigen → 011 vor 010/012 vermeiden, in numerischer Reihenfolge einsortieren.
- `…/persistence/TipRepository.java`: T021 (US2) und T027 (US3) → sequenziell oder sorgfältig getrennte Methoden.
- `…/persistence/MatchRepository.java`: T019 und T020 (beide US2) → T020 nach T019.

### Within Each User Story

- Tests zuerst (US1: T005/T006, US2: T014/T015, US3: T026) und fehlschlagen lassen.
- Repositories/Models vor Services; Services vor Job-/Command-Verdrahtung.

### Parallel Opportunities

- **Phase 2**: T002 [P] startbar; T003/T004 sequenziell danach.
- **US1**: T005, T006, T007, T008, T009, T010 sind [P] (verschiedene Dateien); T011→T012→T013 sequenziell.
- **US2**: T014, T015, T016, T017 [P]; T021, T022 [P]; T019→T020; T023→T024→T025.
- **US3**: T026, T027, T028 [P]; T029→T030.
- Nach Phase 2 können US1, US2, US3 von verschiedenen Personen parallel bearbeitet werden — bei den oben genannten geteilten Dateien koordinieren.

---

## Parallel Example: User Story 1

```bash
# Tests zuerst (parallel):
Task: "LeaderboardRankDiffTest in src/test/java/com/example/wmtippspiel/leaderboard/LeaderboardRankDiffTest.java"
Task: "PersistenceIntegrationTest-Erweiterung für LeaderboardSnapshotRepository"

# Danach parallele Bausteine:
Task: "Changeset 011-create-leaderboard-snapshot.sql"
Task: "Config app.leaderboard.top-n / leaderboard-channel-id in application.yml + AppProperties"
Task: "RankDelta in …/leaderboard/RankDelta.java"
Task: "LeaderboardSnapshotRepository in …/persistence/LeaderboardSnapshotRepository.java"
```

---

## Implementation Strategy

### MVP First (User Story 1)

1. Phase 1 (Setup) → 2. Phase 2 (Foundational, F7-Refactor) → 3. Phase 3 (US1) → **STOP & VALIDATE** (Board editiert sich pro Batch, Recovery/Neustart korrekt) → ggf. deployen.

### Incremental Delivery

1. Setup + Foundational → 2. US1 (F11, MVP) → 3. US2 (F12) → 4. US3 (F13). Jede Story liefert eigenständigen Mehrwert, ohne vorige zu brechen.

### Parallel Team Strategy

Nach Phase 2: Dev A → US1, Dev B → US2, Dev C → US3; geteilte Dateien (`EvaluateJob.java`, `db.changelog-master.yaml`, `TipRepository.java`, `MatchRepository.java`) koordinieren.

---

## Notes

- [P] = andere Datei, keine offene Abhängigkeit.
- `ScoringService` bleibt unberührt — F11–F13 lesen nur (FR-026).
- Alle Schema-Änderungen als additive Liquibase-Changesets (Verfassung II); keine Edits an angewandten Changesets.
- Neue Zeitstempel in UTC speichern, Anzeige Europe/Berlin (Verfassung IV).
- Nach jedem Task/logischer Gruppe committen; an Checkpoints Story unabhängig validieren.
