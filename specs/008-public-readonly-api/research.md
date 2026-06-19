# Phase 0 — Research: Öffentliche Read-only-API

Alle offenen Entscheidungen aus Technical Context sind hier aufgelöst; es
verbleiben keine `NEEDS CLARIFICATION`.

## R1 — Web-Stack: Servlet vs. reaktiv

- **Decision**: `spring-boot-starter-web` (Tomcat, Servlet) ergänzen und
  `spring.main.web-application-type` von `none` auf `servlet` setzen. Vom Nutzer
  bestätigt (Plan-Entscheidung).
- **Rationale**: Die gesamte Persistenz-/Service-Schicht ist blockierend
  (`JdbcClient`, synchrone Services). Der Servlet-Stack ist der natürliche Sitz
  dafür: direkte Rückgabe von DTOs, einfaches `@Cacheable`, MockMvc-Tests.
  `WebClient` (aus `spring-boot-starter-webflux`) funktioniert unverändert als
  reiner HTTP-Client weiter.
- **Alternatives considered**: Reaktiv über das bereits vorhandene WebFlux
  (keine neue Abhängigkeit) — verworfen, weil jeder Endpoint blockierende
  JDBC-Aufrufe auf `Schedulers.boundedElastic()` auslagern müsste und Caching mit
  `Mono`/`Flux` umständlicher und Event-Loop-Blocking-Fehler wahrscheinlicher
  sind.
- **Impact / Risiko**: `web-application-type: none → servlet` startet Tomcat.
  Bestehende `@SpringBootTest`-Tests dürfen dadurch nicht ungewollt einen
  Container hochfahren ⇒ in nicht-Web-Tests explizit `webEnvironment = NONE`
  setzen (siehe R7). Docker/Compose muss den Port (Default 8080) exponieren.

## R2 — Stabiler, nicht-sensibler öffentlicher Identifier (Clarify Q1)

- **Decision**: `publicId = Base64Url( HMAC-SHA256(key = app.public-api.id-secret,
  msg = user_id) )`, gekürzt auf einen stabilen Präfix (z. B. 16–22 Zeichen),
  zur Laufzeit berechnet. **Keine Persistenz, keine Schema-Änderung.**
- **Auflösung `publicId → user`**: HMAC ist einwegig, daher Rückauflösung per
  **Enumeration**: über die Teilnehmerliste (`TipRepository.leaderboard()` liefert
  je `user_id` den Anzeigenamen) den `publicId` jedes Nutzers berechnen und
  vergleichen. Bei Community-Größe (Dutzende) vernachlässigbar; Ergebnis ist
  zusätzlich cachebar.
- **Rationale**: Stabil über Anzeigenamen-Umbenennungen (basiert auf der
  unveränderlichen `user_id`), gibt die Discord-ID nicht preis (nicht
  zurückrechenbar ohne Secret), kommt ohne neue Spalte/Tabelle aus (Prinzip II
  bleibt unangetastet).
- **Alternatives considered**:
  - Persistierter Zufalls-Token (neue Spalte/Tabelle + Liquibase-Changeset) —
    verworfen, da Q1 bewusst die persistenzfreie HMAC-Variante gewählt hat.
  - Slug aus Anzeigenamen — verworfen: nicht stabil bei Umbenennung/Kollision.
- **Sicherheit**: Das Secret kommt ausschließlich aus der Umgebung
  (`PUBLIC_API_ID_SECRET`); fehlt es, MUSS der Start fehlschlagen bzw. die
  Profil-/ID-Funktion deaktiviert sein (kein unsicherer Default).

## R3 — Reveal-Gate für Tipps-pro-Spiel (Clarify Q2)

- **Decision**: Einzeltipps eines Spiels werden NUR dann ins DTO übernommen,
  wenn **`now() (UTC) ≥ match.kickoff` UND `match.revealed == true`**. Andernfalls
  liefert der Endpoint `MatchTipsDto{ released: false, tips: [] }` (HTTP 200) —
  ohne Namen, ohne getippte Ergebnisse, ohne tipp-rekonstruierende Aggregate.
- **Rationale**: Konservativste Auslegung der harten Sicherheitsanforderung
  (FR-012/013, SC-002). Die Prüfung erfolgt im Service VOR dem Mapping; verborgene
  Tipps gelangen gar nicht erst in die Serialisierung.
- **Umsetzung**: Vergleich auf `Instant` (UTC, Prinzip IV); `now()` über die
  vorhandene `Clock`/`TimeConfig`-Bean injizieren, damit der Vor-/Nach-Anpfiff-
  Test deterministisch ist.
- **Alternatives considered**: nur `kickoff` oder nur `revealed` — in Q2
  zugunsten der UND-Verknüpfung verworfen.

## R4 — Datenschutz im Mapping (kein Leak)

