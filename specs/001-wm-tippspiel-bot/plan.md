# Implementation Plan: WM 2026 Tippspiel Discord-Bot

**Branch**: `001-wm-tippspiel-bot` | **Date**: 2026-06-11 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/001-wm-tippspiel-bot/spec.md`

**Note**: This template is filled in by the `/speckit-plan` command. See `.specify/templates/plan-template.md` for the execution workflow.

## Summary

Ein dauerhaft per Discord-Gateway verbundener Spring-Boot-Bot, der den WM-2026-
Spielplan aus externen APIs synchronisiert, ephemerale Tippabgabe per
Slash-Command verwaltet, Tipps automatisch bei Anpfiff offenlegt, beendete
Spiele nach dem 3/1/0-Schema auswertet (inkl. automatischer Neubewertung bei
Ergebniskorrektur), eine Rangliste liefert und ein selbst-aktualisierendes
Live-Board mit interaktiven Filtern in einem read-only Kanal pflegt.

Technischer Ansatz: Maven-Single-Module-Service, Spring Boot 3.x auf Java 21,
JDA als dauerhafter Gateway-Listener für Slash-Commands und Component-
Interaktionen, Spring `@Scheduled`-Jobs für Sync/Reveal/Eval/Board-Refresh,
`JdbcClient`-basierte Repositories über PostgreSQL, Schema ausschließlich via
Liquibase-Changesets (ein Changeset-File pro Tabelle), `WebClient` für
football-data.org und The Odds API. Zeit durchgängig in UTC (`Instant` /
`TIMESTAMPTZ`), Umrechnung nach `Europe/Berlin` nur an der Anzeigegrenze.

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**: Spring Boot 3.x (`WebClient`, Scheduling, JDBC), JDA
(Java Discord API, dauerhafte Gateway-Verbindung), Liquibase, PostgreSQL
JDBC-Treiber, Spring JDBC (`JdbcClient`)

**Storage**: PostgreSQL; Schemaverwaltung ausschließlich über Liquibase-
Changesets (ein Changeset-File pro Tabelle). Datenzugriff über `JdbcClient`
(explizites SQL); **kein** Hibernate/`ddl-auto`.

**Testing**: JUnit 5 + AssertJ für Unit-Tests der Kernlogik (Punktewertung,
Reveal-/Eval-Timing, Tendenz-Ermittlung); Testcontainers (PostgreSQL) für
Repository-/Integrationstests; MockWebServer für externe API-Clients.

**Target Platform**: Langlaufender JVM-Serverprozess (Container/VM), der die
Gateway-Verbindung dauerhaft hält; Neustart/Reconnect darf getrackte
Board-Nachrichten und Reveal-/Eval-Stand nicht verlieren.

**Project Type**: Single-Module-Backend-Service (Discord-Bot)

**Performance Goals**: Reveal innerhalb ≤ 2 Min nach Anpfiff (minütlicher Job);
Filter-Antwort ≤ 3 s (Interaction-Defer + Edit); Board ortsfest via Edit.

**Constraints**: Anstoßzeiten UTC in DB, Anzeige `Europe/Berlin`; Discord-Embed-
Limits (max. 25 Felder / 6000 Zeichen / 10 Embeds) → Board-Aufteilung nach Tag;
football-data.org Free-Tier 10 Req/Min → Sync-Intervalle/Caching beachten;
Offenlegung/Auswertung idempotent über Neustarts.

**Scale/Scope**: 48 Teams, 104 Spiele, eine Community/ein Guild; kleine
Teilnehmerzahl (Dutzende User), 6 User Stories (MVP F1–F7).

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

Geprüft gegen `.specify/memory/constitution.md` v1.0.0:

| Prinzip | Anforderung | Status im Plan |
|---|---|---|
| I. Technologie-Stack | Java 21, Spring Boot 3.x, PostgreSQL | ✅ exakt getroffen |
| II. Liquibase-only | Keine DDL/`ddl-auto` außerhalb Changesets | ✅ `JdbcClient` statt JPA ⇒ kein Hibernate/`ddl-auto`; ein Changeset-File pro Tabelle |
| III. Test-First Kernlogik (NON-NEGOTIABLE) | Tests zuerst für Punktewertung & Reveal-Timing | ✅ Scoring- und Reveal-/Eval-Timing als reine, von Discord/DB entkoppelte Domänenfunktionen → Tests vor Implementierung (Red-Green) |
| IV. Zeit UTC↔Berlin | UTC speichern, `Europe/Berlin` anzeigen | ✅ `Instant`/`TIMESTAMPTZ`, Logik rechnet in UTC; Umrechnung nur in der Anzeige-Schicht |
| V. JDA dauerhafte Gateway-Verbindung | Ereignisgetrieben, Reconnect-fest | ✅ JDA-Listener dauerhaft verbunden; Component-Interaktionen über Gateway; persistente `bot_messages` + idempotente Jobs |

**Ergebnis (Initial Gate)**: PASS — keine Verstöße, Complexity Tracking nicht erforderlich.

**Ergebnis (Post-Design Re-Check, nach Phase 1)**: PASS — Datenmodell und
Kontrakte führen keine schema-verändernde Persistenz, keine Logik in lokaler
Zeit und keine Polling-statt-Gateway-Konstrukte ein.

**Hinweis zur Stack-Entscheidung**: Die Spec nannte „better/plain JDBC oder
Spring Data JPA". Der Plan wählt **Spring JDBC (`JdbcClient`)** — siehe
[research.md](./research.md) (R1). Begründung: explizite SQL-Kontrolle
(Upserts, Aggregation mit Tie-Breaker), keine `ddl-auto`-Versuchung (Prinzip II),
einfache Testbarkeit. JPA bliebe zulässig, müsste aber zwingend mit
`ddl-auto=none` betrieben werden.

## Project Structure

### Documentation (this feature)

```text
specs/001-wm-tippspiel-bot/
├── plan.md              # This file (/speckit-plan command output)
├── research.md          # Phase 0 output (/speckit-plan command)
├── data-model.md        # Phase 1 output (/speckit-plan command)
├── quickstart.md        # Phase 1 output (/speckit-plan command)
├── contracts/           # Phase 1 output (/speckit-plan command)
│   ├── discord-commands.md   # Slash-Command- & Component-Interaktions-Kontrakte
│   └── external-apis.md      # football-data.org & The Odds API (verwendete Felder)
└── tasks.md             # Phase 2 output (/speckit-tasks command - NOT created by /speckit-plan)
```

### Source Code (repository root)

```text
pom.xml
src/
├── main/
│   ├── java/com/example/wmtippspiel/
│   │   ├── WmTippspielApplication.java
│   │   ├── config/            # JDA-Bean, WebClient-Beans, Scheduling, ZoneId-Config
│   │   ├── domain/            # Reine Domänenmodelle + Kernlogik (testpflichtig)
│   │   │   ├── model/         # Match, Tip, BotMessage, MatchStatus, Stage
│   │   │   └── scoring/       # ScoringService (3/1/0), Tendenz-Ermittlung
│   │   ├── persistence/       # JdbcClient-Repositories (Match/Tip/BotMessage)
│   │   ├── sync/              # FootballDataClient, OddsClient, MatchSyncService, OddsSyncService, ChannelMapping
│   │   ├── scheduling/        # SyncJob, RevealJob, EvaluateJob, BoardRefreshJob
│   │   ├── discord/           # JDA-Listener, Command-Handler, Component-Handler
│   │   │   ├── commands/      # /spielplan, /naechstes, /tipp, /rangliste
│   │   │   ├── components/    # Board-Navigation (Select/Buttons), Filter-Handler
│   │   │   ├── board/         # BoardService (Post/Edit, 404-Recovery)
│   │   │   └── render/        # Embed-Builder + Zeit-Formatierung Europe/Berlin
│   │   ├── reveal/            # RevealService (idempotent)
│   │   └── evaluation/        # EvaluationService (3/1/0, Neubewertung, idempotent)
│   └── resources/
│       ├── application.yml
│       ├── tv-channels.yml    # Manuell gepflegtes TV-Sender-Mapping
│       └── db/changelog/
│           ├── db.changelog-master.yaml
│           └── changesets/
│               ├── 001-create-matches.sql
│               ├── 002-create-tips.sql
│               └── 003-create-bot-messages.sql
└── test/
    └── java/com/example/wmtippspiel/
        ├── domain/scoring/    # ScoringServiceTest (3/1/0, Tendenz, Edge Cases) — Test-First
        ├── reveal/            # RevealServiceTest (Timing, Idempotenz) — Test-First
        ├── evaluation/        # EvaluationServiceTest (Auswertung, Neubewertung) — Test-First
        ├── persistence/       # Repository-Tests (Testcontainers PostgreSQL)
        └── sync/              # API-Client-Tests (MockWebServer)
```

**Structure Decision**: Single-Module-Maven-Backend (Option 1, „single project"),
da der Bot ein einzelner langlaufender Service ohne separates Frontend ist. Die
Domänen-Kernlogik (`domain/scoring`, `reveal/`, `evaluation/`) ist bewusst frei
von Discord- und DB-Abhängigkeiten, damit sie gemäß Prinzip III isoliert
test-first entwickelt werden kann.

## Complexity Tracking

> Keine Constitution-Verstöße — Tabelle entfällt.
