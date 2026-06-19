# WM 2026 Tippspiel Discord-Bot

Discord-Bot für ein Community-Tippspiel zur FIFA WM 2026: Spielplan-Überblick,
ephemerale Tippabgabe, automatische Offenlegung bei Anpfiff, Auswertung nach dem
3/1/0-Schema, Rangliste und ein selbst-aktualisierendes Live-Board mit Filtern.

Feature-Spezifikation, Plan und Tasks liegen unter
[`specs/001-wm-tippspiel-bot/`](specs/001-wm-tippspiel-bot/).

## Tech-Stack

- **Java 21**, **Spring Boot 3.x**
- **JDA** (Java Discord API) – dauerhafte Gateway-Verbindung (Slash-Commands,
  Autocomplete, Component-Interaktionen)
- **PostgreSQL** + **Liquibase** (Schema ausschließlich über Changesets, ein File
  pro Tabelle – kein `ddl-auto`)
- **Spring JDBC (`JdbcClient`)** für expliziten Datenzugriff
- **`WebClient`** für die externen APIs (football-data.org, The Odds API)
- Zeit: Persistenz in **UTC**, Anzeige in **Europe/Berlin**

Diese Vorgaben sind in der Projektverfassung verankert:
[`.specify/memory/constitution.md`](.specify/memory/constitution.md).

## Voraussetzungen

- JDK 21, Maven 3.9+
- PostgreSQL (lokal oder Docker)
- Docker (für die Testcontainers-Integrationstests)
- Zugangsdaten: Discord-Bot-Token + Guild-/Channel-IDs, football-data.org
  API-Key, optional The Odds API-Key

## Konfiguration

Geheimnisse über Umgebungsvariablen setzen (nicht committen):

```
DISCORD_TOKEN, DISCORD_GUILD_ID, DISCORD_ANNOUNCE_CHANNEL_ID, DISCORD_BOARD_CHANNEL_ID, DISCORD_INFO_CHANNEL_ID, DISCORD_TIP_CHANNEL_ID, DISCORD_NOTIFY_ROLE_ID
FOOTBALL_DATA_API_KEY
ODDS_ENABLED=true|false   ODDS_API_KEY
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/wmtippspiel
SPRING_DATASOURCE_USERNAME / SPRING_DATASOURCE_PASSWORD
APP_TIMEZONE_DISPLAY=Europe/Berlin
```

Optionale Mapping-Dateien im Classpath:
- `tv-channels.yml` – TV-Sender je Match-ID
- `team-mapping.yml` – Odds-API-Teamnamen → kanonische Namen

## Bauen & Testen

```bash
mvn clean verify     # kompilieren + alle Tests (inkl. Testcontainers; Docker nötig)
mvn test             # alle Tests
```

Verfassungs-Pflichttests (Prinzip III, Kernlogik):

```bash
mvn -Dtest=ScoringServiceTest test       # 3/1/0-Punktewertung
mvn -Dtest=RevealServiceTest test         # Reveal-Timing & Idempotenz
mvn -Dtest=EvaluationServiceTest test     # Auswertung & Neubewertung
```

## Starten

```bash
mvn spring-boot:run
```

Ablauf beim Start: Liquibase migriert → JDA verbindet sich dauerhaft mit dem
Gateway → Slash-Commands werden guild-scoped registriert → Board-Slots werden
gepostet bzw. editiert.

## Live-Tor-Benachrichtigungen (F8)

Während laufender Spiele postet der Bot zeitnah „⚽ TOR!"-Nachrichten in den
Announce-Channel (mit Role-Ping). Der `liveGoalPoll`-Job fragt nur im Live-Fenster
(`kickoff` … `kickoff + 2,5 h`, Status SCHEDULED/IN_PLAY) den aktuellen Stand über
die bestehende football-data.org-Anbindung ab und erkennt Tore per Score-Diff
gegen den persistierten gemeldeten Stand (`notified_home/away`).