- **Decision**: Dedizierte Public-DTOs als Java-`record`s, die schlicht **kein
  Feld** für `user_id`, E-Mail, Tokens oder interne IDs besitzen. Mapping
  ausschließlich in `PublicMappers`; Domänen-`Tip`/`Match` werden NIE direkt
  serialisiert.
- **Rationale**: Strukturelle Garantie statt Filter-Blacklist — was kein
  DTO-Feld ist, kann nicht serialisiert werden. Ergänzend ein Test, der die
  JSON-Antworten gegen eine Verbotsliste (`user_id`, `email`, `token`) prüft
  (SC-001).
- **Match-ID als Ressourcenschlüssel**: Die football-data-Fixture-ID dient als
  Pfadschlüssel (`/matches/{id}/tips`). Sie ist öffentliche Stammdaten-Referenz
  (kein nutzerbezogenes/Geheimnis-Feld) und wird als zulässiger öffentlicher
  Bezeichner behandelt; Discord-`user_id` bleibt strikt intern.

## R5 — Wiederverwendung bestehender Logik (read-only)

- **Decision**: Keine neue Query-/Scoring-Logik. Wiederverwendung:
  - Spielplan: neue read-only `MatchRepository.findAll()`; Filter (Phase/Gruppe/
    Spieltag) in-memory im Service (Datensatz klein).
  - Live: `MatchRepository.findInPlay()`.
  - Leaderboard: `TipRepository.leaderboard()` (exakte Treffer bereits per
    direktem Ergebnisvergleich, FR-010) → `LeaderboardRanking.compute(...)` mit
    `LeaderboardSnapshotRepository.findAllRanks()` für die Rang-Veränderung.
  - Tipps-pro-Spiel: `MatchRepository.findById()` + `TipRepository.findByMatch()`
    hinter dem Reveal-Gate.
  - Profil: `TipRepository.findEvaluatedTipsByUser()` + `ProfileStats.build(...)`
    (+ Rang aus der gerankten Liste). `findEvaluatedTipsByUser` liefert nur
    `evaluated`-Tipps ⇒ Historie enthält ausschließlich bereits gespielte/
    gewertete Spiele und wahrt damit die Reveal-Regel automatisch (FR-017).
- **Rationale**: „Nur auslesen, nichts neu berechnen" (FR-004); minimiert Risiko
  für bestehende Features. `LeaderboardSnapshotRepository.replaceAll(...)` wird
  vom Read-Layer NICHT aufgerufen — die Vergleichsbasis bleibt allein vom
  `EvaluateJob` (F11) gepflegt.

## R6 — Leichtes Caching (FR-021)

- **Decision**: `spring-boot-starter-cache` + Caffeine; `@Cacheable` auf den
  Service-Methoden für **Spielplan** und **Leaderboard** mit kurzer TTL
  (konfigurierbar `app.public-api.cache-ttl-seconds`, Default ~5 s). **Live** und
  **Tipps-pro-Spiel** werden NICHT gecacht (Frische bzw. Reveal-Korrektheit),
  Profil optional ungecacht.
- **Rationale**: Öffentliche, unauthentifizierte Last liefert für alle Clients
  identische Daten ⇒ kurzes Caching senkt DB-Last drastisch ohne spürbare
  Verzögerung; TTL im Sekundenbereich hält Stände aktuell genug.
- **Alternatives considered**: `ConcurrentMapCacheManager` (kein TTL → veraltet)
  und Eigenbau-Zeitcache — verworfen (siehe Complexity Tracking).

## R7 — Kein Schreibpfad / Methodenrestriktion (SC-006) & Testintegrität

- **Decision**: Der Controller deklariert ausschließlich `@GetMapping`. Andere
  HTTP-Methoden ergeben automatisch `405 Method Not Allowed` — kein Spring
  Security nötig. Ein MockMvc-Test verifiziert 405 auf POST/PUT/DELETE.
- **Bestehende Tests**: Nach Umstellung auf `web-application-type: servlet`
  müssen nicht-Web-`@SpringBootTest`s `webEnvironment = SpringBootTest.WebEnvironment.NONE`
  setzen (bzw. ein Test-Profil mit `web-application-type: none`), damit sie keinen
  Tomcat starten. Neue Web-Tests nutzen `@SpringBootTest(webEnvironment = MOCK)`
  + MockMvc bzw. `@AutoConfigureMockMvc`.

## R8 — CORS (Vercel-Frontend)

- **Decision**: `WebMvcConfigurer#addCorsMappings` für `/api/public/**`,
  `allowedOrigins` = konfigurierbare Liste (`app.public-api.cors-allowed-origins`,
  z. B. die Vercel-Domain), `allowedMethods` = `GET, OPTIONS`, keine Credentials.
- **Rationale**: Genau die Frontend-Domain freigeben statt Wildcard; passt zur
  öffentlichen, credential-losen Nutzung.
