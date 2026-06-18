# Implementation Plan: CHECK24-Punkteschema (vierstufige Staffelung)

**Branch**: `006-check24-scoring` | **Date**: 2026-06-18 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/006-check24-scoring/spec.md`

## Summary

Das bisherige 3/1/0-Punkteschema der Auto-Auswertung (F5) wird durch das vierstufige
CHECK24-Schema **4/3/2/0** ersetzt. Die Punktevergabe konzentriert sich auf eine
einzige Funktion `ScoringService.points(...)` (Reihenfolge: exakt → vorzeichenbehaftete
Tordifferenz → Tendenz → 0), die sowohl von der laufenden Auswertung als auch von der
rückwirkenden Neuberechnung genutzt wird. Die Leaderboard-Statistik „exakte Treffer"
wird vom Punktwert entkoppelt und stattdessen live per Score-Vergleich
(`tips.home_score = matches.home_score AND tips.away_score = matches.away_score`)
über einen JOIN auf `matches` berechnet. Ein idempotenter Startup-Runner
(`RecalculationService` + `ApplicationRunner`) rechnet alle bereits ausgewerteten Tipps
einmalig auf das neue Schema um, sichert die alten Stände per Log und überschreibt
`tips.points` nur bei tatsächlicher Abweichung (gefahrlos mehrfach ausführbar).

**Keine Schema-Änderung**: `tips.points` (INT) trägt 0–4; die Exakt-Statistik benötigt
kein neues Feld, da sie live aus dem Vergleich berechnet wird. Daher **kein neues
Liquibase-Changeset** und **keine neuen Abhängigkeiten**.

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**: Spring Boot 3.x, JDA (Discord Gateway), Spring `JdbcClient`, Liquibase, JUnit 5 / AssertJ / Mockito

**Storage**: PostgreSQL (Tabellen `tips`, `matches` — bestehende Spalten, unverändert)

**Testing**: JUnit 5 (Jupiter), AssertJ, Mockito; Scoring test-first gemäß Verfassung Prinzip III

**Target Platform**: Linux-Server (Docker-Compose), reiner Discord-Bot (kein Web-Layer)

**Project Type**: Single project (Maven), Backend-Service

**Performance Goals**: Auswertung/Neuberechnung im Bereich von ~104 Spielen × wenige Tipps — Millisekunden-Bereich; kein Durchsatzdruck

**Constraints**: Keine Schema-Änderung; eine einzige Punkteberechnungs-Quelle; rückwirkender Lauf idempotent und mehrfach gefahrlos; alte Stände vor Überschreiben gesichert (Log)

**Scale/Scope**: Kleine Community; 104 Spiele, wenige Dutzend Tipps je Spiel

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Prinzip | Status | Begründung |
|---------|--------|------------|
| I. Festgelegter Stack (Java 21 / Spring Boot 3.x / PostgreSQL) | ✅ PASS | Reine Änderung im bestehenden Stack; keine neuen Sprachen/Frameworks/DB. |
| II. Schema-Änderungen nur via Liquibase | ✅ PASS | **Keine** Schema-Änderung nötig (INT trägt 0–4; Exakt-Statistik live berechnet). Kein neues Changeset; keine manuelle DDL. |
| III. Test-First für Kernlogik (NON-NEGOTIABLE) | ✅ PASS | Punktewertung ist Kernlogik: Tests für `points()` (alle 4 Stufen + Remis-/Vorzeichen-Sonderfälle) werden zuerst geschrieben, müssen rot sein, dann grün. Auch Neuberechnung/Evaluation testabgedeckt. |
| IV. Zeit UTC speichern / Europe/Berlin anzeigen | ✅ PASS | Feature berührt keine Zeitlogik. |
| V. Discord über JDA, ereignisgetrieben | ✅ PASS | Kein neues Polling; rückwirkender Lauf ist ein einmaliger Startup-Vorgang, keine Gateway-Änderung. |

**Ergebnis (vor Phase 0):** PASS — keine Verstöße, Complexity-Tracking leer.

**Ergebnis (nach Phase 1 Re-Check):** PASS — Design führt keine Schema-Änderung, keine neue Abhängigkeit und keine zweite Punkteberechnungsstelle ein. Siehe [data-model.md](./data-model.md) und [contracts/](./contracts/).

## Project Structure

### Documentation (this feature)

```text
specs/006-check24-scoring/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/           # Phase 1 output
│   ├── scoring.md       # scoreTip-Kontrakt (Stufen, Reihenfolge, Beispiele)
│   ├── leaderboard.md   # Exakt-Treffer-Query-Kontrakt
│   └── recalculation.md # Startup-Neuberechnungs-Kontrakt
└── tasks.md             # Phase 2 (separat via /speckit-tasks)
```

### Source Code (repository root)

```text
src/main/java/com/example/wmtippspiel/
├── domain/scoring/
│   └── ScoringService.java          # GEÄNDERT: points() → 4/3/2/0 (einzige Berechnungsstelle)
├── domain/model/
│   ├── Tip.java                     # unverändert (homeScore, awayScore, points)
│   └── Match.java                   # unverändert (homeScore, awayScore, evaluated)
├── evaluation/
│   ├── EvaluationService.java       # unverändert in Struktur (nutzt ScoringService weiter)
│   └── RecalculationService.java    # NEU: idempotente Neuberechnung über alle evaluierten Spiele
├── recalc/  (oder evaluation/)
│   └── ScoreRecalculationRunner.java# NEU: ApplicationRunner, ruft RecalculationService beim Start
├── persistence/
│   ├── TipRepository.java           # GEÄNDERT: leaderboard()-SQL (exact_hits via JOIN-Vergleich)
│   ├── MatchRepository.java         # GEÄNDERT: findEvaluated() (read) für Neuberechnung
│   └── LeaderboardEntry.java        # unverändert (userId, username, totalPoints, tipCount, exactHits)
└── discord/commands/
    └── RanglisteCommand.java        # unverändert (nutzt LeaderboardEntry weiter)

src/main/resources/
└── application.yml                  # NEU: app.scoring.recalc-on-startup (default true)

src/test/java/com/example/wmtippspiel/
├── domain/scoring/ScoringServiceTest.java     # GEÄNDERT: CSV → 4/3/2/0 + Remis-/Vorzeichen-Fälle
├── evaluation/EvaluationServiceTest.java       # GEÄNDERT: erwartete Punkte 4/3/2/0
└── evaluation/RecalculationServiceTest.java    # NEU: Update bei Abweichung, Idempotenz, evaluated-Guard
```

**Structure Decision**: Single Maven project, bestehendes Paketlayout
(`com.example.wmtippspiel`). Punkteberechnung bleibt in `domain/scoring`; die
Neuberechnung wird als `RecalculationService` neben `EvaluationService` im
`evaluation`-Paket angesiedelt und vom `ScoreRecalculationRunner` (ApplicationRunner)
beim Start ausgelöst — additiv, ohne bestehende Job-/Command-Struktur zu verändern.

## Complexity Tracking

> Keine Verstöße gegen die Verfassung — Tabelle bleibt leer.

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| — | — | — |
