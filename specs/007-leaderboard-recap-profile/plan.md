# Implementation Plan: Live-Leaderboard-Board, Spieltags-Rückblick & /profil

**Branch**: `007-leaderboard-recap-profile` | **Date**: 2026-06-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/007-leaderboard-recap-profile/spec.md`

## Summary

Drei rein lesende Präsentations-Features auf Basis der vorhandenen `tips`/`matches`-Daten und des CHECK24-Schemas (4/3/2/0). **Keine** Änderung an der Punkteberechnung: `ScoringService.points(...)` bleibt die einzige Wertungsstelle; F11–F13 konsumieren nur `tips.points` und ausgewertete `matches`.

- **F11 — Live-Leaderboard-Board**: Ein zweites, selbst-aktualisierendes Board (Slot `board:leaderboard`) in einem **eigenen** read-only Channel, nach dem F7-Muster (Edit-statt-Post, 404-Recovery, Startup-Cleanup). Getriggert pro **Auswertungs-Batch** (ein `evaluateJob`-Lauf). Zeigt Top-N mit Rang, Name, Punkten, exakten Treffern und Rang-Pfeil (↑/↓/–/NEU) gegen den Stand vor dem Batch. Vergleichsbasis persistent in neuer Tabelle `leaderboard_snapshot`.
- **F12 — Spieltags-Rückblick**: Erkennt, wenn alle Spiele eines `matchday` FINISHED+evaluated sind, und postet **genau einmal** eine Zusammenfassung in den Announce-Channel. Idempotenz über neue Tabelle `matchday_recap`. Erfordert additive Persistenz des `matchday`-Felds an `matches`.
- **F13 — `/profil [user]`**: Slash-Command, der die Bilanz eines Users aggregiert (Rang, Punkte, exakte Treffer, Trefferquote, bester/schlechtester Tipp, 4/3/2/0-Verteilung). Antwort **öffentlich**. Keine Schema-Änderung.

Technischer Ansatz: maximale Wiederverwendung bestehender Komponenten (`EmbedStyle`, `BotMessageRepository`, `TipRepository.leaderboard()`, `EvaluationService`-Batch-Ergebnis, `InteractionListener`/`DiscordCommandRegistrar`). Die F7-Board-Upsert-/Recover-/Cleanup-Logik wird in einen wiederverwendbaren Helfer extrahiert, den beide Boards (`board:main`, `board:leaderboard`) nutzen.

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**: Spring Boot 3.3.x, JDA 5.0.0 (Discord Gateway), Spring `JdbcClient`, Liquibase

**Storage**: PostgreSQL. Neue Tabellen `leaderboard_snapshot`, `matchday_recap`; additive Spalte `matches.matchday` — alle via Liquibase-Changesets.

**Testing**: JUnit 5 + Mockito + AssertJ (Unit); `@SpringBootTest` + Testcontainers PostgreSQL (Persistence-Integration). Build: Maven.

**Target Platform**: Linux-Server (dauerhaft laufender Prozess mit JDA-Gateway-Verbindung)

**Project Type**: Single project (Spring-Boot-Service, Discord-Bot)

**Performance Goals**: Board-Edit innerhalb desselben Auswertungszyklus; `/profil`-Antwort innerhalb des JDA-3s-Interaktionsfensters (ggf. `deferReply`).

**Constraints**: Discord-Embed-Limits (≤6000 Zeichen, ≤4096 Beschreibung, ≤25 Felder) — Leaderboard-Liste defensiv abschneiden. football-data.org-Rate-Limit (10 Req/Min) — F12/F13 lösen **keinen** zusätzlichen API-Verkehr aus (rein DB-lesend). Zeit: UTC speichern, Europe/Berlin anzeigen.

**Scale/Scope**: Kleine Community (Dutzende User, 104 Spiele). Top-N Default 15.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Prinzip | Status | Begründung |
|---|---|---|
| I. Festgelegter Stack (Java 21 / Spring Boot 3.x / PostgreSQL) | ✅ Pass | Keine neuen Sprachen/Frameworks/DBs. |
| II. Schema-Änderungen nur via Liquibase | ✅ Pass | Drei additive Changesets (`leaderboard_snapshot`, `matchday_recap`, `matches.matchday`); keine manuelle DDL, kein `ddl-auto`-Schemawandel, keine Edits an angewandten Changesets. |
| III. Test-First für Kernlogik (Punktewertung & Reveal-Timing) | ✅ Pass | Kernlogik wird **nicht** berührt — `ScoringService` bleibt unverändert. Tests für neue Logik (Rang-Diff, Matchday-Completion, Profil-Aggregation) werden test-getrieben ergänzt (empfohlen), lösen aber nicht den Pflicht-Gate aus. |
| IV. UTC speichern, Europe/Berlin anzeigen | ✅ Pass | Neue Zeitstempel (`captured_at`, `posted_at`) als `TIMESTAMPTZ` (UTC); Anzeige über bestehende `EmbedStyle`-Footer-Logik in Europe/Berlin. |
| V. JDA mit dauerhafter Gateway-Verbindung | ✅ Pass | `/profil` und Board-Edits laufen über die bestehende Gateway-Verbindung; Recovery nach Reconnect analog F7. |

**Ergebnis: PASS** (Initial). Keine Einträge in Complexity Tracking nötig.

## Project Structure

### Documentation (this feature)

```text
specs/007-leaderboard-recap-profile/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── profil-command.md
│   ├── leaderboard-board.md
│   └── matchday-recap.md
└── tasks.md             # Phase 2 output (/speckit-tasks — NOT created here)
```

### Source Code (repository root)

```text
src/main/java/com/example/wmtippspiel/
├── domain/
│   ├── scoring/ScoringService.java             # UNVERÄNDERT (einzige Wertungsstelle)
│   └── model/Match.java                         # + matchday-Feld (Record-Komponente)
├── evaluation/
│   ├── EvaluationService.java                   # Batch-Ergebnis exponieren (welche Matches ausgewertet wurden)
│   └── EvaluationPublisher.java                 # bestehend (per-Match Announce) — unverändert
├── leaderboard/                                 # NEU (F11)
│   ├── LeaderboardBoardService.java             # refreshAfterEvaluation(): ranking → diff → edit → snapshot
│   └── RankDelta.java                           # Wertobjekt (userId, currentRank, previousRank, arrow)
├── recap/                                       # NEU (F12)
│   └── MatchdayRecapService.java                # completion-detection + idempotentes Posten
├── discord/
│   ├── DiscordCommandRegistrar.java             # + /profil registrieren
│   ├── InteractionListener.java                 # + /profil dispatchen
│   ├── command/ProfilCommand.java               # NEU (F13)
│   ├── board/
│   │   ├── BoardService.java                    # refactor: nutzt TrackedBoardPublisher
│   │   └── TrackedBoardPublisher.java           # NEU: editOrPost + 404-Recovery + Cleanup (extrahiert aus F7)
│   └── render/
│       ├── EmbedStyle.java                      # UNVERÄNDERT (wiederverwendet)
│       ├── LeaderboardBoardEmbed.java           # NEU (F11) — Top-N + Rang-Pfeile
│       ├── MatchdayRecapEmbed.java              # NEU (F12)
│       └── ProfilEmbed.java                     # NEU (F13)
├── persistence/
│   ├── BotMessageRepository.java                # UNVERÄNDERT (wiederverwendet: Slot board:leaderboard)
│   ├── TipRepository.java                       # + findEvaluatedTipsByUser(), + matchdayLeaderboard()
│   ├── MatchRepository.java                     # + findByMatchday(), matchday im Mapper/Upsert
│   ├── LeaderboardSnapshotRepository.java       # NEU (F11)
│   └── MatchdayRecapRepository.java             # NEU (F12)
├── scheduling/
│   └── EvaluateJob.java                         # nach Batch: LeaderboardBoardService + MatchdayRecapService anstoßen
└── sync/ (bestehende Match-Sync-Mapping-Stelle) # matchday aus football-data.org übernehmen

