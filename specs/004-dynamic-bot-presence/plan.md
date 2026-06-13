# Implementation Plan: Dynamische Bot-Presence (F9)

**Branch**: `004-dynamic-bot-presence` | **Date**: 2026-06-13 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/004-dynamic-bot-presence/spec.md`

## Summary

F9 ergänzt die bestehende Anwendung (F1–F8) um eine **zustandsgesteuerte
Discord-Presence** (Activity-Typ `watching`), ohne vorhandene Komponenten zu
brechen. Ein neuer `PresenceManager` (`@Component`) kapselt Zustandslogik und
Throttling. Er berechnet aus dem aktuellen Datenbestand genau **einen** von drei
priorisierten Zuständen — **LIVE > UPCOMING > IDLE** — und setzt die JDA-Presence
(`jda.getPresence().setActivity(Activity.watching(text))`) **nur**, wenn sich der
Anzeigetext gegenüber dem zuletzt gesetzten tatsächlich geändert hat (FR-008).

Die Zustandslogik wird in zwei reine, testbare Helfer ausgelagert:
`PresenceStateResolver` (Priorität + Auswahl bei mehreren Live-Spielen + Textbau)
und `PresenceThrottle` (garantierter Mindestabstand + Coalescing, sodass das
Discord-Limit **5 Änderungen / 20 s** nie überschritten wird, FR-009). Team-Kürzel
liefert ein `TeamCodeResolver` aus einer statisch gepflegten Mapping-Ressource.

**Verdrahtung (rein additiv):** Der bestehende `liveGoalPoll`-Job triggert nach
jedem Zyklus `presenceManager.recompute()` (LIVE-Eintritt/Stand/Austritt); der
bestehende `boardRefresh`-Job triggert dieselbe Neuberechnung (UPCOMING). Damit
der **Live-Stand frisch aus `matches` lesbar** ist (FR-003), persistiert die
bestehende Live-Poll-Quelle den frischen Stand+Status zusätzlich in `matches`
(vorhandene Spalten — **keine Schema-Änderung, kein Liquibase-Changeset**). Initial-
und Reconnect-Setzen erfolgt über JDA-Events (`onReady`/`onReconnected`/
`onSessionRecreate`, FR-011). **Keine DB-Schema-Änderung, keine neuen
Abhängigkeiten.**

## Technical Context

**Language/Version**: Java 21 (bestehend)

**Primary Dependencies**: Spring Boot 3.x (`@Scheduled`, `@EventListener`,
`JdbcClient`), JDA (`Presence`, `Activity.watching(...)`, `ListenerAdapter`) —
alle bereits vorhanden, **keine neuen Abhängigkeiten**.

**Storage**: PostgreSQL; **keine Schema-Änderung**. F9 liest über `MatchRepository`
(neue Read-Methode `findInPlay()`) und nutzt die **vorhandenen** Spalten
`home_score`/`away_score`/`status` — der Live-Stand wird im bestehenden
Live-Poll-Pfad in `matches` aktuell gehalten (additive Write-Methode, gleiche
Spalten, kein DDL).

**Testing**: JUnit 5 + AssertJ. Reine Unit-Tests für `PresenceStateResolver`
(Priorität LIVE>UPCOMING>IDLE, Auswahl „zuletzt verändert" + Tie-Breaker Anpfiff,
Textformat/Emoji), `PresenceThrottle` (nie >5/20 s, Coalescing „letzter Zustand
gewinnt", Mindestabstand via Test-`Clock`) und `TeamCodeResolver` (Mapping +
Fallback). `PresenceManager` mit gemocktem `JDA`/Repository: `setActivity` nur bei
echter Textänderung (FR-008).

**Target Platform**: Bestehender langlaufender Bot-Prozess; Trigger laufen im
vorhandenen Scheduler-Pool, der verzögerte Throttle-Flush in einem dedizierten
Single-Thread-Executor des `PresenceManager`.

**Project Type**: Single-Module-Backend (bestehend) — additive Erweiterung.

**Performance/Constraints**: Presence-Updates strikt gedrosselt auf einen
konfigurierbaren Mindestabstand (Default **5000 ms** ⇒ ≤4 Updates/20 s, sicher
unter dem 5/20-s-Limit). **Keine** zusätzlichen externen API-Aufrufe (F9 liest nur
DB; der Live-Poll nutzt den bereits getätigten football-data.org-Abruf). Logik
rechnet in UTC (Prinzip IV); der Presence-Text enthält keine Uhrzeiten.

**Scale/Scope**: wenige gleichzeitig laufende Spiele (Endrunde: 2 parallel); nur F9.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Prinzip | Status |
|---|---|
| I. Technologie-Stack (Java 21/Spring/Postgres) | ✅ keine neuen Stack-Elemente, keine neuen Dependencies |
| II. Liquibase-only | ✅ **keine Schema-Änderung** — F9 nutzt vorhandene Spalten; kein Changeset, kein `ddl-auto` |
| III. Test-First Kernlogik (Punktewertung/Reveal-Timing) | ✅ F9 berührt diese nicht. Resolver/Throttle sind reine, entkoppelte Logik und werden **freiwillig test-first** entwickelt, fallen aber nicht unter das III-Mandat |
| IV. Zeit UTC↔Berlin | ✅ Zustands-/Throttle-Logik rechnet in UTC (`Clock`/`Instant`); Presence-Text zeigt keine Zeiten |
| V. JDA dauerhafte Gateway-Verbindung | ✅ Presence wird über die **bestehende** JDA-Verbindung gesetzt; Initial-/Reconnect-Setzen ist **ereignisgetrieben** (JDA `onReady`/`onReconnected`/`onSessionRecreate`), kein neuer Polling-Timer — reconnect-fest (FR-011) |

**Ergebnis (Initial & Post-Design)**: PASS — keine Verstöße, keine Complexity-Einträge.

## Project Structure

### Documentation (this feature)

```text
specs/004-dynamic-bot-presence/
├── plan.md              # Diese Datei
├── research.md          # Phase 0
├── data-model.md        # Phase 1 (In-Memory-Modell; kein DB-Schema)
├── contracts/
│   └── presence-manager.md  # Komponenten-Kontrakte (Resolver/Throttle/Manager)
├── quickstart.md        # Phase 1
├── checklists/
│   └── requirements.md  # Spec-Quality-Checkliste
└── tasks.md             # /speckit-tasks (separat)
```

### Source Code (additive zur bestehenden Struktur)

```text
src/main/java/com/example/wmtippspiel/
├── presence/                       # NEU – F9-Kern
│   ├── PresenceManager.java        # @Component: hält letzten Text, recompute(), JDA-Listener (ready/reconnect), Throttle-Orchestrierung
│   ├── PresenceState.java          # Record: Typ (LIVE|UPCOMING|IDLE) + Anzeigetext (rein)
│   ├── PresenceStateResolver.java  # reine Logik: Priorität, „zuletzt verändert"-Auswahl + Tie-Breaker, Textbau
│   ├── PresenceThrottle.java       # reine Logik (Clock-basiert): Mindestabstand + Coalescing, Garantie ≤5/20 s
│   └── TeamCodeResolver.java       # Teamname → FIFA-Kürzel (Ressource), Fallback gekürzter Klartext
├── live/
│   └── ScoreDiffGoalEventSource.java  # ERWEITERT – persistiert frischen Live-Stand+Status in matches (vorhandene Spalten)
├── persistence/
│   └── MatchRepository.java        # ERWEITERT – findInPlay() (read) + updateLiveScore(id,h,a,status) (write, gleiche Spalten)
└── scheduling/
    ├── LiveGoalPollJob.java        # ERWEITERT – nach Goal-Loop: presenceManager.recompute()
    └── BoardRefreshJob.java        # ERWEITERT – nach boardService.refresh(): presenceManager.recompute()