- Mehrere Tore zwischen zwei Polls → mehrere Posts (laufender Stand).
- VAR-Rücknahme → „⛔ Tor aberkannt"-Notiz (ohne Ping), kein Tor-Post.
- Neustart mitten im Spiel → keine Dopplungen; verpasste Tore werden nachgereicht.
- Die Event-Quelle ist hinter `GoalEventSource` gekapselt (Default: Score-Diff-
  Polling) und später gegen Webhook/WebSocket austauschbar.

Optional konfigurierbar: `APP_JOBS_LIVE_GOAL_POLL_INTERVAL_MS` (Default 60000).

## WM-Notify (Rolle)

Benachrichtigungen laufen ausschließlich über die WM-Notify-Rolle
(`DISCORD_NOTIFY_ROLE_ID`): Der Button im Info-Channel vergibt/entzieht sie;
Reveal, Auswertung und der Anpfiff-Hinweis pingen die Rolle, und die
Tipp-Erinnerung pingt gezielt nur Rollenmitglieder ohne Tipp. Voraussetzungen:

- Bot-Recht **Manage Roles**; die höchste Bot-Rolle muss **über** der Notify-Rolle stehen.
- Für Role-Pings: Rolle **erwähnbar** schalten **oder** dem Bot „Mention All Roles" geben.
- **Server Members Intent** (privileged) im Discord Developer Portal **aktivieren** —
  sonst startet der Bot nicht (er braucht die Rollenmitglieder für die gezielte Erinnerung).

## Mit Docker starten (empfohlen)

Vollständiger Stack (PostgreSQL mit persistentem Volume + Bot):

```bash
cp .env.example .env          # Werte eintragen (mind. DISCORD_TOKEN, FOOTBALL_DATA_API_KEY)
docker compose up -d --build  # DB + Bot starten
docker compose logs -f bot    # Logs verfolgen
```

Nur die Datenbank im Container (Bot lokal aus IDE/Maven):

```bash
docker compose up -d db
mvn spring-boot:run           # nutzt jdbc:postgresql://localhost:5432/wmtippspiel
```

Hinweise:
- Die DB-Daten liegen im Named Volume `pgdata` und überleben Neustarts (Tipps,
  `bot_messages`, Reveal-/Eval-Stand bleiben erhalten).
- DB-Zugangsdaten werden aus `.env` interpoliert (Default `wmtippspiel`/`wmtippspiel`).
  Im Compose-Netz erreicht der Bot die DB unter Host `db`.
- Tests laufen nicht im Image-Build (sie brauchen Docker/Testcontainers); dafür
  `mvn verify` auf dem Host nutzen.

## Commands

| Command | Zweck | Sichtbarkeit |
|---|---|---|
| `/tipp <spiel> <heim> <gast>` | Tipp abgeben/aktualisieren (Autocomplete) | ephemeral |
| `/rangliste` | Aktuelle Rangliste | öffentlich |
| `/spielplan [anzahl]` | Nächste N Spiele (Standard 5) | öffentlich |
| `/naechstes` | Nächstes Spiel mit Countdown | öffentlich |

Automatisch: Offenlegung bei Anpfiff, Auswertung nach Abpfiff, Live-Board mit
Filter-Select im Board-Channel.

## Hintergrund-Jobs

| Job | Intervall | Aufgabe |
|---|---|---|
| Match-Sync | ~15 Min | Spielplan/Ergebnisse holen, upserten |
| Odds-Sync | ~6 h | Quoten holen (wenn aktiviert) |
| Reveal | minütlich | Tipps anstehender Spiele offenlegen |
| Auswertung | minütlich | Beendete Spiele werten |
| Board-Refresh | ~15 Min | Live-Board editieren |

## Öffentliche Read-only-API (Feature 008)

Für eine externe, öffentliche Website stellt der Bot eine **rein lesende**
HTTP-API (Servlet/Tomcat, Default-Port `8080`) unter `/api/public/**` bereit —
nur `GET`, ohne Authentifizierung, ohne Schreibpfade.

