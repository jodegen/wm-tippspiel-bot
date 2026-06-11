# Quickstart: WM 2026 Tippspiel Discord-Bot

Lokaler Entwicklungs- und Betriebs-Einstieg.

## Voraussetzungen

- **JDK 21** (LTS)
- **Maven 3.9+**
- **PostgreSQL** (lokal oder via Docker)
- **Docker** (für Testcontainers in den Integrationstests)
- Zugangsdaten:
  - Discord-Bot-Token + Guild-ID + Announce-/Board-Channel-IDs
  - football-data.org API-Key
  - The Odds API-Key (optional)

## Konfiguration (`application.yml` / Umgebungsvariablen)

Geheimnisse NICHT committen — über Umgebung setzen (Verfassung: Tech-/Datenstandards):

```
DISCORD_TOKEN, DISCORD_GUILD_ID, DISCORD_ANNOUNCE_CHANNEL_ID, DISCORD_BOARD_CHANNEL_ID
FOOTBALL_DATA_API_KEY
ODDS_API_KEY            # optional
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/wmtippspiel
SPRING_DATASOURCE_USERNAME / SPRING_DATASOURCE_PASSWORD
APP_TIMEZONE_DISPLAY=Europe/Berlin   # Anzeige; Persistenz bleibt UTC
```

Hinweis: **kein** `spring.jpa.hibernate.ddl-auto` — Schema kommt ausschließlich
von Liquibase (Prinzip II).

## Datenbank / Migrationen

Liquibase läuft beim App-Start automatisch (`db/changelog/db.changelog-master.yaml`).
Ein Changeset-File pro Tabelle unter `db/changelog/changesets/`. Manuelles DDL
ist untersagt — neue Schemaänderung = neues additives Changeset.

## Bauen & Testen

```bash
mvn clean verify        # kompiliert + führt alle Tests aus
mvn test                # nur Tests
```

**Pflicht-Tests (Prinzip III, Test-First)** — müssen grün sein vor Merge von
Kernlogik-Änderungen:

```bash
mvn -Dtest=ScoringServiceTest test       # 3/1/0-Punktewertung & Tendenz
mvn -Dtest=RevealServiceTest test        # Reveal-Timing & Idempotenz
mvn -Dtest=EvaluationServiceTest test    # Auswertung & Neubewertung
```

## Starten

```bash
mvn spring-boot:run
```

Beim Start: Liquibase migriert → JDA verbindet sich dauerhaft mit dem Gateway →
Slash-Commands werden guild-scoped registriert → Board-Slots werden (einmalig)
gepostet bzw. anschließend editiert.

## Smoke-Test (manuell)

1. `/spielplan` → nächste 5 Spiele erscheinen (Zeiten in Europe/Berlin).
2. `/tipp` mit Autocomplete → tippbares Spiel wählen, Tipp abgeben → **nur für
   dich sichtbare** Bestätigung; erneutes `/tipp` aktualisiert statt dupliziert.
3. Spiel mit Anpfiff in der Vergangenheit präparieren → nach ≤ 1 Min erscheint
   genau ein Reveal-Post.
4. Spiel `FINISHED` mit Endstand → Auswertungs-Post mit 3/1/0-Punkten; `/rangliste`
   zeigt aktualisierte Punkte.
5. Board-Kanal: Navigations-Select wählen → **ephemerale** gefilterte Ansicht,
   öffentliches Board unverändert.
6. Board-Nachricht manuell löschen → nächster `boardRefresh` postet neu.

## Architektur-Eckpfeiler (siehe plan.md / research.md)

- Dauerhafte JDA-Gateway-Verbindung (R2) — nicht nur Cron.
- Reveal-Trigger an `kickoff`, nicht an API-Status (R3).
- UTC speichern, Europe/Berlin/Discord-Timestamp anzeigen (R4).
- `JdbcClient` statt JPA (R1); Liquibase-only.
- Kernlogik (`scoring`/`reveal`/`evaluation`) gegen `Clock` & Repository-
  Interfaces entkoppelt → deterministische Test-First-Suite (R10).
