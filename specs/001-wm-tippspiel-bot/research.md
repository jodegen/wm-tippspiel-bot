# Phase 0 Research: WM 2026 Tippspiel Discord-Bot

Resolves the open technical decisions for the implementation plan. Jede
Entscheidung folgt dem Format Decision / Rationale / Alternatives.

## R1 — Persistenzschicht: `JdbcClient` vs. Spring Data JPA

- **Decision**: Spring JDBC über `JdbcClient` (Spring Framework 6.1+, in Spring
  Boot 3.2+ enthalten); ein dünnes Repository pro Aggregat. Kein ORM/Hibernate.
- **Rationale**:
  - Verfassung Prinzip II verbietet schema-verändernde Persistenz außerhalb von
    Liquibase. Ohne Hibernate existiert gar kein `ddl-auto` — die Versuchung
    entfällt strukturell.
  - Die anspruchsvollen Queries sind SQL-lastig: `INSERT … ON CONFLICT` (Upsert
    für Matches und Tipps), Leaderboard-Aggregation mit Tie-Breaker, gefiltertes
    Lesen für Board/Reveal. `JdbcClient` macht dieses SQL explizit und testbar.
  - Kleines, stabiles Schema (3 Tabellen) → ORM-Mehrwert gering.
- **Alternatives considered**:
  - **Spring Data JPA/Hibernate**: zulässig, aber nur mit `spring.jpa.hibernate.
    ddl-auto=none`; bringt Lazy-Loading-/Flush-Komplexität und verschleiert das
    SQL der Aggregationen. Verworfen.
  - **Spring Data JDBC**: leichter als JPA, aber Aggregat-Mapping-Konventionen
    erschweren die Custom-Aggregationen; `JdbcClient` ist hier transparenter.

## R2 — Discord-Anbindung: dauerhafte Gateway-Verbindung (JDA)

- **Decision**: JDA als Spring-Bean, beim Start verbunden (`JDABuilder` mit den
  nötigen Intents: `GUILD_MESSAGES`; Slash-Commands & Components brauchen keine
  privilegierten Intents). Slash-Commands werden beim Start guild-scoped
  registriert (sofort verfügbar). Interaktionen (Slash, Autocomplete, Component)
  laufen über Gateway-Event-Listener.
- **Rationale**: Prinzip V und FR-028 verlangen ereignisgetriebene, dauerhaft
  verbundene Interaktionsbehandlung; reine `@Scheduled`-Jobs (REST) können keine
  Button-/Select-Interaktionen empfangen. JDA hält die WebSocket-Verbindung,
  reconnectet automatisch und liefert getrackte `message_id`s zum Editieren.
- **Alternatives considered**: Discord4J (reaktiv) — gleichwertig möglich, aber
  JDA hat die einfachere, imperativere API und gute Spring-Integrationsbeispiele.
  Reines REST-Webhook-Modell — scheidet aus, da keine Gateway-Interaktionen.

## R3 — Reveal-Timing-Quelle: Anpfiffzeit statt API-Status

- **Decision**: Reveal-Trigger ist `kickoff <= now() AND revealed = false`
  (now() in UTC), unabhängig vom extern gemeldeten Status. Eval-Trigger ist
  `status = FINISHED AND evaluated = false`.
- **Rationale**: FR-013 und der Edge-Case „Anpfiff trotz Datenverzug". Die
  externe Quelle kann verzögert sein; das Reveal-Versprechen (Tipps spätestens
  bei Anpfiff dicht) darf nicht von API-Latenz abhängen. Timing-Logik ist reine,
  testbare UTC-Arithmetik (Prinzip III/IV).
- **Alternatives considered**: Reveal an `status = IN_PLAY` — verworfen, da
  statusabhängig und verzögerungsanfällig.

## R4 — Zeit-Handling UTC ↔ Europe/Berlin

- **Decision**: Persistenz als `TIMESTAMPTZ`, im Code als `java.time.Instant`.
  Alle Vergleiche/Jobs rechnen in UTC. Anzeige konvertiert an genau einer Stelle
  (`discord/render`) nach `ZoneId.of("Europe/Berlin")`. Für Discord-Countdowns
  wird der Unix-Sekunden-Timestamp (`<t:UNIX:R>` / `<t:UNIX:F>`) genutzt — die
  Zeitzonen-Darstellung übernimmt dann der Client.
- **Rationale**: Prinzip IV. Discord-Relative-Timestamps sind client-lokal und
  laufen als Countdown ohne serverseitige Aktualisierung.
- **Alternatives considered**: `LocalDateTime` in Berlin-Zeit speichern —
  verstößt gegen Prinzip IV, DST-anfällig. Verworfen.

## R5 — Externe APIs & Rate-Limits (`WebClient`)

- **Decision**: Zwei `WebClient`-Beans.
  - football-data.org (Competition `WC`): Spielplan & Ergebnisse; Auth-Header
    `X-Auth-Token`. Sync-Intervall ~15 Min (Free-Tier 10 Req/Min respektieren).
  - The Odds API (`soccer_fifa_world_cup`, Markt `h2h`): Quoten; Sync ~6 h
    (Quota schonen). Quoten optional — Fehler/Leere blockieren nichts (FR-003).
  - Resilienz: Timeouts, defensiver Fehler-Handler (Sync-Job loggt und überspringt
    bei Fehlern, statt zu crashen).