| Endpoint | Zweck |
|---|---|
| `GET /api/public/schedule[?stage=&group=&matchday=]` | Spielplan (vollständig/gefiltert), Anstoß in UTC |
| `GET /api/public/matches/live` | Aktuell laufende Spiele mit Stand |
| `GET /api/public/leaderboard` | Rangliste (Punkte, exakte Treffer, Rang-Veränderung) |
| `GET /api/public/matches/{matchId}/tips` | Tipps eines Spiels — **erst nach Anpfiff** (`kickoff` & `revealed`) |
| `GET /api/public/players/{publicId}` | Spielerprofil über stabilen, nicht-sensiblen Identifier |

Eigenschaften:

- **Datenschutz**: Antworten enthalten nur unbedenkliche Felder (Anzeigename,
  Spiel-/Statistikdaten). Keine Discord-`user_id`, E-Mail, Tokens oder interne
  Schlüssel. Spieler werden über einen HMAC-abgeleiteten `publicId` adressiert.
- **Reveal-Schutz**: Einzeltipps werden serverseitig erst freigegeben, wenn
  `now() (UTC) ≥ kickoff` UND das Spiel `revealed` ist.
- **Zeit**: Zeitpunkte in UTC (ISO-8601); Formatierung macht das Frontend.
- **CORS/Caching**: Frontend und API laufen auf demselben vServer unter
  getrennten (Sub-)Domains (z. B. `app.…` und `api.…`), daher CORS nur für die
  konfigurierte Frontend-Origin; Spielplan/Leaderboard mit kurzer Cache-TTL.
  Backend hinter einem Reverse-Proxy (TLS) betreiben; Port nur an `127.0.0.1`.

**API-Doku (Swagger / OpenAPI)** — automatisch aus dem Code generiert (springdoc):

- Swagger-UI: `GET /swagger-ui.html`
- OpenAPI-Spezifikation (JSON): `GET /v3/api-docs`

Konfiguration (siehe `.env.example`): `SERVER_PORT`,
`PUBLIC_API_CORS_ALLOWED_ORIGINS`, **`PUBLIC_API_ID_SECRET` (Pflicht — ohne Wert
schlägt der Start fehl)**, `PUBLIC_API_CACHE_TTL_SECONDS`. Hinter einem
Reverse-Proxy mit TLS betreiben.

> **Reverse-Proxy & Swagger**: Die Swagger-UI lädt zusätzlich Ressourcen unter
> `/swagger-ui/**` und `/webjars/**`. Am einfachsten leitet der Proxy für die
> API-(Sub-)Domain **alle** Pfade ans Backend. Wird nur `/api/public/**`
> weitergeleitet, müssen `/swagger-ui/**`, `/v3/api-docs` und `/webjars/**`
> zusätzlich geroutet werden, sonst bleibt die UI leer.
>
> **„Failed to fetch" in der Swagger-UI** (Mixed Content): Hinter einem
> TLS-terminierenden Proxy muss dieser die Forwarded-Header setzen, damit das
> Backend `https`/den externen Host kennt (sonst erzeugt es eine
> `http://localhost:8080`-Server-URL, die der Browser blockt):
> ```nginx
> proxy_set_header Host              $host;
> proxy_set_header X-Forwarded-Proto $scheme;   # -> https
> proxy_set_header X-Forwarded-Host  $host;
> proxy_set_header X-Forwarded-For   $remote_addr;
> ```
> Das Backend wertet diese via `server.forward-headers-strategy=framework` aus.
> Alternativ/als Garantie `PUBLIC_API_PUBLIC_BASE_URL` (z. B. `https://api.…`)
> setzen — dann wird diese URL fest als OpenAPI-Server hinterlegt.

## Betriebshinweise

- Der Prozess muss durchlaufen (Gateway-Verbindung). JDA reconnectet automatisch;
  getrackte Board-Nachrichten (`bot_messages`) und die Reveal-/Eval-Flags
  überleben Neustarts, sodass nicht doppelt gepostet/gewertet wird.
- Keine Geheimnisse ins Repository (siehe `.gitignore`).
- Schemaänderungen ausschließlich als neues Liquibase-Changeset.
