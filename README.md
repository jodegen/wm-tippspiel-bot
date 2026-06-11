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
DISCORD_TOKEN, DISCORD_GUILD_ID, DISCORD_ANNOUNCE_CHANNEL_ID, DISCORD_BOARD_CHANNEL_ID, DISCORD_INFO_CHANNEL_ID, DISCORD_TIP_CHANNEL_ID
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

## Betriebshinweise

- Der Prozess muss durchlaufen (Gateway-Verbindung). JDA reconnectet automatisch;
  getrackte Board-Nachrichten (`bot_messages`) und die Reveal-/Eval-Flags
  überleben Neustarts, sodass nicht doppelt gepostet/gewertet wird.
- Keine Geheimnisse ins Repository (siehe `.gitignore`).
- Schemaänderungen ausschließlich als neues Liquibase-Changeset.
