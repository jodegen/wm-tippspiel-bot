# Implementation Plan: Öffentliche Read-only-API

**Branch**: `008-public-readonly-api` | **Date**: 2026-06-19 | **Spec**: [spec.md](./spec.md)

**Input**: Feature specification from `/specs/008-public-readonly-api/spec.md`

## Summary

Ein eigener, ausschließlich lesender HTTP-Layer (`/api/public/**`, nur GET) stellt
fünf Sichten für eine externe, öffentliche Read-only-Website bereit: Spielplan,
Live-Spiele, Leaderboard, Tipps-pro-Spiel (reveal-gegated) und Spielerprofil.
Der Layer ist rein additiv: Er liest über die **bestehenden** Repositories
(`MatchRepository`, `TipRepository`, `LeaderboardSnapshotRepository`) und die
bestehende reine Logik (`LeaderboardRanking`, `ProfileStats`) und berechnet nichts
neu. Dedizierte **Public-DTOs** geben nur unbedenkliche Felder preis; die interne
`user_id` (Discord-ID) wird niemals serialisiert. Spieler werden über einen
**deterministischen HMAC der `user_id`** adressiert (kein Persistieren, keine
Schema-Änderung). Der Tipps-Endpoint erzwingt serverseitig konservativ
`now() (UTC) ≥ kickoff` UND `revealed = true`, bevor überhaupt Einzeltipps ins
DTO übernommen werden. CORS wird für die Vercel-Frontend-Domain freigeschaltet;
Spielplan/Leaderboard erhalten leichtes, kurz-TTL-Caching.

**Technischer Ansatz** (aus Research): Da die gesamte Persistenz-/Service-Schicht
blockierend ist (`JdbcClient`), wird der bestehende `web-application-type: none`
auf **`servlet`** umgestellt und `spring-boot-starter-web` (Tomcat) ergänzt — der
natürliche Sitz für blockierende Repositories, einfaches `@Cacheable` und
MockMvc-Tests. JDA-Gateway und `@Scheduled`-Jobs laufen unverändert im selben
Prozess weiter.

## Technical Context

**Language/Version**: Java 21 (LTS)

**Primary Dependencies**: Spring Boot 3.3.5; **neu**: `spring-boot-starter-web`
(Tomcat, Servlet-Stack) + `spring-boot-starter-cache` + Caffeine (leichtes
TTL-Caching). Bestehend: `spring-boot-starter-jdbc` (`JdbcClient`),
`spring-boot-starter-webflux` (bleibt — nur als `WebClient`), JDA, Liquibase.

**Storage**: PostgreSQL via `JdbcClient` (read-only Nutzung in diesem Feature).
**Keine Schema-Änderung** — der öffentliche Identifier wird zur Laufzeit
abgeleitet (HMAC), nicht persistiert.

**Testing**: JUnit 5 + Spring Boot Test; MockMvc für Controller-/Security-/CORS-
Tests; Testcontainers-PostgreSQL (vorhandenes Muster aus `PersistenceIntegrationTest`)
für End-to-End-Lesepfade.

**Target Platform**: Linux-Server (Docker-Compose auf vServer), JVM-Prozess, der
zugleich Discord-Bot ist; Tomcat auf konfigurierbarem Port (Default 8080).

**Project Type**: Single Spring-Boot-Service (Discord-Bot + nun zusätzlich
read-only HTTP-API im selben Prozess).

**Performance Goals**: 95 % der Abrufe < 1 s (SC-005). Leichtes Caching
(Spielplan/Leaderboard, TTL Größenordnung Sekunden) schont die DB unter
öffentlicher, unauthentifizierter Last (FR-021).

**Constraints**: Kein Schreibpfad (nur GET; nicht-GET ⇒ 405, SC-006). Kein Leak
sensibler Felder (user_id/E-Mail/Tokens/interne IDs), verifiziert über JSON-
Strukturprüfung (SC-001). Reveal-Gate serverseitig, konservativ (SC-002). UTC in
JSON (FR-005). Secrets (HMAC-Key) nur über Umgebung (Verfassung).

**Scale/Scope**: Turnierdaten ~104 Spiele; Teilnehmer in Community-Größenordnung
(Dutzende). In-Memory-Filterung und HMAC-Enumeration zur Profilauflösung sind bei
dieser Größe unkritisch.

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Prinzip | Status | Begründung |
|---------|--------|------------|
| I. Festgelegter Stack (Java 21 / Spring Boot 3.x) | ✅ PASS | Bleibt Java 21 / Spring Boot 3.x. Neue Abhängigkeit `spring-boot-starter-web` ist Teil von Spring Boot und unumgänglich, um HTTP überhaupt anzubieten; in Complexity Tracking begründet. |
| II. Schema-Änderungen nur via Liquibase | ✅ PASS | **Keine** Schema-Änderung. Öffentlicher Identifier = HMAC zur Laufzeit, ohne Persistenz (Clarify Q1). Kein Changeset nötig. |
| III. Test-First für Kernlogik (Reveal-Timing) | ✅ PASS (verpflichtend) | Das Reveal-Gate (kickoff/revealed) ist Reveal-Timing-Kernlogik ⇒ Tests sind PFLICHT und werden test-first geschrieben (Red-Green-Refactor), insbesondere der Vor-/Nach-Anpfiff-Leak-Test. |
| IV. UTC speichern, Europe/Berlin anzeigen | ✅ PASS | API liefert Zeitpunkte in UTC (FR-005); Anzeige-Formatierung macht das Frontend. Reveal-Vergleich rechnet auf UTC (`Instant`). |
| V. JDA mit dauerhafter Gateway-Verbindung | ✅ PASS | Unberührt. Web-Server läuft additiv im selben Prozess; JDA-/Scheduling-Verdrahtung bleibt unverändert. |