src/main/resources/
├── application.yml                              # + app.discord.leaderboard-channel-id, + app.leaderboard.top-n
└── db/changelog/
    ├── db.changelog-master.yaml                # + 3 includes
    └── changesets/
        ├── 010-add-matches-matchday.sql         # NEU
        ├── 011-create-leaderboard-snapshot.sql  # NEU
        └── 012-create-matchday-recap.sql        # NEU

src/test/java/com/example/wmtippspiel/
├── leaderboard/LeaderboardRankDiffTest.java     # NEU — Rang-Diff inkl. NEU/Restart
├── recap/MatchdayRecapServiceTest.java          # NEU — completion + Idempotenz
├── discord/command/ProfilAggregationTest.java   # NEU — Aggregation/Trefferquote/Verteilung
└── persistence/PersistenceIntegrationTest.java  # erweitert — neue Repos + matchday
```

**Structure Decision**: Bestehendes Single-Project-Layout (`com.example.wmtippspiel.*`). Neue, feature-eigene Pakete `leaderboard` und `recap`; F13 fügt sich in die vorhandene `discord/command`-Struktur ein. Die F7-Board-Mechanik wird einmal in `TrackedBoardPublisher` extrahiert und von `board:main` (F7) + `board:leaderboard` (F11) geteilt — die einzige Änderung an bestehendem Verhalten, abgesichert durch bestehende Board-/Integrationstests.

## Complexity Tracking

> Keine Verfassungsverstöße — Tabelle entfällt.