- **Rationale**: FR-001/003/032 und „Offene Punkte" der Quell-Spec (Rate-Limits).
- **Alternatives considered**: `RestClient` (synchron) — ebenfalls möglich; die
  Spec nennt explizit `WebClient`, daher `WebClient` (blockierend via `.block()`
  in den Jobs ist akzeptabel, da Jobs ohnehin scheduled laufen).

## R6 — Quoten-Matching football-data.org ↔ The Odds API

- **Decision**: MVP nutzt eine pflegbare Team-Namens-Mapping-Tabelle/Properties
  (`canonical team name` ↔ Odds-API-Name) statt reiner String-Heuristik; nicht
  zuordenbare Quoten werden verworfen (Spiel bleibt ohne Quoten).
- **Rationale**: „Offene Punkte" der Quell-Spec; robustes Mapping verhindert
  Falschzuordnungen. Quoten sind optional, daher ist Verwerfen akzeptabel.
- **Alternatives considered**: Fuzzy-String-Matching — fehleranfällig; reines
  Verwerfen ohne Mapping — zu viele fehlende Quoten.

## R7 — Live-Board: Slotting, Edit-Strategie & Recovery

- **Decision**: Board-Slots pro Tag (`board:day:YYYY-MM-DD`, plus `board:nav`
  für die Navigationskomponente); je Slot eine persistierte Nachricht in
  `bot_messages`. `boardRefresh`-Job editiert bestehende Nachrichten; schlägt der
  Edit mit 404 (`UnknownMessage`) fehl, wird neu gepostet und `message_id`
  aktualisiert (FR-027). Aufteilung nach Tag hält Embed-Limits ein (FR-023).
- **Rationale**: FR-021–FR-024/027, Embed-Limits (≤25 Felder/6000 Zeichen) und
  Board-Recovery aus „Offene Punkte".
- **Alternatives considered**: Eine Nachricht für alles — sprengt Embed-Limits.
  Neu posten statt editieren — verstößt gegen „ortsfestes Board" (FR-021/SC-006).

## R8 — Filter-Interaktionen ephemeral

- **Decision**: Component-Interaktion (`StringSelectInteractionEvent`/Buttons)
  wird mit `deferReply(true)`/`reply(...).setEphemeral(true)` beantwortet; das
  öffentliche Board wird dabei nicht verändert (FR-026, SC-006/007). `deferReply`
  sichert die 3-Sekunden-Antwortgrenze von Discord ab.
- **Rationale**: FR-025/026, SC-007.
- **Alternatives considered**: Öffentliche Antwort — leakt/verschmutzt Kanal.

## R9 — Idempotenz & Neubewertung

- **Decision**: `revealed`/`evaluated`-Flags als Guards; Reveal/Eval laufen in
  Transaktionen, die Flag-Setzen und Posten koppeln (Post-Fehler ⇒ Flag bleibt
  false ⇒ Retry im nächsten Lauf). Neubewertung (FR-017a): erkennt ein bereits
  `evaluated`-Spiel mit geändertem Endstand (Vergleich gespeicherter vs. neuer
  Score beim Sync), setzt `evaluated=false`, wertet neu aus und postet einen
  Korrektur-Hinweis.
- **Rationale**: FR-012/016/031 (idempotent über Neustarts) und FR-017a.
- **Alternatives considered**: Append-only Event-Log — überdimensioniert für MVP.

## R10 — Test-Strategie für Kernlogik (Prinzip III)

- **Decision**:
  - `ScoringService`/Tendenz: reine Funktionen `(homeActual, awayActual,
    homeTip, awayTip) -> points`; Parametrisierte JUnit-5-Tests decken 3/1/0 inkl.
    Unentschieden, Tendenzgrenzen und „daneben" ab. **Test-First.**
  - Reveal-/Eval-Timing: `RevealService`/`EvaluationService` arbeiten gegen eine
    injizierte `Clock` (UTC) und Repository-Interfaces (gemockt) → deterministische
    Timing-/Idempotenz-/Neubewertungs-Tests ohne Discord/DB. **Test-First.**
  - Repository- und API-Client-Tests separat (Testcontainers/MockWebServer),
    nicht test-first-pflichtig, aber empfohlen.
- **Rationale**: Prinzip III macht genau Punktewertung und Reveal-Timing
  test-pflichtig; Entkopplung über `Clock` und Interfaces macht sie schnell und
  deterministisch.
- **Alternatives considered**: End-to-End gegen echtes Discord — langsam, flaky,
  ungeeignet als Pflicht-Gate.

## Auflösung offener Spec-Punkte

| Offener Punkt (Quell-Spec / Spec) | Auflösung |
|---|---|
| Quoten-Matching | R6: pflegbare Mapping-Tabelle, sonst verwerfen |
| API-Rate-Limits | R5: Sync-Intervalle 15 Min / 6 h, defensive Fehlerbehandlung |
| Reveal-Edge-Case | R3: Trigger an `kickoff`, nicht an Status |
| Gateway statt Cron | R2: JDA dauerhaft verbunden |
| Embed-Limits | R7: Slotting pro Tag |
| Board-Recovery | R7: 404-Erkennung → Neu-Post + `bot_messages`-Update |
| Multi-Guild | Out of Scope (Spec-Annahme: eine Community/ein Guild) |

**Status**: Alle NEEDS CLARIFICATION aufgelöst. Bereit für Phase 1.