**Gate-Ergebnis**: PASS (vor Phase 0 und nach Phase 1 — siehe unten).

## Project Structure

### Documentation (this feature)

```text
specs/008-public-readonly-api/
├── plan.md              # This file
├── research.md          # Phase 0 output
├── data-model.md        # Phase 1 output
├── quickstart.md        # Phase 1 output
├── contracts/
│   └── public-api.openapi.yaml   # Phase 1 output (read-only GET contract)
└── checklists/
    └── requirements.md  # from /speckit-specify (+ /speckit-clarify)
```

### Source Code (repository root)

```text
src/main/java/com/example/wmtippspiel/
├── publicapi/                       # NEU — gesamter Read-only-HTTP-Layer
│   ├── PublicApiController.java      # @RestController, /api/public, nur @GetMapping
│   ├── PublicQueryService.java       # liest bestehende Repos; @Cacheable (Schedule/Leaderboard)
│   ├── PublicIdService.java          # HMAC(user_id, secret) + Auflösung publicId → user_id
│   ├── PublicMappers.java            # Entity → Public-DTO (kein user_id/intern serialisiert)
│   ├── PublicApiConfig.java          # CORS (Vercel-Domain) + CacheManager (Caffeine)
│   └── dto/
│       ├── MatchDto.java             # Spielplan-Zeile (UTC-Anstoß, Sender, Quote, Ergebnis, Status, Gruppe/Phase)
│       ├── LiveMatchDto.java         # laufendes Spiel + aktueller Stand
│       ├── LeaderboardRowDto.java    # Rang, Anzeigename, Punkte, exakte Treffer, Rang-Delta
│       ├── MatchTipsDto.java         # { released: bool, tips: [PublicTipDto] } (reveal-gated)
│       ├── PublicTipDto.java         # Anzeigename, getipptes Ergebnis, ggf. Punkte
│       ├── ProfileDto.java           # Anzeigename, publicId, Statistik, Verteilung, best/worst, Historie
│       └── PointDistributionDto.java # Zähler 4/3/2/0
│
├── config/AppProperties.java        # ERWEITERT: nested record PublicApi(corsAllowedOrigins, idSecret, cacheTtlSeconds)
└── persistence/MatchRepository.java # ERWEITERT (read-only, additiv): findAll()

src/main/resources/
└── application.yml                  # web-application-type: none → servlet; server.port; app.public-api.*

src/test/java/com/example/wmtippspiel/publicapi/
├── PublicIdServiceTest.java         # HMAC: deterministisch, nicht reversibel, Auflösung
├── PublicMappersTest.java           # DTO/JSON enthält KEIN user_id/intern (SC-001)
├── RevealGateTest.java              # (Kernlogik, test-first) Vor-/Nach-Anpfiff (SC-002)
└── PublicApiWebTest.java            # MockMvc: 5 Endpoints, 404, 405 (SC-006), CORS-Header

src/test/java/com/example/wmtippspiel/persistence/
└── PersistenceIntegrationTest.java  # ERWEITERT: webEnvironment=NONE absichern + findAll()-Fixture
```

**Structure Decision**: Single-Project-Spring-Boot-Service bleibt bestehen. Der
neue Layer lebt isoliert im Paket `publicapi` (+ `publicapi.dto`) und hängt nur
lesend an vorhandenen Repositories/Helpern. Bestehende Pakete werden nur additiv
berührt: `AppProperties` (neue Config-Sektion), `MatchRepository` (eine read-only
`findAll()`), `application.yml` (Web-Server aktivieren).

## Complexity Tracking

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| Neue Abhängigkeit `spring-boot-starter-web` (Tomcat, Servlet) | Es gibt aktuell keinen HTTP-Server (`web-application-type: none`); ohne Web-Starter lässt sich keine REST-API anbieten. Servlet passt zum durchgängig blockierenden `JdbcClient`. | Reaktiv über vorhandenes WebFlux verworfen: blockierende JDBC-Aufrufe müssten je Endpoint auf `boundedElastic` ausgelagert werden, `@Cacheable` mit `Mono` ist umständlicher und fehleranfälliger (Event-Loop-Blocking-Risiko). |
| Neue Abhängigkeit `spring-boot-starter-cache` + Caffeine | Leichtes TTL-Caching (FR-021) zum DB-Schutz unter öffentlicher Last; idiomatisch via `@Cacheable`. | Eigenbau-Zeit-Cache verworfen (mehr fehleranfälliger Code als eine kleine, etablierte Bibliothek); `ConcurrentMapCacheManager` ohne TTL verworfen (kein Ablauf ⇒ veraltete Stände). |

> Beide Ergänzungen sind additiv, ändern kein Schema und berühren keine
> Kernlogik (Punktewertung/Reveal-Timing) inhaltlich; sie sind für das Feature
> notwendig und damit gemäß Prinzip I gerechtfertigt.

## Phase 0 — siehe [research.md](./research.md)
## Phase 1 — siehe [data-model.md](./data-model.md), [contracts/](./contracts/), [quickstart.md](./quickstart.md)

**Post-Design Constitution Re-Check**: PASS — das Design führt keine
Schema-Änderung ein, hält das Reveal-Gate als test-first Kernlogik, liefert UTC,
und lässt JDA/Scheduling unberührt. Keine neuen Verstöße.