src/main/resources/
└── presence/team-codes.properties # NEU – Teamname=KÜRZEL (analog manuellem TV-Mapping)

src/test/java/com/example/wmtippspiel/
└── presence/
    ├── PresenceStateResolverTest.java  # NEU (test-first) – Priorität, Multi-Live, Textformat
    ├── PresenceThrottleTest.java       # NEU (test-first) – 5/20s-Garantie, Coalescing
    ├── TeamCodeResolverTest.java       # NEU – Mapping/Fallback
    └── PresenceManagerTest.java        # NEU – setActivity nur bei Änderung (gemocktes JDA)
```

**Structure Decision**: Rein additiv. Neue Klassen liegen isoliert im neuen Paket
`presence`. Bestehende Pfade werden an genau **vier** Stellen erweitert, ohne
Signatur-/Verhaltensänderung vorhandener Methoden: zwei Job-Trigger
(`LiveGoalPollJob`, `BoardRefreshJob` rufen zusätzlich `recompute()`), eine
additive Persistenz im Live-Poll (`ScoreDiffGoalEventSource` schreibt frischen
Live-Stand) und zwei neue `MatchRepository`-Methoden (`findInPlay` lesend,
`updateLiveScore` schreibend auf vorhandenen Spalten). Der `PresenceManager`
bekommt das **bereits gebaute** `JDA`-Bean injiziert und registriert sich per
`@PostConstruct` selbst als JDA-Listener (`jda.addEventListener(this)`) — dadurch
**kein** Build-Zeit-Zyklus mit dem `JDA`-Bean (das keine Abhängigkeit auf
`PresenceManager` hat).

## Complexity Tracking

> Keine Constitution-Verstöße — Tabelle entfällt.
